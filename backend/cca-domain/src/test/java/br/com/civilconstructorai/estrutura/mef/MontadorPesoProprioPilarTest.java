package br.com.civilconstructorai.estrutura.mef;

import br.com.civilconstructorai.estrutura.carga.Combinacao;
import br.com.civilconstructorai.estrutura.carga.NaturezaAcao;
import br.com.civilconstructorai.estrutura.modelo.ElementoEstrutural;
import br.com.civilconstructorai.estrutura.modelo.ModeloEstrutural;
import br.com.civilconstructorai.estrutura.modelo.PavimentoRef;
import br.com.civilconstructorai.estrutura.modelo.Ponto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class MontadorPesoProprioPilarTest {

    @Test
    void pesoProprioDistribuidoEquilibraEProduzEncurtamentoECompressaoVariavel() {
        var terreo = new PavimentoRef("t", "Térreo", 0);
        var cobertura = new PavimentoRef("c", "Cobertura", 3);
        var pilar = ElementoEstrutural.pilar("P1", "t", new Ponto(0, 0),
                0.25, 0.25, "t", "c", "MAT_CONC_FCK30");
        var modelo = new ModeloEstrutural(UUID.randomUUID(), 1,
                List.of(terreo, cobertura), List.of(pilar));
        var ma = MontadorModeloAnalitico.montar(new MontadorModeloAnalitico.Entrada(
                modelo, List.of(), null, Map.of("MAT_CONC_FCK30", 30e6)));
        var g = new Combinacao("G", Combinacao.EstadoLimite.ELS_RARA,
                Map.of(NaturezaAcao.PERMANENTE, 1.0));

        var resultado = new SolverMef(ma).resolver(List.of(g)).porCombinacao().get("G");

        // A=0,0625 m²; q=25A=1,5625 kN/m; L=3 m; E=30e6 kN/m².
        // ∫0^L q(L-s)/(EA) ds = qL²/(2EA) = 3,75 µm.
        // Concentrar todo o peso no topo daria indevidamente o dobro.
        assertThat(resultado.deslocamentoMax()).isCloseTo(3.75e-6, within(1e-15));
        assertThat(resultado.reacoes()).singleElement().satisfies(reacao -> {
            assertThat(reacao.fz()).isCloseTo(4.6875, within(1e-12));
            assertThat(reacao.fx()).isCloseTo(0, within(1e-12));
            assertThat(reacao.fy()).isCloseTo(0, within(1e-12));
            assertThat(reacao.mx()).isCloseTo(0, within(1e-12));
            assertThat(reacao.my()).isCloseTo(0, within(1e-12));
            assertThat(reacao.mz()).isCloseTo(0, within(1e-12));
        });
        assertThat(resultado.barras()).singleElement().satisfies(barra -> {
            assertThat(barra.nMax()).isCloseTo(4.6875, within(1e-12));
            assertThat(barra.pontos()).allSatisfy(ponto -> {
                // Tração positiva: N(s)=-q(L-s), s medido da base.
                assertThat(ponto.n()).isCloseTo(-1.5625 * (3 - ponto.s()), within(1e-12));
                assertThat(ponto.vy()).isCloseTo(0, within(1e-12));
                assertThat(ponto.vz()).isCloseTo(0, within(1e-12));
                assertThat(ponto.t()).isCloseTo(0, within(1e-12));
                assertThat(ponto.my()).isCloseTo(0, within(1e-12));
                assertThat(ponto.mz()).isCloseTo(0, within(1e-12));
                assertThat(ponto.flecha()).isCloseTo(0, within(1e-12));
            });
            assertThat(barra.pontos().getFirst().n()).isCloseTo(-4.6875, within(1e-12));
            assertThat(barra.pontos().getLast().n()).isCloseTo(0, within(1e-12));
        });
    }
}
