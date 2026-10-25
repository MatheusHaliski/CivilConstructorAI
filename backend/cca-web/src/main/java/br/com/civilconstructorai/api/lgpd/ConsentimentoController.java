package br.com.civilconstructorai.api.lgpd;

import br.com.civilconstructorai.comum.ContextoUsuario;
import br.com.civilconstructorai.lgpd.ConsentimentoService;
import br.com.civilconstructorai.lgpd.Finalidade;
import br.com.civilconstructorai.lgpd.Portas.HashProtegido;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** RF24 · um toggle por finalidade; conceder e revogar pelo mesmo controle; histórico imutável. */
@RestController
@RequestMapping("/api/v1/me/consentimentos")
public class ConsentimentoController {

    public record Alteracao(@NotNull Boolean concedido) {
    }

    private final ConsentimentoService service;
    private final ContextoUsuario contexto;
    private final HashProtegido hash;

    public ConsentimentoController(ConsentimentoService service, ContextoUsuario contexto, HashProtegido hash) {
        this.service = service;
        this.contexto = contexto;
        this.hash = hash;
    }

    @GetMapping
    public List<ConsentimentoService.Item> listar() {
        return service.listar(contexto.exigir().contaId());
    }

    @PutMapping("/{finalidade}")
    public ConsentimentoService.Item alterar(@PathVariable Finalidade finalidade, @Valid @RequestBody Alteracao corpo, HttpServletRequest req) {
        return service.alterar(contexto.exigir().contaId(), finalidade, corpo.concedido(), hash.hash("ip", req.getRemoteAddr()));
    }

    @GetMapping("/historico")
    public List<Map<String, Object>> historico() {
        return service.historico(contexto.exigir().contaId()).stream()
                .map(e -> Map.<String, Object>of("finalidade", e.getFinalidade(), "acao", e.getAcao(), "versaoTexto", e.getVersaoTexto(), "ocorridoEm", e.getOcorridoEm()))
                .toList();
    }
}
