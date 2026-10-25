package br.com.civilconstructorai.material;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/** Consulta, sob RLS da empresa ativa, os elementos que usam um material. */
@Component
public class UsoDeMaterialJdbc implements UsoDeMaterial {

    private final JdbcTemplate jdbc;

    public UsoDeMaterialJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Uso> elementosCom(String codigoMaterial, int limite) {
        return jdbc.query("""
                select o.nome, e.id, e.tipo, e.rotulo
                  from elemento e join modelo_bim m on m.id = e.modelo_id join obra o on o.id = m.obra_id
                 where upper(e.material_id) = upper(?)
                 order by o.nome, e.tipo limit ?""",
                (rs, i) -> new Uso(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)), codigoMaterial, limite);
    }
}
