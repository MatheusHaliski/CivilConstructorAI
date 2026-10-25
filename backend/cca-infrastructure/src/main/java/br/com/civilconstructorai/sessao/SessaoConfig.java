package br.com.civilconstructorai.sessao;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisIndexedHttpSession;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * Sessão opaca no servidor (Spring Session Data Redis sobre Valkey, repositório indexado para listar e encerrar
 * sessões por usuário — RF23). Cookie {@code __Host-cca_session}: HttpOnly, Secure, SameSite=Lax, Path=/,
 * <strong>sem Max-Age</strong>. Ociosa 60 min (deslizante); o limite absoluto de 24 h é um filtro próprio.
 */
@Configuration
@EnableRedisIndexedHttpSession(maxInactiveIntervalInSeconds = 60 * 60, redisNamespace = "cca:sessao")
public class SessaoConfig {

    public static final String COOKIE = "__Host-cca_session";
    public static final String ATRIBUTO_CRIADA_EM = "cca.criadaEm";
    public static final long ABSOLUTO_SEGUNDOS = 24 * 60 * 60;

    @Bean
    CookieSerializer cookieSerializer() {
        var s = new DefaultCookieSerializer();
        s.setCookieName(COOKIE);
        s.setUseHttpOnlyCookie(true);
        s.setUseSecureCookie(true);
        s.setSameSite("Lax");
        s.setCookiePath("/");
        s.setCookieMaxAge(-1);
        s.setUseBase64Encoding(true);
        return s;
    }
}
