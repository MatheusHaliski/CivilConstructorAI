package br.com.civilconstructorai.estrutura.dimensionamento;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class BitolasSegurancaTest {
    @Test void rejeitaEstriboSemSolucaoEmVezDeViolarAreaEEspacamento() {
        assertThatThrownBy(() -> Bitolas.estribo(100, 5)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void todoEstriboRetornadoAtendeAsDuasRestricoes() {
        for (double area : new double[]{0, 0.5, 2, 8, 15, 20}) {
            for (double limite : new double[]{7.5, 10, 12, 20, 30}) {
                var e = Bitolas.estribo(area, limite);
                assertThat(e.espacamentoCm()).isPositive().isLessThanOrEqualTo(limite);
                assertThat(e.aswPorMetroCm2()).isGreaterThanOrEqualTo(area);
            }
        }
    }
    @Test void areaLongitudinalNaoPodeSerArredondadaParaBaixo() {
        for (double necessaria : new double[]{1.569, 3.141, 5.6, 10.1}) {
            var a = Bitolas.escolher(necessaria, 2, 10);
            assertThat(a.areaCm2()).isEqualTo(a.barras() * a.bitola().areaCm2());
            assertThat(a.areaCm2()).isGreaterThanOrEqualTo(necessaria);
        }
    }
    @Test void entradasInvalidasOuForaDoCatalogoNaoProduzemArmadura() {
        for (double valor : new double[]{-1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThatThrownBy(() -> Bitolas.estribo(valor, 20)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Bitolas.escolher(valor, 2, 10)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> Bitolas.estribo(1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Bitolas.escolher(1000, 2, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Bitolas.escolher(1, 2, 30)).isInstanceOf(IllegalArgumentException.class);
    }
}
