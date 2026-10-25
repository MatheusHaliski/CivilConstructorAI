package br.com.civilconstructorai.api.lgpd;

import br.com.civilconstructorai.lgpd.ExclusaoContaService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * RF26 · exclusão com carência. O alertdialog pede caixa "Entendo" + senha + código (modo sudo, RF23);
 * {@code POST /auth/reactivate} (módulo de identidade) chama {@link ExclusaoContaService#reativar}.
 */
@RestController
@RequestMapping("/api/v1/me/deletion")
public class DeletionController {

    public record Pedido(@NotNull Boolean entendi) {
    }

    private final ExclusaoContaService service;

    public DeletionController(ExclusaoContaService service) {
        this.service = service;
    }

    /** o que fica e o que sai, e a data final da carência — para o alertdialog */
    @GetMapping
    public ExclusaoContaService.Consequencias consequencias() {
        return service.consequencias();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ExclusaoContaService.Consequencias solicitar(@Valid @RequestBody Pedido corpo) {
        return service.solicitar(corpo.entendi());
    }
}
