package br.com.civilconstructorai.estrutura.dimensionamento;

import br.com.civilconstructorai.estrutura.carga.Carga;
import br.com.civilconstructorai.estrutura.carga.GeradorCombinacoes;
import br.com.civilconstructorai.estrutura.carga.NaturezaAcao;
import br.com.civilconstructorai.estrutura.carga.TipoCarga;
import br.com.civilconstructorai.estrutura.mef.MontadorModeloAnalitico;
import br.com.civilconstructorai.estrutura.mef.SolverMef;
import br.com.civilconstructorai.estrutura.modelo.ElementoEstrutural;
import br.com.civilconstructorai.estrutura.modelo.ModeloEstrutural;
import br.com.civilconstructorai.estrutura.modelo.PavimentoRef;
import br.com.civilconstructorai.estrutura.modelo.Ponto;
import br.com.civilconstructorai.notacao.CatalogoMateriais;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Referências do dimensionamento. Os valores esperados vêm de conta à mão (indicada em cada teste) ou de outra
 * formulação independente do mesmo resultado (equilíbrio da seção × fórmula fechada da flexão simples).
 */
class DimensionamentoReferenciaTest {

    static final UUID OBRA = UUID.randomUUID();

    /** 0,85·fcd·Ac + As·σs(2 ‰): C30, 20×40, 4 Ø 16 → 0,85·21,43 MPa·0,08 m² + 8,04 cm²·420 MPa = 1457,1 + 337,8 = 1794,9 kN */
    @Test
    void compressaoCentradaMaxima() {
        var barras = SecaoConcreto.distribuir(0.20, 0.40, 4, 16, 0.045);
        var s = new SecaoConcreto(0.20, 0.40, 30, 500, 210_000, barras);
        double as = 4 * Math.PI * 16 * 16 / 4 * 1e-6;
        double esperado = 0.85 * 30 / 1.4 * 1000 * 0.08 + as * 420_000;
        assertThat(s.nRdMax()).isCloseTo(esperado, within(0.5));
        assertThat(esperado).isCloseTo(1794.9, within(0.5));
    }

    /**
     * Flexão simples por dois caminhos independentes: a fórmula fechada (Nbr6118.flexaoMd) dá As para Md = 112 kN·m
     * em 20×50 (d = 45,5 cm); a seção com essa área numa barra a d resiste, por equilíbrio, MRd(N = 0) ≈ 112 kN·m.
     */
    @Test
    void flexaoSimplesBateComEquilibrioDaSecao() {
        double md = 112, b = 0.20, h = 0.50, d = 0.455;
        var f = Nbr6118.flexaoMd(md, b, h, d, 30, 500);
        assertThat(f.verifica()).isTrue();
        double asM2 = f.asCm2() / 1e4;
        // barra única no eixo, a d da face comprimida (face em v = +h/2)
        var s = new SecaoConcreto(b, h, 30, 500, 210_000, List.of(new SecaoConcreto.Barra(0, h / 2 - d, asM2)));
        double mrd = s.mRd(0, SecaoConcreto.Direcao.Y);
        // As foi arredondado a 0,01 cm² no resultado da fórmula fechada: tolerância de 0,5 %
        assertThat(mrd).isCloseTo(md, within(md * 0.005));
    }

    /**
     * Ancoragem básica Ø 12,5 CA-50 em C30, boa aderência: fctd = 0,7·0,3·30^(2/3)/1,4 = 1,448 MPa; fbd = 2,25·1,448 =
     * 3,259 MPa; lb = (12,5/4)·(434,8/3,259) = 417 mm (≈ 33φ).
     */
    @Test
    void ancoragemBasica() {
        var par = new Detalhamento.Parametros(30, 500, 2.25, "CA-50", 0.03);
        assertThat(par.lb(12.5, true)).isCloseTo(0.417, within(0.002));
        assertThat(par.lb(12.5, false)).isCloseTo(0.417 / 0.7, within(0.003));
        // emenda: α0t = 2,0 sobre lb,nec (As,ef = As,calc)
        assertThat(par.traspasse(12.5, true, 1)).isCloseTo(2 * 0.417, within(0.004));
    }

    /** CA-50 começa em 6,3 mm (barras); o fio de 5,0 mm é CA-60 — a lista antiga trazia "CA-50 Ø 5,0" */
    @Test
    void bitolasPorCategoria() {
        assertThat(Bitolas.CA50).extracting(Bitolas.Bitola::mm).doesNotContain(5.0).contains(6.3, 8.0, 10.0, 12.5, 16.0, 20.0, 25.0);
        assertThat(Bitolas.CA60).extracting(Bitolas.Bitola::mm).containsExactly(5.0);
        assertThat(Bitolas.CA50.stream().filter(x -> x.mm() == 10.0).findFirst().orElseThrow().kgPorMetro()).isCloseTo(0.617, within(0.001));
        var m = Bitolas.malha(3.0, 0.10);
        assertThat(m.espacamentoM()).isLessThanOrEqualTo(0.20);
        assertThat(m.bitola().mm() / 1000).isLessThanOrEqualTo(0.10 / 8);
        assertThat(m.areaCm2PorM()).isGreaterThanOrEqualTo(3.0);
    }

    // ------------------------------------------------------------------ pórtico completo
    static ModeloEstrutural portico(String material, double lajeH) {
        var els = new ArrayList<ElementoEstrutural>();
        double[][] cantos = {{0, 0}, {6, 0}, {6, 4}, {0, 4}};
        for (int k = 0; k < 4; k++) {
            els.add(ElementoEstrutural.pilar("P" + k, "t", new Ponto(cantos[k][0], cantos[k][1]), 0.20, 0.40, "t", "s", material));
        }
        for (int k = 0; k < 4; k++) {
            var a = cantos[k]; var b = cantos[(k + 1) % 4];
            els.add(ElementoEstrutural.viga("V" + k, "s", new Ponto(a[0], a[1]), new Ponto(b[0], b[1]), 0.20, 0.50, material));
        }
        els.add(ElementoEstrutural.laje("L1", "s", List.of(new Ponto(0, 0), new Ponto(6, 0), new Ponto(6, 4), new Ponto(0, 4)), lajeH, material));
        return new ModeloEstrutural(OBRA, 1, List.of(new PavimentoRef("t", "Térreo", 0), new PavimentoRef("s", "Superior", 3)), els);
    }

    static List<DimensionadorNbr6118.ResultadoElemento> dimensionar(ModeloEstrutural m, List<Carga> cargas, String caa) {
        var cat = CatalogoMateriais.padrao();
        var mods = new java.util.HashMap<String, Double>();
        for (var e : m.elementos()) {
            if (e.materialId() != null) {
                cat.valorCalculo(e.materialId(), "Ecs", null).ifPresent(v -> mods.put(e.materialId(), v * 1000));
            }
        }
        var ma = MontadorModeloAnalitico.montar(new MontadorModeloAnalitico.Entrada(m, cargas, null, mods));
        var r = new SolverMef(ma).resolver(GeradorCombinacoes.gerar(true, false));
        var entrada = new DimensionadorNbr6118.Entrada(m, r, cargas, caa,
                cod -> cat.item(cod).map(i -> new DimensionadorNbr6118.InfoMaterial(i.codigo(), i.nome(), i.numero("fck").isPresent() ? i.numero("fck").getAsDouble() : null, false, "CATALOGO")),
                500, 2.25, "CA-50", fck -> MontadorModeloAnalitico.moduloConcreto(fck));
        return new DimensionadorNbr6118(entrada).dimensionar();
    }

    static List<Carga> cargas(double sobrecarga) {
        return List.of(new Carga(UUID.randomUUID(), OBRA, "s", NaturezaAcao.PERMANENTE, TipoCarga.AREA, 1.5, null, "revestimento", null),
                new Carga(UUID.randomUUID(), OBRA, "s", NaturezaAcao.ACIDENTAL, TipoCarga.AREA, sobrecarga, null, "sobrecarga", null));
    }

    static DimensionadorNbr6118.ResultadoElemento de(List<DimensionadorNbr6118.ResultadoElemento> rs, String id) {
        return rs.stream().filter(r -> r.elementoId().equals(id)).findFirst().orElseThrow();
    }

    static Verificacao.Estado estado(DimensionadorNbr6118.ResultadoElemento r, String codigo) {
        return r.verificacoes().stream().filter(v -> v.codigo().equals(codigo)).findFirst().map(Verificacao::estado).orElseThrow(() -> new AssertionError("sem " + codigo));
    }

    @Test
    void vigaSeparaArmaduraInferiorESuperiorEDetalhaBarras() {
        var rs = dimensionar(portico("MAT.CONCRETO.C30", 0.10), cargas(2), "II");
        var v = de(rs, "V0");
        assertThat(estado(v, "FLEX+")).isEqualTo(Verificacao.Estado.ATENDIDA);
        assertThat(estado(v, "FLEX-")).isEqualTo(Verificacao.Estado.ATENDIDA);
        assertThat(v.asInferiorCm2()).isPositive();
        assertThat(v.asSuperiorCm2()).isPositive();
        assertThat(estado(v, "TOR")).isEqualTo(Verificacao.Estado.NAO_APLICAVEL);
        assertThat(v.estadoDetalhamento()).isEqualTo("NAO_VERIFICADA");
        // massa = Σ das posições; cada posição localiza o elemento de origem
        double soma = v.detalhamento().posicoes().stream().mapToDouble(Detalhamento.Posicao::massaKg).sum();
        assertThat(v.acoKg()).isCloseTo(soma, within(0.11));
        assertThat(v.detalhamento().posicoes()).allMatch(p -> p.elementoId().equals("V0") && p.quantidade() > 0 && p.comprimentoUnitarioM() > 0);
        assertThat(v.detalhamento().posicoes()).anyMatch(p -> p.funcao().startsWith("longitudinal superior"));
        // massa de cada posição = n × comprimento × massa nominal
        var p1 = v.detalhamento().posicoes().getFirst();
        assertThat(p1.massaKg()).isCloseTo(p1.quantidade() * p1.comprimentoUnitarioM() * Detalhamento.kgPorMetro(p1.diametroMm()), within(0.15));
    }

    @Test
    void lajeUsaAsCargasLancadas() {
        var leve = de(dimensionar(portico("MAT.CONCRETO.C30", 0.12), cargas(2), "II"), "L1");
        var pesada = de(dimensionar(portico("MAT.CONCRETO.C30", 0.12), cargas(10), "II"), "L1");
        assertThat(pesada.mdKnm()).isGreaterThan(leve.mdKnm() * 1.8);
        // Grashof: λ = 6/4 = 1,5, kx = λ⁴/(1+λ⁴) = 0,835; pd = 1,4·(0,12·25 + 1,5) + 1,4·2 = 9,1 kN/m²; Mxd = 0,835·9,1·4²/8
        double kx = Math.pow(1.5, 4) / (1 + Math.pow(1.5, 4));
        double pd = 1.4 * (0.12 * 25 + 1.5) + 1.4 * 2;
        assertThat(leve.mdKnm()).isCloseTo(kx * pd * 16 / 8, within(0.02));
        assertThat(estado(leve, "PLACA")).isEqualTo(Verificacao.Estado.NAO_APLICAVEL);
    }

    @Test
    void pilarConsideraMomentosDaAnalise() {
        var p = de(dimensionar(portico("MAT.CONCRETO.C30", 0.10), cargas(2), "II"), "P0");
        assertThat(estado(p, "FLEX-COMP")).isEqualTo(Verificacao.Estado.ATENDIDA);
        assertThat(p.memoria()).anyMatch(l -> l.contains("Md (dir. b)") && !l.contains("Md (dir. b) máx = 0,00"));
        assertThat(p.mdKnm()).isGreaterThan(0);
        assertThat(p.detalhamento().posicoes().getFirst().quantidade()).isGreaterThanOrEqualTo(4);
    }

    @Test
    void semMaterialNaoAssumeC30() {
        var rs = dimensionar(portico(null, 0.10), cargas(2), "II");
        var v = de(rs, "V0");
        assertThat(v.estado()).isEqualTo("DADOS_INSUFICIENTES");
        assertThat(v.verifica()).isFalse();
        assertThat(estado(v, "MAT")).isEqualTo(Verificacao.Estado.DADOS_INSUFICIENTES);
    }

    @Test
    void semCaaNaoHaAprovacao() {
        var v = de(dimensionar(portico("MAT.CONCRETO.C30", 0.10), cargas(2), null), "V0");
        assertThat(estado(v, "DUR")).isEqualTo(Verificacao.Estado.DADOS_INSUFICIENTES);
        assertThat(v.verifica()).isFalse();
    }

    @Test
    void classeAbaixoDaExigidaPelaAgressividadeReprova() {
        var v = de(dimensionar(portico("MAT.CONCRETO.C25", 0.10), cargas(2), "III"), "V0");
        assertThat(estado(v, "DUR")).isEqualTo(Verificacao.Estado.NAO_ATENDIDA);
        assertThat(v.estado()).isEqualTo("NAO_ATENDIDA");
    }

    @Test
    void vigaComMomentoAlemDaSecaoReprovaEmVezDeAprovar() {
        // 10 kN/m² de sobrecarga em viga 20×25: a seção não comporta o momento
        var els = new ArrayList<>(portico("MAT.CONCRETO.C30", 0.10).elementos());
        els.replaceAll(e -> e.id().equals("V0") ? ElementoEstrutural.viga("V0", "s", e.p1(), e.p2(), 0.12, 0.25, "MAT.CONCRETO.C30") : e);
        var m = new ModeloEstrutural(OBRA, 1, portico(null, 0.1).pavimentos(), els);
        var v = de(dimensionar(m, cargas(30), "II"), "V0");
        assertThat(v.estado()).isEqualTo("NAO_ATENDIDA");
        assertThat(v.verifica()).isFalse();
    }

    @Test
    void contraventamentoSemNbr8800NaoEhAprovado() {
        var els = new ArrayList<>(portico("MAT.CONCRETO.C30", 0.10).elementos());
        els.add(ElementoEstrutural.contraventamento("C1", "s", new Ponto(0, 0), new Ponto(6, 4), "L 50x5", "MAT.ACO_ESTRUTURAL.A36"));
        var c = de(dimensionar(new ModeloEstrutural(OBRA, 1, portico(null, 0.1).pavimentos(), els), cargas(2), "II"), "C1");
        assertThat(c.estado()).isEqualTo("NAO_VERIFICADA");
        assertThat(c.verificacoes()).allMatch(v -> v.estado() == Verificacao.Estado.NAO_VERIFICADA);
        assertThat(c.massaPerfilKg()).isCloseTo(4.75e-4 * Math.hypot(6, 4) * 7850, within(0.2));
    }

    @Test
    void consolidacaoNuncaAprovaComVerificacaoAusente() {
        var at = Verificacao.atendida("A", "a", "VERIFICACAO", null, null);
        assertThat(Verificacao.consolidar(List.of(at))).isEqualTo(Verificacao.Estado.ATENDIDA);
        assertThat(Verificacao.consolidar(List.of(at, Verificacao.naoVerificada("B", "b", "VERIFICACAO", null, "x")))).isEqualTo(Verificacao.Estado.NAO_VERIFICADA);
        assertThat(Verificacao.consolidar(List.of(at, Verificacao.dadosInsuficientes("B", "b", "VERIFICACAO", null, "x")))).isEqualTo(Verificacao.Estado.DADOS_INSUFICIENTES);
        assertThat(Verificacao.consolidar(List.of())).isEqualTo(Verificacao.Estado.NAO_VERIFICADA);
        assertThat(Verificacao.consolidar(List.of(Verificacao.naoAplicavel("C", "c", "VERIFICACAO", "x")))).isEqualTo(Verificacao.Estado.NAO_VERIFICADA);
        assertThat(Optional.of(1)).isPresent();
        assertThat(Map.of()).isEmpty();
    }

    /**
     * Conferência à mão do pórtico 6 × 4 m (laje 12 cm, revestimento 1,5 kN/m², sobrecarga 2 kN/m², ELU 1,4G + 1,4Q):
     * pd = 1,4·(0,12·25 + 1,5) + 1,4·2 = 9,1 kN/m². Viga de 6 m: trapézio das charneiras (pico 9,1·2 = 18,2 kN/m, rampas de
     * 2 m) → M0 = w·(3L² − 4a²)/24 = 18,2·(108 − 16)/24 = 69,77 kN·m; peso próprio 1,4·0,2·0,5·25 = 3,5 kN/m → 15,75 kN·m;
     * biapoiada: 85,52 kN·m. Com momentos de extremidade iguais (simetria), M no meio = M0 − |M extremo|.
     * Cortante = (9,1·8 + 3,5·6)/2 = 46,9 kN. Pilar: (24·9,1 + 3,5·20 + 4·1,4·0,2·0,4·3·25)/4 = 80,5 kN.
     */
    @Test
    void porticoBateComContaManual() {
        var rs = dimensionar(portico("MAT.CONCRETO.C30", 0.12), cargas(2), "II");
        var v = de(rs, "V0");
        assertThat(v.mdKnm()).isCloseTo(85.52 - 18.59, within(0.05));
        assertThat(v.memoria()).anyMatch(l -> l.contains("Md− = 18.59") || l.contains("Md− = 18,59"));
        assertThat(v.vdKn()).isCloseTo(46.9, within(0.05));
        assertThat(de(rs, "P0").ndKn()).isCloseTo(80.5, within(0.05));
    }
}
