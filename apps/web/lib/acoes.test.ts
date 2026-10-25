import { describe, expect, it } from "vitest";
import { ModeloBim, TABELA_A } from "@cca/notacao-vi";
import { ACOES, acaoPorCodigo, buscarAcoes, codigoMaterial, materialPorCodigo, montarArgs } from "./acoes";

describe("catálogo de ações (RF29)", () => {
  it("ids são únicos, estáveis e no padrão MOD.<VERBO>.<ALVO>", () => {
    const ids = ACOES.map((a) => a.id);
    expect(new Set(ids).size).toBe(ids.length);
    for (const id of ids) expect(id).toMatch(/^MOD\.[A-Z]+\.[A-Z_]+$/);
  });
  it("códigos legados e de VI continuam interpretáveis", () => {
    expect(acaoPorCodigo("parede")?.id).toBe("MOD.CRIAR.PAREDE");
    expect(acaoPorCodigo("MOD.CRIAR.VIGA")?.codigoVi).toBe("VIGA");
  });
  it("busca por sinônimo e sem acento", () => {
    expect(buscarAcoes("coluna").map((a) => a.id)).toContain("MOD.CRIAR.PILAR");
    expect(buscarAcoes("fundacao").map((a) => a.id)).toContain("MOD.CRIAR.SAPATA");
  });
  it("materiais: código MAT. e materialId legado resolvem para o mesmo material, e são únicos", () => {
    const cods = TABELA_A.map(codigoMaterial);
    expect(new Set(cods).size).toBe(cods.length);
    for (const m of TABELA_A) { expect(materialPorCodigo(codigoMaterial(m))).toBe(m); expect(materialPorCodigo(m.materialId)).toBe(m); }
  });
  it("valida unidade, limites e categoria do material", () => {
    const parede = acaoPorCodigo("MOD.CRIAR.PAREDE")!;
    const { erros } = montarArgs(parede, { p1: { x: 0, y: 0 }, p2: { x: 0, y: 0 }, esp: "3", mat: "MAT_CONC_FCK30" });
    const msgs = erros.map((e) => e.mensagem).join(" | ");
    expect(msgs).toMatch(/Espessura: máximo 1 m/);
    expect(msgs).toMatch(/não é da categoria arquitetura/);
    expect(msgs).toMatch(/coincidem/);
  });
  it("args montados geram a mesma geometria no modelo (prévia = aplicação)", () => {
    const parede = acaoPorCodigo("MOD.CRIAR.PAREDE")!;
    const { args, erros } = montarArgs(parede, { p1: { x: 0, y: 0 }, p2: { x: 5, y: 0 }, esp: "0,15", h: "2.8" });
    expect(erros).toEqual([]);
    const m = new ModeloBim();
    const r = m.aplicar(parede.codigoVi, args, { origem: "FORMULARIO", pavimentoId: m.pavimentos[0]!.id });
    const criada = r.diff.criados[0] as unknown as { p2: { x: number }; espessura: number; materialId: string };
    expect(criada.p2.x).toBe(5); expect(criada.espessura).toBe(0.15); expect(criada.materialId).toBe("MAT_ALV_BLOCO14");
    expect(r.vi).toContain("PAREDE");
  });
  it("posição de abertura em % vira fração 0–1", () => {
    const porta = acaoPorCodigo("MOD.CRIAR.PORTA")!;
    const { args } = montarArgs(porta, { parede: "x", posicao: "25" });
    expect(args["posicao"]).toBe(0.25);
  });
});
