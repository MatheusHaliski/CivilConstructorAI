package br.com.civilconstructorai.lgpd;

import br.com.civilconstructorai.comum.ContextoTenant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Processa exportações pendentes (fila SKIP LOCKED simplificada: uma por transação) e expira as vencidas. */
@Component
public class ExportacaoJob {

    private static final Logger log = LoggerFactory.getLogger(ExportacaoJob.class);
    private final ExportacaoService service;

    public ExportacaoJob(ExportacaoService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${app.lgpd.exportacao-intervalo-ms:15000}", initialDelayString = "${app.lgpd.exportacao-intervalo-ms:15000}")
    public void executar() {
        try {
            ContextoTenant.comoSistema(() -> {
                for (var id : service.pendentes()) {
                    service.processar(id);
                }
                service.expirarVencidas();
                return null;
            });
        } catch (Exception e) {
            log.warn("job de exportação: {}", e.getMessage());
        }
    }
}
