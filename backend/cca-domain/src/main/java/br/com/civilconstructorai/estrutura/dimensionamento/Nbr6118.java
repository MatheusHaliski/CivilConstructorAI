package br.com.civilconstructorai.estrutura.dimensionamento;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Dimensionamento em concreto armado pela NBR 6118:2023 (seções retangulares, CA-50). Unidades de entrada em
 * kN, m e MPa; áreas de aço em cm². Cada método devolve também a memória de cálculo em pt-BR.
 */
public final class Nbr6118 {

    public static final double GAMA_C = 1.4;
    public static final double GAMA_S = 1.15;
    public static final double GAMA_F = 1.4;
    public static final double FYK = 500.0;
    public static final double ES = 210_000.0;

    private Nbr6118() {
    }

    public record Memoria(List<String> linhas) {
        public Memoria add(String fmt, Object... args) {
            linhas.add(String.format(Locale.ROOT, fmt, args));
            return this;
        }

        public static Memoria nova() {
            return new Memoria(new ArrayList<>());
        }
    }

    public record Flexao(boolean verifica, String motivo, double asCm2, double asMinCm2, double xSobreD, double dominio, Memoria memoria) {
    }

    public record Cisalhamento(boolean verifica, String motivo, double aswPorMetroCm2, double aswMinPorMetroCm2, double vrd2, double vc, double sMaxCm, Memoria memoria) {
    }

    public record Pilar(boolean verifica, String motivo, double asCm2, double asMinCm2, double asMaxCm2, double esbeltez, Memoria memoria) {
    }

    public record Flecha(boolean verifica, double flechaMm, double limiteMm, double ieqSobreIc, Memoria memoria) {
    }

    public record Fissuracao(boolean verifica, double wkMm, double limiteMm, Memoria memoria) {
    }

    public static double fcd(double fck) {
        return fck / GAMA_C;
    }

    public static double fyd() {
        return FYK / GAMA_S;
    }

    public static double fctm(double fck) {
        return fck <= 50 ? 0.3 * Math.pow(fck, 2.0 / 3) : 2.12 * Math.log(1 + 0.11 * fck);
    }

    public static double fctkInf(double fck) {
        return 0.7 * fctm(fck);
    }

    public static double fctd(double fck) {
        return fctkInf(fck) / GAMA_C;
    }

    /** taxa mínima de armadura de flexão (17.3.5.2.1), CA-50, em % de Ac */
    public static double rhoMin(double fck) {
        if (fck <= 30) return 0.150;
        if (fck <= 35) return 0.164;
        if (fck <= 40) return 0.179;
        if (fck <= 45) return 0.194;
        return 0.208;
    }

    /**
     * Flexão simples, seção retangular b×h (m), d (m), momento característico Mk (kN·m), fck (MPa).
     * Diagrama retangular 0,8x · 0,85 fcd; limite x/d ≤ 0,45 (fck ≤ 50) para ductilidade (14.6.4.3).
     */
    public static Flexao flexaoSimples(double mk, double b, double h, double d, double fck) {
        var mem = Memoria.nova();
        double md = GAMA_F * mk;
        mem.add("Md = γf·Mk = %.2f·%.2f = %.2f kN·m", GAMA_F, mk, md);
        return flexao(md, b, h, d, fck, FYK, mem);
    }

    /** flexão simples a partir do momento de cálculo Md (kN·m) já combinado (envoltória ELU) e do fyk do aço escolhido */
    public static Flexao flexaoMd(double md, double b, double h, double d, double fck, double fyk) {
        var mem = Memoria.nova();
        mem.add("Md = %.2f kN·m (envoltória das combinações ELU, já com γf)", md);
        return flexao(md, b, h, d, fck, fyk, mem);
    }

    private static Flexao flexao(double md, double b, double h, double d, double fck, double fyk, Memoria mem) {
        double fyd = fyk / GAMA_S;
        double fcdKpa = fcd(fck) * 1000, fydKpa = fyd * 1000;
        mem.add("fcd = %.1f/%.2f = %.2f MPa; fyd = %.0f/%.2f = %.1f MPa", fck, GAMA_C, fcd(fck), fyk, GAMA_S, fyd);
        double disc = d * d - 2.353 * md / (fcdKpa * b);
        double asMin = rhoMin(fck) / 100 * b * h * 1e4;
        if (disc < 0) {
            mem.add("d² − 2,353·Md/(fcd·b) < 0: a seção não resiste ao momento com armadura simples.");
            return new Flexao(false, "Seção insuficiente para Md: aumente h ou use armadura dupla.", Double.NaN, asMin, Double.NaN, 4, mem);
        }
        double x = (d - Math.sqrt(disc)) / 0.8;
        double xd = x / d;
        double dominio = xd <= 0.259 ? 2 : 3;
        mem.add("x = (d − √(d² − 2,353·Md/(fcd·b)))/0,8 = %.4f m; x/d = %.3f (domínio %.0f)", x, xd, dominio);
        boolean ductil = xd <= 0.45;
        double as = md / (fydKpa * (d - 0.4 * x)) * 1e4;
        mem.add("As = Md/(fyd·(d − 0,4x)) = %.2f cm²", as);
        mem.add("As,min = ρmin·b·h = %.3f%%·%.0f·%.0f = %.2f cm²", rhoMin(fck), b * 100, h * 100, asMin);
        double asFinal = Math.max(as, asMin);
        double asMax = 0.04 * b * h * 1e4;
        if (!ductil) {
            mem.add("x/d = %.3f > 0,45: viola o limite de ductilidade (14.6.4.3).", xd);
            return new Flexao(false, "x/d > 0,45: aumente a altura útil ou o fck.", asFinal, asMin, xd, dominio, mem);
        }
        if (asFinal > asMax) {
            mem.add("As = %.2f cm² > As,máx = 4%%·Ac = %.2f cm².", asFinal, asMax);
            return new Flexao(false, "Armadura acima de 4 % da seção.", asFinal, asMin, xd, dominio, mem);
        }
        return new Flexao(true, "Verifica", Math.round(asFinal * 100) / 100.0, Math.round(asMin * 100) / 100.0, Math.round(xd * 1000) / 1000.0, dominio, mem);
    }

    /** cisalhamento, modelo de cálculo I (θ = 45°), 17.4.2.2; Vk em kN, bw e d em m */
    public static Cisalhamento cisalhamento(double vk, double bw, double d, double fck) {
        return cisalhamentoVd(GAMA_F * vk, bw, d, fck);
    }

    /** idem, a partir do cortante de cálculo Vd (kN) já combinado */
    public static Cisalhamento cisalhamentoVd(double vsd, double bw, double d, double fck) {
        var mem = Memoria.nova();
        double vk = vsd / GAMA_F;
        double alphaV2 = 1 - fck / 250;
        double vrd2 = 0.27 * alphaV2 * fcd(fck) * 1000 * bw * d;
        double vc = 0.6 * fctd(fck) * 1000 * bw * d;
        double fywd = Math.min(fyd(), 435);
        mem.add("Vsd = %.2f kN (Vk equivalente %.2f kN); VRd2 = 0,27·αv2·fcd·bw·d = %.2f kN; Vc = 0,6·fctd·bw·d = %.2f kN", vsd, vk, vrd2, vc);
        if (vsd > vrd2) {
            mem.add("Vsd > VRd2: esmagamento da biela.");
            return new Cisalhamento(false, "Vsd > VRd2: aumente a seção (biela comprimida).", Double.NaN, Double.NaN, vrd2, vc, 0, mem);
        }
        double vsw = Math.max(0, vsd - vc);
        double aswS = vsw / (0.9 * d * fywd * 1000) * 1e4; // cm²/m
        double aswMin = 0.2 * fctm(fck) / FYK * bw * 1e4; // cm²/m (ρsw,min = 0,2·fctm/fywk)
        double sMax = vsd <= 0.67 * vrd2 ? Math.min(0.6 * d, 0.30) : Math.min(0.3 * d, 0.20);
        mem.add("Vsw = Vsd − Vc = %.2f kN; Asw/s = Vsw/(0,9·d·fywd) = %.2f cm²/m; Asw/s,min = %.2f cm²/m; s,máx = %.0f cm", vsw, aswS, aswMin, sMax * 100);
        return new Cisalhamento(true, "Verifica", Math.round(Math.max(aswS, aswMin) * 100) / 100.0, Math.round(aswMin * 100) / 100.0, Math.round(vrd2 * 10) / 10.0, Math.round(vc * 10) / 10.0, sMax * 100, mem);
    }

    /**
     * Pilar retangular b×h (m), comprimento l (m), normal característica Nk (kN), fck. Método simplificado:
     * excentricidade mínima e1,min = 0,015 + 0,03h, esbeltez λ = 3,46·l/h, momento de 2ª ordem pelo pilar-padrão
     * com curvatura aproximada quando λ > 35, e flexo-compressão pela aproximação Nd ≤ 0,85·fcd·Ac·(1 − 2e/h) + As·fyd.
     */
    public static Pilar pilar(double nk, double b, double h, double l, double fck) {
        var mem = Memoria.nova();
        double nd = GAMA_F * nk;
        double ac = b * h;
        double fcdKpa = fcd(fck) * 1000, fydKpa = fyd() * 1000;
        double e1 = 0.015 + 0.03 * h;
        double lambda = 3.46 * l / h;
        double nu = nd / (ac * fcdKpa);
        double e2 = 0;
        if (lambda > 35) {
            double curv = Math.min(0.005 / (h * (nu + 0.5)), 0.005 / h);
            e2 = l * l / 10 * curv;
        }
        double e = e1 + e2;
        mem.add("Nd = %.2f kN; Ac = %.4f m²; ν = Nd/(Ac·fcd) = %.3f; λ = 3,46·l/h = %.1f", nd, ac, nu, lambda);
        mem.add("e1,min = 0,015 + 0,03h = %.4f m; e2 = %.4f m; e = %.4f m", e1, e2, e);
        if (lambda > 90) {
            mem.add("λ > 90: fora do método simplificado.");
            return new Pilar(false, "Pilar esbelto (λ > 90): exige análise de 2ª ordem refinada.", Double.NaN, 0.004 * ac * 1e4, 0.08 * ac * 1e4, lambda, mem);
        }
        double capConcreto = 0.85 * fcdKpa * ac * Math.max(0, 1 - 2 * e / h);
        double as = Math.max(0, (nd - capConcreto) / (fydKpa - 0.85 * fcdKpa)) * 1e4;
        double asMin = Math.max(0.15 * nd / fydKpa, 0.004 * ac) * 1e4;
        double asMax = 0.08 * ac * 1e4;
        mem.add("As = (Nd − 0,85·fcd·Ac·(1 − 2e/h))/(fyd − 0,85·fcd) = %.2f cm²; As,min = máx(0,15·Nd/fyd; 0,4%%·Ac) = %.2f cm²; As,máx = 8%%·Ac = %.2f cm²", as, asMin, asMax);
        double asFinal = Math.max(as, asMin);
        if (asFinal > asMax) {
            return new Pilar(false, "Armadura acima de 8 % da seção: aumente a seção do pilar.", asFinal, asMin, asMax, lambda, mem);
        }
        if (Math.min(b, h) < 0.19 || ac < 0.036) {
            return new Pilar(false, "Seção abaixo do mínimo (19 cm / 360 cm²).", asFinal, asMin, asMax, lambda, mem);
        }
        return new Pilar(true, "Verifica", Math.round(asFinal * 100) / 100.0, Math.round(asMin * 100) / 100.0, Math.round(asMax * 100) / 100.0, Math.round(lambda * 10) / 10.0, mem);
    }

    /**
     * Flecha imediata + diferida (17.3.2.1): Ieq de Branson com Mr = α·fct·Ic/yt (α = 1,5 retangular),
     * carga quase-permanente q (kN/m), vão L (m), limite L/250.
     */
    public static Flecha flecha(double qQuasePermanente, double l, double b, double h, double d, double asCm2, double fck, double ecsKpa) {
        var mem = Memoria.nova();
        double ic = b * h * h * h / 12;
        double yt = h / 2;
        double mr = 1.5 * fctm(fck) * 1000 * ic / yt;
        double ma = qQuasePermanente * l * l / 8;
        double n = ES * 1000 / ecsKpa;
        double as = asCm2 / 1e4;
        // linha neutra no estádio II: b·x²/2 = n·As·(d − x)
        double aQ = b / 2, bQ = n * as, cQ = -n * as * d;
        double x2 = (-bQ + Math.sqrt(bQ * bQ - 4 * aQ * cQ)) / (2 * aQ);
        double iii = b * x2 * x2 * x2 / 3 + n * as * (d - x2) * (d - x2);
        double razao = Math.min(1, Math.pow(mr / Math.max(ma, 1e-9), 3));
        double ieq = Math.min(ic, razao * ic + (1 - razao) * iii);
        double fImediata = 5 * qQuasePermanente * Math.pow(l, 4) / (384 * ecsKpa * ieq);
        double alphaF = 1.32; // Δξ = ξ(∞) − ξ(1 mês) = 2 − 0,68, sem armadura de compressão
        double fTotal = fImediata * (1 + alphaF);
        double limite = l / 250;
        mem.add("Ic = %.3e m⁴; Mr = %.2f kN·m; Ma = %.2f kN·m; III = %.3e m⁴; Ieq = %.3e m⁴ (%.2f·Ic)", ic, mr, ma, iii, ieq, ieq / ic);
        mem.add("f0 = 5qL⁴/(384·Ecs·Ieq) = %.2f mm; f∞ = f0·(1 + αf) = %.2f mm; limite L/250 = %.2f mm", fImediata * 1000, fTotal * 1000, limite * 1000);
        return new Flecha(fTotal <= limite, Math.round(fTotal * 1e4) / 10.0, Math.round(limite * 1e4) / 10.0, Math.round(ieq / ic * 1000) / 1000.0, mem);
    }

    /** abertura de fissura (17.3.3.2), estimativa pela tensão no aço em serviço; CAA II → wk ≤ 0,3 mm */
    public static Fissuracao fissuracao(double mFrequente, double d, double asCm2, double bitolaMm, double fck, double wkLimiteMm) {
        var mem = Memoria.nova();
        double z = 0.9 * d;
        double sigmaS = mFrequente / (z * asCm2 / 1e4) / 1000; // MPa
        double eta1 = 2.25;
        double wk = (bitolaMm / (12.5 * eta1)) * (sigmaS / ES) * (3 * sigmaS / fctm(fck));
        mem.add("σs = M/(0,9d·As) = %.1f MPa; wk = (φ/(12,5·η1))·(σs/Es)·(3σs/fctm) = %.3f mm ≤ %.1f mm", sigmaS, wk, wkLimiteMm);
        return new Fissuracao(wk <= wkLimiteMm, Math.round(wk * 1000) / 1000.0, wkLimiteMm, mem);
    }
}
