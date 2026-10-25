package br.com.civilconstructorai.interop;

import br.com.civilconstructorai.modelo.Enums.OrigemComando;
import br.com.civilconstructorai.modelo.ModeloBim;
import br.com.civilconstructorai.notacao.Ponto2D;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EscritorIfcTest {

    private static Ponto2D p(double x, double y) {
        return new Ponto2D(x, y);
    }

    @Test
    void escreveIfc4x3QueOLeitorMinimoAceitaComAsEntidadesEsperadas() {
        var m = new ModeloBim();
        m.aplicarAuditado("PAVIMENTO", Map.of("nome", "1º", "z", 3), OrigemComando.CANVAS, null);
        m.aplicarAuditado("PAREDE", Map.of("p1", p(0, 0), "p2", p(5, 0)), OrigemComando.CANVAS, null);
        m.aplicarAuditado("PILAR", Map.of("centro", p(0, 0)), OrigemComando.CANVAS, null);
        m.aplicarAuditado("VIGA", Map.of("p1", p(0, 0), "p2", p(5, 0)), OrigemComando.CANVAS, null);
        m.aplicarAuditado("LAJE", Map.of("contorno", List.of(p(0, 0), p(5, 0), p(5, 4), p(0, 4)), "h", 0.12), OrigemComando.CANVAS, null);
        m.aplicarAuditado("PORTA", Map.of("pos", p(2.5, 0)), OrigemComando.CANVAS, null);
        var ifc = EscritorIfc.escrever(m, "Obra Teste", "Eng. Responsável", Instant.parse("2026-10-25T00:00:00Z"));
        var resumo = LeitorIfc.ler(ifc.getBytes(StandardCharsets.UTF_8));
        assertThat(resumo.valido()).as(resumo.problemas().toString()).isTrue();
        assertThat(resumo.schema()).isEqualTo("IFC4X3_ADD2");
        assertThat(resumo.porTipo()).containsEntry("IFCPROJECT", 1).containsEntry("IFCSITE", 1).containsEntry("IFCBUILDING", 1)
                .containsEntry("IFCBUILDINGSTOREY", 2).containsEntry("IFCWALL", 1).containsEntry("IFCCOLUMN", 1).containsEntry("IFCBEAM", 1)
                .containsEntry("IFCSLAB", 1).containsEntry("IFCDOOR", 1);
        assertThat(resumo.porTipo().get("IFCPROPERTYSET")).isEqualTo(5);
        assertThat(ifc).contains("Pset_TabelaA").contains("MAT.CONCRETO.C30").doesNotContain("FireRating").doesNotContain("CPF");
    }

    @Test
    void globalIdEhEstavelETem22Caracteres() {
        var id = UUID.fromString("0192f4c2-5c1e-7b3a-9d2e-0123456789ab");
        var g1 = EscritorIfc.globalId(id);
        assertThat(g1).hasSize(22).isEqualTo(EscritorIfc.globalId(id));
        assertThat(g1.charAt(0)).isIn('0', '1', '2', '3');
        assertThat(g1).matches("[0-9A-Za-z_$]{22}");
        assertThat(EscritorIfc.globalId(UUID.randomUUID())).isNotEqualTo(g1);
    }

    @Test
    void leitorRejeitaArquivoTruncadoOuSemProjeto() {
        var r = LeitorIfc.ler("ISO-10303-21;\nHEADER;\nFILE_SCHEMA(('IFC4'));\nENDSEC;\nDATA;\n#1=IFCWALL('x');\n".getBytes(StandardCharsets.UTF_8));
        assertThat(r.valido()).isFalse();
        assertThat(r.problemas()).anyMatch(s -> s.contains("END-ISO")).anyMatch(s -> s.contains("IfcProject"));
    }
}
