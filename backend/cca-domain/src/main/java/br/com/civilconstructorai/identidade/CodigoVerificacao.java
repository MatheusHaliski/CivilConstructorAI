package br.com.civilconstructorai.identidade;

import br.com.civilconstructorai.comum.EntidadeBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Código numérico enviado por e-mail (verificação, recuperação de senha, sudo): 30 min, 5 tentativas, uso único. */
@Entity
@Table(name = "codigo_verificacao")
public class CodigoVerificacao extends EntidadeBase {

    public static final int MAX_TENTATIVAS = 5;

    public enum Finalidade { EMAIL, RECUPERACAO_SENHA, SUDO }

    @Column(name = "conta_id", nullable = false)
    private UUID contaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Finalidade finalidade;

    @Column(name = "codigo_hash", nullable = false)
    private String codigoHash;

    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    @Column(nullable = false)
    private int tentativas;

    @Column(name = "usado_em")
    private Instant usadoEm;

    protected CodigoVerificacao() {
    }

    public CodigoVerificacao(UUID contaId, Finalidade finalidade, String codigoHash, Instant expiraEm) {
        this.contaId = contaId;
        this.finalidade = finalidade;
        this.codigoHash = codigoHash;
        this.expiraEm = expiraEm;
    }

    public boolean expirado(Instant agora) {
        return agora.isAfter(expiraEm);
    }

    public boolean usado() {
        return usadoEm != null;
    }

    public boolean esgotado() {
        return tentativas >= MAX_TENTATIVAS;
    }

    public int registrarTentativa() {
        tentativas++;
        return MAX_TENTATIVAS - tentativas;
    }

    public void marcarUsado(Instant agora) {
        this.usadoEm = agora;
    }

    public UUID getContaId() { return contaId; }
    public Finalidade getFinalidade() { return finalidade; }
    public String getCodigoHash() { return codigoHash; }
    public Instant getExpiraEm() { return expiraEm; }
    public int getTentativas() { return tentativas; }
}
