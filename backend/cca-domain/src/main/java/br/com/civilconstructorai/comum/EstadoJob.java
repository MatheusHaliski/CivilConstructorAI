package br.com.civilconstructorai.comum;

import java.util.Map;
import java.util.Set;

/** Estados de todo job assíncrono (MEF, prancha, exportação, IFC, extração por IA). */
public enum EstadoJob {
    PENDENTE, EM_EXECUCAO, CONCLUIDO, FALHOU, OBSOLETO;

    public static final MaquinaDeEstados<EstadoJob> MAQUINA = new MaquinaDeEstados<>("Job", Map.of(
            PENDENTE, Set.of(EM_EXECUCAO, OBSOLETO),
            EM_EXECUCAO, Set.of(CONCLUIDO, FALHOU, OBSOLETO),
            CONCLUIDO, Set.of(OBSOLETO),
            FALHOU, Set.of(PENDENTE, OBSOLETO),
            OBSOLETO, Set.of()));
}
