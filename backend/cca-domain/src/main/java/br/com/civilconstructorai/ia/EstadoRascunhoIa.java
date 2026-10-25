package br.com.civilconstructorai.ia;

import br.com.civilconstructorai.comum.MaquinaDeEstados;

import java.util.Map;
import java.util.Set;

/**
 * Todo resultado de IA nasce {@code GERADO_POR_IA} e só vale depois de {@code APROVADO} por profissional
 * habilitado (RF27). {@code OBSOLETO} quando a entrada muda; {@code RECUSADO_PELO_MODELO} quando o modelo
 * recusou ({@code stop_reason = refusal}).
 */
public enum EstadoRascunhoIa {
    GERADO_POR_IA, EM_REVISAO, APROVADO, REJEITADO, OBSOLETO, RECUSADO_PELO_MODELO;

    public static final MaquinaDeEstados<EstadoRascunhoIa> MAQUINA = new MaquinaDeEstados<>("RascunhoIa", Map.of(
            GERADO_POR_IA, Set.of(EM_REVISAO, REJEITADO, OBSOLETO),
            EM_REVISAO, Set.of(APROVADO, REJEITADO, OBSOLETO),
            APROVADO, Set.of(OBSOLETO),
            REJEITADO, Set.of(),
            OBSOLETO, Set.of(),
            RECUSADO_PELO_MODELO, Set.of()));

    public boolean pendente() {
        return this == GERADO_POR_IA || this == EM_REVISAO;
    }
}
