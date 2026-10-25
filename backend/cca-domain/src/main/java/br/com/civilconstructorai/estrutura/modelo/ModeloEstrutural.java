package br.com.civilconstructorai.estrutura.modelo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Recorte do modelo BIM que a análise, o dimensionamento, as fundações e os quantitativos consomem.
 * É independente das tabelas do módulo {@code modelo}: a aplicação obtém este recorte por uma porta
 * ({@code ModeloEstruturalFonte}). Unidades em metros; elevações em metros; eixo Z para cima.
 */
public record ModeloEstrutural(UUID obraId, long versao, List<PavimentoRef> pavimentos, List<ElementoEstrutural> elementos) {

    public static ModeloEstrutural vazio(UUID obraId) {
        return new ModeloEstrutural(obraId, 0, List.of(new PavimentoRef("pav_terreo", "Térreo", 0.0)), List.of());
    }

    public List<PavimentoRef> pavimentosOrdenados() {
        return pavimentos.stream().sorted((a, b) -> Double.compare(a.elevacao(), b.elevacao())).toList();
    }

    public Optional<PavimentoRef> pavimento(String id) {
        return pavimentos.stream().filter(p -> p.id().equals(id)).findFirst();
    }

    public Optional<PavimentoRef> pavimentoAcima(String id) {
        var ord = pavimentosOrdenados();
        for (int i = 0; i < ord.size() - 1; i++) {
            if (ord.get(i).id().equals(id)) {
                return Optional.of(ord.get(i + 1));
            }
        }
        return Optional.empty();
    }

    public List<ElementoEstrutural> doPavimento(String pavimentoId) {
        return elementos.stream().filter(e -> pavimentoId.equals(e.pavimentoId())).toList();
    }

    public List<ElementoEstrutural> doTipo(TipoElementoEstrutural tipo) {
        return elementos.stream().filter(e -> e.tipo() == tipo).toList();
    }

    public Optional<ElementoEstrutural> elemento(String id) {
        return elementos.stream().filter(e -> e.id().equals(id)).findFirst();
    }

    /** altura do pilar: diferença de elevação entre nível de topo e base (3,0 m quando só há um pavimento) */
    public double alturaPilar(ElementoEstrutural pilar) {
        var base = pavimento(pilar.nivelBaseId() == null ? pilar.pavimentoId() : pilar.nivelBaseId());
        var topo = pilar.nivelTopoId() == null ? Optional.<PavimentoRef>empty() : pavimento(pilar.nivelTopoId());
        if (base.isPresent() && topo.isPresent() && !base.get().id().equals(topo.get().id())) {
            return topo.get().elevacao() - base.get().elevacao();
        }
        return base.flatMap(b -> pavimentoAcima(b.id())).map(t -> t.elevacao() - base.get().elevacao()).orElse(3.0);
    }
}
