package br.com.civilconstructorai.persistencia;

import br.com.civilconstructorai.comum.ContextoTenant;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;

/**
 * Segunda camada do tenant (RLS, RF21): logo depois do BEGIN executa
 * {@code set_config('app.tenant_id' | 'app.sistema' | 'app.conta_id', …, true)} — <em>local</em> à transação —
 * para refletir mudanças de contexto feitas depois da retirada da conexão (ex.: {@code comoSistema} dentro de uma
 * transação já aberta não é suportado; abra uma transação nova). A primeira camada é o {@link TenantDataSource}.
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
        em.createNativeQuery("select set_config('app.tenant_id', :tenant, true), set_config('app.sistema', :sistema, true), set_config('app.conta_id', :conta, true)")
                .setParameter("tenant", ContextoTenant.atual().map(Object::toString).orElse(""))
                .setParameter("sistema", ContextoTenant.sistema() ? "on" : "off")
                .setParameter("conta", ContextoTenant.conta().map(Object::toString).orElse(""))
                .getSingleResult();
    }
}
