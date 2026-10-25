package br.com.civilconstructorai.estrutura.vaos;

import br.com.civilconstructorai.estrutura.modelo.ElementoEstrutural;
import br.com.civilconstructorai.estrutura.modelo.ModeloEstrutural;
import br.com.civilconstructorai.estrutura.modelo.Ponto;
import br.com.civilconstructorai.estrutura.modelo.TipoElementoEstrutural;

import java.util.ArrayList;
import java.util.List;

/**
 * RF06 · Caixa Cartesiana Sanitizada e verificação do Fencepost Error: a envoltória real vem das extremidades
 * dos pilares/vigas (Xmin, Xmax, Ymin, Ymax), e numa linha de vigas n vãos exigem n+1 apoios.
 */
public final class CalculadoraVaos {

    public static final double TOL = 0.30;

    private CalculadoraVaos() {
    }

    public record Caixa(double xMin, double xMax, double yMin, double yMax) {
        public boolean contem(Ponto p, double folga) {
            return p.x() >= xMin - folga && p.x() <= xMax + folga && p.y() >= yMin - folga && p.y() <= yMax + folga;
        }
    }

    public record LinhaVigas(String eixo, double posicao, int vaos, int apoios, boolean fencepostOk, List<Double> comprimentosVaos) {
    }

    public record Achado(String codigo, String elementoId, String mensagem) {
    }

    public record RelatorioVaos(String pavimentoId, Caixa caixa, List<LinhaVigas> linhas, List<Achado> achados) {
        public boolean aprovado() {
            return achados.isEmpty();
        }
    }

    /** pilares que sustentam o pavimento: lançados nele ou que nascem/chegam nele (base ou topo), sem rascunhos de IA */
    public static List<ElementoEstrutural> pilaresDoPavimento(ModeloEstrutural m, String pavimentoId) {
        return m.elementos().stream()
                .filter(e -> e.tipo() == TipoElementoEstrutural.PILAR && !e.rascunhoIa())
                .filter(e -> pavimentoId.equals(e.pavimentoId()) || pavimentoId.equals(e.nivelBaseId()) || pavimentoId.equals(e.nivelTopoId()))
                .toList();
    }

    public static Caixa caixaReal(ModeloEstrutural m, String pavimentoId) {
        double xMin = Double.MAX_VALUE, xMax = -Double.MAX_VALUE, yMin = Double.MAX_VALUE, yMax = -Double.MAX_VALUE;
        boolean algum = false;
        for (var e : pilaresDoPavimento(m, pavimentoId)) {
            algum = true;
            xMin = Math.min(xMin, e.centro().x()); xMax = Math.max(xMax, e.centro().x());
            yMin = Math.min(yMin, e.centro().y()); yMax = Math.max(yMax, e.centro().y());
        }
        return algum ? new Caixa(xMin, xMax, yMin, yMax) : null;
    }

    public static RelatorioVaos verificar(ModeloEstrutural m, String pavimentoId) {
        var achados = new ArrayList<Achado>();
        var caixa = caixaReal(m, pavimentoId);
        var elementos = m.doPavimento(pavimentoId);
        if (caixa != null) {
            for (var e : elementos) {
                if (e.rascunhoIa() || (e.tipo() != TipoElementoEstrutural.VIGA && e.tipo() != TipoElementoEstrutural.LAJE)) {
                    continue;
                }
                var pts = e.tipo() == TipoElementoEstrutural.VIGA ? List.of(e.p1(), e.p2()) : e.contorno();
                for (var p : pts) {
                    if (!caixa.contem(p, 0.5)) {
                        achados.add(new Achado("C-FORA_DA_CAIXA", e.id(), e.tipo() + " extrapola a envoltória real dos pilares (Fencepost Error): [" + f(p.x()) + ", " + f(p.y()) + "]."));
                        break;
                    }
                }
            }
        }
        var linhas = new ArrayList<LinhaVigas>();
        var pilares = pilaresDoPavimento(m, pavimentoId);
        var vigas = elementos.stream().filter(e -> e.tipo() == TipoElementoEstrutural.VIGA && !e.rascunhoIa()).toList();
        for (boolean horizontal : new boolean[]{true, false}) {
            var posicoes = new ArrayList<Double>();
            for (var v : vigas) {
                boolean ehHorizontal = Math.abs(v.p1().y() - v.p2().y()) <= TOL;
                boolean ehVertical = Math.abs(v.p1().x() - v.p2().x()) <= TOL;
                if (horizontal && ehHorizontal) {
                    adicionarUnico(posicoes, v.p1().y());
                } else if (!horizontal && ehVertical) {
                    adicionarUnico(posicoes, v.p1().x());
                }
            }
            for (double pos : posicoes) {
                var vigasLinha = vigas.stream().filter(v -> horizontal ? Math.abs(v.p1().y() - pos) <= TOL && Math.abs(v.p2().y() - pos) <= TOL
                        : Math.abs(v.p1().x() - pos) <= TOL && Math.abs(v.p2().x() - pos) <= TOL).toList();
                var apoios = pilares.stream().filter(p -> Math.abs((horizontal ? p.centro().y() : p.centro().x()) - pos) <= TOL).toList();
                var coords = new ArrayList<Double>();
                for (var p : apoios) {
                    adicionarUnico(coords, horizontal ? p.centro().x() : p.centro().y());
                }
                coords.sort(Double::compare);
                var vaos = new ArrayList<Double>();
                for (int i = 0; i + 1 < coords.size(); i++) {
                    vaos.add(Math.round((coords.get(i + 1) - coords.get(i)) * 1000) / 1000.0);
                }
                int nVaos = vaos.size();
                boolean ok = coords.size() == nVaos + 1;
                // viga além do último apoio ou antes do primeiro = balanço sem apoio → fencepost
                for (var v : vigasLinha) {
                    double a = horizontal ? Math.min(v.p1().x(), v.p2().x()) : Math.min(v.p1().y(), v.p2().y());
                    double b = horizontal ? Math.max(v.p1().x(), v.p2().x()) : Math.max(v.p1().y(), v.p2().y());
                    if (!coords.isEmpty() && (a < coords.get(0) - 1.5 || b > coords.get(coords.size() - 1) + 1.5)) {
                        ok = false;
                        achados.add(new Achado("FENCEPOST", v.id(), "Viga avança " + f(Math.max(coords.get(0) - a, b - coords.get(coords.size() - 1))) + " m além do último apoio na linha " + (horizontal ? "y=" : "x=") + f(pos) + " (" + nVaos + " vãos exigem " + (nVaos + 1) + " apoios)."));
                    }
                    if (apoios.size() < 2) {
                        ok = false;
                        achados.add(new Achado("APOIO_FALTANTE", v.id(), "Viga na linha " + (horizontal ? "y=" : "x=") + f(pos) + " tem " + apoios.size() + " apoio(s); são necessários ao menos 2."));
                    }
                }
                linhas.add(new LinhaVigas(horizontal ? "y" : "x", pos, nVaos, coords.size(), ok, vaos));
            }
        }
        return new RelatorioVaos(pavimentoId, caixa, linhas, achados);
    }

    private static void adicionarUnico(List<Double> lista, double v) {
        for (var x : lista) {
            if (Math.abs(x - v) <= TOL) {
                return;
            }
        }
        lista.add(v);
    }

    private static String f(double v) {
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }
}
