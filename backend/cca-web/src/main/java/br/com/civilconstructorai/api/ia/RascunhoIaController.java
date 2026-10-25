package br.com.civilconstructorai.api.ia;

import br.com.civilconstructorai.ia.EstadoRascunhoIa;
import br.com.civilconstructorai.ia.PedidoRevisaoHumana;
import br.com.civilconstructorai.ia.RascunhoIa;
import br.com.civilconstructorai.ia.RascunhoIaService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** RF27 · rascunhos "gerados por IA": revisão, aprovação por profissional habilitado, rejeição e revisão humana. */
@RestController
@RequestMapping("/api/v1/obras/{obraId}/rascunhos-ia")
public class RascunhoIaController {

    public record Aprovacao(@NotBlank String artRrt) {
    }

    public record Motivo(@NotBlank String motivo) {
    }

    public record RespostaRevisao(@NotBlank String resposta) {
    }

    private final RascunhoIaService service;

    public RascunhoIaController(RascunhoIaService service) {
        this.service = service;
    }

    @GetMapping
    public List<Map<String, Object>> listar(@PathVariable UUID obraId, @RequestParam(required = false) EstadoRascunhoIa estado) {
        return service.listar(obraId, estado).stream().map(RascunhoIaController::json).toList();
    }

    @GetMapping("/{id}")
    public Map<String, Object> obter(@PathVariable UUID obraId, @PathVariable UUID id) {
        return json(service.obter(obraId, id));
    }

    @PostMapping("/{id}/revisao")
    public Map<String, Object> enviarParaRevisao(@PathVariable UUID obraId, @PathVariable UUID id) {
        return json(service.enviarParaRevisao(obraId, id));
    }

    @PostMapping("/{id}/aprovar")
    public Map<String, Object> aprovar(@PathVariable UUID obraId, @PathVariable UUID id, @Valid @RequestBody Aprovacao corpo) {
        return json(service.aprovar(obraId, id, corpo.artRrt()));
    }

    @PostMapping("/{id}/rejeitar")
    public Map<String, Object> rejeitar(@PathVariable UUID obraId, @PathVariable UUID id, @Valid @RequestBody Motivo corpo) {
        return json(service.rejeitar(obraId, id, corpo.motivo()));
    }

    @PostMapping("/{id}/revisao-humana")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> pedirRevisaoHumana(@PathVariable UUID obraId, @PathVariable UUID id, @Valid @RequestBody Motivo corpo) {
        return json(service.pedirRevisaoHumana(obraId, id, corpo.motivo()));
    }

    @GetMapping("/{id}/revisao-humana")
    public List<Map<String, Object>> revisoes(@PathVariable UUID obraId, @PathVariable UUID id) {
        return service.revisoes(obraId, id).stream().map(RascunhoIaController::json).toList();
    }

    @PostMapping("/{id}/revisao-humana/{pedidoId}/responder")
    public Map<String, Object> responderRevisao(@PathVariable UUID obraId, @PathVariable UUID id, @PathVariable UUID pedidoId, @Valid @RequestBody RespostaRevisao corpo) {
        return json(service.responderRevisao(obraId, id, pedidoId, corpo.resposta()));
    }

    static Map<String, Object> json(RascunhoIa r) {
        var m = new LinkedHashMap<String, Object>();
        m.put("id", r.getId());
        m.put("obraId", r.getObraId());
        m.put("tipo", r.getTipo());
        m.put("rota", r.getRota());
        m.put("entidadeRef", r.getEntidadeRef());
        m.put("estado", r.getEstado());
        m.put("geradoPorIa", true);
        m.put("conteudo", r.getConteudo());
        m.put("criadoEm", r.getCriadoEm());
        m.put("aprovadoEm", r.getAprovadoEm());
        m.put("artRrt", r.getArtRrt());
        m.put("motivo", r.getMotivo());
        return m;
    }

    static Map<String, Object> json(PedidoRevisaoHumana p) {
        var m = new LinkedHashMap<String, Object>();
        m.put("id", p.getId());
        m.put("rascunhoId", p.getRascunhoId());
        m.put("estado", p.getEstado());
        m.put("motivo", p.getMotivo());
        m.put("prazoEm", p.getPrazoEm());
        m.put("resposta", p.getResposta());
        m.put("respondidoEm", p.getRespondidoEm());
        return m;
    }
}
