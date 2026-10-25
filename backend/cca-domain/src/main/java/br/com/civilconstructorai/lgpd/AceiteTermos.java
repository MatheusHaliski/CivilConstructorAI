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

/** Aceite registrado com versão, data e IP (hash) — Art. 7º, V e Art. 8º. */
@Entity
@Table(name = "aceite_termos")
public class AceiteTermos {

    @Id
    private UUID id = Ids.novo();

    @Column(name = "conta_id", nullable = false, updatable = false)
    private UUID contaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private TipoDocumentoLegal tipo;

    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String versao;

    @Column(name = "aceito_em", nullable = false, updatable = false)
    private Instant aceitoEm;

    @Column(name = "ip_hash", nullable = false, updatable = false, columnDefinition = "text")
    private String ipHash;

    @Column(name = "user_agent", columnDefinition = "text")
    private String userAgent;

    protected AceiteTermos() {
    }

    public AceiteTermos(UUID contaId, TipoDocumentoLegal tipo, String versao, Instant aceitoEm, String ipHash, String userAgent) {
        this.contaId = contaId;
        this.tipo = tipo;
        this.versao = versao;
        this.aceitoEm = aceitoEm;
        this.ipHash = ipHash;
        this.userAgent = userAgent == null ? null : userAgent.substring(0, Math.min(200, userAgent.length()));
    }

    public UUID getId() { return id; }
    public UUID getContaId() { return contaId; }
    public TipoDocumentoLegal getTipo() { return tipo; }
    public String getVersao() { return versao; }
    public Instant getAceitoEm() { return aceitoEm; }
}
