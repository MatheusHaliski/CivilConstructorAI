package br.com.civilconstructorai.identidade;

import br.com.civilconstructorai.email.EnviadorEmailLog;
import br.com.civilconstructorai.support.AbstractIdentidadeIT;
import br.com.civilconstructorai.support.ApiClient;
import br.com.civilconstructorai.support.CnpjDeTeste;
import br.com.civilconstructorai.support.EmailDeTeste;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** RF19/RF23 · login em etapas, mensagem neutra, 429, 2FA obrigatório para DONO, TOTP, sudo, sessões. */
class LoginIT extends AbstractIdentidadeIT {

    @LocalServerPort
    int porta;

    @Autowired
    EnviadorEmailLog emails;

    private ApiClient cliente() {
        return new ApiClient(porta, e -> EmailDeTeste.ultimoCodigo(emails, e));
    }

    @Test
    void mensagemNeutraParaContaInexistenteOuSenhaErrada() {
        var c = cliente();
        var inexistente = c.login("ninguem-" + UUID.randomUUID() + "@teste.cca", "qualquer-coisa-123456");
        var email = "login-" + UUID.randomUUID() + "@teste.cca";
        cliente().registrarEAtivar(email, "Pessoa Login", Map.of());
        var senhaErrada = c.login(email, "senha-errada-mas-longa-1");
        assertThat(inexistente.status()).isEqualTo(403);
        assertThat(senhaErrada.status()).isEqualTo(403);
        assertThat(inexistente.texto("detail")).isEqualTo(senhaErrada.texto("detail")).isEqualTo("E-mail ou senha incorretos.");
    }

    @Test
    void loginSemMfaEntraDiretoETrocaIdDeSessao() {
        var email = "direto-" + UUID.randomUUID() + "@teste.cca";
        cliente().registrarEAtivar(email, "Pessoa Direta", Map.of());
        var c = cliente();
        var r = c.login(email, ApiClient.SENHA_PADRAO);
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.texto("proximaEtapa")).isEqualTo("AUTENTICADO");
        assertThat(c.sessao().status()).isEqualTo(200);
        assertThat(c.logout().status()).isEqualTo(204);
        assertThat(c.sessao().status()).isEqualTo(401);
    }

    @Test
    void limitePorContaDevolve429ComRetryAfter() {
        var email = "limite-" + UUID.randomUUID() + "@teste.cca";
        cliente().registrarEAtivar(email, "Pessoa Limite", Map.of());
        var c = cliente();
        ApiClient.Resposta ultima = null;
        for (int i = 0; i < 11; i++) {
            ultima = c.login(email, "errada-" + i + "-mas-longa");
        }
        assertThat(ultima.status()).isEqualTo(429);
        assertThat(ultima.header("Retry-After")).isNotNull();
    }

    @Test
    void donoDeEmpresaPrecisaConfigurarMfaEAtivaTotp() {
        var email = "dono-" + UUID.randomUUID() + "@teste.cca";
        var c = cliente();
        var r = c.registrarEAtivar(email, "Dono Construtora", Map.of("tipo", "EMPRESA", "cnpj", CnpjDeTeste.aleatorio(), "razaoSocial", "Construtora Teste Ltda"));
        assertThat(r.status()).isEqualTo(201);
        c.logout();
        var login = c.login(email, ApiClient.SENHA_PADRAO);
        assertThat(login.texto("proximaEtapa")).isEqualTo("CONFIGURAR_MFA");

        // configura TOTP
        var setup = c.post("/me/mfa/totp/setup", null);
        assertThat(setup.status()).isEqualTo(200);
        var segredo = setup.texto("segredo");
        assertThat(setup.texto("uriOtpauth")).startsWith("otpauth://totp/");
        var confirm = c.post("/me/mfa/totp/confirm", Map.of("codigo", totp(segredo)));
        assertThat(confirm.status()).isEqualTo(200);
        var codigos = confirm.corpo().get("codigosRecuperacao");
        assertThat(codigos.size()).isEqualTo(10);

        // próximo login exige o segundo fator; código de recuperação é de uso único
        c.logout();
        assertThat(c.login(email, ApiClient.SENHA_PADRAO).texto("proximaEtapa")).isEqualTo("MFA");
        var recuperacao = codigos.get(0).asString();
        assertThat(c.segundoFator(recuperacao).texto("proximaEtapa")).isEqualTo("AUTENTICADO");
        assertThat(c.sessao().corpo().get("mfaVerificado").asBoolean()).isTrue();
        c.logout();
        c.login(email, ApiClient.SENHA_PADRAO);
        assertThat(c.segundoFator(recuperacao).status()).isEqualTo(403);
        assertThat(c.segundoFator(totp(segredo)).texto("proximaEtapa")).isEqualTo("AUTENTICADO");

        // sudo: senha + TOTP; troca de senha derruba as outras sessões
        var outra = cliente();
        outra.login(email, ApiClient.SENHA_PADRAO);
        outra.segundoFator(totp(segredo));
        assertThat(outra.sessao().status()).isEqualTo(200);
        assertThat(c.get("/me/sessions").corpo().size()).isGreaterThanOrEqualTo(2);
        var semSudo = c.post("/me/password", Map.of("senhaAtual", ApiClient.SENHA_PADRAO, "novaSenha", "outra-frase-bem-longa-2026"));
        assertThat(semSudo.status()).isEqualTo(403);
        assertThat(semSudo.corpo().get("type").asString()).endsWith("/sudo_required");
        assertThat(c.post("/me/sudo", Map.of("senha", ApiClient.SENHA_PADRAO, "codigo", totp(segredo))).status()).isEqualTo(204);
        assertThat(c.post("/me/password", Map.of("senhaAtual", ApiClient.SENHA_PADRAO, "novaSenha", "outra-frase-bem-longa-2026")).status()).isEqualTo(204);
        assertThat(outra.sessao().status()).isEqualTo(401);
        assertThat(c.sessao().status()).isEqualTo(200);
    }

    static String totp(String segredo) {
        try {
            var gen = new DefaultCodeGenerator();
            return gen.generate(segredo, Math.floorDiv(new SystemTimeProvider().getTime(), 30));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
