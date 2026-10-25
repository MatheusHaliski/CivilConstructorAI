package br.com.civilconstructorai.estrutura.restricoes;

import br.com.civilconstructorai.comum.EntidadeTenant;
import br.com.civilconstructorai.comum.ExcecaoDominio;
import br.com.civilconstructorai.comum.MaquinaDeEstados;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Exceção normativa justificada (ex.: pilar-parede de 14 cm com laudo), aprovada por profissional habilitado. */
@Entity
@Table(name = "excecao_normativa")
public class ExcecaoNormativa extends EntidadeTenant {

    public enum Estado {
        SOLICITADA, ATIVA, RECUSADA, REVOGADA, OBSOLETA;

        public static final MaquinaDeEstados<Estado> MAQUINA = new MaquinaDeEstados<>("ExceçãoNormativa", Map.of(
                SOLICITADA, Set.of(ATIVA, RECUSADA, OBSOLETA),
                ATIVA, Set.of(REVOGADA, OBSOLETA),
                RECUSADA, Set.of(),
                REVOGADA, Set.of(),
                OBSOLETA, Set.of()));
    }

    @Column(name = "obra_id", nullable = false)
    private UUID obraId;

    @Column(nullable = false)
    private String regra;

    @Column(name = "elemento_id")
    private String elementoId;

    @Column(name = "valor_solicitado", nullable = false)
    private double valorSolicitado;

    @Column(nullable = false)
    private String justificativa;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Estado estado = Estado.SOLICITADA;

    @Column(name = "solicitada_por", nullable = false)
    private UUID solicitadaPor;

    @Column(name = "decidida_por")
    private UUID decididaPor;

    @Column(name = "decidida_em")
    private Instant decididaEm;

    protected ExcecaoNormativa() {
    }

    public ExcecaoNormativa(UUID tenantId, UUID obraId, String regra, String elementoId, double valorSolicitado, String justificativa, UUID solicitadaPor) {
        super(tenantId);
        if (justificativa == null || justificativa.length() < 20) {
            throw new ExcecaoDominio.Validacao("justificativa", "Justifique a exceção com ao menos 20 caracteres (norma, laudo ou ensaio).");
        }
        this.obraId = obraId;
        this.regra = regra;
        this.elementoId = elementoId;
        this.valorSolicitado = valorSolicitado;
        this.justificativa = justificativa;
        this.solicitadaPor = solicitadaPor;
    }

    public void decidir(boolean aprovar, UUID quem, Instant quando) {
        estado = Estado.MAQUINA.transicionar(estado, aprovar ? Estado.ATIVA : Estado.RECUSADA);
        decididaPor = quem;
        decididaEm = quando;
    }

    public void revogar(UUID quem, Instant quando) {
        estado = Estado.MAQUINA.transicionar(estado, Estado.REVOGADA);
        decididaPor = quem;
        decididaEm = quando;
    }

    public UUID getObraId() { return obraId; }
    public String getRegra() { return regra; }
    public String getElementoId() { return elementoId; }
    public double getValorSolicitado() { return valorSolicitado; }
    public String getJustificativa() { return justificativa; }
    public Estado getEstado() { return estado; }
    public UUID getSolicitadaPor() { return solicitadaPor; }
    public UUID getDecididaPor() { return decididaPor; }
    public Instant getDecididaEm() { return decididaEm; }
}
