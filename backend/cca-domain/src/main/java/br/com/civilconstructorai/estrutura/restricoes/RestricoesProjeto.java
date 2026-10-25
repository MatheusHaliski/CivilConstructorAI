package br.com.civilconstructorai.estrutura.restricoes;

import br.com.civilconstructorai.comum.EntidadeTenant;
import br.com.civilconstructorai.comum.ExcecaoDominio;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.UUID;

/**
 * RF08 · tolerâncias geométricas e seções mínimas por obra. Os padrões vêm da Tabela A / NBR 6118 (13.2) e a
 * empresa só pode torná-los <strong>mais restritivos</strong>.
 */
@Entity
@Table(name = "restricoes_projeto")
public class RestricoesProjeto extends EntidadeTenant {

    public static final double PADRAO_SHORT_CURVE = 0.05;
    public static final double PADRAO_PILAR_LADO = 0.19;
    public static final double PADRAO_PILAR_AREA = 0.036;
    public static final double PADRAO_VIGA_B = 0.12;
    public static final double PADRAO_LAJE_H = 0.08;
    public static final double PADRAO_PAREDE_ESP = 0.05;

    @Column(name = "obra_id", nullable = false, unique = true)
    private UUID obraId;

    @Column(name = "short_curve_tolerance", nullable = false)
    private double shortCurveTolerance = PADRAO_SHORT_CURVE;

    @Column(name = "pilar_lado_min", nullable = false)
    private double pilarLadoMin = PADRAO_PILAR_LADO;

    @Column(name = "pilar_area_min", nullable = false)
    private double pilarAreaMin = PADRAO_PILAR_AREA;

    @Column(name = "viga_b_min", nullable = false)
    private double vigaBMin = PADRAO_VIGA_B;

    @Column(name = "laje_h_min", nullable = false)
    private double lajeHMin = PADRAO_LAJE_H;

    @Column(name = "parede_esp_min", nullable = false)
    private double paredeEspMin = PADRAO_PAREDE_ESP;

    protected RestricoesProjeto() {
    }

    public RestricoesProjeto(UUID tenantId, UUID obraId) {
        super(tenantId);
        this.obraId = obraId;
    }

    public void atualizar(double shortCurve, double pilarLado, double pilarArea, double vigaB, double lajeH, double paredeEsp) {
        var erros = new ArrayList<ExcecaoDominio.Validacao.Erro>();
        if (shortCurve > PADRAO_SHORT_CURVE || shortCurve <= 0) erros.add(new ExcecaoDominio.Validacao.Erro("shortCurveTolerance", "Entre 0 e 0,05 m (só mais restritivo que o padrão)."));
        if (pilarLado < PADRAO_PILAR_LADO) erros.add(new ExcecaoDominio.Validacao.Erro("pilarLadoMin", "Mínimo normativo 0,19 m (NBR 6118 13.2.3)."));
        if (pilarArea < PADRAO_PILAR_AREA) erros.add(new ExcecaoDominio.Validacao.Erro("pilarAreaMin", "Mínimo normativo 360 cm²."));
        if (vigaB < PADRAO_VIGA_B) erros.add(new ExcecaoDominio.Validacao.Erro("vigaBMin", "Mínimo normativo 0,12 m (NBR 6118 13.2.2)."));
        if (lajeH < PADRAO_LAJE_H) erros.add(new ExcecaoDominio.Validacao.Erro("lajeHMin", "Mínimo normativo 0,08 m (NBR 6118 13.2.4.1)."));
        if (paredeEsp < PADRAO_PAREDE_ESP) erros.add(new ExcecaoDominio.Validacao.Erro("paredeEspMin", "Mínimo 0,05 m."));
        if (!erros.isEmpty()) {
            throw new ExcecaoDominio.Validacao(erros);
        }
        this.shortCurveTolerance = shortCurve;
        this.pilarLadoMin = pilarLado;
        this.pilarAreaMin = pilarArea;
        this.vigaBMin = vigaB;
        this.lajeHMin = lajeH;
        this.paredeEspMin = paredeEsp;
    }

    public UUID getObraId() { return obraId; }
    public double getShortCurveTolerance() { return shortCurveTolerance; }
    public double getPilarLadoMin() { return pilarLadoMin; }
    public double getPilarAreaMin() { return pilarAreaMin; }
    public double getVigaBMin() { return vigaBMin; }
    public double getLajeHMin() { return lajeHMin; }
    public double getParedeEspMin() { return paredeEspMin; }
}
