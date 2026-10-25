package br.com.civilconstructorai.api.interop;

import br.com.civilconstructorai.interop.InteropRepositorio.Exportacao;
import br.com.civilconstructorai.interop.InteropRepositorio.Formato;
import br.com.civilconstructorai.interop.InteropRepositorio.Pareamento;
import br.com.civilconstructorai.interop.InteropService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** RF15 · exportação IFC/STEP, importação de referência e pareamento Revit (fase 2). */
@RestController
@RequestMapping("/api/v1")
public class InteropController {

    private final InteropService service;

    public InteropController(InteropService service) {
        this.service = service;
    }

    public record ExportarReq(Formato formato) {
    }

    @PostMapping("/obras/{obraId}/exportacoes")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Exportacao exportar(@PathVariable UUID obraId, @RequestBody ExportarReq req) {
        return service.solicitarExportacao(obraId, req.formato() == null ? Formato.IFC4X3 : req.formato());
    }

    @GetMapping("/obras/{obraId}/exportacoes")
    public List<Exportacao> listar(@PathVariable UUID obraId) {
        return service.listar(obraId);
    }

    @GetMapping("/obras/{obraId}/exportacoes/{id}")
    public Exportacao buscar(@PathVariable UUID obraId, @PathVariable UUID id) {
        return service.buscar(obraId, id);
    }

    @GetMapping("/obras/{obraId}/exportacoes/{id}/download")
    public Map<String, URI> download(@PathVariable UUID obraId, @PathVariable UUID id) {
        return Map.of("url", service.download(obraId, id));
    }

    @PostMapping("/obras/{obraId}/exportacoes/processar")
    public Map<String, String> processar(@PathVariable UUID obraId) {
        service.processarPendentes();
        return Map.of("status", "ok");
    }

    @PostMapping(value = "/obras/{obraId}/importacoes/ifc", consumes = "application/octet-stream")
    public Map<String, Object> importar(@PathVariable UUID obraId, @RequestBody byte[] conteudo) {
        var r = service.importarReferencia(obraId, conteudo);
        var out = new java.util.LinkedHashMap<String, Object>();
        out.put("valido", r.valido());
        out.put("schema", r.schema());
        out.put("totalEntidades", r.totalEntidades());
        out.put("porTipo", r.porTipo());
        out.put("problemas", r.problemas());
        return out;
    }

    // ---------------- Revit (pyRevit)
    @PostMapping("/obras/{obraId}/revit/pareamentos")
    @ResponseStatus(HttpStatus.CREATED)
    public InteropService.CodigoPareamento gerarCodigo(@PathVariable UUID obraId) {
        return service.gerarCodigo(obraId);
    }

    @GetMapping("/obras/{obraId}/revit/pareamentos")
    public List<Pareamento> pareamentos(@PathVariable UUID obraId) {
        return service.pareamentos(obraId);
    }

    @PostMapping("/obras/{obraId}/revit/pareamentos/{id}/revogar")
    public Pareamento revogar(@PathVariable UUID obraId, @PathVariable UUID id) {
        return service.revogar(obraId, id);
    }

    public record ParearReq(@NotBlank String codigo, String dispositivo) {
    }

    /** chamado pelo pyRevit (sem sessão de usuário): troca o código de uso único por token de dispositivo */
    @PostMapping("/revit/parear")
    public InteropService.TokenDispositivo parear(@jakarta.validation.Valid @RequestBody ParearReq req) {
        return service.parear(req.codigo(), req.dispositivo());
    }

    @GetMapping("/revit/comandos")
    public Map<String, Object> comandos(@RequestHeader("X-Revit-Token") String token, @RequestParam(defaultValue = "0") long desde) {
        return service.comandosParaRevit(token, desde);
    }
}
