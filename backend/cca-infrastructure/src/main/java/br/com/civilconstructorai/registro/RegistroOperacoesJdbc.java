package br.com.civilconstructorai.registro;

import br.com.civilconstructorai.comum.Ids;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Registro de operações em {@code registro_operacao} (V1). Só ids e códigos; nunca valores pessoais. */
@Component
public class RegistroOperacoesJdbc implements RegistroOperacoes {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public RegistroOperacoesJdbc(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    public void registrar(Operacao op) {
        jdbc.update("""
                insert into registro_operacao (id, tipo, autor_id, tenant_id, obra_id, entidade, entidade_id, atributos)
                values (?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                """, Ids.novo(), op.tipo(), op.autorId(), op.empresaId(), op.obraId(), op.entidade(), op.entidadeId(),
                mapper.writeValueAsString(op.atributos() == null ? java.util.Map.of() : op.atributos()));
    }
}
