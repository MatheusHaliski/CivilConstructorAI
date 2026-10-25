package br.com.civilconstructorai.seguranca;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

/** AES-256-GCM com nonce aleatório de 12 bytes prefixado ao texto cifrado e AAD opcional. */
public final class CifraAesGcm {

    private static final SecureRandom RANDOM = new SecureRandom();
    private final SecretKeySpec chave;

    public CifraAesGcm(byte[] chave32) {
        if (chave32.length != 32) {
            throw new IllegalArgumentException("Chave AES-256 precisa de 32 bytes.");
        }
        this.chave = new SecretKeySpec(chave32, "AES");
    }

    public byte[] cifrar(byte[] claro, byte[] aad) {
        try {
            var nonce = new byte[12];
            RANDOM.nextBytes(nonce);
            var c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, chave, new GCMParameterSpec(128, nonce));
            if (aad != null) {
                c.updateAAD(aad);
            }
            var cifrado = c.doFinal(claro);
            return ByteBuffer.allocate(12 + cifrado.length).put(nonce).put(cifrado).array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public byte[] decifrar(byte[] blob, byte[] aad) {
        try {
            var bb = ByteBuffer.wrap(blob);
            var nonce = new byte[12];
            bb.get(nonce);
            var cifrado = new byte[bb.remaining()];
            bb.get(cifrado);
            var c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, chave, new GCMParameterSpec(128, nonce));
            if (aad != null) {
                c.updateAAD(aad);
            }
            return c.doFinal(cifrado);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao decifrar (dado adulterado ou chave errada).", e);
        }
    }
}
