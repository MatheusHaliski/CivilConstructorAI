package br.com.civilconstructorai.registro;

import java.util.Map;
import java.util.UUID;

/**
 * Registro de operações (LGPD Art. 37): toda aprovação, mudança de papel, exportação, acesso de suporte,
 * aceite e revogação de consentimento. Só ids e códigos — <strong>nunca</strong> valores pessoais
 * (nome, e-mail, CPF, IP em claro, conteúdo de ASO).
 */
public interface RegistroOperacoes {

    void registrar(Operacao operacao);

    record Operacao(
            String tipo,
            UUID autorId,
            UUID empresaId,
            UUID obraId,
            String entidade,
            UUID entidadeId,
            Map<String, String> atributos) {

        public static Operacao de(String tipo, UUID autorId, UUID empresaId, UUID obraId, String entidade, UUID entidadeId) {
            return new Operacao(tipo, autorId, empresaId, obraId, entidade, entidadeId, Map.of());
        }
    }
}
