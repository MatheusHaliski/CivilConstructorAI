package br.com.civilconstructorai.estrutura.mef;

import br.com.civilconstructorai.estrutura.carga.Carga;
import br.com.civilconstructorai.estrutura.carga.Combinacao;
import br.com.civilconstructorai.estrutura.carga.NaturezaAcao;
import br.com.civilconstructorai.estrutura.carga.TipoCarga;
import br.com.civilconstructorai.estrutura.modelo.ElementoEstrutural;
import br.com.civilconstructorai.estrutura.modelo.ModeloEstrutural;
import br.com.civilconstructorai.estrutura.modelo.PavimentoRef;
import br.com.civilconstructorai.estrutura.modelo.Ponto;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Casos de referência calculados à mão, passando pelo caminho real modelo BIM → modelo analítico → solver.
 * Cada teste diz a fórmula e de onde vem o valor esperado; nenhum compara o solver com ele mesmo.
 */
class ReferenciaEstruturalTest {

    static final UUID OBRA = UUID.randomUUID();
    static final double E30 = MontadorModeloAnalitico.moduloConcreto(30);
    static final List<PavimentoRef> DOIS_NIVEIS = List.of(new PavimentoRef("t", "Térreo", 0), new PavimentoRef("s", "Superior", 3));

    static Combinacao so(NaturezaAcao n) {
        return new Combinacao("só " + n, Combinacao.EstadoLimite.ELS_RARA, Map.of(n, 1.0));
    }

    static int noNoTopo(ModeloAnalitico ma, double x, double y, double z) {
        return ma.nos().stream().filter(n -> Math.abs(n.x() - x) < 1e-6 && Math.abs(n.y() - y) < 1e-6 && Math.abs(n.z() - z) < 1e-6).findFirst().orElseThrow().id();
    }

    /**
     * Pilar em balanço 20 × 40 cm (b = 0,20 m em X, h = 0,40 m em Y, como o desenho em planta e a cena 3D mostram),
     * H = 3 m, força horizontal de 10 kN no topo. δ = F·H³/(3·E·I), com a inércia que resiste à flexão naquela
     * direção: em X a altura da seção é b (I = h·b³/12); em Y é h (I = b·h³/12).
     */
    @Test
    void pilarEmBalancoUsaAInerciaDaDirecaoCarregada() {
        var pilar = ElementoEstrutural.pilar("P1", "t", new Ponto(0, 0), 0.20, 0.40, "t", "s", null);
        var m = new ModeloEstrutural(OBRA, 1, DOIS_NIVEIS, List.of(pilar));
        for (var dir : new String[]{"X", "Y"}) {
            var ma = MontadorModeloAnalitico.montar(new MontadorModeloAnalitico.Entrada(m, List.of(), null, Map.of()));
            int topo = noNoTopo(ma, 0, 0, 3);
            ma.caso(NaturezaAcao.VENTO).nodais().add(new ModeloAnalitico.CargaNodal(topo, dir.equals("X") ? 10 : 0, dir.equals("Y") ? 10 : 0, 0, 0, 0, 0));
            var r = new SolverMef(ma).resolver(List.of(so(NaturezaAcao.VENTO))).porCombinacao().values().iterator().next();
            double i = dir.equals("X") ? 0.40 * Math.pow(0.20, 3) / 12 : 0.20 * Math.pow(0.40, 3) / 12;
            double esperado = 10 * Math.pow(3, 3) / (3 * E30 * i);
            assertThat(r.deslocamentoMax()).as("deslocamento no topo, força em " + dir).isCloseTo(esperado, within(esperado * 0.01));
        }
    }

    /**
     * Laje 6 × 4 m apoiada em 4 vigas, sobrecarga 5 kN/m² (só a carga acidental, sem peso próprio). Pelas
     * charneiras a 45° (área de influência, NBR 6118 14.7.6.1): cada viga de 6 m recebe o trapézio
     * (6 + 2)/2 × 2 = 8 m² → 40 kN; cada viga de 4 m recebe o triângulo 4 × 2/2 = 4 m² → 20 kN. Total 120 kN.
     * A carga total de cada viga sai da variação do esforço cortante entre as extremidades da barra.
     */
    @Test
    void lajeRetangularRepartePorAreaDeInfluencia() {
        var els = new ArrayList<ElementoEstrutural>();
        double[][] cantos = {{0, 0}, {6, 0}, {6, 4}, {0, 4}};
        for (int k = 0; k < 4; k++) {
            els.add(ElementoEstrutural.pilar("P" + k, "t", new Ponto(cantos[k][0], cantos[k][1]), 0.20, 0.20, "t", "s", null));
        }
        for (int k = 0; k < 4; k++) {
            var a = cantos[k]; var b = cantos[(k + 1) % 4];
            els.add(ElementoEstrutural.viga("V" + k, "s", new Ponto(a[0], a[1]), new Ponto(b[0], b[1]), 0.20, 0.50, null));
        }
        els.add(ElementoEstrutural.laje("L1", "s", List.of(new Ponto(0, 0), new Ponto(6, 0), new Ponto(6, 4), new Ponto(0, 4)), 0.10, null));
        var m = new ModeloEstrutural(OBRA, 1, DOIS_NIVEIS, els);
        var sobrecarga = new Carga(UUID.randomUUID(), OBRA, "s", NaturezaAcao.ACIDENTAL, TipoCarga.AREA, 5, null, "sobrecarga", null);
        var ma = MontadorModeloAnalitico.montar(new MontadorModeloAnalitico.Entrada(m, List.of(sobrecarga), null, Map.of()));
        var r = new SolverMef(ma).resolver(List.of(so(NaturezaAcao.ACIDENTAL))).porCombinacao().values().iterator().next();
        for (var b : r.barras()) {
            if (!b.elementoId().startsWith("V")) {
                continue;
            }
            double total = Math.abs(b.pontos().getLast().vz() - b.pontos().getFirst().vz());
            double esperado = b.comprimento() > 5 ? 40 : 20;
            assertThat(total).as("carga total na viga " + b.elementoId() + " (L = " + b.comprimento() + " m)").isCloseTo(esperado, within(0.5));
        }
        assertThat(r.reacoes().stream().mapToDouble(SolverMef.Reacao::fz).sum()).isCloseTo(120, within(0.5));
    }

    /**
     * Carga concentrada de 30 kN numa viga de 6 m, a 1,5 m do apoio esquerdo. Qualquer que seja a rigidez do
     * pórtico, o cortante salta exatamente 30 kN sob a carga e o momento máximo positivo fica sob a carga
     * (s = 1,5 m). Se a carga cair no nó do apoio, a viga não flete.
     */
    @Test
    void cargaConcentradaNaVigaFicaNaPosicaoInformada() {
        var els = new ArrayList<ElementoEstrutural>(List.of(
                ElementoEstrutural.pilar("PA", "t", new Ponto(0, 0), 0.20, 0.20, "t", "s", null),
                ElementoEstrutural.pilar("PB", "t", new Ponto(6, 0), 0.20, 0.20, "t", "s", null),
                ElementoEstrutural.viga("V1", "s", new Ponto(0, 0), new Ponto(6, 0), 0.20, 0.50, null)));
        var m = new ModeloEstrutural(OBRA, 1, DOIS_NIVEIS, els);
        var p = new Carga(UUID.randomUUID(), OBRA, "s", NaturezaAcao.ACIDENTAL, TipoCarga.PONTUAL, 30, "V1", "equipamento", null, 1.5);
        var ma = MontadorModeloAnalitico.montar(new MontadorModeloAnalitico.Entrada(m, List.of(p), null, Map.of()));
        var r = new SolverMef(ma).resolver(List.of(so(NaturezaAcao.ACIDENTAL))).porCombinacao().values().iterator().next();
        var trechos = r.barras().stream().filter(b -> b.elementoId().equals("V1")).toList();
        assertThat(trechos).as("a viga é dividida no ponto da carga").hasSize(2);
        var esq = trechos.get(0); var dir = trechos.get(1);
        assertThat(esq.comprimento()).isCloseTo(1.5, within(1e-6));
        assertThat(Math.abs(esq.pontos().getLast().vz() - dir.pontos().getFirst().vz())).as("salto do cortante").isCloseTo(30, within(0.05));
        double mSobACarga = esq.pontos().getLast().my();
        assertThat(mSobACarga).as("momento sob a carga é o máximo positivo da viga").isGreaterThan(0)
                .isCloseTo(Math.max(esq.mMax(), dir.mMax()), within(1e-6));
    }

    // ---------------------------------------------------------------------------------------------------------
    // Casos isolados no solver. Tolerâncias: as soluções são exatas para Euler-Bernoulli, então o erro vem só do
    // arredondamento da saída (esforços a 1e-4 kN / kN·m) e da aritmética de ponto flutuante: 1e-3 em forças e
    // momentos (absoluto) e 0,1 % em deslocamentos (relativo, saída não arredondada).
    // ---------------------------------------------------------------------------------------------------------

    static ModeloAnalitico.Apoio rotulaPlana(int no, boolean travaX) {
        // apoio de viga no plano x-z: trava z (e x num dos lados), y e torção; deixa girar em torno de y
        return new ModeloAnalitico.Apoio(no, travaX, true, true, true, false, true);
    }

    static SolverMef.ResultadoCombinacao resolver(ModeloAnalitico m, NaturezaAcao n) {
        return new SolverMef(m).resolver(List.of(so(n))).porCombinacao().values().iterator().next();
    }

    /** balanço L = 3 m, P = 12 kN na ponta: M no engaste = −P·L = −36 kN·m (traciona em cima); δ = P·L³/(3EI) */
    @Test
    void balancoComCargaNaPonta() {
        var m = new ModeloAnalitico();
        int a = m.adicionarNo(0, 0, 0), b = m.adicionarNo(3, 0, 0);
        var sec = ModeloAnalitico.Secao.retangular(0.2, 0.5, E30);
        m.adicionarBarra("V", "V", a, b, sec);
        m.adicionarApoio(ModeloAnalitico.Apoio.engaste(a));
        m.caso(NaturezaAcao.PERMANENTE).nodais().add(new ModeloAnalitico.CargaNodal(b, 0, 0, -12, 0, 0, 0));
        var r = resolver(m, NaturezaAcao.PERMANENTE);
        var rb = r.barras().get(0);
        assertThat(rb.pontos().getFirst().my()).isCloseTo(-36, within(1e-3));
        assertThat(rb.pontos().getLast().my()).isCloseTo(0, within(1e-3));
        double d = 12 * 27 / (3 * E30 * sec.iy());
        assertThat(r.deslocamentoMax()).isCloseTo(d, within(d * 1e-3));
        assertThat(r.equilibrio().ok()).isTrue();
    }

    /**
     * Carga triangular (0 → w = 9 kN/m) em viga biapoiada de 6 m: resultante W = wL/2 = 27 kN; reações W/3 = 9 kN
     * e 2W/3 = 18 kN; M máx = wL²/(9√3) = 20,785 kN·m em x = L/√3 = 3,464 m; flecha máx = 0,00652·wL⁴/(EI)
     * (= 0,01304·W·L³/(EI) com W = wL/2 — confundir as duas formas dobra o valor).
     */
    @Test
    void cargaTriangularEmVigaBiapoiada() {
        double l = 6, w = 9;
        var m = new ModeloAnalitico();
        int a = m.adicionarNo(0, 0, 0), b = m.adicionarNo(l, 0, 0);
        var sec = ModeloAnalitico.Secao.retangular(0.2, 0.5, E30);
        int barra = m.adicionarBarra("V", "V", a, b, sec);
        m.adicionarApoio(rotulaPlana(a, true));
        m.adicionarApoio(rotulaPlana(b, false));
        m.caso(NaturezaAcao.PERMANENTE).variaveis().add(new ModeloAnalitico.CargaVariavel(barra, 0, l, 0, 0, 0, 0, 0, -w));
        var r = resolver(m, NaturezaAcao.PERMANENTE);
        var reacoes = r.reacoes().stream().sorted((x, y) -> Integer.compare(x.no(), y.no())).toList();
        assertThat(reacoes.get(0).fz()).isCloseTo(9, within(1e-3));
        assertThat(reacoes.get(1).fz()).isCloseTo(18, within(1e-3));
        // M em x = L/√3 por estática (o solver amostra 11 pontos; confere-se o valor amostrado mais próximo pela fórmula)
        var rb = r.barras().get(0);
        for (var p : rb.pontos()) {
            double x = p.s();
            double mTeorico = 9 * x - w * x * x * x / (6 * l);
            assertThat(p.my()).as("M em x = " + x).isCloseTo(mTeorico, within(1e-3));
        }
        assertThat(w * l * l / (9 * Math.sqrt(3))).isCloseTo(20.785, within(1e-3));
        double flecha = 0.00652 * w * Math.pow(l, 4) / (E30 * sec.iy());
        // a flecha máxima ocorre em x = 0,5193·L, entre pontos amostrados: tolerância de 0,5 % (amostragem, não método)
        assertThat(rb.flechaMax()).isCloseTo(flecha, within(flecha * 5e-3));
        assertThat(r.equilibrio().ok()).isTrue();
    }

    /** biengastada com carga uniforme: M extremos = −wL²/12, meio = +wL²/24, flecha = wL⁴/(384EI) */
    @Test
    void vigaBiengastadaUniforme() {
        double l = 5, w = 12;
        var m = new ModeloAnalitico();
        int a = m.adicionarNo(0, 0, 0), b = m.adicionarNo(l, 0, 0);
        var sec = ModeloAnalitico.Secao.retangular(0.2, 0.5, E30);
        int barra = m.adicionarBarra("V", "V", a, b, sec);
        m.adicionarApoio(ModeloAnalitico.Apoio.engaste(a));
        m.adicionarApoio(ModeloAnalitico.Apoio.engaste(b));
        m.caso(NaturezaAcao.PERMANENTE).distribuidas().add(new ModeloAnalitico.CargaDistribuida(barra, 0, 0, -w));
        var rb = resolver(m, NaturezaAcao.PERMANENTE).barras().get(0);
        assertThat(rb.pontos().getFirst().my()).isCloseTo(-w * l * l / 12, within(1e-3));
        assertThat(rb.pontos().get(5).my()).isCloseTo(w * l * l / 24, within(1e-3));
        double f = w * Math.pow(l, 4) / (384 * E30 * sec.iy());
        assertThat(rb.flechaMax()).isCloseTo(f, within(f * 1e-3));
    }

    /** pilar girado 90°: o b passa a ficar em Y, então a força em X encontra a inércia b·h³/12 */
    @Test
    void pilarGiradoTrocaAInerciaResistente() {
        var pilar = ElementoEstrutural.pilar("P1", "t", new Ponto(0, 0), 0.20, 0.40, "t", "s", null, 90, false);
        var ma = MontadorModeloAnalitico.montar(new MontadorModeloAnalitico.Entrada(new ModeloEstrutural(OBRA, 1, DOIS_NIVEIS, List.of(pilar)), List.of(), null, Map.of()));
        ma.caso(NaturezaAcao.VENTO).nodais().add(new ModeloAnalitico.CargaNodal(noNoTopo(ma, 0, 0, 3), 10, 0, 0, 0, 0, 0));
        var r = resolver(ma, NaturezaAcao.VENTO);
        double esperado = 10 * 27 / (3 * E30 * (0.20 * Math.pow(0.40, 3) / 12));
        assertThat(r.deslocamentoMax()).isCloseTo(esperado, within(esperado * 1e-3));
    }

    /** pilar circular Ø 30 cm: I = π·d⁴/64 nas duas direções */
    @Test
    void pilarCircular() {
        var pilar = ElementoEstrutural.pilar("P1", "t", new Ponto(0, 0), 0.30, 0.30, "t", "s", null, 0, true);
        var ma = MontadorModeloAnalitico.montar(new MontadorModeloAnalitico.Entrada(new ModeloEstrutural(OBRA, 1, DOIS_NIVEIS, List.of(pilar)), List.of(), null, Map.of()));
        ma.caso(NaturezaAcao.VENTO).nodais().add(new ModeloAnalitico.CargaNodal(noNoTopo(ma, 0, 0, 3), 0, 10, 0, 0, 0, 0));
        var r = resolver(ma, NaturezaAcao.VENTO);
        double esperado = 10 * 27 / (3 * E30 * (Math.PI * Math.pow(0.30, 4) / 64));
        assertThat(r.deslocamentoMax()).isCloseTo(esperado, within(esperado * 1e-3));
    }

    /**
     * Treliça de duas barras (A(0;0), B(4;0), C(2;1,5), barras de 2,5 m), P = 10 kN para baixo em C. Por estática:
     * N = −P/(2·sen θ) = −10/(2·0,6) = −8,333 kN (compressão) em cada barra. Barras só axiais (contraventamento).
     */
    @Test
    void trelicaPorEstatica() {
        var m = new ModeloAnalitico();
        int a = m.adicionarNo(0, 0, 0), b = m.adicionarNo(4, 0, 0), c = m.adicionarNo(2, 0, 1.5);
        var sec = ModeloAnalitico.Secao.axial(4.8e-4, MontadorModeloAnalitico.E_ACO);
        m.adicionarBarra("C1", "C1", a, c, sec, 0, true);
        m.adicionarBarra("C2", "C2", b, c, sec, 0, true);
        m.adicionarApoio(new ModeloAnalitico.Apoio(a, true, true, true, true, true, true));
        m.adicionarApoio(new ModeloAnalitico.Apoio(b, true, true, true, true, true, true));
        m.adicionarApoio(new ModeloAnalitico.Apoio(c, false, true, false, true, true, true)); // só fora do plano e rotações
        m.caso(NaturezaAcao.PERMANENTE).nodais().add(new ModeloAnalitico.CargaNodal(c, 0, 0, -10, 0, 0, 0));
        var r = resolver(m, NaturezaAcao.PERMANENTE);
        for (var rb : r.barras()) {
            assertThat(rb.pontos().get(5).n()).as(rb.rotulo()).isCloseTo(-10 / (2 * 0.6), within(1e-3));
            assertThat(rb.mMax()).isZero();
        }
    }

    /** contraventamento em L 50x5 entra como treliça; perfil não reconhecido é excluído com aviso, não some calado */
    @Test
    void contraventamentoEntraComoTrelicaOuGeraAviso() {
        assertThat(MontadorModeloAnalitico.areaPerfil("L 50x5")).isCloseTo(4.75e-4, within(1e-9));
        assertThat(MontadorModeloAnalitico.areaPerfil("L 60x40x5")).isCloseTo(4.75e-4, within(1e-9));
        assertThat(MontadorModeloAnalitico.areaPerfil("W 200x15")).isNull();
        var els = new ArrayList<ElementoEstrutural>();
        double[][] cantos = {{0, 0}, {4, 0}, {4, 3}, {0, 3}};
        for (int k = 0; k < 4; k++) {
            els.add(ElementoEstrutural.pilar("P" + k, "t", new Ponto(cantos[k][0], cantos[k][1]), 0.20, 0.20, "t", "s", null));
        }
        for (int k = 0; k < 4; k++) {
            var a = cantos[k]; var b = cantos[(k + 1) % 4];
            els.add(ElementoEstrutural.viga("V" + k, "s", new Ponto(a[0], a[1]), new Ponto(b[0], b[1]), 0.20, 0.40, null));
        }
        els.add(ElementoEstrutural.contraventamento("C1", "s", new Ponto(0, 0), new Ponto(4, 3), "L 50x5", "MAT_ACO"));
        els.add(ElementoEstrutural.contraventamento("C2", "s", new Ponto(0, 3), new Ponto(4, 0), "tubo 2\"", "MAT_ACO"));
        var ma = MontadorModeloAnalitico.montar(new MontadorModeloAnalitico.Entrada(new ModeloEstrutural(OBRA, 1, DOIS_NIVEIS, els), List.of(), null, Map.of()));
        assertThat(ma.barras().stream().filter(ModeloAnalitico.Barra::trelica).map(ModeloAnalitico.Barra::elementoId)).containsExactly("C1");
        assertThat(ma.avisos()).anyMatch(av -> "C2".equals(av.elementoId()) && av.tipo().equals("EXCLUIDO"));
        assertThat(ma.avisos()).anyMatch(av -> "C1".equals(av.elementoId()) && av.mensagem().contains("Não é cabo"));
    }

    /** viga solta (sem apoio) num modelo com pilares: o solver aponta o nó e a direção, não um índice interno */
    @Test
    void modeloComTrechoDesligadoFalhaApontandoONo() {
        var els = List.of(
                ElementoEstrutural.pilar("P1", "t", new Ponto(0, 0), 0.20, 0.20, "t", "s", null),
                ElementoEstrutural.viga("V-solta", "s", new Ponto(10, 10), new Ponto(14, 10), 0.20, 0.40, null));
        var ma = MontadorModeloAnalitico.montar(new MontadorModeloAnalitico.Entrada(new ModeloEstrutural(OBRA, 1, DOIS_NIVEIS, els), List.of(), null, Map.of()));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new SolverMef(ma).resolver(List.of(so(NaturezaAcao.PERMANENTE))))
                .isInstanceOf(SolverMef.EstruturaHipostaticaException.class)
                .hasMessageContaining("nó").hasMessageMatching(".*\\((10|14)\\.00; 10\\.00; 3\\.00\\) m.*");
    }

    /** cargas sem alvo, carga de área sem laje e posição além da viga viram avisos (nada é descartado calado) */
    @Test
    void cargasDescartadasViramAvisos() {
        var els = List.of(
                ElementoEstrutural.pilar("PA", "t", new Ponto(0, 0), 0.20, 0.20, "t", "s", null),
                ElementoEstrutural.pilar("PB", "t", new Ponto(6, 0), 0.20, 0.20, "t", "s", null),
                ElementoEstrutural.viga("V1", "s", new Ponto(0, 0), new Ponto(6, 0), 0.20, 0.50, null));
        var cargas = List.of(
                new Carga(UUID.randomUUID(), OBRA, "s", NaturezaAcao.ACIDENTAL, TipoCarga.LINEAR, 5, null, "sem alvo", null),
                new Carga(UUID.randomUUID(), OBRA, "s", NaturezaAcao.ACIDENTAL, TipoCarga.AREA, 2, null, "sobrecarga", null),
                new Carga(UUID.randomUUID(), OBRA, "s", NaturezaAcao.ACIDENTAL, TipoCarga.PONTUAL, 10, "V1", "longe", null, 9.0),
                new Carga(UUID.randomUUID(), OBRA, "s", NaturezaAcao.ACIDENTAL, TipoCarga.PONTUAL, 10, "V1", "sem posição", null));
        var ma = MontadorModeloAnalitico.montar(new MontadorModeloAnalitico.Entrada(new ModeloEstrutural(OBRA, 1, DOIS_NIVEIS, els), cargas, null, Map.of()));
        assertThat(ma.avisos()).extracting(ModeloAnalitico.Aviso::mensagem)
                .anyMatch(t -> t.contains("sem alvo") && t.contains("sem elemento alvo"))
                .anyMatch(t -> t.contains("sobrecarga") && t.contains("sem lajes"))
                .anyMatch(t -> t.contains("longe") && t.contains("além do comprimento"))
                .anyMatch(t -> t.contains("sem posição") && t.contains("meio do vão"));
    }

    /** viga com uma ponta sem apoio, ligada a outra viga: o pórtico resolve como balanço, e isso vira aviso explícito */
    @Test
    void extremidadeLivreDeVigaViraAviso() {
        var els = List.of(
                ElementoEstrutural.pilar("PA", "t", new Ponto(0, 0), 0.20, 0.20, "t", "s", null),
                ElementoEstrutural.pilar("PB", "t", new Ponto(0, 6), 0.20, 0.20, "t", "s", null),
                ElementoEstrutural.viga("V1", "s", new Ponto(0, 0), new Ponto(0, 6), 0.20, 0.50, null),
                ElementoEstrutural.viga("V2", "s", new Ponto(0, 3), new Ponto(2.5, 3), 0.14, 0.40, null));
        var ma = MontadorModeloAnalitico.montar(new MontadorModeloAnalitico.Entrada(new ModeloEstrutural(OBRA, 1, DOIS_NIVEIS, els), List.of(), null, Map.of()));
        assertThat(ma.avisos()).anyMatch(a -> "V2".equals(a.elementoId()) && a.tipo().equals("PREMISSA") && a.mensagem().contains("(2.50; 3.00)") && a.mensagem().contains("balanço"));
        assertThat(ma.avisos()).noneMatch(a -> "V1".equals(a.elementoId()) && a.mensagem().contains("extremidade livre"));
    }
}
