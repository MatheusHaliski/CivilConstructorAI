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

it("acumula camadas por face sem misturar os lados opostos", async () => {
  const { posicoesCamadas } = await import("./geometria-cena");
  const resultado = posicoesCamadas([
    { id: "reboco", face: "LADO_A", espessura_m: .02 },
    { id: "gesso", face: "LADO_B", espessura_m: .01 },
    { id: "azulejo", face: "LADO_A", espessura_m: .008 },
  ]);
  expect(resultado[0]!.centro).toBeCloseTo(.01);
  expect(resultado[1]!.inicio).toBe(0);
  expect(resultado[2]!.inicio).toBeCloseTo(.02);
  expect(resultado[2]!.centro).toBeCloseTo(.024);
});

it("paginação mantém peças em metros quando a superfície dobra e os recortes mudam", async () => {
  const { parametrosTexturaModulo } = await import("./geometria-cena");
  const curto = parametrosTexturaModulo(.2, .3, .005, { tipo: "PAREDE", x0: 0, x1: 2.05, z0: 0, z1: 3.05 })!;
  const longo = parametrosTexturaModulo(.2, .3, .005, { tipo: "PAREDE", x0: 0, x1: 4.1, z0: 0, z1: 3.05 })!;
  expect(curto.repeat[0]).toBeCloseTo(10);
  expect(longo.repeat[0]).toBeCloseTo(20);
  // Um trecho novo a partir de 1,025 m começa exatamente no quinto módulo.
  const recorte = parametrosTexturaModulo(.2, .3, .005, { tipo: "PAREDE", x0: 1.025, x1: 2.05, z0: .61, z1: 3.05 })!;
  expect(recorte.offset[0]).toBeCloseTo(5);
  expect(recorte.offset[1]).toBeCloseTo(2);
  expect(recorte.juntaU * .205).toBeCloseTo(.005);
  const posterior = parametrosTexturaModulo(.2, .3, .005, { tipo: "PAREDE", x0: 1.025, x1: 2.05, z0: .61, z1: 3.05, facePosterior: true })!;
  expect(posterior.offset[0] + posterior.repeat[0]).toBeCloseTo(recorte.offset[0]);
});

it("lajes usam UV métricos XY antes da rotação para X/−Z, sem esticar módulos ao contorno", async () => {
  const { parametrosTexturaModulo } = await import("./geometria-cena");
  const p = parametrosTexturaModulo(.6, .3, 0, { tipo: "LAJE" })!;
  const g = new THREE.ExtrudeGeometry(formaLaje([{ x: 2, y: 1 }, { x: 8, y: 1 }, { x: 8, y: 4 }, { x: 2, y: 4 }]), { depth: .02, bevelEnabled: false });
  try {
    const xy = g.getAttribute("position"), uv = g.getAttribute("uv"), normal = g.getAttribute("normal");
    const topo = Array.from({ length: xy.count }, (_, i) => i).filter(i => normal.getZ(i) > .99);
    expect(topo.length).toBeGreaterThan(0);
    for (const i of topo) {
      expect(uv.getX(i) * p.repeat[0] * .6).toBeCloseTo(xy.getX(i));
      expect(uv.getY(i) * p.repeat[1] * .3).toBeCloseTo(xy.getY(i));
    }
  } finally { g.dispose(); }
});
