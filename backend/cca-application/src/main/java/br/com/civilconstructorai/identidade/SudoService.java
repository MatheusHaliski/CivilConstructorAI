package br.com.civilconstructorai.identidade;

import br.com.civilconstructorai.comum.ContextoUsuario;
import br.com.civilconstructorai.comum.ExcecaoDominio;
import br.com.civilconstructorai.comum.Relogio;
import br.com.civilconstructorai.identidade.Portas.ArmazemSessao;
import br.com.civilconstructorai.identidade.Portas.LimitadorTentativas;
import br.com.civilconstructorai.identidade.Repositorios.ContaRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * RF23 · modo sudo: senha + código (TOTP, ou código por e-mail quando não há 2FA) valem por 10 min na sessão.
 * Ações sensíveis chamam {@link #exigir()} → 403 {@code sudo_required}.
 */
@Service
public class SudoService {

    public static final Duration VALIDADE = Duration.ofMinutes(10);

    private final ContaRepository contas;
    private final PasswordEncoder senhas;
    private final MfaService mfa;
    private final CadastroService codigos;
    private final LimitadorTentativas limites;
    private final ArmazemSessao sessao;
    private final ContextoUsuario contexto;
    private final Relogio relogio;

    public SudoService(ContaRepository contas, PasswordEncoder senhas, MfaService mfa, CadastroService codigos, LimitadorTentativas limites, ArmazemSessao sessao, ContextoUsuario contexto, Relogio relogio) {
        this.contas = contas;
        this.senhas = senhas;
        this.mfa = mfa;
        this.codigos = codigos;
        this.limites = limites;
        this.sessao = sessao;
        this.contexto = contexto;
        this.relogio = relogio;
    }

    /** pede o código por e-mail para quem não tem 2FA */
    public void pedirCodigo() {
        var conta = contaAtual();
        if (conta.isMfaAtivo()) {
            throw new ExcecaoDominio.Conflito("Use o código do seu aplicativo autenticador.");
        }
        codigos.enviarCodigo(conta, CodigoVerificacao.Finalidade.SUDO);
    }

    public void entrar(String senha, String codigo) {
        var conta = contaAtual();
        limites.registrar("sudo:conta:" + conta.getId(), 5, Duration.ofMinutes(15));
        if (!senhas.matches(senha == null ? "" : senha, conta.getSenhaHash())) {
            throw new ExcecaoDominio.AcessoNegado("Senha ou código incorretos.");
        }
        if (conta.isMfaAtivo()) {
            if (!mfa.verificar(conta, codigo)) {
                throw new ExcecaoDominio.AcessoNegado("Senha ou código incorretos.");
            }
        } else {
            codigos.conferirCodigo(conta.getId(), CodigoVerificacao.Finalidade.SUDO, codigo);
        }
        var ate = relogio.agora().plus(VALIDADE).getEpochSecond();
        var principal = principalAtual().comSudoAte(ate);
        sessao.autenticar(principal);
    }

    public void exigir() {
        var u = contexto.exigir();
        if (!u.sudoAtivo()) {
            throw new ExcecaoDominio.SudoNecessario();
        }
    }

    private Conta contaAtual() {
        var u = contexto.exigir();
        return contas.findById(u.contaId()).orElseThrow(() -> new ExcecaoDominio.AcessoNegado("Entre novamente."));
    }

    private PrincipalSessao principalAtual() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof PrincipalSessao p) {
            return p;
        }
        throw new ExcecaoDominio.AcessoNegado("Entre novamente.");
    }
}
