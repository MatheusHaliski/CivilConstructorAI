package br.com.civilconstructorai.lgpd;

import br.com.civilconstructorai.comum.Relogio;
import br.com.civilconstructorai.lgpd.Repositorios.SagaEliminacaoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Orquestra a saga idempotente de eliminação (RF26): 1 anonimizar PostgreSQL (mantendo o legal em
 * {@code retencao_legal}) · 2 remover S3 depois do commit do passo 1 · 3 limpar Cassandra · 4 limpar Valkey ·
 * 5 destruir KEK + {@code user_key} (crypto-shredding) · 6 recibo + estado terminal. Falha num passo grava
 * FALHOU_PASSO e o passo; a próxima execução retoma dali sem repetir os anteriores.
 */
@Service
public class SagaEliminacaoService {

    private static final Logger log = LoggerFactory.getLogger(SagaEliminacaoService.class);

    private final PassosEliminacao passos;
    private final SagaEliminacaoRepository sagas;
    private final ContaLgpdDao contas;
    private final Relogio relogio;

    public SagaEliminacaoService(PassosEliminacao passos, SagaEliminacaoRepository sagas, ContaLgpdDao contas, Relogio relogio) {
        this.passos = passos;
        this.sagas = sagas;
        this.contas = contas;
        this.relogio = relogio;
    }

    /** executa (ou retoma) a saga de uma conta; devolve o estado ao fim desta execução */
    public SagaEliminacao executar(UUID contaId) {
        var saga = passos.iniciarOuRetomar(contaId);
        if (saga.getEstado() == SagaEliminacao.Estado.CONCLUIDA) {
            return saga;
        }
        for (int passo = saga.getPassoAtual(); passo <= SagaEliminacao.PASSO_RECIBO; passo++) {
            try {
                passos.executarPasso(saga.getId(), passo);
            } catch (RuntimeException e) {
                log.warn("saga {} falhou no passo {}: {}", saga.getId(), passo, e.getClass().getSimpleName());
                return passos.marcarFalha(saga.getId(), passo, e);
            }
        }
        return sagas.findById(saga.getId()).orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<UUID> contasVencidas() {
        return contas.contasParaEliminar(relogio.agora());
    }

    @Transactional(readOnly = true)
    public SagaEliminacao estado(UUID contaId) {
        return sagas.findByContaId(contaId).orElse(null);
    }
}
