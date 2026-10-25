"use client";
import { use, useMemo, useState } from "react";
import { useSearchParams } from "next/navigation";
import { CheckCircle2, Eye, Search, Undo2 } from "lucide-react";
import { CATALOGO_MATERIAIS, ErroComandoVI, ModeloBim, materiaisPersonalizados, type RelatorioAuditoria } from "@cca/notacao-vi";
import { ErroApi, post } from "@/lib/api";
import { useAcao, useDados } from "@/lib/dados";
import { editaModelo, usePapeis } from "@/lib/sessao";
import type { ResultadoComando, VisaoModelo } from "@/lib/tipos";
import { ACOES, buscarAcoes, codigoMaterial, montarArgs, viDaAcao, type AcaoConstrutiva, type EntradaAcao, type ErroValidacao, type ValoresAcao } from "@/lib/acoes";
import { deServidor } from "@/lib/motor/servidor";
import { numero } from "@/lib/formatos";
import { Aviso, Botao, Cartao, Etiqueta, Selecao, cx } from "@/components/ui";
import { CabecalhoObra } from "@/components/obra/comuns";

type Previa = { vi: string; criados: { tipo: string; rotulo: string }[]; alterados: number; relatorio: RelatorioAuditoria } | { erro: string; relatorio?: RelatorioAuditoria };

/** RF29 · ações construtivas sem digitar códigos: escolher a ação, preencher por listas, ver a prévia auditada e aplicar (com desfazer). */
export default function PaginaAcoes({ params }: { params: Promise<{ obraId: string }> }) {
  const { obraId } = use(params);
  const busca = useSearchParams();
  const pode = editaModelo(usePapeis(obraId));
  const modelo = useDados<VisaoModelo>(["modelo", obraId], `/obras/${obraId}/modelo`);
  const [termo, setTermo] = useState("");
  const [acaoId, setAcaoId] = useState(busca.get("acao") ?? "MOD.CRIAR.PAREDE");
  const acao = ACOES.find((a) => a.id === acaoId) ?? ACOES[0]!;
  const pavs = useMemo(() => [...(modelo.data?.pavimentos ?? [])].sort((a, b) => a.elevacao - b.elevacao), [modelo.data]);
  const [pavId, setPavId] = useState("");
  const pav = pavId || pavs[0]?.id || "";
  const [valores, setValores] = useState<ValoresAcao>({});
  const [erros, setErros] = useState<ErroValidacao[]>([]);
  const [previa, setPrevia] = useState<Previa | null>(null);
  const [aplicados, setAplicados] = useState<{ id: string; vi: string }[]>([]);
  const lista = buscarAcoes(termo);

  const local = useMemo(() => (modelo.data ? new ModeloBim(deServidor(modelo.data)) : null), [modelo.data]);
  const intersecoes = local?.intersecoesDeEixos() ?? [];
  const elementosPav = (modelo.data?.elementos ?? []).filter((e) => e["pavimentoId"] === pav);
  const rot = (e: Record<string, unknown>) => String(e["rotulo"] ?? e["id"]).slice(0, 18);

  const escolher = (a: AcaoConstrutiva) => { setAcaoId(a.id); setValores({}); setErros([]); setPrevia(null); };
  const preparar = () => {
    const { args, erros } = montarArgs(acao, valores);
    setErros(erros);
    if (erros.length || !modelo.data) { setPrevia(null); return null; }
    try {
      const m = new ModeloBim(deServidor(modelo.data));
      const r = m.aplicar(acao.codigoVi, args, { origem: "FORMULARIO", pavimentoId: pav });
      setPrevia({ vi: r.vi, criados: r.diff.criados.map((e) => ({ tipo: e.tipo, rotulo: m.rotuloAutomatico(e) })), alterados: r.diff.alterados.length, relatorio: r.relatorio });
      return { args, vi: r.vi };
    } catch (e) {
      setPrevia({ erro: e instanceof Error ? e.message : String(e), relatorio: e instanceof ErroComandoVI ? e.relatorio : undefined });
      return null;
    }
  };
  const aplicar = useAcao(async () => {
    const p = preparar();
    if (!p || !modelo.data) throw new Error("Corrija os campos destacados antes de aplicar.");
    try {
      return await post<ResultadoComando>(`/obras/${obraId}/modelo/comandos`, { pavimentoId: pav, codigo: acao.codigoVi, args: p.args, vi: p.vi, origem: "FORMULARIO", versaoBase: modelo.data.versao });
    } catch (e) {
      if (e instanceof ErroApi && e.status === 409) throw new Error("Outra pessoa alterou o modelo enquanto você preparava a ação. A prévia foi recarregada; confira e aplique de novo.");
      throw e;
    }
  }, { invalidar: [["modelo", obraId], ["quantitativos", obraId]], sucesso: (r) => { setAplicados((x) => [{ id: r.comandoId, vi: r.vi }, ...x]); setPrevia(null); return `Aplicado: versão ${r.versao} do modelo.`; } });
  const desfazer = useAcao((id: string) => post(`/obras/${obraId}/modelo/comandos/${id}/desfazer`), { invalidar: [["modelo", obraId], ["quantitativos", obraId]], sucesso: () => { setAplicados((x) => x.slice(1)); return "Desfeito no servidor (comando inverso registrado)."; } });

  const set = (nome: string, v: ValoresAcao[string]) => { setValores((x) => ({ ...x, [nome]: v })); setPrevia(null); };
  const erroDe = (nome: string) => erros.find((e) => e.campo === nome)?.mensagem;

  return (
    <>
      <CabecalhoObra obraId={obraId} grupo="Ações e códigos" secao="Ações construtivas" descricao="Escolha o que construir, preencha por listas (eixos, paredes, materiais) e confira a prévia auditada antes de gravar. O código VI é gerado para você." />
      {!pode && <Aviso tom="info" className="mb-4">Seu papel nesta obra permite só leitura: você pode montar e conferir a prévia, mas não aplicar.</Aviso>}
      <div className="grid gap-4 xl:grid-cols-[15rem_minmax(0,1fr)_20rem]">
        <Cartao titulo="Ações">
          <label className="relative block"><span className="sr-only">Filtrar ações</span><Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-text-3" aria-hidden />
            <input type="search" value={termo} onChange={(e) => setTermo(e.target.value)} placeholder="parede, coluna, abertura…" className="tap w-full rounded-full border border-line bg-bg pl-9 pr-3 text-sm" /></label>
          <ul className="flex flex-col gap-1" aria-label="Ações disponíveis">
            {lista.map((a) => (
              <li key={a.id}><button type="button" aria-pressed={a.id === acao.id} onClick={() => escolher(a)} className={cx("tap w-full rounded-[var(--radius-sm)] px-3 py-1.5 text-left", a.id === acao.id ? "bg-text text-bg" : "hover:bg-surface")}>
                <span className="block text-sm font-medium">{a.rotulo}</span><span className={cx("mono block text-[11px]", a.id === acao.id ? "text-bg/80" : "text-text-3")}>{a.id} · {a.grupo}</span>
              </button></li>
            ))}
            {!lista.length && <li className="text-sm text-text-3">Nenhuma ação para “{termo}”.</li>}
          </ul>
        </Cartao>

        <Cartao titulo={acao.rotulo} descricao={acao.descricao} acoes={<Etiqueta tom="marca"><span className="mono">{acao.id} v{acao.versao}</span></Etiqueta>}>
          <Selecao rotulo="Pavimento" value={pav} onChange={(e) => { setPavId(e.target.value); setPrevia(null); }} opcoes={pavs.map((p) => ({ valor: p.id, rotulo: `${p.nome} (${p.elevacao >= 0 ? "+" : ""}${p.elevacao.toFixed(2)} m)` }))} />
          <div className="grid gap-3 sm:grid-cols-2">
            {acao.entradas.map((e) => <Campo key={`${acao.id}-${e.nome}`} e={e} valor={valores[e.nome]} erro={erroDe(e.nome)} onChange={(v) => set(e.nome, v)} intersecoes={intersecoes}
              paredes={elementosPav.filter((x) => x["tipo"] === "PAREDE").map((x) => ({ id: String(x["id"]), rotulo: rot(x) }))} pilares={elementosPav.filter((x) => x["tipo"] === "PILAR").map((x) => ({ id: String(x["id"]), rotulo: rot(x) }))} />)}
          </div>
          {acao.dependencias && <p className="text-xs text-text-3">Efeito em dependências: {acao.dependencias}</p>}
          <div className="flex flex-wrap gap-2">
            <Botao variante="secundario" icone={<Eye className="size-4" aria-hidden />} onClick={() => preparar()} disabled={!modelo.data}>Ver prévia</Botao>
            <Botao icone={<CheckCircle2 className="size-4" aria-hidden />} disabled={!pode || !modelo.data} carregando={aplicar.isPending} onClick={() => aplicar.mutate()}>Aplicar no modelo</Botao>
          </div>
          {erros.length > 0 && <Aviso tom="perigo" titulo="Revise os campos"><ul className="list-disc pl-4">{erros.map((x) => <li key={x.campo}>{x.mensagem}</li>)}</ul></Aviso>}
        </Cartao>

        <div className="flex flex-col gap-4">
          <Cartao titulo="Prévia" descricao="Calculada sobre a versão atual do modelo, sem gravar.">
            {!previa ? <p className="text-sm text-text-3">Preencha e clique em Ver prévia.</p> : "erro" in previa ? (
              <Aviso tom="perigo" titulo="O auditor bloquearia esta ação">{previa.erro}{previa.relatorio && <Violacoes r={previa.relatorio} />}</Aviso>
            ) : (
              <div className="flex flex-col gap-2 text-sm">
                <p className="text-xs text-text-3">Código gerado</p><code className="mono block break-all rounded-[var(--radius-sm)] bg-surface p-2 text-xs">{previa.vi}</code>
                <p><strong>{previa.criados.length}</strong> elemento(s) novo(s){previa.alterados ? `, ${previa.alterados} alterado(s)` : ""}: {previa.criados.map((c) => `${c.tipo.toLowerCase()} ${c.rotulo}`).join(", ") || "—"}</p>
                {previa.relatorio.correcoes.length > 0 && <Aviso tom="aviso" titulo="Correções automáticas do auditor"><ul>{previa.relatorio.correcoes.map((c, i) => <li key={i} className="mono text-xs">{c.regra}: {c.campo} {String(c.antes)} → {String(c.depois)}</li>)}</ul></Aviso>}
                <Violacoes r={previa.relatorio} />
              </div>
            )}
          </Cartao>
          <Cartao titulo="Aplicadas nesta sessão" descricao="Desfazer grava o comando inverso no histórico do modelo.">
            {!aplicados.length ? <p className="text-sm text-text-3">Nada aplicado ainda.</p> : <ul className="flex flex-col gap-2">{aplicados.map((a, i) => <li key={a.id} className="flex items-center justify-between gap-2"><code className="mono truncate text-xs">{a.vi}</code>{i === 0 && <Botao pequeno variante="secundario" icone={<Undo2 className="size-4" aria-hidden />} carregando={desfazer.isPending} onClick={() => desfazer.mutate(a.id)}>Desfazer</Botao>}</li>)}</ul>}
          </Cartao>
          <Cartao titulo="Contrato da ação">
            <dl className="grid gap-2 text-xs">
              <div><dt className="text-text-3">Pré-condições</dt><dd>{acao.precondicoes.join(" ") || "—"}</dd></div>
              <div><dt className="text-text-3">Resultado</dt><dd>{acao.resultado}</dd></div>
              <div><dt className="text-text-3">Erros possíveis</dt><dd className="mono">{acao.erros.join(" · ") || "—"}</dd></div>
              <div><dt className="text-text-3">Permissão · desfazer</dt><dd>Editar modelo (arquiteto/engenheiro) · sim, por comando inverso</dd></div>
            </dl>
          </Cartao>
        </div>
      </div>
    </>
  );
}

function Violacoes({ r }: { r: RelatorioAuditoria }) {
  const v = [...r.laco1.violacoes, ...r.laco2.violacoes, ...r.laco2.avisos];
  if (!v.length) return <p className="flex items-center gap-1 text-xs text-success"><CheckCircle2 className="size-4" aria-hidden /> Sem violações nos laços 1 e 2 do auditor.</p>;
  return <ul className="mt-1 flex flex-col gap-1 text-xs">{v.map((x, i) => <li key={i}><Etiqueta tom={x.severidade === "VIOLACAO" ? "perigo" : "aviso"}>{x.codigo}</Etiqueta> {x.mensagem}{x.esperado ? ` (esperado ${x.esperado}, obtido ${x.obtido})` : ""}</li>)}</ul>;
}

function Campo({ e, valor, erro, onChange, intersecoes, paredes, pilares }: { e: EntradaAcao; valor: ValoresAcao[string] | undefined; erro?: string; onChange: (v: ValoresAcao[string]) => void; intersecoes: { nome: string; p: { x: number; y: number } }[]; paredes: { id: string; rotulo: string }[]; pilares: { id: string; rotulo: string }[] }) {
  const sufixo = e.unidade ? ` (${e.unidade})` : "";
  if (e.tipo === "ponto") return <CampoPonto e={e} valor={valor as { x: number; y: number } | undefined} erro={erro} onChange={onChange} intersecoes={intersecoes} />;
  if (e.tipo === "pontos") return <CampoContorno e={e} valor={(valor as { x: number; y: number }[] | undefined) ?? []} erro={erro} onChange={onChange} intersecoes={intersecoes} />;
  if (e.tipo === "material") {
    const ops = [...CATALOGO_MATERIAIS.materiais, ...materiaisPersonalizados()].filter((m) => !e.aplicaA || m.aplicacoes.includes(e.aplicaA));
    return <Selecao rotulo={e.rotulo} erro={erro} value={String(valor ?? e.padrao ?? "")} onChange={(x) => onChange(x.target.value)} opcoes={ops.map((m) => ({ valor: m.codigo, rotulo: `${m.nome}${m.resumo ? ` (${m.resumo})` : ""}${m.personalizado ? " · personalizado" : ""}` }))} ajuda="Propriedades, origem dos valores e referências: Projeto › Materiais." />;
  }
  if (e.tipo === "parede" || e.tipo === "pilar") {
    const ops = e.tipo === "parede" ? paredes : pilares;
    return <Selecao rotulo={e.rotulo} erro={erro} value={String(valor ?? "")} onChange={(x) => onChange(x.target.value)} vazio={ops.length ? "Escolher…" : `Nenhum(a) ${e.tipo} neste pavimento`} opcoes={ops.map((o) => ({ valor: o.id, rotulo: o.rotulo }))} />;
  }
  return (
    <label className="flex min-w-0 flex-col gap-1.5 text-sm">
      <span className="font-medium">{e.rotulo}{sufixo}</span>
      <input inputMode={e.tipo === "texto" ? "text" : "decimal"} aria-invalid={!!erro} value={String(valor ?? e.padrao ?? "")} onChange={(x) => onChange(x.target.value)} className={cx("tap w-full min-w-0 rounded-[var(--radius-sm)] border bg-bg px-3", e.tipo !== "texto" && "mono", erro ? "border-danger" : "border-line")} />
      {e.min != null && <span className="text-xs text-text-3">{e.ajuda ?? `Entre ${e.min} e ${e.max}${e.unidade === "%" ? "%" : ` ${e.unidade ?? ""}`}`}</span>}
      {erro && <span className="text-xs text-danger">{erro}</span>}
    </label>
  );
}

function CampoPonto({ e, valor, erro, onChange, intersecoes }: { e: EntradaAcao; valor?: { x: number; y: number }; erro?: string; onChange: (v: { x: number; y: number }) => void; intersecoes: { nome: string; p: { x: number; y: number } }[] }) {
  const nome = intersecoes.find((i) => valor && Math.abs(i.p.x - valor.x) < 1e-6 && Math.abs(i.p.y - valor.y) < 1e-6)?.nome ?? "";
  return (
    <fieldset className="flex min-w-0 flex-col gap-1.5 text-sm">
      <legend className="font-medium">{e.rotulo} (m)</legend>
      <select aria-label={`${e.rotulo}: interseção de eixos`} value={nome} onChange={(x) => { const i = intersecoes.find((k) => k.nome === x.target.value); if (i) onChange(i.p); }} className={cx("tap w-full min-w-0 rounded-[var(--radius-sm)] border bg-bg px-3", erro ? "border-danger" : "border-line")}>
        <option value="">{intersecoes.length ? "Interseção de eixos…" : "Sem eixos: use coordenadas"}</option>
        {intersecoes.map((i) => <option key={i.nome} value={i.nome}>{i.nome} · ({numero(i.p.x)}; {numero(i.p.y)})</option>)}
      </select>
      <div className="grid grid-cols-2 gap-2">
        {(["x", "y"] as const).map((k) => <input key={k} aria-label={`${e.rotulo}: ${k} em metros`} inputMode="decimal" className="tap mono w-full min-w-0 rounded-[var(--radius-sm)] border border-line bg-bg px-3" placeholder={k}
          value={valor ? String(valor[k]) : ""} onChange={(x) => { const n = Number(x.target.value.replace(",", ".")); onChange({ x: valor?.x ?? 0, y: valor?.y ?? 0, [k]: Number.isFinite(n) ? n : NaN }); }} />)}
      </div>
      {erro && <span className="text-xs text-danger">{erro}</span>}
    </fieldset>
  );
}

function CampoContorno({ e, valor, erro, onChange, intersecoes }: { e: EntradaAcao; valor: { x: number; y: number }[]; erro?: string; onChange: (v: { x: number; y: number }[]) => void; intersecoes: { nome: string; p: { x: number; y: number } }[] }) {
  return (
    <fieldset className="flex min-w-0 flex-col gap-1.5 text-sm sm:col-span-2">
      <legend className="font-medium">{e.rotulo}</legend>
      <div className="flex flex-wrap gap-1">{intersecoes.map((i) => <button key={i.nome} type="button" onClick={() => onChange([...valor, i.p])} className="tap rounded-full border border-line px-3 text-xs hover:bg-surface">+ {i.nome}</button>)}
        {!intersecoes.length && <span className="text-xs text-text-3">Crie a malha de eixos para escolher vértices por interseção.</span>}</div>
      <p className="mono text-xs">{valor.length ? valor.map((p) => `(${numero(p.x)}; ${numero(p.y)})`).join(" → ") : "Nenhum vértice."}</p>
      {valor.length > 0 && <Botao pequeno variante="fantasma" onClick={() => onChange(valor.slice(0, -1))}>Remover último vértice</Botao>}
      {erro && <span className="text-xs text-danger">{erro}</span>}
    </fieldset>
  );
}
