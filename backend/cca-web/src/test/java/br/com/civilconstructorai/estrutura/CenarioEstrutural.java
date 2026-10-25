package br.com.civilconstructorai.estrutura;

import br.com.civilconstructorai.acesso.PapelObra;
import br.com.civilconstructorai.comum.ContextoTenant;
import br.com.civilconstructorai.comum.Ids;
import br.com.civilconstructorai.comum.UsuarioAtual;
import br.com.civilconstructorai.estrutura.modelo.ElementoEstrutural;
import br.com.civilconstructorai.estrutura.modelo.ModeloEstrutural;
import br.com.civilconstructorai.estrutura.modelo.PavimentoRef;
import br.com.civilconstructorai.estrutura.modelo.Ponto;
import br.com.civilconstructorai.support.TesteSeguranca;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Cenário dos ITs de RF04–RF12: empresa + obra criadas como sistema (RLS real), usuários montados a partir do
 * {@link UsuarioAtual} (o bean {@code obraAccess} lê só a sessão) e recortes estruturais prontos para o
 * {@link ModeloEstruturalEmMemoria} do perfil {@code it}.
 */
public final class CenarioEstrutural {

    public static final String TERREO = "pav_terreo";
    public static final String TIPO = "pav_1";
    public static final String CONCRETO = "MAT_CONC_FCK30";

    private CenarioEstrutural() {
    }

    public record Cenario(UUID empresaId, UUID obraId, JdbcTemplate jdbc, PlatformTransactionManager tm) {
        /**
         * conta real (com aceite dos documentos legais vigentes — RF24, senão o filtro de reaceite responde 403) e
         * {@link UsuarioAtual} com o papel na obra; o vínculo é lido da sessão pelo bean {@code obraAccess}
         */
        public UsuarioAtual usuario(PapelObra papel, boolean mfa, boolean selo) {
            var contaId = criarConta(jdbc, tm, papel, mfa, selo);
            return TesteSeguranca.usuario(contaId, empresaId, obraId, papel, mfa, selo);
        }

        public UsuarioAtual engenheiro() {
            return usuario(PapelObra.ENGENHEIRO, true, true);
        }

        public UsuarioAtual cliente() {
            return usuario(PapelObra.CLIENTE, false, false);
        }
    }

    /** empresa + obra; {@code perimetroWkt} (SIRGAS 2000, lon/lat) pode ser nulo */
    public static Cenario criar(JdbcTemplate jdbc, PlatformTransactionManager tm, String perimetroWkt) {
        try {
            return ContextoTenant.comoSistema(() -> new TransactionTemplate(tm).execute(st -> {
                var empresaId = Ids.novo();
                var obraId = Ids.novo();
                jdbc.update("insert into empresa (id, tenant_id, razao_social, cnpj, cnpj_verificado) values (?, ?, ?, ?, true)", empresaId, empresaId, "Construtora Estrutural", cnpj());
                if (perimetroWkt == null) {
                    jdbc.update("insert into obra (id, tenant_id, nome, estado) values (?, ?, ?, 'EM_ANDAMENTO')", obraId, empresaId, "Obra estrutural");
                } else {
                    jdbc.update("insert into obra (id, tenant_id, nome, estado, perimetro) values (?, ?, ?, 'EM_ANDAMENTO', ST_GeomFromText(?, 4674))", obraId, empresaId, "Obra com perímetro", perimetroWkt);
                }
                return new Cenario(empresaId, obraId, jdbc, tm);
            }));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static Cenario criar(JdbcTemplate jdbc, PlatformTransactionManager tm) {
        return criar(jdbc, tm, null);
    }

    /** pórtico espacial 5 × 4 m, pé-direito 3 m: 4 pilares 20×50, 4 vigas 15×50 e laje h = 12 cm no 1º pavimento */
    public static ModeloEstrutural portico(UUID obraId, long versao) {
        var a = new Ponto(0, 0);
        var b = new Ponto(5, 0);
        var c = new Ponto(5, 4);
        var d = new Ponto(0, 4);
        return new ModeloEstrutural(obraId, versao, List.of(new PavimentoRef(TERREO, "Térreo", 0), new PavimentoRef(TIPO, "1º pavimento", 3.0)), List.of(
                ElementoEstrutural.pilar("P1", TERREO, a, 0.2, 0.5, TERREO, TIPO, CONCRETO).comRotulo("P1"),
                ElementoEstrutural.pilar("P2", TERREO, b, 0.2, 0.5, TERREO, TIPO, CONCRETO).comRotulo("P2"),
                ElementoEstrutural.pilar("P3", TERREO, c, 0.2, 0.5, TERREO, TIPO, CONCRETO).comRotulo("P3"),
                ElementoEstrutural.pilar("P4", TERREO, d, 0.2, 0.5, TERREO, TIPO, CONCRETO).comRotulo("P4"),
                ElementoEstrutural.viga("V1", TIPO, a, b, 0.15, 0.5, CONCRETO).comRotulo("V1"),
                ElementoEstrutural.viga("V2", TIPO, b, c, 0.15, 0.5, CONCRETO).comRotulo("V2"),
                ElementoEstrutural.viga("V3", TIPO, c, d, 0.15, 0.5, CONCRETO).comRotulo("V3"),
                ElementoEstrutural.viga("V4", TIPO, d, a, 0.15, 0.5, CONCRETO).comRotulo("V4"),
                ElementoEstrutural.laje("L1", TIPO, List.of(a, b, c, d), 0.12, CONCRETO).comRotulo("L1")));
    }

    /** uma viga solta, sem pilar algum: estrutura hipostática */
    public static ModeloEstrutural vigaSolta(UUID obraId, long versao) {
        return new ModeloEstrutural(obraId, versao, List.of(new PavimentoRef(TERREO, "Térreo", 0), new PavimentoRef(TIPO, "1º pavimento", 3.0)), List.of(
                ElementoEstrutural.viga("V1", TIPO, new Ponto(0, 0), new Ponto(5, 0), 0.15, 0.5, CONCRETO).comRotulo("V1")));
    }

    private static UUID criarConta(JdbcTemplate jdbc, PlatformTransactionManager tm, PapelObra papel, boolean mfa, boolean selo) {
        try {
            return ContextoTenant.comoSistema(() -> new TransactionTemplate(tm).execute(st -> {
                var contaId = Ids.novo();
                jdbc.update("insert into conta (id, email, senha_hash, nome, tipo, estado, mfa_ativo, selo_estado) values (?, ?::citext, ?, ?, 'PESSOA', 'ACTIVE', ?, ?)",
                        contaId, "estrutura+" + contaId + "@exemplo.com", "{argon2id}x", "Pessoa " + papel, mfa, selo ? "VERIFIED" : "NONE");
                for (var tipo : new String[]{"TERMOS", "POLITICA"}) {
                    jdbc.update("insert into aceite_termos (id, conta_id, tipo, versao, ip_hash) values (?, ?, ?, '1.0', 'teste')", Ids.novo(), contaId, tipo);
                }
                return contaId;
            }));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String cnpj() {
        var n = Math.abs(Ids.novo().getLeastSignificantBits() % 100_000_000_000_000L);
        return String.format("%014d", n);
    }
}
