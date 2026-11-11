package br.com.civilconstructorai.estrutura;

import br.com.civilconstructorai.estrutura.carga.*;
import br.com.civilconstructorai.estrutura.mef.SolverMef;
import br.com.civilconstructorai.estrutura.modelo.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static br.com.civilconstructorai.estrutura.DimensionamentoService.EstadoVerificacao.*;

class DimensionamentoSegurancaTest {
    private final DimensionamentoService servico = new DimensionamentoService(null, null, null, null, null, null, null, null);

    private ElementoEstrutural viga(String material) {
        return ElementoEstrutural.viga("V1", "t", new Ponto(0, 0), new Ponto(4, 0), .20, .50, material);
    }
    private ModeloEstrutural modelo(ElementoEstrutural... elementos) {
        return new ModeloEstrutural(UUID.randomUUID(), 1, List.of(new PavimentoRef("t", "Térreo", 0)), List.of(elementos));
    }
    private SolverMef.ResultadoAnalise analise(boolean esforcos) {
        var resultado = new LinkedHashMap<String, SolverMef.ResultadoCombinacao>();
        for (var c : GeradorCombinacoes.gerar(false, false)) {
            var barras = esforcos ? List.of(new SolverMef.ResultadoBarra(0, "V1", "V1", 4, List.of(), 0, 10, 10, -5, .001))
                    : List.<SolverMef.ResultadoBarra>of();
            resultado.put(c.nome(), new SolverMef.ResultadoCombinacao(c, barras, List.of(), .001));
        }
        return new SolverMef.ResultadoAnalise(2, 1, resultado);
    }
    @Test void naoConvertePerfilMetalicoOuMaterialAusenteEmConcreto() {
        for (String material : Arrays.asList("MAT_ACO_PERFIL_W", "MAT_ACO_CA50", "FCK30_INVENTADO", null)) {
            var r = servico.dimensionar(modelo(viga(material)), analise(true)).getFirst();
            assertThat(r.estadoVerificacao()).isEqualTo(NAO_SUPORTADO);
            assertThat(r.verifica()).isFalse();
            assertThat(r.acoKg()).isZero();
        }
    }
    @Test void naoSubstituiEsforcosAusentesPorZero() {
        var r = servico.dimensionar(modelo(viga("MAT_CONC_FCK30")), analise(false)).getFirst();
        assertThat(r.estadoVerificacao()).isEqualTo(NAO_VERIFICADO);
        assertThat(r.motivo()).contains("Esforços ausentes");
    }
    @Test void naoOmiteElementoIaDoRelatorio() {
        var r = servico.dimensionar(modelo(viga("MAT_CONC_FCK30").comoRascunhoIa()), analise(true)).getFirst();
        assertThat(r.verifica()).isFalse();
        assertThat(r.estadoVerificacao()).isEqualTo(NAO_VERIFICADO);
    }
    @Test void analiseSemCombinacoesOuModeloVazioNaoConclui() {
        assertThatThrownBy(() -> servico.dimensionar(modelo(viga("MAT_CONC_FCK30")),
                new SolverMef.ResultadoAnalise(2, 1, Map.of()))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> servico.dimensionar(modelo(), analise(true))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void calculoSimplificadoNaoViraAprovacaoCompleta() {
        var r = servico.dimensionar(modelo(viga("MAT_CONC_FCK30")), analise(true)).getFirst();
        assertThat(r.estadoVerificacao()).isEqualTo(NAO_VERIFICADO);
        assertThat(r.verifica()).isFalse();
        assertThat(r.motivo()).contains("Pré-dimensionamento");
        assertThatThrownBy(() -> DimensionamentoService.exigirResultadosVerificados(List.of(r)))
                .hasMessageContaining("Aprovação bloqueada");
        assertThatThrownBy(() -> DimensionamentoService.exigirResultadosVerificados(List.of()))
                .hasMessageContaining("Aprovação bloqueada");
    }
    @Test void jsonLegadoComVerificaTrueNaoComprovaCobertura() {
        var json = new ObjectMapper();
        var r = json.readValue("""
                {"elementoId":"V1","tipo":"VIGA","verifica":true,"memoria":[],
                 "mdKnm":10,"vdKn":5,"ndKn":0,"asCm2":2,"acoKg":10}
                """, DimensionamentoService.ResultadoElemento.class);
        assertThat(r.estadoVerificacao()).isEqualTo(NAO_VERIFICADO);
        assertThat(r.verifica()).isFalse();
        assertThatThrownBy(() -> DimensionamentoService.exigirResultadosVerificados(List.of(r)))
                .hasMessageContaining("Aprovação bloqueada");
        var copiado = json.readValue(json.writeValueAsString(r), DimensionamentoService.ResultadoElemento.class);
        assertThat(copiado).isEqualTo(r);
    }

    @Test void acoLegadoNaoAlimentaQuantitativoComoDimensionamentoAprovado() {
        var id = UUID.randomUUID();
        var d = new br.com.civilconstructorai.estrutura.dimensionamento.Dimensionamento(id, id, id, 1);
        d.iniciar();
        d.concluir("""
                [{"elementoId":"V1","tipo":"VIGA","verifica":true,"memoria":[],
                  "mdKnm":10,"vdKn":5,"ndKn":0,"asCm2":2,"acoKg":10}]
                """, 10, 0);
        d.aprovar(id, "ART-legada", java.time.Instant.now());
        var repositorio = org.mockito.Mockito.mock(Repositorios.Dimensionamentos.class);
        org.mockito.Mockito.when(repositorio.findByObraIdOrderByCriadoEmDesc(id)).thenReturn(List.of(d));
        var s = new DimensionamentoService(repositorio, null, null, null, null, null, null, new Json(new ObjectMapper()));
        assertThat(s.acoAprovadoPorElemento(id, 1)).isEmpty();
    }
}
