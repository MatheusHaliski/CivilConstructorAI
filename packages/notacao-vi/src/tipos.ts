/** Tipos do domínio BIM e da Notação VI. Unidades: metros, kN, MPa. Eixo Z para cima. */

export type Ponto2D = { x: number; y: number };
export type Ponto3D = { x: number; y: number; z: number };

export type Pavimento = {
  id: string;
  nome: string;
  /** elevação do piso acabado em metros; térreo = 0 */
  elevacao: number;
  tipo: "FUNDACAO" | "TERREO" | "TIPO" | "COBERTURA" | "CAIXA_DAGUA";
};

export type Eixo = {
  id: string;
  nome: string; // A, B, C… ou 1, 2, 3…
  direcao: "X" | "Y";
  /** posição ao longo do eixo perpendicular (eixo X vertical tem x fixo; eixo Y horizontal tem y fixo) */
  posicao: number;
  inicio: number;
  fim: number;
  /** recuo da bolha em metros (RF03) */
  recuoBolha: number;
};

export type Camada = "arquitetura" | "estrutura" | "fundacao" | "desenho" | "anotacao" | "ia-rascunho";

export type TipoElemento =
  | "PAREDE" | "PILAR" | "VIGA" | "LAJE" | "PORTA" | "JANELA" | "AMBIENTE"
  | "LINHA" | "POLILINHA" | "CIRCULO" | "RETANGULO" | "ARCO" | "PONTO" | "PLANO_REF"
  | "CONTRAVENTAMENTO" | "SAPATA" | "BLOCO" | "RADIER" | "ESTACA"
  | "COTA" | "TEXTO" | "ETIQUETA" | "HACHURA";

export type ElementoBase = {
  id: string;
  tipo: TipoElemento;
  pavimentoId: string;
  camada: Camada;
  materialId?: string;
  /** origem do comando que criou/alterou por último */
  origem: OrigemComando;
  /** rascunho de IA ainda não aprovado (RF27) */
  rascunhoIaId?: string;
  rotulo?: string;
};

export type Parede = ElementoBase & { tipo: "PAREDE"; p1: Ponto2D; p2: Ponto2D; espessura: number; altura: number; externa: boolean };
export type Pilar = ElementoBase & {
  tipo: "PILAR"; centro: Ponto2D; b: number; h: number; secao: "RETANGULAR" | "CIRCULAR";
  nivelBaseId: string; nivelTopoId: string; baseOffset: number; topoOffset: number; continuo: boolean; rotacao: number;
};
export type Viga = ElementoBase & { tipo: "VIGA"; p1: Ponto2D; p2: Ponto2D; b: number; h: number };
export type Laje = ElementoBase & { tipo: "LAJE"; contorno: Ponto2D[]; h: number };
export type Abertura = ElementoBase & { tipo: "PORTA" | "JANELA"; paredeId: string; posicao: number; largura: number; altura: number; peitoril: number };
export type Ambiente = ElementoBase & { tipo: "AMBIENTE"; ponto: Ponto2D; nome: string; area?: number };
export type Linha = ElementoBase & { tipo: "LINHA" | "PLANO_REF"; p1: Ponto2D; p2: Ponto2D };
export type Polilinha = ElementoBase & { tipo: "POLILINHA"; pontos: Ponto2D[]; fechada: boolean };
export type Circulo = ElementoBase & { tipo: "CIRCULO"; centro: Ponto2D; raio: number };
export type Retangulo = ElementoBase & { tipo: "RETANGULO"; p1: Ponto2D; p2: Ponto2D };
export type Arco = ElementoBase & { tipo: "ARCO"; p1: Ponto2D; p2: Ponto2D; p3: Ponto2D };
export type PontoRef = ElementoBase & { tipo: "PONTO"; p: Ponto2D };
export type Contraventamento = ElementoBase & { tipo: "CONTRAVENTAMENTO"; p1: Ponto2D; p2: Ponto2D; perfil: string };
export type Fundacao = ElementoBase & {
  tipo: "SAPATA" | "BLOCO" | "RADIER" | "ESTACA"; pilarId?: string; centro: Ponto2D; bx: number; by: number; h: number;
  estacas?: number; diametroEstaca?: number;
};
export type Cota = ElementoBase & { tipo: "COTA"; p1: Ponto2D; p2: Ponto2D; afastamento: number; estilo: "LINEAR" | "ALINHADA" | "NIVEL" };
export type Texto = ElementoBase & { tipo: "TEXTO" | "ETIQUETA"; p: Ponto2D; texto: string; altura: number };
export type Hachura = ElementoBase & { tipo: "HACHURA"; contorno: Ponto2D[]; padrao: string };

export type Elemento =
  | Parede | Pilar | Viga | Laje | Abertura | Ambiente | Linha | Polilinha | Circulo | Retangulo | Arco | PontoRef
  | Contraventamento | Fundacao | Cota | Texto | Hachura;

export type OrigemComando = "CANVAS" | "CONSOLE_VI" | "FORMULARIO" | "IA" | "SISTEMA";

/** Um comando da Notação VI: `VI(COD, arg=valor, …)` */
export type ComandoVI = {
  id: string;
  codigo: string;
  args: Record<string, unknown>;
  origem: OrigemComando;
  versaoBase: number;
  autorId?: string;
  criadoEm: string;
};

export type DiffModelo = {
  versaoNova: number;
  criados: Elemento[];
  alterados: Elemento[];
  removidos: string[];
  pavimentosCriados?: Pavimento[];
  eixosCriados?: Eixo[];
  eixosAlterados?: Eixo[];
};

export type Severidade = "AVISO" | "VIOLACAO";
export type Violacao = {
  codigo: string;
  severidade: Severidade;
  regra: string;
  elementoRef?: string;
  mensagem: string;
  esperado?: string;
  obtido?: string;
};
export type Correcao = { regra: string; elementoRef: string; campo: string; antes: unknown; depois: unknown };

export type RelatorioAuditoria = {
  laco1: { aprovado: boolean; violacoes: Violacao[] };
  laco2: { aprovado: boolean; violacoes: Violacao[]; avisos: Violacao[] };
  correcoes: Correcao[];
  aprovado: boolean;
};

export type EstadoModelo = {
  versao: number;
  pavimentos: Pavimento[];
  eixos: Eixo[];
  elementos: Elemento[];
};
