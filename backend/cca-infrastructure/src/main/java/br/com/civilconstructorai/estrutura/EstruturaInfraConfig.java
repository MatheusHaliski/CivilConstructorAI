package br.com.civilconstructorai.estrutura;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Os repositórios de estrutura, geotecnia, quantitativo e orçamento (RF04–RF12) são interfaces aninhadas em
 * {@code Repositorios} (uma classe por módulo) ou no serviço; a varredura padrão ({@code PersistenciaConfig})
 * ignora interfaces aninhadas, então este módulo registra as suas aqui — mesmo padrão de {@code LgpdInfraConfig}
 * e {@code IdentidadeInfraConfig}. Os nomes de bean vêm de {@code @Repository("<modulo><Nome>")}.
 */
@Configuration
@EnableJpaRepositories(
        basePackages = {"br.com.civilconstructorai.estrutura", "br.com.civilconstructorai.geotecnia",
                "br.com.civilconstructorai.quantitativo", "br.com.civilconstructorai.orcamento"},
        considerNestedRepositories = true)
public class EstruturaInfraConfig {
}
