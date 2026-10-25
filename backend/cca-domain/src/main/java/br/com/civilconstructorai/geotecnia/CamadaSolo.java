package br.com.civilconstructorai.geotecnia;

/** Camada do perfil: cotas do topo e da base (m, negativas abaixo da boca do furo), tipo e descrição. */
public record CamadaSolo(double cotaTopo, double cotaBase, TipoSolo tipo, String descricao) {

    public double espessura() {
        return cotaTopo - cotaBase;
    }

    public boolean contem(double cota) {
        return cota <= cotaTopo && cota > cotaBase;
    }
}
