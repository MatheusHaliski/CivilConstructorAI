package br.com.civilconstructorai.lgpd;

import br.com.civilconstructorai.comum.EntidadeBase;
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

/** Pedido do titular (Art. 18) com prazo de 15 dias (Art. 18 §5º). {@code tenantId} nulo = sobre dados da conta. */
@Entity
@Table(name = "pedido_titular")
public class PedidoTitular extends EntidadeBase {

    public static final Duration PRAZO = Duration.ofDays(15);

    public enum Tipo { ACESSO, CORRECAO, EXCLUSAO, PORTABILIDADE, INFORMACAO_COMPARTILHAMENTO, REVOGACAO, OPOSICAO }

    public enum Estado {
        ABERTO, EM_ANALISE, AGUARDANDO_TITULAR, ATENDIDO, NEGADO, ATRASADO;

        public static final MaquinaDeEstados<Estado> MAQUINA = new MaquinaDeEstados<>("PedidoTitular", Map.of(
                ABERTO, Set.of(EM_ANALISE, ATENDIDO, NEGADO, ATRASADO),
                EM_ANALISE, Set.of(AGUARDANDO_TITULAR, ATENDIDO, NEGADO, ATRASADO),
                AGUARDANDO_TITULAR, Set.of(EM_ANALISE, ATENDIDO, NEGADO, ATRASADO),
                ATRASADO, Set.of(EM_ANALISE, ATENDIDO, NEGADO),
                ATENDIDO, Set.of(),
                NEGADO, Set.of()));

        public boolean aberto() {
            return this != ATENDIDO && this != NEGADO;
        }
    }

    @Column(name = "conta_id", nullable = false, updatable = false)
    private UUID contaId;

    @Column(name = "tenant_id", updatable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "text")
    private Tipo tipo;

    @Column(nullable = false, columnDefinition = "text")
    private String categoria;

    @Column(nullable = false, columnDefinition = "text")
    private String descricao;

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

    protected PedidoTitular() {
    }

    public PedidoTitular(UUID contaId, UUID tenantId, Tipo tipo, String categoria, String descricao, Instant agora) {
        this.contaId = contaId;
        this.tenantId = tenantId;
        this.tipo = tipo;
        this.categoria = categoria;
        this.descricao = descricao;
        this.prazoEm = agora.plus(PRAZO);
    }

    public void transicionar(Estado para, UUID por, String resposta, Instant agora) {
        this.estado = Estado.MAQUINA.transicionar(this.estado, para);
        if (!para.aberto()) {
            this.respondidoPor = por;
            this.respondidoEm = agora;
            this.resposta = resposta;
        } else if (resposta != null) {
            this.resposta = resposta;
        }
    }

    /** chamado pelo job: passado o prazo sem resposta → ATRASADO */
    public boolean marcarAtrasadoSe(Instant agora) {
        if (estado.aberto() && estado != Estado.ATRASADO && agora.isAfter(prazoEm)) {
            estado = Estado.ATRASADO;
            return true;
        }
        return false;
    }

    public UUID getContaId() { return contaId; }
    public UUID getTenantId() { return tenantId; }
    public Tipo getTipo() { return tipo; }
    public String getCategoria() { return categoria; }
    public String getDescricao() { return descricao; }
    public Estado getEstado() { return estado; }
    public Instant getPrazoEm() { return prazoEm; }
    public String getResposta() { return resposta; }
    public Instant getRespondidoEm() { return respondidoEm; }
}
