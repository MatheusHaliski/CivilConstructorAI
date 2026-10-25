package br.com.civilconstructorai.modelo;

import br.com.civilconstructorai.comum.ExcecaoDominio;
import br.com.civilconstructorai.comum.Ids;
import br.com.civilconstructorai.modelo.Eixo.Direcao;
import br.com.civilconstructorai.modelo.Enums.Camada;
import br.com.civilconstructorai.modelo.Enums.OrigemComando;
import br.com.civilconstructorai.modelo.Enums.TipoElemento;
import br.com.civilconstructorai.notacao.Geo;
import br.com.civilconstructorai.notacao.ParserVi;
import br.com.civilconstructorai.notacao.Ponto2D;
import br.com.civilconstructorai.notacao.TabelaA;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Modelo BIM em memória: fonte da verdade do desenho de uma obra. Porte de packages/notacao-vi/src/modelo.ts.
 * {@link #executar} aplica uma função da Tabela B num rascunho e devolve o diff; quem orquestra auditoria,
 * versão e persistência é o {@code ModeloService}. {@code aplicarAuditado} é o atalho usado pelos testes de
 * domínio (audita com o auditor local e grava snapshot para desfazer).
 */
public class ModeloBim {

    private long versao;
    private final List<Pavimento> pavimentos = new ArrayList<>();
    private final List<Eixo> eixos = new ArrayList<>();
    private final List<Elemento> elementos = new ArrayList<>();
    private final List<Estado> pilhaDesfazer = new ArrayList<>();
    private final List<Estado> pilhaRefazer = new ArrayList<>();

    public record Estado(long versao, List<Pavimento> pavimentos, List<Eixo> eixos, List<Elemento> elementos) {
    }

    public ModeloBim() {
        pavimentos.add(new Pavimento(Ids.novo(), "Térreo", 0, Enums.TipoPavimento.TERREO));
    }

    /**
     * Térreo inicial da obra com id determinístico: a leitura de um modelo ainda não gravado e a criação do modelo na
     * primeira escrita devolvem o mesmo pavimento, então o id lido pelo cliente continua válido no primeiro comando.
     */
    public static Pavimento terreoInicial(UUID obraId) {
        var id = UUID.nameUUIDFromBytes(("cca:terreo:" + obraId).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new Pavimento(id, "Térreo", 0, Enums.TipoPavimento.TERREO);
    }

    public static Estado estadoInicial(UUID obraId) {
        return new Estado(0, List.of(terreoInicial(obraId)), List.of(), List.of());
    }

    public ModeloBim(Estado estado) {
        restaurar(estado);
        if (pavimentos.isEmpty()) {
            pavimentos.add(new Pavimento(Ids.novo(), "Térreo", 0, Enums.TipoPavimento.TERREO));
        }
    }

    public Estado estado() {
        return new Estado(versao, List.copyOf(pavimentos), List.copyOf(eixos), elementos.stream().map(Elemento::copia).toList());
    }

    private void restaurar(Estado e) {
        versao = e.versao();
        pavimentos.clear();
        pavimentos.addAll(e.pavimentos());
        eixos.clear();
        eixos.addAll(e.eixos());
        elementos.clear();
        e.elementos().forEach(x -> elementos.add(x.copia()));
    }

    public ModeloBim copia() {
        return new ModeloBim(estado());
    }

    // ------------------------------------------------------------------ consultas
    public long versao() { return versao; }
    public void setVersao(long v) { this.versao = v; }
    public List<Pavimento> pavimentos() { return pavimentos; }
    public List<Eixo> eixos() { return eixos; }
    public List<Elemento> elementos() { return elementos; }

    public Optional<Elemento> elemento(UUID id) {
        return id == null ? Optional.empty() : elementos.stream().filter(e -> e.getId().equals(id)).findFirst();
    }

    public Optional<Pavimento> pavimento(UUID id) {
        return id == null ? Optional.empty() : pavimentos.stream().filter(p -> p.id().equals(id)).findFirst();
    }

    public List<Pavimento> pavimentosOrdenados() {
        return pavimentos.stream().sorted(Comparator.comparingDouble(Pavimento::elevacao)).toList();
    }

    public Pavimento pavimentoBase() {
        return pavimentosOrdenados().stream().filter(p -> p.elevacao() >= 0).findFirst().orElse(pavimentosOrdenados().get(0));
    }

    public Optional<Pavimento> pavimentoAcima(UUID id) {
        var ord = pavimentosOrdenados();
        int i = -1;
        for (int k = 0; k < ord.size(); k++) {
            if (ord.get(k).id().equals(id)) {
                i = k;
            }
        }
        return i >= 0 && i + 1 < ord.size() ? Optional.of(ord.get(i + 1)) : Optional.empty();
    }

    public List<Elemento> elementosDo(UUID pavimentoId) {
        return elementos.stream().filter(e -> e.getPavimentoId().equals(pavimentoId)).toList();
    }

    public record Intersecao(String nome, Ponto2D p) {
    }

    /** interseções de eixos (snap EIX) */
    public List<Intersecao> intersecoesDeEixos() {
        var out = new ArrayList<Intersecao>();
        for (var ex : eixos) {
            if (ex.direcao() != Direcao.X) {
                continue;
            }
            for (var ey : eixos) {
                if (ey.direcao() == Direcao.Y) {
                    out.add(new Intersecao(ex.nome() + "/" + ey.nome(), Ponto2D.de(ex.posicao(), ey.posicao())));
                }
            }
        }
        return out;
    }

    public Optional<Geo.Caixa> caixaDosEixos() {
        var xs = eixos.stream().filter(e -> e.direcao() == Direcao.X).map(Eixo::posicao).toList();
        var ys = eixos.stream().filter(e -> e.direcao() == Direcao.Y).map(Eixo::posicao).toList();
        if (xs.size() < 2 || ys.size() < 2) {
            return Optional.empty();
        }
        return Optional.of(new Geo.Caixa(new Ponto2D(xs.stream().min(Double::compare).get(), ys.stream().min(Double::compare).get()),
                new Ponto2D(xs.stream().max(Double::compare).get(), ys.stream().max(Double::compare).get())));
    }

    public String rotuloAutomatico(Elemento e) {
        var prefixo = switch (e.getTipo()) {
            case PILAR -> "P"; case VIGA -> "V"; case LAJE -> "L"; case PAREDE -> "PAR"; case PORTA -> "PT"; case JANELA -> "J";
            case SAPATA -> "S"; case BLOCO -> "B"; case RADIER -> "R"; default -> e.getTipo().name().substring(0, 2);
        };
        int idx = 0, meu = 0;
        for (var x : elementos) {
            if (x.getTipo() == e.getTipo() && x.getPavimentoId().equals(e.getPavimentoId())) {
                idx++;
                if (x.getId().equals(e.getId())) {
                    meu = idx;
                }
            }
        }
        return prefixo + (meu == 0 ? idx + 1 : meu);
    }

    public Ponto2D pontoRepresentativo(Elemento e) {
        if (e.ponto("centro") != null) {
            return e.ponto("centro");
        }
        if (e.temSegmento()) {
            return e.ponto("p1").medio(e.ponto("p2"));
        }
        if (!e.pontos("contorno").isEmpty()) {
            return e.pontos("contorno").get(0);
        }
        if (!e.pontos("pontos").isEmpty()) {
            return e.pontos("pontos").get(0);
        }
        if (e.ponto("ponto") != null) {
            return e.ponto("ponto");
        }
        if (e.ponto("p") != null) {
            return e.ponto("p");
        }
        if (e.getTipo() == TipoElemento.PORTA || e.getTipo() == TipoElemento.JANELA) {
            var w = elemento(e.uuid("paredeId")).orElse(null);
            if (w != null) {
                var a = w.ponto("p1");
                var b = w.ponto("p2");
                return a.mais(b.menos(a).vezes(e.num("posicao", 0.5)));
            }
        }
        return new Ponto2D(0, 0);
    }

    // ------------------------------------------------------------------ desfazer / refazer
    public void guardarParaDesfazer(Estado antes) {
        pilhaDesfazer.add(antes);
        pilhaRefazer.clear();
    }

    public Optional<DiffModelo> desfazer() {
        if (pilhaDesfazer.isEmpty()) {
            return Optional.empty();
        }
        var snap = pilhaDesfazer.remove(pilhaDesfazer.size() - 1);
        pilhaRefazer.add(estado());
        return Optional.of(restaurarComDiff(snap));
    }

    public Optional<DiffModelo> refazer() {
        if (pilhaRefazer.isEmpty()) {
            return Optional.empty();
        }
        var snap = pilhaRefazer.remove(pilhaRefazer.size() - 1);
        pilhaDesfazer.add(estado());
        return Optional.of(restaurarComDiff(snap));
    }

    /** restaura um estado anterior e devolve o diff em relação ao atual (versão sempre avança) */
    public DiffModelo restaurarComDiff(Estado alvo) {
        var antesIds = new HashSet<UUID>();
        elementos.forEach(e -> antesIds.add(e.getId()));
        var novaVersao = versao + 1;
        restaurar(alvo);
        var diff = new DiffModelo();
        for (var e : elementos) {
            (antesIds.contains(e.getId()) ? diff.alterados : diff.criados).add(e);
        }
        var depois = new HashSet<UUID>();
        elementos.forEach(e -> depois.add(e.getId()));
        antesIds.stream().filter(id -> !depois.contains(id)).forEach(diff.removidos::add);
        diff.pavimentosCriados.addAll(pavimentos);
        diff.eixosCriados.addAll(eixos);
        versao = novaVersao;
        diff.versaoNova = versao;
        return diff;
    }

    // ------------------------------------------------------------------ execução das funções da Tabela B
    private static ExcecaoDominio.Validacao erro(String codigo, String msg) {
        return new ExcecaoDominio.Validacao(List.of(new ExcecaoDominio.Validacao.Erro(codigo, msg)));
    }

    private static Ponto2D pt(Map<String, Object> a, String nome) {
        var v = a.get(nome);
        if (v == null) {
            throw erro("VI-002", "Argumento obrigatório \"" + nome + "\" ausente ou não é ponto.");
        }
        try {
            return Elemento.aPonto(v);
        } catch (IllegalArgumentException e) {
            throw erro("VI-003", "Argumento \"" + nome + "\" não é ponto: " + v);
        }
    }

    private static Ponto2D ptOpt(Map<String, Object> a, String... nomes) {
        for (var n : nomes) {
            if (a.get(n) != null) {
                return pt(a, n);
            }
        }
        return null;
    }

    private static double num(Map<String, Object> a, String nome, Double padrao) {
        var v = a.get(nome);
        if (v == null || "".equals(v)) {
            if (padrao != null) {
                return padrao;
            }
            throw erro("VI-002", "Argumento obrigatório \"" + nome + "\" ausente.");
        }
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(v).replace(',', '.'));
        } catch (NumberFormatException e) {
            throw erro("VI-003", "Argumento \"" + nome + "\" não é número.");
        }
    }

    private static String str(Map<String, Object> a, String nome, String padrao) {
        var v = a.get(nome);
        return v == null || "".equals(v) ? padrao : String.valueOf(v);
    }

    private static boolean bool(Map<String, Object> a, String nome, boolean padrao) {
        var v = a.get(nome);
        if (v instanceof Boolean b) {
            return b;
        }
        if (v instanceof String s) {
            return s.equalsIgnoreCase("S") || s.equalsIgnoreCase("true");
        }
        return padrao;
    }

    @SuppressWarnings("unchecked")
    private static List<Ponto2D> pontos(Map<String, Object> a, String... nomes) {
        for (var n : nomes) {
            var v = a.get(n);
            if (v instanceof List<?> l) {
                var out = new ArrayList<Ponto2D>();
                for (var o : l) {
                    out.add(Elemento.aPonto(o));
                }
                return out;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<Elemento> selecao(Map<String, Object> a) {
        var v = a.get("sel");
        if (v == null) {
            return List.of();
        }
        var out = new ArrayList<Elemento>();
        for (var o : (List<Object>) v) {
            elemento(o instanceof UUID u ? u : UUID.fromString(String.valueOf(o))).ifPresent(out::add);
        }
        return out;
    }

    private Elemento novo(ComandoVi cmd, UUID pav, TipoElemento tipo, Camada camada, Map<String, Object> props) {
        return Elemento.novo(tipo, pav, camada, cmd.origem(), props);
    }

    private static Map<String, Object> props(Object... kv) {
        var m = new LinkedHashMap<String, Object>();
        for (int i = 0; i < kv.length; i += 2) {
            if (kv[i + 1] != null) {
                m.put((String) kv[i], kv[i + 1]);
            }
        }
        return m;
    }

    /** Executa a função no estado atual (sem auditar nem versionar). */
    /** {@code mat=MAT.ESTRUTURAL.CONC_FCK30} (código do catálogo) vale o mesmo que {@code mat=MAT_CONC_FCK30}. */
    private static Map<String, Object> comMaterialNormalizado(Map<String, Object> args) {
        if (!(args.get("mat") instanceof String mat)) {
            return args;
        }
        var copia = new java.util.LinkedHashMap<>(args);
        copia.put("mat", TabelaA.normalizarId(mat));
        return copia;
    }

    public DiffModelo executar(ComandoVi cmd, UUID pavimentoId) {
        var diff = new DiffModelo();
        diff.versaoNova = versao + 1;
        var a = comMaterialNormalizado(cmd.args());
        var pav = pavimentoId != null ? pavimentoId : a.get("pav") != null ? UUID.fromString(String.valueOf(a.get("pav"))) : pavimentoBase().id();
        if (pavimento(pav).isEmpty()) {
            throw erro("VI-003", "Pavimento inexistente.");
        }
        if (cmd.origem() == OrigemComando.IA) {
            diff.geradoPorIa = true;
        }
        switch (cmd.codigo()) {
            case "PAREDE" -> criar(diff, novo(cmd, pav, TipoElemento.PAREDE, Camada.arquitetura, props(
                    "p1", pt(a, "p1"), "p2", pt(a, "p2"), "espessura", num(a, "esp", 0.15), "altura", num(a, "h", 2.8),
                    "materialId", str(a, "mat", "MAT_ALV_BLOCO14"), "externa", bool(a, "externa", true))));
            case "PILAR" -> {
                var topo = pavimentoAcima(pav).map(Pavimento::id).orElse(pav);
                criar(diff, novo(cmd, pav, TipoElemento.PILAR, Camada.estrutura, props(
                        "centro", ptOpt(a, "centro", "p") != null ? ptOpt(a, "centro", "p") : pt(a, "centro"),
                        "b", num(a, "b", 0.19), "h", num(a, "h", 0.4), "secao", "CIRCULAR".equals(a.get("secao")) ? "CIRCULAR" : "RETANGULAR",
                        "materialId", str(a, "mat", "MAT_CONC_FCK30"), "nivelBaseId", pav, "nivelTopoId", topo,
                        "baseOffset", num(a, "baseOffset", 0.0), "topoOffset", num(a, "topoOffset", 0.0),
                        "continuo", bool(a, "continuo", false), "rotacao", num(a, "rot", 0.0))));
            }
            case "VIGA" -> criar(diff, novo(cmd, pav, TipoElemento.VIGA, Camada.estrutura, props(
                    "p1", pt(a, "p1"), "p2", pt(a, "p2"), "b", num(a, "b", 0.12), "h", num(a, "h", 0.4), "materialId", str(a, "mat", "MAT_CONC_FCK30"))));
            case "CONTRAVENTAMENTO" -> criar(diff, novo(cmd, pav, TipoElemento.CONTRAVENTAMENTO, Camada.estrutura, props(
                    "p1", pt(a, "p1"), "p2", pt(a, "p2"), "perfil", str(a, "perfil", "L 50x5"), "materialId", "MAT_ACO_PERFIL_W")));
            case "LAJE", "REGIAO" -> {
                var contorno = pontos(a, "contorno", "pontos");
                if (contorno == null || contorno.size() < 3) {
                    throw erro("VI-002", "Laje precisa de contorno com 3+ vértices.");
                }
                criar(diff, novo(cmd, pav, TipoElemento.LAJE, Camada.estrutura, props("contorno", new ArrayList<Object>(contorno), "h", num(a, "h", 0.12), "materialId", str(a, "mat", "MAT_CONC_FCK30"))));
            }
            case "PORTA", "JANELA" -> {
                var parede = encontrarParede(a.get("parede") == null ? null : UUID.fromString(String.valueOf(a.get("parede"))), ptOpt(a, "pos"), pav);
                if (parede == null) {
                    throw erro("VI-002", "Abertura precisa de uma parede hospedeira (clique sobre a parede).");
                }
                double posicao = a.get("posicao") instanceof Number n ? n.doubleValue() : Geo.projetarNoSegmento(pt(a, "pos"), parede.ponto("p1"), parede.ponto("p2")).t();
                boolean porta = cmd.codigo().equals("PORTA");
                criar(diff, novo(cmd, pav, porta ? TipoElemento.PORTA : TipoElemento.JANELA, Camada.arquitetura, props(
                        "paredeId", parede.getId(), "posicao", posicao, "largura", num(a, "larg", porta ? 0.8 : 1.2), "altura", num(a, "alt", porta ? 2.1 : 1.2),
                        "peitoril", porta ? 0.0 : num(a, "peitoril", 1.0), "materialId", porta ? "MAT_MADEIRA_PORTA" : "MAT_VIDRO_TEMP8")));
            }
            case "AMBIENTE" -> criar(diff, novo(cmd, pav, TipoElemento.AMBIENTE, Camada.arquitetura, props("ponto", pt(a, "ponto"), "nome", str(a, "nome", "Ambiente"))));
            case "LINHA", "PLANO_REF", "LINHA_DETALHE" -> criar(diff, novo(cmd, pav,
                    cmd.codigo().equals("PLANO_REF") ? TipoElemento.PLANO_REF : TipoElemento.LINHA,
                    cmd.codigo().equals("LINHA_DETALHE") ? Camada.anotacao : Camada.desenho, props("p1", pt(a, "p1"), "p2", pt(a, "p2"))));
            case "RAIO_CONSTRUCAO", "LINHA_CONSTRUCAO" -> {
                var p = ptOpt(a, "p", "origem");
                if (p == null) {
                    throw erro("VI-002", "Argumento obrigatório \"p\" ausente.");
                }
                var d = pt(a, "direcao");
                var v = d.menos(p);
                double l = Math.hypot(v.x(), v.y());
                double k = 1000 / (l == 0 ? 1 : l);
                var e = novo(cmd, pav, TipoElemento.LINHA, Camada.desenho, props(
                        "p1", cmd.codigo().equals("RAIO_CONSTRUCAO") ? p : Ponto2D.de(p.x() - v.x() * k, p.y() - v.y() * k),
                        "p2", Ponto2D.de(p.x() + v.x() * k, p.y() + v.y() * k)));
                e.setRotulo("construcao");
                criar(diff, e);
            }
            case "POLILINHA", "SPLINE" -> {
                var pts = pontos(a, "pontos");
                if (pts == null || pts.size() < 2) {
                    throw erro("VI-002", "Polilinha precisa de 2+ pontos.");
                }
                criar(diff, novo(cmd, pav, TipoElemento.POLILINHA, Camada.desenho, props("pontos", new ArrayList<Object>(pts), "fechada", bool(a, "fechar", bool(a, "fechada", false)))));
            }
            case "CIRCULO" -> criar(diff, novo(cmd, pav, TipoElemento.CIRCULO, Camada.desenho, props("centro", pt(a, "centro"), "raio", num(a, "raio", 1.0))));
            case "ANEL" -> {
                var e = novo(cmd, pav, TipoElemento.CIRCULO, Camada.desenho, props("centro", pt(a, "centro"), "raio", num(a, "re", 0.3), "raioInterno", num(a, "ri", 0.2)));
                e.setRotulo("anel");
                criar(diff, e);
            }
            case "RETANGULO" -> criar(diff, novo(cmd, pav, TipoElemento.RETANGULO, Camada.desenho, props("p1", pt(a, "p1"), "p2", pt(a, "p2"))));
            case "ARCO" -> criar(diff, novo(cmd, pav, TipoElemento.ARCO, Camada.desenho, props("p1", pt(a, "p1"), "p2", pt(a, "p2"), "p3", pt(a, "p3"))));
            case "POLIGONO" -> {
                var c = pt(a, "centro");
                int nl = Math.max(3, (int) Math.round(num(a, "n", 6.0)));
                double r = num(a, "raio", 1.0);
                var pts = new ArrayList<Object>();
                for (int i = 0; i < nl; i++) {
                    pts.add(Ponto2D.de(c.x() + r * Math.cos(2 * Math.PI * i / nl), c.y() + r * Math.sin(2 * Math.PI * i / nl)));
                }
                criar(diff, novo(cmd, pav, TipoElemento.POLILINHA, Camada.desenho, props("pontos", pts, "fechada", true)));
            }
            case "ELIPSE" -> {
                var c = pt(a, "centro");
                double ea = num(a, "a", 2.0), eb = num(a, "b", 1.0);
                var pts = new ArrayList<Object>();
                for (int i = 0; i < 36; i++) {
                    pts.add(Ponto2D.de(c.x() + ea * Math.cos(Math.PI * i / 18), c.y() + eb * Math.sin(Math.PI * i / 18)));
                }
                criar(diff, novo(cmd, pav, TipoElemento.POLILINHA, Camada.desenho, props("pontos", pts, "fechada", true)));
            }
            case "PONTO" -> {
                var e = novo(cmd, pav, TipoElemento.PONTO, Camada.desenho, props("p", pt(a, "p")));
                e.setRotulo(str(a, "rotulo", null));
                criar(diff, e);
            }
            case "EIXO" -> {
                var p1 = pt(a, "p1");
                var p2 = pt(a, "p2");
                var direcao = Math.abs(p2.x() - p1.x()) < Math.abs(p2.y() - p1.y()) ? Direcao.X : Direcao.Y;
                var nome = str(a, "nome", proximoNomeEixo(direcao));
                if (eixos.stream().anyMatch(e -> e.nome().equals(nome))) {
                    throw erro("VI-003", "Já existe um eixo chamado " + nome + ".");
                }
                var eixo = new Eixo(Ids.novo(), nome, direcao, direcao == Direcao.X ? p1.x() : p1.y(),
                        direcao == Direcao.X ? Math.min(p1.y(), p2.y()) : Math.min(p1.x(), p2.x()),
                        direcao == Direcao.X ? Math.max(p1.y(), p2.y()) : Math.max(p1.x(), p2.x()), 1.0);
                eixos.add(eixo);
                diff.eixosCriados.add(eixo);
            }
            case "MALHA" -> malha(a, diff);
            case "PAVIMENTO" -> {
                double z = num(a, a.containsKey("z") ? "z" : "elevacao", null);
                if (pavimentos.stream().anyMatch(p -> Math.abs(p.elevacao() - z) < 0.01)) {
                    throw erro("VI-003", "Já existe pavimento na elevação " + z + " m.");
                }
                // RF03 CA-04: pé-direito entre 2,20 e 12,00 m (acima do solo); fora disso só com confirmação explícita
                if (z >= 0 && !bool(a, "confirmar", false)) {
                    var abaixo = pavimentos.stream().filter(p -> p.elevacao() >= 0 && p.elevacao() < z).mapToDouble(Pavimento::elevacao).max();
                    var acima = pavimentos.stream().filter(p -> p.elevacao() > z).mapToDouble(Pavimento::elevacao).min();
                    for (var vizinho : new java.util.OptionalDouble[] {abaixo, acima}) {
                        if (vizinho.isPresent()) {
                            double pe = Math.abs(z - vizinho.getAsDouble());
                            if (pe < 2.20 - 1e-9 || pe > 12.00 + 1e-9) {
                                throw erro("VI-003", String.format(java.util.Locale.of("pt", "BR"),
                                        "Pé-direito de %.2f m fora do intervalo 2,20–12,00 m. Se for intencional (mezanino, reservatório), confirme com confirmar=true.", pe));
                            }
                        }
                    }
                }
                var novoPav = new Pavimento(Ids.novo(), str(a, "nome", "Pavimento " + pavimentos.size()), z, Pavimento.tipoPara(z));
                pavimentos.add(novoPav);
                diff.pavimentosCriados.add(novoPav);
                // pilares do pavimento imediatamente abaixo passam a ter este como topo (Disciplina Z)
                var abaixo = pavimentosOrdenados().stream().filter(p -> p.elevacao() < z).reduce((x, y) -> y).orElse(null);
                if (abaixo != null) {
                    for (var e : elementos) {
                        if (e.getTipo() == TipoElemento.PILAR && abaixo.id().equals(e.uuid("nivelBaseId")) && abaixo.id().equals(e.uuid("nivelTopoId"))) {
                            e.getProps().put("nivelTopoId", novoPav.id());
                            diff.alterados.add(e);
                        }
                    }
                }
            }
            case "SAPATA", "BLOCO", "RADIER", "FUNDACAO" -> {
                var tipo = TipoElemento.valueOf(cmd.codigo().equals("FUNDACAO") ? str(a, "tipo", "SAPATA") : cmd.codigo());
                var pilar = selecao(a).stream().filter(e -> e.getTipo() == TipoElemento.PILAR).findFirst()
                        .or(() -> a.get("pilar") == null ? Optional.empty() : elemento(UUID.fromString(String.valueOf(a.get("pilar"))))).orElse(null);
                var centro = pilar != null ? pilar.ponto("centro") : pt(a, "centro");
                criar(diff, novo(cmd, pav, tipo, Camada.fundacao, props("pilarId", pilar == null ? null : pilar.getId(), "centro", centro,
                        "bx", num(a, "bx", 1.5), "by", num(a, "by", 1.5), "h", num(a, "h", 0.5),
                        "estacas", a.get("estacas") == null ? null : num(a, "estacas", null), "diametroEstaca", a.get("diam") == null ? null : num(a, "diam", null),
                        "materialId", "MAT_CONC_FCK25")));
            }
            case "COTA_LINEAR", "COTA_ALINHADA", "COTA_NIVEL" -> {
                var p1 = ptOpt(a, "p1", "p");
                var p2 = ptOpt(a, "p2", "p");
                if (p1 == null || p2 == null) {
                    throw erro("VI-002", "Cota precisa de p1 e p2 (ou p).");
                }
                criar(diff, novo(cmd, pav, TipoElemento.COTA, Camada.anotacao, props("p1", p1, "p2", p2, "afastamento", num(a, "afast", 1.0),
                        "estilo", cmd.codigo().equals("COTA_LINEAR") ? "LINEAR" : cmd.codigo().equals("COTA_NIVEL") ? "NIVEL" : "ALINHADA")));
            }
            case "TEXTO" -> criar(diff, novo(cmd, pav, TipoElemento.TEXTO, Camada.anotacao, props("p", pt(a, "p"), "texto", str(a, "texto", ""), "altura", num(a, "altura", 0.25))));
            case "ETIQUETA" -> {
                for (var e : selecao(a)) {
                    criar(diff, novo(cmd, pav, TipoElemento.ETIQUETA, Camada.anotacao, props("p", pontoRepresentativo(e), "texto", e.getRotulo() != null ? e.getRotulo() : rotuloAutomatico(e), "altura", 0.25, "alvoId", e.getId())));
                }
            }
            case "HACHURA" -> {
                var alvo = selecao(a).stream().findFirst().orElse(null);
                List<Ponto2D> contorno = alvo == null ? null : !alvo.pontos("contorno").isEmpty() ? alvo.pontos("contorno") : alvo.getTipo() == TipoElemento.POLILINHA ? alvo.pontos("pontos") : null;
                if (contorno == null) {
                    throw erro("VI-002", "Hachura precisa de um contorno fechado selecionado.");
                }
                criar(diff, novo(cmd, pav, TipoElemento.HACHURA, Camada.anotacao, props("contorno", new ArrayList<Object>(contorno), "padrao", str(a, "padrao", "CONCRETO"))));
            }
            // ------------------------------------------------------------ modificação
            case "MOVER", "COPIAR" -> {
                var d = pt(a, "destino").menos(pt(a, "base"));
                for (var e : selecao(a)) {
                    var alvo = cmd.codigo().equals("COPIAR") ? copiaNova(e, cmd) : e;
                    alvo.mapearPontos(p -> p.mais(d));
                    if (alvo == e) {
                        diff.alterados.add(e);
                    } else {
                        criar(diff, alvo);
                    }
                }
            }
            case "ROTACIONAR" -> {
                var b = pt(a, "base");
                double ang = num(a, "ang", 90.0);
                for (var e : selecao(a)) {
                    e.mapearPontos(p -> Geo.rotacionar(p, b, ang));
                    if (e.getTipo() == TipoElemento.PILAR) {
                        e.setNum("rotacao", (e.num("rotacao", 0) + ang) % 360);
                    }
                    diff.alterados.add(e);
                }
            }
            case "ESPELHAR" -> {
                var p1 = pt(a, "p1");
                var p2 = pt(a, "p2");
                boolean apagar = bool(a, "apagar", false);
                for (var e : selecao(a)) {
                    var alvo = apagar ? e : copiaNova(e, cmd);
                    alvo.mapearPontos(p -> Geo.espelhar(p, p1, p2));
                    if (alvo == e) {
                        diff.alterados.add(e);
                    } else {
                        criar(diff, alvo);
                    }
                }
            }
            case "ESCALAR" -> {
                var b = pt(a, "base");
                double k = num(a, "fator", 2.0);
                for (var e : selecao(a)) {
                    if (e.getTipo().construtivo()) {
                        throw erro("VI-003", e.getTipo() + " não pode ser escalado: use as propriedades.");
                    }
                    e.mapearPontos(p -> b.mais(p.menos(b).vezes(k)));
                    if (e.getTipo() == TipoElemento.CIRCULO) {
                        e.setNum("raio", e.num("raio", 1) * k);
                    }
                    diff.alterados.add(e);
                }
            }
            case "DESLOCAR" -> {
                double dist = num(a, "dist", 1.0);
                var lado = pt(a, "lado");
                for (var e : selecao(a)) {
                    if (!e.temSegmento()) {
                        continue;
                    }
                    var p1 = e.ponto("p1");
                    var v = e.ponto("p2").menos(p1);
                    double l = Math.hypot(v.x(), v.y());
                    if (l == 0) {
                        l = 1;
                    }
                    var nrm = new Ponto2D(-v.y() / l, v.x() / l);
                    double sinal = Math.signum(nrm.x() * (lado.x() - p1.x()) + nrm.y() * (lado.y() - p1.y()));
                    if (sinal == 0) {
                        sinal = 1;
                    }
                    var copia = copiaNova(e, cmd);
                    final double s = sinal;
                    copia.mapearPontos(p -> p.mais(nrm.vezes(dist * s)));
                    criar(diff, copia);
                }
            }
            case "ESTICAR" -> {
                var d = pt(a, "destino").menos(pt(a, "base"));
                Geo.Caixa janela = a.get("janela") instanceof Map<?, ?> j ? new Geo.Caixa(Elemento.aPonto(j.get("min")), Elemento.aPonto(j.get("max"))) : null;
                for (var e : selecao(a)) {
                    if (e.getTipo() == TipoElemento.PILAR) {
                        e.mapearPontos(p -> p.mais(d));
                    } else {
                        e.mapearPontos(p -> janela == null || janela.contem(p, 0) ? p.mais(d) : p);
                    }
                    diff.alterados.add(e);
                }
            }
            case "ALONGAR" -> {
                double delta = num(a, "delta", 1.0);
                for (var e : selecao(a)) {
                    if (e.temSegmento()) {
                        var v = e.ponto("p2").menos(e.ponto("p1"));
                        double l = Math.hypot(v.x(), v.y());
                        if (l == 0) {
                            l = 1;
                        }
                        e.setPonto("p2", e.ponto("p2").mais(v.vezes(delta / l)));
                        diff.alterados.add(e);
                    }
                }
            }
            case "MATRIZ" -> {
                int nx = Math.max(1, (int) Math.round(num(a, "nx", 3.0))), ny = Math.max(1, (int) Math.round(num(a, "ny", 1.0)));
                double dx = num(a, "dx", 5.0), dy = num(a, "dy", 6.0);
                for (var e : selecao(a)) {
                    for (int i = 0; i < nx; i++) {
                        for (int j = 0; j < ny; j++) {
                            if (i == 0 && j == 0) {
                                continue;
                            }
                            var c = copiaNova(e, cmd);
                            var d = new Ponto2D(i * dx, j * dy);
                            c.mapearPontos(p -> p.mais(d));
                            criar(diff, c);
                        }
                    }
                }
            }
            case "APAGAR" -> {
                var ids = new HashSet<UUID>();
                selecao(a).forEach(e -> ids.add(e.getId()));
                for (var e : elementos) {
                    if ((e.getTipo() == TipoElemento.PORTA || e.getTipo() == TipoElemento.JANELA) && ids.contains(e.uuid("paredeId"))) {
                        ids.add(e.getId());
                    }
                }
                elementos.removeIf(e -> ids.contains(e.getId()));
                diff.removidos.addAll(ids);
            }
            case "EXPLODIR" -> {
                for (var e : selecao(a)) {
                    if (e.getTipo() != TipoElemento.POLILINHA && e.getTipo() != TipoElemento.RETANGULO) {
                        throw erro("VI-003", e.getTipo() + " não explode.");
                    }
                    List<Ponto2D> pts = e.getTipo() == TipoElemento.RETANGULO
                            ? List.of(e.ponto("p1"), new Ponto2D(e.ponto("p2").x(), e.ponto("p1").y()), e.ponto("p2"), new Ponto2D(e.ponto("p1").x(), e.ponto("p2").y()))
                            : e.pontos("pontos");
                    boolean fechada = e.getTipo() == TipoElemento.RETANGULO || e.flag("fechada", false);
                    for (int i = 0; i < pts.size() - (fechada ? 0 : 1); i++) {
                        criar(diff, novo(cmd, pav, TipoElemento.LINHA, Camada.desenho, props("p1", pts.get(i), "p2", pts.get((i + 1) % pts.size()))));
                    }
                    elementos.removeIf(x -> x.getId().equals(e.getId()));
                    diff.removidos.add(e.getId());
                }
            }
            case "UNIR" -> {
                var linhas = selecao(a).stream().filter(e -> e.getTipo() == TipoElemento.LINHA).toList();
                if (linhas.size() < 2) {
                    throw erro("VI-002", "Selecione 2+ linhas para unir.");
                }
                var pts = new ArrayList<Ponto2D>(List.of(linhas.get(0).ponto("p1"), linhas.get(0).ponto("p2")));
                var resto = new ArrayList<>(linhas.subList(1, linhas.size()));
                while (!resto.isEmpty()) {
                    var fim = pts.get(pts.size() - 1);
                    Elemento achada = null;
                    for (var l : resto) {
                        if (l.ponto("p1").distancia(fim) < 0.001 || l.ponto("p2").distancia(fim) < 0.001) {
                            achada = l;
                            break;
                        }
                    }
                    if (achada == null) {
                        throw erro("VI-003", "As linhas não são contínuas.");
                    }
                    resto.remove(achada);
                    pts.add(achada.ponto("p1").distancia(fim) < 0.001 ? achada.ponto("p2") : achada.ponto("p1"));
                }
                var ids = new HashSet<UUID>();
                linhas.forEach(l -> ids.add(l.getId()));
                elementos.removeIf(e -> ids.contains(e.getId()));
                diff.removidos.addAll(ids);
                criar(diff, novo(cmd, pav, TipoElemento.POLILINHA, Camada.desenho, props("pontos", new ArrayList<Object>(pts), "fechada", pts.get(0).distancia(pts.get(pts.size() - 1)) < 0.001)));
            }
            case "ALTERAR" -> {
                var e = elemento(UUID.fromString(String.valueOf(a.get("id")))).orElseThrow(() -> erro("VI-002", "Elemento não encontrado."));
                if (a.get("props") instanceof Map<?, ?> m) {
                    m.forEach((k, v) -> {
                        var chave = String.valueOf(k);
                        if (chave.equals("rotulo")) {
                            e.setRotulo(String.valueOf(v));
                        } else if (chave.equals("materialId") || chave.equals("mat")) {
                            e.setMaterialId(TabelaA.normalizarId(String.valueOf(v)));
                        } else if (!chave.equals("id") && !chave.equals("tipo")) {
                            e.getProps().put(chave, v);
                        }
                    });
                }
                diff.alterados.add(e);
            }
            case "APARAR", "ESTENDER" -> aparar(a, cmd, diff);
            case "ALINHAR" -> {
                var ref = selecao(Map.of("sel", a.getOrDefault("ref", List.of()))).stream().findFirst().orElse(null);
                if (ref == null || !ref.temSegmento()) {
                    throw erro("VI-002", "Alinhar precisa de uma referência com segmento (eixo, linha, parede).");
                }
                for (var e : selecao(a)) {
                    var c = pontoRepresentativo(e);
                    var proj = Geo.projetarNoSegmento(c, ref.ponto("p1"), ref.ponto("p2")).ponto();
                    var d = proj.menos(c);
                    e.mapearPontos(p -> p.mais(d));
                    diff.alterados.add(e);
                }
            }
            default -> throw erro("VI-001", "Função \"" + cmd.codigo() + "\" não existe na Tabela B.");
        }
        return diff;
    }

    private void criar(DiffModelo diff, Elemento e) {
        elementos.add(e);
        diff.criados.add(e);
    }

    private Elemento copiaNova(Elemento e, ComandoVi cmd) {
        var c = e.copia();
        c.setId(Ids.novo());
        c.setOrigem(cmd.origem());
        return c;
    }

    @SuppressWarnings("unchecked")
    /** A…Z, AA, AB… (malhas acima de 26 eixos em X geravam "[", "\\"…) */
    static String nomeColuna(int i) {
        var sb = new StringBuilder();
        for (int n = i; n >= 0; n = n / 26 - 1) {
            sb.insert(0, (char) ('A' + n % 26));
        }
        return sb.toString();
    }

    private void malha(Map<String, Object> a, DiffModelo diff) {
        int nx = Math.max(1, (int) Math.round(num(a, "nx", 5.0))), ny = Math.max(1, (int) Math.round(num(a, "ny", 3.0)));
        List<Double> dxs = new ArrayList<>(), dys = new ArrayList<>();
        if (a.get("x") instanceof List<?> l) {
            l.forEach(o -> dxs.add(((Number) o).doubleValue()));
        } else {
            for (int i = 0; i < nx - 1; i++) {
                dxs.add(num(a, "dx", 5.0));
            }
        }
        if (a.get("y") instanceof List<?> l) {
            l.forEach(o -> dys.add(((Number) o).doubleValue()));
        } else {
            for (int i = 0; i < ny - 1; i++) {
                dys.add(num(a, "dy", 6.0));
            }
        }
        // RF03 CA-02: espaçamento ≥ ShortCurveTolerance (0,05 m)
        if (dxs.stream().anyMatch(d -> d < 0.05) || dys.stream().anyMatch(d -> d < 0.05)) {
            throw erro("VI-003", "Espaçamento de eixo deve ser de pelo menos 0,05 m (ShortCurveTolerance).");
        }
        // RF03 CA-01: sem nomes repetidos por direção (antes: 500 por violar eixo_modelo_id_nome_key ao gerar a 2ª malha)
        var repetidos = new ArrayList<String>();
        for (int i = 0; i <= dxs.size(); i++) {
            var n = nomeColuna(i);
            if (eixos.stream().anyMatch(e -> e.direcao() == Direcao.X && e.nome().equals(n))) {
                repetidos.add(n);
            }
        }
        for (int i = 0; i <= dys.size(); i++) {
            var n = String.valueOf(i + 1);
            if (eixos.stream().anyMatch(e -> e.direcao() == Direcao.Y && e.nome().equals(n))) {
                repetidos.add(n);
            }
        }
        if (!repetidos.isEmpty()) {
            throw erro("VI-003", "O modelo já tem os eixos " + String.join(", ", repetidos) + ". Para acrescentar eixos use EIXO; para refazer a malha, apague a atual primeiro.");
        }
        var o = ptOpt(a, "origem");
        if (o == null) {
            o = new Ponto2D(0, 0);
        }
        double totalX = dxs.stream().mapToDouble(Double::doubleValue).sum(), totalY = dys.stream().mapToDouble(Double::doubleValue).sum();
        double x = o.x();
        for (int i = 0; i <= dxs.size(); i++) {
            var eixo = new Eixo(Ids.novo(), nomeColuna(i), Direcao.X, Geo.arredondar(x), o.y() - 1, o.y() + totalY + 1, 1.0);
            eixos.add(eixo);
            diff.eixosCriados.add(eixo);
            if (i < dxs.size()) {
                x += dxs.get(i);
            }
        }
        double y = o.y();
        for (int i = 0; i <= dys.size(); i++) {
            var eixo = new Eixo(Ids.novo(), String.valueOf(i + 1), Direcao.Y, Geo.arredondar(y), o.x() - 1, o.x() + totalX + 1, 1.0);
            eixos.add(eixo);
            diff.eixosCriados.add(eixo);
            if (i < dys.size()) {
                y += dys.get(i);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void aparar(Map<String, Object> a, ComandoVi cmd, DiffModelo diff) {
        var arestas = new ArrayList<Elemento>();
        if (a.get("arestas") instanceof List<?> l) {
            for (var o : l) {
                elemento(o instanceof UUID u ? u : UUID.fromString(String.valueOf(o))).filter(Elemento::temSegmento).ifPresent(arestas::add);
            }
        }
        boolean estender = cmd.codigo().equals("ESTENDER");
        for (var e : selecao(a)) {
            if (!e.temSegmento()) {
                continue;
            }
            var clique = ptOpt(a, "clique");
            if (clique == null) {
                clique = e.ponto("p2");
            }
            Ponto2D melhor = null;
            double melhorT = Double.MAX_VALUE;
            for (var ar : arestas) {
                var ip = Geo.intersecaoSegmentos(e.ponto("p1"), e.ponto("p2"), ar.ponto("p1"), ar.ponto("p2"), estender).orElse(null);
                if (ip == null) {
                    continue;
                }
                double t = Geo.projetarNoSegmento(ip, e.ponto("p1"), e.ponto("p2")).t();
                if (melhor == null || Math.abs(t - 0.5) < Math.abs(melhorT - 0.5)) {
                    melhor = ip;
                    melhorT = t;
                }
            }
            if (melhor == null) {
                continue;
            }
            if (clique.distancia(e.ponto("p1")) < clique.distancia(e.ponto("p2"))) {
                e.setPonto("p1", melhor);
            } else {
                e.setPonto("p2", melhor);
            }
            diff.alterados.add(e);
        }
    }

    private Elemento encontrarParede(UUID id, Ponto2D pos, UUID pav) {
        if (id != null) {
            return elemento(id).orElse(null);
        }
        if (pos == null) {
            return null;
        }
        Elemento melhor = null;
        double melhorD = Double.MAX_VALUE;
        for (var e : elementosDo(pav)) {
            if (e.getTipo() != TipoElemento.PAREDE) {
                continue;
            }
            double d = Geo.projetarNoSegmento(pos, e.ponto("p1"), e.ponto("p2")).ponto().distancia(pos);
            if (d <= e.num("espessura", 0.15) + 0.15 && d < melhorD) {
                melhor = e;
                melhorD = d;
            }
        }
        return melhor;
    }

    private String proximoNomeEixo(Direcao direcao) {
        long usados = eixos.stream().filter(e -> e.direcao() == direcao).count();
        return direcao == Direcao.X ? nomeColuna((int) usados) : String.valueOf(usados + 1);
    }

    /** RF03: mover um eixo reposiciona os pilares ancorados nele (dentro da tolerância) */
    public List<Elemento> moverEixo(UUID eixoId, double novaPosicao, double tolerancia) {
        var idx = -1;
        for (int i = 0; i < eixos.size(); i++) {
            if (eixos.get(i).id().equals(eixoId)) {
                idx = i;
            }
        }
        if (idx < 0) {
            throw new ExcecaoDominio.RecursoNaoEncontrado("Eixo");
        }
        var eixo = eixos.get(idx);
        double delta = novaPosicao - eixo.posicao();
        eixos.set(idx, eixo.comPosicao(novaPosicao));
        var movidos = new ArrayList<Elemento>();
        for (var e : elementos) {
            if (e.getTipo() != TipoElemento.PILAR) {
                continue;
            }
            var c = e.ponto("centro");
            double coord = eixo.direcao() == Direcao.X ? c.x() : c.y();
            if (Math.abs(coord - eixo.posicao()) <= tolerancia) {
                e.setPonto("centro", eixo.direcao() == Direcao.X ? Ponto2D.de(c.x() + delta, c.y()) : Ponto2D.de(c.x(), c.y() + delta));
                movidos.add(e);
            }
        }
        return movidos;
    }

    /** Atalho para testes de domínio: executa + audita (laço 1 e 2 locais) + versiona + snapshot. */
    public ResultadoLocal aplicarAuditado(String codigo, Map<String, Object> args, OrigemComando origem, Long versaoBase) {
        long base = versaoBase == null ? versao : versaoBase;
        if (base != versao) {
            throw new ExcecaoDominio.Conflito("Modelo avançou para a versão " + versao + " (base " + base + "). Recarregue o diff.");
        }
        var cmd = new ComandoVi(Ids.novo(), codigo.toUpperCase(), new LinkedHashMap<>(args), origem, base, null, java.time.Instant.now());
        var antes = estado();
        var rascunho = copia();
        var diff = rascunho.executar(cmd, null);
        var relatorio = new Auditor(new ServicoGeometriaLocal()).auditar(rascunho, diff, new Auditor.Opcoes(0.05, false, Auditor.PoliticaCaixa.RECORTAR));
        if (!relatorio.aprovado()) {
            throw new ComandoReprovadoException(relatorio);
        }
        guardarParaDesfazer(antes);
        restaurar(rascunho.estado());
        versao = base + 1;
        diff.versaoNova = versao;
        return new ResultadoLocal(diff, relatorio, cmd);
    }

    public record ResultadoLocal(DiffModelo diff, RelatorioAuditoria relatorio, ComandoVi comando) {
    }

    /** resolve coordenadas relativas/polares dos args em relação ao último ponto */
    public static Map<String, Object> resolverCoordenadas(Map<String, Object> args) {
        var out = new LinkedHashMap<String, Object>();
        Ponto2D ultimo = null;
        for (var e : args.entrySet()) {
            var v = e.getValue();
            if (v instanceof ParserVi.Relativo r) {
                var base = ultimo == null ? new Ponto2D(0, 0) : ultimo;
                v = Ponto2D.de(base.x() + r.dx(), base.y() + r.dy());
            } else if (v instanceof ParserVi.Polar p) {
                var base = ultimo == null ? new Ponto2D(0, 0) : ultimo;
                v = Ponto2D.de(base.x() + p.d() * Math.cos(Math.toRadians(p.ang())), base.y() + p.d() * Math.sin(Math.toRadians(p.ang())));
            }
            if (v instanceof Ponto2D p) {
                ultimo = p;
            }
            out.put(e.getKey(), v);
        }
        return out;
    }

    public Set<UUID> idsElementos() {
        var s = new HashSet<UUID>();
        elementos.forEach(e -> s.add(e.getId()));
        return s;
    }
}
