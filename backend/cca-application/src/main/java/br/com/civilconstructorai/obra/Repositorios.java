package br.com.civilconstructorai.obra;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class Repositorios {

    private Repositorios() {
    }

    @Repository("obraObraRepository")
    public interface ObraRepository extends JpaRepository<Obra, UUID> {
        List<Obra> findByTenantIdOrderByNome(UUID tenantId);

        Optional<Obra> findByIdAndTenantId(UUID id, UUID tenantId);
    }

    @Repository("obraMembroObraRepository")
    public interface MembroObraRepository extends JpaRepository<MembroObra, UUID> {
        List<MembroObra> findByContaId(UUID contaId);

        List<MembroObra> findByContaIdAndTenantId(UUID contaId, UUID tenantId);

        List<MembroObra> findByObraId(UUID obraId);

        List<MembroObra> findByObraIdAndContaId(UUID obraId, UUID contaId);

        boolean existsByObraIdAndPapel(UUID obraId, br.com.civilconstructorai.acesso.PapelObra papel);
    }
}
