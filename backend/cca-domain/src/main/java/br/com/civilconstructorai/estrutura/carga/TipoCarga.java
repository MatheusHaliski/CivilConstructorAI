package br.com.civilconstructorai.estrutura.carga;

/** Tipo geométrico da carga e sua unidade obrigatória. */
public enum TipoCarga {
    AREA("kN/m²"), LINEAR("kN/m"), PONTUAL("kN");

    private final String unidade;

    TipoCarga(String unidade) {
        this.unidade = unidade;
    }

    public String unidade() {
        return unidade;
    }
}
