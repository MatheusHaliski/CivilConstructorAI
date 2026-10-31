package br.com.civilconstructorai.modelo;

import br.com.civilconstructorai.modelo.Enums.TipoElemento;
import br.com.civilconstructorai.notacao.Ponto2D;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Escadas e rampas retas — porte de {@code circulacao.ts} (mesmos cálculos): perfil lateral, altura do piso num ponto
 * e medidas. Espelho, piso e inclinação são geométricos; a verificação normativa não é feita aqui.
 */
public final class Circulacao {

    /** espelho adotado para sugerir o número de degraus quando o usuário não informa (ajustável no elemento) */
    public static final double ESPELHO_ADOTADO = 0.175;

    private Circulacao() {
    }

    public static boolean eh(Elemento e) {
        return e.getTipo() == TipoElemento.ESCADA || e.getTipo() == TipoElemento.RAMPA;
    }

    public static int degraus(Elemento e) {
        if (e.getTipo() != TipoElemento.ESCADA) {
            return 0;
        }
        return Math.max(1, e.getProps().get("degraus") instanceof Number n ? n.intValue() : (int) Math.round(e.num("desnivel", 0) / ESPELHO_ADOTADO));
    }

    /** polígono do perfil lateral em (s ao longo de p1→p2, h): escada em dente de serra cheio até o piso; rampa em cunha */
    public static List<double[]> perfil(Elemento e) {
        double L = e.ponto("p1").distancia(e.ponto("p2")), H = e.num("desnivel", 0);
        var pts = new ArrayList<double[]>();
        pts.add(new double[]{0, 0});
        if (e.getTipo() == TipoElemento.RAMPA) {
            pts.add(new double[]{L, H});
        } else {
            int n = degraus(e);
            double g = L / n, h = H / n;
            for (int i = 0; i < n; i++) {
                pts.add(new double[]{i * g, (i + 1) * h});
                pts.add(new double[]{(i + 1) * g, (i + 1) * h});
            }
        }
        pts.add(new double[]{L, 0});
        return pts;
    }

    /** altura do piso de circulação sob o ponto (em relação ao pavimento), ou null fora da escada/rampa */
    public static Double altura(Elemento e, Ponto2D p) {
        var a = e.ponto("p1");
        var b = e.ponto("p2");
        double L = a.distancia(b);
        if (L <= 0) {
            return null;
        }
        double ux = (b.x() - a.x()) / L, uy = (b.y() - a.y()) / L;
        double t = ((p.x() - a.x()) * ux + (p.y() - a.y()) * uy) / L, d = -(p.x() - a.x()) * uy + (p.y() - a.y()) * ux;
        if (t < 0 || t > 1 || Math.abs(d) > e.num("largura", 1.0) / 2) {
            return null;
        }
        double H = e.num("desnivel", 0);
        if (e.getTipo() == TipoElemento.RAMPA) {
            return H * t;
        }
        int n = degraus(e);
        return Math.min(n, Math.floor(t * n) + 1) * H / n;
    }

    /** altura do piso num ponto do pavimento: a maior escada/rampa sob o ponto, ou 0 */
    public static double elevacaoNoPiso(ModeloBim m, UUID pav, Ponto2D p) {
        double h = 0;
        for (var e : m.elementosDo(pav)) {
            if (eh(e)) {
                var a = altura(e, p);
                if (a != null && a > h) {
                    h = a;
                }
            }
        }
        return ObjetosPrediais.r3(h);
    }
}
