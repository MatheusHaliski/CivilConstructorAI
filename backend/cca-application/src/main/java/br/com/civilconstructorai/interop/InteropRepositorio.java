package br.com.civilconstructorai.interop;

import br.com.civilconstructorai.comum.EstadoJob;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Porta de persistência de exportações (IFC/STEP) e pareamentos Revit (RF15). */
public interface InteropRepositorio {

    enum Formato { IFC4X3, STEP_AP242 }

    record Exportacao(UUID id, UUID obraId, Formato formato, EstadoJob estado, long versaoModelo, String chave, String erro, UUID solicitadaPor, Instant criadaEm, Instant concluidaEm) {
        public Exportacao com(EstadoJob novo, String chave, String erro) {
            return new Exportacao(id, obraId, formato, novo, versaoModelo, chave, erro, solicitadaPor, criadaEm, novo == EstadoJob.CONCLUIDO || novo == EstadoJob.FALHOU ? Instant.now() : concluidaEm);
        }
    }

    enum EstadoPareamento { AGUARDANDO_CODIGO, PAREADO, EXPIRADO, REVOGADO }

    record Pareamento(UUID id, UUID obraId, UUID contaId, EstadoPareamento estado, String codigoHash, String tokenHash, String dispositivo, Instant expiraEm, Instant criadoEm, Instant pareadoEm) {
        public Pareamento com(EstadoPareamento novo, String tokenHash, String dispositivo, Instant pareadoEm) {
            return new Pareamento(id, obraId, contaId, novo, codigoHash, tokenHash, dispositivo, expiraEm, criadoEm, pareadoEm);
        }
    }

    void salvar(Exportacao e);

    Optional<Exportacao> exportacao(UUID obraId, UUID id);

    List<Exportacao> exportacoes(UUID obraId);

    List<Exportacao> exportacoesPendentes(int limite);

    void salvar(Pareamento p);

    Optional<Pareamento> pareamento(UUID obraId, UUID id);

    Optional<Pareamento> pareamentoPorCodigo(String codigoHash);

    Optional<Pareamento> pareamentoPorToken(String tokenHash);

    List<Pareamento> pareamentos(UUID obraId);
}
