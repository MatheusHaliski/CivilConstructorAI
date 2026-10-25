package br.com.civilconstructorai.orcamento;

import br.com.civilconstructorai.comum.EntidadeBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/** Composição ou insumo SINAPI numa versão da tabela. */
@Entity
@Table(name = "composicao_sinapi")
public class ComposicaoSinapi extends EntidadeBase {

    @Column(name = "tabela_id", nullable = false)
    private UUID tabelaId;

    @Column(nullable = false)
    private String codigo;

    @Column(nullable = false)
    private String descricao;

    @Column(nullable = false)
    private String unidade;

    @Column(name = "custo_unitario", nullable = false, precision = 14, scale = 2)
    private BigDecimal custoUnitario;

    @Column(nullable = false)
    private String grupo;

    protected ComposicaoSinapi() {
    }

    public ComposicaoSinapi(UUID tabelaId, String codigo, String descricao, String unidade, BigDecimal custoUnitario, String grupo) {
        this.tabelaId = tabelaId;
        this.codigo = codigo.trim();
        this.descricao = descricao.trim();
        this.unidade = normalizarUnidade(unidade);
        this.custoUnitario = custoUnitario;
        this.grupo = grupo == null ? "" : grupo;
    }

    public static String normalizarUnidade(String u) {
        var s = u.trim().toUpperCase().replace("³", "3").replace("²", "2");
        return switch (s) {
            case "M3", "M³" -> "m3";
            case "M2", "M²" -> "m2";
            case "M" -> "m";
            case "KG" -> "kg";
            case "UN", "UND", "UNID" -> "un";
            case "H" -> "h";
            default -> s.toLowerCase();
        };
    }

    public UUID getTabelaId() { return tabelaId; }
    public String getCodigo() { return codigo; }
    public String getDescricao() { return descricao; }
    public String getUnidade() { return unidade; }
    public BigDecimal getCustoUnitario() { return custoUnitario; }
    public String getGrupo() { return grupo; }
}
