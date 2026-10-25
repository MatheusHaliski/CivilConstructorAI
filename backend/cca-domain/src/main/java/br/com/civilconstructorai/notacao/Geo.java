package br.com.civilconstructorai.notacao;

import java.util.List;
import java.util.Optional;

/** Geometria 2D elementar (porte de geometria.ts). */
public final class Geo {

    public static final double EPS = 1e-6;

    private Geo() {
    }

    public static double arredondar(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    public static Ponto2D rotacionar(Ponto2D p, Ponto2D base, double grausAnti) {
        double r = Math.toRadians(grausAnti);
        double dx = p.x() - base.x(), dy = p.y() - base.y();
        return Ponto2D.de(base.x() + dx * Math.cos(r) - dy * Math.sin(r), base.y() + dx * Math.sin(r) + dy * Math.cos(r));
    }

    public static Ponto2D espelhar(Ponto2D p, Ponto2D a, Ponto2D b) {
        double dx = b.x() - a.x(), dy = b.y() - a.y();
        double l2 = dx * dx + dy * dy;
        if (l2 < EPS) {
            l2 = EPS;
        }
        double t = ((p.x() - a.x()) * dx + (p.y() - a.y()) * dy) / l2;
        double px = a.x() + t * dx, py = a.y() + t * dy;
        return Ponto2D.de(2 * px - p.x(), 2 * py - p.y());
    }

    /** ponto mais próximo no segmento ab e parâmetro t ∈ [0,1] */
    public record Projecao(Ponto2D ponto, double t) {
    }

    public static Projecao projetarNoSegmento(Ponto2D p, Ponto2D a, Ponto2D b) {
        double dx = b.x() - a.x(), dy = b.y() - a.y();
        double l2 = dx * dx + dy * dy;
        if (l2 < EPS) {
            return new Projecao(a, 0);
        }
        double t = Math.max(0, Math.min(1, ((p.x() - a.x()) * dx + (p.y() - a.y()) * dy) / l2));
        return new Projecao(Ponto2D.de(a.x() + t * dx, a.y() + t * dy), t);
    }

    public static Optional<Ponto2D> intersecaoSegmentos(Ponto2D a, Ponto2D b, Ponto2D c, Ponto2D d, boolean estender) {
        double rx = b.x() - a.x(), ry = b.y() - a.y(), sx = d.x() - c.x(), sy = d.y() - c.y();
        double den = rx * sy - ry * sx;
        if (Math.abs(den) < EPS) {
            return Optional.empty();
        }
        double qx = c.x() - a.x(), qy = c.y() - a.y();
        double t = (qx * sy - qy * sx) / den;
        double u = (qx * ry - qy * rx) / den;
        if (!estender && (t < -EPS || t > 1 + EPS || u < -EPS || u > 1 + EPS)) {
            return Optional.empty();
        }
        return Optional.of(Ponto2D.de(a.x() + t * rx, a.y() + t * ry));
    }

    public static double areaPoligono(List<Ponto2D> pts) {
        double a = 0;
        for (int i = 0; i < pts.size(); i++) {
            Ponto2D p = pts.get(i), q = pts.get((i + 1) % pts.size());
            a += p.x() * q.y() - q.x() * p.y();
        }
        return Math.abs(a) / 2;
    }

    public static double perimetroPoligono(List<Ponto2D> pts) {
        double l = 0;
        for (int i = 0; i < pts.size(); i++) {
            l += pts.get(i).distancia(pts.get((i + 1) % pts.size()));
        }
        return l;
    }

    public record Caixa(Ponto2D min, Ponto2D max) {
        public boolean contem(Ponto2D p, double folga) {
            return p.x() >= min.x() - folga && p.x() <= max.x() + folga && p.y() >= min.y() - folga && p.y() <= max.y() + folga;
        }

        public Ponto2D clampar(Ponto2D p, double folga) {
            return Ponto2D.de(Math.min(Math.max(p.x(), min.x() - folga), max.x() + folga), Math.min(Math.max(p.y(), min.y() - folga), max.y() + folga));
        }
    }

    public static Caixa caixa(List<Ponto2D> pts) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (var p : pts) {
            minX = Math.min(minX, p.x());
            minY = Math.min(minY, p.y());
            maxX = Math.max(maxX, p.x());
            maxY = Math.max(maxY, p.y());
        }
        return new Caixa(new Ponto2D(minX, minY), new Ponto2D(maxX, maxY));
    }

    public static boolean temAutoIntersecao(List<Ponto2D> pts) {
        int n = pts.size();
        for (int i = 0; i < n; i++) {
            for (int j = i + 2; j < n; j++) {
                if (i == 0 && j == n - 1) {
                    continue;
                }
                if (intersecaoSegmentos(pts.get(i), pts.get((i + 1) % n), pts.get(j), pts.get((j + 1) % n), false).isPresent()) {
                    return true;
                }
            }
        }
        return false;
    }
}
