package br.com.civilconstructorai.identidade;

import br.com.civilconstructorai.comum.ContextoTenant;
import br.com.civilconstructorai.comum.ExcecaoDominio;
import br.com.civilconstructorai.comum.Relogio;
import br.com.civilconstructorai.email.EnviadorEmail;
import br.com.civilconstructorai.empresa.Cnpj;
import br.com.civilconstructorai.empresa.Empresa;
import br.com.civilconstructorai.empresa.MembroEmpresa;
import br.com.civilconstructorai.empresa.Repositorios.EmpresaRepository;
import br.com.civilconstructorai.empresa.Repositorios.MembroEmpresaRepository;
import br.com.civilconstructorai.acesso.PapelObra;
import br.com.civilconstructorai.lgpd.Portas.ChavesUsuario;
import br.com.civilconstructorai.identidade.Portas.ArmazemSessao;
import br.com.civilconstructorai.identidade.Portas.AssinadorHmac;
import br.com.civilconstructorai.identidade.Portas.LimitadorTentativas;
import br.com.civilconstructorai.identidade.Portas.RegistroAceite;
import br.com.civilconstructorai.identidade.Repositorios.CodigoVerificacaoRepository;
import br.com.civilconstructorai.identidade.Repositorios.ContaRepository;
import br.com.civilconstructorai.identidade.Repositorios.PreferenciaRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * RF18 · cadastro de pessoa ou empresa, aceite versionado, código de e-mail (30 min, 5 tentativas, reenvio
 * 1/min). Todos os erros juntos em 422. E-mail já cadastrado → resposta neutra (não revela contas).
 */
@Service
public class CadastroService {

    public static final Duration VALIDADE_CODIGO = Duration.ofMinutes(30);
    private static final SecureRandom RANDOM = new SecureRandom();

    public record Pedido(TipoConta tipo, String nome, String email, String senha, LocalDate dataNascimento,
                         String cnpj, String razaoSocial, TipoSelo seloTipo, String registroProfissional, String registroUf,
                         boolean aceitaTermos, boolean aceitaPolitica, String versaoTermos, String versaoPolitica) {
    }

    public record Resultado(UUID contaId, EstadoConta estado, boolean emailJaExistia) {
    }

    private final ContaRepository contas;
    private final CodigoVerificacaoRepository codigos;
    private final PreferenciaRepository preferencias;
    private final EmpresaRepository empresas;
    private final MembroEmpresaRepository membrosEmpresa;
    private final PasswordEncoder senhas;
    private final AssinadorHmac hmac;
    private final LimitadorTentativas limites;
    private final RegistroAceite aceites;
    private final ChavesUsuario chavesUsuario;
    private final EnviadorEmail email;
    private final ArmazemSessao sessao;
    private final SessaoUsuarioFabrica fabrica;
    private final Relogio relogio;
    private final TransactionTemplate tx;

    public CadastroService(ContaRepository contas, CodigoVerificacaoRepository codigos, PreferenciaRepository preferencias,
                           EmpresaRepository empresas, MembroEmpresaRepository membrosEmpresa, PasswordEncoder senhas, AssinadorHmac hmac,
                           LimitadorTentativas limites, RegistroAceite aceites, ChavesUsuario chavesUsuario, EnviadorEmail email,
                           ArmazemSessao sessao, SessaoUsuarioFabrica fabrica, Relogio relogio, TransactionTemplate tx) {
        this.contas = contas;
        this.codigos = codigos;
        this.preferencias = preferencias;
        this.empresas = empresas;
        this.membrosEmpresa = membrosEmpresa;
        this.senhas = senhas;
        this.hmac = hmac;
        this.limites = limites;
        this.aceites = aceites;
        this.chavesUsuario = chavesUsuario;
        this.email = email;
        this.sessao = sessao;
        this.fabrica = fabrica;
        this.relogio = relogio;
        this.tx = tx;
    }

    public Resultado registrar(Pedido p, String ip, String userAgent) {
        limites.registrar("cadastro:ip:" + ip, 20, Duration.ofHours(1));
        var erros = validar(p);
        if (!erros.isEmpty()) {
            throw new ExcecaoDominio.Validacao(erros);
        }
        var hoje = LocalDate.ofInstant(relogio.agora(), ZoneOffset.UTC);

        // e-mail já cadastrado: mesma resposta de sucesso, sem criar nada (anti-enumeração); avisa o dono por e-mail
        if (contas.existsByEmailIgnoreCase(p.email())) {
            email.enviar(p.email(), "Tentativa de cadastro com o seu e-mail", "Alguém tentou criar uma conta no CivilConstructorAI com este e-mail. Se foi você, entre com sua senha ou recupere o acesso.");
            return new Resultado(null, EstadoConta.PENDING_VERIFICATION, true);
        }

        var conta = tx.execute(st -> {
            var c = Conta.nova(p.email(), senhas.encode(p.senha()), p.nome(), p.tipo(), p.dataNascimento(), hoje);
            if (p.tipo() == TipoConta.EMPRESA) {
                c.alterarCnpj(Cnpj.normalizar(p.cnpj()));
            }
            if (p.seloTipo() != null) {
                c.solicitarSelo(p.seloTipo(), p.registroProfissional(), p.registroUf());
            }
            // flush: o aceite é gravado via JDBC na mesma transação e depende da FK conta_id já inserida
            c = contas.saveAndFlush(c);
            // DEK por usuário cifrada por uma KEK registrada no cofre (cofre_kek): o mesmo caminho que a exportação
            // (RF25) e o crypto-shredding (RF26) usam para abrir/destruir a chave
            chavesUsuario.criarParaConta(c.getId());
            preferencias.save(new Preferencia(c.getId()));
            var ipHash = hmac.hmacHex("ip", ip == null ? "" : ip);
            aceites.registrar(c.getId(), "TERMOS", p.versaoTermos(), ipHash, userAgent);
            aceites.registrar(c.getId(), "POLITICA", p.versaoPolitica(), ipHash, userAgent);
            return c;
        });

        if (p.tipo() == TipoConta.EMPRESA) {
            criarEmpresaDoCadastro(conta, p);
        }
        enviarCodigo(conta, CodigoVerificacao.Finalidade.EMAIL);

        // abre sessão já como PENDING_VERIFICATION (só /me/verify-email e /me funcionam até ativar)
        var vinculos = fabrica.empresasDaPessoa(conta.getId());
        var vinculo = vinculos.size() == 1 ? fabrica.vinculoNaEmpresa(conta.getId(), vinculos.getFirst()) : null;
        sessao.trocarId();
        sessao.autenticar(fabrica.principal(conta, vinculo, false));
        return new Resultado(conta.getId(), conta.getEstado(), false);
    }

    /** a empresa é o tenant: a inserção roda dentro do tenant recém-criado (RLS with check) */
    private void criarEmpresaDoCadastro(Conta dono, Pedido p) {
        var empresa = Empresa.nova(p.razaoSocial() == null ? p.nome() : p.razaoSocial(), null, p.cnpj());
        if (empresas.existsByCnpj(empresa.getCnpj())) {
            throw new ExcecaoDominio.Conflito("Já existe uma empresa com este CNPJ. Peça um convite ao administrador dela.");
        }
        try {
            ContextoTenant.comTenant(empresa.getId(), () -> tx.execute(st -> {
                empresas.save(empresa);
                membrosEmpresa.save(new MembroEmpresa(empresa.getId(), dono.getId(), PapelObra.DONO));
                return null;
            }));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        tx.executeWithoutResult(st -> contas.findById(dono.getId()).ifPresent(c -> c.marcarMfaObrigatorio(true)));
    }

    private List<ExcecaoDominio.Validacao.Erro> validar(Pedido p) {
        var erros = new ArrayList<ExcecaoDominio.Validacao.Erro>();
        if (p.tipo() == null) {
            erros.add(new ExcecaoDominio.Validacao.Erro("tipo", "Informe se o cadastro é de pessoa ou de empresa."));
        }
        if (p.nome() == null || p.nome().trim().length() < 3) {
            erros.add(new ExcecaoDominio.Validacao.Erro("nome", "Informe o nome completo."));
        }
        if (p.email() == null || !p.email().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            erros.add(new ExcecaoDominio.Validacao.Erro("email", "Informe um e-mail válido."));
        }
        erros.addAll(PoliticaSenha.problemas(p.senha(), p.email()));
        var hoje = LocalDate.ofInstant(relogio.agora(), ZoneOffset.UTC);
        if (p.dataNascimento() == null) {
            erros.add(new ExcecaoDominio.Validacao.Erro("dataNascimento", "Informe a data de nascimento."));
        } else if (p.dataNascimento().plusYears(Conta.IDADE_MINIMA).isAfter(hoje)) {
            erros.add(new ExcecaoDominio.Validacao.Erro("dataNascimento", "O app é profissional: só maiores de 18 anos."));
        }
        if (p.tipo() == TipoConta.EMPRESA) {
            if (p.cnpj() == null || !Cnpj.valido(p.cnpj().replaceAll("\\D", ""))) {
                erros.add(new ExcecaoDominio.Validacao.Erro("cnpj", "Informe um CNPJ válido."));
            }
        }
        if (p.seloTipo() != null && p.seloTipo() != TipoSelo.CNPJ) {
            if (p.registroProfissional() == null || p.registroProfissional().isBlank()) {
                erros.add(new ExcecaoDominio.Validacao.Erro("registroProfissional", "Informe o número do " + p.seloTipo() + "."));
            }
            if (p.registroUf() == null || !p.registroUf().matches("^[A-Za-z]{2}$")) {
                erros.add(new ExcecaoDominio.Validacao.Erro("registroUf", "Informe a UF do registro."));
            }
        }
        var vigentes = aceites.vigentes();
        if (!p.aceitaTermos() || !vigentes.termos().equals(p.versaoTermos())) {
            erros.add(new ExcecaoDominio.Validacao.Erro("aceitaTermos", "É preciso aceitar os Termos de Uso vigentes (versão " + vigentes.termos() + ")."));
        }
        if (!p.aceitaPolitica() || !vigentes.politica().equals(p.versaoPolitica())) {
            erros.add(new ExcecaoDominio.Validacao.Erro("aceitaPolitica", "É preciso aceitar a Política de Privacidade vigente (versão " + vigentes.politica() + ")."));
        }
        return erros;
    }

    // ---------------------------------------------------------------- códigos de e-mail
    public void enviarCodigo(Conta conta, CodigoVerificacao.Finalidade finalidade) {
        limites.registrar("codigo:reenvio:" + conta.getId() + ":" + finalidade, 1, Duration.ofMinutes(1));
        var codigo = String.format("%06d", RANDOM.nextInt(1_000_000));
        tx.executeWithoutResult(st -> {
            codigos.apagarPendentes(conta.getId(), finalidade);
            codigos.save(new CodigoVerificacao(conta.getId(), finalidade, hmac.hmacHex("codigo", conta.getId() + ":" + codigo), relogio.agora().plus(VALIDADE_CODIGO)));
        });
        var assunto = switch (finalidade) {
            case EMAIL -> "Seu código de verificação";
            case RECUPERACAO_SENHA -> "Código para redefinir a senha";
            case SUDO -> "Código de confirmação";
        };
        email.enviar(conta.getEmail(), assunto, "Seu código é " + codigo + ". Ele vale por 30 minutos.");
    }

    public void reenviarCodigo(UUID contaId) {
        var conta = contas.findById(contaId).orElseThrow(() -> new ExcecaoDominio.RecursoNaoEncontrado("Conta"));
        if (conta.getEstado() != EstadoConta.PENDING_VERIFICATION) {
            throw new ExcecaoDominio.Conflito("Este e-mail já foi verificado.");
        }
        enviarCodigo(conta, CodigoVerificacao.Finalidade.EMAIL);
    }

    /** confere um código; devolve tentativas restantes em caso de erro via Validacao (422) ou RegraDeNegocio expirado (410 na web) */
    public void conferirCodigo(UUID contaId, CodigoVerificacao.Finalidade finalidade, String codigo) {
        limites.registrar("codigo:conferir:" + contaId + ":" + finalidade, 10, Duration.ofMinutes(30));
        tx.executeWithoutResult(st -> {
            var cv = codigos.findTopByContaIdAndFinalidadeAndUsadoEmIsNullOrderByCriadoEmDesc(contaId, finalidade)
                    .orElseThrow(() -> new ExcecaoDominio.RegraDeNegocio("codigo_expirado", "Peça um novo código."));
            if (cv.expirado(relogio.agora()) || cv.esgotado()) {
                throw new ExcecaoDominio.RegraDeNegocio("codigo_expirado", "Este código expirou. Peça um novo.");
            }
            var esperado = hmac.hmacHex("codigo", contaId + ":" + (codigo == null ? "" : codigo.trim()));
            if (!java.security.MessageDigest.isEqual(esperado.getBytes(), cv.getCodigoHash().getBytes())) {
                int restantes = cv.registrarTentativa();
                throw new ExcecaoDominio.Validacao("codigo", restantes > 0 ? "Código incorreto. Restam " + restantes + " tentativa(s)." : "Código incorreto. Peça um novo código.");
            }
            cv.marcarUsado(relogio.agora());
        });
    }

    public void verificarEmail(UUID contaId, String codigo) {
        conferirCodigo(contaId, CodigoVerificacao.Finalidade.EMAIL, codigo);
        tx.executeWithoutResult(st -> {
            var conta = contas.findById(contaId).orElseThrow(() -> new ExcecaoDominio.RecursoNaoEncontrado("Conta"));
            if (conta.getEstado() == EstadoConta.PENDING_VERIFICATION) {
                conta.ativar();
            }
        });
        // libera a sessão aberta no cadastro (até aqui restrita às rotas de verificação)
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof PrincipalSessao p && p.contaId().equals(contaId) && !p.emailVerificado()) {
            sessao.autenticar(p.comEmailVerificado());
        }
    }
}
