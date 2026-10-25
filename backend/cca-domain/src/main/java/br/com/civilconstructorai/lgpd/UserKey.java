package br.com.civilconstructorai.lgpd;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Chave de dados por usuário (DEK), guardada cifrada por uma KEK que mora no cofre de chaves — fora do ciclo de
 * backup deste banco. Destruir a KEK torna a DEK (e tudo cifrado com ela, inclusive em backups) ilegível.
 */
@Entity
@Table(name = "user_key")
public class UserKey {

    @Id
    @Column(name = "usuario_id")
    private UUID usuarioId;

    @Column(name = "kek_id", nullable = false)
    private UUID kekId;

    @Column(name = "versao_chave", nullable = false)
    private int versaoChave = 1;

    @Column(name = "dek_cifrada_pela_kek", nullable = false)
    private byte[] dekCifradaPelaKek;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm = Instant.now();

    protected UserKey() {
    }

    public UserKey(UUID usuarioId, UUID kekId, byte[] dekCifradaPelaKek) {
        this.usuarioId = usuarioId;
        this.kekId = kekId;
        this.dekCifradaPelaKek = dekCifradaPelaKek;
    }

    public UUID getUsuarioId() { return usuarioId; }
    public UUID getKekId() { return kekId; }
    public int getVersaoChave() { return versaoChave; }
    public byte[] getDekCifradaPelaKek() { return dekCifradaPelaKek; }
}
