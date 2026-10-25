package br.com.civilconstructorai.config;

import br.com.civilconstructorai.comum.Relogio;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Beans comuns da aplicação: relógio do sistema e TransactionTemplate (quem troca de tenant usa). */
@Configuration
public class BeansComuns {

    @Bean
    @ConditionalOnMissingBean
    Relogio relogio() {
        return Relogio.sistema();
    }

    @Bean
    @ConditionalOnMissingBean
    TransactionTemplate transactionTemplate(PlatformTransactionManager tm) {
        return new TransactionTemplate(tm);
    }
}
