"use client";
/** Atalhos de dados: consultas e mutações com Problem Details → toast, invalidação por prefixo de chave. */
import { useMutation, useQuery, useQueryClient, type QueryKey } from "@tanstack/react-query";
import { api, mensagemDeErro, type Opcoes } from "./api";
import { avisar } from "@/components/ui";

export function useDados<T>(chave: QueryKey, caminho: string, opcoes?: { query?: Opcoes["query"]; ativo?: boolean; intervalo?: number }) {
  return useQuery<T>({ queryKey: [...chave, opcoes?.query ?? {}], queryFn: () => api<T>("GET", caminho, { query: opcoes?.query }), enabled: opcoes?.ativo ?? true, refetchInterval: opcoes?.intervalo });
}

export function useAcao<TVar = void, TRes = unknown>(fn: (v: TVar) => Promise<TRes>, opcoes?: { invalidar?: QueryKey[]; sucesso?: string | ((r: TRes) => string | void); erro?: boolean }) {
  const qc = useQueryClient();
  return useMutation<TRes, Error, TVar>({
    mutationFn: fn,
    onSuccess: (r) => {
      opcoes?.invalidar?.forEach((k) => void qc.invalidateQueries({ queryKey: k }));
      const m = typeof opcoes?.sucesso === "function" ? opcoes.sucesso(r) : opcoes?.sucesso;
      if (m) avisar(m, "sucesso");
    },
    onError: (e) => { if (opcoes?.erro !== false) avisar(mensagemDeErro(e), "perigo"); },
  });
}

export const nomeEstadoJob: Record<string, { rotulo: string; tom: "neutro" | "info" | "sucesso" | "perigo" | "aviso" }> = {
  PENDENTE: { rotulo: "Pendente", tom: "neutro" }, EM_EXECUCAO: { rotulo: "Em execução", tom: "info" }, CONCLUIDO: { rotulo: "Concluído", tom: "sucesso" }, FALHOU: { rotulo: "Falhou", tom: "perigo" }, OBSOLETO: { rotulo: "Resultado desatualizado", tom: "aviso" },
  GERADO_POR_IA: { rotulo: "Gerado por IA", tom: "aviso" }, CALCULADO: { rotulo: "Calculado", tom: "info" }, EM_REVISAO: { rotulo: "Em revisão", tom: "info" }, APLICADO_NO_MODELO: { rotulo: "Aplicado no modelo", tom: "sucesso" }, APROVADO: { rotulo: "Aprovado", tom: "sucesso" }, REJEITADO: { rotulo: "Rejeitado", tom: "perigo" },
  RASCUNHO: { rotulo: "Rascunho", tom: "neutro" }, EM_GERACAO: { rotulo: "Em geração", tom: "info" }, GERADA: { rotulo: "Gerada", tom: "sucesso" }, EMITIDA: { rotulo: "Emitida", tom: "sucesso" }, OBSOLETA: { rotulo: "Desatualizada", tom: "aviso" },
  PENDENTE_CONFIRMACAO: { rotulo: "Aguardando confirmação", tom: "aviso" }, CONFIRMADO: { rotulo: "Confirmado", tom: "sucesso" }, SOLICITADA: { rotulo: "Solicitada", tom: "info" }, APROVADA: { rotulo: "Aprovada", tom: "sucesso" }, REJEITADA: { rotulo: "Rejeitada", tom: "perigo" }, REVOGADA: { rotulo: "Revogada", tom: "neutro" },
  ABERTO: { rotulo: "Aberto", tom: "info" }, PENDENTE_REVISAO: { rotulo: "Pendente de revisão", tom: "aviso" }, EM_ANDAMENTO: { rotulo: "Em andamento", tom: "info" }, RESPONDIDO: { rotulo: "Respondido", tom: "sucesso" }, ATENDIDO: { rotulo: "Atendido", tom: "sucesso" }, NEGADO: { rotulo: "Negado", tom: "perigo" },
};

/** estados padronizados de verificação (espelha Verificacao.Estado): nunca “sem falhas” quando há verificação não executada */
export const nomeVerificacao: Record<string, { rotulo: string; tom: "neutro" | "info" | "sucesso" | "perigo" | "aviso" }> = {
  NAO_VERIFICADA: { rotulo: "Não verificado", tom: "neutro" }, EM_PROCESSAMENTO: { rotulo: "Em processamento", tom: "info" }, ATENDIDA: { rotulo: "Verificação atendida", tom: "sucesso" },
  NAO_ATENDIDA: { rotulo: "Verificação não atendida", tom: "perigo" }, DADOS_INSUFICIENTES: { rotulo: "Dados insuficientes", tom: "aviso" }, DESATUALIZADA: { rotulo: "Resultado desatualizado", tom: "aviso" },
  NAO_APLICAVEL: { rotulo: "Não aplicável", tom: "neutro" },
};
