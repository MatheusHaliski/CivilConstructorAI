/**
 * Biblioteca de objetos (móveis, equipamentos, louças, iluminação, pontos de instalação e pessoas) e conexões
 * prediais. Fonte única: ../catalogo/objetos.json, o mesmo arquivo que o backend Java lê (CatalogoObjetos).
 *
 * Três camadas da instalação, mantidas separadas (AUD-03):
 *  - ligação lógica: o circuito (`circuito`) e o sistema da conexão;
 *  - conexão física: o elemento CONEXAO entre dois conectores identificados (porta de saída → porta de entrada);
 *  - representação gráfica: o percurso esquemático derivado (`percursoConexao`), recalculado sempre que um dos
 *    objetos se move. Uma linha desenhada encostada no objeto não é conexão.
 */
import catalogoJson from "../catalogo/objetos.json" with { type: "json" };
import type { Camada, Conexao, ModeloLeitura, Objeto, Ponto2D, Ponto3D, SistemaPredial } from "./tipos";

export type FluxoConector = "ENTRA" | "SAI";
export type ConectorItem = {
  id: string; sistema: SistemaPredial; fluxo: FluxoConector; pos: Ponto3D; papel: string;
  tensaoV?: number | string; potenciaW?: number; dnMm?: number; correnteMaxA?: number; maxConexoes?: number; nota?: string;
};
export type Montagem = "PISO" | "PAREDE" | "TETO";
export type ItemObjeto = {
  id: string; nome: string; categoria: string; disciplina: "mobiliario" | "eletrica" | "hidrossanitaria" | "humanos"; forma: string;
  dim: { l: number; p: number; a: number }; montagem: Montagem; elevacao?: number; redimensionavel: boolean; ifc: string; cor: string;
  palavras: string; conectores: ConectorItem[]; nota?: string; sobreposicaoPermitida?: boolean;
  alturaVariavel?: { min: number; max: number }; luz?: { fluxoLm: number; temperaturaK: number; alcanceM: number };
};
export type CatalogoObjetos = { versao: string; descricao: string; aviso: string; categorias: { id: string; nome: string }[]; itens: ItemObjeto[] };

export const CATALOGO_OBJETOS = catalogoJson as unknown as CatalogoObjetos;

export function itemObjeto(id: string | undefined | null): ItemObjeto | undefined {
  return id ? CATALOGO_OBJETOS.itens.find((i) => i.id === id) : undefined;
}

/** camada (disciplina) em que o objeto entra: mobiliário, elétrica, hidrossanitária ou pessoas */
export function camadaDoItem(i: ItemObjeto): Camada {
  return i.disciplina;
}

const semAcento = (s: string) => s.normalize("NFD").replace(/\p{M}/gu, "").toLowerCase();

/** busca por nome, categoria, palavras, fabricante ("genérico") e dimensões ("2.1", "210", "180x60") */
export function buscarObjetos(termo: string, categoria?: string): ItemObjeto[] {
  const t = semAcento(termo.trim());
  const dims = t.match(/\d+(?:[.,]\d+)?/g)?.map((x) => Number(x.replace(",", "."))).map((x) => (x > 10 ? x / 100 : x)) ?? [];
  return CATALOGO_OBJETOS.itens.filter((i) => {
    if (categoria && i.categoria !== categoria) return false;
    if (!t) return true;
    const texto = semAcento(`${i.nome} ${i.categoria} ${i.palavras} generico ${i.id}`);
    const palavras = t.replace(/\d+(?:[.,]\d+)?(?:\s*x\s*\d+(?:[.,]\d+)?)*/g, " ").split(/\s+/).filter(Boolean);
    const okTexto = palavras.every((p) => texto.includes(p));
    const okDims = dims.every((d) => [i.dim.l, i.dim.p, i.dim.a].some((v) => Math.abs(v - d) <= 0.011));
    return okTexto && okDims;
  });
}

// ------------------------------------------------------------------ geometria do objeto
const RAD = Math.PI / 180;

/** pé-direito livre do pavimento: elevação do pavimento de cima menos a laje mais espessa dele (ou 2,80 m) */
export function tetoDoPavimento(m: ModeloLeitura, pavimentoId: string): number {
  const pav = m.pavimento(pavimentoId);
  if (!pav) return 2.8;
  const acima = m.pavimentoAcima(pavimentoId);
  if (!acima) return 2.8;
  const lajes = m.elementosDo(acima.id).filter((e) => e.tipo === "LAJE").map((e) => (e as { h: number }).h);
  return Math.round((acima.elevacao - pav.elevacao - (lajes.length ? Math.max(...lajes) : 0)) * 1000) / 1000;
}

/** ponto local (x ao longo da largura, y ao longo da profundidade, z a partir da base) em coordenadas globais */
export function pontoGlobal(m: ModeloLeitura, o: Objeto, local: Ponto3D): Ponto3D {
  const r = o.rotacao * RAD, c = Math.cos(r), s = Math.sin(r);
  const pav = m.pavimento(o.pavimentoId);
  return { x: o.centro.x + local.x * c - local.y * s, y: o.centro.y + local.x * s + local.y * c, z: (pav?.elevacao ?? 0) + o.elevacao + local.z };
}

/** cantos em planta do objeto (retângulo girado) */
export function cantosObjeto(o: Objeto): Ponto2D[] {
  const r = o.rotacao * RAD, c = Math.cos(r), s = Math.sin(r), l = o.dim.l / 2, p = o.dim.p / 2;
  return [[-l, -p], [l, -p], [l, p], [-l, p]].map(([x, y]) => ({ x: o.centro.x + x! * c - y! * s, y: o.centro.y + x! * s + y! * c }));
}

/** fator de escala dos conectores quando o objeto foi redimensionado (pessoas, móveis redimensionáveis) */
function escalaConector(o: Objeto, i: ItemObjeto, c: ConectorItem): Ponto3D {
  return { x: c.pos.x * (o.dim.l / i.dim.l), y: c.pos.y * (o.dim.p / i.dim.p), z: c.pos.z * (o.dim.a / i.dim.a || 1) };
}

export function conectorDoObjeto(o: Objeto, id: string): ConectorItem | undefined {
  return itemObjeto(o.item)?.conectores.find((c) => c.id === id);
}

export function posicaoConector(m: ModeloLeitura, o: Objeto, conectorId: string): Ponto3D | undefined {
  const i = itemObjeto(o.item); const c = i?.conectores.find((x) => x.id === conectorId);
  return i && c ? pontoGlobal(m, o, escalaConector(o, i, c)) : undefined;
}

// ------------------------------------------------------------------ conexões
export const ROTA = {
  /** elétrica e ventilação sobem e correm sob o teto; água fria e quente também, mais baixas; esgoto corre sob o piso com declividade */
  ELETRICA: 0.05, VENTILACAO: 0.1, AGUA_FRIA: 0.2, AGUA_QUENTE: 0.3, ESGOTO_PROFUNDIDADE: 0.25,
};
export const DECLIVIDADE_PADRAO = 0.02;
export const MATERIAL_PADRAO: Record<SistemaPredial, string> = {
  ELETRICA: "MAT.CONDUTOR.COBRE_PVC_70", AGUA_FRIA: "MAT.TUBO.PVC_AGUA_FRIA", AGUA_QUENTE: "MAT.TUBO.CPVC_AGUA_QUENTE", ESGOTO: "MAT.TUBO.PVC_ESGOTO", VENTILACAO: "MAT.TUBO.PVC_ESGOTO",
};

const r3 = (v: number) => Math.round(v * 1000) / 1000;
const p3 = (x: number, y: number, z: number): Ponto3D => ({ x: r3(x), y: r3(y), z: r3(z) });

/**
 * Percurso esquemático (não roteado): sobe da saída até a faixa do sistema, corre em X e depois em Y e desce até a
 * entrada. Esgoto desce para baixo do piso e corre com declividade constante. Mesmo algoritmo no Java (ConexoesPrediais).
 */
export function percursoConexao(m: ModeloLeitura, c: Conexao): Ponto3D[] {
  const de = m.elemento(c.de.id) as Objeto | undefined, para = m.elemento(c.para.id) as Objeto | undefined;
  if (!de || !para || de.tipo !== "OBJETO" || para.tipo !== "OBJETO") return [];
  const a = posicaoConector(m, de, c.de.conector), b = posicaoConector(m, para, c.para.conector);
  if (!a || !b) return [];
  const pts: Ponto3D[] = [];
  // tomada → aparelho é o cabo do próprio aparelho (plugue), não eletroduto: linha direta
  if (itemObjeto(de.item)?.conectores.find((k) => k.id === c.de.conector)?.papel === "TOMADA") return [p3(a.x, a.y, a.z), p3(b.x, b.y, b.z)];
  if (c.sistema === "ESGOTO") {
    const pav = m.pavimento(de.pavimentoId);
    const z0 = (pav?.elevacao ?? 0) - ROTA.ESGOTO_PROFUNDIDADE;
    const i = c.declividade ?? DECLIVIDADE_PADRAO;
    const dx = Math.abs(b.x - a.x), dy = Math.abs(b.y - a.y);
    pts.push(p3(a.x, a.y, a.z), p3(a.x, a.y, z0), p3(b.x, a.y, z0 - i * dx), p3(b.x, b.y, z0 - i * (dx + dy)), p3(b.x, b.y, b.z));
  } else {
    const pav = m.pavimento(de.pavimentoId);
    const zf = (pav?.elevacao ?? 0) + tetoDoPavimento(m, de.pavimentoId) - ROTA[c.sistema];
    pts.push(p3(a.x, a.y, a.z), p3(a.x, a.y, zf), p3(b.x, a.y, zf), p3(b.x, b.y, zf), p3(b.x, b.y, b.z));
  }
  return pts.filter((p, k) => k === 0 || Math.hypot(p.x - pts[k - 1]!.x, p.y - pts[k - 1]!.y, p.z - pts[k - 1]!.z) > 1e-6);
}

export function comprimentoPercurso(pts: Ponto3D[]): number {
  let s = 0;
  for (let k = 1; k < pts.length; k++) s += Math.hypot(pts[k]!.x - pts[k - 1]!.x, pts[k]!.y - pts[k - 1]!.y, pts[k]!.z - pts[k - 1]!.z);
  return r3(s);
}

/** cota inicial e final do trecho horizontal de esgoto (para a verificação de declividade) */
export function cotasEsgoto(pts: Ponto3D[]): { inicio: number; fim: number; horizontal: number } {
  if (pts.length < 4) return { inicio: 0, fim: 0, horizontal: 0 };
  const ini = pts[1]!, fim = pts[pts.length - 2]!;
  return { inicio: ini.z, fim: fim.z, horizontal: r3(Math.abs(fim.x - ini.x) + Math.abs(fim.y - ini.y)) };
}

const tensoes = (t: number | string | undefined): number[] => (t === undefined ? [] : String(t).split("/").map(Number));

export type ProblemaConexao = { codigo: string; mensagem: string };

/**
 * Regras de compatibilidade de uma conexão (laço 1 do auditor, igual no Java): objetos e conectores existentes,
 * mesmo sistema, saída → entrada, tensão compatível, corrente do aparelho dentro do limite da tomada e número de
 * ligações por conector.
 */
export function problemasConexao(m: ModeloLeitura, c: Conexao): ProblemaConexao[] {
  const out: ProblemaConexao[] = [];
  const de = m.elemento(c.de.id) as Objeto | undefined, para = m.elemento(c.para.id) as Objeto | undefined;
  if (!de || de.tipo !== "OBJETO" || !para || para.tipo !== "OBJETO") return [{ codigo: "CON-OBJETO", mensagem: "A conexão precisa ligar dois objetos existentes." }];
  if (de.id === para.id) out.push({ codigo: "CON-MESMO", mensagem: "Um objeto não pode ser ligado a ele mesmo." });
  const ide = itemObjeto(de.item), ipara = itemObjeto(para.item);
  const cde = ide?.conectores.find((x) => x.id === c.de.conector), cpara = ipara?.conectores.find((x) => x.id === c.para.conector);
  if (!cde) out.push({ codigo: "CON-CONECTOR", mensagem: `${ide?.nome ?? de.item} não tem o conector ${c.de.conector}${ide && !ide.conectores.length ? " (este objeto não tem conexões elétricas nem hidráulicas)" : ""}.` });
  if (!cpara) out.push({ codigo: "CON-CONECTOR", mensagem: `${ipara?.nome ?? para.item} não tem o conector ${c.para.conector}${ipara && !ipara.conectores.length ? " (este objeto não tem conexões elétricas nem hidráulicas)" : ""}.` });
  if (!cde || !cpara) return out;
  if (cde.sistema !== cpara.sistema || cde.sistema !== c.sistema) out.push({ codigo: "CON-SISTEMA", mensagem: `Sistemas incompatíveis: ${rotuloSistema(cde.sistema)} (${ide!.nome}) com ${rotuloSistema(cpara.sistema)} (${ipara!.nome}).` });
  if (cde.fluxo !== "SAI" || cpara.fluxo !== "ENTRA") out.push({ codigo: "CON-FLUXO", mensagem: `A ligação vai de uma saída para uma entrada: ${ide!.nome}.${cde.id} é ${cde.fluxo === "SAI" ? "saída" : "entrada"} e ${ipara!.nome}.${cpara.id} é ${cpara.fluxo === "SAI" ? "saída" : "entrada"}.` });
  if (cde.sistema === "ELETRICA" && cpara.sistema === "ELETRICA") {
    const td = tensoes(cde.tensaoV), tp = tensoes(cpara.tensaoV);
    if (td.length && tp.length && !tp.some((t) => td.includes(t))) out.push({ codigo: "CON-TENSAO", mensagem: `Tensão incompatível: ${ipara!.nome} é ${tp.join("/")} V e ${ide!.nome} fornece ${td.join("/")} V.` });
    const potencia = para.potenciaW ?? cpara.potenciaW;
    if (cde.correnteMaxA && potencia) {
      const v = tp[0] ?? td[0] ?? 127; const ib = potencia / v;
      if (ib > cde.correnteMaxA + 1e-9) out.push({ codigo: "CON-CORRENTE", mensagem: `${ipara!.nome} (${potencia} W em ${v} V → ${ib.toFixed(1)} A) excede a ${ide!.nome} (${cde.correnteMaxA} A). Ligue em circuito próprio a partir do quadro.` });
    }
  }
  for (const [o, con, item] of [[de, cde, ide!], [para, cpara, ipara!]] as const) {
    const usadas = m.elementos.filter((e) => e.tipo === "CONEXAO" && e.id !== c.id && ((e.de.id === o.id && e.de.conector === con.id) || (e.para.id === o.id && e.para.conector === con.id))).length;
    if (usadas + 1 > (con.maxConexoes ?? 1)) out.push({ codigo: "CON-OCUPADO", mensagem: `O conector ${con.id} de ${item.nome} já está ligado (${usadas} de ${con.maxConexoes ?? 1}).` });
  }
  return out;
}

export function rotuloSistema(s: SistemaPredial): string {
  return { ELETRICA: "elétrica", AGUA_FRIA: "água fria", AGUA_QUENTE: "água quente", ESGOTO: "esgoto", VENTILACAO: "ventilação" }[s];
}

/** objeto: regras de dimensão e montagem (laço 1) */
export function problemasObjeto(m: ModeloLeitura, o: Objeto): ProblemaConexao[] {
  const i = itemObjeto(o.item);
  if (!i) return [{ codigo: "OBJ-ITEM", mensagem: `Objeto "${o.item}" não existe na biblioteca.` }];
  const out: ProblemaConexao[] = [];
  if (!(o.dim.l > 0 && o.dim.p > 0 && o.dim.a > 0)) out.push({ codigo: "OBJ-DIM", mensagem: "Dimensões do objeto precisam ser positivas." });
  if (i.alturaVariavel) {
    if (o.dim.a < i.alturaVariavel.min - 1e-9 || o.dim.a > i.alturaVariavel.max + 1e-9) out.push({ codigo: "OBJ-ALTURA", mensagem: `${i.nome}: altura entre ${i.alturaVariavel.min} e ${i.alturaVariavel.max} m (informado ${o.dim.a} m).` });
    const k = o.dim.a / i.dim.a;
    if (Math.abs(o.dim.l - i.dim.l * k) > 0.002 || Math.abs(o.dim.p - i.dim.p * k) > 0.002) out.push({ codigo: "OBJ-PROPORCAO", mensagem: `${i.nome}: a escala é uniforme (altura define as proporções); não deforme a figura.` });
  } else if (!i.redimensionavel && (Math.abs(o.dim.l - i.dim.l) > 0.001 || Math.abs(o.dim.p - i.dim.p) > 0.001 || Math.abs(o.dim.a - i.dim.a) > 0.001)) {
    out.push({ codigo: "OBJ-DIM", mensagem: `${i.nome} tem dimensões de produto (${i.dim.l} × ${i.dim.p} × ${i.dim.a} m) e não pode ser deformado. Escolha outro modelo da biblioteca.` });
  }
  if (i.montagem === "PAREDE" && !(o.paredeId && m.elemento(o.paredeId)?.tipo === "PAREDE")) out.push({ codigo: "OBJ-HOSPEDEIRO", mensagem: `${i.nome} é fixado em parede: posicione sobre uma parede.` });
  if (o.elevacao < -2 || o.elevacao > 12) out.push({ codigo: "OBJ-ELEVACAO", mensagem: "Elevação do objeto fora de −2 a 12 m em relação ao piso." });
  return out;
}
