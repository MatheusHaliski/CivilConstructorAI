package br.com.civilconstructorai.estrutura.carga;

import br.com.civilconstructorai.comum.ExcecaoDominio;

/**
 * NBR 6123: Vk = V0·S1·S2·S3; q = 0,613·Vk² (N/m², com Vk em m/s). A força por pavimento usa a área de
 * fachada tributária e o coeficiente de arrasto Ca.
 */
public record ParametrosVento(double v0, double s1, double s2, double s3, double ca) {

    public ParametrosVento {
        if (v0 <= 0 || v0 > 70) {
            throw new ExcecaoDominio.Validacao("v0", "Velocidade básica V0 deve estar entre 0 e 70 m/s (isopletas da NBR 6123).");
        }
        if (s1 <= 0 || s2 <= 0 || s3 <= 0 || ca <= 0) {
            throw new ExcecaoDominio.Validacao("fatores", "S1, S2, S3 e Ca devem ser maiores que zero.");
        }
    }

    public static ParametrosVento padrao(double v0) {
        return new ParametrosVento(v0, 1.0, 1.0, 1.0, 1.2);
    }

    public double velocidadeCaracteristica() {
        return v0 * s1 * s2 * s3;
    }

    /** pressão dinâmica em kN/m² */
    public double pressaoDinamica() {
        var vk = velocidadeCaracteristica();
        return 0.613 * vk * vk / 1000.0;
    }

    /** força de arrasto (kN) numa área de fachada (m²) */
    public double forca(double areaFachada) {
        return ca * pressaoDinamica() * areaFachada;
    }
}
