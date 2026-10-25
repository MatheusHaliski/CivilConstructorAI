package br.com.civilconstructorai.notacao;

import java.util.List;
import java.util.Optional;

/**
 * Visão legada ("Tabela A") sobre o {@link CatalogoMateriais}: os consumidores antigos (auditor, IFC) recebem um
 * {@link Material} montado a partir do catálogo. Os códigos legados (MAT_CONC_FCK30…) continuam aceitos e
 * resolvem para o código canônico (MAT.CONCRETO.C30…). Resistência ao fogo e transmitância térmica deixaram de ser
 * atributos do material: dependem do elemento (dimensões, cobrimento, camadas).
 */
public final class TabelaA {

    private TabelaA() {
    }

    public static List<Material> materiais() {
        return CatalogoMateriais.padrao().itens().stream().map(TabelaA::adaptar).toList();
    }

    public static Optional<Material> material(String id) {
        return CatalogoMateriais.padrao().item(id).map(TabelaA::adaptar);
    }

    /** código canônico do catálogo; um valor desconhecido volta como veio (o auditor o reprova) */
    public static String normalizarId(String id) {
        if (id == null) {
            return null;
        }
        return CatalogoMateriais.padrao().canonico(id).orElse(id.trim());
    }

    private static Material adaptar(CatalogoMateriais.Item i) {
        var cat = CatalogoMateriais.padrao();
        var props = cat.propriedadesCompletas(i, null);
        Double fck = num(props, "fck"), fyk = num(props, "fyk");
        Double peso = num(props, "pesoEspecifico");
        boolean portante = switch (i.familia()) {
            case "CONCRETO", "ACO_ARMADURA", "ACO_ESTRUTURAL" -> true;
            case "ALVENARIA" -> i.propriedade("funcao").map(p -> "estrutural".equals(p.valor())).orElse(false);
            default -> false;
        };
        String dominio = switch (i.familia()) {
            case "CONCRETO", "ACO_ARMADURA" -> "losango";
            case "ACO_ESTRUTURAL" -> "hexagono";
            default -> "retangulo";
        };
        String unidade = switch (i.familia()) {
            case "CONCRETO" -> "m3";
            case "ACO_ARMADURA", "ACO_ESTRUTURAL" -> "kg";
            case "ALVENARIA", "ARGAMASSA" -> "m2";
            case "TUBO", "CONDUTOR" -> "m";
            default -> "un";
        };
        var servico = cat.servicoDo(i.codigo(), unidade);
        return new Material(i.codigo(), i.nome(), new Material.Ontologia(dominio, "professional", i.familia().toLowerCase()),
                new Material.Fisicas(fck, fyk, null, null, portante, peso == null ? 0 : peso),
                new Material.Estilo(i.cor() == null ? "#9CA3AF" : i.cor(), "professional", null),
                new Material.Restricoes(0.05, 0.0, null, num(props, "largura")),
                new Material.Quantidades(unidade, 0, servico.map(CatalogoMateriais.Servico::sinapi).orElse(null)));
    }

    private static Double num(List<CatalogoMateriais.Propriedade> props, String chave) {
        return props.stream().filter(p -> p.chave().equals(chave)).findFirst().flatMap(p -> p.numero().isPresent() ? Optional.of(p.numero().getAsDouble()) : Optional.empty()).orElse(null);
    }
}
