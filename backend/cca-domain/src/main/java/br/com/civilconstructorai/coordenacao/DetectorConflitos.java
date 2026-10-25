package br.com.civilconstructorai.coordenacao;

import br.com.civilconstructorai.modelo.Elemento;
import br.com.civilconstructorai.modelo.Enums.TipoElemento;
import br.com.civilconstructorai.modelo.ModeloBim;
import br.com.civilconstructorai.modelo.Pavimento;
import br.com.civilconstructorai.notacao.Ponto2D;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Detecção determinística de conflitos de coordenação sobre o modelo BIM (proposta P-COORD-01, auditoria 2026-10).
 * Convenção de nível igual à do modelo analítico: vigas e lajes de um pavimento têm a face superior na elevação dele;
 * pilares sobem do pavimento base até o de topo. Sólidos: prisma retangular orientado em planta × intervalo em z
 * (lajes pela caixa envolvente do contorno, simplificação declarada na evidência).
 * <p>
 * Regras implementadas: GEO-01 pilares sobrepostos; GEO-02 vigas duplicadas (colineares e sobrepostas); GEO-03 lajes
 * sobrepostas no mesmo pavimento; CON-01 abertura que atravessa pilar; CON-02 viga do pavimento de cima abaixo do
 * topo de porta ou janela; LIG-01 extremidade de viga sem apoio (pilar ou outra viga). Folga e conflitos funcionais
 * dependem de instalações modeladas e não são verificados. Nenhuma regra propõe furo em viga ou pilar.
 */
public final class DetectorConflitos {

    public static final double TOL = 0.01;

    public enum Tipo { GEOMETRICO, FOLGA, CONECTIVIDADE, FUNCIONAL, CONSTRUTIVO }

    /** {@code chave}: estável entre versões (tipo, regra e ids ordenados), usada para reverificar */
    public record Conflito(String chave, Tipo tipo, String regra, List<UUID> elementos, String mensagem, String evidencia) {
    }

    /** prisma: retângulo de centro c, eixo u (unitário), meias-dimensões a (ao longo de u) e b, entre z0 e z1 */
    record Prisma(Ponto2D c, double ux, double uy, double a, double b, double z0, double z1) {
        boolean intersecta(Prisma o, double folga) {
            if (z1 - folga <= o.z0 || o.z1 - folga <= z0) {
                return false;
            }
            // eixos separadores do teorema dos eixos separantes (2D, retângulos orientados)
            double[][] eixos = {{ux, uy}, {-uy, ux}, {o.ux, o.uy}, {-o.uy, o.ux}};
            double dx = o.c.x() - c.x(), dy = o.c.y() - c.y();
            for (var e : eixos) {
                double d = Math.abs(dx * e[0] + dy * e[1]);
                double r1 = a * Math.abs(ux * e[0] + uy * e[1]) + b * Math.abs(-uy * e[0] + ux * e[1]);
                double r2 = o.a * Math.abs(o.ux * e[0] + o.uy * e[1]) + o.b * Math.abs(-o.uy * e[0] + o.ux * e[1]);
                if (d >= r1 + r2 - folga) {
                    return false;
                }
            }
            return true;
        }
    }

    private DetectorConflitos() {
    }

    public static List<Conflito> detectar(ModeloBim m) {
        var out = new ArrayList<Conflito>();
        var elementos = m.elementos().stream().filter(e -> e.getRascunhoIaId() == null).toList();
        var pilares = elementos.stream().filter(e -> e.getTipo() == TipoElemento.PILAR).toList();
        var vigas = elementos.stream().filter(e -> e.getTipo() == TipoElemento.VIGA).toList();
        var lajes = elementos.stream().filter(e -> e.getTipo() == TipoElemento.LAJE).toList();
        var aberturas = elementos.stream().filter(e -> e.getTipo() == TipoElemento.PORTA || e.getTipo() == TipoElemento.JANELA).toList();

        for (int i = 0; i < pilares.size(); i++) {
            for (int j = i + 1; j < pilares.size(); j++) {
                var a = pilares.get(i);
                var b = pilares.get(j);
                if (prismaPilar(m, a).intersecta(prismaPilar(m, b), TOL)) {
                    out.add(conflito(Tipo.GEOMETRICO, "GEO-01", List.of(a, b), m, "Pilares " + nome(m, a) + " e " + nome(m, b) + " ocupam o mesmo volume.",
                            "seções em planta se sobrepõem e os intervalos de altura se cruzam"));
                }
            }
        }
        for (int i = 0; i < vigas.size(); i++) {
            for (int j = i + 1; j < vigas.size(); j++) {
                var a = vigas.get(i);
                var b = vigas.get(j);
                if (!a.getPavimentoId().equals(b.getPavimentoId())) {
                    continue;
                }
                double sobre = sobreposicaoColinear(a.ponto("p1"), a.ponto("p2"), b.ponto("p1"), b.ponto("p2"));
                if (sobre > TOL) {
                    out.add(conflito(Tipo.GEOMETRICO, "GEO-02", List.of(a, b), m, "Vigas " + nome(m, a) + " e " + nome(m, b) + " estão sobrepostas no mesmo eixo.",
                            String.format(Locale.ROOT, "trecho comum de %.2f m", sobre)));
                }
            }
        }
        for (int i = 0; i < lajes.size(); i++) {
            for (int j = i + 1; j < lajes.size(); j++) {
                var a = lajes.get(i);
                var b = lajes.get(j);
                if (!a.getPavimentoId().equals(b.getPavimentoId())) {
                    continue;
                }
                double area = areaComum(a.pontos("contorno"), b.pontos("contorno"));
                if (area > 0.01) {
                    out.add(conflito(Tipo.GEOMETRICO, "GEO-03", List.of(a, b), m, "Lajes " + nome(m, a) + " e " + nome(m, b) + " se sobrepõem.",
                            String.format(Locale.ROOT, "área comum das caixas envolventes ≈ %.2f m² (simplificação: contorno pela caixa envolvente)", area)));
                }
            }
        }
        for (var ab : aberturas) {
            var parede = m.elemento(ab.uuid("paredeId")).orElse(null);
            if (parede == null) {
                continue;
            }
            var pr = prismaAbertura(m, ab, parede);
            for (var p : pilares) {
                if (pr.intersecta(prismaPilar(m, p), TOL)) {
                    out.add(conflito(Tipo.CONSTRUTIVO, "CON-01", List.of(ab, p), m, (ab.getTipo() == TipoElemento.PORTA ? "Porta " : "Janela ") + nome(m, ab) + " atravessa o pilar " + nome(m, p) + ".",
                            String.format(Locale.ROOT, "vão de %.2f m na parede %s cruza a seção do pilar", ab.num("largura", 0.8), nome(m, parede))));
                }
            }
            var acima = m.pavimentoAcima(parede.getPavimentoId()).orElse(null);
            if (acima != null) {
                for (var v : vigas) {
                    if (!v.getPavimentoId().equals(acima.id())) {
                        continue;
                    }
                    var pv = prismaViga(m, v);
                    if (pr.intersecta(pv, TOL)) {
                        out.add(conflito(Tipo.CONSTRUTIVO, "CON-02", List.of(ab, v), m, "Viga " + nome(m, v) + " fica abaixo do topo da " + (ab.getTipo() == TipoElemento.PORTA ? "porta " : "janela ") + nome(m, ab) + ".",
                                String.format(Locale.ROOT, "fundo da viga em z = %.2f m; topo da abertura em z = %.2f m", pv.z0(), pr.z1())));
                    }
                }
            }
        }
        for (var v : vigas) {
            for (String ponta : List.of("p1", "p2")) {
                var q = v.ponto(ponta);
                double z = elevacao(m, v.getPavimentoId());
                boolean apoiada = pilares.stream().anyMatch(p -> {
                    var pp = prismaPilar(m, p);
                    return z >= pp.z0() - TOL && z <= pp.z1() + TOL && dentro(q, pp, 0.05);
                }) || vigas.stream().anyMatch(o -> o != v && o.getPavimentoId().equals(v.getPavimentoId()) && distanciaAoSegmento(q, o.ponto("p1"), o.ponto("p2")) <= Math.max(o.num("b", 0.14), 0.05));
                if (!apoiada) {
                    out.add(conflito(Tipo.CONECTIVIDADE, "LIG-01", List.of(v), m, "Extremidade " + ponta + " da viga " + nome(m, v) + " não chega a pilar nem a outra viga.",
                            String.format(Locale.ROOT, "ponto (%.2f; %.2f) a z = %.2f m sem apoio a menos de 5 cm", q.x(), q.y(), z)));
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ sólidos
    static double elevacao(ModeloBim m, UUID pav) {
        return m.pavimento(pav).map(Pavimento::elevacao).orElse(0.0);
    }

    static Prisma prismaPilar(ModeloBim m, Elemento p) {
        var base = m.pavimento(p.uuid("nivelBaseId") == null ? p.getPavimentoId() : p.uuid("nivelBaseId")).orElse(null);
        double z0 = (base == null ? 0 : base.elevacao()) + p.num("baseOffset", 0);
        var topo = p.uuid("nivelTopoId") == null ? null : m.pavimento(p.uuid("nivelTopoId")).orElse(null);
        double z1;
        if (topo != null && base != null && !topo.id().equals(base.id())) {
            z1 = topo.elevacao();
        } else {
            z1 = base == null ? 3.0 : m.pavimentoAcima(base.id()).map(Pavimento::elevacao).orElse(base.elevacao() + 3.0);
        }
        z1 += p.num("topoOffset", 0);
        double ang = Math.toRadians(p.num("rotacao", 0));
        boolean circular = "CIRCULAR".equals(p.texto("secao"));
        double b = p.num("b", 0.19), h = circular ? b : p.num("h", 0.4);
        return new Prisma(p.ponto("centro"), Math.cos(ang), Math.sin(ang), b / 2, h / 2, z0, z1);
    }

    static Prisma prismaViga(ModeloBim m, Elemento v) {
        var a = v.ponto("p1");
        var b = v.ponto("p2");
        double l = Math.hypot(b.x() - a.x(), b.y() - a.y());
        double z = elevacao(m, v.getPavimentoId());
        return new Prisma(new Ponto2D((a.x() + b.x()) / 2, (a.y() + b.y()) / 2), (b.x() - a.x()) / l, (b.y() - a.y()) / l, l / 2, v.num("b", 0.14) / 2, z - v.num("h", 0.4), z);
    }

    static Prisma prismaAbertura(ModeloBim m, Elemento ab, Elemento parede) {
        var a = parede.ponto("p1");
        var b = parede.ponto("p2");
        double l = Math.hypot(b.x() - a.x(), b.y() - a.y());
        double t = ab.num("posicao", 0.5);
        var c = new Ponto2D(a.x() + (b.x() - a.x()) * t, a.y() + (b.y() - a.y()) * t);
        double z = elevacao(m, parede.getPavimentoId()) + ab.num("peitoril", 0);
        return new Prisma(c, (b.x() - a.x()) / l, (b.y() - a.y()) / l, ab.num("largura", 0.8) / 2, parede.num("espessura", 0.15) / 2, z, z + ab.num("altura", 2.1));
    }

    private static boolean dentro(Ponto2D q, Prisma p, double folga) {
        double dx = q.x() - p.c().x(), dy = q.y() - p.c().y();
        return Math.abs(dx * p.ux() + dy * p.uy()) <= p.a() + folga && Math.abs(-dx * p.uy() + dy * p.ux()) <= p.b() + folga;
    }

    static double distanciaAoSegmento(Ponto2D q, Ponto2D a, Ponto2D b) {
        double dx = b.x() - a.x(), dy = b.y() - a.y(), l2 = dx * dx + dy * dy;
        double t = l2 == 0 ? 0 : Math.max(0, Math.min(1, ((q.x() - a.x()) * dx + (q.y() - a.y()) * dy) / l2));
        return Math.hypot(q.x() - (a.x() + t * dx), q.y() - (a.y() + t * dy));
    }

    /** comprimento comum de dois segmentos colineares (0 se não colineares) */
    static double sobreposicaoColinear(Ponto2D a, Ponto2D b, Ponto2D c, Ponto2D d) {
        if (distanciaReta(c, a, b) > TOL || distanciaReta(d, a, b) > TOL) {
            return 0;
        }
        double l = Math.hypot(b.x() - a.x(), b.y() - a.y());
        double ux = (b.x() - a.x()) / l, uy = (b.y() - a.y()) / l;
        double tc = (c.x() - a.x()) * ux + (c.y() - a.y()) * uy, td = (d.x() - a.x()) * ux + (d.y() - a.y()) * uy;
        return Math.max(0, Math.min(l, Math.max(tc, td)) - Math.max(0, Math.min(tc, td)));
    }

    private static double distanciaReta(Ponto2D q, Ponto2D a, Ponto2D b) {
        double l = Math.hypot(b.x() - a.x(), b.y() - a.y());
        return Math.abs((b.x() - a.x()) * (a.y() - q.y()) - (a.x() - q.x()) * (b.y() - a.y())) / l;
    }

    private static double areaComum(List<Ponto2D> p, List<Ponto2D> q) {
        if (p.isEmpty() || q.isEmpty()) {
            return 0;
        }
        double ax0 = p.stream().mapToDouble(Ponto2D::x).min().orElse(0), ax1 = p.stream().mapToDouble(Ponto2D::x).max().orElse(0);
        double ay0 = p.stream().mapToDouble(Ponto2D::y).min().orElse(0), ay1 = p.stream().mapToDouble(Ponto2D::y).max().orElse(0);
        double bx0 = q.stream().mapToDouble(Ponto2D::x).min().orElse(0), bx1 = q.stream().mapToDouble(Ponto2D::x).max().orElse(0);
        double by0 = q.stream().mapToDouble(Ponto2D::y).min().orElse(0), by1 = q.stream().mapToDouble(Ponto2D::y).max().orElse(0);
        return Math.max(0, Math.min(ax1, bx1) - Math.max(ax0, bx0)) * Math.max(0, Math.min(ay1, by1) - Math.max(ay0, by0));
    }

    private static String nome(ModeloBim m, Elemento e) {
        return e.getRotulo() != null ? e.getRotulo() : m.rotuloAutomatico(e);
    }

    private static Conflito conflito(Tipo tipo, String regra, List<Elemento> els, ModeloBim m, String msg, String evidencia) {
        var ids = els.stream().map(Elemento::getId).sorted().toList();
        return new Conflito(chave(tipo, regra, ids), tipo, regra, ids, msg, evidencia);
    }

    static String chave(Tipo tipo, String regra, List<UUID> ids) {
        try {
            var md = MessageDigest.getInstance("SHA-256");
            md.update((tipo + "|" + regra + "|" + ids).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(md.digest()).substring(0, 24);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
