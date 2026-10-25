package br.com.civilconstructorai.modelo;

import br.com.civilconstructorai.acesso.PapelObra;
import br.com.civilconstructorai.comum.ContextoTenant;
import br.com.civilconstructorai.comum.UsuarioAtual;
import br.com.civilconstructorai.ia.ModeloLinguagem;
import br.com.civilconstructorai.ia.RotaIa;
import br.com.civilconstructorai.support.AbstractIT;
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
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Auditoria 2026-10, caminho da IA (texto natural → comandos VI → auditor → rascunho): falha do provedor, resposta
 * fora do esquema, comando não permitido e vínculo do rascunho com a versão do modelo e com a chamada registrada.
 */
@AutoConfigureMockMvc
@Import(CaminhoIaIT.IaProgramavel.class)
class CaminhoIaIT extends AbstractIT {

    static final AtomicReference<Supplier<ModeloLinguagem.Resposta>> PROXIMA = new AtomicReference<>();

    static ModeloLinguagem.Resposta json(String j) {
        return new ModeloLinguagem.Resposta(j, "end_turn", false, 100, 30, 0, 0, 5, "teste");
    }

    @TestConfiguration
    static class IaProgramavel {
        @Bean
        @Primary
        ModeloLinguagem iaProgramavel() {
            return new ModeloLinguagem() {
                @Override
                public Resposta gerarEstruturado(RotaIa rota, String system, String entrada, String schema, Map<String, String> contexto) {
                    return PROXIMA.get() == null ? json("{\"comandos\":[]}") : PROXIMA.get().get();
                }

                @Override
                public Resposta responderComCitacoes(RotaIa rota, String system, byte[] pdf, String pergunta) {
                    return json("{\"resposta\":\"\",\"citacoes\":[]}");
                }
            };
        }
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager tm;
    @Autowired ObjectMapper mapper;

    ObraDeTeste.Cenario cenario;
    UsuarioAtual engenheiro;

    @BeforeEach
    void cenario() {
        cenario = ObraDeTeste.criar(jdbc, tm);
        engenheiro = ObraDeTeste.membro(jdbc, tm, cenario, PapelObra.ENGENHEIRO, true, true);
        PROXIMA.set(null);
    }

    private MvcResult texto(String t) throws Exception {
        return mvc.perform(post("/api/v1/obras/" + cenario.obraId() + "/modelo/texto-natural").with(ComoUsuario.como(engenheiro)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("texto", t)))).andReturn();
    }

    private long versao() throws Exception {
        var r = mvc.perform(get("/api/v1/obras/" + cenario.obraId() + "/modelo").with(ComoUsuario.como(engenheiro))).andReturn();
        return mapper.readTree(r.getResponse().getContentAsByteArray()).get("versao").asLong();
    }

    private Long contar(String sql) {
        try {
            return ContextoTenant.comoSistema(() -> new TransactionTemplate(tm).execute(st -> jdbc.queryForObject(sql, Long.class, cenario.obraId())));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void falhaDoProvedorDevolve503ENaoAlteraOModelo() throws Exception {
        long antes = versao();
        PROXIMA.set(() -> { throw new IllegalStateException("timeout de rede"); });
        var r = texto("crie uma parede de 5 m");
        assertThat(r.getResponse().getStatus()).isEqualTo(503);
        assertThat(r.getResponse().getContentAsString()).contains("ia_indisponivel").contains("Nada foi alterado");
        assertThat(versao()).isEqualTo(antes);
    }

    @Test
    void respostaForaDoEsquemaEhRecusada() throws Exception {
        long antes = versao();
        PROXIMA.set(() -> json("{\"comandos\":[{\"codigo\":\"PAREDE\",\"args\":\"p1=0,0\"}]}"));
        var r = texto("crie uma parede");
        assertThat(r.getResponse().getStatus()).isEqualTo(422);
        assertThat(r.getResponse().getContentAsString()).contains("ia_resposta_invalida");
        assertThat(versao()).isEqualTo(antes);
    }

    @Test
    void comandoNaoPermitidoParaIaNaoEhExecutado() throws Exception {
        long antes = versao();
        PROXIMA.set(() -> json("{\"comandos\":[{\"codigo\":\"PAREDE\",\"args\":{\"p1\":[0,0],\"p2\":[5,0]}},{\"codigo\":\"APAGAR\",\"args\":{\"ids\":[\"x\"]}}]}"));
        var r = texto("apague tudo e crie uma parede");
        assertThat(r.getResponse().getStatus()).isEqualTo(422);
        assertThat(r.getResponse().getContentAsString()).contains("ia_comando_nao_permitido").contains("APAGAR");
        assertThat(versao()).as("nem a parede válida do mesmo lote é aplicada").isEqualTo(antes);
    }

    @Test
    void rascunhoGuardaVersoesDoModeloEAChamadaRegistrada() throws Exception {
        long antes = versao();
        PROXIMA.set(() -> json("{\"comandos\":[{\"codigo\":\"PAREDE\",\"args\":{\"p1\":[0,0],\"p2\":[5,0]}}]}"));
        var r = texto("crie uma parede de 5 m");
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(201);
        String conteudo = ContextoTenant.comoSistema(() -> new TransactionTemplate(tm).execute(st ->
                jdbc.queryForObject("select conteudo from rascunho_ia where obra_id = ? order by criado_em desc limit 1", String.class, cenario.obraId())));
        var c = mapper.readTree(conteudo);
        assertThat(c.get("versaoModeloBase").asLong()).isEqualTo(antes);
        assertThat(c.get("versaoModeloResultado").asLong()).isEqualTo(antes + 1);
        assertThat(contar("select count(*) from rascunho_ia where obra_id = ? and chamada_ia_id is not null")).isEqualTo(1L);
        assertThat(contar("select count(*) from chamada_ia where obra_id = ? and rota = 'comando-vi'")).isEqualTo(1L);
    }
}
