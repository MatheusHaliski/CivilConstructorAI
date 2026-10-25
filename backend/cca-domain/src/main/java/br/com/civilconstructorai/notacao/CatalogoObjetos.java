package br.com.civilconstructorai.notacao;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Biblioteca de objetos (móveis, equipamentos, louças, iluminação, pontos de instalação e pessoas). Fonte única:
 * {@code packages/notacao-vi/catalogo/objetos.json}, o mesmo arquivo que o motor TS lê ({@code objetos.ts}).
 * Potências, vazões e diâmetros dos conectores são valores de referência adotados, não dados de fabricante.
 */
public final class CatalogoObjetos {

    public record Ponto3D(double x, double y, double z) {
    }

    /** {@code fluxo}: SAI fornece, ENTRA recebe; {@code tensoes}: vazio quando não se aplica */
    public record Conector(String id, String sistema, String fluxo, Ponto3D pos, String papel, List<Double> tensoes, Double potenciaW, Double dnMm,
                           Double correnteMaxA, int maxConexoes) {
    }

    public record Dim(double l, double p, double a) {
    }

    public record Item(String id, String nome, String categoria, String disciplina, String forma, Dim dim, String montagem, Double elevacao,
                       boolean redimensionavel, String ifc, double[] alturaVariavel, boolean temLuz, boolean sobreposicaoPermitida, List<Conector> conectores, String palavras) {
        public Optional<Conector> conector(String id) {
            return conectores.stream().filter(c -> c.id().equals(id)).findFirst();
        }
    }

    private static final CatalogoObjetos PADRAO = carregar();

    private final String versao;
    private final Map<String, Item> itens;

    private CatalogoObjetos(String versao, Map<String, Item> itens) {
        this.versao = versao;
        this.itens = itens;
    }

    public static CatalogoObjetos padrao() {
        return PADRAO;
    }

    public String versao() {
        return versao;
    }

    public List<Item> itens() {
        return List.copyOf(itens.values());
    }

    public Optional<Item> item(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(itens.get(id));
    }

    private static CatalogoObjetos carregar() {
        try (InputStream in = CatalogoObjetos.class.getClassLoader().getResourceAsStream("catalogo/objetos.json")) {
            if (in == null) {
                throw new IllegalStateException("catalogo/objetos.json ausente do classpath (packages/notacao-vi/catalogo).");
            }
            return ler(new ObjectMapper().readTree(in));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static CatalogoObjetos ler(JsonNode raiz) {
        var itens = new LinkedHashMap<String, Item>();
        for (var i : raiz.path("itens")) {
            var cons = new ArrayList<Conector>();
            for (var c : i.path("conectores")) {
                var p = c.path("pos");
                var tensoes = new ArrayList<Double>();
                if (!c.path("tensaoV").isMissingNode()) {
                    for (var t : c.path("tensaoV").asString().split("/")) {
                        tensoes.add(Double.parseDouble(t));
                    }
                }
                cons.add(new Conector(c.path("id").asString(), c.path("sistema").asString(), c.path("fluxo").asString(),
                        new Ponto3D(p.path("x").asDouble(), p.path("y").asDouble(), p.path("z").asDouble()), c.path("papel").asString(), List.copyOf(tensoes),
                        num(c, "potenciaW"), num(c, "dnMm"), num(c, "correnteMaxA"), c.path("maxConexoes").isMissingNode() ? 1 : c.path("maxConexoes").asInt()));
            }
            var d = i.path("dim");
            var av = i.path("alturaVariavel");
            itens.put(i.path("id").asString(), new Item(i.path("id").asString(), i.path("nome").asString(), i.path("categoria").asString(),
                    i.path("disciplina").asString(), i.path("forma").asString(), new Dim(d.path("l").asDouble(), d.path("p").asDouble(), d.path("a").asDouble()),
                    i.path("montagem").asString(), num(i, "elevacao"), i.path("redimensionavel").asBoolean(), i.path("ifc").asString(""),
                    av.isMissingNode() ? null : new double[]{av.path("min").asDouble(), av.path("max").asDouble()}, !i.path("luz").isMissingNode(),
                    i.path("sobreposicaoPermitida").asBoolean(false), List.copyOf(cons), i.path("palavras").asString("")));
        }
        return new CatalogoObjetos(raiz.path("versao").asString(), itens);
    }

    private static Double num(JsonNode n, String campo) {
        var v = n.path(campo);
        return v.isMissingNode() || v.isNull() ? null : v.asDouble();
    }
}
