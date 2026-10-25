package br.com.civilconstructorai.lgpd;

import br.com.civilconstructorai.acesso.PapelObra;
import br.com.civilconstructorai.arquivo.ArmazenamentoArquivos;
import br.com.civilconstructorai.comum.ContextoTenant;
import br.com.civilconstructorai.comum.UsuarioAtual;
import br.com.civilconstructorai.support.AbstractIT;
import br.com.civilconstructorai.support.TesteSeguranca;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** RF25 · exportação: sudo → 202, job → PRONTA, senha exibida uma vez, pacote decifra e contém export.json. */
@AutoConfigureMockMvc
class ExportacaoIT extends AbstractIT {

    @Autowired MockMvc mvc;
    @Autowired LgpdFixtures fixtures;
    @Autowired ExportacaoService exportacoes;
    @Autowired ArmazenamentoArquivos arquivos;
    @Autowired ObjectMapper mapper;

    @Test
    void fluxoCompletoDaExportacao() throws Exception {
        var contaId = fixtures.conta("export-" + UUID.randomUUID() + "@teste.dev", "ACTIVE");
        fixtures.aceitarDocumentos(contaId, "1.0");
        var empresaId = UUID.randomUUID();
        var semSudo = AutenticacaoTeste.como(TesteSeguranca.usuario(contaId, empresaId, null, PapelObra.CLIENTE, true, false));
        var comSudo = AutenticacaoTeste.como(new UsuarioAtual(contaId, empresaId, Optional.empty(), Map.of(), true, false, true));

        mvc.perform(post("/api/v1/me/exportacoes").with(semSudo).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("https://civilconstructorai.com.br/problemas/sudo_required"));

        var corpo = mvc.perform(post("/api/v1/me/exportacoes").with(comSudo).with(csrf()))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        var id = UUID.fromString(mapper.readTree(corpo).get("id").asString());

        mvc.perform(get("/api/v1/me/exportacoes/" + id).with(comSudo))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("PENDENTE"));

        // o job roda como sistema
        ContextoTenant.comoSistema(() -> { exportacoes.processar(id); return null; });

        var pronta = mvc.perform(get("/api/v1/me/exportacoes/" + id).with(comSudo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONCLUIDO"))
                .andExpect(jsonPath("$.urlDownload").isNotEmpty())
                .andExpect(jsonPath("$.senhaZip").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        var senha = mapper.readTree(pronta).get("senhaZip").asString();

        // segunda consulta: a senha não é mostrada de novo (fica só o hash)
        mvc.perform(get("/api/v1/me/exportacoes/" + id).with(comSudo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.senhaZip").doesNotExist());

        var chave = "exports/" + contaId + "/" + id + ".zip.cca";
        var pacote = arquivos.ler(chave).orElseThrow();
        var zip = CifraExportacao.decifrar(pacote, senha);
        try (var zin = new ZipInputStream(new ByteArrayInputStream(zip))) {
            var entrada = zin.getNextEntry();
            assertThat(entrada.getName()).isEqualTo("export.json");
            var json = mapper.readTree(new String(zin.readAllBytes(), StandardCharsets.UTF_8));
            assertThat(json.get("schema").asString()).isEqualTo("cca-export/v1");
            assertThat(json.get("categorias").has("consentimentos")).isTrue();
        }
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> CifraExportacao.decifrar(pacote, "senha-errada")).isInstanceOf(IllegalStateException.class);
    }
}
