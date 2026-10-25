/**
 * Mapa único de navegação: grupos com título em maiúsculas e itens sempre visíveis na barra lateral.
 * Toda sub-aba é uma rota própria (nada fica escondido atrás de abas internas da página).
 * A busca da barra superior também lê daqui.
 */
import type { LucideIcon } from "lucide-react";
import {
  Activity, BadgeCheck, Bot, Boxes, BrainCircuit, Building2, Calculator, ClipboardList, Cpu, Database, Download, FileBox, FileClock,
  FileSearch, FileText, FileUp, Fingerprint, FolderKanban, Gauge, HardHat, Inbox, KeyRound, Layers3, LayoutDashboard, ListChecks,
  MousePointerClick, PencilRuler, PlusCircle, Receipt, Ruler, ScrollText, ShieldCheck, Sparkles, SquareStack, Trash2, UserCog, UserRound, Users, Wand2, Weight,
} from "lucide-react";

export type ItemNav = { href: string; rotulo: string; Icone: LucideIcon; descricao?: string; palavras?: string; exato?: boolean };
export type GrupoNav = { id: string; titulo: string; itens: ItemNav[] };

/** `gestorEmpresa: false` esconde o que só DONO/ADMIN da empresa usa (o backend responderia 403). */
export type ContextoNav = { suportePlataforma?: boolean; gestorEmpresa?: boolean };

const SO_GESTOR = new Set(["/obras/nova", "/empresa/membros", "/empresa/privacidade", "/empresa/consumo-ia"]);

export function gruposGlobais(ctx: ContextoNav = {}): GrupoNav[] {
  const g: GrupoNav[] = [
    { id: "obras", titulo: "Obras", itens: [
      { href: "/obras", rotulo: "Todas as obras", Icone: FolderKanban, exato: true, descricao: "Lista, filtros e estado de cada obra" },
      { href: "/obras/nova", rotulo: "Nova obra", Icone: PlusCircle, descricao: "Cadastrar obra com térreo e eixos" },
      { href: "/motor", rotulo: "Área de treino", Icone: PencilRuler, descricao: "Motor 2D/3D sem gravar em obra", palavras: "motor desenho cad treino" },
    ] },
    { id: "empresa", titulo: "Empresa", itens: [
      { href: "/empresa/dados", rotulo: "Dados da empresa", Icone: Building2, palavras: "cnpj razão social tenant" },
      { href: "/empresa/membros", rotulo: "Membros e convites", Icone: Users, palavras: "convidar papel" },
      { href: "/empresa/privacidade", rotulo: "Privacidade (controladora)", Icone: ShieldCheck, palavras: "lgpd inventário titulares" },
      { href: "/empresa/consumo-ia", rotulo: "Consumo de IA", Icone: Gauge, palavras: "tokens custo" },
    ] },
    { id: "conta", titulo: "Minha conta", itens: [
      { href: "/conta", rotulo: "Perfil e selo", Icone: UserRound, exato: true, palavras: "crea cau foto preferências" },
      { href: "/conta/seguranca", rotulo: "Segurança e sessões", Icone: KeyRound, palavras: "2fa senha totp" },
      { href: "/ia", rotulo: "Transparência da IA", Icone: BrainCircuit, palavras: "uso de ia rotas modelo" },
    ] },
    { id: "dados", titulo: "Seus dados", itens: [
      { href: "/conta/privacidade/consentimentos", rotulo: "Consentimentos", Icone: ListChecks },
      { href: "/conta/privacidade/meus-dados", rotulo: "Meus dados", Icone: Fingerprint, palavras: "corrigir compartilhamento" },
      { href: "/conta/privacidade/exportar", rotulo: "Exportar", Icone: Download, palavras: "portabilidade zip" },
      { href: "/conta/privacidade/pedidos", rotulo: "Pedidos ao encarregado", Icone: Inbox },
      { href: "/conta/privacidade/excluir-conta", rotulo: "Excluir conta", Icone: Trash2 },
    ] },
  ];
  if (ctx.suportePlataforma) g.push({ id: "plataforma", titulo: "Plataforma", itens: [{ href: "/plataforma/selos", rotulo: "Selos profissionais", Icone: BadgeCheck }] });
  if (ctx.gestorEmpresa === false) for (const grupo of g) grupo.itens = grupo.itens.filter((i) => !SO_GESTOR.has(i.href));
  return g;
}

/** Sub-abas de cada aba da obra; `ESTRUTURA_ABAS` etc. também validam o segmento [aba] nas páginas. */
export const ESTRUTURA_ABAS = ["cargas", "lancamento-ia", "vaos", "restricoes"] as const;
export const ANALISE_ABAS = ["mef", "dimensionamento"] as const;
export const GEOTECNIA_ABAS = ["furos", "boletim", "perfil", "fundacoes"] as const;
export const EMPRESA_ABAS = ["dados", "membros", "privacidade", "consumo-ia"] as const;
export const PRIVACIDADE_ABAS = ["consentimentos", "meus-dados", "exportar", "pedidos", "excluir-conta"] as const;

export const TITULOS_ABAS: Record<string, string> = {
  cargas: "Cargas e combinações", "lancamento-ia": "Lançamento por IA", vaos: "Auditoria de vãos", restricoes: "Tolerâncias e seções mínimas",
  mef: "Análise MEF", dimensionamento: "Dimensionamento",
  furos: "Furos SPT", boletim: "Boletim por IA", perfil: "Perfil do solo", fundacoes: "Fundações",
  dados: "Dados da empresa", membros: "Membros e convites", privacidade: "Privacidade (controladora)", "consumo-ia": "Consumo de IA",
  consentimentos: "Consentimentos", "meus-dados": "Meus dados", exportar: "Exportar meus dados", pedidos: "Pedidos ao encarregado", "excluir-conta": "Excluir conta",
};

export function gruposObra(obraId: string): GrupoNav[] {
  const b = `/obras/${obraId}`;
  return [
    { id: "obra", titulo: "Obra", itens: [
      { href: b, rotulo: "Painel", Icone: LayoutDashboard, exato: true, descricao: "Resumo, estado e próximos passos" },
      { href: `${b}/membros`, rotulo: "Equipe da obra", Icone: UserCog, palavras: "membros papéis" },
    ] },
    { id: "projeto", titulo: "Projeto", itens: [
      { href: `${b}/modelo`, rotulo: "Modelo BIM", Icone: HardHat, descricao: "Motor 2D/3D com comandos e Notação VI", palavras: "motor desenho cad revit autocad" },
    ] },
    { id: "assistente", titulo: "Assistente IA", itens: [
      { href: `${b}/copilot`, rotulo: "Copilot", Icone: Sparkles, descricao: "Sugere a próxima ação; você confirma cada passo", palavras: "ia sugestão próxima ação" },
      { href: `${b}/autopilot`, rotulo: "Autopilot", Icone: Bot, descricao: "Executa um roteiro de ações e para em cada aprovação", palavras: "ia automático roteiro" },
      { href: `${b}/ia`, rotulo: "Rascunhos de IA", Icone: FileClock, descricao: "Tudo que a IA gerou aguardando decisão", palavras: "aprovar rejeitar" },
    ] },
    { id: "acoes", titulo: "Ações e códigos", itens: [
      { href: `${b}/acoes`, rotulo: "Ações construtivas", Icone: MousePointerClick, descricao: "Escolha a ação, preencha o formulário, veja a prévia", palavras: "parede pilar viga laje porta janela malha sapata" },
      { href: `${b}/codigos`, rotulo: "Códigos construtivos", Icone: ScrollText, descricao: "Catálogo de comandos, aliases e Notação VI sem digitar", palavras: "comando alias vi notação catálogo" },
    ] },
    { id: "estrutura", titulo: "Estrutura", itens: [
      { href: `${b}/estrutura/cargas`, rotulo: "Cargas e combinações", Icone: Weight, palavras: "nbr 6120 6123 8681 vento" },
      { href: `${b}/estrutura/lancamento-ia`, rotulo: "Lançamento por IA", Icone: Wand2, palavras: "pilares vigas lajes automático" },
      { href: `${b}/estrutura/vaos`, rotulo: "Auditoria de vãos", Icone: Ruler, palavras: "caixa cartesiana poste n+1" },
      { href: `${b}/estrutura/restricoes`, rotulo: "Tolerâncias e seções", Icone: SquareStack, palavras: "nbr 6118 mínimos exceção" },
      { href: `${b}/analises/mef`, rotulo: "Análise MEF", Icone: Activity, palavras: "pórtico diagramas esforços" },
      { href: `${b}/analises/dimensionamento`, rotulo: "Dimensionamento", Icone: Calculator, palavras: "armadura elu els art" },
    ] },
    { id: "geotecnia", titulo: "Geotecnia", itens: [
      { href: `${b}/geotecnia/furos`, rotulo: "Furos SPT", Icone: Layers3, palavras: "sondagem nspt nbr 6484" },
      { href: `${b}/geotecnia/boletim`, rotulo: "Boletim por IA", Icone: FileUp, palavras: "pdf extração" },
      { href: `${b}/geotecnia/perfil`, rotulo: "Perfil do solo", Icone: FileSearch, palavras: "estratigráfico interpolado" },
      { href: `${b}/geotecnia/fundacoes`, rotulo: "Fundações", Icone: Boxes, palavras: "sapata bloco estaca radier nbr 6122" },
    ] },
    { id: "custos", titulo: "Custos", itens: [
      { href: `${b}/quantitativos`, rotulo: "Quantitativos", Icone: ClipboardList, palavras: "concreto forma aço" },
      { href: `${b}/orcamento`, rotulo: "Orçamento SINAPI", Icone: Receipt, palavras: "custo aditivo" },
    ] },
    { id: "entregaveis", titulo: "Entregáveis", itens: [
      { href: `${b}/pranchas`, rotulo: "Pranchas", Icone: FileText, palavras: "pdf dwg carimbo" },
      { href: `${b}/interop`, rotulo: "IFC / Revit", Icone: FileBox, palavras: "importar exportar bim" },
    ] },
  ];
}

/** Atalhos para o resto do sistema quando a lateral está no contexto da obra. */
export function gruposGeraisCompactos(): GrupoNav {
  return { id: "geral", titulo: "Geral", itens: [
    { href: "/obras", rotulo: "Todas as obras", Icone: FolderKanban, exato: true },
    { href: "/empresa/dados", rotulo: "Empresa", Icone: Building2 },
    { href: "/conta", rotulo: "Minha conta", Icone: UserRound, exato: true },
    { href: "/conta/privacidade/consentimentos", rotulo: "Seus dados", Icone: Database },
    { href: "/motor", rotulo: "Área de treino", Icone: Cpu },
  ] };
}

export function itemAtivo(i: ItemNav, caminho: string): boolean {
  if (i.exato) return caminho === i.href;
  return caminho === i.href || caminho.startsWith(i.href + "/");
}

/** obraId quando a rota atual está dentro de /obras/[obraId] (não "nova"). */
export function obraDoCaminho(caminho: string): string | null {
  const m = /^\/obras\/([^/]+)/.exec(caminho);
  return m && m[1] && m[1] !== "nova" ? m[1] : null;
}

const semAcento = (s: string) => s.normalize("NFD").replace(/\p{M}/gu, "").toLowerCase();

/** Busca da barra superior: casa por rótulo, grupo, descrição e palavras-chave, sem acento. */
export function buscarNavegacao(grupos: GrupoNav[], termo: string, limite = 8): (ItemNav & { grupo: string })[] {
  const t = semAcento(termo.trim());
  if (!t) return [];
  const partes = t.split(/\s+/);
  const out: (ItemNav & { grupo: string; nota: number })[] = [];
  for (const g of grupos) for (const i of g.itens) {
    const rot = semAcento(i.rotulo);
    const tudo = `${rot} ${semAcento(g.titulo)} ${semAcento(i.descricao ?? "")} ${semAcento(i.palavras ?? "")}`;
    if (!partes.every((p) => tudo.includes(p))) continue;
    out.push({ ...i, grupo: g.titulo, nota: rot.startsWith(t) ? 0 : rot.includes(t) ? 1 : 2 });
  }
  return out.sort((a, b) => a.nota - b.nota).slice(0, limite);
}
