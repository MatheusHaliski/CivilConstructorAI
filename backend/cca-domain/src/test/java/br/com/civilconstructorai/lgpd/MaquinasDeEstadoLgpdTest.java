package br.com.civilconstructorai.lgpd;

import br.com.civilconstructorai.comum.ExcecaoDominio;
import br.com.civilconstructorai.ia.EstadoRascunhoIa;
import br.com.civilconstructorai.ia.RascunhoIa;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MaquinasDeEstadoLgpdTest {

    @Test
    void consentimentoConcedeRevogaEReconfirma() {
        var c = new ConsentimentoAtual(UUID.randomUUID(), Finalidade.MARKETING, "1.0");
        assertThat(c.getEstado()).isEqualTo(EstadoConsentimento.NAO_CONCEDIDO);
        c.transicionar(EstadoConsentimento.CONCEDIDO, "1.0", Instant.now());
        c.transicionar(EstadoConsentimento.A_CONFIRMAR, "1.0", Instant.now());
        c.transicionar(EstadoConsentimento.CONCEDIDO, "1.1", Instant.now());
        c.transicionar(EstadoConsentimento.REVOGADO, "1.1", Instant.now());
        c.transicionar(EstadoConsentimento.ENCERRADO, "1.1", Instant.now());
        assertThatThrownBy(() -> c.transicionar(EstadoConsentimento.CONCEDIDO, "1.1", Instant.now()))
                .isInstanceOf(ExcecaoDominio.TransicaoInvalida.class);
    }

    @Test
    void retencaoNaoFicaAbaixoDoMinimoLegal() {
        var tenant = UUID.randomUUID();
        assertThatThrownBy(() -> new RetencaoEmpresa(tenant, CategoriaDado.SAUDE_OCUPACIONAL, 120))
                .isInstanceOf(ExcecaoDominio.Validacao.class)
                .hasMessageContaining("1 problema");
        var ok = new RetencaoEmpresa(tenant, CategoriaDado.DOCUMENTOS_TRABALHISTAS, 72);
        assertThat(ok.getPrazoMeses()).isEqualTo(72);
        assertThat(CategoriaDado.SAUDE_OCUPACIONAL.minimoMeses()).isEqualTo(240);
        assertThat(CategoriaDado.CONTATO_OPERACIONAL.minimoMeses()).isEqualTo(3);
        assertThat(CategoriaDado.SAUDE_OCUPACIONAL.sensivel()).isTrue();
    }

    @Test
    void sagaRetomaDoPassoQueFalhou() {
        var s = new SagaEliminacao(UUID.randomUUID(), "hash");
        s.executando();
        s.passoConcluido(1);
        s.passoConcluido(2);
        s.falhou("passo 3: boom");
        assertThat(s.getEstado()).isEqualTo(SagaEliminacao.Estado.FALHOU_PASSO);
        assertThat(s.getPassoAtual()).isEqualTo(3);
        s.executando();
        assertThatThrownBy(() -> s.passoConcluido(1)).isInstanceOf(IllegalStateException.class);
        s.passoConcluido(3);
        s.passoConcluido(4);
        s.passoConcluido(5);
        s.concluir(Instant.now());
        assertThat(s.getEstado()).isEqualTo(SagaEliminacao.Estado.CONCLUIDA);
        assertThatThrownBy(s::executando).isInstanceOf(ExcecaoDominio.TransicaoInvalida.class);
    }

    @Test
    void rascunhoIaSoValeDepoisDeAprovadoComArt() {
        var r = new RascunhoIa(UUID.randomUUID(), UUID.randomUUID(), "ORCAMENTO", "associacao-sinapi", "orc-1", "{}", UUID.randomUUID(), null, false);
        assertThat(r.getEstado()).isEqualTo(EstadoRascunhoIa.GERADO_POR_IA);
        r.enviarParaRevisao();
        assertThatThrownBy(() -> r.aprovar(UUID.randomUUID(), " ", Instant.now())).isInstanceOf(ExcecaoDominio.Validacao.class);
        r.aprovar(UUID.randomUUID(), "ART 123", Instant.now());
        assertThat(r.getEstado()).isEqualTo(EstadoRascunhoIa.APROVADO);
        r.obsoletar("modelo mudou");
        assertThatThrownBy(() -> r.rejeitar(UUID.randomUUID(), "x")).isInstanceOf(ExcecaoDominio.TransicaoInvalida.class);
        var recusado = new RascunhoIa(UUID.randomUUID(), UUID.randomUUID(), "X", "comando-vi", null, "{}", null, null, true);
        assertThat(recusado.getEstado()).isEqualTo(EstadoRascunhoIa.RECUSADO_PELO_MODELO);
        assertThat(EstadoRascunhoIa.MAQUINA.terminal(EstadoRascunhoIa.RECUSADO_PELO_MODELO)).isTrue();
    }

    @Test
    void pedidoDoTitularFicaAtrasadoDepoisDoPrazo() {
        var agora = Instant.parse("2026-10-01T12:00:00Z");
        var p = new PedidoTitular(UUID.randomUUID(), null, PedidoTitular.Tipo.ACESSO, "CONTA", "quero ver", agora);
        assertThat(p.getPrazoEm()).isEqualTo(agora.plus(PedidoTitular.PRAZO));
        assertThat(p.marcarAtrasadoSe(agora.plusSeconds(3600))).isFalse();
        assertThat(p.marcarAtrasadoSe(agora.plus(PedidoTitular.PRAZO).plusSeconds(1))).isTrue();
        p.transicionar(PedidoTitular.Estado.ATENDIDO, UUID.randomUUID(), "pronto", agora);
        assertThat(p.getEstado().aberto()).isFalse();
    }

    @Test
    void versoesDeDocumentoComparamNumericamente() {
        assertThat(VersaoDocumento.desatualizada("1.0", "1.1")).isTrue();
        assertThat(VersaoDocumento.desatualizada("1.10", "1.9")).isFalse();
        assertThat(VersaoDocumento.desatualizada(null, "1.0")).isTrue();
        assertThat(VersaoDocumento.desatualizada("2.0", "2.0")).isFalse();
    }
}
