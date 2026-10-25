package br.com.civilconstructorai.modelo;

import br.com.civilconstructorai.comum.ExcecaoDominio;
import br.com.civilconstructorai.modelo.Enums.OrigemComando;
import br.com.civilconstructorai.modelo.Enums.Severidade;
import br.com.civilconstructorai.notacao.Ponto2D;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Porte de packages/notacao-vi/src/modelo.test.ts. */
class ModeloBimTest {

    private static Ponto2D p(double x, double y) {
        return new Ponto2D(x, y);
    }

    private static ModeloBim.ResultadoLocal aplicar(ModeloBim m, String codigo, Map<String, Object> args) {
        return m.aplicarAuditado(codigo, args, OrigemComando.CANVAS, null);
    }

    @Test
    void criaMalhaPilaresNosEixosEVigaVersionaEDesfaz() {
        var m = new ModeloBim();
        aplicar(m, "MALHA", Map.of("nx", 3, "dx", 5, "ny", 2, "dy", 6));
        assertThat(m.eixos()).hasSize(5);
        assertThat(m.intersecoesDeEixos()).hasSize(6);
        var r = aplicar(m, "PILAR", Map.of("centro", p(0, 0), "b", 0.19, "h", 0.4));
        assertThat(r.relatorio().aprovado()).isTrue();
        assertThat(m.versao()).isEqualTo(2);
        aplicar(m, "VIGA", Map.of("p1", p(0, 0), "p2", p(5, 0)));
        assertThat(m.elementos()).hasSize(2);
        m.desfazer();
        assertThat(m.elementos()).hasSize(1);
        m.refazer();
        assertThat(m.elementos()).hasSize(2);
    }

    @Test
    void reprovaPilar15x15ComTodosOsErrosDeUmaVez() {
        var m = new ModeloBim();
        assertThatThrownBy(() -> aplicar(m, "PILAR", Map.of("centro", p(0, 0), "b", 0.15, "h", 0.15)))
                .isInstanceOf(ComandoReprovadoException.class)
                .satisfies(e -> {
                    var codigos = ((ComandoReprovadoException) e).relatorio().laco1().violacoes().stream().map(RelatorioAuditoria.Violacao::codigo).toList();
                    assertThat(codigos).contains("C-SECAO_MIN_PILAR", "C-AREA_MIN_PILAR");
                });
        assertThat(m.versao()).isZero();
        assertThat(m.elementos()).isEmpty();
    }

    @Test
    void descartaVetorNuloEParedeCurta() {
        var m = new ModeloBim();
        assertThatThrownBy(() -> aplicar(m, "PAREDE", Map.of("p1", p(0, 0), "p2", p(0, 0)))).isInstanceOf(ComandoReprovadoException.class)
                .satisfies(e -> assertThat(((ComandoReprovadoException) e).relatorio().laco1().violacoes()).extracting(RelatorioAuditoria.Violacao::codigo).contains("C-VETOR_NULO"));
        assertThatThrownBy(() -> aplicar(m, "PAREDE", Map.of("p1", p(0, 0), "p2", p(0.03, 0)))).isInstanceOf(ComandoReprovadoException.class)
                .satisfies(e -> assertThat(((ComandoReprovadoException) e).relatorio().laco1().violacoes()).extracting(RelatorioAuditoria.Violacao::codigo).contains("C-SHORT_CURVE"));
    }

    @Test
    void envoltoriaAbertaEhAvisoNaEdicaoEViolacaoNaConclusao() {
        var m = new ModeloBim();
        var pav = m.pavimentoBase().id();
        var r = aplicar(m, "PAREDE", Map.of("p1", p(0, 0), "p2", p(5, 0)));
        assertThat(r.relatorio().aprovado()).isTrue();
        assertThat(r.relatorio().laco2().avisos()).extracting(RelatorioAuditoria.Violacao::codigo).contains("ENVOLTORIA_ABERTA");
        var geo = new ServicoGeometriaLocal();
        assertThat(geo.verificarConclusao(m, pav)).anyMatch(v -> v.codigo().equals("ENVOLTORIA_ABERTA") && v.severidade() == Severidade.VIOLACAO);
        aplicar(m, "PAREDE", Map.of("p1", p(5, 0), "p2", p(5, 4)));
        aplicar(m, "PAREDE", Map.of("p1", p(5, 4), "p2", p(0, 4)));
        var fim = aplicar(m, "PAREDE", Map.of("p1", p(0, 4), "p2", p(0, 0)));
        assertThat(fim.relatorio().laco2().avisos()).isEmpty();
        assertThat(geo.verificarConclusao(m, pav).stream().filter(v -> v.severidade() == Severidade.VIOLACAO)).isEmpty();
    }

    @Test
    void disciplinaZZeraOffsetsEPrendeTopoAoPavimentoAcima() {
        var m = new ModeloBim();
        aplicar(m, "PAVIMENTO", Map.of("nome", "1º", "z", 3));
        var r = aplicar(m, "PILAR", Map.of("centro", p(1, 1), "baseOffset", -0.5));
        assertThat(r.relatorio().correcoes()).anyMatch(c -> c.campo().equals("baseOffset") && Double.valueOf(0).equals(c.depois()));
        var pilar = m.elementos().get(0);
        assertThat(pilar.num("baseOffset", 9)).isZero();
        var superior = m.pavimentosOrdenados().get(1);
        assertThat(pilar.uuid("nivelTopoId")).isEqualTo(superior.id());
    }

    @Test
    void conflitoDeVersaoLancaConflito() {
        var m = new ModeloBim();
        aplicar(m, "LINHA", Map.of("p1", p(0, 0), "p2", p(1, 0)));
        assertThatThrownBy(() -> m.aplicarAuditado("LINHA", Map.of("p1", p(0, 0), "p2", p(1, 1)), OrigemComando.CANVAS, 0L))
                .isInstanceOf(ExcecaoDominio.Conflito.class).hasMessageContaining("versão");
    }

    @Test
    void portaPrecisaCaberNaParedeComAfastamento() {
        var m = new ModeloBim();
        aplicar(m, "PAREDE", Map.of("p1", p(0, 0), "p2", p(1, 0)));
        assertThatThrownBy(() -> aplicar(m, "PORTA", Map.of("pos", p(0.5, 0), "larg", 0.9))).isInstanceOf(ComandoReprovadoException.class)
                .satisfies(e -> assertThat(((ComandoReprovadoException) e).relatorio().laco2().violacoes()).extracting(RelatorioAuditoria.Violacao::codigo).contains("G-ABERTURA_FORA_DA_FACE"));
        aplicar(m, "PAREDE", Map.of("p1", p(0, 2), "p2", p(4, 2)));
        var r = aplicar(m, "PORTA", Map.of("pos", p(2, 2)));
        assertThat(r.diff().criados.get(0).getTipo()).isEqualTo(Enums.TipoElemento.PORTA);
    }

    @Test
    void funcaoDesconhecidaEArgumentoAusenteSaoErrosDeValidacao() {
        var m = new ModeloBim();
        assertThatThrownBy(() -> aplicar(m, "TELHADO", Map.of())).isInstanceOf(ExcecaoDominio.Validacao.class).hasMessageContaining("1 problema");
        assertThatThrownBy(() -> aplicar(m, "VIGA", Map.of("p1", p(0, 0)))).isInstanceOf(ExcecaoDominio.Validacao.class)
                .satisfies(e -> assertThat(((ExcecaoDominio.Validacao) e).erros().get(0).campo()).isEqualTo("VI-002"));
    }

    @Test
    void modificacoesMovemCopiamEspelhamEApagamComAberturasHospedadas() {
        var m = new ModeloBim();
        var w = aplicar(m, "PAREDE", Map.of("p1", p(0, 0), "p2", p(4, 0))).diff().criados.get(0);
        aplicar(m, "PORTA", Map.of("pos", p(2, 0)));
        var copia = aplicar(m, "COPIAR", Map.of("sel", List.of(w.getId()), "base", p(0, 0), "destino", p(0, 3)));
        assertThat(copia.diff().criados).hasSize(1);
        assertThat(copia.diff().criados.get(0).ponto("p1")).isEqualTo(p(0, 3));
        var esp = aplicar(m, "ESPELHAR", Map.of("sel", List.of(w.getId()), "p1", p(0, 5), "p2", p(10, 5)));
        assertThat(esp.diff().criados.get(0).ponto("p1").y()).isEqualTo(10.0);
        var apaga = aplicar(m, "APAGAR", Map.of("sel", List.of(w.getId())));
        assertThat(apaga.diff().removidos).hasSize(2); // parede + porta hospedada
    }

    @Test
    void moverEixoReancoraPilares() {
        var m = new ModeloBim();
        aplicar(m, "MALHA", Map.of("nx", 2, "dx", 5, "ny", 2, "dy", 6));
        aplicar(m, "PILAR", Map.of("centro", p(5, 0)));
        var eixoB = m.eixos().stream().filter(e -> e.nome().equals("B")).findFirst().orElseThrow();
        var movidos = m.moverEixo(eixoB.id(), 6.0, 0.1);
        assertThat(movidos).hasSize(1);
        assertThat(movidos.get(0).ponto("centro").x()).isEqualTo(6.0);
    }
}
