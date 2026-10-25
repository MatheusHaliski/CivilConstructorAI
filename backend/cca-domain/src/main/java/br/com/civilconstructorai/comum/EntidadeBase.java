package br.com.civilconstructorai.comum;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Base de toda entidade pública: UUID v7, datas de criação/atualização e versão otimista. */
@MappedSuperclass
public abstract class EntidadeBase {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id = Ids.novo();

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    @Version
    @Column(nullable = false)
    private long versao;

    @PrePersist
    void aoCriar() {
        var agora = Instant.now();
        if (criadoEm == null) {
            criadoEm = agora;
        }
        atualizadoEm = agora;
    }

    @PreUpdate
    void aoAtualizar() {
        atualizadoEm = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    protected void setId(UUID id) {
        this.id = id;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }

    public long getVersao() {
        return versao;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof EntidadeBase e && getClass() == e.getClass() && id.equals(e.id));
    }

    @Override
    public int hashCode() {
        return Objects.hash(getClass(), id);
    }
}
