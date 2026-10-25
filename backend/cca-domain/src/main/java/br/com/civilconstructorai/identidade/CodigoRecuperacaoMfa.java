package br.com.civilconstructorai.identidade;

import br.com.civilconstructorai.comum.EntidadeBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Um dos 10 códigos de recuperação do 2FA: guardado só como hash, uso único. */
@Entity
@Table(name = "codigo_recuperacao_mfa")
public class CodigoRecuperacaoMfa extends EntidadeBase {

    @Column(name = "conta_id", nullable = false)
    private UUID contaId;

    @Column(name = "codigo_hash", nullable = false)
    private String codigoHash;

    @Column(name = "usado_em")
    private Instant usadoEm;

    protected CodigoRecuperacaoMfa() {
    }

    public CodigoRecuperacaoMfa(UUID contaId, String codigoHash) {
        this.contaId = contaId;
        this.codigoHash = codigoHash;
    }

    public boolean disponivel() {
        return usadoEm == null;
    }

    public void usar(Instant agora) {
        this.usadoEm = agora;
    }

    public UUID getContaId() { return contaId; }
    public String getCodigoHash() { return codigoHash; }
}
