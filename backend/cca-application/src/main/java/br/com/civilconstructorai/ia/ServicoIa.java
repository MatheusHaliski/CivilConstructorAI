package br.com.civilconstructorai.ia;

import br.com.civilconstructorai.comum.ContextoTenant;
import br.com.civilconstructorai.comum.ExcecaoDominio;
import br.com.civilconstructorai.comum.Relogio;
import br.com.civilconstructorai.ia.Repositorios.ChamadaIaRepository;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fachada da camada de IA para os outros módulos: chama a porta {@link ModeloLinguagem}, registra a chamada
 * ({@code chamada_ia}, só metadados), checa {@code stop_reason}/recusa e valida a saída contra os campos
 * obrigatórios do schema antes de devolver. Quem chama já removeu dados pessoais (ADR-004).
 */
@Service
public class ServicoIa {

    public record Contexto(UUID tenantId, UUID obraId, UUID usuarioId) {
    }

    public record Resultado(JsonNode json, String jsonBruto, boolean recusada, String stopReason, UUID chamadaId, long totalTokens) {
        public boolean completa() {
            return !recusada && json != null;
        }
    }

    private final ModeloLinguagem modelo;
    private final ChamadaIaRepository chamadas;
    private final ObjectMapper mapper;
    private final Relogio relogio;

    public ServicoIa(ModeloLinguagem modelo, ChamadaIaRepository chamadas, ObjectMapper mapper, Relogio relogio) {
        this.modelo = modelo;
        this.chamadas = chamadas;
        this.mapper = mapper;
        this.relogio = relogio;
    }

    /**
     * @param camposObrigatorios caminhos de primeiro nível que precisam existir na saída (validação estrutural;
     *                           a validação completa de JSON Schema é feita pelo próprio modelo em structured outputs)
     */
    public Resultado gerarEstruturado(RotaIa rota, String systemPromptFixo, String entradaVariavel, String jsonSchema, List<String> camposObrigatorios, Contexto ctx) {
        ModeloLinguagem.Resposta resposta;
        try {
            resposta = modelo.gerarEstruturado(rota, systemPromptFixo, entradaVariavel, jsonSchema, Map.of("rota", rota.id()));
        } catch (ExcecaoDominio e) {
            throw e;
        } catch (RuntimeException e) {
            // falha do provedor (rede, tempo esgotado, 5xx): 503 antes de qualquer escrita no modelo; sem tokens, não há uso a registrar
            throw new ExcecaoDominio.Indisponivel("ia_indisponivel", "O provedor de IA não respondeu. Nada foi alterado; tente de novo em instantes.");
        }
        var chamadaId = registrar(rota, ctx, resposta);
        if (resposta.recusa() || "refusal".equals(resposta.stopReason())) {
            return new Resultado(null, null, true, resposta.stopReason(), chamadaId, resposta.inputTokens() + resposta.outputTokens());
        }
        if ("max_tokens".equals(resposta.stopReason())) {
            throw new ExcecaoDominio.RegraDeNegocio("ia_saida_truncada", "A resposta do modelo foi truncada (max_tokens). Reduza a entrada ou divida em lotes.");
        }
        JsonNode json;
        try {
            json = mapper.readTree(resposta.conteudoJson());
        } catch (RuntimeException e) {
            throw new ExcecaoDominio.RegraDeNegocio("ia_saida_invalida", "A saída do modelo não é JSON válido.");
        }
        var faltando = camposObrigatorios.stream().filter(c -> !json.has(c) || json.get(c).isNull()).toList();
        if (!faltando.isEmpty()) {
            throw new ExcecaoDominio.RegraDeNegocio("ia_saida_invalida", "A saída do modelo não tem os campos obrigatórios: " + String.join(", ", faltando) + ".");
        }
        return new Resultado(json, resposta.conteudoJson(), false, resposta.stopReason(), chamadaId, resposta.inputTokens() + resposta.outputTokens());
    }

    public Resultado responderComCitacoes(RotaIa rota, String systemPromptFixo, byte[] pdf, String pergunta, Contexto ctx) {
        var resposta = modelo.responderComCitacoes(rota, systemPromptFixo, pdf, pergunta);
        var chamadaId = registrar(rota, ctx, resposta);
        if (resposta.recusa()) {
            return new Resultado(null, null, true, resposta.stopReason(), chamadaId, 0);
        }
        return new Resultado(mapper.readTree(resposta.conteudoJson()), resposta.conteudoJson(), false, resposta.stopReason(), chamadaId, resposta.inputTokens() + resposta.outputTokens());
    }

    /** registra antes de validar a saída: a chamada fica no histórico mesmo quando o conteúdo é rejeitado */
    private UUID registrar(RotaIa rota, Contexto ctx, ModeloLinguagem.Resposta r) {
        var tenant = ctx != null && ctx.tenantId() != null ? ctx.tenantId() : ContextoTenant.atual().orElseThrow(() -> new IllegalStateException("chamada de IA sem tenant"));
        var c = new ChamadaIa(tenant, ctx == null ? null : ctx.obraId(), ctx == null ? null : ctx.usuarioId(), rota.id(), rota.finalidade(), rota.baseLegal(),
                r.modelo() == null ? RotaIa.MODELO_PADRAO : r.modelo(), rota.effort().name().toLowerCase(), r.inputTokens(), r.outputTokens(),
                r.cacheReadInputTokens(), r.cacheCreationInputTokens(), r.stopReason(), r.recusa(), r.latenciaMs(), relogio.agora());
        return chamadas.save(c).getId();
    }
}
