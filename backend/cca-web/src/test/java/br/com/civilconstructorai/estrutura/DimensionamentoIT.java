package br.com.civilconstructorai.estrutura;

import br.com.civilconstructorai.acesso.PapelObra;
import br.com.civilconstructorai.comum.UsuarioAtual;
import br.com.civilconstructorai.support.AbstractIT;
import br.com.civilconstructorai.support.TesteSeguranca;
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

/** RF07/RF08: dimensionamento NBR 6118 a partir da análise; aprovação exige papel aprovador + selo + 2FA (RF21). */
@AutoConfigureMockMvc
class DimensionamentoIT extends AbstractIT {

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
        modelos.registrar(CenarioEstrutural.portico(cenario.obraId(), 1));
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

    private JsonNode json(MvcResult r) {
        return mapper.readTree(r.getResponse().getContentAsByteArray());
    }

    private String dimensionar() throws Exception {
        var analise = postJson(engenheiro, "/analises", null);
        assertThat(json(analise).get("estado").asString()).isEqualTo("CONCLUIDO");
        var dim = postJson(engenheiro, "/dimensionamentos", Map.of("analiseId", json(analise).get("id").asString()));
        assertThat(dim.getResponse().getStatus()).as(dim.getResponse().getContentAsString()).isEqualTo(202);
        var corpo = json(dim);
        assertThat(corpo.get("estado").asString()).as(corpo.toString()).isEqualTo("CALCULADO");
        return corpo.get("id").asString();
    }

    @Test
    void dimensionaTodosOsElementosComMemoriaDeCalculo() throws Exception {
        var id = dimensionar();
        var resultados = mvc.perform(get(base() + "/dimensionamentos/" + id + "/resultados").with(authentication(TesteSeguranca.autenticacao(engenheiro)))).andReturn();
        assertThat(resultados.getResponse().getStatus()).isEqualTo(200);
        var lista = json(resultados);
        assertThat(lista).hasSize(9); // 4 pilares + 4 vigas + 1 laje
        for (var r : lista) {
            assertThat(r.get("memoria")).as(r.toString()).isNotNull();
            assertThat(r.get("verifica").asBoolean()).isFalse();
            assertThat(r.get("estadoVerificacao").asString()).isIn("NAO_ATENDE", "NAO_VERIFICADO");
        }
        assertThat(lista.findValues("tipo").stream().map(JsonNode::asString)).contains("PILAR", "VIGA", "LAJE");
    }

    @Test
    void aprovarSemSeloOu2faRecebe403() throws Exception {
        var id = dimensionar();
        var semSelo = postJson(cenario.usuario(PapelObra.ENGENHEIRO, true, false), "/dimensionamentos/" + id + "/aprovar", Map.of("artRrt", "CREA-PR 123456"));
        assertThat(semSelo.getResponse().getStatus()).isEqualTo(403);
        var semMfa = postJson(cenario.usuario(PapelObra.ENGENHEIRO, false, true), "/dimensionamentos/" + id + "/aprovar", Map.of("artRrt", "CREA-PR 123456"));
        assertThat(semMfa.getResponse().getStatus()).isEqualTo(403);
        var orcamentista = postJson(cenario.usuario(PapelObra.ORCAMENTISTA, true, true), "/dimensionamentos/" + id + "/aprovar", Map.of("artRrt", "CREA-PR 123456"));
        assertThat(orcamentista.getResponse().getStatus()).isEqualTo(403);
        var clienteRejeita = postJson(cenario.cliente(), "/dimensionamentos/" + id + "/rejeitar", null);
        assertThat(clienteRejeita.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void mesmoComSelo2faEArtPreDimensionamentoNaoPodeSerAprovado() throws Exception {
        var id = dimensionar();
        var semArt = postJson(engenheiro, "/dimensionamentos/" + id + "/aprovar", Map.of());
        assertThat(semArt.getResponse().getStatus()).isEqualTo(422);
        var aprovacao = postJson(engenheiro, "/dimensionamentos/" + id + "/aprovar", Map.of("artRrt", "CREA-PR 123456"));
        var corpo = json(aprovacao);
        assertThat(aprovacao.getResponse().getStatus()).as(corpo.toString()).isEqualTo(422);
        assertThat(corpo.toString()).contains("elementos_nao_verificam");
    }

    @Test
    void mudancaDoModeloBloqueiaAprovacaoCom409() throws Exception {
        var id = dimensionar();
        modelos.registrar(CenarioEstrutural.portico(cenario.obraId(), 2));
        var r = postJson(engenheiro, "/dimensionamentos/" + id + "/aprovar", Map.of("artRrt", "CREA-PR 123456"));
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(409);
    }

    @Test
    void perfilMetalicoNaoRecebeAnaliseComPropriedadesDeConcreto() throws Exception {
        var m = CenarioEstrutural.portico(cenario.obraId(), 1);
        var elementos = new java.util.ArrayList<>(m.elementos());
        elementos.add(br.com.civilconstructorai.estrutura.modelo.ElementoEstrutural.viga("aco", "p1",
                new br.com.civilconstructorai.estrutura.modelo.Ponto(0, 0),
                new br.com.civilconstructorai.estrutura.modelo.Ponto(4, 0), .2, .5, "MAT_ACO_PERFIL_W"));
        modelos.registrar(new br.com.civilconstructorai.estrutura.modelo.ModeloEstrutural(
                m.obraId(), m.versao(), m.pavimentos(), elementos));
        var r = json(postJson(engenheiro, "/analises", null));
        assertThat(r.get("estado").asString()).isEqualTo("FALHOU");
        assertThat(r.get("mensagemErro").asString()).contains("Material não suportado");
    }
}
