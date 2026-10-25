package br.com.civilconstructorai.interop;

import br.com.civilconstructorai.comum.ContextoTenant;
import br.com.civilconstructorai.comum.EstadoJob;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class InteropRepositorioJdbc implements InteropRepositorio {

    private final JdbcTemplate jdbc;

    public InteropRepositorioJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void salvar(Exportacao e) {
        jdbc.update("""
                insert into exportacao_modelo (id, tenant_id, obra_id, formato, estado, versao_modelo, chave, erro, solicitada_por, criada_em, concluida_em)
                values (?, coalesce(?, (select tenant_id from obra where id = ?)), ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (id) do update set estado = excluded.estado, chave = excluded.chave, erro = excluded.erro, concluida_em = excluded.concluida_em
                """, e.id(), ContextoTenant.atual().orElse(null), e.obraId(), e.obraId(), e.formato().name(), e.estado().name(), e.versaoModelo(), e.chave(), e.erro(), e.solicitadaPor(),
                Timestamp.from(e.criadaEm()), e.concluidaEm() == null ? null : Timestamp.from(e.concluidaEm()));
    }

    @Override
    public Optional<Exportacao> exportacao(UUID obraId, UUID id) {
        return jdbc.query("select * from exportacao_modelo where obra_id = ? and id = ?", this::mapExp, obraId, id).stream().findFirst();
    }

    @Override
    public List<Exportacao> exportacoes(UUID obraId) {
        return jdbc.query("select * from exportacao_modelo where obra_id = ? order by criada_em desc", this::mapExp, obraId);
    }

    @Override
    public List<Exportacao> exportacoesPendentes(int limite) {
        return jdbc.query("select * from exportacao_modelo where estado = 'PENDENTE' order by criada_em limit ?", this::mapExp, limite);
    }

    private Exportacao mapExp(ResultSet rs, int i) throws SQLException {
        return new Exportacao(rs.getObject("id", UUID.class), rs.getObject("obra_id", UUID.class), Formato.valueOf(rs.getString("formato")), EstadoJob.valueOf(rs.getString("estado")),
                rs.getLong("versao_modelo"), rs.getString("chave"), rs.getString("erro"), rs.getObject("solicitada_por", UUID.class), rs.getTimestamp("criada_em").toInstant(),
                rs.getTimestamp("concluida_em") == null ? null : rs.getTimestamp("concluida_em").toInstant());
    }

    @Override
    public void salvar(Pareamento p) {
        jdbc.update("""
                insert into pareamento_revit (id, tenant_id, obra_id, conta_id, estado, codigo_hash, token_hash, dispositivo, expira_em, criado_em, pareado_em)
                values (?, coalesce(?, (select tenant_id from obra where id = ?)), ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (id) do update set estado = excluded.estado, token_hash = excluded.token_hash, dispositivo = excluded.dispositivo, pareado_em = excluded.pareado_em
                """, p.id(), ContextoTenant.atual().orElse(null), p.obraId(), p.obraId(), p.contaId(), p.estado().name(), p.codigoHash(), p.tokenHash(), p.dispositivo(),
                Timestamp.from(p.expiraEm()), Timestamp.from(p.criadoEm()), p.pareadoEm() == null ? null : Timestamp.from(p.pareadoEm()));
    }

    @Override
    public Optional<Pareamento> pareamento(UUID obraId, UUID id) {
        return jdbc.query("select * from pareamento_revit where obra_id = ? and id = ?", this::mapPar, obraId, id).stream().findFirst();
    }

    @Override
    public Optional<Pareamento> pareamentoPorCodigo(String codigoHash) {
        return jdbc.query("select * from pareamento_revit where codigo_hash = ?", this::mapPar, codigoHash).stream().findFirst();
    }

    @Override
    public Optional<Pareamento> pareamentoPorToken(String tokenHash) {
        return jdbc.query("select * from pareamento_revit where token_hash = ?", this::mapPar, tokenHash).stream().findFirst();
    }

    @Override
    public List<Pareamento> pareamentos(UUID obraId) {
        return jdbc.query("select * from pareamento_revit where obra_id = ? order by criado_em desc", this::mapPar, obraId);
    }

    private Pareamento mapPar(ResultSet rs, int i) throws SQLException {
        return new Pareamento(rs.getObject("id", UUID.class), rs.getObject("obra_id", UUID.class), rs.getObject("conta_id", UUID.class), EstadoPareamento.valueOf(rs.getString("estado")),
                rs.getString("codigo_hash"), rs.getString("token_hash"), rs.getString("dispositivo"), rs.getTimestamp("expira_em").toInstant(), rs.getTimestamp("criado_em").toInstant(),
                rs.getTimestamp("pareado_em") == null ? null : rs.getTimestamp("pareado_em").toInstant());
    }
}
