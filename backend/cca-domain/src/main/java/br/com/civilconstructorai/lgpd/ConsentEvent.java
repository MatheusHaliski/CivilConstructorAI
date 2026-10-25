package br.com.civilconstructorai.lgpd;

import br.com.civilconstructorai.comum.Ids;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Evento imutável de consentimento (Art. 8º §5º): finalidade, ação, versão do texto, data e IP (hash). */
@Entity
@Table(name = "consent_event")
public class ConsentEvent {

    public enum Acao { CONCEDER, REVOGAR, CONFIRMAR, ENCERRAR }

    @Id
    private UUID id = Ids.novo();

    @Column(name = "conta_id", nullable = false, updatable = false)
    private UUID contaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private Finalidade finalidade;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private Acao acao;

    @Column(name = "versao_texto", nullable = false, updatable = false, columnDefinition = "text")
    private String versaoTexto;

    @Column(name = "ocorrido_em", nullable = false, updatable = false)
    private Instant ocorridoEm;

    @Column(name = "ip_hash", nullable = false, updatable = false, columnDefinition = "text")
    private String ipHash;

    protected ConsentEvent() {
    }

    public ConsentEvent(UUID contaId, Finalidade finalidade, Acao acao, String versaoTexto, Instant ocorridoEm, String ipHash) {
        this.contaId = contaId;
        this.finalidade = finalidade;
        this.acao = acao;
        this.versaoTexto = versaoTexto;
        this.ocorridoEm = ocorridoEm;
        this.ipHash = ipHash;
    }

    public UUID getId() { return id; }
    public UUID getContaId() { return contaId; }
    public Finalidade getFinalidade() { return finalidade; }
    public Acao getAcao() { return acao; }
    public String getVersaoTexto() { return versaoTexto; }
    public Instant getOcorridoEm() { return ocorridoEm; }
}
