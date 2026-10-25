package br.com.civilconstructorai.api.material;

import br.com.civilconstructorai.material.MateriaisService;
import br.com.civilconstructorai.obra.BaseCalculoService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Catálogo técnico de materiais e materiais personalizados da empresa. */
@RestController
@RequestMapping("/api/v1/materiais")
public class MateriaisController {

    private final MateriaisService materiais;
    private final BaseCalculoService bases;

    public MateriaisController(MateriaisService materiais, BaseCalculoService bases) {
        this.materiais = materiais;
        this.bases = bases;
    }

    /** com obraId, as propriedades derivadas usam o agregado declarado na base de cálculo da obra */
    @GetMapping("/catalogo")
    public Map<String, Object> catalogo(@RequestParam(required = false) UUID obraId) {
        String agregado = obraId == null ? null : bases.obter(obraId).agregado();
        return materiais.catalogo(agregado);
    }

    @GetMapping("/personalizados")
    public List<MateriaisService.Personalizado> personalizados() {
        return materiais.listarPersonalizados();
    }

    @PostMapping("/personalizados")
    @ResponseStatus(HttpStatus.CREATED)
    public MateriaisService.Personalizado criar(@RequestBody MateriaisService.NovoMaterial n) {
        return materiais.criar(n);
    }

    public record Decisao(boolean validar, String motivo) {
    }

    @PostMapping("/personalizados/{id}/decisao")
    public MateriaisService.Personalizado decidir(@PathVariable UUID id, @RequestBody Decisao d) {
        return materiais.decidir(id, d.validar(), d.motivo());
    }

    @DeleteMapping("/personalizados/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable UUID id) {
        materiais.excluir(id);
    }
}
