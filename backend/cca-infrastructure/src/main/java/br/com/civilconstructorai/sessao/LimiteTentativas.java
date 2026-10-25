package br.com.civilconstructorai.sessao;

import br.com.civilconstructorai.comum.ExcecaoDominio;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Limite de tentativas no Valkey por chave (conta, IP, código de e-mail, TOTP): janela fixa com contador.
 * Estourou → {@link ExcecaoDominio.LimiteExcedido} (429 + Retry-After). A mensagem de login continua neutra.
 */
@Component
public class LimiteTentativas {

    private final StringRedisTemplate redis;

    public LimiteTentativas(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** incrementa e lança 429 quando passa de {@code maximo} dentro de {@code janela} */
    public void registrar(String chave, int maximo, Duration janela) {
        var k = "cca:limite:" + chave;
        Long n = redis.opsForValue().increment(k);
        if (n != null && n == 1L) {
            redis.expire(k, janela);
        }
        if (n != null && n > maximo) {
            Long ttl = redis.getExpire(k);
            throw new ExcecaoDominio.LimiteExcedido(ttl == null || ttl < 0 ? janela.toSeconds() : ttl);
        }
    }

    public void limpar(String chave) {
        redis.delete("cca:limite:" + chave);
    }
}
