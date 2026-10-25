package br.com.civilconstructorai.prancha;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Porta de persistência de pranchas (RF14). */
public interface PranchaRepositorio {

    enum Estado { EM_GERACAO, GERADA, EMITIDA, OBSOLETA, FALHOU }

    enum Tipo { PLANTA_BAIXA, PLANTA_FORMAS, CORTE, ELEVACAO }

    record Prancha(UUID id, UUID obraId, UUID pavimentoId, Tipo tipo, Estado estado, String formato, String escala, long versaoModelo,
                   String chaveSvg, String chaveDxf, String erro, UUID solicitadaPor, UUID emitidaPor, String artRrt, Instant criadaEm, Instant emitidaEm) {
        public Prancha com(Estado novo, String svg, String dxf, String erro) {
            return new Prancha(id, obraId, pavimentoId, tipo, novo, formato, escala, versaoModelo, svg, dxf, erro, solicitadaPor, emitidaPor, artRrt, criadaEm, emitidaEm);
        }

        public Prancha emitida(UUID por, String art, Instant quando) {
            return new Prancha(id, obraId, pavimentoId, tipo, Estado.EMITIDA, formato, escala, versaoModelo, chaveSvg, chaveDxf, erro, solicitadaPor, por, art, criadaEm, quando);
        }
    }

    void salvar(Prancha p);

    Optional<Prancha> buscar(UUID obraId, UUID id);

    List<Prancha> listar(UUID obraId);

    List<Prancha> pendentes(int limite);

    void marcarObsoletas(UUID obraId, long versaoModelo);
}
