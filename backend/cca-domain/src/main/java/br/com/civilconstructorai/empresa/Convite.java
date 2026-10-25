package br.com.civilconstructorai.empresa;

import br.com.civilconstructorai.acesso.PapelObra;
import br.com.civilconstructorai.comum.EntidadeTenant;
import br.com.civilconstructorai.comum.ExcecaoDominio;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * Convite para empresa (DONO/ADMIN, sem obra) ou para obra (demais papéis). Expira em 7 dias, uso único,
 * token guardado só como hash. Nunca promove acima do papel de quem convidou.
 */
@Entity
@Table(name = "convite")
public class Convite extends EntidadeTenant {

    public static final Duration VALIDADE = Duration.ofDays(7);

    @Column(name = "obra_id")
    private UUID obraId;

    @Column(nullable = false, columnDefinition = "citext")
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PapelObra papel;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "convidado_por", nullable = false)
    private UUID convidadoPor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoConvite estado = EstadoConvite.PENDENTE;

    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    @Column(name = "aceito_por")
    private UUID aceitoPor;

    @Column(name = "aceito_em")
    private Instant aceitoEm;

    protected Convite() {
    }

    private Convite(UUID empresaId) {
        super(empresaId);
    }

    public static Convite novo(UUID empresaId, UUID obraId, String email, PapelObra papel, UUID convidadoPor, PapelObra papelDoConvidante, String tokenHash, Instant agora) {
        // RF20 CA-04: ninguém entra como DONO por convite (antes um DONO conseguia convidar outro DONO)
        if (papel == PapelObra.DONO) {
            throw new ExcecaoDominio.AcessoNegado("convite_dono", "Ninguém entra como dono por convite; a posse da empresa não se transfere por aqui.");
        }
        if (!papelDoConvidante.podeConvidar(papel)) {
            throw new ExcecaoDominio.AcessoNegado("convite_acima_do_convidante", "Um convite nunca dá um papel acima do seu (" + papelDoConvidante.rotulo() + ").");
        }
        if (papel.escopoEmpresa() && obraId != null) {
            throw new ExcecaoDominio.Validacao("obraId", "Convite para DONO/ADMIN é de empresa, sem obra.");
        }
        if (!papel.escopoEmpresa() && obraId == null) {
            throw new ExcecaoDominio.Validacao("obraId", "Convite para papel de obra precisa da obra.");
        }
        var c = new Convite(empresaId);
        c.obraId = obraId;
        c.email = email.trim().toLowerCase(Locale.ROOT);
        c.papel = papel;
        c.convidadoPor = convidadoPor;
        c.tokenHash = tokenHash;
        c.expiraEm = agora.plus(VALIDADE);
        return c;
    }

    public boolean expirado(Instant agora) {
        return agora.isAfter(expiraEm);
    }

    /** aceito por quem tem o e-mail do convite; expirado/usado/revogado → 410 na camada web */
    public void aceitar(UUID contaId, String emailDaConta, Instant agora) {
        if (expirado(agora)) {
            throw new ExcecaoDominio.RegraDeNegocio("convite_expirado", "Este convite expirou. Peça um novo.");
        }
        if (estado != EstadoConvite.PENDENTE) {
            throw new ExcecaoDominio.RegraDeNegocio("convite_indisponivel", "Este convite já foi usado ou revogado.");
        }
        if (!email.equalsIgnoreCase(emailDaConta.trim())) {
            throw new ExcecaoDominio.AcessoNegado("Este convite foi enviado para outro e-mail. Entre com a conta certa.");
        }
        estado = EstadoConvite.MAQUINA.transicionar(estado, EstadoConvite.ACEITO);
        aceitoPor = contaId;
        aceitoEm = agora;
    }

    public void recusar(Instant agora) {
        if (expirado(agora) || estado != EstadoConvite.PENDENTE) {
            throw new ExcecaoDominio.RegraDeNegocio("convite_indisponivel", "Este convite não está mais disponível.");
        }
        estado = EstadoConvite.MAQUINA.transicionar(estado, EstadoConvite.RECUSADO);
    }

    public void revogar() {
        estado = EstadoConvite.MAQUINA.transicionar(estado, EstadoConvite.REVOGADO);
    }

    public void renovarToken(String novoTokenHash, Instant agora) {
        if (expirado(agora) || estado != EstadoConvite.PENDENTE) {
            throw new ExcecaoDominio.RegraDeNegocio("convite_indisponivel", "Só convites pendentes e dentro do prazo podem ser reenviados.");
        }
        this.tokenHash = novoTokenHash;
    }

    public UUID getObraId() { return obraId; }
    public String getEmail() { return email; }
    public PapelObra getPapel() { return papel; }
    public String getTokenHash() { return tokenHash; }
    public UUID getConvidadoPor() { return convidadoPor; }
    public EstadoConvite getEstado() { return estado; }
    public Instant getExpiraEm() { return expiraEm; }
    public UUID getAceitoPor() { return aceitoPor; }
}
