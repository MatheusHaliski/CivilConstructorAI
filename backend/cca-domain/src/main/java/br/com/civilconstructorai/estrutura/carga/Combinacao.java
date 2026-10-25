package br.com.civilconstructorai.estrutura.carga;

import java.util.Map;

/** Combinação de ações: nome, estado-limite e fator por natureza (NBR 8681). */
public record Combinacao(String nome, EstadoLimite estadoLimite, Map<NaturezaAcao, Double> fatores) {

    public enum EstadoLimite { ELU_NORMAL, ELS_FREQUENTE, ELS_QUASE_PERMANENTE, ELS_RARA }

    public double fator(NaturezaAcao n) {
        return fatores.getOrDefault(n, 0.0);
    }
}
