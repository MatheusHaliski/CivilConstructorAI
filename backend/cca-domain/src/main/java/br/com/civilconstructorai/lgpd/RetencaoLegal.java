package br.com.civilconstructorai.lgpd;

import br.com.civilconstructorai.comum.EntidadeBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** O que a saga manteve por obrigação legal, com a base legal registrada (Art. 16, I). */
@Entity
@Table(name = "retencao_legal")
public class RetencaoLegal extends EntidadeBase {

    @Column(name = "saga_id", nullable = false, updatable = false)
    private UUID sagaId;

    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String entidade;

    @Column(name = "registro_id", nullable = false, updatable = false)
    private UUID registroId;

    @Column(name = "base_legal", nullable = false, updatable = false, columnDefinition = "text")
    private String baseLegal;

    @Column(name = "prazo_ate")
    private Instant prazoAte;

    @Column(columnDefinition = "text")
    private String dados;

    protected RetencaoLegal() {
    }

    public RetencaoLegal(UUID sagaId, String entidade, UUID registroId, String baseLegal, Instant prazoAte, String dados) {
        this.sagaId = sagaId;
        this.entidade = entidade;
        this.registroId = registroId;
        this.baseLegal = baseLegal;
        this.prazoAte = prazoAte;
        this.dados = dados;
    }

    public UUID getSagaId() { return sagaId; }
    public String getEntidade() { return entidade; }
    public UUID getRegistroId() { return registroId; }
    public String getBaseLegal() { return baseLegal; }
    public Instant getPrazoAte() { return prazoAte; }
    public String getDados() { return dados; }
}
