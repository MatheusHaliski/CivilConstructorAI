package br.com.civilconstructorai.seguranca;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.Arrays;

/** Fora dos perfis dev/it o app não sobe sem {@code CCA_APP_SECRET}. */
@Configuration
@EnableConfigurationProperties(SegredoApp.class)
public class SegredoAppValidador {

    public SegredoAppValidador(SegredoApp segredo, Environment env) {
        var perfis = Arrays.asList(env.getActiveProfiles());
        boolean local = perfis.contains("dev") || perfis.contains("it");
        if (!local) {
            segredo.bytes();
        }
    }
}
