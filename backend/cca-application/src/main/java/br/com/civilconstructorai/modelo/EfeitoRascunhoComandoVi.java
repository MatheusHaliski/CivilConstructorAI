package br.com.civilconstructorai.modelo;

import br.com.civilconstructorai.ia.EfeitoDoRascunho;
import br.com.civilconstructorai.ia.RascunhoIa;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** RF02/RF27: decidir um rascunho de comando VI promove ou descarta os elementos que ele criou no modelo. */
@Component
public class EfeitoRascunhoComandoVi implements EfeitoDoRascunho {

    private final ModeloService modelo;

    public EfeitoRascunhoComandoVi(ModeloService modelo) {
        this.modelo = modelo;
    }

    @Override
    public String tipo() {
        return ModeloService.TIPO_RASCUNHO_COMANDO;
    }

    @Override
    public void aoAprovar(UUID obraId, RascunhoIa rascunho) {
        modelo.decidirRascunhoIa(obraId, rascunho.getId(), true);
    }

    @Override
    public void aoRejeitar(UUID obraId, RascunhoIa rascunho) {
        modelo.decidirRascunhoIa(obraId, rascunho.getId(), false);
    }
}
