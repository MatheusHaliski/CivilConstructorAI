package br.com.civilconstructorai.persistencia;

import br.com.civilconstructorai.comum.ContextoTenant;
import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Define o tenant <em>na retirada da conexão do pool</em>, e não só no início da transação.
 * <p>
 * Motivo: no Spring Data JPA 4 os métodos de consulta derivados do {@code SimpleJpaRepository} não são mais
 * transacionais por padrão; uma consulta chamada fora de um {@code @Transactional} roda em autocommit e, sem
 * {@code app.tenant_id}, o RLS devolve zero linhas <strong>em silêncio</strong>. Executar o {@code set_config}
 * (nível de sessão) a cada {@code getConnection()} garante o tenant certo em qualquer caminho — JPA, JdbcTemplate,
 * transacional ou não. O {@link TenantJpaTransactionManager} continua fazendo o {@code SET LOCAL} dentro das
 * transações, como segunda camada. Toda retirada sobrescreve o valor anterior, então uma conexão devolvida ao pool
 * nunca "vaza" o tenant da requisição anterior.
 */
public class TenantDataSource extends DelegatingDataSource {

    private static final String SQL = "select set_config('app.tenant_id', ?, false), set_config('app.sistema', ?, false), set_config('app.conta_id', ?, false)";

    public TenantDataSource(DataSource alvo) {
        super(alvo);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return preparar(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return preparar(super.getConnection(username, password));
    }

    private static Connection preparar(Connection c) throws SQLException {
        try (var ps = c.prepareStatement(SQL)) {
            ps.setString(1, ContextoTenant.atual().map(Object::toString).orElse(""));
            ps.setString(2, ContextoTenant.sistema() ? "on" : "off");
            ps.setString(3, ContextoTenant.conta().map(Object::toString).orElse(""));
            ps.execute();
        } catch (SQLException e) {
            c.close();
            throw e;
        }
        return c;
    }
}
