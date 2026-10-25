"use client";
/**
 * Seletor de material do elemento: pesquisa por nome, código, família ou aplicação; comparação lado a lado; propriedades
 * com unidade, origem do valor (especificado, fabricante, ensaio, estimado, adotado) e referência com edição; prévia dos
 * elementos afetados e dos resultados que ficarão desatualizados; desfazer. Não escolhe material genérico em silêncio:
 * elemento sem material aparece como "não informado".
 */
import { useMemo, useState } from "react";
import { CATALOGO_MATERIAIS, familia, itemMaterial, materiaisPersonalizados, propriedadesCompletas, referencia, type Elemento, type ItemCatalogo, type Propriedade } from "@cca/notacao-vi";
import { useMotor } from "@/lib/motor/estado";
import { Botao, Etiqueta } from "@/components/ui";

const ROTULO_ORIGEM: Record<string, { rotulo: string; tom: "neutro" | "info" | "aviso" | "sucesso" }> = {
  ESPECIFICADO: { rotulo: "especificado", tom: "info" }, DECLARADO_FABRICANTE: { rotulo: "declarado pelo fabricante", tom: "info" }, ENSAIO: { rotulo: "ensaio", tom: "sucesso" },
  ESTIMADO: { rotulo: "estimado", tom: "aviso" }, ADOTADO: { rotulo: "adotado", tom: "neutro" },
};
const SUPORTE: Record<string, string> = { CALCULO: "usado no cálculo", ANALISE: "usado na análise", QUANTITATIVO: "só quantitativo", REPRESENTACAO: "só representação", NAO_IMPLEMENTADO: "não implementado" };
/** propriedades essenciais por família, mostradas primeiro; as demais ficam em "todas as propriedades" */
const ESSENCIAIS: Record<string, string[]> = { CONCRETO: ["fck", "Ecs", "grupo"], ALVENARIA: ["largura", "funcao", "fbk"], ACO_ESTRUTURAL: ["fy", "fu", "E"], ACO_ARMADURA: ["fyk", "Es"] };

function valor(p: Propriedade) {
  return `${typeof p.valor === "number" ? p.valor.toLocaleString("pt-BR") : p.valor ?? "—"}${p.unidade ? ` ${p.unidade}` : ""}`;
}

function LinhaPropriedade({ p }: { p: Propriedade }) {
  const ref = referencia(p.referencia);
  const o = ROTULO_ORIGEM[p.origem] ?? { rotulo: p.origem.toLowerCase(), tom: "neutro" as const };
  return (
    <li className="flex flex-col gap-0.5 border-t border-line-soft py-1">
      <span className="flex items-center gap-1 flex-wrap"><span className="text-text-2">{p.rotulo}</span><strong className="mono">{valor(p)}</strong><Etiqueta tom={o.tom}>{o.rotulo}</Etiqueta></span>
      {(ref || p.metodo || p.nota) && <span className="text-[11px] text-text-3">{p.metodo && <>{p.metodo} · </>}{p.nota && <>{p.nota} · </>}{ref ? <span title={ref.situacao}>{ref.organismo} {ref.identificador}:{ref.edicao}{ref.cancelada ? " — CANCELADA" : ""} ({ref.fonte.natureza})</span> : "sem referência normativa"}</span>}
    </li>
  );
}

export function SeletorMaterial({ elemento }: { elemento: Elemento }) {
  const s = useMotor();
  const atualId = (elemento as unknown as { materialId?: string }).materialId;
  const atual = itemMaterial(atualId);
  const [aberto, setAberto] = useState(false);
  const [busca, setBusca] = useState("");
  const [comparar, setComparar] = useState<string[]>([]);
  const [escolhido, setEscolhido] = useState<string | null>(null);
  const [escopo, setEscopo] = useState<"elemento" | "tipo">("elemento");
  const [aplicados, setAplicados] = useState(0);
  const opcoes = useMemo(() => {
    const t = busca.trim().toLowerCase();
    return [...CATALOGO_MATERIAIS.materiais, ...materiaisPersonalizados()].filter((m) => m.aplicacoes.includes(elemento.tipo)).filter((m) => !t || [m.codigo, m.nome, m.resumo ?? "", m.familia, ...m.legado].some((x) => x.toLowerCase().includes(t)));
  }, [busca, elemento.tipo]);
  const alvos = escopo === "elemento" ? [elemento] : s.modelo.elementosDo(s.pavimentoId).filter((e) => e.tipo === elemento.tipo);
  const novo = escolhido ? itemMaterial(escolhido) : undefined;
  const mudam = alvos.filter((e) => itemMaterial((e as unknown as { materialId?: string }).materialId)?.codigo !== novo?.codigo);
  const estrutural = ["PILAR", "VIGA", "LAJE", "SAPATA", "CONTRAVENTAMENTO"].includes(elemento.tipo) || (elemento.tipo === "PAREDE" && novo?.propriedades.some((p) => p.chave === "funcao" && p.valor === "estrutural"));

  const aplicar = async () => {
    if (!novo || !mudam.length) return;
    const ok = await s.aplicarLote(mudam.map((e) => ({ codigo: "ALTERAR", args: { id: e.id, props: { materialId: novo.codigo } } })), "CANVAS");
    if (ok) { setAplicados(mudam.length); setEscolhido(null); setAberto(false); }
  };
  const desfazer = async () => { for (let i = 0; i < aplicados; i++) await s.desfazer(); setAplicados(0); };

  const fam = atual ? familia(atual.familia) : undefined;
  return (
    <section aria-label="Material" className="flex flex-col gap-2 text-xs border border-line-soft rounded p-2">
      <div className="flex items-center gap-2 flex-wrap">
        <span className="text-text-3">Material</span>
        {atual ? <><strong>{atual.nome}</strong><span className="mono text-text-3">{atual.codigo}</span>{fam && <Etiqueta>{SUPORTE[fam.suporte] ?? fam.suporte}</Etiqueta>}</>
          : atualId ? <Etiqueta tom="perigo">código fora do catálogo: {atualId}</Etiqueta> : <Etiqueta tom="aviso">não informado</Etiqueta>}
      </div>
      {atual && <details><summary className="cursor-pointer">Propriedades (unidade, origem do valor e referência)</summary><ul>{propriedadesCompletas(atual).map((p) => <LinhaPropriedade key={p.chave} p={p} />)}</ul></details>}
      {aplicados > 0 && <p className="flex items-center gap-2 text-success">Material aplicado em {aplicados} elemento(s). <Botao pequeno variante="secundario" onClick={() => void desfazer()}>Desfazer</Botao></p>}
      {s.podeEditar && !aberto && <Botao pequeno variante="secundario" onClick={() => setAberto(true)}>Trocar material…</Botao>}
      {aberto && (
        <div className="flex flex-col gap-2">
          <label className="flex flex-col gap-1"><span className="text-text-3">Pesquisar (nome, código, família, código legado)</span>
            <input value={busca} onChange={(e) => setBusca(e.target.value)} className="min-h-9 rounded border border-line bg-bg px-2 text-sm" placeholder="ex.: C30, cerâmico, A572" /></label>
          {!opcoes.length && <p className="text-text-3">Nenhum material do catálogo se aplica a {elemento.tipo.toLowerCase()} com esse termo.</p>}
          <ul className="flex flex-col gap-1 max-h-72 overflow-auto" aria-label="Opções de material">
            {opcoes.map((m) => {
              const props = propriedadesCompletas(m);
              const ess = (ESSENCIAIS[m.familia] ?? []).map((k) => props.find((p) => p.chave === k)).filter((p): p is Propriedade => !!p);
              return (
                <li key={m.codigo} className={`rounded border p-2 ${escolhido === m.codigo ? "border-brand bg-brand-soft" : "border-line-soft"}`}>
                  <div className="flex items-center gap-2 flex-wrap">
                    <label className="flex items-center gap-1 cursor-pointer"><input type="radio" name={`mat-${elemento.id}`} checked={escolhido === m.codigo} onChange={() => setEscolhido(m.codigo)} className="accent-[var(--brand)]" /><strong>{m.nome}</strong></label>
                    <span className="mono text-text-3">{m.codigo}</span>{m.codigo === atual?.codigo && <Etiqueta tom="marca">atual</Etiqueta>}{m.personalizado && <Etiqueta tom="aviso">personalizado da empresa</Etiqueta>}
                    <label className="ml-auto flex items-center gap-1"><input type="checkbox" checked={comparar.includes(m.codigo)} disabled={!comparar.includes(m.codigo) && comparar.length >= 3} onChange={() => setComparar((c) => c.includes(m.codigo) ? c.filter((x) => x !== m.codigo) : [...c, m.codigo])} />comparar</label>
                  </div>
                  {m.resumo && <p className="text-text-3">{m.resumo}</p>}
                  <p>{ess.map((p) => <span key={p.chave} className="mr-2">{p.rotulo.split(" (")[0]}: <span className="mono">{valor(p)}</span> <span className="text-text-3">({ROTULO_ORIGEM[p.origem]?.rotulo ?? p.origem})</span></span>)}</p>
                </li>
              );
            })}
          </ul>
          {comparar.length >= 2 && <Comparacao itens={comparar.map((c) => itemMaterial(c)).filter((x): x is ItemCatalogo => !!x)} />}
          {novo && (
            <div className="flex flex-col gap-1 rounded border border-line-soft p-2" aria-live="polite">
              <div className="flex gap-3 flex-wrap"><label className="flex items-center gap-1"><input type="radio" checked={escopo === "elemento"} onChange={() => setEscopo("elemento")} />só este elemento</label><label className="flex items-center gap-1"><input type="radio" checked={escopo === "tipo"} onChange={() => setEscopo("tipo")} />todos os elementos {elemento.tipo.toLowerCase()} deste pavimento</label></div>
              <p><strong>Prévia:</strong> {mudam.length} elemento(s) passam para <strong>{novo.nome}</strong>{alvos.length - mudam.length > 0 ? ` (${alvos.length - mudam.length} já usam este material)` : ""}: {mudam.slice(0, 8).map((e) => e.rotulo ?? e.id.slice(0, 6)).join(", ")}{mudam.length > 8 ? "…" : ""}.</p>
              {estrutural && <p className="text-warning">Material estrutural: análises e dimensionamentos desta obra ficarão como “Resultado desatualizado” e precisarão ser refeitos. Resultados já emitidos não são recalculados em silêncio.</p>}
              <p className="text-text-3">Quantitativos e orçamento passam a usar o serviço vinculado a este material. A troca gera VI(ALTERAR) auditado e pode ser desfeita.</p>
              <div className="flex gap-2"><Botao pequeno onClick={() => void aplicar()} disabled={!mudam.length}>Aplicar em {mudam.length}</Botao><Botao pequeno variante="fantasma" onClick={() => { setAberto(false); setEscolhido(null); }}>Cancelar</Botao></div>
            </div>
          )}
        </div>
      )}
    </section>
  );
}

function Comparacao({ itens }: { itens: ItemCatalogo[] }) {
  const listas = itens.map((i) => propriedadesCompletas(i));
  const chaves = [...new Set(listas.flatMap((l) => l.map((p) => p.chave)))];
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-[11px]"><caption className="text-left font-medium">Comparação</caption>
        <thead><tr><th scope="col" className="text-left">Propriedade</th>{itens.map((i) => <th key={i.codigo} scope="col" className="text-left">{i.nome}</th>)}</tr></thead>
        <tbody>{chaves.map((k) => { const ps = listas.map((l) => l.find((p) => p.chave === k)); const r = ps.find(Boolean)!; const difere = new Set(ps.map((p) => (p ? String(p.valor) : "—"))).size > 1; return (
          <tr key={k} className={`border-t border-line-soft ${difere ? "font-medium" : ""}`}><th scope="row" className="text-left font-normal text-text-2">{r.rotulo}</th>{ps.map((p, j) => <td key={j} className="mono">{p ? valor(p) : "—"}{p && <span className="text-text-3 font-sans"> ({ROTULO_ORIGEM[p.origem]?.rotulo ?? p.origem})</span>}</td>)}</tr>); })}</tbody>
      </table>
    </div>
  );
}
