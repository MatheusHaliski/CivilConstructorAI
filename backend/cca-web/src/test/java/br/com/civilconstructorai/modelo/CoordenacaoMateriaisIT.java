package br.com.civilconstructorai.modelo;

import br.com.civilconstructorai.acesso.PapelObra;
import br.com.civilconstructorai.comum.UsuarioAtual;
import br.com.civilconstructorai.support.AbstractIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Auditoria 2026-10: coordenação (conflito resolvido só por reverificação, reaberto quando volta), base de cálculo
 * validada e materiais personalizados (unidade, documentação do produto, exclusão de material em uso).
 */
@AutoConfigureMockMvc
class CoordenacaoMateriaisIT extends AbstractIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager tm;
    @Autowired ObjectMapper mapper;

    ObraDeTeste.Cenario cenario;
    UsuarioAtual engenheiro;

    @BeforeEach
    void cenario() {
        cenario = ObraDeTeste.criar(jdbc, tm);
        engenheiro = ObraDeTeste.membro(jdbc, tm, cenario, PapelObra.ENGENHEIRO, true, true);
    }

    private String obra() {
        return "/api/v1/obras/" + cenario.obraId();
    }

    private MvcResult enviar(String metodo, String caminho, Object corpo) throws Exception {
        var req = switch (metodo) {
            case "PUT" -> put(caminho);
            case "DELETE" -> delete(caminho);
            default -> post(caminho);
        };
        req = req.with(ComoUsuario.como(engenheiro)).with(csrf());
        if (corpo != null) {
            req = req.contentType(MediaType.APPLICATION_JSON).content(corpo instanceof String s ? s : mapper.writeValueAsString(corpo));
        }
        return mvc.perform(req).andReturn();
    }

    private JsonNode json(MvcResult r) {
        return mapper.readTree(r.getResponse().getContentAsByteArray());
    }

    private String comando(String corpo) throws Exception {
        var r = enviar("POST", obra() + "/modelo/comandos", corpo);
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(201);
        return json(r).get("comandoId").asString();
    }

    private JsonNode verificar() throws Exception {
        var r = enviar("POST", obra() + "/coordenacao/verificar", null);
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(200);
        return json(r);
    }

    @Test
    void conflitoSoResolvePorReverificacaoEReabreQuandoVolta() throws Exception {
        comando("{\"codigo\":\"PILAR\",\"args\":{\"centro\":{\"x\":0,\"y\":0}}}");
        comando("{\"codigo\":\"VIGA\",\"args\":{\"p1\":{\"x\":0,\"y\":0},\"p2\":{\"x\":5,\"y\":0}}}");
        var v1 = verificar();
        assertThat(v1.get("novos").asInt()).isEqualTo(1);
        assertThat(v1.get("naoVerificado").size()).isGreaterThan(0);
        var c = v1.get("conflitos").get(0);
        assertThat(c.get("regra").asString()).isEqualTo("LIG-01");
        assertThat(c.get("estado").asString()).isEqualTo("ABERTO");
        String id = c.get("id").asString();

        var manual = enviar("POST", obra() + "/coordenacao/conflitos/" + id + "/estado", Map.of("estado", "RESOLVIDO"));
        assertThat(manual.getResponse().getStatus()).isEqualTo(422);
        var semJustificativa = enviar("POST", obra() + "/coordenacao/conflitos/" + id + "/estado", Map.of("estado", "ACEITO"));
        assertThat(semJustificativa.getResponse().getStatus()).isEqualTo(422);
        assertThat(enviar("POST", obra() + "/coordenacao/conflitos/" + id + "/estado", Map.of("estado", "EM_REVISAO")).getResponse().getStatus()).isEqualTo(200);

        String pilar2 = comando("{\"codigo\":\"PILAR\",\"args\":{\"centro\":{\"x\":5,\"y\":0}}}");
        var v2 = verificar();
        assertThat(v2.get("resolvidos").asInt()).isEqualTo(1);
        var resolvido = v2.get("conflitos").get(0);
        assertThat(resolvido.get("estado").asString()).isEqualTo("RESOLVIDO");
        assertThat(resolvido.get("versaoVerificada").asLong()).isEqualTo(v2.get("versaoModelo").asLong());

        assertThat(enviar("POST", obra() + "/modelo/comandos/" + pilar2 + "/desfazer", "").getResponse().getStatus()).isBetween(200, 201);
        var v3 = verificar();
        assertThat(v3.get("reabertos").asInt()).isEqualTo(1);
        assertThat(v3.get("conflitos").get(0).get("estado").asString()).isEqualTo("ABERTO");
    }

    @Test
    void baseDeCalculoRecusaCaaInexistenteEGravaAValida() throws Exception {
        var invalida = enviar("PUT", obra() + "/base-calculo", Map.of("classeAgressividade", "V"));
        assertThat(invalida.getResponse().getStatus()).isEqualTo(422);
        var ok = enviar("PUT", obra() + "/base-calculo", Map.of("classeAgressividade", "III", "agregado", "basalto", "finalidade", "RESIDENCIAL"));
        assertThat(ok.getResponse().getStatus()).as(ok.getResponse().getContentAsString()).isEqualTo(200);
        var b = json(ok);
        assertThat(b.get("classeAgressividade").asString()).isEqualTo("III");
        assertThat(b.get("naoInformado").toString()).doesNotContain("agressividade").contains("sistema construtivo");
        assertThat(b.get("referencias").toString()).contains("NBR 6118").contains("2023");
        // catálogo da obra com agregado basalto: Eci do C30 = 1,2 · 5600 · √30
        var cat = json(mvc.perform(get("/api/v1/materiais/catalogo").param("obraId", cenario.obraId().toString()).with(ComoUsuario.como(engenheiro))).andReturn());
        var c30 = cat.get("itens").valueStream().filter(i -> i.get("codigo").asString().equals("MAT.CONCRETO.C30")).findFirst().orElseThrow();
        var eci = c30.get("propriedades").valueStream().filter(p -> p.get("chave").asString().equals("Eci")).findFirst().orElseThrow();
        assertThat(eci.get("valor").asDouble()).isCloseTo(1.2 * 5600 * Math.sqrt(30), org.assertj.core.data.Offset.offset(0.1));
        assertThat(eci.get("origem").asString()).isEqualTo("ESTIMADO");
    }

    @Test
    void materialPersonalizadoValidaUnidadeDocumentacaoEUso() throws Exception {
        var semDoc = enviar("POST", "/api/v1/materiais/personalizados", Map.of("familia", "CONCRETO", "nome", "Concreto C32 fornecedor X",
                "propriedades", List.of(Map.of("chave", "fck", "valor", 32, "unidade", "MPa", "origem", "DECLARADO_FABRICANTE"))));
        assertThat(semDoc.getResponse().getStatus()).isEqualTo(422);
        assertThat(semDoc.getResponse().getContentAsString()).contains("documentação");
        var unidadeErrada = enviar("POST", "/api/v1/materiais/personalizados", Map.of("familia", "CONCRETO", "nome", "Concreto C32 fornecedor X",
                "propriedades", List.of(Map.of("chave", "fck", "valor", 32, "unidade", "kPa", "origem", "ESPECIFICADO"))));
        assertThat(unidadeErrada.getResponse().getStatus()).isEqualTo(422);
        assertThat(unidadeErrada.getResponse().getContentAsString()).contains("Unidade incorreta");
        var criado = enviar("POST", "/api/v1/materiais/personalizados", Map.of("familia", "CONCRETO", "nome", "Concreto C32 fornecedor X", "baseadoEm", "MAT.CONCRETO.C30",
                "propriedades", List.of(Map.of("chave", "fck", "valor", 32, "unidade", "MPa", "origem", "ESPECIFICADO"))));
        assertThat(criado.getResponse().getStatus()).as(criado.getResponse().getContentAsString()).isEqualTo(201);
        var mat = json(criado);
        assertThat(mat.get("situacao").asString()).isEqualTo("VALIDACAO_PENDENTE");
        String codigo = mat.get("codigo").asString();

        // código com ponto chega intacto ao auditor (antes: MAT_PERSONALIZADO_… recusado) e o personalizado da empresa é aceito
        comando("{\"codigo\":\"PILAR\",\"args\":{\"centro\":{\"x\":0,\"y\":0},\"mat\":\"" + codigo + "\"}}");
        comando("{\"codigo\":\"PILAR\",\"args\":{\"centro\":{\"x\":5,\"y\":0},\"mat\":\"MAT.CONCRETO.C40\"}}");
        var emUso = enviar("DELETE", "/api/v1/materiais/personalizados/" + mat.get("id").asString(), null);
        assertThat(emUso.getResponse().getStatus()).isEqualTo(409);
        assertThat(emUso.getResponse().getContentAsString()).contains("Obra de teste");
    }

    /** AUD-CC-3D-001: objetos e conexões gravados no banco, relidos iguais, verificados pela rede e pela coordenação */
    @Test
    void objetosEConexoesPersistemERedeEConflitosLeemDoModelo() throws Exception {
        comando("{\"codigo\":\"PAREDE\",\"args\":{\"p1\":{\"x\":0,\"y\":0},\"p2\":{\"x\":6,\"y\":0}}}");
        String qd = criado(comando2("{\"codigo\":\"OBJETO\",\"args\":{\"item\":\"INSTALACOES.QUADRO_DISTRIBUICAO\",\"pos\":{\"x\":5,\"y\":0.1}}}"));
        String tom = criado(comando2("{\"codigo\":\"OBJETO\",\"args\":{\"item\":\"INSTALACOES.TOMADA_10A\",\"pos\":{\"x\":1,\"y\":0.1}}}"));
        String gel = criado(comando2("{\"codigo\":\"OBJETO\",\"args\":{\"item\":\"COZINHA.GELADEIRA\",\"pos\":{\"x\":1,\"y\":0.52}}}"));
        String pessoa = criado(comando2("{\"codigo\":\"OBJETO\",\"args\":{\"item\":\"PESSOAS.ADULTO\",\"pos\":{\"x\":3,\"y\":2},\"altura\":1.6}}"));
        comando2("{\"codigo\":\"CONECTAR\",\"args\":{\"de\":\"" + qd + "\",\"para\":\"" + tom + "\",\"circuito\":\"C2\",\"disjuntor\":10}}");
        String cx = criado(comando2("{\"codigo\":\"CONECTAR\",\"args\":{\"de\":\"" + gel + "\",\"para\":\"" + tom + "\"}}"));

        // relido do banco: camadas novas, dimensões, sentido e circuito herdado da tomada
        var m = json(mvc.perform(get(obra() + "/modelo").with(ComoUsuario.como(engenheiro))).andReturn());
        JsonNode eCx = null, ePessoa = null;
        for (var e : m.get("elementos")) {
            if (e.get("id").asString().equals(cx)) eCx = e;
            if (e.get("id").asString().equals(pessoa)) ePessoa = e;
        }
        assertThat(eCx).isNotNull();
        assertThat(eCx.get("camada").asString()).isEqualTo("eletrica");
        assertThat(eCx.get("props").get("de").get("id").asString()).isEqualTo(tom);
        assertThat(eCx.get("props").get("circuito").asString()).isEqualTo("C2");
        assertThat(ePessoa.get("camada").asString()).isEqualTo("humanos");
        assertThat(ePessoa.get("props").get("dim").get("a").asDouble()).isEqualTo(1.6);

        // rede de instalações a partir das conexões gravadas
        var redes = json(mvc.perform(get(obra() + "/coordenacao/instalacoes").param("fp", "1").with(ComoUsuario.como(engenheiro))).andReturn());
        assertThat(redes.get("trechos").asInt()).isEqualTo(2);
        assertThat(redes.toString()).contains("MEP-ELE-05").contains("declarada pelas cargas");

        // incompatível é recusado pelo servidor, com motivo
        var chuv = criado(comando2("{\"codigo\":\"OBJETO\",\"args\":{\"item\":\"BANHEIRO.CHUVEIRO_ELETRICO\",\"pos\":{\"x\":4,\"y\":0.1}}}"));
        var r = enviar("POST", obra() + "/modelo/comandos", "{\"codigo\":\"CONECTAR\",\"args\":{\"de\":\"" + qd + ":S1\",\"para\":\"" + chuv + ":AF1\"}}");
        assertThat(r.getResponse().getStatus()).isEqualTo(422);
        assertThat(r.getResponse().getContentAsString()).contains("Sistemas incompat");

        // catálogo público e rota inexistente
        assertThat(mvc.perform(get("/api/v1/catalogo/objetos").param("q", "geladeira 70")).andReturn().getResponse().getContentAsString()).contains("COZINHA.GELADEIRA");
        assertThat(mvc.perform(get("/api/v1/rota-que-nao-existe").with(ComoUsuario.como(engenheiro))).andReturn().getResponse().getStatus()).isEqualTo(404);
    }

    private JsonNode comando2(String corpo) throws Exception {
        var r = enviar("POST", obra() + "/modelo/comandos", corpo);
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(201);
        return json(r);
    }

    private static String criado(JsonNode r) {
        return r.get("diff").get("criados").get(0).get("id").asString();
    }

    /** o navegador manda args e o texto VI; o texto não representa objetos aninhados, então os args valem */
    @Test
    void alterarPeloPainelGravaPropsAninhadasMesmoComTextoViJunto() throws Exception {
        String w = criado(comando2("{\"codigo\":\"PAREDE\",\"args\":{\"p1\":{\"x\":0,\"y\":0},\"p2\":{\"x\":6,\"y\":0}}}"));
        String lum = criado(comando2("{\"codigo\":\"OBJETO\",\"args\":{\"item\":\"ILUMINACAO.ARANDELA\",\"pos\":{\"x\":2,\"y\":0.2}}}"));
        comando2("{\"codigo\":\"ALTERAR\",\"args\":{\"id\":\"" + lum + "\",\"props\":{\"ligada\":false,\"elevacao\":1.9}},\"vi\":\"VI(ALTERAR, id=\\\"" + lum + "\\\", props=[object Object])\"}");
        comando2("{\"codigo\":\"ALTERAR\",\"args\":{\"id\":\"" + w + "\",\"props\":{\"altura\":3.1}},\"vi\":\"VI(ALTERAR, id=\\\"" + w + "\\\", props=[object Object])\"}");
        var m = json(mvc.perform(get(obra() + "/modelo").with(ComoUsuario.como(engenheiro))).andReturn());
        for (var e : m.get("elementos")) {
            if (e.get("id").asString().equals(lum)) {
                assertThat(e.get("props").get("ligada").asBoolean()).isFalse();
                assertThat(e.get("props").get("elevacao").asDouble()).isEqualTo(1.9);
            }
            if (e.get("id").asString().equals(w)) {
                assertThat(e.get("props").get("altura").asDouble()).isEqualTo(3.1);
            }
        }
    }
}
