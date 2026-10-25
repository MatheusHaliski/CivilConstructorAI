"use client";
import { use } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { get } from "@/lib/api";
import { editaModelo, usePapeis } from "@/lib/sessao";
import type { ObraResumo } from "@/lib/tipos";
import { Motor } from "@/components/motor/Motor";
import { Etiqueta } from "@/components/ui";

/** RF01/RF02/RF03/RF13/RF28 · Modelo BIM da obra no motor de desenho. */
export default function PaginaModelo({ params }: { params: Promise<{ obraId: string }> }) {
  const { obraId } = use(params);
  const papeis = usePapeis(obraId);
  const q = useSearchParams();
  const cmd = q.get("cmd");
  // ?sel=id1,id2&vista=3D: destaca elementos (ex.: os de um conflito do painel de falhas)
  const sel = q.get("sel")?.split(",").filter(Boolean) ?? null;
  const vista = q.get("vista") === "3D" ? "3D" : null;
  const obra = useQuery({ queryKey: ["obra", obraId], queryFn: () => get<ObraResumo>(`/obras/${obraId}`) });
  const pode = editaModelo(papeis) && obra.data?.estado !== "ARQUIVADA";
  return (
    <div className="flex flex-col gap-3">
      <header className="flex items-center justify-between gap-3 flex-wrap">
        <div><nav aria-label="Trilha" className="text-xs text-text-3"><Link href="/obras" className="underline">Obras</Link> › <Link href={`/obras/${obraId}`} className="underline">{obra.data?.nome ?? "…"}</Link> › Projeto › Modelo BIM</nav><h1 className="text-xl font-semibold">Modelo BIM</h1></div>
        <div className="flex gap-2">{!pode && <Etiqueta tom="aviso">somente leitura</Etiqueta>}<Etiqueta tom="marca">Notação VI</Etiqueta></div>
      </header>
      <Motor obraId={obraId} podeEditar={pode} comandoInicial={cmd} selecaoInicial={sel} vistaInicial={vista} />
    </div>
  );
}
