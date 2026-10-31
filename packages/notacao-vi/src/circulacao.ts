/**
 * Escadas e rampas retas (AUD-08): perfil lateral, altura do piso num ponto da planta e medidas derivadas. O mesmo
 * cálculo está em Circulacao.java. Medidas são geométricas (espelho, piso, inclinação); a verificação normativa de
 * escadas e rampas não é executada aqui.
 */
import { dist } from "./geometria";
import type { Circulacao, ModeloLeitura, Ponto2D } from "./tipos";

/** espelho adotado para sugerir o número de degraus quando o usuário não informa (ajustável no elemento) */
export const ESPELHO_ADOTADO = 0.175;

export function degrausDe(e: Circulacao): number { return e.tipo === "ESCADA" ? Math.max(1, e.degraus ?? Math.round(e.desnivel / ESPELHO_ADOTADO)) : 0; }

/** polígono do perfil lateral em (s ao longo de p1→p2, h): escada em dente de serra cheio até o piso; rampa em cunha */
export function perfilCirculacao(e: Circulacao): [number, number][] {
  const L = dist(e.p1, e.p2), H = e.desnivel;
  if (e.tipo === "RAMPA") return [[0, 0], [L, H], [L, 0]];
  const n = degrausDe(e), g = L / n, h = H / n;
  const pts: [number, number][] = [[0, 0]];
  for (let i = 0; i < n; i++) pts.push([i * g, (i + 1) * h], [(i + 1) * g, (i + 1) * h]);
  pts.push([L, 0]);
  return pts;
}

export function medidasCirculacao(e: Circulacao): { comprimento: number; espelho?: number; piso?: number; inclinacaoPct?: number } {
  const L = dist(e.p1, e.p2);
  if (e.tipo === "RAMPA") return { comprimento: L, inclinacaoPct: L > 0 ? (e.desnivel / L) * 100 : 0 };
  const n = degrausDe(e);
  return { comprimento: L, espelho: e.desnivel / n, piso: L / n };
}

/** posição relativa: t ao longo de p1→p2 (0..1) e afastamento lateral do eixo (m) */
function local(e: Circulacao, p: Ponto2D): { t: number; d: number } {
  const L = dist(e.p1, e.p2) || 1, ux = (e.p2.x - e.p1.x) / L, uy = (e.p2.y - e.p1.y) / L;
  return { t: ((p.x - e.p1.x) * ux + (p.y - e.p1.y) * uy) / L, d: -(p.x - e.p1.x) * uy + (p.y - e.p1.y) * ux };
}

/** altura do piso de circulação sob o ponto (em relação ao pavimento), ou null fora de escadas e rampas */
export function alturaCirculacao(e: Circulacao, p: Ponto2D): number | null {
  const { t, d } = local(e, p);
  if (t < 0 || t > 1 || Math.abs(d) > e.largura / 2) return null;
  if (e.tipo === "RAMPA") return e.desnivel * t;
  const n = degrausDe(e);
  return (Math.min(n, Math.floor(t * n) + 1) * e.desnivel) / n;
}

/** altura do piso num ponto do pavimento: a maior escada/rampa sob o ponto, ou 0 */
export function elevacaoNoPiso(m: ModeloLeitura, pavimentoId: string, p: Ponto2D): number {
  let h = 0;
  for (const e of m.elementosDo(pavimentoId)) {
    if (e.tipo !== "ESCADA" && e.tipo !== "RAMPA") continue;
    const a = alturaCirculacao(e, p);
    if (a != null && a > h) h = a;
  }
  return Math.round(h * 1000) / 1000;
}

/** extremidade relativa (t) de um ponto: < 0 antes do pé, > 1 depois do topo; usada pela caminhada em 1ª pessoa */
export function posicaoNaCirculacao(e: Circulacao, p: Ponto2D): { t: number; dentroLateral: boolean } {
  const { t, d } = local(e, p);
  return { t, dentroLateral: Math.abs(d) <= e.largura / 2 };
}

/** contorno em planta (retângulo do lance) */
export function contornoCirculacao(e: Circulacao): Ponto2D[] {
  const L = dist(e.p1, e.p2) || 1, nx = (-(e.p2.y - e.p1.y) / L) * (e.largura / 2), ny = ((e.p2.x - e.p1.x) / L) * (e.largura / 2);
  return [{ x: e.p1.x + nx, y: e.p1.y + ny }, { x: e.p2.x + nx, y: e.p2.y + ny }, { x: e.p2.x - nx, y: e.p2.y - ny }, { x: e.p1.x - nx, y: e.p1.y - ny }];
}
