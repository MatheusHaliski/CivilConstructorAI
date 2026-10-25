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
        var barraPorElemento = new HashMap<String, Integer>();
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
            barraPorElemento.put(p.id(), id);
            if (Math.abs(base.elevacao() - elevacaoMinima) < 1e-6) {
                ma.adicionarApoio(ModeloAnalitico.Apoio.engaste(ni));
            }
            // peso próprio do pilar como força no topo
            ma.caso(NaturezaAcao.PERMANENTE).nodais().add(new ModeloAnalitico.CargaNodal(nj, 0, 0, -p.b() * p.h() * (zTopo - base.elevacao()) * PESO_CONCRETO, 0, 0, 0));
        }

        for (var v : m.doTipo(TipoElementoEstrutural.VIGA)) {
            if (v.rascunhoIa()) {
                continue;
            }
            double z = m.pavimento(v.pavimentoId()).map(x -> x.elevacao()).orElse(0.0);
            int ni = ma.noEm(v.p1().x(), v.p1().y(), z, TOL_LIGACAO);
            int nj = ma.noEm(v.p2().x(), v.p2().y(), z, TOL_LIGACAO);
            if (ni == nj) {
                continue;
            }
            var sec = ModeloAnalitico.Secao.retangular(v.b(), v.h(), modulo(in, v.materialId()));
            int id = ma.adicionarBarra(v.id(), rotulo(v, "V"), ni, nj, sec);
            barraPorElemento.put(v.id(), id);
            ma.caso(NaturezaAcao.PERMANENTE).distribuidas().add(new ModeloAnalitico.CargaDistribuida(id, 0, 0, -v.b() * v.h() * PESO_CONCRETO));
        }

        // lajes: peso próprio + cargas de área, repartidos pelas vigas do contorno
        for (var l : m.doTipo(TipoElementoEstrutural.LAJE)) {
            if (l.rascunhoIa()) {
                continue;
            }
            double area = ElementoEstrutural.areaPoligono(l.contorno());
            var vigasContorno = new ArrayList<ElementoEstrutural>();
            for (var v : m.doPavimento(l.pavimentoId())) {
                if (v.tipo() == TipoElementoEstrutural.VIGA && barraPorElemento.containsKey(v.id()) && noContorno(v.p1(), l.contorno()) && noContorno(v.p2(), l.contorno())) {
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
                        ma.caso(nat).distribuidas().add(new ModeloAnalitico.CargaDistribuida(barraPorElemento.get(v.id()), 0, 0, -porMetro));
                    }
                } else {
                    // sem vigas de contorno: carga nos pilares dentro da laje
                    var pilares = m.doPavimento(l.pavimentoId()).stream().filter(e -> e.tipo() == TipoElementoEstrutural.PILAR && barraPorElemento.containsKey(e.id())).toList();
                    for (var p : pilares) {
                        int nj = ma.barras().get(barraPorElemento.get(p.id())).noJ();
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
            var idBarra = barraPorElemento.get(c.getElementoAlvoId());
            if (idBarra == null) {
                continue;
            }
            if (c.getTipo() == TipoCarga.LINEAR) {
                ma.caso(c.getNatureza()).distribuidas().add(new ModeloAnalitico.CargaDistribuida(idBarra, 0, 0, -c.getValor()));
            } else {
                int no = ma.barras().get(idBarra).noJ();
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
