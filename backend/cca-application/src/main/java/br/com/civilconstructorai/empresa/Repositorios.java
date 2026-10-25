package br.com.civilconstructorai.empresa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class Repositorios {

    private Repositorios() {
    }

    @Repository("empresaEmpresaRepository")
    public interface EmpresaRepository extends JpaRepository<Empresa, UUID> {
        boolean existsByCnpj(String cnpj);
    }

    @Repository("empresaMembroEmpresaRepository")
    public interface MembroEmpresaRepository extends JpaRepository<MembroEmpresa, UUID> {
        List<MembroEmpresa> findByContaId(UUID contaId);

        List<MembroEmpresa> findByTenantId(UUID tenantId);

        Optional<MembroEmpresa> findByTenantIdAndContaId(UUID tenantId, UUID contaId);
    }

    @Repository("empresaConviteRepository")
    public interface ConviteRepository extends JpaRepository<Convite, UUID> {
        Optional<Convite> findByTokenHash(String tokenHash);

        List<Convite> findByTenantIdOrderByCriadoEmDesc(UUID tenantId);

        List<Convite> findByTenantIdAndEstadoOrderByCriadoEmDesc(UUID tenantId, EstadoConvite estado);

        long countByTenantIdAndEmailIgnoreCaseAndEstado(UUID tenantId, String email, EstadoConvite estado);
    }
}
