package br.com.civilconstructorai.geotecnia;

/**
 * Leitura do SPT a cada metro (NBR 6484): Nspt inteiro ≥ 0; {@code impenetravel} marca o limite de sondagem
 * de forma explícita, em vez de um número.
 */
public record LeituraSpt(double profundidade, int nspt, boolean impenetravel) {

    public LeituraSpt {
        if (profundidade < 0) {
            throw new IllegalArgumentException("Profundidade negativa.");
        }
        if (nspt < 0) {
            throw new IllegalArgumentException("Nspt deve ser inteiro ≥ 0.");
        }
    }

    public int nsptCalculo() {
        return impenetravel ? Math.max(nspt, 50) : nspt;
    }
}
