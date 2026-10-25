package br.com.civilconstructorai.modelo;

import br.com.civilconstructorai.modelo.Enums.TipoElemento;
import br.com.civilconstructorai.modelo.RelatorioAuditoria.Violacao;
import br.com.civilconstructorai.notacao.Geo;
import br.com.civilconstructorai.notacao.Ponto2D;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Laço 2 em Java com JTS (sem kernel BRep): validade do contorno (auto-interseção, área > 0),
 * interpenetração pilar×pilar, abertura cabendo na face com afastamento ≥ 0,10 m, fechamento da envoltória
 * (aviso na edição, violação na conclusão — RF16 CA-09) e apoio inferior de pilares (RF17).
 */
public class ServicoGeometriaLocal implements ServicoGeometria {

    private static final GeometryFactory GF = new GeometryFactory();

    @Override
    public Resultado lacoGeometria(ModeloBim m, DiffModelo diff) {
        var violacoes = new ArrayList<Violacao>();
        var avisos = new ArrayList<Violacao>();
        for (var e : diff.novosOuAlterados()) {
            switch (e.getTipo()) {
                case LAJE, HACHURA -> {
                    var c = e.pontos("contorno");
                    if (c.size() >= 3) {
                        if (Geo.temAutoIntersecao(c)) {
                            violacoes.add(Violacao.de("G-AUTO_INTERSECAO", "BRep", "Contorno da laje se auto-intersecta.", e));
                        } else if (!poligono(c).isValid() || poligono(c).getArea() <= 1e-6) {
                            violacoes.add(Violacao.de("G-CONTORNO_INVALIDO", "BRep", "Contorno sem área (sólido degenerado).", e));
                        }
                    }
                }
                case PILAR -> {
                    for (var o : m.elementosDo(e.getPavimentoId())) {
                        if (o.getTipo() == TipoElemento.PILAR && !o.getId().equals(e.getId())
                                && o.ponto("centro").distancia(e.ponto("centro")) < (e.num("b", 0.19) + o.num("b", 0.19)) / 2 - 1e-6) {
                            violacoes.add(Violacao.de("G-INTERPENETRACAO", "BRep", "Pilar sobreposto a " + (o.getRotulo() != null ? o.getRotulo() : o.getId()) + ".", e));
                        }
                    }
                }
                case PORTA, JANELA -> {
                    var w = m.elemento(e.uuid("paredeId")).orElse(null);
                    if (w == null) {
                        violacoes.add(Violacao.de("G-SEM_HOSPEDEIRA", "NewOpening", "Abertura sem parede hospedeira.", e));
                        break;
                    }
                    double l = w.ponto("p1").distancia(w.ponto("p2"));
                    double ini = e.num("posicao", 0.5) * l - e.num("largura", 0.8) / 2, fim = e.num("posicao", 0.5) * l + e.num("largura", 0.8) / 2;
                    if (ini < 0.1 || fim > l - 0.1) {
                        violacoes.add(Violacao.de("G-ABERTURA_FORA_DA_FACE", "NewOpening", "Abertura precisa caber inteira na face com afastamento ≥ 0,10 m das bordas.", e)
                                .comValores("0,10 m das bordas", String.format(Locale.ROOT, "%.2f..%.2f de %.2f m", ini, fim, l)));
                    }
                    if (e.num("peitoril", 0) + e.num("altura", 2.1) > w.num("altura", 2.8) + 1e-9) {
                        violacoes.add(Violacao.de("G-ABERTURA_ALTA", "NewOpening", "Abertura mais alta que a parede.", e));
                    }
                }
                default -> { }
            }
        }
        var pavs = new LinkedHashSet<UUID>();
        diff.novosOuAlterados().forEach(e -> pavs.add(e.getPavimentoId()));
        for (var pav : pavs) {
            var lacuna = envoltoriaAberta(m, pav);
            if (lacuna != null) {
                avisos.add(violacaoEnvoltoria(m, pav, lacuna).aviso());
            }
        }
        return new Resultado(violacoes, avisos);
    }

    @Override
    public List<Violacao> verificarConclusao(ModeloBim m, UUID pavimentoId) {
        var out = new ArrayList<Violacao>();
        var lacuna = envoltoriaAberta(m, pavimentoId);
        if (lacuna != null) {
            out.add(violacaoEnvoltoria(m, pavimentoId, lacuna));
        }
        for (var e : m.elementosDo(pavimentoId)) {
            if (e.getTipo() == TipoElemento.PILAR && !temApoioInferior(m, e)) {
                out.add(Violacao.de("Z-SEM_APOIO_INFERIOR", "RF17 transição", "Pilar " + (e.getRotulo() != null ? e.getRotulo() : m.rotuloAutomatico(e)) + " nasce sem pilar abaixo (transição em viga?). Confirme.", e).aviso());
            }
        }
        return out;
    }

    private static Violacao violacaoEnvoltoria(ModeloBim m, UUID pav, double[] lacuna) {
        var nome = m.pavimento(pav).map(Pavimento::nome).orElse("?");
        return new Violacao("ENVOLTORIA_ABERTA", Enums.Severidade.VIOLACAO, "RF16 fechamento da envoltória", null,
                String.format(Locale.ROOT, "Envoltória aberta no pavimento %s: folga de %.2f m em [%.2f, %.2f].", nome, lacuna[2], lacuna[0], lacuna[1]),
                "≤ 0,001 m", String.format(Locale.ROOT, "%.3f m", lacuna[2]));
    }

    private static Polygon poligono(List<Ponto2D> c) {
        var coords = new Coordinate[c.size() + 1];
        for (int i = 0; i < c.size(); i++) {
            coords[i] = new Coordinate(c.get(i).x(), c.get(i).y());
        }
        coords[c.size()] = coords[0];
        LinearRing anel = GF.createLinearRing(coords);
        return GF.createPolygon(anel);
    }

    /** devolve {x, y, folga} da pior lacuna ou null se o anel de paredes externas está fechado */
    public static double[] envoltoriaAberta(ModeloBim m, UUID pavimentoId) {
        var paredes = m.elementosDo(pavimentoId).stream().filter(e -> e.getTipo() == TipoElemento.PAREDE && e.flag("externa", true)).toList();
        if (paredes.isEmpty()) {
            return null;
        }
        var extremos = new ArrayList<Ponto2D>();
        var donos = new ArrayList<Elemento>();
        for (var w : paredes) {
            extremos.add(w.ponto("p1"));
            donos.add(w);
            extremos.add(w.ponto("p2"));
            donos.add(w);
        }
        double[] pior = null;
        for (int i = 0; i < extremos.size(); i++) {
            var p = extremos.get(i);
            double menor = Double.POSITIVE_INFINITY;
            for (int j = 0; j < extremos.size(); j++) {
                if (j != i) {
                    menor = Math.min(menor, p.distancia(extremos.get(j)));
                }
            }
            boolean conectado = menor <= 0.001;
            if (!conectado) {
                // extremo apoiado no meio de OUTRA parede (encontro em T); a própria parede não conta
                // (após a ida e volta pelo JSON os pontos são novas instâncias, então compara-se a parede dona, não o ponto)
                for (var w : paredes) {
                    if (w != donos.get(i) && pontoNaParede(p, w)) {
                        conectado = true;
                        break;
                    }
                }
            }
            if (!conectado && (pior == null || menor > pior[2])) {
                pior = new double[]{p.x(), p.y(), Double.isInfinite(menor) ? 0 : menor};
            }
        }
        return pior;
    }

    private static boolean pontoNaParede(Ponto2D p, Elemento w) {
        var a = w.ponto("p1");
        var b = w.ponto("p2");
        double d = a.distancia(b);
        if (d < 1e-9) {
            return false;
        }
        double t = ((p.x() - a.x()) * (b.x() - a.x()) + (p.y() - a.y()) * (b.y() - a.y())) / (d * d);
        if (t < -1e-6 || t > 1 + 1e-6) {
            return false;
        }
        var proj = Ponto2D.de(a.x() + t * (b.x() - a.x()), a.y() + t * (b.y() - a.y()));
        return proj.distancia(p) <= 0.001 + w.num("espessura", 0.15) / 2;
    }

    static boolean temApoioInferior(ModeloBim m, Elemento pilar) {
        var base = m.pavimento(pilar.uuid("nivelBaseId")).orElse(null);
        if (base == null) {
            return true;
        }
        var ord = m.pavimentosOrdenados();
        int i = ord.indexOf(base);
        if (i <= 0) {
            return true;
        }
        var abaixo = ord.get(i - 1);
        return m.elementosDo(abaixo.id()).stream().anyMatch(e ->
                (e.getTipo() == TipoElemento.PILAR && e.ponto("centro").distancia(pilar.ponto("centro")) < 0.05)
                        || e.getTipo() == TipoElemento.SAPATA || e.getTipo() == TipoElemento.BLOCO || e.getTipo() == TipoElemento.RADIER);
    }
}
