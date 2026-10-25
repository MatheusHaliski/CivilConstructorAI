package br.com.civilconstructorai.api.estrutura;

import br.com.civilconstructorai.estrutura.AnaliseService;
import br.com.civilconstructorai.estrutura.DimensionamentoService;
import br.com.civilconstructorai.estrutura.analise.AnaliseEstrutural;
import br.com.civilconstructorai.estrutura.dimensionamento.Dimensionamento;
import br.com.civilconstructorai.estrutura.mef.SolverMef;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** RF05 análise estrutural (MEF) · RF07 dimensionamento. */
@RestController
@RequestMapping("/api/v1/obras/{obraId}")
public class AnaliseController {

    private final AnaliseService analises;
    private final DimensionamentoService dimensionamentos;

    public AnaliseController(AnaliseService analises, DimensionamentoService dimensionamentos) {
        this.analises = analises;
        this.dimensionamentos = dimensionamentos;
    }

    public record DimensionamentoRequest(@NotNull UUID analiseId) {
    }

    public record AprovacaoRequest(String artRrt) {
    }

    @PostMapping("/analises")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AnaliseEstrutural solicitar(@PathVariable UUID obraId) {
        return analises.solicitar(obraId);
    }

    @GetMapping("/analises")
    public List<AnaliseEstrutural> listar(@PathVariable UUID obraId) {
        return analises.listar(obraId);
    }

    @GetMapping("/analises/{analiseId}")
    public AnaliseEstrutural obter(@PathVariable UUID obraId, @PathVariable UUID analiseId) {
        return analises.obter(obraId, analiseId);
    }

    @GetMapping("/analises/{analiseId}/diagramas")
    public SolverMef.ResultadoAnalise diagramas(@PathVariable UUID obraId, @PathVariable UUID analiseId) {
        return analises.resultado(obraId, analiseId);
    }

    @PostMapping("/dimensionamentos")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Dimensionamento dimensionar(@PathVariable UUID obraId, @RequestBody DimensionamentoRequest r) {
        return dimensionamentos.solicitar(obraId, r.analiseId());
    }

    @GetMapping("/dimensionamentos")
    public List<Dimensionamento> listarDimensionamentos(@PathVariable UUID obraId) {
        return dimensionamentos.listar(obraId);
    }

    @GetMapping("/dimensionamentos/{id}")
    public Dimensionamento obterDimensionamento(@PathVariable UUID obraId, @PathVariable UUID id) {
        return dimensionamentos.obter(obraId, id);
    }

    @GetMapping("/dimensionamentos/{id}/resultados")
    public List<DimensionamentoService.ResultadoElemento> resultados(@PathVariable UUID obraId, @PathVariable UUID id) {
        return dimensionamentos.resultados(obraId, id);
    }

    @PostMapping("/dimensionamentos/{id}/aprovar")
    public Dimensionamento aprovar(@PathVariable UUID obraId, @PathVariable UUID id, @RequestBody(required = false) AprovacaoRequest r) {
        return dimensionamentos.decidir(obraId, id, true, r == null ? null : r.artRrt());
    }

    @PostMapping("/dimensionamentos/{id}/rejeitar")
    public Dimensionamento rejeitar(@PathVariable UUID obraId, @PathVariable UUID id) {
        return dimensionamentos.decidir(obraId, id, false, null);
    }
}
