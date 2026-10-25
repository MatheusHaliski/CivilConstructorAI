package br.com.civilconstructorai.lgpd;

import br.com.civilconstructorai.comum.EntidadeBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

/** Recibo da eliminação: hash da conta, passos executados e bases legais do que ficou — sem conteúdo pessoal. */
@Entity
@Table(name = "recibo_eliminacao")
public class ReciboEliminacao extends EntidadeBase {

    @Column(name = "saga_id", nullable = false, updatable = false)
    private UUID sagaId;

    @Column(name = "conta_hash", nullable = false, updatable = false, columnDefinition = "text")
    private String contaHash;

    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String passos;

    @Column(name = "bases_legais", nullable = false, updatable = false, columnDefinition = "text")
    private String basesLegais;

    protected ReciboEliminacao() {
    }

    public ReciboEliminacao(UUID sagaId, String contaHash, String passos, String basesLegais) {
        this.sagaId = sagaId;
        this.contaHash = contaHash;
        this.passos = passos;
        this.basesLegais = basesLegais;
    }

    public UUID getSagaId() { return sagaId; }
    public String getContaHash() { return contaHash; }
    public String getPassos() { return passos; }
    public String getBasesLegais() { return basesLegais; }
}
