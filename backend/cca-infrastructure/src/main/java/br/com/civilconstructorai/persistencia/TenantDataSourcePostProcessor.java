package br.com.civilconstructorai.persistencia;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/** Envolve o {@code DataSource} principal (Hikari) em {@link TenantDataSource}; o DataSource do Flyway não passa aqui. */
@Component
public class TenantDataSourcePostProcessor implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if ("dataSource".equals(beanName) && bean instanceof DataSource ds && !(bean instanceof TenantDataSource)) {
            return new TenantDataSource(ds);
        }
        return bean;
    }
}
