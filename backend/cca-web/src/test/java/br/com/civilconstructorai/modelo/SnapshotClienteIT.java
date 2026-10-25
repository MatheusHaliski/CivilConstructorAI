package br.com.civilconstructorai.modelo;

import br.com.civilconstructorai.acesso.PapelObra;
import br.com.civilconstructorai.support.AbstractIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** RF13 CA-08: CLIENTE só recebe versões liberadas (senão 404); FORNECEDOR não libera (403). */
@AutoConfigureMockMvc
@Import(AcessoObraTeste.class)
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
class SnapshotClienteIT extends AbstractIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager tm;

    @Test
    void clienteSoVeVersaoLiberada() throws Exception {
        var c = ObraDeTeste.criar(jdbc, tm);
        var eng = ObraDeTeste.membro(jdbc, tm, c, PapelObra.ENGENHEIRO, true, true);
        var cliente = ObraDeTeste.membro(jdbc, tm, c, PapelObra.CLIENTE, false, false);
        var fornecedor = ObraDeTeste.membro(jdbc, tm, c, PapelObra.FORNECEDOR, false, false);
        var base = "/api/v1/obras/" + c.obraId() + "/modelo";

        mvc.perform(post(base + "/comandos").with(ComoUsuario.como(eng)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"codigo\":\"PAREDE\",\"args\":{\"p1\":{\"x\":0,\"y\":0},\"p2\":{\"x\":5,\"y\":0}}}")).andExpect(status().isCreated());
        mvc.perform(post(base + "/texto-natural").with(ComoUsuario.como(eng)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"texto\":\"outra parede\"}")).andExpect(status().isCreated());

        mvc.perform(get(base + "/snapshot").with(ComoUsuario.como(cliente))).andExpect(status().isNotFound());
        mvc.perform(get(base + "/snapshot").with(ComoUsuario.como(eng))).andExpect(status().isOk())
                .andExpect(jsonPath("$.camadas.arquitetura.length()").value(1)).andExpect(jsonPath("$.camadas['ia-rascunho'].length()").value(1));

        mvc.perform(patch(base + "/liberar-cliente").with(ComoUsuario.como(fornecedor)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"liberar\":true}")).andExpect(status().isForbidden());
        mvc.perform(patch(base + "/liberar-cliente").with(ComoUsuario.como(eng)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"liberar\":true}")).andExpect(status().isOk());

        mvc.perform(get(base + "/snapshot").with(ComoUsuario.como(cliente))).andExpect(status().isOk())
                .andExpect(jsonPath("$.liberadaCliente").value(true))
                .andExpect(jsonPath("$.camadas.arquitetura.length()").value(1))
                .andExpect(jsonPath("$.camadas['ia-rascunho'].length()").value(0));
        // pedir outra versão → 404
        mvc.perform(get(base + "/snapshot?versao=1").with(ComoUsuario.como(cliente))).andExpect(status().isNotFound());
    }
}
