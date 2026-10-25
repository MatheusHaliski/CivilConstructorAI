"use client";
import { use, useState } from "react";
import { Download, FileText, Stamp } from "lucide-react";
import { get, post } from "@/lib/api";
import { useAcao, useDados } from "@/lib/dados";
import { aprova, editaModelo, usePapeis } from "@/lib/sessao";
import type { Prancha } from "@/lib/tipos";
import { data } from "@/lib/formatos";
import { Botao, Cartao, Carregando, Entrada, Etiqueta, Selecao, Tabela, Vazio } from "@/components/ui";
import { CabecalhoObra, EstadoEtiqueta, usePavimentos } from "@/components/obra/comuns";

const TIPOS = [{ valor: "PLANTA_BAIXA", rotulo: "Planta baixa" }, { valor: "PLANTA_FORMAS", rotulo: "Planta de formas" }, { valor: "CORTE", rotulo: "Corte" }, { valor: "ELEVACAO", rotulo: "Elevação" }];

/** RF14 · Pranchas (planta baixa/formas, cortes, elevações) com bolhas recuadas, carimbo com RT + ART; job assíncrono no S3. */
export default function PaginaPranchas({ params }: { params: Promise<{ obraId: string }> }) {
  const { obraId } = use(params);
  const papeis = usePapeis(obraId); const edita = editaModelo(papeis); const eng = aprova(papeis);
  const pav = usePavimentos(obraId);
  const base = `/obras/${obraId}/pranchas`;
  const lista = useDados<Prancha[]>(["pranchas", obraId], base, { intervalo: 5000 });
  const [f, setF] = useState({ tipo: "PLANTA_BAIXA", pavimentoId: "", escala: "1:50" });
  const [art, setArt] = useState("");
  const solicitar = useAcao((v: typeof f) => post(base, { tipo: v.tipo, pavimentoId: v.pavimentoId || null, escala: v.escala }), { invalidar: [["pranchas", obraId]], sucesso: "Prancha solicitada (geração assíncrona)." });
  const processar = useAcao(() => post(`${base}/processar`), { invalidar: [["pranchas", obraId]], sucesso: "Fila processada." });
  const emitir = useAcao((id: string) => post(`${base}/${id}/emitir`, { artRrt: art }), { invalidar: [["pranchas", obraId]], sucesso: "Prancha emitida (sem marca d'água)." });
  const baixar = useAcao(async (id: string) => { const r = await get<{ url: string }>(`${base}/${id}/download`); window.open(r.url, "_blank", "noopener"); }, { erro: true });
  return (
    <>
      <CabecalhoObra obraId={obraId} secao="Pranchas" descricao="Formato ABNT (NBR 16752), escala padrão 1:50/1:100, marca d'água 'rascunho' até a emissão com ART/RRT." acoes={eng && <Botao variante="secundario" pequeno carregando={processar.isPending} onClick={() => processar.mutate()}>Processar fila</Botao>} />
      <div className="grid gap-4 lg:grid-cols-3">
        <Cartao titulo="Pranchas da obra" className="lg:col-span-2">
          {lista.isLoading ? <Carregando /> : <Tabela linhas={lista.data ?? []} chave={(p) => p.id} vazio={<Vazio icone={<FileText className="size-8" aria-hidden />} titulo="Nenhuma prancha" descricao="Solicite a primeira: planta baixa do térreo em 1:50." />} colunas={[
            { titulo: "Tipo", celula: (p) => TIPOS.find((t) => t.valor === p.tipo)?.rotulo ?? p.tipo }, { titulo: "Pavimento", celula: (p) => pav.find((x) => x.id === p.pavimentoId)?.nome ?? "—" }, { titulo: "Escala", celula: (p) => p.escala ?? "—" },
            { titulo: "Modelo", celula: (p) => `v${p.versaoModelo ?? "—"}` }, { titulo: "Estado", celula: (p) => <span className="flex gap-1"><EstadoEtiqueta estado={p.estado} />{p.estado === "GERADA" && <Etiqueta tom="aviso">rascunho</Etiqueta>}</span> }, { titulo: "Quando", celula: (p) => data(p.criadaEm ?? (p["solicitadaEm"] as string)) },
            { titulo: "", celula: (p) => <div className="flex gap-1">{(p.estado === "GERADA" || p.estado === "EMITIDA") && <Botao pequeno variante="secundario" icone={<Download className="size-4" aria-hidden />} onClick={() => baixar.mutate(p.id)}>PDF</Botao>}{eng && p.estado === "GERADA" && <Botao pequeno icone={<Stamp className="size-4" aria-hidden />} disabled={!art} onClick={() => emitir.mutate(p.id)}>Emitir</Botao>}</div> },
          ]} />}
          {eng && <Entrada rotulo="ART/RRT para emissão" className="mono" value={art} onChange={(e) => setArt(e.target.value)} ajuda="Emitir remove a marca d'água e grava o carimbo com o responsável técnico." />}
        </Cartao>
        <Cartao titulo="Solicitar prancha">
          <Selecao rotulo="Tipo" value={f.tipo} onChange={(e) => setF((v) => ({ ...v, tipo: e.target.value }))} opcoes={TIPOS} />
          <Selecao rotulo="Pavimento" value={f.pavimentoId} onChange={(e) => setF((v) => ({ ...v, pavimentoId: e.target.value }))} vazio="Todos / não se aplica" opcoes={pav.map((p) => ({ valor: p.id, rotulo: p.nome }))} />
          <Selecao rotulo="Escala" value={f.escala} onChange={(e) => setF((v) => ({ ...v, escala: e.target.value }))} opcoes={["1:25", "1:50", "1:75", "1:100", "1:200"].map((x) => ({ valor: x, rotulo: x }))} />
          <Botao pequeno disabled={!edita} carregando={solicitar.isPending} onClick={() => solicitar.mutate(f)}>Gerar prancha</Botao>
          <p className="text-xs text-text-3">Cotas e bolhas não sobrepõem geometria; a prancha fica ligada à versão do modelo.</p>
        </Cartao>
      </div>
    </>
  );
}
