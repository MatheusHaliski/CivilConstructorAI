package br.com.civilconstructorai.obra;

import br.com.civilconstructorai.comum.EntidadeTenant;
import br.com.civilconstructorai.comum.ExcecaoDominio;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import java.util.UUID;

/** Obra de uma empresa. Perímetro e localização em SIRGAS 2000 (SRID 4674) no PostGIS. */
@Entity
@Table(name = "obra")
public class Obra extends EntidadeTenant {

    public static final int SRID = 4674;

    @Column(nullable = false)
    private String nome;

    @Column
    private String endereco;

    @Column
    private String municipio;

    @Column(length = 2)
    private String uf;

    @Column(columnDefinition = "geometry(Polygon,4674)")
    private Polygon perimetro;

    @Column(columnDefinition = "geometry(Point,4674)")
    private Point localizacao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoObra estado = EstadoObra.PLANEJADA;

    @Column(name = "art_rrt")
    private String artRrt;

    @Column(name = "responsavel_tecnico_id")
    private UUID responsavelTecnicoId;

    protected Obra() {
    }

    public Obra(UUID empresaId, String nome, String endereco, String municipio, String uf) {
        super(empresaId);
        if (nome == null || nome.isBlank()) {
            throw new ExcecaoDominio.Validacao("nome", "Informe o nome da obra.");
        }
        this.nome = nome.trim();
        this.endereco = endereco;
        this.municipio = municipio;
        this.uf = uf == null ? null : uf.toUpperCase();
    }

    public void exigirEditavel() {
        if (estado.somenteLeitura()) {
            throw new ExcecaoDominio.Conflito("Obra arquivada é somente leitura.");
        }
    }

    public void atualizar(String nome, String endereco, String municipio, String uf, String artRrt) {
        exigirEditavel();
        if (nome != null && !nome.isBlank()) {
            this.nome = nome.trim();
        }
        if (endereco != null) {
            this.endereco = endereco;
        }
        if (municipio != null) {
            this.municipio = municipio;
        }
        if (uf != null) {
            this.uf = uf.toUpperCase();
        }
        if (artRrt != null) {
            this.artRrt = artRrt;
        }
    }

    public void definirGeometria(Polygon perimetro, Point localizacao) {
        exigirEditavel();
        if (perimetro != null) {
            perimetro.setSRID(SRID);
            if (!perimetro.isValid()) {
                throw new ExcecaoDominio.Validacao("perimetro", "Perímetro inválido (auto-interseção ou anel aberto).");
            }
        }
        if (localizacao != null) {
            localizacao.setSRID(SRID);
        }
        this.perimetro = perimetro;
        this.localizacao = localizacao;
    }

    public void definirResponsavelTecnico(UUID contaId, String artRrt) {
        exigirEditavel();
        this.responsavelTecnicoId = contaId;
        if (artRrt != null) {
            this.artRrt = artRrt;
        }
    }

    /** transição de estado; sair de PLANEJADA exige engenheiro(a) responsável */
    public void transicionar(EstadoObra para) {
        if (estado == EstadoObra.PLANEJADA && para == EstadoObra.EM_ANDAMENTO && responsavelTecnicoId == null) {
            throw new ExcecaoDominio.RegraDeNegocio("obra_sem_responsavel", "Defina o(a) engenheiro(a) responsável antes de iniciar a obra.");
        }
        this.estado = EstadoObra.MAQUINA.transicionar(this.estado, para);
    }

    public String getNome() { return nome; }
    public String getEndereco() { return endereco; }
    public String getMunicipio() { return municipio; }
    public String getUf() { return uf; }
    public Polygon getPerimetro() { return perimetro; }
    public Point getLocalizacao() { return localizacao; }
    public EstadoObra getEstado() { return estado; }
    public String getArtRrt() { return artRrt; }
    public UUID getResponsavelTecnicoId() { return responsavelTecnicoId; }
}
