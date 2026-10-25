package br.com.civilconstructorai.estrutura.lancamento;

import br.com.civilconstructorai.comum.MaquinaDeEstados;

import java.util.Map;
import java.util.Set;

/** Estado do lançamento estrutural por pavimento (RF04). */
public enum EstadoLancamento {
    VAZIO, SUGESTAO_IA, EM_LANCAMENTO, CARREGADO, PRONTO_PARA_ANALISE;

    public static final MaquinaDeEstados<EstadoLancamento> MAQUINA = new MaquinaDeEstados<>("Lançamento", Map.of(
            VAZIO, Set.of(SUGESTAO_IA, EM_LANCAMENTO),
            SUGESTAO_IA, Set.of(EM_LANCAMENTO, VAZIO),
            EM_LANCAMENTO, Set.of(CARREGADO, EM_LANCAMENTO),
            CARREGADO, Set.of(PRONTO_PARA_ANALISE, EM_LANCAMENTO, CARREGADO),
            PRONTO_PARA_ANALISE, Set.of(EM_LANCAMENTO, CARREGADO)));
}
