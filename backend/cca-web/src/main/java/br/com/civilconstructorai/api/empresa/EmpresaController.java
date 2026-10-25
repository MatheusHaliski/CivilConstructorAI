package br.com.civilconstructorai.api.empresa;

import br.com.civilconstructorai.acesso.PapelObra;
import br.com.civilconstructorai.empresa.ConviteService;
import br.com.civilconstructorai.empresa.EmpresaService;
import br.com.civilconstructorai.empresa.EstadoConvite;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** RF20/RF22 · /api/v1/companies: empresa, membros DONO/ADMIN e convites. */
@RestController
@RequestMapping("/api/v1/companies")
public class EmpresaController {

    private final EmpresaService empresas;
    private final ConviteService convites;
    private final br.com.civilconstructorai.identidade.LoginService login;

    public EmpresaController(EmpresaService empresas, ConviteService convites, br.com.civilconstructorai.identidade.LoginService login) {
        this.login = login;
        this.empresas = empresas;
        this.convites = convites;
    }

    public record CriarEmpresa(@NotBlank String razaoSocial, String nomeFantasia, @NotBlank String cnpj) {
    }

    @PostMapping
    public ResponseEntity<EmpresaService.Resumo> criar(@RequestBody CriarEmpresa r) {
        var criada = empresas.criar(r.razaoSocial(), r.nomeFantasia(), r.cnpj());
        // a empresa recém-criada vira a ativa: a sessão já é de DONO e, sem 2FA, só a própria conta responde (RF19 CA-06)
        login.trocarEmpresaAtiva(criada.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(criada);
    }

    @GetMapping("/{empresaId}")
    public EmpresaService.Resumo obter(@PathVariable UUID empresaId) {
        return empresas.obter(empresaId);
    }

    public record AtualizarEmpresa(String razaoSocial, String nomeFantasia, String encarregadoContato) {
    }

    @PatchMapping("/{empresaId}")
    public EmpresaService.Resumo atualizar(@PathVariable UUID empresaId, @RequestBody AtualizarEmpresa r) {
        return empresas.atualizar(empresaId, r.razaoSocial(), r.nomeFantasia(), r.encarregadoContato());
    }

    @GetMapping("/{empresaId}/membros")
    public List<EmpresaService.Membro> membros(@PathVariable UUID empresaId) {
        return empresas.membros(empresaId);
    }

    public record PapelRequest(@NotNull PapelObra papel) {
    }

    @PatchMapping("/{empresaId}/membros/{contaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void mudarPapel(@PathVariable UUID empresaId, @PathVariable UUID contaId, @RequestBody PapelRequest r) {
        empresas.mudarPapel(empresaId, contaId, r.papel());
    }

    // ---------------------------------------------------------------- convites (RF20)
    public record CriarConvite(@NotBlank @Email String email, UUID obraId, @NotNull PapelObra papel) {
    }

    @PostMapping("/{empresaId}/invites")
    public ResponseEntity<ConviteService.Resumo> convidar(@PathVariable UUID empresaId, @RequestBody CriarConvite r, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(convites.criar(empresaId, r.email(), r.obraId(), r.papel(), urlBase(req)));
    }

    @GetMapping("/{empresaId}/invites")
    public List<ConviteService.Resumo> listar(@PathVariable UUID empresaId, @RequestParam(required = false) EstadoConvite estado) {
        return convites.listar(empresaId, estado);
    }

    @PostMapping("/{empresaId}/invites/{conviteId}/resend")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reenviar(@PathVariable UUID empresaId, @PathVariable UUID conviteId, HttpServletRequest req) {
        convites.reenviar(empresaId, conviteId, urlBase(req));
    }

    @DeleteMapping("/{empresaId}/invites/{conviteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revogar(@PathVariable UUID empresaId, @PathVariable UUID conviteId) {
        convites.revogar(empresaId, conviteId);
    }

    @org.springframework.beans.factory.annotation.Value("${app.web.url:http://localhost:3000}")
    private String urlPublica;

    /** URL pública do app web (configuração), nunca o cabeçalho Origin enviado pelo cliente */
    String urlBase(HttpServletRequest req) {
        return urlPublica;
    }

    static Map<String, Object> mapa(ConviteService.Publico p) {
        return Map.of("empresa", p.empresa(), "obra", p.obra() == null ? "" : p.obra(), "papel", p.papel().name(), "expiraEm", p.expiraEm().toString(), "email", p.emailMascarado());
    }
}
