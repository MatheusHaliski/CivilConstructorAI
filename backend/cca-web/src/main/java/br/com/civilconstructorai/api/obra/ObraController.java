package br.com.civilconstructorai.api.obra;

import br.com.civilconstructorai.acesso.PapelObra;
import br.com.civilconstructorai.obra.EstadoObra;
import br.com.civilconstructorai.obra.ObraService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** RF22 · /api/v1/obras: obras da empresa ativa, estados, membros e papéis. */
@RestController
@RequestMapping("/api/v1/obras")
public class ObraController {

    private final ObraService obras;

    public ObraController(ObraService obras) {
        this.obras = obras;
    }

    @GetMapping
    public List<ObraService.Resumo> listar() {
        return obras.listar();
    }

    public record CriarObra(@NotBlank String nome, String endereco, String municipio, String uf, List<double[]> perimetro, double[] localizacao) {
    }

    @PostMapping
    public ResponseEntity<ObraService.Resumo> criar(@RequestBody CriarObra r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(obras.criar(r.nome(), r.endereco(), r.municipio(), r.uf(), r.perimetro(), r.localizacao()));
    }

    @GetMapping("/{obraId}")
    public ObraService.Resumo obter(@PathVariable UUID obraId) {
        return obras.obter(obraId);
    }

    public record AtualizarObra(String nome, String endereco, String municipio, String uf, String artRrt, List<double[]> perimetro, double[] localizacao) {
    }

    @PatchMapping("/{obraId}")
    public ObraService.Resumo atualizar(@PathVariable UUID obraId, @RequestBody AtualizarObra r) {
        return obras.atualizar(obraId, r.nome(), r.endereco(), r.municipio(), r.uf(), r.artRrt(), r.perimetro(), r.localizacao());
    }

    public record Transicao(@NotNull EstadoObra para, String motivo) {
    }

    @PostMapping("/{obraId}/transicoes")
    public ObraService.Resumo transicionar(@PathVariable UUID obraId, @RequestBody Transicao t) {
        return obras.transicionar(obraId, t.para(), t.motivo());
    }

    public record Responsavel(@NotNull UUID contaId, String artRrt) {
    }

    @PutMapping("/{obraId}/responsavel-tecnico")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void responsavel(@PathVariable UUID obraId, @RequestBody Responsavel r) {
        obras.definirResponsavelTecnico(obraId, r.contaId(), r.artRrt());
    }

    @GetMapping("/{obraId}/membros")
    public List<ObraService.Membro> membros(@PathVariable UUID obraId) {
        return obras.membros(obraId);
    }

    public record PapelRequest(@NotNull PapelObra papel) {
    }

    @PatchMapping("/{obraId}/membros/{contaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void mudarPapel(@PathVariable UUID obraId, @PathVariable UUID contaId, @RequestBody PapelRequest r) {
        obras.mudarPapel(obraId, contaId, r.papel());
    }

    @DeleteMapping("/{obraId}/membros/{contaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remover(@PathVariable UUID obraId, @PathVariable UUID contaId) {
        obras.removerMembro(obraId, contaId);
    }

    public record Trabalhador(UUID contaId) {
    }

    @PostMapping("/{obraId}/trabalhadores")
    public ResponseEntity<Map<String, Object>> trabalhador(@PathVariable UUID obraId, @RequestBody(required = false) Trabalhador t) {
        var id = obras.cadastrarTrabalhador(obraId, t == null ? null : t.contaId());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("contaId", id));
    }
}
