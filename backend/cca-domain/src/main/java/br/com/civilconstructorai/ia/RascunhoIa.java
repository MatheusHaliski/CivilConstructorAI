package br.com.civilconstructorai.ia;

import br.com.civilconstructorai.comum.EntidadeTenant;
import br.com.civilconstructorai.comum.ExcecaoDominio;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Rascunho gerado por IA, marcado como tal até aprovação por profissional habilitado (ART/RRT). */
@Entity
@Table(name = "rascunho_ia")
public class RascunhoIa extends EntidadeTenant {

    @Column(name = "obra_id", nullable = false, updatable = false)
    private UUID obraId;

    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String tipo;

    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String rota;

    @Column(name = "entidade_ref", columnDefinition = "text")
    private String entidadeRef;

    @Column(nullable = false, columnDefinition = "text")
    private String conteudo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "text")
    private EstadoRascunhoIa estado = EstadoRascunhoIa.GERADO_POR_IA;

    @Column(name = "criado_por", updatable = false)
    private UUID criadoPor;

    @Column(name = "aprovado_por")
    private UUID aprovadoPor;

    @Column(name = "aprovado_em")
    private Instant aprovadoEm;

    @Column(name = "art_rrt", columnDefinition = "text")
    private String artRrt;

    @Column(columnDefinition = "text")
    private String motivo;

    @Column(name = "chamada_ia_id")
    private UUID chamadaIaId;

    protected RascunhoIa() {
    }

    public RascunhoIa(UUID tenantId, UUID obraId, String tipo, String rota, String entidadeRef, String conteudo, UUID criadoPor, UUID chamadaIaId, boolean recusado) {
        super(tenantId);
        this.obraId = obraId;
        this.tipo = tipo;
        this.rota = rota;
        this.entidadeRef = entidadeRef;
        this.conteudo = conteudo;
        this.criadoPor = criadoPor;
        this.chamadaIaId = chamadaIaId;
        if (recusado) {
            this.estado = EstadoRascunhoIa.RECUSADO_PELO_MODELO;
        }
    }

    public void enviarParaRevisao() {
        estado = EstadoRascunhoIa.MAQUINA.transicionar(estado, EstadoRascunhoIa.EM_REVISAO);
    }

    public void aprovar(UUID por, String artRrt, Instant agora) {
        if (artRrt == null || artRrt.isBlank()) {
            throw new ExcecaoDominio.Validacao("artRrt", "Informe o número da ART/RRT que cobre esta aprovação.");
        }
        estado = EstadoRascunhoIa.MAQUINA.transicionar(estado, EstadoRascunhoIa.APROVADO);
        this.aprovadoPor = por;
        this.aprovadoEm = agora;
        this.artRrt = artRrt.trim();
    }

    public void rejeitar(UUID por, String motivo) {
        estado = EstadoRascunhoIa.MAQUINA.transicionar(estado, EstadoRascunhoIa.REJEITADO);
        this.aprovadoPor = por;
        this.motivo = motivo;
    }

    public void obsoletar(String motivo) {
        estado = EstadoRascunhoIa.MAQUINA.transicionar(estado, EstadoRascunhoIa.OBSOLETO);
        this.motivo = motivo;
    }

    public UUID getObraId() { return obraId; }
    public String getTipo() { return tipo; }
    public String getRota() { return rota; }
    public String getEntidadeRef() { return entidadeRef; }
    public String getConteudo() { return conteudo; }
    public EstadoRascunhoIa getEstado() { return estado; }
    public UUID getCriadoPor() { return criadoPor; }
    public UUID getAprovadoPor() { return aprovadoPor; }
    public Instant getAprovadoEm() { return aprovadoEm; }
    public String getArtRrt() { return artRrt; }
    public String getMotivo() { return motivo; }
    public UUID getChamadaIaId() { return chamadaIaId; }
}
