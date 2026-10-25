package br.com.civilconstructorai.orcamento;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Repositórios do módulo orçamento (RF12). */
public final class Repositorios {

    private Repositorios() {
    }

    @Repository("orcamentoTabelas")

    public interface Tabelas extends JpaRepository<TabelaSinapi, UUID> {
        List<TabelaSinapi> findAllByOrderByMesReferenciaDesc();

        Optional<TabelaSinapi> findFirstByUfAndDesoneradoOrderByMesReferenciaDesc(String uf, boolean desonerado);
    }

    @Repository("orcamentoComposicoes")

    public interface Composicoes extends JpaRepository<ComposicaoSinapi, UUID> {
        Optional<ComposicaoSinapi> findByTabelaIdAndCodigo(UUID tabelaId, String codigo);

        List<ComposicaoSinapi> findByTabelaIdAndDescricaoContainingIgnoreCase(UUID tabelaId, String termo);

        List<ComposicaoSinapi> findByTabelaId(UUID tabelaId);

        long countByTabelaId(UUID tabelaId);
    }

    @Repository("orcamentoOrcamentos")

    public interface Orcamentos extends JpaRepository<Orcamento, UUID> {
        List<Orcamento> findByObraIdOrderByCriadoEmDesc(UUID obraId);

        Optional<Orcamento> findByIdAndObraId(UUID id, UUID obraId);
    }

    @Repository("orcamentoItens")

    public interface Itens extends JpaRepository<ItemOrcamento, UUID> {
        List<ItemOrcamento> findByOrcamentoIdOrderByEtapaAscServicoAsc(UUID orcamentoId);

        Optional<ItemOrcamento> findByIdAndOrcamentoId(UUID id, UUID orcamentoId);
    }

    @Repository("orcamentoAditivos")

    public interface Aditivos extends JpaRepository<Aditivo, UUID> {
        List<Aditivo> findByOrcamentoIdOrderByCriadoEmDesc(UUID orcamentoId);

        Optional<Aditivo> findByIdAndOrcamentoId(UUID id, UUID orcamentoId);
    }
}
