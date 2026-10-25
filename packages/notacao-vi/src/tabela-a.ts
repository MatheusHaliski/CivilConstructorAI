/**
 * Catálogo técnico de materiais (fonte única: ../catalogo/materiais.json, o mesmo arquivo que o backend Java lê).
 * Separa material, família, componente, composição e serviço; cada propriedade traz origem (ESPECIFICADO,
 * DECLARADO_FABRICANTE, ENSAIO, ESTIMADO, ADOTADO). `TABELA_A`/`material()` continuam como visão legada para o
 * motor e o auditor; códigos legados (MAT_CONC_FCK30…) resolvem para o código canônico (MAT.CONCRETO.C30…).
 */
import catalogoJson from "../catalogo/materiais.json" with { type: "json" };

export type OrigemValor = "ESPECIFICADO" | "DECLARADO_FABRICANTE" | "ENSAIO" | "ESTIMADO" | "ADOTADO";
export type SuporteFamilia = "CALCULO" | "ANALISE" | "QUANTITATIVO" | "REPRESENTACAO" | "NAO_IMPLEMENTADO";

export type Referencia = {
  id: string; organismo: string; identificador: string; titulo: string; edicao: string; emendas: string[]; tipo: string;
  situacao: string; escopo: string; fonte: { url: string; natureza: string; consultadoEm: string | null }; cancelada?: boolean; substituidaPor?: string;
};
export type Propriedade = { chave: string; rotulo: string; valor: number | string | null; unidade: string | null; origem: OrigemValor; referencia: string | null; nota?: string | null; metodo?: string | null };
export type FamiliaMaterial = { id: string; nome: string; descricao: string; suporte: SuporteFamilia; referencias: string[] };
export type ItemCatalogo = {
  codigo: string; nome: string; familia: string; resumo?: string; legado: string[]; aplicacoes: string[]; estilo: { cor?: string; textura?: string };
  propriedades: Propriedade[]; personalizado?: boolean; situacaoValidacao?: string;
};
export type Bitola = { mm: number; situacao: string };
export type Servico = { codigo: string; nome: string; material: string; unidade: string; sinapi?: string; situacaoSinapi?: string };
export type Composicao = { codigo: string; nome: string; elemento: string; camadas: { material: string; espessura: number; assentamento?: string }[]; nota?: string };
export type CatalogoMateriais = {
  versao: string; descricao: string; origens: Record<OrigemValor, string>; suporte: Record<SuporteFamilia, string>; tiposReferencia: Record<string, string>;
  referencias: Referencia[]; familias: FamiliaMaterial[]; materiais: ItemCatalogo[]; bitolas: Record<string, Bitola[]>; composicoes: Composicao[]; servicos: Servico[];
};

export const CATALOGO_MATERIAIS = catalogoJson as unknown as CatalogoMateriais;

const ALIASES = new Map<string, string>();
for (const m of CATALOGO_MATERIAIS.materiais) {
  ALIASES.set(m.codigo.toUpperCase(), m.codigo);
  for (const l of m.legado) ALIASES.set(l.toUpperCase(), m.codigo);
}

/** αE do agregado graúdo (NBR 6118 8.2.8) */
export const ALFA_E: Record<string, number> = { basalto: 1.2, diabasio: 1.2, granito: 1.0, gnaisse: 1.0, calcario: 0.9, arenito: 0.7 };
export const AGREGADO_PADRAO = "granito";

/** código canônico; valor desconhecido volta como veio (o auditor o reprova) */
export function normalizarMaterialId(id: string): string {
  return ALIASES.get(id.trim().toUpperCase()) ?? id.trim();
}

/** materiais personalizados da empresa ativa (carregados pelo app); o auditor e o seletor passam a conhecê-los */
const PERSONALIZADOS: ItemCatalogo[] = [];

export function registrarMateriaisPersonalizados(itens: ItemCatalogo[]): void {
  for (const m of PERSONALIZADOS) ALIASES.delete(m.codigo.toUpperCase());
  PERSONALIZADOS.length = 0;
  for (const m of itens) { PERSONALIZADOS.push({ ...m, legado: m.legado ?? [], aplicacoes: m.aplicacoes ?? [], estilo: m.estilo ?? {}, personalizado: true }); ALIASES.set(m.codigo.toUpperCase(), m.codigo); }
}

export function materiaisPersonalizados(): readonly ItemCatalogo[] {
  return PERSONALIZADOS;
}

export function itemMaterial(id: string | undefined | null): ItemCatalogo | undefined {
  if (!id) return undefined;
  const c = ALIASES.get(id.trim().toUpperCase());
  return c ? CATALOGO_MATERIAIS.materiais.find((m) => m.codigo === c) ?? PERSONALIZADOS.find((m) => m.codigo === c) : undefined;
}

export function referencia(id: string | null | undefined): Referencia | undefined {
  return id ? CATALOGO_MATERIAIS.referencias.find((r) => r.id === id) : undefined;
}

export function familia(id: string): FamiliaMaterial | undefined {
  return CATALOGO_MATERIAIS.familias.find((f) => f.id === id);
}

const arr1 = (v: number) => Math.round(v * 10) / 10;

/** propriedades do item mais as derivadas — mesmo método e arredondamento do backend (CatalogoMateriais.java) */
export function propriedadesCompletas(item: ItemCatalogo, agregado?: string | null): Propriedade[] {
  const out = [...item.propriedades];
  const num = (k: string) => { const v = item.propriedades.find((p) => p.chave === k)?.valor; return typeof v === "number" ? v : undefined; };
  if (item.familia === "CONCRETO") {
    const fck = num("fck");
    if (fck !== undefined) {
      const ag = agregado && ALFA_E[agregado] !== undefined ? agregado : AGREGADO_PADRAO;
      const alfaE = ALFA_E[ag]!;
      const eci = alfaE * 5600 * Math.sqrt(fck);
      const alfaI = Math.min(1, 0.8 + 0.2 * fck / 80);
      out.push({ chave: "Eci", rotulo: "Módulo de elasticidade tangente inicial (Eci)", valor: arr1(eci), unidade: "MPa", origem: "ESTIMADO", referencia: "NBR6118", nota: `agregado ${ag}${agregado ? "" : " (premissa: não informado no projeto)"}`, metodo: `Eci = αE·5600·√fck, αE = ${alfaE.toFixed(1)}` });
      out.push({ chave: "Ecs", rotulo: "Módulo de deformação secante (Ecs)", valor: arr1(alfaI * eci), unidade: "MPa", origem: "ESTIMADO", referencia: "NBR6118", nota: "usado na análise estrutural", metodo: `Ecs = αi·Eci, αi = 0,8 + 0,2·fck/80 = ${alfaI.toFixed(3)}` });
      out.push({ chave: "pesoEspecifico", rotulo: "Peso específico (concreto armado)", valor: 25, unidade: "kN/m³", origem: "ADOTADO", referencia: "NBR6118", nota: "8.2.2" });
      out.push({ chave: "poisson", rotulo: "Coeficiente de Poisson", valor: 0.2, unidade: null, origem: "ADOTADO", referencia: "NBR6118", nota: "8.2.9" });
    }
  } else if (item.familia === "ACO_ARMADURA") {
    out.push({ chave: "Es", rotulo: "Módulo de elasticidade (Es)", valor: 210000, unidade: "MPa", origem: "ADOTADO", referencia: "NBR6118", nota: "8.3.5" });
    out.push({ chave: "massaEspecifica", rotulo: "Massa específica", valor: 7850, unidade: "kg/m³", origem: "ADOTADO", referencia: "NBR6118", nota: "8.3.3" });
  } else if (item.familia === "ACO_ESTRUTURAL") {
    out.push({ chave: "E", rotulo: "Módulo de elasticidade (E)", valor: 200000, unidade: "MPa", origem: "ADOTADO", referencia: "NBR8800", nota: "cláusula não conferida no texto oficial" });
    out.push({ chave: "pesoEspecifico", rotulo: "Peso específico", valor: 78.5, unidade: "kN/m³", origem: "ADOTADO", referencia: null, nota: "7850 kg/m³" });
  }
  return out;
}

export function bitolasDe(codigoAco: string): Bitola[] {
  const c = ALIASES.get(codigoAco.toUpperCase());
  return c ? CATALOGO_MATERIAIS.bitolas[c] ?? [] : [];
}

export function servicoDo(codigoMaterial: string, unidade: string): Servico | undefined {
  const c = ALIASES.get(codigoMaterial.toUpperCase());
  return CATALOGO_MATERIAIS.servicos.find((s) => s.material === c && s.unidade === unidade);
}

// ------------------------------------------------------------------ visão legada (motor e auditor)
/** Visão legada de um material (motor TS e auditor). */
export type Material = {
  materialId: string;
  name: string;
  ontology: { symbolDomain: "losango" | "retangulo" | "hexagono"; borderType: string; category: string };
  physicalProperties: { fck_MPa?: number; fyk_MPa?: number; thermalTransmittance?: number; fireRating?: string; loadBearing: boolean; densidade_kN_m3: number };
  style: { colorHex: string; borderType: "professional" | "thin" | "dashed"; texture?: string };
  geometricConstraints: { shortCurveTolerance_m: number; baseOffset: number; secaoMinima_m?: number; espessuraMinima_m?: number };
  baseQuantities: { unit: "m3" | "m2" | "kg" | "m" | "un"; unitCost: number; sinapi?: string };
};

function adaptar(i: ItemCatalogo): Material {
  const props = propriedadesCompletas(i);
  const num = (k: string) => { const v = props.find((p) => p.chave === k)?.valor; return typeof v === "number" ? v : undefined; };
  const unidade: Material["baseQuantities"]["unit"] = i.familia === "CONCRETO" ? "m3" : i.familia === "ACO_ARMADURA" || i.familia === "ACO_ESTRUTURAL" ? "kg"
    : i.familia === "ALVENARIA" || i.familia === "ARGAMASSA" ? "m2" : i.familia === "TUBO" || i.familia === "CONDUTOR" ? "m" : "un";
  const portante = i.familia === "CONCRETO" || i.familia === "ACO_ARMADURA" || i.familia === "ACO_ESTRUTURAL" || (i.familia === "ALVENARIA" && i.propriedades.some((p) => p.chave === "funcao" && p.valor === "estrutural"));
  return {
    materialId: i.codigo, name: i.nome,
    ontology: { symbolDomain: i.familia === "CONCRETO" || i.familia === "ACO_ARMADURA" ? "losango" : i.familia === "ACO_ESTRUTURAL" ? "hexagono" : "retangulo", borderType: "professional", category: i.familia.toLowerCase() },
    physicalProperties: { fck_MPa: num("fck"), fyk_MPa: num("fyk"), loadBearing: portante, densidade_kN_m3: num("pesoEspecifico") ?? 0 },
    style: { colorHex: i.estilo.cor ?? "#9CA3AF", borderType: "professional", texture: i.estilo.textura },
    geometricConstraints: { shortCurveTolerance_m: 0.05, baseOffset: 0.0, espessuraMinima_m: num("largura") },
    baseQuantities: { unit: unidade, unitCost: 0, sinapi: servicoDo(i.codigo, unidade)?.sinapi },
  };
}

export const TABELA_A: Material[] = CATALOGO_MATERIAIS.materiais.map(adaptar);

export function material(id: string): Material | undefined {
  const c = ALIASES.get(id.trim().toUpperCase());
  if (!c) return undefined;
  const p = PERSONALIZADOS.find((m) => m.codigo === c);
  return TABELA_A.find((m) => m.materialId === c) ?? (p ? adaptar(p) : undefined);
}
