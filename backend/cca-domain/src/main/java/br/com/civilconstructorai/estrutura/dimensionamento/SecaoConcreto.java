package br.com.civilconstructorai.estrutura.dimensionamento;

import java.util.ArrayList;
import java.util.List;

/**
 * Seção retangular de concreto armado com barras discretas, para flexo-compressão (NBR 6118:2023, 17.2.2 e 17.2.3):
 * diagrama retangular de tensões 0,85·fcd em 0,8·x (fck ≤ 50 MPa), domínios de deformação com pivôs A (aço a 10 ‰
 * de alongamento), B (concreto a 3,5 ‰) e C (2 ‰ a 3h/7 da face comprimida), aço elastoplástico perfeito.
 * Coordenadas: u ao longo de b (eixo X do pilar), v ao longo de h (eixo Y do pilar), origem no centro. Unidades:
 * m, kN, MPa na entrada; esforços em kN e kN·m. Simplificação declarada: a área de concreto ocupada pelas barras
 * comprimidas não é descontada.
 */
public final class SecaoConcreto {

    public static final double EPS_CU = 0.0035;
    public static final double EPS_C2 = 0.002;
    public static final double EPS_SU = 0.010;

    public record Barra(double u, double v, double areaM2) {
    }

    public enum Direcao { X, Y }

    private final double b;
    private final double h;
    private final double fcdKpa;
    private final double fydKpa;
    private final double esKpa;
    private final List<Barra> barras;

    public SecaoConcreto(double b, double h, double fckMpa, double fykMpa, double esMpa, List<Barra> barras) {
        if (fckMpa > 50) {
            throw new IllegalArgumentException("Diagrama retangular simplificado só vale para fck ≤ 50 MPa.");
        }
        this.b = b;
        this.h = h;
        this.fcdKpa = fckMpa / Nbr6118.GAMA_C * 1000;
        this.fydKpa = fykMpa / Nbr6118.GAMA_S * 1000;
        this.esKpa = esMpa * 1000;
        this.barras = List.copyOf(barras);
    }

    /**
     * n barras de diâmetro φ (mm) distribuídas no perímetro (cantos + faces), com o centro a {@code dLinha} das faces.
     * Faces longas recebem as barras intermediárias primeiro, mantendo o espaçamento o mais uniforme possível.
     */
    public static List<Barra> distribuir(double b, double h, int n, double phiMm, double dLinha) {
        if (n < 4 || n % 2 != 0) {
            throw new IllegalArgumentException("Número de barras do pilar deve ser par e ≥ 4.");
        }
        double area = Math.PI * phiMm * phiMm / 4 * 1e-6;
        double eu = b / 2 - dLinha, ev = h / 2 - dLinha;
        // nb barras ao longo de b (faces paralelas a b), nh ao longo de h, contando os cantos nas duas
        int melhorNb = 2, melhorNh = 2;
        double melhor = Double.MAX_VALUE;
        for (int nb = 2; nb <= n / 2; nb++) {
            int nh = (n + 4) / 2 - nb;
            if (nh < 2) {
                continue;
            }
            double sb = 2 * eu / (nb - 1), sh = 2 * ev / (nh - 1);
            double dif = Math.abs(sb - sh);
            if (dif < melhor) {
                melhor = dif;
                melhorNb = nb;
                melhorNh = nh;
            }
        }
        var out = new ArrayList<Barra>();
        for (int i = 0; i < melhorNb; i++) {
            double u = -eu + 2 * eu * i / (melhorNb - 1);
            out.add(new Barra(u, -ev, area));
            out.add(new Barra(u, ev, area));
        }
        for (int j = 1; j < melhorNh - 1; j++) {
            double v = -ev + 2 * ev * j / (melhorNh - 1);
            out.add(new Barra(-eu, v, area));
            out.add(new Barra(eu, v, area));
        }
        return out;
    }

    public List<Barra> barras() {
        return barras;
    }

    /** maior esforço normal de compressão resistente (deformação uniforme de 2 ‰) */
    public double nRdMax() {
        return 0.85 * fcdKpa * b * h + barras.stream().mapToDouble(x -> x.areaM2() * tensaoAco(EPS_C2)).sum();
    }

    /** maior tração resistente (só o aço) */
    public double nRdMin() {
        return -barras.stream().mapToDouble(x -> x.areaM2() * fydKpa).sum();
    }

    /**
     * Momento resistente na direção dada para o esforço normal de cálculo Nd (kN, compressão positiva). NaN quando
     * Nd está fora do intervalo resistente da seção.
     */
    public double mRd(double nd, Direcao dir) {
        if (nd > nRdMax() || nd < nRdMin()) {
            return Double.NaN;
        }
        double prof = dir == Direcao.X ? b : h;
        double lo = 1e-7 * prof, hi = 1e4 * prof;
        if (nd < esforcos(lo, dir)[0]) {
            return Double.NaN; // tração além do domínio 2 modelado (domínio 1 não implementado)
        }
        for (int it = 0; it < 200; it++) {
            double mid = Math.sqrt(lo * hi);
            if (esforcos(mid, dir)[0] < nd) {
                lo = mid;
            } else {
                hi = mid;
            }
            if (hi / lo < 1 + 1e-12) {
                break;
            }
        }
        return esforcos(Math.sqrt(lo * hi), dir)[1];
    }

    /** [N, M] para linha neutra a x (m) da face comprimida, na direção dada */
    double[] esforcos(double x, Direcao dir) {
        double prof = dir == Direcao.X ? b : h, largura = dir == Direcao.X ? h : b;
        // profundidade de cada barra a partir da face comprimida (lado positivo da coordenada)
        var y = new double[barras.size()];
        double yMax = 0;
        for (int i = 0; i < y.length; i++) {
            double c = dir == Direcao.X ? barras.get(i).u() : barras.get(i).v();
            y[i] = prof / 2 - c;
            yMax = Math.max(yMax, y[i]);
        }
        double a = Math.min(0.8 * x, prof);
        double nc = 0.85 * fcdKpa * largura * a;
        double n = nc, m = nc * (prof / 2 - a / 2);
        for (int i = 0; i < y.length; i++) {
            double eps = deformacao(x, y[i], prof, yMax);
            double f = barras.get(i).areaM2() * tensaoAco(eps);
            n += f;
            m += f * (prof / 2 - y[i]);
        }
        return new double[]{n, m};
    }

    /** deformação (compressão +) à profundidade yi, pelos pivôs A, B e C */
    private static double deformacao(double x, double yi, double prof, double dMax) {
        if (x > prof) {
            // domínio 5: pivô C a 3h/7, deformação de 2 ‰
            return EPS_C2 * (x - yi) / (x - 3 * prof / 7);
        }
        double eps = EPS_CU * (x - yi) / x;
        double epsAcoMaisTracionado = EPS_CU * (x - dMax) / x;
        if (epsAcoMaisTracionado < -EPS_SU) {
            // domínio 2 (e 1): pivô A, aço mais tracionado a 10 ‰
            return -EPS_SU * (x - yi) / (x - dMax);
        }
        return eps;
    }

    private double tensaoAco(double eps) {
        return Math.max(-fydKpa, Math.min(fydKpa, esKpa * eps));
    }
}
