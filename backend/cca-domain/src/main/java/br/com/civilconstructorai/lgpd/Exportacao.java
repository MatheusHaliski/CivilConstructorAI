package br.com.civilconstructorai.lgpd;

import br.com.civilconstructorai.comum.EntidadeBase;
import br.com.civilconstructorai.comum.EstadoJob;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Exportação dos dados do titular (Art. 18, V). A senha do pacote é gerada pelo job na hora de cifrar, fica
 * cifrada com a DEK do titular até a primeira consulta depois de PRONTA, é exibida uma vez e então só o hash
 * permanece.
 */
@Entity
@Table(name = "exportacao")
public class Exportacao extends EntidadeBase {

    @Column(name = "titular_id", nullable = false, updatable = false)
    private UUID titularId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "text")
    private EstadoJob estado = EstadoJob.PENDENTE;

    @Column(name = "chave_s3", columnDefinition = "text")
    private String chaveS3;

    @Column(name = "senha_cifrada_dek")
    private byte[] senhaCifradaDek;

    @Column(name = "senha_exibida_em")
    private Instant senhaExibidaEm;

    @Column(name = "hash_senha", columnDefinition = "text")
    private String hashSenha;

    @Column(name = "tamanho_bytes")
    private Long tamanhoBytes;

    @Column(name = "expira_em")
    private Instant expiraEm;

    @Column(columnDefinition = "text")
    private String erro;

    protected Exportacao() {
    }

    public Exportacao(UUID titularId) {
        this.titularId = titularId;
    }

    public void iniciar() {
        estado = EstadoJob.MAQUINA.transicionar(estado, EstadoJob.EM_EXECUCAO);
    }

    public void concluir(String chaveS3, byte[] senhaCifradaDek, String hashSenha, long tamanho, Instant expiraEm) {
        estado = EstadoJob.MAQUINA.transicionar(estado, EstadoJob.CONCLUIDO);
        this.chaveS3 = chaveS3;
        this.senhaCifradaDek = senhaCifradaDek;
        this.hashSenha = hashSenha;
        this.tamanhoBytes = tamanho;
        this.expiraEm = expiraEm;
    }

    public void falhar(String motivo) {
        estado = EstadoJob.MAQUINA.transicionar(estado, EstadoJob.FALHOU);
        this.erro = motivo == null ? "falha" : motivo.substring(0, Math.min(500, motivo.length()));
    }

    /** devolve a senha cifrada uma única vez e apaga; depois fica só o hash */
    public byte[] consumirSenha(Instant agora) {
        var s = senhaCifradaDek;
        senhaCifradaDek = null;
        senhaExibidaEm = agora;
        return s;
    }

    public boolean pronta() {
        return estado == EstadoJob.CONCLUIDO;
    }

    public UUID getTitularId() { return titularId; }
    public EstadoJob getEstado() { return estado; }
    public String getChaveS3() { return chaveS3; }
    public boolean senhaDisponivel() { return senhaCifradaDek != null; }
    public Instant getSenhaExibidaEm() { return senhaExibidaEm; }
    public String getHashSenha() { return hashSenha; }
    public Long getTamanhoBytes() { return tamanhoBytes; }
    public Instant getExpiraEm() { return expiraEm; }
    public String getErro() { return erro; }
}
