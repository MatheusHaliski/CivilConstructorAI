package br.com.civilconstructorai.material;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class Repositorios {

    private Repositorios() {
    }

    @Repository("materialPersonalizadoRepository")
    public interface MateriaisPersonalizados extends JpaRepository<MaterialPersonalizado, UUID> {
        List<MaterialPersonalizado> findByTenantIdOrderByNome(UUID tenantId);

        Optional<MaterialPersonalizado> findByTenantIdAndCodigoIgnoreCase(UUID tenantId, String codigo);

        Optional<MaterialPersonalizado> findByIdAndTenantId(UUID id, UUID tenantId);
    }
}
