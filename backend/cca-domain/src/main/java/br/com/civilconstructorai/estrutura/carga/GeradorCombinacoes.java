package br.com.civilconstructorai.estrutura.carga;

import java.util.List;
import java.util.Map;

import static br.com.civilconstructorai.estrutura.carga.Combinacao.EstadoLimite.*;
import static br.com.civilconstructorai.estrutura.carga.NaturezaAcao.*;

/**
 * Combinações da NBR 8681 (edificações, cargas acidentais de uso residencial/comercial):
 * ELU normal: γg = 1,4 (desfavorável) · γq = 1,4 · ψ0 = 0,5 para sobrecarga, 0,6 para vento.
 * ELS: frequente ψ1 (Q 0,4 · W 0,3) e quase-permanente ψ2 (Q 0,3 · W 0).
 */
public final class GeradorCombinacoes {

    public static final double GAMA_G = 1.4;
    public static final double GAMA_Q = 1.4;
    public static final double PSI0_Q = 0.5;
    public static final double PSI0_W = 0.6;
    public static final double PSI1_Q = 0.4;
    public static final double PSI1_W = 0.3;
    public static final double PSI2_Q = 0.3;

    private GeradorCombinacoes() {
    }

    public static List<Combinacao> gerar(boolean temAcidental, boolean temVento) {
        var out = new java.util.ArrayList<Combinacao>();
        out.add(new Combinacao("ELU 1 · 1,4G", ELU_NORMAL, Map.of(PERMANENTE, GAMA_G)));
        if (temAcidental) {
            out.add(new Combinacao("ELU 2 · 1,4G + 1,4Q", ELU_NORMAL, Map.of(PERMANENTE, GAMA_G, ACIDENTAL, GAMA_Q)));
        }
        if (temVento) {
            out.add(new Combinacao("ELU 3 · 1,4G + 1,4W", ELU_NORMAL, Map.of(PERMANENTE, GAMA_G, VENTO, GAMA_Q)));
        }
        if (temAcidental && temVento) {
            out.add(new Combinacao("ELU 4 · 1,4G + 1,4Q + 0,84W", ELU_NORMAL, Map.of(PERMANENTE, GAMA_G, ACIDENTAL, GAMA_Q, VENTO, GAMA_Q * PSI0_W)));
            out.add(new Combinacao("ELU 5 · 1,4G + 1,4W + 0,70Q", ELU_NORMAL, Map.of(PERMANENTE, GAMA_G, VENTO, GAMA_Q, ACIDENTAL, GAMA_Q * PSI0_Q)));
        }
        out.add(new Combinacao("ELS QP · G + 0,3Q", ELS_QUASE_PERMANENTE, temAcidental ? Map.of(PERMANENTE, 1.0, ACIDENTAL, PSI2_Q) : Map.of(PERMANENTE, 1.0)));
        out.add(new Combinacao("ELS F · G + 0,4Q + 0,3W", ELS_FREQUENTE, Map.of(PERMANENTE, 1.0, ACIDENTAL, temAcidental ? PSI1_Q : 0.0, VENTO, temVento ? PSI1_W : 0.0)));
        out.add(new Combinacao("ELS R · G + Q + 0,6W", ELS_RARA, Map.of(PERMANENTE, 1.0, ACIDENTAL, temAcidental ? 1.0 : 0.0, VENTO, temVento ? PSI0_W : 0.0)));
        return List.copyOf(out);
    }
}
