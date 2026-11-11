package br.com.civilconstructorai.estrutura.mef;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class RaizesPolinomiaisTest {
    @Test
    void isolaSeteRaizesInclusiveExtremidadesSemDependerDaEscalaDosCoeficientes() {
        // t(t-1/8)(t-1/4)(t-1/2)(t-3/4)(t-7/8)(t-1), expandido com coeficientes binários exatos.
        double[] p = {0,.01025390625,-.17919921875,1.1142578125,-3.2421875,4.796875,-3.5,1};
        double[] esperadas = {0,.125,.25,.5,.75,.875,1};
        for (double escala : new double[]{1,1e-250,1e250}) {
            var raizes = RaizesPolinomiais.noIntervaloUnitario(Arrays.stream(p).map(x -> x*escala).toArray());
            assertThat(raizes).hasSize(esperadas.length);
            for (int i=0; i<esperadas.length; i++) assertThat(raizes.get(i)).isCloseTo(esperadas[i],within(3e-11));
        }
    }

    @Test
    void incluiRaizMultiplaSemTrocaDeSinal() {
        // (t-0,2)^2(t-0,8): verificar apenas troca de sinal perderia a raiz 0,2.
        var raizes = RaizesPolinomiais.noIntervaloUnitario(new double[]{-.032,.36,-1.2,1});
        assertThat(raizes.stream().anyMatch(t -> Math.abs(t-.2)<1e-11)).isTrue();
        assertThat(raizes.stream().anyMatch(t -> Math.abs(t-.8)<1e-11)).isTrue();
    }

    @Test
    void polinomiosConstantesENulosNaoCriamExtremosEspurios() {
        assertThat(RaizesPolinomiais.noIntervaloUnitario(new double[]{0,0,0})).isEmpty();
        assertThat(RaizesPolinomiais.noIntervaloUnitario(new double[]{2,0,0})).isEmpty();
        assertThat(RaizesPolinomiais.noIntervaloUnitario(new double[]{1,1})).isEmpty();
    }

    @Test
    void rejeitaCoeficientesNaoFinitosEmVezDeRetornarFalsaAusenciaDeExtremos() {
        assertThatThrownBy(() -> RaizesPolinomiais.noIntervaloUnitario(new double[]{1,Double.NaN}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RaizesPolinomiais.noIntervaloUnitario(new double[]{Double.POSITIVE_INFINITY,1}))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
