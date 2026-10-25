package br.com.civilconstructorai.comum;

import java.util.Map;
import java.util.Set;

/** Máquina de estados declarativa: transição inválida = {@link ExcecaoDominio.TransicaoInvalida} (409). */
public final class MaquinaDeEstados<E extends Enum<E>> {

    private final String entidade;
    private final Map<E, Set<E>> transicoes;

    public MaquinaDeEstados(String entidade, Map<E, Set<E>> transicoes) {
        this.entidade = entidade;
        this.transicoes = Map.copyOf(transicoes);
    }

    public boolean permite(E de, E para) {
        return transicoes.getOrDefault(de, Set.of()).contains(para);
    }

    public E transicionar(E de, E para) {
        if (!permite(de, para)) {
            throw new ExcecaoDominio.TransicaoInvalida(entidade, de, para);
        }
        return para;
    }

    public boolean terminal(E estado) {
        return transicoes.getOrDefault(estado, Set.of()).isEmpty();
    }
}
