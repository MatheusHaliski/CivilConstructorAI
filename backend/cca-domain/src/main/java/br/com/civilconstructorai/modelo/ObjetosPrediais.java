package br.com.civilconstructorai.modelo;

import br.com.civilconstructorai.modelo.Enums.TipoElemento;
import br.com.civilconstructorai.notacao.CatalogoObjetos;
import br.com.civilconstructorai.notacao.CatalogoObjetos.Ponto3D;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Objetos da biblioteca e conexões prediais no modelo — porte de {@code objetos.ts} (mesmos algoritmos e
 * arredondamentos, para que prévia e gravação coincidam). Ligação lógica (circuito), conexão física (elemento
 * CONEXAO entre conectores) e representação gráfica (percurso esquemático derivado) ficam separadas.
 */
public final class ObjetosPrediais {

    public record Problema(String codigo, String mensagem) {
    }

    public static final double ROTA_ELETRICA = 0.05, ROTA_VENTILACAO = 0.1, ROTA_AGUA_FRIA = 0.2, ROTA_AGUA_QUENTE = 0.3, ESGOTO_PROFUNDIDADE = 0.25;
    public static final double DECLIVIDADE_PADRAO = 0.02;
    public static final Map<String, String> MATERIAL_PADRAO = Map.of("ELETRICA", "MAT.CONDUTOR.COBRE_PVC_70", "AGUA_FRIA", "MAT.TUBO.PVC_AGUA_FRIA",
            "AGUA_QUENTE", "MAT.TUBO.CPVC_AGUA_QUENTE", "ESGOTO", "MAT.TUBO.PVC_ESGOTO", "VENTILACAO", "MAT.TUBO.PVC_ESGOTO");

    private ObjetosPrediais() {
    }

    static double r3(double v) {
        return Math.round(v * 1000) / 1000.0;
    }

    public static Optional<CatalogoObjetos.Item> item(Elemento o) {
        return CatalogoObjetos.padrao().item(o.texto("item"));
    }

    @SuppressWarnings("unchecked")
    public static double[] dim(Elemento o) {
        var d = o.getProps().get("dim");
        if (d instanceof Map<?, ?> m) {
            return new double[]{num(m.get("l")), num(m.get("p")), num(m.get("a"))};
        }
        return new double[]{0, 0, 0};
    }

    public static void setDim(Elemento o, double l, double p, double a) {
        var m = new java.util.LinkedHashMap<String, Object>();
        m.put("l", r3(l));
        m.put("p", r3(p));
        m.put("a", r3(a));
        o.getProps().put("dim", m);
    }

    private static double num(Object v) {
        return v instanceof Number n ? n.doubleValue() : v == null ? 0 : Double.parseDouble(String.valueOf(v));
    }

    /** pé-direito livre: elevação do pavimento de cima menos a laje mais espessa dele (ou 2,80 m) */
    public static double teto(ModeloBim m, UUID pav) {
        var p = m.pavimento(pav).orElse(null);
        if (p == null) {
            return 2.8;
        }
        var acima = m.pavimentoAcima(pav).orElse(null);
        if (acima == null) {
            return 2.8;
        }
        double laje = m.elementosDo(acima.id()).stream().filter(e -> e.getTipo() == TipoElemento.LAJE).mapToDouble(e -> e.num("h", 0)).max().orElse(0);
        return r3(acima.elevacao() - p.elevacao() - laje);
    }

    public static Ponto3D global(ModeloBim m, Elemento o, Ponto3D local) {
        double r = Math.toRadians(o.num("rotacao", 0)), c = Math.cos(r), s = Math.sin(r);
        var centro = o.ponto("centro");
        double z0 = m.pavimento(o.getPavimentoId()).map(Pavimento::elevacao).orElse(0.0);
        return new Ponto3D(centro.x() + local.x() * c - local.y() * s, centro.y() + local.x() * s + local.y() * c, z0 + o.num("elevacao", 0) + local.z());
    }

    public static Optional<Ponto3D> posicaoConector(ModeloBim m, Elemento o, String conector) {
        var it = item(o).orElse(null);
        if (it == null) {
            return Optional.empty();
        }
        var d = dim(o);
        return it.conector(conector).map(c -> global(m, o, new Ponto3D(c.pos().x() * (d[0] / it.dim().l()), c.pos().y() * (d[1] / it.dim().p()),
                c.pos().z() * (it.dim().a() == 0 ? 1 : d[2] / it.dim().a()))));
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> ponta(Elemento conexao, String lado) {
        var v = conexao.getProps().get(lado);
        return v instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    public static Optional<Elemento> objeto(ModeloBim m, Map<String, Object> ponta) {
        var id = ponta.get("id");
        if (id == null) {
            return Optional.empty();
        }
        try {
            return m.elemento(UUID.fromString(String.valueOf(id))).filter(e -> e.getTipo() == TipoElemento.OBJETO);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** percurso esquemático: mesmo algoritmo de {@code percursoConexao} no TS */
    public static List<Ponto3D> percurso(ModeloBim m, Elemento c) {
        var de = objeto(m, ponta(c, "de")).orElse(null);
        var para = objeto(m, ponta(c, "para")).orElse(null);
        if (de == null || para == null) {
            return List.of();
        }
        var a = posicaoConector(m, de, String.valueOf(ponta(c, "de").get("conector"))).orElse(null);
        var b = posicaoConector(m, para, String.valueOf(ponta(c, "para").get("conector"))).orElse(null);
        if (a == null || b == null) {
            return List.of();
        }
        var pts = new ArrayList<Ponto3D>();
        // tomada → aparelho é o cabo do próprio aparelho (plugue), não eletroduto: linha direta
        if (item(de).flatMap(it -> it.conector(String.valueOf(ponta(c, "de").get("conector")))).map(k -> "TOMADA".equals(k.papel())).orElse(false)) {
            return List.of(p3(a.x(), a.y(), a.z()), p3(b.x(), b.y(), b.z()));
        }
        String sistema = c.texto("sistema");
        double z0pav = m.pavimento(de.getPavimentoId()).map(Pavimento::elevacao).orElse(0.0);
        if ("ESGOTO".equals(sistema)) {
            double z0 = z0pav - ESGOTO_PROFUNDIDADE;
            double i = c.num("declividade", DECLIVIDADE_PADRAO);
            double dx = Math.abs(b.x() - a.x()), dy = Math.abs(b.y() - a.y());
            pts.add(p3(a.x(), a.y(), a.z()));
            pts.add(p3(a.x(), a.y(), z0));
            pts.add(p3(b.x(), a.y(), z0 - i * dx));
            pts.add(p3(b.x(), b.y(), z0 - i * (dx + dy)));
            pts.add(p3(b.x(), b.y(), b.z()));
        } else {
            double faixa = switch (sistema == null ? "" : sistema) {
                case "VENTILACAO" -> ROTA_VENTILACAO;
                case "AGUA_FRIA" -> ROTA_AGUA_FRIA;
                case "AGUA_QUENTE" -> ROTA_AGUA_QUENTE;
                default -> ROTA_ELETRICA;
            };
            double zf = z0pav + teto(m, de.getPavimentoId()) - faixa;
            pts.add(p3(a.x(), a.y(), a.z()));
            pts.add(p3(a.x(), a.y(), zf));
            pts.add(p3(b.x(), a.y(), zf));
            pts.add(p3(b.x(), b.y(), zf));
            pts.add(p3(b.x(), b.y(), b.z()));
        }
        var out = new ArrayList<Ponto3D>();
        for (var p : pts) {
            if (out.isEmpty() || dist(out.getLast(), p) > 1e-6) {
                out.add(p);
            }
        }
        return out;
    }

    private static Ponto3D p3(double x, double y, double z) {
        return new Ponto3D(r3(x), r3(y), r3(z));
    }

    static double dist(Ponto3D a, Ponto3D b) {
        return Math.sqrt(Math.pow(b.x() - a.x(), 2) + Math.pow(b.y() - a.y(), 2) + Math.pow(b.z() - a.z(), 2));
    }

    public static double comprimento(List<Ponto3D> pts) {
        double s = 0;
        for (int k = 1; k < pts.size(); k++) {
            s += dist(pts.get(k - 1), pts.get(k));
        }
        return r3(s);
    }

    public static String rotuloSistema(String s) {
        return switch (s == null ? "" : s) {
            case "ELETRICA" -> "elétrica";
            case "AGUA_FRIA" -> "água fria";
            case "AGUA_QUENTE" -> "água quente";
            case "ESGOTO" -> "esgoto";
            case "VENTILACAO" -> "ventilação";
            default -> String.valueOf(s);
        };
    }

    private static String nome(Elemento o) {
        return item(o).map(CatalogoObjetos.Item::nome).orElse(o.texto("item"));
    }

    /** regras de compatibilidade (iguais a {@code problemasConexao} no TS) */
    public static List<Problema> problemasConexao(ModeloBim m, Elemento c) {
        var out = new ArrayList<Problema>();
        var pDe = ponta(c, "de");
        var pPara = ponta(c, "para");
        var de = objeto(m, pDe).orElse(null);
        var para = objeto(m, pPara).orElse(null);
        if (de == null || para == null) {
            return List.of(new Problema("CON-OBJETO", "A conexão precisa ligar dois objetos existentes."));
        }
        if (de.getId().equals(para.getId())) {
            out.add(new Problema("CON-MESMO", "Um objeto não pode ser ligado a ele mesmo."));
        }
        var ide = item(de).orElse(null);
        var ipara = item(para).orElse(null);
        var cde = ide == null ? null : ide.conector(String.valueOf(pDe.get("conector"))).orElse(null);
        var cpara = ipara == null ? null : ipara.conector(String.valueOf(pPara.get("conector"))).orElse(null);
        if (cde == null) {
            out.add(new Problema("CON-CONECTOR", nome(de) + " não tem o conector " + pDe.get("conector")
                    + (ide != null && ide.conectores().isEmpty() ? " (este objeto não tem conexões elétricas nem hidráulicas)" : "") + "."));
        }
        if (cpara == null) {
            out.add(new Problema("CON-CONECTOR", nome(para) + " não tem o conector " + pPara.get("conector")
                    + (ipara != null && ipara.conectores().isEmpty() ? " (este objeto não tem conexões elétricas nem hidráulicas)" : "") + "."));
        }
        if (cde == null || cpara == null) {
            return out;
        }
        if (!cde.sistema().equals(cpara.sistema()) || !cde.sistema().equals(c.texto("sistema"))) {
            out.add(new Problema("CON-SISTEMA", "Sistemas incompatíveis: " + rotuloSistema(cde.sistema()) + " (" + ide.nome() + ") com " + rotuloSistema(cpara.sistema()) + " (" + ipara.nome() + ")."));
        }
        if (!"SAI".equals(cde.fluxo()) || !"ENTRA".equals(cpara.fluxo())) {
            out.add(new Problema("CON-FLUXO", "A ligação vai de uma saída para uma entrada: " + ide.nome() + "." + cde.id() + " é " + ("SAI".equals(cde.fluxo()) ? "saída" : "entrada")
                    + " e " + ipara.nome() + "." + cpara.id() + " é " + ("SAI".equals(cpara.fluxo()) ? "saída" : "entrada") + "."));
        }
        if ("ELETRICA".equals(cde.sistema()) && "ELETRICA".equals(cpara.sistema())) {
            var td = cde.tensoes();
            var tp = cpara.tensoes();
            if (!td.isEmpty() && !tp.isEmpty() && tp.stream().noneMatch(td::contains)) {
                out.add(new Problema("CON-TENSAO", "Tensão incompatível: " + ipara.nome() + " é " + juntar(tp) + " V e " + ide.nome() + " fornece " + juntar(td) + " V."));
            }
            Double potencia = para.getProps().get("potenciaW") instanceof Number n ? n.doubleValue() : cpara.potenciaW();
            if (cde.correnteMaxA() != null && potencia != null) {
                double v = !tp.isEmpty() ? tp.getFirst() : !td.isEmpty() ? td.getFirst() : 127;
                double ib = potencia / v;
                if (ib > cde.correnteMaxA() + 1e-9) {
                    out.add(new Problema("CON-CORRENTE", String.format(Locale.ROOT, "%s (%s W em %s V → %.1f A) excede a %s (%s A). Ligue em circuito próprio a partir do quadro.",
                            ipara.nome(), fmt(potencia), fmt(v), ib, ide.nome(), fmt(cde.correnteMaxA()))));
                }
            }
        }
        for (var lado : List.of(new Object[]{de, cde, ide}, new Object[]{para, cpara, ipara})) {
            var o = (Elemento) lado[0];
            var con = (CatalogoObjetos.Conector) lado[1];
            var it = (CatalogoObjetos.Item) lado[2];
            long usadas = m.elementos().stream().filter(e -> e.getTipo() == TipoElemento.CONEXAO && !e.getId().equals(c.getId())
                    && (liga(e, "de", o, con) || liga(e, "para", o, con))).count();
            if (usadas + 1 > con.maxConexoes()) {
                out.add(new Problema("CON-OCUPADO", "O conector " + con.id() + " de " + it.nome() + " já está ligado (" + usadas + " de " + con.maxConexoes() + ")."));
            }
        }
        return out;
    }

    private static boolean liga(Elemento conexao, String lado, Elemento o, CatalogoObjetos.Conector con) {
        var p = ponta(conexao, lado);
        return o.getId().toString().equals(String.valueOf(p.get("id"))) && con.id().equals(String.valueOf(p.get("conector")));
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    private static String juntar(List<Double> ts) {
        return String.join("/", ts.stream().map(ObjetosPrediais::fmt).toList());
    }

    /** regras de dimensão e montagem do objeto (iguais a {@code problemasObjeto} no TS) */
    public static List<Problema> problemasObjeto(ModeloBim m, Elemento o) {
        var it = item(o).orElse(null);
        if (it == null) {
            return List.of(new Problema("OBJ-ITEM", "Objeto \"" + o.texto("item") + "\" não existe na biblioteca."));
        }
        var out = new ArrayList<Problema>();
        var d = dim(o);
        if (!(d[0] > 0 && d[1] > 0 && d[2] > 0)) {
            out.add(new Problema("OBJ-DIM", "Dimensões do objeto precisam ser positivas."));
        }
        if (it.alturaVariavel() != null) {
            var av = it.alturaVariavel();
            if (d[2] < av[0] - 1e-9 || d[2] > av[1] + 1e-9) {
                out.add(new Problema("OBJ-ALTURA", it.nome() + ": altura entre " + fmt(av[0]) + " e " + fmt(av[1]) + " m (informado " + fmt(d[2]) + " m)."));
            }
            double k = d[2] / it.dim().a();
            if (Math.abs(d[0] - it.dim().l() * k) > 0.002 || Math.abs(d[1] - it.dim().p() * k) > 0.002) {
                out.add(new Problema("OBJ-PROPORCAO", it.nome() + ": a escala é uniforme (altura define as proporções); não deforme a figura."));
            }
        } else if (!it.redimensionavel() && (Math.abs(d[0] - it.dim().l()) > 0.001 || Math.abs(d[1] - it.dim().p()) > 0.001 || Math.abs(d[2] - it.dim().a()) > 0.001)) {
            out.add(new Problema("OBJ-DIM", it.nome() + " tem dimensões de produto (" + fmt(it.dim().l()) + " × " + fmt(it.dim().p()) + " × " + fmt(it.dim().a())
                    + " m) e não pode ser deformado. Escolha outro modelo da biblioteca."));
        }
        if ("PAREDE".equals(it.montagem())) {
            var host = o.texto("paredeId") == null ? null : m.elemento(o.uuid("paredeId")).filter(e -> e.getTipo() == TipoElemento.PAREDE).orElse(null);
            if (host == null) {
                out.add(new Problema("OBJ-HOSPEDEIRO", it.nome() + " é fixado em parede: posicione sobre uma parede."));
            }
        }
        double elev = o.num("elevacao", 0);
        if (elev < -2 || elev > 12) {
            out.add(new Problema("OBJ-ELEVACAO", "Elevação do objeto fora de −2 a 12 m em relação ao piso."));
        }
        return out;
    }
}
