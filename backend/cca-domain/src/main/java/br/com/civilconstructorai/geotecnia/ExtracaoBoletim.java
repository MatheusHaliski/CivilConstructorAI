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

/** Extração de boletim SPT por IA (RF09): duas chamadas (structured outputs e depois citações); rascunho até confirmação. */
@Entity
@Table(name = "extracao_boletim")
public class ExtracaoBoletim extends EntidadeTenant {

    @Column(name = "obra_id", nullable = false)
    private UUID obraId;

    @Column(name = "chave_s3", nullable = false)
    private String chaveS3;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoRascunho estado = EstadoRascunho.PENDENTE;

    @Column(name = "dados_json")
    private String dadosJson;

    @Column(name = "citacoes_json")
    private String citacoesJson;

    @Column(name = "modelo_ia")
    private String modeloIa;

    @Column(name = "input_tokens", nullable = false)
    private long inputTokens;

    @Column(name = "output_tokens", nullable = false)
    private long outputTokens;

    @Column(name = "mensagem_erro")
    private String mensagemErro;

    @Column(name = "decidido_por")
    private UUID decididoPor;

    @Column(name = "decidido_em")
    private Instant decididoEm;

    protected ExtracaoBoletim() {
    }

    public ExtracaoBoletim(UUID tenantId, UUID obraId, String chaveS3) {
        super(tenantId);
        this.obraId = obraId;
        this.chaveS3 = chaveS3;
    }

    public void iniciar() {
        estado = EstadoRascunho.MAQUINA.transicionar(estado, EstadoRascunho.EM_EXECUCAO);
    }

    public void concluir(String dadosJson, String citacoesJson, String modeloIa, long in, long out) {
        estado = EstadoRascunho.MAQUINA.transicionar(estado, EstadoRascunho.GERADO_POR_IA);
        this.dadosJson = dadosJson;
        this.citacoesJson = citacoesJson;
        this.modeloIa = modeloIa;
        this.inputTokens = in;
        this.outputTokens = out;
    }

    public void falhar(String msg) {
        estado = EstadoRascunho.MAQUINA.transicionar(estado, EstadoRascunho.FALHOU);
        this.mensagemErro = msg;
    }

    public void decidir(boolean aprovar, UUID quem, Instant quando) {
        if (estado == EstadoRascunho.GERADO_POR_IA) {
            estado = EstadoRascunho.MAQUINA.transicionar(estado, EstadoRascunho.EM_REVISAO);
        }
        estado = EstadoRascunho.MAQUINA.transicionar(estado, aprovar ? EstadoRascunho.APROVADO : EstadoRascunho.REJEITADO);
        this.decididoPor = quem;
        this.decididoEm = quando;
    }

    public UUID getObraId() { return obraId; }
    public String getChaveS3() { return chaveS3; }
    public EstadoRascunho getEstado() { return estado; }
    public String getDadosJson() { return dadosJson; }
    public String getCitacoesJson() { return citacoesJson; }
    public String getModeloIa() { return modeloIa; }
    public long getInputTokens() { return inputTokens; }
    public long getOutputTokens() { return outputTokens; }
    public String getMensagemErro() { return mensagemErro; }
    public UUID getDecididoPor() { return decididoPor; }
    public Instant getDecididoEm() { return decididoEm; }
}
