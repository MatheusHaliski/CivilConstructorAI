package br.com.civilconstructorai.estrutura.dimensionamento;

import br.com.civilconstructorai.notacao.TabelaA;

/** Domínio do protótipo: somente concretos cadastrados, sem inferir resistência pelo nome do material. */
public final class ConcretoSuportado {
    private ConcretoSuportado() { }

    public static double fck(String materialId) {
        var material = TabelaA.material(materialId).orElseThrow(() ->
                new IllegalArgumentException("Material estrutural ausente ou desconhecido: " + materialId));
        var fck = material.physicalProperties().fck_MPa();
        if (fck == null || !Double.isFinite(fck) || fck < 20 || fck > 50) {
            throw new IllegalArgumentException("Material não suportado pelo cálculo de concreto: " + materialId);
        }
        return fck;
    }
}
