package br.com.civilconstructorai.ia;

import br.com.civilconstructorai.comum.EntidadeTenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Registro de uma chamada ao modelo: só metadados (rota, tokens, stop_reason) — nunca o conteúdo. */
@Entity
@Table(name = "chamada_ia")
public class ChamadaIa extends EntidadeTenant {

    @Column(name = "obra_id", updatable = false)
    private UUID obraId;

    @Column(name = "usuario_id", updatable = false)
    private UUID usuarioId;

    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String rota;

    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String finalidade;

    @Column(name = "base_legal", nullable = false, updatable = false, columnDefinition = "text")
    private String baseLegal;

    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String modelo;

    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String effort;

    @Column(name = "input_tokens", nullable = false)
    private long inputTokens;

    @Column(name = "output_tokens", nullable = false)
    private long outputTokens;

    @Column(name = "cache_read_input_tokens", nullable = false)
    private long cacheReadInputTokens;

    @Column(name = "cache_creation_input_tokens", nullable = false)
    private long cacheCreationInputTokens;

    @Column(name = "stop_reason", columnDefinition = "text")
    private String stopReason;

    @Column(nullable = false)
    private boolean recusa;

    @Column(name = "latencia_ms", nullable = false)
    private long latenciaMs;

    @Column(name = "ocorrido_em", nullable = false, updatable = false)
    private Instant ocorridoEm;

    protected ChamadaIa() {
    }

    public ChamadaIa(UUID tenantId, UUID obraId, UUID usuarioId, String rota, String finalidade, String baseLegal, String modelo, String effort,
                     long inputTokens, long outputTokens, long cacheRead, long cacheCreation, String stopReason, boolean recusa, long latenciaMs, Instant ocorridoEm) {
        super(tenantId);
        this.obraId = obraId;
        this.usuarioId = usuarioId;
        this.rota = rota;
        this.finalidade = finalidade;
        this.baseLegal = baseLegal;
        this.modelo = modelo;
        this.effort = effort;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.cacheReadInputTokens = cacheRead;
        this.cacheCreationInputTokens = cacheCreation;
        this.stopReason = stopReason;
        this.recusa = recusa;
        this.latenciaMs = latenciaMs;
        this.ocorridoEm = ocorridoEm;
    }

    public UUID getObraId() { return obraId; }
    public UUID getUsuarioId() { return usuarioId; }
    public String getRota() { return rota; }
    public String getFinalidade() { return finalidade; }
    public String getBaseLegal() { return baseLegal; }
    public String getModelo() { return modelo; }
    public String getEffort() { return effort; }
    public long getInputTokens() { return inputTokens; }
    public long getOutputTokens() { return outputTokens; }
    public long getCacheReadInputTokens() { return cacheReadInputTokens; }
    public long getCacheCreationInputTokens() { return cacheCreationInputTokens; }
    public String getStopReason() { return stopReason; }
    public boolean isRecusa() { return recusa; }
    public long getLatenciaMs() { return latenciaMs; }
    public Instant getOcorridoEm() { return ocorridoEm; }
}
