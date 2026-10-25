package br.com.civilconstructorai.lgpd;

import br.com.civilconstructorai.comum.ContextoTenant;
import br.com.civilconstructorai.comum.Ids;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

/**
 * Dados de teste inseridos direto nas tabelas da V1 (as entidades de identidade são de outro módulo).
 * Linhas com tenant_id passam pelo RLS: a inserção roda com o tenant definido na transação.
 */
@Component
public class LgpdFixtures {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public LgpdFixtures(JdbcTemplate jdbc, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
    }

    public UUID conta(String email, String estado) {
        var id = Ids.novo();
        jdbc.update("""
                insert into conta (id, email, senha_hash, nome, tipo, estado, registro_profissional, registro_uf, selo_tipo, selo_estado, mfa_ativo)
                values (?, ?::citext, '{argon2id}x', 'Engenheira Teste', 'PESSOA', ?, 'CREA-PR 12345', 'PR', 'CREA', 'VERIFIED', true)
                """, id, email, estado);
        return id;
    }

    public void aceitarDocumentos(UUID contaId, String versao) {
        for (var tipo : new String[]{"TERMOS", "POLITICA"}) {
            jdbc.update("insert into aceite_termos (id, conta_id, tipo, versao, ip_hash) values (?, ?, ?, ?, 'h')", Ids.novo(), contaId, tipo, versao);
        }
    }

    public UUID empresaComObra(UUID donoId, UUID[] obraOut) {
        var empresaId = Ids.novo();
        var obraId = Ids.novo();
        try {
            ContextoTenant.comTenant(empresaId, () -> tx.execute(st -> {
                jdbc.update("insert into empresa (id, tenant_id, razao_social, cnpj, cnpj_verificado) values (?, ?, 'Construtora Teste', ?, true)", empresaId, empresaId, "00.000.000/" + Math.abs(empresaId.hashCode() % 9000 + 1000) + "-00");
                jdbc.update("insert into membro_empresa (id, tenant_id, conta_id, papel) values (?, ?, ?, 'DONO')", Ids.novo(), empresaId, donoId);
                jdbc.update("insert into obra (id, tenant_id, nome, estado, art_rrt, responsavel_tecnico_id) values (?, ?, 'Residencial Teste', 'PLANEJADA', 'ART 987', ?)", obraId, empresaId, donoId);
                return null;
            }));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        if (obraOut != null) {
            obraOut[0] = obraId;
        }
        return empresaId;
    }

    public void membroObra(UUID empresaId, UUID obraId, UUID contaId, String papel) {
        try {
            ContextoTenant.comTenant(empresaId, () -> tx.execute(st -> jdbc.update(
                    "insert into membro_obra (id, tenant_id, obra_id, conta_id, papel) values (?, ?, ?, ?, ?)", Ids.novo(), empresaId, obraId, contaId, papel)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public String estadoDaConta(UUID contaId) {
        return jdbc.queryForObject("select estado from conta where id = ?", String.class, contaId);
    }

    public String nomeDaConta(UUID contaId) {
        return jdbc.queryForObject("select nome from conta where id = ?", String.class, contaId);
    }

    public JdbcTemplate jdbc() {
        return jdbc;
    }
}
