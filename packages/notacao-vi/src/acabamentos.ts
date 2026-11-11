/** Camadas físicas, compra e peso instalado. Nenhuma propriedade resistente é inferida. */
import type { Abertura, CamadaAcabamento, Elemento, FaceAcabamento, Parede } from "./tipos";
import type { ModeloBim } from "./modelo";
import { areaPoligono, dist } from "./geometria";

export type MaterialConstrutivo = {
  id: string; nome: string; familia: string; aplicacoes: ("PAREDE" | "LAJE")[];
  espessuraPadrao_m: number; formatos: { largura_m: number; altura_m: number }[];
  cor: string; textura: string; descricao: string; fonte: string;
  pesoEspecifico_kN_m3?: number; massaSuperficial_kg_m2?: number;
};
const formatos = (...medidas: [number, number][]) => medidas.map(([largura_m, altura_m]) => ({ largura_m, altura_m }));
const criar = (id: string, nome: string, familia: string, espessuraPadrao_m: number, cor: string, descricao: string,
  formatosPecas: MaterialConstrutivo["formatos"] = [], fonte = "Configuração geométrica de projeto; cadastrar produto e massa instalada."): MaterialConstrutivo =>
  ({ id, nome, familia, espessuraPadrao_m, cor, textura: familia, descricao, formatos: formatosPecas, fonte, aplicacoes: ["PAREDE", "LAJE"] });

export const MATERIAIS_CONSTRUTIVOS: MaterialConstrutivo[] = [
  criar("MAT_REBOCO", "Reboco", "argamassa", .02, "#c4b9a5", "Revestimento cimentício. Consumo seco não determina massa curada."),
  criar("MAT_SEMIREBOCO", "Semi-reboco (configurável)", "argamassa", .01, "#b8ad98", "Nome de configuração: definir produto, camadas e espessura; não significa metade do reboco."),
  criar("MAT_CHAPISCO", "Chapisco", "argamassa", .005, "#a99f8b", "Camada de preparo; produto, aplicação e massa devem ser especificados."),
  criar("MAT_ARGAMASSA", "Argamassa de assentamento", "argamassa", .005, "#b8b3a8", "Assentamento em camada própria; conferir dupla colagem e rendimento do produto."),
  { ...criar("MAT_AZULEJO", "Azulejo", "ceramica", .006, "#e5eff0", "Peças para parede. As dimensões/junta são editáveis; corte e paginação não estão otimizados.", formatos([.155,.155], [.2,.3], [.3,.6]), "TAI Azulejos: exemplo 15,5×15,5 cm, 6 mm; outros formatos são configurações de projeto."), aplicacoes: ["PAREDE"] },
  criar("MAT_PORCELANATO", "Porcelanato", "ceramica", .008, "#d0c8b9", "Conferir uso parede/piso e ficha do SKU; adesivo e rejunte não estão incluídos.", formatos([.76,.76], [.6,.6], [1.2,.6]), "Tecnogres PTG76420R: exemplo 76×76 cm, 8 mm; massa deve corresponder à peça selecionada."),
  { ...criar("MAT_GRANITO", "Granito — referência Barre Gray", "pedra", .02, "#777d83", "Massa específica de um produto específico; substituir conforme a pedra real. Espessura é de projeto.", formatos([.6,.6], [1.2,.6]), "Polycor BARRE GRAY, ASTM C97: 2654,26 kg/m³; consultar FONTES_MATERIAIS_ACABAMENTOS.md."), pesoEspecifico_kN_m3: 2654.26 * 9.80665 / 1000 },
  criar("MAT_MARMORE", "Mármore", "pedra", .02, "#e5dfd3", "Identificar pedra e método de ensaio; métodos diferentes não devem ser promediados.", formatos([.6,.6], [1.2,.6]), "Levantina Mistral: resultados de densidade por métodos distintos; não foi eleito valor universal."),
  criar("MAT_GESSO", "Gesso de revestimento", "gesso", .01, "#f1eee8", "Consumo de pó não é peso instalado. Informar condição de umidade e dados do produto."),
  criar("MAT_DRYWALL_ST", "Chapa drywall ST", "gesso", .0125, "#e8dfd0", "Somente chapa; perfis, lã, fixadores e outras chapas são componentes separados.", formatos([1.2,2.4], [.6,1.2], [1.2,3]), "Placo ST12,5: ficha com massa entre 8–12 kg/m²; cadastrar valor de projeto documentado, sem média automática."),
  criar("MAT_DRYWALL_RU", "Chapa drywall RU", "gesso", .0125, "#b2c8b0", "Chapa para aplicação conforme fabricante. Cor não comprova resistência à umidade do sistema.", formatos([1.2,2.4])),
  criar("MAT_DRYWALL_RF", "Chapa drywall RF", "gesso", .0125, "#d9b4b2", "A chapa isolada não determina classificação de resistência ao fogo da divisória.", formatos([1.2,2.4])),
  criar("MAT_PINTURA", "Pintura", "pintura", .0002, "#faf7ef", "Espessura ilustrativa editável; especificar demãos, produto e massa instalada."),
];

export function materialConstrutivo(id: string) { return MATERIAIS_CONSTRUTIVOS.find(m => m.id === id); }
export function criarCamadaAcabamento(materialId: string, face: FaceAcabamento, id: string): CamadaAcabamento {
  const m = materialConstrutivo(materialId);
  if (!m) throw new Error("Material de acabamento desconhecido.");
  return { id, materialId, face, espessura_m: m.espessuraPadrao_m, junta_m: m.formatos.length ? .003 : 0,
    perda_percentual: 0, larguraPeca_m: m.formatos[0]?.largura_m, alturaPeca_m: m.formatos[0]?.altura_m,
    pesoEspecifico_kN_m3: m.pesoEspecifico_kN_m3, massaSuperficial_kg_m2: m.massaSuperficial_kg_m2,
    especificacao: m.fonte };
}

/** Retângulos sem sobreposição no plano local da parede. O desconto é a união dos vãos recortados. */
export function retangulosLivresParede(p: Parede, aberturas: Abertura[]): { x0: number; x1: number; z0: number; z1: number }[] {
  const l = dist(p.p1,p.p2), h = p.altura;
  if (!Number.isFinite(l) || !Number.isFinite(h) || l <= 0 || h <= 0) return [];
  const vaos = aberturas.filter(a => a.paredeId === p.id && !a.rascunhoIaId).map(a => ({
    x0: Math.max(0,a.posicao*l-a.largura/2), x1: Math.min(l,a.posicao*l+a.largura/2),
    z0: Math.max(0,a.peitoril), z1: Math.min(h,a.peitoril+a.altura),
  })).filter(a => [a.x0,a.x1,a.z0,a.z1].every(Number.isFinite) && a.x1 > a.x0 && a.z1 > a.z0);
  const xs = [...new Set([0,l,...vaos.flatMap(v => [v.x0,v.x1])])].sort((a,b) => a-b);
  const zs = [...new Set([0,h,...vaos.flatMap(v => [v.z0,v.z1])])].sort((a,b) => a-b);
  const livres = [];
  for (let i=0;i<xs.length-1;i++) for (let j=0;j<zs.length-1;j++) {
    const x0=xs[i]!,x1=xs[i+1]!,z0=zs[j]!,z1=zs[j+1]!,x=(x0+x1)/2,z=(z0+z1)/2;
    if (!vaos.some(v => x > v.x0 && x < v.x1 && z > v.z0 && z < v.z1)) livres.push({x0,x1,z0,z1});
  }
  return livres;
}

export function errosAcabamentos(e: Elemento): string[] {
  const raw = (e as unknown as { acabamentos?: unknown }).acabamentos;
  const lista = raw ?? [];
  if (!Array.isArray(lista)) return ["Acabamentos devem ser uma lista de camadas."];
  if (e.tipo !== "PAREDE" && e.tipo !== "LAJE") return lista.length ? ["Camadas permitidas apenas em paredes e lajes."] : [];
  const erros: string[] = [], ids = new Set<string>();
  for (const item of lista) {
    if (!item || typeof item !== "object") { erros.push("Camada inválida."); continue; }
    const c = item as CamadaAcabamento, m = materialConstrutivo(c.materialId);
    if (!c.id || ids.has(c.id)) erros.push("Identificador de camada ausente ou duplicado.");
    ids.add(c.id);
    if (!m || !m.aplicacoes.includes(e.tipo)) erros.push("Material de acabamento incompatível com o elemento.");
    const faces = e.tipo === "PAREDE" ? ["LADO_A","LADO_B"] : ["SUPERIOR","INFERIOR"];
    if (!faces.includes(c.face)) erros.push("Face de acabamento incompatível.");
    if (!Number.isFinite(c.espessura_m) || c.espessura_m <= 0 || c.espessura_m > .5) erros.push("Espessura de camada deve estar entre 0 e 0,5 m.");
    if (!Number.isFinite(c.junta_m) || c.junta_m < 0 || c.junta_m > .1) erros.push("Junta inválida.");
    if (!Number.isFinite(c.perda_percentual) || c.perda_percentual < 0 || c.perda_percentual > 100) erros.push("Perda deve estar entre 0 e 100%.");
    if ((c.larguraPeca_m === undefined) !== (c.alturaPeca_m === undefined)) erros.push("Informe as duas dimensões da peça.");
    for (const k of ["larguraPeca_m","alturaPeca_m","massaSuperficial_kg_m2","pesoEspecifico_kN_m3"] as const)
      if (c[k] !== undefined && (!Number.isFinite(c[k]) || c[k]! <= 0)) erros.push(`${k}: valor finito positivo obrigatório.`);
  }
  if (e.tipo === "PAREDE" && e.massaNucleo_kg_m2 !== undefined && (!Number.isFinite(e.massaNucleo_kg_m2) || e.massaNucleo_kg_m2 <= 0)) erros.push("Massa do núcleo deve ser positiva e finita.");
  return erros;
}

export type MedicaoAcabamento = {
  elementoId: string; camadaId: string; materialId: string; nome: string; face: FaceAcabamento;
  areaBruta_m2: number; areaLiquida_m2: number; volume_m3: number; areaCompra_m2: number;
  pecasEstimadas?: number; pesoInstalado_kN?: number; pendencias: string[];
};
export function medirAcabamentos(modelo: ModeloBim): MedicaoAcabamento[] {
  const out: MedicaoAcabamento[] = [];
  for (const e of modelo.elementos) {
    if (e.rascunhoIaId || (e.tipo !== "PAREDE" && e.tipo !== "LAJE")) continue;
    const erros = errosAcabamentos(e);
    if (erros.length) throw new Error(erros.join(" "));
    const aberturas = modelo.elementos.filter((a): a is Abertura => a.tipo === "PORTA" || a.tipo === "JANELA");
    const bruta = e.tipo === "PAREDE" ? dist(e.p1,e.p2)*e.altura : areaPoligono(e.contorno);
    const area = e.tipo === "PAREDE" ? retangulosLivresParede(e,aberturas).reduce((s,r) => s+(r.x1-r.x0)*(r.z1-r.z0),0) : bruta;
    for (const c of e.acabamentos ?? []) {
      const volume = area*c.espessura_m, compra=area*(1+c.perda_percentual/100);
      const peso = c.massaSuperficial_kg_m2 !== undefined ? area*c.massaSuperficial_kg_m2*9.80665/1000
        : c.pesoEspecifico_kN_m3 !== undefined ? volume*c.pesoEspecifico_kN_m3 : undefined;
      const pecas = c.larguraPeca_m && c.alturaPeca_m ? Math.ceil(compra/((c.larguraPeca_m+c.junta_m)*(c.alturaPeca_m+c.junta_m))) : undefined;
      out.push({ elementoId:e.id,camadaId:c.id,materialId:c.materialId,nome:materialConstrutivo(c.materialId)!.nome,face:c.face,
        areaBruta_m2:bruta,areaLiquida_m2:area,volume_m3:volume,areaCompra_m2:compra,pecasEstimadas:pecas,pesoInstalado_kN:peso,
        pendencias:[...(peso === undefined ? ["Massa instalada não informada; peso não calculado."] : []),
          ...(pecas !== undefined ? ["Peças por área modular: estimativa sem plano de corte nem arredondamento por caixa."] : [])] });
    }
  }
  return out;
}
