package br.com.civilconstructorai.notacao;

/**
 * Tabela A · Data Model de materiais (VSL): ontologia, propriedades físico-mecânicas, estilo (Tabela H),
 * restrições construtivas (Camada C) e quantitativos (BIM 5D).
 */
public record Material(
        String materialId,
        String name,
        Ontologia ontology,
        Fisicas physicalProperties,
        Estilo style,
        Restricoes geometricConstraints,
        Quantidades baseQuantities) {

    public record Ontologia(String symbolDomain, String borderType, String category) {
    }

    public record Fisicas(Double fck_MPa, Double fyk_MPa, Double thermalTransmittance, String fireRating, boolean loadBearing, double densidade_kN_m3) {
    }

    public record Estilo(String colorHex, String borderType, String texture) {
    }

    public record Restricoes(double shortCurveTolerance_m, double baseOffset, Double secaoMinima_m, Double espessuraMinima_m) {
    }

    public record Quantidades(String unit, double unitCost, String sinapi) {
    }
}
