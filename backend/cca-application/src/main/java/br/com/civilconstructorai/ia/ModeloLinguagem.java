package br.com.civilconstructorai.ia;

import java.util.Map;

/**
 * Porta da camada de IA (ADR-002/003). A única implementação real é o SDK oficial da Anthropic
 * ({@code ClaudeModeloLinguagem}, cca-infrastructure); nos testes entra {@code FakeModeloLinguagem},
 * determinística. Nunca recebe dado pessoal (ADR-004): quem chama já removeu CPF, nomes, ASO, e-mail.
 */
public interface ModeloLinguagem {

    /** Structured outputs: resposta validada contra o JSON Schema da rota, sem texto discursivo. */
    Resposta gerarEstruturado(RotaIa rota, String systemPromptFixo, String entradaVariavel, String jsonSchema, Map<String, String> contexto);

    /** Resposta com citações (página e trecho) sobre um documento PDF — nunca na mesma chamada que structured outputs. */
    Resposta responderComCitacoes(RotaIa rota, String systemPromptFixo, byte[] pdf, String pergunta);

    record Resposta(
            String conteudoJson,
            String stopReason,
            boolean recusa,
            long inputTokens,
            long outputTokens,
            long cacheReadInputTokens,
            long cacheCreationInputTokens,
            long latenciaMs,
            String modelo) {

        public boolean completa() {
            return !recusa && "end_turn".equals(stopReason);
        }
    }
}
