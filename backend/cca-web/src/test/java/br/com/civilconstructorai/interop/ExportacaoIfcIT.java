package br.com.civilconstructorai.interop;

import br.com.civilconstructorai.acesso.PapelObra;
import br.com.civilconstructorai.arquivo.ArmazenamentoArquivos;
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
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** RF15: exportação IFC 4.3 válida, ida e volta pelo leitor, importação de referência e pareamento Revit. */
@AutoConfigureMockMvc
class ExportacaoIfcIT extends AbstractIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager tm;
    @Autowired ObjectMapper mapper;
    @Autowired ArmazenamentoArquivos arquivos;

    private void comando(String base, UsuarioAtual u, String corpo) throws Exception {
        mvc.perform(post(base + "/modelo/comandos").with(ComoUsuario.como(u)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(corpo)).andExpect(status().isCreated());
    }

    @Test
    void exportaIfcIdaEVoltaEPareiaRevit() throws Exception {
        var c = ObraDeTeste.criar(jdbc, tm);
        var eng = ObraDeTeste.membro(jdbc, tm, c, PapelObra.ENGENHEIRO, true, true);
        var base = "/api/v1/obras/" + c.obraId();
        comando(base, eng, "{\"codigo\":\"PAREDE\",\"args\":{\"p1\":{\"x\":0,\"y\":0},\"p2\":{\"x\":5,\"y\":0}}}");
        comando(base, eng, "{\"codigo\":\"PAREDE\",\"args\":{\"p1\":{\"x\":5,\"y\":0},\"p2\":{\"x\":5,\"y\":4}}}");
        comando(base, eng, "{\"codigo\":\"PAREDE\",\"args\":{\"p1\":{\"x\":5,\"y\":4},\"p2\":{\"x\":0,\"y\":4}}}");
        comando(base, eng, "{\"codigo\":\"PAREDE\",\"args\":{\"p1\":{\"x\":0,\"y\":4},\"p2\":{\"x\":0,\"y\":0}}}");
        comando(base, eng, "{\"codigo\":\"PILAR\",\"args\":{\"centro\":{\"x\":0,\"y\":0}}}");
        comando(base, eng, "{\"codigo\":\"LAJE\",\"args\":{\"contorno\":[{\"x\":0,\"y\":0},{\"x\":5,\"y\":0},{\"x\":5,\"y\":4},{\"x\":0,\"y\":4}],\"h\":0.12}}");

        var solicitada = mvc.perform(post(base + "/exportacoes").with(ComoUsuario.como(eng)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"formato\":\"IFC4X3\"}")).andExpect(status().isAccepted()).andReturn();
        var id = mapper.readTree(solicitada.getResponse().getContentAsByteArray()).get("id").asString();
        mvc.perform(post(base + "/exportacoes/processar").with(ComoUsuario.como(eng)).with(csrf())).andExpect(status().isOk());
        var pronta = mvc.perform(get(base + "/exportacoes/" + id).with(ComoUsuario.como(eng))).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONCLUIDO")).andReturn();
        var chave = mapper.readTree(pronta.getResponse().getContentAsByteArray()).get("chave").asString();
        var ifc = arquivos.ler(chave).orElseThrow();
        var resumo = LeitorIfc.ler(ifc);
        assertThat(resumo.valido()).as(resumo.problemas().toString()).isTrue();
        assertThat(resumo.porTipo()).containsEntry("IFCWALL", 4).containsEntry("IFCCOLUMN", 1).containsEntry("IFCSLAB", 1).containsEntry("IFCBUILDINGSTOREY", 1);
        mvc.perform(get(base + "/exportacoes/" + id + "/download").with(ComoUsuario.como(eng))).andExpect(status().isOk()).andExpect(jsonPath("$.url").exists());

        // ida e volta: importar o próprio IFC como referência
        mvc.perform(post(base + "/importacoes/ifc").with(ComoUsuario.como(eng)).with(csrf()).contentType(MediaType.APPLICATION_OCTET_STREAM).content(ifc))
                .andExpect(status().isOk()).andExpect(jsonPath("$.valido").value(true)).andExpect(jsonPath("$.porTipo.IFCWALL").value(4));

        // pareamento Revit (fase 2): código de uso único → token de dispositivo
        var codigoResp = mvc.perform(post(base + "/revit/pareamentos").with(ComoUsuario.como(eng)).with(csrf())).andExpect(status().isCreated()).andReturn();
        var codigo = mapper.readTree(codigoResp.getResponse().getContentAsByteArray()).get("codigo").asString();
        assertThat(codigo).hasSize(6);
        var token = mvc.perform(post("/api/v1/revit/parear").with(ComoUsuario.como(eng)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"codigo\":\"" + codigo + "\",\"dispositivo\":\"Revit 2026\"}")).andExpect(status().isOk()).andReturn();
        var tk = mapper.readTree(token.getResponse().getContentAsByteArray()).get("token").asString();
        // segundo uso do mesmo código falha
        mvc.perform(post("/api/v1/revit/parear").with(ComoUsuario.como(eng)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"codigo\":\"" + codigo + "\"}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/revit/comandos?desde=0").header("X-Revit-Token", tk).with(ComoUsuario.como(eng))).andExpect(status().isOk())
                .andExpect(jsonPath("$.comandos.length()").value(6));
    }
}
