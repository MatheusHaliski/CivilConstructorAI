package br.com.civilconstructorai.estrutura.dimensionamento;

import br.com.civilconstructorai.estrutura.mef.MontadorModeloAnalitico;
import br.com.civilconstructorai.estrutura.modelo.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class ConcretoSuportadoTest {
    @Test void usaResistenciaDoCadastro() {
        assertThat(ConcretoSuportado.fck("MAT_CONC_FCK25")).isEqualTo(25);
        assertThat(ConcretoSuportado.fck("MAT_CONC_FCK30")).isEqualTo(30);
    }
    @Test void analiseTambemBloqueiaMaterialIncompativelMesmoComModuloInformado() {
        for (String id : Arrays.asList("MAT_ACO_PERFIL_W", "MAT_ACO_CA50", "FCK30", null)) {
            var v = ElementoEstrutural.viga("V1", "t", new Ponto(0,0), new Ponto(4,0), .2, .5, id);
            var m = new ModeloEstrutural(UUID.randomUUID(), 1, List.of(new PavimentoRef("t", "Térreo", 0)), List.of(v));
            assertThatThrownBy(() -> MontadorModeloAnalitico.montar(new MontadorModeloAnalitico.Entrada(
                    m, List.of(), null, Map.of("MAT_ACO_PERFIL_W", 210e6))))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Material");
        }
    }
}
