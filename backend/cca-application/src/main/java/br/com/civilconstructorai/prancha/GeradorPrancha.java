package br.com.civilconstructorai.prancha;

import br.com.civilconstructorai.modelo.Eixo;
import br.com.civilconstructorai.modelo.Elemento;
import br.com.civilconstructorai.modelo.Enums.Camada;
import br.com.civilconstructorai.modelo.Enums.TipoElemento;
import br.com.civilconstructorai.modelo.ModeloBim;
import br.com.civilconstructorai.notacao.Geo;
import br.com.civilconstructorai.notacao.Ponto2D;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Gera a prancha (RF14) de forma determinística a partir do modelo auditado: planta do pavimento com paredes,
 * pilares, vigas, aberturas, eixos com bolhas recuadas (sem sobreposição), cotas totais por direção batendo com a
 * envoltória real, anotações manuais da camada {@code anotacao}, carimbo (responsável técnico/ART) e marca
 * d'água "rascunho" até a emissão. Saídas: SVG (A1 1:50 por padrão) e DXF básico (R12 ASCII).
 */
public final class GeradorPrancha {

    private GeradorPrancha() {
    }

    public record Carimbo(String obra, String pavimento, String responsavel, String artRrt, String escala, String formato, boolean rascunho, long versao) {
    }

    private static String f(double v) {
        return String.format(Locale.ROOT, "%.3f", v);
    }

    private static String cota(double metros) {
        return String.format(Locale.ROOT, "%.0f", metros * 100);
    }

    /** caixa útil da planta (eixos + elementos do pavimento) com margem */
    static Geo.Caixa caixaPlanta(ModeloBim m, UUID pavimentoId) {
        var pts = new ArrayList<Ponto2D>();
        for (var e : m.elementosDo(pavimentoId)) {
            pts.addAll(e.todosOsPontos());
        }
        for (var ex : m.eixos()) {
            if (ex.direcao() == Eixo.Direcao.X) {
                pts.add(new Ponto2D(ex.posicao(), ex.inicio()));
                pts.add(new Ponto2D(ex.posicao(), ex.fim()));
            } else {
                pts.add(new Ponto2D(ex.inicio(), ex.posicao()));
                pts.add(new Ponto2D(ex.fim(), ex.posicao()));
            }
        }
        if (pts.isEmpty()) {
            pts.add(new Ponto2D(0, 0));
            pts.add(new Ponto2D(10, 10));
        }
        return Geo.caixa(pts);
    }

    /** envoltória real: caixa dos eixos quando há malha, senão dos elementos construtivos (RF06) */
    static Geo.Caixa envoltoriaReal(ModeloBim m, UUID pavimentoId) {
        return m.caixaDosEixos().orElseGet(() -> {
            var pts = new ArrayList<Ponto2D>();
            for (var e : m.elementosDo(pavimentoId)) {
                if (e.getTipo().construtivo()) {
                    pts.addAll(e.todosOsPontos());
                }
            }
            return pts.isEmpty() ? new Geo.Caixa(new Ponto2D(0, 0), new Ponto2D(0, 0)) : Geo.caixa(pts);
        });
    }

    public static String svg(ModeloBim m, UUID pavimentoId, boolean formas, Carimbo carimbo) {
        var cx = caixaPlanta(m, pavimentoId);
        double margem = 3.0;
        double minX = cx.min().x() - margem, maxY = cx.max().y() + margem;
        double larg = (cx.max().x() - cx.min().x()) + 2 * margem, alt = (cx.max().y() - cx.min().y()) + 2 * margem + 1.5;
        double k = 20; // px por metro
        var sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"%.0f\" height=\"%.0f\" viewBox=\"0 0 %.0f %.0f\" font-family=\"Inter, sans-serif\" font-size=\"6\">\n", larg * k, alt * k, larg * k, alt * k));
        sb.append("<rect width=\"100%\" height=\"100%\" fill=\"#FFFFFF\"/>\n");
        // transformação: y para cima
        java.util.function.DoubleUnaryOperator X = x -> (x - minX) * k;
        java.util.function.DoubleUnaryOperator Y = y -> (maxY - y) * k;

        // eixos com bolhas recuadas
        sb.append("<g id=\"EIXOS\" stroke=\"#6B7280\" stroke-dasharray=\"6 2 1 2\" stroke-width=\"0.5\">\n");
        var bolhas = new ArrayList<double[]>();
        for (var ex : m.eixos()) {
            double recuo = ex.recuoBolha();
            if (ex.direcao() == Eixo.Direcao.X) {
                double bx = X.applyAsDouble(ex.posicao()), by = Y.applyAsDouble(ex.fim() + recuo);
                by = evitarSobreposicao(bolhas, bx, by, 7);
                sb.append(String.format(Locale.ROOT, "<line x1=\"%.1f\" y1=\"%.1f\" x2=\"%.1f\" y2=\"%.1f\"/>\n", bx, Y.applyAsDouble(ex.inicio()), bx, by + 6));
                sb.append(String.format(Locale.ROOT, "<circle cx=\"%.1f\" cy=\"%.1f\" r=\"6\" fill=\"#FFF\" stroke-dasharray=\"none\"/><text x=\"%.1f\" y=\"%.1f\" text-anchor=\"middle\" stroke=\"none\" fill=\"#111827\">%s</text>\n", bx, by, bx, by + 2, ex.nome()));
            } else {
                double by = Y.applyAsDouble(ex.posicao()), bx = X.applyAsDouble(ex.inicio() - recuo);
                bx = evitarSobreposicaoX(bolhas, bx, by, 7);
                sb.append(String.format(Locale.ROOT, "<line x1=\"%.1f\" y1=\"%.1f\" x2=\"%.1f\" y2=\"%.1f\"/>\n", bx + 6, by, X.applyAsDouble(ex.fim()), by));
                sb.append(String.format(Locale.ROOT, "<circle cx=\"%.1f\" cy=\"%.1f\" r=\"6\" fill=\"#FFF\" stroke-dasharray=\"none\"/><text x=\"%.1f\" y=\"%.1f\" text-anchor=\"middle\" stroke=\"none\" fill=\"#111827\">%s</text>\n", bx, by, bx, by + 2, ex.nome()));
            }
        }
        sb.append("</g>\n");

        // elementos
        sb.append("<g id=\"CORTADO\" stroke=\"#111827\" stroke-width=\"1.2\" fill=\"none\">\n");
        for (var e : m.elementosDo(pavimentoId)) {
            if (formas && (e.getTipo() == TipoElemento.PAREDE || e.getTipo() == TipoElemento.PORTA || e.getTipo() == TipoElemento.JANELA)) {
                continue;
            }
            if (!formas && e.getCamada() == Camada.fundacao) {
                continue;
            }
            switch (e.getTipo()) {
                case PAREDE -> {
                    var p1 = e.ponto("p1"); var p2 = e.ponto("p2");
                    double esp = e.num("espessura", 0.15) * k;
                    sb.append(String.format(Locale.ROOT, "<line x1=\"%.1f\" y1=\"%.1f\" x2=\"%.1f\" y2=\"%.1f\" stroke-width=\"%.1f\" stroke=\"#374151\"/>\n", X.applyAsDouble(p1.x()), Y.applyAsDouble(p1.y()), X.applyAsDouble(p2.x()), Y.applyAsDouble(p2.y()), esp));
                }
                case VIGA -> {
                    var p1 = e.ponto("p1"); var p2 = e.ponto("p2");
                    sb.append(String.format(Locale.ROOT, "<line x1=\"%.1f\" y1=\"%.1f\" x2=\"%.1f\" y2=\"%.1f\" stroke-width=\"%.1f\" stroke=\"#1D4ED8\" stroke-dasharray=\"%s\"/>\n", X.applyAsDouble(p1.x()), Y.applyAsDouble(p1.y()), X.applyAsDouble(p2.x()), Y.applyAsDouble(p2.y()), e.num("b", 0.12) * k, formas ? "none" : "4 2"));
                    var mid = p1.medio(p2);
                    sb.append(String.format(Locale.ROOT, "<text x=\"%.1f\" y=\"%.1f\" stroke=\"none\" fill=\"#1D4ED8\">%s %sx%s</text>\n", X.applyAsDouble(mid.x()) + 2, Y.applyAsDouble(mid.y()) - 2, rot(m, e), cota(e.num("b", 0.12)), cota(e.num("h", 0.4))));
                }
                case PILAR -> {
                    var c = e.ponto("centro");
                    double b = e.num("b", 0.19) * k, h = e.num("h", 0.4) * k;
                    sb.append(String.format(Locale.ROOT, "<rect x=\"%.1f\" y=\"%.1f\" width=\"%.1f\" height=\"%.1f\" fill=\"#111827\" transform=\"rotate(%.1f %.1f %.1f)\"/>\n", X.applyAsDouble(c.x()) - b / 2, Y.applyAsDouble(c.y()) - h / 2, b, h, -e.num("rotacao", 0), X.applyAsDouble(c.x()), Y.applyAsDouble(c.y())));
                    sb.append(String.format(Locale.ROOT, "<text x=\"%.1f\" y=\"%.1f\" stroke=\"none\" fill=\"#111827\">%s %sx%s</text>\n", X.applyAsDouble(c.x()) + b / 2 + 2, Y.applyAsDouble(c.y()) - h / 2 - 2, rot(m, e), cota(e.num("b", 0.19)), cota(e.num("h", 0.4))));
                }
                case LAJE -> {
                    var pts = e.pontos("contorno");
                    var d = new StringBuilder();
                    for (int i = 0; i < pts.size(); i++) {
                        d.append(i == 0 ? "M" : "L").append(f(X.applyAsDouble(pts.get(i).x()))).append(' ').append(f(Y.applyAsDouble(pts.get(i).y()))).append(' ');
                    }
                    d.append('Z');
                    sb.append(String.format(Locale.ROOT, "<path d=\"%s\" fill=\"#E5E7EB\" fill-opacity=\"0.5\" stroke=\"#6B7280\" stroke-width=\"0.5\"/>\n", d));
                    var c0 = pts.get(0);
                    sb.append(String.format(Locale.ROOT, "<text x=\"%.1f\" y=\"%.1f\" stroke=\"none\" fill=\"#374151\">%s h=%s</text>\n", X.applyAsDouble(c0.x()) + 4, Y.applyAsDouble(c0.y()) - 4, rot(m, e), cota(e.num("h", 0.12))));
                }
                case PORTA, JANELA -> {
                    var p = m.pontoRepresentativo(e);
                    double largAb = e.num("largura", 0.8) * k;
                    sb.append(String.format(Locale.ROOT, "<rect x=\"%.1f\" y=\"%.1f\" width=\"%.1f\" height=\"%.1f\" fill=\"#FFFFFF\" stroke=\"%s\" stroke-width=\"0.8\"/>\n", X.applyAsDouble(p.x()) - largAb / 2, Y.applyAsDouble(p.y()) - 2, largAb, 4.0, e.getTipo() == TipoElemento.PORTA ? "#A16207" : "#0EA5E9"));
                }
                case SAPATA, BLOCO, RADIER -> {
                    var c = e.ponto("centro");
                    double bx = e.num("bx", 1.5) * k, by = e.num("by", 1.5) * k;
                    sb.append(String.format(Locale.ROOT, "<rect x=\"%.1f\" y=\"%.1f\" width=\"%.1f\" height=\"%.1f\" fill=\"none\" stroke=\"#6B7280\" stroke-dasharray=\"3 2\"/>\n", X.applyAsDouble(c.x()) - bx / 2, Y.applyAsDouble(c.y()) - by / 2, bx, by));
                }
                case LINHA, PLANO_REF -> {
                    if ("construcao".equals(e.getRotulo())) {
                        break;
                    }
                    var p1 = e.ponto("p1"); var p2 = e.ponto("p2");
                    sb.append(String.format(Locale.ROOT, "<line x1=\"%.1f\" y1=\"%.1f\" x2=\"%.1f\" y2=\"%.1f\" stroke=\"#9CA3AF\" stroke-width=\"0.4\"/>\n", X.applyAsDouble(p1.x()), Y.applyAsDouble(p1.y()), X.applyAsDouble(p2.x()), Y.applyAsDouble(p2.y())));
                }
                case POLILINHA -> {
                    var pts = e.pontos("pontos");
                    var d = new StringBuilder();
                    for (int i = 0; i < pts.size(); i++) {
                        d.append(i == 0 ? "M" : "L").append(f(X.applyAsDouble(pts.get(i).x()))).append(' ').append(f(Y.applyAsDouble(pts.get(i).y()))).append(' ');
                    }
                    if (e.flag("fechada", false)) {
                        d.append('Z');
                    }
                    sb.append(String.format(Locale.ROOT, "<path d=\"%s\" stroke=\"#9CA3AF\" stroke-width=\"0.4\"/>\n", d));
                }
                case CIRCULO -> {
                    var c = e.ponto("centro");
                    sb.append(String.format(Locale.ROOT, "<circle cx=\"%.1f\" cy=\"%.1f\" r=\"%.1f\" stroke=\"#9CA3AF\" stroke-width=\"0.4\"/>\n", X.applyAsDouble(c.x()), Y.applyAsDouble(c.y()), e.num("raio", 1) * k));
                }
                default -> { }
            }
        }
        sb.append("</g>\n");

        // anotações manuais (camada anotacao) sobrevivem à regeneração
        sb.append("<g id=\"TEXTO\" stroke=\"none\" fill=\"#111827\">\n");
        for (var e : m.elementosDo(pavimentoId)) {
            if (e.getCamada() != Camada.anotacao) {
                continue;
            }
            switch (e.getTipo()) {
                case TEXTO, ETIQUETA -> {
                    var p = e.ponto("p");
                    sb.append(String.format(Locale.ROOT, "<text x=\"%.1f\" y=\"%.1f\">%s</text>\n", X.applyAsDouble(p.x()), Y.applyAsDouble(p.y()), esc(e.texto("texto"))));
                }
                case COTA -> {
                    var p1 = e.ponto("p1"); var p2 = e.ponto("p2");
                    if ("NIVEL".equals(e.texto("estilo"))) {
                        var z = m.pavimento(pavimentoId).map(x -> x.elevacao()).orElse(0.0);
                        sb.append(String.format(Locale.ROOT, "<text x=\"%.1f\" y=\"%.1f\">%s%.2f</text>\n", X.applyAsDouble(p1.x()), Y.applyAsDouble(p1.y()), z >= 0 ? "+" : "", z));
                    } else {
                        cotaSvg(sb, X, Y, p1, p2, e.num("afastamento", 1.0), k);
                    }
                }
                case LINHA -> {
                    var p1 = e.ponto("p1"); var p2 = e.ponto("p2");
                    sb.append(String.format(Locale.ROOT, "<line x1=\"%.1f\" y1=\"%.1f\" x2=\"%.1f\" y2=\"%.1f\" stroke=\"#111827\" stroke-width=\"0.6\"/>\n", X.applyAsDouble(p1.x()), Y.applyAsDouble(p1.y()), X.applyAsDouble(p2.x()), Y.applyAsDouble(p2.y())));
                }
                case HACHURA -> {
                    var pts = e.pontos("contorno");
                    var d = new StringBuilder();
                    for (int i = 0; i < pts.size(); i++) {
                        d.append(i == 0 ? "M" : "L").append(f(X.applyAsDouble(pts.get(i).x()))).append(' ').append(f(Y.applyAsDouble(pts.get(i).y()))).append(' ');
                    }
                    sb.append(String.format(Locale.ROOT, "<path d=\"%sZ\" fill=\"url(#hachura)\" stroke=\"none\"/>\n", d));
                }
                default -> { }
            }
        }
        sb.append("</g>\n");
        sb.append("<defs><pattern id=\"hachura\" width=\"4\" height=\"4\" patternUnits=\"userSpaceOnUse\" patternTransform=\"rotate(45)\"><line x1=\"0\" y1=\"0\" x2=\"0\" y2=\"4\" stroke=\"#6B7280\" stroke-width=\"0.5\"/></pattern></defs>\n");

        // cotas totais por direção = envoltória real (RF14 CA-06)
        var env = envoltoriaReal(m, pavimentoId);
        sb.append("<g id=\"COTAS\" stroke=\"#111827\" stroke-width=\"0.5\" fill=\"#111827\">\n");
        cotaSvg(sb, X, Y, new Ponto2D(env.min().x(), env.min().y()), new Ponto2D(env.max().x(), env.min().y()), -(2.0), k);
        cotaSvg(sb, X, Y, new Ponto2D(env.max().x(), env.min().y()), new Ponto2D(env.max().x(), env.max().y()), 2.0, k);
        sb.append("</g>\n");

        // carimbo e marca d'água
        double cw = 160, ch = 28;
        sb.append(String.format(Locale.ROOT, "<g id=\"CARIMBO\" transform=\"translate(%.1f %.1f)\"><rect width=\"%.0f\" height=\"%.0f\" fill=\"#FFF\" stroke=\"#111827\"/>", larg * k - cw - 4, alt * k - ch - 4, cw, ch));
        sb.append(String.format(Locale.ROOT, "<text x=\"3\" y=\"8\">%s · %s</text><text x=\"3\" y=\"16\">Resp. técnico: %s · %s</text><text x=\"3\" y=\"24\">Escala %s · %s · v%d</text></g>\n",
                esc(carimbo.obra()), esc(carimbo.pavimento()), esc(carimbo.responsavel()), esc(carimbo.artRrt() == null ? "ART/RRT pendente" : carimbo.artRrt()), carimbo.escala(), carimbo.formato(), carimbo.versao()));
        if (carimbo.rascunho()) {
            sb.append(String.format(Locale.ROOT, "<text x=\"%.1f\" y=\"%.1f\" font-size=\"28\" fill=\"#DC2626\" fill-opacity=\"0.25\" text-anchor=\"middle\" transform=\"rotate(-30 %.1f %.1f)\">RASCUNHO — SEM VALIDADE TÉCNICA</text>\n", larg * k / 2, alt * k / 2, larg * k / 2, alt * k / 2));
        }
        sb.append("</svg>\n");
        return sb.toString();
    }

    private static void cotaSvg(StringBuilder sb, java.util.function.DoubleUnaryOperator X, java.util.function.DoubleUnaryOperator Y, Ponto2D p1, Ponto2D p2, double afast, double k) {
        double dx = p2.x() - p1.x(), dy = p2.y() - p1.y();
        double l = Math.hypot(dx, dy);
        if (l < 1e-9) {
            return;
        }
        double nx = -dy / l * afast, ny = dx / l * afast;
        var a = new Ponto2D(p1.x() + nx, p1.y() + ny);
        var b = new Ponto2D(p2.x() + nx, p2.y() + ny);
        sb.append(String.format(Locale.ROOT, "<line x1=\"%.1f\" y1=\"%.1f\" x2=\"%.1f\" y2=\"%.1f\"/>", X.applyAsDouble(a.x()), Y.applyAsDouble(a.y()), X.applyAsDouble(b.x()), Y.applyAsDouble(b.y())));
        sb.append(String.format(Locale.ROOT, "<line x1=\"%.1f\" y1=\"%.1f\" x2=\"%.1f\" y2=\"%.1f\"/>", X.applyAsDouble(p1.x()), Y.applyAsDouble(p1.y()), X.applyAsDouble(a.x()), Y.applyAsDouble(a.y())));
        sb.append(String.format(Locale.ROOT, "<line x1=\"%.1f\" y1=\"%.1f\" x2=\"%.1f\" y2=\"%.1f\"/>", X.applyAsDouble(p2.x()), Y.applyAsDouble(p2.y()), X.applyAsDouble(b.x()), Y.applyAsDouble(b.y())));
        var mid = a.medio(b);
        sb.append(String.format(Locale.ROOT, "<text x=\"%.1f\" y=\"%.1f\" text-anchor=\"middle\" stroke=\"none\">%s</text>\n", X.applyAsDouble(mid.x()), Y.applyAsDouble(mid.y()) - 1.5, cota(l)));
    }

    private static double evitarSobreposicao(List<double[]> bolhas, double x, double y, double r) {
        double yy = y;
        boolean colide = true;
        while (colide) {
            colide = false;
            for (var b : bolhas) {
                if (Math.hypot(b[0] - x, b[1] - yy) < 2 * r) {
                    yy -= 2 * r;
                    colide = true;
                }
            }
        }
        bolhas.add(new double[]{x, yy});
        return yy;
    }

    private static double evitarSobreposicaoX(List<double[]> bolhas, double x, double y, double r) {
        double xx = x;
        boolean colide = true;
        while (colide) {
            colide = false;
            for (var b : bolhas) {
                if (Math.hypot(b[0] - xx, b[1] - y) < 2 * r) {
                    xx -= 2 * r;
                    colide = true;
                }
            }
        }
        bolhas.add(new double[]{xx, y});
        return xx;
    }

    private static String rot(ModeloBim m, Elemento e) {
        return e.getRotulo() != null ? e.getRotulo() : m.rotuloAutomatico(e);
    }

    static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /** DXF R12 ASCII com as camadas EIXOS, COTAS, CORTADO, VISTA, OCULTO, TEXTO (RF14 CA-07). */
    public static String dxf(ModeloBim m, UUID pavimentoId, boolean formas) {
        var sb = new StringBuilder();
        sb.append("0\nSECTION\n2\nHEADER\n9\n$INSUNITS\n70\n6\n0\nENDSEC\n");
        sb.append("0\nSECTION\n2\nTABLES\n0\nTABLE\n2\nLAYER\n70\n6\n");
        for (var camada : List.of("EIXOS", "COTAS", "CORTADO", "VISTA", "OCULTO", "TEXTO")) {
            sb.append("0\nLAYER\n2\n").append(camada).append("\n70\n0\n62\n7\n6\nCONTINUOUS\n");
        }
        sb.append("0\nENDTAB\n0\nENDSEC\n0\nSECTION\n2\nENTITIES\n");
        for (var ex : m.eixos()) {
            if (ex.direcao() == Eixo.Direcao.X) {
                linha(sb, "EIXOS", ex.posicao(), ex.inicio(), ex.posicao(), ex.fim() + ex.recuoBolha());
                circulo(sb, "EIXOS", ex.posicao(), ex.fim() + ex.recuoBolha() + 0.3, 0.3);
                texto(sb, "TEXTO", ex.posicao() - 0.1, ex.fim() + ex.recuoBolha() + 0.2, 0.25, ex.nome());
            } else {
                linha(sb, "EIXOS", ex.inicio() - ex.recuoBolha(), ex.posicao(), ex.fim(), ex.posicao());
                circulo(sb, "EIXOS", ex.inicio() - ex.recuoBolha() - 0.3, ex.posicao(), 0.3);
                texto(sb, "TEXTO", ex.inicio() - ex.recuoBolha() - 0.4, ex.posicao() - 0.1, 0.25, ex.nome());
            }
        }
        for (var e : m.elementosDo(pavimentoId)) {
            switch (e.getTipo()) {
                case PAREDE -> {
                    if (!formas) {
                        linha(sb, "CORTADO", e.ponto("p1"), e.ponto("p2"));
                    }
                }
                case VIGA -> linha(sb, formas ? "CORTADO" : "OCULTO", e.ponto("p1"), e.ponto("p2"));
                case PILAR -> {
                    var c = e.ponto("centro");
                    double b = e.num("b", 0.19) / 2, h = e.num("h", 0.4) / 2;
                    linha(sb, "CORTADO", c.x() - b, c.y() - h, c.x() + b, c.y() - h);
                    linha(sb, "CORTADO", c.x() + b, c.y() - h, c.x() + b, c.y() + h);
                    linha(sb, "CORTADO", c.x() + b, c.y() + h, c.x() - b, c.y() + h);
                    linha(sb, "CORTADO", c.x() - b, c.y() + h, c.x() - b, c.y() - h);
                    texto(sb, "TEXTO", c.x() + b + 0.05, c.y() + h, 0.2, rot(m, e));
                }
                case LAJE -> {
                    var pts = e.pontos("contorno");
                    for (int i = 0; i < pts.size(); i++) {
                        linha(sb, "VISTA", pts.get(i), pts.get((i + 1) % pts.size()));
                    }
                }
                case LINHA, PLANO_REF -> linha(sb, e.getCamada() == Camada.anotacao ? "TEXTO" : "VISTA", e.ponto("p1"), e.ponto("p2"));
                case CIRCULO -> circulo(sb, "VISTA", e.ponto("centro").x(), e.ponto("centro").y(), e.num("raio", 1));
                case TEXTO, ETIQUETA -> texto(sb, "TEXTO", e.ponto("p").x(), e.ponto("p").y(), e.num("altura", 0.25), e.texto("texto"));
                case COTA -> {
                    linha(sb, "COTAS", e.ponto("p1"), e.ponto("p2"));
                    var mid = e.ponto("p1").medio(e.ponto("p2"));
                    texto(sb, "COTAS", mid.x(), mid.y() + 0.1, 0.2, cota(e.ponto("p1").distancia(e.ponto("p2"))));
                }
                default -> { }
            }
        }
        var env = envoltoriaReal(m, pavimentoId);
        linha(sb, "COTAS", env.min().x(), env.min().y() - 2, env.max().x(), env.min().y() - 2);
        texto(sb, "COTAS", (env.min().x() + env.max().x()) / 2, env.min().y() - 1.9, 0.25, cota(env.max().x() - env.min().x()));
        linha(sb, "COTAS", env.max().x() + 2, env.min().y(), env.max().x() + 2, env.max().y());
        texto(sb, "COTAS", env.max().x() + 2.1, (env.min().y() + env.max().y()) / 2, 0.25, cota(env.max().y() - env.min().y()));
        sb.append("0\nENDSEC\n0\nEOF\n");
        return sb.toString();
    }

    private static void linha(StringBuilder sb, String camada, Ponto2D a, Ponto2D b) {
        linha(sb, camada, a.x(), a.y(), b.x(), b.y());
    }

    private static void linha(StringBuilder sb, String camada, double x1, double y1, double x2, double y2) {
        sb.append("0\nLINE\n8\n").append(camada).append("\n10\n").append(f(x1)).append("\n20\n").append(f(y1)).append("\n30\n0\n11\n").append(f(x2)).append("\n21\n").append(f(y2)).append("\n31\n0\n");
    }

    private static void circulo(StringBuilder sb, String camada, double x, double y, double r) {
        sb.append("0\nCIRCLE\n8\n").append(camada).append("\n10\n").append(f(x)).append("\n20\n").append(f(y)).append("\n30\n0\n40\n").append(f(r)).append('\n');
    }

    private static void texto(StringBuilder sb, String camada, double x, double y, double h, String t) {
        sb.append("0\nTEXT\n8\n").append(camada).append("\n10\n").append(f(x)).append("\n20\n").append(f(y)).append("\n30\n0\n40\n").append(f(h)).append("\n1\n").append(t == null ? "" : t).append('\n');
    }
}
