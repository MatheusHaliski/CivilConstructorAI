package br.com.civilconstructorai.identidade;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Os repositórios de identidade/empresa/obra são interfaces aninhadas em {@code Repositorios} (uma por módulo);
 * a varredura padrão ({@code PersistenciaConfig}) ignora interfaces aninhadas, então este módulo registra as suas aqui
 * — mesmo padrão de {@code LgpdInfraConfig}. Os nomes de bean vêm de {@code @Repository("<modulo><Nome>")}.
 */
@Configuration
@EnableJpaRepositories(
        basePackages = {"br.com.civilconstructorai.identidade", "br.com.civilconstructorai.empresa", "br.com.civilconstructorai.obra"},
        considerNestedRepositories = true)
public class IdentidadeInfraConfig {
}
