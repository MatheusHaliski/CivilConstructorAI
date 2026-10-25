package br.com.civilconstructorai.lgpd;

import br.com.civilconstructorai.comum.EntidadeTenant;
import br.com.civilconstructorai.comum.ExcecaoDominio;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

/** Prazo de retenção configurado pela construtora por categoria — nunca abaixo do mínimo legal (DN-03). */
@Entity
@Table(name = "retencao_empresa")
public class RetencaoEmpresa extends EntidadeTenant {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private CategoriaDado categoria;

    @Column(name = "prazo_meses", nullable = false)
    private int prazoMeses;

    @Column(name = "base_legal", nullable = false, columnDefinition = "text")
    private String baseLegal;

    protected RetencaoEmpresa() {
    }

    public RetencaoEmpresa(UUID tenantId, CategoriaDado categoria, int prazoMeses) {
        super(tenantId);
        this.categoria = categoria;
        this.baseLegal = categoria.baseLegal();
        definirPrazo(prazoMeses);
    }

    public void definirPrazo(int meses) {
        if (meses < categoria.minimoMeses()) {
            throw new ExcecaoDominio.Validacao("prazoMeses",
                    "Retenção de \"" + categoria.titulo() + "\" não pode ficar abaixo de " + categoria.minimoMeses()
                            + " meses (" + categoria.descricaoPrazo() + " — " + categoria.baseLegal() + ").");
        }
        this.prazoMeses = meses;
    }

    public CategoriaDado getCategoria() { return categoria; }
    public int getPrazoMeses() { return prazoMeses; }
    public String getBaseLegal() { return baseLegal; }
}
