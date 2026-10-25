package br.com.civilconstructorai.geotecnia;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** PostGIS: ST_Contains(perimetro, ponto SIRGAS 2000). Sem perímetro cadastrado → {@code null}. */
@Component
public class PerimetroObraJdbc implements PerimetroObra {

    private final JdbcTemplate jdbc;

    public PerimetroObraJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Boolean contem(UUID obraId, double longitude, double latitude) {
        return jdbc.query(
                "select case when perimetro is null then null else ST_Contains(perimetro, ST_SetSRID(ST_MakePoint(?, ?), 4674)) end from obra where id = ?",
                rs -> {
                    if (!rs.next()) {
                        return null;
                    }
                    boolean v = rs.getBoolean(1);
                    return rs.wasNull() ? null : v;
                }, longitude, latitude, obraId);
    }
}
