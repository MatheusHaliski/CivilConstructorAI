package br.com.civilconstructorai.estrutura.modelo;

/** Ponto em planta, em metros. */
public record Ponto(double x, double y) {

    public double distancia(Ponto o) {
        return Math.hypot(o.x - x, o.y - y);
    }

    public Ponto mais(double dx, double dy) {
        return new Ponto(x + dx, y + dy);
    }

    public static Ponto medio(Ponto a, Ponto b) {
        return new Ponto((a.x + b.x) / 2, (a.y + b.y) / 2);
    }
}
