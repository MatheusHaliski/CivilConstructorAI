package br.com.civilconstructorai.orcamento;

import br.com.civilconstructorai.comum.EntidadeBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDate;

/** Versão da tabela SINAPI: UF, mês de referência e regime (desonerado ou não). Global (sem tenant). */
@Entity
@Table(name = "tabela_sinapi")
public class TabelaSinapi extends EntidadeBase {

    @Column(nullable = false, length = 2)
    private String uf;

    @Column(name = "mes_referencia", nullable = false)
    private LocalDate mesReferencia;

    @Column(nullable = false)
    private boolean desonerado;

    @Column(nullable = false)
    private int composicoes;

    protected TabelaSinapi() {
    }

    public TabelaSinapi(String uf, LocalDate mesReferencia, boolean desonerado) {
        this.uf = uf.toUpperCase();
        this.mesReferencia = mesReferencia.withDayOfMonth(1);
        this.desonerado = desonerado;
    }

    public void setComposicoes(int composicoes) { this.composicoes = composicoes; }
    public String getUf() { return uf; }
    public LocalDate getMesReferencia() { return mesReferencia; }
    public boolean isDesonerado() { return desonerado; }
    public int getComposicoes() { return composicoes; }

    public String rotulo() {
        return "SINAPI " + uf + " " + mesReferencia.getMonthValue() + "/" + mesReferencia.getYear() + (desonerado ? " desonerado" : " não desonerado");
    }
}
