package br.com.civilconstructorai.modelo;

import br.com.civilconstructorai.comum.Ids;
import br.com.civilconstructorai.modelo.Enums.OrigemComando;
import br.com.civilconstructorai.notacao.Ponto2D;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Paridade com o motor TS: roda {@code packages/notacao-vi/fixtures/comandos-paridade.json}, o mesmo arquivo do
 * {@code paridade.test.ts}. A prévia no navegador e a gravação no servidor precisam dar o mesmo resultado (RF01/RF28).
 */
class ModeloBimParidadeTest {

    private static final Path FIXTURE = Path.of("..", "..", "packages", "notacao-vi", "fixtures", "comandos-paridade.json");

    @SuppressWarnings("unchecked")
    @TestFactory
    Stream<DynamicTest> mesmosResultadosQueOMotorTs() throws Exception {
        var raiz = new ObjectMapper().readValue(Files.readString(FIXTURE), Map.class);
        var setup = (List<Map<String, Object>>) raiz.get("setup");
        var casos = (List<Map<String, Object>>) raiz.get("casos");
        return casos.stream().map(c -> DynamicTest.dynamicTest((String) c.get("codigo"), () -> {
            var terreo = ModeloBim.terreoInicial(UUID.randomUUID());
            var m = new ModeloBim(new ModeloBim.Estado(0, new ArrayList<>(List.of(terreo)), new ArrayList<>(), new ArrayList<>()));
            var ids = new ArrayList<String>();
            for (var s : setup) {
                ids.add(executar(m, terreo.id(), (String) s.get("codigo"), (Map<String, Object>) s.get("args")).criados.getFirst().getId().toString());
            }
            for (var s : (List<Map<String, Object>>) c.getOrDefault("preparo", List.of())) {
                ids.add(executar(m, terreo.id(), (String) s.get("codigo"), (Map<String, Object>) trocarRefs(s.get("args"), ids)).criados.getFirst().getId().toString());
            }
            var d = executar(m, terreo.id(), (String) c.get("codigo"), (Map<String, Object>) trocarRefs(c.get("args"), ids));
            var e = (Map<String, Object>) c.get("espera");
            if (e.containsKey("criados")) {
                assertThat(d.criados).as("criados").hasSize(((Number) e.get("criados")).intValue());
            }
            if (e.containsKey("alterados")) {
                assertThat(d.alterados).as("alterados").hasSize(((Number) e.get("alterados")).intValue());
            }
            if (e.containsKey("removidos")) {
                assertThat(d.removidos).as("removidos").hasSize(((Number) e.get("removidos")).intValue());
            }
            if (e.containsKey("eixos")) {
                assertThat(d.eixosCriados).as("eixos").hasSize(((Number) e.get("eixos")).intValue());
            }
            if (e.containsKey("tipo")) {
                assertThat(d.criados.getFirst().getTipo().name()).isEqualTo(e.get("tipo"));
            }
            if (e.containsKey("materialId")) {
                var alvo = d.criados.isEmpty() ? d.alterados.getFirst() : d.criados.getFirst();
                assertThat(alvo.getMaterialId()).isEqualTo(e.get("materialId"));
            }
            if (e.containsKey("igual")) {
                var alvo = d.criados.isEmpty() ? d.alterados.getFirst() : d.criados.getFirst();
                for (var par : ((Map<String, Object>) e.get("igual")).entrySet()) {
                    assertThat(texto(alvo, par.getKey())).as(par.getKey()).isEqualTo(String.valueOf(trocarRefs(par.getValue(), ids)));
                }
            }
            for (var v : (List<Map<String, Object>>) e.getOrDefault("verifica", List.of())) {
                var alvo = m.elemento(UUID.fromString(String.valueOf(trocarRefs(v.get("ref"), ids)))).orElseThrow();
                assertThat(campo(alvo.getProps(), (String) v.get("campo"))).as(v.get("ref") + "." + v.get("campo")).isCloseTo(((Number) v.get("valor")).doubleValue(), within(1e-6));
            }
            if (e.containsKey("campo")) {
                var alvo = d.criados.isEmpty() ? d.alterados.getFirst() : d.criados.getFirst();
                assertThat(campo(alvo.getProps(), (String) e.get("campo"))).as((String) e.get("campo")).isCloseTo(((Number) e.get("valor")).doubleValue(), within(1e-6));
            }
        }));
    }

    @SuppressWarnings("unchecked")
    private static DiffModelo executar(ModeloBim m, UUID pav, String codigo, Map<String, Object> args) {
        return m.executar(new ComandoVi(Ids.novo(), codigo, ModeloBim.resolverCoordenadas(new LinkedHashMap<>(args)), OrigemComando.CANVAS, m.versao(), UUID.randomUUID(), Instant.now()), pav);
    }

    @SuppressWarnings("unchecked")
    private static Object trocarRefs(Object v, List<String> ids) {
        if (v instanceof String s && s.matches("^\\$\\d+$")) {
            return ids.get(Integer.parseInt(s.substring(1)));
        }
        if (v instanceof List<?> l) {
            return l.stream().map(x -> trocarRefs(x, ids)).toList();
        }
        if (v instanceof Map<?, ?> mapa) {
            var out = new LinkedHashMap<String, Object>();
            mapa.forEach((k, x) -> out.put((String) k, trocarRefs(x, ids)));
            return out;
        }
        return v;
    }

    /** campos de texto do elemento (camada e paredeId ficam fora de props no Java) */
    @SuppressWarnings("unchecked")
    private static String texto(Elemento e, String caminho) {
        if (caminho.equals("camada")) {
            return e.getCamada().rotulo();
        }
        Object o = e.getProps();
        for (var k : caminho.split("\\.")) {
            o = ((Map<String, Object>) o).get(k);
        }
        return String.valueOf(o);
    }

    @SuppressWarnings("unchecked")
    private static double campo(Map<String, Object> props, String caminho) {
        Object o = props;
        for (var k : caminho.split("\\.")) {
            o = o instanceof Ponto2D p ? (k.equals("x") ? p.x() : p.y()) : ((Map<String, Object>) o).get(k);
        }
        return ((Number) o).doubleValue();
    }
}
