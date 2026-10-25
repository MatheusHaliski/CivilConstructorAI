package br.com.civilconstructorai.api.identidade;

import br.com.civilconstructorai.comum.ContextoUsuario;
import br.com.civilconstructorai.identidade.CadastroService;
import br.com.civilconstructorai.identidade.LoginService;
import br.com.civilconstructorai.identidade.Portas.RegistroAceite;
import br.com.civilconstructorai.identidade.TipoConta;
import br.com.civilconstructorai.identidade.TipoSelo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** RF18/RF19 · /api/v1/auth: CSRF, cadastro, login em etapas, reativação, empresa ativa, sessão e saída. */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final CadastroService cadastro;
    private final LoginService login;
    private final ContextoUsuario contexto;
    private final RegistroAceite aceites;

    public AuthController(CadastroService cadastro, LoginService login, ContextoUsuario contexto, RegistroAceite aceites) {
        this.cadastro = cadastro;
        this.login = login;
        this.contexto = contexto;
        this.aceites = aceites;
    }

    /** entrega o cookie XSRF-TOKEN (csrf.spa) — basta tocar no token */
    @GetMapping("/csrf")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void csrf(CsrfToken token) {
        token.getToken();
    }

    public record RegisterRequest(
            @NotNull TipoConta tipo, @NotBlank String nome, @NotBlank String email, @NotBlank String senha, LocalDate dataNascimento,
            String cnpj, String razaoSocial, TipoSelo seloTipo, String registroProfissional, String registroUf,
            boolean aceitaTermos, boolean aceitaPolitica, String versaoTermos, String versaoPolitica) {
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody RegisterRequest r, HttpServletRequest req) {
        var res = cadastro.registrar(new CadastroService.Pedido(r.tipo(), r.nome(), r.email(), r.senha(), r.dataNascimento(), r.cnpj(), r.razaoSocial(),
                r.seloTipo(), r.registroProfissional(), r.registroUf(), r.aceitaTermos(), r.aceitaPolitica(), r.versaoTermos(), r.versaoPolitica()), ip(req), req.getHeader("User-Agent"));
        // resposta idêntica para e-mail novo e já cadastrado (anti-enumeração)
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("estado", "PENDING_VERIFICATION", "mensagem", "Enviamos um código para o seu e-mail."));
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String senha) {
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest r, HttpServletRequest req) {
        return resposta(login.senha(r.email(), r.senha(), ip(req)));
    }

    public record CodigoRequest(@NotBlank String codigo) {
    }

    @PostMapping("/login/2fa")
    public Map<String, Object> doisFatores(@RequestBody CodigoRequest r, HttpServletRequest req) {
        return resposta(login.segundoFator(r.codigo(), ip(req)));
    }

    public record ReativarRequest(boolean reativar) {
    }

    @PostMapping("/reactivate")
    public Map<String, Object> reativar(@RequestBody ReativarRequest r) {
        return resposta(login.reativar(r.reativar()));
    }

    public record EmpresaRequest(@NotNull UUID empresaId) {
    }

    @PostMapping("/login/empresa")
    public Map<String, Object> escolherEmpresa(@RequestBody EmpresaRequest r) {
        return resposta(login.escolherEmpresa(r.empresaId()));
    }

    @GetMapping("/session")
    public Map<String, Object> session() {
        var u = contexto.atual().orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Entre para continuar."));
        return Map.of(
                "contaId", u.contaId(),
                "empresaAtivaId", u.empresaAtivaId() == null ? "" : u.empresaAtivaId().toString(),
                "papelEmpresa", u.papelEmpresa().map(Enum::name).orElse(""),
                "papeisPorObra", u.papeisPorObra(),
                "mfaVerificado", u.mfaVerificado(),
                "seloVerificado", u.seloVerificado(),
                "sudoAtivo", u.sudoAtivo());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout() {
        login.sair();
    }

    /** RF18 · versões vigentes dos documentos legais (público) */
    @GetMapping("/legal/versoes")
    public RegistroAceite.Versoes versoes() {
        return aceites.vigentes();
    }

    private static Map<String, Object> resposta(LoginService.Resposta r) {
        return Map.of("proximaEtapa", r.proximaEtapa().name(), "empresas", r.empresas() == null ? List.of() : r.empresas());
    }

    static String ip(HttpServletRequest req) {
        var xff = req.getHeader("X-Forwarded-For");
        return xff != null && !xff.isBlank() ? xff.split(",")[0].trim() : req.getRemoteAddr();
    }
}
