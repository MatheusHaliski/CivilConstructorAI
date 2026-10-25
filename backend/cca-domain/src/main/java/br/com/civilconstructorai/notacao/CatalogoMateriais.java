package br.com.civilconstructorai.notacao;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Catálogo técnico de materiais (packages/notacao-vi/catalogo/materiais.json — o mesmo arquivo que o motor TS lê).
 * Separa material, família, componente, composição e serviço; cada propriedade tem origem (especificado, declarado
 * pelo fabricante, ensaio, estimado, adotado). As propriedades derivadas (Ecs, peso específico, Es…) são calculadas
 * aqui com o método e a referência declarados, para que o cálculo e a tela mostrem o mesmo valor.
 */
public final class CatalogoMateriais {

    public enum Origem { ESPECIFICADO, DECLARADO_FABRICANTE, ENSAIO, ESTIMADO, ADOTADO }

    public record Fonte(String url, String natureza, String consultadoEm) {
    }

    public record Referencia(String id, String organismo, String identificador, String titulo, String edicao, List<String> emendas, String tipo,
                             String situacao, String escopo, Fonte fonte, boolean cancelada, String substituidaPor) {
        public String rotulo() {
            return identificador + ":" + edicao;
        }
    }

    /** {@code valor}: número, texto ou nulo ("não informado" — nunca zero); {@code metodo}: como um valor estimado foi obtido */
    public record Propriedade(String chave, String rotulo, Object valor, String unidade, Origem origem, String referencia, String nota, String metodo) {
        public OptionalDouble numero() {
            return valor instanceof Number n ? OptionalDouble.of(n.doubleValue()) : OptionalDouble.empty();
        }
    }

    public record Familia(String id, String nome, String descricao, String suporte, List<String> referencias) {
    }

    public record Item(String codigo, String nome, String familia, String resumo, List<String> legado, List<String> aplicacoes,
                       String cor, List<Propriedade> propriedades, boolean personalizado, String situacaoValidacao) {
        public Optional<Propriedade> propriedade(String chave) {
            return propriedades.stream().filter(p -> p.chave().equals(chave)).findFirst();
        }

        public OptionalDouble numero(String chave) {
            return propriedade(chave).map(Propriedade::numero).orElse(OptionalDouble.empty());
        }
    }

    public record Bitola(double mm, String situacao) {
        public double areaCm2() {
            return Math.PI * mm * mm / 4 / 100;
        }

        /** massa nominal (kg/m) = área × 7850 kg/m³ */
        public double kgPorMetro() {
            return Math.PI * mm * mm / 4 * 1e-6 * 7850;
        }
    }

    public record Servico(String codigo, String nome, String material, String unidade, String sinapi, String situacaoSinapi) {
    }

    public record Camada(String material, double espessura, String assentamento) {
    }

    public record Composicao(String codigo, String nome, String elemento, List<Camada> camadas, String nota) {
        public double espessuraTotal() {
            return camadas.stream().mapToDouble(Camada::espessura).sum();
        }
    }

    /** αE do agregado graúdo (NBR 6118 8.2.8) */
    public static final Map<String, Double> ALFA_E = Map.of("basalto", 1.2, "diabasio", 1.2, "granito", 1.0, "gnaisse", 1.0, "calcario", 0.9, "arenito", 0.7);
    public static final String AGREGADO_PADRAO = "granito";

    private static final CatalogoMateriais PADRAO = carregar();

    private final String versao;
    private final Map<String, Referencia> referencias;
    private final Map<String, Familia> familias;
    private final Map<String, Item> itens;
    private final Map<String, String> aliases;
    private final Map<String, List<Bitola>> bitolas;
    private final List<Servico> servicos;
    private final List<Composicao> composicoes;

    private CatalogoMateriais(String versao, Map<String, Referencia> referencias, Map<String, Familia> familias, Map<String, Item> itens,
                              Map<String, List<Bitola>> bitolas, List<Servico> servicos, List<Composicao> composicoes) {
        this.versao = versao;
        this.referencias = referencias;
        this.familias = familias;
        this.itens = itens;
        this.bitolas = bitolas;
        this.servicos = servicos;
        this.composicoes = composicoes;
        this.aliases = new LinkedHashMap<>();
        for (var i : itens.values()) {
            aliases.put(i.codigo().toUpperCase(Locale.ROOT), i.codigo());
            for (var l : i.legado()) {
                aliases.put(l.toUpperCase(Locale.ROOT), i.codigo());
            }
        }
    }

    public static CatalogoMateriais padrao() {
        return PADRAO;
    }

    public String versao() { return versao; }
    public Map<String, Referencia> referencias() { return referencias; }
    public Map<String, Familia> familias() { return familias; }
    public List<Item> itens() { return List.copyOf(itens.values()); }
    public List<Servico> servicos() { return servicos; }
    public List<Composicao> composicoes() { return composicoes; }

    /** código canônico para um código ou código legado; vazio quando não existe no catálogo */
    public Optional<String> canonico(String codigoOuLegado) {
        return codigoOuLegado == null ? Optional.empty() : Optional.ofNullable(aliases.get(codigoOuLegado.trim().toUpperCase(Locale.ROOT)));
    }

    public Optional<Item> item(String codigoOuLegado) {
        return canonico(codigoOuLegado).map(itens::get);
    }

    public List<Bitola> bitolas(String codigoAco) {
        return canonico(codigoAco).map(c -> bitolas.getOrDefault(c, List.of())).orElse(List.of());
    }

    public Optional<Servico> servicoDo(String codigoMaterial, String unidade) {
        var c = canonico(codigoMaterial);
        return c.flatMap(cod -> servicos.stream().filter(s -> s.material().equals(cod) && s.unidade().equals(unidade)).findFirst());
    }

    /**
     * Propriedades do item com as derivadas por família, já com origem, referência e método:
     * concreto → Eci, Ecs (estimados, NBR 6118 8.2.8, αE do agregado), peso específico 25 kN/m³ e ν = 0,2 (adotados);
     * aço para armadura → Es = 210 GPa e massa específica 7850 kg/m³ (adotados, NBR 6118 8.3);
     * aço estrutural → E = 200 GPa e peso específico 78,5 kN/m³ (adotados).
     */
    public List<Propriedade> propriedadesCompletas(Item item, String agregado) {
        var out = new ArrayList<>(item.propriedades());
        switch (item.familia()) {
            case "CONCRETO" -> item.numero("fck").ifPresent(fck -> {
                String ag = agregado == null || !ALFA_E.containsKey(agregado) ? AGREGADO_PADRAO : agregado;
                double alfaE = ALFA_E.get(ag);
                double eci = alfaE * 5600 * Math.sqrt(fck);
                double alfaI = Math.min(1.0, 0.8 + 0.2 * fck / 80);
                out.add(new Propriedade("Eci", "Módulo de elasticidade tangente inicial (Eci)", arr(eci), "MPa", Origem.ESTIMADO, "NBR6118",
                        "agregado " + ag + (agregado == null ? " (premissa: não informado no projeto)" : ""), String.format(Locale.ROOT, "Eci = αE·5600·√fck, αE = %.1f", alfaE)));
                out.add(new Propriedade("Ecs", "Módulo de deformação secante (Ecs)", arr(alfaI * eci), "MPa", Origem.ESTIMADO, "NBR6118",
                        "usado na análise estrutural", String.format(Locale.ROOT, "Ecs = αi·Eci, αi = 0,8 + 0,2·fck/80 = %.3f", alfaI)));
                out.add(new Propriedade("pesoEspecifico", "Peso específico (concreto armado)", 25.0, "kN/m³", Origem.ADOTADO, "NBR6118", "8.2.2", null));
                out.add(new Propriedade("poisson", "Coeficiente de Poisson", 0.2, null, Origem.ADOTADO, "NBR6118", "8.2.9", null));
            });
            case "ACO_ARMADURA" -> {
                out.add(new Propriedade("Es", "Módulo de elasticidade (Es)", 210_000.0, "MPa", Origem.ADOTADO, "NBR6118", "8.3.5", null));
                out.add(new Propriedade("massaEspecifica", "Massa específica", 7850.0, "kg/m³", Origem.ADOTADO, "NBR6118", "8.3.3", null));
            }
            case "ACO_ESTRUTURAL" -> {
                out.add(new Propriedade("E", "Módulo de elasticidade (E)", 200_000.0, "MPa", Origem.ADOTADO, "NBR8800", "cláusula não conferida no texto oficial", null));
                out.add(new Propriedade("pesoEspecifico", "Peso específico", 78.5, "kN/m³", Origem.ADOTADO, null, "7850 kg/m³", null));
            }
            default -> { }
        }
        return out;
    }

    /** valor numérico adotado no cálculo (propriedade direta ou derivada); vazio quando o dado não existe */
    public OptionalDouble valorCalculo(String codigoOuLegado, String chave, String agregado) {
        var it = item(codigoOuLegado);
        if (it.isEmpty()) {
            return OptionalDouble.empty();
        }
        return propriedadesCompletas(it.get(), agregado).stream().filter(p -> p.chave().equals(chave)).findFirst().map(Propriedade::numero).orElse(OptionalDouble.empty());
    }

    // ------------------------------------------------------------------ leitura
    private static CatalogoMateriais carregar() {
        try (InputStream in = CatalogoMateriais.class.getClassLoader().getResourceAsStream("catalogo/materiais.json")) {
            if (in == null) {
                throw new IllegalStateException("catalogo/materiais.json ausente do classpath (packages/notacao-vi/catalogo).");
            }
            return ler(new ObjectMapper().readTree(in));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static CatalogoMateriais ler(JsonNode raiz) {
        var refs = new LinkedHashMap<String, Referencia>();
        for (var r : raiz.path("referencias")) {
            var emendas = new ArrayList<String>();
            r.path("emendas").forEach(x -> emendas.add(x.asString()));
            var f = r.path("fonte");
            refs.put(r.path("id").asString(), new Referencia(r.path("id").asString(), r.path("organismo").asString(), r.path("identificador").asString(),
                    r.path("titulo").asString(), r.path("edicao").asString(), List.copyOf(emendas), r.path("tipo").asString(), r.path("situacao").asString(),
                    r.path("escopo").asString(), new Fonte(texto(f, "url"), texto(f, "natureza"), texto(f, "consultadoEm")),
                    r.path("cancelada").asBoolean(false), texto(r, "substituidaPor")));
        }
        var fams = new LinkedHashMap<String, Familia>();
        for (var f : raiz.path("familias")) {
            var rs = new ArrayList<String>();
            f.path("referencias").forEach(x -> rs.add(x.asString()));
            fams.put(f.path("id").asString(), new Familia(f.path("id").asString(), f.path("nome").asString(), f.path("descricao").asString(), f.path("suporte").asString(), List.copyOf(rs)));
        }
        var itens = new LinkedHashMap<String, Item>();
        for (var m : raiz.path("materiais")) {
            itens.put(m.path("codigo").asString(), item(m, false, null));
        }
        var bitolas = new LinkedHashMap<String, List<Bitola>>();
        for (var e : raiz.path("bitolas").properties()) {
            var l = new ArrayList<Bitola>();
            e.getValue().forEach(b -> l.add(new Bitola(b.path("mm").asDouble(), b.path("situacao").asString())));
            bitolas.put(e.getKey(), List.copyOf(l));
        }
        var servicos = new ArrayList<Servico>();
        for (var s : raiz.path("servicos")) {
            servicos.add(new Servico(s.path("codigo").asString(), s.path("nome").asString(), s.path("material").asString(), s.path("unidade").asString(),
                    texto(s, "sinapi"), texto(s, "situacaoSinapi")));
        }
        var comps = new ArrayList<Composicao>();
        for (var c : raiz.path("composicoes")) {
            var camadas = new ArrayList<Camada>();
            c.path("camadas").forEach(k -> camadas.add(new Camada(k.path("material").asString(), k.path("espessura").asDouble(), texto(k, "assentamento"))));
            comps.add(new Composicao(c.path("codigo").asString(), c.path("nome").asString(), c.path("elemento").asString(), List.copyOf(camadas), texto(c, "nota")));
        }
        return new CatalogoMateriais(raiz.path("versao").asString(), refs, fams, itens, bitolas, List.copyOf(servicos), List.copyOf(comps));
    }

    /** lê um item no formato do catálogo (também usado para materiais personalizados gravados por empresa) */
    public static Item item(JsonNode m, boolean personalizado, String situacaoValidacao) {
        var legado = new ArrayList<String>();
        m.path("legado").forEach(x -> legado.add(x.asString()));
        var apl = new ArrayList<String>();
        m.path("aplicacoes").forEach(x -> apl.add(x.asString()));
        var props = new ArrayList<Propriedade>();
        for (var p : m.path("propriedades")) {
            var v = p.path("valor");
            Object valor = v.isNumber() ? (Object) v.asDouble() : v.isNull() || v.isMissingNode() ? null : v.asString();
            props.add(new Propriedade(p.path("chave").asString(), p.path("rotulo").asString(), valor, texto(p, "unidade"),
                    Origem.valueOf(p.path("origem").asString()), texto(p, "referencia"), texto(p, "nota"), texto(p, "metodo")));
        }
        return new Item(m.path("codigo").asString(), m.path("nome").asString(), m.path("familia").asString(), texto(m, "resumo"), List.copyOf(legado),
                List.copyOf(apl), texto(m.path("estilo"), "cor"), List.copyOf(props), personalizado, situacaoValidacao);
    }

    private static String texto(JsonNode n, String campo) {
        var v = n.path(campo);
        return v.isMissingNode() || v.isNull() ? null : v.asString();
    }

    private static double arr(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
