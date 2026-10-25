package br.com.civilconstructorai.geotecnia;

import br.com.civilconstructorai.comum.EntidadeTenant;
import br.com.civilconstructorai.estrutura.EstadoRascunho;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Job de dimensionamento de fundações (RF10). Propostas por pilar em {@code fundacao_proposta}. */
@Entity
@Table(name = "dimensionamento_fundacao")
public class DimensionamentoFundacao extends EntidadeTenant {

    @Column(name = "obra_id", nullable = false)
    private UUID obraId;

    @Column(name = "analise_id", nullable = false)
    private UUID analiseId;

    @Column(name = "versao_modelo", nullable = false)
    private long versaoModelo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoRascunho estado = EstadoRascunho.PENDENTE;

    @Column(name = "solucao_geral", nullable = false)
    private String solucaoGeral = "INDEFINIDA";

    @Column(name = "mensagem_erro")
    private String mensagemErro;

    @Column(name = "aprovado_por")
    private UUID aprovadoPor;

    @Column(name = "decidido_em")
    private Instant decididoEm;

    protected DimensionamentoFundacao() {
    }

    public DimensionamentoFundacao(UUID tenantId, UUID obraId, UUID analiseId, long versaoModelo) {
        super(tenantId);
        this.obraId = obraId;
        this.analiseId = analiseId;
        this.versaoModelo = versaoModelo;
    }

    public void iniciar() { estado = EstadoRascunho.MAQUINA.transicionar(estado, EstadoRascunho.EM_EXECUCAO); }

    public void concluir(String solucaoGeral) {
        estado = EstadoRascunho.MAQUINA.transicionar(estado, EstadoRascunho.CALCULADO);
        this.solucaoGeral = solucaoGeral;
    }

    public void falhar(String msg) {
        estado = EstadoRascunho.MAQUINA.transicionar(estado, EstadoRascunho.FALHOU);
        this.mensagemErro = msg;
    }

    public void aplicarNoModelo() {
        if (estado == EstadoRascunho.CALCULADO) {
            estado = EstadoRascunho.MAQUINA.transicionar(estado, EstadoRascunho.EM_REVISAO);
        }
        estado = EstadoRascunho.MAQUINA.transicionar(estado, EstadoRascunho.APLICADO_NO_MODELO);
    }

    public void aprovar(UUID quem, Instant quando) {
        if (estado == EstadoRascunho.CALCULADO) {
            estado = EstadoRascunho.MAQUINA.transicionar(estado, EstadoRascunho.EM_REVISAO);
        }
        estado = EstadoRascunho.MAQUINA.transicionar(estado, EstadoRascunho.APROVADO);
        aprovadoPor = quem;
        decididoEm = quando;
    }

    public void rejeitar(UUID quem, Instant quando) {
        if (estado == EstadoRascunho.CALCULADO) {
            estado = EstadoRascunho.MAQUINA.transicionar(estado, EstadoRascunho.EM_REVISAO);
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

    public UUID getObraId() { return obraId; }
    public UUID getAnaliseId() { return analiseId; }
    public long getVersaoModelo() { return versaoModelo; }
    public EstadoRascunho getEstado() { return estado; }
    public String getSolucaoGeral() { return solucaoGeral; }
    public String getMensagemErro() { return mensagemErro; }
    public UUID getAprovadoPor() { return aprovadoPor; }
    public Instant getDecididoEm() { return decididoEm; }
}
