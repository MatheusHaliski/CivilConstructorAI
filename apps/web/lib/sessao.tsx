"use client";
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ErroApi, get, post } from "./api";
import type { PapelObra, Sessao } from "./tipos";

type Estado = { carregando: boolean; sessao: Sessao | null; recarregar: () => Promise<Sessao | null>; sair: () => Promise<void> };

const Ctx = createContext<Estado | null>(null);

export function ProvedorApp({ children }: { children: ReactNode }) {
  const [client] = useState(() => new QueryClient({
    defaultOptions: { queries: { retry: (n, e) => !(e instanceof ErroApi && e.status < 500) && n < 2, staleTime: 15_000, refetchOnWindowFocus: false } },
  }));
  return (
    <QueryClientProvider client={client}>
      <ProvedorSessao>{children}</ProvedorSessao>
    </QueryClientProvider>
  );
}

export function ProvedorSessao({ children }: { children: ReactNode }) {
  const [sessao, setSessao] = useState<Sessao | null>(null);
  const [carregando, setCarregando] = useState(true);

  const recarregar = useCallback(async () => {
    try {
      const s = await get<Sessao>("/auth/session");
      setSessao(s);
      return s;
    } catch {
      setSessao(null);
      return null;
    } finally {
      setCarregando(false);
    }
  }, []);

  const sair = useCallback(async () => {
    try { await post("/auth/logout"); } finally { setSessao(null); }
  }, []);

  useEffect(() => { void recarregar(); }, [recarregar]);

  const valor = useMemo(() => ({ carregando, sessao, recarregar, sair }), [carregando, sessao, recarregar, sair]);
  return <Ctx.Provider value={valor}>{children}</Ctx.Provider>;
}

export function useSessao() {
  const v = useContext(Ctx);
  if (!v) throw new Error("useSessao fora do ProvedorSessao");
  return v;
}

/** Papéis da pessoa numa obra (camada 2 do RF21 espelhada no front: só esconde, nunca autoriza). */
export function usePapeis(obraId?: string): PapelObra[] {
  const { sessao } = useSessao();
  if (!sessao || !obraId) return [];
  // mesma regra do backend (UsuarioAtual.papeisNaObra): o papel na empresa vale em todas as obras dela
  const papeis = new Set<PapelObra>(sessao.papeisPorObra[obraId] ?? []);
  if (sessao.papelEmpresa) papeis.add(sessao.papelEmpresa as PapelObra);
  return [...papeis];
}
export const editaModelo = (p: PapelObra[]) => p.some((x) => ["DONO", "ADMIN", "ENGENHEIRO", "ARQUITETO"].includes(x));
export const aprova = (p: PapelObra[]) => p.some((x) => ["DONO", "ADMIN", "ENGENHEIRO"].includes(x));
export const ehCliente = (p: PapelObra[]) => p.length > 0 && p.every((x) => x === "CLIENTE");
