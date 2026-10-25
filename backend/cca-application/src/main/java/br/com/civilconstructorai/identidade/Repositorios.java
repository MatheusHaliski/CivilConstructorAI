package br.com.civilconstructorai.identidade;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Repositórios Spring Data do módulo de identidade (implementados pela infraestrutura JPA). */
public final class Repositorios {

    private Repositorios() {
    }

    @Repository("identidadeContaRepository")
    public interface ContaRepository extends JpaRepository<Conta, UUID> {
        Optional<Conta> findByEmailIgnoreCase(String email);

        boolean existsByEmailIgnoreCase(String email);

        List<Conta> findBySeloEstado(EstadoSelo estado);

        List<Conta> findByEstadoAndEliminacaoPrevistaEmBefore(EstadoConta estado, Instant limite);
    }

    @Repository("identidadeCodigoVerificacaoRepository")
    public interface CodigoVerificacaoRepository extends JpaRepository<CodigoVerificacao, UUID> {
        Optional<CodigoVerificacao> findTopByContaIdAndFinalidadeAndUsadoEmIsNullOrderByCriadoEmDesc(UUID contaId, CodigoVerificacao.Finalidade finalidade);

        @Modifying
        @Query("delete from CodigoVerificacao c where c.contaId = :contaId and c.finalidade = :finalidade")
        void apagarPendentes(UUID contaId, CodigoVerificacao.Finalidade finalidade);
    }

    @Repository("identidadeCodigoRecuperacaoMfaRepository")
    public interface CodigoRecuperacaoMfaRepository extends JpaRepository<CodigoRecuperacaoMfa, UUID> {
        List<CodigoRecuperacaoMfa> findByContaIdAndUsadoEmIsNull(UUID contaId);

        void deleteByContaId(UUID contaId);

        long countByContaIdAndUsadoEmIsNull(UUID contaId);
    }

    @Repository("identidadeUserKeyRepository")
    public interface UserKeyRepository extends JpaRepository<UserKey, UUID> {
    }

    @Repository("identidadePreferenciaRepository")
    public interface PreferenciaRepository extends JpaRepository<Preferencia, UUID> {
    }
}
