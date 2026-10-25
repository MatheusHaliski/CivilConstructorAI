package br.com.civilconstructorai.quantitativo;

import br.com.civilconstructorai.estrutura.modelo.ElementoEstrutural;
import br.com.civilconstructorai.estrutura.modelo.ModeloEstrutural;
import br.com.civilconstructorai.estrutura.modelo.PavimentoRef;
import br.com.civilconstructorai.estrutura.modelo.Ponto;
import br.com.civilconstructorai.estrutura.vaos.CalculadoraVaos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class QuantificadorTest {

    static ModeloEstrutural modelo() {
        var terreo = new PavimentoRef("pav_terreo", "Térreo", 0);
        var tipo = new PavimentoRef("pav_1", "1º", 3);
        return new ModeloEstrutural(UUID.randomUUID(), 7, List.of(terreo, tipo), List.of(
                ElementoEstrutural.pilar("p1", "pav_terreo", new Ponto(0, 0), 0.2, 0.5, "pav_terreo", "pav_1", "MAT_CONC_FCK30"),
                ElementoEstrutural.pilar("p2", "pav_terreo", new Ponto(5, 0), 0.2, 0.5, "pav_terreo", "pav_1", "MAT_CONC_FCK30"),
                ElementoEstrutural.viga("v1", "pav_terreo", new Ponto(0, 0), new Ponto(5, 0), 0.15, 0.5, "MAT_CONC_FCK30"),
                ElementoEstrutural.laje("l1", "pav_terreo", List.of(new Ponto(0, 0), new Ponto(5, 0), new Ponto(5, 4), new Ponto(0, 4)), 0.12, "MAT_CONC_FCK30"),
                ElementoEstrutural.parede("w1", "pav_terreo", new Ponto(0, 4), new Ponto(5, 4), 0.14, 2.8, "MAT_ALV_BLOCO14"),
                ElementoEstrutural.abertura("pt1", br.com.civilconstructorai.estrutura.modelo.TipoElementoEstrutural.PORTA, "pav_terreo", "w1", 0.8, 2.1),
                ElementoEstrutural.viga("ia", "pav_terreo", new Ponto(0, 4), new Ponto(5, 4), 0.15, 0.5, "MAT_CONC_FCK30").comoRascunhoIa()));
    }

    @Test
    void mesmoModeloMesmoHashERascunhoIaNaoEntra() {
        var a = Quantificador.quantificar(modelo(), Map.of());
        var b = Quantificador.quantificar(modelo(), Map.of());
        assertThat(a.hash()).isEqualTo(b.hash());
        assertThat(a.linhas().stream().map(Quantificador.Linha::elementoId)).doesNotContain("ia");
        var pilar = a.linhas().stream().filter(l -> l.elementoId().equals("p1")).findFirst().orElseThrow();
        assertThat(pilar.concretoM3()).isCloseTo(0.2 * 0.5 * 3.0, within(0.001));
        assertThat(pilar.formaM2()).isCloseTo(2 * (0.2 + 0.5) * 3.0, within(0.001));
        assertThat(pilar.acoKg()).isCloseTo(0.3 * 110, within(0.01));
        var viga = a.linhas().stream().filter(l -> l.elementoId().equals("v1")).findFirst().orElseThrow();
        assertThat(viga.concretoM3()).isCloseTo(0.15 * 0.5 * (5 - 0.5), within(0.001)); // desconta meio pilar em cada ponta
        var parede = a.linhas().stream().filter(l -> l.elementoId().equals("w1")).findFirst().orElseThrow();
        assertThat(parede.alvenariaM2()).isCloseTo(5 * 2.8 - 0.8 * 2.1, within(0.001));
        assertThat(a.totalGeral().concretoM3()).isGreaterThan(2);
    }

    @Test
    void acoAprovadoSubstituiTaxa() {
        var r = Quantificador.quantificar(modelo(), Map.of("v1", 42.0));
        var viga = r.linhas().stream().filter(l -> l.elementoId().equals("v1")).findFirst().orElseThrow();
        assertThat(viga.acoKg()).isEqualTo(42.0);
        assertThat(viga.origemAco()).contains("aprovado");
    }

    @Test
    void fencepostDetectaVigaAlemDoUltimoApoio() {
        var m = modelo();
        var com = new ModeloEstrutural(m.obraId(), 8, m.pavimentos(), new java.util.ArrayList<>(m.elementos()) {{
            add(ElementoEstrutural.viga("v2", "pav_terreo", new Ponto(5, 0), new Ponto(9, 0), 0.15, 0.5, "MAT_CONC_FCK30"));
        }});
        var rel = CalculadoraVaos.verificar(com, "pav_terreo");
        assertThat(rel.achados().stream().map(CalculadoraVaos.Achado::codigo)).contains("FENCEPOST");
        assertThat(rel.caixa().xMax()).isEqualTo(5);
    }

    @Test
    void fnv1aIgualAoPacoteTypescript() {
        assertThat(Quantificador.fnv1a("")).isEqualTo("811c9dc5");
        assertThat(Quantificador.fnv1a("a")).isEqualTo("e40c292c");
    }
}
