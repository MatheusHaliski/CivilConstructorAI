package br.com.civilconstructorai.obra;

import br.com.civilconstructorai.comum.MaquinaDeEstados;

import java.util.Map;
import java.util.Set;

/** RF22: PLANEJADA → EM_ANDAMENTO ⇄ PARALISADA → CONCLUIDA → ARQUIVADA (somente leitura). */
public enum EstadoObra {
    PLANEJADA, EM_ANDAMENTO, PARALISADA, CONCLUIDA, ARQUIVADA;

    public static final MaquinaDeEstados<EstadoObra> MAQUINA = new MaquinaDeEstados<>("Obra", Map.of(
            PLANEJADA, Set.of(EM_ANDAMENTO),
            EM_ANDAMENTO, Set.of(PARALISADA, CONCLUIDA),
            PARALISADA, Set.of(EM_ANDAMENTO, CONCLUIDA),
            CONCLUIDA, Set.of(ARQUIVADA),
            ARQUIVADA, Set.of()));

    public boolean somenteLeitura() {
        return this == ARQUIVADA;
    }
}
