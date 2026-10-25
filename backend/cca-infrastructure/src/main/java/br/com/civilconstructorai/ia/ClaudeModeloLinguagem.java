package br.com.civilconstructorai.ia;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.BadRequestException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.helpers.MessageAccumulator;
import com.anthropic.models.messages.Base64PdfSource;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.CitationsConfigParam;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.DocumentBlockParam;
import com.anthropic.models.messages.JsonOutputFormat;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageContentParam;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlockParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Camada de IA com o SDK oficial da Anthropic (ADR-002/003): {@code claude-opus-5-5}, {@code effort} explícito
 * por rota em {@code output_config}, structured outputs ({@code output_config.format} com JSON Schema),
 * streaming com a mensagem final pelo {@link MessageAccumulator}, prompt caching da parte fixa, sem
 * {@code temperature}/{@code top_p}/{@code top_k}, sem prefill, {@code tool_choice} auto, {@code stop_reason}
 * sempre verificado e fallback do servidor ligado. Ativa com {@code app.ia.habilitada=true} e
 * {@code ANTHROPIC_API_KEY} no ambiente (do cofre de segredos, nunca em .yml).
 */
@Component
@ConditionalOnProperty(name = "app.ia.habilitada", havingValue = "true")
public class ClaudeModeloLinguagem implements ModeloLinguagem {

    private static final Logger log = LoggerFactory.getLogger(ClaudeModeloLinguagem.class);
    private static final String BETA_FALLBACK = "server-side-fallback-2026-07-01";
    private static final long MAX_TOKENS_ESTRUTURADO = 16_384;
    private static final long MAX_TOKENS_CITACOES = 8_192;

    private final AnthropicClient cliente;
    private final ObjectMapper mapper;

    public ClaudeModeloLinguagem(ObjectMapper mapper) {
        this(AnthropicOkHttpClient.fromEnv(), mapper);
    }

    ClaudeModeloLinguagem(AnthropicClient cliente, ObjectMapper mapper) {
        this.cliente = cliente;
        this.mapper = mapper;
    }

    @Override
    public Resposta gerarEstruturado(RotaIa rota, String systemPromptFixo, String entradaVariavel, String jsonSchema, Map<String, String> contexto) {
        var inicio = System.nanoTime();
        var params = base(rota, systemPromptFixo, MAX_TOKENS_ESTRUTURADO)
                .outputConfig(OutputConfig.builder()
                        .effort(effort(rota))
                        .format(JsonOutputFormat.builder().schema(schema(jsonSchema)).build())
                        .build())
                .addUserMessage(entradaVariavel)
                .build();
        var msg = executar(params);
        return converter(msg, inicio);
    }

    @Override
    public Resposta responderComCitacoes(RotaIa rota, String systemPromptFixo, byte[] pdf, String pergunta) {
        var inicio = System.nanoTime();
        var documento = DocumentBlockParam.builder()
                .source(Base64PdfSource.builder().data(Base64.getEncoder().encodeToString(pdf)).build())
                .citations(CitationsConfigParam.builder().enabled(true).build())
                .build();
        var params = base(rota, systemPromptFixo, MAX_TOKENS_CITACOES)
                .outputConfig(OutputConfig.builder().effort(effort(rota)).build())
                .addUserMessage(MessageContentParam.ofContentBlockParams(List.of(ContentBlockParam.ofDocument(documento), ContentBlockParam.ofText(pergunta))))
                .build();
        var msg = executar(params);
        var inicioResposta = converter(msg, inicio);
        // citações viram JSON próprio: {resposta, citacoes:[{texto, pagina?}]}
        var citacoes = new java.util.ArrayList<Map<String, Object>>();
        var texto = new StringBuilder();
        msg.content().forEach(b -> b.text().ifPresent(t -> {
            texto.append(t.text());
            t.citations().ifPresent(cs -> cs.forEach(c -> citacoes.add(Map.of("citacao", c.toString()))));
        }));
        var json = mapper.writeValueAsString(Map.of("resposta", texto.toString(), "citacoes", citacoes));
        return new Resposta(json, inicioResposta.stopReason(), inicioResposta.recusa(), inicioResposta.inputTokens(), inicioResposta.outputTokens(),
                inicioResposta.cacheReadInputTokens(), inicioResposta.cacheCreationInputTokens(), inicioResposta.latenciaMs(), inicioResposta.modelo());
    }

    private MessageCreateParams.Builder base(RotaIa rota, String systemPromptFixo, long maxTokens) {
        // parte fixa primeiro, com cache_control; a variável vai na mensagem do usuário
        var system = TextBlockParam.builder().text(systemPromptFixo).cacheControl(CacheControlEphemeral.builder().build()).build();
        return MessageCreateParams.builder()
                .model(RotaIa.MODELO_PADRAO)
                .maxTokens(maxTokens)
                .system(MessageCreateParams.System.ofTextBlockParams(List.of(system)))
                // fallback do servidor (beta): a API troca para o modelo padrão de fallback em indisponibilidade
                .putAdditionalHeader("anthropic-beta", BETA_FALLBACK)
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                .putAdditionalBodyProperty("metadata", JsonValue.from(Map.of("user_id", "rota:" + rota.id())));
    }

    /** streaming sempre: respostas longas não estouram timeout; a mensagem final sai do acumulador do SDK */
    private Message executar(MessageCreateParams params) {
        try {
            var acumulador = MessageAccumulator.create();
            try (var stream = cliente.messages().createStreaming(params)) {
                stream.stream().forEach(acumulador::accumulate);
            }
            return acumulador.message();
        } catch (RateLimitException e) {
            // retentável: o SDK já aplicou as tentativas padrão; aqui vira 429 para o chamador
            throw new br.com.civilconstructorai.comum.ExcecaoDominio.LimiteExcedido(30);
        } catch (BadRequestException e) {
            // não retentável: requisição inválida (schema, tamanho) — erro de programação, não do usuário
            log.error("requisição inválida à API de IA: {}", e.getMessage());
            throw new IllegalStateException("Requisição inválida à camada de IA.", e);
        } catch (AnthropicServiceException e) {
            throw new IllegalStateException("A camada de IA está indisponível no momento.", e);
        } catch (AnthropicIoException e) {
            throw new IllegalStateException("Falha de rede ao chamar a camada de IA.", e);
        }
    }

    private Resposta converter(Message msg, long inicioNanos) {
        var stop = msg.stopReason().map(StopReason::asString).orElse("end_turn");
        var recusa = msg.stopReason().filter(s -> s.equals(StopReason.REFUSAL)).isPresent();
        var texto = new StringBuilder();
        msg.content().forEach(b -> b.text().ifPresent(t -> texto.append(t.text())));
        var u = msg.usage();
        return new Resposta(texto.toString(), stop, recusa, u.inputTokens(), u.outputTokens(),
                u.cacheReadInputTokens().orElse(0L), u.cacheCreationInputTokens().orElse(0L),
                (System.nanoTime() - inicioNanos) / 1_000_000, msg.model().asString());
    }

    private static OutputConfig.Effort effort(RotaIa rota) {
        return switch (rota.effort()) {
            case LOW -> OutputConfig.Effort.LOW;
            case MEDIUM -> OutputConfig.Effort.MEDIUM;
            case HIGH -> OutputConfig.Effort.HIGH;
        };
    }

    private JsonOutputFormat.Schema schema(String jsonSchema) {
        Map<String, Object> mapa = mapper.readValue(jsonSchema, mapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
        var b = JsonOutputFormat.Schema.builder();
        mapa.forEach((k, v) -> b.putAdditionalProperty(k, JsonValue.from(v)));
        return b.build();
    }
}
