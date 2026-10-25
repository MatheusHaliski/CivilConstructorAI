package br.com.civilconstructorai.material;

import br.com.civilconstructorai.comum.EntidadeTenant;
import br.com.civilconstructorai.comum.ExcecaoDominio;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Material criado por uma empresa (fora do catálogo técnico). Fica identificado como personalizado, com situação de
 * validação e histórico; enquanto não for VALIDADO por profissional com selo, o dimensionamento marca as
 * verificações que dependem dele como "dados insuficientes".
 */
@Entity
@Table(name = "material_personalizado")
public class MaterialPersonalizado extends EntidadeTenant {

    public enum Situacao { VALIDACAO_PENDENTE, VALIDADO, REJEITADO }

    @Column(nullable = false)
    private String codigo;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false)
    private String familia;

    @Column(name = "baseado_em")
    private String baseadoEm;

    /** item no mesmo formato do catálogo (propriedades com origem e unidade) */
    @Column(name = "item_json", nullable = false)
    private String itemJson;

    @Column(nullable = false)
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    private Situacao situacao = Situacao.VALIDACAO_PENDENTE;

    @Column(name = "criado_por", nullable = false)
    private UUID criadoPor;

    @Column(name = "historico_json", nullable = false)
    private String historicoJson = "[]";

    protected MaterialPersonalizado() {
    }

    public MaterialPersonalizado(UUID tenantId, String codigo, String nome, String familia, String baseadoEm, String itemJson, UUID criadoPor, String historicoJson) {
        super(tenantId);
        this.codigo = codigo;
        this.nome = nome;
        this.familia = familia;
        this.baseadoEm = baseadoEm;
        this.itemJson = itemJson;
        this.criadoPor = criadoPor;
        this.historicoJson = historicoJson;
    }

    public void decidir(boolean validar, String historicoJson) {
        if (situacao != Situacao.VALIDACAO_PENDENTE) {
            throw new ExcecaoDominio.Conflito("Material já " + (situacao == Situacao.VALIDADO ? "validado" : "rejeitado") + ".");
        }
        this.situacao = validar ? Situacao.VALIDADO : Situacao.REJEITADO;
        this.historicoJson = historicoJson;
    }

    public String getCodigo() { return codigo; }
    public String getNome() { return nome; }
    public String getFamilia() { return familia; }
    public String getBaseadoEm() { return baseadoEm; }
    public String getItemJson() { return itemJson; }
    public Situacao getSituacao() { return situacao; }
    public UUID getCriadoPor() { return criadoPor; }
    public String getHistoricoJson() { return historicoJson; }
}
