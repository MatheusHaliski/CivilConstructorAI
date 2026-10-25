"use client";
import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ComponentType } from "react";
import { Sparkles } from "lucide-react";
import { useDados, nomeEstadoJob } from "@/lib/dados";
import type { ObraResumo, Pavimento, VisaoModelo } from "@/lib/tipos";
import { Etiqueta, cx } from "@/components/ui";
import { IconeDimensionamento, IconeEstrutura, IconeGeotecnia, IconeIa, IconeInterop, IconeMembros, IconeModelo, IconeOrcamento, IconePainel, IconePranchas, IconeQuantitativos, type IconeMarcaProps } from "@/components/marca/IconesMarca";

const SECOES: { seg: string; rotulo: string; Icone: ComponentType<IconeMarcaProps>; rf: string }[] = [
  { seg: "", rotulo: "Painel", Icone: IconePainel, rf: "RF22" }, { seg: "modelo", rotulo: "Modelo", Icone: IconeModelo, rf: "RF01" }, { seg: "estrutura", rotulo: "Estrutura", Icone: IconeEstrutura, rf: "RF04" },
  { seg: "analises", rotulo: "Análise e dimensionamento", Icone: IconeDimensionamento, rf: "RF05" }, { seg: "geotecnia", rotulo: "Geotecnia e fundações", Icone: IconeGeotecnia, rf: "RF09" },
  { seg: "quantitativos", rotulo: "Quantitativos", Icone: IconeQuantitativos, rf: "RF11" }, { seg: "orcamento", rotulo: "Orçamento", Icone: IconeOrcamento, rf: "RF12" }, { seg: "pranchas", rotulo: "Pranchas", Icone: IconePranchas, rf: "RF14" },
  { seg: "interop", rotulo: "IFC / Revit", Icone: IconeInterop, rf: "RF15" }, { seg: "ia", rotulo: "Rascunhos de IA", Icone: IconeIa, rf: "RF27" }, { seg: "membros", rotulo: "Membros", Icone: IconeMembros, rf: "RF22" },
];

export function NavObra({ obraId }: { obraId: string }) {
  const caminho = usePathname();
  const base = `/obras/${obraId}`;
  return (
    <nav aria-label="Seções da obra" className="flex gap-1 overflow-x-auto border-b border-line-soft pb-1 -mx-1 px-1">
      {SECOES.map((s) => {
        const href = s.seg ? `${base}/${s.seg}` : base;
        const on = s.seg ? caminho.startsWith(href) : caminho === base;
        return <Link key={s.seg} href={href} aria-current={on ? "page" : undefined} className={cx("tap inline-flex shrink-0 items-center gap-2 rounded-[var(--radius-sm)] px-3 text-sm whitespace-nowrap", on ? "bg-surface text-text font-medium" : "text-text-2 hover:bg-surface")}><s.Icone ativo={on} tamanho={20} /> {s.rotulo}</Link>;
      })}
    </nav>
  );
}

export function useObra(obraId: string) { return useDados<ObraResumo>(["obra", obraId], `/obras/${obraId}`); }
export function usePavimentos(obraId: string): Pavimento[] { const m = useDados<VisaoModelo>(["modelo", obraId], `/obras/${obraId}/modelo`); return [...(m.data?.pavimentos ?? [])].sort((a, b) => a.elevacao - b.elevacao); }

export function CabecalhoObra({ obraId, secao, descricao, acoes }: { obraId: string; secao: string; descricao?: string; acoes?: React.ReactNode }) {
  const obra = useObra(obraId);
  return (
    <header className="flex flex-col gap-3 mb-4">
      <nav className="text-xs text-text-3"><Link href="/obras" className="underline">Obras</Link> › <Link href={`/obras/${obraId}`} className="underline">{obra.data?.nome ?? "…"}</Link> › {secao}</nav>
      <div className="flex items-start justify-between gap-3 flex-wrap"><div><h1 className="text-2xl font-semibold">{secao}</h1>{descricao && <p className="text-sm text-text-3 mt-1 max-w-prose">{descricao}</p>}</div>{acoes && <div className="flex gap-2 flex-wrap">{acoes}</div>}</div>
      <NavObra obraId={obraId} />
    </header>
  );
}

export function EstadoEtiqueta({ estado }: { estado?: string }) { const e = nomeEstadoJob[estado ?? ""]; return <Etiqueta tom={e?.tom ?? "neutro"}>{e?.rotulo ?? estado ?? "—"}</Etiqueta>; }
export function EtiquetaIa() { return <Etiqueta tom="ia" icone={<Sparkles className="size-3" aria-hidden />}>gerado por IA</Etiqueta>; }
