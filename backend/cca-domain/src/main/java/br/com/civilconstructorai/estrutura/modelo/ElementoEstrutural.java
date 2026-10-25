package br.com.civilconstructorai.estrutura.modelo;

import java.util.List;

/**
 * Elemento do recorte estrutural. Campos não aplicáveis ao tipo ficam nulos/zero:
 * PILAR usa centro, b, h, nivelBaseId, nivelTopoId; VIGA usa p1, p2, b, h; LAJE usa contorno e h;
 * PAREDE usa p1, p2, espessura (b) e altura (h); aberturas usam paredeId, largura (b) e altura (h);
 * fundações usam centro, bx, by e h.
 */
public record ElementoEstrutural(
        String id,
        TipoElementoEstrutural tipo,
        String pavimentoId,
        String materialId,
        String rotulo,
        Ponto p1,
        Ponto p2,
        Ponto centro,
        double b,
        double h,
        List<Ponto> contorno,
        String nivelBaseId,
        String nivelTopoId,
        String paredeId,
        double bx,
        double by,
        boolean rascunhoIa) {

    public static ElementoEstrutural pilar(String id, String pav, Ponto centro, double b, double h, String nivelBase, String nivelTopo, String material) {
        return new ElementoEstrutural(id, TipoElementoEstrutural.PILAR, pav, material, null, null, null, centro, b, h, List.of(), nivelBase, nivelTopo, null, 0, 0, false);
    }

    public static ElementoEstrutural viga(String id, String pav, Ponto p1, Ponto p2, double b, double h, String material) {
        return new ElementoEstrutural(id, TipoElementoEstrutural.VIGA, pav, material, null, p1, p2, Ponto.medio(p1, p2), b, h, List.of(), null, null, null, 0, 0, false);
    }

    public static ElementoEstrutural laje(String id, String pav, List<Ponto> contorno, double h, String material) {
        return new ElementoEstrutural(id, TipoElementoEstrutural.LAJE, pav, material, null, null, null, centroide(contorno), 0, h, List.copyOf(contorno), null, null, null, 0, 0, false);
    }

    public static ElementoEstrutural parede(String id, String pav, Ponto p1, Ponto p2, double espessura, double altura, String material) {
        return new ElementoEstrutural(id, TipoElementoEstrutural.PAREDE, pav, material, null, p1, p2, Ponto.medio(p1, p2), espessura, altura, List.of(), null, null, null, 0, 0, false);
    }

    public static ElementoEstrutural abertura(String id, TipoElementoEstrutural tipo, String pav, String paredeId, double largura, double altura) {
        return new ElementoEstrutural(id, tipo, pav, null, null, null, null, null, largura, altura, List.of(), null, null, paredeId, 0, 0, false);
    }

    public static ElementoEstrutural fundacao(String id, TipoElementoEstrutural tipo, String pav, Ponto centro, double bx, double by, double h, String material) {
        return new ElementoEstrutural(id, tipo, pav, material, null, null, null, centro, 0, h, List.of(), null, null, null, bx, by, false);
    }

    public ElementoEstrutural comRotulo(String novoRotulo) {
        return new ElementoEstrutural(id, tipo, pavimentoId, materialId, novoRotulo, p1, p2, centro, b, h, contorno, nivelBaseId, nivelTopoId, paredeId, bx, by, rascunhoIa);
    }

    public ElementoEstrutural comoRascunhoIa() {
        return new ElementoEstrutural(id, tipo, pavimentoId, materialId, rotulo, p1, p2, centro, b, h, contorno, nivelBaseId, nivelTopoId, paredeId, bx, by, true);
    }

    public double comprimento() {
        return p1 == null || p2 == null ? 0 : p1.distancia(p2);
    }

    public double areaSecao() {
        return b * h;
    }

    public static Ponto centroide(List<Ponto> pts) {
        double sx = 0, sy = 0;
        for (var p : pts) {
            sx += p.x();
            sy += p.y();
        }
        return pts.isEmpty() ? new Ponto(0, 0) : new Ponto(sx / pts.size(), sy / pts.size());
    }

    public static double areaPoligono(List<Ponto> pts) {
        double a = 0;
        for (int i = 0; i < pts.size(); i++) {
            var p = pts.get(i);
            var q = pts.get((i + 1) % pts.size());
            a += p.x() * q.y() - q.x() * p.y();
        }
        return Math.abs(a) / 2;
    }

    public static double perimetroPoligono(List<Ponto> pts) {
        double l = 0;
        for (int i = 0; i < pts.size(); i++) {
            l += pts.get(i).distancia(pts.get((i + 1) % pts.size()));
        }
        return l;
    }
}
