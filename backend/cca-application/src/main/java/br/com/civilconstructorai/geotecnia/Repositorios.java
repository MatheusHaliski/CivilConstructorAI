package br.com.civilconstructorai.geotecnia;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Repositórios do módulo geotecnia (RF09, RF10). */
public final class Repositorios {

    private Repositorios() {
    }

    @Repository("geotecniaFuros")

    public interface Furos extends JpaRepository<FuroSondagem, UUID> {
        List<FuroSondagem> findByObraIdOrderByCodigoAsc(UUID obraId);

        Optional<FuroSondagem> findByIdAndObraId(UUID id, UUID obraId);

        boolean existsByObraIdAndCodigo(UUID obraId, String codigo);
    }

    @Repository("geotecniaExtracoes")

    public interface Extracoes extends JpaRepository<ExtracaoBoletim, UUID> {
        List<ExtracaoBoletim> findByObraIdOrderByCriadoEmDesc(UUID obraId);

        Optional<ExtracaoBoletim> findByIdAndObraId(UUID id, UUID obraId);
    }

    @Repository("geotecniaDimensionamentosFundacao")

    public interface DimensionamentosFundacao extends JpaRepository<DimensionamentoFundacao, UUID> {
        List<DimensionamentoFundacao> findByObraIdOrderByCriadoEmDesc(UUID obraId);

        Optional<DimensionamentoFundacao> findByIdAndObraId(UUID id, UUID obraId);
    }

    @Repository("geotecniaPropostas")

    public interface Propostas extends JpaRepository<FundacaoProposta, UUID> {
        List<FundacaoProposta> findByDimensionamentoIdOrderByPilarRotuloAsc(UUID dimensionamentoId);
    }
}
