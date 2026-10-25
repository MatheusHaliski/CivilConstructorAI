package br.com.civilconstructorai.lgpd;

import br.com.civilconstructorai.comum.ContextoTenant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Job diário (03:15 por padrão): elimina contas cuja carência terminou. Reexecutável: a saga é idempotente. */
@Component
public class EliminacaoJob {

    private static final Logger log = LoggerFactory.getLogger(EliminacaoJob.class);
    private final SagaEliminacaoService saga;

    public EliminacaoJob(SagaEliminacaoService saga) {
        this.saga = saga;
    }

    @Scheduled(cron = "${app.lgpd.eliminacao-cron:0 15 3 * * *}")
    public void executar() {
        try {
            ContextoTenant.comoSistema(() -> {
                for (var contaId : saga.contasVencidas()) {
                    var s = saga.executar(contaId);
                    log.info("saga de eliminação {} → {} (passo {})", s.getId(), s.getEstado(), s.getPassoAtual());
                }
                return null;
            });
        } catch (Exception e) {
            log.warn("job de eliminação: {}", e.getMessage());
        }
    }
}
