package br.com.civilconstructorai.ia;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Implementação falsa e determinística da camada de IA para dev e testes ({@code app.ia.habilitada=false}).
 * Devolve um JSON mínimo válido por rota; a implementação real é {@code ClaudeModeloLinguagem}.
 */
@Component
@ConditionalOnProperty(name = "app.ia.habilitada", havingValue = "false", matchIfMissing = true)
public class FakeModeloLinguagem implements ModeloLinguagem {

    @Override
    public Resposta gerarEstruturado(RotaIa rota, String systemPromptFixo, String entradaVariavel, String jsonSchema, Map<String, String> contexto) {
        var json = switch (rota) {
            case COMANDO_VI -> "{\"comandos\":[{\"codigo\":\"PAREDE\",\"args\":{\"p1\":[0,0],\"p2\":[5,0],\"esp\":0.15,\"h\":2.8,\"mat\":\"MAT_ALV_BLOCO14\"}}]}";
            case LANCAMENTO_ESTRUTURAL -> "{\"pilares\":[],\"vigas\":[],\"lajes\":[]}";
            case EXTRACAO_BOLETIM_SPT -> "{\"furos\":[]}";
            case ASSOCIACAO_SINAPI -> "{\"associacoes\":[]}";
            case CORRECAO_AUDITORIA -> "{\"comandos\":[]}";
        };
        return new Resposta(json, "end_turn", false, 120, 40, 100, 0, 5, "fake-" + RotaIa.MODELO_PADRAO);
    }

    @Override
    public Resposta responderComCitacoes(RotaIa rota, String systemPromptFixo, byte[] pdf, String pergunta) {
        return new Resposta("{\"resposta\":\"(fake)\",\"citacoes\":[]}", "end_turn", false, 300, 60, 0, 0, 5, "fake-" + RotaIa.MODELO_PADRAO);
    }
}
