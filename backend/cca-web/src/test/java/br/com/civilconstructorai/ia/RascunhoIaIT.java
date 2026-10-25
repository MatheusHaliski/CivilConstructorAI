package br.com.civilconstructorai.ia;

import br.com.civilconstructorai.acesso.PapelObra;
import br.com.civilconstructorai.comum.ContextoTenant;
import br.com.civilconstructorai.lgpd.AutenticacaoTeste;
import br.com.civilconstructorai.lgpd.LgpdFixtures;
import br.com.civilconstructorai.support.AbstractIT;
import br.com.civilconstructorai.support.TesteSeguranca;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** RF27 · rascunho gerado por IA: aprovar exige papel + selo + 2FA + ART; outra empresa → 404; chamada registrada. */
@AutoConfigureMockMvc
class RascunhoIaIT extends AbstractIT {

    @Autowired MockMvc mvc;
    @Autowired LgpdFixtures fixtures;
    @Autowired RascunhoIaService rascunhos;
    @Autowired ServicoIa servicoIa;
    @Autowired UsoIaService uso;

    @Test
    void cicloDoRascunhoEControleDeAprovacao() throws Exception {
        var engId = fixtures.conta("eng-" + UUID.randomUUID() + "@teste.dev", "ACTIVE");
        fixtures.aceitarDocumentos(engId, "1.0");
        var obra = new UUID[1];
        var empresaId = fixtures.empresaComObra(engId, obra);
        var obraId = obra[0];
        fixtures.membroObra(empresaId, obraId, engId, "ENGENHEIRO");

        // IA (fake determinística) → ServicoIa registra a chamada → rascunho GERADO_POR_IA
        var ctx = new ServicoIa.Contexto(empresaId, obraId, engId);
        var r = ContextoTenant.comTenant(empresaId, () -> {
            var res = servicoIa.gerarEstruturado(RotaIa.COMANDO_VI, "fixo", "faça uma parede de 5 m", "{\"type\":\"object\"}", List.of("comandos"), ctx);
            assertThat(res.completa()).isTrue();
            return rascunhos.criar(empresaId, obraId, "COMANDO_VI", RotaIa.COMANDO_VI, "parede-1", res.jsonBruto(), engId, res.chamadaId(), false);
        });

        var semSelo = AutenticacaoTeste.como(TesteSeguranca.usuario(engId, empresaId, obraId, PapelObra.ENGENHEIRO, true, false));
        var completo = AutenticacaoTeste.como(TesteSeguranca.usuario(engId, empresaId, obraId, PapelObra.ENGENHEIRO, true, true));
        // engenheira de outra empresa, com os termos aceitos (senão o filtro de reaceite responderia 403 antes)
        var outraId = fixtures.conta("eng2-" + UUID.randomUUID() + "@teste.dev", "ACTIVE");
        fixtures.aceitarDocumentos(outraId, "1.0");
        var outraEmpresa = AutenticacaoTeste.como(TesteSeguranca.usuario(outraId, UUID.randomUUID(), UUID.randomUUID(), PapelObra.ENGENHEIRO, true, true));

        mvc.perform(get("/api/v1/obras/" + obraId + "/rascunhos-ia").with(completo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].estado").value("GERADO_POR_IA"))
                .andExpect(jsonPath("$[0].geradoPorIa").value(true));

        mvc.perform(get("/api/v1/obras/" + obraId + "/rascunhos-ia").with(outraEmpresa)).andExpect(status().isNotFound());

        mvc.perform(post("/api/v1/obras/" + obraId + "/rascunhos-ia/" + r.getId() + "/aprovar").with(semSelo).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"artRrt\":\"ART 1\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("CREA/CAU")));

        mvc.perform(post("/api/v1/obras/" + obraId + "/rascunhos-ia/" + r.getId() + "/aprovar").with(completo).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"artRrt\":\"  \"}"))
                .andExpect(status().isUnprocessableContent());

        mvc.perform(post("/api/v1/obras/" + obraId + "/rascunhos-ia/" + r.getId() + "/aprovar").with(completo).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"artRrt\":\"ART 123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APROVADO"))
                .andExpect(jsonPath("$.artRrt").value("ART 123456"));

        mvc.perform(post("/api/v1/obras/" + obraId + "/rascunhos-ia/" + r.getId() + "/revisao-humana").with(completo).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"quero entender a decisão\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("ABERTO"))
                .andExpect(jsonPath("$.prazoEm").isNotEmpty());

        mvc.perform(get("/api/v1/ia/uso").with(completo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[0].modelo").value("claude-opus-5-5"));

        var dono = AutenticacaoTeste.como(TesteSeguranca.usuario(engId, empresaId, null, PapelObra.DONO, true, true));
        mvc.perform(get("/api/v1/empresas/" + empresaId + "/ia/consumo").with(dono))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rota").value("comando-vi"))
                .andExpect(jsonPath("$[0].chamadas").value(1));
    }
}
