package br.com.civilconstructorai.identidade;

import br.com.civilconstructorai.comum.Ids;
import br.com.civilconstructorai.identidade.Portas.AssinadorHmac;
import br.com.civilconstructorai.identidade.Portas.LimitadorTentativas;
import br.com.civilconstructorai.identidade.Portas.RegistroAceite;
import br.com.civilconstructorai.identidade.Portas.Totp;
import br.com.civilconstructorai.seguranca.CifraAesGcm;
import br.com.civilconstructorai.seguranca.DerivacaoChaves;
import br.com.civilconstructorai.sessao.LimiteTentativas;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;

/** Adaptadores das portas de identidade: HKDF/HMAC, Valkey, TOTP, AES-GCM do segredo TOTP, aceites, DEK por usuário. */
public final class AdaptadoresIdentidade {

    private AdaptadoresIdentidade() {
    }

    @Component
    public static class AssinadorHmacHkdf implements AssinadorHmac {
        private final DerivacaoChaves chaves;

        public AssinadorHmacHkdf(DerivacaoChaves chaves) {
            this.chaves = chaves;
        }

        @Override
        public String hmacHex(String finalidade, String dado) {
            try {
                var mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(chaves.derivar("hmac:" + finalidade, 32), "HmacSHA256"));
                return HexFormat.of().formatHex(mac.doFinal(dado.getBytes(StandardCharsets.UTF_8)));
            } catch (GeneralSecurityException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @Component
    public static class LimitadorTentativasValkey implements LimitadorTentativas {
        private final LimiteTentativas limite;

        public LimitadorTentativasValkey(LimiteTentativas limite) {
            this.limite = limite;
        }

        @Override
        public void registrar(String chave, int maximo, Duration janela) {
            limite.registrar(chave, maximo, janela);
        }

        @Override
        public void limpar(String chave) {
            limite.limpar(chave);
        }

        @Override
        public boolean primeiroUso(String chave, Duration janela) {
            return limite.primeiroUso(chave, janela);
        }
    }

    /** TOTP RFC 6238: SHA-1, 6 dígitos, 30 s, tolerância de ±1 período. */
    @Component
    public static class TotpSamstevens implements Totp {
        private final DefaultSecretGenerator gerador = new DefaultSecretGenerator(32);
        private final DefaultCodeVerifier verificador = new DefaultCodeVerifier(new DefaultCodeGenerator(HashingAlgorithm.SHA1, 6), new SystemTimeProvider());

        public TotpSamstevens() {
            verificador.setAllowedTimePeriodDiscrepancy(1);
        }

        @Override
        public String gerarSegredo() {
            return gerador.generate();
        }

        @Override
        public boolean verificar(String segredo, String codigo) {
            return codigo != null && verificador.isValidCode(segredo, codigo);
        }

        @Override
        public String uriOtpauth(String segredo, String email) {
            var conta = URLEncoder.encode("CivilConstructorAI:" + email, StandardCharsets.UTF_8).replace("+", "%20");
            return "otpauth://totp/" + conta + "?secret=" + segredo + "&issuer=CivilConstructorAI&algorithm=SHA1&digits=6&period=30";
        }
    }

    /** segredo TOTP cifrado com AES-256-GCM (subchave HKDF "totp"), AAD = id da conta */
    @Component
    public static class CifradorTotpAesGcm implements MfaService.CifradorSegredoTotp {
        private final CifraAesGcm cifra;

        public CifradorTotpAesGcm(DerivacaoChaves chaves) {
            this.cifra = new CifraAesGcm(chaves.derivar("totp", 32));
        }

        @Override
        public byte[] cifrar(UUID contaId, String segredo) {
            return cifra.cifrar(segredo.getBytes(StandardCharsets.UTF_8), contaId.toString().getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public String decifrar(UUID contaId, byte[] cifrado) {
            return new String(cifra.decifrar(cifrado, contaId.toString().getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
        }
    }

    /** aceite versionado em {@code aceite_termos}; versões vigentes vêm da configuração (app.legal.*) */
    @Component
    public static class RegistroAceiteJdbc implements RegistroAceite {
        private final JdbcTemplate jdbc;
        private final String versaoTermos;
        private final String versaoPolitica;

        public RegistroAceiteJdbc(JdbcTemplate jdbc, @Value("${app.legal.termos.versao:1.0}") String versaoTermos, @Value("${app.legal.politica.versao:1.0}") String versaoPolitica) {
            this.jdbc = jdbc;
            this.versaoTermos = versaoTermos;
            this.versaoPolitica = versaoPolitica;
        }

        @Override
        public void registrar(UUID contaId, String tipo, String versao, String ipHash, String userAgent) {
            jdbc.update("insert into aceite_termos (id, conta_id, tipo, versao, ip_hash, user_agent) values (?, ?, ?, ?, ?, ?)",
                    Ids.novo(), contaId, tipo, versao, ipHash, userAgent == null ? null : userAgent.substring(0, Math.min(userAgent.length(), 255)));
        }

        @Override
        public Versoes vigentes() {
            return new Versoes(versaoTermos, versaoPolitica);
        }
    }
}
