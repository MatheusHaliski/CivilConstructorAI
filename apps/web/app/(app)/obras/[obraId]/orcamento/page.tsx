"use client";
import { use, useState } from "react";
import { Plus, Search, Sparkles } from "lucide-react";
import { del, post } from "@/lib/api";
import { useAcao, useDados } from "@/lib/dados";
import { aprova, usePapeis, useSessao } from "@/lib/sessao";
import type { ItemOrcamento, Orcamento } from "@/lib/tipos";
import { data, moeda, numero } from "@/lib/formatos";
import { Alternar, Aviso, Botao, Caixa, Cartao, Carregando, Entrada, Etiqueta, Selecao, Tabela, Vazio } from "@/components/ui";
import { CabecalhoObra, EstadoEtiqueta, EtiquetaIa } from "@/components/obra/comuns";

type Tabela_ = { id: string; uf: string; mesReferencia: string; desonerado: boolean; composicoes: number };
type Comp = { id: string; codigo: string; descricao: string; unidade: string; custoUnitario: number; grupo?: string };
type Aditivo = { id: string; descricao: string; valor: number; estado: string };
/** espelha a visão de OrcamentoController.visao */
type Visao = { id: string; estado: string; dataBase: string; total: number; bdiPct: number; geradoPorIa: boolean; totaisPorEtapa: Record<string, number>; itens: ItemOrcamento[]; itensPendentesIa: number; tabelaDemonstracao?: boolean; quantitativoDesatualizado?: boolean; versaoModelo?: number };

/** RF12 · Orçamento 5D: quantitativos × SINAPI (UF, mês, desonerado), sugestão por IA validada, aprovação com selo, aditivos. */
export default function PaginaOrcamento({ params }: { params: Promise<{ obraId: string }> }) {
  const { obraId } = use(params);
  const { sessao } = useSessao();
  const eng = aprova(usePapeis(obraId));
  const base = `/obras/${obraId}/orcamentos`;
  const lista = useDados<Orcamento[]>(["orcamentos", obraId], base);
  const tabelas = useDados<Tabela_[]>(["sinapi-tabelas"], "/sinapi/tabelas");
  const [sel, setSel] = useState<string | null>(null);
  const visao = useDados<Visao>(["orcamento", obraId, sel], `${base}/${sel}`, { ativo: !!sel });
  const aditivos = useDados<Aditivo[]>(["aditivos", obraId, sel], `${base}/${sel}/aditivos`, { ativo: !!sel });
  const [g, setG] = useState({ uf: "SP", desonerado: false, usarIa: true });
  const [termo, setTermo] = useState(""); const [tabelaId, setTabelaId] = useState("");
  const comps = useDados<Comp[]>(["sinapi", tabelaId, termo], "/sinapi/composicoes", { query: { tabelaId, termo }, ativo: !!tabelaId && termo.length >= 2 });
  const [novo, setNovo] = useState({ etapa: "ESTRUTURA", servico: "", codigoSinapi: "", unidade: "m3", quantidade: "" });
  const [adit, setAdit] = useState({ descricao: "", valor: "" });
  const inv = [["orcamentos", obraId], ["orcamento", obraId], ["aditivos", obraId]];
  const gerar = useAcao((v: typeof g) => post<Orcamento>(base, v), { invalidar: inv, sucesso: "Orçamento gerado como rascunho." });
  const addItem = useAcao((v: typeof novo) => post(`${base}/${sel}/itens`, { ...v, elementoTipo: null, quantidade: Number(v.quantidade.replace(",", ".")) }), { invalidar: inv, sucesso: "Item adicionado." });
  const confirmar = useAcao((itemId: string) => post(`${base}/${sel}/itens/${itemId}/confirmar`), { invalidar: inv, sucesso: "Vínculo SINAPI confirmado." });
  const remover = useAcao((itemId: string) => del(`${base}/${sel}/itens/${itemId}`), { invalidar: inv });
  const decidir = useAcao((acao: "aprovar" | "rejeitar") => post(`${base}/${sel}/${acao}`), { invalidar: inv, sucesso: "Decisão registrada." });
  const liberar = useAcao((v: { totais: boolean; detalhe: boolean }) => post(`${base}/${sel}/liberar-cliente`, v), { invalidar: inv, sucesso: "Visibilidade para o cliente atualizada." });
  const propor = useAcao((v: typeof adit) => post(`${base}/${sel}/aditivos`, { descricao: v.descricao, valor: Number(v.valor.replace(",", ".")) }), { invalidar: inv, sucesso: "Aditivo proposto ao cliente." });
  const decidirAdit = useAcao((v: { id: string; aprovar: boolean }) => post(`${base}/${sel}/aditivos/${v.id}/decidir`, undefined, { aprovar: v.aprovar }), { invalidar: inv });
  const o = lista.data?.find((x) => x.id === sel); const itens = visao.data?.itens ?? [];
  const papeis = usePapeis(obraId); const cliente = papeis.length > 0 && papeis.every((p) => p === "CLIENTE");

  return (
    <>
      <CabecalhoObra obraId={obraId} secao="Orçamento" descricao="Cada quantitativo vira item com código SINAPI, unidade, quantidade, custo unitário e data-base; o orçamento guarda a versão da tabela." />
      <div className="grid gap-4 lg:grid-cols-3">
        <Cartao titulo="Orçamentos" className={sel ? "lg:col-span-1" : "lg:col-span-2"} acoes={eng && !tabelas.data?.length ? <Etiqueta tom="aviso">nenhuma tabela SINAPI importada</Etiqueta> : null}>
          {lista.isLoading ? <Carregando /> : <ul className="flex flex-col gap-2">{(lista.data ?? []).map((x) => <li key={x.id}><button type="button" onClick={() => setSel(x.id)} aria-pressed={sel === x.id} className={`tap w-full text-left rounded-[var(--radius-sm)] border px-3 ${sel === x.id ? "border-brand bg-brand-soft/40" : "border-line-soft hover:bg-surface"}`}><span className="flex items-center justify-between gap-2"><span className="text-sm font-medium">{moeda(x.total)} <span className="text-xs text-text-3">· data-base {x.dataBase ?? "—"} · BDI {numero(x["bdiPct"] as number, 1)}%</span></span><EstadoEtiqueta estado={x.estado} /></span><span className="text-xs text-text-3">modelo v{String(x["versaoModelo"] ?? "—")} · {String(x["itensGeradosPorIa"] ?? 0)} itens por IA · {data(x["criadoEm"] as string)}</span></button></li>)}{!lista.data?.length && <li><Vazio titulo="Nenhum orçamento" descricao="Gere a partir do quantitativo atual." /></li>}</ul>}
        </Cartao>
        {eng && !sel && <Cartao titulo="Gerar orçamento">
          <Entrada rotulo="UF (tabela SINAPI)" maxLength={2} value={g.uf} onChange={(e) => setG((v) => ({ ...v, uf: e.target.value.toUpperCase() }))} className="mono" />
          <Caixa rotulo="Desonerado" checked={g.desonerado} onChange={(e) => setG((v) => ({ ...v, desonerado: e.target.checked }))} />
          <Caixa rotulo="Sugerir vínculos por IA" descricao="Consulta a tabela SINAPI interna (tool use); cada sugestão fica marcada e precisa de confirmação." checked={g.usarIa} onChange={(e) => setG((v) => ({ ...v, usarIa: e.target.checked }))} />
          <Botao pequeno icone={<Sparkles className="size-4" aria-hidden />} carregando={gerar.isPending} onClick={() => gerar.mutate(g, { onSuccess: (r) => setSel(r.id) })}>Gerar</Botao>
        </Cartao>}
        {sel && o && <>
          <Cartao titulo={`Itens · ${moeda(visao.data?.total ?? o.total)}`} descricao={visao.data ? Object.entries(visao.data.totaisPorEtapa).map(([e, v]) => `${e}: ${moeda(v)}`).join(" · ") : undefined} className="lg:col-span-2" acoes={<>{eng && o.estado !== "APROVADO" && o.estado !== "REJEITADO" && <><Botao pequeno disabled={!sessao?.seloVerificado} title={!sessao?.seloVerificado ? "Exige selo verificado e 2FA" : undefined} onClick={() => decidir.mutate("aprovar")}>Aprovar</Botao><Botao pequeno variante="fantasma" onClick={() => decidir.mutate("rejeitar")}>Rejeitar</Botao></>}</>}>
            {visao.data?.tabelaDemonstracao && <Aviso tom="perigo" titulo="Preços de demonstração">Este orçamento usou a tabela de demonstração ({visao.data.dataBase}). Os valores são ilustrativos e não servem como estimativa: importe a tabela SINAPI oficial da UF e do regime da obra e gere um novo orçamento.</Aviso>}
            {visao.data?.quantitativoDesatualizado && <Aviso tom="aviso" titulo="Modelo mudou depois deste orçamento">O orçamento foi feito sobre a versão {visao.data.versaoModelo} do modelo. Gere um novo para refletir o modelo atual; a diferença em relação ao aprovado vira proposta de aditivo.</Aviso>}
            {visao.isLoading ? <Carregando /> : <Tabela linhas={itens} chave={(i) => i.id} vazio={<Vazio titulo="Sem itens" />} colunas={[
              { titulo: "Etapa / serviço", celula: (i) => <span><span className="text-xs text-text-3">{i.etapa}</span><br />{i.servico}{i.geradoPorIa && !i.confirmadoPor && <> <EtiquetaIa /></>}</span> },
              { titulo: "SINAPI", celula: (i) => <span className="mono text-xs">{i.codigoSinapi}<br /><span className="text-text-3">{String(i["descricao"] ?? "")}</span></span> }, { titulo: "Qtd", celula: (i) => `${numero(i.quantidade, 3)} ${i.unidade}`, numerica: true },
              { titulo: "Unit.", celula: (i) => moeda(i.custoUnitario), numerica: true }, { titulo: "Total", celula: (i) => <span className="font-medium">{moeda(i.total)}</span>, numerica: true },
              { titulo: "", celula: (i) => eng && !cliente ? <div className="flex gap-1">{i.geradoPorIa && !i.confirmadoPor && <Botao pequeno onClick={() => confirmar.mutate(i.id)}>Confirmar</Botao>}<Botao pequeno variante="fantasma" onClick={() => remover.mutate(i.id)}>Remover</Botao></div> : null },
            ]} />}
            {(visao.data?.itensPendentesIa ?? 0) > 0 && <Aviso tom="aviso">Itens sugeridos pela IA precisam de confirmação antes da aprovação (justificativa no detalhe).</Aviso>}
          </Cartao>
          {eng && <div className="flex flex-col gap-4">
            <Cartao titulo="Adicionar item (busca SINAPI)">
              <Selecao rotulo="Tabela" value={tabelaId} onChange={(e) => setTabelaId(e.target.value)} vazio="Escolher…" opcoes={(tabelas.data ?? []).map((t) => ({ valor: t.id, rotulo: `${t.uf} ${t.mesReferencia} ${t.desonerado ? "deson." : ""}` }))} />
              <Entrada rotulo="Buscar composição" value={termo} onChange={(e) => setTermo(e.target.value)} placeholder="ex.: fôrma, concreto fck 30" />
              {comps.data && <ul className="max-h-40 overflow-auto text-xs border border-line-soft rounded">{comps.data.map((c) => <li key={c.id}><button type="button" className="w-full text-left px-2 py-1 hover:bg-surface" onClick={() => setNovo((v) => ({ ...v, codigoSinapi: c.codigo, unidade: c.unidade, servico: v.servico || c.descricao }))}><span className="mono">{c.codigo}</span> {c.descricao} · {c.unidade} · {moeda(c.custoUnitario)}</button></li>)}</ul>}
              <Entrada rotulo="Etapa" value={novo.etapa} onChange={(e) => setNovo((v) => ({ ...v, etapa: e.target.value }))} /><Entrada rotulo="Serviço" value={novo.servico} onChange={(e) => setNovo((v) => ({ ...v, servico: e.target.value }))} />
              <div className="grid grid-cols-3 gap-2"><Entrada rotulo="Código" className="mono" value={novo.codigoSinapi} onChange={(e) => setNovo((v) => ({ ...v, codigoSinapi: e.target.value }))} /><Entrada rotulo="Unid." value={novo.unidade} onChange={(e) => setNovo((v) => ({ ...v, unidade: e.target.value }))} /><Entrada rotulo="Qtd" inputMode="decimal" className="mono" value={novo.quantidade} onChange={(e) => setNovo((v) => ({ ...v, quantidade: e.target.value }))} /></div>
              <Botao pequeno icone={<Plus className="size-4" aria-hidden />} disabled={!novo.codigoSinapi || !novo.quantidade || !novo.servico} carregando={addItem.isPending} onClick={() => addItem.mutate(novo)}>Adicionar</Botao>
            </Cartao>
            <Cartao titulo="Cliente vê (DN-02)"><Alternar rotulo="Totais" ligado={!!o.liberadoCliente} onChange={(v) => liberar.mutate({ totais: v, detalhe: !!o["detalheLiberadoCliente"] })} /><Alternar rotulo="Detalhe por item" ligado={!!o["detalheLiberadoCliente"]} onChange={(v) => liberar.mutate({ totais: !!o.liberadoCliente, detalhe: v })} /></Cartao>
            <Cartao titulo="Aditivos"><Entrada rotulo="Descrição" value={adit.descricao} onChange={(e) => setAdit((v) => ({ ...v, descricao: e.target.value }))} /><Entrada rotulo="Valor (R$)" inputMode="decimal" className="mono" value={adit.valor} onChange={(e) => setAdit((v) => ({ ...v, valor: e.target.value }))} /><Botao pequeno variante="secundario" disabled={!adit.descricao || !adit.valor} onClick={() => propor.mutate(adit)}>Propor ao cliente</Botao>
              <ul className="text-xs flex flex-col gap-1">{(aditivos.data ?? []).map((a) => <li key={a.id} className="flex justify-between gap-2"><span>{a.descricao} · {moeda(a.valor)} <EstadoEtiqueta estado={a.estado} /></span>{a.estado === "PROPOSTO" && <span className="flex gap-1"><Botao pequeno onClick={() => decidirAdit.mutate({ id: a.id, aprovar: true })}>Aprovar</Botao><Botao pequeno variante="fantasma" onClick={() => decidirAdit.mutate({ id: a.id, aprovar: false })}>Negar</Botao></span>}</li>)}</ul></Cartao>
          </div>}
          {cliente && aditivos.data?.some((a) => a.estado === "PROPOSTO") && <Cartao titulo="Aditivos para sua aprovação" className="lg:col-span-1"><ul className="text-sm flex flex-col gap-2">{aditivos.data.filter((a) => a.estado === "PROPOSTO").map((a) => <li key={a.id} className="flex justify-between gap-2"><span>{a.descricao} · {moeda(a.valor)}</span><span className="flex gap-1"><Botao pequeno onClick={() => decidirAdit.mutate({ id: a.id, aprovar: true })}>Aprovar</Botao><Botao pequeno variante="fantasma" onClick={() => decidirAdit.mutate({ id: a.id, aprovar: false })}>Recusar</Botao></span></li>)}</ul></Cartao>}
        </>}
      </div>
      <p className="mt-4 text-xs text-text-3 flex items-center gap-1"><Search className="size-3" aria-hidden />Import mensal da tabela SINAPI: <code className="mono">POST /api/v1/sinapi/importar</code> (CSV; versão guardada no orçamento).</p>
    </>
  );
}
