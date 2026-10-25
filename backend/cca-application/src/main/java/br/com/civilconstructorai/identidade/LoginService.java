package br.com.civilconstructorai.identidade;

import br.com.civilconstructorai.comum.ExcecaoDominio;
import br.com.civilconstructorai.comum.Relogio;
import br.com.civilconstructorai.email.EnviadorEmail;
import br.com.civilconstructorai.identidade.Portas.ArmazemSessao;
import br.com.civilconstructorai.identidade.Portas.LimitadorTentativas;
import br.com.civilconstructorai.identidade.Repositorios.ContaRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.Serial;
import java.io.Serializable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * RF19 · login em etapas: senha → (2FA) → (oferta de reativação) → (escolha da empresa). O estado
 * intermediário fica <strong>na sessão do servidor</strong>; o cliente só vê {@code proximaEtapa}.
 * Mensagem neutra em falha de credencial; limite por conta e por IP (429); troca do id de sessão ao autenticar.
 */
@Service
public class LoginService {

    public static final String CHAVE_ESTADO = "cca.login";

    public enum Etapa { MFA, CONFIGURAR_MFA, OFERTA_REATIVACAO, ESCOLHER_EMPRESA, AUTENTICADO }

    public record EstadoLogin(UUID contaId, boolean senhaOk, boolean mfaOk, boolean reativacaoResolvida, ArrayList<UUID> empresas) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
    }

    public record Resposta(Etapa proximaEtapa, List<UUID> empresas) {
    }

    private final ContaRepository contas;
    private final PasswordEncoder senhas;
    private final LimitadorTentativas limites;
    private final ArmazemSessao sessao;
    private final SessaoUsuarioFabrica fabrica;
    private final MfaService mfa;
    private final EnviadorEmail email;
    private final Relogio relogio;
    private final TransactionTemplate tx;

    public LoginService(ContaRepository contas, PasswordEncoder senhas, LimitadorTentativas limites, ArmazemSessao sessao,
                        SessaoUsuarioFabrica fabrica, MfaService mfa, EnviadorEmail email, Relogio relogio, TransactionTemplate tx) {
        this.contas = contas;
        this.senhas = senhas;
        this.limites = limites;
        this.sessao = sessao;
        this.fabrica = fabrica;
        this.mfa = mfa;
        this.email = email;
        this.relogio = relogio;
        this.tx = tx;
    }

    /** etapa 1: e-mail + senha */
    public Resposta senha(String emailInformado, String senha, String ip) {
        var e = emailInformado == null ? "" : emailInformado.trim().toLowerCase();
        limites.registrar("login:ip:" + ip, 30, Duration.ofMinutes(15));
        limites.registrar("login:conta:" + e, 10, Duration.ofMinutes(15));
        var conta = contas.findByEmailIgnoreCase(e).orElse(null);
        boolean ok = conta != null && senhas.matches(senha == null ? "" : senha, conta.getSenhaHash()) && conta.getEstado().podeEntrar();
        if (!ok) {
            // custo constante: mesmo com conta inexistente a resposta é a mesma
            throw new ExcecaoDominio.AcessoNegado("E-mail ou senha incorretos.");
        }
        limites.limpar("login:conta:" + e);
        sessao.remover(CHAVE_ESTADO);
        var estado = new EstadoLogin(conta.getId(), true, false, false, new ArrayList<>());
        sessao.gravar(CHAVE_ESTADO, estado);
        return avancar(estado, conta);
    }

    /** etapa 2: TOTP ou código de recuperação */
    public Resposta segundoFator(String codigo, String ip) {
        var estado = estadoOuErro();
        var conta = contas.findById(estado.contaId()).orElseThrow(() -> new ExcecaoDominio.AcessoNegado("Entre novamente."));
        limites.registrar("mfa:conta:" + conta.getId(), 5, Duration.ofMinutes(15));
        if (!mfa.verificar(conta, codigo)) {
            throw new ExcecaoDominio.AcessoNegado("Código inválido.");
        }
        var novo = new EstadoLogin(estado.contaId(), true, true, estado.reativacaoResolvida(), estado.empresas());
        sessao.gravar(CHAVE_ESTADO, novo);
        return avancar(novo, conta);
    }

    /** oferta durante a carência: reativa ou segue como está (RF26) */
    public Resposta reativar(boolean reativar) {
        var estado = estadoOuErro();
        var conta = tx.execute(st -> {
            var c = contas.findById(estado.contaId()).orElseThrow(() -> new ExcecaoDominio.AcessoNegado("Entre novamente."));
            if (c.getEstado() != EstadoConta.PENDING_DELETION) {
                throw new ExcecaoDominio.Conflito("A conta não está em exclusão.");
            }
            if (reativar) {
                c.reativar();
            }
            return c;
        });
        var novo = new EstadoLogin(estado.contaId(), true, estado.mfaOk(), true, estado.empresas());
        sessao.gravar(CHAVE_ESTADO, novo);
        return avancar(novo, conta);
    }

    /** escolha da empresa ativa quando a pessoa tem mais de uma; também usada para trocar depois do login */
    public Resposta escolherEmpresa(UUID empresaId) {
        var estado = sessao.ler(CHAVE_ESTADO, EstadoLogin.class).orElse(null);
        UUID contaId = estado != null ? estado.contaId() : principalAtual().map(PrincipalSessao::contaId).orElseThrow(() -> new ExcecaoDominio.AcessoNegado("Entre para continuar."));
        var conta = contas.findById(contaId).orElseThrow(() -> new ExcecaoDominio.AcessoNegado("Entre novamente."));
        var empresas = fabrica.empresasDaPessoa(contaId);
        if (!empresas.contains(empresaId)) {
            throw new ExcecaoDominio.RecursoNaoEncontrado("Empresa");
        }
        boolean mfaOk = estado != null ? estado.mfaOk() : principalAtual().map(PrincipalSessao::mfaVerificado).orElse(false);
        concluir(conta, empresaId, mfaOk);
        return new Resposta(Etapa.AUTENTICADO, empresas);
    }

    public void sair() {
        sessao.invalidar();
    }

    // ---------------------------------------------------------------- fluxo
    private Resposta avancar(EstadoLogin estado, Conta conta) {
        boolean exigeMfa = conta.isMfaObrigatorio() || fabrica.exigeMfa(conta.getId());
        if (!estado.mfaOk()) {
            if (conta.isMfaAtivo()) {
                return new Resposta(Etapa.MFA, List.of());
            }
            if (exigeMfa) {
                // entra, mas o cliente é levado a configurar o 2FA antes de qualquer ação de aprovação
                concluirParcial(conta, estado);
                return new Resposta(Etapa.CONFIGURAR_MFA, List.of());
            }
        }
        if (conta.getEstado() == EstadoConta.PENDING_DELETION && !estado.reativacaoResolvida()) {
            return new Resposta(Etapa.OFERTA_REATIVACAO, List.of());
        }
        var empresas = fabrica.empresasDaPessoa(conta.getId());
        if (empresas.size() > 1) {
            sessao.gravar(CHAVE_ESTADO, new EstadoLogin(estado.contaId(), true, estado.mfaOk(), estado.reativacaoResolvida(), new ArrayList<>(empresas)));
            return new Resposta(Etapa.ESCOLHER_EMPRESA, empresas);
        }
        concluir(conta, empresas.isEmpty() ? null : empresas.getFirst(), estado.mfaOk());
        return new Resposta(Etapa.AUTENTICADO, empresas);
    }

    private void concluirParcial(Conta conta, EstadoLogin estado) {
        var empresas = fabrica.empresasDaPessoa(conta.getId());
        concluir(conta, empresas.size() == 1 ? empresas.getFirst() : null, false);
    }

    private void concluir(Conta conta, UUID empresaId, boolean mfaOk) {
        var vinculo = empresaId == null ? null : fabrica.vinculoNaEmpresa(conta.getId(), empresaId);
        sessao.remover(CHAVE_ESTADO);
        sessao.trocarId();
        sessao.autenticar(fabrica.principal(conta, vinculo, mfaOk));
        tx.executeWithoutResult(st -> contas.findById(conta.getId()).ifPresent(c -> c.registrarLogin(relogio.agora())));
        email.enviar(conta.getEmail(), "Novo acesso à sua conta", "Detectamos um novo acesso à sua conta do CivilConstructorAI. Se não foi você, troque a senha e encerre as outras sessões em Conta › Segurança.");
    }

    private EstadoLogin estadoOuErro() {
        var estado = sessao.ler(CHAVE_ESTADO, EstadoLogin.class).orElseThrow(() -> new ExcecaoDominio.AcessoNegado("Comece pelo e-mail e senha."));
        if (!estado.senhaOk()) {
            throw new ExcecaoDominio.AcessoNegado("Comece pelo e-mail e senha.");
        }
        return estado;
    }

    private java.util.Optional<PrincipalSessao> principalAtual() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof PrincipalSessao p ? java.util.Optional.of(p) : java.util.Optional.empty();
    }
}
