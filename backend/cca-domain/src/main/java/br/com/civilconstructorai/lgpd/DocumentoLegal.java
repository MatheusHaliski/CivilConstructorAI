package br.com.civilconstructorai.lgpd;

import br.com.civilconstructorai.comum.Ids;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Versão publicada de Termos de Uso ou Política de Privacidade (texto-fonte em docs/legal). */
@Entity
@Table(name = "documento_legal")
public class DocumentoLegal {

    @Id
    private UUID id = Ids.novo();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "text")
    private TipoDocumentoLegal tipo;

    @Column(nullable = false, columnDefinition = "text")
    private String versao;

    @Column(name = "vigente_desde", nullable = false)
    private Instant vigenteDesde;

    @Column(nullable = false, columnDefinition = "text")
    private String url;

    protected DocumentoLegal() {
    }

    public DocumentoLegal(TipoDocumentoLegal tipo, String versao, Instant vigenteDesde, String url) {
        this.tipo = tipo;
        this.versao = versao;
        this.vigenteDesde = vigenteDesde;
        this.url = url;
    }

    public UUID getId() { return id; }
    public TipoDocumentoLegal getTipo() { return tipo; }
    public String getVersao() { return versao; }
    public Instant getVigenteDesde() { return vigenteDesde; }
    public String getUrl() { return url; }
}
