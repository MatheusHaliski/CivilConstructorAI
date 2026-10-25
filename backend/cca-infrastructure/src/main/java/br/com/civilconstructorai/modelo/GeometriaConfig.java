package br.com.civilconstructorai.modelo;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Laço 2 local (Java/JTS) a menos que {@code app.geometria.remota=true} (serviço Python, cca-infrastructure). */
@Configuration
public class GeometriaConfig {

    @Bean
    @ConditionalOnProperty(name = "app.geometria.remota", havingValue = "false", matchIfMissing = true)
    ServicoGeometria servicoGeometriaLocal() {
        return new ServicoGeometriaLocal();
    }
}
