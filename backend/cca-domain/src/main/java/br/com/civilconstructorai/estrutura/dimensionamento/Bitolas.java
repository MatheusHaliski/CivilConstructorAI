package br.com.civilconstructorai.estrutura.dimensionamento;

import br.com.civilconstructorai.notacao.CatalogoMateriais;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Bitolas por categoria de aço, lidas do catálogo técnico (CA-50: barras a partir de 6,3 mm; CA-60: fio de 5,0 mm),
 * e escolha de arranjos. Área e massa nominal saem da geometria (π·φ²/4; 7850 kg/m³). Auditoria 2026-10: a lista
 * anterior trazia "CA-50 Ø 5,0 mm", diâmetro que a NBR 7480 classifica como fio (CA-60), não barra.
 */
public final class Bitolas {

    public record Bitola(double mm, double areaCm2, double kgPorMetro, String categoria) {
        public Bitola(double mm, double areaCm2, double kgPorMetro) {
            this(mm, areaCm2, kgPorMetro, "CA-50");
        }
    }

    public static final List<Bitola> CA50 = daCategoria("MAT.ACO_ARMADURA.CA50", "CA-50");
    public static final List<Bitola> CA60 = daCategoria("MAT.ACO_ARMADURA.CA60", "CA-60");

    private static List<Bitola> daCategoria(String codigo, String rotulo) {
        var out = new ArrayList<Bitola>();
        for (var b : CatalogoMateriais.padrao().bitolas(codigo)) {
            out.add(new Bitola(b.mm(), Math.round(b.areaCm2() * 1000) / 1000.0, Math.round(b.kgPorMetro() * 1000) / 1000.0, rotulo));
        }
        return List.copyOf(out);
    }

    public record Arranjo(int barras, Bitola bitola, double areaCm2) {
        public String descricao() {
            return barras + " Ø " + bitola.mm() + " mm " + bitola.categoria() + " (" + String.format(Locale.ROOT, "%.2f", areaCm2) + " cm²)";
        }
    }

    private Bitolas() {
    }

    /** menor área que atende, de minBarras a 8 barras (até 12 se preciso), bitolas CA-50 ≥ bitolaMinMm */
    public static Arranjo escolher(double asNecessariaCm2, int minBarras, double bitolaMinMm) {
        Arranjo melhor = null;
        for (var b : CA50) {
            if (b.mm() < bitolaMinMm) {
                continue;
            }
            int n = Math.max(minBarras, (int) Math.ceil(asNecessariaCm2 / b.areaCm2() - 1e-9));
            if (n > 12) {
                continue;
            }
            var arranjo = new Arranjo(n, b, Math.round(n * b.areaCm2() * 100) / 100.0);
            double folga = arranjo.areaCm2() - asNecessariaCm2;
            if (melhor == null || (n <= 8 && (melhor.barras() > 8 || folga < melhor.areaCm2() - asNecessariaCm2))) {
                melhor = arranjo;
            }
        }
        if (melhor == null) {
            var b = CA50.get(CA50.size() - 1);
            int n = (int) Math.ceil(asNecessariaCm2 / b.areaCm2());
            melhor = new Arranjo(n, b, n * b.areaCm2());
        }
        return melhor;
    }

    /** estribo: Asw/s (cm²/m) → bitola de 2 ramos e espaçamento (cm) limitado a sMax; fio CA-60 de 5,0 mm ou barras CA-50 */
    public record Estribo(double mm, double espacamentoCm, double aswPorMetroCm2) {
        public String descricao() {
            return "Ø " + mm + " mm " + (mm < 6.3 ? "CA-60" : "CA-50") + " c/ " + String.format(Locale.ROOT, "%.1f", espacamentoCm) + " cm (2 ramos)";
        }
    }

    public static Estribo estribo(double aswSobreSCm2PorM, double sMaxCm) {
        return estribo(aswSobreSCm2PorM, sMaxCm, 0);
    }

    /** {@code phiMinMm}: φt ≥ máx(5 mm; φl/4) nos pilares (18.4.3) */
    public static Estribo estribo(double aswSobreSCm2PorM, double sMaxCm, double phiMinMm) {
        var opcoes = new ArrayList<Bitola>(CA60);
        CA50.stream().filter(b -> b.mm() <= 10).forEach(opcoes::add);
        for (var b : opcoes) {
            if (b.mm() < phiMinMm) {
                continue;
            }
            double s = 2 * b.areaCm2() / Math.max(aswSobreSCm2PorM, 1e-6) * 100; // cm
            s = Math.floor(Math.min(s, sMaxCm) / 2.5) * 2.5;
            if (s >= 7.5) {
                return new Estribo(b.mm(), s, 2 * b.areaCm2() / (s / 100));
            }
        }
        return new Estribo(10.0, 7.5, 2 * 0.785 / 0.075);
    }

    /** malha de laje: bitola e espaçamento (m) com área ≥ As (cm²/m), s ≤ mín(2h; 20 cm) e φ ≤ h/8 (NBR 6118, 20.1) */
    public record Malha(Bitola bitola, double espacamentoM, double areaCm2PorM) {
        public String descricao() {
            return "Ø " + bitola.mm() + " mm " + bitola.categoria() + " c/ " + String.format(Locale.ROOT, "%.1f", espacamentoM * 100) + " cm ("
                    + String.format(Locale.ROOT, "%.2f", areaCm2PorM) + " cm²/m)";
        }
    }

    public static Malha malha(double asCm2PorM, double hLaje) {
        double sMax = Math.min(2 * hLaje, 0.20);
        var opcoes = new ArrayList<Bitola>(CA60);
        CA50.stream().filter(b -> b.mm() <= 12.5).forEach(opcoes::add);
        Malha melhor = null;
        for (var b : opcoes) {
            if (b.mm() / 1000 > hLaje / 8 + 1e-9) {
                continue;
            }
            for (double s = Math.floor(sMax / 0.025) * 0.025; s >= 0.075 - 1e-9; s -= 0.025) {
                double area = b.areaCm2() / s;
                if (area >= asCm2PorM - 1e-9) {
                    var m = new Malha(b, Math.round(s * 1000) / 1000.0, Math.round(area * 100) / 100.0);
                    if (melhor == null || m.areaCm2PorM() < melhor.areaCm2PorM()) {
                        melhor = m;
                    }
                    break;
                }
            }
        }
        return melhor;
    }
}
