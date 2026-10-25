package br.com.civilconstructorai.estrutura;

import br.com.civilconstructorai.comum.MaquinaDeEstados;

import java.util.Map;
import java.util.Set;

/** Ciclo de todo resultado que exige responsabilidade técnica (IA ou cálculo): rascunho até aprovação (RF27). */
public enum EstadoRascunho {
    PENDENTE, EM_EXECUCAO, GERADO_POR_IA, CALCULADO, EM_REVISAO, APLICADO_NO_MODELO, APROVADO, REJEITADO, FALHOU, OBSOLETO;

    public static final MaquinaDeEstados<EstadoRascunho> MAQUINA = new MaquinaDeEstados<>("Rascunho", Map.of(
            PENDENTE, Set.of(EM_EXECUCAO, OBSOLETO),
            EM_EXECUCAO, Set.of(GERADO_POR_IA, CALCULADO, FALHOU, OBSOLETO),
            GERADO_POR_IA, Set.of(EM_REVISAO, REJEITADO, OBSOLETO),
            CALCULADO, Set.of(EM_REVISAO, OBSOLETO),
            EM_REVISAO, Set.of(CALCULADO, APLICADO_NO_MODELO, APROVADO, REJEITADO, OBSOLETO),
            APLICADO_NO_MODELO, Set.of(APROVADO, REJEITADO, OBSOLETO),
            APROVADO, Set.of(OBSOLETO),
            REJEITADO, Set.of(OBSOLETO),
            FALHOU, Set.of(PENDENTE, OBSOLETO),
            OBSOLETO, Set.of()));
}
