package br.com.civilconstructorai.modelo;

import br.com.civilconstructorai.modelo.Enums.TipoElemento;
import br.com.civilconstructorai.modelo.RelatorioAuditoria.Correcao;
import br.com.civilconstructorai.modelo.RelatorioAuditoria.Violacao;
import br.com.civilconstructorai.notacao.Geo;
import br.com.civilconstructorai.notacao.Ponto2D;
import br.com.civilconstructorai.notacao.TabelaA;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loop duplo determinístico (RF16): laço 1 (código/Tabela C: ShortCurveTolerance e vetor nulo RF08, seções
 * mínimas NBR 6118, Tabela A, Caixa Cartesiana RF06, Disciplina Z RF17) e laço 2 (geometria, via
 * {@link ServicoGeometria}). Corrige o corrigível e registra cada correção; reprova o resto.
 */
public class Auditor {

    public enum PoliticaCaixa { RECORTAR, BLOQUEAR }

    /** {@code materialExtra}: códigos aceitos além do catálogo (materiais personalizados da empresa ativa) */
    public record Opcoes(double shortCurveTolerance, boolean conclusao, PoliticaCaixa politicaCaixa, java.util.function.Predicate<String> materialExtra) {
        public Opcoes(double shortCurveTolerance, boolean conclusao, PoliticaCaixa politicaCaixa) {
            this(shortCurveTolerance, conclusao, politicaCaixa, c -> false);
        }

        public static Opcoes padrao() {
            return new Opcoes(0.05, false, PoliticaCaixa.RECORTAR);
        }
    }

    static final double PILAR_LADO_MIN = 0.19, PILAR_AREA_MIN = 0.036, VIGA_B_MIN = 0.12, LAJE_H_MIN = 0.08, PAREDE_ESP_MIN = 0.05;

    private final ServicoGeometria geometria;

    public Auditor(ServicoGeometria geometria) {
        this.geometria = geometria;
    }

    public RelatorioAuditoria auditar(ModeloBim m, DiffModelo diff, Opcoes opts) {
        var v1 = new ArrayList<Violacao>();
        var correcoes = new ArrayList<Correcao>();
        double tol = opts.shortCurveTolerance();

        for (var e : diff.novosOuAlterados()) {
            var mat = e.getMaterialId() == null ? null : TabelaA.material(e.getMaterialId()).orElse(null);
            if (e.getMaterialId() != null && mat == null && !opts.materialExtra().test(e.getMaterialId())) {
                v1.add(Violacao.de("VI-003", "Tabela A", "Material " + e.getMaterialId() + " não existe na Tabela A.", e));
            }
            if (e.temSegmento()) {
                double l = e.ponto("p1").distancia(e.ponto("p2"));
                if (l < 1e-9) {
                    v1.add(Violacao.de("C-VETOR_NULO", "ShortCurveTolerance", e.getTipo() + " com comprimento nulo descartado antes do kernel.", e).comValores("≥ " + tol + " m", "0 m"));
                } else if (l < tol) {
                    v1.add(Violacao.de("C-SHORT_CURVE", "ShortCurveTolerance", String.format(Locale.ROOT, "%s de %.3f m é menor que a tolerância de %s m.", e.getTipo(), l, tol), e)
                            .comValores("≥ " + tol + " m", String.format(Locale.ROOT, "%.3f m", l)));
                }
            }
            switch (e.getTipo()) {
                case PILAR -> {
                    double b = e.num("b", 0.19), h = e.num("h", 0.4);
                    if (Math.min(b, h) < PILAR_LADO_MIN) {
                        v1.add(Violacao.de("C-SECAO_MIN_PILAR", "NBR 6118 13.2.3", String.format(Locale.ROOT, "Pilar %.0fx%.0f cm: menor lado deve ser ≥ 19 cm.", b * 100, h * 100), e).comValores("≥ 0,19 m", Math.min(b, h) + " m"));
                    }
                    if (b * h < PILAR_AREA_MIN) {
                        v1.add(Violacao.de("C-AREA_MIN_PILAR", "NBR 6118 13.2.3", "Área da seção do pilar deve ser ≥ 360 cm².", e).comValores("≥ 360 cm²", String.format(Locale.ROOT, "%.0f cm²", b * h * 1e4)));
                    }
                    disciplinaZ(m, e, v1, correcoes);
                }
                case VIGA -> {
                    if (e.num("b", 0.12) < VIGA_B_MIN) {
                        v1.add(Violacao.de("C-SECAO_MIN_VIGA", "NBR 6118 13.2.2", String.format(Locale.ROOT, "Viga com b = %.0f cm: mínimo 12 cm.", e.num("b", 0) * 100), e).comValores("≥ 0,12 m", e.num("b", 0) + " m"));
                    }
                }
                case LAJE -> {
                    if (e.num("h", 0.12) < LAJE_H_MIN) {
                        v1.add(Violacao.de("C-ESP_MIN_LAJE", "NBR 6118 13.2.4.1", String.format(Locale.ROOT, "Laje com h = %.0f cm: mínimo 8 cm.", e.num("h", 0) * 100), e).comValores("≥ 0,08 m", e.num("h", 0) + " m"));
                    }
                    if (e.pontos("contorno").size() < 3) {
                        v1.add(Violacao.de("C-CONTORNO", "RF04", "Laje precisa de contorno fechado com 3+ vértices.", e));
                    }
                }
                case PAREDE -> {
                    double esp = e.num("espessura", 0.15);
                    if (esp < PAREDE_ESP_MIN || esp > 1) {
                        v1.add(Violacao.de("C-ESP_PAREDE", "RF01 RN-01", "Espessura de parede entre 0,05 m e 1,00 m.", e).comValores("0,05–1,00 m", esp + " m"));
                    }
                    if (mat != null && mat.geometricConstraints().espessuraMinima_m() != null && esp < mat.geometricConstraints().espessuraMinima_m()) {
                        v1.add(Violacao.de("C-ESP_MATERIAL", "Tabela A", mat.name() + " exige espessura ≥ " + mat.geometricConstraints().espessuraMinima_m() + " m.", e));
                    }
                }
                default -> { }
            }
        }

        // objetos e conexões prediais (AUD-02/03/04): mesmas regras do auditor TS
        var novos = diff.novosOuAlterados();
        for (var e : novos) {
            if (e.getTipo() == TipoElemento.OBJETO) {
                ObjetosPrediais.problemasObjeto(m, e).forEach(p -> v1.add(Violacao.de(p.codigo(), "Biblioteca de objetos", p.mensagem(), e)));
            }
            if (e.getTipo() == TipoElemento.CONEXAO) {
                ObjetosPrediais.problemasConexao(m, e).forEach(p -> v1.add(Violacao.de(p.codigo(), "Conexões prediais", p.mensagem(), e)));
            }
        }
        var mudaram = new java.util.HashSet<String>();
        novos.stream().filter(e -> e.getTipo() == TipoElemento.OBJETO).forEach(e -> mudaram.add(e.getId().toString()));
        var avisosConexao = new ArrayList<Violacao>();
        for (var c : m.elementos()) {
            if (c.getTipo() != TipoElemento.CONEXAO || novos.contains(c)) {
                continue;
            }
            String deId = String.valueOf(ObjetosPrediais.ponta(c, "de").get("id")), paraId = String.valueOf(ObjetosPrediais.ponta(c, "para").get("id"));
            if (!mudaram.contains(deId) && !mudaram.contains(paraId)) {
                continue;
            }
            var probs = ObjetosPrediais.problemasConexao(m, c);
            String rotulo = c.getRotulo() != null ? c.getRotulo() : m.rotuloAutomatico(c);
            probs.forEach(p -> v1.add(Violacao.de(p.codigo(), "Conexões prediais", "Conexão " + rotulo + " ficou inválida: " + p.mensagem(), c)));
            if (probs.isEmpty()) {
                String nomes = ObjetosPrediais.objeto(m, ObjetosPrediais.ponta(c, "de")).flatMap(ObjetosPrediais::item).map(i -> i.nome()).orElse(deId) + " → "
                        + ObjetosPrediais.objeto(m, ObjetosPrediais.ponta(c, "para")).flatMap(ObjetosPrediais::item).map(i -> i.nome()).orElse(paraId);
                avisosConexao.add(new Violacao("CON-RECALCULADA", Enums.Severidade.AVISO, "Conexões prediais", c.getId(),
                        String.format(Locale.ROOT, "Conexão %s continua ligada; percurso esquemático recalculado: %.2f m.", nomes, ObjetosPrediais.comprimento(ObjetosPrediais.percurso(m, c))), null, null));
            }
        }

        caixaCartesiana(m, diff, opts, v1, correcoes);

        boolean l1 = v1.isEmpty();
        var r2 = geometria.lacoGeometria(m, diff);
        var v2 = new ArrayList<>(r2.violacoes());
        var avisos = new ArrayList<Violacao>();
        for (var a : r2.avisos()) {
            if (opts.conclusao() && a.codigo().equals("ENVOLTORIA_ABERTA")) {
                v2.add(new Violacao(a.codigo(), Enums.Severidade.VIOLACAO, a.regra(), a.elementoRef(), a.mensagem(), a.esperado(), a.obtido()));
            } else {
                avisos.add(a);
            }
        }
        avisos.addAll(avisosConexao);
        boolean l2 = v2.isEmpty();
        return new RelatorioAuditoria(new RelatorioAuditoria.Laco(l1, v1), new RelatorioAuditoria.Laco2(l2, v2, avisos), correcoes, l1 && l2, 0);
    }

    /** RF17: base e topo presos aos níveis, offsets zerados, transpasse só com `continuo` */
    private static void disciplinaZ(ModeloBim m, Elemento e, List<Violacao> v1, List<Correcao> correcoes) {
        if (e.num("baseOffset", 0) != 0) {
            correcoes.add(new Correcao("RF17 Disciplina Z", e.getId(), "baseOffset", e.num("baseOffset", 0), 0.0));
            e.setNum("baseOffset", 0);
        }
        if (e.num("topoOffset", 0) != 0) {
            correcoes.add(new Correcao("RF17 Disciplina Z", e.getId(), "topoOffset", e.num("topoOffset", 0), 0.0));
            e.setNum("topoOffset", 0);
        }
        var base = m.pavimento(e.uuid("nivelBaseId")).orElse(null);
        var topo = m.pavimento(e.uuid("nivelTopoId")).orElse(null);
        if (base == null) {
            var pavDoElemento = m.pavimento(e.getPavimentoId()).orElse(null);
            if (pavDoElemento != null) {
                correcoes.add(new Correcao("RF17 Disciplina Z", e.getId(), "nivelBaseId", e.uuid("nivelBaseId"), pavDoElemento.id()));
                e.getProps().put("nivelBaseId", pavDoElemento.id());
                base = pavDoElemento;
            }
        }
        if (base != null && topo != null && topo.elevacao() < base.elevacao()) {
            v1.add(Violacao.de("Z-TOPO_ABAIXO_BASE", "RF17 Disciplina Z", "Topo do pilar abaixo da base.", e));
        }
        if (base != null) {
            var acima = m.pavimentoAcima(base.id()).orElse(null);
            boolean topoEhAcima = topo != null && acima != null && topo.id().equals(acima.id());
            boolean topoEhBase = topo != null && topo.id().equals(base.id());
            if (acima != null && !topoEhAcima && !e.flag("continuo", false)) {
                if (!topoEhBase || topo == null) {
                    correcoes.add(new Correcao("RF17 transpasse", e.getId(), "nivelTopoId", e.uuid("nivelTopoId"), acima.id()));
                } else {
                    correcoes.add(new Correcao("RF17 Disciplina Z", e.getId(), "nivelTopoId", e.uuid("nivelTopoId"), acima.id()));
                }
                e.getProps().put("nivelTopoId", acima.id());
            }
        }
    }

    /** RF06: nada além da envoltória real dos eixos (Fencepost Error); contagem de apoios = vãos + 1 */
    private static void caixaCartesiana(ModeloBim m, DiffModelo diff, Opcoes opts, List<Violacao> v1, List<Correcao> correcoes) {
        var cx = m.caixaDosEixos().orElse(null);
        if (cx == null) {
            return;
        }
        double folga = 0.5;
        for (var e : diff.novosOuAlterados()) {
            if (e.getTipo() != TipoElemento.PILAR && e.getTipo() != TipoElemento.VIGA && e.getTipo() != TipoElemento.LAJE) {
                continue;
            }
            var pts = e.todosOsPontos();
            if (pts.isEmpty() && (e.getTipo() == TipoElemento.PORTA || e.getTipo() == TipoElemento.JANELA)) {
                pts = List.of(m.pontoRepresentativo(e));
            }
            boolean fora = pts.stream().anyMatch(p -> !cx.contem(p, folga));
            if (!fora) {
                continue;
            }
            if (opts.politicaCaixa() == PoliticaCaixa.RECORTAR && e.temSegmento()) {
                var antes = Map.of("p1", e.ponto("p1"), "p2", e.ponto("p2"));
                e.setPonto("p1", cx.clampar(e.ponto("p1"), folga));
                e.setPonto("p2", cx.clampar(e.ponto("p2"), folga));
                correcoes.add(new Correcao("RF06 Caixa Cartesiana", e.getId(), "p1/p2", antes, Map.of("p1", e.ponto("p1"), "p2", e.ponto("p2"))));
            } else if (opts.politicaCaixa() == PoliticaCaixa.RECORTAR && e.getTipo() == TipoElemento.LAJE) {
                var antes = e.pontos("contorno");
                e.setPontos("contorno", antes.stream().map(p -> cx.clampar(p, folga)).toList());
                correcoes.add(new Correcao("RF06 Caixa Cartesiana", e.getId(), "contorno", antes, e.pontos("contorno")));
            } else {
                v1.add(Violacao.de("C-FORA_DA_CAIXA", "RF06 Caixa Cartesiana", e.getTipo() + " extrapola a envoltória real dos eixos (Fencepost Error).", e)
                        .comValores(String.format(Locale.ROOT, "[%.2f,%.2f]–[%.2f,%.2f]", cx.min().x(), cx.min().y(), cx.max().x(), cx.max().y()), pts.toString()));
            }
        }
        // fencepost: numa linha de vigas sobre um eixo, apoios (pilares) = vãos + 1
        for (var e : diff.novosOuAlterados()) {
            if (e.getTipo() != TipoElemento.VIGA) {
                continue;
            }
            var p1 = e.ponto("p1");
            var p2 = e.ponto("p2");
            boolean apoio1 = temApoio(m, e, p1), apoio2 = temApoio(m, e, p2);
            if (!apoio1 || !apoio2) {
                v1.add(Violacao.de("C-APOIO_FALTANTE", "RF06 Fencepost (apoios = vãos + 1)", "Viga sem apoio (pilar ou viga) em " + (!apoio1 ? "p1" : "p2") + ".", e).aviso());
                v1.remove(v1.size() - 1); // apoio faltante é aviso informativo (política INSERIR fica para o RF04); não reprova
            }
        }
    }

    private static boolean temApoio(ModeloBim m, Elemento viga, Ponto2D p) {
        for (var o : m.elementosDo(viga.getPavimentoId())) {
            if (o.getId().equals(viga.getId())) {
                continue;
            }
            if (o.getTipo() == TipoElemento.PILAR && o.ponto("centro").distancia(p) <= Math.max(o.num("b", 0.19), o.num("h", 0.4)) / 2 + 0.05) {
                return true;
            }
            if ((o.getTipo() == TipoElemento.VIGA || o.getTipo() == TipoElemento.PAREDE) && o.temSegmento() && Geo.projetarNoSegmento(p, o.ponto("p1"), o.ponto("p2")).ponto().distancia(p) <= 0.1) {
                return true;
            }
        }
        return false;
    }
}
