package br.com.civilconstructorai.orcamento;

import br.com.civilconstructorai.comum.UsuarioAtual;
import br.com.civilconstructorai.estrutura.CenarioEstrutural;
import br.com.civilconstructorai.estrutura.ModeloEstruturalEmMemoria;
import br.com.civilconstructorai.ia.ModeloLinguagem;
import br.com.civilconstructorai.ia.RotaIa;
import br.com.civilconstructorai.support.AbstractIT;
import br.com.civilconstructorai.support.TesteSeguranca;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
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

/**
 * RF11/RF12: quantitativos do modelo, orçamento SINAPI com sugestões da IA (marcadas, validadas e confirmadas
 * antes da aprovação) e visão do CLIENTE restrita aos totais liberados (DN-02).
 */
@AutoConfigureMockMvc
@Import(OrcamentoIT.IaDeTeste.class)
class OrcamentoIT extends AbstractIT {

    /**
     * IA determinística da rota ASSOCIACAO_SINAPI: uma sugestão válida (fôrma de viga → 92267, m²), uma com
     * unidade incompatível (fôrma de laje → 92779, kg) e uma com código inexistente — as duas últimas devem
     * ser descartadas pela validação do serviço.
     */
    @TestConfiguration
    static class IaDeTeste {
        @Bean
        @Primary
        ModeloLinguagem iaOrcamento() {
            return new ModeloLinguagem() {
                @Override
                public Resposta gerarEstruturado(RotaIa rota, String system, String entrada, String schema, Map<String, String> contexto) {
                    var json = rota == RotaIa.ASSOCIACAO_SINAPI
                            ? "{\"associacoes\":[" +
                            "{\"servico\":\"Fôrma viga\",\"codigo\":\"92267\",\"justificativa\":\"fôrma de madeira para vigas, m²\"}," +
                            "{\"servico\":\"Fôrma laje\",\"codigo\":\"92779\",\"justificativa\":\"unidade errada (kg)\"}," +
                            "{\"servico\":\"Fôrma pilar\",\"codigo\":\"00000\",\"justificativa\":\"código inexistente\"}]}"
                            : "{}";
                    return new Resposta(json, "end_turn", false, 100, 50, 0, 0, 3, "ia-de-teste");
                }

                @Override
                public Resposta responderComCitacoes(RotaIa rota, String system, byte[] pdf, String pergunta) {
                    return new Resposta("{\"resposta\":\"\",\"citacoes\":[]}", "end_turn", false, 1, 1, 0, 0, 1, "ia-de-teste");
                }
            };
        }
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager tm;
    @Autowired ObjectMapper mapper;
    @Autowired ModeloEstruturalEmMemoria modelos;

    CenarioEstrutural.Cenario cenario;
    UsuarioAtual engenheiro;
    UsuarioAtual cliente;

    @BeforeEach
    void cenario() {
        cenario = CenarioEstrutural.criar(jdbc, tm);
        engenheiro = cenario.engenheiro();
        cliente = cenario.cliente();
        modelos.registrar(CenarioEstrutural.portico(cenario.obraId(), 2));
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
        return mvc.perform(get(caminho.startsWith("/api") ? caminho : base() + caminho).with(authentication(TesteSeguranca.autenticacao(u)))).andReturn();
    }

    private JsonNode json(MvcResult r) {
        return mapper.readTree(r.getResponse().getContentAsByteArray());
    }

    @Test
    void quantitativosDoModeloEExportacao() throws Exception {
        var q = getJson(engenheiro, "/quantitativos");
        assertThat(q.getResponse().getStatus()).as(q.getResponse().getContentAsString()).isEqualTo(200);
        var corpo = json(q);
        assertThat(corpo.get("linhas")).hasSize(9);
        assertThat(corpo.get("versaoModelo").asLong()).isEqualTo(2);
        assertThat(corpo.get("hash").asString()).hasSize(8);
        assertThat(corpo.get("totalGeral").get("concretoM3").asDouble()).isGreaterThan(3);

        // mesmo modelo → mesmo levantamento (hash determinístico)
        assertThat(json(getJson(engenheiro, "/quantitativos")).get("id").asString()).isEqualTo(corpo.get("id").asString());

        var csv = getJson(engenheiro, "/quantitativos/export?formato=csv");
        assertThat(csv.getResponse().getStatus()).isEqualTo(200);
        assertThat(csv.getResponse().getContentAsString()).contains("P1").contains("V1");
        var xlsx = getJson(engenheiro, "/quantitativos/export?formato=xlsx");
        assertThat(xlsx.getResponse().getStatus()).isEqualTo(200);
        assertThat(xlsx.getResponse().getContentAsByteArray()).startsWith(new byte[]{0x50, 0x4B});

        // DN-02: cliente só vê depois de liberado, e só totais
        assertThat(getJson(cliente, "/quantitativos").getResponse().getStatus()).isEqualTo(404);
        var liberar = postJson(engenheiro, "/quantitativos/" + corpo.get("id").asString() + "/liberar-cliente?liberar=true", null);
        assertThat(liberar.getResponse().getStatus()).as(liberar.getResponse().getContentAsString()).isEqualTo(200);
        var visaoCliente = getJson(cliente, "/quantitativos");
        assertThat(visaoCliente.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(visaoCliente).get("linhas")).isEmpty();
        assertThat(json(visaoCliente).get("totalGeral").get("concretoM3").asDouble()).isGreaterThan(3);
    }

    @Test
    void sinapiSeedDisponivel() throws Exception {
        var tabelas = getJson(engenheiro, "/api/v1/sinapi/tabelas");
        assertThat(tabelas.getResponse().getStatus()).as(tabelas.getResponse().getContentAsString()).isEqualTo(200);
        assertThat(json(tabelas).size()).isGreaterThanOrEqualTo(1);
        var tabelaId = json(tabelas).get(0).get("id").asString();
        var formas = getJson(engenheiro, "/api/v1/sinapi/composicoes?tabelaId=" + tabelaId + "&termo=Fôrma");
        assertThat(formas.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(formas).size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void orcamentoComSugestaoIaValidadaEVisaoDoCliente() throws Exception {
        var gerado = postJson(engenheiro, "/orcamentos", Map.of("uf", "PR", "desonerado", false, "usarIa", true));
        assertThat(gerado.getResponse().getStatus()).as(gerado.getResponse().getContentAsString()).isEqualTo(201);
        var orc = json(gerado);
        var orcId = orc.get("id").asString();
        assertThat(orc.get("estado").asString()).isEqualTo("GERADO_POR_IA");
        assertThat(orc.get("itensGeradosPorIa").asInt()).as("só a sugestão com código existente e unidade compatível entra").isEqualTo(1);
        assertThat(orc.get("total").asDouble()).isGreaterThan(0);

        var visao = json(getJson(engenheiro, "/orcamentos/" + orcId));
        assertThat(visao.get("itensPendentesIa").asInt()).isEqualTo(1);
        JsonNode itemIa = null;
        for (var i : visao.get("itens")) {
            if (i.get("geradoPorIa").asBoolean()) {
                itemIa = i;
            }
            assertThat(i.get("codigoSinapi").asString()).isNotEqualTo("00000");
        }
        assertThat(itemIa).isNotNull();
        assertThat(itemIa.get("codigoSinapi").asString()).isEqualTo("92267");
        assertThat(itemIa.get("servico").asString()).isEqualTo("Fôrma viga");
        assertThat(itemIa.get("unidade").asString()).isEqualTo("m2");
        assertThat(itemIa.get("justificativaIa").asString()).isNotBlank();

        // aprovar com sugestão pendente → 422; após confirmar → APROVADO
        var bloqueado = postJson(engenheiro, "/orcamentos/" + orcId + "/aprovar", null);
        assertThat(bloqueado.getResponse().getStatus()).isEqualTo(422);
        assertThat(bloqueado.getResponse().getContentAsString()).contains("itens_ia_pendentes");

        var confirmado = postJson(engenheiro, "/orcamentos/" + orcId + "/itens/" + itemIa.get("id").asString() + "/confirmar", null);
        assertThat(confirmado.getResponse().getStatus()).as(confirmado.getResponse().getContentAsString()).isEqualTo(200);
        assertThat(json(confirmado).get("confirmadoPor").asString()).isEqualTo(engenheiro.contaId().toString());

        assertThat(postJson(cliente, "/orcamentos/" + orcId + "/aprovar", null).getResponse().getStatus()).isEqualTo(403);
        var aprovado = postJson(engenheiro, "/orcamentos/" + orcId + "/aprovar", null);
        assertThat(aprovado.getResponse().getStatus()).as(aprovado.getResponse().getContentAsString()).isEqualTo(200);
        assertThat(json(aprovado).get("estado").asString()).isEqualTo("APROVADO");

        // DN-02: cliente não vê nada até a liberação; depois, só totais por etapa
        assertThat(getJson(cliente, "/orcamentos/" + orcId).getResponse().getStatus()).isEqualTo(404);
        var liberado = postJson(engenheiro, "/orcamentos/" + orcId + "/liberar-cliente", Map.of("totais", true, "detalhe", false));
        assertThat(liberado.getResponse().getStatus()).as(liberado.getResponse().getContentAsString()).isEqualTo(200);
        var visaoCliente = getJson(cliente, "/orcamentos/" + orcId);
        assertThat(visaoCliente.getResponse().getStatus()).isEqualTo(200);
        var vc = json(visaoCliente);
        assertThat(vc.get("total").asDouble()).isGreaterThan(0);
        assertThat(vc.get("totaisPorEtapa").has("Estrutura")).isTrue();
        assertThat(vc.has("itens")).as("detalhe não liberado").isFalse();
        assertThat(vc.has("itensPendentesIa")).isFalse();

        // aditivo proposto pela engenharia, decidido pelo cliente
        var aditivo = postJson(engenheiro, "/orcamentos/" + orcId + "/aditivos", Map.of("descricao", "Reforço de fundação", "valor", 12500.00));
        assertThat(aditivo.getResponse().getStatus()).as(aditivo.getResponse().getContentAsString()).isEqualTo(201);
        var decidido = postJson(cliente, "/orcamentos/" + orcId + "/aditivos/" + json(aditivo).get("id").asString() + "/decidir?aprovar=true", null);
        assertThat(decidido.getResponse().getStatus()).as(decidido.getResponse().getContentAsString()).isEqualTo(200);
    }
}
