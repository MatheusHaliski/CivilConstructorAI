package br.com.civilconstructorai.comum;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

import java.util.UUID;

/**
 * Entidade pertencente a uma empresa (tenant). A coluna {@code tenant_id} é usada pela política de
 * Row-Level Security do PostgreSQL (RF21): a conexão define {@code app.tenant_id} a cada transação e o banco
 * só devolve linhas daquele tenant — mesmo que a aplicação esqueça o filtro.
 */
@MappedSuperclass
public abstract class EntidadeTenant extends EntidadeBase {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    protected EntidadeTenant() {
    }

    protected EntidadeTenant(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getTenantId() {
        return tenantId;
    }
}
