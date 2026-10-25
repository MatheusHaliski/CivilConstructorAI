package br.com.civilconstructorai.seguranca;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.security.Security;
import java.util.Map;

/** Argon2id (BouncyCastle) atrás de um DelegatingPasswordEncoder, para permitir migração futura de parâmetros. */
@Configuration
public class SenhaConfig {

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        // saltLength 16, hashLength 32, parallelism 1, memory 64 MiB, iterations 3 (OWASP 2024)
        var argon2 = new Argon2PasswordEncoder(16, 32, 1, 65_536, 3);
        return new DelegatingPasswordEncoder("argon2id", Map.of("argon2id", argon2));
    }
}
