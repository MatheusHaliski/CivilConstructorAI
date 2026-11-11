import * as THREE from "three";
import type { Ponto2D } from "@cca/notacao-vi";

/** Pavimento é a cota superior da viga/laje, igual ao recorte do modelo analítico. */
export const centroVerticalViga = (nivel: number, altura: number) => nivel - altura / 2;
export const baseVerticalLaje = (nivel: number, espessura: number) => nivel - espessura;

/** Após rotação −π/2 em X: (x, y) do contorno vira (X=x, Z=−y) na cena. */
export function formaLaje(contorno: Ponto2D[]) {
  return new THREE.Shape(contorno.map(p => new THREE.Vector2(p.x, p.y)));
}
