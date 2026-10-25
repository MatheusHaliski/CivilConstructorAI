package br.com.civilconstructorai.prancha;

import br.com.civilconstructorai.comum.Ids;
import br.com.civilconstructorai.modelo.ComandoVi;
import br.com.civilconstructorai.modelo.Enums.OrigemComando;
import br.com.civilconstructorai.modelo.ModeloBim;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** RF14: planta de um modelo com eixos, pilares, vigas, laje, parede com janela e linha auxiliar (cenário da auditoria 2026-10). */
class GeradorPranchaTest {

    private static void aplicar(ModeloBim m, UUID pav, String codigo, Map<String, Object> args) {
        m.executar(new ComandoVi(Ids.novo(), codigo, args, OrigemComando.CANVAS, m.versao(), UUID.randomUUID(), Instant.now()), pav);
    }

    private static Map<String, Object> p(double x, double y) {
        return Map.of("x", x, "y", y);
    }

    @Test
    void plantaComTodosOsTiposGeraSvgEDxfComEscalaEMarcaDagua() {
        var terreo = ModeloBim.terreoInicial(UUID.randomUUID());
        var m = new ModeloBim(new ModeloBim.Estado(0, new java.util.ArrayList<>(List.of(terreo)), new java.util.ArrayList<>(), new java.util.ArrayList<>()));
        var t = terreo.id();
        aplicar(m, t, "MALHA", Map.of("nx", 2, "dx", 5, "ny", 2, "dy", 4));
        aplicar(m, t, "PAVIMENTO", Map.of("nome", "Cobertura", "z", 3.0));
        for (var c : List.of(p(0, 0), p(5, 0), p(0, 4), p(5, 4))) {
            aplicar(m, t, "PILAR", Map.of("centro", c, "b", 0.2, "h", 0.4));
        }
        aplicar(m, t, "VIGA", Map.of("p1", p(0, 0), "p2", p(5, 0), "b", 0.14, "h", 0.4));
        aplicar(m, t, "LAJE", Map.of("contorno", List.of(p(0, 0), p(5, 0), p(5, 4), p(0, 4)), "h", 0.10));
        aplicar(m, t, "PAREDE", Map.of("p1", p(0, 2), "p2", p(5, 2), "esp", 0.14, "h", 2.8));
        var parede = m.elementos().stream().filter(e -> e.getTipo().name().equals("PAREDE")).findFirst().orElseThrow();
        aplicar(m, t, "JANELA", Map.of("parede", parede.getId().toString(), "posicao", 0.5, "larg", 2.0, "alt", 1.5, "peitoril", 1.0));
        aplicar(m, t, "LINHA", Map.of("p1", p(7, 7), "p2", p(8, 7)));
        aplicar(m, t, "COTA_LINEAR", Map.of("p1", p(0, 0), "p2", p(5, 0)));
        aplicar(m, t, "TEXTO", Map.of("p", p(1, 1), "texto", "Sala"));

        var svg = GeradorPrancha.svg(m, t, false, new GeradorPrancha.Carimbo("Obra", "Térreo", "Fulana", null, "1:50", "A3", true, m.versao()));
        assertThat(svg).contains("Escala 1:50").contains("RASCUNHO").contains("CARIMBO");
        var dxf = GeradorPrancha.dxf(m, t, false);
        assertThat(dxf).contains("EIXOS").contains("COTAS");
    }
}
