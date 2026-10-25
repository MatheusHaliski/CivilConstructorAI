package br.com.civilconstructorai.estrutura;

import br.com.civilconstructorai.estrutura.modelo.ElementoEstrutural;
import br.com.civilconstructorai.estrutura.modelo.ModeloEstrutural;
import br.com.civilconstructorai.estrutura.modelo.PavimentoRef;
import br.com.civilconstructorai.estrutura.modelo.Ponto;
import br.com.civilconstructorai.estrutura.modelo.TipoElementoEstrutural;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.UUID;

/**
 * Lê o recorte estrutural das tabelas do módulo {@code modelo} (V7): {@code modelo_bim(id, obra_id, versao)},
 * {@code pavimento(id, modelo_id, nome, elevacao)} e {@code elemento(id, modelo_id, pavimento_id, tipo, camada,
 * material_id, rotulo, geometria, rascunho_ia_id)} com {@code geometria} em JSONB (chaves {@code centro, b, h, p1,
 * p2, contorno, espessura, altura, largura, paredeId, nivelBaseId, nivelTopoId, bx, by}). Elementos da camada
 * {@code ia-rascunho} entram marcados como rascunho (não contam em análise nem quantitativos). Sem modelo para a
 * obra, devolve um modelo vazio em vez de falhar.
 */
@Component
@Profile("!it")
public class ModeloEstruturalJdbc implements ModeloEstruturalFonte {

    private static final Logger log = LoggerFactory.getLogger(ModeloEstruturalJdbc.class);
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public ModeloEstruturalJdbc(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    public ModeloEstrutural carregar(UUID obraId) {
        try {
            var cabecalho = jdbc.query("select id, versao from modelo_bim where obra_id = ?",
                    rs -> rs.next() ? new Object[]{rs.getObject("id", UUID.class), rs.getLong("versao")} : null, obraId);
            if (cabecalho == null) {
                return ModeloEstrutural.vazio(obraId);
            }
            var modeloId = (UUID) cabecalho[0];
            long versao = (Long) cabecalho[1];
            var pavimentos = jdbc.query("select id, nome, elevacao from pavimento where modelo_id = ? order by elevacao",
                    (rs, i) -> new PavimentoRef(rs.getString("id"), rs.getString("nome"), rs.getDouble("elevacao")), modeloId);
            var elementos = new ArrayList<ElementoEstrutural>();
            jdbc.query("select id, pavimento_id, tipo, camada, material_id, rotulo, geometria::text as geometria, rascunho_ia_id from elemento where modelo_id = ?", rs -> {
                boolean rascunho = rs.getString("rascunho_ia_id") != null || "ia-rascunho".equals(rs.getString("camada"));
                var e = converter(rs.getString("id"), rs.getString("pavimento_id"), rs.getString("tipo"), rs.getString("material_id"), rs.getString("rotulo"),
                        rs.getString("geometria"), rascunho);
                if (e != null) {
                    elementos.add(e);
                }
            }, modeloId);
            if (pavimentos.isEmpty()) {
                return ModeloEstrutural.vazio(obraId);
            }
            return new ModeloEstrutural(obraId, versao, pavimentos, elementos);
        } catch (DataAccessException e) {
            log.warn("tabelas do modelo BIM indisponíveis para a obra {}: {}", obraId, e.getMostSpecificCause().getMessage());
            return ModeloEstrutural.vazio(obraId);
        }
    }

    ElementoEstrutural converter(String id, String pav, String tipo, String material, String rotulo, String geometria, boolean rascunho) {
        JsonNode g = mapper.readTree(geometria == null ? "{}" : geometria);
        ElementoEstrutural e = switch (tipo) {
            case "PILAR" -> ElementoEstrutural.pilar(id, pav, ponto(g.path("centro")), g.path("b").asDouble(), g.path("h").asDouble(),
                    texto(g, "nivelBaseId"), texto(g, "nivelTopoId"), material);
            case "VIGA" -> ElementoEstrutural.viga(id, pav, ponto(g.path("p1")), ponto(g.path("p2")), g.path("b").asDouble(), g.path("h").asDouble(), material);
            case "LAJE" -> {
                var pts = new ArrayList<Ponto>();
                for (JsonNode p : g.path("contorno")) {
                    pts.add(ponto(p));
                }
                yield ElementoEstrutural.laje(id, pav, pts, g.path("h").asDouble(), material);
            }
            case "PAREDE" -> ElementoEstrutural.parede(id, pav, ponto(g.path("p1")), ponto(g.path("p2")), g.path("espessura").asDouble(), g.path("altura").asDouble(), material);
            case "PORTA", "JANELA" -> ElementoEstrutural.abertura(id, TipoElementoEstrutural.valueOf(tipo), pav, texto(g, "paredeId"), g.path("largura").asDouble(), g.path("altura").asDouble());
            case "SAPATA", "BLOCO", "RADIER" -> ElementoEstrutural.fundacao(id, TipoElementoEstrutural.valueOf(tipo), pav, ponto(g.path("centro")), g.path("bx").asDouble(), g.path("by").asDouble(), g.path("h").asDouble(), material);
            default -> null;
        };
        if (e == null) {
            return null;
        }
        if (rotulo != null) {
            e = e.comRotulo(rotulo);
        }
        return rascunho ? e.comoRascunhoIa() : e;
    }

    private static Ponto ponto(JsonNode n) {
        return new Ponto(n.path("x").asDouble(), n.path("y").asDouble());
    }

    private static String texto(JsonNode g, String campo) {
        return g.path(campo).isMissingNode() || g.path(campo).isNull() ? null : g.path(campo).asText();
    }

}
