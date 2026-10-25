package br.com.civilconstructorai.notacao;

import java.util.List;
import java.util.Optional;

/** Materiais padrão da Tabela A (mesmo conteúdo de packages/notacao-vi/src/tabela-a.ts). */
public final class TabelaA {

    private TabelaA() {
    }

    private static Material m(String id, String nome, String dominio, String categoria, Double fck, Double fyk, Double u, String fogo, boolean portante,
                              double densidade, String cor, String borda, String textura, Double secaoMin, Double espMin, String unidade, double custo, String sinapi) {
        return new Material(id, nome, new Material.Ontologia(dominio, "professional", categoria),
                new Material.Fisicas(fck, fyk, u, fogo, portante, densidade), new Material.Estilo(cor, borda, textura),
                new Material.Restricoes(0.05, 0.0, secaoMin, espMin), new Material.Quantidades(unidade, custo, sinapi));
    }

    public static final List<Material> MATERIAIS = List.of(
            m("MAT_CONC_FCK30", "Concreto Armado fck = 30 MPa", "losango", "estrutural", 30.0, null, 1.75, "F90", true, 25, "#9CA3AF", "professional", "concreto", 0.19, null, "m3", 450.0, "94965"),
            m("MAT_CONC_FCK25", "Concreto Armado fck = 25 MPa", "losango", "estrutural", 25.0, null, null, "F90", true, 25, "#A8A29E", "professional", "concreto", 0.19, null, "m3", 410.0, "94964"),
            m("MAT_ACO_CA50", "Aço CA-50", "losango", "estrutural", null, 500.0, null, null, true, 78.5, "#475569", "thin", null, null, null, "kg", 9.8, "92779"),
            m("MAT_ALV_BLOCO14", "Alvenaria de bloco cerâmico 14 cm", "retangulo", "arquitetura", null, null, 2.4, "F60", false, 13, "#C2410C", "professional", "tijolo", null, 0.09, "m2", 98.0, "103323"),
            m("MAT_ALV_BLOCO19", "Alvenaria estrutural de bloco de concreto 19 cm", "retangulo", "estrutural", null, null, null, "F90", true, 14, "#9A3412", "professional", "bloco", null, 0.14, "m2", 125.0, "103355"),
            m("MAT_ACO_PERFIL_W", "Perfil de aço laminado W (ASTM A572)", "hexagono", "estrutural", null, 345.0, null, "F30", true, 78.5, "#1E3A8A", "thin", null, null, null, "kg", 14.2, "100777"),
            m("MAT_VIDRO_TEMP8", "Vidro temperado 8 mm", "retangulo", "arquitetura", null, null, 5.7, null, false, 25, "#38BDF8", "thin", null, null, null, "m2", 420.0, "102180"),
            m("MAT_MADEIRA_PORTA", "Porta de madeira semi-oca 80x210", "retangulo", "arquitetura", null, null, null, null, false, 6, "#A16207", "thin", null, null, null, "un", 650.0, "90843"));

    public static Optional<Material> material(String id) {
        var normalizado = normalizarId(id);
        return MATERIAIS.stream().filter(x -> x.materialId().equals(normalizado)).findFirst();
    }

    /**
     * Aceita o código do catálogo de códigos ({@code MAT.ESTRUTURAL.CONC_FCK30}) e devolve o materialId legado
     * ({@code MAT_CONC_FCK30}); qualquer outro valor volta como veio. Mesma regra de {@code normalizarMaterialId} no TS.
     */
    public static String normalizarId(String id) {
        if (id == null) {
            return null;
        }
        var m = java.util.regex.Pattern.compile("^MAT\\.[A-Za-z]+\\.(.+)$").matcher(id.trim());
        return m.matches() ? "MAT_" + m.group(1).toUpperCase() : id;
    }
}
