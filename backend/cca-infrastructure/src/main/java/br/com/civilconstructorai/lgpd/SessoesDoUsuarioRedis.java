package br.com.civilconstructorai.lgpd;

import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.UUID;

/**
 * Encerra todas as sessões da pessoa (índice por principal do Spring Session — o login grava
 * {@code PRINCIPAL_NAME_INDEX_NAME = contaId}) e apaga contadores de tentativa no Valkey.
 */
@Component
public class SessoesDoUsuarioRedis implements Portas.SessoesDoUsuario {

    private final FindByIndexNameSessionRepository<? extends Session> sessoes;
    private final StringRedisTemplate redis;

    public SessoesDoUsuarioRedis(FindByIndexNameSessionRepository<? extends Session> sessoes, StringRedisTemplate redis) {
        this.sessoes = sessoes;
        this.redis = redis;
    }

    @Override
    public void encerrarTodas(UUID contaId) {
        sessoes.findByPrincipalName(contaId.toString()).keySet().forEach(sessoes::deleteById);
        var chaves = new ArrayList<String>();
        try (var cursor = redis.scan(ScanOptions.scanOptions().match("cca:limite:*" + contaId + "*").count(200).build())) {
            cursor.forEachRemaining(chaves::add);
        }
        if (!chaves.isEmpty()) {
            redis.delete(chaves);
        }
    }
}
