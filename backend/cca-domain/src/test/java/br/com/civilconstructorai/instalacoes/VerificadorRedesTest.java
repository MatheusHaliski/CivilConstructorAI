package br.com.civilconstructorai.instalacoes;

import br.com.civilconstructorai.estrutura.dimensionamento.Verificacao.Estado;
import br.com.civilconstructorai.instalacoes.VerificadorRedes.Componente;
import br.com.civilconstructorai.instalacoes.VerificadorRedes.Parametros;
import br.com.civilconstructorai.instalacoes.VerificadorRedes.Rede;
import br.com.civilconstructorai.instalacoes.VerificadorRedes.Regime;
import br.com.civilconstructorai.instalacoes.VerificadorRedes.Sistema;
import br.com.civilconstructorai.instalacoes.VerificadorRedes.Trecho;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Projeto de teste da proposta P-MEP-01, com defeitos introduzidos de propósito em cada sistema. */
class VerificadorRedesTest {

    private static Componente c(String id, String tipo, Sistema s, Map<String, Object> props) {
        return new Componente(id, tipo, s, 0, 0, 0, props);
    }

    private static Trecho tubo(String id, Sistema s, String o, String d, String mat, double l, double dn, double z0, double z1) {
        return new Trecho(id, s, o, d, mat, s == Sistema.ESGOTO || s == Sistema.VENTILACAO ? Regime.GRAVIDADE : Regime.PRESSURIZADO, l, dn, z0, z1, null, 0, Set.of());
    }

    private static Trecho fio(String id, String o, String d, String circ, double l, double s, Set<String> cond) {
        return new Trecho(id, Sistema.ELETRICA, o, d, "MAT.CONDUTOR.COBRE_PVC_70", Regime.CONDUTOR, l, 0, 0, 0, circ, s, cond);
    }

    private static List<String> codigos(VerificadorRedes.Resultado r, Estado e) {
        return r.itens().stream().filter(i -> i.verificacao().estado() == e).map(i -> i.verificacao().codigo() + "@" + i.elementoId()).toList();
    }

    @Test
    void esgotoComContrapendenciaDeclividadeBaixaReducaoDeDiametroESemVentilacao() {
        var rede = new Rede(List.of(c("BS", "APARELHO", Sistema.ESGOTO, Map.of()), c("CX1", "CAIXA_INSPECAO", Sistema.ESGOTO, Map.of()), c("J1", "CAIXA", Sistema.ESGOTO, Map.of()),
                c("LV", "APARELHO", Sistema.ESGOTO, Map.of())),
                List.of(tubo("E1", Sistema.ESGOTO, "BS", "J1", "MAT.TUBO.PVC_ESGOTO", 2.0, 100, 0.50, 0.49), // 0,5 % < 1 %
                        tubo("E2", Sistema.ESGOTO, "J1", "CX1", "MAT.TUBO.PVC_ESGOTO", 3.0, 75, 0.49, 0.55), // contrapendência e DN reduz
                        tubo("E3", Sistema.ESGOTO, "LV", "J1", "MAT.TUBO.PVC_AGUA_FRIA", 1.0, 40, 0.60, 0.58))); // 2 % ok, material errado
        var r = VerificadorRedes.verificar(rede, Parametros.naoInformados());
        var nao = codigos(r, Estado.NAO_ATENDIDA);
        assertThat(nao).contains("MEP-ESG-01@E1", "MEP-ESG-02@E2", "MEP-ESG-03@E2", "MEP-MAT-01@E3", "MEP-VEN-01@rede");
        assertThat(codigos(r, Estado.ATENDIDA)).contains("MEP-ESG-01@E3");
        assertThat(codigos(r, Estado.NAO_VERIFICADA)).contains("MEP-ESG-05@rede");
        assertThat(r.estado()).isEqualTo(Estado.NAO_ATENDIDA);
        // aparelhos chegam à caixa de inspeção pelo grafo (o defeito é de cota, não de conectividade)
        assertThat(nao).noneMatch(x -> x.startsWith("MEP-ESG-04"));
    }

    @Test
    void esgotoPressurizadoNaoRecebeRegraDeGravidade() {
        var t = new Trecho("R1", Sistema.ESGOTO, "BOMBA", "CX", "MAT.TUBO.PVC_ESGOTO", Regime.PRESSURIZADO, 10, 50, 0, 3, null, 0, Set.of());
        var rede = new Rede(List.of(c("BOMBA", "APARELHO", Sistema.ESGOTO, Map.of()), c("CX", "CAIXA_INSPECAO", Sistema.ESGOTO, Map.of())), List.of(t));
        var r = VerificadorRedes.verificar(rede, Parametros.naoInformados());
        assertThat(codigos(r, Estado.NAO_VERIFICADA)).contains("MEP-ESG-00@R1");
        assertThat(codigos(r, Estado.NAO_ATENDIDA)).noneMatch(x -> x.startsWith("MEP-ESG-02"));
    }

    @Test
    void aguaQuenteSemAlimentacaoDoAquecedorEMistura() {
        var rede = new Rede(List.of(c("ENT", "ENTRADA", Sistema.AGUA_FRIA, Map.of()), c("AQ", "AQUECEDOR", Sistema.AGUA_QUENTE, Map.of()),
                c("CH", "PONTO_CONSUMO", Sistema.AGUA_QUENTE, Map.of()), c("PIA", "PONTO_CONSUMO", Sistema.AGUA_FRIA, Map.of())),
                List.of(tubo("AF1", Sistema.AGUA_FRIA, "ENT", "PIA", "MAT.TUBO.PVC_AGUA_FRIA", 5, 25, 0, 0),
                        tubo("AQ1", Sistema.AGUA_QUENTE, "AQ", "CH", "MAT.TUBO.PVC_AGUA_FRIA", 4, 22, 0, 0), // PVC de água fria na quente
                        tubo("X1", Sistema.AGUA_FRIA, "PIA", "CH", "MAT.TUBO.PVC_AGUA_FRIA", 1, 25, 0, 0))); // fria ligada direto ao ponto de quente
        var r = VerificadorRedes.verificar(rede, Parametros.naoInformados());
        var nao = codigos(r, Estado.NAO_ATENDIDA);
        assertThat(nao).contains("MEP-AGU-03@AQ", "MEP-MAT-01@AQ1", "MEP-SIS-01@X1");
        assertThat(codigos(r, Estado.NAO_VERIFICADA)).contains("MEP-AGU-04@rede");
        assertThat(nao).noneMatch(x -> x.equals("MEP-AGU-02@CH"));
    }

    @Test
    void circuitoSemPeComProtecaoSubdimensionadaEQuedaDeTensao() {
        var quadro = c("QD", "QUADRO", Sistema.ELETRICA, Map.of("disjuntores", Map.of("C1", 10, "C2", 16)));
        var rede = new Rede(List.of(quadro, c("T1", "TOMADA", Sistema.ELETRICA, Map.of("potenciaW", 1500)), c("T2", "TOMADA", Sistema.ELETRICA, Map.of("potenciaW", 1000)),
                c("L1", "LUMINARIA", Sistema.ELETRICA, Map.of("potenciaW", 100)), c("L2", "LUMINARIA", Sistema.ELETRICA, Map.of("potenciaW", 100))),
                List.of(fio("C1a", "QD", "T1", "C1", 15, 2.5, Set.of("F", "N")), fio("C1b", "T1", "T2", "C1", 10, 2.5, Set.of("F", "N", "PE")),
                        fio("C2a", "QD", "L1", "C2", 40, 1.5, Set.of("F", "N")), fio("C2b", "X", "L2", "C2", 5, 1.5, Set.of("F", "N"))));
        var r = VerificadorRedes.verificar(rede, new Parametros(127, 1.0, 4.0, "limite declarado pelo projetista (exemplo de teste)"));
        var nao = codigos(r, Estado.NAO_ATENDIDA);
        // C1: 2500 W / 127 V = 19,7 A > 10 A; trecho sem PE em circuito com tomada
        assertThat(nao).contains("MEP-ELE-05@C1", "MEP-ELE-04@C1a", "MEP-CON-01@C2b", "MEP-ELE-02@C2");
        var ib = r.itens().stream().filter(i -> i.elementoId().equals("C1") && i.verificacao().codigo().equals("MEP-ELE-05")).findFirst().orElseThrow();
        assertThat(ib.verificacao().evidencia()).contains("19.69 A");
        // Iz nunca é inventado
        assertThat(codigos(r, Estado.DADOS_INSUFICIENTES)).contains("MEP-ELE-06@C1", "MEP-ELE-06@C2");
        // C2: 200 W, 45 m, 1,5 mm²: ΔV = 2·0,0172·45·1,575/1,5 = 1,63 V (1,28 %) ≤ 4 %
        assertThat(codigos(r, Estado.ATENDIDA)).contains("MEP-ELE-07@C2");
        // C1: 25 m, 2,5 mm²: ΔV = 2·0,0172·25·19,69/2,5 = 6,77 V (5,33 %) > 4 %
        assertThat(nao).contains("MEP-ELE-07@C1");
    }

    @Test
    void semParametrosNaoHaFalsoAtendimentoEAssinaturaMudaComARede() {
        var rede = new Rede(List.of(c("QD", "QUADRO", Sistema.ELETRICA, Map.of()), c("T1", "TOMADA", Sistema.ELETRICA, Map.of("potenciaW", 600))),
                List.of(fio("a", "QD", "T1", "C1", 10, 2.5, Set.of("F", "N", "PE"))));
        var r = VerificadorRedes.verificar(rede, Parametros.naoInformados());
        assertThat(codigos(r, Estado.ATENDIDA)).isEmpty();
        assertThat(r.estado()).isEqualTo(Estado.DADOS_INSUFICIENTES);
        var outra = new Rede(rede.componentes(), List.of(fio("a", "QD", "T1", "C1", 12, 2.5, Set.of("F", "N", "PE"))));
        assertThat(VerificadorRedes.assinatura(outra, Parametros.naoInformados())).isNotEqualTo(r.assinatura());
    }
}
