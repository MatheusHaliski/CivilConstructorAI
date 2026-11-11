package br.com.civilconstructorai.estrutura.dimensionamento;

import java.util.List;

/** Bitolas comerciais CA-50 (mm) e áreas (cm²); escolha de arranjo para uma área de aço necessária. */
public final class Bitolas {

    public record Bitola(double mm, double areaCm2, double kgPorMetro) {
    }

    public static final List<Bitola> CA50 = List.of(
            new Bitola(5.0, 0.196, 0.154), new Bitola(6.3, 0.312, 0.245), new Bitola(8.0, 0.503, 0.395),
            new Bitola(10.0, 0.785, 0.617), new Bitola(12.5, 1.227, 0.963), new Bitola(16.0, 2.011, 1.578),
            new Bitola(20.0, 3.142, 2.466), new Bitola(25.0, 4.909, 3.853));

    public record Arranjo(int barras, Bitola bitola, double areaCm2) {
        public String descricao() {
            return barras + " Ø " + bitola.mm() + " mm (" + String.format(java.util.Locale.ROOT, "%.2f", areaCm2) + " cm²)";
        }
    }

    private Bitolas() {
    }

    /** menor área que atende, preferindo de 2 a 8 barras e bitolas de 10 a 20 mm para vigas/pilares */
    public static Arranjo escolher(double asNecessariaCm2, int minBarras, double bitolaMinMm) {
        if (!Double.isFinite(asNecessariaCm2) || asNecessariaCm2 < 0 || minBarras < 1 || minBarras > 12
                || !Double.isFinite(bitolaMinMm) || bitolaMinMm <= 0) {
            throw new IllegalArgumentException("Parâmetros inválidos para escolha de armadura longitudinal.");
        }
        Arranjo melhor = null;
        for (var b : CA50) {
            if (b.mm() < bitolaMinMm) {
                continue;
            }
            int n = Math.max(minBarras, (int) Math.ceil(asNecessariaCm2 / b.areaCm2()));
            if (n > 12 || n * b.areaCm2() < asNecessariaCm2) {
                continue;
            }
            var arranjo = new Arranjo(n, b, n * b.areaCm2());
            double folga = arranjo.areaCm2() - asNecessariaCm2;
            if (melhor == null || (n <= 8 && (melhor.barras() > 8 || folga < melhor.areaCm2() - asNecessariaCm2))) {
                melhor = arranjo;
            }
        }
        if (melhor == null) {
            throw new IllegalArgumentException("Armadura longitudinal inviável no catálogo de até 12 barras.");
        }
        return melhor;
    }

    /** estribo: Asw/s (cm²/m) → bitola de 2 ramos e espaçamento (cm) limitado a sMax */
    public record Estribo(double mm, double espacamentoCm, double aswPorMetroCm2) {
        public String descricao() {
            return "Ø " + mm + " mm c/ " + String.format(java.util.Locale.ROOT, "%.1f", espacamentoCm) + " cm (2 ramos)";
        }
    }

    public static Estribo estribo(double aswSobreSCm2PorM, double sMaxCm) {
        if (!Double.isFinite(aswSobreSCm2PorM) || aswSobreSCm2PorM < 0
                || !Double.isFinite(sMaxCm) || sMaxCm <= 0) {
            throw new IllegalArgumentException("Parâmetros inválidos para escolha de estribo.");
        }
        for (var b : CA50) {
            if (b.mm() > 10) {
                break;
            }
            double s = 2 * b.areaCm2() / Math.max(aswSobreSCm2PorM, 1e-6) * 100; // cm
            s = Math.floor(Math.min(s, sMaxCm) / 2.5) * 2.5;
            if (s >= 7.5 && s <= sMaxCm && 2 * b.areaCm2() / (s / 100) >= aswSobreSCm2PorM) {
                return new Estribo(b.mm(), s, 2 * b.areaCm2() / (s / 100));
            }
        }
        throw new IllegalArgumentException("Estribo inviável: nenhum arranjo do catálogo atende à área e ao espaçamento máximo.");
    }
}
