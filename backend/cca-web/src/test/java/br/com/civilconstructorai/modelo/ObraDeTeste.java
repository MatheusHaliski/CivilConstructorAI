package br.com.civilconstructorai.modelo;

import br.com.civilconstructorai.acesso.PapelObra;
import br.com.civilconstructorai.comum.ContextoTenant;
import br.com.civilconstructorai.comum.Ids;
import br.com.civilconstructorai.comum.UsuarioAtual;
import br.com.civilconstructorai.support.TesteSeguranca;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

/**
 * Cria empresa, obra e uma conta com vínculo real (membro_empresa/membro_obra) como sistema, para os ITs
 * do modelo/pranchas/interop funcionarem tanto com o stub de acesso quanto com o {@code obraAccess} real.
 */
public final class ObraDeTeste {

    private ObraDeTeste() {
    }

    public record Cenario(UUID empresaId, UUID obraId) {
        public UsuarioAtual usuario(PapelObra papel, boolean mfa, boolean selo) {
            return TesteSeguranca.usuario(Ids.novo(), empresaId, obraId, papel, mfa, selo);
        }
    }

    public static Cenario criar(JdbcTemplate jdbc, PlatformTransactionManager tm) {
        try {
            return ContextoTenant.comoSistema(() -> new TransactionTemplate(tm).execute(st -> {
                var empresaId = Ids.novo();
                var obraId = Ids.novo();
                jdbc.update("insert into empresa (id, tenant_id, razao_social, cnpj, cnpj_verificado) values (?, ?, ?, ?, true)", empresaId, empresaId, "Construtora Teste", cnpj());
                jdbc.update("insert into obra (id, tenant_id, nome, estado) values (?, ?, ?, 'EM_ANDAMENTO')", obraId, empresaId, "Obra de teste");
                return new Cenario(empresaId, obraId);
            }));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** cria conta + vínculo real na obra/empresa para o papel, devolvendo o UsuarioAtual correspondente */
    public static UsuarioAtual membro(JdbcTemplate jdbc, PlatformTransactionManager tm, Cenario c, PapelObra papel, boolean mfa, boolean selo) {
        try {
            return ContextoTenant.comoSistema(() -> new TransactionTemplate(tm).execute(st -> {
                var contaId = Ids.novo();
                jdbc.update("insert into conta (id, email, senha_hash, nome, tipo, estado, mfa_ativo, selo_estado) values (?, ?, ?, ?, 'PESSOA', 'ACTIVE', ?, ?)",
                        contaId, "teste+" + contaId + "@exemplo.com", "{argon2id}x", "Pessoa " + papel, mfa, selo ? "VERIFIED" : "NONE");
                if (papel.escopoEmpresa()) {
                    jdbc.update("insert into membro_empresa (id, tenant_id, conta_id, papel) values (?, ?, ?, ?)", Ids.novo(), c.empresaId(), contaId, papel.name());
                } else {
                    jdbc.update("insert into membro_obra (id, tenant_id, obra_id, conta_id, papel) values (?, ?, ?, ?, ?)", Ids.novo(), c.empresaId(), c.obraId(), contaId, papel.name());
                }
                aceitarDocumentosLegais(jdbc, contaId);
                return TesteSeguranca.usuario(contaId, c.empresaId(), c.obraId(), papel, mfa, selo);
            }));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** RF24: sem aceite da versão vigente dos Termos e da Política, toda rota /api/v1 responde 403 reaceite_pendente */
    private static void aceitarDocumentosLegais(JdbcTemplate jdbc, UUID contaId) {
        for (var tipo : new String[] {"TERMOS", "POLITICA"}) {
            var versao = jdbc.query("select versao from documento_legal where tipo = ? order by vigente_desde desc limit 1",
                    (rs, i) -> rs.getString(1), tipo).stream().findFirst().orElse("1.0");
            jdbc.update("insert into aceite_termos (id, conta_id, tipo, versao, ip_hash) values (?, ?, ?, ?, 'teste')", Ids.novo(), contaId, tipo, versao);
        }
    }

    private static String cnpj() {
        var n = Math.abs(Ids.novo().getLeastSignificantBits() % 100_000_000_000_000L);
        return String.format("%014d", n);
    }
}
