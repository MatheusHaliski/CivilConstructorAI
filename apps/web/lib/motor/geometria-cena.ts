import * as THREE from "three";
import type { Ponto2D } from "@cca/notacao-vi";

/** Pavimento é a cota superior da viga/laje, igual ao recorte do modelo analítico. */
export const centroVerticalViga = (nivel: number, altura: number) => nivel - altura / 2;
export const baseVerticalLaje = (nivel: number, espessura: number) => nivel - espessura;

/** Após rotação −π/2 em X: (x, y) do contorno vira (X=x, Z=−y) na cena. */
export function formaLaje(contorno: Ponto2D[]) {
  return new THREE.Shape(contorno.map(p => new THREE.Vector2(p.x, p.y)));
}

/** Offsets medidos desde a superfície do núcleo; mantém a ordem de aplicação por face. */
export function posicoesCamadas<T extends { id: string; face: string; espessura_m: number }>(camadas: T[]) {
  const acumulado = new Map<string, number>();
  return camadas.map(camada => {
    const inicio = acumulado.get(camada.face) ?? 0;
    const espessura = Math.max(0, camada.espessura_m);
    acumulado.set(camada.face, inicio + espessura);
    return { camada, inicio, centro: inicio + espessura / 2, espessura };
  });
}

/** Escala física do módulo: paredes usam UV 0..1; lajes já têm UV em metros no XY do contorno. */
export function parametrosTexturaModulo(larguraPeca_m: number, alturaPeca_m: number, junta_m: number,
  superficie: { tipo: "PAREDE"; x0: number; x1: number; z0: number; z1: number; facePosterior?: boolean } | { tipo: "LAJE" }) {
  const moduloX = larguraPeca_m + junta_m, moduloY = alturaPeca_m + junta_m;
  if (!(larguraPeca_m > 0 && alturaPeca_m > 0 && junta_m >= 0) || ![moduloX, moduloY].every(Number.isFinite)) return undefined;
  const repeat: [number, number] = superficie.tipo === "LAJE" ? [1 / moduloX, 1 / moduloY] : [(superficie.x1 - superficie.x0) / moduloX, (superficie.z1 - superficie.z0) / moduloY];
  const offset: [number, number] = superficie.tipo === "LAJE" ? [0, 0] : [superficie.x0 / moduloX, superficie.z0 / moduloY];
  // BoxGeometry espelha o U na face −Z: compensar conserva a origem global ao atravessar recortes.
  if (superficie.tipo === "PAREDE" && superficie.facePosterior) { repeat[0] *= -1; offset[0] = superficie.x1 / moduloX; }
  return { repeat, offset, juntaU: junta_m / moduloX, juntaV: junta_m / moduloY };
}
