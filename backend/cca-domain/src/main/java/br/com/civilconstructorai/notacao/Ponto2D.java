package br.com.civilconstructorai.notacao;

/** Ponto no plano do pavimento, em metros (SI). Eixo Z é o do pavimento. */
public record Ponto2D(double x, double y) {

    public static Ponto2D de(double x, double y) {
        return new Ponto2D(Geo.arredondar(x), Geo.arredondar(y));
    }

    public Ponto2D mais(Ponto2D o) {
        return de(x + o.x, y + o.y);
    }

    public Ponto2D menos(Ponto2D o) {
        return de(x - o.x, y - o.y);
    }

    public Ponto2D vezes(double k) {
        return de(x * k, y * k);
    }

    public double distancia(Ponto2D o) {
        return Math.hypot(o.x - x, o.y - y);
    }

    public Ponto2D medio(Ponto2D o) {
        return de((x + o.x) / 2, (y + o.y) / 2);
    }
}
