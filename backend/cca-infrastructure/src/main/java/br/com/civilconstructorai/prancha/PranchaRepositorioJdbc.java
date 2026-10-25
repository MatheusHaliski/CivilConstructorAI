package br.com.civilconstructorai.prancha;

import br.com.civilconstructorai.comum.ContextoTenant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PranchaRepositorioJdbc implements PranchaRepositorio {

    private final JdbcTemplate jdbc;

    public PranchaRepositorioJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void salvar(Prancha p) {
        var t = ContextoTenant.atual().orElse(null);
        jdbc.update("""
                insert into prancha (id, tenant_id, obra_id, pavimento_id, tipo, estado, formato, escala, versao_modelo, chave_svg, chave_dxf, erro, solicitada_por, emitida_por, art_rrt, criada_em, emitida_em)
                values (?, coalesce(?, (select tenant_id from obra where id = ?)), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (id) do update set estado = excluded.estado, chave_svg = excluded.chave_svg, chave_dxf = excluded.chave_dxf, erro = excluded.erro,
                  emitida_por = excluded.emitida_por, art_rrt = excluded.art_rrt, emitida_em = excluded.emitida_em
                """, p.id(), t, p.obraId(), p.obraId(), p.pavimentoId(), p.tipo().name(), p.estado().name(), p.formato(), p.escala(), p.versaoModelo(), p.chaveSvg(), p.chaveDxf(), p.erro(),
                p.solicitadaPor(), p.emitidaPor(), p.artRrt(), Timestamp.from(p.criadaEm()), p.emitidaEm() == null ? null : Timestamp.from(p.emitidaEm()));
    }

    @Override
    public Optional<Prancha> buscar(UUID obraId, UUID id) {
        return jdbc.query("select * from prancha where obra_id = ? and id = ?", this::map, obraId, id).stream().findFirst();
    }

    @Override
    public List<Prancha> listar(UUID obraId) {
        return jdbc.query("select * from prancha where obra_id = ? order by criada_em desc", this::map, obraId);
    }

    @Override
    public List<Prancha> pendentes(int limite) {
        return jdbc.query("select * from prancha where estado = 'EM_GERACAO' order by criada_em limit ?", this::map, limite);
    }

    @Override
    public void marcarObsoletas(UUID obraId, long versaoModelo) {
        jdbc.update("update prancha set estado = 'OBSOLETA' where obra_id = ? and versao_modelo < ? and estado in ('GERADA')", obraId, versaoModelo);
    }

    private Prancha map(ResultSet rs, int i) throws SQLException {
        return new Prancha(rs.getObject("id", UUID.class), rs.getObject("obra_id", UUID.class), rs.getObject("pavimento_id", UUID.class), Tipo.valueOf(rs.getString("tipo")), Estado.valueOf(rs.getString("estado")),
                rs.getString("formato"), rs.getString("escala"), rs.getLong("versao_modelo"), rs.getString("chave_svg"), rs.getString("chave_dxf"), rs.getString("erro"),
                rs.getObject("solicitada_por", UUID.class), rs.getObject("emitida_por", UUID.class), rs.getString("art_rrt"), rs.getTimestamp("criada_em").toInstant(),
                rs.getTimestamp("emitida_em") == null ? null : rs.getTimestamp("emitida_em").toInstant());
    }
}
