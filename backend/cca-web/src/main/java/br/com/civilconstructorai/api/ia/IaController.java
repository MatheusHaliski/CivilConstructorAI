package br.com.civilconstructorai.api.ia;

import br.com.civilconstructorai.ia.UsoIaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** RF27 · tela "Uso de IA" (Art. 9º, 20 e 33) e consumo por empresa. */
@RestController
public class IaController {

    private final UsoIaService uso;

    public IaController(UsoIaService uso) {
        this.uso = uso;
    }

    @GetMapping("/api/v1/ia/uso")
    public List<UsoIaService.Rota> uso() {
        return uso.catalogo();
    }

    @GetMapping("/api/v1/empresas/{empresaId}/ia/consumo")
    public List<UsoIaService.Consumo> consumo(@PathVariable UUID empresaId) {
        return uso.consumo(empresaId);
    }
}
