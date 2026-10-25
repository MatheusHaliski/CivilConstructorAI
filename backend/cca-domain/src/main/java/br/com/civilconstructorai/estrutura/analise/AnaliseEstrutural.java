package br.com.civilconstructorai.estrutura.analise;

import br.com.civilconstructorai.comum.EntidadeTenant;
import br.com.civilconstructorai.comum.EstadoJob;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Job de análise estrutural (RF05): versionado pela versão do modelo; resultado em JSON. */
@Entity
@Table(name = "analise_estrutural")
public class AnaliseEstrutural extends EntidadeTenant {

    /** cópia da base de cálculo (CAA, agregado, catálogo, referências e edições) usada neste resultado */
    @Column(name = "base_calculo_json")
    private String baseCalculoJson;

    @Column(name = "obra_id", nullable = false)
    private UUID obraId;

    @Column(name = "versao_modelo", nullable = false)
    private long versaoModelo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoJob estado = EstadoJob.PENDENTE;

    @Column(name = "solicitada_por", nullable = false)
    private UUID solicitadaPor;

    @Column(name = "resultado_json")
    private String resultadoJson;

    @Column(name = "resumo_json")
    private String resumoJson;

    @Column(name = "mensagem_erro")
    private String mensagemErro;

    @Column(name = "iniciada_em")
    private Instant iniciadaEm;

    @Column(name = "concluida_em")
    private Instant concluidaEm;

    protected AnaliseEstrutural() {
    }

    public AnaliseEstrutural(UUID tenantId, UUID obraId, long versaoModelo, UUID solicitadaPor) {
        super(tenantId);
        this.obraId = obraId;
        this.versaoModelo = versaoModelo;
        this.solicitadaPor = solicitadaPor;
    }

    public void iniciar(Instant quando) {
        estado = EstadoJob.MAQUINA.transicionar(estado, EstadoJob.EM_EXECUCAO);
        iniciadaEm = quando;
    }

    public void concluir(String resultadoJson, String resumoJson, Instant quando) {
        estado = EstadoJob.MAQUINA.transicionar(estado, EstadoJob.CONCLUIDO);
        this.resultadoJson = resultadoJson;
        this.resumoJson = resumoJson;
        this.concluidaEm = quando;
    }

    public void falhar(String mensagem, Instant quando) {
        estado = EstadoJob.MAQUINA.transicionar(estado, EstadoJob.FALHOU);
        this.mensagemErro = mensagem;
        this.concluidaEm = quando;
    }

    public void tornarObsoleta() {
        if (!EstadoJob.MAQUINA.terminal(estado)) {
            estado = EstadoJob.MAQUINA.transicionar(estado, EstadoJob.OBSOLETO);
        }
    }

    public UUID getObraId() { return obraId; }
    public long getVersaoModelo() { return versaoModelo; }
    public EstadoJob getEstado() { return estado; }
    public UUID getSolicitadaPor() { return solicitadaPor; }
    public String getResultadoJson() { return resultadoJson; }
    public String getResumoJson() { return resumoJson; }
    public String getMensagemErro() { return mensagemErro; }
    public Instant getIniciadaEm() { return iniciadaEm; }
    public Instant getConcluidaEm() { return concluidaEm; }

    public String getBaseCalculoJson() { return baseCalculoJson; }

    public void registrarBaseCalculo(String json) { this.baseCalculoJson = json; }
}
