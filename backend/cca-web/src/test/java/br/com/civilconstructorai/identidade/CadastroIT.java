package br.com.civilconstructorai.identidade;

import br.com.civilconstructorai.comum.ContextoTenant;
import br.com.civilconstructorai.support.CnpjDeTeste;

import br.com.civilconstructorai.email.EnviadorEmailLog;
import br.com.civilconstructorai.support.AbstractIdentidadeIT;
import br.com.civilconstructorai.support.ApiClient;
import br.com.civilconstructorai.support.EmailDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** RF18 · cadastro: 422 com todos os erros, menor de 18, e-mail duplicado neutro, verificação do e-mail. */
class CadastroIT extends AbstractIdentidadeIT {

    @LocalServerPort
    int porta;

    @Autowired
    EnviadorEmailLog emails;

    @Autowired
    br.com.civilconstructorai.lgpd.Portas.ChavesUsuario chavesUsuario;

    private ApiClient cliente() {
        return new ApiClient(porta, e -> EmailDeTeste.ultimoCodigo(emails, e));
    }

    @Test
    void todosOsErrosDeUmaVezEm422() {
        var c = cliente().csrf();
        var r = c.post("/auth/register", Map.of(
                "tipo", "PESSOA", "nome", "Jo", "email", "sem-arroba", "senha", "curta",
                "dataNascimento", LocalDate.now().minusYears(15).toString(),
                "aceitaTermos", false, "aceitaPolitica", false));
        assertThat(r.status()).isEqualTo(422);
        var campos = r.corpo().get("errors").valueStream().map(e -> e.get("campo").asString()).toList();
        assertThat(campos).contains("nome", "email", "senha", "dataNascimento", "aceitaTermos", "aceitaPolitica");
        assertThat(r.corpo().get("type").asString()).endsWith("/validacao");
    }

    @Test
    void menorDeIdadeNaoCadastra() {
        var r = cliente().registrar("menor-" + UUID.randomUUID() + "@teste.cca", "Pessoa Nova", Map.of("dataNascimento", LocalDate.now().minusYears(17).toString()));
        assertThat(r.status()).isEqualTo(422);
        assertThat(r.corpo().get("errors").toString()).contains("maiores de 18");
    }

    @Test
    void senhaComEmailOuComumEhRejeitada() {
        var email = "eng-" + UUID.randomUUID() + "@teste.cca";
        var r = cliente().registrar(email, "Engenheira Teste", Map.of("senha", "construcao123"));
        assertThat(r.status()).isEqualTo(422);
        assertThat(r.corpo().get("errors").toString()).contains("muito comum");
    }

    @Test
    void cadastraVerificaEmailEAbreSessao() throws Exception {
        var email = "nova-" + UUID.randomUUID() + "@teste.cca";
        var c = cliente();
        var r = c.registrar(email, "Pessoa Nova", Map.of());
        assertThat(r.status()).isEqualTo(201);
        // sessão já aberta como PENDING_VERIFICATION
        var s = c.sessao();
        assertThat(s.status()).isEqualTo(200);
        // antes de confirmar o e-mail, a sessão só serve às rotas de verificação
        var cedo = c.post("/companies", Map.of("razaoSocial", "Construtora Apressada Ltda", "cnpj", CnpjDeTeste.aleatorio()));
        assertThat(cedo.status()).isEqualTo(403);
        assertThat(cedo.corpo().get("type").asString()).endsWith("/verificacao_pendente");
        // código errado: 422 com tentativas restantes
        var errado = c.post("/me/verify-email", Map.of("codigo", "000000"));
        assertThat(errado.status()).isEqualTo(422);
        assertThat(errado.corpo().get("errors").toString()).contains("Restam 4");
        var certo = c.post("/me/verify-email", Map.of("codigo", EmailDeTeste.ultimoCodigo(emails, email)));
        assertThat(certo.status()).isEqualTo(204);
        assertThat(c.get("/me/profile").texto("estado")).isEqualTo("ACTIVE");
        // verificada, a mesma sessão passa a valer para o resto da API
        assertThat(c.post("/companies", Map.of("razaoSocial", "Construtora Verificada Ltda", "cnpj", CnpjDeTeste.aleatorio())).status()).isEqualTo(201);
        // a DEK criada no cadastro abre pelo cofre de chaves (exportação RF25, crypto-shredding RF26)
        var contaId = java.util.UUID.fromString(s.texto("contaId"));
        var claro = "dado-do-titular".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var volta = ContextoTenant.comoSistema(() -> chavesUsuario.decifrar(contaId, chavesUsuario.cifrar(contaId, claro)));
        assertThat(volta).isEqualTo(claro);
    }

    @Test
    void emailJaCadastradoRecebeRespostaNeutra() {
        var email = "dup-" + UUID.randomUUID() + "@teste.cca";
        cliente().registrarEAtivar(email, "Primeira", Map.of());
        int antes = emails.enviados().size();
        var r = cliente().registrar(email, "Segunda", Map.of());
        assertThat(r.status()).isEqualTo(201);
        assertThat(r.corpo().get("estado").asString()).isEqualTo("PENDING_VERIFICATION");
        // o dono do e-mail é avisado; ninguém descobre que a conta existe pela resposta
        assertThat(emails.enviados().size()).isEqualTo(antes + 1);
        assertThat(emails.enviados().getLast().assunto()).contains("Tentativa de cadastro");
    }

    @Test
    void reenvioDeCodigoLimitadoAUmPorMinuto() {
        var email = "reenvio-" + UUID.randomUUID() + "@teste.cca";
        var c = cliente();
        assertThat(c.registrar(email, "Pessoa Reenvio", Map.of()).status()).isEqualTo(201);
        var r = c.post("/me/verify-email/resend", null);
        assertThat(r.status()).isEqualTo(429);
        assertThat(r.header("Retry-After")).isNotNull();
    }
}
