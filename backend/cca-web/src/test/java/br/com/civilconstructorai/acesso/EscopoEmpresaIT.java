package br.com.civilconstructorai.acesso;

import br.com.civilconstructorai.comum.UsuarioAtual;
import br.com.civilconstructorai.modelo.ComoUsuario;
import br.com.civilconstructorai.modelo.ObraDeTeste;
import br.com.civilconstructorai.support.AbstractIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * RF21 CA-01/CA-04 (auditoria 2026-10): papel de empresa (DONO/ADMIN) só vale para obras da empresa ativa.
 * Reproduz o defeito em que o dono da empresa B gravava um comando no modelo da obra da empresa A e, com isso,
 * a dona legítima passava a receber 500 ao salvar (modelo_bim.obra_id unique).
 */
@AutoConfigureMockMvc
class EscopoEmpresaIT extends AbstractIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager tm;

    private int status(UsuarioAtual u, String metodo, String url, String corpo) throws Exception {
        var req = metodo.equals("GET") ? get(url) : post(url).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(corpo);
        return mvc.perform(req.with(ComoUsuario.como(u))).andReturn().getResponse().getStatus();
    }

    @Test
    void donoDeOutraEmpresaNaoLeNemGravaNaObraAlheiaEADonaContinuaGravando() throws Exception {
        var a = ObraDeTeste.criar(jdbc, tm);
        var b = ObraDeTeste.criar(jdbc, tm);
        var donaA = ObraDeTeste.membro(jdbc, tm, a, PapelObra.DONO, true, true);
        var donoB = ObraDeTeste.membro(jdbc, tm, b, PapelObra.DONO, true, true);
        var modeloA = "/api/v1/obras/" + a.obraId() + "/modelo";
        var linha = "{\"codigo\":\"LINHA\",\"args\":{\"p1\":{\"x\":0,\"y\":0},\"p2\":{\"x\":1,\"y\":0}},\"versaoBase\":0}";

        assertThat(status(donoB, "GET", modeloA, null)).isEqualTo(404);
        assertThat(status(donoB, "GET", "/api/v1/obras/" + UUID.randomUUID() + "/modelo", null)).isEqualTo(404);
        assertThat(status(donoB, "POST", modeloA + "/comandos", linha)).isEqualTo(404);
        assertThat(status(donoB, "GET", "/api/v1/obras/" + a.obraId() + "/estrutura/cargas", null)).isEqualTo(404);
        assertThat(status(donoB, "GET", "/api/v1/obras/" + a.obraId() + "/rascunhos-ia", null)).isEqualTo(404);

        assertThat(status(donaA, "GET", modeloA, null)).isEqualTo(200);
        assertThat(status(donaA, "POST", modeloA + "/comandos", linha)).isEqualTo(201);
        assertThat(status(donoB, "GET", "/api/v1/obras/" + b.obraId() + "/modelo", null)).isEqualTo(200);
    }
}
