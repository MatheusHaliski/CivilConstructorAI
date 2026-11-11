package br.com.civilconstructorai.acabamento;

import br.com.civilconstructorai.estrutura.modelo.*;
import br.com.civilconstructorai.estrutura.mef.*;
import br.com.civilconstructorai.estrutura.carga.*;
import br.com.civilconstructorai.quantitativo.Quantificador;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class AcabamentosTest {
    private Acabamento camada(String face,double perda) {return new Acabamento("reboco","MAT_REBOCO",face,.02,null,null,0,perda,null,20.0,null);}
    private ModeloEstrutural modelo(ElementoEstrutural... elementos) {return new ModeloEstrutural(UUID.randomUUID(),1,List.of(new PavimentoRef("t","Térreo",0)),List.of(elementos));}
    private ElementoEstrutural parede() {return ElementoEstrutural.parede("w","t",new Ponto(0,0),new Ponto(5,0),.14,2.8,"MAT_ALV_BLOCO14").comAcabamentos(List.of(camada("LADO_A",10)),false,null);}
    @Test void descontaUniaoDosVaosEPesoNaoIncluiPerdas() {
        var p=parede(); var a=ElementoEstrutural.abertura("a",TipoElementoEstrutural.PORTA,"t","w",.8,2.1).comPosicaoAbertura(.5,0);
        var b=ElementoEstrutural.abertura("b",TipoElementoEstrutural.JANELA,"t","w",.8,2.1).comPosicaoAbertura(.5,0);
        var r=MedidorAcabamentos.medir(modelo(p,a,b)).getFirst();
        assertThat(r.areaLiquidaM2()).isCloseTo(12.32,within(1e-9));
        assertThat(r.volumeM3()).isCloseTo(.2464,within(1e-9));
        assertThat(r.pesoInstaladoKN()).isCloseTo(4.928,within(1e-9));
        assertThat(r.quantidadeCompraM2()).isCloseTo(13.552,within(1e-9));
    }
    @Test void vaoForaDaParedeNaoProduzMedicaoEnganosa() {
        var a=ElementoEstrutural.abertura("a",TipoElementoEstrutural.PORTA,"t","w",.8,2.1).comPosicaoAbertura(0,0);
        assertThatThrownBy(()->MedidorAcabamentos.medir(modelo(parede(),a))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void rascunhoNaoEntraEHashIncluiMaterialEPeso() {
        assertThat(MedidorAcabamentos.medir(modelo(parede().comoRascunhoIa()))).isEmpty();
        var p=parede(); var b=p.comAcabamentos(List.of(new Acabamento("reboco","MAT_ARGAMASSA","LADO_A",.02,null,null,0,10,null,20.0,null)),false,null);
        assertThat(Quantificador.quantificar(modelo(p),Map.of()).hash()).isNotEqualTo(Quantificador.quantificar(modelo(b),Map.of()).hash());
    }
    @Test void lajeSomaAcabamentoUmaVez() {
        var l=ElementoEstrutural.laje("l","t",List.of(new Ponto(0,0),new Ponto(5,0),new Ponto(5,4),new Ponto(0,4)),.12,"MAT_CONC_FCK30").comAcabamentos(List.of(camada("SUPERIOR",50)),true,null);
        var p=ElementoEstrutural.pilar("p","t",new Ponto(0,0),.2,.4,"t","t","MAT_CONC_FCK30");
        var ma=MontadorModeloAnalitico.montar(new MontadorModeloAnalitico.Entrada(modelo(l,p),List.of(),null,Map.of()));
        assertThat(ma.caso(NaturezaAcao.PERMANENTE).nodais().stream().mapToDouble(x->x.fz()).sum()).isCloseTo(-68,within(1e-9));
    }
    @Test void paredeTransfereNucleoECamadasESuporteParcialBloqueia() {
        var w=parede().comAcabamentos(List.of(camada("LADO_A",0)),true,"v");
        var v=ElementoEstrutural.viga("v","t",new Ponto(0,0),new Ponto(5,0),.2,.4,"MAT_CONC_FCK30");
        var ma=MontadorModeloAnalitico.montar(new MontadorModeloAnalitico.Entrada(modelo(w,v),List.of(),null,Map.of()));
        assertThat(ma.caso(NaturezaAcao.PERMANENTE).distribuidas()).hasSize(2);
        var curta=ElementoEstrutural.viga("v","t",new Ponto(0,0),new Ponto(4,0),.2,.4,"MAT_CONC_FCK30");
        assertThatThrownBy(()->MontadorModeloAnalitico.montar(new MontadorModeloAnalitico.Entrada(modelo(w,curta),List.of(),null,Map.of()))).hasMessageContaining("coincidir");
    }
    @Test void pesoFaltanteEConflitoManualBloqueiamAnalise() {
        var w=parede().comAcabamentos(List.of(new Acabamento("a","MAT_PINTURA","LADO_A",.001,null,null,0,0,null,null,null)),true,"v");
        assertThatThrownBy(()->MontadorModeloAnalitico.montar(new MontadorModeloAnalitico.Entrada(modelo(w),List.of(),null,Map.of()))).hasMessageContaining("peso específico");
        var v=ElementoEstrutural.viga("v","t",new Ponto(0,0),new Ponto(5,0),.2,.4,"MAT_CONC_FCK30");
        var c=new Carga(UUID.randomUUID(),UUID.randomUUID(),"t",NaturezaAcao.PERMANENTE,TipoCarga.LINEAR,3,"v","manual","G");
        assertThatThrownBy(()->MontadorModeloAnalitico.montar(new MontadorModeloAnalitico.Entrada(modelo(parede().comAcabamentos(List.of(camada("LADO_A",0)),true,"v"),v),List.of(c),null,Map.of()))).hasMessageContaining("Revise/remova");
    }
    @Test void divisoriaSecaExigeMassaDoNucleoMesmoComChapasPesadas() {
        var w=ElementoEstrutural.parede("w","t",new Ponto(0,0),new Ponto(5,0),.09,2.8,"MAT_DIVISORIA_SECA").comAcabamentos(List.of(camada("LADO_A",0)),true,"v");
        var v=ElementoEstrutural.viga("v","t",new Ponto(0,0),new Ponto(5,0),.2,.4,"MAT_CONC_FCK30");
        assertThatThrownBy(()->MontadorModeloAnalitico.montar(new MontadorModeloAnalitico.Entrada(modelo(w,v),List.of(),null,Map.of()))).hasMessageContaining("massaNucleo_kg_m2");
        assertThatCode(()->MontadorModeloAnalitico.montar(new MontadorModeloAnalitico.Entrada(modelo(w.comMassaNucleo(4.0),v),List.of(),null,Map.of()))).doesNotThrowAnyException();
    }
    @Test void limitesDeCamadaEAplicacaoSaoIguaisAoCatalogoWeb() {
        assertThatThrownBy(()->new Acabamento("a","MAT_REBOCO","LADO_A",.501,null,null,0,0,null,null,null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new Acabamento("a","MAT_REBOCO","LADO_A",.02,null,null,.101,0,null,null,null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new Acabamento("a","MAT_AZULEJO","SUPERIOR",.01,null,null,0,0,null,null,null).validarFace(false)).hasMessageContaining("exclusivo de parede");
        assertThatThrownBy(()->parede().comMassaNucleo(0.0)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void alvenariaSemCamadasTambemDescontaUniaoDosVaos() {
        var w=parede().comAcabamentos(List.of(),false,null);
        var a=ElementoEstrutural.abertura("a",TipoElementoEstrutural.PORTA,"t","w",.8,2.1);
        var b=ElementoEstrutural.abertura("b",TipoElementoEstrutural.PORTA,"t","w",.8,2.1);
        assertThat(Quantificador.quantificar(modelo(w,a,b),Map.of()).totalGeral().alvenariaM2()).isEqualTo(12.32);
    }
    @Test void validaNumerosFacesEIdSemAtribuirResistencia() {
        assertThatThrownBy(()->new Acabamento("a","MAT_REBOCO","LADO_A",Double.NaN,null,null,0,0,null,null,null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->camada("SUPERIOR",0).validarFace(true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->Acabamento.ler(List.of(Map.of("id","a","materialId","MAT_CONC_FCK30","face","LADO_A","espessura_m",.02)),true)).isInstanceOf(IllegalArgumentException.class);
    }
}
