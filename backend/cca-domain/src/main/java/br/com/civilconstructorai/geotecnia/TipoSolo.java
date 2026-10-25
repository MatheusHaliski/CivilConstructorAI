package br.com.civilconstructorai.geotecnia;

/**
 * Tipos de solo com os coeficientes de Aoki-Velloso (K em kPa, α em %) e Décourt-Quaresma (C em kPa).
 */
public enum TipoSolo {
    AREIA(1000, 1.4, 400),
    AREIA_SILTOSA(800, 2.0, 400),
    AREIA_ARGILOSA(600, 3.0, 400),
    SILTE(400, 3.0, 250),
    SILTE_ARENOSO(550, 2.2, 250),
    SILTE_ARGILOSO(230, 3.4, 200),
    ARGILA(200, 6.0, 120),
    ARGILA_ARENOSA(350, 2.4, 120),
    ARGILA_SILTOSA(220, 4.0, 120),
    ATERRO(200, 6.0, 120),
    ROCHA_ALTERADA(1000, 1.4, 400);

    private final double kKpa;
    private final double alfaPct;
    private final double cKpa;

    TipoSolo(double kKpa, double alfaPct, double cKpa) {
        this.kKpa = kKpa;
        this.alfaPct = alfaPct;
        this.cKpa = cKpa;
    }

    public double kKpa() { return kKpa; }
    public double alfaPct() { return alfaPct; }
    public double cKpa() { return cKpa; }
}
