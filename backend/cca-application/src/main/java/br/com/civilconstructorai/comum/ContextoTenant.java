package br.com.civilconstructorai.comum;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;

/**
 * Tenant (empresa) da transação corrente. A infraestrutura lê daqui para executar
 * {@code set_config('app.tenant_id', …, true)} no início de cada transação (RLS, RF21).
 * Jobs de sistema usam {@link #comoSistema(Callable)} — a política de RLS permite o papel de sistema.
 */
public final class ContextoTenant {

    private static final ThreadLocal<UUID> TENANT = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> SISTEMA = ThreadLocal.withInitial(() -> false);

    private ContextoTenant() {
    }

    public static Optional<UUID> atual() {
        return Optional.ofNullable(TENANT.get());
    }

    public static boolean sistema() {
        return SISTEMA.get();
    }

    public static void definir(UUID tenantId) {
        TENANT.set(tenantId);
    }

    public static void limpar() {
        TENANT.remove();
        SISTEMA.remove();
    }

    public static <T> T comTenant(UUID tenantId, Callable<T> acao) throws Exception {
        var anterior = TENANT.get();
        TENANT.set(tenantId);
        try {
            return acao.call();
        } finally {
            if (anterior == null) {
                TENANT.remove();
            } else {
                TENANT.set(anterior);
            }
        }
    }

    public static <T> T comoSistema(Callable<T> acao) throws Exception {
        SISTEMA.set(true);
        try {
            return acao.call();
        } finally {
            SISTEMA.remove();
        }
    }
}
