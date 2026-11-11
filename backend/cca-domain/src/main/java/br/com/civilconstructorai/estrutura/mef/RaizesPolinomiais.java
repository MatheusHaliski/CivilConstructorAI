package br.com.civilconstructorai.estrutura.mef;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Isolamento numérico de raízes reais em [0,1], para os polinômios de grau até sete do deslocamento. */
final class RaizesPolinomiais {
    /** Tolerância absoluta da abscissa adimensional; não é tolerância normativa de deslocamento. */
    static final double TOL_POSICAO = 2e-13;

    private RaizesPolinomiais() { }

    /** Coeficientes em potências crescentes. Inclui candidatos de raiz múltipla próximos do erro de avaliação. */
    static List<Double> noIntervaloUnitario(double[] coeficientes) {
        int grau = coeficientes.length-1;
        while (grau > 0 && coeficientes[grau] == 0) grau--;
        double escala = 0;
        for (int i=0; i<=grau; i++) {
            if (!Double.isFinite(coeficientes[i])) throw new IllegalArgumentException("Polinômio de deslocamento não finito.");
            escala = Math.max(escala,Math.abs(coeficientes[i]));
        }
        if (grau == 0 || escala == 0) return List.of();
        double[] p = Arrays.copyOf(coeficientes,grau+1);
        double soma = 0;
        for (int i=0; i<p.length; i++) { p[i] /= escala; soma += Math.abs(p[i]); }
        if (grau == 1) {
            double raiz = -p[0]/p[1];
            return raiz >= 0 && raiz <= 1 ? List.of(raiz) : List.of();
        }
        // As raízes da derivada particionam o domínio em trechos monótonos, evitando
        // procurar apenas trocas de sinal numa malha que pode saltar raízes próximas.
        var limites = new ArrayList<Double>();
        limites.add(0.0);
        for (double r : noIntervaloUnitario(derivada(p))) if (r > 0 && r < 1) limites.add(r);
        limites.add(1.0);
        var raizes = new ArrayList<Double>();
        double erroAvaliacao = 32*Math.ulp(1.0)*soma;
        for (double t : limites) {
            // A raiz múltipla pode não trocar sinal. Guardar este candidato é conservador
            // para a busca de máximo; os valores de deslocamento são avaliados depois.
            if (Math.abs(avaliar(p,t)) <= erroAvaliacao) adicionar(raizes,t);
        }
        for (int i=0; i+1<limites.size(); i++) {
            double a = limites.get(i), b = limites.get(i+1), fa = avaliar(p,a), fb = avaliar(p,b);
            if (!sinaisOpostos(fa,fb)) continue;
            for (int passo=0; passo<64 && b-a>TOL_POSICAO; passo++) {
                double meio = a+(b-a)/2, fm = avaliar(p,meio);
                if (fm == 0) { a = meio; b = meio; break; }
                if (sinaisOpostos(fa,fm)) { b = meio; } else { a = meio; fa = fm; }
            }
            adicionar(raizes,a+(b-a)/2);
        }
        raizes.sort(Double::compare);
        return List.copyOf(raizes);
    }

    private static boolean sinaisOpostos(double a, double b) {
        return (a < 0 && b > 0) || (a > 0 && b < 0);
    }

    private static void adicionar(List<Double> raizes, double r) {
        if (raizes.stream().noneMatch(x -> Math.abs(x-r) <= TOL_POSICAO)) raizes.add(r);
    }

    private static double[] derivada(double[] p) {
        double[] d = new double[p.length-1];
        for (int i=1; i<p.length; i++) d[i-1] = i*p[i];
        return d;
    }

    private static double avaliar(double[] p, double t) {
        double v = p[p.length-1];
        for (int i=p.length-2; i>=0; i--) v = v*t+p[i];
        return v;
    }
}
