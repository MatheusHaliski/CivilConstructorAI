package br.com.civilconstructorai.api.obra;

import br.com.civilconstructorai.obra.BaseCalculoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Base de cálculo da obra: finalidade, sistema construtivo, CAA, agregado, catálogo e referências adotadas. */
@RestController
@RequestMapping("/api/v1/obras/{obraId}/base-calculo")
public class BaseCalculoController {

    private final BaseCalculoService bases;

    public BaseCalculoController(BaseCalculoService bases) {
        this.bases = bases;
    }

    @GetMapping
    public BaseCalculoService.Visao obter(@PathVariable UUID obraId) {
        return bases.obter(obraId);
    }

    @PutMapping
    public BaseCalculoService.Visao atualizar(@PathVariable UUID obraId, @RequestBody BaseCalculoService.Alteracao a) {
        return bases.atualizar(obraId, a);
    }
}
