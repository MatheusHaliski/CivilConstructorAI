package br.com.civilconstructorai.identidade;

import br.com.civilconstructorai.comum.ExcecaoDominio;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Preferências de exibição (RF23). A configuração do motor de desenho ({@code motor} jsonb) é do RF28. */
@Entity
@Table(name = "preferencia")
public class Preferencia {

    @Id
    @Column(name = "conta_id", nullable = false, updatable = false)
    private UUID contaId;

    @Column(nullable = false)
    private String idioma = "pt-BR";

    @Column(nullable = false)
    private String tema = "claro";

    @Column(name = "fonte_pct", nullable = false)
    private int fontePct = 100;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm = Instant.now();

    protected Preferencia() {
    }

    public Preferencia(UUID contaId) {
        this.contaId = contaId;
    }

    public void atualizar(String idioma, String tema, Integer fontePct) {
        if (idioma != null) {
            this.idioma = idioma;
        }
        if (tema != null) {
            if (!tema.equals("claro") && !tema.equals("escuro") && !tema.equals("sistema")) {
                throw new ExcecaoDominio.Validacao("tema", "Tema deve ser claro, escuro ou sistema.");
            }
            this.tema = tema;
        }
        if (fontePct != null) {
            if (fontePct < 85 || fontePct > 150) {
                throw new ExcecaoDominio.Validacao("fontePct", "Tamanho da fonte entre 85 % e 150 %.");
            }
            this.fontePct = fontePct;
        }
    }

    @PreUpdate
    void aoAtualizar() {
        atualizadoEm = Instant.now();
    }

    public UUID getContaId() { return contaId; }
    public String getIdioma() { return idioma; }
    public String getTema() { return tema; }
    public int getFontePct() { return fontePct; }
}
