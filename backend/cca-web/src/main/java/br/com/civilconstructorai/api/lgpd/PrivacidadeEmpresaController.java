package br.com.civilconstructorai.api.lgpd;

import br.com.civilconstructorai.lgpd.CategoriaDado;
import br.com.civilconstructorai.lgpd.InventarioService;
import br.com.civilconstructorai.lgpd.PedidoTitular;
import br.com.civilconstructorai.lgpd.PedidoTitularService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** RF25 · "Dados dos trabalhadores" (empresa controladora): inventário, retenção e fila de pedidos (Art. 37/39). */
@RestController
@RequestMapping("/api/v1/empresas/{empresaId}/privacidade")
public class PrivacidadeEmpresaController {

    public record Retencao(@NotNull @Min(0) Integer prazoMeses) {
    }

    public record Resposta(@NotNull PedidoTitular.Estado estado, String resposta) {
    }

    private final InventarioService inventario;
    private final PedidoTitularService pedidos;

    public PrivacidadeEmpresaController(InventarioService inventario, PedidoTitularService pedidos) {
        this.inventario = inventario;
        this.pedidos = pedidos;
    }

    @GetMapping("/inventario")
    public List<InventarioService.Linha> inventario(@PathVariable UUID empresaId) {
        return inventario.inventario(empresaId);
    }

    @PutMapping("/retencao/{categoria}")
    public InventarioService.Linha retencao(@PathVariable UUID empresaId, @PathVariable CategoriaDado categoria, @Valid @RequestBody Retencao corpo) {
        return inventario.configurarRetencao(empresaId, categoria, corpo.prazoMeses());
    }

    @GetMapping("/pedidos")
    public List<Map<String, Object>> fila(@PathVariable UUID empresaId) {
        return pedidos.filaDaEmpresa(empresaId).stream().map(DadosTitularController::pedido).toList();
    }

    @PatchMapping("/pedidos/{pedidoId}")
    public Map<String, Object> responder(@PathVariable UUID empresaId, @PathVariable UUID pedidoId, @Valid @RequestBody Resposta corpo) {
        return DadosTitularController.pedido(pedidos.responder(empresaId, pedidoId, corpo.estado(), corpo.resposta()));
    }
}
