package br.com.civilconstructorai.lgpd;

import br.com.civilconstructorai.comum.EntidadeBase;
import br.com.civilconstructorai.comum.MaquinaDeEstados;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Saga idempotente de eliminação (RF26): seis passos, cada um em transação própria; falha em um passo guarda
 * {@code passoAtual} e a próxima execução retoma dali, sem repetir os anteriores.
 */
@Entity
@Table(name = "saga_eliminacao")
public class SagaEliminacao extends EntidadeBase {

    public static final int PASSO_ANONIMIZAR = 1;
    public static final int PASSO_S3 = 2;
    public static final int PASSO_CASSANDRA = 3;
    public static final int PASSO_VALKEY = 4;
    public static final int PASSO_CHAVE = 5;
    public static final int PASSO_RECIBO = 6;
    public static final int CONCLUIDO = 7;

    public enum Estado {
        INICIADA, EM_EXECUCAO, FALHOU_PASSO, CONCLUIDA;

        public static final MaquinaDeEstados<Estado> MAQUINA = new MaquinaDeEstados<>("SagaEliminacao", Map.of(
                INICIADA, Set.of(EM_EXECUCAO),
                EM_EXECUCAO, Set.of(FALHOU_PASSO, CONCLUIDA, EM_EXECUCAO),
                FALHOU_PASSO, Set.of(EM_EXECUCAO),
                CONCLUIDA, Set.of()));
    }

    @Column(name = "conta_id", nullable = false, updatable = false)
    private UUID contaId;

    @Column(name = "conta_hash", nullable = false, updatable = false, columnDefinition = "text")
    private String contaHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "text")
    private Estado estado = Estado.INICIADA;

    @Column(name = "passo_atual", nullable = false)
    private int passoAtual = PASSO_ANONIMIZAR;

    @Column(nullable = false)
    private int tentativas;

    @Column(columnDefinition = "text")
    private String erro;

    /** JSON (lista) das chaves S3 a remover depois do commit do passo 1 */
    @Column(name = "chaves_s3", nullable = false, columnDefinition = "text")
    private String chavesS3 = "[]";

    @Column(name = "iniciada_em", nullable = false, updatable = false)
    private Instant iniciadaEm = Instant.now();

    @Column(name = "concluida_em")
    private Instant concluidaEm;

    protected SagaEliminacao() {
    }

    public SagaEliminacao(UUID contaId, String contaHash) {
        this.contaId = contaId;
        this.contaHash = contaHash;
    }

    public void executando() {
        estado = Estado.MAQUINA.transicionar(estado, Estado.EM_EXECUCAO);
        tentativas++;
        erro = null;
    }

    public void passoConcluido(int passo) {
        if (passo != passoAtual) {
            throw new IllegalStateException("Passo " + passo + " concluído fora de ordem (atual " + passoAtual + ").");
        }
        passoAtual = passo + 1;
    }

    public void falhou(String motivo) {
        estado = Estado.MAQUINA.transicionar(estado, Estado.FALHOU_PASSO);
        erro = motivo == null ? "falha" : motivo.substring(0, Math.min(500, motivo.length()));
    }

    public void concluir(Instant agora) {
        estado = Estado.MAQUINA.transicionar(estado, Estado.CONCLUIDA);
        passoAtual = CONCLUIDO;
        concluidaEm = agora;
    }

    public void registrarChavesS3(String json) {
        this.chavesS3 = json;
    }

    public UUID getContaId() { return contaId; }
    public String getContaHash() { return contaHash; }
    public Estado getEstado() { return estado; }
    public int getPassoAtual() { return passoAtual; }
    public int getTentativas() { return tentativas; }
    public String getErro() { return erro; }
    public String getChavesS3() { return chavesS3; }
    public Instant getIniciadaEm() { return iniciadaEm; }
    public Instant getConcluidaEm() { return concluidaEm; }
}
