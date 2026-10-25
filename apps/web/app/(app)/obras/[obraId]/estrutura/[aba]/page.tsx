"use client";
import { notFound } from "next/navigation";
import { ESTRUTURA_ABAS, TITULOS_ABAS } from "@/lib/navegacao";
import { use, useState } from "react";
import { Sparkles, Wind } from "lucide-react";
import { del, post, put } from "@/lib/api";
import { useAcao, useDados } from "@/lib/dados";
import { aprova, editaModelo, usePapeis } from "@/lib/sessao";
import type { Carga, RascunhoIa, Restricoes } from "@/lib/tipos";
import { data, numero } from "@/lib/formatos";
import { Aviso, Botao, Cartao, Carregando, Entrada, Etiqueta, Selecao, Tabela, Vazio } from "@/components/ui";
import { CabecalhoObra, EstadoEtiqueta, EtiquetaIa, usePavimentos } from "@/components/obra/comuns";

type Lancamento = { id: string; pavimentoId: string; estado: string; combinacoesJson?: string; ventoJson?: string };
type RascunhoLanc = RascunhoIa & { pavimentoId: string; conteudoJson?: string; modeloIa?: string; inputTokens?: number; outputTokens?: number; motivoRejeicao?: string };
/** espelha CalculadoraVaos.RelatorioVaos */
type RelatorioVaos = { pavimentoId: string; caixa?: { xMin: number; xMax: number; yMin: number; yMax: number }; linhas: { eixo: string; posicao: number; vaos: number; apoios: number; fencepostOk: boolean; comprimentosVaos: number[] }[]; achados: { codigo?: string; regra?: string; mensagem: string; [k: string]: unknown }[] };
type Excecao = { id: string; regra: string; elementoId: string; valorSolicitado: number; justificativa: string; estado: string };
type Combinacao = { nome: string; estadoLimite: string; fatores: Record<string, number> };

/** RF04 lançamento e cargas · RF06 auditoria de vãos · RF08 tolerâncias e seções mínimas (com exceções normativas). */
export default function PaginaEstrutura({ params }: { params: Promise<{ obraId: string; aba: string }> }) {
  const { obraId, aba } = use(params);
  if (!(ESTRUTURA_ABAS as readonly string[]).includes(aba)) notFound();
  const papeis = usePapeis(obraId); const edita = editaModelo(papeis); const eng = aprova(papeis);
  const pav = usePavimentos(obraId);
  const [pavId, setPavId] = useState("");
  const pavAtivo = pavId || pav[0]?.id || "";
  const base = `/obras/${obraId}/estrutura`;
  const cargas = useDados<Carga[]>(["estrutura", obraId, "cargas"], `${base}/cargas`);
  const lanc = useDados<Lancamento[]>(["estrutura", obraId, "lanc"], `${base}/lancamentos`);
  const comb = useDados<Combinacao[]>(["estrutura", obraId, "comb"], `${base}/combinacoes`, { query: { pavimentoId: pavAtivo }, ativo: !!pavAtivo });
  const rascunhos = useDados<RascunhoLanc[]>(["estrutura", obraId, "rascunhos"], `${base}/lancamento/rascunhos`);
  const vaos = useDados<RelatorioVaos[]>(["estrutura", obraId, "vaos"], `${base}/vaos`);
  const restr = useDados<Restricoes>(["estrutura", obraId, "restricoes"], `${base}/restricoes`);
  const exc = useDados<Excecao[]>(["estrutura", obraId, "exc"], `${base}/excecoes-normativas`);
  const inv = [["estrutura", obraId]];
  const [nc, setNc] = useState({ natureza: "PERMANENTE", tipo: "AREA", valor: "", descricao: "", casoCarga: "G1", elementoAlvoId: "" });
  const [vento, setVento] = useState({ v0: "45", s1: "1", s2: "1", s3: "1", ca: "1.2" });
  const [r, setR] = useState<Restricoes | null>(null);
  const [ne, setNe] = useState({ regra: "pilarLadoMin", elementoId: "", valorSolicitado: "", justificativa: "" });
  const lancar = useAcao((v: typeof nc) => post(`${base}/cargas`, { pavimentoId: pavAtivo, natureza: v.natureza, tipo: v.tipo, valor: Number(v.valor.replace(",", ".")), descricao: v.descricao || null, casoCarga: v.casoCarga || null, elementoAlvoId: v.elementoAlvoId || null }), { invalidar: inv, sucesso: "Carga lançada." });
  const removerCarga = useAcao((id: string) => del(`${base}/cargas/${id}`), { invalidar: inv });
  const salvarVento = useAcao((v: typeof vento) => put(`${base}/vento/${pavAtivo}`, { v0: Number(v.v0), s1: Number(v.s1), s2: Number(v.s2), s3: Number(v.s3), ca: Number(v.ca) }), { invalidar: inv, sucesso: "Vento (NBR 6123) salvo." });
  const pronto = useAcao(() => post(`${base}/lancamentos/${pavAtivo}/pronto`), { invalidar: inv, sucesso: "Lançamento marcado como pronto para análise." });
  const sugerir = useAcao(() => post(`${base}/lancamento/sugerir`, { pavimentoId: pavAtivo }), { invalidar: inv, sucesso: "Sugestão da IA criada como rascunho." });
  const decidirRasc = useAcao((v: { id: string; acao: "aprovar" | "rejeitar"; artRrt?: string; motivo?: string }) => post(`${base}/lancamento/rascunhos/${v.id}/${v.acao}`, v.acao === "aprovar" ? { artRrt: v.artRrt } : { motivo: v.motivo }), { invalidar: [...inv, ["modelo", obraId]], sucesso: "Decisão registrada." });
  const salvarRestr = useAcao((v: Restricoes) => put(`${base}/restricoes`, v), { invalidar: inv, sucesso: "Restrições atualizadas." });
  const pedirExc = useAcao((v: typeof ne) => post(`${base}/excecoes-normativas`, { regra: v.regra, elementoId: v.elementoId, valorSolicitado: Number(v.valorSolicitado.replace(",", ".")), justificativa: v.justificativa }), { invalidar: inv, sucesso: "Exceção solicitada." });
  const decidirExc = useAcao((v: { id: string; aprovar: boolean }) => post(`${base}/excecoes-normativas/${v.id}/decidir`, undefined, { aprovar: v.aprovar }), { invalidar: inv });
  const revogarExc = useAcao((id: string) => post(`${base}/excecoes-normativas/${id}/revogar`), { invalidar: inv });
  const lancAtual = lanc.data?.find((l) => l.pavimentoId === pavAtivo);
  const restrAtual = r ?? restr.data;

  return (
    <>
      <CabecalhoObra obraId={obraId} secao={TITULOS_ABAS[aba] ?? aba} grupo="Estrutura" descricao="Lançamento de elementos (no Modelo), cargas e combinações, auditoria de vãos e restrições de projeto." acoes={<Selecao rotulo="Pavimento" value={pavAtivo} onChange={(e) => setPavId(e.target.value)} opcoes={pav.map((p) => ({ valor: p.id, rotulo: `${p.nome} (${p.elevacao >= 0 ? "+" : ""}${p.elevacao.toFixed(2)})` }))} />} />
      <div className="mt-4 grid gap-4 lg:grid-cols-3">
        {aba === "cargas" && <>
          <Cartao titulo="Cargas do pavimento" className="lg:col-span-2" acoes={<>{lancAtual && <EstadoEtiqueta estado={lancAtual.estado} />}{eng && <Botao pequeno variante="secundario" carregando={pronto.isPending} onClick={() => pronto.mutate()}>Marcar pronto para análise</Botao>}</>}>
            {cargas.isLoading ? <Carregando /> : <Tabela linhas={(cargas.data ?? []).filter((c) => c.pavimentoId === pavAtivo)} chave={(c) => c.id} vazio={<Vazio titulo="Nenhuma carga neste pavimento" descricao="Peso próprio é automático; lance revestimentos, alvenaria, acidental (NBR 6120) e vento (NBR 6123)." />} colunas={[
              { titulo: "Natureza", celula: (c) => <Etiqueta tom={c.natureza === "PERMANENTE" ? "neutro" : c.natureza === "VENTO" ? "info" : "aviso"}>{c.natureza}</Etiqueta> },
              { titulo: "Tipo", celula: (c) => c.tipo === "AREA" ? "kN/m²" : c.tipo === "LINEAR" ? "kN/m" : "kN" }, { titulo: "Valor", celula: (c) => numero(c.valor), numerica: true },
              { titulo: "Caso", celula: (c) => c.casoCarga ?? "—" }, { titulo: "Descrição", celula: (c) => c.descricao ?? "—" },
              { titulo: "", celula: (c) => edita ? <Botao pequeno variante="fantasma" onClick={() => removerCarga.mutate(c.id)}>Remover</Botao> : null },
            ]} />}
            <details className="text-sm"><summary className="cursor-pointer font-medium">Combinações geradas (NBR 8681)</summary>
              <Tabela className="mt-2" linhas={comb.data ?? []} chave={(c) => c.nome} vazio={<p className="text-text-3 text-xs">Sem combinações: lance cargas primeiro.</p>} colunas={[{ titulo: "Nome", celula: (c) => c.nome }, { titulo: "Estado-limite", celula: (c) => c.estadoLimite }, { titulo: "Fatores", celula: (c) => <span className="mono text-xs">{Object.entries(c.fatores).map(([k, v]) => `${k}=${v}`).join(" · ")}</span> }]} />
            </details>
          </Cartao>
          <div className="flex flex-col gap-4">
            <Cartao titulo="Lançar carga">
              <Selecao rotulo="Natureza" value={nc.natureza} onChange={(e) => setNc((v) => ({ ...v, natureza: e.target.value }))} opcoes={[{ valor: "PERMANENTE", rotulo: "Permanente (G)" }, { valor: "ACIDENTAL", rotulo: "Acidental (Q)" }, { valor: "VENTO", rotulo: "Vento (W)" }]} />
              <Selecao rotulo="Tipo" value={nc.tipo} onChange={(e) => setNc((v) => ({ ...v, tipo: e.target.value }))} opcoes={[{ valor: "AREA", rotulo: "Área (kN/m²)" }, { valor: "LINEAR", rotulo: "Linear (kN/m)" }, { valor: "PONTUAL", rotulo: "Pontual (kN)" }]} />
              <Entrada rotulo="Valor" inputMode="decimal" className="mono" value={nc.valor} onChange={(e) => setNc((v) => ({ ...v, valor: e.target.value }))} />
              <Entrada rotulo="Caso de carga" value={nc.casoCarga} onChange={(e) => setNc((v) => ({ ...v, casoCarga: e.target.value }))} />
              <Entrada rotulo="Elemento alvo (id/rótulo, opcional)" value={nc.elementoAlvoId} onChange={(e) => setNc((v) => ({ ...v, elementoAlvoId: e.target.value }))} className="mono" />
              <Entrada rotulo="Descrição" value={nc.descricao} onChange={(e) => setNc((v) => ({ ...v, descricao: e.target.value }))} />
              <Botao pequeno disabled={!edita || !nc.valor} carregando={lancar.isPending} onClick={() => lancar.mutate(nc)}>Lançar</Botao>
            </Cartao>
            <Cartao titulo="Vento (NBR 6123)">
              <div className="grid grid-cols-2 gap-2">{(["v0", "s1", "s2", "s3", "ca"] as const).map((k) => <Entrada key={k} rotulo={k === "v0" ? "V0 (m/s)" : k.toUpperCase()} inputMode="decimal" className="mono" value={vento[k]} onChange={(e) => setVento((v) => ({ ...v, [k]: e.target.value }))} />)}</div>
              <Botao pequeno variante="secundario" icone={<Wind className="size-4" aria-hidden />} disabled={!edita} carregando={salvarVento.isPending} onClick={() => salvarVento.mutate(vento)}>Salvar vento</Botao>
            </Cartao>
          </div>
        </>}
        {aba === "lancamento-ia" && <Cartao titulo="Lançamento automático sugerido pela IA" className="lg:col-span-3" descricao="Pilares nos cruzamentos de eixos, vigas entre apoios e lajes por contorno. Entra como rascunho até a aprovação do engenheiro (RF27)." acoes={edita && <Botao pequeno icone={<Sparkles className="size-4" aria-hidden />} carregando={sugerir.isPending} onClick={() => sugerir.mutate()}>Sugerir para {pav.find((p) => p.id === pavAtivo)?.nome}</Botao>}>
          {rascunhos.isLoading ? <Carregando /> : <Tabela linhas={rascunhos.data ?? []} chave={(x) => x.id} vazio={<Vazio titulo="Nenhum rascunho" descricao="Crie a malha de eixos no Modelo e peça uma sugestão." />} colunas={[
            { titulo: "Pavimento", celula: (x) => pav.find((p) => p.id === x.pavimentoId)?.nome ?? x.pavimentoId }, { titulo: "Estado", celula: (x) => <span className="flex gap-1"><EstadoEtiqueta estado={x.estado} /><EtiquetaIa /></span> },
            { titulo: "Modelo / tokens", celula: (x) => <span className="mono text-xs">{x.modeloIa ?? "—"} · {x.inputTokens ?? 0}/{x.outputTokens ?? 0}</span> }, { titulo: "Conteúdo", celula: (x) => <code className="mono text-xs break-all line-clamp-2">{x.conteudoJson ?? ""}</code> },
            { titulo: "Decisão", celula: (x) => eng && x.estado === "GERADO_POR_IA" ? <div className="flex gap-1"><Botao pequeno onClick={() => decidirRasc.mutate({ id: x.id, acao: "aprovar", artRrt: window.prompt("ART/RRT para aprovar:") ?? "" })}>Aprovar</Botao><Botao pequeno variante="secundario" onClick={() => decidirRasc.mutate({ id: x.id, acao: "rejeitar", motivo: window.prompt("Motivo:") ?? "sem motivo" })}>Rejeitar</Botao></div> : x.motivoRejeicao ?? "—" },
          ]} />}
        </Cartao>}
        {aba === "vaos" && <Cartao titulo="Caixa Cartesiana Sanitizada" className="lg:col-span-3" descricao="O auditor recalcula vãos e envoltória pelas extremidades reais dos eixos/pilares (n vãos ⇒ n+1 apoios).">
          {vaos.isLoading ? <Carregando /> : !vaos.data?.length ? <Vazio titulo="Sem relatório" descricao="Lance eixos e pilares no Modelo." /> : (
            <div className="grid gap-3 md:grid-cols-2">{vaos.data.map((v, i) => (
              <div key={i} className="rounded-[var(--radius-sm)] border border-line-soft p-3 text-sm flex flex-col gap-2">
                <p className="font-medium flex items-center gap-2">{pav.find((p) => p.id === v.pavimentoId)?.nome ?? v.pavimentoId} {v.achados.length ? <Etiqueta tom="aviso">{v.achados.length} achado(s)</Etiqueta> : <Etiqueta tom="sucesso">vãos ok</Etiqueta>}</p>
                {v.caixa && <p className="mono text-xs text-text-2">Caixa real: X [{numero(v.caixa.xMin)} ; {numero(v.caixa.xMax)}] · Y [{numero(v.caixa.yMin)} ; {numero(v.caixa.yMax)}] m</p>}
                <Tabela linhas={v.linhas} chave={(l) => l.eixo} colunas={[{ titulo: "Eixo", celula: (l) => l.eixo }, { titulo: "Posição (m)", celula: (l) => numero(l.posicao), numerica: true }, { titulo: "Vãos", celula: (l) => l.vaos, numerica: true }, { titulo: "Apoios", celula: (l) => l.apoios, numerica: true }, { titulo: "n+1", celula: (l) => l.fencepostOk ? <Etiqueta tom="sucesso">ok</Etiqueta> : <Etiqueta tom="perigo">erro do poste</Etiqueta> }, { titulo: "Comprimentos (m)", celula: (l) => <span className="mono text-xs">{(l.comprimentosVaos ?? []).map((c) => numero(c)).join(" · ")}</span> }]} />
                {v.achados.length > 0 && <ul className="text-xs text-warning">{v.achados.map((x, k) => <li key={k}>{x.codigo ?? x.regra}: {x.mensagem}</li>)}</ul>}
              </div>))}</div>
          )}
        </Cartao>}
        {aba === "restricoes" && <>
          <Cartao titulo="Restrições de projeto (Camada C)" className="lg:col-span-2" descricao="Padrão da Tabela A (geometricConstraints); mínimos da NBR 6118.">
            {!restrAtual ? <Carregando /> : <div className="grid gap-3 sm:grid-cols-3">
              {([["shortCurveTolerance", "ShortCurveTolerance (m)"], ["pilarLadoMin", "Pilar: lado mín. (m)"], ["pilarAreaMin", "Pilar: área mín. (cm²)"], ["vigaBMin", "Viga: b mín. (m)"], ["lajeHMin", "Laje: h mín. (m)"], ["paredeEspMin", "Parede: esp. mín. (m)"]] as [keyof Restricoes, string][]).map(([k, rot]) => <Entrada key={k} rotulo={rot} inputMode="decimal" className="mono" value={String(restrAtual[k])} disabled={!eng} onChange={(e) => setR({ ...restrAtual, [k]: Number(e.target.value.replace(",", ".")) })} />)}
            </div>}
            {eng && <Botao pequeno disabled={!r} carregando={salvarRestr.isPending} onClick={() => r && salvarRestr.mutate(r)}>Salvar restrições</Botao>}
          </Cartao>
          <Cartao titulo="Exceções normativas" descricao="Pedido justificado para um elemento específico; decide quem aprova.">
            <Selecao rotulo="Regra" value={ne.regra} onChange={(e) => setNe((v) => ({ ...v, regra: e.target.value }))} opcoes={["shortCurveTolerance", "pilarLadoMin", "pilarAreaMin", "vigaBMin", "lajeHMin", "paredeEspMin"].map((x) => ({ valor: x, rotulo: x }))} />
            <Entrada rotulo="Elemento (id/rótulo)" value={ne.elementoId} onChange={(e) => setNe((v) => ({ ...v, elementoId: e.target.value }))} className="mono" />
            <Entrada rotulo="Valor solicitado" inputMode="decimal" className="mono" value={ne.valorSolicitado} onChange={(e) => setNe((v) => ({ ...v, valorSolicitado: e.target.value }))} />
            <Entrada rotulo="Justificativa" value={ne.justificativa} onChange={(e) => setNe((v) => ({ ...v, justificativa: e.target.value }))} />
            <Botao pequeno disabled={!edita || !ne.elementoId || !ne.justificativa} carregando={pedirExc.isPending} onClick={() => pedirExc.mutate(ne)}>Solicitar</Botao>
            <ul className="flex flex-col gap-2 text-xs border-t border-line-soft pt-3">{(exc.data ?? []).map((x) => <li key={x.id} className="flex items-center justify-between gap-2"><span><span className="mono">{x.regra}</span> {x.elementoId} = {x.valorSolicitado} <EstadoEtiqueta estado={x.estado} /></span>{eng && x.estado === "SOLICITADA" ? <span className="flex gap-1"><Botao pequeno onClick={() => decidirExc.mutate({ id: x.id, aprovar: true })}>Aprovar</Botao><Botao pequeno variante="fantasma" onClick={() => decidirExc.mutate({ id: x.id, aprovar: false })}>Negar</Botao></span> : eng && x.estado === "APROVADA" ? <Botao pequeno variante="fantasma" onClick={() => revogarExc.mutate(x.id)}>Revogar</Botao> : null}</li>)}{!exc.data?.length && <li className="text-text-3">Nenhuma exceção.</li>}</ul>
          </Cartao>
        </>}
      </div>
      {!edita && <Aviso tom="info" className="mt-4">Seu papel permite só leitura nesta obra.</Aviso>}
    </>
  );
}
