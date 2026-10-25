"use client";
import Link from "next/link";
import { Archive, CheckCircle2, ClipboardList, HardHat, MapPin, PauseCircle, Plus } from "lucide-react";
import { useDados } from "@/lib/dados";
import { useSessao } from "@/lib/sessao";
import { ESTADOS_OBRA, rotuloPapel, type ObraResumo } from "@/lib/tipos";
import { Aviso, Botao, CabecalhoPagina, Carregando, Etiqueta, Vazio } from "@/components/ui";
import { IconeObras } from "@/components/marca/IconesMarca";

const ICONES = { ClipboardList, HardHat, PauseCircle, CheckCircle2, Archive };

export function EtiquetaObra({ estado }: { estado: ObraResumo["estado"] }) {
  const e = ESTADOS_OBRA[estado]; const I = ICONES[e.icone as keyof typeof ICONES] ?? ClipboardList;
  return <span className="inline-flex items-center gap-1 rounded-full border px-2.5 py-0.5 text-xs font-medium" style={{ color: e.cor, borderColor: "var(--line-soft)" }}><I className="size-3.5" aria-hidden />{e.rotulo}</span>;
}

/** RF22 · Obras da empresa ativa visíveis à pessoa (RLS + papel). */
export default function PaginaObras() {
  const { sessao } = useSessao();
  const semEmpresa = !sessao?.empresaAtivaId;
  const q = useDados<ObraResumo[]>(["obras"], "/obras", { ativo: !semEmpresa });
  return (
    <>
      <CabecalhoPagina titulo="Obras" descricao="Cada obra tem modelo BIM, estrutura, fundações, quantitativos, orçamento e pranchas próprios." acoes={!semEmpresa && <Link href="/obras/nova"><Botao icone={<Plus className="size-4" aria-hidden />}>Nova obra</Botao></Link>} />
      {semEmpresa ? (
        <Aviso tom="info" titulo="Você ainda não tem uma empresa ativa">Crie a sua empresa (ou aceite um convite) para começar. <Link href="/empresa" className="underline font-medium">Ir para Empresa</Link></Aviso>
      ) : q.isLoading ? <Carregando /> : !q.data?.length ? (
        <Vazio icone={<IconeObras tamanho={40} />} titulo="Nenhuma obra ainda" descricao="Crie a primeira obra da empresa para abrir o motor de desenho." acao={<Link href="/obras/nova"><Botao>Criar obra</Botao></Link>} />
      ) : (
        <ul className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
          {q.data.map((o) => (
            <li key={o.id}>
              <Link href={`/obras/${o.id}`} className="block h-full rounded-[var(--radius-md)] border border-line-soft bg-bg p-4 hover:border-line focus-visible:outline-2">
                <div className="flex items-start justify-between gap-2"><h2 className="font-semibold text-text">{o.nome}</h2><EtiquetaObra estado={o.estado} /></div>
                <p className="mt-1 flex items-center gap-1 text-sm text-text-3"><MapPin className="size-3.5" aria-hidden />{[o.endereco, o.municipio, o.uf].filter(Boolean).join(" · ") || "Sem endereço"}</p>
                <div className="mt-3 flex flex-wrap gap-1">{o.meusPapeis.map((p) => <Etiqueta key={p} tom="marca">{rotuloPapel(p)}</Etiqueta>)}{o.artRrt && <Etiqueta tom="sucesso">ART {o.artRrt}</Etiqueta>}</div>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </>
  );
}
