package br.com.civilconstructorai.modelo;

import br.com.civilconstructorai.comum.MaquinaDeEstados;

import java.util.Map;
import java.util.Set;

/** Enumerações do modelo BIM e do ciclo de auditoria. */
public final class Enums {

    private Enums() {
    }

    public enum TipoElemento {
        PAREDE, PILAR, VIGA, LAJE, PORTA, JANELA, AMBIENTE,
        LINHA, POLILINHA, CIRCULO, RETANGULO, ARCO, PONTO, PLANO_REF,
        CONTRAVENTAMENTO, SAPATA, BLOCO, RADIER, ESTACA,
        COTA, TEXTO, ETIQUETA, HACHURA, OBJETO, CONEXAO;

        public boolean construtivo() {
            return switch (this) {
                case PAREDE, PILAR, VIGA, LAJE, PORTA, JANELA, SAPATA, BLOCO, RADIER, ESTACA, CONTRAVENTAMENTO -> true;
                default -> false;
            };
        }
    }

    public enum Camada { arquitetura, estrutura, fundacao, desenho, anotacao, ia_rascunho, mobiliario, eletrica, hidrossanitaria, humanos;
        public String rotulo() {
            return this == ia_rascunho ? "ia-rascunho" : name();
        }
    }

    public enum OrigemComando { CANVAS, CONSOLE_VI, FORMULARIO, IA, SISTEMA }

    public enum Severidade { AVISO, VIOLACAO }

    /** ciclo da auditoria paramétrica híbrida (RF16) */
    public enum EstadoCiclo {
        INICIADO, LACO_CODIGO, LACO_GEOMETRIA, CORRIGINDO_COM_LLM, APROVADO, REPROVADO;

        public static final MaquinaDeEstados<EstadoCiclo> MAQUINA = new MaquinaDeEstados<>("CicloAuditoria", Map.of(
                INICIADO, Set.of(LACO_CODIGO),
                LACO_CODIGO, Set.of(LACO_GEOMETRIA, CORRIGINDO_COM_LLM, REPROVADO),
                LACO_GEOMETRIA, Set.of(APROVADO, CORRIGINDO_COM_LLM, REPROVADO),
                CORRIGINDO_COM_LLM, Set.of(LACO_CODIGO, REPROVADO),
                APROVADO, Set.of(),
                REPROVADO, Set.of()));
    }

    /** estado do pavimento em relação ao fechamento (RF16 CA-09 / RF14) */
    public enum TipoPavimento { FUNDACAO, TERREO, TIPO, COBERTURA, CAIXA_DAGUA }
}
