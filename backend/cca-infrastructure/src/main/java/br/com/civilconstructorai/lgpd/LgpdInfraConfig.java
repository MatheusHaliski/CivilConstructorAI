package br.com.civilconstructorai.lgpd;

import br.com.civilconstructorai.comum.Relogio;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({LgpdConfigProps.class, VersoesLegaisProps.class})
public class LgpdInfraConfig {

    /** relógio do sistema; testes substituem por um relógio controlado */
    @Bean
    @ConditionalOnMissingBean(Relogio.class)
    Relogio relogio() {
        return Relogio.sistema();
    }
}
