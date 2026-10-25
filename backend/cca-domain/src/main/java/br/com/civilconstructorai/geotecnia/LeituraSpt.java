package br.com.civilconstructorai.geotecnia;

/**
 * Leitura do SPT a cada metro (NBR 6484): Nspt inteiro ≥ 0; {@code impenetravel} marca o limite de sondagem
 * de forma explícita, em vez de um número.
 */
public record LeituraSpt(double profundidade, int nspt, boolean impenetravel) {

    /** profundidade ≥ 0 e Nspt ≥ 0 são validados por quem recebe a entrada, juntando todos os erros do furo (RF09 CA-03) */
    public boolean valida() {
        return profundidade >= 0 && nspt >= 0;
    }

    public int nsptCalculo() {
        return impenetravel ? Math.max(nspt, 50) : nspt;
    }
}
