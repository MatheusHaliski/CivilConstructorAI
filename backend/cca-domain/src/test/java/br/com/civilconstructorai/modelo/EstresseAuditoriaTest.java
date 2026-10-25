package br.com.civilconstructorai.modelo;

import br.com.civilconstructorai.modelo.Enums.OrigemComando;
import br.com.civilconstructorai.modelo.Enums.Severidade;
import br.com.civilconstructorai.notacao.Ponto2D;
import br.com.civilconstructorai.notacao.Sanitizador;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Suíte de estresse do RF16 (E01–E18): a auditoria elimina 100 % das inconsistências listadas nas diretrizes. */
class EstresseAuditoriaTest {

    private static Ponto2D p(double x, double y) {
        return new Ponto2D(x, y);
    }

    private static ModeloBim.ResultadoLocal ok(ModeloBim m, String codigo, Map<String, Object> args) {
        var r = m.aplicarAuditado(codigo, args, OrigemComando.CANVAS, null);
        assertThat(r.relatorio().aprovado()).isTrue();
        return r;
    }

    private static ComandoReprovadoException reprova(ModeloBim m, String codigo, Map<String, Object> args) {
        try {
            m.aplicarAuditado(codigo, args, OrigemComando.IA, null);
        } catch (ComandoReprovadoException e) {
            return e;
        }
        throw new AssertionError("deveria reprovar: " + codigo);
    }

    /** prédio de 4 pavimentos, malha 5x3, pilares em todos os cruzamentos, vigas em todas as linhas e lajes */
    private static ModeloBim predio4Pavimentos() {
        var m = new ModeloBim();
        ok(m, "MALHA", Map.of("nx", 5, "dx", 5, "ny", 3, "dy", 6));
        for (int i = 1; i <= 3; i++) {
            ok(m, "PAVIMENTO", Map.of("nome", i + "º", "z", 3.0 * i));
        }
        for (var pav : m.pavimentosOrdenados()) {
            for (var ix : m.intersecoesDeEixos()) {
                ok(m, "PILAR", Map.of("pav", pav.id().toString(), "centro", ix.p(), "b", 0.2, "h", 0.5));
            }
            for (int j = 0; j <= 2; j++) {
                ok(m, "VIGA", Map.of("pav", pav.id().toString(), "p1", p(0, j * 6), "p2", p(20, j * 6), "b", 0.15, "h", 0.5));
            }
            ok(m, "LAJE", Map.of("pav", pav.id().toString(), "contorno", List.of(p(0, 0), p(20, 0), p(20, 12), p(0, 12)), "h", 0.12));
        }
        return m;
    }

    @Test
    void e01_e03_predioDe4PavimentosMalha5x3ComMarquisesEscalonadasEAberturasNoTerreo() {
        var m = predio4Pavimentos();
        assertThat(m.pavimentos()).hasSize(4);
        assertThat(m.elementos().stream().filter(e -> e.getTipo() == Enums.TipoElemento.PILAR)).hasSize(4 * 15);
        // E02 marquises escalonadas 1,0–2,0 m por andar: lajes em balanço dentro da folga da caixa cartesiana
        var ord = m.pavimentosOrdenados();
        double[] balancos = {0, 1.0, 1.5, 2.0};
        for (int i = 1; i < ord.size(); i++) {
            var r = m.aplicarAuditado("LAJE", Map.of("pav", ord.get(i).id().toString(), "contorno", List.of(p(0, 12), p(20, 12), p(20, 12 + balancos[i]), p(0, 12 + balancos[i])), "h", 0.1), OrigemComando.CANVAS, null);
            assertThat(r.relatorio().aprovado()).isTrue();
            // marquise de 1,5 e 2,0 m extrapola a folga de 0,5 m da caixa dos eixos → recortada (RF06), nunca reprovada
            if (balancos[i] > 0.5) {
                assertThat(r.relatorio().correcoes()).anyMatch(c -> c.regra().contains("Caixa Cartesiana"));
            }
        }
        // E03 aberturas no térreo: paredes externas fechadas + porta e janela
        var terreo = ord.get(0).id().toString();
        ok(m, "PAREDE", Map.of("pav", terreo, "p1", p(0, 0), "p2", p(20, 0)));
        ok(m, "PAREDE", Map.of("pav", terreo, "p1", p(20, 0), "p2", p(20, 12)));
        ok(m, "PAREDE", Map.of("pav", terreo, "p1", p(20, 12), "p2", p(0, 12)));
        var fecha = ok(m, "PAREDE", Map.of("pav", terreo, "p1", p(0, 12), "p2", p(0, 0)));
        assertThat(fecha.relatorio().laco2().avisos()).isEmpty();
        ok(m, "PORTA", Map.of("pav", terreo, "pos", p(10, 0), "larg", 0.9, "alt", 2.1));
        ok(m, "JANELA", Map.of("pav", terreo, "pos", p(20, 6), "larg", 1.2, "alt", 1.2, "peitoril", 1.0));
        assertThat(new ServicoGeometriaLocal().verificarConclusao(m, ord.get(0).id()).stream().filter(v -> v.severidade() == Severidade.VIOLACAO)).isEmpty();
    }

    @Test
    void e04_fencepostExtrapolacaoDe4mEmMalha4x4EhRecortadaPelaCaixaCartesiana() {
        var m = new ModeloBim();
        ok(m, "MALHA", Map.of("nx", 4, "dx", 4, "ny", 4, "dy", 4));
        var r = m.aplicarAuditado("VIGA", Map.of("p1", p(0, 0), "p2", p(16, 0), "b", 0.15, "h", 0.4), OrigemComando.IA, null);
        assertThat(r.relatorio().aprovado()).isTrue();
        assertThat(r.relatorio().correcoes()).anyMatch(c -> c.regra().contains("Caixa Cartesiana"));
        assertThat(r.diff().criados.get(0).ponto("p2").x()).isLessThanOrEqualTo(12.5);
    }

    @Test
    void e05_e06_efeitoZ0ETranspasseEntrePavimentos() {
        var m = new ModeloBim();
        ok(m, "PAVIMENTO", Map.of("nome", "1º", "z", 3));
        ok(m, "PAVIMENTO", Map.of("nome", "2º", "z", 6));
        var r = m.aplicarAuditado("PILAR", Map.of("centro", p(1, 1), "baseOffset", -0.8, "topoOffset", 0.3), OrigemComando.IA, null);
        assertThat(r.relatorio().correcoes()).extracting(RelatorioAuditoria.Correcao::campo).contains("baseOffset", "topoOffset");
        var pilar = r.diff().criados.get(0);
        assertThat(pilar.num("baseOffset", 9)).isZero();
        assertThat(pilar.num("topoOffset", 9)).isZero();
        // transpasse: topo no 2º sem `continuo` → corrigido para o 1º
        var topo2 = m.pavimentosOrdenados().get(2).id();
        var r2 = m.aplicarAuditado("PILAR", Map.of("centro", p(5, 1), "nivelTopoId", topo2.toString()), OrigemComando.IA, null);
        assertThat(r2.diff().criados.get(0).uuid("nivelTopoId")).isEqualTo(m.pavimentosOrdenados().get(1).id());
        var r3 = ok(m, "PILAR", Map.of("centro", p(10, 1), "continuo", true));
        assertThat(r3.relatorio().correcoes()).noneMatch(c -> c.regra().contains("transpasse"));
    }

    @Test
    void e07_e09_vetorNuloParedeCurtaEPilar15x15() {
        var m = new ModeloBim();
        assertThat(reprova(m, "VIGA", Map.of("p1", p(1, 1), "p2", p(1, 1))).relatorio().laco1().violacoes()).extracting(RelatorioAuditoria.Violacao::codigo).contains("C-VETOR_NULO");
        assertThat(reprova(m, "PAREDE", Map.of("p1", p(0, 0), "p2", p(0.03, 0))).relatorio().laco1().violacoes()).extracting(RelatorioAuditoria.Violacao::codigo).contains("C-SHORT_CURVE");
        assertThat(reprova(m, "PILAR", Map.of("centro", p(0, 0), "b", 0.15, "h", 0.15)).relatorio().laco1().violacoes()).extracting(RelatorioAuditoria.Violacao::codigo).contains("C-SECAO_MIN_PILAR", "C-AREA_MIN_PILAR");
        assertThat(m.elementos()).isEmpty();
    }

    @Test
    void e10_e12_janelaMaiorQueAParedeEPilaresSobrepostos() {
        var m = new ModeloBim();
        ok(m, "PAREDE", Map.of("p1", p(0, 0), "p2", p(2, 0), "h", 2.8));
        var e10 = reprova(m, "JANELA", Map.of("pos", p(1, 0), "larg", 3.0, "alt", 1.2, "peitoril", 1.0));
        assertThat(e10.relatorio().laco2().violacoes()).extracting(RelatorioAuditoria.Violacao::codigo).contains("G-ABERTURA_FORA_DA_FACE");
        var alta = reprova(m, "JANELA", Map.of("pos", p(1, 0), "larg", 1.0, "alt", 2.0, "peitoril", 1.0));
        assertThat(alta.relatorio().laco2().violacoes()).extracting(RelatorioAuditoria.Violacao::codigo).contains("G-ABERTURA_ALTA");
        ok(m, "PILAR", Map.of("centro", p(5, 5)));
        var e12 = reprova(m, "PILAR", Map.of("centro", p(5.05, 5)));
        assertThat(e12.relatorio().laco2().violacoes()).extracting(RelatorioAuditoria.Violacao::codigo).contains("G-INTERPENETRACAO");
    }

    @Test
    void e11_envoltoriaComFolgaDe2cmEhAvisoNaEdicaoEReprovaNaConclusao() {
        var m = new ModeloBim();
        ok(m, "PAREDE", Map.of("p1", p(0, 0), "p2", p(5, 0)));
        ok(m, "PAREDE", Map.of("p1", p(5, 0), "p2", p(5, 4)));
        ok(m, "PAREDE", Map.of("p1", p(5, 4), "p2", p(0, 4)));
        var r = ok(m, "PAREDE", Map.of("p1", p(0, 4), "p2", p(0, 0.02)));
        assertThat(r.relatorio().laco2().avisos()).extracting(RelatorioAuditoria.Violacao::codigo).contains("ENVOLTORIA_ABERTA");
        var conclusao = new ServicoGeometriaLocal().verificarConclusao(m, m.pavimentoBase().id());
        assertThat(conclusao).anyMatch(v -> v.codigo().equals("ENVOLTORIA_ABERTA") && v.severidade() == Severidade.VIOLACAO);
    }

    @Test
    void e13_materialInexistenteReprovaNoLaco1() {
        var m = new ModeloBim();
        var e = reprova(m, "VIGA", Map.of("p1", p(0, 0), "p2", p(5, 0), "mat", "MAT_INEXISTENTE"));
        assertThat(e.relatorio().laco1().violacoes()).extracting(RelatorioAuditoria.Violacao::codigo).contains("VI-003");
    }

    @Test
    void e14_textoComOrdinaisEEmojisEhSanitizadoSemErro() {
        assertThat(Sanitizador.identificador("MAT_CONC_FCK30 ☀️ 1ª")).isEqualTo("MAT_CONC_FCK30_1a");
        assertThat(Sanitizador.identificador("Pilar P-3 (5º)")).isEqualTo("Pilar_P-3_5o");
        assertThat(Sanitizador.texto("Viga V12 – vão 5º 😀")).doesNotContain("😀").doesNotContain("º");
    }

    @Test
    void e15_e16_auditoriaIdempotenteEComandoDoUsuarioNaoVaiAoLlm() {
        var m = new ModeloBim();
        ok(m, "MALHA", Map.of("nx", 3, "dx", 5, "ny", 2, "dy", 6));
        var r = ok(m, "PILAR", Map.of("centro", p(0, 0)));
        var relatorio2 = new Auditor(new ServicoGeometriaLocal()).auditar(m.copia(), r.diff(), Auditor.Opcoes.padrao());
        assertThat(relatorio2.correcoes()).isEmpty();
        assertThat(relatorio2.aprovado()).isTrue();
        // usuário: reprovação volta direto (sem iterações de LLM)
        var e = reprova(m, "PILAR", Map.of("centro", p(0, 0), "b", 0.1, "h", 0.1));
        assertThat(e.relatorio().iteracoesLlm()).isZero();
    }

    @Test
    void e17_e18_lajeAutoIntersectanteEContornoAberto() {
        var m = new ModeloBim();
        var e17 = reprova(m, "LAJE", Map.of("contorno", List.of(p(0, 0), p(4, 4), p(4, 0), p(0, 4)), "h", 0.12));
        assertThat(e17.relatorio().laco2().violacoes()).extracting(RelatorioAuditoria.Violacao::codigo).contains("G-AUTO_INTERSECAO");
        assertThatThrownBy(() -> m.aplicarAuditado("LAJE", Map.of("contorno", List.of(p(0, 0), p(4, 0)), "h", 0.12), OrigemComando.CANVAS, null))
                .isInstanceOf(br.com.civilconstructorai.comum.ExcecaoDominio.Validacao.class);
        var fina = reprova(m, "LAJE", Map.of("contorno", List.of(p(0, 0), p(4, 0), p(4, 4), p(0, 4)), "h", 0.05));
        assertThat(fina.relatorio().laco1().violacoes()).extracting(RelatorioAuditoria.Violacao::codigo).contains("C-ESP_MIN_LAJE");
    }
}
