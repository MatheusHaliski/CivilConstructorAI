package br.com.civilconstructorai.estrutura;

import br.com.civilconstructorai.estrutura.modelo.ModeloEstrutural;

import java.util.UUID;

/**
 * Porta: recorte estrutural do modelo BIM de uma obra (pavimentos + elementos). A implementação de produção
 * lê as tabelas do módulo {@code modelo} (JdbcTemplate); a de testes guarda modelos em memória.
 */
public interface ModeloEstruturalFonte {

    ModeloEstrutural carregar(UUID obraId);

    default long versaoAtual(UUID obraId) {
        return carregar(obraId).versao();
    }
}
