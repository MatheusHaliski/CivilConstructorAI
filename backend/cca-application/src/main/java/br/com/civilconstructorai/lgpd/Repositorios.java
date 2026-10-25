package br.com.civilconstructorai.lgpd;

import br.com.civilconstructorai.comum.EstadoJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Repositórios Spring Data do módulo LGPD (um arquivo: são interfaces de uma linha). */
public final class Repositorios {

    private Repositorios() {
    }

    public interface ConsentimentoAtualRepository extends JpaRepository<ConsentimentoAtual, ConsentimentoAtual.Chave> {
        List<ConsentimentoAtual> findByChaveContaId(UUID contaId);
    }

    public interface ConsentEventRepository extends JpaRepository<ConsentEvent, UUID> {
        List<ConsentEvent> findByContaIdOrderByOcorridoEmDesc(UUID contaId);
    }

    public interface AceiteTermosRepository extends JpaRepository<AceiteTermos, UUID> {
        Optional<AceiteTermos> findFirstByContaIdAndTipoOrderByAceitoEmDesc(UUID contaId, TipoDocumentoLegal tipo);
        List<AceiteTermos> findByContaIdOrderByAceitoEmDesc(UUID contaId);
    }

    public interface DocumentoLegalRepository extends JpaRepository<DocumentoLegal, UUID> {
        Optional<DocumentoLegal> findByTipoAndVersao(TipoDocumentoLegal tipo, String versao);
        List<DocumentoLegal> findByTipoOrderByVigenteDesdeDesc(TipoDocumentoLegal tipo);
    }

    public interface PedidoTitularRepository extends JpaRepository<PedidoTitular, UUID> {
        List<PedidoTitular> findByContaIdOrderByCriadoEmDesc(UUID contaId);
        List<PedidoTitular> findByTenantIdOrderByPrazoEmAsc(UUID tenantId);
        Optional<PedidoTitular> findByIdAndTenantId(UUID id, UUID tenantId);
        @Query("select p from PedidoTitular p where p.estado not in ('ATENDIDO','NEGADO','ATRASADO') and p.prazoEm < :agora")
        List<PedidoTitular> vencidos(Instant agora);
    }

    public interface ExportacaoRepository extends JpaRepository<Exportacao, UUID> {
        Optional<Exportacao> findByIdAndTitularId(UUID id, UUID titularId);
        List<Exportacao> findByTitularIdOrderByCriadoEmDesc(UUID titularId);
        long countByTitularIdAndCriadoEmAfter(UUID titularId, Instant desde);
        List<Exportacao> findByEstadoOrderByCriadoEmAsc(EstadoJob estado);
        boolean existsByTitularIdAndEstadoIn(UUID titularId, List<EstadoJob> estados);
    }

    public interface RetencaoEmpresaRepository extends JpaRepository<RetencaoEmpresa, UUID> {
        List<RetencaoEmpresa> findByTenantId(UUID tenantId);
        Optional<RetencaoEmpresa> findByTenantIdAndCategoria(UUID tenantId, CategoriaDado categoria);
    }

    public interface SagaEliminacaoRepository extends JpaRepository<SagaEliminacao, UUID> {
        Optional<SagaEliminacao> findByContaId(UUID contaId);
    }

    public interface RetencaoLegalRepository extends JpaRepository<RetencaoLegal, UUID> {
        List<RetencaoLegal> findBySagaId(UUID sagaId);
    }

    public interface ReciboEliminacaoRepository extends JpaRepository<ReciboEliminacao, UUID> {
        Optional<ReciboEliminacao> findBySagaId(UUID sagaId);
    }

    public interface UserKeyRepository extends JpaRepository<UserKey, UUID> {
    }
}
