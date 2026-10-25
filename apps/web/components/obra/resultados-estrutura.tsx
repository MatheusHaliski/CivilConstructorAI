"use client";
import { useMemo, useState } from "react";
import { numero } from "@/lib/formatos";
import { nomeVerificacao } from "@/lib/dados";
import { Aviso, Etiqueta, Selecao, Tabela } from "@/components/ui";

/** espelha SolverMef.Esforco */
type Esforco = { s: number; n: number; vy: number; vz: number; t: number; my: number; mz: number; flecha: number };
/** espelha SolverMef.ResultadoBarra */
export type Barra = { barra: number; elementoId: string; rotulo?: string; comprimento: number; pontos?: Esforco[]; nMax: number; nMin?: number; vMax: number; mMax: number; mMin: number; mzMaxAbs?: number; tMaxAbs?: number; flechaMax: number; noI?: number; noJ?: number; trelica?: boolean };
type Equilibrio = { somaCargas: number[]; somaReacoes: number[]; residuoRelativo: number; ok: boolean };
type ResultadoCombinacao = { combinacao: { nome: string; estadoLimite: string; fatores: Record<string, number> }; barras: Barra[]; reacoes: { no: number; fx: number; fy: number; fz: number; mx: number; my: number; mz: number }[]; deslocamentoMax: number; deslocamentos?: number[][]; equilibrio?: Equilibrio };
type AvisoModelo = { tipo: string; elementoId?: string; mensagem: string };
/** espelha SolverMef.ResultadoAnalise */
export type ResultadoAnalise = { nos: number; barras: number; porCombinacao: Record<string, ResultadoCombinacao>; coordenadas?: number[][]; avisos?: AvisoModelo[]; metadados?: { natureza?: string; metodo?: string; hipoteses?: string[]; unidades?: Record<string, string>; convencao?: string; discretizacao?: string } };

/** espelha Verificacao */
export type Verificacao = { codigo: string; nome: string; etapa: string; estado: string; referencia?: string; evidencia?: string; motivo?: string };
/** espelha Detalhamento.Posicao */
type Posicao = { posicao: string; elementoId: string; rotuloElemento?: string; funcao: string; categoria: string; diametroMm: number; quantidade: number; comprimentoUnitarioM: number; forma: string; massaKg: number; regra: string };
/** espelha DimensionamentoService.ResultadoElemento */
export type ResultadoElemento = {
  elementoId: string; rotulo?: string; tipo: string; estado?: string; verifica: boolean; motivo?: string; mdKnm: number; vdKn: number; ndKn: number; armaduraLongitudinal?: string; estribos?: string;
  asCm2: number; acoKg: number; memoria: string[]; verificacoes?: Verificacao[]; detalhamento?: { posicoes: Posicao[]; massaLiquidaKg: number; limitacoes: string[] }; material?: string;
  asInferiorCm2?: number; asSuperiorCm2?: number; massaPerfilKg?: number; estadoDetalhamento?: string;
};
/** espelha BaseCalculo.Snapshot */
type BaseCalculo = { finalidade?: string; sistemaConstrutivo?: string; classeAgressividade?: string; agregado?: string; versaoCatalogo?: string; referencias?: string[]; uf?: string; municipio?: string; naoInformado?: string[]; registradaEm?: string };

const GRANDEZAS = [
  { chave: "n", rotulo: "Esforço normal N", unidade: "kN" }, { chave: "vy", rotulo: "Cortante Vy", unidade: "kN" }, { chave: "vz", rotulo: "Cortante Vz", unidade: "kN" },
  { chave: "t", rotulo: "Torçor T", unidade: "kN·m" }, { chave: "my", rotulo: "Momento My", unidade: "kN·m" }, { chave: "mz", rotulo: "Momento Mz", unidade: "kN·m" }, { chave: "flecha", rotulo: "Deslocamento transversal (flecha local)", unidade: "mm" },
] as const;
type Grandeza = (typeof GRANDEZAS)[number]["chave"];

export function EtiquetaVerificacao({ estado }: { estado?: string }) {
  const e = nomeVerificacao[estado ?? "NAO_VERIFICADA"] ?? nomeVerificacao.NAO_VERIFICADA!;
  return <Etiqueta tom={e.tom}>{e.rotulo}</Etiqueta>;
}

export function BaseDeCalculo({ json }: { json?: string | null }) {
  const b = useMemo<BaseCalculo | null>(() => { try { return json ? JSON.parse(json) : null; } catch { return null; } }, [json]);
  if (!b) return <p className="text-xs text-text-3">Resultado anterior ao registro da base de cálculo: finalidade, CAA, agregado e versão do catálogo não foram gravados com ele.</p>;
  return (
    <details className="text-xs">
      <summary className="cursor-pointer">Base de cálculo gravada com este resultado (catálogo {b.versaoCatalogo ?? "—"})</summary>
      <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-0.5 mt-1">
        <dt className="text-text-3">Finalidade</dt><dd>{b.finalidade ?? "não informada"}</dd>
        <dt className="text-text-3">Sistema construtivo</dt><dd>{b.sistemaConstrutivo ?? "não informado"}</dd>
        <dt className="text-text-3">CAA</dt><dd>{b.classeAgressividade ?? "não informada"}</dd>
        <dt className="text-text-3">Agregado (αE)</dt><dd>{b.agregado ?? "não informado (adota-se αE = 1,0)"}</dd>
        <dt className="text-text-3">Local</dt><dd>{[b.municipio, b.uf].filter(Boolean).join(" / ") || "não informado"}</dd>
        <dt className="text-text-3">Referências dos motores</dt><dd>{(b.referencias ?? []).join(" · ") || "—"}</dd>
      </dl>
      {!!b.naoInformado?.length && <p className="text-warning mt-1">Não informado na base: {b.naoInformado.join(", ")}.</p>}
    </details>
  );
}

/** diagrama de uma grandeza ao longo de uma barra, com eixo, extremos, unidade e convenção */
function DiagramaBarra({ barra, grandeza }: { barra: Barra; grandeza: Grandeza }) {
  const g = GRANDEZAS.find((x) => x.chave === grandeza)!;
  const pts = (barra.pontos ?? []).map((p) => ({ s: p.s, v: grandeza === "flecha" ? p.flecha * 1000 : p[grandeza] }));
  if (pts.length < 2) return <p className="text-xs text-text-3">Esta barra não tem pontos amostrados.</p>;
  const W = 520, H = 180, m = 36;
  const p0 = pts[0]!, pn = pts[pts.length - 1]!;
  const L = barra.comprimento || pn.s || 1;
  const vmax = Math.max(...pts.map((p) => Math.abs(p.v)), 1e-9);
  const x = (s: number) => m + (s / L) * (W - 2 * m);
  const y = (v: number) => H / 2 - (v / vmax) * (H / 2 - 24);
  const pmax = pts.reduce((a, p) => (p.v > a.v ? p : a), p0), pmin = pts.reduce((a, p) => (p.v < a.v ? p : a), p0);
  const caminho = `M ${x(p0.s)} ${y(0)} ` + pts.map((p) => `L ${x(p.s)} ${y(p.v)}`).join(" ") + ` L ${x(pn.s)} ${y(0)} Z`;
  return (
    <figure className="flex flex-col gap-1">
      <svg role="img" aria-label={`${g.rotulo} ao longo da barra ${barra.rotulo ?? barra.barra}: máximo ${numero(pmax.v, 2)} ${g.unidade} em s = ${numero(pmax.s, 2)} m; mínimo ${numero(pmin.v, 2)} ${g.unidade} em s = ${numero(pmin.s, 2)} m`} viewBox={`0 0 ${W} ${H}`} className="w-full max-w-2xl h-44">
        <path d={caminho} fill="var(--brand-soft)" stroke="var(--brand)" strokeWidth="1.5" />
        <line x1={x(0)} x2={x(L)} y1={y(0)} y2={y(0)} stroke="var(--text-2)" strokeWidth="2" />
        <text x={x(0)} y={y(0) + 14} fontSize="10" fill="var(--text-3)">I (s = 0)</text>
        <text x={x(L)} y={y(0) + 14} fontSize="10" fill="var(--text-3)" textAnchor="end">J (s = {numero(L, 2)} m)</text>
        <circle cx={x(pmax.s)} cy={y(pmax.v)} r="3" fill="var(--brand)" /><text x={x(pmax.s)} y={y(pmax.v) - 6} fontSize="10" textAnchor="middle" fill="var(--text)">{numero(pmax.v, 2)}</text>
        {pmin !== pmax && <><circle cx={x(pmin.s)} cy={y(pmin.v)} r="3" fill="var(--obra-andamento)" /><text x={x(pmin.s)} y={y(pmin.v) + 14} fontSize="10" textAnchor="middle" fill="var(--text)">{numero(pmin.v, 2)}</text></>}
      </svg>
      <figcaption className="text-xs text-text-3">{g.rotulo} em {g.unidade}, barra {barra.rotulo ?? barra.barra}{barra.trelica ? " (barra de treliça: só esforço normal)" : ""}. Valores positivos para cima do eixo; sinal pela convenção dos eixos locais abaixo. Escala vertical: maior |valor| = {numero(vmax, 2)} {g.unidade}.</figcaption>
    </figure>
  );
}

/** deformada a partir dos deslocamentos nodais, amplificada; retas entre nós (sem interpolação da elástica) */
function Deformada({ r, comb }: { r: ResultadoAnalise; comb: ResultadoCombinacao }) {
  const [vista, setVista] = useState<"XZ" | "YZ" | "XY">("XZ");
  const coords = r.coordenadas, desl = comb.deslocamentos;
  if (!coords?.length || !desl?.length) return <p className="text-xs text-text-3">Esta análise não gravou coordenadas e deslocamentos nodais (análise anterior à auditoria 2026-10): refaça a análise para ver a deformada.</p>;
  const [ia, ib] = vista === "XZ" ? [0, 2] : vista === "YZ" ? [1, 2] : [0, 1];
  const co = (n: number, i: number) => coords[n]?.[i] ?? 0, de = (n: number, i: number) => desl[n]?.[i] ?? 0;
  const us = coords.map((_, n) => co(n, ia)), vs = coords.map((_, n) => co(n, ib));
  const uMin = Math.min(...us), uMax = Math.max(...us), vMin = Math.min(...vs), vMax = Math.max(...vs);
  const tamanho = Math.max(uMax - uMin, vMax - vMin, 1);
  const dMax = Math.max(...desl.map((_, n) => Math.hypot(de(n, 0), de(n, 1), de(n, 2))), 1e-12);
  const fator = Math.max(1, Math.round((0.08 * tamanho) / dMax));
  const W = 520, H = 300, m = 24, k = Math.min((W - 2 * m) / Math.max(uMax - uMin, 1e-6), (H - 2 * m) / Math.max(vMax - vMin, 1e-6));
  const px = (u: number) => m + (u - uMin) * k, py = (v: number) => H - m - (v - vMin) * k;
  const barras = comb.barras.filter((b) => b.noI != null && b.noJ != null);
  return (
    <figure className="flex flex-col gap-1">
      <Selecao rotulo="Vista" value={vista} onChange={(e) => setVista(e.target.value as typeof vista)} opcoes={[{ valor: "XZ", rotulo: "Elevação XZ" }, { valor: "YZ", rotulo: "Elevação YZ" }, { valor: "XY", rotulo: "Planta XY" }]} />
      <svg role="img" aria-label={`Deformada da combinação ${comb.combinacao.nome}, vista ${vista}, amplificada ${fator} vezes; deslocamento máximo ${numero(comb.deslocamentoMax * 1000, 2)} mm`} viewBox={`0 0 ${W} ${H}`} className="w-full max-w-2xl h-72 bg-surface rounded">
        {barras.map((b) => <line key={`o${b.barra}`} x1={px(co(b.noI!, ia))} y1={py(co(b.noI!, ib))} x2={px(co(b.noJ!, ia))} y2={py(co(b.noJ!, ib))} stroke="var(--text-3)" strokeDasharray="4 3" strokeWidth="1" />)}
        {barras.map((b) => { const a = b.noI!, c = b.noJ!; return <line key={`d${b.barra}`} x1={px(co(a, ia) + fator * de(a, ia))} y1={py(co(a, ib) + fator * de(a, ib))} x2={px(co(c, ia) + fator * de(c, ia))} y2={py(co(c, ib) + fator * de(c, ib))} stroke="var(--brand)" strokeWidth="2" />; })}
      </svg>
      <figcaption className="text-xs text-text-3">Tracejado: geometria original · contínuo: deformada amplificada <strong>×{fator}</strong> (escala gráfica, não real). Deslocamento nodal máximo {numero(comb.deslocamentoMax * 1000, 2)} mm. Retas entre nós: a curvatura dentro de cada barra não é desenhada.</figcaption>
    </figure>
  );
}

/** RF05: resultados calculados (não medidos) da análise, com convenção, unidades, avisos e equilíbrio */
export function ResultadosAnalise({ r, estado, versaoModelo, baseCalculoJson }: { r: ResultadoAnalise; estado?: string; versaoModelo?: number; baseCalculoJson?: string | null }) {
  const nomes = Object.keys(r.porCombinacao ?? {});
  const [comb, setComb] = useState(nomes[0] ?? "");
  const [grandeza, setGrandeza] = useState<Grandeza>("my");
  const atual = r.porCombinacao?.[comb];
  const [barraSel, setBarraSel] = useState<number | null>(null);
  if (!atual) return <p className="text-sm text-text-3">Sem resultados por combinação.</p>;
  const barra = atual.barras.find((b) => b.barra === barraSel) ?? atual.barras[0];
  const md = r.metadados;
  const eq = atual.equilibrio;
  return (
    <div className="flex flex-col gap-3">
      {estado === "OBSOLETO" && <Aviso tom="aviso" titulo="Resultado desatualizado">O modelo, as cargas ou a base de cálculo mudaram depois desta análise (v{versaoModelo ?? "—"}). Os valores abaixo não representam o modelo atual; solicite uma nova análise.</Aviso>}
      <Aviso tom="info" titulo="Esforços calculados, não medidos">{md?.metodo ?? "MEF linear"}. São solicitações obtidas do modelo analítico (modelo v{versaoModelo ?? "—"}); nenhum valor vem de sensores. Não é verificação de resistência: isso é feito no dimensionamento.</Aviso>
      <BaseDeCalculo json={baseCalculoJson} />
      {md && <details className="text-xs"><summary className="cursor-pointer">Hipóteses, unidades e convenção de sinais</summary>
        <ul className="list-disc pl-5 mt-1">{(md.hipoteses ?? []).map((h) => <li key={h}>{h}</li>)}</ul>
        <p className="mt-1"><strong>Unidades:</strong> {Object.entries(md.unidades ?? {}).map(([k, v]) => `${k} em ${v}`).join("; ")}.</p>
        <p className="mt-1"><strong>Convenção:</strong> {md.convencao}</p>
        <p className="mt-1"><strong>Discretização:</strong> {md.discretizacao}</p>
      </details>}
      {!!r.avisos?.length && <details className="text-xs" open={r.avisos.some((a) => a.tipo === "EXCLUIDO")}><summary className="cursor-pointer">{r.avisos.length} aviso(s) do modelo analítico (exclusões, aproximações e premissas)</summary>
        <ul className="mt-1 flex flex-col gap-1">{r.avisos.map((a, i) => <li key={i} className="flex gap-2 items-start"><Etiqueta tom={a.tipo === "EXCLUIDO" ? "perigo" : a.tipo === "APROXIMACAO" ? "aviso" : "neutro"}>{a.tipo.toLowerCase()}</Etiqueta><span>{a.mensagem}</span></li>)}</ul>
      </details>}
      <div className="flex items-end gap-3 flex-wrap">
        <Selecao rotulo="Combinação" value={comb} onChange={(e) => setComb(e.target.value)} opcoes={nomes.map((n) => ({ valor: n, rotulo: `${n} (${r.porCombinacao[n]?.combinacao.estadoLimite ?? ""})` }))} />
        <Selecao rotulo="Grandeza" value={grandeza} onChange={(e) => setGrandeza(e.target.value as Grandeza)} opcoes={GRANDEZAS.map((g) => ({ valor: g.chave, rotulo: `${g.rotulo} (${g.unidade})` }))} />
        <Selecao rotulo="Barra" value={String(barra?.barra ?? "")} onChange={(e) => setBarraSel(Number(e.target.value))} opcoes={atual.barras.map((b) => ({ valor: String(b.barra), rotulo: `${b.rotulo ?? b.barra}${b.trelica ? " (treliça)" : ""}` }))} />
      </div>
      <p className="text-xs text-text-3">{r.nos} nós · {r.barras} barras · fatores {Object.entries(atual.combinacao.fatores ?? {}).map(([k, v]) => `${k}×${v}`).join(" + ")} · deslocamento nodal máx. {numero(atual.deslocamentoMax * 1000, 2)} mm</p>
      {eq ? <p className={`text-xs ${eq.ok ? "text-success" : "text-danger"}`}>Equilíbrio global: Σ cargas ({eq.somaCargas.slice(0, 3).map((v) => numero(v, 2)).join("; ")}) kN · Σ reações ({eq.somaReacoes.slice(0, 3).map((v) => numero(v, 2)).join("; ")}) kN · resíduo relativo {eq.residuoRelativo.toExponential(1)} {eq.ok ? "(conferido)" : "(NÃO conferido)"}</p> : <p className="text-xs text-text-3">Equilíbrio global não gravado nesta análise.</p>}
      {barra && <DiagramaBarra barra={barra} grandeza={grandeza} />}
      <Deformada r={r} comb={atual} />
      <Tabela linhas={atual.barras} chave={(b) => String(b.barra)} colunas={[{ titulo: "Barra", celula: (b) => <span>{b.rotulo ?? b.barra}{b.trelica && <span className="text-xs text-text-3"> treliça</span>}</span> }, { titulo: "L (m)", celula: (b) => numero(b.comprimento), numerica: true }, { titulo: "N máx / mín (kN)", celula: (b) => `${numero(b.nMax)} / ${numero(b.nMin ?? b.nMax)}`, numerica: true }, { titulo: "|V| máx (kN)", celula: (b) => numero(b.vMax), numerica: true }, { titulo: "My máx / mín (kN·m)", celula: (b) => `${numero(b.mMax)} / ${numero(b.mMin)}`, numerica: true }, { titulo: "|Mz| máx (kN·m)", celula: (b) => numero(b.mzMaxAbs ?? 0), numerica: true }, { titulo: "Flecha local (mm)", celula: (b) => numero(b.flechaMax * 1000, 2), numerica: true }]} />
    </div>
  );
}

/** RF07: análise (solicitações) → verificação → dimensionamento → detalhamento, separados e com estado próprio */
export function ResultadosDimensionamento({ linhas, estado, baseCalculoJson }: { linhas: ResultadoElemento[]; estado?: string; baseCalculoJson?: string | null }) {
  const [sel, setSel] = useState<string | null>(null);
  const atual = linhas.find((l) => l.elementoId === sel);
  const posicoes = linhas.flatMap((l) => l.detalhamento?.posicoes ?? []);
  const nBarras = posicoes.reduce((a, p) => a + p.quantidade, 0);
  const comp = posicoes.reduce((a, p) => a + p.quantidade * p.comprimentoUnitarioM, 0);
  const massa = posicoes.reduce((a, p) => a + p.massaKg, 0);
  const contagem = linhas.reduce<Record<string, number>>((a, l) => { const e = l.estado ?? (l.verifica ? "ATENDIDA" : "NAO_ATENDIDA"); a[e] = (a[e] ?? 0) + 1; return a; }, {});
  return (
    <div className="flex flex-col gap-3">
      {estado === "OBSOLETO" && <Aviso tom="aviso" titulo="Resultado desatualizado">A análise, o modelo ou a base de cálculo mudaram depois deste dimensionamento. Ele não pode ser aprovado; dimensione de novo.</Aviso>}
      <BaseDeCalculo json={baseCalculoJson} />
      <div className="flex gap-2 flex-wrap" aria-label="Resumo por estado">{Object.entries(contagem).map(([e, n]) => <span key={e} className="flex items-center gap-1 text-xs"><EtiquetaVerificacao estado={e} /> {n}</span>)}</div>
      {(contagem.NAO_VERIFICADA || contagem.DADOS_INSUFICIENTES) ? <p className="text-xs text-warning">Há elementos com verificações não executadas ou sem dados: o dimensionamento está incompleto e não deve ser lido como “sem falhas”.</p> : null}
      <Tabela linhas={linhas} chave={(r) => r.elementoId} colunas={[
        { titulo: "Elemento", celula: (r) => <button type="button" className="underline text-left" onClick={() => setSel(r.elementoId)}>{r.rotulo ?? r.elementoId} <span className="text-xs text-text-3">{r.tipo.toLowerCase()}</span></button> },
        { titulo: "Estado", celula: (r) => <span className="flex flex-col gap-0.5"><EtiquetaVerificacao estado={r.estado ?? (r.verifica ? "ATENDIDA" : "NAO_ATENDIDA")} />{r.motivo && <span className="text-xs text-text-2 max-w-xs">{r.motivo}</span>}</span> },
        { titulo: "Material", celula: (r) => <span className="mono text-xs">{r.material ?? "—"}</span> },
        { titulo: "Solicitações de cálculo", celula: (r) => <span className="mono text-xs">Md {numero(r.mdKnm, 1)} kN·m · Vd {numero(r.vdKn, 1)} kN · Nd {numero(r.ndKn, 1)} kN</span> },
        { titulo: "Armadura (dimensionamento)", celula: (r) => <span className="mono text-xs">{r.armaduraLongitudinal ?? "—"}<br />{r.estribos ?? ""}{r.asInferiorCm2 != null && <><br />As,inf {numero(r.asInferiorCm2, 2)} · As,sup {numero(r.asSuperiorCm2 ?? 0, 2)} cm²</>}</span> },
        { titulo: "Detalhamento", celula: (r) => <EtiquetaVerificacao estado={r.estadoDetalhamento ?? "NAO_VERIFICADA"} /> },
        { titulo: "Massa (kg)", celula: (r) => numero(r.massaPerfilKg ?? r.acoKg, 1), numerica: true },
      ]} />
      {atual && <section aria-label={`Verificações de ${atual.rotulo ?? atual.elementoId}`} className="flex flex-col gap-2 border-t border-line-soft pt-3">
        <h3 className="font-medium">Verificações de {atual.rotulo ?? atual.elementoId}</h3>
        <Tabela linhas={atual.verificacoes ?? []} chave={(v) => v.codigo} vazio={<p className="text-xs text-text-3">Sem verificações registradas (resultado anterior à auditoria 2026-10).</p>} colunas={[
          { titulo: "Etapa", celula: (v) => v.etapa.toLowerCase() }, { titulo: "Verificação", celula: (v) => v.nome }, { titulo: "Estado", celula: (v) => <EtiquetaVerificacao estado={v.estado} /> },
          { titulo: "Evidência / motivo", celula: (v) => <span className="mono text-xs">{v.evidencia ?? ""}{v.motivo && <span className="block text-text-2 font-sans">{v.motivo}</span>}</span> }, { titulo: "Referência", celula: (v) => <span className="text-xs">{v.referencia ?? "—"}</span> },
        ]} />
        {!!atual.detalhamento?.limitacoes?.length && <ul className="text-xs text-text-3 list-disc pl-5">{atual.detalhamento.limitacoes.map((l) => <li key={l}>{l}</li>)}</ul>}
        <details className="text-xs"><summary className="cursor-pointer">Memória de cálculo</summary><ul className="mono">{atual.memoria.map((m, i) => <li key={i}>{m}</li>)}</ul></details>
      </section>}
      <section aria-label="Quadro de armaduras" className="flex flex-col gap-2 border-t border-line-soft pt-3">
        <h3 className="font-medium">Quadro de armaduras por posição</h3>
        <p className="text-xs text-text-3">Número de barras, comprimento e massa em colunas separadas. Massa nominal = comprimento × π·φ²/4 × 7850 kg/m³; sem perdas de corte (somadas no orçamento, se houver).</p>
        <Tabela linhas={posicoes} chave={(p) => `${p.elementoId}-${p.posicao}`} vazio={<p className="text-xs text-text-3">Nenhuma posição detalhada.</p>} colunas={[
          { titulo: "Pos.", celula: (p) => <span className="mono">{p.posicao}</span> }, { titulo: "Elemento", celula: (p) => p.rotuloElemento ?? p.elementoId }, { titulo: "Função", celula: (p) => p.funcao },
          { titulo: "Aço / φ", celula: (p) => `${p.categoria} Ø ${numero(p.diametroMm, 1)} mm` }, { titulo: "Qtd.", celula: (p) => p.quantidade, numerica: true },
          { titulo: "Compr. unit. (m)", celula: (p) => numero(p.comprimentoUnitarioM, 2), numerica: true }, { titulo: "Compr. total (m)", celula: (p) => numero(p.quantidade * p.comprimentoUnitarioM, 2), numerica: true },
          { titulo: "Massa (kg)", celula: (p) => numero(p.massaKg, 2), numerica: true }, { titulo: "Forma / regra", celula: (p) => <span className="text-xs">{p.forma} · {p.regra}</span> },
        ]} />
        <p className="text-xs"><strong>Totais:</strong> {nBarras} barras · {numero(comp, 1)} m · {numero(massa, 1)} kg (massa líquida, sem perdas)</p>
      </section>
    </div>
  );
}
