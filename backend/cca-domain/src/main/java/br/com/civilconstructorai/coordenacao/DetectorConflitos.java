package br.com.civilconstructorai.coordenacao;

import br.com.civilconstructorai.modelo.Elemento;
import br.com.civilconstructorai.modelo.Enums.TipoElemento;
import br.com.civilconstructorai.modelo.ModeloBim;
import br.com.civilconstructorai.modelo.ObjetosPrediais;
import br.com.civilconstructorai.modelo.Pavimento;
import br.com.civilconstructorai.notacao.CatalogoObjetos;
import br.com.civilconstructorai.notacao.CatalogoObjetos.Ponto3D;
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
 * topo de porta ou janela; LIG-01 extremidade de viga sem apoio (pilar ou outra viga).
 * <p>
 * Objetos e instalações (AUD-05): OBJ-01 objeto × parede (exceto a parede que o hospeda e o vão de porta); OBJ-02
 * objeto × objeto (exceto itens que admitem sobreposição, como tapete); OBJ-03 objeto na área de giro da folha de
 * porta (mesma convenção de desenho da planta: dobradiça na ponta inicial, abre para a esquerda da parede); OBJ-04
 * objeto que invade laje ou viga do pavimento de cima; MEP-01 percurso de conexão que atravessa viga ou pilar;
 * MEP-02 percursos de sistemas diferentes mais próximos que a soma dos raios + {@link #FOLGA_MEP}; MEP-03 aparelho
 * ligado a tomada de outro ambiente (a linha tomada → aparelho cruza parede); HUM-01 pessoa dentro de parede ou
 * objeto. Os percursos são os esquemáticos derivados ({@link ObjetosPrediais#percurso}), não traçado de obra; o
 * diâmetro vem do {@code dnMm} da conexão (elétrica sem diâmetro: eixo). {@link #FOLGA_MEP} é parâmetro de verificação
 * adotado, não limite normativo. Nenhuma regra propõe furo em viga ou pilar: a correção indicada é desviar e
 * submeter passagens à validação do responsável técnico.
 */
public final class DetectorConflitos {

    public static final double TOL = 0.01;
    /** distância livre de verificação entre percursos de sistemas diferentes (adotada, ajustável; não é valor de norma) */
    public static final double FOLGA_MEP = 0.05;

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
                    var c = conflito(Tipo.CONECTIVIDADE, "LIG-01", List.of(v), m, "Extremidade " + ponta + " da viga " + nome(m, v) + " não chega a pilar nem a outra viga.",
                            String.format(Locale.ROOT, "ponto (%.2f; %.2f) a z = %.2f m sem apoio a menos de 5 cm", q.x(), q.y(), z));
                    // as duas pontas da mesma viga são conflitos distintos: a chave leva a ponta (antes colidiam e a gravação falhava)
                    out.add(new Conflito(chave(Tipo.CONECTIVIDADE, "LIG-01:" + ponta, c.elementos()), c.tipo(), c.regra(), c.elementos(), c.mensagem(), c.evidencia()));
                }
            }
        }
        objetosEInstalacoes(m, elementos, vigas, pilares, lajes, out);
        // a chave identifica o conflito entre verificações e é única por obra no banco
        var vistas = new java.util.HashSet<String>();
        out.removeIf(c -> !vistas.add(c.chave()));
        return out;
    }

    // ------------------------------------------------------------------ objetos, instalações e pessoas (AUD-05)
    private static void objetosEInstalacoes(ModeloBim m, List<Elemento> elementos, List<Elemento> vigas, List<Elemento> pilares, List<Elemento> lajes, List<Conflito> out) {
        var objetos = elementos.stream().filter(e -> e.getTipo() == TipoElemento.OBJETO && ObjetosPrediais.item(e).isPresent()).toList();
        var paredes = elementos.stream().filter(e -> e.getTipo() == TipoElemento.PAREDE).toList();
        var portas = elementos.stream().filter(e -> e.getTipo() == TipoElemento.PORTA).toList();
        var conexoes = elementos.stream().filter(e -> e.getTipo() == TipoElemento.CONEXAO).toList();

        for (var o : objetos) {
            var it = ObjetosPrediais.item(o).orElseThrow();
            boolean humano = "humanos".equals(it.disciplina());
            var po = prismaObjeto(m, o);
            for (var w : paredes) {
                if (!w.getPavimentoId().equals(o.getPavimentoId()) || w.getId().toString().equals(o.texto("paredeId")) || noVaoDePorta(m, o, w, portas)) {
                    continue;
                }
                var pw = prismaParede(m, w);
                if (po.intersecta(pw, TOL)) {
                    double pen = penetracao(po, pw);
                    out.add(conflito(humano ? Tipo.FUNCIONAL : Tipo.GEOMETRICO, humano ? "HUM-01" : "OBJ-01", List.of(o, w), m,
                            (humano ? "A pessoa " : "") + nomeObjeto(m, o) + " está dentro da parede " + nome(m, w) + ". Correção: afaste o objeto "
                                    + String.format(Locale.ROOT, "%.2f m", pen) + " ou fixe-o na face da parede.",
                            String.format(Locale.ROOT, "volumes se cruzam em até %.2f m em planta", pen)));
                }
            }
            if (!humano) {
                var acima = m.pavimentoAcima(o.getPavimentoId()).orElse(null);
                if (acima != null) {
                    for (var v : vigas) {
                        if (v.getPavimentoId().equals(acima.id()) && po.intersecta(prismaViga(m, v), TOL)) {
                            var pv = prismaViga(m, v);
                            out.add(conflito(Tipo.GEOMETRICO, "OBJ-04", List.of(o, v), m, nomeObjeto(m, o) + " invade a viga " + nome(m, v) + ". Correção: reduza a elevação do objeto para "
                                            + String.format(Locale.ROOT, "%.2f m", Math.max(0, pv.z0() - elevacao(m, o.getPavimentoId()) - (po.z1() - po.z0()))) + " ou desloque-o para fora da projeção da viga.",
                                    String.format(Locale.ROOT, "topo do objeto em z = %.2f m; fundo da viga em z = %.2f m", po.z1(), pv.z0())));
                        }
                    }
                    for (var l : lajes) {
                        if (!l.getPavimentoId().equals(acima.id())) {
                            continue;
                        }
                        var pl = prismaLaje(m, l);
                        if (pl != null && po.intersecta(pl, TOL)) {
                            out.add(conflito(Tipo.GEOMETRICO, "OBJ-04", List.of(o, l), m, nomeObjeto(m, o) + " está dentro da laje " + nome(m, l) + ". Correção: posicione o objeto sob o teto (elevação máx. "
                                            + String.format(Locale.ROOT, "%.2f m", Math.max(0, pl.z0() - elevacao(m, o.getPavimentoId()) - (po.z1() - po.z0()))) + ") ou use um modelo embutido validado pelo projetista.",
                                    String.format(Locale.ROOT, "topo do objeto em z = %.2f m; face inferior da laje em z = %.2f m (contorno pela caixa envolvente)", po.z1(), pl.z0())));
                        }
                    }
                }
                for (var p : portas) {
                    var w = m.elemento(p.uuid("paredeId")).orElse(null);
                    if (w == null || !w.getPavimentoId().equals(o.getPavimentoId()) || it.sobreposicaoPermitida()) {
                        continue;
                    }
                    double zp = elevacao(m, w.getPavimentoId());
                    if (po.z1() <= zp + TOL || po.z0() >= zp + p.num("altura", 2.1)) {
                        continue;
                    }
                    if (sobrepoe(giroPorta(p, w), cantos(po))) {
                        out.add(conflito(Tipo.FUNCIONAL, "OBJ-03", List.of(o, p), m, nomeObjeto(m, o) + " impede a abertura da porta " + nome(m, p) + ". Correção: desloque o objeto para fora do raio de "
                                        + String.format(Locale.ROOT, "%.2f m", p.num("largura", 0.8)) + " da folha ou inverta o sentido de abertura da porta.",
                                String.format(Locale.ROOT, "área de giro: quarto de círculo de raio %.2f m a partir da dobradiça", p.num("largura", 0.8))));
                    }
                }
            }
        }
        for (int i = 0; i < objetos.size(); i++) {
            for (int j = i + 1; j < objetos.size(); j++) {
                var a = objetos.get(i);
                var b = objetos.get(j);
                var ia = ObjetosPrediais.item(a).orElseThrow();
                var ib = ObjetosPrediais.item(b).orElseThrow();
                if (!a.getPavimentoId().equals(b.getPavimentoId()) || ia.sobreposicaoPermitida() || ib.sobreposicaoPermitida()) {
                    continue;
                }
                boolean humano = "humanos".equals(ia.disciplina()) || "humanos".equals(ib.disciplina());
                var pa = prismaObjeto(m, a);
                var pb = prismaObjeto(m, b);
                if (pa.intersecta(pb, TOL)) {
                    out.add(conflito(humano ? Tipo.FUNCIONAL : Tipo.GEOMETRICO, humano ? "HUM-01" : "OBJ-02", List.of(a, b), m,
                            nomeObjeto(m, a) + " e " + nomeObjeto(m, b) + " ocupam o mesmo espaço. Correção: afaste um deles " + String.format(Locale.ROOT, "%.2f m", penetracao(pa, pb)) + ".",
                            String.format(Locale.ROOT, "volumes se cruzam (z %.2f–%.2f m × %.2f–%.2f m)", pa.z0(), pa.z1(), pb.z0(), pb.z1())));
                }
            }
        }
        var percursos = new java.util.LinkedHashMap<Elemento, List<Ponto3D>>();
        for (var c : conexoes) {
            var pts = ObjetosPrediais.percurso(m, c);
            if (pts.size() >= 2) {
                percursos.put(c, pts);
            }
        }
        for (var e : percursos.entrySet()) {
            var c = e.getKey();
            var pts = e.getValue();
            double r = c.num("dnMm", 0) / 2000.0;
            var pavs = new java.util.HashSet<UUID>();
            var de = ObjetosPrediais.objeto(m, ObjetosPrediais.ponta(c, "de")).orElseThrow();
            pavs.add(de.getPavimentoId());
            m.pavimentoAcima(de.getPavimentoId()).ifPresent(p -> pavs.add(p.id()));
            var estruturais = new ArrayList<Elemento>();
            vigas.stream().filter(v -> pavs.contains(v.getPavimentoId())).forEach(estruturais::add);
            estruturais.addAll(pilares);
            for (var s : estruturais) {
                var ps = s.getTipo() == TipoElemento.VIGA ? prismaViga(m, s) : prismaPilar(m, s);
                Ponto3D hit = null;
                for (int k = 1; k < pts.size() && hit == null; k++) {
                    hit = cruza(pts.get(k - 1), pts.get(k), ps, r);
                }
                if (hit != null) {
                    out.add(conflito(Tipo.GEOMETRICO, "MEP-01", List.of(c, s), m, "O percurso de " + ObjetosPrediais.rotuloSistema(c.texto("sistema")) + " " + nomeConexao(m, c)
                                    + " atravessa " + (s.getTipo() == TipoElemento.VIGA ? "a viga " : "o pilar ") + nome(m, s) + ". O CivilConstructor não abre furos em elementos estruturais: "
                                    + "desvie o percurso ou submeta a passagem à validação do engenheiro estrutural.",
                            String.format(Locale.ROOT, "percurso esquemático cruza o sólido em (%.2f; %.2f; z = %.2f)", hit.x(), hit.y(), hit.z())));
                }
            }
        }
        var lista = new ArrayList<>(percursos.entrySet());
        for (int i = 0; i < lista.size(); i++) {
            for (int j = i + 1; j < lista.size(); j++) {
                var a = lista.get(i).getKey();
                var b = lista.get(j).getKey();
                if (a.texto("sistema") == null || a.texto("sistema").equals(b.texto("sistema")) || compartilhamObjeto(a, b)) {
                    continue;
                }
                double lim = a.num("dnMm", 0) / 2000.0 + b.num("dnMm", 0) / 2000.0 + FOLGA_MEP;
                double[] melhor = {Double.MAX_VALUE, 0, 0, 0};
                var pa = lista.get(i).getValue();
                var pb = lista.get(j).getValue();
                for (int k = 1; k < pa.size(); k++) {
                    for (int q = 1; q < pb.size(); q++) {
                        var d = distanciaSegmentos(pa.get(k - 1), pa.get(k), pb.get(q - 1), pb.get(q));
                        if (d[0] < melhor[0]) {
                            melhor = d;
                        }
                    }
                }
                if (melhor[0] < lim) {
                    boolean cruza = melhor[0] < lim - FOLGA_MEP + 1e-9;
                    out.add(conflito(cruza ? Tipo.GEOMETRICO : Tipo.FOLGA, "MEP-02", List.of(a, b), m, "Os percursos de " + ObjetosPrediais.rotuloSistema(a.texto("sistema")) + " " + nomeConexao(m, a)
                                    + " e de " + ObjetosPrediais.rotuloSistema(b.texto("sistema")) + " " + nomeConexao(m, b) + (cruza ? " se interceptam" : " ficam muito próximos")
                                    + ". Correção: desloque um dos pontos ou altere a cota do trecho.",
                            String.format(Locale.ROOT, "distância entre eixos %.3f m em (%.2f; %.2f; z = %.2f); limite de verificação %.3f m (soma dos raios + folga adotada de %.2f m)",
                                    melhor[0], melhor[1], melhor[2], melhor[3], lim, FOLGA_MEP)));
                }
            }
        }
        for (var c : conexoes) {
            var de = ObjetosPrediais.objeto(m, ObjetosPrediais.ponta(c, "de")).orElse(null);
            var para = ObjetosPrediais.objeto(m, ObjetosPrediais.ponta(c, "para")).orElse(null);
            if (de == null || para == null) {
                continue;
            }
            var con = ObjetosPrediais.item(de).flatMap(it -> it.conector(String.valueOf(ObjetosPrediais.ponta(c, "de").get("conector")))).orElse(null);
            if (con == null || !"TOMADA".equals(con.papel())) {
                continue;
            }
            var a = de.ponto("centro");
            var b = para.ponto("centro");
            for (var w : paredes) {
                if (!w.getPavimentoId().equals(para.getPavimentoId()) && !w.getPavimentoId().equals(de.getPavimentoId())) {
                    continue;
                }
                var pw = prismaParede(m, w);
                if (cruza(new Ponto3D(a.x(), a.y(), 0), new Ponto3D(b.x(), b.y(), 0), new Prisma(pw.c(), pw.ux(), pw.uy(), pw.a(), pw.b(), -1, 1), 0) != null) {
                    out.add(conflito(Tipo.FUNCIONAL, "MEP-03", List.of(de, para, w), m, "A ligação de " + nomeObjeto(m, para) + " à " + nomeObjeto(m, de)
                                    + " atravessa a parede " + nome(m, w) + ": o aparelho está em outro ambiente. Correção: ligue o aparelho a uma tomada do ambiente onde ele está, volte-o ao ambiente original ou desfaça a conexão.",
                            String.format(Locale.ROOT, "linha tomada (%.2f; %.2f) → aparelho (%.2f; %.2f) cruza a parede", a.x(), a.y(), b.x(), b.y())));
                    break;
                }
            }
        }
    }

    static Prisma prismaObjeto(ModeloBim m, Elemento o) {
        var d = ObjetosPrediais.dim(o);
        double ang = Math.toRadians(o.num("rotacao", 0));
        double z = elevacao(m, o.getPavimentoId()) + o.num("elevacao", 0);
        return new Prisma(o.ponto("centro"), Math.cos(ang), Math.sin(ang), d[0] / 2, d[1] / 2, z, z + d[2]);
    }

    static Prisma prismaParede(ModeloBim m, Elemento w) {
        var a = w.ponto("p1");
        var b = w.ponto("p2");
        double l = Math.max(1e-9, Math.hypot(b.x() - a.x(), b.y() - a.y()));
        double z = elevacao(m, w.getPavimentoId());
        return new Prisma(new Ponto2D((a.x() + b.x()) / 2, (a.y() + b.y()) / 2), (b.x() - a.x()) / l, (b.y() - a.y()) / l, l / 2, w.num("espessura", 0.15) / 2, z, z + w.num("altura", 2.8));
    }

    /** laje do pavimento: face superior na elevação dele; contorno pela caixa envolvente */
    static Prisma prismaLaje(ModeloBim m, Elemento l) {
        var c = l.pontos("contorno");
        if (c.isEmpty()) {
            return null;
        }
        double x0 = c.stream().mapToDouble(Ponto2D::x).min().orElse(0), x1 = c.stream().mapToDouble(Ponto2D::x).max().orElse(0);
        double y0 = c.stream().mapToDouble(Ponto2D::y).min().orElse(0), y1 = c.stream().mapToDouble(Ponto2D::y).max().orElse(0);
        double z = elevacao(m, l.getPavimentoId());
        return new Prisma(new Ponto2D((x0 + x1) / 2, (y0 + y1) / 2), 1, 0, (x1 - x0) / 2, (y1 - y0) / 2, z - l.num("h", 0.12), z);
    }

    /** objeto cujo centro está no vão de uma porta da parede (passagem): o conflito é da folha, não da alvenaria */
    private static boolean noVaoDePorta(ModeloBim m, Elemento o, Elemento w, List<Elemento> portas) {
        var c = o.ponto("centro");
        return portas.stream().filter(p -> w.getId().equals(p.uuid("paredeId"))).anyMatch(p -> dentro(c, prismaAbertura(m, p, w), 0.05));
    }

    /** quarto de círculo da folha: dobradiça em c − u·L/2, entre u e a normal à esquerda n, raio L (convenção do Canvas2D) */
    static List<Ponto2D> giroPorta(Elemento p, Elemento w) {
        var a = w.ponto("p1");
        var b = w.ponto("p2");
        double l = Math.max(1e-9, Math.hypot(b.x() - a.x(), b.y() - a.y()));
        double ux = (b.x() - a.x()) / l, uy = (b.y() - a.y()) / l, nx = -uy, ny = ux;
        double t = p.num("posicao", 0.5), larg = p.num("largura", 0.8), esp = w.num("espessura", 0.15) / 2;
        double hx = a.x() + (b.x() - a.x()) * t - ux * larg / 2 + nx * esp, hy = a.y() + (b.y() - a.y()) * t - uy * larg / 2 + ny * esp;
        var pts = new ArrayList<Ponto2D>();
        pts.add(new Ponto2D(hx, hy));
        for (int k = 0; k <= 8; k++) {
            double f = Math.PI / 2 * k / 8, cx = Math.cos(f), sx = Math.sin(f);
            pts.add(new Ponto2D(hx + larg * (ux * cx + nx * sx), hy + larg * (uy * cx + ny * sx)));
        }
        return pts;
    }

    static List<Ponto2D> cantos(Prisma p) {
        double vx = -p.uy(), vy = p.ux();
        var c = p.c();
        return List.of(new Ponto2D(c.x() - p.ux() * p.a() - vx * p.b(), c.y() - p.uy() * p.a() - vy * p.b()), new Ponto2D(c.x() + p.ux() * p.a() - vx * p.b(), c.y() + p.uy() * p.a() - vy * p.b()),
                new Ponto2D(c.x() + p.ux() * p.a() + vx * p.b(), c.y() + p.uy() * p.a() + vy * p.b()), new Ponto2D(c.x() - p.ux() * p.a() + vx * p.b(), c.y() - p.uy() * p.a() + vy * p.b()));
    }

    /** polígonos convexos se sobrepõem por mais de {@link #TOL} (eixos separantes) */
    static boolean sobrepoe(List<Ponto2D> p, List<Ponto2D> q) {
        for (var poli : List.of(p, q)) {
            for (int i = 0; i < poli.size(); i++) {
                var a = poli.get(i);
                var b = poli.get((i + 1) % poli.size());
                double ex = -(b.y() - a.y()), ey = b.x() - a.x(), n = Math.hypot(ex, ey);
                if (n < 1e-12) {
                    continue;
                }
                ex /= n;
                ey /= n;
                double p0 = Double.MAX_VALUE, p1 = -Double.MAX_VALUE, q0 = Double.MAX_VALUE, q1 = -Double.MAX_VALUE;
                for (var v : p) {
                    double d = v.x() * ex + v.y() * ey;
                    p0 = Math.min(p0, d);
                    p1 = Math.max(p1, d);
                }
                for (var v : q) {
                    double d = v.x() * ex + v.y() * ey;
                    q0 = Math.min(q0, d);
                    q1 = Math.max(q1, d);
                }
                if (Math.min(p1, q1) - Math.max(p0, q0) <= TOL) {
                    return false;
                }
            }
        }
        return true;
    }

    /** quanto um prisma entra no outro em planta, medido na direção de menor penetração */
    private static double penetracao(Prisma p, Prisma o) {
        double[][] eixos = {{p.ux(), p.uy()}, {-p.uy(), p.ux()}, {o.ux(), o.uy()}, {-o.uy(), o.ux()}};
        double min = Double.MAX_VALUE;
        double dx = o.c().x() - p.c().x(), dy = o.c().y() - p.c().y();
        for (var e : eixos) {
            double d = Math.abs(dx * e[0] + dy * e[1]);
            double r1 = p.a() * Math.abs(p.ux() * e[0] + p.uy() * e[1]) + p.b() * Math.abs(-p.uy() * e[0] + p.ux() * e[1]);
            double r2 = o.a() * Math.abs(o.ux() * e[0] + o.uy() * e[1]) + o.b() * Math.abs(-o.uy() * e[0] + o.ux() * e[1]);
            min = Math.min(min, r1 + r2 - d);
        }
        return Math.max(0, min);
    }

    /** primeiro ponto em que o segmento (engrossado pelo raio r) entra no prisma reduzido de {@link #TOL}; null se não entra */
    static Ponto3D cruza(Ponto3D a, Ponto3D b, Prisma p, double r) {
        double vx = -p.uy(), vy = p.ux();
        double[] o = {(a.x() - p.c().x()) * p.ux() + (a.y() - p.c().y()) * p.uy(), (a.x() - p.c().x()) * vx + (a.y() - p.c().y()) * vy, a.z()};
        double[] f = {(b.x() - p.c().x()) * p.ux() + (b.y() - p.c().y()) * p.uy(), (b.x() - p.c().x()) * vx + (b.y() - p.c().y()) * vy, b.z()};
        double[] lo = {-p.a() - r + TOL, -p.b() - r + TOL, p.z0() - r + TOL};
        double[] hi = {p.a() + r - TOL, p.b() + r - TOL, p.z1() + r - TOL};
        double t0 = 0, t1 = 1;
        for (int k = 0; k < 3; k++) {
            double d = f[k] - o[k];
            if (Math.abs(d) < 1e-12) {
                if (o[k] <= lo[k] || o[k] >= hi[k]) {
                    return null;
                }
            } else {
                double ta = (lo[k] - o[k]) / d, tb = (hi[k] - o[k]) / d;
                t0 = Math.max(t0, Math.min(ta, tb));
                t1 = Math.min(t1, Math.max(ta, tb));
                if (t0 >= t1) {
                    return null;
                }
            }
        }
        return new Ponto3D(a.x() + (b.x() - a.x()) * t0, a.y() + (b.y() - a.y()) * t0, a.z() + (b.z() - a.z()) * t0);
    }

    /** {distância, x, y, z do ponto médio entre os mais próximos} de dois segmentos 3D */
    static double[] distanciaSegmentos(Ponto3D p1, Ponto3D q1, Ponto3D p2, Ponto3D q2) {
        double[] d1 = {q1.x() - p1.x(), q1.y() - p1.y(), q1.z() - p1.z()}, d2 = {q2.x() - p2.x(), q2.y() - p2.y(), q2.z() - p2.z()};
        double[] r = {p1.x() - p2.x(), p1.y() - p2.y(), p1.z() - p2.z()};
        double a = dot(d1, d1), e = dot(d2, d2), f = dot(d2, r), s, t;
        if (a < 1e-12 && e < 1e-12) {
            s = t = 0;
        } else if (a < 1e-12) {
            s = 0;
            t = clamp(f / e);
        } else {
            double c = dot(d1, r);
            if (e < 1e-12) {
                t = 0;
                s = clamp(-c / a);
            } else {
                double b = dot(d1, d2), den = a * e - b * b;
                s = den > 1e-12 ? clamp((b * f - c * e) / den) : 0;
                t = (b * s + f) / e;
                if (t < 0) {
                    t = 0;
                    s = clamp(-c / a);
                } else if (t > 1) {
                    t = 1;
                    s = clamp((b - c) / a);
                }
            }
        }
        double[] c1 = {p1.x() + d1[0] * s, p1.y() + d1[1] * s, p1.z() + d1[2] * s}, c2 = {p2.x() + d2[0] * t, p2.y() + d2[1] * t, p2.z() + d2[2] * t};
        return new double[]{Math.sqrt(Math.pow(c1[0] - c2[0], 2) + Math.pow(c1[1] - c2[1], 2) + Math.pow(c1[2] - c2[2], 2)), (c1[0] + c2[0]) / 2, (c1[1] + c2[1]) / 2, (c1[2] + c2[2]) / 2};
    }

    private static double dot(double[] u, double[] v) {
        return u[0] * v[0] + u[1] * v[1] + u[2] * v[2];
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }

    private static boolean compartilhamObjeto(Elemento a, Elemento b) {
        var ids = new java.util.HashSet<String>();
        for (var lado : List.of("de", "para")) {
            ids.add(String.valueOf(ObjetosPrediais.ponta(a, lado).get("id")));
        }
        for (var lado : List.of("de", "para")) {
            if (ids.contains(String.valueOf(ObjetosPrediais.ponta(b, lado).get("id")))) {
                return true;
            }
        }
        return false;
    }

    private static String nomeObjeto(ModeloBim m, Elemento o) {
        return ObjetosPrediais.item(o).map(CatalogoObjetos.Item::nome).orElse("Objeto") + " (" + nome(m, o) + ")";
    }

    private static String nomeConexao(ModeloBim m, Elemento c) {
        var de = ObjetosPrediais.objeto(m, ObjetosPrediais.ponta(c, "de")).map(o -> nomeObjeto(m, o)).orElse("?");
        var para = ObjetosPrediais.objeto(m, ObjetosPrediais.ponta(c, "para")).map(o -> nomeObjeto(m, o)).orElse("?");
        return de + " → " + para;
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
