package br.com.civilconstructorai.estrutura.carga;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class GeradorCombinacoesTest {

    @Test
    void comGQeWGeraCincoEluETresEls() {
        var c = GeradorCombinacoes.gerar(true, true);
        assertThat(c.stream().filter(x -> x.estadoLimite() == Combinacao.EstadoLimite.ELU_NORMAL)).hasSize(5);
        var principal = c.stream().filter(x -> x.nome().startsWith("ELU 4")).findFirst().orElseThrow();
        assertThat(principal.fator(NaturezaAcao.PERMANENTE)).isEqualTo(1.4);
        assertThat(principal.fator(NaturezaAcao.ACIDENTAL)).isEqualTo(1.4);
        assertThat(principal.fator(NaturezaAcao.VENTO)).isEqualTo(1.4 * 0.6);
        var qp = c.stream().filter(x -> x.estadoLimite() == Combinacao.EstadoLimite.ELS_QUASE_PERMANENTE).findFirst().orElseThrow();
        assertThat(qp.fator(NaturezaAcao.ACIDENTAL)).isEqualTo(0.3);
        assertThat(qp.fator(NaturezaAcao.VENTO)).isEqualTo(0.0);
    }

    @Test
    void soPermanenteGeraUmaElu() {
        var c = GeradorCombinacoes.gerar(false, false);
        assertThat(c.stream().filter(x -> x.estadoLimite() == Combinacao.EstadoLimite.ELU_NORMAL)).hasSize(1);
    }

    @Test
    void ventoNbr6123() {
        var v = ParametrosVento.padrao(40);
        assertThat(v.pressaoDinamica()).isCloseTo(0.613 * 1600 / 1000, within(1e-9));
        assertThat(v.forca(10)).isCloseTo(1.2 * 0.613 * 1600 / 1000 * 10, within(1e-9));
        assertThatThrownBy(() -> ParametrosVento.padrao(90)).isInstanceOf(br.com.civilconstructorai.comum.ExcecaoDominio.Validacao.class);
    }
}
