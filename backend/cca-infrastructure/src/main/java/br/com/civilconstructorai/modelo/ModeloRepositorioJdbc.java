package br.com.civilconstructorai.modelo;

import br.com.civilconstructorai.comum.ContextoTenant;
import br.com.civilconstructorai.comum.ExcecaoDominio;
import br.com.civilconstructorai.comum.Ids;
import br.com.civilconstructorai.modelo.Enums.EstadoCiclo;
import br.com.civilconstructorai.modelo.Enums.OrigemComando;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistência do modelo em PostgreSQL (tabelas de V7) via JdbcTemplate na mesma transação do
 * {@code TenantJpaTransactionManager}, portanto sujeita ao RLS por tenant.
 */
@Repository
public class ModeloRepositorioJdbc implements ModeloRepositorio {

    private final JdbcTemplate jdbc;
    private final EstadoJson json;

    public ModeloRepositorioJdbc(JdbcTemplate jdbc, EstadoJson json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    private UUID tenant() {
        return ContextoTenant.atual().orElseThrow(() -> new ExcecaoDominio.AcessoNegado("Escolha a empresa ativa."));
    }

    @Override
    public Optional<ModeloCarregado> carregar(UUID obraId) {
        var cab = jdbc.query("select id, versao, liberada_cliente, versao_liberada from modelo_bim where obra_id = ?", rs -> rs.next()
                ? new Object[]{rs.getObject("id", UUID.class), rs.getLong("versao"), rs.getBoolean("liberada_cliente"), rs.getObject("versao_liberada") == null ? null : rs.getLong("versao_liberada")} : null, obraId);
        if (cab == null) {
            return Optional.empty();
        }
        var modeloId = (UUID) cab[0];
        var pavs = jdbc.query("select id, nome, elevacao, tipo from pavimento where modelo_id = ? order by elevacao", (rs, i) ->
                new Pavimento(rs.getObject("id", UUID.class), rs.getString("nome"), rs.getBigDecimal("elevacao").doubleValue(), Enums.TipoPavimento.valueOf(rs.getString("tipo"))), modeloId);
        var eixos = jdbc.query("select id, nome, direcao, posicao, inicio, fim, recuo_bolha from eixo where modelo_id = ? order by direcao, posicao", (rs, i) ->
                new Eixo(rs.getObject("id", UUID.class), rs.getString("nome"), Eixo.Direcao.valueOf(rs.getString("direcao")), rs.getBigDecimal("posicao").doubleValue(),
                        rs.getBigDecimal("inicio").doubleValue(), rs.getBigDecimal("fim").doubleValue(), rs.getBigDecimal("recuo_bolha").doubleValue()), modeloId);
        var elementos = jdbc.query("select id, pavimento_id, tipo, camada, material_id, origem, rascunho_ia_id, rotulo, geometria from elemento where modelo_id = ? order by criado_em", (rs, i) -> {
            var m = json.mapa(rs.getString("geometria"));
            var props = new java.util.LinkedHashMap<String, Object>(m);
            var camadaTxt = rs.getString("camada");
            return new Elemento(rs.getObject("id", UUID.class), Enums.TipoElemento.valueOf(rs.getString("tipo")), rs.getObject("pavimento_id", UUID.class),
                    camadaTxt.equals("ia-rascunho") ? Enums.Camada.ia_rascunho : Enums.Camada.valueOf(camadaTxt), rs.getString("material_id"), OrigemComando.valueOf(rs.getString("origem")),
                    rs.getObject("rascunho_ia_id", UUID.class), rs.getString("rotulo"), props);
        }, modeloId);
        var estado = new ModeloBim.Estado((Long) cab[1], pavs, eixos, elementos);
        return Optional.of(new ModeloCarregado(modeloId, obraId, (Long) cab[1], (Boolean) cab[2], (Long) cab[3], estado));
    }

    @Override
    public ModeloCarregado criar(UUID obraId) {
        var id = Ids.novo();
        var t = tenant();
        jdbc.update("insert into modelo_bim (id, tenant_id, obra_id, versao) values (?, ?, ?, 0)", id, t, obraId);
        var terreo = ModeloBim.terreoInicial(obraId);
        jdbc.update("insert into pavimento (id, tenant_id, modelo_id, nome, elevacao, tipo) values (?, ?, ?, ?, ?, ?)", terreo.id(), t, id, terreo.nome(), terreo.elevacao(), terreo.tipo().name());
        return new ModeloCarregado(id, obraId, 0, false, null, new ModeloBim.Estado(0, List.of(terreo), List.of(), List.of()));
    }

    @Override
    public long travarVersao(UUID obraId) {
        try {
            return jdbc.queryForObject("select versao from modelo_bim where obra_id = ? for update", Long.class, obraId);
        } catch (EmptyResultDataAccessException e) {
            throw new ExcecaoDominio.RecursoNaoEncontrado("Modelo");
        }
    }

    @Override
    public void salvarAplicacao(UUID obraId, ModeloBim.Estado antes, ModeloBim.Estado depois, DiffModelo diff, ComandoVi comando, String vi, RelatorioAuditoria relatorio) {
        var modeloId = jdbc.queryForObject("select id from modelo_bim where obra_id = ?", UUID.class, obraId);
        var t = tenant();
        long novaVersao = depois.versao();
        jdbc.update("update modelo_bim set versao = ?, atualizado_em = now() where id = ?", novaVersao, modeloId);
        // pavimentos e eixos: sincroniza pelo conjunto (poucos registros)
        var pavIds = new HashSet<UUID>();
        for (var p : depois.pavimentos()) {
            pavIds.add(p.id());
            jdbc.update("""
                    insert into pavimento (id, tenant_id, modelo_id, nome, elevacao, tipo) values (?, ?, ?, ?, ?, ?)
                    on conflict (id) do update set nome = excluded.nome, elevacao = excluded.elevacao, tipo = excluded.tipo
                    """, p.id(), t, modeloId, p.nome(), p.elevacao(), p.tipo().name());
        }
        jdbc.update("delete from pavimento where modelo_id = ? and not (id = any(?::uuid[]))", modeloId, textos(pavIds));
        var eixoIds = new HashSet<UUID>();
        for (var e : depois.eixos()) {
            eixoIds.add(e.id());
            jdbc.update("""
                    insert into eixo (id, tenant_id, modelo_id, nome, direcao, posicao, inicio, fim, recuo_bolha) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    on conflict (id) do update set nome = excluded.nome, posicao = excluded.posicao, inicio = excluded.inicio, fim = excluded.fim, recuo_bolha = excluded.recuo_bolha
                    """, e.id(), t, modeloId, e.nome(), e.direcao().name(), e.posicao(), e.inicio(), e.fim(), e.recuoBolha());
        }
        jdbc.update("delete from eixo where modelo_id = ? and not (id = any(?::uuid[]))", modeloId, textos(eixoIds));
        // elementos pelo diff
        if (!diff.removidos.isEmpty()) {
            jdbc.update("delete from elemento where modelo_id = ? and id = any(?::uuid[])", modeloId, textos(diff.removidos));
        }
        for (var e : diff.novosOuAlterados()) {
            var geometria = json.json(e.getProps());
            jdbc.update("""
                    insert into elemento (id, tenant_id, modelo_id, pavimento_id, tipo, camada, material_id, origem, rascunho_ia_id, rotulo, geometria)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                    on conflict (id) do update set pavimento_id = excluded.pavimento_id, camada = excluded.camada, material_id = excluded.material_id,
                      origem = excluded.origem, rascunho_ia_id = excluded.rascunho_ia_id, rotulo = excluded.rotulo, geometria = excluded.geometria, atualizado_em = now()
                    """, e.getId(), t, modeloId, e.getPavimentoId(), e.getTipo().name(), e.getCamada().rotulo(), e.getMaterialId(), e.getOrigem().name(), e.getRascunhoIaId(), e.getRotulo(), geometria);
        }
        // para desfazer/diff: se o diff "restaurou" um estado inteiro (DESFAZER), os elementos alterados podem não existir mais — garante presença
        if (comando.codigo().equals("DESFAZER")) {
            var atuais = new HashSet<UUID>();
            depois.elementos().forEach(e -> atuais.add(e.getId()));
            jdbc.update("delete from elemento where modelo_id = ? and not (id = any(?::uuid[]))", modeloId, textos(atuais));
        }
        jdbc.update("""
                insert into comando_vi (id, tenant_id, modelo_id, obra_id, codigo, vi, args, origem, versao_base, versao_nova, autor_id, estado_anterior, relatorio, criado_em)
                values (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?)
                """, comando.id(), t, modeloId, obraId, comando.codigo(), vi, json.json(comando.args()), comando.origem().name(), comando.versaoBase(), novaVersao,
                comando.autorId(), json.estado(antes), json.mapper().writeValueAsString(json.relatorio(relatorio)), Timestamp.from(comando.criadoEm()));
    }

    @Override
    public Optional<ComandoRegistro> comando(UUID obraId, UUID comandoId) {
        var l = jdbc.query("select * from comando_vi where obra_id = ? and id = ?", this::mapComando, obraId, comandoId);
        return l.stream().findFirst();
    }

    @Override
    public void vincularRascunho(UUID obraId, java.util.Collection<UUID> elementos, UUID rascunhoId) {
        for (var id : elementos) {
            jdbc.update("update elemento set rascunho_ia_id = ? where id = ? and modelo_id = (select id from modelo_bim where obra_id = ?)", rascunhoId, id, obraId);
        }
    }

    @Override
    public List<ComandoRegistro> comandos(UUID obraId, long desdeVersaoExclusiva) {
        return jdbc.query("select * from comando_vi where obra_id = ? and versao_nova > ? order by versao_nova", this::mapComando, obraId, desdeVersaoExclusiva);
    }

    private ComandoRegistro mapComando(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        return new ComandoRegistro(rs.getObject("id", UUID.class), rs.getObject("obra_id", UUID.class), rs.getString("codigo"), rs.getString("vi"), OrigemComando.valueOf(rs.getString("origem")),
                rs.getLong("versao_base"), rs.getLong("versao_nova"), rs.getObject("autor_id", UUID.class), rs.getTimestamp("criado_em").toInstant(),
                json.estadoDe(rs.getString("estado_anterior")), json.relatorioDe(rs.getString("relatorio")));
    }

    @Override
    public Optional<ModeloBim.Estado> estadoNaVersao(UUID obraId, long versao) {
        var l = jdbc.query("select estado_anterior from comando_vi where obra_id = ? and versao_base = ? order by criado_em limit 1", (rs, i) -> json.estadoDe(rs.getString(1)), obraId, versao);
        if (!l.isEmpty()) {
            var e = l.get(0);
            return Optional.of(new ModeloBim.Estado(versao, e.pavimentos(), e.eixos(), e.elementos()));
        }
        return carregar(obraId).filter(c -> c.versao() == versao).map(ModeloCarregado::estado);
    }

    @Override
    public void liberarCliente(UUID obraId, boolean liberar, long versao) {
        jdbc.update("update modelo_bim set liberada_cliente = ?, versao_liberada = ?, atualizado_em = now() where obra_id = ?", liberar, liberar ? versao : null, obraId);
    }

    @Override
    public void salvarCiclo(CicloRegistro c) {
        jdbc.update("""
                insert into ciclo_auditoria (id, tenant_id, obra_id, comando_id, estado, iteracoes, historico, iniciado_em, encerrado_em)
                values (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)
                on conflict (id) do update set estado = excluded.estado, iteracoes = excluded.iteracoes, historico = excluded.historico, encerrado_em = excluded.encerrado_em
                """, c.id(), tenant(), c.obraId(), c.comandoId(), c.estado().name(), c.iteracoes(), json.mapper().writeValueAsString(c.historico()),
                Timestamp.from(c.iniciadoEm()), c.encerradoEm() == null ? null : Timestamp.from(c.encerradoEm()));
    }

    @Override
    public List<CicloRegistro> ciclos(UUID obraId, UUID comandoId) {
        return jdbc.query("select * from ciclo_auditoria where obra_id = ? and comando_id = ? order by iniciado_em", (rs, i) -> new CicloRegistro(
                rs.getObject("id", UUID.class), obraId, comandoId, EstadoCiclo.valueOf(rs.getString("estado")), rs.getInt("iteracoes"),
                json.mapper().readValue(rs.getString("historico"), new tools.jackson.core.type.TypeReference<List<CicloRegistro.Iteracao>>() { }),
                rs.getTimestamp("iniciado_em").toInstant(), rs.getTimestamp("encerrado_em") == null ? null : rs.getTimestamp("encerrado_em").toInstant()), obraId, comandoId);
    }

    @Override
    public List<AlertaTransicao> alertas(UUID obraId) {
        return jdbc.query("select * from alerta_transicao where obra_id = ? order by criado_em", this::mapAlerta, obraId);
    }

    @Override
    public void salvarAlerta(AlertaTransicao a) {
        jdbc.update("""
                insert into alerta_transicao (id, tenant_id, obra_id, elemento_id, tipo, estado, mensagem, justificativa, criado_em) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (id) do update set estado = excluded.estado, justificativa = excluded.justificativa
                """, a.id(), tenant(), a.obraId(), a.elementoId(), a.tipo(), a.estado(), a.mensagem(), a.justificativa(), Timestamp.from(a.criadoEm() == null ? Instant.now() : a.criadoEm()));
    }

    @Override
    public Optional<AlertaTransicao> alerta(UUID obraId, UUID alertaId) {
        return jdbc.query("select * from alerta_transicao where obra_id = ? and id = ?", this::mapAlerta, obraId, alertaId).stream().findFirst();
    }

    private AlertaTransicao mapAlerta(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        return new AlertaTransicao(rs.getObject("id", UUID.class), rs.getObject("obra_id", UUID.class), rs.getObject("elemento_id", UUID.class), rs.getString("tipo"), rs.getString("estado"),
                rs.getString("mensagem"), rs.getString("justificativa"), rs.getTimestamp("criado_em").toInstant());
    }

    private static String[] textos(java.util.Collection<UUID> ids) {
        return ids.stream().map(UUID::toString).toArray(String[]::new);
    }
}
