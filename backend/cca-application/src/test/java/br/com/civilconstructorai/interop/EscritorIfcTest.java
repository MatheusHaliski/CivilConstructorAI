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
    void objetosSaemComEntidadeDoCatalogoPortasEConexoesEntrePortas() throws Exception {
        var m = new ModeloBim();
        m.aplicarAuditado("PAREDE", Map.of("p1", p(0, 0), "p2", p(5, 0)), OrigemComando.CANVAS, null);
        m.aplicarAuditado("AMBIENTE", Map.of("ponto", p(2, 2), "nome", "Cozinha"), OrigemComando.CANVAS, null);
        var tom = m.aplicarAuditado("OBJETO", Map.of("item", "INSTALACOES.TOMADA_10A", "pos", p(1, 0.1)), OrigemComando.CANVAS, null).diff().criados.getFirst().getId();
        var gel = m.aplicarAuditado("OBJETO", Map.of("item", "COZINHA.GELADEIRA", "pos", p(1, 0.52)), OrigemComando.CANVAS, null).diff().criados.getFirst().getId();
        m.aplicarAuditado("OBJETO", Map.of("item", "SALA.SOFA_3L", "pos", p(3, 2)), OrigemComando.CANVAS, null);
        m.aplicarAuditado("OBJETO", Map.of("item", "PESSOAS.ADULTO", "pos", p(4, 2)), OrigemComando.CANVAS, null);
        m.aplicarAuditado("CONECTAR", Map.of("de", tom.toString(), "para", gel.toString(), "circuito", "C3"), OrigemComando.CANVAS, null);
        // quadro com duas saídas: no IFC cada porta liga-se a uma só outra, então S1 vira S1/1 e S1/2
        var qd = m.aplicarAuditado("OBJETO", Map.of("item", "INSTALACOES.QUADRO_DISTRIBUICAO", "pos", p(4.5, 0.1)), OrigemComando.CANVAS, null).diff().criados.getFirst().getId();
        var tom2 = m.aplicarAuditado("OBJETO", Map.of("item", "INSTALACOES.TOMADA_10A", "pos", p(3, 0.1)), OrigemComando.CANVAS, null).diff().criados.getFirst().getId();
        m.aplicarAuditado("CONECTAR", Map.of("de", qd.toString(), "para", tom.toString()), OrigemComando.CANVAS, null);
        m.aplicarAuditado("CONECTAR", Map.of("de", qd.toString(), "para", tom2.toString()), OrigemComando.CANVAS, null);
        var ifc = EscritorIfc.escrever(m, "Obra Teste", "Eng. Responsável", Instant.parse("2026-10-25T00:00:00Z"));
        // cópia para validação externa do esquema (ifcopenshell.validate), fora do repositório
        java.nio.file.Files.writeString(java.nio.file.Path.of("target", "ifc-objetos.ifc"), ifc);
        var resumo = LeitorIfc.ler(ifc.getBytes(StandardCharsets.UTF_8));
        assertThat(resumo.valido()).as(resumo.problemas().toString()).isTrue();
        assertThat(resumo.porTipo()).containsEntry("IFCOUTLET", 2).containsEntry("IFCELECTRICDISTRIBUTIONBOARD", 1).containsEntry("IFCELECTRICAPPLIANCE", 1).containsEntry("IFCFURNITURE", 1)
                .containsEntry("IFCDISTRIBUTIONPORT", 7).containsEntry("IFCRELNESTS", 4).containsEntry("IFCRELCONNECTSPORTS", 3)
                .containsEntry("IFCDISTRIBUTIONCIRCUIT", 2).containsEntry("IFCSPACE", 1);
        assertThat(ifc).contains("Pset_TabelaA").contains("'COZINHA.GELADEIRA'").contains(".SOURCE.").contains(".SINK.").contains("'Circuito C3'").contains("'S1/1'").contains("'S1/2'")
                .doesNotContain("PESSOAS.ADULTO").contains("IFCPOWERMEASURE(");
    }

    @Test
    void textoForaDoAsciiUsaCodificacaoStep() {
        assertThat(EscritorIfc.s("Térreo")).isEqualTo("'T\\X2\\00E9\\X0\\rreo'");
        assertThat(EscritorIfc.s("d'água \\ ok")).isEqualTo("'d''\\X2\\00E1\\X0\\gua \\\\ ok'");
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
