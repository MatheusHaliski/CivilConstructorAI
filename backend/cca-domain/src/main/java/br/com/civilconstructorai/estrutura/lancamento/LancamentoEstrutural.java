package br.com.civilconstructorai.estrutura.lancamento;

import br.com.civilconstructorai.comum.EntidadeTenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

/** Lançamento estrutural de um pavimento: estado e combinações geradas (JSON). */
@Entity
@Table(name = "lancamento_estrutural")
public class LancamentoEstrutural extends EntidadeTenant {

    @Column(name = "obra_id", nullable = false)
    private UUID obraId;

    @Column(name = "pavimento_id", nullable = false)
    private String pavimentoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoLancamento estado = EstadoLancamento.VAZIO;

    @Column(name = "combinacoes_json", nullable = false)
    private String combinacoesJson = "[]";

    @Column(name = "vento_json")
    private String ventoJson;

    protected LancamentoEstrutural() {
    }

    public LancamentoEstrutural(UUID tenantId, UUID obraId, String pavimentoId) {
        super(tenantId);
        this.obraId = obraId;
        this.pavimentoId = pavimentoId;
    }

    public void transicionar(EstadoLancamento para) {
        this.estado = EstadoLancamento.MAQUINA.transicionar(estado, para);
    }

    public UUID getObraId() { return obraId; }
    public String getPavimentoId() { return pavimentoId; }
    public EstadoLancamento getEstado() { return estado; }
    public String getCombinacoesJson() { return combinacoesJson; }
    public void setCombinacoesJson(String combinacoesJson) { this.combinacoesJson = combinacoesJson; }
    public String getVentoJson() { return ventoJson; }
    public void setVentoJson(String ventoJson) { this.ventoJson = ventoJson; }
}
