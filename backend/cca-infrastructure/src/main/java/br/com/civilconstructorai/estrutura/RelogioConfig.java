package br.com.civilconstructorai.estrutura;

import br.com.civilconstructorai.comum.Relogio;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Relógio padrão do sistema quando nenhum outro módulo registrou um. */
@Configuration
public class RelogioConfig {

    @Bean
    @ConditionalOnMissingBean(Relogio.class)
    Relogio relogioSistemaEstrutura() {
        return Relogio.sistema();
    }
}
