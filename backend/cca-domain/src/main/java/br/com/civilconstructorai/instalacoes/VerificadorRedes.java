package br.com.civilconstructorai.instalacoes;

import br.com.civilconstructorai.estrutura.dimensionamento.Verificacao;
import br.com.civilconstructorai.notacao.CatalogoMateriais;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * PROPOSTA P-MEP-01 (auditoria 2026-10) — motor experimental, sem RF e ainda não ligado ao modelo BIM. Verifica redes
 * de instalações descritas como componentes (com portas implícitas) e trechos com conectividade explícita
 * (origem → destino). Distingue o que é regra lógica ou física (continuidade, sistema, sentido, declividade,
 * corrente de projeto, queda de tensão) do que depende de texto normativo não consultado (perdas de carga e pressões
 * da ABNT NBR 5626:2020, capacidade de condução da ABNT NBR 5410:2004): esses ficam "Não verificado" ou
 * "Dados insuficientes" até o projetista informar o valor de uma fonte identificada. Uma linha desenhada não é
 * dimensionamento: sem trecho conectado, nada é verificado.
 */
public final class VerificadorRedes {

    public enum Sistema { ELETRICA, AGUA_FRIA, AGUA_QUENTE, ESGOTO, VENTILACAO }

    public enum Regime { GRAVIDADE, PRESSURIZADO, CONDUTOR }

    /** tipos de componente: QUADRO, TOMADA, LUMINARIA, CAIXA, ENTRADA, RESERVATORIO, REGISTRO, VALVULA, AQUECEDOR, PONTO_CONSUMO, APARELHO, CAIXA_INSPECAO, COLETOR, TERMINAL_VENTILACAO */
    public record Componente(String id, String tipo, Sistema sistema, double x, double y, double z, Map<String, Object> props) {
        double num(String k) {
            return props.get(k) instanceof Number n ? n.doubleValue() : Double.NaN;
        }
    }

    /**
     * {@code dnMm}: diâmetro nominal (tubos) ou seção em mm² (condutores, em {@code secaoMm2}); cotas da geratriz
     * inferior nas extremidades (m); {@code circuito}: identificação do circuito elétrico; {@code condutores}: "F", "N", "PE".
     */
    public record Trecho(String id, Sistema sistema, String origemId, String destinoId, String materialId, Regime regime, double comprimentoM,
                         double dnMm, double cotaOrigem, double cotaDestino, String circuito, double secaoMm2, Set<String> condutores) {
    }

    public record Rede(List<Componente> componentes, List<Trecho> trechos) {
    }

    /** parâmetros declarados pelo projetista, com a fonte; NaN = não informado */
    public record Parametros(double tensaoV, double fatorPotencia, double limiteQuedaTensaoPct, String fonteLimiteQueda) {
        public static Parametros naoInformados() {
            return new Parametros(Double.NaN, Double.NaN, Double.NaN, null);
        }
    }

    public record Resultado(String assinatura, List<Item> itens) {
        public Verificacao.Estado estado() {
            return Verificacao.consolidar(itens.stream().map(Item::verificacao).toList());
        }
    }

    public record Item(String elementoId, Sistema sistema, Verificacao verificacao) {
    }

    /** resistividade do cobre a 20 °C (Ω·mm²/m), constante física; em operação o condutor aquece e a resistência cresce */
    static final double RHO_COBRE_20C = 0.0172;

    static final String NBR8160 = "ABNT NBR 8160:1999";
    static final String NBR5626 = "ABNT NBR 5626:2020";
    static final String NBR5410 = "ABNT NBR 5410:2004";

    private VerificadorRedes() {
    }

    public static Resultado verificar(Rede rede, Parametros par) {
        var out = new ArrayList<Item>();
        var porId = new HashMap<String, Componente>();
        rede.componentes().forEach(c -> porId.put(c.id(), c));
        for (var t : rede.trechos()) {
            conectividade(t, porId, out);
            material(t, out);
        }
        esgoto(rede, porId, out);
        ventilacao(rede, out);
        aguas(rede, porId, out);
        eletrica(rede, porId, par, out);
        return new Resultado(assinatura(rede, par), out);
    }

    // ------------------------------------------------------------------ comum
    private static void conectividade(Trecho t, Map<String, Componente> porId, List<Item> out) {
        var o = porId.get(t.origemId());
        var d = porId.get(t.destinoId());
        if (o == null || d == null) {
            out.add(new Item(t.id(), t.sistema(), Verificacao.naoAtendida("MEP-CON-01", "Extremidades ligadas a componentes", "CONECTIVIDADE", "regra lógica do modelo",
                    "origem " + (o == null ? "inexistente" : t.origemId()) + "; destino " + (d == null ? "inexistente" : t.destinoId()), "Ligue o trecho a componentes existentes.")));
            return;
        }
        boolean aquecedor = (o.tipo().equals("AQUECEDOR") || d.tipo().equals("AQUECEDOR")) && Set.of(Sistema.AGUA_FRIA, Sistema.AGUA_QUENTE).contains(t.sistema());
        boolean ventEsgoto = java.util.EnumSet.of(o.sistema(), d.sistema(), t.sistema()).equals(java.util.EnumSet.of(Sistema.ESGOTO, Sistema.VENTILACAO));
        if (!aquecedor && !ventEsgoto && (o.sistema() != t.sistema() || d.sistema() != t.sistema())) {
            out.add(new Item(t.id(), t.sistema(), Verificacao.naoAtendida("MEP-SIS-01", "Sistemas distintos não se misturam", "CONECTIVIDADE", "regra lógica do modelo",
                    "trecho " + t.sistema() + " liga " + o.sistema() + " (" + o.id() + ") a " + d.sistema() + " (" + d.id() + ")",
                    "Água fria e quente só se encontram no aquecedor; esgoto e ventilação só no ponto de ventilação.")));
        }
    }

    private static void material(Trecho t, List<Item> out) {
        if (t.materialId() == null) {
            out.add(new Item(t.id(), t.sistema(), Verificacao.dadosInsuficientes("MEP-MAT-01", "Material compatível com o sistema", "ESPECIFICACAO", null, "Material do trecho não informado.")));
            return;
        }
        var item = CatalogoMateriais.padrao().item(t.materialId()).orElse(null);
        if (item == null) {
            out.add(new Item(t.id(), t.sistema(), Verificacao.dadosInsuficientes("MEP-MAT-01", "Material compatível com o sistema", "ESPECIFICACAO", null, "Material " + t.materialId() + " fora do catálogo.")));
        } else if (!item.aplicacoes().contains(t.sistema().name())) {
            out.add(new Item(t.id(), t.sistema(), Verificacao.naoAtendida("MEP-MAT-01", "Material compatível com o sistema", "ESPECIFICACAO", "catálogo técnico " + CatalogoMateriais.padrao().versao(),
                    item.nome() + " é indicado para " + String.join(", ", item.aplicacoes()) + "; trecho é " + t.sistema(), "Troque o material por um indicado para " + t.sistema() + ".")));
        }
    }

    // ------------------------------------------------------------------ esgoto e ventilação
    private static void esgoto(Rede rede, Map<String, Componente> porId, List<Item> out) {
        var trechos = rede.trechos().stream().filter(t -> t.sistema() == Sistema.ESGOTO).toList();
        for (var t : trechos) {
            if (t.regime() == Regime.PRESSURIZADO) {
                out.add(new Item(t.id(), t.sistema(), Verificacao.naoVerificada("MEP-ESG-00", "Esgoto pressurizado (recalque)", "VERIFICACAO", null,
                        "Trecho declarado pressurizado: regras de declividade de escoamento por gravidade não se aplicam e o recalque não é verificado.")));
                continue;
            }
            if (!(t.comprimentoM() > 0)) {
                out.add(new Item(t.id(), t.sistema(), Verificacao.dadosInsuficientes("MEP-ESG-01", "Declividade mínima", "VERIFICACAO", NBR8160, "Comprimento do trecho não informado.")));
                continue;
            }
            double i = (t.cotaOrigem() - t.cotaDestino()) / t.comprimentoM();
            String ev = String.format(Locale.ROOT, "cotas %.3f → %.3f m em %.2f m: i = %.2f %%; DN %.0f", t.cotaOrigem(), t.cotaDestino(), t.comprimentoM(), i * 100, t.dnMm());
            if (i < 0) {
                out.add(new Item(t.id(), t.sistema(), Verificacao.naoAtendida("MEP-ESG-02", "Sentido do escoamento (contrapendência)", "VERIFICACAO", "regra física (escoamento por gravidade)", ev,
                        "O destino está mais alto que a origem: inverta o sentido ou corrija as cotas.")));
                continue;
            }
            double minima = t.dnMm() <= 75 ? 0.02 : t.dnMm() >= 100 ? 0.01 : Double.NaN;
            if (Double.isNaN(minima)) {
                out.add(new Item(t.id(), t.sistema(), Verificacao.dadosInsuficientes("MEP-ESG-01", "Declividade mínima", "VERIFICACAO", NBR8160,
                        "DN " + t.dnMm() + " entre 75 e 100: a regra registrada só cobre DN ≤ 75 (2 %) e DN ≥ 100 (1 %).")));
            } else if (i + 1e-9 < minima) {
                out.add(new Item(t.id(), t.sistema(), Verificacao.naoAtendida("MEP-ESG-01", "Declividade mínima", "VERIFICACAO", NBR8160 + " (declividade mínima 2 % para DN ≤ 75 e 1 % para DN ≥ 100, segundo fontes que citam a norma)",
                        ev, String.format(Locale.ROOT, "Aumente a declividade para ao menos %.0f %%.", minima * 100))));
            } else {
                out.add(new Item(t.id(), t.sistema(), Verificacao.atendida("MEP-ESG-01", "Declividade mínima", "VERIFICACAO", NBR8160, ev)));
            }
        }
        // diâmetro não diminui no sentido do escoamento
        for (var t : trechos) {
            for (var jus : trechos) {
                if (jus.origemId().equals(t.destinoId()) && jus.dnMm() + 1e-9 < t.dnMm()) {
                    out.add(new Item(jus.id(), Sistema.ESGOTO, Verificacao.naoAtendida("MEP-ESG-03", "Diâmetro não diminui a jusante", "VERIFICACAO", "boa prática de projeto (não conferida no texto da norma)",
                            String.format(Locale.ROOT, "DN %.0f a montante (%s) → DN %.0f a jusante", t.dnMm(), t.id(), jus.dnMm()), "Use a jusante diâmetro igual ou maior.")));
                }
            }
        }
        // continuidade até um coletor ou caixa de inspeção
        if (!trechos.isEmpty()) {
            var destinos = rede.componentes().stream().filter(c -> c.sistema() == Sistema.ESGOTO && Set.of("COLETOR", "CAIXA_INSPECAO").contains(c.tipo())).map(Componente::id).toList();
            for (var c : rede.componentes()) {
                if (c.sistema() == Sistema.ESGOTO && c.tipo().equals("APARELHO") && !alcanca(c.id(), destinos, trechos, true)) {
                    out.add(new Item(c.id(), Sistema.ESGOTO, Verificacao.naoAtendida("MEP-ESG-04", "Aparelho ligado até coletor ou caixa de inspeção", "CONECTIVIDADE", "regra lógica do modelo",
                            "nenhum caminho a jusante de " + c.id() + " chega a coletor ou caixa de inspeção", "Complete o ramal até a caixa ou o coletor.")));
                }
            }
            out.add(new Item("rede", Sistema.ESGOTO, Verificacao.naoVerificada("MEP-ESG-05", "Distância máxima entre dispositivos de inspeção", "VERIFICACAO", NBR8160,
                    "Limite de distância não registrado a partir do texto da norma.")));
        }
    }

    private static void ventilacao(Rede rede, List<Item> out) {
        boolean temEsgoto = rede.trechos().stream().anyMatch(t -> t.sistema() == Sistema.ESGOTO);
        boolean temVentilacao = rede.trechos().stream().anyMatch(t -> t.sistema() == Sistema.VENTILACAO);
        if (temEsgoto && !temVentilacao) {
            out.add(new Item("rede", Sistema.VENTILACAO, Verificacao.naoAtendida("MEP-VEN-01", "Sistema de esgoto ventilado", "CONECTIVIDADE", NBR8160 + " (exige ventilação; dimensionamento não implementado)",
                    "rede de esgoto sem nenhum trecho de ventilação", "Inclua coluna ou ramal de ventilação ligado ao esgoto.")));
        } else if (temVentilacao) {
            out.add(new Item("rede", Sistema.VENTILACAO, Verificacao.naoVerificada("MEP-VEN-02", "Dimensionamento da ventilação", "DIMENSIONAMENTO", NBR8160, "Diâmetros e distâncias da ventilação não verificados.")));
        }
    }

    // ------------------------------------------------------------------ água fria e quente
    private static void aguas(Rede rede, Map<String, Componente> porId, List<Item> out) {
        for (var s : List.of(Sistema.AGUA_FRIA, Sistema.AGUA_QUENTE)) {
            var trechos = rede.trechos().stream().filter(t -> t.sistema() == s).toList();
            if (trechos.isEmpty()) {
                continue;
            }
            var fontes = rede.componentes().stream().filter(c -> s == Sistema.AGUA_FRIA ? Set.of("ENTRADA", "RESERVATORIO").contains(c.tipo()) : c.tipo().equals("AQUECEDOR")).map(Componente::id).toList();
            if (fontes.isEmpty()) {
                out.add(new Item("rede", s, Verificacao.naoAtendida("MEP-AGU-01", s == Sistema.AGUA_FRIA ? "Rede com alimentação (entrada ou reservatório)" : "Água quente com aquecedor",
                        "CONECTIVIDADE", "regra lógica do modelo", "nenhuma fonte para " + s, s == Sistema.AGUA_FRIA ? "Ligue a rede à entrada ou ao reservatório." : "Inclua o aquecedor e alimente-o com água fria.")));
            }
            for (var c : rede.componentes()) {
                if (c.sistema() == s && c.tipo().equals("PONTO_CONSUMO") && !fontes.isEmpty() && fontes.stream().noneMatch(f -> alcanca(f, List.of(c.id()), trechos, true))) {
                    out.add(new Item(c.id(), s, Verificacao.naoAtendida("MEP-AGU-02", "Ponto de consumo alimentado", "CONECTIVIDADE", "regra lógica do modelo",
                            "nenhum caminho da fonte até " + c.id(), "Complete o ramal até o ponto.")));
                }
            }
            if (s == Sistema.AGUA_QUENTE) {
                for (var aq : rede.componentes().stream().filter(c -> c.tipo().equals("AQUECEDOR")).toList()) {
                    boolean alimentado = rede.trechos().stream().anyMatch(t -> t.sistema() == Sistema.AGUA_FRIA && t.destinoId().equals(aq.id()));
                    if (!alimentado) {
                        out.add(new Item(aq.id(), s, Verificacao.naoAtendida("MEP-AGU-03", "Aquecedor alimentado por água fria", "CONECTIVIDADE", "regra lógica do modelo",
                                "nenhum trecho de água fria chega ao aquecedor " + aq.id(), "Ligue a água fria à entrada do aquecedor.")));
                    }
                }
            }
            out.add(new Item("rede", s, Verificacao.naoVerificada("MEP-AGU-04", "Vazões, perdas de carga e pressões nos pontos", "DIMENSIONAMENTO", NBR5626,
                    "Método de cálculo do texto de 2020 não consultado; o software não estima pressão nem perda de carga.")));
        }
    }

    // ------------------------------------------------------------------ elétrica
    private static void eletrica(Rede rede, Map<String, Componente> porId, Parametros par, List<Item> out) {
        var trechos = rede.trechos().stream().filter(t -> t.sistema() == Sistema.ELETRICA).toList();
        if (trechos.isEmpty()) {
            return;
        }
        var quadros = rede.componentes().stream().filter(c -> c.tipo().equals("QUADRO")).map(Componente::id).toList();
        var porCircuito = new HashMap<String, List<Trecho>>();
        for (var t : trechos) {
            porCircuito.computeIfAbsent(t.circuito() == null ? "(sem circuito)" : t.circuito(), k -> new ArrayList<>()).add(t);
        }
        for (var e : porCircuito.entrySet()) {
            String cid = e.getKey();
            var ts = e.getValue();
            if (cid.equals("(sem circuito)")) {
                ts.forEach(t -> out.add(new Item(t.id(), Sistema.ELETRICA, Verificacao.dadosInsuficientes("MEP-ELE-01", "Trecho pertence a um circuito", "CONECTIVIDADE", null, "Circuito não informado."))));
                continue;
            }
            var pontos = new HashSet<String>();
            ts.forEach(t -> { pontos.add(t.origemId()); pontos.add(t.destinoId()); });
            var cargas = pontos.stream().map(porId::get).filter(c -> c != null && Set.of("TOMADA", "LUMINARIA", "APARELHO").contains(c.tipo())).toList();
            boolean origemQuadro = quadros.stream().anyMatch(q -> cargas.stream().allMatch(c -> alcanca(q, List.of(c.id()), ts, false)));
            if (!origemQuadro) {
                out.add(new Item(cid, Sistema.ELETRICA, Verificacao.naoAtendida("MEP-ELE-02", "Circuito com origem no quadro e continuidade até as cargas", "CONECTIVIDADE", "regra lógica do modelo",
                        "circuito " + cid + ": nem todas as cargas têm caminho até um quadro", "Ligue o circuito ao quadro e complete o percurso.")));
            }
            boolean temTomada = cargas.stream().anyMatch(c -> c.tipo().equals("TOMADA"));
            for (var t : ts) {
                var cond = t.condutores() == null ? Set.<String>of() : t.condutores();
                if (!cond.contains("F") || !cond.contains("N")) {
                    out.add(new Item(t.id(), Sistema.ELETRICA, Verificacao.naoAtendida("MEP-ELE-03", "Fase e neutro presentes", "ESPECIFICACAO", "regra física (circuito monofásico F-N)",
                            "condutores declarados: " + cond, "Declare fase (F) e neutro (N) no trecho.")));
                }
                if (temTomada && !cond.contains("PE")) {
                    out.add(new Item(t.id(), Sistema.ELETRICA, Verificacao.naoAtendida("MEP-ELE-04", "Condutor de proteção em circuito com tomada", "ESPECIFICACAO", NBR5410 + " (cláusula não conferida)",
                            "condutores declarados: " + cond, "Inclua o condutor de proteção (PE).")));
                }
            }
            // corrente de projeto, proteção e queda de tensão (monofásico)
            double potencia = cargas.stream().mapToDouble(c -> c.num("potenciaW")).filter(v -> !Double.isNaN(v)).sum();
            if (Double.isNaN(par.tensaoV()) || Double.isNaN(par.fatorPotencia()) || potencia <= 0) {
                out.add(new Item(cid, Sistema.ELETRICA, Verificacao.dadosInsuficientes("MEP-ELE-05", "Corrente de projeto e proteção", "DIMENSIONAMENTO", NBR5410, "Tensão, fator de potência ou potência das cargas não informados.")));
                continue;
            }
            double ib = potencia / (par.tensaoV() * par.fatorPotencia());
            var quadro = quadros.stream().map(porId::get).filter(q -> q.props().get("disjuntores") instanceof Map<?, ?> m && m.containsKey(cid)).findFirst().orElse(null);
            Double in = quadro == null ? null : ((Number) ((Map<?, ?>) quadro.props().get("disjuntores")).get(cid)).doubleValue();
            String evIb = String.format(Locale.ROOT, "Ib = P/(V·FP) = %.0f/(%.0f·%.2f) = %.2f A", potencia, par.tensaoV(), par.fatorPotencia(), ib);
            if (in == null) {
                out.add(new Item(cid, Sistema.ELETRICA, Verificacao.dadosInsuficientes("MEP-ELE-05", "Ib ≤ In (disjuntor)", "DIMENSIONAMENTO", NBR5410, evIb + "; disjuntor do circuito não informado.")));
            } else if (ib > in) {
                out.add(new Item(cid, Sistema.ELETRICA, Verificacao.naoAtendida("MEP-ELE-05", "Ib ≤ In (disjuntor)", "DIMENSIONAMENTO", NBR5410, evIb + String.format(Locale.ROOT, " > In = %.0f A", in), "Divida o circuito ou revise a proteção.")));
            } else {
                out.add(new Item(cid, Sistema.ELETRICA, Verificacao.atendida("MEP-ELE-05", "Ib ≤ In (disjuntor)", "DIMENSIONAMENTO", NBR5410, evIb + String.format(Locale.ROOT, " ≤ In = %.0f A", in))));
            }
            out.add(new Item(cid, Sistema.ELETRICA, Verificacao.dadosInsuficientes("MEP-ELE-06", "In ≤ Iz (capacidade de condução)", "DIMENSIONAMENTO", NBR5410,
                    "Capacidade de condução depende do método de instalação e do agrupamento (tabelas da norma não transcritas): informe Iz de fonte identificada.")));
            double l = ts.stream().mapToDouble(Trecho::comprimentoM).sum();
            double s = ts.stream().mapToDouble(Trecho::secaoMm2).min().orElse(Double.NaN);
            if (!(s > 0)) {
                out.add(new Item(cid, Sistema.ELETRICA, Verificacao.dadosInsuficientes("MEP-ELE-07", "Queda de tensão", "DIMENSIONAMENTO", NBR5410, "Seção do condutor não informada.")));
                continue;
            }
            double dv = 2 * RHO_COBRE_20C * l * ib / s;
            double pct = dv / par.tensaoV() * 100;
            String evDv = String.format(Locale.ROOT, "ΔV = 2·ρ·L·Ib/S = 2·%.4f·%.1f·%.2f/%.1f = %.2f V (%.2f %%), ρ do cobre a 20 °C, carga concentrada no fim (a favor da segurança)", RHO_COBRE_20C, l, ib, s, dv, pct);
            if (Double.isNaN(par.limiteQuedaTensaoPct())) {
                out.add(new Item(cid, Sistema.ELETRICA, Verificacao.dadosInsuficientes("MEP-ELE-07", "Queda de tensão", "DIMENSIONAMENTO", NBR5410, evDv + "; limite não informado (declare o limite e a fonte).")));
            } else if (pct > par.limiteQuedaTensaoPct()) {
                out.add(new Item(cid, Sistema.ELETRICA, Verificacao.naoAtendida("MEP-ELE-07", "Queda de tensão", "DIMENSIONAMENTO", par.fonteLimiteQueda(), evDv + String.format(Locale.ROOT, " > limite %.1f %%", par.limiteQuedaTensaoPct()), "Aumente a seção ou encurte o circuito.")));
            } else {
                out.add(new Item(cid, Sistema.ELETRICA, Verificacao.atendida("MEP-ELE-07", "Queda de tensão", "DIMENSIONAMENTO", par.fonteLimiteQueda(), evDv + String.format(Locale.ROOT, " ≤ limite %.1f %%", par.limiteQuedaTensaoPct()))));
            }
        }
    }

    /** existe caminho de {@code de} até algum de {@code alvos} seguindo os trechos (no sentido origem→destino se {@code orientado}) */
    static boolean alcanca(String de, List<String> alvos, List<Trecho> trechos, boolean orientado) {
        var vistos = new HashSet<String>();
        var fila = new ArrayDeque<String>(List.of(de));
        while (!fila.isEmpty()) {
            var n = fila.poll();
            if (alvos.contains(n)) {
                return true;
            }
            if (!vistos.add(n)) {
                continue;
            }
            for (var t : trechos) {
                if (t.origemId().equals(n)) {
                    fila.add(t.destinoId());
                } else if (!orientado && t.destinoId().equals(n)) {
                    fila.add(t.origemId());
                }
            }
        }
        return false;
    }

    /** assinatura da rede e dos parâmetros: resultado com assinatura diferente da atual está desatualizado */
    public static String assinatura(Rede rede, Parametros par) {
        try {
            var md = MessageDigest.getInstance("SHA-256");
            md.update((rede.toString() + "|" + par).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(md.digest()).substring(0, 16);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
