package br.com.civilconstructorai.instalacoes;

import br.com.civilconstructorai.instalacoes.VerificadorRedes.Componente;
import br.com.civilconstructorai.instalacoes.VerificadorRedes.Rede;
import br.com.civilconstructorai.instalacoes.VerificadorRedes.Regime;
import br.com.civilconstructorai.instalacoes.VerificadorRedes.Sistema;
import br.com.civilconstructorai.instalacoes.VerificadorRedes.Trecho;
import br.com.civilconstructorai.modelo.Elemento;
import br.com.civilconstructorai.modelo.Enums.TipoElemento;
import br.com.civilconstructorai.modelo.ModeloBim;
import br.com.civilconstructorai.modelo.ObjetosPrediais;
import br.com.civilconstructorai.notacao.CatalogoObjetos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Monta a rede do {@link VerificadorRedes} a partir do modelo: um componente por objeto e sistema (o aquecedor é um
 * componente só, onde água fria e quente se encontram), com o tipo vindo do papel do conector na biblioteca; um trecho
 * por elemento CONEXAO, com comprimento e cotas do percurso esquemático. A rede reflete o que está ligado — um objeto
 * desenhado sem conexão não vira trecho.
 */
public final class RedeDoModelo {

    private RedeDoModelo() {
    }

    public static String idComponente(Elemento o, CatalogoObjetos.Item it, String sistema) {
        boolean aquecedor = it.conectores().stream().anyMatch(c -> "AQUECEDOR".equals(c.papel()));
        return aquecedor && ("AGUA_FRIA".equals(sistema) || "AGUA_QUENTE".equals(sistema)) ? o.getId().toString() : o.getId() + ":" + sistema;
    }

    public static Rede montar(ModeloBim m) {
        var componentes = new LinkedHashMap<String, Componente>();
        var disjuntores = new HashMap<String, Map<String, Object>>();
        var conexoes = m.elementos().stream().filter(e -> e.getTipo() == TipoElemento.CONEXAO && e.getRascunhoIaId() == null).toList();
        for (var c : conexoes) {
            var de = ObjetosPrediais.objeto(m, ObjetosPrediais.ponta(c, "de")).orElse(null);
            if (de != null && "ELETRICA".equals(c.texto("sistema")) && c.getProps().get("disjuntorA") instanceof Number in) {
                var it = ObjetosPrediais.item(de).orElse(null);
                if (it != null) {
                    disjuntores.computeIfAbsent(idComponente(de, it, "ELETRICA"), k -> new LinkedHashMap<>()).put(c.texto("circuito") == null ? "C1" : c.texto("circuito"), in.doubleValue());
                }
            }
        }
        for (var o : m.elementos()) {
            if (o.getTipo() != TipoElemento.OBJETO || o.getRascunhoIaId() != null) {
                continue;
            }
            var it = ObjetosPrediais.item(o).orElse(null);
            if (it == null) {
                continue;
            }
            var sistemas = new LinkedHashSet<String>();
            it.conectores().forEach(c -> sistemas.add(c.sistema()));
            for (var s : sistemas) {
                var con = it.conectores().stream().filter(c -> c.sistema().equals(s)).findFirst().orElseThrow();
                String id = idComponente(o, it, s);
                if (componentes.containsKey(id)) {
                    continue;
                }
                var p = ObjetosPrediais.posicaoConector(m, o, con.id()).orElseThrow();
                var props = new LinkedHashMap<String, Object>();
                props.put("objetoId", o.getId().toString());
                props.put("item", it.id());
                Double potencia = o.getProps().get("potenciaW") instanceof Number n ? n.doubleValue()
                        : it.conectores().stream().filter(c -> c.sistema().equals(s) && c.potenciaW() != null).map(CatalogoObjetos.Conector::potenciaW).findFirst().orElse(null);
                // "ligada" é só o efeito visual da luminária: a carga continua no circuito
                if ("ELETRICA".equals(s) && potencia != null) {
                    props.put("potenciaW", potencia);
                }
                if ("ELETRICA".equals(s) && con.tensoes().size() == 1) {
                    props.put("tensaoV", con.tensoes().getFirst());
                }
                if (disjuntores.containsKey(id)) {
                    props.put("disjuntores", disjuntores.get(id));
                }
                componentes.put(id, new Componente(id, con.papel(), Sistema.valueOf(s), p.x(), p.y(), p.z(), props));
            }
        }
        var trechos = new ArrayList<Trecho>();
        for (var c : conexoes) {
            var de = ObjetosPrediais.objeto(m, ObjetosPrediais.ponta(c, "de")).orElse(null);
            var para = ObjetosPrediais.objeto(m, ObjetosPrediais.ponta(c, "para")).orElse(null);
            String s = c.texto("sistema");
            Sistema sistema;
            try {
                sistema = Sistema.valueOf(s);
            } catch (IllegalArgumentException | NullPointerException e) {
                continue;
            }
            String origem = de == null ? "?" : ObjetosPrediais.item(de).map(it -> idComponente(de, it, s)).orElse("?");
            String destino = para == null ? "?" : ObjetosPrediais.item(para).map(it -> idComponente(para, it, s)).orElse("?");
            var pts = ObjetosPrediais.percurso(m, c);
            double comprimento = ObjetosPrediais.comprimento(pts), cotaO = Double.NaN, cotaD = Double.NaN;
            Regime regime = sistema == Sistema.ESGOTO ? Regime.GRAVIDADE : sistema == Sistema.ELETRICA ? Regime.CONDUTOR : Regime.PRESSURIZADO;
            if (sistema == Sistema.ESGOTO && pts.size() >= 4) {
                // trecho horizontal sob o piso: entre a primeira e a penúltima inflexão do percurso
                var a = pts.get(1);
                var b = pts.get(pts.size() - 2);
                comprimento = Math.abs(b.x() - a.x()) + Math.abs(b.y() - a.y());
                cotaO = a.z();
                cotaD = b.z();
            }
            Set<String> condutores = new LinkedHashSet<>();
            if (c.getProps().get("condutores") instanceof List<?> l) {
                l.forEach(x -> condutores.add(String.valueOf(x)));
            }
            trechos.add(new Trecho(c.getId().toString(), sistema, origem, destino, c.texto("materialId"), regime, comprimento, c.num("dnMm", 0), cotaO, cotaD,
                    sistema == Sistema.ELETRICA ? (c.texto("circuito") == null ? "C1" : c.texto("circuito")) : null, c.num("secaoMm2", Double.NaN), condutores));
        }
        return new Rede(List.copyOf(componentes.values()), trechos);
    }
}
