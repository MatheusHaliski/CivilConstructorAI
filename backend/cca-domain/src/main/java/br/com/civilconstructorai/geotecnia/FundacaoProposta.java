package br.com.civilconstructorai.geotecnia;

import br.com.civilconstructorai.comum.EntidadeTenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

/** Fundação proposta para um pilar: tipo, geometria e memória (RF10). */
@Entity
@Table(name = "fundacao_proposta")
public class FundacaoProposta extends EntidadeTenant {

    @Column(name = "dimensionamento_id", nullable = false)
    private UUID dimensionamentoId;

    @Column(name = "obra_id", nullable = false)
    private UUID obraId;

    @Column(name = "pilar_id", nullable = false)
    private String pilarId;

    @Column(name = "pilar_rotulo")
    private String pilarRotulo;

    @Column(nullable = false)
    private String tipo; // SAPATA, BLOCO, RADIER

    @Column(name = "centro_x", nullable = false)
    private double centroX;

    @Column(name = "centro_y", nullable = false)
    private double centroY;

    @Column(nullable = false)
    private double bx;

    @Column(nullable = false)
    private double by;

    @Column(nullable = false)
    private double h;

    @Column(name = "carga_kn", nullable = false)
    private double cargaKn;

    @Column
    private Integer estacas;

    @Column(name = "diametro_estaca")
    private Double diametroEstaca;

    @Column(name = "comprimento_estaca")
    private Double comprimentoEstaca;

    @Column(name = "memoria_json", nullable = false)
    private String memoriaJson = "[]";

    @Column(nullable = false)
    private boolean verifica;

    protected FundacaoProposta() {
    }

    public FundacaoProposta(UUID tenantId, UUID dimensionamentoId, UUID obraId, String pilarId, String pilarRotulo, String tipo,
                            double centroX, double centroY, double bx, double by, double h, double cargaKn,
                            Integer estacas, Double diametroEstaca, Double comprimentoEstaca, String memoriaJson, boolean verifica) {
        super(tenantId);
        this.dimensionamentoId = dimensionamentoId;
        this.obraId = obraId;
        this.pilarId = pilarId;
        this.pilarRotulo = pilarRotulo;
        this.tipo = tipo;
        this.centroX = centroX;
        this.centroY = centroY;
        this.bx = bx;
        this.by = by;
        this.h = h;
        this.cargaKn = cargaKn;
        this.estacas = estacas;
        this.diametroEstaca = diametroEstaca;
        this.comprimentoEstaca = comprimentoEstaca;
        this.memoriaJson = memoriaJson;
        this.verifica = verifica;
    }

    public UUID getDimensionamentoId() { return dimensionamentoId; }
    public UUID getObraId() { return obraId; }
    public String getPilarId() { return pilarId; }
    public String getPilarRotulo() { return pilarRotulo; }
    public String getTipo() { return tipo; }
    public double getCentroX() { return centroX; }
    public double getCentroY() { return centroY; }
    public double getBx() { return bx; }
    public double getBy() { return by; }
    public double getH() { return h; }
    public double getCargaKn() { return cargaKn; }
    public Integer getEstacas() { return estacas; }
    public Double getDiametroEstaca() { return diametroEstaca; }
    public Double getComprimentoEstaca() { return comprimentoEstaca; }
    public String getMemoriaJson() { return memoriaJson; }
    public boolean isVerifica() { return verifica; }
}
