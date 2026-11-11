import { it, expect } from "vitest";
import * as THREE from "three";
import { centroVerticalViga, baseVerticalLaje, formaLaje } from "./geometria-cena";

it("topos da viga e da laje coincidem com a cota dos pilares em 3 m", () => {
  expect(centroVerticalViga(3, .5) + .5/2).toBe(3);
  expect(baseVerticalLaje(3, .15) + .15).toBe(3);
});
it("contorno da laje ocupa o mesmo lado dos eixos que vigas e pilares", () => {
  const g = new THREE.ExtrudeGeometry(formaLaje([{x:0,y:0},{x:6,y:0},{x:6,y:4},{x:0,y:4}]), { depth:.15,bevelEnabled:false });
  try {
    g.rotateX(-Math.PI/2); g.translate(0,baseVerticalLaje(3,.15),0);g.computeBoundingBox();
    expect(g.boundingBox!.min.x).toBeCloseTo(0);
    expect(g.boundingBox!.max.x).toBeCloseTo(6);
    expect(g.boundingBox!.min.z).toBeCloseTo(-4);
    expect(g.boundingBox!.max.z).toBeCloseTo(0);
    expect(g.boundingBox!.min.y).toBeCloseTo(2.85);
    expect(g.boundingBox!.max.y).toBeCloseTo(3);
  } finally { g.dispose(); }
});
