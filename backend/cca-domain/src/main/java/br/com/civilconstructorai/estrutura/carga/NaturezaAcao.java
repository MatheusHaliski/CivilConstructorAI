package br.com.civilconstructorai.estrutura.carga;

/** Natureza da ação (NBR 8681): G permanente, Q acidental (variável), W vento. */
public enum NaturezaAcao {
    PERMANENTE("G"), ACIDENTAL("Q"), VENTO("W");

    private final String sigla;

    NaturezaAcao(String sigla) {
        this.sigla = sigla;
    }

    public String sigla() {
        return sigla;
    }
}
