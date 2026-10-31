/**
 * ModeloBim: fonte da verdade do desenho. Nada entra sem `aplicar(ComandoVI)`, que roda o auditor
 * (laço 1 + laço 2 determinísticos) e devolve o diff. Desfazer/refazer por snapshots versionados.
 */
import type {
  Abertura, AncoraCota, Circulacao, ComandoVI, Conexao, Cota, DiffModelo, Eixo, Elemento, EstadoModelo, Laje, Objeto, OrigemComando, Parede, Pavimento, Pilar, Ponto2D, RelatorioAuditoria, SistemaPredial, Viga,
} from "./tipos";
import { DECLIVIDADE_PADRAO, MATERIAL_PADRAO, camadaDoItem, itemObjeto, tetoDoPavimento, type ConectorItem } from "./objetos";
import { ESPELHO_ADOTADO, elevacaoNoPiso } from "./circulacao";
import { auditar, type OpcoesAuditoria } from "./auditor";
import { arredondarPonto, dist, espelhar, projetarNoSegmento, rotacionar, soma, sub } from "./geometria";
import { formatarVI } from "./vi";
import { normalizarMaterialId } from "./tabela-a";

let seq = 0;
export function novoId(prefixo = "e"): string {
  seq += 1;
  return `${prefixo}_${Date.now().toString(36)}${seq.toString(36)}`;
}

/** A…Z, AA, AB… (malhas acima de 26 eixos em X) */
export function nomeColuna(i: number): string {
  let s = "";
  for (let n = i; n >= 0; n = Math.floor(n / 26) - 1) s = String.fromCharCode(65 + (n % 26)) + s;
  return s;
}

export class ErroComandoVI extends Error {
  constructor(public readonly codigo: string, mensagem: string, public readonly relatorio?: RelatorioAuditoria) {
    super(mensagem);
  }
}

export type ResultadoAplicacao = { diff: DiffModelo; relatorio: RelatorioAuditoria; comando: ComandoVI; vi: string };

type Snapshot = { estado: EstadoModelo; comando: ComandoVI | null };

export class ModeloBim {
  versao = 0;
  pavimentos: Pavimento[] = [];
  eixos: Eixo[] = [];
  elementos: Elemento[] = [];
  historico: ComandoVI[] = [];
  private pilhaDesfazer: Snapshot[] = [];
  private pilhaRefazer: Snapshot[] = [];
  opcoesAuditoria: OpcoesAuditoria = {};

  constructor(estado?: Partial<EstadoModelo>) {
    if (estado) Object.assign(this, structuredClone(estado));
    if (this.pavimentos.length === 0) this.pavimentos.push({ id: "pav_terreo", nome: "Térreo", elevacao: 0, tipo: "TERREO" });
  }

  estado(): EstadoModelo {
    return structuredClone({ versao: this.versao, pavimentos: this.pavimentos, eixos: this.eixos, elementos: this.elementos });
  }

  elemento(id: string): Elemento | undefined { return this.elementos.find((e) => e.id === id); }
  pavimento(id: string): Pavimento | undefined { return this.pavimentos.find((p) => p.id === id); }
  pavimentosOrdenados(): Pavimento[] { return [...this.pavimentos].sort((a, b) => a.elevacao - b.elevacao); }
  pavimentoAcima(id: string): Pavimento | undefined {
    const ord = this.pavimentosOrdenados(); const i = ord.findIndex((p) => p.id === id);
    return i >= 0 ? ord[i + 1] : undefined;
  }
  elementosDo(pavimentoId: string): Elemento[] { return this.elementos.filter((e) => e.pavimentoId === pavimentoId); }

  /** Aplica um comando: audita, grava, versiona. Lança ErroComandoVI em reprovação (422) ou conflito (409). */
  aplicar(codigo: string, args: Record<string, unknown>, opts: { origem?: OrigemComando; versaoBase?: number; pavimentoId?: string; autorId?: string } = {}): ResultadoAplicacao {
    const versaoBase = opts.versaoBase ?? this.versao;
    if (versaoBase !== this.versao) throw new ErroComandoVI("CONFLITO", `Modelo avançou para a versão ${this.versao} (base ${versaoBase}). Recarregue o diff.`);
    const comando: ComandoVI = { id: novoId("cmd"), codigo: codigo.toUpperCase(), args, origem: opts.origem ?? "CANVAS", versaoBase, autorId: opts.autorId, criadoEm: new Date().toISOString() };
    const pavimentoId = (args["pav"] as string) ?? opts.pavimentoId ?? this.pavimentosOrdenados()[0]!.id;

    const antes = this.estado();
    const rascunho = new ModeloBim(antes);
    const diff = rascunho.executar(comando, pavimentoId);
    const relatorio = auditar(rascunho, diff, comando, this.opcoesAuditoria);
    if (!relatorio.aprovado) {
      const motivos = [...relatorio.laco1.violacoes, ...relatorio.laco2.violacoes].map((x) => x.mensagem).join(" ");
      throw new ErroComandoVI("REPROVADO", `Comando reprovado pelo auditor. ${motivos}`.trim(), relatorio);
    }

    this.pilhaDesfazer.push({ estado: antes, comando });
    this.pilhaRefazer = [];
    this.pavimentos = rascunho.pavimentos; this.eixos = rascunho.eixos; this.elementos = rascunho.elementos;
    this.versao = versaoBase + 1;
    diff.versaoNova = this.versao;
    this.historico.push(comando);
    return { diff, relatorio, comando, vi: formatarVI(comando.codigo, comando.args) };
  }

  desfazer(): DiffModelo | null {
    const snap = this.pilhaDesfazer.pop();
    if (!snap) return null;
    this.pilhaRefazer.push({ estado: this.estado(), comando: snap.comando });
    return this.restaurar(snap.estado);
  }

  refazer(): DiffModelo | null {
    const snap = this.pilhaRefazer.pop();
    if (!snap) return null;
    this.pilhaDesfazer.push({ estado: this.estado(), comando: snap.comando });
    return this.restaurar(snap.estado);
  }

  private restaurar(estado: EstadoModelo): DiffModelo {
    const antesIds = new Set(this.elementos.map((e) => e.id));
    const novo = structuredClone(estado);
    const depoisIds = new Set(novo.elementos.map((e) => e.id));
    const diff: DiffModelo = {
      versaoNova: this.versao + 1,
      criados: novo.elementos.filter((e) => !antesIds.has(e.id)),
      alterados: novo.elementos.filter((e) => antesIds.has(e.id)),
      removidos: [...antesIds].filter((id) => !depoisIds.has(id)),
      pavimentosCriados: novo.pavimentos, eixosCriados: novo.eixos,
    };
    this.pavimentos = novo.pavimentos; this.eixos = novo.eixos; this.elementos = novo.elementos;
    this.versao += 1;
    return diff;
  }

  // ------------------------------------------------------------------ execução das funções da Tabela B
  private executar(cmd: ComandoVI, pavimentoId: string): DiffModelo {
    const diff: DiffModelo = { versaoNova: this.versao + 1, criados: [], alterados: [], removidos: [] };
    // mat: código do catálogo ou legado (MAT_CONC_FCK30 → MAT.CONCRETO.C30) — igual ao ModeloBim Java
    const a = typeof cmd.args["mat"] === "string" ? { ...cmd.args, mat: normalizarMaterialId(cmd.args["mat"] as string) } : cmd.args;
    const base = (tipo: Elemento["tipo"], camada: Elemento["camada"], extra: Record<string, unknown> = {}) => ({
      id: novoId(), tipo, pavimentoId, camada, origem: cmd.origem, ...(cmd.origem === "IA" ? { camada: "ia-rascunho" as const } : {}), ...extra,
    });
    const pt = (v: unknown, nome: string): Ponto2D => {
      if (v && typeof v === "object" && "x" in (v as object)) return arredondarPonto(v as Ponto2D);
      if (Array.isArray(v) && v.length === 2) return arredondarPonto({ x: Number(v[0]), y: Number(v[1]) });
      throw new ErroComandoVI("VI-002", `Argumento obrigatório "${nome}" ausente ou não é ponto.`);
    };
    const n = (v: unknown, padrao?: number, nome = ""): number => {
      if (v === undefined || v === null || v === "") { if (padrao !== undefined) return padrao; throw new ErroComandoVI("VI-002", `Argumento obrigatório "${nome}" ausente.`); }
      const x = typeof v === "number" ? v : Number(String(v).replace(",", "."));
      if (Number.isNaN(x)) throw new ErroComandoVI("VI-003", `Argumento "${nome}" não é número.`);
      return x;
    };
    const sel = (): Elemento[] => {
      const ids = (a["sel"] as string[] | undefined) ?? [];
      return ids.map((id) => this.elemento(id)).filter((e): e is Elemento => !!e);
    };
    const criar = (e: Elemento) => { this.elementos.push(e); diff.criados.push(e); return e; };
    const alterar = (e: Elemento) => { diff.alterados.push(e); return e; };

    switch (cmd.codigo) {
      case "PAREDE": {
        criar(base("PAREDE", "arquitetura", { p1: pt(a["p1"], "p1"), p2: pt(a["p2"], "p2"), espessura: n(a["esp"], 0.15), altura: n(a["h"], 2.8), materialId: (a["mat"] as string) ?? "MAT.ALVENARIA.CERAMICO_VEDACAO_14", externa: a["externa"] !== false }) as Parede);
        break;
      }
      case "PILAR": {
        const pav = this.pavimento(pavimentoId)!;
        const topo = this.pavimentoAcima(pav.id);
        criar(base("PILAR", "estrutura", {
          centro: pt(a["centro"] ?? a["p"], "centro"), b: n(a["b"], 0.19), h: n(a["h"], 0.4), secao: a["secao"] === "CIRCULAR" ? "CIRCULAR" : "RETANGULAR",
          materialId: (a["mat"] as string) ?? "MAT.CONCRETO.C30", nivelBaseId: pav.id, nivelTopoId: topo?.id ?? pav.id,
          baseOffset: n(a["baseOffset"], 0), topoOffset: n(a["topoOffset"], 0), continuo: a["continuo"] === true, rotacao: n(a["rot"], 0),
        }) as Pilar);
        break;
      }
      case "VIGA": {
        criar(base("VIGA", "estrutura", { p1: pt(a["p1"], "p1"), p2: pt(a["p2"], "p2"), b: n(a["b"], 0.12), h: n(a["h"], 0.4), materialId: (a["mat"] as string) ?? "MAT.CONCRETO.C30" }) as Viga);
        break;
      }
      case "CONTRAVENTAMENTO": {
        criar(base("CONTRAVENTAMENTO", "estrutura", { p1: pt(a["p1"], "p1"), p2: pt(a["p2"], "p2"), perfil: (a["perfil"] as string) ?? "L 50x5", materialId: (a["mat"] as string) ?? "MAT.ACO_ESTRUTURAL.A572_G50" }) as Elemento);
        break;
      }
      case "LAJE": case "REGIAO": {
        const contorno = ((a["contorno"] ?? a["pontos"]) as unknown[] | undefined)?.map((p, i) => pt(p, `contorno[${i}]`));
        if (!contorno || contorno.length < 3) throw new ErroComandoVI("VI-002", "Laje precisa de contorno com 3+ vértices.");
        criar(base("LAJE", "estrutura", { contorno, h: n(a["h"], 0.12), materialId: (a["mat"] as string) ?? "MAT.CONCRETO.C30" }) as Laje);
        break;
      }
      case "PORTA": case "JANELA": {
        const parede = this.encontrarParede(a["parede"] as string | undefined, a["pos"] as Ponto2D | undefined, pavimentoId);
        if (!parede) throw new ErroComandoVI("VI-002", "Abertura precisa de uma parede hospedeira (clique sobre a parede).");
        const posicao = typeof a["posicao"] === "number" ? (a["posicao"] as number) : projetarNoSegmento(pt(a["pos"], "pos"), parede.p1, parede.p2).t;
        const porta = cmd.codigo === "PORTA";
        criar(base(cmd.codigo, "arquitetura", { paredeId: parede.id, posicao, largura: n(a["larg"], porta ? 0.8 : 1.2), altura: n(a["alt"], porta ? 2.1 : 1.2), peitoril: porta ? 0 : n(a["peitoril"], 1.0), materialId: porta ? "MAT.COMPONENTE.PORTA_MADEIRA_80x210" : "MAT.COMPONENTE.VIDRO_TEMPERADO_8" }) as Abertura);
        break;
      }
      case "ESCADA": case "RAMPA": {
        const p1 = pt(a["p1"], "p1"), p2 = pt(a["p2"], "p2");
        if (dist(p1, p2) < 0.3) throw new ErroComandoVI("VI-002", `${cmd.codigo === "ESCADA" ? "Escada" : "Rampa"} precisa de pelo menos 0,30 m entre o pé e o topo.`);
        const pav = this.pavimento(pavimentoId)!; const acima = this.pavimentoAcima(pavimentoId);
        if (a["desnivel"] === undefined && !acima) throw new ErroComandoVI("VI-002", "Informe o desnível: não há pavimento acima para chegar.");
        const desnivel = arr3(n(a["desnivel"], acima ? acima.elevacao - pav.elevacao : 0, "desnivel"));
        const largura = n(a["larg"], cmd.codigo === "ESCADA" ? 1.0 : 1.2, "larg");
        if (!(desnivel > 0) || !(largura > 0)) throw new ErroComandoVI("VI-002", "Desnível e largura precisam ser positivos.");
        const extra: Record<string, unknown> = { p1, p2, largura, desnivel };
        if (cmd.codigo === "ESCADA") { const g = Math.round(n(a["degraus"], Math.max(1, Math.round(desnivel / ESPELHO_ADOTADO)), "degraus")); if (g < 1) throw new ErroComandoVI("VI-002", "A escada precisa de ao menos 1 degrau."); extra["degraus"] = g; }
        criar(base(cmd.codigo, "arquitetura", extra) as Circulacao);
        break;
      }
      case "AMBIENTE": criar(base("AMBIENTE", "arquitetura", { ponto: pt(a["ponto"], "ponto"), nome: (a["nome"] as string) ?? "Ambiente" }) as Elemento); break;
      case "LINHA": case "PLANO_REF": case "LINHA_DETALHE":
        criar(base(cmd.codigo === "PLANO_REF" ? "PLANO_REF" : "LINHA", cmd.codigo === "LINHA_DETALHE" ? "anotacao" : "desenho", { p1: pt(a["p1"], "p1"), p2: pt(a["p2"], "p2") }) as Elemento); break;
      case "RAIO_CONSTRUCAO": case "LINHA_CONSTRUCAO": {
        const p = pt(a["p"] ?? a["origem"], "p"); const d = pt(a["direcao"], "direcao");
        const v = sub(d, p); const l = Math.hypot(v.x, v.y) || 1; const k = 1000 / l;
        criar(base("LINHA", "desenho", { p1: cmd.codigo === "RAIO_CONSTRUCAO" ? p : { x: p.x - v.x * k, y: p.y - v.y * k }, p2: { x: p.x + v.x * k, y: p.y + v.y * k }, rotulo: "construcao" }) as Elemento);
        break;
      }
      case "POLILINHA": case "SPLINE": {
        const pontos = (a["pontos"] as unknown[]).map((p, i) => pt(p, `pontos[${i}]`));
        if (pontos.length < 2) throw new ErroComandoVI("VI-002", "Polilinha precisa de 2+ pontos.");
        criar(base("POLILINHA", "desenho", { pontos, fechada: a["fechar"] === true || a["fechada"] === true }) as Elemento);
        break;
      }
      case "CIRCULO": criar(base("CIRCULO", "desenho", { centro: pt(a["centro"], "centro"), raio: n(a["raio"], 1, "raio") }) as Elemento); break;
      case "ANEL": criar(base("CIRCULO", "desenho", { centro: pt(a["centro"], "centro"), raio: n(a["re"], 0.3), raioInterno: n(a["ri"], 0.2), rotulo: "anel" }) as Elemento); break;
      case "RETANGULO": criar(base("RETANGULO", "desenho", { p1: pt(a["p1"], "p1"), p2: pt(a["p2"], "p2") }) as Elemento); break;
      case "ARCO": criar(base("ARCO", "desenho", { p1: pt(a["p1"], "p1"), p2: pt(a["p2"], "p2"), p3: pt(a["p3"], "p3") }) as Elemento); break;
      case "POLIGONO": {
        const c = pt(a["centro"], "centro"); const nl = Math.max(3, Math.round(n(a["n"], 6))); const r = n(a["raio"], 1);
        const pontos = Array.from({ length: nl }, (_, i) => arredondarPonto({ x: c.x + r * Math.cos((2 * Math.PI * i) / nl), y: c.y + r * Math.sin((2 * Math.PI * i) / nl) }));
        criar(base("POLILINHA", "desenho", { pontos, fechada: true }) as Elemento); break;
      }
      case "ELIPSE": {
        const c = pt(a["centro"], "centro"); const ea = n(a["a"], 2), eb = n(a["b"], 1);
        const pontos = Array.from({ length: 36 }, (_, i) => arredondarPonto({ x: c.x + ea * Math.cos((Math.PI * i) / 18), y: c.y + eb * Math.sin((Math.PI * i) / 18) }));
        criar(base("POLILINHA", "desenho", { pontos, fechada: true }) as Elemento); break;
      }
      case "PONTO": criar(base("PONTO", "desenho", { p: pt(a["p"], "p"), rotulo: (a["rotulo"] as string) || undefined }) as Elemento); break;
      case "EIXO": {
        const p1 = pt(a["p1"], "p1"), p2 = pt(a["p2"], "p2");
        const direcao: Eixo["direcao"] = Math.abs(p2.x - p1.x) < Math.abs(p2.y - p1.y) ? "X" : "Y";
        const eixo: Eixo = { id: novoId("eixo"), nome: (a["nome"] as string) || this.proximoNomeEixo(direcao), direcao, posicao: direcao === "X" ? p1.x : p1.y, inicio: direcao === "X" ? Math.min(p1.y, p2.y) : Math.min(p1.x, p2.x), fim: direcao === "X" ? Math.max(p1.y, p2.y) : Math.max(p1.x, p2.x), recuoBolha: 1.0 };
        if (this.eixos.some((e) => e.nome === eixo.nome)) throw new ErroComandoVI("VI-003", `Já existe um eixo chamado ${eixo.nome}.`);
        this.eixos.push(eixo); (diff.eixosCriados ??= []).push(eixo); break;
      }
      case "MALHA": {
        const nx = Math.max(1, Math.round(n(a["nx"], 5))), ny = Math.max(1, Math.round(n(a["ny"], 3)));
        const dxs = Array.isArray(a["x"]) ? (a["x"] as number[]) : Array.from({ length: nx - 1 }, () => n(a["dx"], 5));
        const dys = Array.isArray(a["y"]) ? (a["y"] as number[]) : Array.from({ length: ny - 1 }, () => n(a["dy"], 6));
        if ([...dxs, ...dys].some((d) => d < 0.05)) throw new ErroComandoVI("VI-003", "Espaçamento de eixo deve ser de pelo menos 0,05 m (ShortCurveTolerance).");
        const repetidos = [...dxs.concat([0]).map((_, i) => ["X", nomeColuna(i)]), ...dys.concat([0]).map((_, i) => ["Y", String(i + 1)])]
          .filter(([d, nome]) => this.eixos.some((e) => e.direcao === d && e.nome === nome)).map(([, nome]) => nome);
        if (repetidos.length) throw new ErroComandoVI("VI-003", `O modelo já tem os eixos ${repetidos.join(", ")}. Para acrescentar eixos use EIXO; para refazer a malha, apague a atual primeiro.`);
        const o = a["origem"] ? pt(a["origem"], "origem") : { x: 0, y: 0 };
        const totalX = dxs.reduce((s, d) => s + d, 0), totalY = dys.reduce((s, d) => s + d, 0);
        let x = o.x;
        dxs.concat([0]).forEach((d, i) => { const eixo: Eixo = { id: novoId("eixo"), nome: nomeColuna(i), direcao: "X", posicao: x, inicio: o.y - 1, fim: o.y + totalY + 1, recuoBolha: 1 }; this.eixos.push(eixo); (diff.eixosCriados ??= []).push(eixo); x += d; });
        let y = o.y;
        dys.concat([0]).forEach((d, i) => { const eixo: Eixo = { id: novoId("eixo"), nome: String(i + 1), direcao: "Y", posicao: y, inicio: o.x - 1, fim: o.x + totalX + 1, recuoBolha: 1 }; this.eixos.push(eixo); (diff.eixosCriados ??= []).push(eixo); y += d; });
        break;
      }
      case "PAVIMENTO": {
        const z = n(a["z"] ?? a["elevacao"], undefined, "z");
        if (this.pavimentos.some((p) => Math.abs(p.elevacao - z) < 0.01)) throw new ErroComandoVI("VI-003", `Já existe pavimento na elevação ${z} m.`);
        if (z >= 0 && a["confirmar"] !== true) {
          const acimaDoSolo = this.pavimentos.map((p) => p.elevacao).filter((e) => e >= 0);
          const abaixo = Math.max(...acimaDoSolo.filter((e) => e < z)), acima = Math.min(...acimaDoSolo.filter((e) => e > z));
          for (const v of [abaixo, acima]) {
            if (!Number.isFinite(v)) continue;
            const pe = Math.abs(z - v);
            if (pe < 2.2 - 1e-9 || pe > 12 + 1e-9) throw new ErroComandoVI("VI-003", `Pé-direito de ${pe.toFixed(2).replace(".", ",")} m fora do intervalo 2,20–12,00 m. Se for intencional (mezanino, reservatório), confirme com confirmar=true.`);
          }
        }
        const pav: Pavimento = { id: novoId("pav"), nome: (a["nome"] as string) || `Pavimento ${this.pavimentos.length}`, elevacao: z, tipo: (a["tipo"] as Pavimento["tipo"]) ?? (z < 0 ? "FUNDACAO" : z === 0 ? "TERREO" : "TIPO") };
        this.pavimentos.push(pav); (diff.pavimentosCriados ??= []).push(pav);
        // pilares do pavimento imediatamente abaixo passam a ter este como topo (Disciplina Z, RF17)
        const abaixo = this.pavimentosOrdenados().filter((p) => p.elevacao < z).pop();
        if (abaixo) for (const e of this.elementos) if (e.tipo === "PILAR" && e.nivelBaseId === abaixo.id && e.nivelTopoId === abaixo.id) { e.nivelTopoId = pav.id; alterar(e); }
        break;
      }
      case "SAPATA": case "BLOCO": case "RADIER": case "FUNDACAO": {
        const tipo = (cmd.codigo === "FUNDACAO" ? ((a["tipo"] as string) ?? "SAPATA") : cmd.codigo) as "SAPATA" | "BLOCO" | "RADIER";
        const pilar = sel().find((e) => e.tipo === "PILAR") as Pilar | undefined ?? (a["pilar"] ? (this.elemento(a["pilar"] as string) as Pilar) : undefined);
        const centro = pilar ? pilar.centro : pt(a["centro"], "centro");
        criar(base(tipo, "fundacao", { pilarId: pilar?.id, centro, bx: n(a["bx"], 1.5), by: n(a["by"], 1.5), h: n(a["h"], 0.5), estacas: a["estacas"] ? n(a["estacas"]) : undefined, diametroEstaca: a["diam"] ? n(a["diam"]) : undefined, materialId: (a["mat"] as string) ?? "MAT.CONCRETO.C25" }) as Elemento);
        break;
      }
      case "COTA_LINEAR": case "COTA_ALINHADA": case "COTA_NIVEL":
      {
        const c = base("COTA", "anotacao", { p1: pt(a["p1"] ?? a["p"], "p1"), p2: pt(a["p2"] ?? a["p"], "p2"), afastamento: n(a["afast"], 1), estilo: cmd.codigo === "COTA_LINEAR" ? "LINEAR" : cmd.codigo === "COTA_NIVEL" ? "NIVEL" : "ALINHADA" }) as Cota;
        const a1 = this.ancoraEm(c.p1, pavimentoId), a2 = this.ancoraEm(c.p2, pavimentoId);
        if (a1 || a2) c.ancoras = { ...(a1 ? { p1: a1 } : {}), ...(a2 ? { p2: a2 } : {}) };
        criar(c); break;
      }
      case "TEXTO": criar(base("TEXTO", "anotacao", { p: pt(a["p"], "p"), texto: String(a["texto"] ?? ""), altura: n(a["altura"], 0.25) }) as Elemento); break;
      case "ETIQUETA": {
        for (const e of sel()) criar(base("ETIQUETA", "anotacao", { p: this.pontoRepresentativo(e), texto: e.rotulo ?? this.rotuloAutomatico(e), altura: 0.25 }) as Elemento);
        break;
      }
      case "HACHURA": {
        const alvo = sel()[0];
        const contorno = alvo && "contorno" in alvo ? alvo.contorno : alvo && alvo.tipo === "POLILINHA" ? alvo.pontos : undefined;
        if (!contorno) throw new ErroComandoVI("VI-002", "Hachura precisa de um contorno fechado selecionado.");
        criar(base("HACHURA", "anotacao", { contorno, padrao: (a["padrao"] as string) ?? "CONCRETO" }) as Elemento); break;
      }
      // ------------------------------------------------------------ objetos e conexões prediais (AUD-02/03/04)
      case "OBJETO": {
        const item = itemObjeto(a["item"] as string);
        if (!a["item"]) throw new ErroComandoVI("VI-002", 'Argumento obrigatório "item" ausente (código da biblioteca de objetos).');
        if (!item) throw new ErroComandoVI("VI-003", `Objeto "${String(a["item"])}" não existe na biblioteca.`);
        let dim = { ...item.dim };
        if (item.alturaVariavel && a["altura"] !== undefined) { const k = n(a["altura"], item.dim.a) / item.dim.a; dim = { l: arr3(item.dim.l * k), p: arr3(item.dim.p * k), a: arr3(item.dim.a * k) }; }
        for (const k of ["l", "p", "a"] as const) if (a[k] !== undefined) dim[k] = n(a[k], dim[k], k);
        const pos = pt(a["pos"] ?? a["centro"], "pos");
        const o = base("OBJETO", camadaDoItem(item), { item: item.id, centro: pos, rotacao: arr3(n(a["rot"], 0)), elevacao: item.elevacao ?? 0, dim }) as Objeto;
        if (item.luz) o.ligada = a["ligada"] !== false;
        if (item.montagem === "PAREDE") {
          const w = this.encontrarParede(a["parede"] as string | undefined, pos, pavimentoId, 0.6);
          if (w) {
            const { ponto } = projetarNoSegmento(pos, w.p1, w.p2);
            const u = sub(w.p2, w.p1); const l = Math.hypot(u.x, u.y) || 1;
            let nrm = { x: -u.y / l, y: u.x / l };
            if (nrm.x * (pos.x - ponto.x) + nrm.y * (pos.y - ponto.y) < 0) nrm = { x: -nrm.x, y: -nrm.y };
            o.centro = arredondarPonto(soma(ponto, { x: nrm.x * (w.espessura / 2 + dim.p / 2), y: nrm.y * (w.espessura / 2 + dim.p / 2) }));
            o.rotacao = arr3(((Math.atan2(nrm.y, nrm.x) * 180) / Math.PI - 90 + 360) % 360);
            o.paredeId = w.id;
          }
        }
        if (item.montagem === "TETO") o.elevacao = arr3(tetoDoPavimento(this, pavimentoId) - dim.a);
        if (item.disciplina === "humanos") o.elevacao = elevacaoNoPiso(this, pavimentoId, o.centro); // pessoa sobre escada/rampa
        if (a["elev"] !== undefined) o.elevacao = n(a["elev"], 0, "elev");
        criar(o);
        break;
      }
      case "CONECTAR": {
        const ref = (v: unknown, nome: string): { id: string; conector?: string } => {
          if (typeof v === "string") { const [id, conector] = v.split(":"); return { id: id!, conector }; }
          if (v && typeof v === "object" && "id" in (v as object)) return { id: String((v as { id: unknown }).id), conector: (v as { conector?: string }).conector };
          throw new ErroComandoVI("VI-002", `Argumento obrigatório "${nome}" ausente (objeto e conector, ex.: de="id:E1").`);
        };
        let de = ref(a["de"], "de"), para = ref(a["para"], "para");
        const oDe = this.elemento(de.id), oPara = this.elemento(para.id);
        if (!oDe || oDe.tipo !== "OBJETO" || !oPara || oPara.tipo !== "OBJETO") throw new ErroComandoVI("VI-003", "CONECTAR liga dois objetos da biblioteca (equipamento, louça, luminária, ponto de instalação).");
        const sistemaPedido = a["sistema"] as SistemaPredial | undefined;
        const cons = (o: Objeto) => itemObjeto(o.item)?.conectores ?? [];
        const livre = (o: Objeto, c: ConectorItem) => this.elementos.filter((e) => e.tipo === "CONEXAO" && ((e.de.id === o.id && e.de.conector === c.id) || (e.para.id === o.id && e.para.conector === c.id))).length < (c.maxConexoes ?? 1);
        if (!de.conector || !para.conector) {
          // escolhe automaticamente um par saída → entrada do mesmo sistema, livre, nos dois sentidos
          const pares: [Objeto, ConectorItem, Objeto, ConectorItem][] = [];
          for (const [x, y] of [[oDe, oPara], [oPara, oDe]] as const)
            for (const cx of cons(x)) for (const cy of cons(y))
              if (cx.fluxo === "SAI" && cy.fluxo === "ENTRA" && cx.sistema === cy.sistema && (!sistemaPedido || cx.sistema === sistemaPedido)
                && (!de.conector || (x.id === de.id ? cx.id : cy.id) === de.conector) && livre(x, cx) && livre(y, cy)) pares.push([x, cx, y, cy]);
          if (!pares.length) {
            const nome = (o: Objeto) => itemObjeto(o.item)?.nome ?? o.item;
            const lista = (o: Objeto) => cons(o).map((c) => `${c.id} (${c.sistema.toLowerCase()}, ${c.fluxo === "SAI" ? "saída" : "entrada"})`).join(", ") || "nenhum conector";
            throw new ErroComandoVI("VI-003", `Não há par de conectores compatível entre ${nome(oDe)} [${lista(oDe)}] e ${nome(oPara)} [${lista(oPara)}].`);
          }
          const [x, cx, y, cy] = pares[0]!;
          de = { id: x.id, conector: cx.id }; para = { id: y.id, conector: cy.id };
        } else {
          // conectores explícitos: normaliza para saída → entrada
          const cDe = cons(oDe).find((c) => c.id === de.conector), cPara = cons(oPara).find((c) => c.id === para.conector);
          if (cDe?.fluxo === "ENTRA" && cPara?.fluxo === "SAI") [de, para] = [para, de];
        }
        const cA = cons(this.elemento(de.id) as Objeto).find((c) => c.id === de.conector), cB = cons(this.elemento(para.id) as Objeto).find((c) => c.id === para.conector);
        const sistema = (cA?.sistema ?? cB?.sistema ?? sistemaPedido ?? "ELETRICA") as SistemaPredial;
        const extra: Record<string, unknown> = { sistema, de: { id: de.id, conector: de.conector }, para: { id: para.id, conector: para.conector }, materialId: (a["mat"] as string) ?? MATERIAL_PADRAO[sistema] };
        // cabo de aparelho numa tomada pertence ao circuito que alimenta a tomada (sem isso o aparelho "sairia" do circuito)
        const circuitoDaTomada = cA?.papel === "TOMADA" ? (this.elementos.find((x) => x.tipo === "CONEXAO" && (x as Conexao).para.id === de.id) as Conexao | undefined)?.circuito : undefined;
        if (sistema === "ELETRICA") { extra["circuito"] = (a["circuito"] as string) ?? circuitoDaTomada ?? "C1"; extra["secaoMm2"] = n(a["secao"], 2.5); extra["condutores"] = (a["condutores"] as string[]) ?? ["F", "N", "PE"]; if (a["disjuntor"] !== undefined) extra["disjuntorA"] = n(a["disjuntor"]); }
        else { extra["dnMm"] = n(a["dn"], Math.max(cA?.dnMm ?? 0, cB?.dnMm ?? 0) || 20); if (sistema === "ESGOTO") extra["declividade"] = n(a["declividade"], DECLIVIDADE_PADRAO); }
        criar(base("CONEXAO", sistema === "ELETRICA" ? "eletrica" : "hidrossanitaria", extra) as Conexao);
        break;
      }
      // ------------------------------------------------------------ modificação
      case "MOVER": case "COPIAR": {
        const d = sub(pt(a["destino"], "destino"), pt(a["base"], "base"));
        const selecionados = sel();
        const ids = new Set(selecionados.map((e) => e.id));
        for (const e of selecionados) {
          const alvo = cmd.codigo === "COPIAR" ? (structuredClone(e) as Elemento) : e;
          if (cmd.codigo === "COPIAR") { alvo.id = novoId(); alvo.origem = cmd.origem; if (alvo.tipo === "OBJETO") delete alvo.paredeId; }
          const antes = alvo.tipo === "PAREDE" ? { p1: { ...alvo.p1 }, p2: { ...alvo.p2 } } : null;
          this.transladar(alvo, d);
          if (alvo.tipo === "OBJETO" && itemObjeto(alvo.item)?.disciplina === "humanos") alvo.elevacao = elevacaoNoPiso(this, alvo.pavimentoId, alvo.centro);
          if (cmd.codigo === "COPIAR") criar(alvo); else alterar(alvo);
          if (cmd.codigo === "MOVER" && alvo.tipo === "PAREDE" && antes) this.propagarParede(alvo, antes, d, ids, alterar);
        }
        break;
      }
      case "ROTACIONAR": {
        const b = pt(a["base"], "base"); const ang = n(a["ang"], 90);
        for (const e of sel()) {
          if (e.tipo === "CONEXAO") continue;
          this.mapearPontos(e, (p) => rotacionar(p, b, ang));
          if (e.tipo === "PILAR") e.rotacao = (e.rotacao + ang) % 360;
          if (e.tipo === "OBJETO") e.rotacao = arr3((((e.rotacao + ang) % 360) + 360) % 360);
          alterar(e);
        }
        break;
      }
      case "ESPELHAR": {
        const p1 = pt(a["p1"], "p1"), p2 = pt(a["p2"], "p2");
        for (const e of sel()) {
          const alvo = a["apagar"] === true || a["apagar"] === "S" ? e : (structuredClone(e) as Elemento);
          if (alvo !== e) { alvo.id = novoId(); alvo.origem = cmd.origem; }
          this.mapearPontos(alvo, (p) => espelhar(p, p1, p2));
          if (alvo.tipo === "OBJETO") { const ang = (Math.atan2(p2.y - p1.y, p2.x - p1.x) * 180) / Math.PI; alvo.rotacao = arr3((((2 * ang - alvo.rotacao - 180) % 360) + 360) % 360); if (alvo !== e) delete alvo.paredeId; }
          if (alvo === e) alterar(e); else criar(alvo);
        }
        break;
      }
      case "ESCALAR": {
        const b = pt(a["base"], "base"); const k = n(a["fator"], 2);
        for (const e of sel()) {
          if (["PAREDE", "PILAR", "VIGA", "LAJE", "PORTA", "JANELA", "SAPATA", "BLOCO", "RADIER"].includes(e.tipo)) throw new ErroComandoVI("VI-003", `${e.tipo} não pode ser escalado: use as propriedades.`);
          if (e.tipo === "OBJETO") {
            const it = itemObjeto(e.item);
            if (it?.alturaVariavel) throw new ErroComandoVI("VI-003", `${it.nome}: mude a altura nas propriedades (a escala da pessoa é uniforme).`);
            if (!it?.redimensionavel) throw new ErroComandoVI("VI-003", `${it?.nome ?? e.item} tem dimensões de produto e não pode ser escalado.`);
            e.dim = { l: arr3(e.dim.l * k), p: arr3(e.dim.p * k), a: arr3(e.dim.a * k) };
          }
          this.mapearPontos(e, (p) => soma(b, { x: (p.x - b.x) * k, y: (p.y - b.y) * k })); if (e.tipo === "CIRCULO") e.raio *= k; alterar(e); }
        break;
      }
      case "DESLOCAR": {
        const dist_ = n(a["dist"], 1); const lado = pt(a["lado"], "lado");
        for (const e of sel()) {
          if (!("p1" in e && "p2" in e)) continue;
          const v = sub(e.p2, e.p1); const l = Math.hypot(v.x, v.y) || 1; const nrm = { x: -v.y / l, y: v.x / l };
          const sinal = Math.sign(nrm.x * (lado.x - e.p1.x) + nrm.y * (lado.y - e.p1.y)) || 1;
          const copia = structuredClone(e) as Elemento; copia.id = novoId(); copia.origem = cmd.origem;
          this.transladar(copia, { x: nrm.x * dist_ * sinal, y: nrm.y * dist_ * sinal }); criar(copia);
        }
        break;
      }
      case "ESTICAR": {
        const d = sub(pt(a["destino"], "destino"), pt(a["base"], "base"));
        const janela = a["janela"] as { min: Ponto2D; max: Ponto2D } | undefined;
        const dentro = (p: Ponto2D) => !janela || (p.x >= janela.min.x && p.x <= janela.max.x && p.y >= janela.min.y && p.y <= janela.max.y);
        for (const e of sel()) { if (e.tipo === "PILAR") this.transladar(e, d); else this.mapearPontos(e, (p) => (dentro(p) ? soma(p, d) : p)); alterar(e); }
        break;
      }
      case "ALONGAR": { const delta = n(a["delta"], 1); for (const e of sel()) { if ("p1" in e && "p2" in e) { const v = sub(e.p2, e.p1); const l = Math.hypot(v.x, v.y) || 1; e.p2 = arredondarPonto(soma(e.p2, { x: (v.x / l) * delta, y: (v.y / l) * delta })); alterar(e); } } break; }
      case "MATRIZ": {
        const nx = Math.max(1, Math.round(n(a["nx"], 3))), ny = Math.max(1, Math.round(n(a["ny"], 1))); const dx = n(a["dx"], 5), dy = n(a["dy"], 6);
        for (const e of sel()) for (let i = 0; i < nx; i++) for (let j = 0; j < ny; j++) { if (i === 0 && j === 0) continue; const c = structuredClone(e) as Elemento; c.id = novoId(); c.origem = cmd.origem; this.transladar(c, { x: i * dx, y: j * dy }); criar(c); }
        break;
      }
      case "APAGAR": {
        const ids = new Set(sel().map((e) => e.id));
        // hospedados (aberturas e objetos de parede) saem com a parede; conexões saem com qualquer extremidade
        for (const e of this.elementos) if ((e.tipo === "PORTA" || e.tipo === "JANELA") && ids.has(e.paredeId)) ids.add(e.id);
        for (const e of this.elementos) if (e.tipo === "OBJETO" && e.paredeId && ids.has(e.paredeId)) ids.add(e.id);
        for (const e of this.elementos) if (e.tipo === "CONEXAO" && (ids.has(e.de.id) || ids.has(e.para.id))) ids.add(e.id);
        this.elementos = this.elementos.filter((e) => !ids.has(e.id)); diff.removidos.push(...ids); break;
      }
      case "EXPLODIR": {
        for (const e of sel()) {
          if (e.tipo !== "POLILINHA" && e.tipo !== "RETANGULO") throw new ErroComandoVI("VI-003", `${e.tipo} não explode.`);
          const pts = e.tipo === "RETANGULO" ? [e.p1, { x: e.p2.x, y: e.p1.y }, e.p2, { x: e.p1.x, y: e.p2.y }] : e.pontos;
          const fechada = e.tipo === "RETANGULO" || e.fechada;
          for (let i = 0; i < pts.length - (fechada ? 0 : 1); i++) criar(base("LINHA", "desenho", { p1: pts[i]!, p2: pts[(i + 1) % pts.length]! }) as Elemento);
          this.elementos = this.elementos.filter((x) => x.id !== e.id); diff.removidos.push(e.id);
        }
        break;
      }
      case "UNIR": {
        const linhas = sel().filter((e) => e.tipo === "LINHA") as Extract<Elemento, { tipo: "LINHA" | "PLANO_REF" }>[];
        if (linhas.length < 2) throw new ErroComandoVI("VI-002", "Selecione 2+ linhas para unir.");
        const pontos: Ponto2D[] = [linhas[0]!.p1, linhas[0]!.p2];
        const resto = linhas.slice(1);
        while (resto.length) {
          const fim = pontos[pontos.length - 1]!;
          const i = resto.findIndex((l) => dist(l.p1, fim) < 0.001 || dist(l.p2, fim) < 0.001);
          if (i < 0) throw new ErroComandoVI("VI-003", "As linhas não são contínuas.");
          const l = resto.splice(i, 1)[0]!; pontos.push(dist(l.p1, fim) < 0.001 ? l.p2 : l.p1);
        }
        const ids = new Set(linhas.map((l) => l.id)); this.elementos = this.elementos.filter((e) => !ids.has(e.id)); diff.removidos.push(...ids);
        criar(base("POLILINHA", "desenho", { pontos, fechada: dist(pontos[0]!, pontos[pontos.length - 1]!) < 0.001 }) as Elemento); break;
      }
      case "ALTERAR": {
        const e = this.elemento(a["id"] as string);
        if (!e) throw new ErroComandoVI("VI-002", "Elemento não encontrado.");
        const props = (a["props"] as Record<string, unknown>) ?? {};
        for (const [k, v] of Object.entries(props)) {
          if (k === "id" || k === "tipo") continue;
          // como no Java: "mat" e "materialId" gravam o materialId (normalizado)
          if (k === "mat" || k === "materialId") (e as unknown as Record<string, unknown>)["materialId"] = normalizarMaterialId(String(v));
          else if (k === "altura" && e.tipo === "OBJETO") { const kk = Number(v) / e.dim.a; e.dim = { l: arr3(e.dim.l * kk), p: arr3(e.dim.p * kk), a: arr3(Number(v)) }; }
          else (e as unknown as Record<string, unknown>)[k] = v;
        }
        alterar(e); break;
      }
      case "APARAR": case "ESTENDER": {
        const arestas = ((a["arestas"] as string[]) ?? []).map((id) => this.elemento(id)).filter((e): e is Elemento => !!e && "p1" in e && "p2" in e);
        for (const e of sel()) {
          if (!("p1" in e && "p2" in e)) continue;
          const clique = a["clique"] ? pt(a["clique"], "clique") : e.p2;
          const { intersecaoSegmentos } = require_geometria();
          let melhor: { p: Ponto2D; t: number } | null = null;
          for (const ar of arestas) {
            if (!("p1" in ar && "p2" in ar)) continue;
            const ip = intersecaoSegmentos(e.p1, e.p2, ar.p1, ar.p2, cmd.codigo === "ESTENDER");
            if (!ip) continue;
            const t = projetarNoSegmento(ip, e.p1, e.p2).t;
            if (!melhor || Math.abs(t - 0.5) < Math.abs(melhor.t - 0.5)) melhor = { p: ip, t };
          }
          if (!melhor) continue;
          if (cmd.codigo === "APARAR") { if (dist(clique, e.p1) < dist(clique, e.p2)) e.p1 = arredondarPonto(melhor.p); else e.p2 = arredondarPonto(melhor.p); }
          else { if (dist(clique, e.p1) < dist(clique, e.p2)) e.p1 = arredondarPonto(melhor.p); else e.p2 = arredondarPonto(melhor.p); }
          alterar(e);
        }
        break;
      }
      default:
        throw new ErroComandoVI("VI-001", `Função "${cmd.codigo}" não existe na Tabela B.`);
    }
    this.atualizarCotasAssociadas(diff);
    return diff;
  }

  /** extremidade (p1/p2) ou centro de um elemento do pavimento coincidente com o ponto (1 mm): âncora da cota */
  private ancoraEm(p: Ponto2D, pavimentoId: string): AncoraCota | undefined {
    for (const e of this.elementosDo(pavimentoId)) {
      if (e.tipo === "PAREDE" || e.tipo === "VIGA" || e.tipo === "LINHA" || e.tipo === "PLANO_REF" || e.tipo === "ESCADA" || e.tipo === "RAMPA") {
        if (dist(e.p1, p) <= 1e-3) return { id: e.id, ponto: "p1" };
        if (dist(e.p2, p) <= 1e-3) return { id: e.id, ponto: "p2" };
      }
      if (e.tipo === "PILAR" && dist(e.centro, p) <= 1e-3) return { id: e.id, ponto: "centro" };
    }
    return undefined;
  }

  /** cotas ancoradas seguem os elementos alterados; âncora de elemento apagado é descartada (mesma regra no Java) */
  private atualizarCotasAssociadas(diff: DiffModelo) {
    const mudou = new Set([...diff.alterados.map((e) => e.id), ...diff.removidos]);
    if (!mudou.size) return;
    for (const c of this.elementos) {
      if (c.tipo !== "COTA" || !c.ancoras) continue;
      let alterou = false;
      for (const lado of ["p1", "p2"] as const) {
        const an = c.ancoras[lado]; if (!an || !mudou.has(an.id)) continue;
        const ref = this.elemento(an.id) as (Elemento & Partial<Record<"p1" | "p2" | "centro", Ponto2D>>) | undefined;
        const p = ref?.[an.ponto];
        if (!p) { delete c.ancoras[lado]; alterou = true; continue; }
        if (dist(p, c[lado]) > 1e-9) { c[lado] = { ...p }; alterou = true; }
      }
      if (!c.ancoras.p1 && !c.ancoras.p2) delete c.ancoras;
      if (alterou && !diff.alterados.includes(c)) diff.alterados.push(c);
    }
  }

  // ------------------------------------------------------------------ utilitários
  private transladar(e: Elemento, d: Ponto2D) { this.mapearPontos(e, (p) => soma(p, d)); }

  mapearPontos(e: Elemento, f: (p: Ponto2D) => Ponto2D) {
    const m = (p: Ponto2D) => arredondarPonto(f(p));
    switch (e.tipo) {
      case "PAREDE": case "VIGA": case "LINHA": case "PLANO_REF": case "RETANGULO": case "CONTRAVENTAMENTO": case "COTA": case "ESCADA": case "RAMPA": e.p1 = m(e.p1); e.p2 = m(e.p2); break;
      case "ARCO": e.p1 = m(e.p1); e.p2 = m(e.p2); e.p3 = m(e.p3); break;
      case "PILAR": case "CIRCULO": case "SAPATA": case "BLOCO": case "RADIER": case "ESTACA": case "OBJETO": e.centro = m(e.centro); break;
      case "LAJE": case "HACHURA": e.contorno = e.contorno.map(m); break;
      case "POLILINHA": e.pontos = e.pontos.map(m); break;
      case "AMBIENTE": e.ponto = m(e.ponto); break;
      case "PONTO": e.p = m(e.p); break;
      case "TEXTO": case "ETIQUETA": e.p = m(e.p); break;
      case "PORTA": case "JANELA": break; // seguem a parede hospedeira
    }
  }

  /**
   * AUD-01 · parede deslocada leva junto o que depende dela (mesma regra no Java):
   *  - paredes não paralelas cuja extremidade estava sobre o eixo dela: a extremidade vai para a interseção com o eixo novo;
   *  - portas e janelas dessas paredes mantêm a distância à extremidade que não se moveu;
   *  - cotas com ponto sobre o eixo da parede (ou o prolongamento até 2 m): o ponto acompanha o deslocamento perpendicular;
   *  - objetos fixados nela (paredeId) acompanham o deslocamento.
   */
  private propagarParede(w: Parede, antes: { p1: Ponto2D; p2: Ponto2D }, d: Ponto2D, movidos: Set<string>, alterar: (e: Elemento) => Elemento) {
    const u = sub(antes.p2, antes.p1); const L = Math.hypot(u.x, u.y); if (L < 1e-9) return;
    const ux = u.x / L, uy = u.y / L;
    const dn = { x: d.x - (d.x * ux + d.y * uy) * ux, y: d.y - (d.x * ux + d.y * uy) * uy };
    const naLinha = (p: Ponto2D, folgaPonta: number) => {
      const t = (p.x - antes.p1.x) * ux + (p.y - antes.p1.y) * uy;
      const dist_ = Math.abs((p.x - antes.p1.x) * uy - (p.y - antes.p1.y) * ux);
      return dist_ <= 0.005 && t >= -folgaPonta - 1e-9 && t <= L + folgaPonta + 1e-9;
    };
    for (const o of this.elementosDo(w.pavimentoId)) {
      if (movidos.has(o.id) || o.id === w.id) continue;
      if (o.tipo === "PAREDE") {
        const v = sub(o.p2, o.p1); const lo = Math.hypot(v.x, v.y); if (lo < 1e-9) continue;
        if (Math.abs((v.x / lo) * uy - (v.y / lo) * ux) < Math.sin((5 * Math.PI) / 180)) continue; // paralela: não arrasta
        let mudou = false; const antesO = { p1: { ...o.p1 }, p2: { ...o.p2 } };
        for (const k of ["p1", "p2"] as const) {
          if (!naLinha(o[k], 0)) continue;
          const ip = geo.intersecaoSegmentos(o.p1, o.p2, w.p1, w.p2, true);
          if (ip) { o[k] = arredondarPonto(ip); mudou = true; }
        }
        if (!mudou) continue;
        alterar(o);
        const novoL = dist(o.p1, o.p2), velhoL = dist(antesO.p1, antesO.p2);
        const p1Fixo = dist(o.p1, antesO.p1) < 1e-9;
        for (const ab of this.elementos) if ((ab.tipo === "PORTA" || ab.tipo === "JANELA") && ab.paredeId === o.id) {
          const s_ = ab.posicao * velhoL; const novoS = p1Fixo ? s_ : novoL - (velhoL - s_);
          ab.posicao = novoS / novoL; alterar(ab);
        }
      }
      if (o.tipo === "COTA") {
        let mudou = false;
        for (const k of ["p1", "p2"] as const) if (naLinha(o[k], 2)) { (o as Cota)[k] = arredondarPonto(soma(o[k], dn)); mudou = true; }
        if (mudou) alterar(o);
      }
      if (o.tipo === "OBJETO" && o.paredeId === w.id) { o.centro = arredondarPonto(soma(o.centro, d)); alterar(o); }
    }
  }

  pontoRepresentativo(e: Elemento): Ponto2D {
    if ("centro" in e) return e.centro;
    if ("p1" in e && "p2" in e) return { x: (e.p1.x + e.p2.x) / 2, y: (e.p1.y + e.p2.y) / 2 };
    if ("contorno" in e) return e.contorno[0]!;
    if ("pontos" in e) return e.pontos[0]!;
    if ("ponto" in e) return e.ponto;
    if ("p" in e) return e.p;
    if (e.tipo === "PORTA" || e.tipo === "JANELA") { const w = this.elemento(e.paredeId) as Parede | undefined; return w ? soma(w.p1, { x: (w.p2.x - w.p1.x) * e.posicao, y: (w.p2.y - w.p1.y) * e.posicao }) : { x: 0, y: 0 }; }
    return { x: 0, y: 0 };
  }

  rotuloAutomatico(e: Elemento): string {
    const prefixo: Partial<Record<Elemento["tipo"], string>> = { PILAR: "P", VIGA: "V", LAJE: "L", PAREDE: "PAR", PORTA: "PT", JANELA: "J", SAPATA: "S", BLOCO: "B", RADIER: "R", OBJETO: "OB", CONEXAO: "CX" };
    const p = prefixo[e.tipo] ?? e.tipo.slice(0, 2);
    const idx = this.elementos.filter((x) => x.tipo === e.tipo && x.pavimentoId === e.pavimentoId).findIndex((x) => x.id === e.id) + 1;
    return `${p}${idx}`;
  }

  private encontrarParede(id: string | undefined, pos: Ponto2D | undefined, pavimentoId: string, alcance = 0.15): Parede | undefined {
    if (id) return this.elemento(id) as Parede | undefined;
    if (!pos) return undefined;
    let melhor: { w: Parede; d: number } | undefined;
    for (const e of this.elementosDo(pavimentoId)) if (e.tipo === "PAREDE") {
      const { ponto } = projetarNoSegmento(pos, e.p1, e.p2); const d = dist(ponto, pos);
      if (d <= e.espessura + alcance && (!melhor || d < melhor.d)) melhor = { w: e, d };
    }
    return melhor?.w;
  }

  private proximoNomeEixo(direcao: Eixo["direcao"]): string {
    const usados = this.eixos.filter((e) => e.direcao === direcao).length;
    return direcao === "X" ? nomeColuna(usados) : String(usados + 1);
  }

  /** interseções de eixos (snap EIX) */
  intersecoesDeEixos(): { nome: string; p: Ponto2D }[] {
    const xs = this.eixos.filter((e) => e.direcao === "X"), ys = this.eixos.filter((e) => e.direcao === "Y");
    const out: { nome: string; p: Ponto2D }[] = [];
    for (const ex of xs) for (const ey of ys) out.push({ nome: `${ex.nome}/${ey.nome}`, p: { x: ex.posicao, y: ey.posicao } });
    return out;
  }
}

const arr3 = (v: number) => Math.round(v * 1000) / 1000;

// importação tardia para evitar ciclo em bundlers que não fazem hoisting de ESM
import * as geo from "./geometria";
function require_geometria() { return geo; }
