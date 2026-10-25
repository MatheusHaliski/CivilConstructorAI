package br.com.civilconstructorai.coordenacao;

import br.com.civilconstructorai.comum.EntidadeTenant;
import br.com.civilconstructorai.comum.ExcecaoDominio;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Conflito de coordenação persistido por obra e chave estável. Estados: ABERTO → EM_REVISAO → (RESOLVIDO | ACEITO).
 * RESOLVIDO só é atribuído pela reverificação, quando o conflito deixa de ser detectado numa versão posterior do
 * modelo; ACEITO exige justificativa de quem pode aprovar. Um conflito resolvido que volta a aparecer é reaberto.
 */
@Entity
@Table(name = "conflito_coordenacao")
public class ConflitoCoordenacao extends EntidadeTenant {

    public enum Estado { ABERTO, EM_REVISAO, RESOLVIDO, ACEITO }

    @Column(name = "obra_id", nullable = false)
    private UUID obraId;

    @Column(nullable = false)
    private String chave;

    @Column(nullable = false)
    private String tipo;

    @Column(nullable = false)
    private String regra;

    @Column(name = "elementos_json", nullable = false)
    private String elementosJson;

    @Column(nullable = false)
    private String mensagem;

    @Column(nullable = false)
    private String evidencia;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Estado estado = Estado.ABERTO;

    @Column
    private String justificativa;

    @Column(name = "versao_detectada", nullable = false)
    private long versaoDetectada;

    @Column(name = "versao_verificada", nullable = false)
    private long versaoVerificada;

    @Column(name = "decidido_por")
    private UUID decididoPor;

    @Column(name = "decidido_em")
    private Instant decididoEm;

    protected ConflitoCoordenacao() {
    }

    public ConflitoCoordenacao(UUID tenantId, UUID obraId, DetectorConflitos.Conflito c, String elementosJson, long versaoModelo) {
        super(tenantId);
        this.obraId = obraId;
        this.chave = c.chave();
        this.tipo = c.tipo().name();
        this.regra = c.regra();
        this.elementosJson = elementosJson;
        this.versaoDetectada = versaoModelo;
        atualizarDeteccao(c, versaoModelo);
    }

    /** o conflito foi detectado de novo na versão dada */
    public void atualizarDeteccao(DetectorConflitos.Conflito c, long versaoModelo) {
        this.mensagem = c.mensagem();
        this.evidencia = c.evidencia();
        this.versaoVerificada = versaoModelo;
        if (estado == Estado.RESOLVIDO) {
            estado = Estado.ABERTO;
            justificativa = "reaberto: detectado de novo na versão " + versaoModelo;
            decididoPor = null;
            decididoEm = null;
        }
    }

    /** a reverificação na versão dada não encontrou o conflito */
    public void naoDetectado(long versaoModelo, Instant agora) {
        if (estado == Estado.ABERTO || estado == Estado.EM_REVISAO) {
            estado = Estado.RESOLVIDO;
            justificativa = "não detectado na reverificação da versão " + versaoModelo;
            decididoPor = null;
            decididoEm = agora;
        }
        versaoVerificada = versaoModelo;
    }

    public void decidir(Estado novo, String justificativa, UUID por, Instant agora) {
        if (novo == Estado.RESOLVIDO) {
            throw new ExcecaoDominio.RegraDeNegocio("conflito_resolvido_manual", "Um conflito só passa a Resolvido quando a reverificação do modelo deixa de encontrá-lo. Corrija o modelo e verifique de novo.");
        }
        if (novo == Estado.ACEITO && (justificativa == null || justificativa.isBlank())) {
            throw new ExcecaoDominio.Validacao("justificativa", "Aceitar um conflito exige justificativa técnica.");
        }
        if (estado == Estado.RESOLVIDO && novo != Estado.ABERTO) {
            throw new ExcecaoDominio.RegraDeNegocio("conflito_ja_resolvido", "Conflito já resolvido pela reverificação.");
        }
        this.estado = novo;
        this.justificativa = justificativa == null || justificativa.isBlank() ? null : justificativa.trim();
        this.decididoPor = por;
        this.decididoEm = agora;
    }

    public UUID getObraId() { return obraId; }
    public String getChave() { return chave; }
    public String getTipo() { return tipo; }
    public String getRegra() { return regra; }
    public String getElementosJson() { return elementosJson; }
    public String getMensagem() { return mensagem; }
    public String getEvidencia() { return evidencia; }
    public Estado getEstado() { return estado; }
    public String getJustificativa() { return justificativa; }
    public long getVersaoDetectada() { return versaoDetectada; }
    public long getVersaoVerificada() { return versaoVerificada; }
    public UUID getDecididoPor() { return decididoPor; }
    public Instant getDecididoEm() { return decididoEm; }
}
