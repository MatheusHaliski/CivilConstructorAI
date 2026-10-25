package br.com.civilconstructorai.persistencia;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableTransactionManagement
@EntityScan(basePackages = "br.com.civilconstructorai")
// único ponto de registro de repositórios: as interfaces ficam aninhadas em `Repositorios` de cada módulo
@EnableJpaRepositories(basePackages = "br.com.civilconstructorai", considerNestedRepositories = true)
public class PersistenciaConfig {

    @Bean
    PlatformTransactionManager transactionManager(EntityManagerFactory emf) {
        return new TenantJpaTransactionManager(emf);
    }
}
