package br.com.civilconstructorai.modelo;

import br.com.civilconstructorai.modelo.Enums.Camada;
import br.com.civilconstructorai.modelo.Enums.OrigemComando;
import br.com.civilconstructorai.modelo.Enums.TipoElemento;
import br.com.civilconstructorai.modelo.Enums.TipoPavimento;
import br.com.civilconstructorai.notacao.Ponto2D;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Serialização explícita do estado do modelo (sem anotações no domínio): jsonb no PostgreSQL e corpo da API. */
@Component
public class EstadoJson {

    private static final TypeReference<LinkedHashMap<String, Object>> MAPA = new TypeReference<>() { };
    private final ObjectMapper mapper;

    public EstadoJson(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public ObjectMapper mapper() {
        return mapper;
    }

    public Map<String, Object> elemento(Elemento e) {
        var m = new LinkedHashMap<String, Object>();
        m.put("id", e.getId().toString());
        m.put("tipo", e.getTipo().name());
        m.put("pavimentoId", e.getPavimentoId().toString());
        m.put("camada", e.getCamada().rotulo());
        m.put("materialId", e.getMaterialId());
        m.put("origem", e.getOrigem().name());
        m.put("rascunhoIaId", e.getRascunhoIaId() == null ? null : e.getRascunhoIaId().toString());
        m.put("rotulo", e.getRotulo());
        m.put("props", props(e.getProps()));
        return m;
    }

    @SuppressWarnings("unchecked")
    private static Object valor(Object v) {
        if (v instanceof Ponto2D p) {
            return Map.of("x", p.x(), "y", p.y());
        }
        if (v instanceof UUID u) {
            return u.toString();
        }
        if (v instanceof List<?> l) {
            var out = new ArrayList<Object>();
            l.forEach(x -> out.add(valor(x)));
            return out;
        }
        if (v instanceof Map<?, ?> mm) {
            return props((Map<String, Object>) mm);
        }
        return v;
    }

    private static Map<String, Object> props(Map<String, Object> p) {
        var out = new LinkedHashMap<String, Object>();
        p.forEach((k, v) -> out.put(k, valor(v)));
        return out;
    }

    public Elemento elementoDe(Map<String, Object> m) {
        @SuppressWarnings("unchecked")
        var props = (Map<String, Object>) m.getOrDefault("props", Map.of());
        var camadaTxt = String.valueOf(m.get("camada"));
        var camada = camadaTxt.equals("ia-rascunho") ? Camada.ia_rascunho : Camada.valueOf(camadaTxt);
        return new Elemento(UUID.fromString(String.valueOf(m.get("id"))), TipoElemento.valueOf(String.valueOf(m.get("tipo"))), UUID.fromString(String.valueOf(m.get("pavimentoId"))),
                camada, m.get("materialId") == null ? null : String.valueOf(m.get("materialId")), OrigemComando.valueOf(String.valueOf(m.get("origem"))),
                m.get("rascunhoIaId") == null ? null : UUID.fromString(String.valueOf(m.get("rascunhoIaId"))), m.get("rotulo") == null ? null : String.valueOf(m.get("rotulo")), new LinkedHashMap<>(props));
    }

    public Map<String, Object> pavimento(Pavimento p) {
        return Map.of("id", p.id().toString(), "nome", p.nome(), "elevacao", p.elevacao(), "tipo", p.tipo().name());
    }

    public Pavimento pavimentoDe(Map<String, Object> m) {
        return new Pavimento(UUID.fromString(String.valueOf(m.get("id"))), String.valueOf(m.get("nome")), ((Number) m.get("elevacao")).doubleValue(), TipoPavimento.valueOf(String.valueOf(m.get("tipo"))));
    }

    public Map<String, Object> eixo(Eixo e) {
        return Map.of("id", e.id().toString(), "nome", e.nome(), "direcao", e.direcao().name(), "posicao", e.posicao(), "inicio", e.inicio(), "fim", e.fim(), "recuoBolha", e.recuoBolha());
    }

    public Eixo eixoDe(Map<String, Object> m) {
        return new Eixo(UUID.fromString(String.valueOf(m.get("id"))), String.valueOf(m.get("nome")), Eixo.Direcao.valueOf(String.valueOf(m.get("direcao"))),
                ((Number) m.get("posicao")).doubleValue(), ((Number) m.get("inicio")).doubleValue(), ((Number) m.get("fim")).doubleValue(), ((Number) m.get("recuoBolha")).doubleValue());
    }

    public String estado(ModeloBim.Estado e) {
        var m = new LinkedHashMap<String, Object>();
        m.put("versao", e.versao());
        m.put("pavimentos", e.pavimentos().stream().map(this::pavimento).toList());
        m.put("eixos", e.eixos().stream().map(this::eixo).toList());
        m.put("elementos", e.elementos().stream().map(this::elemento).toList());
        return mapper.writeValueAsString(m);
    }

    @SuppressWarnings("unchecked")
    public ModeloBim.Estado estadoDe(String json) {
        var m = mapper.readValue(json, MAPA);
        var pavs = ((List<Map<String, Object>>) m.getOrDefault("pavimentos", List.of())).stream().map(this::pavimentoDe).toList();
        var eixos = ((List<Map<String, Object>>) m.getOrDefault("eixos", List.of())).stream().map(this::eixoDe).toList();
        var elementos = ((List<Map<String, Object>>) m.getOrDefault("elementos", List.of())).stream().map(this::elementoDe).toList();
        return new ModeloBim.Estado(((Number) m.getOrDefault("versao", 0)).longValue(), pavs, eixos, elementos);
    }

    public String json(Object o) {
        return mapper.writeValueAsString(valor(o));
    }

    public Map<String, Object> mapa(String json) {
        return mapper.readValue(json, MAPA);
    }

    public Map<String, Object> relatorio(RelatorioAuditoria r) {
        return mapper.convertValue(r, MAPA);
    }

    public RelatorioAuditoria relatorioDe(String json) {
        return mapper.readValue(json, RelatorioAuditoria.class);
    }
}
