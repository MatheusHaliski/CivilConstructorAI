package br.com.civilconstructorai.empresa;

import br.com.civilconstructorai.comum.MaquinaDeEstados;

import java.util.Map;
import java.util.Set;

/** RF20: PENDENTE → ACEITO | RECUSADO | EXPIRADO | REVOGADO (todos terminais). */
public enum EstadoConvite {
    PENDENTE, ACEITO, RECUSADO, EXPIRADO, REVOGADO;

    public static final MaquinaDeEstados<EstadoConvite> MAQUINA = new MaquinaDeEstados<>("Convite", Map.of(
            PENDENTE, Set.of(ACEITO, RECUSADO, EXPIRADO, REVOGADO),
            ACEITO, Set.of(), RECUSADO, Set.of(), EXPIRADO, Set.of(), REVOGADO, Set.of()));
}
