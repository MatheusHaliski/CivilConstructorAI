package br.com.civilconstructorai.prancha;

import br.com.civilconstructorai.acesso.PapelObra;
import br.com.civilconstructorai.arquivo.ArmazenamentoArquivos;
import br.com.civilconstructorai.comum.UsuarioAtual;
import br.com.civilconstructorai.modelo.AcessoObraTeste;
import br.com.civilconstructorai.modelo.ComoUsuario;
import br.com.civilconstructorai.modelo.ObraDeTeste;
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
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** RF14: solicitar → job gera SVG/DXF determinísticos → download pré-assinado → emissão por profissional habilitado. */
@AutoConfigureMockMvc
@Import(AcessoObraTeste.class)
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
class PranchaIT extends AbstractIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager tm;
    @Autowired ObjectMapper mapper;
    @Autowired ArmazenamentoArquivos arquivos;

    private void comando(String base, UsuarioAtual u, String corpo) throws Exception {
        mvc.perform(post(base + "/modelo/comandos").with(ComoUsuario.como(u)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(corpo)).andExpect(status().isCreated());
    }

    @Test
    void geraEmiteEBloqueiaFormasParaArquiteto() throws Exception {
        var c = ObraDeTeste.criar(jdbc, tm);
        var eng = ObraDeTeste.membro(jdbc, tm, c, PapelObra.ENGENHEIRO, true, true);
        var engSemMfa = ObraDeTeste.membro(jdbc, tm, c, PapelObra.ENGENHEIRO, false, true);
        var arq = ObraDeTeste.membro(jdbc, tm, c, PapelObra.ARQUITETO, true, true);
        var base = "/api/v1/obras/" + c.obraId();

        comando(base, eng, "{\"codigo\":\"MALHA\",\"args\":{\"nx\":2,\"dx\":5,\"ny\":2,\"dy\":4}}");
        comando(base, eng, "{\"codigo\":\"PAREDE\",\"args\":{\"p1\":{\"x\":0,\"y\":0},\"p2\":{\"x\":5,\"y\":0}}}");
        // envoltória aberta: prancha reprovada (RF16 CA-09)
        mvc.perform(post(base + "/pranchas").with(ComoUsuario.como(eng)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tipo\":\"PLANTA_BAIXA\"}")).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.relatorio.laco2.violacoes[0].codigo").value("ENVOLTORIA_ABERTA"));
        comando(base, eng, "{\"codigo\":\"PAREDE\",\"args\":{\"p1\":{\"x\":5,\"y\":0},\"p2\":{\"x\":5,\"y\":4}}}");
        comando(base, eng, "{\"codigo\":\"PAREDE\",\"args\":{\"p1\":{\"x\":5,\"y\":4},\"p2\":{\"x\":0,\"y\":4}}}");
        comando(base, eng, "{\"codigo\":\"PAREDE\",\"args\":{\"p1\":{\"x\":0,\"y\":4},\"p2\":{\"x\":0,\"y\":0}}}");
        comando(base, eng, "{\"codigo\":\"PILAR\",\"args\":{\"centro\":{\"x\":0,\"y\":0}}}");
        comando(base, eng, "{\"codigo\":\"PILAR\",\"args\":{\"centro\":{\"x\":5,\"y\":0}}}");
        comando(base, eng, "{\"codigo\":\"VIGA\",\"args\":{\"p1\":{\"x\":0,\"y\":0},\"p2\":{\"x\":5,\"y\":0}}}");
        comando(base, eng, "{\"codigo\":\"TEXTO\",\"args\":{\"p\":{\"x\":2,\"y\":2},\"texto\":\"Sala\"}}");

        var solicitada = mvc.perform(post(base + "/pranchas").with(ComoUsuario.como(eng)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tipo\":\"PLANTA_BAIXA\"}")).andExpect(status().isAccepted()).andReturn();
        var id = mapper.readTree(solicitada.getResponse().getContentAsByteArray()).get("id").asString();

        // arquiteto não pede planta de formas
        mvc.perform(post(base + "/pranchas").with(ComoUsuario.como(arq)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tipo\":\"PLANTA_FORMAS\"}")).andExpect(status().isForbidden());

        mvc.perform(post(base + "/pranchas/processar").with(ComoUsuario.como(eng)).with(csrf())).andExpect(status().isOk());
        var gerada = mvc.perform(get(base + "/pranchas/" + id).with(ComoUsuario.como(eng))).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("GERADA")).andReturn();
        var chaveSvg = mapper.readTree(gerada.getResponse().getContentAsByteArray()).get("chaveSvg").asString();
        var svg = new String(arquivos.ler(chaveSvg).orElseThrow(), StandardCharsets.UTF_8);
        assertThat(svg).startsWith("<svg").contains("RASCUNHO").contains("id=\"EIXOS\"").contains("id=\"COTAS\"").contains("Sala").contains(">500<");
        var dxf = new String(arquivos.ler(chaveSvg.replace(".svg", ".dxf")).orElseThrow(), StandardCharsets.UTF_8);
        assertThat(dxf).contains("EIXOS").contains("COTAS").contains("CORTADO").contains("VISTA").contains("OCULTO").contains("TEXTO").endsWith("EOF\n");

        mvc.perform(get(base + "/pranchas/" + id + "/download").with(ComoUsuario.como(eng))).andExpect(status().isOk()).andExpect(jsonPath("$.svg").exists());

        // emitir: sem 2FA → 403; com selo + 2FA + ART → EMITIDA e sem marca d'água
        mvc.perform(post(base + "/pranchas/" + id + "/emitir").with(ComoUsuario.como(engSemMfa)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"artRrt\":\"ART 123\"}")).andExpect(status().isForbidden());
        mvc.perform(post(base + "/pranchas/" + id + "/emitir").with(ComoUsuario.como(eng)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"artRrt\":\"ART 123456\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("EMITIDA"));
        assertThat(new String(arquivos.ler(chaveSvg).orElseThrow(), StandardCharsets.UTF_8)).doesNotContain("RASCUNHO").contains("ART 123456");
    }
}
