/**
 * Osnap (RF28 CA-04): candidatos a ponto notável com prioridade EIX > EXT > INT > MED > CEN > PER > TAN > NOD > QUA > PRX > NIV.
 * Devolve o melhor dentro do raio de atração (em pixels, convertido para metros pela escala atual).
 */
import { cantosObjeto, dist, intersecaoSegmentos, percursoConexao, projetarNoSegmento, type Elemento, type ModeloBim, type ModoOsnap, type Ponto2D, PRIORIDADE_OSNAP } from "@cca/notacao-vi";

export type Candidato = { modo: ModoOsnap; p: Ponto2D; rotulo: string };

const ROTULO: Record<ModoOsnap, string> = { EIX: "Eixo", EXT: "Extremidade", INT: "Interseção", MED: "Ponto médio", CEN: "Centro", PER: "Perpendicular", TAN: "Tangente", NOD: "Nó", QUA: "Quadrante", PRX: "Mais próximo", NIV: "Nível" };

function segmentos(e: Elemento): [Ponto2D, Ponto2D][] {
  switch (e.tipo) {
    case "PAREDE": case "VIGA": case "LINHA": case "PLANO_REF": case "CONTRAVENTAMENTO": case "COTA": return [[e.p1, e.p2]];
    case "RETANGULO": { const a = e.p1, c = e.p2, b = { x: c.x, y: a.y }, d = { x: a.x, y: c.y }; return [[a, b], [b, c], [c, d], [d, a]]; }
    case "LAJE": case "HACHURA": return e.contorno.map((p, i, arr) => [p, arr[(i + 1) % arr.length]!] as [Ponto2D, Ponto2D]);
    case "POLILINHA": return e.pontos.slice(0, e.fechada ? undefined : -1).map((p, i) => [p, e.pontos[(i + 1) % e.pontos.length]!] as [Ponto2D, Ponto2D]);
    case "PILAR": { const b = e.b / 2, h = e.h / 2, c = e.centro; const q = [{ x: c.x - b, y: c.y - h }, { x: c.x + b, y: c.y - h }, { x: c.x + b, y: c.y + h }, { x: c.x - b, y: c.y + h }]; return q.map((p, i) => [p, q[(i + 1) % 4]!] as [Ponto2D, Ponto2D]); }
    case "OBJETO": { const q = cantosObjeto(e); return q.map((p, i) => [p, q[(i + 1) % 4]!] as [Ponto2D, Ponto2D]); }
    case "SAPATA": case "BLOCO": case "RADIER": { const b = e.bx / 2, h = e.by / 2, c = e.centro; const q = [{ x: c.x - b, y: c.y - h }, { x: c.x + b, y: c.y - h }, { x: c.x + b, y: c.y + h }, { x: c.x - b, y: c.y + h }]; return q.map((p, i) => [p, q[(i + 1) % 4]!] as [Ponto2D, Ponto2D]); }
    default: return [];
  }
}

export function candidatos(modelo: ModeloBim, pavimentoId: string, cursor: Ponto2D, raio: number, modos: Set<ModoOsnap>, ocultos: Set<string>, ultimo: Ponto2D | null): Candidato[] {
  const out: Candidato[] = [];
  const perto = (p: Ponto2D) => dist(p, cursor) <= raio;
  if (modos.has("EIX")) for (const i of modelo.intersecoesDeEixos()) if (perto(i.p)) out.push({ modo: "EIX", p: i.p, rotulo: `Eixo ${i.nome}` });
  const els = modelo.elementosDo(pavimentoId).filter((e) => !ocultos.has(e.id));
  const segs: { e: Elemento; s: [Ponto2D, Ponto2D] }[] = [];
  for (const e of els) {
    for (const s of segmentos(e)) {
      segs.push({ e, s });
      if (modos.has("EXT")) for (const p of s) if (perto(p)) out.push({ modo: "EXT", p, rotulo: ROTULO.EXT });
      if (modos.has("MED")) { const m = { x: (s[0].x + s[1].x) / 2, y: (s[0].y + s[1].y) / 2 }; if (perto(m)) out.push({ modo: "MED", p: m, rotulo: ROTULO.MED }); }
      if (modos.has("PER") && ultimo) { const pr = projetarNoSegmento(ultimo, s[0], s[1]); if (pr.t > 0 && pr.t < 1 && perto(pr.ponto)) out.push({ modo: "PER", p: pr.ponto, rotulo: ROTULO.PER }); }
      if (modos.has("PRX")) { const pr = projetarNoSegmento(cursor, s[0], s[1]); if (perto(pr.ponto)) out.push({ modo: "PRX", p: pr.ponto, rotulo: ROTULO.PRX }); }
    }
    if ("centro" in e && modos.has("CEN") && perto(e.centro)) out.push({ modo: "CEN", p: e.centro, rotulo: ROTULO.CEN });
    if (e.tipo === "CIRCULO" && modos.has("QUA")) for (const q of [[1, 0], [0, 1], [-1, 0], [0, -1]]) { const p = { x: e.centro.x + q[0]! * e.raio, y: e.centro.y + q[1]! * e.raio }; if (perto(p)) out.push({ modo: "QUA", p, rotulo: ROTULO.QUA }); }
    if ((e.tipo === "PONTO" || e.tipo === "AMBIENTE") && modos.has("NOD")) { const p = e.tipo === "PONTO" ? e.p : e.ponto; if (perto(p)) out.push({ modo: "NOD", p, rotulo: ROTULO.NOD }); }
  }
  if (modos.has("INT")) {
    for (let i = 0; i < segs.length; i++) for (let j = i + 1; j < segs.length; j++) {
      const a = segs[i]!, b = segs[j]!; if (a.e.id === b.e.id) continue;
      const ip = intersecaoSegmentos(a.s[0], a.s[1], b.s[0], b.s[1]);
      if (ip && perto(ip)) out.push({ modo: "INT", p: ip, rotulo: ROTULO.INT });
    }
  }
  // interseção com eixos (eixo × segmento)
  if (modos.has("INT")) for (const ex of modelo.eixos) {
    const a = ex.direcao === "X" ? { x: ex.posicao, y: ex.inicio } : { x: ex.inicio, y: ex.posicao };
    const b = ex.direcao === "X" ? { x: ex.posicao, y: ex.fim } : { x: ex.fim, y: ex.posicao };
    for (const s of segs) { const ip = intersecaoSegmentos(a, b, s.s[0], s.s[1]); if (ip && perto(ip)) out.push({ modo: "INT", p: ip, rotulo: `Interseção eixo ${ex.nome}` }); }
  }
  return out;
}

export function melhor(cands: Candidato[], cursor: Ponto2D): Candidato | null {
  if (!cands.length) return null;
  const prio = (m: ModoOsnap) => PRIORIDADE_OSNAP.indexOf(m);
  return [...cands].sort((a, b) => prio(a.modo) - prio(b.modo) || dist(a.p, cursor) - dist(b.p, cursor))[0] ?? null;
}

export function snapGrade(p: Ponto2D, passo: number): Ponto2D {
  return { x: Math.round(p.x / passo) * passo, y: Math.round(p.y / passo) * passo };
}

/** Teste de seleção: distância do cursor ao elemento (metros). */
export function distanciaAoElemento(e: Elemento, p: Ponto2D, modelo: ModeloBim): number {
  const segs = segmentos(e);
  if (segs.length) {
    let d = Infinity;
    for (const [a, b] of segs) d = Math.min(d, dist(projetarNoSegmento(p, a, b).ponto, p));
    if (e.tipo === "PAREDE") d = Math.max(0, d - e.espessura / 2);
    if (e.tipo === "VIGA") d = Math.max(0, d - e.b / 2);
    if (e.tipo === "OBJETO" && pontoNoRetangulo(p, cantosObjeto(e))) d = 0;
    return d;
  }
  switch (e.tipo) {
    case "CIRCULO": return Math.abs(dist(e.centro, p) - e.raio);
    case "ARCO": return Math.min(dist(e.p1, p), dist(e.p2, p), dist(e.p3, p));
    case "PONTO": return dist(e.p, p);
    case "AMBIENTE": return dist(e.ponto, p) - 0.3;
    case "TEXTO": case "ETIQUETA": return dist(e.p, p) - 0.3;
    case "PORTA": case "JANELA": return dist(modelo.pontoRepresentativo(e), p) - e.largura / 2;
    case "CONEXAO": { const pts = percursoConexao(modelo, e); let d = Infinity; for (let i = 1; i < pts.length; i++) d = Math.min(d, dist(projetarNoSegmento(p, pts[i - 1]!, pts[i]!).ponto, p)); return d; }
    default: return Infinity;
  }
}

/** Caixa envolvente do elemento (para janela de seleção e zoom). */
export function caixaElemento(e: Elemento, modelo: ModeloBim): { min: Ponto2D; max: Ponto2D } {
  const pts: Ponto2D[] = [];
  for (const s of segmentos(e)) pts.push(...s);
  if (e.tipo === "CIRCULO") pts.push({ x: e.centro.x - e.raio, y: e.centro.y - e.raio }, { x: e.centro.x + e.raio, y: e.centro.y + e.raio });
  if (e.tipo === "ARCO") pts.push(e.p1, e.p2, e.p3);
  if (e.tipo === "CONEXAO") pts.push(...percursoConexao(modelo, e));
  if (!pts.length) { const p = modelo.pontoRepresentativo(e); pts.push(p); }
  return { min: { x: Math.min(...pts.map((p) => p.x)), y: Math.min(...pts.map((p) => p.y)) }, max: { x: Math.max(...pts.map((p) => p.x)), y: Math.max(...pts.map((p) => p.y)) } };
}

function pontoNoRetangulo(p: Ponto2D, q: Ponto2D[]): boolean {
  let sinal = 0;
  for (let i = 0; i < q.length; i++) { const a = q[i]!, b = q[(i + 1) % q.length]!; const c = (b.x - a.x) * (p.y - a.y) - (b.y - a.y) * (p.x - a.x); if (Math.abs(c) < 1e-12) continue; const sg = Math.sign(c); if (sinal && sg !== sinal) return false; sinal = sg; }
  return true;
}
