package br.com.civilconstructorai.seguranca;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Base64;

/**
 * Segredo-mestre do app ({@code CCA_APP_SECRET}, Base64, 32+ bytes). Obrigatório fora de {@code dev}/{@code it}
 * (validado em {@link SegredoAppValidador}). Subchaves por finalidade saem por HKDF ({@link DerivacaoChaves}).
 */
@ConfigurationProperties(prefix = "app.seguranca")
public record SegredoApp(String appSecret) {

    public byte[] bytes() {
        if (appSecret == null || appSecret.isBlank()) {
            throw new IllegalStateException("app.seguranca.app-secret (CCA_APP_SECRET) não definido.");
        }
        var b = Base64.getDecoder().decode(appSecret.trim());
        if (b.length < 32) {
            throw new IllegalStateException("CCA_APP_SECRET precisa ter 32+ bytes (Base64).");
        }
        return b;
    }
}
