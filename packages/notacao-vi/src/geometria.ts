import type { Ponto2D } from "./tipos";

export const EPS = 1e-6;

export const dist = (a: Ponto2D, b: Ponto2D) => Math.hypot(b.x - a.x, b.y - a.y);
export const soma = (a: Ponto2D, b: Ponto2D): Ponto2D => ({ x: a.x + b.x, y: a.y + b.y });
export const sub = (a: Ponto2D, b: Ponto2D): Ponto2D => ({ x: a.x - b.x, y: a.y - b.y });
export const escala = (a: Ponto2D, k: number): Ponto2D => ({ x: a.x * k, y: a.y * k });
export const medio = (a: Ponto2D, b: Ponto2D): Ponto2D => ({ x: (a.x + b.x) / 2, y: (a.y + b.y) / 2 });
export const arredondar = (v: number, casas = 3) => Math.round(v * 10 ** casas) / 10 ** casas;
export const arredondarPonto = (p: Ponto2D): Ponto2D => ({ x: arredondar(p.x), y: arredondar(p.y) });

export function rotacionar(p: Ponto2D, base: Ponto2D, grausAnti: number): Ponto2D {
  const r = (grausAnti * Math.PI) / 180;
  const d = sub(p, base);
  return { x: base.x + d.x * Math.cos(r) - d.y * Math.sin(r), y: base.y + d.x * Math.sin(r) + d.y * Math.cos(r) };
}

export function espelhar(p: Ponto2D, a: Ponto2D, b: Ponto2D): Ponto2D {
  const d = sub(b, a);
  const l2 = d.x * d.x + d.y * d.y || EPS;
  const t = ((p.x - a.x) * d.x + (p.y - a.y) * d.y) / l2;
  const proj = { x: a.x + t * d.x, y: a.y + t * d.y };
  return { x: 2 * proj.x - p.x, y: 2 * proj.y - p.y };
}

/** ponto mais próximo de p no segmento ab e o parâmetro t ∈ [0,1] */
export function projetarNoSegmento(p: Ponto2D, a: Ponto2D, b: Ponto2D): { ponto: Ponto2D; t: number } {
  const d = sub(b, a);
  const l2 = d.x * d.x + d.y * d.y;
  if (l2 < EPS) return { ponto: a, t: 0 };
  const t = Math.max(0, Math.min(1, ((p.x - a.x) * d.x + (p.y - a.y) * d.y) / l2));
  return { ponto: { x: a.x + t * d.x, y: a.y + t * d.y }, t };
}

/** interseção de dois segmentos (ou null) */
export function intersecaoSegmentos(a: Ponto2D, b: Ponto2D, c: Ponto2D, d: Ponto2D, extender = false): Ponto2D | null {
  const r = sub(b, a), s = sub(d, c);
  const den = r.x * s.y - r.y * s.x;
  if (Math.abs(den) < EPS) return null;
  const qp = sub(c, a);
  const t = (qp.x * s.y - qp.y * s.x) / den;
  const u = (qp.x * r.y - qp.y * r.x) / den;
  if (!extender && (t < -EPS || t > 1 + EPS || u < -EPS || u > 1 + EPS)) return null;
  return { x: a.x + t * r.x, y: a.y + t * r.y };
}

export function areaPoligono(pts: Ponto2D[]): number {
  let a = 0;
  for (let i = 0; i < pts.length; i++) {
    const p = pts[i]!, q = pts[(i + 1) % pts.length]!;
    a += p.x * q.y - q.x * p.y;
  }
  return Math.abs(a) / 2;
}

export function perimetroPoligono(pts: Ponto2D[]): number {
  let l = 0;
  for (let i = 0; i < pts.length; i++) l += dist(pts[i]!, pts[(i + 1) % pts.length]!);
  return l;
}

export function caixa(pontos: Ponto2D[]): { min: Ponto2D; max: Ponto2D } {
  const xs = pontos.map((p) => p.x), ys = pontos.map((p) => p.y);
  return { min: { x: Math.min(...xs), y: Math.min(...ys) }, max: { x: Math.max(...xs), y: Math.max(...ys) } };
}

export function pontoDentroPoligono(p: Ponto2D, poly: Ponto2D[]): boolean {
  let dentro = false;
  for (let i = 0, j = poly.length - 1; i < poly.length; j = i++) {
    const a = poly[i]!, b = poly[j]!;
    if (a.y > p.y !== b.y > p.y && p.x < ((b.x - a.x) * (p.y - a.y)) / (b.y - a.y) + a.x) dentro = !dentro;
  }
  return dentro;
}

/** polígono tem auto-interseção entre arestas não adjacentes */
export function temAutoIntersecao(pts: Ponto2D[]): boolean {
  const n = pts.length;
  for (let i = 0; i < n; i++) {
    for (let j = i + 2; j < n; j++) {
      if (i === 0 && j === n - 1) continue;
      if (intersecaoSegmentos(pts[i]!, pts[(i + 1) % n]!, pts[j]!, pts[(j + 1) % n]!)) return true;
    }
  }
  return false;
}
