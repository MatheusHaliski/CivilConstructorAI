package br.com.civilconstructorai.identidade;

import br.com.civilconstructorai.comum.EntidadeBase;
import br.com.civilconstructorai.comum.ExcecaoDominio;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.Locale;
import java.util.Objects;

/**
 * Conta de acesso (RF18/RF19/RF21/RF23/RF26). Uma pessoa pode estar em várias empresas (membro_empresa /
 * membro_obra); a conta em si não pertence a tenant nenhum.
 */
@Entity
@Table(name = "conta")
public class Conta extends EntidadeBase {

    public static final int IDADE_MINIMA = 18;

    @Column(nullable = false, columnDefinition = "citext")
    private String email;

    @Column(name = "senha_hash", nullable = false)
    private String senhaHash;

    @Column(nullable = false)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoConta tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoConta estado = EstadoConta.PENDING_VERIFICATION;

    @Column(name = "data_nascimento")
    private LocalDate dataNascimento;

    @Column(name = "cpf_hash")
    private String cpfHash;

    @Column
    private String cnpj;

    @Column(name = "registro_profissional")
    private String registroProfissional;

    @Column(name = "registro_uf", length = 2)
    private String registroUf;

    @Enumerated(EnumType.STRING)
    @Column(name = "selo_tipo")
    private TipoSelo seloTipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "selo_estado", nullable = false)
    private EstadoSelo seloEstado = EstadoSelo.NONE;

    @Column(name = "selo_verificado_em")
    private Instant seloVerificadoEm;

    @Column(name = "selo_motivo")
    private String seloMotivo;

    @Column(name = "mfa_ativo", nullable = false)
    private boolean mfaAtivo;

    @Column(name = "mfa_segredo_cifrado")
    private byte[] mfaSegredoCifrado;

    @Column(name = "mfa_obrigatorio", nullable = false)
    private boolean mfaObrigatorio;

    @Column(name = "eliminacao_prevista_em")
    private Instant eliminacaoPrevistaEm;

    @Column(name = "ultimo_login_em")
    private Instant ultimoLoginEm;

    protected Conta() {
    }

    public static Conta nova(String email, String senhaHash, String nome, TipoConta tipo, LocalDate dataNascimento, LocalDate hoje) {
        if (dataNascimento == null || Period.between(dataNascimento, hoje).getYears() < IDADE_MINIMA) {
            throw new ExcecaoDominio.Validacao("dataNascimento", "O app é profissional: só maiores de " + IDADE_MINIMA + " anos.");
        }
        var c = new Conta();
        c.email = email.trim().toLowerCase(Locale.ROOT);
        c.senhaHash = Objects.requireNonNull(senhaHash);
        c.nome = nome.trim();
        c.tipo = tipo;
        c.dataNascimento = dataNascimento;
        return c;
    }

    // ---------------------------------------------------------------- máquina de estados
    public void transicionar(EstadoConta para) {
        this.estado = EstadoConta.MAQUINA.transicionar(this.estado, para);
    }

    public void ativar() {
        transicionar(EstadoConta.ACTIVE);
    }

    public void pedirExclusao(Instant eliminacaoPrevista) {
        transicionar(EstadoConta.PENDING_DELETION);
        this.eliminacaoPrevistaEm = eliminacaoPrevista;
    }

    public void reativar() {
        if (estado != EstadoConta.PENDING_DELETION) {
            throw new ExcecaoDominio.TransicaoInvalida("Conta", estado, EstadoConta.ACTIVE);
        }
        transicionar(EstadoConta.ACTIVE);
        this.eliminacaoPrevistaEm = null;
    }

    // ---------------------------------------------------------------- selo profissional (RF21)
    public void solicitarSelo(TipoSelo tipo, String registro, String uf) {
        this.seloTipo = tipo;
        this.registroProfissional = registro;
        this.registroUf = uf == null ? null : uf.toUpperCase(Locale.ROOT);
        this.seloEstado = EstadoSelo.MAQUINA.transicionar(seloEstado == EstadoSelo.PENDING ? EstadoSelo.NONE : seloEstado, EstadoSelo.PENDING);
        this.seloVerificadoEm = null;
        this.seloMotivo = null;
    }

    /** rejeição exige motivo, que o profissional vê para corrigir e reenviar (RF21) */
    public void decidirSelo(boolean aprovado, String motivo, Instant agora) {
        if (!aprovado && (motivo == null || motivo.isBlank())) {
            throw new ExcecaoDominio.Validacao("motivo", "Informe o motivo da rejeição: o profissional precisa saber o que corrigir.");
        }
        this.seloEstado = EstadoSelo.MAQUINA.transicionar(seloEstado, aprovado ? EstadoSelo.VERIFIED : EstadoSelo.REJECTED);
        this.seloVerificadoEm = aprovado ? agora : null;
        this.seloMotivo = aprovado ? null : motivo.trim();
    }

    /** nome, registro ou CNPJ mudaram: selo verificado volta a PENDING */
    private void invalidarSeloSeVerificado() {
        if (seloEstado == EstadoSelo.VERIFIED) {
            seloEstado = EstadoSelo.PENDING;
            seloVerificadoEm = null;
        }
    }

    public void alterarNome(String novoNome) {
        if (novoNome == null || novoNome.isBlank()) {
            throw new ExcecaoDominio.Validacao("nome", "Informe o nome.");
        }
        if (!novoNome.trim().equals(this.nome)) {
            this.nome = novoNome.trim();
            invalidarSeloSeVerificado();
        }
    }

    public void alterarCnpj(String novoCnpj) {
        if (!Objects.equals(novoCnpj, this.cnpj)) {
            this.cnpj = novoCnpj;
            invalidarSeloSeVerificado();
        }
    }

    public void alterarRegistro(String registro, String uf) {
        if (!Objects.equals(registro, this.registroProfissional) || !Objects.equals(uf, this.registroUf)) {
            this.registroProfissional = registro;
            this.registroUf = uf;
            invalidarSeloSeVerificado();
        }
    }

    // ---------------------------------------------------------------- credenciais e 2FA
    public void trocarSenha(String novoHash) {
        this.senhaHash = Objects.requireNonNull(novoHash);
    }

    public void ativarMfa(byte[] segredoCifrado) {
        this.mfaSegredoCifrado = segredoCifrado;
        this.mfaAtivo = true;
    }

    public void desativarMfa() {
        if (mfaObrigatorio) {
            throw new ExcecaoDominio.Conflito("O 2FA é obrigatório para o seu papel e não pode ser desativado.");
        }
        this.mfaAtivo = false;
        this.mfaSegredoCifrado = null;
    }

    public void marcarMfaObrigatorio(boolean obrigatorio) {
        this.mfaObrigatorio = obrigatorio;
    }

    public void registrarLogin(Instant agora) {
        this.ultimoLoginEm = agora;
    }

    public void definirCpfHash(String cpfHash) {
        this.cpfHash = cpfHash;
    }

    // ---------------------------------------------------------------- getters
    public String getEmail() { return email; }
    public String getSenhaHash() { return senhaHash; }
    public String getNome() { return nome; }
    public TipoConta getTipo() { return tipo; }
    public EstadoConta getEstado() { return estado; }
    public LocalDate getDataNascimento() { return dataNascimento; }
    public String getCnpj() { return cnpj; }
    public String getRegistroProfissional() { return registroProfissional; }
    public String getRegistroUf() { return registroUf; }
    public TipoSelo getSeloTipo() { return seloTipo; }
    public EstadoSelo getSeloEstado() { return seloEstado; }
    public Instant getSeloVerificadoEm() { return seloVerificadoEm; }
    public String getSeloMotivo() { return seloMotivo; }
    public boolean isMfaAtivo() { return mfaAtivo; }
    public byte[] getMfaSegredoCifrado() { return mfaSegredoCifrado; }
    public boolean isMfaObrigatorio() { return mfaObrigatorio; }
    public Instant getEliminacaoPrevistaEm() { return eliminacaoPrevistaEm; }
    public Instant getUltimoLoginEm() { return ultimoLoginEm; }
    public boolean seloVerificado() { return seloEstado == EstadoSelo.VERIFIED; }
}
