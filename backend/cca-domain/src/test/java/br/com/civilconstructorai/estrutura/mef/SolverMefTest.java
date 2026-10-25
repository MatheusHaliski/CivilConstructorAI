package br.com.civilconstructorai.estrutura.mef;

import br.com.civilconstructorai.estrutura.carga.Combinacao;
import br.com.civilconstructorai.estrutura.carga.NaturezaAcao;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class SolverMefTest {

    private static final Combinacao G = new Combinacao("G", Combinacao.EstadoLimite.ELS_RARA, Map.of(NaturezaAcao.PERMANENTE, 1.0));

    @Test
    void vigaBiapoiadaDaMomentoQL2Sobre8EFlechaTeorica() {
        double l = 6.0, q = 10.0, b = 0.2, h = 0.5;
        double e = MontadorModeloAnalitico.moduloConcreto(30);
        var m = new ModeloAnalitico();
        int n1 = m.adicionarNo(0, 0, 0), n2 = m.adicionarNo(l, 0, 0);
        var sec = ModeloAnalitico.Secao.retangular(b, h, e);
        int barra = m.adicionarBarra("V1", "V1", n1, n2, sec);
        m.adicionarApoio(new ModeloAnalitico.Apoio(n1, true, true, true, true, false, false));
        m.adicionarApoio(new ModeloAnalitico.Apoio(n2, false, true, true, true, false, false));
        m.caso(NaturezaAcao.PERMANENTE).distribuidas().add(new ModeloAnalitico.CargaDistribuida(barra, 0, 0, -q));

        var r = new SolverMef(m).resolver(List.of(G)).porCombinacao().get("G");
        var rb = r.barras().get(0);
        double mTeorico = q * l * l / 8;
        assertThat(rb.mMax()).isCloseTo(mTeorico, within(mTeorico * 0.01));
        assertThat(rb.pontos().get(0).my()).isCloseTo(0, within(0.05));
        assertThat(rb.pontos().get(10).my()).isCloseTo(0, within(0.05));
        assertThat(Math.abs(rb.pontos().get(0).vz())).isCloseTo(q * l / 2, within(0.3));
        double flechaTeorica = 5 * q * Math.pow(l, 4) / (384 * e * sec.iy());
        assertThat(r.deslocamentoMax()).isCloseTo(flechaTeorica, within(flechaTeorica * 0.01));
        assertThat(r.reacoes().stream().mapToDouble(SolverMef.Reacao::fz).sum()).isCloseTo(q * l, within(0.5));
    }

    @Test
    void porticoPlanoEngastadoEquilibraCargaVerticalEHorizontal() {
        double e = MontadorModeloAnalitico.moduloConcreto(30);
        var m = new ModeloAnalitico();
        int a = m.adicionarNo(0, 0, 0), b = m.adicionarNo(0, 0, 3), c = m.adicionarNo(5, 0, 3), d = m.adicionarNo(5, 0, 0);
        var pilar = ModeloAnalitico.Secao.retangular(0.2, 0.4, e);
        var viga = ModeloAnalitico.Secao.retangular(0.2, 0.5, e);
        m.adicionarBarra("P1", "P1", a, b, pilar);
        int v = m.adicionarBarra("V1", "V1", b, c, viga);
        m.adicionarBarra("P2", "P2", d, c, pilar);
        m.adicionarApoio(ModeloAnalitico.Apoio.engaste(a));
        m.adicionarApoio(ModeloAnalitico.Apoio.engaste(d));
        m.caso(NaturezaAcao.PERMANENTE).distribuidas().add(new ModeloAnalitico.CargaDistribuida(v, 0, 0, -20));
        m.caso(NaturezaAcao.VENTO).nodais().add(new ModeloAnalitico.CargaNodal(b, 15, 0, 0, 0, 0, 0));

        var comb = new Combinacao("G+W", Combinacao.EstadoLimite.ELU_NORMAL, Map.of(NaturezaAcao.PERMANENTE, 1.0, NaturezaAcao.VENTO, 1.0));
        var r = new SolverMef(m).resolver(List.of(comb)).porCombinacao().get("G+W");
        assertThat(r.reacoes().stream().mapToDouble(SolverMef.Reacao::fz).sum()).isCloseTo(100, within(0.5));
        assertThat(r.reacoes().stream().mapToDouble(SolverMef.Reacao::fx).sum()).isCloseTo(-15, within(0.5));
        var vigaRes = r.barras().stream().filter(x -> x.rotulo().equals("V1")).findFirst().orElseThrow();
        // viga de pórtico: momento positivo no meio menor que qL²/8 (engastamento parcial) e negativos nos nós
        assertThat(vigaRes.mMax()).isBetween(20.0, 62.5);
        assertThat(vigaRes.mMin()).isLessThan(0);
    }

    @Test
    void estruturaSemApoiosEhHipostatica() {
        var m = new ModeloAnalitico();
        int n1 = m.adicionarNo(0, 0, 0), n2 = m.adicionarNo(4, 0, 0);
        m.adicionarBarra("V", "V", n1, n2, ModeloAnalitico.Secao.retangular(0.2, 0.4, 2.5e7));
        m.caso(NaturezaAcao.PERMANENTE).distribuidas().add(new ModeloAnalitico.CargaDistribuida(0, 0, 0, -5));
        assertThatThrownBy(() -> new SolverMef(m).resolver(List.of(G))).isInstanceOf(SolverMef.EstruturaHipostaticaException.class);
    }

    @Test
    void mecanismoComApoioInsuficienteEhSingular() {
        var m = new ModeloAnalitico();
        int n1 = m.adicionarNo(0, 0, 0), n2 = m.adicionarNo(4, 0, 0);
        m.adicionarBarra("V", "V", n1, n2, ModeloAnalitico.Secao.retangular(0.2, 0.4, 2.5e7));
        m.adicionarApoio(new ModeloAnalitico.Apoio(n1, false, true, true, true, false, false)); // livre em x: translação de corpo rígido
        m.adicionarApoio(new ModeloAnalitico.Apoio(n2, false, true, true, true, false, false));
        m.caso(NaturezaAcao.PERMANENTE).distribuidas().add(new ModeloAnalitico.CargaDistribuida(0, 0, 0, -5));
        assertThatThrownBy(() -> new SolverMef(m).resolver(List.of(G))).isInstanceOf(SolverMef.EstruturaHipostaticaException.class);
    }
}
