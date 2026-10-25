"use client";
import { notFound } from "next/navigation";
import { ANALISE_ABAS, TITULOS_ABAS } from "@/lib/navegacao";
import { use, useState } from "react";
import { Calculator, Play } from "lucide-react";
import { post } from "@/lib/api";
import { useAcao, useDados } from "@/lib/dados";
import { aprova, usePapeis, useSessao } from "@/lib/sessao";
import type { Analise, Dimensionamento } from "@/lib/tipos";
import { data, numero } from "@/lib/formatos";
import { Aviso, Botao, Cartao, Carregando, Entrada, Selecao, Tabela, Vazio } from "@/components/ui";
import { ResultadosAnalise, ResultadosDimensionamento, type ResultadoAnalise, type ResultadoElemento } from "@/components/obra/resultados-estrutura";
import { CabecalhoObra, EstadoEtiqueta } from "@/components/obra/comuns";

/** RF05 análise MEF (job assíncrono) · RF07 dimensionamento ELU/ELS com aprovação por engenheiro com selo (ART). */
export default function PaginaAnalises({ params }: { params: Promise<{ obraId: string; aba: string }> }) {
  const { obraId, aba } = use(params);
  if (!(ANALISE_ABAS as readonly string[]).includes(aba)) notFound();
  const { sessao } = useSessao();
  const eng = aprova(usePapeis(obraId));
  const base = `/obras/${obraId}`;
  const analises = useDados<Analise[]>(["analises", obraId], `${base}/analises`, { intervalo: 5000 });
  const dims = useDados<Dimensionamento[]>(["dims", obraId], `${base}/dimensionamentos`, { intervalo: 5000 });
  const [sel, setSel] = useState<string | null>(null);
  const [dimSel, setDimSel] = useState<string | null>(null);
  const [art, setArt] = useState("");
  const [analiseDim, setAnaliseDim] = useState("");
  const diag = useDados<ResultadoAnalise>(["analise", obraId, sel], `${base}/analises/${sel}/diagramas`, { ativo: !!sel });
  const res = useDados<ResultadoElemento[]>(["dim", obraId, dimSel], `${base}/dimensionamentos/${dimSel}/resultados`, { ativo: !!dimSel });
  const solicitar = useAcao(() => post<Analise>(`${base}/analises`), { invalidar: [["analises", obraId]], sucesso: "Análise solicitada (job em execução)." });
  const dimensionar = useAcao((analiseId: string) => post<Dimensionamento>(`${base}/dimensionamentos`, { analiseId }), { invalidar: [["dims", obraId]], sucesso: "Dimensionamento iniciado." });
  const aprovar = useAcao((v: { id: string; artRrt: string }) => post(`${base}/dimensionamentos/${v.id}/aprovar`, { artRrt: v.artRrt }), { invalidar: [["dims", obraId]], sucesso: "Dimensionamento aprovado." });
  const rejeitar = useAcao((id: string) => post(`${base}/dimensionamentos/${id}/rejeitar`), { invalidar: [["dims", obraId]], sucesso: "Dimensionamento rejeitado." });
  const concluidas = (analises.data ?? []).filter((a) => a.estado === "CONCLUIDO");
  return (
    <>
      <CabecalhoObra obraId={obraId} secao={TITULOS_ABAS[aba] ?? aba} grupo="Estrutura" descricao={aba === "mef" ? "Pórtico espacial (6 GDL por nó) e treliça pelo MEF linear. Esforços calculados a partir do modelo, não medidos; resultado é rascunho até aprovação com ART." : "Verificação e dimensionamento de concreto armado (ABNT NBR 6118:2023) a partir de uma análise concluída, com detalhamento por posição. Perfis metálicos e contraventamentos não são verificados (ABNT NBR 8800 não implementada). Aprovação exige ART e selo."} acoes={eng && aba === "mef" && <Botao icone={<Play className="size-4" aria-hidden />} carregando={solicitar.isPending} onClick={() => solicitar.mutate()}>Nova análise</Botao>} />
      <div className="grid gap-4">
        {aba === "mef" && <Cartao titulo="Análises (RF05)">
          {analises.isLoading ? <Carregando /> : <Tabela linhas={analises.data ?? []} chave={(a) => a.id} vazio={<Vazio titulo="Nenhuma análise" descricao="Marque o lançamento como pronto em Estrutura e solicite a análise." />} colunas={[
            { titulo: "Quando", celula: (a) => data((a["concluidaEm"] ?? a["criadoEm"]) as string) }, { titulo: "Modelo", celula: (a) => `v${a.versaoModelo ?? "—"}` }, { titulo: "Estado", celula: (a) => <EstadoEtiqueta estado={String(a.estado)} /> },
            { titulo: "", celula: (a) => <div className="flex gap-1">{a.estado === "CONCLUIDO" && <Botao pequeno variante="secundario" onClick={() => setSel(a.id)}>Diagramas</Botao>}{a.estado === "CONCLUIDO" && eng && <Botao pequeno icone={<Calculator className="size-4" aria-hidden />} onClick={() => dimensionar.mutate(a.id)}>Dimensionar</Botao>}{a.estado === "FALHOU" && <span className="text-xs text-danger">{String(a["mensagemErro"] ?? "")}</span>}</div> },
          ]} />}
          {sel && (diag.isLoading ? <Carregando /> : diag.error ? <Aviso tom="perigo" titulo="Sem diagramas">{String((diag.error as Error).message)}</Aviso> : diag.data ? (() => { const a = (analises.data ?? []).find((x) => x.id === sel); return <ResultadosAnalise key={sel} r={diag.data} estado={a?.estado} versaoModelo={a?.versaoModelo} baseCalculoJson={a?.["baseCalculoJson"] as string | undefined} />; })() : null)}
        </Cartao>}
        {aba === "dimensionamento" && <Cartao titulo="Dimensionamentos (RF07)" acoes={eng && <div className="flex items-end gap-2"><Selecao rotulo="Análise concluída" value={analiseDim} onChange={(e) => setAnaliseDim(e.target.value)} vazio="Escolher…" opcoes={concluidas.map((a) => ({ valor: a.id, rotulo: `v${a.versaoModelo ?? "—"} · ${data((a["concluidaEm"] ?? a["criadoEm"]) as string)}` }))} /><Botao pequeno icone={<Calculator className="size-4" aria-hidden />} disabled={!analiseDim} carregando={dimensionar.isPending} onClick={() => dimensionar.mutate(analiseDim)}>Dimensionar</Botao></div>}>
          {dims.isLoading ? <Carregando /> : <Tabela linhas={dims.data ?? []} chave={(d) => d.id} vazio={<Vazio titulo="Nenhum dimensionamento" descricao={concluidas.length ? "Escolha uma análise concluída e clique em Dimensionar." : "Conclua uma análise primeiro."} />} colunas={[
            { titulo: "Quando", celula: (d) => data(d.criadoEm) }, { titulo: "Estado", celula: (d) => <EstadoEtiqueta estado={d.estado} /> },
            { titulo: "Aço (kg)", celula: (d) => numero(d["acoTotalKg"] as number, 1), numerica: true }, { titulo: "Não atendidos", celula: (d) => <span className={(d["elementosNaoVerificam"] as number) ? "text-danger font-medium" : ""}>{String(d["elementosNaoVerificam"] ?? 0)}</span>, numerica: true },
            { titulo: "", celula: (d) => <div className="flex gap-1"><Botao pequeno variante="secundario" onClick={() => setDimSel(d.id)}>Memória</Botao>{eng && d.estado === "CALCULADO" && <><Botao pequeno onClick={() => aprovar.mutate({ id: d.id, artRrt: art })} disabled={!art || !sessao?.seloVerificado} title={!sessao?.seloVerificado ? "Exige selo profissional verificado" : undefined}>Aprovar</Botao><Botao pequeno variante="fantasma" onClick={() => rejeitar.mutate(d.id)}>Rejeitar</Botao></>}</div> },
          ]} />}
          {eng && <Entrada rotulo="ART/RRT para aprovação" value={art} onChange={(e) => setArt(e.target.value)} className="mono" ajuda={sessao?.seloVerificado ? "Aprovação exige 2FA + selo verificado." : "Seu selo profissional ainda não está verificado (Conta › Selo)."} />}
          {dimSel && (res.isLoading ? <Carregando /> : (() => { const d = (dims.data ?? []).find((x) => x.id === dimSel); return <ResultadosDimensionamento linhas={res.data ?? []} estado={d?.estado} baseCalculoJson={d?.["baseCalculoJson"] as string | undefined} />; })())}
        </Cartao>}
      </div>
    </>
  );
}
