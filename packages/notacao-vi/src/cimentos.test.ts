import { describe, expect, it } from "vitest";
import { avaliarCimento, cimentosComDosagem, cimentoPorId, type DosagemCimentoDocumentada } from "./cimentos";
const dose: DosagemCimentoDocumentada = {
  id: "d1", cimentoId: "CIM_CP_II_F_32", fckProjetoMPa: 30, fckComprovadoMPa: 34,
  referenciaEnsaio: "Relatório laboratório 123 / análise estatística", origemMemorial: "Memorial M1", versaoModelo: 7, estado: "VALIDADO",
  composicao: { aguaLigante: 0.45, agregado: "Agregado A/lote B", consumoCimentoKgM3: 350, procedimentoCura: "Cura conforme relatório" },
};
describe("seleção de cimento depende de dosagem, nunca equivalência de classe", () => {
  it("não oferece opções sem evidência e aceita só fck/versão comprovados", () => {
    expect(cimentosComDosagem(30, 7, [])).toEqual([]);
    expect(cimentosComDosagem(30, 7, [dose]).map((c) => c.id)).toEqual([dose.cimentoId]);
    expect(cimentosComDosagem(25, 7, [dose])).toEqual([]);
    expect(cimentosComDosagem(30, 8, [dose])).toEqual([]);
  });
  it("rejeita rascunho, fonte ausente, composição ausente e desempenho insuficiente", () => {
    for (const invalida of [ { ...dose, estado: "PENDENTE" as const }, { ...dose, referenciaEnsaio: "" },
      { ...dose, fckComprovadoMPa: 29 }, { ...dose, cimentoId: "inventado" },
      { ...dose, composicao: { ...dose.composicao, aguaLigante: NaN } }, { ...dose, composicao: { ...dose.composicao, agregado: "" } } ]) {
      expect(cimentosComDosagem(30, 7, [invalida])).toEqual([]);
    }
  });
  it("não deriva RS/BC da família nem classe do ARI", () => {
    expect(cimentoPorId("CIM_CP_V_ARI")?.classe28d).toBeUndefined();
    expect(avaliarCimento("CIM_CP_IV_32").estado).toBe("NAO_VERIFICADO");
    expect(avaliarCimento("CIM_CP_IV_32", { origemMemorial: "M1", versaoModelo: 1, desempenhoValidado: true, exigeRS: true }).estado).toBe("NAO_VERIFICADO");
    expect(avaliarCimento("CIM_CP_II_F_25", { origemMemorial: "M1", versaoModelo: 1, desempenhoValidado: true, classeMinima: 32 }).estado).toBe("INCOMPATIVEL");
  });
});
