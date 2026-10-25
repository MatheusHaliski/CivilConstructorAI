package br.com.civilconstructorai.lgpd;

import br.com.civilconstructorai.comum.ExcecaoDominio;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso direto à tabela {@code conta} (dona: módulo de identidade) para o que a LGPD precisa: estado,
 * carência, correção de campos, anonimização. SQL explícito, sem dependência das entidades de identidade.
 */
@Component
public class ContaLgpdDao {

    private final JdbcTemplate jdbc;

    public ContaLgpdDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Resumo(UUID id, String email, String nome, String tipo, EstadoContaLgpd estado, String registroProfissional,
                         String registroUf, String seloTipo, String seloEstado, boolean mfaAtivo, Instant eliminacaoPrevistaEm, Instant criadoEm) {
    }

    public Optional<Resumo> buscar(UUID contaId) {
        var linhas = jdbc.query("""
                select id, email::text as email, nome, tipo, estado, registro_profissional, registro_uf, selo_tipo, selo_estado,
                       mfa_ativo, eliminacao_prevista_em, criado_em
                  from conta where id = ?
                """, (rs, i) -> new Resumo(
                rs.getObject("id", UUID.class), rs.getString("email"), rs.getString("nome"), rs.getString("tipo"),
                EstadoContaLgpd.valueOf(rs.getString("estado")), rs.getString("registro_profissional"), rs.getString("registro_uf"),
                rs.getString("selo_tipo"), rs.getString("selo_estado"), rs.getBoolean("mfa_ativo"),
                rs.getTimestamp("eliminacao_prevista_em") == null ? null : rs.getTimestamp("eliminacao_prevista_em").toInstant(),
                rs.getTimestamp("criado_em").toInstant()), contaId);
        return linhas.stream().findFirst();
    }

    public Resumo exigir(UUID contaId) {
        return buscar(contaId).orElseThrow(() -> new ExcecaoDominio.RecursoNaoEncontrado("Conta"));
    }

    public void solicitarExclusao(UUID contaId, Instant eliminacaoPrevistaEm) {
        jdbc.update("update conta set estado = 'PENDING_DELETION', eliminacao_prevista_em = ?, atualizado_em = now() where id = ?",
                java.sql.Timestamp.from(eliminacaoPrevistaEm), contaId);
    }

    public void reativar(UUID contaId) {
        jdbc.update("update conta set estado = 'ACTIVE', eliminacao_prevista_em = null, atualizado_em = now() where id = ? and estado = 'PENDING_DELETION'", contaId);
    }

    public List<UUID> contasParaEliminar(Instant agora) {
        return jdbc.query("select id from conta where estado = 'PENDING_DELETION' and eliminacao_prevista_em <= ? order by eliminacao_prevista_em",
                (rs, i) -> rs.getObject("id", UUID.class), java.sql.Timestamp.from(agora));
    }

    /** correção de campo pelo titular (Art. 18, III); mudar nome/registro em conta verificada volta o selo a PENDING */
    public void corrigir(UUID contaId, String campo, String valor) {
        switch (campo) {
            case "nome" -> jdbc.update("""
                    update conta set nome = ?, selo_estado = case when selo_estado = 'VERIFIED' then 'PENDING' else selo_estado end,
                           selo_verificado_em = case when selo_estado = 'VERIFIED' then null else selo_verificado_em end,
                           atualizado_em = now() where id = ?""", valor, contaId);
            case "registroProfissional" -> jdbc.update("""
                    update conta set registro_profissional = ?, selo_estado = case when selo_estado = 'VERIFIED' then 'PENDING' else selo_estado end,
                           selo_verificado_em = case when selo_estado = 'VERIFIED' then null else selo_verificado_em end,
                           atualizado_em = now() where id = ?""", valor, contaId);
            case "registroUf" -> jdbc.update("update conta set registro_uf = ?, atualizado_em = now() where id = ?", valor, contaId);
            default -> throw new ExcecaoDominio.Validacao("campo", "Campo \"" + campo + "\" não pode ser corrigido por aqui. Use: nome, registroProfissional, registroUf.");
        }
    }

    /** passo 1 da saga: anonimiza a pessoa; devolve o que foi apagado por categoria (só contagens) */
    public Map<String, Integer> anonimizar(UUID contaId) {
        int conta = jdbc.update("""
                update conta set email = ('anon+' || id::text || '@anon.invalid')::citext, nome = 'Titular removido', senha_hash = '!',
                       data_nascimento = null, cpf_hash = null, cnpj = null, registro_profissional = null, registro_uf = null,
                       mfa_ativo = false, mfa_segredo_cifrado = null, ultimo_login_em = null, atualizado_em = now()
                 where id = ?""", contaId);
        int codigos = jdbc.update("delete from codigo_verificacao where conta_id = ?", contaId);
        int recup = jdbc.update("delete from codigo_recuperacao_mfa where conta_id = ?", contaId);
        int pref = jdbc.update("delete from preferencia where conta_id = ?", contaId);
        int membroEmpresa = jdbc.update("delete from membro_empresa where conta_id = ?", contaId);
        int membroObra = jdbc.update("delete from membro_obra where conta_id = ?", contaId);
        int convites = jdbc.update("update convite set estado = 'REVOGADO', atualizado_em = now() where convidado_por = ? and estado = 'PENDENTE'", contaId);
        return Map.of("conta", conta, "codigos", codigos + recup, "preferencias", pref, "vinculos", membroEmpresa + membroObra, "convitesRevogados", convites);
    }

    public void marcarEliminada(UUID contaId) {
        jdbc.update("update conta set estado = 'ANONYMOUS_LGPD_DELETED', eliminacao_prevista_em = null, atualizado_em = now() where id = ?", contaId);
    }

    /** obras em que a pessoa é responsável técnico (ART): nome + registro ficam por obrigação legal */
    public List<Map<String, Object>> responsabilidadesTecnicas(UUID contaId) {
        return jdbc.queryForList("""
                select o.id as obra_id, o.art_rrt, c.nome, c.registro_profissional, c.registro_uf
                  from obra o join conta c on c.id = o.responsavel_tecnico_id
                 where o.responsavel_tecnico_id = ?""", contaId);
    }

    public List<Map<String, Object>> vinculos(UUID contaId) {
        return jdbc.queryForList("""
                select e.id as empresa_id, e.razao_social, null::uuid as obra_id, null::text as obra, m.papel, m.criado_em
                  from membro_empresa m join empresa e on e.id = m.tenant_id where m.conta_id = ?
                union all
                select e.id, e.razao_social, o.id, o.nome, m.papel, m.criado_em
                  from membro_obra m join obra o on o.id = m.obra_id join empresa e on e.id = o.tenant_id where m.conta_id = ?
                """, contaId, contaId);
    }

    public List<Map<String, Object>> operacoesDoAutor(UUID contaId) {
        return jdbc.queryForList("select tipo, entidade, entidade_id, tenant_id, obra_id, ocorrido_em from registro_operacao where autor_id = ? order by ocorrido_em desc limit 5000", contaId);
    }

    public int contarMembrosDaEmpresa(UUID tenantId) {
        Integer n = jdbc.queryForObject("select count(distinct conta_id) from (select conta_id from membro_obra where tenant_id = ? union select conta_id from membro_empresa where tenant_id = ?) x", Integer.class, tenantId, tenantId);
        return n == null ? 0 : n;
    }

    public Map<String, Object> preferencias(UUID contaId) {
        var l = jdbc.queryForList("select idioma, tema, fonte_pct, motor::text as motor from preferencia where conta_id = ?", contaId);
        return l.isEmpty() ? Map.of() : l.get(0);
    }
}
