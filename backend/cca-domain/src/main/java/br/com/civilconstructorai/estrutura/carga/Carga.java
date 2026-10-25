package br.com.civilconstructorai.estrutura.carga;

import br.com.civilconstructorai.comum.EntidadeTenant;
import br.com.civilconstructorai.comum.ExcecaoDominio;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.UUID;

/**
 * Carga lançada numa obra (RF04): natureza (G/Q/W), tipo (área, linear, pontual) com unidade coerente e o
 * elemento alvo (laje, viga, pilar) ou o pavimento inteiro quando {@code elementoAlvoId} é nulo.
 */
@Entity
@Table(name = "carga")
public class Carga extends EntidadeTenant {

    @Column(name = "obra_id", nullable = false)
    private UUID obraId;

    @Column(name = "pavimento_id", nullable = false)
    private String pavimentoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NaturezaAcao natureza;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoCarga tipo;

    /** kN/m², kN/m ou kN conforme o tipo */
    @Column(nullable = false)
    private double valor;

    @Column(name = "elemento_alvo_id")
    private String elementoAlvoId;

    @Column(nullable = false)
    private String descricao;

    /** agrupa cargas no mesmo caso (ex.: "revestimento", "alvenaria", "sobrecarga residencial") */
    @Column(name = "caso_carga", nullable = false)
    private String casoCarga;

    /** carga pontual em viga: distância (m) a partir do primeiro ponto (p1) da viga; nula nos demais casos */
    @Column(name = "posicao_m")
    private Double posicao;

    protected Carga() {
    }

    public Carga(UUID tenantId, UUID obraId, String pavimentoId, NaturezaAcao natureza, TipoCarga tipo, double valor,
                 String elementoAlvoId, String descricao, String casoCarga) {
        this(tenantId, obraId, pavimentoId, natureza, tipo, valor, elementoAlvoId, descricao, casoCarga, null);
    }

    public Carga(UUID tenantId, UUID obraId, String pavimentoId, NaturezaAcao natureza, TipoCarga tipo, double valor,
                 String elementoAlvoId, String descricao, String casoCarga, Double posicao) {
        super(tenantId);
        var erros = new ArrayList<ExcecaoDominio.Validacao.Erro>();
        if (pavimentoId == null || pavimentoId.isBlank()) {
            erros.add(new ExcecaoDominio.Validacao.Erro("pavimentoId", "Informe o pavimento."));
        }
        if (natureza == null) {
            erros.add(new ExcecaoDominio.Validacao.Erro("natureza", "Informe a natureza da ação (PERMANENTE, ACIDENTAL, VENTO)."));
        }
        if (tipo == null) {
            erros.add(new ExcecaoDominio.Validacao.Erro("tipo", "Informe o tipo da carga (AREA, LINEAR, PONTUAL)."));
        }
        if (valor <= 0) {
            erros.add(new ExcecaoDominio.Validacao.Erro("valor", "O valor da carga deve ser maior que zero."));
        }
        if (descricao == null || descricao.isBlank()) {
            erros.add(new ExcecaoDominio.Validacao.Erro("descricao", "Descreva a carga."));
        }
        if (posicao != null && (tipo != TipoCarga.PONTUAL || posicao < 0 || !Double.isFinite(posicao))) {
            erros.add(new ExcecaoDominio.Validacao.Erro("posicao", tipo != TipoCarga.PONTUAL
                    ? "Posição só se aplica a carga pontual." : "A posição deve ser uma distância em metros, maior ou igual a zero."));
        }
        if (!erros.isEmpty()) {
            throw new ExcecaoDominio.Validacao(erros);
        }
        this.posicao = posicao;
        this.obraId = obraId;
        this.pavimentoId = pavimentoId;
        this.natureza = natureza;
        this.tipo = tipo;
        this.valor = valor;
        this.elementoAlvoId = elementoAlvoId;
        this.descricao = descricao;
        this.casoCarga = casoCarga == null || casoCarga.isBlank() ? natureza.sigla() : casoCarga;
    }

    public UUID getObraId() { return obraId; }
    public String getPavimentoId() { return pavimentoId; }
    public NaturezaAcao getNatureza() { return natureza; }
    public TipoCarga getTipo() { return tipo; }
    public double getValor() { return valor; }
    public String getUnidade() { return tipo.unidade(); }
    public String getElementoAlvoId() { return elementoAlvoId; }
    public String getDescricao() { return descricao; }
    public String getCasoCarga() { return casoCarga; }
    public Double getPosicao() { return posicao; }
}
