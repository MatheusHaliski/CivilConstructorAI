"use client";
import { use, useState } from "react";
import { Calculator, Play } from "lucide-react";
import { post } from "@/lib/api";
import { useAcao, useDados } from "@/lib/dados";
import { aprova, usePapeis, useSessao } from "@/lib/sessao";
import type { Analise, Dimensionamento } from "@/lib/tipos";
import { data, numero } from "@/lib/formatos";
import { Aviso, Botao, Cartao, Carregando, Entrada, Etiqueta, Selecao, Tabela, Vazio } from "@/components/ui";
import { CabecalhoObra, EstadoEtiqueta } from "@/components/obra/comuns";

type Barra = { barra: number; elementoId: string; rotulo?: string; comprimento: number; nMax: number; vMax: number; mMax: number; mMin: number; flechaMax: number };
type ResultadoCombinacao = { combinacao: { nome: string; estadoLimite: string; fatores: Record<string, number> }; barras: Barra[]; reacoes: { no: number; fx: number; fy: number; fz: number; mx: number; my: number; mz: number }[]; deslocamentoMax: number };
/** espelha SolverMef.ResultadoAnalise */
type ResultadoAnalise = { nos: number; barras: number; porCombinacao: Record<string, ResultadoCombinacao> };
/** espelha DimensionamentoService.ResultadoElemento */
type ResultadoElemento = { elementoId: string; rotulo?: string; tipo: string; verifica: boolean; motivo?: string; mdKnm: number; vdKn: number; ndKn: number; armaduraLongitudinal?: string; estribos?: string; asCm2: number; acoKg: number; memoria: string[] };

function Diagramas({ r }: { r: ResultadoAnalise }) {
  const nomes = Object.keys(r.porCombinacao ?? {});
  const [comb, setComb] = useState(nomes[0] ?? "");
  const atual = r.porCombinacao?.[comb];
  if (!atual) return <p className="text-sm text-text-3">Sem resultados por combinação.</p>;
  const barras = atual.barras;
  const maxM = Math.max(...barras.map((b) => Math.max(Math.abs(b.mMax), Math.abs(b.mMin))), 1e-6);
  return (
    <div className="flex flex-col gap-2">
      <div className="flex items-end gap-3 flex-wrap">
        <Selecao rotulo="Combinação" value={comb} onChange={(e) => setComb(e.target.value)} opcoes={nomes.map((n) => ({ valor: n, rotulo: n }))} />
        <p className="text-xs text-text-3 pb-3">{r.nos} nós · {r.barras} barras · deslocamento máx. {numero(atual.deslocamentoMax * 1000, 2)} mm</p>
      </div>
      <svg role="img" aria-label={`Momento fletor máximo por barra na combinação ${comb}`} viewBox={`0 0 ${Math.max(300, barras.length * 28)} 150`} className="w-full h-40">
        <line x1="0" x2={Math.max(300, barras.length * 28)} y1="75" y2="75" stroke="var(--line-soft)" />
        {barras.map((b, i) => { const hp = (Math.abs(b.mMax) / maxM) * 60, hn = (Math.abs(b.mMin) / maxM) * 60; return <g key={b.barra}><rect x={i * 28 + 4} y={75 - hp} width={9} height={hp} fill="var(--brand)" /><rect x={i * 28 + 14} y={75} width={9} height={hn} fill="var(--obra-andamento)" /><text x={i * 28 + 14} y={146} fontSize="8" textAnchor="middle" fill="var(--text-3)">{b.rotulo ?? b.barra}</text></g>; })}
      </svg>
      <p className="text-xs text-text-3"><span className="inline-block size-2 bg-brand mr-1" aria-hidden />M+ máximo · <span className="inline-block size-2 mr-1" style={{ background: "var(--obra-andamento)" }} aria-hidden />M− mínimo (kN·m)</p>
      <Tabela linhas={barras} chave={(b) => String(b.barra)} colunas={[{ titulo: "Barra", celula: (b) => b.rotulo ?? b.barra }, { titulo: "L (m)", celula: (b) => numero(b.comprimento), numerica: true }, { titulo: "N máx (kN)", celula: (b) => numero(b.nMax), numerica: true }, { titulo: "V máx (kN)", celula: (b) => numero(b.vMax), numerica: true }, { titulo: "M+ (kN·m)", celula: (b) => numero(b.mMax), numerica: true }, { titulo: "M− (kN·m)", celula: (b) => numero(b.mMin), numerica: true }, { titulo: "Flecha (mm)", celula: (b) => numero(b.flechaMax * 1000, 2), numerica: true }]} />
    </div>
  );
}

/** RF05 análise MEF (job assíncrono) · RF07 dimensionamento ELU/ELS com aprovação por engenheiro com selo (ART). */
export default function PaginaAnalises({ params }: { params: Promise<{ obraId: string }> }) {
  const { obraId } = use(params);
  const { sessao } = useSessao();
  const eng = aprova(usePapeis(obraId));
  const base = `/obras/${obraId}`;
  const analises = useDados<Analise[]>(["analises", obraId], `${base}/analises`, { intervalo: 5000 });
  const dims = useDados<Dimensionamento[]>(["dims", obraId], `${base}/dimensionamentos`, { intervalo: 5000 });
  const [sel, setSel] = useState<string | null>(null);
  const [dimSel, setDimSel] = useState<string | null>(null);
  const [art, setArt] = useState("");
  const diag = useDados<ResultadoAnalise>(["analise", obraId, sel], `${base}/analises/${sel}/diagramas`, { ativo: !!sel });
  const res = useDados<ResultadoElemento[]>(["dim", obraId, dimSel], `${base}/dimensionamentos/${dimSel}/resultados`, { ativo: !!dimSel });
  const solicitar = useAcao(() => post<Analise>(`${base}/analises`), { invalidar: [["analises", obraId]], sucesso: "Análise solicitada (job em execução)." });
  const dimensionar = useAcao((analiseId: string) => post<Dimensionamento>(`${base}/dimensionamentos`, { analiseId }), { invalidar: [["dims", obraId]], sucesso: "Dimensionamento iniciado." });
  const aprovar = useAcao((v: { id: string; artRrt: string }) => post(`${base}/dimensionamentos/${v.id}/aprovar`, { artRrt: v.artRrt }), { invalidar: [["dims", obraId]], sucesso: "Dimensionamento aprovado." });
  const rejeitar = useAcao((id: string) => post(`${base}/dimensionamentos/${id}/rejeitar`), { invalidar: [["dims", obraId]], sucesso: "Dimensionamento rejeitado." });
  const concluidas = (analises.data ?? []).filter((a) => a.estado === "CONCLUIDO");
  return (
    <>
      <CabecalhoObra obraId={obraId} secao="Análise e dimensionamento" descricao="Pórtico espacial (6 GDL por nó) pelo MEF; armaduras pela NBR 6118 / perfis NBR 8800. Resultado é rascunho até aprovação com ART." acoes={eng && <Botao icone={<Play className="size-4" aria-hidden />} carregando={solicitar.isPending} onClick={() => solicitar.mutate()}>Nova análise</Botao>} />
      <div className="grid gap-4 lg:grid-cols-2">
        <Cartao titulo="Análises (RF05)">
          {analises.isLoading ? <Carregando /> : <Tabela linhas={analises.data ?? []} chave={(a) => a.id} vazio={<Vazio titulo="Nenhuma análise" descricao="Marque o lançamento como pronto em Estrutura e solicite a análise." />} colunas={[
            { titulo: "Quando", celula: (a) => data((a["concluidaEm"] ?? a["criadoEm"]) as string) }, { titulo: "Modelo", celula: (a) => `v${a.versaoModelo ?? "—"}` }, { titulo: "Estado", celula: (a) => <EstadoEtiqueta estado={String(a.estado)} /> },
            { titulo: "", celula: (a) => <div className="flex gap-1">{a.estado === "CONCLUIDO" && <Botao pequeno variante="secundario" onClick={() => setSel(a.id)}>Diagramas</Botao>}{a.estado === "CONCLUIDO" && eng && <Botao pequeno icone={<Calculator className="size-4" aria-hidden />} onClick={() => dimensionar.mutate(a.id)}>Dimensionar</Botao>}{a.estado === "FALHOU" && <span className="text-xs text-danger">{String(a["mensagemErro"] ?? "")}</span>}</div> },
          ]} />}
          {sel && (diag.isLoading ? <Carregando /> : diag.error ? <Aviso tom="perigo" titulo="Sem diagramas">{String((diag.error as Error).message)}</Aviso> : diag.data ? <Diagramas key={sel} r={diag.data} /> : null)}
        </Cartao>
        <Cartao titulo="Dimensionamentos (RF07)">
          {dims.isLoading ? <Carregando /> : <Tabela linhas={dims.data ?? []} chave={(d) => d.id} vazio={<Vazio titulo="Nenhum dimensionamento" descricao={concluidas.length ? "Escolha uma análise concluída e clique em Dimensionar." : "Conclua uma análise primeiro."} />} colunas={[
            { titulo: "Quando", celula: (d) => data(d.criadoEm) }, { titulo: "Estado", celula: (d) => <EstadoEtiqueta estado={d.estado} /> },
            { titulo: "Aço (kg)", celula: (d) => numero(d["acoTotalKg"] as number, 1), numerica: true }, { titulo: "Não verifica", celula: (d) => <span className={(d["elementosNaoVerificam"] as number) ? "text-danger font-medium" : ""}>{String(d["elementosNaoVerificam"] ?? 0)}</span>, numerica: true },
            { titulo: "", celula: (d) => <div className="flex gap-1"><Botao pequeno variante="secundario" onClick={() => setDimSel(d.id)}>Memória</Botao>{eng && d.estado === "CALCULADO" && <><Botao pequeno onClick={() => aprovar.mutate({ id: d.id, artRrt: art })} disabled={!art || !sessao?.seloVerificado} title={!sessao?.seloVerificado ? "Exige selo profissional verificado" : undefined}>Aprovar</Botao><Botao pequeno variante="fantasma" onClick={() => rejeitar.mutate(d.id)}>Rejeitar</Botao></>}</div> },
          ]} />}
          {eng && <Entrada rotulo="ART/RRT para aprovação" value={art} onChange={(e) => setArt(e.target.value)} className="mono" ajuda={sessao?.seloVerificado ? "Aprovação exige 2FA + selo verificado." : "Seu selo profissional ainda não está verificado (Conta › Selo)."} />}
          {dimSel && (res.isLoading ? <Carregando /> : <Tabela linhas={res.data ?? []} chave={(r) => r.elementoId} colunas={[
            { titulo: "Elemento", celula: (r) => <span>{r.rotulo ?? r.elementoId} <span className="text-xs text-text-3">{r.tipo.toLowerCase()}</span></span> }, { titulo: "Verifica", celula: (r) => r.verifica ? <Etiqueta tom="sucesso">verifica</Etiqueta> : <span className="flex flex-col gap-0.5"><Etiqueta tom="perigo">NÃO VERIFICA</Etiqueta><span className="text-xs text-danger">{r.motivo}</span></span> },
            { titulo: "Md / Vd / Nd", celula: (r) => <span className="mono text-xs">{numero(r.mdKnm, 1)} kN·m · {numero(r.vdKn, 1)} kN · {numero(r.ndKn, 1)} kN</span> },
            { titulo: "Armadura", celula: (r) => <span className="mono text-xs">{r.armaduraLongitudinal ?? "—"}<br />{r.estribos ?? ""}</span> }, { titulo: "As (cm²)", celula: (r) => numero(r.asCm2), numerica: true }, { titulo: "Aço (kg)", celula: (r) => numero(r.acoKg, 1), numerica: true },
            { titulo: "Memória", celula: (r) => <details className="text-xs"><summary className="cursor-pointer">NBR 6118</summary><ul className="mono">{r.memoria.map((m, i) => <li key={i}>{m}</li>)}</ul></details> },
          ]} />)}
        </Cartao>
      </div>
    </>
  );
}
