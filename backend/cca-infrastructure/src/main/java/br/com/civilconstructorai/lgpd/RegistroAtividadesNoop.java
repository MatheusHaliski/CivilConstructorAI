package br.com.civilconstructorai.lgpd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

/**
 * Registro de atividades (Cassandra, profile {@code full}). Sem Cassandra configurado, o passo 3 da saga é
 * um no-op registrado em log — a implementação com Spring Data Cassandra substitui este bean.
 */
@Configuration
public class RegistroAtividadesNoop {

    private static final Logger log = LoggerFactory.getLogger(RegistroAtividadesNoop.class);

    @Bean
    @ConditionalOnMissingBean(Portas.RegistroAtividades.class)
    Portas.RegistroAtividades registroAtividades() {
        return new Portas.RegistroAtividades() {
            @Override
            public void limparUsuario(UUID contaId) {
                log.debug("Cassandra ausente: nada a limpar para a conta (hash omitido)");
            }
        };
    }
}
