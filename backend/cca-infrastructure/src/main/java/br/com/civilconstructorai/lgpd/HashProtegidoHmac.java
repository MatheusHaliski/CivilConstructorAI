package br.com.civilconstructorai.lgpd;

import br.com.civilconstructorai.seguranca.DerivacaoChaves;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

/** HMAC-SHA256 com subchave por finalidade (ip, export, eliminacao): hash não reversível e não enumerável. */
@Component
public class HashProtegidoHmac implements Portas.HashProtegido {

    private final DerivacaoChaves derivacao;

    public HashProtegidoHmac(DerivacaoChaves derivacao) {
        this.derivacao = derivacao;
    }

    @Override
    public String hash(String finalidade, String valor) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(derivacao.derivar("hash-" + finalidade, 32), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((valor == null ? "" : valor).getBytes(StandardCharsets.UTF_8))).substring(0, 32);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
