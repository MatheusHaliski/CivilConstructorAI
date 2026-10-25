package br.com.civilconstructorai.estrutura.mef;

import br.com.civilconstructorai.estrutura.carga.Carga;
import br.com.civilconstructorai.estrutura.carga.NaturezaAcao;
import br.com.civilconstructorai.estrutura.carga.ParametrosVento;
import br.com.civilconstructorai.estrutura.carga.TipoCarga;
import br.com.civilconstructorai.estrutura.modelo.ElementoEstrutural;
import br.com.civilconstructorai.estrutura.modelo.ModeloEstrutural;
import br.com.civilconstructorai.estrutura.modelo.Ponto;
import br.com.civilconstructorai.estrutura.modelo.TipoElementoEstrutural;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Monta o pórtico espacial a partir do recorte BIM: pilares viram barras verticais entre os níveis, vigas viram
 * barras no nível do pavimento ligadas aos pilares a até {@value #TOL_LIGACAO} m, apoios engastados nas bases
 * dos pilares do nível mais baixo. Cargas: peso próprio (25 kN/m³), lajes (peso + cargas de área) repartidas
 * pelas vigas de contorno proporcionalmente ao comprimento, cargas lineares e pontuais por elemento alvo e
 * vento como forças horizontais em X por nível (NBR 6123). Diafragma rígido não é imposto (modelo conservador).
 */
public final class MontadorModeloAnalitico {

    public static final double TOL_LIGACAO = 0.30;
    public static final double PESO_CONCRETO = 25.0;

    private MontadorModeloAnalitico() {
    }

    public record Entrada(ModeloEstrutural modelo, List<Carga> cargas, ParametrosVento vento, Map<String, Double> moduloPorMaterial) {
    }

    public static ModeloAnalitico montar(Entrada in) {
        var m = in.modelo();
        var ma = new ModeloAnalitico();
        // uma viga contínua vira várias barras (uma por trecho entre apoios); pilar continua sendo uma barra
        var barrasPorElemento = new HashMap<String, List<Integer>>();
        var pavs = m.pavimentosOrdenados();
        var elevacaoMinima = pavs.isEmpty() ? 0 : pavs.get(0).elevacao();

        for (var p : m.doTipo(TipoElementoEstrutural.PILAR)) {
            if (p.rascunhoIa()) {
                continue;
            }
            var base = m.pavimento(p.nivelBaseId() == null ? p.pavimentoId() : p.nivelBaseId()).orElse(pavs.get(0));
            double zTopo = base.elevacao() + m.alturaPilar(p);
            int ni = ma.noEm(p.centro().x(), p.centro().y(), base.elevacao(), TOL_LIGACAO);
            int nj = ma.noEm(p.centro().x(), p.centro().y(), zTopo, TOL_LIGACAO);
            var sec = ModeloAnalitico.Secao.retangular(p.b(), p.h(), modulo(in, p.materialId()));
            int id = ma.adicionarBarra(p.id(), rotulo(p, "P"), ni, nj, sec);
            barrasPorElemento.put(p.id(), List.of(id));
            if (Math.abs(base.elevacao() - elevacaoMinima) < 1e-6) {
                ma.adicionarApoio(ModeloAnalitico.Apoio.engaste(ni));
            }
            // peso próprio do pilar como força no topo
            ma.caso(NaturezaAcao.PERMANENTE).nodais().add(new ModeloAnalitico.CargaNodal(nj, 0, 0, -p.b() * p.h() * (zTopo - base.elevacao()) * PESO_CONCRETO, 0, 0, 0));
        }

        // nós das extremidades de todas as vigas antes de discretizar: assim uma viga que nasce no meio de outra
        // também quebra a viga apoio (ligação viga-viga)
        var vigas = m.doTipo(TipoElementoEstrutural.VIGA).stream().filter(v -> !v.rascunhoIa()).toList();
        for (var v : vigas) {
            double z = m.pavimento(v.pavimentoId()).map(x -> x.elevacao()).orElse(0.0);
            ma.noEm(v.p1().x(), v.p1().y(), z, TOL_LIGACAO);
            ma.noEm(v.p2().x(), v.p2().y(), z, TOL_LIGACAO);
        }
        for (var v : vigas) {
            double z = m.pavimento(v.pavimentoId()).map(x -> x.elevacao()).orElse(0.0);
            int ni = ma.noEm(v.p1().x(), v.p1().y(), z, TOL_LIGACAO);
            int nj = ma.noEm(v.p2().x(), v.p2().y(), z, TOL_LIGACAO);
            if (ni == nj) {
                continue;
            }
            // nós intermediários (pilares, outras vigas) sobre o eixo da viga: viga contínua com apoios internos
            var trecho = nosSobreViga(ma, v, z, ni, nj);
            var sec = ModeloAnalitico.Secao.retangular(v.b(), v.h(), modulo(in, v.materialId()));
            var ids = new ArrayList<Integer>();
            for (int k = 0; k + 1 < trecho.size(); k++) {
                var rot = trecho.size() > 2 ? rotulo(v, "V") + "." + (k + 1) : rotulo(v, "V");
                int id = ma.adicionarBarra(v.id(), rot, trecho.get(k), trecho.get(k + 1), sec);
                ids.add(id);
                ma.caso(NaturezaAcao.PERMANENTE).distribuidas().add(new ModeloAnalitico.CargaDistribuida(id, 0, 0, -v.b() * v.h() * PESO_CONCRETO));
            }
            barrasPorElemento.put(v.id(), List.copyOf(ids));
        }

        // lajes: peso próprio + cargas de área, repartidos pelas vigas do contorno
        for (var l : m.doTipo(TipoElementoEstrutural.LAJE)) {
            if (l.rascunhoIa()) {
                continue;
            }
            double area = ElementoEstrutural.areaPoligono(l.contorno());
            var vigasContorno = new ArrayList<ElementoEstrutural>();
            for (var v : m.doPavimento(l.pavimentoId())) {
                if (v.tipo() == TipoElementoEstrutural.VIGA && barrasPorElemento.containsKey(v.id()) && noContorno(v.p1(), l.contorno()) && noContorno(v.p2(), l.contorno())) {
                    vigasContorno.add(v);
                }
            }
            double somaL = vigasContorno.stream().mapToDouble(ElementoEstrutural::comprimento).sum();
            for (var nat : NaturezaAcao.values()) {
                double q = nat == NaturezaAcao.PERMANENTE ? l.h() * PESO_CONCRETO : 0;
                for (var c : in.cargas()) {
                    if (c.getNatureza() == nat && c.getTipo() == TipoCarga.AREA && c.getPavimentoId().equals(l.pavimentoId())
                            && (c.getElementoAlvoId() == null || c.getElementoAlvoId().equals(l.id()))) {
                        q += c.getValor();
                    }
                }
                if (q == 0) {
                    continue;
                }
                double total = q * area;
                if (somaL > 0) {
                    for (var v : vigasContorno) {
                        double porMetro = total * (v.comprimento() / somaL) / v.comprimento();
                        for (int idBarra : barrasPorElemento.get(v.id())) {
                            ma.caso(nat).distribuidas().add(new ModeloAnalitico.CargaDistribuida(idBarra, 0, 0, -porMetro));
                        }
                    }
                } else {
                    // sem vigas de contorno: carga nos pilares dentro da laje
                    var pilares = m.doPavimento(l.pavimentoId()).stream().filter(e -> e.tipo() == TipoElementoEstrutural.PILAR && barrasPorElemento.containsKey(e.id())).toList();
                    for (var p : pilares) {
                        int nj = ma.barras().get(barrasPorElemento.get(p.id()).getFirst()).noJ();
                        ma.caso(nat).nodais().add(new ModeloAnalitico.CargaNodal(nj, 0, 0, -total / Math.max(1, pilares.size()), 0, 0, 0));
                    }
                }
            }
        }

        // cargas lineares e pontuais por elemento alvo
        for (var c : in.cargas()) {
            if (c.getTipo() == TipoCarga.AREA || c.getElementoAlvoId() == null) {
                continue;
            }
            var idsBarras = barrasPorElemento.get(c.getElementoAlvoId());
            if (idsBarras == null || idsBarras.isEmpty()) {
                continue;
            }
            if (c.getTipo() == TipoCarga.LINEAR) {
                for (int idBarra : idsBarras) {
                    ma.caso(c.getNatureza()).distribuidas().add(new ModeloAnalitico.CargaDistribuida(idBarra, 0, 0, -c.getValor()));
                }
            } else {
                int no = ma.barras().get(idsBarras.getLast()).noJ();
                ma.caso(c.getNatureza()).nodais().add(new ModeloAnalitico.CargaNodal(no, 0, 0, -c.getValor(), 0, 0, 0));
            }
        }

        // vento: força horizontal em +X por nível, repartida pelos nós do nível
        if (in.vento() != null && pavs.size() > 1) {
            double larguraY = larguraEmY(m);
            for (int i = 1; i < pavs.size(); i++) {
                double zNivel = pavs.get(i).elevacao();
                double hAbaixo = zNivel - pavs.get(i - 1).elevacao();
                double hAcima = i + 1 < pavs.size() ? pavs.get(i + 1).elevacao() - zNivel : 0;
                double areaFachada = (hAbaixo + hAcima) / 2 * larguraY;
                var nosNivel = ma.nos().stream().filter(n -> Math.abs(n.z() - zNivel) < TOL_LIGACAO).toList();
                if (nosNivel.isEmpty()) {
                    continue;
                }
                double forca = in.vento().forca(areaFachada) / nosNivel.size();
                for (var n : nosNivel) {
                    ma.caso(NaturezaAcao.VENTO).nodais().add(new ModeloAnalitico.CargaNodal(n.id(), forca, 0, 0, 0, 0, 0));
                }
            }
        }
        return ma;
    }

    private static double modulo(Entrada in, String materialId) {
        return in.moduloPorMaterial() != null && materialId != null && in.moduloPorMaterial().containsKey(materialId)
                ? in.moduloPorMaterial().get(materialId) : moduloConcreto(30);
    }

    /** Ecs (NBR 6118 8.2.8) em kN/m² para granito: Eci = αE·5600·√fck; Ecs = αi·Eci */
    public static double moduloConcreto(double fckMpa) {
        double alphaI = Math.min(1.0, 0.8 + 0.2 * fckMpa / 80);
        return alphaI * 5600 * Math.sqrt(fckMpa) * 1000;
    }

    private static String rotulo(ElementoEstrutural e, String prefixo) {
        return e.rotulo() != null ? e.rotulo() : prefixo + "-" + e.id().substring(0, Math.min(6, e.id().length()));
    }

    /** nós do nível z sobre o eixo da viga (inclusive as extremidades), ordenados de p1 para p2 */
    static List<Integer> nosSobreViga(ModeloAnalitico ma, ElementoEstrutural v, double z, int ni, int nj) {
        double dx = v.p2().x() - v.p1().x(), dy = v.p2().y() - v.p1().y();
        double l2 = dx * dx + dy * dy;
        var internos = new java.util.TreeMap<Double, Integer>();
        for (var n : ma.nos()) {
            if (n.id() == ni || n.id() == nj || Math.abs(n.z() - z) > TOL_LIGACAO) {
                continue;
            }
            double t = ((n.x() - v.p1().x()) * dx + (n.y() - v.p1().y()) * dy) / l2;
            if (t <= 1e-6 || t >= 1 - 1e-6) {
                continue;
            }
            double px = v.p1().x() + t * dx, py = v.p1().y() + t * dy;
            if (Math.hypot(n.x() - px, n.y() - py) <= TOL_LIGACAO) {
                internos.putIfAbsent(t, n.id());
            }
        }
        var out = new ArrayList<Integer>();
        out.add(ni);
        out.addAll(internos.values());
        out.add(nj);
        return out;
    }

    private static boolean noContorno(Ponto p, List<Ponto> contorno) {
        for (int i = 0; i < contorno.size(); i++) {
            var a = contorno.get(i); var b = contorno.get((i + 1) % contorno.size());
            double l = a.distancia(b);
            if (l < 1e-9) {
                continue;
            }
            double t = ((p.x() - a.x()) * (b.x() - a.x()) + (p.y() - a.y()) * (b.y() - a.y())) / (l * l);
            t = Math.max(0, Math.min(1, t));
            var proj = new Ponto(a.x() + t * (b.x() - a.x()), a.y() + t * (b.y() - a.y()));
            if (proj.distancia(p) <= TOL_LIGACAO) {
                return true;
            }
        }
        return false;
    }

    private static double larguraEmY(ModeloEstrutural m) {
        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        for (var e : m.elementos()) {
            if (e.centro() != null) {
                min = Math.min(min, e.centro().y());
                max = Math.max(max, e.centro().y());
            }
        }
        return max > min ? max - min : 10.0;
    }
}
