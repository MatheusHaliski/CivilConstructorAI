package br.com.civilconstructorai.lgpd;

import br.com.civilconstructorai.comum.ContextoTenant;
import br.com.civilconstructorai.lgpd.Portas.ChavesUsuario;
import br.com.civilconstructorai.lgpd.Portas.CofreChaves;
import br.com.civilconstructorai.lgpd.Portas.RegistroAtividades;
import br.com.civilconstructorai.support.AbstractIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;

/**
 * RF26 · saga idempotente: falha injetada no passo 3 → FALHOU_PASSO; a próxima execução conclui sem repetir
 * os passos 1–2. Crypto-shredding: mesmo "restaurando" a linha de user_key (backup), o dado não decifra.
 */
class EliminacaoIT extends AbstractIT {

    @Autowired LgpdFixtures fixtures;
    @Autowired SagaEliminacaoService saga;
    @Autowired ChavesUsuario chaves;
    @Autowired CofreChaves cofre;
    @Autowired ContaLgpdDao contas;
    @Autowired Repositorios.SagaEliminacaoRepository sagas;
    @Autowired Repositorios.ReciboEliminacaoRepository recibos;
    @Autowired Repositorios.RetencaoLegalRepository retencoes;
    @MockitoBean RegistroAtividades atividades;

    @Test
    void sagaFalhaNoPasso3RetomaEDestroiAChave() throws Exception {
        var contaId = fixtures.conta("elim-" + UUID.randomUUID() + "@teste.dev", "ACTIVE");
        fixtures.aceitarDocumentos(contaId, "1.0");
        var obra = new UUID[1];
        fixtures.empresaComObra(contaId, obra);
        chaves.criarParaConta(contaId);
        var segredo = chaves.cifrar(contaId, "CPF 000.000.000-00".getBytes(StandardCharsets.UTF_8));
        assertThat(new String(chaves.decifrar(contaId, segredo), StandardCharsets.UTF_8)).startsWith("CPF");

        // cópia da linha de user_key, simulando um backup do banco principal tirado antes da eliminação
        var backup = fixtures.jdbc().queryForMap("select usuario_id, kek_id, versao_chave, dek_cifrada_pela_kek from user_key where usuario_id = ?", contaId);

        contas.solicitarExclusao(contaId, Instant.now().minusSeconds(60));
        assertThat(saga.contasVencidas()).contains(contaId);

        doThrow(new IllegalStateException("Cassandra fora do ar")).when(atividades).limparUsuario(any());
        var primeira = ContextoTenant.comoSistema(() -> saga.executar(contaId));
        assertThat(primeira.getEstado()).isEqualTo(SagaEliminacao.Estado.FALHOU_PASSO);
        assertThat(primeira.getPassoAtual()).isEqualTo(SagaEliminacao.PASSO_CASSANDRA);
        // passo 1 já foi efetivado (transação própria): pessoa anonimizada, retenção legal registrada
        assertThat(fixtures.nomeDaConta(contaId)).isEqualTo("Titular removido");
        assertThat(retencoes.findBySagaId(primeira.getId())).anyMatch(r -> r.getEntidade().equals("obra.responsavel_tecnico"));
        // chave ainda existe: o passo 5 não rodou
        assertThat(chaves.existe(contaId)).isTrue();

        doNothing().when(atividades).limparUsuario(any());
        var segunda = ContextoTenant.comoSistema(() -> saga.executar(contaId));
        assertThat(segunda.getEstado()).isEqualTo(SagaEliminacao.Estado.CONCLUIDA);
        assertThat(segunda.getTentativas()).isEqualTo(2);
        assertThat(fixtures.estadoDaConta(contaId)).isEqualTo("ANONYMOUS_LGPD_DELETED");
        assertThat(recibos.findBySagaId(segunda.getId())).isPresent();
        assertThat(recibos.findBySagaId(segunda.getId()).get().getPassos()).isEqualTo("[1,2,3,4,5,6]");
        assertThat(chaves.existe(contaId)).isFalse();
        assertThat(cofre.revogada((UUID) backup.get("kek_id"))).isTrue();

        // "restaura o backup" da user_key: a DEK volta, mas a KEK foi destruída/revogada → ilegível
        fixtures.jdbc().update("insert into user_key (usuario_id, kek_id, versao_chave, dek_cifrada_pela_kek) values (?, ?, ?, ?)",
                backup.get("usuario_id"), backup.get("kek_id"), backup.get("versao_chave"), backup.get("dek_cifrada_pela_kek"));
        assertThatThrownBy(() -> chaves.decifrar(contaId, segredo)).isInstanceOf(IllegalStateException.class).hasMessageContaining("revogada");

        // idempotência: executar de novo não muda nada
        var terceira = ContextoTenant.comoSistema(() -> saga.executar(contaId));
        assertThat(terceira.getEstado()).isEqualTo(SagaEliminacao.Estado.CONCLUIDA);
        assertThat(terceira.getTentativas()).isEqualTo(2);
    }
}
