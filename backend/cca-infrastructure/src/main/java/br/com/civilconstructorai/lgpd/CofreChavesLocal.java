package br.com.civilconstructorai.lgpd;

import br.com.civilconstructorai.comum.Ids;
import br.com.civilconstructorai.seguranca.CifraAesGcm;
import br.com.civilconstructorai.seguranca.DerivacaoChaves;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;

/**
 * Cofre de chaves local (dev/it e primeira versão): KEK por usuário gerada aleatoriamente, guardada em
 * {@code cofre_kek} cifrada pela chave-mestra (HKDF do segredo do app). Destruir = apagar a linha + tombstone em
 * {@code kek_revogada}; uma KEK revogada nunca abre, mesmo que reapareça de um snapshot.
 * <p><strong>Produção:</strong> a chave-mestra vem do AWS KMS {@code sa-east-1} e estas duas tabelas vivem num
 * armazenamento separado, com réplica síncrona, snapshots de no máximo 7 dias e <em>fora</em> do backup lógico do
 * banco principal — é isso que faz o crypto-shredding valer para backups (RF26 CA-07).
 */
@Component
public class CofreChavesLocal implements Portas.CofreChaves {

    private static final SecureRandom RANDOM = new SecureRandom();
    private final JdbcTemplate jdbc;
    private final CifraAesGcm mestra;

    public CofreChavesLocal(JdbcTemplate jdbc, DerivacaoChaves derivacao) {
        this.jdbc = jdbc;
        this.mestra = new CifraAesGcm(derivacao.derivar("cofre-chaves-mestra", 32));
    }

    @Override
    @Transactional
    public KekGerada gerarKek(UUID usuarioId) {
        var kek = new byte[32];
        RANDOM.nextBytes(kek);
        var id = Ids.novo();
        jdbc.update("insert into cofre_kek (id, usuario_id, kek_cifrada) values (?, ?, ?)", id, usuarioId, mestra.cifrar(kek, id.toString().getBytes()));
        return new KekGerada(id, kek);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] abrir(UUID kekId) {
        if (revogada(kekId)) {
            throw new IllegalStateException("KEK revogada (tombstone): dados cifrados com ela são irrecuperáveis.");
        }
        var linhas = jdbc.query("select kek_cifrada from cofre_kek where id = ?", (rs, i) -> rs.getBytes(1), kekId);
        if (linhas.isEmpty()) {
            throw new IllegalStateException("KEK inexistente.");
        }
        return mestra.decifrar(linhas.get(0), kekId.toString().getBytes());
    }

    @Override
    @Transactional
    public void destruir(UUID kekId) {
        jdbc.update("insert into kek_revogada (kek_id) values (?) on conflict do nothing", kekId);
        jdbc.update("delete from cofre_kek where id = ?", kekId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> tombstones() {
        return jdbc.query("select kek_id from kek_revogada order by revogada_em", (rs, i) -> rs.getObject(1, UUID.class));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean revogada(UUID kekId) {
        Integer n = jdbc.queryForObject("select count(*) from kek_revogada where kek_id = ?", Integer.class, kekId);
        return n != null && n > 0;
    }
}
