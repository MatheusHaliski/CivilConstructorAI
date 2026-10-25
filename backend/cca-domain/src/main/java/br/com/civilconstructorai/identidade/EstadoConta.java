package br.com.civilconstructorai.identidade;

import br.com.civilconstructorai.comum.MaquinaDeEstados;

import java.util.Map;
import java.util.Set;

/** Estados da conta (RF18, RF19, RF26). {@code ANONYMOUS_LGPD_DELETED} é terminal. */
public enum EstadoConta {
    PENDING_VERIFICATION, ACTIVE, SUSPENDED, PENDING_DELETION, ANONYMOUS_LGPD_DELETED;

    public static final MaquinaDeEstados<EstadoConta> MAQUINA = new MaquinaDeEstados<>("Conta", Map.of(
            PENDING_VERIFICATION, Set.of(ACTIVE),
            ACTIVE, Set.of(SUSPENDED, PENDING_DELETION),
            SUSPENDED, Set.of(ACTIVE),
            PENDING_DELETION, Set.of(ACTIVE, ANONYMOUS_LGPD_DELETED),
            ANONYMOUS_LGPD_DELETED, Set.of()));

    public boolean podeEntrar() {
        return this == ACTIVE || this == PENDING_VERIFICATION || this == PENDING_DELETION;
    }
}
