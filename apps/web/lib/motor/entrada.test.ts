import { describe, expect, it } from "vitest";
import { ModeloBim } from "@cca/notacao-vi";
import { interpretarPonto, interpretarValor, restringir } from "./entrada";
import { candidatos, melhor, snapGrade } from "./snap";

describe("entrada dinâmica (RF28 CA-02)", () => {
  it("lê absoluto com vírgula ou ponto decimal e ; como separador", () => {
    expect(interpretarPonto("5;0", null)).toMatchObject({ tipo: "ponto", ponto: { x: 5, y: 0 } });
    expect(interpretarPonto("2,5;3.25", null)).toMatchObject({ tipo: "ponto", ponto: { x: 2.5, y: 3.25 } });
    expect(interpretarPonto("4,6", null)).toMatchObject({ tipo: "ponto", ponto: { x: 4, y: 6 } });
  });
  it("relativo e polar partem do último ponto", () => {
    expect(interpretarPonto("@2,0", { x: 1, y: 1 })).toMatchObject({ ponto: { x: 3, y: 1 } });
    const p = interpretarPonto("@5<90", { x: 0, y: 0 });
    expect(p.tipo).toBe("ponto");
    if (p.tipo === "ponto") { expect(p.ponto.x).toBeCloseTo(0); expect(p.ponto.y).toBeCloseTo(5); }
  });
  it("relativo sem ponto anterior e texto inválido viram erro no prompt, sem exceção", () => {
    expect(interpretarPonto("@1,1", null).tipo).toBe("erro");
    expect(interpretarPonto("parede", null).tipo).toBe("erro");
  });
  it("Enter aceita o padrão; opção aceita a inicial; número inválido é erro", () => {
    expect(interpretarValor("", undefined, 0.15, "VALOR")).toEqual({ tipo: "numero", valor: 0.15 });
    expect(interpretarValor("f", ["Desfazer", "Fechar"], undefined, "VALOR")).toEqual({ tipo: "opcao", valor: "Fechar" });
    expect(interpretarValor("0,2", undefined, undefined, "VALOR")).toEqual({ tipo: "numero", valor: 0.2 });
    expect(interpretarValor("abc", undefined, undefined, "VALOR").tipo).toBe("erro");
  });
});

describe("modos do cursor (CA-04/CA-05)", () => {
  it("orto prende ao eixo dominante e polar ao incremento", () => {
    expect(restringir({ x: 5, y: 1 }, { x: 0, y: 0 }, "orto")).toEqual({ x: 5, y: 0 });
    const p = restringir({ x: 5, y: 1.2 }, { x: 0, y: 0 }, "polar", 15);
    expect(Math.atan2(p.y, p.x) * (180 / Math.PI)).toBeCloseTo(15, 5);
  });
  it("snap de grade arredonda ao passo", () => {
    expect(snapGrade({ x: 1.26, y: 2.74 }, 0.5)).toEqual({ x: 1.5, y: 2.5 });
  });
  it("osnap prioriza interseção de eixos sobre extremidade", () => {
    const m = new ModeloBim();
    m.aplicar("MALHA", { nx: 2, dx: 5, ny: 2, dy: 6 });
    m.aplicar("LINHA", { p1: { x: 0, y: 0 }, p2: { x: 3, y: 0 } });
    const pav = m.pavimentosOrdenados()[0]!.id;
    const c = candidatos(m, pav, { x: 0.05, y: 0.05 }, 0.5, new Set(["EIX", "EXT", "PRX"]), new Set(), null);
    expect(melhor(c, { x: 0.05, y: 0.05 })?.modo).toBe("EIX");
  });
});
