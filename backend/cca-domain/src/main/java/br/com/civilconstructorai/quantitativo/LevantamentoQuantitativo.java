package br.com.civilconstructorai.quantitativo;

import br.com.civilconstructorai.comum.EntidadeTenant;
import br.com.civilconstructorai.comum.EstadoJob;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

/** Levantamento quantitativo gravado (RF11): identificado pelo hash; OBSOLETO quando a versão do modelo muda. */
@Entity
@Table(name = "levantamento_quantitativo")
public class LevantamentoQuantitativo extends EntidadeTenant {

    @Column(name = "obra_id", nullable = false)
    private UUID obraId;

    @Column(name = "versao_modelo", nullable = false)
    private long versaoModelo;

    @Column(nullable = false)
    private String hash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoJob estado = EstadoJob.CONCLUIDO;

    @Column(name = "linhas_json", nullable = false)
    private String linhasJson;

    @Column(name = "totais_json", nullable = false)
    private String totaisJson;

    @Column(name = "liberado_cliente", nullable = false)
    private boolean liberadoCliente;

    protected LevantamentoQuantitativo() {
    }

    public LevantamentoQuantitativo(UUID tenantId, UUID obraId, long versaoModelo, String hash, String linhasJson, String totaisJson) {
        super(tenantId);
        this.obraId = obraId;
        this.versaoModelo = versaoModelo;
        this.hash = hash;
        this.linhasJson = linhasJson;
        this.totaisJson = totaisJson;
    }

    public void tornarObsoleto() {
        if (estado != EstadoJob.OBSOLETO) {
            estado = EstadoJob.MAQUINA.transicionar(estado, EstadoJob.OBSOLETO);
        }
    }

    public void liberarParaCliente(boolean liberar) {
        this.liberadoCliente = liberar;
    }

    public UUID getObraId() { return obraId; }
    public long getVersaoModelo() { return versaoModelo; }
    public String getHash() { return hash; }
    public EstadoJob getEstado() { return estado; }
    public String getLinhasJson() { return linhasJson; }
    public String getTotaisJson() { return totaisJson; }
    public boolean isLiberadoCliente() { return liberadoCliente; }
}
