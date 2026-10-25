package br.com.civilconstructorai.api.empresa;

import br.com.civilconstructorai.comum.ExcecaoDominio;
import br.com.civilconstructorai.empresa.ConviteService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

/**
 * RF20 · lado de quem recebe o convite. O resumo é público ({@code /convites/{token}/publico}, liberado na
 * SecurityFilterChain); aceitar/recusar exigem sessão. Convite expirado, usado ou revogado → 410.
 */
@RestController
@RequestMapping("/api/v1")
public class ConviteController {

    private final ConviteService convites;

    public ConviteController(ConviteService convites) {
        this.convites = convites;
    }

    @GetMapping("/convites/{token}/publico")
    public Map<String, Object> publico(@PathVariable String token) {
        return EmpresaController.mapa(convites.publico(token));
    }

    @PostMapping("/invites/{token}/accept")
    public Map<String, Object> aceitar(@PathVariable String token) {
        UUID empresaId = convites.aceitar(token);
        return Map.of("empresaId", empresaId, "mensagem", "Convite aceito. Escolha esta empresa como ativa para começar.");
    }

    @PostMapping("/invites/{token}/decline")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void recusar(@PathVariable String token) {
        convites.recusar(token);
    }

    /** convite indisponível/expirado → 410 Gone (regra de negócio específica deste recurso) */
    @ExceptionHandler(ExcecaoDominio.RegraDeNegocio.class)
    ProblemDetail indisponivel(ExcecaoDominio.RegraDeNegocio e) {
        var status = e.codigo().startsWith("convite_") ? HttpStatus.GONE : HttpStatus.UNPROCESSABLE_CONTENT;
        var p = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        p.setTitle(status == HttpStatus.GONE ? "Convite indisponível" : "Dados inválidos");
        p.setType(URI.create("https://civilconstructorai.com.br/problemas/" + e.codigo()));
        return p;
    }
}
