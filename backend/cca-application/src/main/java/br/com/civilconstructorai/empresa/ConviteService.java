package br.com.civilconstructorai.empresa;

import br.com.civilconstructorai.acesso.PapelObra;
import br.com.civilconstructorai.comum.ContextoTenant;
import br.com.civilconstructorai.comum.ContextoUsuario;
import br.com.civilconstructorai.comum.ExcecaoDominio;
import br.com.civilconstructorai.comum.Relogio;
import br.com.civilconstructorai.email.EnviadorEmail;
import br.com.civilconstructorai.empresa.Repositorios.ConviteRepository;
import br.com.civilconstructorai.empresa.Repositorios.EmpresaRepository;
import br.com.civilconstructorai.empresa.Repositorios.MembroEmpresaRepository;
import br.com.civilconstructorai.identidade.Portas.AssinadorHmac;
import br.com.civilconstructorai.identidade.Portas.LimitadorTentativas;
import br.com.civilconstructorai.identidade.Repositorios.ContaRepository;
import br.com.civilconstructorai.obra.MembroObra;
import br.com.civilconstructorai.obra.Repositorios.MembroObraRepository;
import br.com.civilconstructorai.obra.Repositorios.ObraRepository;
import br.com.civilconstructorai.registro.RegistroOperacoes;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

/**
 * RF20 · convites: criados pelo DONO/ADMIN (empresa ou obra) ou pelo(a) ENGENHEIRO(A) responsável (obra);
 * token de 256 bits só como hash; 7 dias; uso único; e-mail deve bater; nunca promove acima do convidante.
 */
@Service
public class ConviteService {

    private static final SecureRandom RANDOM = new SecureRandom();

    public record Resumo(UUID id, UUID obraId, String email, PapelObra papel, EstadoConvite estado, Instant expiraEm) {
    }

    public record Publico(String empresa, String obra, PapelObra papel, Instant expiraEm, String emailMascarado) {
    }

    private final ConviteRepository convites;
    private final EmpresaRepository empresas;
    private final MembroEmpresaRepository membrosEmpresa;
    private final MembroObraRepository membrosObra;
    private final ObraRepository obras;
    private final ContaRepository contas;
    private final EmpresaService empresaService;
    private final AssinadorHmac hmac;
    private final LimitadorTentativas limites;
    private final EnviadorEmail email;
    private final RegistroOperacoes registro;
    private final ContextoUsuario contexto;
    private final Relogio relogio;
    private final TransactionTemplate tx;

    public ConviteService(ConviteRepository convites, EmpresaRepository empresas, MembroEmpresaRepository membrosEmpresa, MembroObraRepository membrosObra, ObraRepository obras,
                          ContaRepository contas, EmpresaService empresaService, AssinadorHmac hmac, LimitadorTentativas limites, EnviadorEmail email,
                          RegistroOperacoes registro, ContextoUsuario contexto, Relogio relogio, TransactionTemplate tx) {
        this.convites = convites;
        this.empresas = empresas;
        this.membrosEmpresa = membrosEmpresa;
        this.membrosObra = membrosObra;
        this.obras = obras;
        this.contas = contas;
        this.empresaService = empresaService;
        this.hmac = hmac;
        this.limites = limites;
        this.email = email;
        this.registro = registro;
        this.contexto = contexto;
        this.relogio = relogio;
        this.tx = tx;
    }

    public Resumo criar(UUID empresaId, String emailConvidado, UUID obraId, PapelObra papel, String urlBase) {
        var u = empresaService.exigirEmpresaAtiva(empresaId);
        limites.registrar("convite:empresa:" + empresaId, 50, Duration.ofHours(1));
        // 1) quem convida: DONO/ADMIN da empresa, ou engenheiro(a) de alguma obra; mais ninguém convida (403)
        var ehEngenheiro = u.papeisPorObra().values().stream().anyMatch(ps -> ps.contains(PapelObra.ENGENHEIRO));
        var papelDoConvidante = u.papelEmpresa().orElseGet(() -> {
            if (!ehEngenheiro) {
                throw new ExcecaoDominio.AcessoNegado("Só DONO/ADMIN da empresa ou o(a) engenheiro(a) responsável pela obra convidam.");
            }
            return PapelObra.ENGENHEIRO;
        });
        // 2) regra do RF20 antes do escopo: um convite nunca promove acima de quem convida (422)
        if (!papelDoConvidante.podeConvidar(papel)) {
            throw new ExcecaoDominio.RegraDeNegocio("convite_acima_do_convidante", "Um convite nunca promove alguém acima do papel de quem convida (" + papelDoConvidante + ").");
        }
        // 3) escopo: engenheiro(a) só convida para obra em que é engenheiro(a)
        if (!papelDoConvidante.escopoEmpresa() && (obraId == null || !u.temPapelNaObra(obraId, PapelObra.ENGENHEIRO))) {
            throw new ExcecaoDominio.AcessoNegado("Engenheiro(a) só convida para a obra pela qual responde; convites de empresa são de DONO/ADMIN.");
        }
        // dentro de uma transação explícita: fora dela não há set_config('app.tenant_id') e o RLS esconderia a obra
        if (obraId != null && Boolean.TRUE.equals(tx.execute(st -> obras.findByIdAndTenantId(obraId, empresaId).isEmpty()))) {
            throw new ExcecaoDominio.RecursoNaoEncontrado("Obra");
        }
        var token = novoToken();
        var convite = Convite.novo(empresaId, obraId, emailConvidado, papel, u.contaId(), papelDoConvidante, hmac.hmacHex("convite", token), relogio.agora());
        var salvo = tx.execute(st -> {
            if (convites.countByTenantIdAndEmailIgnoreCaseAndEstado(empresaId, convite.getEmail(), EstadoConvite.PENDENTE) > 0) {
                throw new ExcecaoDominio.Conflito("Já existe um convite pendente para este e-mail nesta empresa.");
            }
            var c = convites.save(convite);
            registro.registrar(new RegistroOperacoes.Operacao("CONVITE_CRIADO", u.contaId(), empresaId, obraId, "Convite", c.getId(), Map.of("papel", papel.name())));
            return c;
        });
        enviarEmail(salvo, token, urlBase);
        return resumo(salvo);
    }

    public List<Resumo> listar(UUID empresaId, EstadoConvite estado) {
        empresaService.exigirAdmin(empresaId);
        var lista = estado == null ? convites.findByTenantIdOrderByCriadoEmDesc(empresaId) : convites.findByTenantIdAndEstadoOrderByCriadoEmDesc(empresaId, estado);
        return lista.stream().map(this::resumo).toList();
    }

    public void reenviar(UUID empresaId, UUID conviteId, String urlBase) {
        empresaService.exigirAdmin(empresaId);
        var token = novoToken();
        var c = tx.execute(st -> {
            var conv = convites.findById(conviteId).orElseThrow(() -> new ExcecaoDominio.RecursoNaoEncontrado("Convite"));
            conv.renovarToken(hmac.hmacHex("convite", token), relogio.agora());
            return conv;
        });
        enviarEmail(c, token, urlBase);
    }

    public void revogar(UUID empresaId, UUID conviteId) {
        var u = empresaService.exigirAdmin(empresaId);
        tx.executeWithoutResult(st -> {
            var conv = convites.findById(conviteId).orElseThrow(() -> new ExcecaoDominio.RecursoNaoEncontrado("Convite"));
            conv.revogar();
            registro.registrar(RegistroOperacoes.Operacao.de("CONVITE_REVOGADO", u.contaId(), empresaId, conv.getObraId(), "Convite", conv.getId()));
        });
    }

    /** resumo público (sem sessão): nome da empresa/obra, papel, validade, e-mail mascarado */
    public Publico publico(String token) {
        return comoSistema(() -> tx.execute(st -> {
            var c = porToken(token);
            if (c.getEstado() != EstadoConvite.PENDENTE || c.expirado(relogio.agora())) {
                throw new ExcecaoDominio.RegraDeNegocio("convite_indisponivel", "Este convite expirou ou já foi usado.");
            }
            var empresa = empresas.findById(c.getTenantId()).map(Empresa::getRazaoSocial).orElse("Empresa");
            var obra = c.getObraId() == null ? null : obras.findById(c.getObraId()).map(o -> o.getNome()).orElse(null);
            return new Publico(empresa, obra, c.getPapel(), c.getExpiraEm(), mascarar(c.getEmail()));
        }));
    }

    /** aceite pela conta autenticada: cria o vínculo (empresa ou obra) no tenant do convite */
    public UUID aceitar(String token) {
        var u = contexto.exigir();
        var conta = contas.findById(u.contaId()).orElseThrow(() -> new ExcecaoDominio.AcessoNegado("Entre novamente."));
        var conv = comoSistema(() -> tx.execute(st -> porToken(token)));
        return comTenant(conv.getTenantId(), () -> tx.execute(st -> {
            var c = convites.findById(conv.getId()).orElseThrow(() -> new ExcecaoDominio.RecursoNaoEncontrado("Convite"));
            c.aceitar(conta.getId(), conta.getEmail(), relogio.agora());
            if (c.getPapel().escopoEmpresa()) {
                membrosEmpresa.findByTenantIdAndContaId(c.getTenantId(), conta.getId())
                        .ifPresentOrElse(m -> m.mudarPapel(c.getPapel()), () -> membrosEmpresa.save(new MembroEmpresa(c.getTenantId(), conta.getId(), c.getPapel())));
                if (c.getPapel().exigeMfa()) {
                    conta.marcarMfaObrigatorio(true);
                }
            } else {
                var existentes = membrosObra.findByObraIdAndContaId(c.getObraId(), conta.getId());
                if (existentes.stream().noneMatch(m -> m.getPapel() == c.getPapel())) {
                    membrosObra.save(new MembroObra(c.getTenantId(), c.getObraId(), conta.getId(), c.getPapel()));
                }
                if (c.getPapel().exigeMfa()) {
                    conta.marcarMfaObrigatorio(true);
                }
            }
            registro.registrar(new RegistroOperacoes.Operacao("CONVITE_ACEITO", conta.getId(), c.getTenantId(), c.getObraId(), "Convite", c.getId(), Map.of("papel", c.getPapel().name())));
            return c.getTenantId();
        }));
    }

    public void recusar(String token) {
        comoSistema(() -> tx.execute(st -> {
            porToken(token).recusar(relogio.agora());
            return null;
        }));
    }

    // ---------------------------------------------------------------- util
    private Convite porToken(String token) {
        return convites.findByTokenHash(hmac.hmacHex("convite", token == null ? "" : token.trim()))
                .orElseThrow(() -> new ExcecaoDominio.RegraDeNegocio("convite_indisponivel", "Convite não encontrado, expirado ou já usado."));
    }

    private void enviarEmail(Convite c, String token, String urlBase) {
        var link = (urlBase == null ? "" : urlBase) + "/convites/" + token;
        email.enviar(c.getEmail(), "Você foi convidado(a) para o CivilConstructorAI",
                "Você recebeu um convite como " + c.getPapel() + ". Ele vale até " + c.getExpiraEm() + " e só pode ser usado uma vez: " + link);
    }

    private Resumo resumo(Convite c) {
        return new Resumo(c.getId(), c.getObraId(), c.getEmail(), c.getPapel(), c.getEstado(), c.getExpiraEm());
    }

    private static String novoToken() {
        var b = new byte[32];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static String mascarar(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + email.substring(Math.max(at, 0));
        }
        return email.charAt(0) + "***" + email.substring(at - 1);
    }

    private static <T> T comoSistema(Callable<T> acao) {
        try {
            return ContextoTenant.comoSistema(acao);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static <T> T comTenant(UUID tenant, Callable<T> acao) {
        try {
            return ContextoTenant.comTenant(tenant, acao);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
