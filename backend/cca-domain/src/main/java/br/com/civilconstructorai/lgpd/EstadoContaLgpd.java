package br.com.civilconstructorai.lgpd;

/**
 * Estados da conta relevantes à LGPD, espelhando a coluna {@code conta.estado} (a máquina completa fica no
 * módulo de identidade). {@code ANONYMOUS_LGPD_DELETED} é terminal.
 */
public enum EstadoContaLgpd {
    PENDING_VERIFICATION, ACTIVE, SUSPENDED, PENDING_DELETION, ANONYMOUS_LGPD_DELETED;

    public boolean podeSolicitarExclusao() {
        return this == ACTIVE || this == PENDING_VERIFICATION || this == SUSPENDED;
    }
}
