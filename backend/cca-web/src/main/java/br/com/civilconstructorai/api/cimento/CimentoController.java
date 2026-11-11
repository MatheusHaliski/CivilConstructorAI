package br.com.civilconstructorai.api.cimento;

import br.com.civilconstructorai.cimento.CimentoService;
import br.com.civilconstructorai.cimento.DosagemCimentoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/api/v1/obras/{obraId}/cimentos")
public class CimentoController {
    public record Recomendacao(@NotNull UUID elementoId,
                              @NotNull CimentoService.Ambiente ambiente,
                              @NotNull CimentoService.Prioridade prioridade) { }
    private final CimentoService service;
    private final DosagemCimentoService dosagens;
    public CimentoController(CimentoService service, DosagemCimentoService dosagens) { this.service = service; this.dosagens=dosagens; }
    @PostMapping("/recomendacao")
    public CimentoService.Orientacao recomendar(@PathVariable UUID obraId, @Valid @RequestBody Recomendacao r) {
        return service.recomendar(obraId, new CimentoService.Pedido(r.elementoId(), r.ambiente(), r.prioridade()));
    }
    @PostMapping("/associacao")
    public DosagemCimentoService.Visao associar(@PathVariable UUID obraId, @Valid @RequestBody Associacao a) {
        return dosagens.verificarElemento(obraId,a.dosagemId(),a.elementoId(),a.cimentoId(),a.versaoModelo());
    }
    public record Associacao(@NotNull UUID dosagemId, @NotNull UUID elementoId, @jakarta.validation.constraints.NotBlank String cimentoId, @jakarta.validation.constraints.PositiveOrZero long versaoModelo) { }
    public record Confirmacao(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=2000) String referenciaValidacao) { }
    @GetMapping("/dosagens")
    public List<DosagemCimentoService.Visao> listar(@PathVariable UUID obraId, @RequestParam long versaoModelo) { return dosagens.listar(obraId,versaoModelo); }
    @PostMapping("/dosagens")
    public DosagemCimentoService.Visao registrar(@PathVariable UUID obraId, @Valid @RequestBody DosagemCimentoService.Registro r) { return dosagens.registrar(obraId,r); }
    @PostMapping("/dosagens/{id}/confirmar")
    public DosagemCimentoService.Visao confirmar(@PathVariable UUID obraId,@PathVariable UUID id,@Valid @RequestBody Confirmacao c) { return dosagens.confirmar(obraId,id,c.referenciaValidacao()); }
}
