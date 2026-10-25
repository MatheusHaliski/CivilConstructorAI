package br.com.civilconstructorai.ia;

import br.com.civilconstructorai.acesso.PapelObra;
import br.com.civilconstructorai.comum.ContextoUsuario;
import br.com.civilconstructorai.comum.ExcecaoDominio;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** RF27 · tela "Uso de IA" (catálogo das rotas) e consumo de tokens por rota e mês para a empresa. */
@Service
public class UsoIaService {

    public record Rota(String id, String rf, String finalidade, String baseLegal, String effort, String dadosEnviados, String nuncaEnviado,
                       String localProcessamento, String retencao, String modelo) {
    }

    public record Consumo(String mes, String rota, long chamadas, long inputTokens, long outputTokens, long cacheReadInputTokens, long recusas) {
    }

    private static final String NUNCA = "CPF, nome de trabalhador, e-mail, telefone, ASO/afastamentos, fotos com pessoas, IP, ids de sessão";

    private final JdbcTemplate jdbc;
    private final ContextoUsuario contexto;

    public UsoIaService(JdbcTemplate jdbc, ContextoUsuario contexto) {
        this.jdbc = jdbc;
        this.contexto = contexto;
    }

    public List<Rota> catalogo() {
        return Arrays.stream(RotaIa.values()).map(r -> new Rota(r.id(), r.rf(), r.finalidade(), r.baseLegal(), r.effort().name().toLowerCase(), r.dadosPermitidos(), NUNCA,
                "Anthropic (EUA) — transferência internacional, LGPD Art. 33, IX, com cláusulas contratuais; retenção de dados na API conforme a configuração da conta",
                "metadados da chamada por 2 anos; conteúdo não é guardado por nós", RotaIa.MODELO_PADRAO)).toList();
    }

    @Transactional(readOnly = true)
    public List<Consumo> consumo(UUID empresaId) {
        var u = contexto.exigir();
        if (!empresaId.equals(u.empresaAtivaId())) {
            throw new ExcecaoDominio.RecursoNaoEncontrado("Empresa");
        }
        if (u.papelEmpresa().filter(p -> p == PapelObra.DONO || p == PapelObra.ADMIN).isEmpty()) {
            throw new ExcecaoDominio.AcessoNegado("Só dono ou administrador da empresa vê o consumo de IA.");
        }
        return jdbc.query("""
                select to_char(date_trunc('month', ocorrido_em), 'YYYY-MM') as mes, rota, count(*) as chamadas,
                       sum(input_tokens) as input_tokens, sum(output_tokens) as output_tokens, sum(cache_read_input_tokens) as cache_read,
                       sum(case when recusa then 1 else 0 end) as recusas
                  from chamada_ia where tenant_id = ?
                 group by 1, 2 order by 1 desc, 2
                """, (rs, i) -> new Consumo(rs.getString("mes"), rs.getString("rota"), rs.getLong("chamadas"), rs.getLong("input_tokens"),
                rs.getLong("output_tokens"), rs.getLong("cache_read"), rs.getLong("recusas")), empresaId);
    }

    public Map<String, Object> encarregado(br.com.civilconstructorai.lgpd.Portas.ConfigLgpd cfg) {
        return Map.of("nome", cfg.dpoNome(), "canal", cfg.dpoEmail(), "prazoRespostaDias", cfg.prazoRespostaDias(),
                "controladora", Map.of("razaoSocial", cfg.controladoraRazaoSocial(), "cnpj", cfg.controladoraCnpj()));
    }
}
