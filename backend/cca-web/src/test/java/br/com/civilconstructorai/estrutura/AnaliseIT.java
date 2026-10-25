package br.com.civilconstructorai.estrutura;

import br.com.civilconstructorai.support.AbstractIT;
import br.com.civilconstructorai.support.TesteSeguranca;
import br.com.civilconstructorai.comum.UsuarioAtual;
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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** RF04/RF05/RF06: lançamento de cargas, combinações NBR 8681, análise MEF (job → CONCLUIDO / FALHOU) e vãos. */
@AutoConfigureMockMvc
class AnaliseIT extends AbstractIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager tm;
    @Autowired ObjectMapper mapper;
    @Autowired ModeloEstruturalEmMemoria modelos;

    CenarioEstrutural.Cenario cenario;
    UsuarioAtual engenheiro;

    @BeforeEach
    void cenario() {
        cenario = CenarioEstrutural.criar(jdbc, tm);
        engenheiro = cenario.engenheiro();
        modelos.registrar(CenarioEstrutural.portico(cenario.obraId(), 3));
    }

    private String base() {
        return "/api/v1/obras/" + cenario.obraId();
    }

    private MvcResult postJson(UsuarioAtual u, String caminho, Object corpo) throws Exception {
        var req = post(base() + caminho).with(authentication(TesteSeguranca.autenticacao(u))).with(csrf());
        if (corpo != null) {
            req = req.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(corpo));
        }
        return mvc.perform(req).andReturn();
    }

    private MvcResult getJson(UsuarioAtual u, String caminho) throws Exception {
        return mvc.perform(get(base() + caminho).with(authentication(TesteSeguranca.autenticacao(u)))).andReturn();
    }

    private JsonNode json(MvcResult r) {
        return mapper.readTree(r.getResponse().getContentAsByteArray());
    }

    @Test
    void cargasCombinacoesEAnaliseConcluida() throws Exception {
        var carga = postJson(engenheiro, "/estrutura/cargas", Map.of("pavimentoId", CenarioEstrutural.TIPO, "natureza", "ACIDENTAL", "tipo", "AREA",
                "valor", 2.0, "descricao", "Sobrecarga residencial NBR 6120"));
        assertThat(carga.getResponse().getStatus()).as(carga.getResponse().getContentAsString()).isEqualTo(201);
        assertThat(json(carga).get("natureza").asString()).isEqualTo("ACIDENTAL");

        var invalida = postJson(engenheiro, "/estrutura/cargas", Map.of("pavimentoId", CenarioEstrutural.TIPO, "natureza", "ACIDENTAL", "tipo", "AREA",
                "valor", -1, "descricao", "negativa"));
        assertThat(invalida.getResponse().getStatus()).isEqualTo(422);

        var combinacoes = getJson(engenheiro, "/estrutura/combinacoes?pavimentoId=" + CenarioEstrutural.TIPO);
        assertThat(combinacoes.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(combinacoes).size()).isGreaterThanOrEqualTo(3); // ELU + ELS com G e Q

        var analise = postJson(engenheiro, "/analises", null);
        assertThat(analise.getResponse().getStatus()).as(analise.getResponse().getContentAsString()).isEqualTo(202);
        var corpo = json(analise);
        assertThat(corpo.get("estado").asString()).as(corpo.toString()).isEqualTo("CONCLUIDO");
        assertThat(corpo.get("versaoModelo").asLong()).isEqualTo(3);

        var diagramas = getJson(engenheiro, "/analises/" + corpo.get("id").asString() + "/diagramas");
        assertThat(diagramas.getResponse().getStatus()).isEqualTo(200);
        var d = json(diagramas);
        assertThat(d.get("barras").asInt()).isEqualTo(8);
        var primeira = d.get("porCombinacao").properties().iterator().next().getValue();
        assertThat(primeira.get("reacoes")).hasSize(4);
        double somaFz = 0;
        for (var r : primeira.get("reacoes")) {
            somaFz += r.get("fz").asDouble();
        }
        assertThat(somaFz).as("reações verticais equilibram peso próprio + sobrecarga").isGreaterThan(50);
        assertThat(primeira.get("deslocamentoMax").asDouble()).isGreaterThan(0);

        var lista = getJson(engenheiro, "/analises");
        assertThat(json(lista)).hasSize(1);
    }

    @Test
    void vigaSemApoioFalhaComoHipostatica() throws Exception {
        modelos.registrar(CenarioEstrutural.vigaSolta(cenario.obraId(), 4));
        var analise = postJson(engenheiro, "/analises", null);
        assertThat(analise.getResponse().getStatus()).isEqualTo(202);
        var corpo = json(analise);
        assertThat(corpo.get("estado").asString()).isEqualTo("FALHOU");
        assertThat(corpo.get("mensagemErro").asString()).containsIgnoringCase("hipost");
    }

    @Test
    void vaosDetectamCaixaCartesiana() throws Exception {
        var vaos = getJson(engenheiro, "/estrutura/vaos");
        assertThat(vaos.getResponse().getStatus()).as(vaos.getResponse().getContentAsString()).isEqualTo(200);
        JsonNode relatorio = null;
        for (var r : json(vaos)) {
            if (CenarioEstrutural.TIPO.equals(r.get("pavimentoId").asString())) {
                relatorio = r;
            }
        }
        assertThat(relatorio).as(vaos.getResponse().getContentAsString()).isNotNull();
        var caixa = relatorio.get("caixa");
        assertThat(caixa.get("xMax").asDouble()).isEqualTo(5.0);
        assertThat(caixa.get("yMax").asDouble()).isEqualTo(4.0);
    }

    @Test
    void clienteNaoSolicitaAnaliseEForaDaObraRecebe404() throws Exception {
        var cliente = postJson(cenario.cliente(), "/analises", null);
        assertThat(cliente.getResponse().getStatus()).isEqualTo(403);

        var outra = CenarioEstrutural.criar(jdbc, tm);
        var intruso = postJson(outra.engenheiro(), "/analises", null);
        assertThat(intruso.getResponse().getStatus()).isEqualTo(404);
    }
}
