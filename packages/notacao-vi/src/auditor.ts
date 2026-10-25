/**
 * Auditor determinístico (RF16 loop duplo, RF06 Caixa Cartesiana, RF08 tolerâncias e seções mínimas,
 * RF17 Disciplina Z). Roda sobre o modelo rascunho ANTES de persistir.
 *  - Laço 1 (código): gramática/args/Tabela C → corrige o corrigível, reprova o resto.
 *  - Laço 2 (geometria, versão leve sem kernel): auto-interseção, interpenetração pilar×pilar, aberturas
 *    cabendo na face, envoltória (AVISO na edição incremental; VIOLAÇÃO só nas verificações de conclusão).
 */
import type { Correcao, DiffModelo, Elemento, Pilar, RelatorioAuditoria, Violacao, ComandoVI, Parede, Ponto2D } from "./tipos";
import { material } from "./tabela-a";
import { caixa, dist, intersecaoSegmentos, temAutoIntersecao } from "./geometria";
import type { ModeloBim } from "./modelo";

export type OpcoesAuditoria = {
  shortCurveTolerance?: number; // m, padrão 0,05
  /** verificação de conclusão: envoltória aberta reprova (análise, prancha, exportação, EM_ANDAMENTO) */
  conclusao?: boolean;
  politicaForaDaCaixa?: "RECORTAR" | "BLOQUEAR";
};

const SECOES_MINIMAS = { pilarLado: 0.19, pilarArea: 0.036, vigaB: 0.12, lajeH: 0.08, paredeEsp: 0.05 };

export function auditar(modelo: ModeloBim, diff: DiffModelo, comando: ComandoVI, opts: OpcoesAuditoria = {}): RelatorioAuditoria {
  const tol = opts.shortCurveTolerance ?? 0.05;
  const violacoes1: Violacao[] = [];
  const violacoes2: Violacao[] = [];
  const avisos: Violacao[] = [];
  const correcoes: Correcao[] = [];
  const novos = [...diff.criados, ...diff.alterados];
  const v = (codigo: string, regra: string, mensagem: string, e?: Elemento, extra: Partial<Violacao> = {}): Violacao => ({ codigo, severidade: "VIOLACAO", regra, mensagem, elementoRef: e?.id, ...extra });

  // ---------------- Laço 1 · Tabela C
  for (const e of novos) {
    const mat = e.materialId ? material(e.materialId) : undefined;
    if (e.materialId && !mat) violacoes1.push(v("VI-003", "Tabela A", `Material ${e.materialId} não existe na Tabela A.`, e));
    if ("p1" in e && "p2" in e) {
      const l = dist(e.p1, e.p2);
      if (l < 1e-9) violacoes1.push(v("C-VETOR_NULO", "ShortCurveTolerance", `${e.tipo} com comprimento nulo descartado antes do kernel.`, e, { esperado: `≥ ${tol} m`, obtido: "0 m" }));
      else if (l < tol) violacoes1.push(v("C-SHORT_CURVE", "ShortCurveTolerance", `${e.tipo} de ${l.toFixed(3)} m é menor que a tolerância de ${tol} m.`, e, { esperado: `≥ ${tol} m`, obtido: `${l.toFixed(3)} m` }));
    }
    if (e.tipo === "PILAR") {
      if (Math.min(e.b, e.h) < SECOES_MINIMAS.pilarLado) violacoes1.push(v("C-SECAO_MIN_PILAR", "NBR 6118 13.2.3", `Pilar ${e.b * 100}x${e.h * 100} cm: menor lado deve ser ≥ 19 cm.`, e, { esperado: "≥ 0,19 m", obtido: `${Math.min(e.b, e.h)} m` }));
      if (e.b * e.h < SECOES_MINIMAS.pilarArea) violacoes1.push(v("C-AREA_MIN_PILAR", "NBR 6118 13.2.3", `Área da seção do pilar deve ser ≥ 360 cm².`, e, { esperado: "≥ 360 cm²", obtido: `${(e.b * e.h * 1e4).toFixed(0)} cm²` }));
      // Disciplina Z (RF17): base e topo presos aos níveis, offsets zerados
      if (e.baseOffset !== 0) { correcoes.push({ regra: "RF17 Disciplina Z", elementoRef: e.id, campo: "baseOffset", antes: e.baseOffset, depois: 0 }); e.baseOffset = 0; }
      if (e.topoOffset !== 0) { correcoes.push({ regra: "RF17 Disciplina Z", elementoRef: e.id, campo: "topoOffset", antes: e.topoOffset, depois: 0 }); e.topoOffset = 0; }
      const base = modelo.pavimento(e.nivelBaseId), topo = modelo.pavimento(e.nivelTopoId);
      if (base && topo && topo.elevacao < base.elevacao) violacoes1.push(v("Z-TOPO_ABAIXO_BASE", "RF17 Disciplina Z", "Topo do pilar abaixo da base.", e));
      if (base && topo && base.id !== topo.id && modelo.pavimentoAcima(base.id)?.id !== topo.id && !e.continuo) {
        const acima = modelo.pavimentoAcima(base.id);
        if (acima) { correcoes.push({ regra: "RF17 transpasse", elementoRef: e.id, campo: "nivelTopoId", antes: e.nivelTopoId, depois: acima.id }); e.nivelTopoId = acima.id; }
      }
    }
    if (e.tipo === "VIGA" && e.b < SECOES_MINIMAS.vigaB) violacoes1.push(v("C-SECAO_MIN_VIGA", "NBR 6118 13.2.2", `Viga com b = ${e.b * 100} cm: mínimo 12 cm.`, e, { esperado: "≥ 0,12 m", obtido: `${e.b} m` }));
    if (e.tipo === "LAJE") {
      if (e.h < SECOES_MINIMAS.lajeH) violacoes1.push(v("C-ESP_MIN_LAJE", "NBR 6118 13.2.4.1", `Laje com h = ${e.h * 100} cm: mínimo 8 cm.`, e, { esperado: "≥ 0,08 m", obtido: `${e.h} m` }));
      if (e.contorno.length < 3) violacoes1.push(v("C-CONTORNO", "RF04", "Laje precisa de contorno fechado com 3+ vértices.", e));
    }
    if (e.tipo === "PAREDE") {
      if (e.espessura < SECOES_MINIMAS.paredeEsp || e.espessura > 1) violacoes1.push(v("C-ESP_PAREDE", "RF01 RN-01", "Espessura de parede entre 0,05 m e 1,00 m.", e, { obtido: `${e.espessura} m` }));
      const minEsp = mat?.geometricConstraints.espessuraMinima_m;
      if (minEsp && e.espessura < minEsp) violacoes1.push(v("C-ESP_MATERIAL", "Tabela A", `${mat!.name} exige espessura ≥ ${minEsp} m.`, e));
    }
  }

  // Caixa Cartesiana Sanitizada (RF06): nada além da envoltória real dos eixos
  const cx = caixaDosEixos(modelo);
  if (cx) {
    const folga = 0.5;
    for (const e of novos) {
      const pts = pontosDe(e, modelo);
      const fora = pts.some((p) => p.x < cx.min.x - folga || p.x > cx.max.x + folga || p.y < cx.min.y - folga || p.y > cx.max.y + folga);
      if (fora && (e.tipo === "PILAR" || e.tipo === "VIGA" || e.tipo === "LAJE")) {
        if ((opts.politicaForaDaCaixa ?? "RECORTAR") === "RECORTAR" && "p1" in e && "p2" in e) {
          const antes = { p1: { ...e.p1 }, p2: { ...e.p2 } };
          e.p1 = clampar(e.p1, cx, folga); e.p2 = clampar(e.p2, cx, folga);
          correcoes.push({ regra: "RF06 Caixa Cartesiana", elementoRef: e.id, campo: "p1/p2", antes, depois: { p1: e.p1, p2: e.p2 } });
        } else violacoes1.push(v("C-FORA_DA_CAIXA", "RF06 Caixa Cartesiana", `${e.tipo} extrapola a envoltória real dos eixos (Fencepost Error).`, e));
      }
    }
  }

  // ---------------- Laço 2 · geometria (versão leve)
  for (const e of novos) {
    if (e.tipo === "LAJE" && e.contorno.length >= 3 && temAutoIntersecao(e.contorno)) violacoes2.push(v("G-AUTO_INTERSECAO", "BRep", "Contorno da laje se auto-intersecta.", e));
    if (e.tipo === "PILAR") {
      for (const o of modelo.elementosDo(e.pavimentoId)) if (o.tipo === "PILAR" && o.id !== e.id && dist(o.centro, e.centro) < (e.b + o.b) / 2 - 1e-6) violacoes2.push(v("G-INTERPENETRACAO", "BRep", `Pilar sobreposto a ${o.rotulo ?? o.id}.`, e));
    }
    if (e.tipo === "PORTA" || e.tipo === "JANELA") {
      const w = modelo.elemento(e.paredeId) as Parede | undefined;
      if (!w) { violacoes2.push(v("G-SEM_HOSPEDEIRA", "NewOpening", "Abertura sem parede hospedeira.", e)); continue; }
      const L = dist(w.p1, w.p2); const ini = e.posicao * L - e.largura / 2, fim = e.posicao * L + e.largura / 2;
      if (ini < 0.1 || fim > L - 0.1) violacoes2.push(v("G-ABERTURA_FORA_DA_FACE", "NewOpening", "Abertura precisa caber inteira na face com afastamento ≥ 0,10 m das bordas.", e, { obtido: `${ini.toFixed(2)}..${fim.toFixed(2)} de ${L.toFixed(2)} m` }));
      if (e.peitoril + e.altura > w.altura) violacoes2.push(v("G-ABERTURA_ALTA", "NewOpening", "Abertura mais alta que a parede.", e));
    }
  }
  // envoltória por pavimento: aviso na edição, violação na conclusão (RF16 CA-09)
  const pavs = new Set(novos.map((e) => e.pavimentoId));
  for (const pavId of pavs) {
    const lacuna = envoltoriaAberta(modelo, pavId);
    if (lacuna) {
      const item: Violacao = { codigo: "ENVOLTORIA_ABERTA", severidade: opts.conclusao ? "VIOLACAO" : "AVISO", regra: "RF16 fechamento da envoltória", mensagem: `Envoltória aberta no pavimento ${modelo.pavimento(pavId)?.nome}: folga de ${lacuna.folga.toFixed(2)} m em [${lacuna.p.x.toFixed(2)}, ${lacuna.p.y.toFixed(2)}].`, obtido: `${lacuna.folga.toFixed(3)} m`, esperado: "≤ 0,001 m" };
      (opts.conclusao ? violacoes2 : avisos).push(item);
    }
  }

  const l1 = violacoes1.length === 0, l2 = violacoes2.length === 0;
  return { laco1: { aprovado: l1, violacoes: violacoes1 }, laco2: { aprovado: l2, violacoes: violacoes2, avisos }, correcoes, aprovado: l1 && l2 };
}

/** Verificação de conclusão (análise, prancha, exportação, EM_ANDAMENTO): laço 2 sobre o pavimento inteiro. */
export function verificarConclusao(modelo: ModeloBim, pavimentoId: string): Violacao[] {
  const out: Violacao[] = [];
  const lacuna = envoltoriaAberta(modelo, pavimentoId);
  if (lacuna) out.push({ codigo: "ENVOLTORIA_ABERTA", severidade: "VIOLACAO", regra: "RF16 fechamento da envoltória", mensagem: `Envoltória aberta: folga de ${lacuna.folga.toFixed(2)} m em [${lacuna.p.x.toFixed(2)}, ${lacuna.p.y.toFixed(2)}].` });
  for (const e of modelo.elementosDo(pavimentoId)) if (e.tipo === "PILAR" && !temApoioInferior(modelo, e)) out.push({ codigo: "Z-SEM_APOIO_INFERIOR", severidade: "AVISO", regra: "RF17 transição", mensagem: `Pilar ${e.rotulo ?? e.id} nasce sem pilar abaixo (transição em viga?). Confirme.`, elementoRef: e.id });
  return out;
}

function temApoioInferior(modelo: ModeloBim, p: Pilar): boolean {
  const base = modelo.pavimento(p.nivelBaseId); if (!base) return true;
  const ord = modelo.pavimentosOrdenados(); const i = ord.findIndex((x) => x.id === base.id);
  if (i <= 0) return true; // térreo/fundação
  const abaixo = ord[i - 1]!;
  return modelo.elementosDo(abaixo.id).some((e) => (e.tipo === "PILAR" && dist(e.centro, p.centro) < 0.05) || (e.tipo === "SAPATA" || e.tipo === "BLOCO" || e.tipo === "RADIER"));
}

/** paredes externas formam anel fechado? devolve a maior lacuna */
export function envoltoriaAberta(modelo: ModeloBim, pavimentoId: string): { p: Ponto2D; folga: number } | null {
  const paredes = modelo.elementosDo(pavimentoId).filter((e): e is Parede => e.tipo === "PAREDE" && e.externa);
  if (paredes.length === 0) return null;
  const extremos: Ponto2D[] = paredes.flatMap((w) => [w.p1, w.p2]);
  let pior: { p: Ponto2D; folga: number } | null = null;
  for (const p of extremos) {
    const outros = extremos.filter((q) => q !== p);
    const menor = Math.min(...outros.map((q) => dist(p, q)));
    const conectado = menor <= 0.001 || paredes.some((w) => w.p1 !== p && w.p2 !== p && pontoNaParede(p, w));
    if (!conectado && (!pior || menor > pior.folga)) pior = { p, folga: menor === Infinity ? 0 : menor };
  }
  return pior;
}

function pontoNaParede(p: Ponto2D, w: Parede): boolean {
  const d = dist(w.p1, w.p2); if (d < 1e-9) return false;
  const t = ((p.x - w.p1.x) * (w.p2.x - w.p1.x) + (p.y - w.p1.y) * (w.p2.y - w.p1.y)) / (d * d);
  if (t < -1e-6 || t > 1 + 1e-6) return false;
  const proj = { x: w.p1.x + t * (w.p2.x - w.p1.x), y: w.p1.y + t * (w.p2.y - w.p1.y) };
  return dist(proj, p) <= 0.001 + w.espessura / 2;
}

function caixaDosEixos(modelo: ModeloBim): { min: Ponto2D; max: Ponto2D } | null {
  const xs = modelo.eixos.filter((e) => e.direcao === "X").map((e) => e.posicao), ys = modelo.eixos.filter((e) => e.direcao === "Y").map((e) => e.posicao);
  if (xs.length < 2 || ys.length < 2) return null;
  return { min: { x: Math.min(...xs), y: Math.min(...ys) }, max: { x: Math.max(...xs), y: Math.max(...ys) } };
}

function clampar(p: Ponto2D, cx: { min: Ponto2D; max: Ponto2D }, folga: number): Ponto2D {
  return { x: Math.min(Math.max(p.x, cx.min.x - folga), cx.max.x + folga), y: Math.min(Math.max(p.y, cx.min.y - folga), cx.max.y + folga) };
}

export function pontosDe(e: Elemento, modelo?: ModeloBim): Ponto2D[] {
  if ("contorno" in e) return e.contorno;
  if ("pontos" in e) return e.pontos;
  if ("p1" in e && "p2" in e) return "p3" in e ? [e.p1, e.p2, e.p3] : [e.p1, e.p2];
  if ("centro" in e) return [e.centro];
  if ("ponto" in e) return [e.ponto];
  if ("p" in e) return [e.p];
  if ((e.tipo === "PORTA" || e.tipo === "JANELA") && modelo) return [modelo.pontoRepresentativo(e)];
  return [];
}

export { caixa, intersecaoSegmentos };
