package br.com.civilconstructorai.lgpd;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Projeção do estado atual; a história imutável fica em {@link ConsentEvent}. */
@Entity
@Table(name = "consentimento_atual")
public class ConsentimentoAtual {

    @EmbeddedId
    private Chave chave;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "text")
    private EstadoConsentimento estado = EstadoConsentimento.NAO_CONCEDIDO;

    @Column(name = "versao_texto", nullable = false, columnDefinition = "text")
    private String versaoTexto;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm = Instant.now();

    protected ConsentimentoAtual() {
    }

    public ConsentimentoAtual(UUID contaId, Finalidade finalidade, String versaoTexto) {
        this.chave = new Chave(contaId, finalidade);
        this.versaoTexto = versaoTexto;
    }

    public void transicionar(EstadoConsentimento para, String versaoTexto, Instant quando) {
        this.estado = EstadoConsentimento.MAQUINA.transicionar(this.estado, para);
        this.versaoTexto = versaoTexto;
        this.atualizadoEm = quando;
    }

    public UUID contaId() { return chave.contaId; }
    public Finalidade finalidade() { return chave.finalidade; }
    public EstadoConsentimento getEstado() { return estado; }
    public String getVersaoTexto() { return versaoTexto; }
    public Instant getAtualizadoEm() { return atualizadoEm; }

    @Embeddable
    public static class Chave implements Serializable {
        @Column(name = "conta_id", nullable = false)
        private UUID contaId;
        @Enumerated(EnumType.STRING)
        @Column(nullable = false, columnDefinition = "text")
        private Finalidade finalidade;

        protected Chave() {
        }

        public Chave(UUID contaId, Finalidade finalidade) {
            this.contaId = contaId;
            this.finalidade = finalidade;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Chave c && contaId.equals(c.contaId) && finalidade == c.finalidade;
        }

        @Override
        public int hashCode() {
            return Objects.hash(contaId, finalidade);
        }
    }
}
