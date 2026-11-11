package br.com.civilconstructorai.cimento;
import br.com.civilconstructorai.comum.ExcecaoDominio;
import br.com.civilconstructorai.modelo.Elemento;
import br.com.civilconstructorai.modelo.Enums;
import br.com.civilconstructorai.modelo.Pavimento;
import br.com.civilconstructorai.notacao.Ponto2D;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ValidadorDosagemCimentoTest {
    final DosagemCimentoRepository repo=mock(DosagemCimentoRepository.class);
    final ObjectMapper mapper=new ObjectMapper();
    final ValidadorDosagemCimento v=new ValidadorDosagemCimento(repo,mapper,()->Instant.parse("2026-11-11T00:00:00Z"));
    final UUID obra=UUID.randomUUID();
    DosagemCimento dose() {
        var c=new DosagemCimentoService.Composicao(.45,"Agregado/lote documentado",350,"Cura conforme relatório");
        var r=new DosagemCimentoService.Registro("CIM_CP_II_F_32","Fabricante/produto/lote",30,34,"Relatório123","Memorial1",LocalDate.of(2026,10,1),LocalDate.of(2027,1,1),"Estatística auditada no relatório",7,c);
        return new DosagemCimento(UUID.randomUUID(),obra,7,mapper.writeValueAsString(r),UUID.randomUUID(),"hash");
    }
    @Test void rejeitaRascunhoEFckForjadoEAceitaSomenteRegistroConfirmadoDoBanco() {
        var d=dose(); when(repo.findByIdAndObraId(d.getId(),obra)).thenReturn(Optional.of(d));
        assertThatThrownBy(()->v.validar(obra,d.getId(),"CIM_CP_II_F_32",30,7,"hash")).isInstanceOf(ExcecaoDominio.RegraDeNegocio.class);
        d.confirmar(UUID.randomUUID(),Instant.now(),"ART/relatório");
        assertThat(v.validar(obra,d.getId(),"CIM_CP_II_F_32",30,8,"hash")).isSameAs(d);
        assertThatThrownBy(()->v.validar(obra,d.getId(),"CIM_CP_II_F_32",25,8,"hash")).isInstanceOf(ExcecaoDominio.RegraDeNegocio.class);
        assertThatThrownBy(()->v.validar(obra,d.getId(),"CIM_CP_V_ARI",30,8,"hash")).isInstanceOf(ExcecaoDominio.RegraDeNegocio.class);
        assertThatThrownBy(()->v.validar(obra,d.getId(),"CIM_CP_II_F_32",30,8,"geometria mudou")).isInstanceOf(ExcecaoDominio.RegraDeNegocio.class);
    }
    @Test void hashIgnoraSomenteMetadadosEIdentificaAlteracaoDeMaterialOuGeometria() {
        var e=Elemento.novo(Enums.TipoElemento.PILAR,UUID.randomUUID(),Enums.Camada.estrutura,Enums.OrigemComando.CANVAS,
            Map.of("materialId","MAT_CONC_FCK30","centro",new Ponto2D(0,0),"b",.2,"h",.4));
        var original=v.contextoHash(List.of(e));
        e.getProps().put("cimentoId","CIM_CP_II_F_32"); e.getProps().put("dosagemCimentoId",UUID.randomUUID().toString());
        assertThat(v.contextoHash(List.of(e))).isEqualTo(original);
        e.setNum("b",.3); assertThat(v.contextoHash(List.of(e))).isNotEqualTo(original);
    }
    @Test void hashCanonicoIndependeDaOrdemDeMapasEIncluiElevacaoDosPavimentos() {
        var pav=UUID.randomUUID(); var id=UUID.randomUUID();
        var p1=new LinkedHashMap<String,Object>(); p1.put("b",.2); p1.put("h",.4); p1.put("centro",Map.of("y",0,"x",0));
        var p2=new LinkedHashMap<String,Object>(); p2.put("centro",new TreeMap<>(Map.of("x",0,"y",0))); p2.put("h",.4); p2.put("b",.2);
        var e1=new Elemento(id,Enums.TipoElemento.PILAR,pav,Enums.Camada.estrutura,"MAT_CONC_FCK30",Enums.OrigemComando.CANVAS,null,null,p1);
        var e2=new Elemento(id,Enums.TipoElemento.PILAR,pav,Enums.Camada.estrutura,"MAT_CONC_FCK30",Enums.OrigemComando.CANVAS,null,null,p2);
        var nivel=new Pavimento(pav,"Térreo",0,Enums.TipoPavimento.TERREO);
        assertThat(v.contextoHash(List.of(e1),List.of(nivel),List.of())).isEqualTo(v.contextoHash(List.of(e2),List.of(nivel),List.of()));
        var outroNivel=new Pavimento(pav,"Térreo",1,Enums.TipoPavimento.TERREO);
        assertThat(v.contextoHash(List.of(e1),List.of(nivel),List.of())).isNotEqualTo(v.contextoHash(List.of(e1),List.of(outroNivel),List.of()));
    }
}
