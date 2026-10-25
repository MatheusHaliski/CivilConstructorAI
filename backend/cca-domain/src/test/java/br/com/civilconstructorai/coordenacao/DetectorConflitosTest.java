package br.com.civilconstructorai.coordenacao;

import br.com.civilconstructorai.modelo.Enums.OrigemComando;
import br.com.civilconstructorai.modelo.ModeloBim;
import br.com.civilconstructorai.notacao.Ponto2D;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DetectorConflitosTest {

    private static Ponto2D p(double x, double y) {
        return new Ponto2D(x, y);
    }

    /** pórtico limpo: 2 pilares, viga apoiada nos dois, parede com porta de 2,10 m sob viga de 40 cm em pé-direito 3 m */
    private static ModeloBim limpo() {
        var m = new ModeloBim();
        m.aplicarAuditado("PAVIMENTO", Map.of("nome", "1º", "z", 3), OrigemComando.CANVAS, null);
        m.aplicarAuditado("PILAR", Map.of("centro", p(0, 0)), OrigemComando.CANVAS, null);
        m.aplicarAuditado("PILAR", Map.of("centro", p(5, 0)), OrigemComando.CANVAS, null);
        m.aplicarAuditado("PAREDE", Map.of("p1", p(0, 0), "p2", p(5, 0)), OrigemComando.CANVAS, null);
        m.aplicarAuditado("PORTA", Map.of("pos", p(2.5, 0)), OrigemComando.CANVAS, null);
        return m;
    }

    private static void vigaNoPrimeiro(ModeloBim m, Ponto2D a, Ponto2D b, double h) {
        var pav1 = m.pavimentosOrdenados().get(1).id();
        m.executar(new br.com.civilconstructorai.modelo.ComandoVi(java.util.UUID.randomUUID(), "VIGA", Map.of("p1", a, "p2", b, "h", h), OrigemComando.CANVAS, m.versao(), null, java.time.Instant.now()), pav1);
    }

    private static void forcar(ModeloBim m, String codigo, Map<String, Object> args) {
        m.executar(new br.com.civilconstructorai.modelo.ComandoVi(java.util.UUID.randomUUID(), codigo, args, OrigemComando.CANVAS, m.versao(), null, java.time.Instant.now()), null);
    }

    @Test
    void modeloSemDefeitosNaoTemConflitos() {
        var m = limpo();
        vigaNoPrimeiro(m, p(0, 0), p(5, 0), 0.4);
        assertThat(DetectorConflitos.detectar(m)).isEmpty();
    }

    @Test
    void pilaresSobrepostosVigaBaixaEPortaNoPilar() {
        var m = limpo();
        vigaNoPrimeiro(m, p(0, 0), p(5, 0), 1.0); // fundo a 2,00 m < topo da porta 2,10 m
        // o auditor de comandos recusa estes pilares; o detector precisa achá-los mesmo assim (ex.: modelo vindo de IFC)
        forcar(m, "PILAR", Map.of("centro", p(0.1, 0))); // sobre P1
        forcar(m, "PILAR", Map.of("centro", p(2.5, 0))); // no vão da porta
        var c = DetectorConflitos.detectar(m);
        assertThat(c).extracting(DetectorConflitos.Conflito::regra).contains("GEO-01", "CON-01", "CON-02");
        var baixa = c.stream().filter(x -> x.regra().equals("CON-02")).findFirst().orElseThrow();
        assertThat(baixa.tipo()).isEqualTo(DetectorConflitos.Tipo.CONSTRUTIVO);
        assertThat(baixa.evidencia()).contains("z = 2.00").contains("z = 2.10");
        // nenhuma mensagem propõe abrir furo em elemento estrutural
        assertThat(c).noneMatch(x -> x.mensagem().toLowerCase().contains("furo"));
    }

    @Test
    void vigaSemApoioEVigaDuplicada() {
        var m = limpo();
        vigaNoPrimeiro(m, p(0, 0), p(5, 0), 0.4);
        vigaNoPrimeiro(m, p(1, 0), p(4, 0), 0.4); // duplicada sobre a primeira
        vigaNoPrimeiro(m, p(5, 0), p(5, 4), 0.4); // ponta (5;4) sem pilar nem viga
        var c = DetectorConflitos.detectar(m);
        assertThat(c).extracting(DetectorConflitos.Conflito::regra).contains("GEO-02", "LIG-01");
        assertThat(c.stream().filter(x -> x.regra().equals("LIG-01")).map(DetectorConflitos.Conflito::evidencia)).anyMatch(e -> e.contains("(5.00; 4.00)"));
    }

    @Test
    void chaveEhEstavelEntreExecucoes() {
        var m = limpo();
        vigaNoPrimeiro(m, p(0, 0), p(5, 0), 1.0);
        var a = DetectorConflitos.detectar(m);
        var b = DetectorConflitos.detectar(m.copia());
        assertThat(a).extracting(DetectorConflitos.Conflito::chave).containsExactlyElementsOf(b.stream().map(DetectorConflitos.Conflito::chave).toList());
        assertThat(a.get(0).chave()).hasSize(24);
        assertThat(List.copyOf(a.get(0).elementos())).isSorted();
    }
}
