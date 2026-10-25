package br.com.civilconstructorai.ia;

import br.com.civilconstructorai.comum.ExcecaoDominio;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServicoIaTest {

    private final Repositorios.ChamadaIaRepository chamadas = mock(Repositorios.ChamadaIaRepository.class);
    private final ServicoIa.Contexto ctx = new ServicoIa.Contexto(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

    private ServicoIa servico(ModeloLinguagem.Resposta resposta) {
        when(chamadas.save(any())).thenAnswer(inv -> inv.getArgument(0));
        ModeloLinguagem modelo = new ModeloLinguagem() {
            @Override
            public Resposta gerarEstruturado(RotaIa rota, String s, String e, String schema, Map<String, String> c) {
                return resposta;
            }

            @Override
            public Resposta responderComCitacoes(RotaIa rota, String s, byte[] pdf, String pergunta) {
                return resposta;
            }
        };
        return new ServicoIa(modelo, chamadas, new ObjectMapper(), () -> Instant.parse("2026-10-25T00:00:00Z"));
    }

    @Test
    void registraAChamadaSemConteudoEValidaCamposObrigatorios() {
        var s = servico(new ModeloLinguagem.Resposta("{\"comandos\":[]}", "end_turn", false, 100, 20, 90, 0, 12, "claude-opus-5-5"));
        var r = s.gerarEstruturado(RotaIa.COMANDO_VI, "fixo", "variável", "{}", List.of("comandos"), ctx);
        assertThat(r.completa()).isTrue();
        assertThat(r.json().get("comandos").isArray()).isTrue();
        var captor = ArgumentCaptor.forClass(ChamadaIa.class);
        verify(chamadas).save(captor.capture());
        var c = captor.getValue();
        assertThat(c.getRota()).isEqualTo("comando-vi");
        assertThat(c.getEffort()).isEqualTo("medium");
        assertThat(c.getCacheReadInputTokens()).isEqualTo(90);
        assertThat(c.getTenantId()).isEqualTo(ctx.tenantId());
        assertThat(c.getBaseLegal()).contains("Art. 7º");
    }

    @Test
    void recusaDoModeloNaoViraExcecaoMasResultadoRecusado() {
        var s = servico(new ModeloLinguagem.Resposta("", "refusal", true, 10, 0, 0, 0, 3, "claude-opus-5-5"));
        var r = s.gerarEstruturado(RotaIa.ASSOCIACAO_SINAPI, "f", "v", "{}", List.of("associacoes"), ctx);
        assertThat(r.recusada()).isTrue();
        assertThat(r.json()).isNull();
    }

    @Test
    void saidaSemCampoObrigatorioOuTruncadaEhRejeitada() {
        assertThatThrownBy(() -> servico(new ModeloLinguagem.Resposta("{\"outro\":1}", "end_turn", false, 1, 1, 0, 0, 1, "m"))
                .gerarEstruturado(RotaIa.COMANDO_VI, "f", "v", "{}", List.of("comandos"), ctx))
                .isInstanceOf(ExcecaoDominio.RegraDeNegocio.class).hasMessageContaining("comandos");
        assertThatThrownBy(() -> servico(new ModeloLinguagem.Resposta("{", "max_tokens", false, 1, 1, 0, 0, 1, "m"))
                .gerarEstruturado(RotaIa.COMANDO_VI, "f", "v", "{}", List.of(), ctx))
                .isInstanceOf(ExcecaoDominio.RegraDeNegocio.class).hasMessageContaining("truncada");
    }
}
