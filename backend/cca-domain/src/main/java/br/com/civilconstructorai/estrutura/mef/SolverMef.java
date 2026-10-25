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
 * Método dos Elementos Finitos para pórtico espacial (barras de Euler-Bernoulli, 6 GDL por nó), com matriz
 * densa e eliminação de Gauss com pivotamento parcial — suficiente para dezenas de barras. Resolve um caso
 * por natureza de ação e superpõe pelos fatores de cada combinação (análise linear).
 */
public final class SolverMef {

    public static final int PONTOS = 11;

    public static final class EstruturaHipostaticaException extends RuntimeException {
        public EstruturaHipostaticaException(String msg) {
            super(msg);
        }
    }

    /**
     * esforços numa seção: N (tração +), Vy, Vz, T, My (positivo tracionando a face −z local, a "inferior"),
     * Mz (positivo tracionando a face −y local) e flecha (deslocamento transversal resultante, m)
     */
    public record Esforco(double s, double n, double vy, double vz, double t, double my, double mz, double flecha) {
    }

    public record ResultadoBarra(int barra, String elementoId, String rotulo, double comprimento, List<Esforco> pontos,
                                 double nMax, double vMax, double mMax, double mMin, double flechaMax) {
    }

    public record Reacao(int no, double fx, double fy, double fz, double mx, double my, double mz) {
    }

    public record ResultadoCombinacao(Combinacao combinacao, List<ResultadoBarra> barras, List<Reacao> reacoes, double deslocamentoMax) {
    }

    public record ResultadoAnalise(int nos, int barras, Map<String, ResultadoCombinacao> porCombinacao) {
    }

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
            for (var cd : caso.distribuidas()) {
                var barra = modelo.barras().get(cd.barra());
                var r = rotacao(barra);
                double l = modelo.comprimento(barra);
                double[] w = mult3(r, new double[]{cd.qx(), cd.qy(), cd.qz()});
                double[] fe = new double[12];
                fe[0] = w[0] * l / 2; fe[6] = w[0] * l / 2;
                fe[1] = w[1] * l / 2; fe[7] = w[1] * l / 2; fe[5] = w[1] * l * l / 12; fe[11] = -w[1] * l * l / 12;
                fe[2] = w[2] * l / 2; fe[8] = w[2] * l / 2; fe[4] = -w[2] * l * l / 12; fe[10] = w[2] * l * l / 12;
                for (int i = 0; i < 12; i++) {
                    fixas[barra.id()][i] += fe[i];
                }
                var t = transformacao(r);
                var feGlobal = multT(t, fe);
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
                barras.add(esforcos(barra, fLocal, cargaLocalTotal(barra, comb, r), uLocal));
            }
            var reacoes = new ArrayList<Reacao>();
            for (var ap : modelo.apoios()) {
                int b = ap.no() * 6;
                reacoes.add(new Reacao(ap.no(), arr(reacoesVetor[b] - cargasNodais[b]), arr(reacoesVetor[b + 1] - cargasNodais[b + 1]),
                        arr(reacoesVetor[b + 2] - cargasNodais[b + 2]), arr(reacoesVetor[b + 3] - cargasNodais[b + 3]),
                        arr(reacoesVetor[b + 4] - cargasNodais[b + 4]), arr(reacoesVetor[b + 5] - cargasNodais[b + 5])));
            }
            double dmax = 0;
            for (int n = 0; n < modelo.nos().size(); n++) {
                dmax = Math.max(dmax, Math.sqrt(u[n * 6] * u[n * 6] + u[n * 6 + 1] * u[n * 6 + 1] + u[n * 6 + 2] * u[n * 6 + 2]));
            }
            for (var rb : barras) {
                dmax = Math.max(dmax, rb.flechaMax());
            }
            porCombinacao.put(comb.nome(), new ResultadoCombinacao(comb, barras, reacoes, dmax));
        }
        return new ResultadoAnalise(modelo.nos().size(), modelo.barras().size(), porCombinacao);
    }

    // ------------------------------------------------------------------ esforços ao longo da barra
    private double[] cargaLocalTotal(Barra barra, Combinacao comb, double[][] r) {
        double[] w = new double[3];
        for (var caso : modelo.casos().values()) {
            double fator = comb.fator(caso.natureza());
            if (fator == 0) {
                continue;
            }
            for (var cd : caso.distribuidas()) {
                if (cd.barra() == barra.id()) {
                    var wl = mult3(r, new double[]{cd.qx(), cd.qy(), cd.qz()});
                    w[0] += fator * wl[0]; w[1] += fator * wl[1]; w[2] += fator * wl[2];
                }
            }
        }
        return w;
    }

    private ResultadoBarra esforcos(Barra barra, double[] f, double[] w, double[] uLocal) {
        double l = modelo.comprimento(barra);
        var sec = barra.secao();
        var pontos = new ArrayList<Esforco>(PONTOS);
        double nMax = 0, vMax = 0, mMax = -Double.MAX_VALUE, mMin = Double.MAX_VALUE, flechaMax = 0;
        for (int p = 0; p < PONTOS; p++) {
            double s = l * p / (PONTOS - 1);
            double n = -(f[0] + w[0] * s);
            double vy = f[1] + w[1] * s;
            double vz = f[2] + w[2] * s;
            double t = -f[3];
            double my = momentoFletor(f, s, w[2]);
            double mz = -f[5] + f[1] * s + w[1] * s * s / 2;
            double flecha = flecha(uLocal, w, s, l, sec);
            pontos.add(new Esforco(arr(s), arr(n), arr(vy), arr(vz), arr(t), arr(my), arr(mz), flecha));
            nMax = Math.max(nMax, Math.abs(n));
            vMax = Math.max(vMax, Math.max(Math.abs(vy), Math.abs(vz)));
            mMax = Math.max(mMax, my);
            mMin = Math.min(mMin, my);
            flechaMax = Math.max(flechaMax, flecha);
        }
        return new ResultadoBarra(barra.id(), barra.elementoId(), barra.rotulo(), arr(l), pontos, arr(nMax), arr(vMax), arr(mMax), arr(mMin), flechaMax);
    }

    /**
     * Momento fletor no plano x-z pela estática do trecho [0, s]: as forças nodais f são as que os nós aplicam
     * na barra (convenção da matriz de rigidez, θy = −dw/dx). M(s) positivo = face inferior (−z local) tracionada.
     */
    private static double momentoFletor(double[] f, double s, double wz) {
        return f[4] + f[2] * s + wz * s * s / 2;
    }

    /**
     * Deslocamento transversal resultante em s: interpolação de Hermite dos deslocamentos nodais locais somada
     * à solução particular da carga uniforme (w·s²(L−s)²/24EI), exata para Euler-Bernoulli.
     */
    private static double flecha(double[] u, double[] w, double s, double l, ModeloAnalitico.Secao sec) {
        double xi = s / l;
        double n1 = 1 - 3 * xi * xi + 2 * xi * xi * xi;
        double n2 = l * (xi - 2 * xi * xi + xi * xi * xi);
        double n3 = 3 * xi * xi - 2 * xi * xi * xi;
        double n4 = l * (xi * xi * xi - xi * xi);
        double particular = s * s * (l - s) * (l - s) / 24;
        // plano x-y: v = N1·uy_i + N2·θz_i + N3·uy_j + N4·θz_j
        double vy = n1 * u[1] + n2 * u[5] + n3 * u[7] + n4 * u[11] + w[1] * particular / (sec.e() * sec.iz());
        // plano x-z: θy = −dw/dx → sinais de N2 e N4 trocados
        double vz = n1 * u[2] - n2 * u[4] + n3 * u[8] - n4 * u[10] + w[2] * particular / (sec.e() * sec.iy());
        return Math.sqrt(vy * vy + vz * vz);
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

    private static Lu fatorar(double[][] kIn) {
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
                throw new EstruturaHipostaticaException("Matriz de rigidez singular: estrutura hipostática ou mecanismo (grau de liberdade " + c + ").");
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

    private double[][] rotacao(Barra b) {
        No i = modelo.nos().get(b.noI()), j = modelo.nos().get(b.noJ());
        double l = modelo.comprimento(b);
        double[] ex = {(j.x() - i.x()) / l, (j.y() - i.y()) / l, (j.z() - i.z()) / l};
        boolean vertical = Math.abs(ex[2]) > 0.999;
        double[] ref = vertical ? new double[]{1, 0, 0} : new double[]{0, 0, 1};
        double[] ey = normalizar(cross(ref, ex));
        double[] ez = cross(ex, ey);
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
        double eiz = s.e() * s.iz(), eiy = s.e() * s.iy();
        var k = new double[12][12];
        k[0][0] = ea; k[0][6] = -ea; k[6][0] = -ea; k[6][6] = ea;
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
}
