package br.com.civilconstructorai.quantitativo;

import br.com.civilconstructorai.acabamento.MedidorAcabamentos;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipInputStream;
import javax.xml.parsers.DocumentBuilderFactory;
import static org.assertj.core.api.Assertions.*;

class AcabamentosExportTest {
    private QuantitativoService servico() {
        var a=new MedidorAcabamentos.Linha("t","w","r","MAT_REBOCO","LADO_A",.02,14,1.68,12.32,.2464,4.928,13.552,23.0,"Fachada; \"reboco\"",.6,.6,.002,10);
        var v=new QuantitativoService.Visao(UUID.randomUUID(),1,"hash","CONCLUIDO",false,List.of(),Map.of(),new Quantificador.Totais(0,0,0,0),List.of(a));
        return new QuantitativoService(null,null,null,null,null,null) { @Override public Visao atual(UUID obraId) {return v;} };
    }
    @Test void csvExportaAreasPesosDimensoesESpecComEscape() {
        var csv=new String(servico().exportarCsv(UUID.randomUUID()),StandardCharsets.UTF_8);
        assertThat(csv).contains("peso_instalado_kN","largura_peca_m","4.928","12.320000",";0.6;0.6;");
        assertThat(csv).contains("\"Fachada; \"\"reboco\"\"\"");
    }
    @Test void xlsxTemXmlValidoEIncluiComposicao() throws Exception {
        boolean achou=false;
        try(var zip=new ZipInputStream(new ByteArrayInputStream(servico().exportarXlsx(UUID.randomUUID())))) {
            for(var e=zip.getNextEntry();e!=null;e=zip.getNextEntry()) if(e.getName().equals("xl/worksheets/sheet1.xml")) {
                var dados=zip.readAllBytes();
                DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(dados));
                assertThat(new String(dados,StandardCharsets.UTF_8)).contains("Peso instalado (kN)","4.928","Largura peça (m)","MAT_REBOCO");achou=true;
            }
        }
        assertThat(achou).isTrue();
    }
}
