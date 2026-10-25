package br.com.civilconstructorai.estrutura.lancamento;

import br.com.civilconstructorai.comum.EntidadeTenant;
import br.com.civilconstructorai.estrutura.EstadoRascunho;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Sugestão de lançamento gerada pela IA (rota lancamento-estrutural): rascunho até aprovação (RF04, RF27). */
@Entity
@Table(name = "rascunho_lancamento")
public class RascunhoLancamento extends EntidadeTenant {

    @Column(name = "obra_id", nullable = false)
    private UUID obraId;

    @Column(name = "pavimento_id", nullable = false)
    private String pavimentoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoRascunho estado = EstadoRascunho.GERADO_POR_IA;

    /** JSON {pilares[], vigas[], lajes[]} devolvido pela IA e validado */
    @Column(name = "conteudo_json", nullable = false)
    private String conteudoJson;

    @Column(name = "modelo_ia", nullable = false)
    private String modeloIa;

    @Column(name = "input_tokens", nullable = false)
    private long inputTokens;

    @Column(name = "output_tokens", nullable = false)
    private long outputTokens;

    @Column(name = "stop_reason", nullable = false)
    private String stopReason;

    @Column(name = "aprovado_por")
    private UUID aprovadoPor;

    @Column(name = "decidido_em")
    private Instant decididoEm;

    @Column(name = "motivo_rejeicao")
    private String motivoRejeicao;

    protected RascunhoLancamento() {
    }

    public RascunhoLancamento(UUID tenantId, UUID obraId, String pavimentoId, String conteudoJson, String modeloIa, long inputTokens, long outputTokens, String stopReason) {
        super(tenantId);
        this.obraId = obraId;
        this.pavimentoId = pavimentoId;
        this.conteudoJson = conteudoJson;
        this.modeloIa = modeloIa;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.stopReason = stopReason;
    }

    public void iniciarRevisao() {
        estado = EstadoRascunho.MAQUINA.transicionar(estado, EstadoRascunho.EM_REVISAO);
    }

    public void aprovar(UUID aprovador, Instant quando) {
        if (estado == EstadoRascunho.GERADO_POR_IA) {
            iniciarRevisao();
        }
        estado = EstadoRascunho.MAQUINA.transicionar(estado, EstadoRascunho.APROVADO);
        aprovadoPor = aprovador;
        decididoEm = quando;
    }

    public void rejeitar(UUID quem, Instant quando, String motivo) {
        if (estado == EstadoRascunho.GERADO_POR_IA) {
            iniciarRevisao();
        }
        estado = EstadoRascunho.MAQUINA.transicionar(estado, EstadoRascunho.REJEITADO);
        aprovadoPor = quem;
        decididoEm = quando;
        motivoRejeicao = motivo;
    }

    public void tornarObsoleto() {
        if (!EstadoRascunho.MAQUINA.terminal(estado)) {
            estado = EstadoRascunho.MAQUINA.transicionar(estado, EstadoRascunho.OBSOLETO);
        }
    }

    public UUID getObraId() { return obraId; }
    public String getPavimentoId() { return pavimentoId; }
    public EstadoRascunho getEstado() { return estado; }
    public String getConteudoJson() { return conteudoJson; }
    public String getModeloIa() { return modeloIa; }
    public long getInputTokens() { return inputTokens; }
    public long getOutputTokens() { return outputTokens; }
    public String getStopReason() { return stopReason; }
    public UUID getAprovadoPor() { return aprovadoPor; }
    public Instant getDecididoEm() { return decididoEm; }
    public String getMotivoRejeicao() { return motivoRejeicao; }
}
