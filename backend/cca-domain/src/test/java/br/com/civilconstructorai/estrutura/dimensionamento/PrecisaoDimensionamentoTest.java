package br.com.civilconstructorai.estrutura.dimensionamento;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** Pré-condições numéricas e precisão, independentes da auditoria normativa ainda pendente. */
class PrecisaoDimensionamentoTest {
    @Test void entradaNaoFinitaNaoPodeVirarVerificaComAreaZero() {
        for (double invalido : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertThatThrownBy(() -> Nbr6118.pilar(invalido,.2,.4,3,30)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Nbr6118.cisalhamento(invalido,.2,.45,30)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Nbr6118.flexaoSimples(invalido,.2,.5,.45,30)).isInstanceOf(IllegalArgumentException.class);
        }
    }
    @Test void magnitudesEDimensoesDevemEstarNoDominioDeclarado() {
        assertThatThrownBy(() -> Nbr6118.cisalhamento(-100,.2,.45,30)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Nbr6118.flexaoSimples(100,.2,.5,.6,30)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Nbr6118.flexaoSimples(100,.2,.5,.45,100)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Nbr6118.pilar(600,.2,.4,0,30)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Nbr6118.fissuracao(10,.45,0,16,30,.3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Nbr6118.flecha(10,5,.2,.5,.45,5,30,Double.NaN)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void escolhaDeEstriboDeveAtenderAoMinimoSemArredondamentoIntermediario() {
        var r = Nbr6118.cisalhamento(0,.2,.45,30);
        double minimo = .2*Nbr6118.fctm(30)/500*.2*1e4;
        assertThat(r.aswPorMetroCm2()).isEqualTo(minimo);
        assertThat(r.aswMinPorMetroCm2()).isEqualTo(minimo);
        var arranjo = Bitolas.estribo(r.aswPorMetroCm2(),r.sMaxCm());
        assertThat(arranjo.aswPorMetroCm2()).isGreaterThanOrEqualTo(minimo);
    }
    @Test void flexaoRecuperaEquilibrioDeSecaoComLinhaNeutraConhecida() {
        // Seção independente: x=90 mm, b=200 mm, d=450 mm, C30/CA50.
        // C=262,285714285714 kN; braço=414 mm; Md=108,586285714286 kN·m.
        var r = Nbr6118.flexaoSimples(77.56163265306122,.2,.5,.45,30);
        assertThat(r.xSobreD()).isCloseTo(.2,within(1e-12));
        assertThat(r.asCm2()).isCloseTo(6.032571428571429,within(1e-12));
    }
}
