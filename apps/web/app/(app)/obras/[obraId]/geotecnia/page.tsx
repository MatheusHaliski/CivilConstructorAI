"use client";
import { use, useState } from "react";
import { FileUp, Layers3 } from "lucide-react";
import { post } from "@/lib/api";
import { useAcao, useDados } from "@/lib/dados";
import { aprova, editaModelo, usePapeis } from "@/lib/sessao";
import type { Analise, Fundacao, Furo } from "@/lib/tipos";
import { data, numero } from "@/lib/formatos";
import { Abas, Aviso, Botao, Cartao, Carregando, Entrada, Etiqueta, Selecao, Tabela, Vazio } from "@/components/ui";
import { CabecalhoObra, EstadoEtiqueta, EtiquetaIa } from "@/components/obra/comuns";

type Extracao = { id: string; chaveS3: string; estado: string; dadosJson?: string; citacoesJson?: string; modeloIa?: string; mensagemErro?: string };
type Proposta = { id: string; pilarId: string; pilarRotulo?: string; tipo: string; centroX: number; centroY: number; bx: number; by: number; h: number; cargaKn: number; estacas?: number; diametroEstaca?: number; comprimentoEstaca?: number; verifica: boolean; memoriaJson?: string };

function parse<T>(s?: string): T | null { try { return s ? (JSON.parse(s) as T) : null; } catch { return null; } }

/** RF09 sondagens SPT (cadastro manual ou boletim lido pela IA com citações) · RF10 fundações (NBR 6122). */
export default function PaginaGeotecnia({ params }: { params: Promise<{ obraId: string }> }) {
  const { obraId } = use(params);
  const papeis = usePapeis(obraId); const edita = editaModelo(papeis); const eng = aprova(papeis);
  const base = `/obras/${obraId}`;
  const [aba, setAba] = useState("furos");
  const furos = useDados<Furo[]>(["furos", obraId], `${base}/sondagens`);
  const extr = useDados<Extracao[]>(["extracoes", obraId], `${base}/sondagens/extracoes`, { intervalo: 5000 });
  const fund = useDados<Fundacao[]>(["fundacoes", obraId], `${base}/fundacoes`, { intervalo: 5000 });
  const analises = useDados<Analise[]>(["analises", obraId], `${base}/analises`);
  const [fundSel, setFundSel] = useState<string | null>(null);
  const propostas = useDados<Proposta[]>(["fund", obraId, fundSel], `${base}/fundacoes/${fundSel}/propostas`, { ativo: !!fundSel });
  const [f, setF] = useState({ codigo: "SP-01", x: "0", y: "0", cotaBoca: "0", nivelAgua: "", camadas: "0;-2;ARGILA;argila siltosa\n-2;-6;AREIA;areia média", leituras: "1;4\n2;6\n3;9\n4;12\n5;18" });
  const [perfilXY, setPerfilXY] = useState({ x: "0", y: "0" });
  const perfil = useDados<Record<string, unknown>>(["perfil", obraId, perfilXY], `${base}/sondagens/perfil`, { query: { x: perfilXY.x, y: perfilXY.y }, ativo: aba === "perfil" });
  const [analiseId, setAnaliseId] = useState("");
  const inv = [["furos", obraId], ["extracoes", obraId], ["fundacoes", obraId]];
  const cadastrar = useAcao((v: typeof f) => post(`${base}/sondagens`, {
    codigo: v.codigo, x: Number(v.x), y: Number(v.y), cotaBoca: Number(v.cotaBoca), nivelAgua: v.nivelAgua ? Number(v.nivelAgua) : null,
    camadas: v.camadas.split("\n").filter(Boolean).map((l) => { const [t, b, tipo, d] = l.split(";"); return { cotaTopo: Number(t), cotaBase: Number(b), tipo: (tipo ?? "ARGILA").trim(), descricao: d?.trim() ?? "" }; }),
    leituras: v.leituras.split("\n").filter(Boolean).map((l) => { const [p, n] = l.split(";"); return { profundidade: Number(p), nspt: Number(n), impenetravel: (n ?? "").trim().toUpperCase() === "IMP" }; }),
  }), { invalidar: inv, sucesso: "Furo cadastrado." });
  const confirmar = useAcao((id: string) => post(`${base}/sondagens/${id}/confirmar`), { invalidar: inv, sucesso: "Furo confirmado." });
  const enviarBoletim = useAcao(async (v: { furoId: string; arquivo: File }) => {
    const { url, chave } = await post<{ url: string; chave: string }>(`${base}/sondagens/${v.furoId}/boletim/presign`);
    const r = await fetch(url, { method: "PUT", body: v.arquivo, headers: { "Content-Type": v.arquivo.type || "application/pdf" } });
    if (!r.ok) throw new Error("Falha ao enviar o boletim para o armazenamento.");
    return post(`${base}/sondagens/extracoes`, { chaveS3: chave });
  }, { invalidar: inv, sucesso: "Boletim enviado; a IA está lendo (com citações)." });
  const decidirExtr = useAcao((v: { id: string; acao: "confirmar" | "rejeitar"; x?: number; y?: number }) => post(`${base}/sondagens/extracoes/${v.id}/${v.acao}`, v.acao === "confirmar" ? { x: v.x ?? 0, y: v.y ?? 0 } : undefined), { invalidar: inv, sucesso: "Decisão registrada." });
  const dimensionar = useAcao((v: { analiseId: string }) => post(`${base}/fundacoes`, { analiseId: v.analiseId }), { invalidar: inv, sucesso: "Dimensionamento de fundações iniciado." });
  const decidirFund = useAcao((v: { id: string; acao: "aprovar" | "rejeitar" | "aplicado" }) => post(`${base}/fundacoes/${v.id}/${v.acao}`), { invalidar: [...inv, ["modelo", obraId]], sucesso: "Registrado." });

  return (
    <>
      <CabecalhoObra obraId={obraId} secao="Geotecnia e fundações" descricao="Furos de sondagem com Nspt por metro (NBR 6484) e dimensionamento de sapatas, blocos e radier (NBR 6122)." />
      <Abas ativa={aba} onChange={setAba} abas={[{ id: "furos", rotulo: "Furos SPT (RF09)" }, { id: "boletim", rotulo: "Boletim por IA" }, { id: "perfil", rotulo: "Perfil interpolado" }, { id: "fundacoes", rotulo: "Fundações (RF10)" }]} />
      <div className="mt-4 grid gap-4 lg:grid-cols-3">
        {aba === "furos" && <>
          <Cartao titulo="Furos de sondagem" className="lg:col-span-2">
            {furos.isLoading ? <Carregando /> : <Tabela linhas={furos.data ?? []} chave={(x) => x.id} vazio={<Vazio titulo="Nenhum furo" descricao="Cadastre manualmente ou envie o boletim em PDF." />} colunas={[
              { titulo: "Código", celula: (x) => <span className="mono font-medium">{String(x["codigo"] ?? x.nome)}</span> }, { titulo: "x; y (m)", celula: (x) => <span className="mono">{numero(x.x)}; {numero(x.y)}</span> }, { titulo: "Cota boca", celula: (x) => numero(x.cotaBoca), numerica: true }, { titulo: "NA", celula: (x) => x.nivelAgua == null ? "—" : numero(x.nivelAgua), numerica: true },
              { titulo: "Camadas / leituras", celula: (x) => `${parse<unknown[]>(x["camadasJson"] as string)?.length ?? 0} / ${parse<unknown[]>(x["leiturasJson"] as string)?.length ?? 0}` }, { titulo: "Estado", celula: (x) => <span className="flex gap-1"><EstadoEtiqueta estado={x.estado} />{x["origemIa"] ? <EtiquetaIa /> : null}</span> },
              { titulo: "", celula: (x) => edita && x.estado !== "CONFIRMADO" ? <Botao pequeno onClick={() => confirmar.mutate(x.id)}>Confirmar</Botao> : null },
            ]} />}
          </Cartao>
          <Cartao titulo="Cadastrar furo">
            <Entrada rotulo="Código" value={f.codigo} onChange={(e) => setF((v) => ({ ...v, codigo: e.target.value }))} className="mono" />
            <div className="grid grid-cols-2 gap-2"><Entrada rotulo="x (m)" inputMode="decimal" className="mono" value={f.x} onChange={(e) => setF((v) => ({ ...v, x: e.target.value }))} /><Entrada rotulo="y (m)" inputMode="decimal" className="mono" value={f.y} onChange={(e) => setF((v) => ({ ...v, y: e.target.value }))} /><Entrada rotulo="Cota da boca" inputMode="decimal" className="mono" value={f.cotaBoca} onChange={(e) => setF((v) => ({ ...v, cotaBoca: e.target.value }))} /><Entrada rotulo="Nível d'água" inputMode="decimal" className="mono" value={f.nivelAgua} onChange={(e) => setF((v) => ({ ...v, nivelAgua: e.target.value }))} /></div>
            <label className="text-sm flex flex-col gap-1"><span className="font-medium">Camadas <span className="text-text-3 font-normal">(topo;base;TIPO;descrição por linha)</span></span><textarea rows={3} className="mono rounded border border-line bg-bg p-2 text-xs" value={f.camadas} onChange={(e) => setF((v) => ({ ...v, camadas: e.target.value }))} /></label>
            <label className="text-sm flex flex-col gap-1"><span className="font-medium">Nspt <span className="text-text-3 font-normal">(profundidade;nspt ou IMP)</span></span><textarea rows={4} className="mono rounded border border-line bg-bg p-2 text-xs" value={f.leituras} onChange={(e) => setF((v) => ({ ...v, leituras: e.target.value }))} /></label>
            <Botao pequeno disabled={!edita} carregando={cadastrar.isPending} onClick={() => cadastrar.mutate(f)}>Cadastrar</Botao>
          </Cartao>
        </>}
        {aba === "boletim" && <>
          <Cartao titulo="Extrações de boletim" className="lg:col-span-2" descricao="A IA lê o PDF e aponta página/trecho; só grava após confirmação humana.">
            {extr.isLoading ? <Carregando /> : <Tabela linhas={extr.data ?? []} chave={(x) => x.id} vazio={<Vazio titulo="Nenhum boletim enviado" />} colunas={[
              { titulo: "Arquivo", celula: (x) => <span className="mono text-xs">{x.chaveS3.split("/").pop()}</span> }, { titulo: "Estado", celula: (x) => <span className="flex gap-1"><EstadoEtiqueta estado={x.estado} /><EtiquetaIa /></span> },
              { titulo: "Dados / citações", celula: (x) => <details className="text-xs"><summary className="cursor-pointer">ver</summary><pre className="mono whitespace-pre-wrap max-h-40 overflow-auto">{x.dadosJson}</pre><pre className="mono whitespace-pre-wrap text-text-3 max-h-24 overflow-auto">{x.citacoesJson}</pre>{x.mensagemErro && <p className="text-danger">{x.mensagemErro}</p>}</details> },
              { titulo: "", celula: (x) => edita && (x.estado === "GERADO_POR_IA" || x.estado === "EM_REVISAO") ? <div className="flex gap-1"><Botao pequeno onClick={() => decidirExtr.mutate({ id: x.id, acao: "confirmar", x: 0, y: 0 })}>Confirmar</Botao><Botao pequeno variante="fantasma" onClick={() => decidirExtr.mutate({ id: x.id, acao: "rejeitar" })}>Rejeitar</Botao></div> : null },
            ]} />}
          </Cartao>
          <Cartao titulo="Enviar boletim (PDF)">
            <EnvioBoletim furos={furos.data ?? []} enviar={(furoId, arquivo) => enviarBoletim.mutate({ furoId, arquivo })} ocupado={enviarBoletim.isPending} habilitado={edita} />
          </Cartao>
        </>}
        {aba === "perfil" && <Cartao titulo="Perfil estratigráfico interpolado" className="lg:col-span-3" descricao="Interpolação pelo furo mais próximo do ponto informado.">
          <div className="flex gap-2 items-end"><Entrada rotulo="x (m)" inputMode="decimal" className="mono" value={perfilXY.x} onChange={(e) => setPerfilXY((v) => ({ ...v, x: e.target.value }))} /><Entrada rotulo="y (m)" inputMode="decimal" className="mono" value={perfilXY.y} onChange={(e) => setPerfilXY((v) => ({ ...v, y: e.target.value }))} /></div>
          {perfil.isLoading ? <Carregando /> : perfil.error ? <Aviso tom="aviso">Sem furos confirmados próximos.</Aviso> : perfil.data ? <PerfilSvg p={perfil.data} /> : null}
        </Cartao>}
        {aba === "fundacoes" && <>
          <Cartao titulo="Dimensionamentos de fundação" className="lg:col-span-2" acoes={eng && <div className="flex gap-2 items-end"><Selecao rotulo="Análise" value={analiseId} onChange={(e) => setAnaliseId(e.target.value)} vazio="Escolher…" opcoes={(analises.data ?? []).filter((a) => a.estado === "CONCLUIDO").map((a) => ({ valor: a.id, rotulo: `v${a.versaoModelo} · ${data(a.criadaEm)}` }))} /><Botao pequeno icone={<Layers3 className="size-4" aria-hidden />} disabled={!analiseId} carregando={dimensionar.isPending} onClick={() => dimensionar.mutate({ analiseId })}>Dimensionar</Botao></div>}>
            {fund.isLoading ? <Carregando /> : <Tabela linhas={fund.data ?? []} chave={(x) => x.id} vazio={<Vazio titulo="Nenhum dimensionamento" descricao="Precisa de uma análise concluída (reações) e de furos confirmados (perfil de solo)." />} colunas={[
              { titulo: "Quando", celula: (x) => data(x.criadoEm ?? (x["decididoEm"] as string)) }, { titulo: "Estado", celula: (x) => <EstadoEtiqueta estado={x.estado} /> }, { titulo: "Solução", celula: (x) => String(x["solucaoGeral"] ?? x["mensagemErro"] ?? "—") },
              { titulo: "", celula: (x) => <div className="flex gap-1"><Botao pequeno variante="secundario" onClick={() => setFundSel(x.id)}>Propostas</Botao>{eng && x.estado === "CALCULADO" && <><Botao pequeno onClick={() => decidirFund.mutate({ id: x.id, acao: "aprovar" })}>Aprovar</Botao><Botao pequeno variante="fantasma" onClick={() => decidirFund.mutate({ id: x.id, acao: "rejeitar" })}>Rejeitar</Botao></>}{eng && x.estado === "APROVADO" && <Botao pequeno onClick={() => decidirFund.mutate({ id: x.id, acao: "aplicado" })}>Aplicar no modelo</Botao>}</div> },
            ]} />}
            {fundSel && (propostas.isLoading ? <Carregando /> : <Tabela linhas={propostas.data ?? []} chave={(p) => p.id} colunas={[
              { titulo: "Pilar", celula: (p) => p.pilarRotulo ?? p.pilarId }, { titulo: "Tipo", celula: (p) => <Etiqueta tom="marca">{p.tipo}</Etiqueta> }, { titulo: "bx × by × h (m)", celula: (p) => <span className="mono">{numero(p.bx)} × {numero(p.by)} × {numero(p.h)}</span> }, { titulo: "Carga (kN)", celula: (p) => numero(p.cargaKn, 1), numerica: true },
              { titulo: "Estacas", celula: (p) => p.estacas ? `${p.estacas} × Ø${numero(p.diametroEstaca)} m · L ${numero(p.comprimentoEstaca)} m` : "—" }, { titulo: "Verifica", celula: (p) => p.verifica ? <Etiqueta tom="sucesso">verifica</Etiqueta> : <Etiqueta tom="perigo">não verifica</Etiqueta> },
            ]} />)}
          </Cartao>
          <Cartao titulo="Memória de cálculo" descricao="Método (Aoki-Velloso / Décourt-Quaresma / tensão admissível) e fatores de segurança por proposta.">
            {fundSel && propostas.data?.length ? <ul className="text-xs flex flex-col gap-2">{propostas.data.map((p) => <li key={p.id}><p className="font-medium">{p.pilarRotulo ?? p.pilarId} · {p.tipo}</p><pre className="mono whitespace-pre-wrap text-text-2 max-h-32 overflow-auto">{p.memoriaJson}</pre></li>)}</ul> : <p className="text-sm text-text-3">Escolha um dimensionamento e clique em Propostas.</p>}
          </Cartao>
        </>}
      </div>
    </>
  );
}

function EnvioBoletim({ furos, enviar, ocupado, habilitado }: { furos: Furo[]; enviar: (furoId: string, arquivo: File) => void; ocupado: boolean; habilitado: boolean }) {
  const [furoId, setFuroId] = useState(""); const [arquivo, setArquivo] = useState<File | null>(null);
  return (
    <>
      <Selecao rotulo="Furo" value={furoId} onChange={(e) => setFuroId(e.target.value)} vazio="Escolher…" opcoes={furos.map((x) => ({ valor: x.id, rotulo: String(x["codigo"] ?? x.nome ?? x.id) }))} />
      <label className="flex flex-col gap-1 text-sm"><span className="font-medium">Arquivo PDF</span><input type="file" accept="application/pdf" onChange={(e) => setArquivo(e.target.files?.[0] ?? null)} className="text-sm" /></label>
      <Botao pequeno icone={<FileUp className="size-4" aria-hidden />} disabled={!habilitado || !furoId || !arquivo} carregando={ocupado} onClick={() => furoId && arquivo && enviar(furoId, arquivo)}>Enviar e extrair</Botao>
      <p className="text-xs text-text-3">O original fica no S3 (URL pré-assinada); a extração entra como rascunho de IA.</p>
    </>
  );
}

function PerfilSvg({ p }: { p: Record<string, unknown> }) {
  const camadas = (p["camadas"] as { cotaTopo: number; cotaBase: number; tipo: string; descricao?: string }[]) ?? [];
  const leituras = (p["leituras"] as { profundidade: number; nspt: number; impenetravel: boolean }[]) ?? [];
  if (!camadas.length) return <p className="text-sm text-text-3">Sem camadas.</p>;
  const topo = Math.max(...camadas.map((c) => c.cotaTopo)), base = Math.min(...camadas.map((c) => c.cotaBase)); const esc = 160 / Math.max(1, topo - base);
  const cor: Record<string, string> = { ARGILA: "#B45309", AREIA: "#F0B000", SILTE: "#9CA3AF", ROCHA: "#374151", ATERRO: "#7C4A1E" };
  return (
    <div className="flex gap-4 flex-wrap items-start">
      <svg role="img" aria-label="Perfil estratigráfico" viewBox="0 0 260 180" className="w-72 h-48">
        {camadas.map((c, i) => <g key={i}><rect x="10" y={(topo - c.cotaTopo) * esc + 5} width="80" height={(c.cotaTopo - c.cotaBase) * esc} fill={cor[c.tipo] ?? "#D1D5DB"} stroke="#111827" /><text x="96" y={(topo - c.cotaTopo) * esc + 15} fontSize="9" fill="var(--text)">{c.tipo} {c.descricao ?? ""} ({c.cotaTopo} a {c.cotaBase} m)</text></g>)}
        {leituras.map((l, i) => <g key={i}><circle cx={200 + Math.min(l.nspt, 50)} cy={(l.profundidade) * esc + 5} r="2.5" fill="var(--brand)" /><text x={204 + Math.min(l.nspt, 50)} y={l.profundidade * esc + 8} fontSize="8" fill="var(--text-3)">{l.impenetravel ? "IMP" : l.nspt}</text></g>)}
        <text x="200" y="175" fontSize="8" fill="var(--text-3)">Nspt →</text>
      </svg>
      <dl className="text-sm"><dt className="text-text-3">Furo mais próximo</dt><dd className="mono">{String(p["furoMaisProximo"] ?? "—")} · {numero(p["distancia"] as number)} m</dd></dl>
    </div>
  );
}
