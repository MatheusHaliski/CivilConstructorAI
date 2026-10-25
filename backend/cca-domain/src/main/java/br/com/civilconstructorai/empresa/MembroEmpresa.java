package br.com.civilconstructorai.empresa;

import br.com.civilconstructorai.acesso.PapelObra;
import br.com.civilconstructorai.comum.EntidadeTenant;
import br.com.civilconstructorai.comum.ExcecaoDominio;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

/** Vínculo de escopo de empresa: DONO ou ADMIN (valem para todas as obras). */
@Entity
@Table(name = "membro_empresa")
public class MembroEmpresa extends EntidadeTenant {

    @Column(name = "conta_id", nullable = false)
    private UUID contaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PapelObra papel;

    protected MembroEmpresa() {
    }

    public MembroEmpresa(UUID empresaId, UUID contaId, PapelObra papel) {
        super(empresaId);
        if (!papel.escopoEmpresa()) {
            throw new ExcecaoDominio.Validacao("papel", "Papel de empresa deve ser DONO ou ADMIN.");
        }
        this.contaId = contaId;
        this.papel = papel;
    }

    public void mudarPapel(PapelObra novo) {
        if (!novo.escopoEmpresa()) {
            throw new ExcecaoDominio.Validacao("papel", "Papel de empresa deve ser DONO ou ADMIN.");
        }
        this.papel = novo;
    }

    public UUID getContaId() { return contaId; }
    public PapelObra getPapel() { return papel; }
}
