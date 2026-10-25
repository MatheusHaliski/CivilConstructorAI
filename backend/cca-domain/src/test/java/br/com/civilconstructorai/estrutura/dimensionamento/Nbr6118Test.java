package br.com.civilconstructorai.estrutura.dimensionamento;

import br.com.civilconstructorai.estrutura.mef.MontadorModeloAnalitico;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class Nbr6118Test {

    @Test
    void flexaoSimplesViga20x50C25ComMd100() {
        // referência: viga 20x50, d = 45 cm, C25, Md = 100 kN·m → x ≈ 10,0 cm, As ≈ 5,6 cm²
        var r = Nbr6118.flexaoSimples(100 / Nbr6118.GAMA_F, 0.20, 0.50, 0.45, 25);
        assertThat(r.verifica()).isTrue();
        assertThat(r.asCm2()).isBetween(5.4, 5.8);
        assertThat(r.xSobreD()).isBetween(0.20, 0.25);
        assertThat(r.dominio()).isEqualTo(2);
        assertThat(r.asMinCm2()).isCloseTo(1.5, within(0.01));
    }

    @Test
    void flexaoReprovaQuandoXSobreDPassaDe045() {
        // Md = 250 kN·m: a seção ainda resiste (discriminante ≥ 0), mas x/d ≈ 0,7 > 0,45
        var r = Nbr6118.flexaoSimples(250 / Nbr6118.GAMA_F, 0.20, 0.50, 0.45, 25);
        assertThat(r.verifica()).isFalse();
        assertThat(r.xSobreD()).isGreaterThan(0.45);
        assertThat(r.motivo()).contains("x/d");
    }

    @Test
    void flexaoReprovaSecaoInsuficiente() {
        var r = Nbr6118.flexaoSimples(400 / Nbr6118.GAMA_F, 0.20, 0.50, 0.45, 25);
        assertThat(r.verifica()).isFalse();
        assertThat(r.motivo()).contains("insuficiente");
    }

    @Test
    void cisalhamentoVigaUsual() {
        var r = Nbr6118.cisalhamento(80, 0.20, 0.45, 25);
        assertThat(r.verifica()).isTrue();
        assertThat(r.vrd2()).isGreaterThan(300);
        assertThat(r.vc()).isBetween(60.0, 80.0);
        assertThat(r.aswPorMetroCm2()).isGreaterThanOrEqualTo(r.aswMinPorMetroCm2());
        var estribo = Bitolas.estribo(r.aswPorMetroCm2(), r.sMaxCm());
        assertThat(estribo.espacamentoCm()).isLessThanOrEqualTo(r.sMaxCm());
    }

    @Test
    void pilarCurtoComArmaduraMinima() {
        var r = Nbr6118.pilar(600, 0.20, 0.40, 3.0, 30);
        assertThat(r.verifica()).isTrue();
        assertThat(r.esbeltez()).isCloseTo(3.46 * 3.0 / 0.4, within(0.1));
        assertThat(r.asCm2()).isGreaterThanOrEqualTo(0.004 * 0.2 * 0.4 * 1e4);
        assertThat(r.asCm2()).isLessThanOrEqualTo(r.asMaxCm2());
    }

    @Test
    void pilarMuitoEsbeltoForaDoMetodo() {
        var r = Nbr6118.pilar(600, 0.19, 0.19, 6.0, 30);
        assertThat(r.verifica()).isFalse();
    }

    @Test
    void flechaDeVigaFolgadaVerifica() {
        var r = Nbr6118.flecha(15, 5.0, 0.20, 0.50, 0.45, 5.0, 25, MontadorModeloAnalitico.moduloConcreto(25));
        assertThat(r.verifica()).isTrue();
        assertThat(r.flechaMm()).isLessThan(r.limiteMm());
        assertThat(r.ieqSobreIc()).isBetween(0.1, 1.0);
    }

    @Test
    void bitolasEscolhemArranjoComFolgaPequena() {
        var a = Bitolas.escolher(5.6, 2, 10);
        assertThat(a.areaCm2()).isGreaterThanOrEqualTo(5.6);
        assertThat(a.barras()).isBetween(2, 8);
    }
}
