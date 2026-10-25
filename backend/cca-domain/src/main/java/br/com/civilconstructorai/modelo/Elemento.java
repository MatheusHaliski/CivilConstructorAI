package br.com.civilconstructorai.modelo;

import br.com.civilconstructorai.comum.Ids;
import br.com.civilconstructorai.modelo.Enums.Camada;
import br.com.civilconstructorai.modelo.Enums.OrigemComando;
import br.com.civilconstructorai.modelo.Enums.TipoElemento;
import br.com.civilconstructorai.notacao.Ponto2D;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * Elemento do modelo. A geometria e as propriedades específicas de cada tipo ficam em {@code props}
 * (persistido como jsonb): p1/p2 (parede, viga, linha), centro/b/h (pilar), contorno (laje), paredeId/posicao
 * (aberturas), pontos (polilinha), etc. Acesso tipado pelos auxiliares.
 */
public final class Elemento {

    private UUID id;
    private TipoElemento tipo;
    private UUID pavimentoId;
    private Camada camada;
    private String materialId;
    private OrigemComando origem;
    private UUID rascunhoIaId;
    private String rotulo;
    private final Map<String, Object> props;

    public Elemento(UUID id, TipoElemento tipo, UUID pavimentoId, Camada camada, String materialId, OrigemComando origem, UUID rascunhoIaId, String rotulo, Map<String, Object> props) {
        this.id = id;
        this.tipo = tipo;
        this.pavimentoId = pavimentoId;
        this.camada = camada;
        this.materialId = materialId;
        this.origem = origem;
        this.rascunhoIaId = rascunhoIaId;
        this.rotulo = rotulo;
        this.props = new LinkedHashMap<>(props == null ? Map.of() : props);
    }

    public static Elemento novo(TipoElemento tipo, UUID pavimentoId, Camada camada, OrigemComando origem, Map<String, Object> props) {
        return new Elemento(Ids.novo(), tipo, pavimentoId, origem == OrigemComando.IA ? Camada.ia_rascunho : camada, (String) props.get("materialId"), origem, null, null, props);
    }

    public Elemento copia() {
        return new Elemento(id, tipo, pavimentoId, camada, materialId, origem, rascunhoIaId, rotulo, copiaProps(props));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> copiaProps(Map<String, Object> m) {
        var out = new LinkedHashMap<String, Object>();
        m.forEach((k, v) -> out.put(k, v instanceof List<?> l ? new ArrayList<>(l) : v instanceof Map<?, ?> mm ? copiaProps((Map<String, Object>) mm) : v));
        return out;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public TipoElemento getTipo() { return tipo; }
    public UUID getPavimentoId() { return pavimentoId; }
    public void setPavimentoId(UUID pavimentoId) { this.pavimentoId = pavimentoId; }
    public Camada getCamada() { return camada; }
    public void setCamada(Camada camada) { this.camada = camada; }
    public String getMaterialId() { return materialId; }
    public void setMaterialId(String materialId) { this.materialId = materialId; props.put("materialId", materialId); }
    public OrigemComando getOrigem() { return origem; }
    public void setOrigem(OrigemComando origem) { this.origem = origem; }
    public UUID getRascunhoIaId() { return rascunhoIaId; }
    public void setRascunhoIaId(UUID rascunhoIaId) { this.rascunhoIaId = rascunhoIaId; }
    public String getRotulo() { return rotulo; }
    public void setRotulo(String rotulo) { this.rotulo = rotulo; }
    public Map<String, Object> getProps() { return props; }

    // ---- acesso tipado
    public Ponto2D ponto(String chave) {
        var v = props.get(chave);
        return v == null ? null : aPonto(v);
    }

    public static Ponto2D aPonto(Object v) {
        if (v instanceof Ponto2D p) {
            return p;
        }
        if (v instanceof Map<?, ?> m && m.get("x") != null) {
            return Ponto2D.de(((Number) m.get("x")).doubleValue(), ((Number) m.get("y")).doubleValue());
        }
        if (v instanceof List<?> l && l.size() == 2 && l.get(0) instanceof Number a && l.get(1) instanceof Number b) {
            return Ponto2D.de(a.doubleValue(), b.doubleValue());
        }
        throw new IllegalArgumentException("Não é ponto: " + v);
    }

    public void setPonto(String chave, Ponto2D p) {
        props.put(chave, p);
    }

    public double num(String chave, double padrao) {
        var v = props.get(chave);
        return v instanceof Number n ? n.doubleValue() : padrao;
    }

    public void setNum(String chave, double v) {
        props.put(chave, v);
    }

    public String texto(String chave) {
        var v = props.get(chave);
        return v == null ? null : String.valueOf(v);
    }

    public boolean flag(String chave, boolean padrao) {
        var v = props.get(chave);
        return v instanceof Boolean b ? b : padrao;
    }

    public UUID uuid(String chave) {
        var v = props.get(chave);
        return v == null ? null : v instanceof UUID u ? u : UUID.fromString(String.valueOf(v));
    }

    @SuppressWarnings("unchecked")
    public List<Ponto2D> pontos(String chave) {
        var v = props.get(chave);
        if (v == null) {
            return List.of();
        }
        var out = new ArrayList<Ponto2D>();
        for (var o : (List<Object>) v) {
            out.add(aPonto(o));
        }
        return out;
    }

    public void setPontos(String chave, List<Ponto2D> pts) {
        props.put(chave, new ArrayList<Object>(pts));
    }

    /** todos os pontos geométricos do elemento (para caixa, transformações, snap) */
    public List<Ponto2D> todosOsPontos() {
        var out = new ArrayList<Ponto2D>();
        for (var k : List.of("p1", "p2", "p3", "centro", "ponto", "p")) {
            var p = ponto(k);
            if (p != null) {
                out.add(p);
            }
        }
        out.addAll(pontos("contorno"));
        out.addAll(pontos("pontos"));
        return out;
    }

    /** aplica uma transformação a todos os pontos (porte de mapearPontos) */
    public void mapearPontos(UnaryOperator<Ponto2D> f) {
        for (var k : List.of("p1", "p2", "p3", "centro", "ponto", "p")) {
            var p = ponto(k);
            if (p != null) {
                setPonto(k, f.apply(p));
            }
        }
        if (props.containsKey("contorno")) {
            setPontos("contorno", pontos("contorno").stream().map(f).toList());
        }
        if (props.containsKey("pontos")) {
            setPontos("pontos", pontos("pontos").stream().map(f).toList());
        }
    }

    public boolean temSegmento() {
        return ponto("p1") != null && ponto("p2") != null;
    }
}
