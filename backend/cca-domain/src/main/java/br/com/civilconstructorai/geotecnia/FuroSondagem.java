package br.com.civilconstructorai.geotecnia;

import br.com.civilconstructorai.comum.EntidadeTenant;
import br.com.civilconstructorai.comum.ExcecaoDominio;
import br.com.civilconstructorai.comum.MaquinaDeEstados;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Furo de sondagem SPT (RF09). Localização em coordenadas da obra (x, y em metros no sistema do modelo) e
 * geográfica (lon/lat SIRGAS 2000) para a verificação de perímetro. Camadas e leituras ficam em JSON.
 */
@Entity
@Table(name = "furo_sondagem")
public class FuroSondagem extends EntidadeTenant {

    public enum Estado {
        RASCUNHO, CONFIRMADO, CANCELADO;

        public static final MaquinaDeEstados<Estado> MAQUINA = new MaquinaDeEstados<>("FuroSondagem", Map.of(
                RASCUNHO, Set.of(CONFIRMADO, CANCELADO),
                CONFIRMADO, Set.of(CANCELADO),
                CANCELADO, Set.of()));
    }

    @Column(name = "obra_id", nullable = false)
    private UUID obraId;

    @Column(nullable = false)
    private String codigo;

    @Column(nullable = false)
    private double x;

    @Column(nullable = false)
    private double y;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "cota_boca", nullable = false)
    private double cotaBoca;

    @Column(name = "nivel_agua")
    private Double nivelAgua;

    @Column(name = "camadas_json", nullable = false)
    private String camadasJson = "[]";

    @Column(name = "leituras_json", nullable = false)
    private String leiturasJson = "[]";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Estado estado = Estado.RASCUNHO;

    @Column(name = "boletim_chave_s3")
    private String boletimChaveS3;

    @Column(name = "origem_ia", nullable = false)
    private boolean origemIa;

    protected FuroSondagem() {
    }

    public FuroSondagem(UUID tenantId, UUID obraId, String codigo, double x, double y, Double longitude, Double latitude, double cotaBoca, Double nivelAgua) {
        super(tenantId);
        if (codigo == null || !codigo.matches("SP-?\\d{1,3}")) {
            throw new ExcecaoDominio.Validacao("codigo", "Código do furo no padrão SP-01.");
        }
        this.obraId = obraId;
        this.codigo = codigo;
        this.x = x;
        this.y = y;
        this.longitude = longitude;
        this.latitude = latitude;
        this.cotaBoca = cotaBoca;
        this.nivelAgua = nivelAgua;
    }

    /** camadas devem ser contíguas (base de uma = topo da seguinte) e descendentes */
    public static void validarCamadas(List<CamadaSolo> camadas) {
        var erros = new ArrayList<ExcecaoDominio.Validacao.Erro>();
        for (int i = 0; i < camadas.size(); i++) {
            var c = camadas.get(i);
            if (c.cotaBase() >= c.cotaTopo()) {
                erros.add(new ExcecaoDominio.Validacao.Erro("camadas[" + i + "]", "Cota da base deve ser menor que a do topo."));
            }
            if (i > 0 && Math.abs(camadas.get(i - 1).cotaBase() - c.cotaTopo()) > 0.01) {
                erros.add(new ExcecaoDominio.Validacao.Erro("camadas[" + i + "]", "Camadas devem ser contíguas, sem sobreposição nem vazio."));
            }
            if (c.tipo() == null) {
                erros.add(new ExcecaoDominio.Validacao.Erro("camadas[" + i + "].tipo", "Informe o tipo de solo."));
            }
        }
        if (!erros.isEmpty()) {
            throw new ExcecaoDominio.Validacao(erros);
        }
    }

    public void confirmar() {
        estado = Estado.MAQUINA.transicionar(estado, Estado.CONFIRMADO);
    }

    public void cancelar() {
        estado = Estado.MAQUINA.transicionar(estado, Estado.CANCELADO);
    }

    public UUID getObraId() { return obraId; }
    public String getCodigo() { return codigo; }
    public double getX() { return x; }
    public double getY() { return y; }
    public Double getLongitude() { return longitude; }
    public Double getLatitude() { return latitude; }
    public double getCotaBoca() { return cotaBoca; }
    public Double getNivelAgua() { return nivelAgua; }
    public String getCamadasJson() { return camadasJson; }
    public void setCamadasJson(String camadasJson) { this.camadasJson = camadasJson; }
    public String getLeiturasJson() { return leiturasJson; }
    public void setLeiturasJson(String leiturasJson) { this.leiturasJson = leiturasJson; }
    public Estado getEstado() { return estado; }
    public String getBoletimChaveS3() { return boletimChaveS3; }
    public void setBoletimChaveS3(String boletimChaveS3) { this.boletimChaveS3 = boletimChaveS3; }
    public boolean isOrigemIa() { return origemIa; }
    public void setOrigemIa(boolean origemIa) { this.origemIa = origemIa; }
}
