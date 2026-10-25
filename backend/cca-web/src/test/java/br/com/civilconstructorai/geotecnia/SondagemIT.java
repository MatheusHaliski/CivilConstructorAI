package br.com.civilconstructorai.geotecnia;

import br.com.civilconstructorai.comum.UsuarioAtual;
import br.com.civilconstructorai.estrutura.CenarioEstrutural;
import br.com.civilconstructorai.support.AbstractIT;
import br.com.civilconstructorai.support.TesteSeguranca;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** RF09: furos SPT dentro do perímetro da obra (PostGIS), confirmação e perfil interpolado. */
@AutoConfigureMockMvc
// o módulo LGPD declara um @EnableJpaRepositories próprio que duplica o scan de PersistenciaConfig; como nos ITs do modelo, permite a sobreposição
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
class SondagemIT extends AbstractIT {

    /** quadrado de ~200 m em Curitiba (SIRGAS 2000) */
    static final String PERIMETRO = "POLYGON((-49.271 -25.431, -49.269 -25.431, -49.269 -25.429, -49.271 -25.429, -49.271 -25.431))";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager tm;
    @Autowired ObjectMapper mapper;

    CenarioEstrutural.Cenario cenario;
    UsuarioAtual engenheiro;

    @BeforeEach
    void cenario() {
        cenario = CenarioEstrutural.criar(jdbc, tm, PERIMETRO);
        engenheiro = cenario.engenheiro();
    }

    private String base() {
        return "/api/v1/obras/" + cenario.obraId() + "/sondagens";
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

    private Map<String, Object> furo(String codigo, double x, double y, double lon, double lat) {
        var leituras = new java.util.ArrayList<Map<String, Object>>();
        for (int z = 1; z <= 10; z++) {
            leituras.add(Map.of("profundidade", z, "nspt", z < 4 ? 6 : z < 8 ? 14 : 25, "impenetravel", false));
        }
        var m = new LinkedHashMap<String, Object>();
        m.put("codigo", codigo);
        m.put("x", x);
        m.put("y", y);
        m.put("longitude", lon);
        m.put("latitude", lat);
        m.put("cotaBoca", 0.0);
        m.put("nivelAgua", -3.0);
        m.put("camadas", List.of(Map.of("cotaTopo", 0, "cotaBase", -4, "tipo", "ARGILA_ARENOSA", "descricao", "argila arenosa marrom"),
                Map.of("cotaTopo", -4, "cotaBase", -10, "tipo", "AREIA", "descricao", "areia média compacta")));
        m.put("leituras", leituras);
        return m;
    }

    @Test
    void furoForaDoPerimetroRecebe422() throws Exception {
        var fora = postJson(engenheiro, "", furo("SP-01", 0, 0, -49.300, -25.500));
        assertThat(fora.getResponse().getStatus()).as(fora.getResponse().getContentAsString()).isEqualTo(422);
        assertThat(fora.getResponse().getContentAsString()).contains("perímetro");
        assertThat(mvc.perform(get(base()).with(authentication(TesteSeguranca.autenticacao(engenheiro)))).andReturn().getResponse().getContentAsString()).isEqualTo("[]");
    }

    @Test
    void furoDentroDoPerimetroConfirmadoAlimentaOPerfil() throws Exception {
        var dentro = postJson(engenheiro, "", furo("SP-01", 0, 0, -49.270, -25.430));
        assertThat(dentro.getResponse().getStatus()).as(dentro.getResponse().getContentAsString()).isEqualTo(201);
        var id = json(dentro).get("id").asString();
        assertThat(json(dentro).get("codigo").asString()).isEqualTo("SP-01");

        var duplicado = postJson(engenheiro, "", furo("SP-01", 5, 0, -49.2705, -25.4302));
        assertThat(duplicado.getResponse().getStatus()).isEqualTo(422);

        var codigoInvalido = postJson(engenheiro, "", furo("FURO1", 5, 0, -49.2705, -25.4302));
        assertThat(codigoInvalido.getResponse().getStatus()).isEqualTo(422);

        var semConfirmar = mvc.perform(get(base() + "/perfil?x=2&y=1").with(authentication(TesteSeguranca.autenticacao(engenheiro)))).andReturn();
        assertThat(semConfirmar.getResponse().getStatus()).as("sem furo confirmado não há perfil").isIn(404, 422);

        var confirmado = postJson(engenheiro, "/" + id + "/confirmar", null);
        assertThat(confirmado.getResponse().getStatus()).as(confirmado.getResponse().getContentAsString()).isEqualTo(200);
        assertThat(json(confirmado).get("estado").asString()).isEqualTo("CONFIRMADO");

        var perfil = mvc.perform(get(base() + "/perfil?x=2&y=1").with(authentication(TesteSeguranca.autenticacao(engenheiro)))).andReturn();
        assertThat(perfil.getResponse().getStatus()).as(perfil.getResponse().getContentAsString()).isEqualTo(200);
        assertThat(json(perfil).get("furoMaisProximo").asString()).isEqualTo("SP-01");
        assertThat(json(perfil).get("leituras")).hasSize(10);

        var presign = postJson(engenheiro, "/" + id + "/boletim/presign", null);
        assertThat(presign.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(presign).get("url").asString()).isNotBlank();
    }

    @Test
    void clienteNaoCadastraFuro() throws Exception {
        var cliente = postJson(cenario.cliente(), "", furo("SP-02", 0, 0, -49.270, -25.430));
        assertThat(cliente.getResponse().getStatus()).isEqualTo(403);
    }
}
