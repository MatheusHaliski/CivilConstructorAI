package br.com.civilconstructorai.ia;

import br.com.civilconstructorai.comum.EntidadeTenant;
import br.com.civilconstructorai.comum.MaquinaDeEstados;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Pedido de revisão humana de decisão automatizada (LGPD Art. 20) com prazo de 15 dias. */
@Entity
@Table(name = "pedido_revisao_humana")
public class PedidoRevisaoHumana extends EntidadeTenant {

    public static final Duration PRAZO = Duration.ofDays(15);

    public enum Estado {
        ABERTO, EM_ANALISE, RESPONDIDO;

        public static final MaquinaDeEstados<Estado> MAQUINA = new MaquinaDeEstados<>("PedidoRevisaoHumana", Map.of(
                ABERTO, Set.of(EM_ANALISE, RESPONDIDO),
                EM_ANALISE, Set.of(RESPONDIDO),
                RESPONDIDO, Set.of()));
    }

    @Column(name = "rascunho_id", nullable = false, updatable = false)
    private UUID rascunhoId;

    @Column(name = "solicitante_id", nullable = false, updatable = false)
    private UUID solicitanteId;

    @Column(nullable = false, columnDefinition = "text")
    private String motivo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "text")
    private Estado estado = Estado.ABERTO;

    @Column(name = "prazo_em", nullable = false)
    private Instant prazoEm;

    @Column(columnDefinition = "text")
    private String resposta;

    @Column(name = "respondido_por")
    private UUID respondidoPor;

    @Column(name = "respondido_em")
    private Instant respondidoEm;

    protected PedidoRevisaoHumana() {
    }

    public PedidoRevisaoHumana(UUID tenantId, UUID rascunhoId, UUID solicitanteId, String motivo, Instant agora) {
        super(tenantId);
        this.rascunhoId = rascunhoId;
        this.solicitanteId = solicitanteId;
        this.motivo = motivo;
        this.prazoEm = agora.plus(PRAZO);
    }

    public void responder(UUID por, String resposta, Instant agora) {
        estado = Estado.MAQUINA.transicionar(estado, Estado.RESPONDIDO);
        this.respondidoPor = por;
        this.resposta = resposta;
        this.respondidoEm = agora;
    }

    public UUID getRascunhoId() { return rascunhoId; }
    public UUID getSolicitanteId() { return solicitanteId; }
    public String getMotivo() { return motivo; }
    public Estado getEstado() { return estado; }
    public Instant getPrazoEm() { return prazoEm; }
    public String getResposta() { return resposta; }
    public Instant getRespondidoEm() { return respondidoEm; }
}
