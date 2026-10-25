package br.com.civilconstructorai.estrutura.dimensionamento;

import br.com.civilconstructorai.estrutura.carga.Carga;
import br.com.civilconstructorai.estrutura.carga.Combinacao;
import br.com.civilconstructorai.estrutura.carga.NaturezaAcao;
import br.com.civilconstructorai.estrutura.carga.TipoCarga;
import br.com.civilconstructorai.estrutura.mef.MontadorModeloAnalitico;
import br.com.civilconstructorai.estrutura.mef.SolverMef;
import br.com.civilconstructorai.estrutura.modelo.ElementoEstrutural;
import br.com.civilconstructorai.estrutura.modelo.ModeloEstrutural;
import br.com.civilconstructorai.estrutura.modelo.TipoElementoEstrutural;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static br.com.civilconstructorai.estrutura.dimensionamento.Verificacao.Estado;

/**
 * Dimensionamento e verificação de vigas, pilares e lajes de concreto armado pela ABNT NBR 6118:2023 a partir dos
 * esforços CALCULADOS por uma análise linear (nunca medidos). Separa as etapas: solicitações (análise), verificação de
 * resistência e serviço, dimensionamento das armaduras e detalhamento. Toda verificação tem estado explícito; o que o
 * método não cobre aparece como NAO_VERIFICADA, nunca como aprovação. Contraventamentos metálicos entram com as
 * verificações da NBR 8800 marcadas como não implementadas.
 */
public final class DimensionadorNbr6118 {

    public static final String NBR = "ABNT NBR 6118:2023";
    /** limite abaixo do qual um esforço secundário é tratado como nulo (kN·m) */
    static final double DESPREZIVEL = 1.0;

    /** material resolvido no catálogo (ou personalizado da empresa) */
    public record InfoMaterial(String codigo, String nome, Double fck, boolean personalizado, String situacaoValidacao) {
    }

    public record Entrada(ModeloEstrutural modelo, SolverMef.ResultadoAnalise analise, List<Carga> cargas, String caa,
                          Function<String, Optional<InfoMaterial>> materiais, double fykAco, double eta1Aco, String categoriaAco,
                          Function<Double, Double> moduloPorFck) {
    }

    public record ResultadoElemento(String elementoId, String rotulo, String tipo, String estado, boolean verifica, String motivo,
                                    double mdKnm, double vdKn, double ndKn, String armaduraLongitudinal, String estribos,
                                    double asCm2, double acoKg, List<String> memoria, List<Verificacao> verificacoes,
                                    Detalhamento.Resultado detalhamento, String material, Double asInferiorCm2, Double asSuperiorCm2, Double massaPerfilKg,
                                    String estadoDetalhamento) {
    }

    private final Entrada in;
    private final List<SolverMef.ResultadoCombinacao> elu;
    private final SolverMef.ResultadoCombinacao qp;
    private final SolverMef.ResultadoCombinacao freq;

    public DimensionadorNbr6118(Entrada in) {
        this.in = in;
        this.elu = in.analise().porCombinacao().values().stream().filter(c -> c.combinacao().estadoLimite() == Combinacao.EstadoLimite.ELU_NORMAL).toList();
        this.qp = in.analise().porCombinacao().values().stream().filter(c -> c.combinacao().estadoLimite() == Combinacao.EstadoLimite.ELS_QUASE_PERMANENTE).findFirst().orElse(null);
        this.freq = in.analise().porCombinacao().values().stream().filter(c -> c.combinacao().estadoLimite() == Combinacao.EstadoLimite.ELS_FREQUENTE).findFirst().orElse(null);
    }

    public List<ResultadoElemento> dimensionar() {
        var out = new ArrayList<ResultadoElemento>();
        for (var e : in.modelo().elementos()) {
            if (e.rascunhoIa()) {
                continue;
            }
            switch (e.tipo()) {
                case VIGA -> out.add(viga(e));
                case PILAR -> out.add(pilar(e));
                case LAJE -> out.add(laje(e));
                case CONTRAVENTAMENTO -> out.add(contraventamento(e));
                default -> { }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ comuns
    /** cobrimento nominal (m) pela CAA, NBR 6118 tabela 7.2 (Δc = 10 mm) */
    static double cobrimento(String caa, boolean laje) {
        String c = caa == null ? "II" : caa;
        return switch (c) {
            case "I" -> laje ? 0.020 : 0.025;
            case "III" -> laje ? 0.035 : 0.040;
            case "IV" -> laje ? 0.045 : 0.050;
            default -> laje ? 0.025 : 0.030;
        };
    }

    /** classe mínima de concreto por CAA (NBR 6118, tabela 7.1, concreto armado) */
    static double fckMinimo(String caa) {
        return switch (caa) {
            case "I" -> 20;
            case "III" -> 30;
            case "IV" -> 40;
            default -> 25;
        };
    }

    /** verificações de material e durabilidade; devolve o fck quando o cálculo pode prosseguir */
    private Double materialEDurabilidade(ElementoEstrutural e, List<Verificacao> vs, List<String> mem, boolean laje) {
        var info = e.materialId() == null ? Optional.<InfoMaterial>empty() : in.materiais().apply(e.materialId());
        if (info.isEmpty() || info.get().fck() == null) {
            vs.add(Verificacao.dadosInsuficientes("MAT", "Material com resistência definida", "VERIFICACAO", "catálogo técnico",
                    e.materialId() == null ? "Elemento sem material: atribua um concreto do catálogo." : "Material " + e.materialId() + " sem fck no catálogo."));
            return null;
        }
        var m = info.get();
        mem.add(String.format(Locale.ROOT, "Material: %s (%s), fck = %.0f MPa%s", m.nome(), m.codigo(), m.fck(), m.personalizado() ? " — personalizado" : ""));
        if (m.personalizado() && !"VALIDADO".equals(m.situacaoValidacao())) {
            vs.add(Verificacao.dadosInsuficientes("MAT-VAL", "Material personalizado validado", "VERIFICACAO", null,
                    "Material personalizado com validação pendente: resultados não podem ser considerados atendidos."));
        }
        if (in.caa() == null) {
            vs.add(Verificacao.dadosInsuficientes("DUR", "Durabilidade: classe de agressividade e cobrimento", "VERIFICACAO", NBR + ", 7.4",
                    "Classe de agressividade ambiental não informada na base de cálculo: cobrimento da CAA II adotado como premissa ("
                            + Math.round(cobrimento(null, laje) * 1000) + " mm)."));
        } else if (m.fck() < fckMinimo(in.caa())) {
            vs.add(Verificacao.naoAtendida("DUR", "Durabilidade: classe mínima de concreto", "VERIFICACAO", NBR + ", tabela 7.1",
                    String.format(Locale.ROOT, "CAA %s exige ≥ C%.0f; adotado C%.0f", in.caa(), fckMinimo(in.caa()), m.fck()), "Concreto abaixo da classe mínima para a agressividade."));
        } else {
            vs.add(Verificacao.atendida("DUR", "Durabilidade: classe mínima de concreto e cobrimento", "VERIFICACAO", NBR + ", tabelas 7.1 e 7.2",
                    String.format(Locale.ROOT, "CAA %s: C%.0f ≥ C%.0f; cobrimento nominal %d mm", in.caa(), m.fck(), fckMinimo(in.caa()), Math.round(cobrimento(in.caa(), laje) * 1000))));
        }
        if (m.fck() > 50) {
            vs.add(Verificacao.naoVerificada("ESCOPO", "Escopo do método", "VERIFICACAO", NBR,
                    "Concreto do grupo II (fck > 50 MPa): as fórmulas implementadas valem até C50."));
            return null;
        }
        return m.fck();
    }

    private ResultadoElemento montar(ElementoEstrutural e, String tipo, List<Verificacao> vs, List<String> mem, double md, double vd, double nd,
                                     String longitudinal, String estribos, double asCm2, Detalhamento.Resultado det, String material,
                                     Double asInf, Double asSup, Double massaPerfil) {
        // o detalhamento (simplificado) tem estado próprio; o estado do elemento cobre análise, verificação e dimensionamento
        var estado = Verificacao.consolidar(vs.stream().filter(v -> !"DET".equals(v.codigo())).toList());
        String estadoDet = vs.stream().filter(v -> "DET".equals(v.codigo())).map(v -> v.estado().name()).findFirst().orElse(Estado.NAO_VERIFICADA.name());
        String motivo = estado == Estado.ATENDIDA ? "Todas as verificações aplicáveis atendidas" : vs.stream()
                .filter(v -> v.estado() == estado && !"DET".equals(v.codigo())).map(v -> v.nome() + ": " + (v.motivo() != null ? v.motivo() : v.evidencia())).findFirst().orElse(estado.name());
        double kg = det == null ? 0 : Math.round(det.massaLiquidaKg() * 10) / 10.0;
        return new ResultadoElemento(e.id(), e.rotulo() == null ? tipo.substring(0, 1) : e.rotulo(), tipo, estado.name(), estado == Estado.ATENDIDA, motivo,
                r2(md), r2(vd), r2(nd), longitudinal, estribos, r2(asCm2), kg, mem, vs, det, material, asInf, asSup, massaPerfil, estadoDet);
    }

    private List<SolverMef.ResultadoBarra> barrasDe(SolverMef.ResultadoCombinacao c, String elementoId) {
        return c.barras().stream().filter(b -> elementoId.equals(b.elementoId())).toList();
    }

    // ------------------------------------------------------------------ viga
    private ResultadoElemento viga(ElementoEstrutural v) {
        var vs = new ArrayList<Verificacao>();
        var mem = new ArrayList<String>();
        Double fck = materialEDurabilidade(v, vs, mem, false);
        double c = cobrimento(in.caa(), false);
        var trechos = elu.isEmpty() ? List.<SolverMef.ResultadoBarra>of() : barrasDe(elu.getFirst(), v.id());
        if (trechos.isEmpty()) {
            vs.add(Verificacao.dadosInsuficientes("ANALISE", "Esforços da análise", "ANALISE", null, "A viga não está no modelo analítico (veja os avisos da análise)."));
            return montar(v, "VIGA", vs, mem, 0, 0, 0, null, null, 0, null, matDesc(v), null, null, null);
        }
        // apoios (posições ao longo da viga) e envoltórias por vão / apoio
        int nv = trechos.size();
        var apoios = new double[nv + 1];
        for (int k = 0; k < nv; k++) {
            apoios[k + 1] = apoios[k] + trechos.get(k).comprimento();
        }
        var mPos = new double[nv];
        var mNeg = new double[nv + 1];
        double vMax = 0, tMax = 0, mzMax = 0, nMax = 0;
        for (var comb : elu) {
            var bs = barrasDe(comb, v.id());
            for (int k = 0; k < bs.size() && k < nv; k++) {
                var b = bs.get(k);
                mPos[k] = Math.max(mPos[k], Math.max(0, b.mMax()));
                mNeg[k] = Math.max(mNeg[k], Math.max(0, -b.pontos().getFirst().my()));
                mNeg[k + 1] = Math.max(mNeg[k + 1], Math.max(0, -b.pontos().getLast().my()));
                vMax = Math.max(vMax, b.vMax());
                tMax = Math.max(tMax, b.tMaxAbs() == null ? 0 : b.tMaxAbs());
                mzMax = Math.max(mzMax, b.mzMaxAbs() == null ? 0 : b.mzMaxAbs());
                nMax = Math.max(nMax, b.nMax());
            }
        }
        double mdPos = max(mPos), mdNeg = max(mNeg);
        mem.add(String.format(Locale.ROOT, "Solicitações calculadas (MEF linear, envoltória de %d combinações ELU): Md+ = %.2f kN·m; Md− = %.2f kN·m; Vd = %.2f kN; Td = %.2f kN·m",
                elu.size(), mdPos, mdNeg, vMax, tMax));
        if (fck == null) {
            for (var cod : List.of("FLEX+", "FLEX-", "CIS", "ELS-F", "ELS-W")) {
                vs.add(Verificacao.naoVerificada(cod, nomeVerificacao(cod), "VERIFICACAO", NBR, "Sem material válido."));
            }
            return montar(v, "VIGA", vs, mem, Math.max(mdPos, mdNeg), vMax, nMax, null, null, 0, null, matDesc(v), null, null, null);
        }
        double d = v.h() - c - 0.0063 - 0.0125 / 2;
        mem.add(String.format(Locale.ROOT, "d = h − c − φt − φl/2 = %.3f m (premissa φt = 6,3 mm, φl = 12,5 mm); c = %.0f mm", d, c * 1000));
        // flexão: armadura inferior (M+) e superior (M−) separadas
        var fpos = Nbr6118.flexaoMd(Math.max(mdPos, 0.01), v.b(), v.h(), d, fck, in.fykAco());
        mem.addAll(fpos.memoria().linhas());
        vs.add(fpos.verifica() ? Verificacao.atendida("FLEX+", "Flexão — momento positivo (armadura inferior)", "DIMENSIONAMENTO", NBR + ", 17.2",
                String.format(Locale.ROOT, "Md+ = %.2f kN·m; As = %.2f cm²; x/d = %.3f", mdPos, fpos.asCm2(), fpos.xSobreD()))
                : Verificacao.naoAtendida("FLEX+", "Flexão — momento positivo (armadura inferior)", "DIMENSIONAMENTO", NBR + ", 17.2",
                String.format(Locale.ROOT, "Md+ = %.2f kN·m", mdPos), fpos.motivo()));
        var inferior = Bitolas.escolher(Double.isNaN(fpos.asCm2()) ? fpos.asMinCm2() : fpos.asCm2(), 2, 10);
        Nbr6118.Flexao fneg = null;
        Bitolas.Arranjo superior = null;
        var asSupApoio = new double[nv + 1];
        if (mdNeg > DESPREZIVEL) {
            fneg = Nbr6118.flexaoMd(mdNeg, v.b(), v.h(), d, fck, in.fykAco());
            mem.addAll(fneg.memoria().linhas());
            vs.add(fneg.verifica() ? Verificacao.atendida("FLEX-", "Flexão — momento negativo nos apoios (armadura superior)", "DIMENSIONAMENTO", NBR + ", 17.2",
                    String.format(Locale.ROOT, "Md− = %.2f kN·m; As = %.2f cm²", mdNeg, fneg.asCm2()))
                    : Verificacao.naoAtendida("FLEX-", "Flexão — momento negativo nos apoios (armadura superior)", "DIMENSIONAMENTO", NBR + ", 17.2",
                    String.format(Locale.ROOT, "Md− = %.2f kN·m", mdNeg), fneg.motivo()));
            superior = Bitolas.escolher(Double.isNaN(fneg.asCm2()) ? fneg.asMinCm2() : fneg.asCm2(), 2, 10);
            for (int k = 0; k <= nv; k++) {
                if (mNeg[k] > DESPREZIVEL) {
                    var f = Nbr6118.flexaoMd(mNeg[k], v.b(), v.h(), d, fck, in.fykAco());
                    asSupApoio[k] = Double.isNaN(f.asCm2()) ? f.asMinCm2() : f.asCm2();
                }
            }
        } else {
            vs.add(Verificacao.naoAplicavel("FLEX-", "Flexão — momento negativo nos apoios", "DIMENSIONAMENTO", "Sem momento negativo relevante nas combinações (|Md−| < 1 kN·m)."));
        }
        // disposição em uma camada
        double phiT = 0.0063;
        double livre = (v.b() - 2 * c - 2 * phiT - inferior.barras() * inferior.bitola().mm() / 1000) / Math.max(1, inferior.barras() - 1);
        double livreMin = Math.max(0.020, inferior.bitola().mm() / 1000);
        vs.add(livre >= livreMin ? Verificacao.atendida("DISP", "Disposição das barras inferiores em uma camada", "DETALHAMENTO", NBR + ", 18.3.2.2",
                String.format(Locale.ROOT, "espaço livre %.1f mm ≥ %.1f mm (diâmetro do agregado não informado: não considerado)", livre * 1000, livreMin * 1000))
                : Verificacao.naoAtendida("DISP", "Disposição das barras inferiores em uma camada", "DETALHAMENTO", NBR + ", 18.3.2.2",
                String.format(Locale.ROOT, "%s: espaço livre %.1f mm < %.1f mm", inferior.descricao(), livre * 1000, livreMin * 1000),
                "As barras não cabem em uma camada; aumente b ou use bitola maior (detalhamento em duas camadas não implementado)."));
        // cisalhamento
        var cis = Nbr6118.cisalhamentoVd(vMax, v.b(), d, fck);
        mem.addAll(cis.memoria().linhas());
        vs.add(cis.verifica() ? Verificacao.atendida("CIS", "Cisalhamento (biela e estribos, modelo I)", "DIMENSIONAMENTO", NBR + ", 17.4.2.2",
                String.format(Locale.ROOT, "Vsd = %.2f kN ≤ VRd2 = %.2f kN; Asw/s = %.2f cm²/m", vMax, cis.vrd2(), cis.aswPorMetroCm2()))
                : Verificacao.naoAtendida("CIS", "Cisalhamento (biela comprimida)", "DIMENSIONAMENTO", NBR + ", 17.4.2.2",
                String.format(Locale.ROOT, "Vsd = %.2f kN > VRd2 = %.2f kN", vMax, cis.vrd2()), cis.motivo()));
        var estribo = Bitolas.estribo(Double.isNaN(cis.aswPorMetroCm2()) ? cis.aswMinPorMetroCm2() : cis.aswPorMetroCm2(), cis.sMaxCm() > 0 ? cis.sMaxCm() : 20);
        // torção, flexão lateral e esforço normal: não cobertos pelo método
        vs.add(tMax > DESPREZIVEL ? Verificacao.naoVerificada("TOR", "Torção", "VERIFICACAO", NBR + ", 17.5", String.format(Locale.ROOT, "Td = %.2f kN·m: verificação de torção não implementada.", tMax))
                : Verificacao.naoAplicavel("TOR", "Torção", "VERIFICACAO", String.format(Locale.ROOT, "Td = %.2f kN·m < 1 kN·m", tMax)));
        vs.add(mzMax > DESPREZIVEL ? Verificacao.naoVerificada("FLEX-OBL", "Flexão em torno do eixo vertical (oblíqua)", "VERIFICACAO", NBR + ", 17.2",
                String.format(Locale.ROOT, "|Mz| = %.2f kN·m: flexão oblíqua de viga não implementada.", mzMax))
                : Verificacao.naoAplicavel("FLEX-OBL", "Flexão em torno do eixo vertical", "VERIFICACAO", "|Mz| < 1 kN·m"));
        double nLimite = 0.1 * Nbr6118.fcd(fck) * 1000 * v.b() * v.h();
        vs.add(nMax > nLimite ? Verificacao.naoVerificada("FLEX-COMP", "Flexão composta", "VERIFICACAO", NBR + ", 17.2", String.format(Locale.ROOT, "|Nd| = %.1f kN > 0,1·fcd·Ac: flexão composta em viga não implementada.", nMax))
                : Verificacao.naoAplicavel("FLEX-COMP", "Flexão composta", "VERIFICACAO", String.format(Locale.ROOT, "|Nd| = %.1f kN ≤ 0,1·fcd·Ac = %.1f kN", nMax, nLimite)));
        // ELS: flecha (quase-permanente) e fissuração (frequente)
        double ecs = in.moduloPorFck().apply(fck);
        double lMax = trechos.stream().mapToDouble(SolverMef.ResultadoBarra::comprimento).max().orElse(v.comprimento());
        if (qp != null) {
            double mQp = barrasDe(qp, v.id()).stream().mapToDouble(SolverMef.ResultadoBarra::mMax).max().orElse(0);
            double qEq = 8 * Math.max(mQp, 0) / Math.max(lMax * lMax, 1e-6);
            var fl = Nbr6118.flecha(qEq, lMax, v.b(), v.h(), d, inferior.areaCm2(), fck, ecs);
            mem.addAll(fl.memoria().linhas());
            mem.add("Flecha: carga uniforme equivalente ao maior momento positivo quase-permanente no maior vão (aproximação para vigas contínuas).");
            vs.add(fl.verifica() ? Verificacao.atendida("ELS-F", "Deformação excessiva (flecha total)", "VERIFICACAO", NBR + ", 17.3.2 e tabela 13.3",
                    String.format(Locale.ROOT, "f∞ = %.2f mm ≤ L/250 = %.2f mm", fl.flechaMm(), fl.limiteMm()))
                    : Verificacao.naoAtendida("ELS-F", "Deformação excessiva (flecha total)", "VERIFICACAO", NBR + ", 17.3.2 e tabela 13.3",
                    String.format(Locale.ROOT, "f∞ = %.2f mm > L/250 = %.2f mm", fl.flechaMm(), fl.limiteMm()), "Aumente h ou a armadura; considere contraflecha."));
        } else {
            vs.add(Verificacao.dadosInsuficientes("ELS-F", "Deformação excessiva (flecha total)", "VERIFICACAO", NBR, "Sem combinação quase-permanente na análise."));
        }
        if (freq != null) {
            double mF = barrasDe(freq, v.id()).stream().mapToDouble(SolverMef.ResultadoBarra::mMax).max().orElse(0);
            double wkLim = wkLimite(in.caa());
            var fis = Nbr6118.fissuracao(Math.max(mF, 0), d, inferior.areaCm2(), inferior.bitola().mm(), fck, wkLim);
            mem.addAll(fis.memoria().linhas());
            vs.add(fis.verifica() ? Verificacao.atendida("ELS-W", "Abertura de fissuras", "VERIFICACAO", NBR + ", 17.3.3 e tabela 13.4",
                    String.format(Locale.ROOT, "wk = %.3f mm ≤ %.1f mm", fis.wkMm(), wkLim))
                    : Verificacao.naoAtendida("ELS-W", "Abertura de fissuras", "VERIFICACAO", NBR + ", 17.3.3 e tabela 13.4",
                    String.format(Locale.ROOT, "wk = %.3f mm > %.1f mm", fis.wkMm(), wkLim), "Use mais barras de bitola menor ou aumente a armadura."));
        } else {
            vs.add(Verificacao.dadosInsuficientes("ELS-W", "Abertura de fissuras", "VERIFICACAO", NBR, "Sem combinação frequente na análise."));
        }
        // detalhamento e geometria das barras
        double z = in.modelo().pavimento(v.pavimentoId()).map(p -> p.elevacao()).orElse(0.0);
        var par = new Detalhamento.Parametros(fck, in.fykAco(), in.eta1Aco(), in.categoriaAco(), c);
        var det = Detalhamento.viga(v.id(), v.rotulo() == null ? "V" : v.rotulo(), new double[]{v.p1().x(), v.p1().y()}, new double[]{v.p2().x(), v.p2().y()}, z, v.b(), v.h(),
                apoios, inferior, superior, asSupApoio, estribo, Double.isNaN(fpos.asCm2()) ? fpos.asMinCm2() : fpos.asCm2(), par);
        vs.add(new Verificacao("DET", "Detalhamento (ancoragem, emendas, ganchos)", "DETALHAMENTO", Estado.NAO_VERIFICADA, NBR + ", item 9",
                "posições geradas com regras simplificadas", String.join(" ", det.limitacoes())));
        String longi = "inf.: " + inferior.descricao() + (superior != null ? " · sup.: " + superior.descricao() : "");
        return montar(v, "VIGA", vs, mem, Math.max(mdPos, mdNeg), vMax, nMax, longi, estribo.descricao(),
                inferior.areaCm2() + (superior == null ? 0 : superior.areaCm2()), det, matDesc(v), inferior.areaCm2(), superior == null ? 0.0 : superior.areaCm2(), null);
    }

    // ------------------------------------------------------------------ pilar
    private ResultadoElemento pilar(ElementoEstrutural p) {
        var vs = new ArrayList<Verificacao>();
        var mem = new ArrayList<String>();
        Double fck = materialEDurabilidade(p, vs, mem, false);
        double c = cobrimento(in.caa(), false);
        double l = in.modelo().alturaPilar(p);
        if (elu.isEmpty() || barrasDe(elu.getFirst(), p.id()).isEmpty()) {
            vs.add(Verificacao.dadosInsuficientes("ANALISE", "Esforços da análise", "ANALISE", null, "O pilar não está no modelo analítico."));
            return montar(p, "PILAR", vs, mem, 0, 0, 0, null, null, 0, null, matDesc(p), null, null, null);
        }
        if (p.circular()) {
            vs.add(Verificacao.naoVerificada("FLEX-COMP", "Flexo-compressão", "DIMENSIONAMENTO", NBR + ", 17.2", "Seção circular: verificação não implementada."));
            return montar(p, "PILAR", vs, mem, 0, 0, 0, null, null, 0, null, matDesc(p), null, null, null);
        }
        // solicitações por combinação: Nd (compressão +), Md na direção de b (My local) e de h (Mz local)
        record Sol(String comb, double nd, double mx, double my) {
        }
        var sols = new ArrayList<Sol>();
        double vMax = 0;
        for (var comb : elu) {
            for (var b : barrasDe(comb, p.id())) {
                double nd = -(b.nMin() == null ? 0 : b.nMin());
                double mx = b.pontos().stream().mapToDouble(x -> Math.abs(x.my())).max().orElse(0);
                double my = b.pontos().stream().mapToDouble(x -> Math.abs(x.mz())).max().orElse(0);
                sols.add(new Sol(comb.combinacao().nome(), Math.max(nd, 0), mx, my));
                vMax = Math.max(vMax, b.vMax());
            }
        }
        double ndMax = sols.stream().mapToDouble(Sol::nd).max().orElse(0);
        mem.add(String.format(Locale.ROOT, "Solicitações calculadas (%d combinações ELU): Nd máx = %.2f kN; Md (dir. b) máx = %.2f kN·m; Md (dir. h) máx = %.2f kN·m",
                elu.size(), ndMax, sols.stream().mapToDouble(Sol::mx).max().orElse(0), sols.stream().mapToDouble(Sol::my).max().orElse(0)));
        if (fck == null) {
            vs.add(Verificacao.naoVerificada("FLEX-COMP", "Flexo-compressão oblíqua", "DIMENSIONAMENTO", NBR, "Sem material válido."));
            return montar(p, "PILAR", vs, mem, 0, vMax, ndMax, null, null, 0, null, matDesc(p), null, null, null);
        }
        double lambdaX = 3.46 * l / p.b(), lambdaY = 3.46 * l / p.h();
        mem.add(String.format(Locale.ROOT, "le = %.2f m (premissa: distância entre pavimentos); λ (dir. b) = %.1f; λ (dir. h) = %.1f", l, lambdaX, lambdaY));
        if (Math.max(lambdaX, lambdaY) > 90) {
            vs.add(Verificacao.naoVerificada("ESBELTEZ", "Pilar esbelto (λ > 90)", "VERIFICACAO", NBR + ", 15.8.3",
                    String.format(Locale.ROOT, "λ = %.1f > 90: exige método geral com não linearidade física e geométrica (não implementado).", Math.max(lambdaX, lambdaY))));
            return montar(p, "PILAR", vs, mem, 0, vMax, ndMax, null, null, 0, null, matDesc(p), null, null, null);
        }
        vs.add(Verificacao.atendida("ESBELTEZ", "Esbeltez dentro do método do pilar-padrão (λ ≤ 90)", "VERIFICACAO", NBR + ", 15.8.3.3",
                String.format(Locale.ROOT, "λ = %.1f ≤ 90", Math.max(lambdaX, lambdaY))));
        double fcdKpa = Nbr6118.fcd(fck) * 1000, fydKpa = in.fykAco() / Nbr6118.GAMA_S * 1000;
        double ac = p.b() * p.h();
        double asMin = Math.max(0.15 * ndMax / fydKpa, 0.004 * ac) * 1e4, asMax = 0.08 * ac * 1e4;
        // momentos totais de cálculo por combinação e direção (1ª ordem ≥ mínimo, + 2ª ordem local)
        record Md(String comb, double nd, double mdx, double mdy) {
        }
        var mds = new ArrayList<Md>();
        for (var s : sols) {
            mds.add(new Md(s.comb(), s.nd(), momentoTotal(s.nd(), s.mx(), p.b(), l, ac, fcdKpa), momentoTotal(s.nd(), s.my(), p.h(), l, ac, fcdKpa)));
        }
        var gov = mds.stream().max((a, b) -> Double.compare(a.mdx() + a.mdy(), b.mdx() + b.mdy())).orElseThrow();
        mem.add(String.format(Locale.ROOT, "Combinação de maior momento: %s — Nd = %.2f kN; Md,tot (dir. b) = %.2f kN·m; Md,tot (dir. h) = %.2f kN·m "
                + "(e1,min = 0,015 + 0,03·dimensão; 2ª ordem local pelo pilar-padrão com curvatura aproximada; αb = 1,0)", gov.comb(), gov.nd(), gov.mdx(), gov.mdy()));
        // procura o arranjo mais leve que atende a interação oblíqua simplificada (α = 1, 17.2.5.2) em todas as combinações
        double esCalc = 210_000;
        double phiTGuess = 0.005;
        Integer melhorN = null;
        Double melhorPhi = null;
        double melhorAs = Double.MAX_VALUE, melhorUso = Double.NaN;
        List<SecaoConcreto.Barra> melhorBarras = null;
        for (int n : new int[]{4, 6, 8, 10, 12, 14, 16}) {
            for (var bit : Bitolas.CA50) {
                if (bit.mm() < 10) {
                    continue;
                }
                double as = n * bit.areaCm2();
                if (as < asMin - 1e-9 || as > asMax || as >= melhorAs) {
                    continue;
                }
                double dLinha = c + phiTGuess + bit.mm() / 2000;
                List<SecaoConcreto.Barra> barras;
                try {
                    barras = SecaoConcreto.distribuir(p.b(), p.h(), n, bit.mm(), dLinha);
                } catch (IllegalArgumentException ex) {
                    continue;
                }
                if (!espacamentoOk(barras, bit.mm(), p.b(), p.h())) {
                    continue;
                }
                var secao = new SecaoConcreto(p.b(), p.h(), fck, in.fykAco(), esCalc, barras);
                double usoMax = 0;
                boolean ok = true;
                for (var md : mds) {
                    double mrx = secao.mRd(md.nd(), SecaoConcreto.Direcao.X), mry = secao.mRd(md.nd(), SecaoConcreto.Direcao.Y);
                    if (Double.isNaN(mrx) || Double.isNaN(mry) || mrx <= 0 || mry <= 0) {
                        ok = false;
                        break;
                    }
                    double uso = md.mdx() / mrx + md.mdy() / mry;
                    usoMax = Math.max(usoMax, uso);
                    if (uso > 1 + 1e-9) {
                        ok = false;
                        break;
                    }
                }
                if (ok) {
                    melhorAs = as;
                    melhorN = n;
                    melhorPhi = bit.mm();
                    melhorUso = usoMax;
                    melhorBarras = barras;
                }
            }
        }
        if (melhorN == null) {
            vs.add(Verificacao.naoAtendida("FLEX-COMP", "Flexo-compressão oblíqua", "DIMENSIONAMENTO", NBR + ", 17.2.5",
                    String.format(Locale.ROOT, "Nd = %.1f kN; nenhum arranjo até 8 %% de Ac atende", gov.nd()), "Seção insuficiente: aumente as dimensões do pilar ou o fck."));
            return montar(p, "PILAR", vs, mem, gov.mdx() + gov.mdy(), vMax, ndMax, null, null, 0, null, matDesc(p), null, null, null);
        }
        vs.add(Verificacao.atendida("FLEX-COMP", "Flexo-compressão oblíqua (interação simplificada α = 1)", "DIMENSIONAMENTO", NBR + ", 17.2.5.2",
                String.format(Locale.ROOT, "%d Ø %.1f mm: Σ Md/MRd = %.3f ≤ 1 na combinação mais desfavorável; As = %.2f cm² entre As,mín = %.2f e As,máx = %.2f cm²",
                        melhorN, melhorPhi, melhorUso, melhorAs, asMin, asMax)));
        Bitolas.Estribo est = Bitolas.estribo(0.5, Math.min(Math.min(20, 12 * melhorPhi / 10), Math.min(p.b(), p.h()) * 100), Math.max(5, melhorPhi / 4));
        vs.add(Verificacao.atendida("EST", "Estribos (diâmetro e espaçamento máximos)", "DETALHAMENTO", NBR + ", 18.4.3",
                String.format(Locale.ROOT, "φt = %.1f mm ≥ máx(5; φl/4); s = %.1f cm ≤ mín(20 cm; menor dimensão; 12φl)", est.mm(), est.espacamentoCm())));
        if (vMax > DESPREZIVEL) {
            // a favor da segurança: menor largura como bw e maior dimensão como altura útil
            double bw = Math.min(p.b(), p.h()), dCis = Math.max(p.b(), p.h()) - c - est.mm() / 1000 - melhorPhi / 2000;
            var cisP = Nbr6118.cisalhamentoVd(vMax, bw, dCis, fck);
            mem.addAll(cisP.memoria().linhas());
            double aswEst = 2 * Math.PI * est.mm() * est.mm() / 4 / 100 / (est.espacamentoCm() / 100);
            if (!cisP.verifica()) {
                vs.add(Verificacao.naoAtendida("CIS", "Cisalhamento no pilar", "VERIFICACAO", NBR + ", 17.4.2.2", String.format(Locale.ROOT, "Vsd = %.2f kN > VRd2 = %.2f kN", vMax, cisP.vrd2()), cisP.motivo()));
            } else if (aswEst + 1e-9 < cisP.aswPorMetroCm2()) {
                est = Bitolas.estribo(cisP.aswPorMetroCm2(), est.espacamentoCm(), Math.max(5, melhorPhi / 4));
                vs.add(Verificacao.atendida("CIS", "Cisalhamento no pilar", "VERIFICACAO", NBR + ", 17.4.2.2",
                        String.format(Locale.ROOT, "Vsd = %.2f kN; estribos aumentados para %s (Asw/s exigido %.2f cm²/m)", vMax, est.descricao(), cisP.aswPorMetroCm2())));
            } else {
                vs.add(Verificacao.atendida("CIS", "Cisalhamento no pilar", "VERIFICACAO", NBR + ", 17.4.2.2",
                        String.format(Locale.ROOT, "Vsd = %.2f kN ≤ VRd2 = %.2f kN; estribos construtivos: %.2f ≥ %.2f cm²/m", vMax, cisP.vrd2(), aswEst, cisP.aswPorMetroCm2())));
            }
        } else {
            vs.add(Verificacao.naoAplicavel("CIS", "Cisalhamento no pilar", "VERIFICACAO", "Vd < 1 kN"));
        }
        boolean continua = in.modelo().doTipo(TipoElementoEstrutural.PILAR).stream().anyMatch(o -> o != p && o.centro().distancia(p.centro()) < 0.05
                && p.nivelTopoId() != null && p.nivelTopoId().equals(o.nivelBaseId() == null ? o.pavimentoId() : o.nivelBaseId()));
        var base = in.modelo().pavimento(p.nivelBaseId() == null ? p.pavimentoId() : p.nivelBaseId()).map(x -> x.elevacao()).orElse(0.0);
        var par = new Detalhamento.Parametros(fck, in.fykAco(), in.eta1Aco(), in.categoriaAco(), c);
        var det = Detalhamento.pilar(p.id(), p.rotulo() == null ? "P" : p.rotulo(), p.centro().x(), p.centro().y(), p.rotacao(), base, l, p.b(), p.h(), melhorBarras, melhorPhi, continua, est, par);
        vs.add(new Verificacao("DET", "Detalhamento (emendas e ganchos)", "DETALHAMENTO", Estado.NAO_VERIFICADA, NBR + ", item 9 e 18.4",
                "posições geradas com regras simplificadas", String.join(" ", det.limitacoes())));
        String longi = String.format(Locale.ROOT, "%d Ø %.1f mm %s (%.2f cm²)", melhorN, melhorPhi, in.categoriaAco(), melhorAs);
        return montar(p, "PILAR", vs, mem, gov.mdx() + gov.mdy(), vMax, ndMax, longi, est.descricao(), melhorAs, det, matDesc(p), null, null, null);
    }

    /** Md,tot = máx(M1d, Nd·e1,min) + M2d (pilar-padrão, curvatura aproximada, quando λ > λ1); dimensão na direção do momento */
    private static double momentoTotal(double nd, double m1, double dim, double l, double ac, double fcdKpa) {
        double m1min = nd * (0.015 + 0.03 * dim);
        double m1d = Math.max(m1, m1min);
        double lambda = 3.46 * l / dim;
        double e1 = nd > 0 ? m1d / nd : 0;
        double lambda1 = Math.max(35, Math.min(90, 25 + 12.5 * e1 / dim));
        double m2 = 0;
        if (lambda > lambda1 && nd > 0) {
            double nu = nd / (ac * fcdKpa);
            double curv = Math.min(0.005 / (dim * (nu + 0.5)), 0.005 / dim);
            m2 = nd * l * l / 10 * curv;
        }
        return m1d + m2;
    }

    /** espaçamento livre ≥ máx(20 mm; φ) e entre eixos ≤ mín(2·menor dimensão; 400 mm) (NBR 6118, 18.4.2.2) */
    private static boolean espacamentoOk(List<SecaoConcreto.Barra> barras, double phiMm, double b, double h) {
        double minLivre = Math.max(0.020, phiMm / 1000), maxEixos = Math.min(2 * Math.min(b, h), 0.40);
        for (int i = 0; i < barras.size(); i++) {
            double dMin = Double.MAX_VALUE;
            for (int j = 0; j < barras.size(); j++) {
                if (i == j) {
                    continue;
                }
                dMin = Math.min(dMin, Math.hypot(barras.get(i).u() - barras.get(j).u(), barras.get(i).v() - barras.get(j).v()));
            }
            if (dMin - phiMm / 1000 < minLivre - 1e-9 || dMin > maxEixos + 1e-9) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ laje
    private ResultadoElemento laje(ElementoEstrutural s) {
        var vs = new ArrayList<Verificacao>();
        var mem = new ArrayList<String>();
        Double fck = materialEDurabilidade(s, vs, mem, true);
        double c = cobrimento(in.caa(), true);
        var pts = s.contorno();
        double xMin = pts.stream().mapToDouble(x -> x.x()).min().orElse(0), xMax = pts.stream().mapToDouble(x -> x.x()).max().orElse(0);
        double yMin = pts.stream().mapToDouble(x -> x.y()).min().orElse(0), yMax = pts.stream().mapToDouble(x -> x.y()).max().orElse(0);
        double dimX = xMax - xMin, dimY = yMax - yMin;
        double lx = Math.min(dimX, dimY), ly = Math.max(dimX, dimY), lambda = ly / Math.max(lx, 1e-9);
        // cargas reais da laje, por natureza (as mesmas regras do modelo analítico)
        var q = new LinkedHashMap<NaturezaAcao, Double>();
        q.put(NaturezaAcao.PERMANENTE, s.h() * MontadorModeloAnalitico.PESO_CONCRETO);
        double area = ElementoEstrutural.areaPoligono(pts);
        for (var cg : in.cargas()) {
            boolean alvo = cg.getElementoAlvoId() == null ? cg.getPavimentoId().equals(s.pavimentoId()) : cg.getElementoAlvoId().equals(s.id());
            if (!alvo) {
                continue;
            }
            if (cg.getTipo() == TipoCarga.AREA) {
                q.merge(cg.getNatureza(), cg.getValor(), Double::sum);
            } else if (cg.getTipo() == TipoCarga.PONTUAL && s.id().equals(cg.getElementoAlvoId())) {
                q.merge(cg.getNatureza(), cg.getValor() / Math.max(area, 1e-6), Double::sum);
            }
        }
        mem.add(String.format(Locale.ROOT, "Laje %.2f × %.2f m (retângulo envolvente), h = %.2f m; cargas por natureza (kN/m²): %s", dimX, dimY, s.h(), q));
        double qd = combinar(q, Combinacao.EstadoLimite.ELU_NORMAL), qQp = combinar(q, Combinacao.EstadoLimite.ELS_QUASE_PERMANENTE), qF = combinar(q, Combinacao.EstadoLimite.ELS_FREQUENTE);
        mem.add(String.format(Locale.ROOT, "pd (ELU, envoltória) = %.2f kN/m²; ELS quase-permanente = %.2f; frequente = %.2f", qd, qQp, qF));
        boolean temVigas = in.analise().avisos() == null || in.analise().avisos().stream().noneMatch(a -> s.id().equals(a.elementoId()) && a.mensagem().contains("sem vigas"));
        if (fck == null) {
            vs.add(Verificacao.naoVerificada("FLEX-X", "Flexão", "DIMENSIONAMENTO", NBR, "Sem material válido."));
            return montar(s, "LAJE", vs, mem, 0, 0, 0, null, "-", 0, null, matDesc(s), null, null, null);
        }
        double kx, mxd, myd;
        String metodo;
        if (lambda > 2) {
            kx = 1;
            mxd = qd * lx * lx / 8;
            myd = 0;
            metodo = String.format(Locale.ROOT, "armada em uma direção (λ = ly/lx = %.2f > 2): faixa de 1 m biapoiada, Md = pd·lx²/8", lambda);
        } else {
            kx = Math.pow(lambda, 4) / (1 + Math.pow(lambda, 4));
            mxd = kx * qd * lx * lx / 8;
            myd = (1 - kx) * qd * ly * ly / 8;
            metodo = String.format(Locale.ROOT, "armada em cruz (λ = %.2f): faixas cruzadas de Grashof (flechas iguais no centro), kx = λ⁴/(1+λ⁴) = %.3f, "
                    + "bordas simplesmente apoiadas, sem redução por torção (a favor da segurança)", lambda, kx);
        }
        mem.add("Método: " + metodo + ". Premissa: bordas simplesmente apoiadas, sem continuidade com lajes vizinhas.");
        double phiEst = 0.008;
        double d = s.h() - c - phiEst / 2;
        var fx = Nbr6118.flexaoMd(Math.max(mxd, 0.01), 1.0, s.h(), d, fck, in.fykAco());
        mem.addAll(fx.memoria().linhas());
        double asX = Double.isNaN(fx.asCm2()) ? fx.asMinCm2() : fx.asCm2();
        vs.add(fx.verifica() ? Verificacao.atendida("FLEX-X", "Flexão na direção do menor vão", "DIMENSIONAMENTO", NBR + ", 17.2 e 19.3",
                String.format(Locale.ROOT, "Md = %.2f kN·m/m; As = %.2f cm²/m", mxd, asX))
                : Verificacao.naoAtendida("FLEX-X", "Flexão na direção do menor vão", "DIMENSIONAMENTO", NBR + ", 17.2", String.format(Locale.ROOT, "Md = %.2f kN·m/m", mxd), fx.motivo()));
        double asY;
        if (lambda > 2) {
            double asMinRho = Nbr6118.rhoMin(fck) / 100 * 100 * s.h() * 100;
            asY = Math.max(Math.max(0.2 * asX, 0.9), 0.5 * asMinRho);
            vs.add(Verificacao.atendida("FLEX-Y", "Armadura secundária (distribuição)", "DETALHAMENTO", NBR + ", tabela 19.1",
                    String.format(Locale.ROOT, "As,sec = máx(0,2·As,princ; 0,9 cm²/m; 0,5·ρmín·b·h) = %.2f cm²/m", asY)));
        } else {
            var fy = Nbr6118.flexaoMd(Math.max(myd, 0.01), 1.0, s.h(), d - phiEst, fck, in.fykAco());
            mem.addAll(fy.memoria().linhas());
            asY = Double.isNaN(fy.asCm2()) ? fy.asMinCm2() : fy.asCm2();
            vs.add(fy.verifica() ? Verificacao.atendida("FLEX-Y", "Flexão na direção do maior vão", "DIMENSIONAMENTO", NBR + ", 17.2 e 19.3",
                    String.format(Locale.ROOT, "Md = %.2f kN·m/m; As = %.2f cm²/m", myd, asY))
                    : Verificacao.naoAtendida("FLEX-Y", "Flexão na direção do maior vão", "DIMENSIONAMENTO", NBR + ", 17.2", String.format(Locale.ROOT, "Md = %.2f kN·m/m", myd), fy.motivo()));
        }
        var mX = Bitolas.malha(asX, s.h());
        var mY = Bitolas.malha(asY, s.h());
        if (mX == null || mY == null) {
            vs.add(Verificacao.naoAtendida("DISP", "Malha possível (φ ≤ h/8; s ≤ mín(2h; 20 cm))", "DETALHAMENTO", NBR + ", 20.1",
                    String.format(Locale.ROOT, "As = %.2f / %.2f cm²/m", asX, asY), "Nenhuma bitola até 12,5 mm atende com os espaçamentos permitidos: aumente h."));
            return montar(s, "LAJE", vs, mem, mxd, 0, 0, null, "-", asX, null, matDesc(s), null, null, null);
        }
        // cisalhamento sem armadura transversal (19.4.1)
        double vsd = kx * qd * lx / 2;
        double rho1 = Math.min(0.02, mX.areaCm2PorM() / 1e4 / (1.0 * d));
        double k = Math.max(1, Math.abs(1.6 - d));
        double tauRd = 0.25 * Nbr6118.fctd(fck) * 1000;
        double vrd1 = tauRd * k * (1.2 + 40 * rho1) * 1.0 * d;
        mem.add(String.format(Locale.ROOT, "VSd = %.2f kN/m; VRd1 = τRd·k·(1,2 + 40ρ1)·bw·d = %.2f kN/m (τRd = 0,25·fctd; k = %.2f; ρ1 = %.4f)", vsd, vrd1, k, rho1));
        vs.add(vsd <= vrd1 ? Verificacao.atendida("CIS", "Cisalhamento sem armadura transversal", "VERIFICACAO", NBR + ", 19.4.1",
                String.format(Locale.ROOT, "VSd = %.2f ≤ VRd1 = %.2f kN/m", vsd, vrd1))
                : Verificacao.naoAtendida("CIS", "Cisalhamento sem armadura transversal", "VERIFICACAO", NBR + ", 19.4.1",
                String.format(Locale.ROOT, "VSd = %.2f > VRd1 = %.2f kN/m", vsd, vrd1), "Exige armadura transversal (não implementada para lajes) ou maior espessura."));
        // flecha (faixa na direção x) e fissuração
        double ecs = in.moduloPorFck().apply(fck);
        var fl = Nbr6118.flecha(kx * qQp, lx, 1.0, s.h(), d, mX.areaCm2PorM(), fck, ecs);
        mem.addAll(fl.memoria().linhas());
        vs.add(fl.verifica() ? Verificacao.atendida("ELS-F", "Deformação excessiva (faixa no menor vão)", "VERIFICACAO", NBR + ", 17.3.2 e tabela 13.3",
                String.format(Locale.ROOT, "f∞ = %.2f mm ≤ %.2f mm (aproximação por faixa; não é cálculo de placa)", fl.flechaMm(), fl.limiteMm()))
                : Verificacao.naoAtendida("ELS-F", "Deformação excessiva (faixa no menor vão)", "VERIFICACAO", NBR + ", 17.3.2",
                String.format(Locale.ROOT, "f∞ = %.2f mm > %.2f mm", fl.flechaMm(), fl.limiteMm()), "Aumente h."));
        double wkLim = wkLimite(in.caa());
        var fis = Nbr6118.fissuracao(kx * qF * lx * lx / 8, d, mX.areaCm2PorM(), mX.bitola().mm(), fck, wkLim);
        mem.addAll(fis.memoria().linhas());
        vs.add(fis.verifica() ? Verificacao.atendida("ELS-W", "Abertura de fissuras", "VERIFICACAO", NBR + ", 17.3.3", String.format(Locale.ROOT, "wk = %.3f mm ≤ %.1f mm", fis.wkMm(), wkLim))
                : Verificacao.naoAtendida("ELS-W", "Abertura de fissuras", "VERIFICACAO", NBR + ", 17.3.3", String.format(Locale.ROOT, "wk = %.3f mm > %.1f mm", fis.wkMm(), wkLim), "Reduza o espaçamento ou a bitola."));
        vs.add(temVigas ? Verificacao.naoAplicavel("PUNC", "Punção", "VERIFICACAO", "Laje apoiada em vigas.")
                : Verificacao.naoVerificada("PUNC", "Punção", "VERIFICACAO", NBR + ", 19.5", "Laje sem vigas de apoio: punção não implementada."));
        boolean vizinha = in.modelo().doPavimento(s.pavimentoId()).stream().anyMatch(o -> o != s && o.tipo() == TipoElementoEstrutural.LAJE && !o.rascunhoIa() && compartilhaBorda(s, o));
        vs.add(vizinha ? Verificacao.naoVerificada("PLACA", "Continuidade com laje vizinha", "ANALISE", NBR + ", 14.7.6",
                "Há laje vizinha com borda comum: momentos negativos de continuidade não calculados (bordas tratadas como apoiadas).")
                : Verificacao.naoAplicavel("PLACA", "Continuidade com laje vizinha", "ANALISE", "Sem laje vizinha com borda comum no pavimento."));
        double z = in.modelo().pavimento(s.pavimentoId()).map(p -> p.elevacao()).orElse(0.0);
        var par = new Detalhamento.Parametros(fck, in.fykAco(), in.eta1Aco(), in.categoriaAco(), c);
        boolean xMenor = dimX <= dimY;
        var det = Detalhamento.laje(s.id(), s.rotulo() == null ? "L" : s.rotulo(), xMin, xMax, yMin, yMax, z, s.h(),
                xMenor ? mX.bitola().mm() : mY.bitola().mm(), xMenor ? mX.espacamentoM() : mY.espacamentoM(),
                xMenor ? mY.bitola().mm() : mX.bitola().mm(), xMenor ? mY.espacamentoM() : mX.espacamentoM(), par);
        return montar(s, "LAJE", vs, mem, mxd, vsd, 0, "menor vão: " + mX.descricao() + " · maior vão: " + mY.descricao(), "-",
                mX.areaCm2PorM(), det, matDesc(s), mX.areaCm2PorM(), null, null);
    }

    /** abertura de fissura característica limite, concreto armado (NBR 6118, tabela 13.4): CAA I 0,4; II e III 0,3; IV 0,2 mm */
    static double wkLimite(String caa) {
        return "I".equals(caa) ? 0.4 : "IV".equals(caa) ? 0.2 : 0.3;
    }

    /** duas lajes compartilham borda quando os retângulos envolventes se tocam ao longo de um trecho (> 0,10 m) */
    static boolean compartilhaBorda(ElementoEstrutural a, ElementoEstrutural b) {
        double[] ra = caixa(a), rb = caixa(b);
        double tol = 0.05;
        double sobreX = Math.min(ra[1], rb[1]) - Math.max(ra[0], rb[0]), sobreY = Math.min(ra[3], rb[3]) - Math.max(ra[2], rb[2]);
        boolean tocaX = Math.abs(ra[1] - rb[0]) < tol || Math.abs(rb[1] - ra[0]) < tol;
        boolean tocaY = Math.abs(ra[3] - rb[2]) < tol || Math.abs(rb[3] - ra[2]) < tol;
        return (tocaX && sobreY > 0.10) || (tocaY && sobreX > 0.10);
    }

    private static double[] caixa(ElementoEstrutural e) {
        var p = e.contorno();
        return new double[]{p.stream().mapToDouble(x -> x.x()).min().orElse(0), p.stream().mapToDouble(x -> x.x()).max().orElse(0),
                p.stream().mapToDouble(x -> x.y()).min().orElse(0), p.stream().mapToDouble(x -> x.y()).max().orElse(0)};
    }

    private double combinar(Map<NaturezaAcao, Double> q, Combinacao.EstadoLimite el) {
        double max = 0;
        for (var c : in.analise().porCombinacao().values()) {
            if (c.combinacao().estadoLimite() != el) {
                continue;
            }
            double v = 0;
            for (var e : q.entrySet()) {
                v += c.combinacao().fator(e.getKey()) * e.getValue();
            }
            max = Math.max(max, v);
        }
        return max;
    }

    // ------------------------------------------------------------------ aço
    private ResultadoElemento contraventamento(ElementoEstrutural c) {
        var vs = new ArrayList<Verificacao>();
        var mem = new ArrayList<String>();
        double nt = 0, ncomp = 0, l = c.comprimento();
        for (var comb : elu) {
            for (var b : barrasDe(comb, c.id())) {
                nt = Math.max(nt, b.pontos().stream().mapToDouble(x -> x.n()).max().orElse(0));
                ncomp = Math.max(ncomp, -(b.nMin() == null ? 0 : b.nMin()));
            }
        }
        mem.add(String.format(Locale.ROOT, "Perfil %s; esforço normal calculado: tração máx. %.2f kN, compressão máx. %.2f kN", c.perfil(), nt, ncomp));
        var area = MontadorModeloAnalitico.areaPerfil(c.perfil());
        Double massa = area == null ? null : Math.round(area * l * 7850 * 10) / 10.0;
        vs.add(Verificacao.naoVerificada("NBR8800-T", "Tração (escoamento da seção bruta e ruptura da líquida)", "VERIFICACAO", "ABNT NBR 8800:2024, 5.2", "Dimensionamento de aço não implementado."));
        vs.add(Verificacao.naoVerificada("NBR8800-C", "Compressão com flambagem", "VERIFICACAO", "ABNT NBR 8800:2024, 5.3", "Dimensionamento de aço não implementado."));
        vs.add(Verificacao.naoVerificada("NBR8800-L", "Ligações", "VERIFICACAO", "ABNT NBR 8800:2024, item 6", "Ligações não modeladas."));
        return montar(c, "CONTRAVENTAMENTO", vs, mem, 0, 0, Math.max(nt, ncomp), c.perfil(), "-", 0, null, matDesc(c), null, null, massa);
    }

    private String matDesc(ElementoEstrutural e) {
        if (e.materialId() == null) {
            return "não atribuído";
        }
        return in.materiais().apply(e.materialId()).map(m -> m.nome() + " (" + m.codigo() + ")").orElse(e.materialId() + " (fora do catálogo)");
    }

    private static String nomeVerificacao(String cod) {
        return switch (cod) {
            case "FLEX+" -> "Flexão — momento positivo";
            case "FLEX-" -> "Flexão — momento negativo";
            case "CIS" -> "Cisalhamento";
            case "ELS-F" -> "Deformação excessiva";
            case "ELS-W" -> "Abertura de fissuras";
            default -> cod;
        };
    }

    private static double max(double[] v) {
        double m = 0;
        for (double x : v) {
            m = Math.max(m, x);
        }
        return m;
    }

    private static double r2(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
