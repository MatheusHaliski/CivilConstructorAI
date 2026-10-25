package br.com.civilconstructorai.ia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class Repositorios {

    private Repositorios() {
    }

    public interface RascunhoIaRepository extends JpaRepository<RascunhoIa, UUID> {
        List<RascunhoIa> findByObraIdOrderByCriadoEmDesc(UUID obraId);
        List<RascunhoIa> findByObraIdAndEstadoOrderByCriadoEmDesc(UUID obraId, EstadoRascunhoIa estado);
        Optional<RascunhoIa> findByIdAndObraId(UUID id, UUID obraId);
        List<RascunhoIa> findByObraIdAndEntidadeRefAndEstadoIn(UUID obraId, String entidadeRef, List<EstadoRascunhoIa> estados);
    }

    public interface PedidoRevisaoHumanaRepository extends JpaRepository<PedidoRevisaoHumana, UUID> {
        List<PedidoRevisaoHumana> findByRascunhoIdOrderByCriadoEmDesc(UUID rascunhoId);
    }

    public interface ChamadaIaRepository extends JpaRepository<ChamadaIa, UUID> {
    }
}
