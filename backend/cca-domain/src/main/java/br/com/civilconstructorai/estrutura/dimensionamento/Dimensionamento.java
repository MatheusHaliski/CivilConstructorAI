package br.com.civilconstructorai.estrutura.dimensionamento;

import br.com.civilconstructorai.comum.EntidadeTenant;
import br.com.civilconstructorai.comum.ExcecaoDominio;
import br.com.civilconstructorai.estrutura.EstadoRascunho;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Job de dimensionamento (RF07): resultado por elemento + memória em JSON; aprovação por profissional habilitado. */
@Entity
@Table(name = "dimensionamento")
public class Dimensionamento extends EntidadeTenant {

    /** cópia da base de cálculo (CAA, agregado, catálogo, referências e edições) usada neste resultado */
    @Column(name = "base_calculo_json")
    private String baseCalculoJson;

    @Column(name = "obra_id", nullable = false)
    private UUID obraId;

    @Column(name = "analise_id", nullable = false)
    private UUID analiseId;

    @Column(name = "versao_modelo", nullable = false)
    private long versaoModelo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoRascunho estado = EstadoRascunho.PENDENTE;

    @Column(name = "resultados_json")
    private String resultadosJson;

    @Column(name = "aco_total_kg", nullable = false)
    private double acoTotalKg;

    @Column(name = "elementos_nao_verificam", nullable = false)
    private int elementosNaoVerificam;

    @Column(name = "mensagem_erro")
    private String mensagemErro;

    @Column(name = "aprovado_por")
    private UUID aprovadoPor;

    @Column(name = "decidido_em")
    private Instant decididoEm;

    @Column(name = "art_rrt")
    private String artRrt;

    protected Dimensionamento() {
    }

    public Dimensionamento(UUID tenantId, UUID obraId, UUID analiseId, long versaoModelo) {
        super(tenantId);
        this.obraId = obraId;
        this.analiseId = analiseId;
        this.versaoModelo = versaoModelo;
    }

    public void iniciar() {
        estado = EstadoRascunho.MAQUINA.transicionar(estado, EstadoRascunho.EM_EXECUCAO);
    }

    public void concluir(String resultadosJson, double acoTotalKg, int naoVerificam) {
        estado = EstadoRascunho.MAQUINA.transicionar(estado, EstadoRascunho.CALCULADO);
        this.resultadosJson = resultadosJson;
        this.acoTotalKg = acoTotalKg;
        this.elementosNaoVerificam = naoVerificam;
    }

    public void falhar(String mensagem) {
        estado = EstadoRascunho.MAQUINA.transicionar(estado, EstadoRascunho.FALHOU);
        this.mensagemErro = mensagem;
    }

    public void enviarParaRevisao() {
        estado = EstadoRascunho.MAQUINA.transicionar(estado, EstadoRascunho.EM_REVISAO);
    }

    public void aprovar(UUID aprovador, String artRrt, Instant quando) {
        if (elementosNaoVerificam > 0) {
            throw new ExcecaoDominio.RegraDeNegocio("elementos_nao_verificam", elementosNaoVerificam + " elemento(s) não verifica(m); corrija antes de aprovar.");
        }
        if (artRrt == null || artRrt.isBlank()) {
            throw new ExcecaoDominio.Validacao("artRrt", "Informe o número da ART/RRT da aprovação.");
        }
        if (estado == EstadoRascunho.CALCULADO) {
            enviarParaRevisao();
        }
        estado = EstadoRascunho.MAQUINA.transicionar(estado, EstadoRascunho.APROVADO);
        this.aprovadoPor = aprovador;
        this.artRrt = artRrt;
        this.decididoEm = quando;
    }

    public void rejeitar(UUID quem, Instant quando) {
        if (estado == EstadoRascunho.CALCULADO) {
            enviarParaRevisao();
        }
        estado = EstadoRascunho.MAQUINA.transicionar(estado, EstadoRascunho.REJEITADO);
        this.aprovadoPor = quem;
        this.decididoEm = quando;
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
    public String getResultadosJson() { return resultadosJson; }
    public double getAcoTotalKg() { return acoTotalKg; }
    public int getElementosNaoVerificam() { return elementosNaoVerificam; }
    public String getMensagemErro() { return mensagemErro; }
    public UUID getAprovadoPor() { return aprovadoPor; }
    public Instant getDecididoEm() { return decididoEm; }
    public String getArtRrt() { return artRrt; }

    public String getBaseCalculoJson() { return baseCalculoJson; }

    public void registrarBaseCalculo(String json) { this.baseCalculoJson = json; }
}
