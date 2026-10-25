package br.com.civilconstructorai.modelo;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Porta de persistência do modelo BIM (implementada em cca-infrastructure sobre PostgreSQL com RLS). */
public interface ModeloRepositorio {

    record ModeloCarregado(UUID modeloId, UUID obraId, long versao, boolean liberadaCliente, Long versaoLiberada, ModeloBim.Estado estado) {
    }

    record ComandoRegistro(UUID id, UUID obraId, String codigo, String vi, Enums.OrigemComando origem, long versaoBase, long versaoNova, UUID autorId,
                           Instant criadoEm, ModeloBim.Estado estadoAnterior, RelatorioAuditoria relatorio) {
    }

    record CicloRegistro(UUID id, UUID obraId, UUID comandoId, Enums.EstadoCiclo estado, int iteracoes, List<Iteracao> historico, Instant iniciadoEm, Instant encerradoEm) {
        public record Iteracao(int numero, String laco, int violacoes, long inputTokens, long outputTokens, String stopReason, long latenciaMs) {
        }
    }

    record AlertaTransicao(UUID id, UUID obraId, UUID elementoId, String tipo, String estado, String mensagem, String justificativa, Instant criadoEm) {
    }

    Optional<ModeloCarregado> carregar(UUID obraId);

    ModeloCarregado criar(UUID obraId);

    /** trava a linha do modelo (SELECT … FOR UPDATE) e devolve a versão atual */
    long travarVersao(UUID obraId);

    void salvarAplicacao(UUID obraId, ModeloBim.Estado antes, ModeloBim.Estado depois, DiffModelo diff, ComandoVi comando, String vi, RelatorioAuditoria relatorio);

    Optional<ComandoRegistro> comando(UUID obraId, UUID comandoId);

    List<ComandoRegistro> comandos(UUID obraId, long desdeVersaoExclusiva);

    Optional<ModeloBim.Estado> estadoNaVersao(UUID obraId, long versao);

    void liberarCliente(UUID obraId, boolean liberar, long versao);

    void salvarCiclo(CicloRegistro ciclo);

    List<CicloRegistro> ciclos(UUID obraId, UUID comandoId);

    List<AlertaTransicao> alertas(UUID obraId);

    void salvarAlerta(AlertaTransicao alerta);

    Optional<AlertaTransicao> alerta(UUID obraId, UUID alertaId);
}
