/**
 * Tabela A · Data Model de materiais (VSL). Cada insumo é um objeto JSON tipado com as cinco camadas das
 * diretrizes: ontologia, propriedades físico-mecânicas, estilo (Tabela H), restrições construtivas (Camada C)
 * e quantitativos (BIM 5D).
 */
export type Material = {
  materialId: string;
  name: string;
  ontology: { symbolDomain: "losango" | "retangulo" | "hexagono"; borderType: string; category: "estrutural" | "arquitetura" | "sistemas" | "fundacao" };
  physicalProperties: { fck_MPa?: number; fyk_MPa?: number; thermalTransmittance?: number; fireRating?: string; loadBearing: boolean; densidade_kN_m3: number };
  style: { colorHex: string; borderType: "professional" | "thin" | "dashed"; texture?: string };
  geometricConstraints: { shortCurveTolerance_m: number; baseOffset: number; secaoMinima_m?: number; espessuraMinima_m?: number };
  baseQuantities: { unit: "m3" | "m2" | "kg" | "m" | "un"; unitCost: number; sinapi?: string };
};

export const TABELA_A: Material[] = [
  {
    materialId: "MAT_CONC_FCK30", name: "Concreto Armado fck = 30 MPa",
    ontology: { symbolDomain: "losango", borderType: "professional", category: "estrutural" },
    physicalProperties: { fck_MPa: 30, thermalTransmittance: 1.75, fireRating: "F90", loadBearing: true, densidade_kN_m3: 25 },
    style: { colorHex: "#9CA3AF", borderType: "professional", texture: "concreto" },
    geometricConstraints: { shortCurveTolerance_m: 0.05, baseOffset: 0.0, secaoMinima_m: 0.19 },
    baseQuantities: { unit: "m3", unitCost: 450.0, sinapi: "94965" },
  },
  {
    materialId: "MAT_CONC_FCK25", name: "Concreto Armado fck = 25 MPa",
    ontology: { symbolDomain: "losango", borderType: "professional", category: "estrutural" },
    physicalProperties: { fck_MPa: 25, fireRating: "F90", loadBearing: true, densidade_kN_m3: 25 },
    style: { colorHex: "#A8A29E", borderType: "professional", texture: "concreto" },
    geometricConstraints: { shortCurveTolerance_m: 0.05, baseOffset: 0.0, secaoMinima_m: 0.19 },
    baseQuantities: { unit: "m3", unitCost: 410.0, sinapi: "94964" },
  },
  {
    materialId: "MAT_ACO_CA50", name: "Aço CA-50",
    ontology: { symbolDomain: "losango", borderType: "professional", category: "estrutural" },
    physicalProperties: { fyk_MPa: 500, loadBearing: true, densidade_kN_m3: 78.5 },
    style: { colorHex: "#475569", borderType: "thin" },
    geometricConstraints: { shortCurveTolerance_m: 0.05, baseOffset: 0.0 },
    baseQuantities: { unit: "kg", unitCost: 9.8, sinapi: "92779" },
  },
  {
    materialId: "MAT_ALV_BLOCO14", name: "Alvenaria de bloco cerâmico 14 cm",
    ontology: { symbolDomain: "retangulo", borderType: "professional", category: "arquitetura" },
    physicalProperties: { thermalTransmittance: 2.4, fireRating: "F60", loadBearing: false, densidade_kN_m3: 13 },
    style: { colorHex: "#C2410C", borderType: "professional", texture: "tijolo" },
    geometricConstraints: { shortCurveTolerance_m: 0.05, baseOffset: 0.0, espessuraMinima_m: 0.09 },
    baseQuantities: { unit: "m2", unitCost: 98.0, sinapi: "103323" },
  },
  {
    materialId: "MAT_ALV_BLOCO19", name: "Alvenaria estrutural de bloco de concreto 19 cm",
    ontology: { symbolDomain: "retangulo", borderType: "professional", category: "estrutural" },
    physicalProperties: { fireRating: "F90", loadBearing: true, densidade_kN_m3: 14 },
    style: { colorHex: "#9A3412", borderType: "professional", texture: "bloco" },
    geometricConstraints: { shortCurveTolerance_m: 0.05, baseOffset: 0.0, espessuraMinima_m: 0.14 },
    baseQuantities: { unit: "m2", unitCost: 125.0, sinapi: "103355" },
  },
  {
    materialId: "MAT_ACO_PERFIL_W", name: "Perfil de aço laminado W (ASTM A572)",
    ontology: { symbolDomain: "hexagono", borderType: "professional", category: "estrutural" },
    physicalProperties: { fyk_MPa: 345, fireRating: "F30", loadBearing: true, densidade_kN_m3: 78.5 },
    style: { colorHex: "#1E3A8A", borderType: "thin" },
    geometricConstraints: { shortCurveTolerance_m: 0.05, baseOffset: 0.0 },
    baseQuantities: { unit: "kg", unitCost: 14.2, sinapi: "100777" },
  },
  {
    materialId: "MAT_VIDRO_TEMP8", name: "Vidro temperado 8 mm",
    ontology: { symbolDomain: "retangulo", borderType: "thin", category: "arquitetura" },
    physicalProperties: { thermalTransmittance: 5.7, loadBearing: false, densidade_kN_m3: 25 },
    style: { colorHex: "#38BDF8", borderType: "thin" },
    geometricConstraints: { shortCurveTolerance_m: 0.05, baseOffset: 0.0 },
    baseQuantities: { unit: "m2", unitCost: 420.0, sinapi: "102180" },
  },
  {
    materialId: "MAT_MADEIRA_PORTA", name: "Porta de madeira semi-oca 80x210",
    ontology: { symbolDomain: "retangulo", borderType: "thin", category: "arquitetura" },
    physicalProperties: { loadBearing: false, densidade_kN_m3: 6 },
    style: { colorHex: "#A16207", borderType: "thin" },
    geometricConstraints: { shortCurveTolerance_m: 0.05, baseOffset: 0.0 },
    baseQuantities: { unit: "un", unitCost: 650.0, sinapi: "90843" },
  },
];

export function material(id: string): Material | undefined {
  const normalizado = normalizarMaterialId(id);
  return TABELA_A.find((m) => m.materialId === normalizado);
}

/** `MAT.ESTRUTURAL.CONC_FCK30` (código do catálogo) → `MAT_CONC_FCK30` (materialId); outro valor volta como veio. */
export function normalizarMaterialId(id: string): string {
  const m = /^MAT\.[A-Za-z]+\.(.+)$/.exec(id.trim());
  return m ? `MAT_${m[1]!.toUpperCase()}` : id;
}
