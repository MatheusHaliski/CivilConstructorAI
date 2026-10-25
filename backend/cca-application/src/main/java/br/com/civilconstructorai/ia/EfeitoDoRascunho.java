package br.com.civilconstructorai.ia;

import java.util.UUID;

/**
 * O que acontece fora do registro do rascunho quando ele é decidido (RF27): cada módulo que gera rascunhos registra o
 * seu efeito pelo {@link #tipo()} — por exemplo, o comando VI promove ou descarta os elementos no modelo.
 */
public interface EfeitoDoRascunho {

    String tipo();

    void aoAprovar(UUID obraId, RascunhoIa rascunho);

    void aoRejeitar(UUID obraId, RascunhoIa rascunho);
}
