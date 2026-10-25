package br.com.civilconstructorai.lgpd;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** {@code app.lgpd.*} — DN-01: nomes da controladora e do Encarregado vêm de configuração, nunca do código. */
@ConfigurationProperties(prefix = "app.lgpd")
public record LgpdConfigProps(
        @DefaultValue("") String controladoraRazaoSocial,
        @DefaultValue("") String controladoraCnpj,
        @DefaultValue("") String dpoNome,
        @DefaultValue("") String dpoEmail,
        @DefaultValue("30") int carenciaDias,
        @DefaultValue("15") int prazoRespostaDias) implements Portas.ConfigLgpd {
}
