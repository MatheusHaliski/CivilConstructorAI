package br.com.civilconstructorai.cimento;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface DosagemCimentoRepository extends JpaRepository<DosagemCimento, UUID> {
    List<DosagemCimento> findByObraIdAndVersaoModeloOrderByCriadoEmDesc(UUID obraId, long versaoModelo);
    List<DosagemCimento> findByObraIdOrderByCriadoEmDesc(UUID obraId);
    Optional<DosagemCimento> findByIdAndObraId(UUID id, UUID obraId);
}
