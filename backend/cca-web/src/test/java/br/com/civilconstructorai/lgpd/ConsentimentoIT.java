package br.com.civilconstructorai.lgpd;

import br.com.civilconstructorai.acesso.PapelObra;
import br.com.civilconstructorai.support.AbstractIT;
import br.com.civilconstructorai.support.TesteSeguranca;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** RF24 · consentimentos por finalidade e bloqueio de reaceite. */
@AutoConfigureMockMvc
class ConsentimentoIT extends AbstractIT {

    @Autowired MockMvc mvc;
    @Autowired LgpdFixtures fixtures;

    @Test
    void togglesComecamDesligadosConcedemERevogamComHistorico() throws Exception {
        var contaId = fixtures.conta("consent-" + UUID.randomUUID() + "@teste.dev", "ACTIVE");
        fixtures.aceitarDocumentos(contaId, "1.0");
        var auth = AutenticacaoTeste.como(TesteSeguranca.usuario(contaId, UUID.randomUUID(), null, PapelObra.CLIENTE, false, false));

        mvc.perform(get("/api/v1/me/consentimentos").with(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].estado").value("NAO_CONCEDIDO"))
                .andExpect(jsonPath("$[0].ativo").value(false));

        mvc.perform(put("/api/v1/me/consentimentos/MARKETING").with(auth).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"concedido\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONCEDIDO"))
                .andExpect(jsonPath("$.ativo").value(true))
                .andExpect(jsonPath("$.versaoTexto").value("1.0"));

        mvc.perform(put("/api/v1/me/consentimentos/MARKETING").with(auth).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"concedido\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("REVOGADO"))
                .andExpect(jsonPath("$.ativo").value(false));

        mvc.perform(get("/api/v1/me/consentimentos/historico").with(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].acao").value("REVOGAR"))
                .andExpect(jsonPath("$[1].acao").value("CONCEDER"));
    }

    @Test
    void novaVersaoDosTermosBloqueiaAteOReaceite() throws Exception {
        var contaId = fixtures.conta("reaceite-" + UUID.randomUUID() + "@teste.dev", "ACTIVE");
        var auth = AutenticacaoTeste.como(TesteSeguranca.usuario(contaId, UUID.randomUUID(), null, PapelObra.CLIENTE, false, false));

        // sem nenhum aceite registrado → tudo fora de /auth, /legal e /me/legal responde 403 reaceite_pendente
        mvc.perform(get("/api/v1/me/consentimentos").with(auth))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("https://civilconstructorai.com.br/problemas/reaceite_pendente"))
                .andExpect(jsonPath("$.reaceitePendente.TERMOS").value("1.0"))
                .andExpect(jsonPath("$.reaceitePendente.POLITICA").value("1.0"));

        mvc.perform(get("/api/v1/legal/documentos")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)));
        mvc.perform(get("/api/v1/me/legal/pendencias").with(auth)).andExpect(status().isOk()).andExpect(jsonPath("$.TERMOS").value("1.0"));

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/me/legal/reaceite").with(auth).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"documento\":\"TERMOS\",\"versao\":\"0.9\"}"))
                .andExpect(status().isConflict());
        for (var tipo : new String[]{"TERMOS", "POLITICA"}) {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/me/legal/reaceite").with(auth).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"documento\":\"" + tipo + "\",\"versao\":\"1.0\"}"))
                    .andExpect(status().isNoContent());
        }
        mvc.perform(get("/api/v1/me/consentimentos").with(auth)).andExpect(status().isOk());
    }
}
