/** Tipos espelhados dos records do backend (cca-web / cca-application). */
export type UUID = string;

export type PapelObra = "DONO" | "ADMIN" | "ENGENHEIRO" | "ARQUITETO" | "ORCAMENTISTA" | "MESTRE_OBRAS" | "TECNICO_SEGURANCA" | "CLIENTE";
export type EstadoObra = "PLANEJADA" | "EM_ANDAMENTO" | "PARALISADA" | "CONCLUIDA" | "ARQUIVADA";
export type EstadoJob = "PENDENTE" | "EM_EXECUCAO" | "CONCLUIDO" | "FALHOU" | "OBSOLETO";
export type EtapaLogin = "MFA" | "CONFIGURAR_MFA" | "OFERTA_REATIVACAO" | "ESCOLHER_EMPRESA" | "AUTENTICADO";
export type TipoConta = "PESSOA" | "EMPRESA";
export type TipoSelo = "CREA" | "CAU" | "CNPJ";
export type EstadoRascunho = "PENDENTE" | "EM_EXECUCAO" | "GERADO_POR_IA" | "CALCULADO" | "EM_REVISAO" | "APLICADO_NO_MODELO" | "APROVADO" | "REJEITADO" | "FALHOU" | "OBSOLETO";

export type Sessao = {
  contaId: UUID;
  empresaAtivaId: string;
  papelEmpresa: string;
  papeisPorObra: Record<UUID, PapelObra[]>;
  mfaVerificado: boolean;
  seloVerificado: boolean;
  sudoAtivo: boolean;
  /** papel exige 2FA e ele ainda não foi configurado: só a própria conta responde até configurar */
  mfaPendente?: boolean;
};

export type RespostaLogin = { proximaEtapa: EtapaLogin; empresas: UUID[] };

export type ObraResumo = {
  id: UUID; nome: string; endereco?: string; municipio?: string; uf?: string; estado: EstadoObra; artRrt?: string;
  responsavelTecnicoId?: UUID; perimetro?: number[][]; localizacao?: number[]; meusPapeis: PapelObra[];
};
export type EmpresaResumo = { id: UUID; razaoSocial: string; nomeFantasia?: string; cnpj: string; cnpjVerificado: boolean; encarregadoContato?: string; meuPapel?: string };
export type Membro = { contaId: UUID; nome: string; papel: PapelObra };
export type ConviteResumo = { id: UUID; obraId?: UUID; email: string; papel: PapelObra; estado: "PENDENTE" | "ACEITO" | "RECUSADO" | "EXPIRADO" | "REVOGADO"; expiraEm: string };
export type ConvitePublico = { empresa: string; obra?: string; papel: PapelObra; expiraEm: string; emailMascarado: string };

export type Perfil = { nome?: string; email?: string; tipo?: TipoConta; estado?: string; registroProfissional?: string; registroUf?: string; cnpj?: string; fotoUrl?: string; mfaAtivo?: boolean; [k: string]: unknown };
export type Preferencias = { idioma?: string; tema?: string; fontePct?: number };
export type SessaoResumo = { apelido: string; criadaEm: string; ultimoAcessoEm: string; userAgent: string; atual: boolean };
export type Selo = { tipo?: TipoSelo; estado: "NONE" | "PENDING" | "VERIFIED" | "REJECTED"; registro?: string; uf?: string; motivo?: string; [k: string]: unknown };

export type Consentimento = { finalidade: string; titulo: string; descricao: string; baseLegal: string; concedido: boolean; versaoTexto?: string; [k: string]: unknown };
export type EventoConsentimento = { finalidade: string; acao: string; versaoTexto: string; em: string; ip?: string; [k: string]: unknown };
export type CategoriaDados = { categoria: string; titulo: string; finalidade: string; baseLegal: string; retencao: string; sensivel: boolean; dados: Record<string, unknown> };
export type Compartilhamento = { tipo: string; nome: string; papel: string; finalidade: string; baseLegal: string; internacional: boolean };
export type ExportacaoTitular = { id: UUID; estado: EstadoJob; urlDownload?: string; senhaZip?: string; tamanhoBytes?: number; expiraEm?: string; erro?: string };
export type PedidoTitular = { id: UUID; tipo: string; estado: string; descricao: string; criadoEm?: string; prazo?: string; resposta?: string; [k: string]: unknown };
export type Consequencias = { dataFinal: string; carenciaDias: number; oQueFica: string[]; oQueSai: string[] };
export type DocumentoLegal = { tipo: "TERMOS" | "POLITICA"; versao: string; url: string };

export type Pavimento = { id: string; nome: string; elevacao: number; tipo: string };
export type Eixo = { id: string; nome: string; direcao: "X" | "Y"; posicao: number; inicio: number; fim: number; recuoBolha: number };
export type VisaoModelo = { obraId: UUID; versao: number; liberadaCliente: boolean; pavimentos: Pavimento[]; eixos: Eixo[]; elementos: Record<string, unknown>[] };
export type ResultadoComando = { comandoId: UUID; vi: string; versao: number; diff: Record<string, unknown>; relatorio: Record<string, unknown> };

export type AlertaTransicao = { id: UUID; obraId: UUID; elementoId: UUID; tipo: string; estado: string; mensagem: string; justificativa?: string; criadoEm: string };
export type RascunhoIa = { id: UUID; tipo?: string; estado: EstadoRascunho; resumo?: string; criadoEm?: string; rota?: string; [k: string]: unknown };

export type Carga = { id: UUID; pavimentoId: string; natureza: "PERMANENTE" | "ACIDENTAL" | "VENTO"; tipo: "AREA" | "LINEAR" | "PONTUAL"; valor: number; elementoAlvoId?: string; descricao?: string; casoCarga?: string };
export type Restricoes = { shortCurveTolerance: number; pilarLadoMin: number; pilarAreaMin: number; vigaBMin: number; lajeHMin: number; paredeEspMin: number };
export type Analise = { id: UUID; estado: EstadoJob | string; versaoModelo?: number; criadaEm?: string; erro?: string; [k: string]: unknown };
export type Dimensionamento = { id: UUID; estado: string; analiseId?: UUID; criadoEm?: string; [k: string]: unknown };
export type Furo = { id: UUID; nome?: string; x?: number; y?: number; cotaBoca?: number; nivelAgua?: number; estado?: string; camadas?: unknown[]; leituras?: unknown[]; [k: string]: unknown };
export type Fundacao = { id: UUID; estado: string; tipo?: string; criadoEm?: string; [k: string]: unknown };
export type Quantitativo = { id?: UUID; versaoModelo?: number; desatualizado?: boolean; totais?: Record<string, number>; linhas?: Record<string, unknown>[]; [k: string]: unknown };
export type Orcamento = { id: UUID; estado: string; total?: number; dataBase?: string; uf?: string; itens?: ItemOrcamento[]; [k: string]: unknown };
export type ItemOrcamento = { id: UUID; etapa: string; servico: string; codigoSinapi: string; unidade: string; quantidade: number; custoUnitario?: number; total?: number; geradoPorIa?: boolean; confirmado?: boolean; [k: string]: unknown };
export type Prancha = { id: UUID; tipo: string; estado: string; escala?: string; pavimentoId?: string; versaoModelo?: number; criadaEm?: string; [k: string]: unknown };
export type Exportacao = { id: UUID; formato: "IFC4X3" | "STEP_AP242"; estado: EstadoJob; versaoModelo: number; erro?: string; criadaEm: string; concluidaEm?: string };
export type Pareamento = { id: UUID; estado: string; dispositivo?: string; expiraEm?: string; criadoEm?: string; pareadoEm?: string };

export const PAPEIS: { valor: PapelObra; rotulo: string }[] = [
  { valor: "DONO", rotulo: "Dono(a)" }, { valor: "ADMIN", rotulo: "Admin" }, { valor: "ENGENHEIRO", rotulo: "Engenheiro(a)" },
  { valor: "ARQUITETO", rotulo: "Arquiteto(a)" }, { valor: "ORCAMENTISTA", rotulo: "Orçamentista" }, { valor: "MESTRE_OBRAS", rotulo: "Mestre de obras" },
  { valor: "TECNICO_SEGURANCA", rotulo: "Técnico(a) de segurança" }, { valor: "CLIENTE", rotulo: "Cliente" },
];
export const rotuloPapel = (p: string) => PAPEIS.find((x) => x.valor === p)?.rotulo ?? p;

export const ESTADOS_OBRA: Record<EstadoObra, { rotulo: string; cor: string; icone: string }> = {
  PLANEJADA: { rotulo: "Planejada", cor: "var(--obra-planejada)", icone: "ClipboardList" },
  EM_ANDAMENTO: { rotulo: "Em andamento", cor: "var(--obra-andamento)", icone: "HardHat" },
  PARALISADA: { rotulo: "Paralisada", cor: "var(--obra-paralisada)", icone: "PauseCircle" },
  CONCLUIDA: { rotulo: "Concluída", cor: "var(--obra-concluida)", icone: "CheckCircle2" },
  ARQUIVADA: { rotulo: "Arquivada", cor: "var(--text-3)", icone: "Archive" },
};
export const TRANSICOES_OBRA: Record<EstadoObra, EstadoObra[]> = {
  PLANEJADA: ["EM_ANDAMENTO"], EM_ANDAMENTO: ["PARALISADA", "CONCLUIDA"], PARALISADA: ["EM_ANDAMENTO"], CONCLUIDA: ["ARQUIVADA"], ARQUIVADA: [],
};
