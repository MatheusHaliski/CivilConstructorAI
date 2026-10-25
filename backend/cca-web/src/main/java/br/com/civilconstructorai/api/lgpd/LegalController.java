package br.com.civilconstructorai.api.lgpd;

import br.com.civilconstructorai.comum.ContextoUsuario;
import br.com.civilconstructorai.lgpd.DocumentosLegaisService;
import br.com.civilconstructorai.lgpd.Portas.ConfigLgpd;
import br.com.civilconstructorai.lgpd.Portas.HashProtegido;
import br.com.civilconstructorai.lgpd.TipoDocumentoLegal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** RF24/RF25 · documentos legais vigentes (público), reaceite e contato com o Encarregado (Art. 41). */
@RestController
public class LegalController {

    public record Reaceite(@NotNull TipoDocumentoLegal documento, @NotBlank String versao) {
    }

    private final DocumentosLegaisService documentos;
    private final ContextoUsuario contexto;
    private final HashProtegido hash;
    private final ConfigLgpd config;

    public LegalController(DocumentosLegaisService documentos, ContextoUsuario contexto, HashProtegido hash, ConfigLgpd config) {
        this.documentos = documentos;
        this.contexto = contexto;
        this.hash = hash;
        this.config = config;
    }

    @GetMapping("/api/v1/legal/documentos")
    public List<DocumentosLegaisService.Documento> documentos() {
        return documentos.vigentes();
    }

    @GetMapping("/api/v1/legal/encarregado")
    public Map<String, Object> encarregado() {
        return Map.of("nome", config.dpoNome(), "canal", config.dpoEmail(), "prazoRespostaDias", config.prazoRespostaDias(),
                "controladora", Map.of("razaoSocial", config.controladoraRazaoSocial(), "cnpj", config.controladoraCnpj()),
                "baseLegal", "LGPD Art. 41");
    }

    @GetMapping("/api/v1/me/legal/pendencias")
    public Map<String, String> pendencias() {
        return documentos.pendencias(contexto.exigir().contaId());
    }

    @PostMapping("/api/v1/me/legal/reaceite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reaceitar(@Valid @RequestBody Reaceite corpo, HttpServletRequest req) {
        documentos.reaceitar(contexto.exigir().contaId(), corpo.documento(), corpo.versao(), hash.hash("ip", req.getRemoteAddr()), req.getHeader("User-Agent"));
    }
}
