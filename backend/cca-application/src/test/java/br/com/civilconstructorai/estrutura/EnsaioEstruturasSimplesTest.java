package br.com.civilconstructorai.estrutura;

import br.com.civilconstructorai.estrutura.carga.*;
import br.com.civilconstructorai.estrutura.mef.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Casos controlados: a comparação independente em Python não reutiliza o solver Java. */
class EnsaioEstruturasSimplesTest {
    public record Barra(String id, int i, int j, double b, double h) { }
    public record Apoio(int no, boolean ux, boolean uz, boolean ry) { }
    public record Distribuida(int barra, double g, double q) { }
    public record Nodal(int no, double gx, double gz, double qx, double qz) { }
    public record Caso(String id, String titulo, double e_kN_m2, List<List<Double>> nos, List<Barra> barras,
                       List<Apoio> apoios, List<Distribuida> distribuidas, List<Nodal> nodais) { }
    public record Comb(String nome, String estado, double g, double q) { }
    public record Integrado(double largura, double profundidade, double altura, double bViga, double hViga,
                            double bPilar, double hPilar, double hLaje, double revestimento, double sobrecarga, double e) { }
    public record Entrada(int schema, Map<String,String> unidades, List<String> hipoteses,
                          List<Comb> combinacoes, List<Caso> casos, Integrado desenhoIntegrado) { }

    @Test void ensaiarEExportarResultadosReproduziveis() throws Exception {
        var mapper = new ObjectMapper();
        var raiz = Path.of("../..").toAbsolutePath().normalize();
        var arquivo = raiz.resolve("docs/validacao/casos-estruturais.json");
        var in = mapper.readValue(Files.readString(arquivo), Entrada.class);
        var out = new LinkedHashMap<String, SolverMef.ResultadoAnalise>();
        for (var c : in.casos()) {
            var m = new ModeloAnalitico();
            for (var n : c.nos()) m.adicionarNo(n.get(0), 0, n.get(1));
            for (var b : c.barras()) m.adicionarBarra(b.id(), b.id(), b.i(), b.j(),
                    ModeloAnalitico.Secao.retangular(b.b(), b.h(), c.e_kN_m2()));
            // Restringe GDL fora do plano XZ, com os vínculos de plano explicitados no arquivo.
            for (int n = 0; n < c.nos().size(); n++) {
                final int no = n;
                var a = c.apoios().stream().filter(x -> x.no() == no).findFirst().orElse(new Apoio(n,false,false,false));
                m.adicionarApoio(new ModeloAnalitico.Apoio(n,a.ux(),true,a.uz(),true,a.ry(),true));
            }
            for (var d : c.distribuidas()) {
                m.caso(NaturezaAcao.PERMANENTE).distribuidas().add(new ModeloAnalitico.CargaDistribuida(d.barra(),0,0,-d.g()));
                m.caso(NaturezaAcao.ACIDENTAL).distribuidas().add(new ModeloAnalitico.CargaDistribuida(d.barra(),0,0,-d.q()));
            }
            for (var n : c.nodais()) {
                m.caso(NaturezaAcao.PERMANENTE).nodais().add(new ModeloAnalitico.CargaNodal(n.no(),n.gx(),0,n.gz(),0,0,0));
                m.caso(NaturezaAcao.ACIDENTAL).nodais().add(new ModeloAnalitico.CargaNodal(n.no(),n.qx(),0,n.qz(),0,0,0));
            }
            var combinacoes = in.combinacoes().stream().map(x -> new Combinacao(x.nome(),
                    Combinacao.EstadoLimite.valueOf(x.estado()),Map.of(NaturezaAcao.PERMANENTE,x.g(),NaturezaAcao.ACIDENTAL,x.q()))).toList();
            var resultado = new SolverMef(m).resolver(combinacoes);
            assertThat(resultado.porCombinacao()).hasSize(4);
            var elu = resultado.porCombinacao().get("ELU_TESTE");
            double fx = 0, fz = 0;
            for (var n : c.nodais()) { fx += 1.4*(n.gx()+n.qx()); fz += 1.4*(n.gz()+n.qz()); }
            for (var d : c.distribuidas()) fz -= 1.4*(d.g()+d.q())*m.comprimento(m.barras().get(d.barra()));
            assertThat(elu.reacoes().stream().mapToDouble(SolverMef.Reacao::fx).sum()+fx).isCloseTo(0,within(.001));
            assertThat(elu.reacoes().stream().mapToDouble(SolverMef.Reacao::fz).sum()+fz).isCloseTo(0,within(.001));
            if (c.id().equals("B1")) assertThat(elu.barras().getFirst().mMax()).isCloseTo(63,within(.0001));
            if (c.id().equals("B2")) assertThat(elu.barras().getFirst().mMin()).isCloseTo(-63,within(.0001));
            if (c.id().equals("C1")) assertThat(elu.barras().getFirst().nMax()).isCloseTo(700,within(.0001));
            if (c.id().equals("L1")) assertThat(elu.barras().getFirst().mMax()).isCloseTo(11.025,within(.0001));
            if (c.id().equals("P1")) {
                var viga = elu.barras().get(1);
                var inicio = viga.pontos().getFirst();
                double q = 28;
                double sCritico = inicio.vz()/q;
                assertThat(sCritico).isBetween(0.0,5.0);
                double mCritico = inicio.my()+inicio.vz()*sCritico-q*sCritico*sCritico/2;
                assertThat(viga.mMax()).isCloseTo(mCritico,within(.0002));
            }
            out.put(c.id(), resultado);
        }
        var integrado = montarDesenho(in.desenhoIntegrado());
        var combinacoes = in.combinacoes().stream().map(x -> new Combinacao(x.nome(),
                Combinacao.EstadoLimite.valueOf(x.estado()),Map.of(NaturezaAcao.PERMANENTE,x.g(),NaturezaAcao.ACIDENTAL,x.q()))).toList();
        var d1 = new SolverMef(integrado).resolver(combinacoes);
        assertThat(d1.barras()).isEqualTo(8);
        var eluD1 = d1.porCombinacao().get("ELU_TESTE");
        assertThat(eluD1.reacoes().stream().mapToDouble(SolverMef.Reacao::fz).sum()).isCloseTo(331.45,within(1e-8));
        assertThat(eluD1.reacoes()).allSatisfy(r -> assertThat(r.fz()).isCloseTo(82.8625,within(1e-8)));
        out.put("D1", d1);
        if (System.getProperty("cca.validacao.saida") != null) {
            var saida = Path.of(System.getProperty("cca.validacao.saida"));
            Files.createDirectories(saida.toAbsolutePath().getParent());
            Files.writeString(saida,mapper.writerWithDefaultPrettyPrinter().writeValueAsString(out)+"\n");
        }
    }

    private ModeloAnalitico montarDesenho(Integrado d) {
        var obra = UUID.randomUUID();
        var t = new br.com.civilconstructorai.estrutura.modelo.PavimentoRef("t", "Térreo", 0);
        var c = new br.com.civilconstructorai.estrutura.modelo.PavimentoRef("c", "Cobertura", d.altura());
        var pontos = List.of(new br.com.civilconstructorai.estrutura.modelo.Ponto(0,0),
                new br.com.civilconstructorai.estrutura.modelo.Ponto(d.largura(),0),
                new br.com.civilconstructorai.estrutura.modelo.Ponto(d.largura(),d.profundidade()),
                new br.com.civilconstructorai.estrutura.modelo.Ponto(0,d.profundidade()));
        var elementos = new ArrayList<br.com.civilconstructorai.estrutura.modelo.ElementoEstrutural>();
        for (int i=0;i<4;i++) {
            elementos.add(br.com.civilconstructorai.estrutura.modelo.ElementoEstrutural.pilar("P"+(i+1),"t",pontos.get(i),d.bPilar(),d.hPilar(),"t","c","MAT_CONC_FCK30"));
            elementos.add(br.com.civilconstructorai.estrutura.modelo.ElementoEstrutural.viga("V"+(i+1),"c",pontos.get(i),pontos.get((i+1)%4),d.bViga(),d.hViga(),"MAT_CONC_FCK30"));
        }
        elementos.add(br.com.civilconstructorai.estrutura.modelo.ElementoEstrutural.laje("L1","c",pontos,d.hLaje(),"MAT_CONC_FCK30"));
        var modelo = new br.com.civilconstructorai.estrutura.modelo.ModeloEstrutural(obra,1,List.of(t,c),elementos);
        var cargas = List.of(new Carga(obra,obra,"c",NaturezaAcao.PERMANENTE,TipoCarga.AREA,d.revestimento(),"L1","Revestimento de teste","G"),
                new Carga(obra,obra,"c",NaturezaAcao.ACIDENTAL,TipoCarga.AREA,d.sobrecarga(),"L1","Sobrecarga de teste","Q"));
        return MontadorModeloAnalitico.montar(new MontadorModeloAnalitico.Entrada(modelo,cargas,null,Map.of("MAT_CONC_FCK30",d.e())));
    }
}
