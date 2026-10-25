package br.com.civilconstructorai.instalacoes;

import br.com.civilconstructorai.estrutura.dimensionamento.Verificacao.Estado;
import br.com.civilconstructorai.modelo.Enums.OrigemComando;
import br.com.civilconstructorai.modelo.ModeloBim;
import br.com.civilconstructorai.notacao.Ponto2D;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RedeDoModeloTest {

    private static UUID id(ModeloBim m, String codigo, Map<String, Object> args) {
        return m.aplicarAuditado(codigo, args, OrigemComando.CANVAS, null).diff().criados.getFirst().getId();
    }

    private static ModeloBim casa() {
        var m = new ModeloBim();
        m.aplicarAuditado("PAREDE", Map.of("p1", new Ponto2D(0, 0), "p2", new Ponto2D(6, 0)), OrigemComando.CANVAS, null);
        m.aplicarAuditado("PAREDE", Map.of("p1", new Ponto2D(6, 0), "p2", new Ponto2D(6, 4)), OrigemComando.CANVAS, null);
        m.aplicarAuditado("PAREDE", Map.of("p1", new Ponto2D(6, 4), "p2", new Ponto2D(0, 4)), OrigemComando.CANVAS, null);
        m.aplicarAuditado("PAREDE", Map.of("p1", new Ponto2D(0, 4), "p2", new Ponto2D(0, 0)), OrigemComando.CANVAS, null);
        return m;
    }

    private static List<String> codigos(VerificadorRedes.Resultado r, Estado e) {
        return r.itens().stream().filter(i -> i.verificacao().estado() == e).map(i -> i.verificacao().codigo()).toList();
    }

    @Test
    void conexoesDoModeloViramTrechosVerificaveis() {
        var m = casa();
        var qd = id(m, "OBJETO", Map.of("item", "INSTALACOES.QUADRO_DISTRIBUICAO", "pos", new Ponto2D(5.9, 2)));
        var tom = id(m, "OBJETO", Map.of("item", "INSTALACOES.TOMADA_10A", "pos", new Ponto2D(1, 0.1)));
        var gel = id(m, "OBJETO", Map.of("item", "COZINHA.GELADEIRA", "pos", new Ponto2D(1, 0.52)));
        id(m, "CONECTAR", Map.of("de", qd.toString(), "para", tom.toString(), "circuito", "C2", "disjuntor", 10));
        id(m, "CONECTAR", Map.of("de", tom.toString(), "para", gel.toString(), "circuito", "C2"));
        var lav = id(m, "OBJETO", Map.of("item", "BANHEIRO.LAVATORIO", "pos", new Ponto2D(3, 3.8)));
        var ci = id(m, "OBJETO", Map.of("item", "INSTALACOES.CAIXA_INSPECAO", "pos", new Ponto2D(3, 5)));
        id(m, "CONECTAR", Map.of("de", lav.toString(), "para", ci.toString(), "sistema", "ESGOTO"));
        var ent = id(m, "OBJETO", Map.of("item", "INSTALACOES.ENTRADA_AGUA", "pos", new Ponto2D(0.1, 3)));
        id(m, "CONECTAR", Map.of("de", ent.toString(), "para", lav.toString()));

        var rede = RedeDoModelo.montar(m);
        assertThat(rede.trechos()).hasSize(4);
        assertThat(rede.componentes()).extracting(VerificadorRedes.Componente::tipo).contains("QUADRO", "TOMADA", "APARELHO", "PONTO_CONSUMO", "CAIXA_INSPECAO", "ENTRADA");
        var quadro = rede.componentes().stream().filter(c -> c.tipo().equals("QUADRO")).findFirst().orElseThrow();
        assertThat(quadro.props().get("disjuntores")).isEqualTo(Map.of("C2", 10.0));
        var esgoto = rede.trechos().stream().filter(t -> t.sistema() == VerificadorRedes.Sistema.ESGOTO).findFirst().orElseThrow();
        assertThat((esgoto.cotaOrigem() - esgoto.cotaDestino()) / esgoto.comprimentoM()).isCloseTo(0.02, org.assertj.core.data.Offset.offset(1e-3));

        var r = VerificadorRedes.verificar(rede, new VerificadorRedes.Parametros(127, 1.0, Double.NaN, null));
        assertThat(codigos(r, Estado.NAO_ATENDIDA)).doesNotContain("MEP-CON-01", "MEP-SIS-01", "MEP-ELE-02", "MEP-AGU-02", "MEP-ESG-02");
        assertThat(codigos(r, Estado.ATENDIDA)).contains("MEP-ELE-05");
        assertThat(r.itens()).anySatisfy(i -> assertThat(i.verificacao().evidencia()).contains("150/(127·1.00)").contains("declarada pelas cargas"));
    }

    @Test
    void chuveiroDe220VUsaATensaoDoProprioCircuito() {
        var m = casa();
        var qd = id(m, "OBJETO", Map.of("item", "INSTALACOES.QUADRO_DISTRIBUICAO", "pos", new Ponto2D(5.9, 2)));
        var ch = id(m, "OBJETO", Map.of("item", "BANHEIRO.CHUVEIRO_ELETRICO", "pos", new Ponto2D(0.1, 2)));
        id(m, "CONECTAR", Map.of("de", qd.toString(), "para", ch + ":E1", "circuito", "C4", "disjuntor", 32));
        var r = VerificadorRedes.verificar(RedeDoModelo.montar(m), new VerificadorRedes.Parametros(127, 1.0, Double.NaN, null));
        // 5500 W / 220 V = 25 A ≤ 32 A; com 127 V daria 43 A e reprovaria por engano
        assertThat(r.itens()).anySatisfy(i -> {
            assertThat(i.verificacao().codigo()).isEqualTo("MEP-ELE-05");
            assertThat(i.verificacao().estado()).isEqualTo(Estado.ATENDIDA);
            assertThat(i.verificacao().evidencia()).contains("5500/(220·1.00) = 25.00 A");
        });
    }

    @Test
    void desligarLuminariaNaoTiraACargaDoCircuito() {
        var m = casa();
        var qd = id(m, "OBJETO", Map.of("item", "INSTALACOES.QUADRO_DISTRIBUICAO", "pos", new Ponto2D(5.9, 2)));
        var lum = id(m, "OBJETO", Map.of("item", "ILUMINACAO.PLAFON", "pos", new Ponto2D(3, 2)));
        id(m, "CONECTAR", Map.of("de", qd.toString(), "para", lum.toString(), "circuito", "C1", "disjuntor", 10));
        m.aplicarAuditado("ALTERAR", Map.of("id", lum.toString(), "props", Map.of("ligada", false)), OrigemComando.CANVAS, null);
        var rede = RedeDoModelo.montar(m);
        assertThat(rede.trechos()).hasSize(1);
        assertThat(rede.componentes().stream().filter(c -> c.tipo().equals("LUMINARIA")).findFirst().orElseThrow().props()).containsKey("potenciaW");
    }
}
