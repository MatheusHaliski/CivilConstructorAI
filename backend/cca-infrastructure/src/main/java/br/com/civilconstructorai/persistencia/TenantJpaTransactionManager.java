package br.com.civilconstructorai.persistencia;

import br.com.civilconstructorai.comum.ContextoTenant;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;

/**
 * Define o tenant na conexão a cada transação (RLS, RF21): logo depois do BEGIN executa
 * {@code set_config('app.tenant_id', <uuid>, true)} — <em>local</em> à transação, então uma conexão devolvida
 * ao pool nunca carrega o tenant de outra requisição. Jobs de sistema marcam {@code app.sistema = on}.
 */
public class TenantJpaTransactionManager extends JpaTransactionManager {

    public TenantJpaTransactionManager(EntityManagerFactory emf) {
        super(emf);
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        super.doBegin(transaction, definition);
        var em = EntityManagerFactoryUtils.getTransactionalEntityManager(getEntityManagerFactory());
        if (em == null) {
            return;
        }
        var tenant = ContextoTenant.atual().map(Object::toString).orElse("");
        em.createNativeQuery("select set_config('app.tenant_id', :tenant, true)")
                .setParameter("tenant", tenant)
                .getSingleResult();
        em.createNativeQuery("select set_config('app.sistema', :sistema, true)")
                .setParameter("sistema", ContextoTenant.sistema() ? "on" : "off")
                .getSingleResult();
    }
}
