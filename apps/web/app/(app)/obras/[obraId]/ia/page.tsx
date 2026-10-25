"use client";
import { use, useState } from "react";
import { MessageSquareWarning, Sparkles } from "lucide-react";
import { post } from "@/lib/api";
import { useAcao, useDados } from "@/lib/dados";
import { aprova, usePapeis, useSessao } from "@/lib/sessao";
import { data } from "@/lib/formatos";
import { Aviso, Botao, Cartao, Carregando, Entrada, Etiqueta, Selecao, Tabela, Vazio } from "@/components/ui";
import { CabecalhoObra, EstadoEtiqueta, EtiquetaIa } from "@/components/obra/comuns";

type Rascunho = { id: string; tipo?: string; rota?: string; entidadeRef?: string; conteudo?: string; estado: string; criadoPor?: string; aprovadoPor?: string; aprovadoEm?: string; artRrt?: string; motivo?: string; criadoEm?: string; [k: string]: unknown };
type Revisao = { id: string; motivo: string; estado: string; prazoEm?: string; resposta?: string; respondidoEm?: string };

/** RF27 · Todo resultado de IA nasce como rascunho; só vale após aprovação de profissional habilitado (selo + 2FA + ART). RF16 loop duplo registrado por chamada. */
export default function PaginaRascunhosIa({ params }: { params: Promise<{ obraId: string }> }) {
  const { obraId } = use(params);
  const { sessao } = useSessao();
  const eng = aprova(usePapeis(obraId));
  const base = `/obras/${obraId}/rascunhos-ia`;
  const [estado, setEstado] = useState("");
  const lista = useDados<Rascunho[]>(["rascunhos-ia", obraId], base, { query: { estado }, intervalo: 10000 });
  const [sel, setSel] = useState<string | null>(null);
  const revisoes = useDados<Revisao[]>(["revisoes", obraId, sel], `${base}/${sel}/revisao-humana`, { ativo: !!sel });
  const [art, setArt] = useState(""); const [motivo, setMotivo] = useState(""); const [resposta, setResposta] = useState("");
  const inv = [["rascunhos-ia", obraId], ["revisoes", obraId], ["modelo", obraId]];
  const acao = useAcao((v: { id: string; acao: "revisao" | "aprovar" | "rejeitar" | "revisao-humana"; corpo?: unknown }) => post(`${base}/${v.id}/${v.acao}`, v.corpo), { invalidar: inv, sucesso: "Registrado." });
  const responder = useAcao((v: { id: string; pedidoId: string; resposta: string }) => post(`${base}/${v.id}/revisao-humana/${v.pedidoId}/responder`, { resposta: v.resposta }), { invalidar: inv, sucesso: "Revisão respondida." });
  const r = lista.data?.find((x) => x.id === sel);
  return (
    <>
      <CabecalhoObra obraId={obraId} secao="Rascunhos de IA" descricao="Comandos VI, lançamentos, orçamentos e extrações gerados por IA. Quem aprova assume a responsabilidade técnica (ART/RRT)." acoes={<Selecao rotulo="Estado" value={estado} onChange={(e) => setEstado(e.target.value)} vazio="Todos" opcoes={["GERADO_POR_IA", "EM_REVISAO", "APROVADO", "REJEITADO"].map((x) => ({ valor: x, rotulo: x.replace(/_/g, " ").toLowerCase() }))} />} />
      <div className="grid gap-4 lg:grid-cols-3">
        <Cartao titulo="Rascunhos" className="lg:col-span-2">
          {lista.isLoading ? <Carregando /> : <Tabela linhas={lista.data ?? []} chave={(x) => x.id} vazio={<Vazio icone={<Sparkles className="size-8" aria-hidden />} titulo="Nenhum rascunho" descricao="Use o console de texto natural no Modelo ou a sugestão de lançamento em Estrutura." />} colunas={[
            { titulo: "Tipo / rota", celula: (x) => <span><span className="font-medium">{x.tipo ?? "—"}</span><br /><span className="mono text-xs text-text-3">{x.rota ?? ""}</span></span> }, { titulo: "Estado", celula: (x) => <span className="flex gap-1"><EstadoEtiqueta estado={x.estado} /><EtiquetaIa /></span> },
            { titulo: "Quando", celula: (x) => data(x.criadoEm ?? x.aprovadoEm) }, { titulo: "", celula: (x) => <Botao pequeno variante="secundario" onClick={() => setSel(x.id)}>Abrir</Botao> },
          ]} />}
        </Cartao>
        <Cartao titulo={r ? `Rascunho · ${r.tipo ?? ""}` : "Detalhe"}>
          {!r ? <p className="text-sm text-text-3">Escolha um rascunho.</p> : <>
            <pre className="mono text-xs whitespace-pre-wrap max-h-48 overflow-auto rounded border border-ai-line bg-ai-bg p-2 text-ai-text">{r.conteudo}</pre>
            <dl className="text-xs grid grid-cols-[auto_1fr] gap-x-3 gap-y-1"><dt className="text-text-3">Referência</dt><dd className="mono">{r.entidadeRef ?? "—"}</dd><dt className="text-text-3">Aprovado por</dt><dd>{r.aprovadoPor ? `${r.aprovadoPor} · ART ${r.artRrt ?? "—"} · ${data(r.aprovadoEm)}` : "—"}</dd>{r.motivo && <><dt className="text-text-3">Motivo</dt><dd>{r.motivo}</dd></>}</dl>
            {eng && (r.estado === "GERADO_POR_IA" || r.estado === "EM_REVISAO") && <div className="flex flex-col gap-2 border-t border-line-soft pt-3">
              {r.estado === "GERADO_POR_IA" && <Botao pequeno variante="secundario" onClick={() => acao.mutate({ id: r.id, acao: "revisao" })}>Enviar para revisão</Botao>}
              <Entrada rotulo="ART/RRT" className="mono" value={art} onChange={(e) => setArt(e.target.value)} />
              <Botao pequeno disabled={!art || !sessao?.seloVerificado} title={!sessao?.seloVerificado ? "Exige selo verificado e 2FA" : undefined} onClick={() => acao.mutate({ id: r.id, acao: "aprovar", corpo: { artRrt: art } })}>Aprovar</Botao>
              <Entrada rotulo="Motivo da rejeição" value={motivo} onChange={(e) => setMotivo(e.target.value)} />
              <Botao pequeno variante="perigo" disabled={!motivo} onClick={() => acao.mutate({ id: r.id, acao: "rejeitar", corpo: { motivo } })}>Rejeitar</Botao>
              {!sessao?.seloVerificado && <Aviso tom="aviso">Seu selo profissional não está verificado; você pode revisar, mas não aprovar.</Aviso>}
            </div>}
            <div className="border-t border-line-soft pt-3 flex flex-col gap-2">
              <p className="text-sm font-medium flex items-center gap-1"><MessageSquareWarning className="size-4" aria-hidden />Revisão humana (LGPD Art. 20)</p>
              <ul className="text-xs flex flex-col gap-1">{(revisoes.data ?? []).map((v) => <li key={v.id} className="rounded border border-line-soft p-2"><p>{v.motivo} <EstadoEtiqueta estado={v.estado} /> <span className="text-text-3">prazo {data(v.prazoEm)}</span></p>{v.resposta ? <p className="text-text-2 mt-1">↳ {v.resposta}</p> : eng ? <div className="flex gap-1 mt-1"><input aria-label="Resposta" className="flex-1 rounded border border-line bg-bg px-2 min-h-8" value={resposta} onChange={(e) => setResposta(e.target.value)} /><Botao pequeno disabled={!resposta} onClick={() => responder.mutate({ id: r.id, pedidoId: v.id, resposta })}>Responder</Botao></div> : null}</li>)}</ul>
              <Botao pequeno variante="secundario" onClick={() => { const m = window.prompt("Por que você pede revisão humana desta decisão automatizada?"); if (m) acao.mutate({ id: r.id, acao: "revisao-humana", corpo: { motivo: m } }); }}>Pedir revisão humana</Botao>
            </div>
          </>}
        </Cartao>
      </div>
      <p className="mt-4 text-xs text-text-3 flex items-center gap-1"><Etiqueta tom="ia">gerado por IA</Etiqueta> marca conteúdo sem aprovação; cada chamada ao modelo registra rota, finalidade, base legal, tokens e stop_reason, sem conteúdo pessoal.</p>
    </>
  );
}
