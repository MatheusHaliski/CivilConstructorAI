package br.com.civilconstructorai.lgpd;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code app.legal.termos.versao} / {@code app.legal.politica.versao}: nova versão exige reaceite (RF24). */
@ConfigurationProperties(prefix = "app.legal")
public class VersoesLegaisProps implements Portas.VersoesLegais {

    private Doc termos = new Doc();
    private Doc politica = new Doc();

    public static class Doc {
        private String versao = "1.0";

        public String getVersao() {
            return versao;
        }

        public void setVersao(String versao) {
            this.versao = versao;
        }
    }

    public Doc getTermos() {
        return termos;
    }

    public void setTermos(Doc termos) {
        this.termos = termos;
    }

    public Doc getPolitica() {
        return politica;
    }

    public void setPolitica(Doc politica) {
        this.politica = politica;
    }

    @Override
    public String termos() {
        return termos == null || termos.versao == null ? "1.0" : termos.versao;
    }

    @Override
    public String politica() {
        return politica == null || politica.versao == null ? "1.0" : politica.versao;
    }
}
