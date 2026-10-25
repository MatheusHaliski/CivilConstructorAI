package br.com.civilconstructorai.estrutura.mef;

import br.com.civilconstructorai.estrutura.carga.Combinacao;
import br.com.civilconstructorai.estrutura.carga.NaturezaAcao;
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

class MontadorModeloAnaliticoTest {

    private static final Combinacao G = new Combinacao("G", Combinacao.EstadoLimite.ELS_RARA, Map.of(NaturezaAcao.PERMANENTE, 1.0));

    /** viga de 15 m sobre 4 pilares (x = 0, 5, 10, 15) e uma viga transversal que nasce no meio dela */
    private static ModeloEstrutural modelo() {
        var terreo = new PavimentoRef("t", "Térreo", 0);
        var p1 = new PavimentoRef("p1", "1º", 3);
        var els = new ArrayList<ElementoEstrutural>();
        for (int i = 0; i <= 3; i++) {
            els.add(ElementoEstrutural.pilar("P" + i, "t", new Ponto(5 * i, 0), 0.2, 0.4, "t", "p1", "MAT_CONC_FCK30"));
        }
        els.add(ElementoEstrutural.viga("V1", "p1", new Ponto(0, 0), new Ponto(15, 0), 0.15, 0.5, "MAT_CONC_FCK30"));
        return new ModeloEstrutural(UUID.randomUUID(), 1, List.of(terreo, p1), els);
    }

    @Test
    void vigaContinuaViraUmaBarraPorTrechoEntreApoios() {
        var ma = MontadorModeloAnalitico.montar(new MontadorModeloAnalitico.Entrada(modelo(), List.of(), null, null));
        var trechos = ma.barras().stream().filter(b -> b.elementoId().equals("V1")).toList();
        assertThat(trechos).hasSize(3);
        // cada trecho liga topos de pilares (nós compartilhados com as barras dos pilares)
        var topos = ma.barras().stream().filter(b -> b.elementoId().startsWith("P")).map(ModeloAnalitico.Barra::noJ).toList();
        assertThat(trechos).allSatisfy(b -> assertThat(topos).contains(b.noI(), b.noJ()));
    }

    @Test
    void momentoDeVigaContinuaEhMuitoMenorQueODeUmVaoUnicoDe15m() {
        var ma = MontadorModeloAnalitico.montar(new MontadorModeloAnalitico.Entrada(modelo(), List.of(), null, null));
        var r = new SolverMef(ma).resolver(List.of(G)).porCombinacao().get("G");
        double q = 0.15 * 0.5 * MontadorModeloAnalitico.PESO_CONCRETO;
        double mMaxViga = r.barras().stream().filter(b -> b.elementoId().equals("V1")).mapToDouble(b -> Math.max(Math.abs(b.mMax()), Math.abs(b.mMin()))).max().orElseThrow();
        // contínua de 3 vãos de 5 m: |M| ≤ q·5²/8 com folga; vão único de 15 m daria q·15²/8 (9×)
        assertThat(mMaxViga).isLessThan(q * 5 * 5 / 8 * 1.2);
        // equilíbrio: soma das reações = peso da viga + pilares
        double pesoPilares = 4 * 0.2 * 0.4 * 3 * MontadorModeloAnalitico.PESO_CONCRETO;
        assertThat(r.reacoes().stream().mapToDouble(SolverMef.Reacao::fz).sum()).isCloseTo(q * 15 + pesoPilares, org.assertj.core.api.Assertions.within(0.5));
    }
}
