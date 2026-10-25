package br.com.civilconstructorai.estrutura.dimensionamento;

import java.util.List;

/**
 * Uma verificação de um elemento, com estado explícito. "Atendida" só quando a verificação foi executada e passou;
 * verificação ausente ou fora do escopo do método é NAO_VERIFICADA, nunca aprovação.
 *
 * @param etapa      ANALISE (solicitações), TENSOES, VERIFICACAO (resistência/serviço), DIMENSIONAMENTO, DETALHAMENTO
 * @param referencia documento, edição e item (ex.: "ABNT NBR 6118:2023, 17.4.2.2")
 * @param evidencia  valores usados e obtidos, com unidades
 */
public record Verificacao(String codigo, String nome, String etapa, Estado estado, String referencia, String evidencia, String motivo) {

    public enum Estado {
        NAO_VERIFICADA, EM_PROCESSAMENTO, ATENDIDA, NAO_ATENDIDA, DADOS_INSUFICIENTES, DESATUALIZADA, NAO_APLICAVEL
    }

    public static Verificacao atendida(String codigo, String nome, String etapa, String ref, String evidencia) {
        return new Verificacao(codigo, nome, etapa, Estado.ATENDIDA, ref, evidencia, null);
    }

    public static Verificacao naoAtendida(String codigo, String nome, String etapa, String ref, String evidencia, String motivo) {
        return new Verificacao(codigo, nome, etapa, Estado.NAO_ATENDIDA, ref, evidencia, motivo);
    }

    public static Verificacao naoVerificada(String codigo, String nome, String etapa, String ref, String motivo) {
        return new Verificacao(codigo, nome, etapa, Estado.NAO_VERIFICADA, ref, null, motivo);
    }

    public static Verificacao dadosInsuficientes(String codigo, String nome, String etapa, String ref, String motivo) {
        return new Verificacao(codigo, nome, etapa, Estado.DADOS_INSUFICIENTES, ref, null, motivo);
    }

    public static Verificacao naoAplicavel(String codigo, String nome, String etapa, String justificativa) {
        return new Verificacao(codigo, nome, etapa, Estado.NAO_APLICAVEL, null, null, justificativa);
    }

    /**
     * Estado do elemento: qualquer não atendida → NAO_ATENDIDA; senão qualquer dado insuficiente → DADOS_INSUFICIENTES;
     * senão qualquer não verificada → NAO_VERIFICADA; só então ATENDIDA. Não aplicável não conta.
     */
    public static Estado consolidar(List<Verificacao> vs) {
        if (vs.stream().anyMatch(v -> v.estado() == Estado.NAO_ATENDIDA)) {
            return Estado.NAO_ATENDIDA;
        }
        if (vs.stream().anyMatch(v -> v.estado() == Estado.DADOS_INSUFICIENTES)) {
            return Estado.DADOS_INSUFICIENTES;
        }
        if (vs.stream().anyMatch(v -> v.estado() == Estado.NAO_VERIFICADA)) {
            return Estado.NAO_VERIFICADA;
        }
        if (vs.stream().noneMatch(v -> v.estado() == Estado.ATENDIDA)) {
            return Estado.NAO_VERIFICADA;
        }
        return Estado.ATENDIDA;
    }
}
