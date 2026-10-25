import { describe, expect, it } from "vitest";
import { corDaTemperatura, pecasDaForma, posicaoSolar, vetorSolar } from "./formas";

describe("formas procedurais", () => {
  it("as peças ficam dentro da caixa do objeto (planta e 3D com as mesmas dimensões)", () => {
    for (const forma of ["sofa", "mesa", "cadeira", "cama", "geladeira", "lavatorio", "vaso", "humano", "pendente", "caixa"]) {
      const l = 1.2, p = 0.8, a = 1.6;
      for (const pc of pecasDaForma(forma, l, p, a, "#888")) {
        expect(Math.abs(pc.c[0]) + pc.s[0] / 2, forma).toBeLessThanOrEqual(l / 2 + 0.03);
        expect(pc.c[2] - pc.s[2] / 2, forma).toBeGreaterThanOrEqual(-1e-9);
        expect(pc.c[2] + pc.s[2] / 2, forma).toBeLessThanOrEqual(a + 0.25);
      }
    }
  });
  it("pessoa escala de modo uniforme com a altura", () => {
    const a = pecasDaForma("humano", 0.5, 0.3, 1.75, "#888"), b = pecasDaForma("humano", 0.5 * 1.6 / 1.75, 0.3 * 1.6 / 1.75, 1.6, "#888");
    const cabecaA = a[a.length - 1]!, cabecaB = b[b.length - 1]!;
    expect(cabecaB.c[2] / cabecaA.c[2]).toBeCloseTo(1.6 / 1.75, 6);
    expect(cabecaB.s[0] / cabecaA.s[0]).toBeCloseTo(1.6 / 1.75, 6);
  });
});

describe("iluminação", () => {
  it("temperatura de cor: 2700 K é mais quente (menos azul) que 6500 K", () => {
    const azul = (h: string) => parseInt(h.slice(5, 7), 16);
    expect(azul(corDaTemperatura(2700))).toBeLessThan(azul(corDaTemperatura(6500)));
    expect(corDaTemperatura(6600).toLowerCase()).toMatch(/^#ff/);
  });
  it("sol: meio-dia no equinócio no equador fica a pino; manhã a leste, tarde a oeste; noite abaixo do horizonte", () => {
    expect(posicaoSolar(0, 80, 12).elevacao).toBeGreaterThan(85);
    const manha = posicaoSolar(-23.5, 172, 8), tarde = posicaoSolar(-23.5, 172, 16);
    expect(manha.azimute).toBeGreaterThan(0); expect(manha.azimute).toBeLessThan(180);
    expect(tarde.azimute).toBeGreaterThan(180);
    expect(posicaoSolar(-23.5, 172, 23).elevacao).toBeLessThan(0);
    // inverno no hemisfério sul: sol ao norte ao meio-dia
    const v = vetorSolar(posicaoSolar(-23.5, 172, 12).elevacao, posicaoSolar(-23.5, 172, 12).azimute);
    expect(v[2]).toBeLessThan(0); // −z = norte
  });
});
