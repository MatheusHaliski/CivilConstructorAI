package br.com.civilconstructorai.seguranca;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Base64;

/**
 * HKDF-SHA256 (RFC 5869) sobre o segredo do app: uma subchave por finalidade
 * ({@code totp}, {@code sessao-apelido}, {@code convite}, {@code cofre-dev}…), nunca o segredo em si.
 */
@Component
public class DerivacaoChaves {

    private final byte[] prk;

    public DerivacaoChaves(SegredoApp segredo, org.springframework.core.env.Environment env) {
        byte[] ikm;
        try {
            ikm = segredo.bytes();
        } catch (IllegalStateException e) {
            var perfis = Arrays.asList(env.getActiveProfiles());
            if (!(perfis.contains("dev") || perfis.contains("it"))) {
                throw e;
            }
            // segredo fixo e público só para desenvolvimento local e testes
            ikm = Base64.getDecoder().decode("ZGV2LW9ubHktc2VjcmV0LW5hby11c2FyLWVtLXByb2R1Y2FvISEhISE=");
        }
        this.prk = hmac(new byte[32], ikm);
    }

    public byte[] derivar(String finalidade, int tamanho) {
        var info = finalidade.getBytes(StandardCharsets.UTF_8);
        var saida = new byte[tamanho];
        byte[] t = new byte[0];
        int pos = 0;
        for (int i = 1; pos < tamanho; i++) {
            var entrada = new byte[t.length + info.length + 1];
            System.arraycopy(t, 0, entrada, 0, t.length);
            System.arraycopy(info, 0, entrada, t.length, info.length);
            entrada[entrada.length - 1] = (byte) i;
            t = hmac(prk, entrada);
            int n = Math.min(t.length, tamanho - pos);
            System.arraycopy(t, 0, saida, pos, n);
            pos += n;
        }
        return saida;
    }

    private static byte[] hmac(byte[] chave, byte[] dados) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(chave, "HmacSHA256"));
            return mac.doFinal(dados);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
