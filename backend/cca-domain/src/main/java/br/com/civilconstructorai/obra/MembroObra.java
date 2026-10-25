package br.com.civilconstructorai.obra;

import br.com.civilconstructorai.acesso.PapelObra;
import br.com.civilconstructorai.comum.EntidadeTenant;
import br.com.civilconstructorai.comum.ExcecaoDominio;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

/** Papel de uma pessoa numa obra. DONO/ADMIN não entram aqui (são de empresa). */
@Entity
@Table(name = "membro_obra")
public class MembroObra extends EntidadeTenant {

    @Column(name = "obra_id", nullable = false)
    private UUID obraId;

    @Column(name = "conta_id", nullable = false)
    private UUID contaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PapelObra papel;

    protected MembroObra() {
    }

    public MembroObra(UUID empresaId, UUID obraId, UUID contaId, PapelObra papel) {
        super(empresaId);
        if (papel.escopoEmpresa()) {
            throw new ExcecaoDominio.Validacao("papel", "DONO e ADMIN são papéis de empresa, não de obra.");
        }
        this.obraId = obraId;
        this.contaId = contaId;
        this.papel = papel;
    }

    public void mudarPapel(PapelObra novo) {
        if (novo.escopoEmpresa()) {
            throw new ExcecaoDominio.Validacao("papel", "DONO e ADMIN são papéis de empresa, não de obra.");
        }
        this.papel = novo;
    }

    public UUID getObraId() { return obraId; }
    public UUID getContaId() { return contaId; }
    public PapelObra getPapel() { return papel; }
}
