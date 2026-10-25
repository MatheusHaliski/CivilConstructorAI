package br.com.civilconstructorai.orcamento;

import br.com.civilconstructorai.comum.EntidadeTenant;
import br.com.civilconstructorai.comum.ExcecaoDominio;
import br.com.civilconstructorai.estrutura.EstadoRascunho;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Orçamento da obra (RF12): itens ligados a composições SINAPI; vale só depois de aprovado por profissional. */
@Entity
@Table(name = "orcamento")
public class Orcamento extends EntidadeTenant {

    @Column(name = "obra_id", nullable = false)
    private UUID obraId;

    @Column(name = "levantamento_id")
    private UUID levantamentoId;

    @Column(name = "versao_modelo", nullable = false)
    private long versaoModelo;

    @Column(name = "tabela_sinapi_id", nullable = false)
    private UUID tabelaSinapiId;

    @Column(name = "data_base", nullable = false)
    private String dataBase;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoRascunho estado = EstadoRascunho.PENDENTE;

    @Column(name = "total", nullable = false, precision = 16, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "bdi_pct", nullable = false)
    private double bdiPct = 25.0;

    @Column(name = "itens_gerados_por_ia", nullable = false)
    private int itensGeradosPorIa;

    @Column(name = "liberado_cliente", nullable = false)
    private boolean liberadoCliente;

    @Column(name = "detalhe_liberado_cliente", nullable = false)
    private boolean detalheLiberadoCliente;

    @Column(name = "aprovado_por")
    private UUID aprovadoPor;

    @Column(name = "decidido_em")
    private Instant decididoEm;

    protected Orcamento() {
    }

    public Orcamento(UUID tenantId, UUID obraId, UUID levantamentoId, long versaoModelo, UUID tabelaSinapiId, String dataBase) {
        super(tenantId);
        this.obraId = obraId;
        this.levantamentoId = levantamentoId;
        this.versaoModelo = versaoModelo;
        this.tabelaSinapiId = tabelaSinapiId;
        this.dataBase = dataBase;
    }

    public void iniciar() { estado = EstadoRascunho.MAQUINA.transicionar(estado, EstadoRascunho.EM_EXECUCAO); }

    public void concluirGeracao(boolean comIa, BigDecimal total, int itensIa) {
        estado = EstadoRascunho.MAQUINA.transicionar(estado, comIa ? EstadoRascunho.GERADO_POR_IA : EstadoRascunho.CALCULADO);
        this.total = total;
        this.itensGeradosPorIa = itensIa;
    }

    public void atualizarTotal(BigDecimal total, int itensIa) {
        this.total = total;
        this.itensGeradosPorIa = itensIa;
    }

    public void enviarParaRevisao() {
        estado = EstadoRascunho.MAQUINA.transicionar(estado, EstadoRascunho.EM_REVISAO);
    }

    public void aprovar(UUID quem, Instant quando, int itensPendentesIa) {
        if (itensPendentesIa > 0) {
            throw new ExcecaoDominio.RegraDeNegocio("itens_ia_pendentes", itensPendentesIa + " item(ns) sugerido(s) pela IA ainda sem confirmação.");
        }
        if (estado == EstadoRascunho.GERADO_POR_IA || estado == EstadoRascunho.CALCULADO) {
            enviarParaRevisao();
        }
        estado = EstadoRascunho.MAQUINA.transicionar(estado, EstadoRascunho.APROVADO);
        aprovadoPor = quem;
        decididoEm = quando;
    }

    public void rejeitar(UUID quem, Instant quando) {
        if (estado == EstadoRascunho.GERADO_POR_IA || estado == EstadoRascunho.CALCULADO) {
            enviarParaRevisao();
        }
        estado = EstadoRascunho.MAQUINA.transicionar(estado, EstadoRascunho.REJEITADO);
        aprovadoPor = quem;
        decididoEm = quando;
    }

    public void tornarObsoleto() {
        if (!EstadoRascunho.MAQUINA.terminal(estado)) {
            estado = EstadoRascunho.MAQUINA.transicionar(estado, EstadoRascunho.OBSOLETO);
        }
    }

    public void liberarParaCliente(boolean totais, boolean detalhe) {
        if (estado != EstadoRascunho.APROVADO) {
            throw new ExcecaoDominio.RegraDeNegocio("orcamento_nao_aprovado", "Só orçamento aprovado pode ser liberado ao cliente.");
        }
        this.liberadoCliente = totais || detalhe;
        this.detalheLiberadoCliente = detalhe;
    }

    public boolean editavel() {
        return estado == EstadoRascunho.GERADO_POR_IA || estado == EstadoRascunho.CALCULADO || estado == EstadoRascunho.EM_REVISAO;
    }

    public UUID getObraId() { return obraId; }
    public UUID getLevantamentoId() { return levantamentoId; }
    public long getVersaoModelo() { return versaoModelo; }
    public UUID getTabelaSinapiId() { return tabelaSinapiId; }
    public String getDataBase() { return dataBase; }
    public EstadoRascunho getEstado() { return estado; }
    public BigDecimal getTotal() { return total; }
    public double getBdiPct() { return bdiPct; }
    public int getItensGeradosPorIa() { return itensGeradosPorIa; }
    public boolean isLiberadoCliente() { return liberadoCliente; }
    public boolean isDetalheLiberadoCliente() { return detalheLiberadoCliente; }
    public UUID getAprovadoPor() { return aprovadoPor; }
    public Instant getDecididoEm() { return decididoEm; }
}
