package br.com.civilconstructorai.support;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Cliente de teste que entra <strong>de verdade</strong>: pega o CSRF em {@code GET /auth/csrf}, guarda os
 * cookies ({@code XSRF-TOKEN} e {@code __Host-cca_session}) e manda {@code X-XSRF-TOKEN} em toda escrita.
 * Sem mock de autenticação. Cookies são tratados à mão para aceitar o atributo Secure em http de teste.
 */
public class ApiClient {

    public static final String SENHA_PADRAO = "frase-longa-e-segura-2026!";

    public record Resposta(int status, JsonNode corpo, Map<String, List<String>> headers) {
        public String texto(String campo) {
            return corpo == null || corpo.get(campo) == null ? null : corpo.get(campo).asString();
        }

        public String header(String nome) {
            var v = headers.get(nome.toLowerCase());
            return v == null || v.isEmpty() ? null : v.getFirst();
        }
    }

    private final String base;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    private final Map<String, String> cookies = new LinkedHashMap<>();
    private final Function<String, String> codigoDoEmail;

    public ApiClient(int porta, Function<String, String> codigoDoEmail) {
        this.base = "http://localhost:" + porta + "/api/v1";
        this.codigoDoEmail = codigoDoEmail;
    }

    public ApiClient csrf() {
        get("/auth/csrf");
        return this;
    }

    // ---------------------------------------------------------------- fluxos prontos
    public Resposta registrar(String email, String nome, Map<String, Object> extras) {
        csrf();
        var versoes = get("/auth/legal/versoes").corpo;
        var corpo = new LinkedHashMap<String, Object>(Map.of(
                "tipo", "PESSOA", "nome", nome, "email", email, "senha", SENHA_PADRAO,
                "dataNascimento", LocalDate.now().minusYears(30).toString(),
                "aceitaTermos", true, "aceitaPolitica", true,
                "versaoTermos", versoes.get("termos").asString(), "versaoPolitica", versoes.get("politica").asString()));
        corpo.putAll(extras);
        return post("/auth/register", corpo);
    }

    /** cadastra, verifica o e-mail com o código enviado e fica com sessão ativa */
    public Resposta registrarEAtivar(String email, String nome, Map<String, Object> extras) {
        var r = registrar(email, nome, extras);
        if (r.status() != 201) {
            return r;
        }
        var codigo = codigoDoEmail.apply(email);
        var v = post("/me/verify-email", Map.of("codigo", codigo));
        if (v.status() != 204) {
            throw new IllegalStateException("verify-email falhou: " + v.status() + " " + v.corpo());
        }
        return r;
    }

    public Resposta login(String email, String senha) {
        csrf();
        return post("/auth/login", Map.of("email", email, "senha", senha));
    }

    public Resposta segundoFator(String codigo) {
        return post("/auth/login/2fa", Map.of("codigo", codigo));
    }

    public Resposta escolherEmpresa(String empresaId) {
        return post("/auth/login/empresa", Map.of("empresaId", empresaId));
    }

    public Resposta sessao() {
        return get("/auth/session");
    }

    public Resposta logout() {
        return post("/auth/logout", null);
    }

    public String empresaAtiva() {
        return sessao().texto("empresaAtivaId");
    }

    /** configura o TOTP nesta sessão (dono/admin/aprovador não usam a empresa sem 2FA — RF19 CA-06) e devolve o segredo */
    public String ativarMfa() {
        var segredo = post("/me/mfa/totp/setup", null).texto("segredo");
        try {
            var codigo = new dev.samstevens.totp.code.DefaultCodeGenerator().generate(segredo,
                    Math.floorDiv(new dev.samstevens.totp.time.SystemTimeProvider().getTime(), 30) - 1);
            var r = post("/me/mfa/totp/confirm", Map.of("codigo", codigo));
            if (r.status() != 200) {
                throw new IllegalStateException("2FA não confirmou: " + r);
            }
        } catch (dev.samstevens.totp.exceptions.CodeGenerationException e) {
            throw new IllegalStateException(e);
        }
        return segredo;
    }

    // ---------------------------------------------------------------- HTTP
    public Resposta get(String caminho) {
        return enviar("GET", caminho, null);
    }

    public Resposta post(String caminho, Object corpo) {
        return enviar("POST", caminho, corpo);
    }

    public Resposta patch(String caminho, Object corpo) {
        return enviar("PATCH", caminho, corpo);
    }

    public Resposta put(String caminho, Object corpo) {
        return enviar("PUT", caminho, corpo);
    }

    public Resposta delete(String caminho) {
        return enviar("DELETE", caminho, null);
    }

    private Resposta enviar(String metodo, String caminho, Object corpo) {
        try {
            var b = HttpRequest.newBuilder(URI.create(base + caminho)).header("Accept", "application/json").header("User-Agent", "ApiClient/teste");
            if (!cookies.isEmpty()) {
                b.header("Cookie", String.join("; ", cookies.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).toList()));
            }
            if (!metodo.equals("GET")) {
                var xsrf = cookies.get("XSRF-TOKEN");
                if (xsrf != null) {
                    b.header("X-XSRF-TOKEN", xsrf);
                }
            }
            var publisher = corpo == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(corpo));
            if (corpo != null) {
                b.header("Content-Type", "application/json");
            }
            b.method(metodo, publisher);
            var res = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            for (var sc : res.headers().allValues("set-cookie")) {
                var par = sc.split(";", 2)[0];
                var eq = par.indexOf('=');
                if (eq > 0) {
                    var nome = par.substring(0, eq).trim();
                    var valor = par.substring(eq + 1).trim();
                    if (valor.isEmpty() || sc.contains("Max-Age=0")) {
                        cookies.remove(nome);
                    } else {
                        cookies.put(nome, valor);
                    }
                }
            }
            JsonNode node = null;
            if (res.body() != null && !res.body().isBlank()) {
                try {
                    node = json.readTree(res.body());
                } catch (RuntimeException ignored) {
                    node = null;
                }
            }
            return new Resposta(res.statusCode(), node, res.headers().map());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
