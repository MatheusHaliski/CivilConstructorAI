package br.com.civilconstructorai.estrutura.mef;

import br.com.civilconstructorai.estrutura.carga.NaturezaAcao;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Modelo de pórtico espacial: nós com 6 GDL, barras prismáticas, apoios e cargas por natureza (G, Q, W).
 * Unidades: m, kN, kN/m², rad.
 */
public final class ModeloAnalitico {

    public record No(int id, double x, double y, double z) {
    }

    /** seção: área, inércias (Iy flexão no plano x-z, Iz no plano x-y), constante de torção J, E e G em kN/m² */
    public record Secao(double area, double iy, double iz, double j, double e, double g) {

        /** seção retangular b (largura, eixo y local) × h (altura, eixo z local) */
        public static Secao retangular(double b, double h, double eModulo) {
            double a = Math.max(b, h), c = Math.min(b, h);
            double j = (1.0 / 3 - 0.21 * (c / a) * (1 - Math.pow(c, 4) / (12 * Math.pow(a, 4)))) * a * Math.pow(c, 3);
            return new Secao(b * h, b * h * h * h / 12, h * b * b * b / 12, j, eModulo, eModulo / (2 * 1.2));
        }
    }

    public record Barra(int id, String elementoId, String rotulo, int noI, int noJ, Secao secao) {
    }

    public record Apoio(int no, boolean ux, boolean uy, boolean uz, boolean rx, boolean ry, boolean rz) {
        public static Apoio engaste(int no) {
            return new Apoio(no, true, true, true, true, true, true);
        }

        public static Apoio rotula(int no) {
            return new Apoio(no, true, true, true, true, false, false);
        }

        public boolean[] restritos() {
            return new boolean[]{ux, uy, uz, rx, ry, rz};
        }
    }

    /** carga distribuída uniforme na barra, em coordenadas globais (kN/m): (qx, qy, qz) */
    public record CargaDistribuida(int barra, double qx, double qy, double qz) {
    }

    /** força/momento nodal em coordenadas globais (kN, kN·m) */
    public record CargaNodal(int no, double fx, double fy, double fz, double mx, double my, double mz) {
    }

    public record CasoCarga(NaturezaAcao natureza, List<CargaDistribuida> distribuidas, List<CargaNodal> nodais) {
    }

    private final List<No> nos = new ArrayList<>();
    private final List<Barra> barras = new ArrayList<>();
    private final List<Apoio> apoios = new ArrayList<>();
    private final Map<NaturezaAcao, CasoCarga> casos = new EnumMap<>(NaturezaAcao.class);

    public int adicionarNo(double x, double y, double z) {
        var n = new No(nos.size(), x, y, z);
        nos.add(n);
        return n.id();
    }

    /** reaproveita um nó existente dentro da tolerância (m) */
    public int noEm(double x, double y, double z, double tol) {
        for (var n : nos) {
            if (Math.abs(n.x - x) <= tol && Math.abs(n.y - y) <= tol && Math.abs(n.z - z) <= tol) {
                return n.id;
            }
        }
        return adicionarNo(x, y, z);
    }

    public int adicionarBarra(String elementoId, String rotulo, int noI, int noJ, Secao secao) {
        if (noI == noJ) {
            throw new IllegalArgumentException("Barra " + rotulo + " com os dois nós iguais.");
        }
        var b = new Barra(barras.size(), elementoId, rotulo, noI, noJ, secao);
        barras.add(b);
        return b.id();
    }

    public void adicionarApoio(Apoio apoio) {
        apoios.add(apoio);
    }

    public CasoCarga caso(NaturezaAcao natureza) {
        return casos.computeIfAbsent(natureza, n -> new CasoCarga(n, new ArrayList<>(), new ArrayList<>()));
    }

    public List<No> nos() { return nos; }
    public List<Barra> barras() { return barras; }
    public List<Apoio> apoios() { return apoios; }
    public Map<NaturezaAcao, CasoCarga> casos() { return casos; }

    public double comprimento(Barra b) {
        var i = nos.get(b.noI()); var j = nos.get(b.noJ());
        return Math.sqrt(Math.pow(j.x - i.x, 2) + Math.pow(j.y - i.y, 2) + Math.pow(j.z - i.z, 2));
    }

    public boolean temCasoComCarga(NaturezaAcao n) {
        var c = casos.get(n);
        return c != null && (!c.distribuidas().isEmpty() || !c.nodais().isEmpty());
    }
}
