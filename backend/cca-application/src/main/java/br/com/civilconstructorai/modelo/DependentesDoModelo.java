package br.com.civilconstructorai.modelo;

import java.util.Set;
import java.util.UUID;

/**
 * Porta: o que depende de uma versão do modelo e deixa de valer quando ele muda (RF04 CA-08, RF05 CA-02, RF07 CA-06,
 * RF10 CA-05, RF11 CA-06, RF14 CA-03). Chamada na mesma transação da gravação do comando.
 * <p>
 * Auditoria 2026-10: nada era invalidado — um pilar novo deixava a análise "Concluído" na tela e o lançamento
 * "pronto para análise"; {@code PranchaRepositorio.marcarObsoletas} existia mas não era chamado.
 */
public interface DependentesDoModelo {

    /**
     * @param estrutural   mudou elemento estrutural, eixo, pavimento ou carga: análise, dimensionamentos e fundações caducam
     *                     e o lançamento dos pavimentos afetados volta a {@code EM_LANCAMENTO}
     * @param construtivo  mudou elemento construtivo (inclui portas e janelas): o levantamento de quantitativos caduca
     * @param pavimentos   pavimentos tocados (vazio = todos)
     */
    void modeloMudou(UUID obraId, long versaoNova, boolean estrutural, boolean construtivo, Set<String> pavimentos);

    /** carga ou vento mudou sem mudar a versão do modelo: toda análise/dimensionamento/fundação da obra caduca (RF04 CA-08) */
    void cargasMudaram(UUID obraId, String pavimentoId);
}
