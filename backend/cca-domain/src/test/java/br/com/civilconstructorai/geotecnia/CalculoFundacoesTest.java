package br.com.civilconstructorai.geotecnia;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CalculoFundacoesTest {

    private static CalculoFundacoes.Perfil perfilAreiaMedia() {
        var leituras = new java.util.ArrayList<LeituraSpt>();
        for (int z = 1; z <= 12; z++) {
            leituras.add(new LeituraSpt(z, z < 4 ? 8 : z < 8 ? 15 : 25, false));
        }
        return new CalculoFundacoes.Perfil(List.of(new CamadaSolo(0, -4, TipoSolo.ARGILA_ARENOSA, "argila arenosa"), new CamadaSolo(-4, -12, TipoSolo.AREIA, "areia média")), leituras, -3.0);
    }

    @Test
    void sapataPorTensaoAdmissivel() {
        var s = CalculoFundacoes.sapata(400, 0.2, 0.4, 1.5, perfilAreiaMedia());
        assertThat(s.verifica()).isTrue();
        assertThat(s.sigmaAdmKpa()).isBetween(150.0, 400.0);
        assertThat(s.lado() * s.lado() * s.sigmaAdmKpa()).isGreaterThanOrEqualTo(1.1 * 400 - 1);
        assertThat(s.altura()).isGreaterThanOrEqualTo((s.lado() - 0.2) / 3);
    }

    @Test
    void aokiVellosoEDecourtQuaresmaDaoCapacidadesPlausiveis() {
        var perfil = perfilAreiaMedia();
        double aoki = CalculoFundacoes.aokiVelloso(0.30, 10, perfil);
        double decourt = CalculoFundacoes.decourtQuaresma(0.30, 10, perfil);
        assertThat(aoki).isBetween(300.0, 2500.0);
        assertThat(decourt).isBetween(300.0, 2500.0);
        var e = CalculoFundacoes.estacas(900, 0.30, 10, perfil);
        assertThat(e.qadmKn()).isEqualTo(Math.round(Math.min(aoki, decourt) / 2));
        assertThat(e.estacas()).isEqualTo((int) Math.ceil(900 / e.qadmKn()));
    }

    @Test
    void comprimentoResistenteEhOndeHaTresLeiturasFortes() {
        assertThat(CalculoFundacoes.comprimentoResistente(perfilAreiaMedia())).isEqualTo(6.0);
    }

    @Test
    void sapatasVizinhasSobrepostas() {
        assertThat(CalculoFundacoes.sapatasSeSobrepoem(2.0, 2.0, 1.5)).isTrue();
        assertThat(CalculoFundacoes.sapatasSeSobrepoem(2.0, 2.0, 2.5)).isFalse();
    }
}
