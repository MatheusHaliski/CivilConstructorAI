package br.com.civilconstructorai.lgpd;

import br.com.civilconstructorai.comum.MaquinaDeEstados;

import java.util.Map;
import java.util.Set;

/**
 * Estado atual do consentimento por finalidade. {@code A_CONFIRMAR} aparece quando o texto da finalidade mudou
 * de versão: o consentimento antigo deixa de valer até a pessoa confirmar de novo (Art. 8º §5º).
 */
public enum EstadoConsentimento {
    NAO_CONCEDIDO, CONCEDIDO, REVOGADO, A_CONFIRMAR, ENCERRADO;

    public static final MaquinaDeEstados<EstadoConsentimento> MAQUINA = new MaquinaDeEstados<>("Consentimento", Map.of(
            NAO_CONCEDIDO, Set.of(CONCEDIDO, ENCERRADO),
            CONCEDIDO, Set.of(REVOGADO, A_CONFIRMAR, ENCERRADO),
            REVOGADO, Set.of(CONCEDIDO, ENCERRADO),
            A_CONFIRMAR, Set.of(CONCEDIDO, REVOGADO, ENCERRADO),
            ENCERRADO, Set.of()));

    /** a finalidade está efetivamente autorizada agora? */
    public boolean ativo() {
        return this == CONCEDIDO;
    }
}
