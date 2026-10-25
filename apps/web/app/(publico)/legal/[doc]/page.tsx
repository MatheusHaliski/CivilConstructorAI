"use client";
import { use } from "react";
import { useQuery } from "@tanstack/react-query";
import { get } from "@/lib/api";
import type { DocumentoLegal } from "@/lib/tipos";
import { Cartao, Carregando } from "@/components/ui";

/** RF24 · Termos e Política vigentes, versionados, e o canal do Encarregado (Art. 41). */
export default function PaginaLegal({ params }: { params: Promise<{ doc: string }> }) {
  const { doc } = use(params);
  const tipo = doc.toUpperCase() === "POLITICA" ? "POLITICA" : "TERMOS";
  const docs = useQuery({ queryKey: ["legal-docs"], queryFn: () => get<DocumentoLegal[]>("/legal/documentos") });
  const dpo = useQuery({ queryKey: ["legal-dpo"], queryFn: () => get<Record<string, string>>("/legal/encarregado") });
  const atual = docs.data?.find((d) => d.tipo === tipo);
  return (
    <Cartao titulo={tipo === "TERMOS" ? "Termos de uso" : "Política de privacidade"} descricao={atual ? `Versão ${atual.versao}` : undefined}>
      {docs.isLoading ? <Carregando /> : atual ? (
        <p className="text-sm">Documento vigente: <a className="text-brand-text underline" href={atual.url} target="_blank" rel="noreferrer">abrir versão {atual.versao}</a>.</p>
      ) : <p className="text-sm text-text-3">Documento ainda não publicado.</p>}
      {dpo.data && (
        <div className="text-sm border-t border-line-soft pt-3">
          <p className="font-medium">Encarregado de dados (DPO)</p>
          <p className="text-text-2">{dpo.data["nome"] || "—"} · {dpo.data["canal"] ? <a className="text-brand-text underline" href={`mailto:${dpo.data["canal"]}`}>{dpo.data["canal"]}</a> : "canal ainda não configurado"} · prazo de resposta {dpo.data["prazoRespostaDias"] ?? 15} dias</p>
        </div>
      )}
    </Cartao>
  );
}
