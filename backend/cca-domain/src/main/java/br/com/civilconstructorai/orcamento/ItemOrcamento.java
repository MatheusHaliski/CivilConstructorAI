package br.com.civilconstructorai.orcamento;

import br.com.civilconstructorai.comum.EntidadeTenant;
import br.com.civilconstructorai.comum.ExcecaoDominio;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/** Item do orçamento: quantitativo × composição SINAPI; marcado quando a associação veio da IA. */
@Entity
@Table(name = "item_orcamento")
public class ItemOrcamento extends EntidadeTenant {

    @Column(name = "orcamento_id", nullable = false)
    private UUID orcamentoId;

    @Column(name = "etapa", nullable = false)
    private String etapa;

    @Column(name = "servico", nullable = false)
    private String servico;

    @Column(name = "elemento_tipo")
    private String elementoTipo;

    @Column(name = "codigo_sinapi", nullable = false)
    private String codigoSinapi;

    @Column(nullable = false)
    private String descricao;

    @Column(nullable = false)
    private String unidade;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal quantidade;

    @Column(name = "custo_unitario", nullable = false, precision = 14, scale = 2)
    private BigDecimal custoUnitario;

    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal total;

    @Column(name = "gerado_por_ia", nullable = false)
    private boolean geradoPorIa;

    @Column(name = "confirmado_por")
    private UUID confirmadoPor;

    @Column(name = "justificativa_ia")
    private String justificativaIa;

    protected ItemOrcamento() {
    }

    public ItemOrcamento(UUID tenantId, UUID orcamentoId, String etapa, String servico, String elementoTipo, ComposicaoSinapi comp,
                         String unidadeQuantitativo, BigDecimal quantidade, boolean geradoPorIa, String justificativaIa) {
        super(tenantId);
        if (!comp.getUnidade().equals(ComposicaoSinapi.normalizarUnidade(unidadeQuantitativo))) {
            throw new ExcecaoDominio.RegraDeNegocio("unidade_incompativel",
                    "Composição " + comp.getCodigo() + " é em " + comp.getUnidade() + ", mas o quantitativo está em " + unidadeQuantitativo + ".");
        }
        if (quantidade == null || quantidade.signum() <= 0) {
            throw new ExcecaoDominio.Validacao("quantidade", "Quantidade deve ser maior que zero.");
        }
        this.orcamentoId = orcamentoId;
        this.etapa = etapa;
        this.servico = servico;
        this.elementoTipo = elementoTipo;
        this.codigoSinapi = comp.getCodigo();
        this.descricao = comp.getDescricao();
        this.unidade = comp.getUnidade();
        this.quantidade = quantidade.setScale(3, RoundingMode.HALF_UP);
        this.custoUnitario = comp.getCustoUnitario();
        this.total = this.quantidade.multiply(custoUnitario).setScale(2, RoundingMode.HALF_UP);
        this.geradoPorIa = geradoPorIa;
        this.justificativaIa = justificativaIa;
    }

    public void confirmar(UUID quem) {
        this.confirmadoPor = quem;
    }

    public boolean pendenteDeConfirmacao() {
        return geradoPorIa && confirmadoPor == null;
    }

    public UUID getOrcamentoId() { return orcamentoId; }
    public String getEtapa() { return etapa; }
    public String getServico() { return servico; }
    public String getElementoTipo() { return elementoTipo; }
    public String getCodigoSinapi() { return codigoSinapi; }
    public String getDescricao() { return descricao; }
    public String getUnidade() { return unidade; }
    public BigDecimal getQuantidade() { return quantidade; }
    public BigDecimal getCustoUnitario() { return custoUnitario; }
    public BigDecimal getTotal() { return total; }
    public boolean isGeradoPorIa() { return geradoPorIa; }
    public UUID getConfirmadoPor() { return confirmadoPor; }
    public String getJustificativaIa() { return justificativaIa; }
}
