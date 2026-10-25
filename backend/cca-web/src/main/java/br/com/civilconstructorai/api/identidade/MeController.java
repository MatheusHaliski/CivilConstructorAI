package br.com.civilconstructorai.api.identidade;

import br.com.civilconstructorai.comum.ContextoUsuario;
import br.com.civilconstructorai.identidade.CadastroService;
import br.com.civilconstructorai.identidade.EstadoSelo;
import br.com.civilconstructorai.identidade.LoginService;
import br.com.civilconstructorai.identidade.MfaService;
import br.com.civilconstructorai.identidade.PerfilService;
import br.com.civilconstructorai.identidade.SudoService;
import br.com.civilconstructorai.identidade.TipoSelo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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

/** RF18/RF21/RF23 · /api/v1/me: verificação de e-mail, perfil, foto, preferências, sudo, senha, sessões, 2FA, selo. */
@RestController
@RequestMapping("/api/v1")
public class MeController {

    private final CadastroService cadastro;
    private final PerfilService perfil;
    private final SudoService sudo;
    private final MfaService mfa;
    private final LoginService login;
    private final ContextoUsuario contexto;

    public MeController(CadastroService cadastro, PerfilService perfil, SudoService sudo, MfaService mfa, LoginService login, ContextoUsuario contexto) {
        this.cadastro = cadastro;
        this.perfil = perfil;
        this.sudo = sudo;
        this.mfa = mfa;
        this.login = login;
        this.contexto = contexto;
    }

    // ---------------------------------------------------------------- RF18
    public record CodigoRequest(@NotBlank String codigo) {
    }

    @PostMapping("/me/verify-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verificarEmail(@RequestBody CodigoRequest r) {
        cadastro.verificarEmail(contexto.exigir().contaId(), r.codigo());
    }

    @PostMapping("/me/verify-email/resend")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reenviar() {
        cadastro.reenviarCodigo(contexto.exigir().contaId());
    }

    // ---------------------------------------------------------------- RF23 perfil
    @GetMapping("/me/profile")
    public PerfilService.Perfil perfil() {
        return perfil.perfil();
    }

    public record PerfilRequest(String nome, String registroProfissional, String registroUf, String cnpj) {
    }

    @PatchMapping("/me/profile")
    public PerfilService.Perfil atualizar(@RequestBody PerfilRequest r) {
        return perfil.atualizar(r.nome(), r.registroProfissional(), r.registroUf(), r.cnpj());
    }

    public record UploadRequest(@NotBlank String contentType) {
    }

    @PostMapping("/me/photo/upload-url")
    public Map<String, String> urlFoto(@RequestBody UploadRequest r) {
        return Map.of("url", perfil.urlEnvioFoto(r.contentType()).toString(), "metodo", "PUT", "validadeMinutos", "15");
    }

    @PostMapping("/me/photo/confirm")
    public PerfilService.Perfil confirmarFoto() {
        perfil.confirmarFoto();
        return perfil.perfil();
    }

    @GetMapping("/me/preferences")
    public Map<String, Object> preferencias() {
        var p = perfil.preferencias();
        return Map.of("idioma", p.getIdioma(), "tema", p.getTema(), "fontePct", p.getFontePct());
    }

    public record PreferenciasRequest(String idioma, String tema, Integer fontePct) {
    }

    @PatchMapping("/me/preferences")
    public Map<String, Object> atualizarPreferencias(@RequestBody PreferenciasRequest r) {
        var p = perfil.atualizarPreferencias(r.idioma(), r.tema(), r.fontePct());
        return Map.of("idioma", p.getIdioma(), "tema", p.getTema(), "fontePct", p.getFontePct());
    }

    // ---------------------------------------------------------------- sudo e senha
    public record SudoRequest(@NotBlank String senha, String codigo) {
    }

    @PostMapping("/me/sudo/codigo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void pedirCodigoSudo() {
        sudo.pedirCodigo();
    }

    @PostMapping("/me/sudo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sudo(@RequestBody SudoRequest r) {
        sudo.entrar(r.senha(), r.codigo());
    }

    public record SenhaRequest(@NotBlank String senhaAtual, @NotBlank String novaSenha) {
    }

    @PostMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void trocarSenha(@RequestBody SenhaRequest r) {
        perfil.trocarSenha(r.senhaAtual(), r.novaSenha());
    }

    // ---------------------------------------------------------------- sessões
    @GetMapping("/me/sessions")
    public List<PerfilService.SessaoResumo> sessoes() {
        return perfil.sessoes();
    }

    @DeleteMapping("/me/sessions/{apelido}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void encerrar(@PathVariable String apelido) {
        perfil.encerrarSessao(apelido);
    }

    @DeleteMapping("/me/sessions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void encerrarOutras() {
        perfil.encerrarOutrasSessoes();
    }

    /** troca a empresa ativa depois do login (RF19) */
    public record EmpresaRequest(@NotNull UUID empresaId) {
    }

    @PostMapping("/session/empresa")
    public Map<String, Object> trocarEmpresa(@RequestBody EmpresaRequest r) {
        var res = login.escolherEmpresa(r.empresaId());
        return Map.of("proximaEtapa", res.proximaEtapa().name(), "empresas", res.empresas());
    }

    // ---------------------------------------------------------------- 2FA
    @PostMapping("/me/mfa/totp/setup")
    public MfaService.Setup setupTotp() {
        return mfa.iniciarSetup(contexto.exigir().contaId());
    }

    @PostMapping("/me/mfa/totp/confirm")
    public Map<String, Object> confirmarTotp(@RequestBody CodigoRequest r) {
        var codigos = mfa.confirmarSetup(contexto.exigir().contaId(), r.codigo());
        return Map.of("codigosRecuperacao", codigos, "aviso", "Guarde estes códigos: não serão mostrados de novo.");
    }

    @PostMapping("/me/mfa/recovery-codes")
    public Map<String, Object> regenerarCodigos() {
        sudo.exigir();
        return Map.of("codigosRecuperacao", mfa.regenerarCodigos(contexto.exigir().contaId()));
    }

    @DeleteMapping("/me/mfa")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desativarMfa() {
        sudo.exigir();
        mfa.desativar(contexto.exigir().contaId());
    }

    // ---------------------------------------------------------------- selo profissional (RF21)
    @GetMapping("/me/selo")
    public Map<String, Object> selo() {
        return perfil.selo();
    }

    public record SeloRequest(@NotNull TipoSelo tipo, String registro, String uf, String contentTypeDocumento) {
    }

    @PostMapping("/me/selo")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> solicitarSelo(@RequestBody SeloRequest r) {
        return Map.of("urlDocumento", perfil.solicitarSelo(r.tipo(), r.registro(), r.uf(), r.contentTypeDocumento()).toString(), "estado", "PENDING");
    }

    @GetMapping("/plataforma/selos")
    @PreAuthorize("hasRole('PLATAFORMA')")
    public List<Map<String, Object>> filaSelos(@RequestParam(defaultValue = "PENDING") EstadoSelo estado) {
        return perfil.filaSelos(estado);
    }

    public record DecisaoSelo(boolean aprovado, String motivo) {
    }

    @PostMapping("/plataforma/selos/{contaId}/decisao")
    @PreAuthorize("hasRole('PLATAFORMA')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void decidirSelo(@PathVariable UUID contaId, @RequestBody DecisaoSelo d) {
        perfil.decidirSelo(contaId, d.aprovado());
    }
}
