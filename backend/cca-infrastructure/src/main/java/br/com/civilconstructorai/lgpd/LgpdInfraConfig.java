package br.com.civilconstructorai.lgpd;

import br.com.civilconstructorai.comum.Relogio;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableConfigurationProperties({LgpdConfigProps.class, VersoesLegaisProps.class})
/*
 * Os repositórios de lgpd/ia são interfaces aninhadas em {@code Repositorios}; a varredura padrão
 * ({@code PersistenciaConfig}) ignora interfaces aninhadas, então este módulo registra as suas aqui.
 */
@EnableJpaRepositories(
        basePackages = {"br.com.civilconstructorai.lgpd", "br.com.civilconstructorai.ia"},
        considerNestedRepositories = true)
public class LgpdInfraConfig {

    /** relógio do sistema; testes substituem por um relógio controlado */
    @Bean
    @ConditionalOnMissingBean(Relogio.class)
    Relogio relogio() {
        return Relogio.sistema();
    }
}
