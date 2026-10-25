package br.com.civilconstructorai.identidade;

import br.com.civilconstructorai.comum.ExcecaoDominio;
import br.com.civilconstructorai.comum.Relogio;
import br.com.civilconstructorai.identidade.Portas.ArmazemSessao;
import br.com.civilconstructorai.identidade.Portas.AssinadorHmac;
import br.com.civilconstructorai.identidade.Portas.Totp;
import br.com.civilconstructorai.identidade.Repositorios.CodigoRecuperacaoMfaRepository;
import br.com.civilconstructorai.identidade.Repositorios.ContaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * RF23 · TOTP (RFC 6238) + 10 códigos de recuperação de uso único. O segredo TOTP fica cifrado com AES-256-GCM
 * (subchave HKDF "totp") — a cifra é injetada pela infraestrutura via {@link CifradorSegredoTotp}.
 */
@Service
public class MfaService {

    public static final String CHAVE_SETUP = "cca.mfa.setup";
    public static final int QTD_CODIGOS = 10;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    /** porta: cifra/decifra o segredo TOTP (AES-256-GCM, AAD = id da conta) */
    public interface CifradorSegredoTotp {
        byte[] cifrar(UUID contaId, String segredo);

        String decifrar(UUID contaId, byte[] cifrado);
    }

    public record Setup(String segredo, String uriOtpauth) {
    }

    private final ContaRepository contas;
    private final CodigoRecuperacaoMfaRepository recuperacao;
    private final Totp totp;
    private final CifradorSegredoTotp cifrador;
    private final AssinadorHmac hmac;
    private final ArmazemSessao sessao;
    private final Relogio relogio;
    private final Portas.LimitadorTentativas usos;
    private final SessaoUsuarioFabrica fabrica;

    public MfaService(ContaRepository contas, CodigoRecuperacaoMfaRepository recuperacao, Totp totp, CifradorSegredoTotp cifrador, AssinadorHmac hmac, ArmazemSessao sessao, Relogio relogio,
                      Portas.LimitadorTentativas usos, SessaoUsuarioFabrica fabrica) {
        this.usos = usos;
        this.fabrica = fabrica;
        this.contas = contas;
        this.recuperacao = recuperacao;
        this.totp = totp;
        this.cifrador = cifrador;
        this.hmac = hmac;
        this.sessao = sessao;
        this.relogio = relogio;
    }

    /** gera segredo e URI otpauth; o segredo fica na sessão até a confirmação */
    public Setup iniciarSetup(UUID contaId) {
        var conta = contas.findById(contaId).orElseThrow(() -> new ExcecaoDominio.RecursoNaoEncontrado("Conta"));
        var segredo = totp.gerarSegredo();
        sessao.gravar(CHAVE_SETUP, segredo);
        return new Setup(segredo, totp.uriOtpauth(segredo, conta.getEmail()));
    }

    /** confirma com um código válido, ativa o 2FA e devolve os 10 códigos de recuperação (só desta vez) */
    @Transactional
    public List<String> confirmarSetup(UUID contaId, String codigo) {
        var segredo = sessao.ler(CHAVE_SETUP, String.class).orElseThrow(() -> new ExcecaoDominio.Validacao("codigo", "Inicie a configuração do 2FA de novo."));
        if (!totp.verificar(segredo, codigo == null ? "" : codigo.trim())) {
            throw new ExcecaoDominio.Validacao("codigo", "Código inválido. Confira o relógio do aparelho e tente de novo.");
        }
        usos.primeiroUso(chaveUso(contaId, codigo.trim()), JANELA_USO);
        var conta = contas.findById(contaId).orElseThrow(() -> new ExcecaoDominio.RecursoNaoEncontrado("Conta"));
        conta.ativarMfa(cifrador.cifrar(contaId, segredo));
        sessao.remover(CHAVE_SETUP);
        // a sessão atual comprovou o segundo fator: sai do bloqueio de 2FA pendente sem novo login
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof PrincipalSessao p && p.contaId().equals(contaId)) {
            sessao.autenticar(p.comMfaConfigurado());
        }
        return regenerarCodigos(contaId);
    }

    @Transactional
    public List<String> regenerarCodigos(UUID contaId) {
        recuperacao.deleteByContaId(contaId);
        var claros = new ArrayList<String>();
        for (int i = 0; i < QTD_CODIGOS; i++) {
            var c = gerarCodigo();
            claros.add(c);
            // o hash usa a forma normalizada (sem hífen/espaços), a mesma que verificar() calcula
            recuperacao.save(new CodigoRecuperacaoMfa(contaId, hmac.hmacHex("recuperacao", contaId + ":" + c.replace("-", ""))));
        }
        return claros;
    }

    @Transactional
    public void desativar(UUID contaId) {
        var conta = contas.findById(contaId).orElseThrow(() -> new ExcecaoDominio.RecursoNaoEncontrado("Conta"));
        // RF23 CA-07: dono/admin/aprovador não desliga o 2FA obrigatório (a auditoria encontrou 204 aqui)
        if (conta.isMfaObrigatorio() || fabrica.exigeMfa(contaId)) {
            throw new ExcecaoDominio.Conflito("O 2FA é obrigatório para o seu papel (dono, administrador ou aprovador); troque o aparelho gerando um novo código em vez de desativar.");
        }
        conta.desativarMfa();
        recuperacao.deleteByContaId(contaId);
    }

    /** TOTP ou código de recuperação (que é consumido) */
    @Transactional
    public boolean verificar(Conta conta, String codigo) {
        if (!conta.isMfaAtivo() || codigo == null) {
            return false;
        }
        var c = codigo.trim().toUpperCase().replace("-", "").replace(" ", "");
        if (c.matches("^\\d{6}$")) {
            var segredo = cifrador.decifrar(conta.getId(), conta.getMfaSegredoCifrado());
            // RF19 CA-05: o mesmo código não vale duas vezes (replay dentro da janela de ±1 passo)
            return totp.verificar(segredo, c) && usos.primeiroUso(chaveUso(conta.getId(), c), JANELA_USO);
        }
        var esperado = hmac.hmacHex("recuperacao", conta.getId() + ":" + c);
        for (var r : recuperacao.findByContaIdAndUsadoEmIsNull(conta.getId())) {
            if (java.security.MessageDigest.isEqual(esperado.getBytes(StandardCharsets.UTF_8), r.getCodigoHash().getBytes(StandardCharsets.UTF_8))) {
                r.usar(relogio.agora());
                return true;
            }
        }
        return false;
    }

    private static final java.time.Duration JANELA_USO = java.time.Duration.ofSeconds(120);

    private static String chaveUso(UUID contaId, String codigo) {
        return "totp:" + contaId + ":" + codigo;
    }

    public long codigosRestantes(UUID contaId) {
        return recuperacao.countByContaIdAndUsadoEmIsNull(contaId);
    }

    private static String gerarCodigo() {
        var sb = new StringBuilder(11);
        for (int i = 0; i < 10; i++) {
            if (i == 5) {
                sb.append('-');
            }
            sb.append(ALFABETO.charAt(RANDOM.nextInt(ALFABETO.length())));
        }
        return sb.toString();
    }
}
