package br.com.civilconstructorai.estrutura.mef;

import br.com.civilconstructorai.estrutura.carga.Combinacao;
import br.com.civilconstructorai.estrutura.carga.NaturezaAcao;
import br.com.civilconstructorai.estrutura.mef.ModeloAnalitico.Barra;
import br.com.civilconstructorai.estrutura.mef.ModeloAnalitico.No;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Método dos Elementos Finitos para pórtico espacial (barras de Euler-Bernoulli, 6 GDL por nó; barras de treliça
 * só com rigidez axial), matriz densa e eliminação de Gauss com pivotamento parcial — suficiente para dezenas de
 * barras. Análise linear elástica de 1ª ordem: resolve um caso por natureza de ação e superpõe pelos fatores de
 * cada combinação. Cargas distribuídas (uniformes ou lineares por trecho) são integradas por Gauss-Legendre de
 * 5 pontos, exato para os polinômios envolvidos: forças de engastamento perfeito, esforços por estática e flecha
 * pela função de Green da viga biengastada. Todo resultado traz a verificação de equilíbrio global.
 */
public final class SolverMef {

    public static final int PONTOS = 11;
    /** resíduo relativo de equilíbrio aceito (reações × cargas aplicadas): erro de arredondamento, não de modelo */
    public static final double TOL_EQUILIBRIO = 1e-6;

    public static final class EstruturaHipostaticaException extends RuntimeException {
        public EstruturaHipostaticaException(String msg) {
            super(msg);
        }
    }

    /**
     * esforços numa seção, eixos locais da barra: N (tração +), Vy, Vz, T, My (positivo tracionando a face −z
     * local, a "inferior" nas vigas), Mz (positivo tracionando a face −y local) e flecha (deslocamento transversal
     * resultante, m). Valores calculados pelo solver, nunca medidos.
     */
    public record Esforco(double s, double n, double vy, double vz, double t, double my, double mz, double flecha) {
    }

    /**
     * Resultado de uma barra. {@code eixoY}/{@code eixoZ}: direções globais dos eixos locais (convenção de sinais);
     * {@code sMMax}/{@code sMMin}: onde ocorrem os extremos de My; {@code mzMaxAbs} e {@code tMaxAbs}: maiores
     * valores absolutos de Mz e torção.
     */
    public record ResultadoBarra(int barra, String elementoId, String rotulo, double comprimento, List<Esforco> pontos,
                                 double nMax, double vMax, double mMax, double mMin, double flechaMax,
                                 Integer noI, Integer noJ, double[] eixoY, double[] eixoZ, Boolean trelica,
                                 Double sMMax, Double sMMin, Double mzMaxAbs, Double tMaxAbs, Double nMin) {
        public ResultadoBarra(int barra, String elementoId, String rotulo, double comprimento, List<Esforco> pontos,
                              double nMax, double vMax, double mMax, double mMin, double flechaMax) {
            this(barra, elementoId, rotulo, comprimento, pontos, nMax, vMax, mMax, mMin, flechaMax, null, null, null, null, null, null, null, null, null, null);
        }
    }

    public record Reacao(int no, double fx, double fy, double fz, double mx, double my, double mz) {
    }

    /** soma das cargas aplicadas e das reações (Fx, Fy, Fz, Mx, My, Mz em torno da origem) e resíduo relativo */
    public record Equilibrio(double[] somaCargas, double[] somaReacoes, double residuoRelativo, boolean ok) {
    }

    public record ResultadoCombinacao(Combinacao combinacao, List<ResultadoBarra> barras, List<Reacao> reacoes, double deslocamentoMax,
                                      List<double[]> deslocamentos, Equilibrio equilibrio) {
        public ResultadoCombinacao(Combinacao combinacao, List<ResultadoBarra> barras, List<Reacao> reacoes, double deslocamentoMax) {
            this(combinacao, barras, reacoes, deslocamentoMax, null, null);
        }
    }

    /**
     * {@code coordenadas}: x, y, z de cada nó (m). {@code avisos}: exclusões, aproximações e premissas do modelo
     * analítico. {@code metadados}: método, hipóteses, unidades e convenções — para quem lê o resultado.
     */
    public record ResultadoAnalise(int nos, int barras, Map<String, ResultadoCombinacao> porCombinacao,
                                   List<double[]> coordenadas, List<ModeloAnalitico.Aviso> avisos, Map<String, Object> metadados) {
        public ResultadoAnalise(int nos, int barras, Map<String, ResultadoCombinacao> porCombinacao) {
            this(nos, barras, porCombinacao, null, null, null);
        }
    }

    /** trecho de carga em coordenadas locais da barra: intensidade wa em s0 e wb em s1 (kN/m, 3 componentes) */
    private record Segmento(double s0, double s1, double[] wa, double[] wb) {
        double[] w(double s) {
            double t = s1 - s0 < 1e-12 ? 0 : (s - s0) / (s1 - s0);
            return new double[]{wa[0] + t * (wb[0] - wa[0]), wa[1] + t * (wb[1] - wa[1]), wa[2] + t * (wb[2] - wa[2])};
        }
    }

    private static final double[] GX = {-0.9061798459386640, -0.5384693101056831, 0, 0.5384693101056831, 0.9061798459386640};
    private static final double[] GW = {0.2369268850561891, 0.4786286704993665, 0.5688888888888889, 0.4786286704993665, 0.2369268850561891};

    private final ModeloAnalitico modelo;

    public SolverMef(ModeloAnalitico modelo) {
        this.modelo = modelo;
    }

    public ResultadoAnalise resolver(List<Combinacao> combinacoes) {
        var ndof = modelo.nos().size() * 6;
        if (modelo.barras().isEmpty()) {
            throw new EstruturaHipostaticaException("Modelo sem barras: lance pilares e vigas antes da análise.");
        }
        var kGlobal = montarRigidez(ndof);
        var livres = grausLivres(ndof);
        if (livres.length == ndof) {
            throw new EstruturaHipostaticaException("Estrutura sem apoios: hipostática.");
        }
        var kReduzida = reduzir(kGlobal, livres);
        var lu = fatorar(kReduzida);

        var deslocPorCaso = new EnumMap<NaturezaAcao, double[]>(NaturezaAcao.class);
        var fixasPorCaso = new EnumMap<NaturezaAcao, double[][]>(NaturezaAcao.class);
        for (var caso : modelo.casos().values()) {
            var f = new double[ndof];
            var fixas = new double[modelo.barras().size()][12];
            for (var cn : caso.nodais()) {
                int b = cn.no() * 6;
                f[b] += cn.fx(); f[b + 1] += cn.fy(); f[b + 2] += cn.fz(); f[b + 3] += cn.mx(); f[b + 4] += cn.my(); f[b + 5] += cn.mz();
            }
            for (var barra : modelo.barras()) {
                var segs = segmentos(barra, caso, 1.0);
                if (segs.isEmpty()) {
                    continue;
                }
                var fe = engastamento(barra, segs);
                for (int i = 0; i < 12; i++) {
                    fixas[barra.id()][i] += fe[i];
                }
                var feGlobal = multT(transformacao(rotacao(barra)), fe);
                int[] dofs = dofs(barra);
                for (int i = 0; i < 12; i++) {
                    f[dofs[i]] += feGlobal[i];
                }
            }
            var fRed = new double[livres.length];
            for (int i = 0; i < livres.length; i++) {
                fRed[i] = f[livres[i]];
            }
            var uRed = resolverLu(lu, fRed);
            var u = new double[ndof];
            for (int i = 0; i < livres.length; i++) {
                u[livres[i]] = uRed[i];
            }
            deslocPorCaso.put(caso.natureza(), u);
            fixasPorCaso.put(caso.natureza(), fixas);
        }

        var porCombinacao = new LinkedHashMap<String, ResultadoCombinacao>();
        for (var comb : combinacoes) {
            var u = new double[ndof];
            var fixas = new double[modelo.barras().size()][12];
            var cargasNodais = new double[ndof];
            for (var e : deslocPorCaso.entrySet()) {
                double fator = comb.fator(e.getKey());
                if (fator == 0) {
                    continue;
                }
                for (int i = 0; i < ndof; i++) {
                    u[i] += fator * e.getValue()[i];
                }
                var fx = fixasPorCaso.get(e.getKey());
                for (int b = 0; b < fx.length; b++) {
                    for (int i = 0; i < 12; i++) {
                        fixas[b][i] += fator * fx[b][i];
                    }
                }
                for (var cn : modelo.casos().get(e.getKey()).nodais()) {
                    int b = cn.no() * 6;
                    cargasNodais[b] += fator * cn.fx(); cargasNodais[b + 1] += fator * cn.fy(); cargasNodais[b + 2] += fator * cn.fz();
                    cargasNodais[b + 3] += fator * cn.mx(); cargasNodais[b + 4] += fator * cn.my(); cargasNodais[b + 5] += fator * cn.mz();
                }
            }
            var barras = new ArrayList<ResultadoBarra>();
            var reacoesVetor = new double[ndof];
            for (var barra : modelo.barras()) {
                var r = rotacao(barra);
                var t = transformacao(r);
                int[] dofs = dofs(barra);
                var uE = new double[12];
                for (int i = 0; i < 12; i++) {
                    uE[i] = u[dofs[i]];
                }
                var uLocal = mult(t, uE);
                var kLocal = rigidezLocal(barra);
                var fLocal = mult(kLocal, uLocal);
                for (int i = 0; i < 12; i++) {
                    fLocal[i] -= fixas[barra.id()][i];
                }
                var fGlobal = multT(t, fLocal);
                for (int i = 0; i < 12; i++) {
                    reacoesVetor[dofs[i]] += fGlobal[i];
                }
                barras.add(esforcos(barra, r, fLocal, segmentosCombinacao(barra, comb), uLocal));
            }
            var reacoes = new ArrayList<Reacao>();
            for (var ap : modelo.apoios()) {
                int b = ap.no() * 6;
                reacoes.add(new Reacao(ap.no(), arr(reacoesVetor[b] - cargasNodais[b]), arr(reacoesVetor[b + 1] - cargasNodais[b + 1]),
                        arr(reacoesVetor[b + 2] - cargasNodais[b + 2]), arr(reacoesVetor[b + 3] - cargasNodais[b + 3]),
                        arr(reacoesVetor[b + 4] - cargasNodais[b + 4]), arr(reacoesVetor[b + 5] - cargasNodais[b + 5])));
            }
            double dmax = 0;
            var deslocamentos = new ArrayList<double[]>();
            for (int n = 0; n < modelo.nos().size(); n++) {
                dmax = Math.max(dmax, Math.sqrt(u[n * 6] * u[n * 6] + u[n * 6 + 1] * u[n * 6 + 1] + u[n * 6 + 2] * u[n * 6 + 2]));
                var d = new double[6];
                for (int k = 0; k < 6; k++) {
                    d[k] = Math.round(u[n * 6 + k] * 1e7) / 1e7;
                }
                deslocamentos.add(d);
            }
            for (var rb : barras) {
                dmax = Math.max(dmax, rb.flechaMax());
            }
            porCombinacao.put(comb.nome(), new ResultadoCombinacao(comb, barras, reacoes, dmax, deslocamentos, equilibrio(comb, reacoesVetor, cargasNodais)));
        }
        var coordenadas = modelo.nos().stream().map(n -> new double[]{n.x(), n.y(), n.z()}).toList();
        return new ResultadoAnalise(modelo.nos().size(), modelo.barras().size(), porCombinacao, coordenadas, List.copyOf(modelo.avisos()), metadados());
    }

    private static Map<String, Object> metadados() {
        var m = new LinkedHashMap<String, Object>();
        m.put("natureza", "CALCULADO");
        m.put("metodo", "MEF linear elástico de 1ª ordem: pórtico espacial, barras de Euler-Bernoulli (6 GDL/nó) e treliça (só axial)");
        m.put("hipoteses", List.of(
                "material elástico linear; sem não linearidade física nem geométrica (sem efeitos de 2ª ordem globais)",
                "lajes não são elementos de placa: entram só como carga repartida nas vigas de apoio",
                "sem diafragma rígido imposto; ligações viga-pilar rígidas; sem excentricidades entre eixos",
                "bases dos pilares do nível mais baixo engastadas",
                "superposição dos casos (G, Q, W) pelos fatores de cada combinação"));
        m.put("unidades", Map.of("comprimento", "m", "forca", "kN", "momento", "kN·m", "deslocamento", "m", "rotacao", "rad"));
        m.put("convencao", "N > 0 tração; My > 0 traciona a face −z local (inferior nas vigas); Mz > 0 traciona a face −y local; "
                + "eixos locais: x do nó I ao nó J; vigas com z local para cima; pilares com z local = eixo X do pilar (dimensão b)");
        m.put("discretizacao", "uma barra por trecho entre nós; esforços amostrados em " + PONTOS + " pontos por barra; sem refinamento de malha "
                + "(solução exata de Euler-Bernoulli para as cargas suportadas, não aproximação por malha)");
        return m;
    }

    // ------------------------------------------------------------------ cargas distribuídas
    /** trechos de carga do caso na barra, já em coordenadas locais e multiplicados pelo fator */
    private List<Segmento> segmentos(Barra barra, ModeloAnalitico.CasoCarga caso, double fator) {
        var out = new ArrayList<Segmento>();
        var r = rotacao(barra);
        double l = modelo.comprimento(barra);
        for (var cd : caso.distribuidas()) {
            if (cd.barra() == barra.id()) {
                var w = escala(mult3(r, new double[]{cd.qx(), cd.qy(), cd.qz()}), fator);
                out.add(new Segmento(0, l, w, w));
            }
        }
        for (var cv : caso.variaveis()) {
            if (cv.barra() == barra.id()) {
                double s0 = Math.max(0, Math.min(l, cv.s0())), s1 = Math.max(0, Math.min(l, cv.s1()));
                if (s1 - s0 < 1e-9) {
                    continue;
                }
                out.add(new Segmento(s0, s1, escala(mult3(r, new double[]{cv.qx0(), cv.qy0(), cv.qz0()}), fator),
                        escala(mult3(r, new double[]{cv.qx1(), cv.qy1(), cv.qz1()}), fator)));
            }
        }
        return out;
    }

    private List<Segmento> segmentosCombinacao(Barra barra, Combinacao comb) {
        var out = new ArrayList<Segmento>();
        for (var caso : modelo.casos().values()) {
            double fator = comb.fator(caso.natureza());
            if (fator != 0) {
                out.addAll(segmentos(barra, caso, fator));
            }
        }
        return out;
    }

    /**
     * Forças nodais equivalentes (engastamento perfeito) em coordenadas locais, pela integral das fórmulas da carga
     * concentrada P em a (b = L − a): R_i = P·b²(3a+b)/L³, R_j = P·a²(a+3b)/L³, M_i = P·a·b²/L², M_j = −P·a²·b/L².
     * Barra de treliça: cargas transversais vão aos nós pela regra da alavanca, sem momentos.
     */
    private double[] engastamento(Barra barra, List<Segmento> segs) {
        double l = modelo.comprimento(barra), l2 = l * l, l3 = l2 * l;
        var fe = new double[12];
        for (var seg : segs) {
            double meia = (seg.s1() - seg.s0()) / 2, meio = (seg.s1() + seg.s0()) / 2;
            for (int g = 0; g < 5; g++) {
                double a = meio + meia * GX[g], peso = meia * GW[g], b = l - a;
                var w = seg.w(a);
                double px = w[0] * peso, py = w[1] * peso, pz = w[2] * peso;
                fe[0] += px * b / l; fe[6] += px * a / l;
                if (barra.trelica()) {
                    fe[1] += py * b / l; fe[7] += py * a / l;
                    fe[2] += pz * b / l; fe[8] += pz * a / l;
                    continue;
                }
                fe[1] += py * b * b * (3 * a + b) / l3; fe[7] += py * a * a * (a + 3 * b) / l3;
                fe[5] += py * a * b * b / l2; fe[11] += -py * a * a * b / l2;
                fe[2] += pz * b * b * (3 * a + b) / l3; fe[8] += pz * a * a * (a + 3 * b) / l3;
                fe[4] += -pz * a * b * b / l2; fe[10] += pz * a * a * b / l2;
            }
        }
        return fe;
    }

    /** ∫₀ˢ w(x) dx e ∫₀ˢ w(x)·(s − x) dx por componente local */
    private static double[][] integrais(List<Segmento> segs, double s) {
        var resultante = new double[3];
        var momento = new double[3];
        for (var seg : segs) {
            double fim = Math.min(seg.s1(), s);
            if (fim <= seg.s0()) {
                continue;
            }
            double meia = (fim - seg.s0()) / 2, meio = (fim + seg.s0()) / 2;
            for (int g = 0; g < 5; g++) {
                double x = meio + meia * GX[g], peso = meia * GW[g];
                var w = seg.w(x);
                for (int k = 0; k < 3; k++) {
                    resultante[k] += w[k] * peso;
                    momento[k] += w[k] * (s - x) * peso;
                }
            }
        }
        return new double[][]{resultante, momento};
    }

    // ------------------------------------------------------------------ esforços ao longo da barra
    private ResultadoBarra esforcos(Barra barra, double[][] r, double[] f, List<Segmento> segs, double[] uLocal) {
        double l = modelo.comprimento(barra);
        var sec = barra.secao();
        var pontos = new ArrayList<Esforco>(PONTOS);
        double nMax = 0, nMin = 0, vMax = 0, mMax = -Double.MAX_VALUE, mMin = Double.MAX_VALUE, flechaMax = 0, mzAbs = 0, tAbs = 0, sMMax = 0, sMMin = 0;
        for (int p = 0; p < PONTOS; p++) {
            double s = l * p / (PONTOS - 1);
            var in = integrais(segs, s);
            double n = -(f[0] + in[0][0]);
            double vy = barra.trelica() ? 0 : f[1] + in[0][1];
            double vz = barra.trelica() ? 0 : f[2] + in[0][2];
            double t = barra.trelica() ? 0 : -f[3];
            double my = barra.trelica() ? 0 : f[4] + f[2] * s + in[1][2];
            double mz = barra.trelica() ? 0 : -f[5] + f[1] * s + in[1][1];
            double flecha = barra.trelica() ? flechaTrelica(uLocal, s / l) : flecha(uLocal, segs, s, l, sec);
            pontos.add(new Esforco(arr(s), arr(n), arr(vy), arr(vz), arr(t), arr(my), arr(mz), flecha));
            nMax = Math.max(nMax, Math.abs(n));
            nMin = Math.min(nMin, n);
            vMax = Math.max(vMax, Math.max(Math.abs(vy), Math.abs(vz)));
            if (my > mMax) {
                mMax = my;
                sMMax = s;
            }
            if (my < mMin) {
                mMin = my;
                sMMin = s;
            }
            mzAbs = Math.max(mzAbs, Math.abs(mz));
            tAbs = Math.max(tAbs, Math.abs(t));
            flechaMax = Math.max(flechaMax, flecha);
        }
        return new ResultadoBarra(barra.id(), barra.elementoId(), barra.rotulo(), arr(l), pontos, arr(nMax), arr(vMax), arr(mMax), arr(mMin), flechaMax,
                barra.noI(), barra.noJ(), arr3(r[1]), arr3(r[2]), barra.trelica(), arr(sMMax), arr(sMMin), arr(mzAbs), arr(tAbs), arr(nMin));
    }

    /**
     * Deslocamento transversal resultante em s: interpolação de Hermite dos deslocamentos nodais locais somada à
     * solução particular das cargas na barra biengastada (função de Green, exata para Euler-Bernoulli).
     */
    private static double flecha(double[] u, List<Segmento> segs, double s, double l, ModeloAnalitico.Secao sec) {
        double xi = s / l;
        double n1 = 1 - 3 * xi * xi + 2 * xi * xi * xi;
        double n2 = l * (xi - 2 * xi * xi + xi * xi * xi);
        double n3 = 3 * xi * xi - 2 * xi * xi * xi;
        double n4 = l * (xi * xi * xi - xi * xi);
        var part = particular(segs, s, l);
        // plano x-y: v = N1·uy_i + N2·θz_i + N3·uy_j + N4·θz_j
        double vy = n1 * u[1] + n2 * u[5] + n3 * u[7] + n4 * u[11] + part[1] / (sec.e() * sec.iz());
        // plano x-z: θy = −dw/dx → sinais de N2 e N4 trocados
        double vz = n1 * u[2] - n2 * u[4] + n3 * u[8] - n4 * u[10] + part[2] / (sec.e() * sec.iy());
        return Math.sqrt(vy * vy + vz * vz);
    }

    private static double flechaTrelica(double[] u, double xi) {
        double vy = (1 - xi) * u[1] + xi * u[7], vz = (1 - xi) * u[2] + xi * u[8];
        return Math.sqrt(vy * vy + vz * vz);
    }

    /**
     * ∫ w(a)·G(s, a) da da viga biengastada (sem dividir por EI). Carga unitária em a, b = L − a:
     * s ≤ a: G = b²·s²·(3aL − (3a + b)·s)/(6L³); s > a: simétrico com (a, s) → (b, L − s).
     */
    private static double[] particular(List<Segmento> segs, double s, double l) {
        var out = new double[3];
        double l3 = l * l * l;
        for (var seg : segs) {
            for (var trecho : new double[][]{{seg.s0(), Math.min(seg.s1(), s)}, {Math.max(seg.s0(), s), seg.s1()}}) {
                if (trecho[1] <= trecho[0]) {
                    continue;
                }
                double meia = (trecho[1] - trecho[0]) / 2, meio = (trecho[1] + trecho[0]) / 2;
                for (int g = 0; g < 5; g++) {
                    double a = meio + meia * GX[g], peso = meia * GW[g], b = l - a;
                    double green = s <= a
                            ? b * b * s * s * (3 * a * l - (3 * a + b) * s) / (6 * l3)
                            : a * a * (l - s) * (l - s) * (3 * b * l - (3 * b + a) * (l - s)) / (6 * l3);
                    var w = seg.w(a);
                    for (int k = 0; k < 3; k++) {
                        out[k] += w[k] * green * peso;
                    }
                }
            }
        }
        return out;
    }

    /** ΣF e ΣM (em torno da origem) das cargas aplicadas na combinação contra as reações nos apoios */
    private Equilibrio equilibrio(Combinacao comb, double[] reacoesVetor, double[] cargasNodais) {
        var cargas = new double[6];
        for (int n = 0; n < modelo.nos().size(); n++) {
            var no = modelo.nos().get(n);
            somar(cargas, new double[]{no.x(), no.y(), no.z()}, new double[]{cargasNodais[n * 6], cargasNodais[n * 6 + 1], cargasNodais[n * 6 + 2]});
            for (int k = 3; k < 6; k++) {
                cargas[k] += cargasNodais[n * 6 + k];
            }
        }
        for (var barra : modelo.barras()) {
            var r = rotacao(barra);
            var ni = modelo.nos().get(barra.noI());
            for (var seg : segmentosCombinacao(barra, comb)) {
                double meia = (seg.s1() - seg.s0()) / 2, meio = (seg.s1() + seg.s0()) / 2;
                for (int g = 0; g < 5; g++) {
                    double a = meio + meia * GX[g], peso = meia * GW[g];
                    var wLocal = seg.w(a);
                    // local → global: Rᵀ·w
                    var wg = new double[]{r[0][0] * wLocal[0] + r[1][0] * wLocal[1] + r[2][0] * wLocal[2],
                            r[0][1] * wLocal[0] + r[1][1] * wLocal[1] + r[2][1] * wLocal[2],
                            r[0][2] * wLocal[0] + r[1][2] * wLocal[1] + r[2][2] * wLocal[2]};
                    var pos = new double[]{ni.x() + r[0][0] * a, ni.y() + r[0][1] * a, ni.z() + r[0][2] * a};
                    somar(cargas, pos, escala(wg, peso));
                }
            }
        }
        var reacoes = new double[6];
        for (var ap : modelo.apoios()) {
            var no = modelo.nos().get(ap.no());
            int b = ap.no() * 6;
            somar(reacoes, new double[]{no.x(), no.y(), no.z()}, new double[]{reacoesVetor[b] - cargasNodais[b], reacoesVetor[b + 1] - cargasNodais[b + 1], reacoesVetor[b + 2] - cargasNodais[b + 2]});
            for (int k = 3; k < 6; k++) {
                reacoes[k] += reacoesVetor[b + k] - cargasNodais[b + k];
            }
        }
        double res = 0, ref = 1;
        for (int k = 0; k < 6; k++) {
            res = Math.max(res, Math.abs(cargas[k] + reacoes[k]));
            ref = Math.max(ref, Math.abs(cargas[k]));
        }
        double rel = res / ref;
        var c = new double[6];
        var rr = new double[6];
        for (int k = 0; k < 6; k++) {
            c[k] = arr(cargas[k]);
            rr[k] = arr(reacoes[k]);
        }
        return new Equilibrio(c, rr, rel, rel <= TOL_EQUILIBRIO);
    }

    private static void somar(double[] acc, double[] pos, double[] f) {
        acc[0] += f[0]; acc[1] += f[1]; acc[2] += f[2];
        acc[3] += pos[1] * f[2] - pos[2] * f[1];
        acc[4] += pos[2] * f[0] - pos[0] * f[2];
        acc[5] += pos[0] * f[1] - pos[1] * f[0];
    }

    // ------------------------------------------------------------------ álgebra
    private double[][] montarRigidez(int ndof) {
        var k = new double[ndof][ndof];
        for (var barra : modelo.barras()) {
            var r = rotacao(barra);
            var t = transformacao(r);
            var kl = rigidezLocal(barra);
            var kg = multT(t, mult(kl, t));
            int[] dofs = dofs(barra);
            for (int i = 0; i < 12; i++) {
                for (int j = 0; j < 12; j++) {
                    k[dofs[i]][dofs[j]] += kg[i][j];
                }
            }
        }
        return k;
    }

    private int[] grausLivres(int ndof) {
        var restrito = new boolean[ndof];
        for (var ap : modelo.apoios()) {
            var r = ap.restritos();
            for (int i = 0; i < 6; i++) {
                if (r[i]) {
                    restrito[ap.no() * 6 + i] = true;
                }
            }
        }
        var livres = new ArrayList<Integer>();
        for (int i = 0; i < ndof; i++) {
            if (!restrito[i]) {
                livres.add(i);
            }
        }
        return livres.stream().mapToInt(Integer::intValue).toArray();
    }

    private static double[][] reduzir(double[][] k, int[] livres) {
        var out = new double[livres.length][livres.length];
        for (int i = 0; i < livres.length; i++) {
            for (int j = 0; j < livres.length; j++) {
                out[i][j] = k[livres[i]][livres[j]];
            }
        }
        return out;
    }

    private record Lu(double[][] a, int[] perm) {
    }

    private Lu fatorar(double[][] kIn) {
        int n = kIn.length;
        var a = new double[n][];
        for (int i = 0; i < n; i++) {
            a[i] = kIn[i].clone();
        }
        var perm = new int[n];
        for (int i = 0; i < n; i++) {
            perm[i] = i;
        }
        double escala = 0;
        for (var linha : a) {
            for (var v : linha) {
                escala = Math.max(escala, Math.abs(v));
            }
        }
        for (int c = 0; c < n; c++) {
            int piv = c;
            for (int r = c + 1; r < n; r++) {
                if (Math.abs(a[r][c]) > Math.abs(a[piv][c])) {
                    piv = r;
                }
            }
            if (Math.abs(a[piv][c]) < 1e-10 * Math.max(escala, 1e-30)) {
                throw new EstruturaHipostaticaException("Matriz de rigidez singular: estrutura hipostática ou mecanismo (" + descreverGrau(perm[c]) + ").");
            }
            if (piv != c) {
                var tmp = a[piv]; a[piv] = a[c]; a[c] = tmp;
                int tp = perm[piv]; perm[piv] = perm[c]; perm[c] = tp;
            }
            for (int r = c + 1; r < n; r++) {
                double m = a[r][c] / a[c][c];
                if (m == 0) {
                    continue;
                }
                a[r][c] = m;
                for (int j = c + 1; j < n; j++) {
                    a[r][j] -= m * a[c][j];
                }
            }
        }
        return new Lu(a, perm);
    }

    /** grau de liberdade reduzido → texto com o nó e a direção, para a mensagem de falha */
    private String descreverGrau(int indiceReduzido) {
        var livres = grausLivres(modelo.nos().size() * 6);
        if (indiceReduzido < 0 || indiceReduzido >= livres.length) {
            return "grau de liberdade " + indiceReduzido;
        }
        int g = livres[indiceReduzido], no = g / 6;
        var n = modelo.nos().get(no);
        String[] dir = {"translação X", "translação Y", "translação Z", "rotação X", "rotação Y", "rotação Z"};
        return String.format(java.util.Locale.ROOT, "nó %d em (%.2f; %.2f; %.2f) m, %s", no, n.x(), n.y(), n.z(), dir[g % 6]);
    }

    private static double[] resolverLu(Lu lu, double[] b) {
        int n = b.length;
        var y = new double[n];
        for (int i = 0; i < n; i++) {
            double s = b[lu.perm()[i]];
            for (int j = 0; j < i; j++) {
                s -= lu.a()[i][j] * y[j];
            }
            y[i] = s;
        }
        var x = new double[n];
        for (int i = n - 1; i >= 0; i--) {
            double s = y[i];
            for (int j = i + 1; j < n; j++) {
                s -= lu.a()[i][j] * x[j];
            }
            x[i] = s / lu.a()[i][i];
        }
        return x;
    }

    private int[] dofs(Barra b) {
        var d = new int[12];
        for (int i = 0; i < 6; i++) {
            d[i] = b.noI() * 6 + i;
            d[6 + i] = b.noJ() * 6 + i;
        }
        return d;
    }

    /** eixos locais (linhas: x, y, z em coordenadas globais), com o giro {@code angulo} em torno do eixo da barra */
    private double[][] rotacao(Barra b) {
        No i = modelo.nos().get(b.noI()), j = modelo.nos().get(b.noJ());
        double l = modelo.comprimento(b);
        double[] ex = {(j.x() - i.x()) / l, (j.y() - i.y()) / l, (j.z() - i.z()) / l};
        boolean vertical = Math.abs(ex[2]) > 0.999;
        double[] ref = vertical ? new double[]{1, 0, 0} : new double[]{0, 0, 1};
        double[] ey = normalizar(cross(ref, ex));
        double[] ez = cross(ex, ey);
        if (b.angulo() != 0) {
            double c = Math.cos(b.angulo()), s = Math.sin(b.angulo());
            double[] ey2 = {c * ey[0] + s * ez[0], c * ey[1] + s * ez[1], c * ey[2] + s * ez[2]};
            double[] ez2 = {-s * ey[0] + c * ez[0], -s * ey[1] + c * ez[1], -s * ey[2] + c * ez[2]};
            ey = ey2;
            ez = ez2;
        }
        return new double[][]{ex, ey, ez};
    }

    private static double[][] transformacao(double[][] r) {
        var t = new double[12][12];
        for (int blk = 0; blk < 4; blk++) {
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    t[blk * 3 + i][blk * 3 + j] = r[i][j];
                }
            }
        }
        return t;
    }

    private double[][] rigidezLocal(Barra b) {
        var s = b.secao();
        double l = modelo.comprimento(b), l2 = l * l, l3 = l2 * l;
        double ea = s.e() * s.area() / l, gj = s.g() * s.j() / l;
        var k = new double[12][12];
        k[0][0] = ea; k[0][6] = -ea; k[6][0] = -ea; k[6][6] = ea;
        if (b.trelica()) {
            return k;
        }
        double eiz = s.e() * s.iz(), eiy = s.e() * s.iy();
        k[3][3] = gj; k[3][9] = -gj; k[9][3] = -gj; k[9][9] = gj;
        // flexão em torno de z (deslocamentos em y)
        k[1][1] = 12 * eiz / l3; k[1][5] = 6 * eiz / l2; k[1][7] = -12 * eiz / l3; k[1][11] = 6 * eiz / l2;
        k[5][1] = 6 * eiz / l2; k[5][5] = 4 * eiz / l; k[5][7] = -6 * eiz / l2; k[5][11] = 2 * eiz / l;
        k[7][1] = -12 * eiz / l3; k[7][5] = -6 * eiz / l2; k[7][7] = 12 * eiz / l3; k[7][11] = -6 * eiz / l2;
        k[11][1] = 6 * eiz / l2; k[11][5] = 2 * eiz / l; k[11][7] = -6 * eiz / l2; k[11][11] = 4 * eiz / l;
        // flexão em torno de y (deslocamentos em z)
        k[2][2] = 12 * eiy / l3; k[2][4] = -6 * eiy / l2; k[2][8] = -12 * eiy / l3; k[2][10] = -6 * eiy / l2;
        k[4][2] = -6 * eiy / l2; k[4][4] = 4 * eiy / l; k[4][8] = 6 * eiy / l2; k[4][10] = 2 * eiy / l;
        k[8][2] = -12 * eiy / l3; k[8][4] = 6 * eiy / l2; k[8][8] = 12 * eiy / l3; k[8][10] = 6 * eiy / l2;
        k[10][2] = -6 * eiy / l2; k[10][4] = 2 * eiy / l; k[10][8] = 6 * eiy / l2; k[10][10] = 4 * eiy / l;
        return k;
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static double[] normalizar(double[] v) {
        double n = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        return new double[]{v[0] / n, v[1] / n, v[2] / n};
    }

    private static double[] escala(double[] v, double f) {
        return new double[]{v[0] * f, v[1] * f, v[2] * f};
    }

    private static double[] mult3(double[][] r, double[] v) {
        return new double[]{r[0][0] * v[0] + r[0][1] * v[1] + r[0][2] * v[2], r[1][0] * v[0] + r[1][1] * v[1] + r[1][2] * v[2], r[2][0] * v[0] + r[2][1] * v[1] + r[2][2] * v[2]};
    }

    private static double[] mult(double[][] m, double[] v) {
        var out = new double[m.length];
        for (int i = 0; i < m.length; i++) {
            double s = 0;
            for (int j = 0; j < v.length; j++) {
                s += m[i][j] * v[j];
            }
            out[i] = s;
        }
        return out;
    }

    private static double[][] mult(double[][] a, double[][] b) {
        var out = new double[a.length][b[0].length];
        for (int i = 0; i < a.length; i++) {
            for (int k = 0; k < b.length; k++) {
                double aik = a[i][k];
                if (aik == 0) {
                    continue;
                }
                for (int j = 0; j < b[0].length; j++) {
                    out[i][j] += aik * b[k][j];
                }
            }
        }
        return out;
    }

    /** Tᵀ·v */
    private static double[] multT(double[][] t, double[] v) {
        var out = new double[t[0].length];
        for (int i = 0; i < t.length; i++) {
            for (int j = 0; j < t[0].length; j++) {
                out[j] += t[i][j] * v[i];
            }
        }
        return out;
    }

    /** Tᵀ·M */
    private static double[][] multT(double[][] t, double[][] m) {
        var out = new double[t[0].length][m[0].length];
        for (int i = 0; i < t.length; i++) {
            for (int j = 0; j < t[0].length; j++) {
                double tij = t[i][j];
                if (tij == 0) {
                    continue;
                }
                for (int c = 0; c < m[0].length; c++) {
                    out[j][c] += tij * m[i][c];
                }
            }
        }
        return out;
    }

    private static double arr(double v) {
        return Math.round(v * 1e4) / 1e4;
    }

    private static double[] arr3(double[] v) {
        return new double[]{arr(v[0]), arr(v[1]), arr(v[2])};
    }
}
