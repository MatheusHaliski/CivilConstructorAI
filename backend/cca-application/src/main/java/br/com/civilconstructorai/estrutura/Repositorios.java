package br.com.civilconstructorai.estrutura;

import br.com.civilconstructorai.estrutura.analise.AnaliseEstrutural;
import br.com.civilconstructorai.estrutura.carga.Carga;
import br.com.civilconstructorai.estrutura.dimensionamento.Dimensionamento;
import br.com.civilconstructorai.estrutura.lancamento.LancamentoEstrutural;
import br.com.civilconstructorai.estrutura.lancamento.RascunhoLancamento;
import br.com.civilconstructorai.estrutura.restricoes.ExcecaoNormativa;
import br.com.civilconstructorai.estrutura.restricoes.RestricoesProjeto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Repositórios Spring Data do módulo estrutura (RF04–RF08). */
public final class Repositorios {

    private Repositorios() {
    }

    @Repository("estruturaCargas")

    public interface Cargas extends JpaRepository<Carga, UUID> {
        List<Carga> findByObraIdOrderByPavimentoIdAscCriadoEmAsc(UUID obraId);

        Optional<Carga> findByIdAndObraId(UUID id, UUID obraId);
    }

    @Repository("estruturaLancamentos")

    public interface Lancamentos extends JpaRepository<LancamentoEstrutural, UUID> {
        Optional<LancamentoEstrutural> findByObraIdAndPavimentoId(UUID obraId, String pavimentoId);

        List<LancamentoEstrutural> findByObraId(UUID obraId);
    }

    @Repository("estruturaRascunhosLancamento")

    public interface RascunhosLancamento extends JpaRepository<RascunhoLancamento, UUID> {
        List<RascunhoLancamento> findByObraIdOrderByCriadoEmDesc(UUID obraId);

        Optional<RascunhoLancamento> findByIdAndObraId(UUID id, UUID obraId);
    }

    @Repository("estruturaAnalises")

    public interface Analises extends JpaRepository<AnaliseEstrutural, UUID> {
        List<AnaliseEstrutural> findByObraIdOrderByCriadoEmDesc(UUID obraId);

        Optional<AnaliseEstrutural> findByIdAndObraId(UUID id, UUID obraId);
    }

    @Repository("estruturaDimensionamentos")

    public interface Dimensionamentos extends JpaRepository<Dimensionamento, UUID> {
        List<Dimensionamento> findByObraIdOrderByCriadoEmDesc(UUID obraId);

        Optional<Dimensionamento> findByIdAndObraId(UUID id, UUID obraId);
    }

    @Repository("estruturaRestricoes")

    public interface Restricoes extends JpaRepository<RestricoesProjeto, UUID> {
        Optional<RestricoesProjeto> findByObraId(UUID obraId);
    }

    @Repository("estruturaExcecoesNormativas")

    public interface ExcecoesNormativas extends JpaRepository<ExcecaoNormativa, UUID> {
        List<ExcecaoNormativa> findByObraIdOrderByCriadoEmDesc(UUID obraId);

        Optional<ExcecaoNormativa> findByIdAndObraId(UUID id, UUID obraId);
    }
}
