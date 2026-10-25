package br.com.civilconstructorai.orcamento;

import br.com.civilconstructorai.comum.EntidadeTenant;
import br.com.civilconstructorai.comum.ExcecaoDominio;
import br.com.civilconstructorai.comum.MaquinaDeEstados;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Aditivo ao orçamento aprovado: proposto pela empresa, aprovado pelo CLIENTE (DN-02). */
@Entity
@Table(name = "aditivo")
public class Aditivo extends EntidadeTenant {

    public enum Estado {
        PROPOSTO, APROVADO_CLIENTE, RECUSADO_CLIENTE, CANCELADO;

        public static final MaquinaDeEstados<Estado> MAQUINA = new MaquinaDeEstados<>("Aditivo", Map.of(
                PROPOSTO, Set.of(APROVADO_CLIENTE, RECUSADO_CLIENTE, CANCELADO),
                APROVADO_CLIENTE, Set.of(), RECUSADO_CLIENTE, Set.of(), CANCELADO, Set.of()));
    }

    @Column(name = "orcamento_id", nullable = false)
    private UUID orcamentoId;

    @Column(nullable = false)
    private String descricao;

    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal valor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Estado estado = Estado.PROPOSTO;

    @Column(name = "proposto_por", nullable = false)
    private UUID propostoPor;

    @Column(name = "decidido_por")
    private UUID decididoPor;

    @Column(name = "decidido_em")
    private Instant decididoEm;

    protected Aditivo() {
    }

    public Aditivo(UUID tenantId, UUID orcamentoId, String descricao, BigDecimal valor, UUID propostoPor) {
        super(tenantId);
        if (descricao == null || descricao.isBlank()) {
            throw new ExcecaoDominio.Validacao("descricao", "Descreva o aditivo.");
        }
        if (valor == null || valor.signum() == 0) {
            throw new ExcecaoDominio.Validacao("valor", "Informe o valor do aditivo (positivo ou negativo).");
        }
        this.orcamentoId = orcamentoId;
        this.descricao = descricao;
        this.valor = valor;
        this.propostoPor = propostoPor;
    }

    public void decidirCliente(boolean aprovar, UUID cliente, Instant quando) {
        estado = Estado.MAQUINA.transicionar(estado, aprovar ? Estado.APROVADO_CLIENTE : Estado.RECUSADO_CLIENTE);
        decididoPor = cliente;
        decididoEm = quando;
    }

    public UUID getOrcamentoId() { return orcamentoId; }
    public String getDescricao() { return descricao; }
    public BigDecimal getValor() { return valor; }
    public Estado getEstado() { return estado; }
    public UUID getPropostoPor() { return propostoPor; }
    public UUID getDecididoPor() { return decididoPor; }
    public Instant getDecididoEm() { return decididoEm; }
}
