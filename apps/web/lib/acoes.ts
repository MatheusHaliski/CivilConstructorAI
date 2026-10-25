/**
 * Catálogo de ações construtivas com códigos modulares (proposta interna, não são códigos de norma):
 *   MOD.CRIAR.<ELEMENTO>  ação de modelagem executável (vira um comando da Notação VI)
 *   MAT.<FAMÍLIA>.<VARIANTE> material da Tabela A (o materialId legado continua aceito)
 * O id é estável e separado do rótulo; `versao` muda quando entradas mudam e `legado` mantém códigos antigos interpretáveis.
 * Cada ação declara entradas com unidade e limites, pré-condições, resultado, permissão e se pode ser desfeita.
 */
import { TABELA_A, formatarVI, type Material } from "@cca/notacao-vi";

export type TipoEntradaAcao = "ponto" | "comprimento" | "inteiro" | "material" | "parede" | "pilar" | "texto" | "fracao" | "pontos";
export type EntradaAcao = {
  nome: string; rotulo: string; tipo: TipoEntradaAcao; unidade?: "m" | "un" | "%";
  padrao?: number | string; min?: number; max?: number; categoriaMaterial?: Material["ontology"]["category"]; opcional?: boolean; ajuda?: string;
};
export type AcaoConstrutiva = {
  id: string; versao: number; legado?: string[]; codigoVi: string; rotulo: string; grupo: "Arquitetura" | "Estrutura" | "Fundação" | "Referência";
  sinonimos: string[]; descricao: string; entradas: EntradaAcao[]; precondicoes: string[]; resultado: string; erros: string[];
  permissao: "EDITAR_MODELO"; desfazer: true; dependencias?: string;
};

const ponto = (nome: string, rotulo: string): EntradaAcao => ({ nome, rotulo, tipo: "ponto", unidade: "m" });
const comp = (nome: string, rotulo: string, padrao: number, min: number, max: number, ajuda?: string): EntradaAcao => ({ nome, rotulo, tipo: "comprimento", unidade: "m", padrao, min, max, ajuda });

export const ACOES: AcaoConstrutiva[] = [
  { id: "MOD.CRIAR.MALHA", versao: 1, codigoVi: "MALHA", rotulo: "Malha de eixos", grupo: "Referência", sinonimos: ["grid", "eixos", "malha", "quadrícula"],
    descricao: "Cria eixos X (A, B, C…) e Y (1, 2, 3…) com espaçamento constante.",
    entradas: [{ nome: "nx", rotulo: "Eixos em X", tipo: "inteiro", unidade: "un", padrao: 4, min: 2, max: 30 }, comp("dx", "Espaçamento X", 5, 1, 20), { nome: "ny", rotulo: "Eixos em Y", tipo: "inteiro", unidade: "un", padrao: 3, min: 2, max: 30 }, comp("dy", "Espaçamento Y", 5, 1, 20)],
    precondicoes: ["Nomes de eixo não podem repetir os existentes."], resultado: "nx + ny eixos nomeados.", erros: ["VI-003 eixo com nome repetido"], permissao: "EDITAR_MODELO", desfazer: true },
  { id: "MOD.CRIAR.PAVIMENTO", versao: 1, codigoVi: "PAVIMENTO", rotulo: "Pavimento", grupo: "Referência", sinonimos: ["nível", "andar", "level", "piso"],
    descricao: "Cria um nível; pilares do nível abaixo passam a ter este como topo.",
    entradas: [{ nome: "nome", rotulo: "Nome", tipo: "texto", padrao: "Pavimento tipo" }, comp("z", "Elevação", 3, -20, 300)],
    precondicoes: ["Não pode existir pavimento na mesma elevação (±1 cm)."], resultado: "Novo pavimento.", erros: ["VI-003 elevação repetida"], permissao: "EDITAR_MODELO", desfazer: true,
    dependencias: "Atualiza o nível de topo dos pilares do pavimento imediatamente abaixo." },
  { id: "MOD.CRIAR.PAREDE", versao: 1, legado: ["PAREDE"], codigoVi: "PAREDE", rotulo: "Parede", grupo: "Arquitetura", sinonimos: ["alvenaria", "muro", "vedação", "wall"],
    descricao: "Parede entre dois pontos com espessura, altura e material.",
    entradas: [ponto("p1", "Início"), ponto("p2", "Fim"), comp("esp", "Espessura", 0.15, 0.05, 1), comp("h", "Altura", 2.8, 0.3, 20), { nome: "mat", rotulo: "Material", tipo: "material", categoriaMaterial: "arquitetura", padrao: "MAT_ALV_BLOCO14" }],
    precondicoes: ["Comprimento maior que a ShortCurveTolerance.", "Espessura ≥ mínimo do material/restrições."], resultado: "Elemento PAREDE hospedeiro de portas e janelas.",
    erros: ["VI-002 segmento curto", "VI-003 espessura abaixo do mínimo"], permissao: "EDITAR_MODELO", desfazer: true, dependencias: "Portas e janelas hospedadas acompanham a parede." },
  { id: "MOD.CRIAR.PORTA", versao: 1, codigoVi: "PORTA", rotulo: "Porta", grupo: "Arquitetura", sinonimos: ["door", "abertura", "acesso"],
    descricao: "Abertura hospedada numa parede, na posição relativa ao longo dela.",
    entradas: [{ nome: "parede", rotulo: "Parede hospedeira", tipo: "parede" }, { nome: "posicao", rotulo: "Posição ao longo da parede", tipo: "fracao", unidade: "%", padrao: 50, min: 0, max: 100 }, comp("larg", "Largura", 0.8, 0.6, 3), comp("alt", "Altura", 2.1, 1.8, 3.5)],
    precondicoes: ["Exige parede hospedeira.", "A abertura precisa caber na parede."], resultado: "Elemento PORTA vinculado à parede (paredeId).", erros: ["VI-002 sem parede hospedeira"], permissao: "EDITAR_MODELO", desfazer: true,
    dependencias: "Descontada da área de alvenaria nos quantitativos." },
  { id: "MOD.CRIAR.JANELA", versao: 1, codigoVi: "JANELA", rotulo: "Janela", grupo: "Arquitetura", sinonimos: ["window", "abertura", "esquadria", "vidro"],
    descricao: "Abertura hospedada numa parede, com peitoril.",
    entradas: [{ nome: "parede", rotulo: "Parede hospedeira", tipo: "parede" }, { nome: "posicao", rotulo: "Posição ao longo da parede", tipo: "fracao", unidade: "%", padrao: 50, min: 0, max: 100 }, comp("larg", "Largura", 1.2, 0.3, 6), comp("alt", "Altura", 1.2, 0.3, 3), comp("peitoril", "Peitoril", 1.0, 0, 2.5)],
    precondicoes: ["Exige parede hospedeira."], resultado: "Elemento JANELA vinculado à parede.", erros: ["VI-002 sem parede hospedeira"], permissao: "EDITAR_MODELO", desfazer: true },
  { id: "MOD.CRIAR.AMBIENTE", versao: 1, codigoVi: "AMBIENTE", rotulo: "Ambiente", grupo: "Arquitetura", sinonimos: ["room", "cômodo", "sala", "espaço"],
    descricao: "Marca um ambiente nomeado num ponto interno.", entradas: [ponto("ponto", "Ponto interno"), { nome: "nome", rotulo: "Nome", tipo: "texto", padrao: "Sala" }],
    precondicoes: [], resultado: "Elemento AMBIENTE (rótulo e área quando fechado).", erros: [], permissao: "EDITAR_MODELO", desfazer: true },
  { id: "MOD.CRIAR.PILAR", versao: 1, codigoVi: "PILAR", rotulo: "Pilar", grupo: "Estrutura", sinonimos: ["coluna", "column", "apoio"],
    descricao: "Pilar retangular do pavimento até o nível acima.",
    entradas: [ponto("centro", "Centro"), comp("b", "Largura b", 0.2, 0.14, 2, "NBR 6118: lado ≥ 19 cm (14 cm com γn)."), comp("h", "Altura da seção h", 0.4, 0.14, 3), { nome: "mat", rotulo: "Material", tipo: "material", categoriaMaterial: "estrutural", padrao: "MAT_CONC_FCK30" }],
    precondicoes: ["Seção ≥ mínimos (Tolerâncias e seções)."], resultado: "Elemento PILAR com nível base/topo.", erros: ["VI-003 seção abaixo do mínimo"], permissao: "EDITAR_MODELO", desfazer: true },
  { id: "MOD.CRIAR.VIGA", versao: 1, codigoVi: "VIGA", rotulo: "Viga", grupo: "Estrutura", sinonimos: ["beam", "travessa"],
    descricao: "Viga entre dois pontos; é discretizada nos apoios intermediários na análise.",
    entradas: [ponto("p1", "Início"), ponto("p2", "Fim"), comp("b", "Largura b", 0.14, 0.12, 1.5), comp("h", "Altura h", 0.4, 0.2, 3), { nome: "mat", rotulo: "Material", tipo: "material", categoriaMaterial: "estrutural", padrao: "MAT_CONC_FCK30" }],
    precondicoes: ["b ≥ 12 cm (NBR 6118)."], resultado: "Elemento VIGA.", erros: ["VI-002 segmento curto"], permissao: "EDITAR_MODELO", desfazer: true },
  { id: "MOD.CRIAR.LAJE", versao: 1, codigoVi: "LAJE", rotulo: "Laje", grupo: "Estrutura", sinonimos: ["slab", "piso", "pano"],
    descricao: "Laje por contorno fechado (3+ vértices).",
    entradas: [{ nome: "contorno", rotulo: "Vértices do contorno", tipo: "pontos", unidade: "m" }, comp("h", "Espessura h", 0.12, 0.07, 0.5), { nome: "mat", rotulo: "Material", tipo: "material", categoriaMaterial: "estrutural", padrao: "MAT_CONC_FCK30" }],
    precondicoes: ["Contorno com 3+ vértices e sem autointerseção."], resultado: "Elemento LAJE.", erros: ["VI-002 contorno inválido"], permissao: "EDITAR_MODELO", desfazer: true },
  { id: "MOD.CRIAR.SAPATA", versao: 1, codigoVi: "SAPATA", rotulo: "Sapata", grupo: "Fundação", sinonimos: ["fundação", "footing", "base"],
    descricao: "Sapata sob um pilar (ou num centro).",
    entradas: [{ nome: "pilar", rotulo: "Pilar apoiado", tipo: "pilar" }, comp("bx", "Dimensão bx", 1.5, 0.6, 8), comp("by", "Dimensão by", 1.5, 0.6, 8), comp("h", "Altura", 0.5, 0.25, 3)],
    precondicoes: ["Dimensionamento definitivo vem de Geotecnia › Fundações (NBR 6122)."], resultado: "Elemento SAPATA vinculado ao pilar.", erros: [], permissao: "EDITAR_MODELO", desfazer: true },
];

/** MAT.<CATEGORIA>.<materialId sem prefixo> ↔ materialId legado da Tabela A. */
export function codigoMaterial(m: Material): string {
  return `MAT.${m.ontology.category.toUpperCase()}.${m.materialId.replace(/^MAT_/, "")}`;
}
export function materialPorCodigo(codigo: string): Material | undefined {
  return TABELA_A.find((m) => m.materialId === codigo || codigoMaterial(m) === codigo);
}
export function acaoPorCodigo(codigo: string): AcaoConstrutiva | undefined {
  const c = codigo.trim().toUpperCase();
  return ACOES.find((a) => a.id === c || a.codigoVi === c || a.legado?.includes(c));
}

const semAcento = (s: string) => s.normalize("NFD").replace(/\p{M}/gu, "").toLowerCase();
export function buscarAcoes(termo: string): AcaoConstrutiva[] {
  const t = semAcento(termo.trim());
  if (!t) return ACOES;
  return ACOES.filter((a) => semAcento([a.id, a.rotulo, a.grupo, a.descricao, ...a.sinonimos].join(" ")).includes(t));
}

export type ValoresAcao = Record<string, string | { x: number; y: number } | { x: number; y: number }[]>;
export type ErroValidacao = { campo: string; mensagem: string };

/** Valida unidades e limites e monta os argumentos do comando VI. */
export function montarArgs(acao: AcaoConstrutiva, v: ValoresAcao): { args: Record<string, unknown>; erros: ErroValidacao[] } {
  const args: Record<string, unknown> = {}; const erros: ErroValidacao[] = [];
  for (const e of acao.entradas) {
    const bruto = v[e.nome];
    if (e.tipo === "ponto") {
      const p = bruto as { x: number; y: number } | undefined;
      if (!p || !Number.isFinite(p.x) || !Number.isFinite(p.y)) erros.push({ campo: e.nome, mensagem: `${e.rotulo}: informe o ponto.` });
      else args[e.nome] = { x: p.x, y: p.y };
    } else if (e.tipo === "pontos") {
      const pts = (bruto as { x: number; y: number }[] | undefined) ?? [];
      if (pts.length < 3) erros.push({ campo: e.nome, mensagem: `${e.rotulo}: são necessários 3 ou mais vértices.` });
      else args[e.nome] = pts;
    } else if (e.tipo === "parede" || e.tipo === "pilar") {
      if (!bruto) { if (!e.opcional) erros.push({ campo: e.nome, mensagem: `${e.rotulo}: escolha um elemento.` }); }
      else args[e.nome] = String(bruto);
    } else if (e.tipo === "material") {
      const m = materialPorCodigo(String(bruto ?? e.padrao ?? ""));
      if (!m) erros.push({ campo: e.nome, mensagem: `${e.rotulo}: material desconhecido.` });
      else if (e.categoriaMaterial && m.ontology.category !== e.categoriaMaterial) erros.push({ campo: e.nome, mensagem: `${m.name} não é da categoria ${e.categoriaMaterial}.` });
      else args[e.nome] = m.materialId;
    } else if (e.tipo === "texto") {
      const s = String(bruto ?? e.padrao ?? "").trim();
      if (!s && !e.opcional) erros.push({ campo: e.nome, mensagem: `${e.rotulo}: obrigatório.` });
      else if (s) args[e.nome] = s;
    } else {
      const s = String(bruto ?? e.padrao ?? "").replace(",", ".").trim();
      const n = Number(s);
      if (!s || !Number.isFinite(n)) { erros.push({ campo: e.nome, mensagem: `${e.rotulo}: número inválido.` }); continue; }
      if (e.tipo === "inteiro" && !Number.isInteger(n)) { erros.push({ campo: e.nome, mensagem: `${e.rotulo}: use um número inteiro.` }); continue; }
      if (e.min != null && n < e.min) { erros.push({ campo: e.nome, mensagem: `${e.rotulo}: mínimo ${e.min}${e.unidade === "%" ? "%" : e.unidade ? ` ${e.unidade}` : ""}.` }); continue; }
      if (e.max != null && n > e.max) { erros.push({ campo: e.nome, mensagem: `${e.rotulo}: máximo ${e.max}${e.unidade === "%" ? "%" : e.unidade ? ` ${e.unidade}` : ""}.` }); continue; }
      args[e.nome] = e.tipo === "fracao" ? n / 100 : n;
    }
  }
  if (acao.codigoVi === "PAREDE" || acao.codigoVi === "VIGA") {
    const a = args["p1"] as { x: number; y: number } | undefined, b = args["p2"] as { x: number; y: number } | undefined;
    if (a && b && Math.hypot(b.x - a.x, b.y - a.y) < 0.05) erros.push({ campo: "p2", mensagem: "Início e fim coincidem (comprimento < 5 cm)." });
  }
  return { args, erros };
}

export function viDaAcao(acao: AcaoConstrutiva, args: Record<string, unknown>): string { return formatarVI(acao.codigoVi, args); }
