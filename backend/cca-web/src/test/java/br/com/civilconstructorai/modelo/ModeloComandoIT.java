package br.com.civilconstructorai.modelo;

import br.com.civilconstructorai.acesso.PapelObra;
import br.com.civilconstructorai.comum.UsuarioAtual;
import br.com.civilconstructorai.support.AbstractIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** RF01/RF02/RF03/RF16: comandos VI pela API com sessão, CSRF, conflito, auditoria e acesso por papel. */
@AutoConfigureMockMvc
@Import(AcessoObraTeste.class)
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
class ModeloComandoIT extends AbstractIT {

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

    private String base() {
        return "/api/v1/obras/" + cenario.obraId() + "/modelo";
    }

    private MvcResult postJson(UsuarioAtual u, String caminho, String corpo) throws Exception {
        return mvc.perform(post(base() + caminho).with(ComoUsuario.como(u)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(corpo)).andReturn();
    }

    private JsonNode json(MvcResult r) {
        return mapper.readTree(r.getResponse().getContentAsByteArray());
    }

    @Test
    void fluxoCompletoMalhaPilarVigaDiffDesfazer() throws Exception {
        var malha = postJson(engenheiro, "/malha", "{\"nx\":3,\"dx\":5,\"ny\":2,\"dy\":6}");
        assertThat(malha.getResponse().getStatus()).as(malha.getResponse().getContentAsString()).isEqualTo(201);
        assertThat(json(malha).get("versao").asLong()).isEqualTo(1);
        assertThat(json(malha).get("diff").get("eixosCriados")).hasSize(5);

        var pilar = postJson(engenheiro, "/comandos", "{\"codigo\":\"PILAR\",\"args\":{\"centro\":{\"x\":0,\"y\":0},\"b\":0.19,\"h\":0.4},\"versaoBase\":1}");
        assertThat(pilar.getResponse().getStatus()).as(pilar.getResponse().getContentAsString()).isEqualTo(201);
        assertThat(json(pilar).get("vi").asString()).startsWith("VI(PILAR");
        assertThat(json(pilar).get("relatorio").get("aprovado").asBoolean()).isTrue();

        // versão desatualizada → 409
        var conflito = postJson(engenheiro, "/comandos", "{\"codigo\":\"LINHA\",\"args\":{\"p1\":{\"x\":0,\"y\":0},\"p2\":{\"x\":1,\"y\":0}},\"versaoBase\":0}");
        assertThat(conflito.getResponse().getStatus()).isEqualTo(409);

        // texto VI com polar
        var viga = postJson(engenheiro, "/comandos", "{\"vi\":\"VI(VIGA, p1=[0,0], p2=@5<0, b=0.12, h=0.4)\"}");
        assertThat(viga.getResponse().getStatus()).as(viga.getResponse().getContentAsString()).isEqualTo(201);
        assertThat(json(viga).get("diff").get("criados").get(0).get("props").get("p2").get("x").asDouble()).isEqualTo(5.0);

        var modelo = mvc.perform(get(base()).with(ComoUsuario.como(engenheiro))).andExpect(status().isOk()).andReturn();
        assertThat(json(modelo).get("versao").asLong()).isEqualTo(3);
        assertThat(json(modelo).get("elementos")).hasSize(2);

        var diff = mvc.perform(get(base() + "/diff?desde=1").with(ComoUsuario.como(engenheiro))).andExpect(status().isOk()).andReturn();
        assertThat(json(diff).get("criados")).hasSize(2);

        var comandoId = json(viga).get("comandoId").asString();
        var desfeito = postJson(engenheiro, "/comandos/" + comandoId + "/desfazer", "");
        assertThat(desfeito.getResponse().getStatus()).as(desfeito.getResponse().getContentAsString()).isEqualTo(201);
        assertThat(json(desfeito).get("diff").get("removidos")).hasSize(1);
        var depois = mvc.perform(get(base()).with(ComoUsuario.como(engenheiro))).andReturn();
        assertThat(json(depois).get("elementos")).hasSize(1);
        assertThat(json(depois).get("versao").asLong()).isEqualTo(4);

        var historico = mvc.perform(get(base() + "/comandos").with(ComoUsuario.como(engenheiro))).andExpect(status().isOk()).andReturn();
        assertThat(json(historico)).hasSize(4);
    }

    @Test
    void reprovacaoDevolve422ComRelatorioCompleto() throws Exception {
        var r = postJson(engenheiro, "/comandos", "{\"codigo\":\"PILAR\",\"args\":{\"centro\":{\"x\":0,\"y\":0},\"b\":0.15,\"h\":0.15}}");
        assertThat(r.getResponse().getStatus()).isEqualTo(422);
        var j = json(r);
        assertThat(j.get("type").asString()).endsWith("auditoria_reprovada");
        var codigos = j.get("relatorio").get("laco1").get("violacoes").findValuesAsString("codigo");
        assertThat(codigos).contains("C-SECAO_MIN_PILAR", "C-AREA_MIN_PILAR");
        assertThat(j.get("errors")).hasSizeGreaterThanOrEqualTo(2);
        mvc.perform(get(base()).with(ComoUsuario.como(engenheiro))).andExpect(jsonPath("$.versao").value(0));
    }

    @Test
    void papelInsuficienteDa403ENaoMembroDa404() throws Exception {
        var mestre = ObraDeTeste.membro(jdbc, tm, cenario, PapelObra.MESTRE_OBRAS, false, false);
        var r = postJson(mestre, "/comandos", "{\"codigo\":\"LINHA\",\"args\":{\"p1\":{\"x\":0,\"y\":0},\"p2\":{\"x\":1,\"y\":0}}}");
        assertThat(r.getResponse().getStatus()).isEqualTo(403);
        var outraObra = ObraDeTeste.membro(jdbc, tm, ObraDeTeste.criar(jdbc, tm), PapelObra.ENGENHEIRO, true, true);
        var r404 = mvc.perform(get(base()).with(ComoUsuario.como(outraObra))).andReturn();
        assertThat(r404.getResponse().getStatus()).isEqualTo(404);
        mvc.perform(get(base())).andExpect(status().isUnauthorized());
    }

    @Test
    void textoNaturalCriaRascunhoGeradoPorIa() throws Exception {
        var r = postJson(engenheiro, "/texto-natural", "{\"texto\":\"faça uma parede de 5 m a partir da origem\"}");
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(201);
        var j = json(r);
        assertThat(j.get("geradoPorIa").asBoolean()).isTrue();
        assertThat(j.get("comandos").get(0).get("diff").get("criados").get(0).get("camada").asString()).isEqualTo("ia-rascunho");
        assertThat(j.get("comandos").get(0).get("diff").get("geradoPorIa").asBoolean()).isTrue();
    }

    @Test
    void verificarConclusaoEAlertasDeTransicao() throws Exception {
        postJson(engenheiro, "/comandos", "{\"codigo\":\"PAREDE\",\"args\":{\"p1\":{\"x\":0,\"y\":0},\"p2\":{\"x\":5,\"y\":0}}}");
        var v = postJson(engenheiro, "/verificar-conclusao", "");
        assertThat(v.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(v).get("aprovado").asBoolean()).isFalse();
        postJson(engenheiro, "/pavimentos", "{\"nome\":\"1º\",\"z\":3}");
        var pav1 = json(mvc.perform(get(base()).with(ComoUsuario.como(engenheiro))).andReturn()).get("pavimentos").get(1).get("id").asString();
        postJson(engenheiro, "/comandos", "{\"pavimentoId\":\"" + pav1 + "\",\"codigo\":\"PILAR\",\"args\":{\"centro\":{\"x\":2,\"y\":2}}}");
        var alertas = mvc.perform(get(base() + "/alertas-transicao").with(ComoUsuario.como(engenheiro))).andExpect(status().isOk()).andReturn();
        assertThat(json(alertas)).hasSize(1);
        var alertaId = json(alertas).get(0).get("id").asString();
        var semJustificativa = postJson(engenheiro, "/alertas-transicao/" + alertaId + "/decidir", "{\"decisao\":\"CONFIRMAR\"}");
        assertThat(semJustificativa.getResponse().getStatus()).isEqualTo(422);
        var ok = postJson(engenheiro, "/alertas-transicao/" + alertaId + "/decidir", "{\"decisao\":\"CONFIRMAR\",\"justificativa\":\"Pilar nasce em viga de transição V3\"}");
        assertThat(ok.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(ok).get("estado").asString()).isEqualTo("CONFIRMADO");
    }
}
