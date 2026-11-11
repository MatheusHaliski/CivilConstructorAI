/** Catálogo técnico preliminar. Cimento é ligante: sua classe NÃO é o fck do concreto. */
export type TipoCimento = "CP I" | "CP II-E" | "CP II-Z" | "CP II-F" | "CP III" | "CP IV" | "CP V-ARI";
export type ClasseCimento = 25 | 32 | 40;
export interface Cimento {
  id: string;
  tipo: TipoCimento;
  nome: string;
  classe28d?: ClasseCimento;
  composicao: string;
  aplicacao: string;
  cuidados: string;
  fonte: string;
  /** Qualificadores só podem ser preenchidos com documentação do produto específico. */
  resistenteSulfatos?: boolean;
  baixoCalor?: boolean;
}
const FONTE = "https://www.cimentoitambe.com.br/portal/tipos-de-cimento/";
const familias: { tipo: TipoCimento; codigo: string; composicao: string; aplicacao: string; cuidados: string }[] = [
  { tipo: "CP I", codigo: "CP_I", composicao: "Clínquer e sulfatos de cálcio; sem adição principal declarada neste catálogo.", aplicacao: "Usos gerais conforme dosagem e ficha do produto.", cuidados: "Confirmar disponibilidade, composição e desempenho no certificado do fabricante." },
  { tipo: "CP II-E", codigo: "CP_II_E", composicao: "Clínquer, sulfatos de cálcio e escória granulada de alto-forno.", aplicacao: "Concreto e argamassa conforme estudo de dosagem.", cuidados: "Teor de escória e resistência a agentes agressivos exigem ficha específica." },
  { tipo: "CP II-Z", codigo: "CP_II_Z", composicao: "Clínquer, sulfatos de cálcio e material pozolânico.", aplicacao: "Concreto e argamassa conforme estudo de dosagem.", cuidados: "Confirmar curva de resistência e condições de cura." },
  { tipo: "CP II-F", codigo: "CP_II_F", composicao: "Clínquer, sulfatos de cálcio e material carbonático (fíler).", aplicacao: "Usos gerais, concreto e argamassa conforme dosagem.", cuidados: "O tipo por si só não certifica resistência a sulfatos nem baixo calor." },
  { tipo: "CP III", codigo: "CP_III", composicao: "Clínquer, sulfatos de cálcio e escória granulada de alto-forno como adição característica.", aplicacao: "Avaliar para controle térmico e durabilidade com documentação do produto.", cuidados: "Não atribuir automaticamente os qualificadores BC ou RS; verificar cura e resistência inicial." },
  { tipo: "CP IV", codigo: "CP_IV", composicao: "Clínquer, sulfatos de cálcio e material pozolânico como adição característica.", aplicacao: "Avaliar em concretos com requisitos de durabilidade e controle térmico.", cuidados: "O produto CP IV-32 RS citado na fonte não qualifica todos os CP IV como RS." },
  { tipo: "CP V-ARI", codigo: "CP_V_ARI", composicao: "Clínquer, sulfatos de cálcio e eventual material carbonático; maior finura para resistência inicial.", aplicacao: "Avaliar quando há requisito explícito de resistência nas primeiras idades.", cuidados: "ARI não é classe de resistência 25/32/40 aos 28 dias. Não autoriza desforma sem ensaio e projeto." },
];
export const CIMENTOS: readonly Cimento[] = familias.flatMap<Cimento>((f) =>
  f.tipo === "CP V-ARI"
    ? [{ ...f, id: `CIM_${f.codigo}`, nome: f.tipo, fonte: FONTE }]
    : ([25, 32, 40] as const).map((classe28d) => ({ ...f, id: `CIM_${f.codigo}_${classe28d}`, nome: `${f.tipo}-${classe28d}`, classe28d, fonte: FONTE })),
);
export function cimentoPorId(id?: string): Cimento | undefined { return CIMENTOS.find((c) => c.id === id); }

export interface EspecificacaoCimento {
  origemMemorial: string;
  versaoModelo: number;
  desempenhoValidado: boolean;
  tiposPermitidos?: readonly TipoCimento[];
  classeMinima?: ClasseCimento;
  exigeRS?: boolean;
  exigeBC?: boolean;
}
export interface CompatibilidadeCimento {
  estado: "COMPATIVEL" | "INCOMPATIVEL" | "NAO_VERIFICADO";
  pendencias: string[];
}
/** Confere requisitos explícitos; não calcula traço nem certifica desempenho estrutural. */
export function avaliarCimento(id: string | undefined, e?: EspecificacaoCimento): CompatibilidadeCimento {
  const c = cimentoPorId(id);
  if (!c) return { estado: "NAO_VERIFICADO", pendencias: ["Selecione um cimento do catálogo."] };
  if (!e || !e.origemMemorial.trim() || !Number.isInteger(e.versaoModelo) || e.versaoModelo < 0 || !e.desempenhoValidado)
    return { estado: "NAO_VERIFICADO", pendencias: ["Seleção preliminar: faltam memorial rastreável, requisitos de desempenho e validação de dosagem/ensaios. Liberação técnica bloqueada."] };
  if (e.tiposPermitidos?.length && !e.tiposPermitidos.includes(c.tipo))
    return { estado: "INCOMPATIVEL", pendencias: ["Tipo não permitido pelo memorial."] };
  if (e.classeMinima && c.classe28d && c.classe28d < e.classeMinima)
    return { estado: "INCOMPATIVEL", pendencias: ["Classe do cimento inferior ao requisito explícito do memorial."] };
  const pendencias: string[] = [];
  if (!e.tiposPermitidos?.length && !e.classeMinima && !e.exigeRS && !e.exigeBC) pendencias.push("Memorial sem requisito verificável de cimento.");
  if (e.classeMinima && !c.classe28d) pendencias.push("ARI não demonstra classe aos 28 dias; anexar certificado do produto.");
  if (e.exigeRS && c.resistenteSulfatos !== true) pendencias.push("Falta certificado do produto específico que comprove RS.");
  if (e.exigeBC && c.baixoCalor !== true) pendencias.push("Falta certificado do produto específico que comprove BC.");
  return { estado: pendencias.length ? "NAO_VERIFICADO" : "COMPATIVEL", pendencias };
}

/** Orientação determinística local, expressamente sem IA e sem autoridade para impor produto. */
export function orientarCimento(prioridade: "GERAL" | "BAIXO_CALOR" | "RESISTENCIA_INICIAL"): string[] {
  return prioridade === "RESISTENCIA_INICIAL" ? ["CIM_CP_V_ARI"]
    : prioridade === "BAIXO_CALOR" ? ["CIM_CP_III_32", "CIM_CP_IV_32"] : ["CIM_CP_II_F_32", "CIM_CP_II_E_32", "CIM_CP_II_Z_32"];
}

/** Registro fornecido por repositório técnico autorizado, nunca por resposta do LLM. */
export interface DosagemCimentoDocumentada {
  id: string;
  cimentoId: string;
  fckProjetoMPa: number;
  fckComprovadoMPa: number;
  referenciaEnsaio: string;
  origemMemorial: string;
  versaoModelo: number;
  estado: "VALIDADO" | "PENDENTE";
  composicao: { aguaLigante: number; agregado: string; consumoCimentoKgM3: number; procedimentoCura: string };
}
/** Nunca infere uma correspondência entre classe de cimento e fck. Não autentica a origem dos registros. */
export function cimentosComDosagem(
  fckProjetoMPa: number | undefined, versaoModelo: number | undefined, dosagens: readonly DosagemCimentoDocumentada[],
): Cimento[] {
  if (!Number.isFinite(fckProjetoMPa) || !fckProjetoMPa || versaoModelo === undefined) return [];
  const ids = new Set(dosagens.filter((d) => d.estado === "VALIDADO" && d.versaoModelo === versaoModelo
    && d.fckProjetoMPa === fckProjetoMPa && Number.isFinite(d.fckComprovadoMPa) && d.fckComprovadoMPa >= fckProjetoMPa
    && d.referenciaEnsaio.trim() && d.origemMemorial.trim()
    && Number.isFinite(d.composicao.aguaLigante) && d.composicao.aguaLigante > 0
    && d.composicao.agregado.trim() && Number.isFinite(d.composicao.consumoCimentoKgM3)
    && d.composicao.consumoCimentoKgM3 > 0 && d.composicao.procedimentoCura.trim()).map((d) => d.cimentoId));
  return CIMENTOS.filter((c) => ids.has(c.id));
}
