package br.com.civilconstructorai.lgpd;

import br.com.civilconstructorai.lgpd.Repositorios.UserKeyRepository;
import br.com.civilconstructorai.seguranca.CifraAesGcm;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * DEK por usuário (AES-256-GCM). A DEK fica em {@code user_key} cifrada pela KEK do cofre; dados pessoais
 * cifrados usam a DEK com o id da conta como AAD. Destruir a KEK torna tudo ilegível — inclusive em backups.
 * O módulo de identidade chama {@link #criarParaConta(UUID)} ao criar a conta.
 */
@Component
public class ChavesUsuarioImpl implements Portas.ChavesUsuario {

    private static final SecureRandom RANDOM = new SecureRandom();
    private final UserKeyRepository userKeys;
    private final Portas.CofreChaves cofre;

    public ChavesUsuarioImpl(UserKeyRepository userKeys, Portas.CofreChaves cofre) {
        this.userKeys = userKeys;
        this.cofre = cofre;
    }

    @Override
    @Transactional
    public void criarParaConta(UUID contaId) {
        if (userKeys.existsById(contaId)) {
            return;
        }
        var dek = new byte[32];
        RANDOM.nextBytes(dek);
        var kek = cofre.gerarKek(contaId);
        userKeys.save(new UserKey(contaId, kek.kekId(), new CifraAesGcm(kek.kek()).cifrar(dek, contaId.toString().getBytes())));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existe(UUID contaId) {
        return userKeys.existsById(contaId);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] cifrar(UUID contaId, byte[] claro) {
        return dek(contaId).cifrar(claro, contaId.toString().getBytes());
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] decifrar(UUID contaId, byte[] cifrado) {
        return dek(contaId).decifrar(cifrado, contaId.toString().getBytes());
    }

    @Override
    @Transactional
    public void destruir(UUID contaId) {
        userKeys.findById(contaId).ifPresent(uk -> {
            cofre.destruir(uk.getKekId());
            userKeys.delete(uk);
        });
    }

    private CifraAesGcm dek(UUID contaId) {
        var uk = userKeys.findById(contaId).orElseThrow(() -> new IllegalStateException("Conta sem chave de dados (DEK)."));
        var kek = cofre.abrir(uk.getKekId());
        return new CifraAesGcm(new CifraAesGcm(kek).decifrar(uk.getDekCifradaPelaKek(), contaId.toString().getBytes()));
    }
}
