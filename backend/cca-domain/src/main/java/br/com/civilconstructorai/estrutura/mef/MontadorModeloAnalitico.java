package br.com.civilconstructorai.estrutura.mef;

import br.com.civilconstructorai.estrutura.carga.Carga;
import br.com.civilconstructorai.estrutura.carga.NaturezaAcao;
import br.com.civilconstructorai.estrutura.carga.ParametrosVento;
import br.com.civilconstructorai.estrutura.carga.TipoCarga;
import br.com.civilconstructorai.estrutura.modelo.ElementoEstrutural;
import br.com.civilconstructorai.estrutura.modelo.ModeloEstrutural;
import br.com.civilconstructorai.estrutura.modelo.Ponto;
import br.com.civilconstructorai.estrutura.modelo.TipoElementoEstrutural;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Monta o pórtico espacial a partir do recorte BIM:
 * <ul>
 *   <li>pilares: barras verticais entre os níveis, seção com b ao longo do eixo X do pilar e h ao longo do Y,
 *       girada pela rotação do pilar (a mesma da planta e da cena 3D); seção circular quando o pilar é circular;</li>
 *   <li>vigas: barras no nível do pavimento, divididas nos nós sobre o eixo (pilares, outras vigas e pontos de
 *       carga concentrada) — ligadas aos pilares a até {@value #TOL_LIGACAO} m;</li>
 *   <li>contraventamentos metálicos: barras de treliça (só esforço normal) entre nós já existentes;</li>
 *   <li>apoios: engaste na base dos pilares do nível mais baixo;</li>
 *   <li>cargas: peso próprio (concreto 25 kN/m³, aço 78,5 kN/m³); lajes retangulares apoiadas nas quatro bordas
 *       repartidas pelas charneiras a 45° (NBR 6118 14.7.6.1, bordas do mesmo tipo), aplicadas como trapézios e
 *       triângulos exatos nas vigas; demais lajes, proporcional ao comprimento apoiado (aproximação declarada);
 *       cargas lineares e pontuais por elemento alvo; vento como forças em +X por nível (NBR 6123).</li>
 * </ul>
 * Tudo o que não entra no modelo, ou entra por aproximação ou premissa, vira {@link ModeloAnalitico.Aviso}.
 */
public final class MontadorModeloAnalitico {

    public static final double TOL_LIGACAO = 0.30;
    public static final double PESO_CONCRETO = 25.0;
    public static final double PESO_ACO = 78.5;
    /** módulo de elasticidade do aço estrutural, kN/m² (200 GPa) */
    public static final double E_ACO = 2.0e8;

    private MontadorModeloAnalitico() {
    }

    public record Entrada(ModeloEstrutural modelo, List<Carga> cargas, ParametrosVento vento, Map<String, Double> moduloPorMaterial) {
    }

    public static ModeloAnalitico montar(Entrada in) {
        var m = in.modelo();
        var ma = new ModeloAnalitico();
        // uma viga contínua vira várias barras (uma por trecho entre nós); pilar continua sendo uma barra
        var barrasPorElemento = new HashMap<String, List<Integer>>();
        var pavs = m.pavimentosOrdenados();
        var elevacaoMinima = pavs.isEmpty() ? 0 : pavs.get(0).elevacao();

        for (var e : m.elementos()) {
            if (e.rascunhoIa() && (e.tipo() == TipoElementoEstrutural.PILAR || e.tipo() == TipoElementoEstrutural.VIGA || e.tipo() == TipoElementoEstrutural.LAJE
                    || e.tipo() == TipoElementoEstrutural.CONTRAVENTAMENTO)) {
                ma.avisar("EXCLUIDO", e.id(), rotulo(e, e.tipo().name().substring(0, 1)) + ": rascunho de IA não aprovado não entra na análise.");
            }
        }

        for (var p : m.doTipo(TipoElementoEstrutural.PILAR)) {
            if (p.rascunhoIa()) {
                continue;
            }
            var base = m.pavimento(p.nivelBaseId() == null ? p.pavimentoId() : p.nivelBaseId()).orElse(pavs.get(0));
            double zTopo = base.elevacao() + m.alturaPilar(p);
            int ni = ma.noEm(p.centro().x(), p.centro().y(), base.elevacao(), TOL_LIGACAO);
            int nj = ma.noEm(p.centro().x(), p.centro().y(), zTopo, TOL_LIGACAO);
            double e = modulo(in, ma, p);
            // eixo z local = eixo X do pilar (dimensão b); eixo y local = −Y do pilar (dimensão h)
            var sec = p.circular() ? ModeloAnalitico.Secao.circular(p.b(), e) : ModeloAnalitico.Secao.retangular(p.h(), p.b(), e);
            int id = ma.adicionarBarra(p.id(), rotulo(p, "P"), ni, nj, sec, Math.toRadians(p.rotacao()), false);
            barrasPorElemento.put(p.id(), List.of(id));
            if (Math.abs(base.elevacao() - elevacaoMinima) < 1e-6) {
                ma.adicionarApoio(ModeloAnalitico.Apoio.engaste(ni));
            }
            // peso próprio do pilar distribuído ao longo da barra (esforço normal cresce para baixo)
            ma.caso(NaturezaAcao.PERMANENTE).distribuidas().add(new ModeloAnalitico.CargaDistribuida(id, 0, 0, -sec.area() * PESO_CONCRETO));
        }

        var vigas = m.doTipo(TipoElementoEstrutural.VIGA).stream().filter(v -> !v.rascunhoIa()).toList();
        // nós das extremidades de todas as vigas antes de discretizar: assim uma viga que nasce no meio de outra
        // também quebra a viga apoio (ligação viga-viga)
        for (var v : vigas) {
            double z = elevacao(m, v);
            ma.noEm(v.p1().x(), v.p1().y(), z, TOL_LIGACAO);
            ma.noEm(v.p2().x(), v.p2().y(), z, TOL_LIGACAO);
        }
        // cargas concentradas em vigas: um nó no ponto de aplicação (a carga vai ao nó, a viga flete)
        var nosDeCarga = new HashMap<Carga, Integer>();
        for (var c : in.cargas()) {
            if (c.getTipo() != TipoCarga.PONTUAL || c.getElementoAlvoId() == null) {
                continue;
            }
            var alvo = m.elemento(c.getElementoAlvoId()).orElse(null);
            if (alvo == null || alvo.tipo() != TipoElementoEstrutural.VIGA || alvo.rascunhoIa()) {
                continue;
            }
            double l = alvo.comprimento();
            double pos;
            if (c.getPosicao() == null) {
                pos = l / 2;
                ma.avisar("PREMISSA", alvo.id(), String.format(Locale.ROOT, "Carga pontual \"%s\" (%.2f kN) sem posição informada: aplicada no meio do vão (%.2f m). Informe a posição.",
                        c.getDescricao(), c.getValor(), pos));
            } else if (c.getPosicao() > l + 1e-6) {
                ma.avisar("EXCLUIDO", alvo.id(), String.format(Locale.ROOT, "Carga pontual \"%s\" a %.2f m, além do comprimento da viga (%.2f m): não aplicada.",
                        c.getDescricao(), c.getPosicao(), l));
                continue;
            } else {
                pos = c.getPosicao();
            }
            double t = pos / l;
            nosDeCarga.put(c, ma.noEm(alvo.p1().x() + t * (alvo.p2().x() - alvo.p1().x()), alvo.p1().y() + t * (alvo.p2().y() - alvo.p1().y()), elevacao(m, alvo), 1e-6));
        }
        for (var v : vigas) {
            double z = elevacao(m, v);
            int ni = ma.noEm(v.p1().x(), v.p1().y(), z, TOL_LIGACAO);
            int nj = ma.noEm(v.p2().x(), v.p2().y(), z, TOL_LIGACAO);
            if (ni == nj) {
                ma.avisar("EXCLUIDO", v.id(), rotulo(v, "V") + ": comprimento menor que a tolerância de ligação (" + TOL_LIGACAO + " m); não entra na análise.");
                continue;
            }
            var trecho = nosSobreViga(ma, v, z, ni, nj);
            double e = modulo(in, ma, v);
            var sec = ModeloAnalitico.Secao.retangular(v.b(), v.h(), e);
            var ids = new ArrayList<Integer>();
            for (int k = 0; k + 1 < trecho.size(); k++) {
                var rot = trecho.size() > 2 ? rotulo(v, "V") + "." + (k + 1) : rotulo(v, "V");
                int id = ma.adicionarBarra(v.id(), rot, trecho.get(k), trecho.get(k + 1), sec);
                ids.add(id);
                ma.caso(NaturezaAcao.PERMANENTE).distribuidas().add(new ModeloAnalitico.CargaDistribuida(id, 0, 0, -v.b() * v.h() * PESO_CONCRETO));
            }
            barrasPorElemento.put(v.id(), List.copyOf(ids));
        }

        // contraventamentos metálicos: barra de treliça entre nós existentes do pavimento
        for (var c : m.doTipo(TipoElementoEstrutural.CONTRAVENTAMENTO)) {
            if (c.rascunhoIa()) {
                continue;
            }
            double z = elevacao(m, c);
            Integer ni = noExistente(ma, c.p1().x(), c.p1().y(), z), nj = noExistente(ma, c.p2().x(), c.p2().y(), z);
            var area = areaPerfil(c.perfil());
            if (area == null) {
                ma.avisar("EXCLUIDO", c.id(), rotulo(c, "C") + ": perfil \"" + c.perfil() + "\" não reconhecido (use L <aba>x<espessura> em mm); não entra na análise.");
                continue;
            }
            if (ni == null || nj == null || ni.equals(nj)) {
                ma.avisar("EXCLUIDO", c.id(), rotulo(c, "C") + ": as extremidades não coincidem com nós de pilares ou vigas do pavimento; não entra na análise.");
                continue;
            }
            int id = ma.adicionarBarra(c.id(), rotulo(c, "C"), ni, nj, ModeloAnalitico.Secao.axial(area, in.moduloPorMaterial() != null && c.materialId() != null && in.moduloPorMaterial().containsKey(c.materialId()) ? in.moduloPorMaterial().get(c.materialId()) : E_ACO), 0, true);
            barrasPorElemento.put(c.id(), List.of(id));
            ma.caso(NaturezaAcao.PERMANENTE).distribuidas().add(new ModeloAnalitico.CargaDistribuida(id, 0, 0, -area * PESO_ACO));
            ma.avisar("APROXIMACAO", c.id(), String.format(Locale.ROOT, "%s: barra de treliça (tração e compressão, ligações rotuladas), área %.2f cm² sem raios de concordância. "
                    + "Não é cabo: não há afrouxamento nem pré-tensão. Flambagem e ligações (NBR 8800) não verificadas.", rotulo(c, "C"), area * 1e4));
        }

        // cargas de área e concentradas sobre lajes, por natureza (kN/m² equivalente)
        var cargaLaje = new HashMap<String, double[]>();
        for (var l : m.doTipo(TipoElementoEstrutural.LAJE)) {
            if (!l.rascunhoIa()) {
                var q = new double[NaturezaAcao.values().length];
                q[NaturezaAcao.PERMANENTE.ordinal()] = l.h() * PESO_CONCRETO;
                cargaLaje.put(l.id(), q);
            }
        }
        for (var c : in.cargas()) {
            if (c.getTipo() == TipoCarga.AREA) {
                if (c.getElementoAlvoId() == null) {
                    var lajes = m.doPavimento(c.getPavimentoId()).stream().filter(x -> cargaLaje.containsKey(x.id())).toList();
                    if (lajes.isEmpty()) {
                        ma.avisar("EXCLUIDO", null, "Carga de área \"" + c.getDescricao() + "\" no pavimento sem lajes analisáveis: não aplicada.");
                    }
                    lajes.forEach(x -> cargaLaje.get(x.id())[c.getNatureza().ordinal()] += c.getValor());
                } else if (cargaLaje.containsKey(c.getElementoAlvoId())) {
                    cargaLaje.get(c.getElementoAlvoId())[c.getNatureza().ordinal()] += c.getValor();
                } else {
                    ma.avisar("EXCLUIDO", c.getElementoAlvoId(), "Carga de área \"" + c.getDescricao() + "\" aplicada a um elemento que não é laje analisável: não aplicada.");
                }
            } else if (c.getTipo() == TipoCarga.PONTUAL && c.getElementoAlvoId() != null && cargaLaje.containsKey(c.getElementoAlvoId())) {
                var laje = m.elemento(c.getElementoAlvoId()).orElseThrow();
                double area = ElementoEstrutural.areaPoligono(laje.contorno());
                cargaLaje.get(laje.id())[c.getNatureza().ordinal()] += c.getValor() / Math.max(area, 1e-6);
                ma.avisar("APROXIMACAO", laje.id(), String.format(Locale.ROOT, "Carga concentrada \"%s\" (%.2f kN) na laje tratada como distribuída na laje inteira: punção e flexão local não são verificadas.",
                        c.getDescricao(), c.getValor()));
            }
        }

        for (var l : m.doTipo(TipoElementoEstrutural.LAJE)) {
            if (l.rascunhoIa()) {
                continue;
            }
            var q = cargaLaje.get(l.id());
            double z = elevacao(m, l);
            var porBorda = bordasApoiadas(ma, m, l, z, barrasPorElemento);
            boolean retangular = retangulo(l.contorno());
            boolean todasApoiadas = porBorda != null && porBorda.stream().allMatch(b -> b.cobertura() >= b.comprimento() - 2 * TOL_LIGACAO);
            if (retangular && todasApoiadas) {
                double lx = Math.min(porBorda.get(0).comprimento(), porBorda.get(1).comprimento());
                for (var nat : NaturezaAcao.values()) {
                    double qn = q[nat.ordinal()];
                    if (qn == 0) {
                        continue;
                    }
                    for (var borda : porBorda) {
                        aplicarCharneiras(ma, nat, qn, borda, lx);
                    }
                }
                continue;
            }
            repartirPorComprimento(ma, m, l, q, barrasPorElemento, retangular ? "nem todas as bordas apoiadas em vigas" : "contorno não retangular");
        }

        // cargas lineares e pontuais por elemento alvo
        for (var c : in.cargas()) {
            if (c.getTipo() == TipoCarga.AREA) {
                continue;
            }
            if (c.getElementoAlvoId() == null) {
                ma.avisar("EXCLUIDO", null, "Carga " + c.getTipo().name().toLowerCase() + " \"" + c.getDescricao() + "\" sem elemento alvo: não aplicada.");
                continue;
            }
            if (cargaLaje.containsKey(c.getElementoAlvoId())) {
                if (c.getTipo() == TipoCarga.LINEAR) {
                    ma.avisar("EXCLUIDO", c.getElementoAlvoId(), "Carga linear \"" + c.getDescricao() + "\" sobre laje: lance-a na viga de apoio ou como carga de área; não aplicada.");
                }
                continue;
            }
            var idsBarras = barrasPorElemento.get(c.getElementoAlvoId());
            if (idsBarras == null || idsBarras.isEmpty()) {
                ma.avisar("EXCLUIDO", c.getElementoAlvoId(), "Carga \"" + c.getDescricao() + "\" num elemento que não está no modelo analítico: não aplicada.");
                continue;
            }
            var alvo = m.elemento(c.getElementoAlvoId()).orElseThrow();
            if (c.getTipo() == TipoCarga.LINEAR) {
                for (int idBarra : idsBarras) {
                    ma.caso(c.getNatureza()).distribuidas().add(new ModeloAnalitico.CargaDistribuida(idBarra, 0, 0, -c.getValor()));
                }
            } else if (alvo.tipo() == TipoElementoEstrutural.VIGA) {
                Integer no = nosDeCarga.get(c);
                if (no != null) {
                    ma.caso(c.getNatureza()).nodais().add(new ModeloAnalitico.CargaNodal(no, 0, 0, -c.getValor(), 0, 0, 0));
                }
            } else {
                // pilar (ou contraventamento): carga no nó superior
                int no = ma.barras().get(idsBarras.getLast()).noJ();
                ma.caso(c.getNatureza()).nodais().add(new ModeloAnalitico.CargaNodal(no, 0, 0, -c.getValor(), 0, 0, 0));
            }
        }

        // vento: força horizontal em +X por nível, repartida pelos nós do nível
        if (in.vento() != null && pavs.size() > 1) {
            double larguraY = larguraEmY(m);
            for (int i = 1; i < pavs.size(); i++) {
                double zNivel = pavs.get(i).elevacao();
                double hAbaixo = zNivel - pavs.get(i - 1).elevacao();
                double hAcima = i + 1 < pavs.size() ? pavs.get(i + 1).elevacao() - zNivel : 0;
                double areaFachada = (hAbaixo + hAcima) / 2 * larguraY;
                var nosNivel = ma.nos().stream().filter(n -> Math.abs(n.z() - zNivel) < TOL_LIGACAO).toList();
                if (nosNivel.isEmpty()) {
                    continue;
                }
                double forca = in.vento().forca(areaFachada) / nosNivel.size();
                for (var n : nosNivel) {
                    ma.caso(NaturezaAcao.VENTO).nodais().add(new ModeloAnalitico.CargaNodal(n.id(), forca, 0, 0, 0, 0, 0));
                }
            }
            ma.avisar("APROXIMACAO", null, "Vento aplicado só na direção +X (forças por nível repartidas igualmente pelos nós). Vento em Y e em −X não é analisado.");
        }
        // extremidade de viga ligada a uma única barra e sem apoio: o pórtico a resolve como balanço — dizer isso
        var grau = new HashMap<Integer, Integer>();
        for (var b : ma.barras()) {
            grau.merge(b.noI(), 1, Integer::sum);
            grau.merge(b.noJ(), 1, Integer::sum);
        }
        var apoiados = new java.util.HashSet<Integer>();
        ma.apoios().forEach(a -> apoiados.add(a.no()));
        for (var v : m.doTipo(TipoElementoEstrutural.VIGA)) {
            var ids = barrasPorElemento.get(v.id());
            if (ids == null || ids.isEmpty()) {
                continue;
            }
            var primeira = ma.barras().get(ids.get(0));
            var ultima = ma.barras().get(ids.get(ids.size() - 1));
            for (int no : new int[]{primeira.noI(), ultima.noJ()}) {
                if (grau.getOrDefault(no, 0) == 1 && !apoiados.contains(no)) {
                    var n = ma.nos().get(no);
                    ma.avisar("PREMISSA", v.id(), String.format(Locale.ROOT, "%s: extremidade livre em (%.2f; %.2f) — sem pilar nem viga; analisada como balanço. "
                            + "Confirme se é intencional (marquise, sacada) ou se falta um apoio.", rotulo(v, "V"), n.x(), n.y()));
                }
            }
        }
        return ma;
    }

    private static double elevacao(ModeloEstrutural m, ElementoEstrutural e) {
        return m.pavimento(e.pavimentoId()).map(x -> x.elevacao()).orElse(0.0);
    }

    /** módulo do material; sem ele, adota o Ecs do C30 e registra a premissa (uma vez por material) */
    private static double modulo(Entrada in, ModeloAnalitico ma, ElementoEstrutural e) {
        String materialId = e.materialId();
        if (in.moduloPorMaterial() != null && materialId != null && in.moduloPorMaterial().containsKey(materialId)) {
            return in.moduloPorMaterial().get(materialId);
        }
        String chave = materialId == null ? "(sem material)" : materialId;
        if (ma.avisos().stream().noneMatch(a -> a.mensagem().startsWith("Material " + chave))) {
            ma.avisar("PREMISSA", e.id(), "Material " + chave + " sem módulo de elasticidade no catálogo: adotado Ecs do concreto C30 ("
                    + Math.round(moduloConcreto(30) / 1000) + " MPa). Atribua um material do catálogo ao elemento.");
        }
        return moduloConcreto(30);
    }

    /** Ecs (NBR 6118 8.2.8) em kN/m² para granito: Eci = αE·5600·√fck; Ecs = αi·Eci */
    public static double moduloConcreto(double fckMpa) {
        double alphaI = Math.min(1.0, 0.8 + 0.2 * fckMpa / 80);
        return alphaI * 5600 * Math.sqrt(fckMpa) * 1000;
    }

    private static final Pattern PERFIL_L = Pattern.compile("^\\s*L\\s*(\\d+(?:[.,]\\d+)?)\\s*[xX×]\\s*(\\d+(?:[.,]\\d+)?)(?:\\s*[xX×]\\s*(\\d+(?:[.,]\\d+)?))?\\s*$");

    /** área (m²) de cantoneira "L 50x5" (abas iguais) ou "L 50x40x5": t·(b1 + b2 − t), sem raios de concordância */
    public static Double areaPerfil(String perfil) {
        if (perfil == null) {
            return null;
        }
        var mt = PERFIL_L.matcher(perfil);
        if (!mt.matches()) {
            return null;
        }
        double a = num(mt.group(1)), b = num(mt.group(2));
        double b1, b2, t;
        if (mt.group(3) == null) {
            b1 = a; b2 = a; t = b;
        } else {
            b1 = a; b2 = b; t = num(mt.group(3));
        }
        if (t <= 0 || t >= Math.min(b1, b2)) {
            return null;
        }
        return t * (b1 + b2 - t) * 1e-6;
    }

    private static double num(String s) {
        return Double.parseDouble(s.replace(',', '.'));
    }

    private static String rotulo(ElementoEstrutural e, String prefixo) {
        return e.rotulo() != null ? e.rotulo() : prefixo + "-" + e.id().substring(0, Math.min(6, e.id().length()));
    }

    private static Integer noExistente(ModeloAnalitico ma, double x, double y, double z) {
        Integer melhor = null;
        double dMin = TOL_LIGACAO;
        for (var n : ma.nos()) {
            double d = Math.sqrt(Math.pow(n.x() - x, 2) + Math.pow(n.y() - y, 2) + Math.pow(n.z() - z, 2));
            if (d <= dMin) {
                dMin = d;
                melhor = n.id();
            }
        }
        return melhor;
    }

    /** nós do nível z sobre o eixo da viga (inclusive as extremidades), ordenados de p1 para p2 */
    static List<Integer> nosSobreViga(ModeloAnalitico ma, ElementoEstrutural v, double z, int ni, int nj) {
        double dx = v.p2().x() - v.p1().x(), dy = v.p2().y() - v.p1().y();
        double l2 = dx * dx + dy * dy;
        var internos = new TreeMap<Double, Integer>();
        for (var n : ma.nos()) {
            if (n.id() == ni || n.id() == nj || Math.abs(n.z() - z) > TOL_LIGACAO) {
                continue;
            }
            double t = ((n.x() - v.p1().x()) * dx + (n.y() - v.p1().y()) * dy) / l2;
            if (t <= 1e-6 || t >= 1 - 1e-6) {
                continue;
            }
            double px = v.p1().x() + t * dx, py = v.p1().y() + t * dy;
            if (Math.hypot(n.x() - px, n.y() - py) <= TOL_LIGACAO) {
                internos.putIfAbsent(t, n.id());
            }
        }
        var out = new ArrayList<Integer>();
        out.add(ni);
        out.addAll(internos.values());
        out.add(nj);
        return out;
    }

    // ------------------------------------------------------------------ lajes
    /** trecho de barra de viga sobre uma borda da laje: posição na borda (tI → tJ) e barra */
    private record TrechoBorda(int barra, double tI, double tJ) {
    }

    private record Borda(Ponto a, Ponto b, double comprimento, List<TrechoBorda> trechos, double cobertura) {
    }

    private static boolean retangulo(List<Ponto> c) {
        if (c.size() != 4) {
            return false;
        }
        for (int i = 0; i < 4; i++) {
            var a = c.get(i); var b = c.get((i + 1) % 4); var d = c.get((i + 2) % 4);
            double ux = b.x() - a.x(), uy = b.y() - a.y(), vx = d.x() - b.x(), vy = d.y() - b.y();
            double cos = (ux * vx + uy * vy) / Math.max(Math.hypot(ux, uy) * Math.hypot(vx, vy), 1e-12);
            if (Math.abs(cos) > 0.01) {
                return false;
            }
        }
        return true;
    }

    /** para cada borda da laje, os trechos de barras de viga que correm sobre ela e quanto da borda cobrem */
    private static List<Borda> bordasApoiadas(ModeloAnalitico ma, ModeloEstrutural m, ElementoEstrutural laje, double z, Map<String, List<Integer>> barrasPorElemento) {
        var c = laje.contorno();
        if (c.size() < 3) {
            return null;
        }
        var bordas = new ArrayList<Borda>();
        for (int i = 0; i < c.size(); i++) {
            var a = c.get(i); var b = c.get((i + 1) % c.size());
            double le = a.distancia(b);
            if (le < 1e-9) {
                continue;
            }
            double ux = (b.x() - a.x()) / le, uy = (b.y() - a.y()) / le;
            var trechos = new ArrayList<TrechoBorda>();
            double cobertura = 0;
            for (var v : m.doPavimento(laje.pavimentoId())) {
                if (v.tipo() != TipoElementoEstrutural.VIGA || !barrasPorElemento.containsKey(v.id())) {
                    continue;
                }
                for (int idBarra : barrasPorElemento.get(v.id())) {
                    var barra = ma.barras().get(idBarra);
                    var ni = ma.nos().get(barra.noI()); var nj = ma.nos().get(barra.noJ());
                    if (Math.abs(ni.z() - z) > TOL_LIGACAO || distanciaReta(a, ux, uy, ni.x(), ni.y()) > TOL_LIGACAO || distanciaReta(a, ux, uy, nj.x(), nj.y()) > TOL_LIGACAO) {
                        continue;
                    }
                    double tI = (ni.x() - a.x()) * ux + (ni.y() - a.y()) * uy, tJ = (nj.x() - a.x()) * ux + (nj.y() - a.y()) * uy;
                    double ini = Math.max(0, Math.min(tI, tJ)), fim = Math.min(le, Math.max(tI, tJ));
                    if (fim - ini > 1e-6) {
                        trechos.add(new TrechoBorda(idBarra, tI, tJ));
                        cobertura += fim - ini;
                    }
                }
            }
            bordas.add(new Borda(a, b, le, trechos, cobertura));
        }
        return bordas;
    }

    private static double distanciaReta(Ponto a, double ux, double uy, double x, double y) {
        return Math.abs((x - a.x()) * uy - (y - a.y()) * ux);
    }

    /**
     * Charneiras a 45° (bordas apoiadas do mesmo tipo): ao longo de uma borda de comprimento Le, a intensidade
     * na viga é w(t) = q·mín(t, Le − t, lx/2), lx = menor lado. Trapézio nas bordas longas, triângulo nas curtas.
     */
    private static void aplicarCharneiras(ModeloAnalitico ma, NaturezaAcao nat, double q, Borda borda, double lx) {
        double le = borda.comprimento();
        double[] quebras = {0, Math.min(lx / 2, le / 2), Math.max(le - lx / 2, le / 2), le};
        for (var tr : borda.trechos()) {
            double ini = Math.max(0, Math.min(tr.tI(), tr.tJ())), fim = Math.min(le, Math.max(tr.tI(), tr.tJ()));
            for (int k = 0; k + 1 < quebras.length; k++) {
                double ta = Math.max(ini, quebras[k]), tb = Math.min(fim, quebras[k + 1]);
                if (tb - ta < 1e-9) {
                    continue;
                }
                double wa = q * Math.min(Math.min(ta, le - ta), lx / 2), wb = q * Math.min(Math.min(tb, le - tb), lx / 2);
                double sa = Math.abs(ta - tr.tI()), sb = Math.abs(tb - tr.tI());
                if (sa > sb) {
                    double t = sa; sa = sb; sb = t;
                    t = wa; wa = wb; wb = t;
                }
                ma.caso(nat).variaveis().add(new ModeloAnalitico.CargaVariavel(tr.barra(), sa, sb, 0, 0, -wa, 0, 0, -wb));
            }
        }
    }

    private static void repartirPorComprimento(ModeloAnalitico ma, ModeloEstrutural m, ElementoEstrutural l, double[] q, Map<String, List<Integer>> barrasPorElemento, String motivo) {
        double area = ElementoEstrutural.areaPoligono(l.contorno());
        var vigasContorno = new ArrayList<ElementoEstrutural>();
        for (var v : m.doPavimento(l.pavimentoId())) {
            if (v.tipo() == TipoElementoEstrutural.VIGA && barrasPorElemento.containsKey(v.id()) && noContorno(v.p1(), l.contorno()) && noContorno(v.p2(), l.contorno())) {
                vigasContorno.add(v);
            }
        }
        double somaL = vigasContorno.stream().mapToDouble(ElementoEstrutural::comprimento).sum();
        var pilares = m.doPavimento(l.pavimentoId()).stream().filter(e -> e.tipo() == TipoElementoEstrutural.PILAR && barrasPorElemento.containsKey(e.id())).toList();
        if (somaL > 0) {
            ma.avisar("APROXIMACAO", l.id(), rotulo(l, "L") + ": " + motivo + " — carga repartida pelas vigas de contorno na proporção do comprimento (não pelas charneiras plásticas).");
        } else if (!pilares.isEmpty()) {
            ma.avisar("APROXIMACAO", l.id(), rotulo(l, "L") + ": sem vigas de contorno — carga dividida igualmente entre os pilares do pavimento (laje lisa não é calculada como placa; punção não verificada).");
        } else {
            ma.avisar("EXCLUIDO", l.id(), rotulo(l, "L") + ": sem vigas nem pilares de apoio — a carga da laje não entra na análise.");
            return;
        }
        for (var nat : NaturezaAcao.values()) {
            double total = q[nat.ordinal()] * area;
            if (total == 0) {
                continue;
            }
            if (somaL > 0) {
                for (var v : vigasContorno) {
                    double porMetro = total / somaL;
                    for (int idBarra : barrasPorElemento.get(v.id())) {
                        ma.caso(nat).distribuidas().add(new ModeloAnalitico.CargaDistribuida(idBarra, 0, 0, -porMetro));
                    }
                }
            } else {
                for (var p : pilares) {
                    int nj = ma.barras().get(barrasPorElemento.get(p.id()).getFirst()).noJ();
                    ma.caso(nat).nodais().add(new ModeloAnalitico.CargaNodal(nj, 0, 0, -total / pilares.size(), 0, 0, 0));
                }
            }
        }
    }

    private static boolean noContorno(Ponto p, List<Ponto> contorno) {
        for (int i = 0; i < contorno.size(); i++) {
            var a = contorno.get(i); var b = contorno.get((i + 1) % contorno.size());
            double l = a.distancia(b);
            if (l < 1e-9) {
                continue;
            }
            double t = ((p.x() - a.x()) * (b.x() - a.x()) + (p.y() - a.y()) * (b.y() - a.y())) / (l * l);
            t = Math.max(0, Math.min(1, t));
            var proj = new Ponto(a.x() + t * (b.x() - a.x()), a.y() + t * (b.y() - a.y()));
            if (proj.distancia(p) <= TOL_LIGACAO) {
                return true;
            }
        }
        return false;
    }

    private static double larguraEmY(ModeloEstrutural m) {
        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        for (var e : m.elementos()) {
            if (e.centro() != null) {
                min = Math.min(min, e.centro().y());
                max = Math.max(max, e.centro().y());
            }
        }
        return max > min ? max - min : 10.0;
    }
}
