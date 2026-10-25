package br.com.civilconstructorai.api.prancha;

import br.com.civilconstructorai.prancha.PranchaRepositorio.Prancha;
import br.com.civilconstructorai.prancha.PranchaRepositorio.Tipo;
import br.com.civilconstructorai.prancha.PranchaService;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** RF14 · pranchas: solicitar, acompanhar, baixar (URL pré-assinada 15 min) e emitir. */
@RestController
@RequestMapping("/api/v1/obras/{obraId}/pranchas")
public class PranchaController {

    private final PranchaService service;

    public PranchaController(PranchaService service) {
        this.service = service;
    }

    public record SolicitarReq(UUID pavimentoId, @NotNull Tipo tipo, String escala) {
    }

    public record EmitirReq(String artRrt) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Prancha solicitar(@PathVariable UUID obraId, @jakarta.validation.Valid @RequestBody SolicitarReq req) {
        return service.solicitar(obraId, req.pavimentoId(), req.tipo(), req.escala());
    }

    @GetMapping
    public List<Prancha> listar(@PathVariable UUID obraId) {
        return service.listar(obraId);
    }

    @GetMapping("/{id}")
    public Prancha buscar(@PathVariable UUID obraId, @PathVariable UUID id) {
        return service.buscar(obraId, id);
    }

    @GetMapping("/{id}/download")
    public Map<String, URI> download(@PathVariable UUID obraId, @PathVariable UUID id) {
        return service.download(obraId, id);
    }

    @PostMapping("/{id}/emitir")
    public Prancha emitir(@PathVariable UUID obraId, @PathVariable UUID id, @RequestBody EmitirReq req) {
        return service.emitir(obraId, id, req.artRrt());
    }

    /** processa a fila agora (útil em dev/testes; o scheduler faz o mesmo a cada poucos segundos) */
    @PostMapping("/processar")
    public Map<String, String> processar(@PathVariable UUID obraId) {
        service.processarPendentes();
        return Map.of("status", "ok");
    }
}
