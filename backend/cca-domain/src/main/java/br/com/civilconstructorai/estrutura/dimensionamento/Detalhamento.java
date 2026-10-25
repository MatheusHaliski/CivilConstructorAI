package br.com.civilconstructorai.estrutura.dimensionamento;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Detalhamento simplificado das armaduras (posições N1, N2…): diâmetro, quantidade, forma, comprimento unitário,
 * ancoragem, emenda, gancho, massa e a geometria 3D de cada barra. Regras (ABNT NBR 6118:2023, item 9) aplicadas e
 * declaradas em cada posição; o que não é feito fica dito em {@link Resultado#limitacoes()}.
 * <ul>
 *   <li>ancoragem básica lb = (φ/4)·(fyd/fbd), fbd = η1·η2·η3·fctd (9.3.2.1 e 9.4.2.4); lb,nec = α·lb·As,calc/As,ef ≥
 *       lb,min = máx(0,3·lb; 10φ; 100 mm), α = 1,0 (barra reta);</li>
 *   <li>gancho em ângulo reto: ponta reta de 8φ (9.4.2.3); estribo com gancho a 45°: ponta de máx(5φt; 50 mm);</li>
 *   <li>emenda por traspasse l0t = α0t·lb,nec ≥ máx(0,3·α0t·lb; 15φ; 200 mm), α0t = 2,0 (premissa: mais de 50 % das
 *       barras emendadas na mesma seção — o maior valor da tabela, a favor da segurança);</li>
 *   <li>barras comerciais de 12 m: comprimentos maiores recebem emendas.</li>
 * </ul>
 * Massa líquida = Σ quantidade × comprimento × massa nominal (π·φ²/4 × 7850 kg/m³); perdas e quantidade comercial ficam
 * em colunas separadas.
 */
public final class Detalhamento {

    public static final double BARRA_COMERCIAL_M = 12.0;
    public static final double ALFA_0T = 2.0;

    /** uma posição de armadura; {@code geometria}: polilinhas em coordenadas globais (m), uma por barra desenhada */
    public record Posicao(String posicao, String elementoId, String rotuloElemento, String funcao, String categoria, double diametroMm, int quantidade,
                          double comprimentoUnitarioM, String forma, double massaKg, String regra, List<List<double[]>> geometria) {
        public double comprimentoTotalM() {
            return quantidade * comprimentoUnitarioM;
        }
    }

    public record Resultado(List<Posicao> posicoes, double massaLiquidaKg, List<String> limitacoes) {
    }

    /** contexto de cálculo de comprimentos */
    public record Parametros(double fckMpa, double fykMpa, double eta1, String categoria, double cobrimentoM) {
        double fbdMpa(boolean boaAderencia, double phiMm) {
            double fctd = 0.7 * 0.3 * Math.pow(fckMpa, 2.0 / 3) / Nbr6118.GAMA_C;
            double eta3 = phiMm < 32 ? 1.0 : (132 - phiMm) / 100;
            return eta1 * (boaAderencia ? 1.0 : 0.7) * eta3 * fctd;
        }

        /** lb (m) */
        double lb(double phiMm, boolean boaAderencia) {
            return phiMm / 1000 / 4 * (fykMpa / Nbr6118.GAMA_S) / fbdMpa(boaAderencia, phiMm);
        }

        double lbNec(double phiMm, boolean boaAderencia, double razaoAs) {
            double lb = lb(phiMm, boaAderencia);
            return Math.max(lb * Math.min(1, razaoAs), Math.max(0.3 * lb, Math.max(10 * phiMm / 1000, 0.10)));
        }

        double traspasse(double phiMm, boolean boaAderencia, double razaoAs) {
            double lb = lb(phiMm, boaAderencia);
            return Math.max(ALFA_0T * lbNec(phiMm, boaAderencia, razaoAs), Math.max(0.3 * ALFA_0T * lb, Math.max(15 * phiMm / 1000, 0.20)));
        }
    }

    private Detalhamento() {
    }

    public static double kgPorMetro(double phiMm) {
        return Math.PI * phiMm * phiMm / 4 * 1e-6 * 7850;
    }

    /** emendas para vencer o comprimento com barras de 12 m: número de emendas e comprimento final com os traspasses */
    static double[] comEmendas(double comprimento, double traspasse) {
        int emendas = 0;
        double total = comprimento;
        while (total > BARRA_COMERCIAL_M * (emendas + 1) - emendas * 1e-9) {
            emendas++;
            total = comprimento + emendas * traspasse;
        }
        return new double[]{emendas, total};
    }

    // ------------------------------------------------------------------ viga
    /**
     * Viga reta de p1 a p2 (planta), face superior na cota zTopo, seção b×h. {@code apoios}: posições (m, a partir de
     * p1) dos apoios, incluindo extremidades; {@code asInferior} por vão e {@code asSuperior} por apoio (cm²).
     */
    public static Resultado viga(String elementoId, String rotulo, double[] p1, double[] p2, double zTopo, double b, double h, double[] apoios,
                                 Bitolas.Arranjo inferior, Bitolas.Arranjo superiorMax, double[] asSuperiorPorApoio, Bitolas.Estribo estribo,
                                 double asInfCalcCm2, Parametros par) {
        var pos = new ArrayList<Posicao>();
        var lim = new ArrayList<String>();
        double dx = p2[0] - p1[0], dy = p2[1] - p1[1], l = Math.hypot(dx, dy);
        double ux = dx / l, uy = dy / l, nx = -uy, ny = ux;
        double c = par.cobrimentoM(), phiT = estribo.mm() / 1000;
        int seq = 1;
        // N1 · armadura inferior (positiva), corrida entre as faces externas, com ganchos de 90° nas extremidades
        double phi = inferior.bitola().mm();
        double gancho = 8 * phi / 1000;
        double reto = l - 2 * c;
        double lbNecInf = par.lbNec(phi, true, asInfCalcCm2 / inferior.areaCm2());
        double trasp = par.traspasse(phi, true, asInfCalcCm2 / inferior.areaCm2());
        var em = comEmendas(reto + 2 * gancho, trasp);
        double compN1 = em[1];
        String regraN1 = String.format(Locale.ROOT, "reta entre faces externas (L − 2c = %.2f m) + 2 ganchos de 90° (8φ = %.2f m)%s; lb,nec = %.2f m",
                reto, gancho, em[0] > 0 ? String.format(Locale.ROOT, " + %d emenda(s) de %.2f m (α0t = %.1f)", (int) em[0], trasp, ALFA_0T) : "", lbNecInf);
        double zInf = zTopo - h + c + phiT + phi / 2000;
        pos.add(new Posicao("N" + seq++, elementoId, rotulo, "longitudinal inferior", par.categoria(), phi, inferior.barras(), arr(compN1), "reta com ganchos de 90°",
                arr1(inferior.barras() * compN1 * kgPorMetro(phi)), regraN1,
                linhasLongitudinais(p1, ux, uy, nx, ny, c, l - c, zInf, inferior.barras(), b, c + phiT + phi / 2000)));
        // N2 · porta-estribos 2 Ø (menor bitola ≥ 6,3 mm da categoria), corridos
        double phiPe = Math.max(6.3, Math.min(10.0, phi));
        double compPe = comEmendas(l - 2 * c, par.traspasse(phiPe, false, 1))[1];
        double zSup = zTopo - c - phiT - phiPe / 2000;
        pos.add(new Posicao("N" + seq++, elementoId, rotulo, "porta-estribos", par.categoria(), phiPe, 2, arr(compPe), "reta",
                arr1(2 * compPe * kgPorMetro(phiPe)), "construtiva, corrida entre faces externas (premissa: 2 barras)",
                linhasLongitudinais(p1, ux, uy, nx, ny, c, l - c, zSup, 2, b, c + phiT + phiPe / 2000)));
        // N3… · armadura superior (negativa) sobre cada apoio com As− > 0: 0,25 do vão adjacente para cada lado + lb,nec
        if (superiorMax != null) {
            double phiS = superiorMax.bitola().mm();
            for (int k = 0; k < apoios.length; k++) {
                double as = asSuperiorPorApoio[k];
                if (as <= 0) {
                    continue;
                }
                int n = Math.max(2, (int) Math.ceil(as / superiorMax.bitola().areaCm2()));
                double lbNec = par.lbNec(phiS, h < 0.60, as / (n * superiorMax.bitola().areaCm2()));
                double esq = k > 0 ? 0.25 * (apoios[k] - apoios[k - 1]) + lbNec : 0, dir = k < apoios.length - 1 ? 0.25 * (apoios[k + 1] - apoios[k]) + lbNec : 0;
                boolean extremo = k == 0 || k == apoios.length - 1;
                double gS = extremo ? 8 * phiS / 1000 : 0;
                double ini = Math.max(c, apoios[k] - esq), fim = Math.min(l - c, apoios[k] + dir);
                double comp = (fim - ini) + gS;
                pos.add(new Posicao("N" + seq++, elementoId, rotulo, "longitudinal superior sobre apoio a " + arr(apoios[k]) + " m", par.categoria(), phiS, n, arr(comp),
                        extremo ? "reta com gancho de 90° no apoio extremo" : "reta",
                        arr1(n * comp * kgPorMetro(phiS)), String.format(Locale.ROOT, "0,25·vão adjacente + lb,nec (%.2f m, η2 = %s) de cada lado; decalagem al não calculada",
                                lbNec, h < 0.60 ? "1,0" : "0,7"),
                        linhasLongitudinais(p1, ux, uy, nx, ny, ini, fim, zTopo - c - phiT - phiS / 2000, n, b, c + phiT + phiS / 2000)));
            }
        }
        // estribos fechados de 2 ramos, ganchos a 45°
        double ponta = Math.max(5 * estribo.mm(), 50) / 1000;
        double compEst = 2 * (b - 2 * c) + 2 * (h - 2 * c) + 2 * ponta;
        double s = estribo.espacamentoCm() / 100;
        int nEst = (int) Math.floor((l - 2 * c) / s) + 1;
        var geoEst = new ArrayList<List<double[]>>();
        for (int i = 0; i < nEst; i++) {
            double t = c + i * s;
            geoEst.add(retanguloTransversal(p1, ux, uy, nx, ny, t, b, h, zTopo, c));
        }
        pos.add(new Posicao("N" + seq, elementoId, rotulo, "estribo", estribo.mm() < 6.3 ? "CA-60" : par.categoria(), estribo.mm(), nEst, arr(compEst),
                "estribo fechado, 2 ramos, ganchos a 45°", arr1(nEst * compEst * kgPorMetro(estribo.mm())),
                String.format(Locale.ROOT, "perímetro interno 2(b + h − 4c) + 2 pontas de %.2f m; s = %.1f cm", ponta, estribo.espacamentoCm()), geoEst));
        lim.add("Decalagem do diagrama (al, 17.4.2.2c) e corte das barras por pontos de momento nulo não calculados: a armadura superior usa 0,25 do vão adjacente.");
        lim.add("Ancoragem nos apoios extremos por gancho de 90°, sem verificar o comprimento disponível no apoio (dimensão do pilar).");
        lim.add("Armadura de pele (h > 60 cm) e armadura de suspensão não detalhadas.");
        if (h > 0.60) {
            lim.add("Viga com h > 60 cm: armadura de pele exigida (17.3.5.2.3) e não detalhada.");
        }
        return new Resultado(pos, pos.stream().mapToDouble(Posicao::massaKg).sum(), lim);
    }

    // ------------------------------------------------------------------ pilar
    public static Resultado pilar(String elementoId, String rotulo, double cx, double cy, double rotacaoGraus, double z0, double altura, double b, double h,
                                  List<SecaoConcreto.Barra> barras, double phiMm, boolean continuaAcima, Bitolas.Estribo estribo, Parametros par) {
        var pos = new ArrayList<Posicao>();
        double c = par.cobrimentoM();
        double trasp = par.traspasse(phiMm, true, 1);
        double comp = continuaAcima ? altura + trasp : altura - c + 8 * phiMm / 1000;
        double ang = Math.toRadians(rotacaoGraus), ca = Math.cos(ang), sa = Math.sin(ang);
        var geo = new ArrayList<List<double[]>>();
        for (var br : barras) {
            double x = cx + br.u() * ca - br.v() * sa, y = cy + br.u() * sa + br.v() * ca;
            geo.add(List.of(new double[]{x, y, z0}, new double[]{x, y, z0 + comp}));
        }
        pos.add(new Posicao("N1", elementoId, rotulo, "longitudinal", par.categoria(), phiMm, barras.size(), arr(comp),
                continuaAcima ? "reta" : "reta com gancho de 90° no topo", arr1(barras.size() * comp * kgPorMetro(phiMm)),
                continuaAcima ? String.format(Locale.ROOT, "altura do lance + emenda por traspasse no pavimento acima (l0t = %.2f m, α0t = %.1f)", trasp, ALFA_0T)
                        : "altura − c + gancho de 90° (8φ) no topo", geo));
        double ponta = Math.max(5 * estribo.mm(), 50) / 1000;
        double compEst = 2 * (b - 2 * c) + 2 * (h - 2 * c) + 2 * ponta;
        double s = estribo.espacamentoCm() / 100;
        int n = (int) Math.floor((altura - 2 * c) / s) + 1;
        var geoEst = new ArrayList<List<double[]>>();
        for (int i = 0; i < n; i++) {
            double z = z0 + c + i * s;
            double eu = b / 2 - c, ev = h / 2 - c;
            var anel = new ArrayList<double[]>();
            for (double[] q : new double[][]{{-eu, -ev}, {eu, -ev}, {eu, ev}, {-eu, ev}, {-eu, -ev}}) {
                anel.add(new double[]{cx + q[0] * ca - q[1] * sa, cy + q[0] * sa + q[1] * ca, z});
            }
            geoEst.add(anel);
        }
        pos.add(new Posicao("N2", elementoId, rotulo, "estribo", estribo.mm() < 6.3 ? "CA-60" : par.categoria(), estribo.mm(), n, arr(compEst),
                "estribo fechado, ganchos a 45°", arr1(n * compEst * kgPorMetro(estribo.mm())),
                String.format(Locale.ROOT, "s = %.1f cm ≤ mín(20 cm; menor dimensão; 12φl)", estribo.espacamentoCm()), geoEst));
        var lim = new ArrayList<String>();
        lim.add("Estribos suplementares (proteção contra flambagem das barras afastadas mais de 20φt do canto, 18.2.4) não verificados.");
        return new Resultado(pos, pos.stream().mapToDouble(Posicao::massaKg).sum(), lim);
    }

    // ------------------------------------------------------------------ laje
    /** malha nas duas direções sobre o retângulo envolvente (xMin..xMax, yMin..yMax), face inferior em zTopo − hLaje */
    public static Resultado laje(String elementoId, String rotulo, double xMin, double xMax, double yMin, double yMax, double zTopo, double hLaje,
                                 double phiX, double sX, double phiY, double sY, Parametros par) {
        var pos = new ArrayList<Posicao>();
        double c = par.cobrimentoM();
        double lx = xMax - xMin, ly = yMax - yMin;
        // N1 · barras na direção X, distribuídas ao longo de Y
        int nX = (int) Math.floor((ly - 2 * c) / sX) + 1;
        double compX = comEmendas(lx - 2 * c + 2 * 8 * phiX / 1000, par.traspasse(phiX, true, 1))[1];
        double zX = zTopo - hLaje + c + phiX / 2000;
        var geoX = new ArrayList<List<double[]>>();
        for (int i = 0; i < nX; i++) {
            double y = yMin + c + i * sX;
            geoX.add(List.of(new double[]{xMin + c, y, zX}, new double[]{xMax - c, y, zX}));
        }
        pos.add(new Posicao("N1", elementoId, rotulo, "inferior, direção X", par.categoria(), phiX, nX, arr(compX), "reta com ganchos de 90°",
                arr1(nX * compX * kgPorMetro(phiX)), String.format(Locale.ROOT, "lx − 2c + 2·8φ; s = %.1f cm", sX * 100), geoX));
        int nY = (int) Math.floor((lx - 2 * c) / sY) + 1;
        double compY = comEmendas(ly - 2 * c + 2 * 8 * phiY / 1000, par.traspasse(phiY, true, 1))[1];
        double zY = zX + phiX / 2000 + phiY / 2000;
        var geoY = new ArrayList<List<double[]>>();
        for (int i = 0; i < nY; i++) {
            double x = xMin + c + i * sY;
            geoY.add(List.of(new double[]{x, yMin + c, zY}, new double[]{x, yMax - c, zY}));
        }
        pos.add(new Posicao("N2", elementoId, rotulo, "inferior, direção Y", par.categoria(), phiY, nY, arr(compY), "reta com ganchos de 90°",
                arr1(nY * compY * kgPorMetro(phiY)), String.format(Locale.ROOT, "ly − 2c + 2·8φ; s = %.1f cm", sY * 100), geoY));
        var lim = new ArrayList<String>();
        lim.add("Armadura negativa sobre os apoios (continuidade com lajes vizinhas) não detalhada: bordas tratadas como simplesmente apoiadas.");
        lim.add("Malha sobre o retângulo envolvente da laje; recortes e aberturas não descontados.");
        return new Resultado(pos, pos.stream().mapToDouble(Posicao::massaKg).sum(), lim);
    }

    // ------------------------------------------------------------------ geometria
    private static List<List<double[]>> linhasLongitudinais(double[] p1, double ux, double uy, double nx, double ny, double s0, double s1, double z, int n, double b, double recuo) {
        var out = new ArrayList<List<double[]>>();
        double largura = b - 2 * recuo;
        for (int i = 0; i < n; i++) {
            double off = n == 1 ? 0 : -largura / 2 + largura * i / (n - 1);
            out.add(List.of(new double[]{p1[0] + ux * s0 + nx * off, p1[1] + uy * s0 + ny * off, z},
                    new double[]{p1[0] + ux * s1 + nx * off, p1[1] + uy * s1 + ny * off, z}));
        }
        return out;
    }

    private static List<double[]> retanguloTransversal(double[] p1, double ux, double uy, double nx, double ny, double t, double b, double h, double zTopo, double c) {
        double x = p1[0] + ux * t, y = p1[1] + uy * t;
        double e = b / 2 - c, zi = zTopo - h + c, zs = zTopo - c;
        return List.of(new double[]{x - nx * e, y - ny * e, zi}, new double[]{x + nx * e, y + ny * e, zi}, new double[]{x + nx * e, y + ny * e, zs},
                new double[]{x - nx * e, y - ny * e, zs}, new double[]{x - nx * e, y - ny * e, zi});
    }

    private static double arr(double v) {
        return Math.round(v * 100) / 100.0;
    }

    private static double arr1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
