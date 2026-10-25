package br.com.civilconstructorai.api.quantitativo;

import br.com.civilconstructorai.quantitativo.QuantitativoService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** RF11 quantitativos: cálculo determinístico, exportação CSV/XLSX, liberação ao cliente. */
@RestController
@RequestMapping("/api/v1/obras/{obraId}/quantitativos")
public class QuantitativoController {

    private final QuantitativoService service;

    public QuantitativoController(QuantitativoService service) {
        this.service = service;
    }

    @GetMapping
    public QuantitativoService.Visao atual(@PathVariable UUID obraId) {
        return service.atual(obraId);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportar(@PathVariable UUID obraId, @RequestParam(defaultValue = "csv") String formato) {
        if ("xlsx".equalsIgnoreCase(formato)) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"quantitativos.xlsx\"")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(service.exportarXlsx(obraId));
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"quantitativos.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(service.exportarCsv(obraId));
    }

    @PostMapping("/{levantamentoId}/liberar-cliente")
    public void liberar(@PathVariable UUID obraId, @PathVariable UUID levantamentoId, @RequestParam(defaultValue = "true") boolean liberar) {
        service.liberarParaCliente(obraId, levantamentoId, liberar);
    }
}
