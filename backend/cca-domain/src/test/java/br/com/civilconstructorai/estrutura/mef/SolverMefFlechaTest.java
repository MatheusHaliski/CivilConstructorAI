package br.com.civilconstructorai.estrutura.mef;

import br.com.civilconstructorai.estrutura.carga.Combinacao;
import br.com.civilconstructorai.estrutura.carga.NaturezaAcao;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SolverMefFlechaTest {
    private static final Combinacao G = new Combinacao("G", Combinacao.EstadoLimite.ELS_RARA,
            Map.of(NaturezaAcao.PERMANENTE, 1.0));

    @Test
    void porticoP1IncluiMaximoDeFlechaEntreAsEstacoesDeEsforco() {
        var m = new ModeloAnalitico();
        int a = m.adicionarNo(0,0,0), b = m.adicionarNo(0,0,3);
        int c = m.adicionarNo(5,0,3), d = m.adicionarNo(5,0,0);
        var pilar = ModeloAnalitico.Secao.retangular(.25,.25,30e6);
        m.adicionarBarra("P1","P1",a,b,pilar);
        int v = m.adicionarBarra("V1","V1",b,c,ModeloAnalitico.Secao.retangular(.2,.5,30e6));
        m.adicionarBarra("P2","P2",d,c,pilar);
        m.adicionarApoio(ModeloAnalitico.Apoio.engaste(a));
        m.adicionarApoio(ModeloAnalitico.Apoio.engaste(d));
        m.caso(NaturezaAcao.PERMANENTE).distribuidas().add(new ModeloAnalitico.CargaDistribuida(v,0,0,-28));
        m.caso(NaturezaAcao.PERMANENTE).nodais().add(new ModeloAnalitico.CargaNodal(b,14,0,0,0,0,0));

        var rb = new SolverMef(m).resolver(List.of(G)).porCombinacao().get("G").barras().get(1);
        // Referência independente: pórtico plano 3 GDL/nó + máximo da solução EB contínua.
        assertThat(rb.flechaMax()).isCloseTo(.0027639162577197326,within(1e-13));
        var extremo = rb.pontos().stream().max(java.util.Comparator.comparingDouble(SolverMef.Esforco::flecha)).orElseThrow();
        assertThat(extremo.s()).isCloseTo(2.4404787,within(2e-7));
    }

    @Test
    void maximoDaResultanteEspacialNaoCoincideComExtremosDasComponentes() {
        double l = 6, e = 30e6;
        var m = new ModeloAnalitico();
        int a = m.adicionarNo(0,0,0), b = m.adicionarNo(l,0,0);
        var sec = ModeloAnalitico.Secao.retangular(.2,.5,e);
        int v = m.adicionarBarra("V1","V1",a,b,sec);
        m.adicionarApoio(ModeloAnalitico.Apoio.rotula(a));
        m.adicionarApoio(new ModeloAnalitico.Apoio(b,false,true,true,true,false,false));
        m.caso(NaturezaAcao.PERMANENTE).distribuidas().add(new ModeloAnalitico.CargaDistribuida(v,0,-1.6,-10));
        m.caso(NaturezaAcao.PERMANENTE).nodais().add(new ModeloAnalitico.CargaNodal(a,0,0,0,0,0,5));
        m.caso(NaturezaAcao.PERMANENTE).nodais().add(new ModeloAnalitico.CargaNodal(b,0,0,0,0,-20,0));

        var rb = new SolverMef(m).resolver(List.of(G)).porCombinacao().get("G").barras().getFirst();
        // Integração independente EI v'' = M, v(0)=v(L)=0, em t=s/L:
        // vy=-.00264t-.009t²+.02028t³-.00864t⁴;
        // vz=-.01056t+.0192t³-.00864t⁴.
        // A raiz de vy*vy'+vz*vz' foi calculada em precisão decimal de 50 dígitos.
        assertThat(rb.flechaMax()).isCloseTo(.00377306458407264896,within(1e-13));
        var extremo = rb.pontos().stream().max(java.util.Comparator.comparingDouble(SolverMef.Esforco::flecha)).orElseThrow();
        assertThat(extremo.s()).isCloseTo(3.1250639623669463,within(1e-10));
        assertThat(Math.abs(extremo.s()-3.2609295642183884)).isGreaterThan(.1); // extremo de vy
        assertThat(Math.abs(extremo.s()-3.0906824968341002)).isGreaterThan(.03); // extremo de vz
        for (var p : rb.pontos()) {
            double t = p.s()/l;
            double vy = -.00264*t-.009*t*t+.02028*t*t*t-.00864*Math.pow(t,4);
            double vz = -.01056*t+.0192*t*t*t-.00864*Math.pow(t,4);
            assertThat(p.flecha()).isCloseTo(Math.hypot(vy,vz),within(1e-13));
        }
    }

    @Test
    void solucoesFechadasBiapoiadaEBalancoMantemMaximosElasticoNosDoisPlanos() {
        for (boolean balanco : new boolean[]{false,true}) {
            double l = 6, qy = -1.6, qz = -10, e = 30e6;
            var m = new ModeloAnalitico();
            int a = m.adicionarNo(0,0,0), b = m.adicionarNo(l,0,0);
            var sec = ModeloAnalitico.Secao.retangular(.2,.5,e);
            int v = m.adicionarBarra("V1","V1",a,b,sec);
            m.adicionarApoio(balanco ? ModeloAnalitico.Apoio.engaste(a) : ModeloAnalitico.Apoio.rotula(a));
            if (!balanco) m.adicionarApoio(new ModeloAnalitico.Apoio(b,false,true,true,true,false,false));
            m.caso(NaturezaAcao.PERMANENTE).distribuidas().add(new ModeloAnalitico.CargaDistribuida(v,0,qy,qz));
            var rb = new SolverMef(m).resolver(List.of(G)).porCombinacao().get("G").barras().getFirst();
            double fator = balanco ? 1.0/8 : 5.0/384;
            double esperado = fator*Math.pow(l,4)/e*Math.hypot(qy/sec.iz(),qz/sec.iy());
            assertThat(rb.flechaMax()).isCloseTo(esperado,within(1e-13));
        }
    }
}
