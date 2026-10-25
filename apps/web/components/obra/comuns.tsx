"use client";
import Link from "next/link";
import { Sparkles } from "lucide-react";
import { useDados, nomeEstadoJob } from "@/lib/dados";
import type { ObraResumo, Pavimento, VisaoModelo } from "@/lib/tipos";
import { Etiqueta } from "@/components/ui";

export function useObra(obraId: string) { return useDados<ObraResumo>(["obra", obraId], `/obras/${obraId}`); }
export function usePavimentos(obraId: string): Pavimento[] { const m = useDados<VisaoModelo>(["modelo", obraId], `/obras/${obraId}/modelo`); return [...(m.data?.pavimentos ?? [])].sort((a, b) => a.elevacao - b.elevacao); }

export function CabecalhoObra({ obraId, secao, grupo, descricao, acoes }: { obraId: string; secao: string; grupo?: string; descricao?: string; acoes?: React.ReactNode }) {
  const obra = useObra(obraId);
  return (
    <header className="flex flex-col gap-3 mb-4">
      <nav aria-label="Trilha" className="text-xs text-text-3"><Link href="/obras" className="underline">Obras</Link> › <Link href={`/obras/${obraId}`} className="underline">{obra.data?.nome ?? "…"}</Link> › {grupo && <>{grupo} › </>}{secao}</nav>
      <div className="flex items-start justify-between gap-3 flex-wrap"><div><h1 className="text-2xl font-semibold">{secao}</h1>{descricao && <p className="text-sm text-text-3 mt-1 max-w-prose">{descricao}</p>}</div>{acoes && <div className="flex gap-2 flex-wrap">{acoes}</div>}</div>
    </header>
  );
}

export function EstadoEtiqueta({ estado }: { estado?: string }) { const e = nomeEstadoJob[estado ?? ""]; return <Etiqueta tom={e?.tom ?? "neutro"}>{e?.rotulo ?? estado ?? "—"}</Etiqueta>; }
export function EtiquetaIa() { return <Etiqueta tom="ia" icone={<Sparkles className="size-3" aria-hidden />}>gerado por IA</Etiqueta>; }
