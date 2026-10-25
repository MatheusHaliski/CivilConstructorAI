"use client";
/** Painéis laterais: camadas (VG/LA), propriedades (PR, formulário H5), atalhos (KS), ajuda, auditoria. */
import { CATALOGO, CATALOGO_MATERIAIS, conflitosDeAlias, MODOS_OSNAP, TECLAS_RESERVADAS_NAVEGADOR, type Camada, type Elemento } from "@cca/notacao-vi";
import { useMotor } from "@/lib/motor/estado";
import { Alternar, Botao, Etiqueta, cx } from "@/components/ui";
import { SeletorMaterial } from "./SeletorMaterial";

const CAMADAS: { id: Camada; rotulo: string; cor: string }[] = [
  { id: "arquitetura", rotulo: "Arquitetura", cor: "#2A2420" }, { id: "estrutura", rotulo: "Estrutura", cor: "#1D4ED8" }, { id: "fundacao", rotulo: "Fundação", cor: "#7C4A1E" },
  { id: "desenho", rotulo: "Desenho auxiliar", cor: "#6B7280" }, { id: "anotacao", rotulo: "Anotação", cor: "#166534" }, { id: "ia-rascunho", rotulo: "Rascunho de IA", cor: "#A855F7" },
];

export function PainelCamadas() {
  const s = useMotor();
  return (
    <div className="flex flex-col gap-2">
      <h3 className="text-sm font-semibold">Camadas e estilos (Tabela H)</h3>
      {CAMADAS.map((c) => (
        <label key={c.id} className="flex items-center gap-2 tap text-sm cursor-pointer">
          <input type="checkbox" checked={s.camadasVisiveis.has(c.id)} onChange={() => s.alternarCamada(c.id)} className="size-4 accent-[var(--brand)]" />
          <span className="inline-block size-3 rounded-sm" style={{ background: c.cor }} aria-hidden />{c.rotulo}
          <span className="ml-auto text-xs text-text-3">{s.modelo.elementosDo(s.pavimentoId).filter((e) => e.camada === c.id).length}</span>
        </label>
      ))}
      <h3 className="text-sm font-semibold mt-2">Atração a objetos (Osnap)</h3>
      <div className="flex flex-wrap gap-1">{MODOS_OSNAP.map((m) => <button key={m} type="button" aria-pressed={s.modos.osnapModos.has(m)} onClick={() => s.alternarOsnap(m)} className={cx("min-h-8 rounded border px-2 mono text-xs", s.modos.osnapModos.has(m) ? "bg-brand-soft text-brand-text border-brand/40" : "border-line-soft text-text-3")}>{m}</button>)}</div>
      <p className="text-xs text-text-3">Prioridade: EIX › EXT › INT › MED › CEN › PER › PRX. EIX e NIV são próprios do BIM.</p>
      {(s.ocultos.size > 0 || s.isolados) && <Botao pequeno variante="secundario" onClick={() => s.iniciar("EU")}>Reexibir tudo (EU)</Botao>}
    </div>
  );
}

const CAMPOS: Record<string, { campo: string; rotulo: string; tipo: "numero" | "texto" | "bool" }[]> = {
  PAREDE: [{ campo: "espessura", rotulo: "Espessura (m)", tipo: "numero" }, { campo: "altura", rotulo: "Altura (m)", tipo: "numero" }, { campo: "externa", rotulo: "Externa", tipo: "bool" }],
  PILAR: [{ campo: "b", rotulo: "b (m)", tipo: "numero" }, { campo: "h", rotulo: "h (m)", tipo: "numero" }, { campo: "rotacao", rotulo: "Rotação (°)", tipo: "numero" }, { campo: "continuo", rotulo: "Pilar contínuo", tipo: "bool" }],
  VIGA: [{ campo: "b", rotulo: "b (m)", tipo: "numero" }, { campo: "h", rotulo: "h (m)", tipo: "numero" }],
  LAJE: [{ campo: "h", rotulo: "Espessura (m)", tipo: "numero" }],
  PORTA: [{ campo: "largura", rotulo: "Largura (m)", tipo: "numero" }, { campo: "altura", rotulo: "Altura (m)", tipo: "numero" }],
  JANELA: [{ campo: "largura", rotulo: "Largura (m)", tipo: "numero" }, { campo: "altura", rotulo: "Altura (m)", tipo: "numero" }, { campo: "peitoril", rotulo: "Peitoril (m)", tipo: "numero" }],
  CIRCULO: [{ campo: "raio", rotulo: "Raio (m)", tipo: "numero" }],
  AMBIENTE: [{ campo: "nome", rotulo: "Nome", tipo: "texto" }],
  TEXTO: [{ campo: "texto", rotulo: "Texto", tipo: "texto" }, { campo: "altura", rotulo: "Altura (m)", tipo: "numero" }],
  SAPATA: [{ campo: "bx", rotulo: "bx (m)", tipo: "numero" }, { campo: "by", rotulo: "by (m)", tipo: "numero" }, { campo: "h", rotulo: "h (m)", tipo: "numero" }],
};

export function PainelPropriedades() {
  const s = useMotor();
  const ids = [...s.selecao];
  const e = ids.length === 1 ? s.modelo.elemento(ids[0]!) : undefined;
  if (!ids.length) return <p className="text-sm text-text-3">Selecione um elemento na planta ou no 3D para ver as propriedades.</p>;
  if (!e) return <p className="text-sm text-text-2">{ids.length} elementos selecionados. <Botao pequeno variante="secundario" onClick={() => s.iniciar("E")}>Apagar (E)</Botao></p>;
  const campos = CAMPOS[e.tipo] ?? [];
  const pav = s.modelo.pavimento(e.pavimentoId);
  const enviar = (campo: string, valor: unknown) => void s.alterarPropriedade(e.id, campo, valor);
  return (
    <div className="flex flex-col gap-3 text-sm">
      <div className="flex items-center gap-2 flex-wrap"><span className="font-semibold">{e.tipo}</span>{e.rotulo && <Etiqueta tom="marca">{e.rotulo}</Etiqueta>}<Etiqueta>{e.camada}</Etiqueta>{e.rascunhoIaId && <Etiqueta tom="ia">gerado por IA</Etiqueta>}</div>
      <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1 text-xs text-text-2"><dt>Id</dt><dd className="mono truncate">{e.id}</dd><dt>Pavimento</dt><dd>{pav?.nome}</dd><dt>Origem</dt><dd>{e.origem}</dd>{"p1" in e && "p2" in e && <><dt>p1 → p2</dt><dd className="mono">[{(e as { p1: { x: number; y: number } }).p1.x},{(e as { p1: { x: number; y: number } }).p1.y}] → [{(e as { p2: { x: number; y: number } }).p2.x},{(e as { p2: { x: number; y: number } }).p2.y}]</dd></>}{"centro" in e && <><dt>Centro</dt><dd className="mono">[{(e as { centro: { x: number; y: number } }).centro.x},{(e as { centro: { x: number; y: number } }).centro.y}]</dd></>}</dl>
      {campos.map((c) => {
        const v = (e as unknown as Record<string, unknown>)[c.campo];
        if (c.tipo === "bool") return <Alternar key={c.campo} rotulo={c.rotulo} ligado={!!v} onChange={(x) => enviar(c.campo, x)} disabled={!s.podeEditar} />;
        return (
          <label key={c.campo} className="flex flex-col gap-1 text-xs"><span className="text-text-3">{c.rotulo}</span>
            <input defaultValue={String(v ?? "")} disabled={!s.podeEditar} inputMode={c.tipo === "numero" ? "decimal" : undefined} className="mono min-h-9 rounded border border-line bg-bg px-2 text-sm"
              onKeyDown={(ev) => { if (ev.key === "Enter") { const t = (ev.target as HTMLInputElement).value; enviar(c.campo, c.tipo === "numero" ? Number(t.replace(",", ".")) : t); } }}
              onBlur={(ev) => { const t = ev.target.value; const novo = c.tipo === "numero" ? Number(t.replace(",", ".")) : t; if (novo !== v && !(c.tipo === "numero" && Number.isNaN(novo as number))) enviar(c.campo, novo); }} />
          </label>
        );
      })}
      {CATALOGO_MATERIAIS.materiais.some((m) => m.aplicacoes.includes(e.tipo)) && <SeletorMaterial key={e.id} elemento={e} />}
      <p className="text-[11px] text-text-3">Enter aplica (gera <code className="mono">VI(ALTERAR)</code> e passa pelo auditor).</p>
      <div className="flex gap-2"><Botao pequeno variante="secundario" onClick={() => s.iniciar("TG")}>Etiquetar (TG)</Botao><Botao pequeno variante="perigo" onClick={() => s.iniciar("E")} disabled={!s.podeEditar}>Apagar</Botao></div>
    </div>
  );
}

export function PainelAtalhos() {
  const s = useMotor();
  const conflitos = conflitosDeAlias();
  const grupos = ["DESENHO", "MODIFICACAO", "VISUALIZACAO", "ANOTACAO", "MODO", "CONFIG"] as const;
  return (
    <div className="flex flex-col gap-3 text-sm">
      <p className="text-xs text-text-3">Perfil ativo: <strong>{s.perfil}</strong>. Aliases do AutoCAD e do Revit convivem; em conflito ({conflitos.map((c) => c.alias).join(", ")}) o perfil decide.</p>
      <p className="text-xs text-text-3">Teclas do navegador ({TECLAS_RESERVADAS_NAVEGADOR.join(", ")}) nunca são capturadas: use os aliases alternativos (Ctrl+Alt+5, Ctrl+Alt+1, Ctrl+Alt+2, Shift+F1).</p>
      {grupos.map((g) => (
        <details key={g} open={g === "DESENHO"}>
          <summary className="cursor-pointer font-medium">{g}</summary>
          <table className="w-full text-xs mt-1"><tbody>
            {CATALOGO.filter((c) => c.categoria === g).map((c) => <tr key={c.codigo} className="border-t border-line-soft"><td className="mono py-1 pr-2 text-brand-text whitespace-nowrap">{(c.aliases[s.perfil] ?? []).join(", ") || "—"}</td><td className="py-1 pr-2">{c.nomePtBr}</td><td className="py-1 text-text-3">{c.nomeEn}</td></tr>)}
          </tbody></table>
        </details>
      ))}
    </div>
  );
}

export function PainelAjuda() {
  const s = useMotor();
  const c = s.comando?.def ?? s.ultimoComando;
  return (
    <div className="flex flex-col gap-2 text-sm">
      {c ? <><h3 className="font-semibold">{c.nomePtBr} <span className="text-text-3 font-normal">({c.nomeEn})</span></h3><p>{c.descricao}</p><ol className="list-decimal pl-5 text-xs text-text-2">{c.roteiro.map((p, i) => <li key={i}>{p.mensagem} <span className="text-text-3">[{p.tipo}{p.repetir ? ", repete" : ""}]</span></li>)}</ol><p className="text-xs text-text-3">RF: {c.rf.join(", ")}{c.papeis ? ` · exige ${c.papeis.join("/")}` : ""}</p></> : <p className="text-text-3">Inicie um comando para ver a ajuda dele aqui.</p>}
      <h4 className="font-medium mt-2">Teclado</h4>
      <ul className="text-xs text-text-2 list-disc pl-5"><li>Esc cancela · Enter/espaço repete o último · ↑↓ histórico</li><li>Ctrl+K paleta · Ctrl+Z desfazer · Ctrl+Y refazer · Delete apaga a seleção</li><li>F3 Osnap · F7 grade · F8 Orto · F9 snap · F10 Polar · F2 histórico</li><li>Roda do mouse: zoom · botão do meio ou Espaço+arrastar: mover vista · botão direito: confirmar/repetir</li><li>Seleção: clique, Shift adiciona, janela (→) seleciona inteiros, cruzamento (←) o que toca</li></ul>
    </div>
  );
}

export function PainelAuditoria() {
  const r = useMotor((s) => s.relatorio);
  if (!r) return <p className="text-sm text-text-3">O relatório do último comando aparece aqui (laço 1: código · laço 2: geometria).</p>;
  const lista = (t: string, v: { codigo: string; mensagem: string }[], tom: "perigo" | "aviso") => v.length ? <div><p className="text-xs font-medium">{t}</p><ul className="text-xs">{v.map((x, i) => <li key={i} className={tom === "perigo" ? "text-danger" : "text-warning"}>{x.codigo}: {x.mensagem}</li>)}</ul></div> : null;
  return (
    <div className="flex flex-col gap-2 text-sm">
      <p>{r.aprovado ? <Etiqueta tom="sucesso">aprovado</Etiqueta> : <Etiqueta tom="perigo">reprovado</Etiqueta>} <span className="text-xs text-text-3">laço 1 {r.laco1.aprovado ? "✔" : "✖"} · laço 2 {r.laco2.aprovado ? "✔" : "✖"}</span></p>
      {lista("Violações (código)", r.laco1.violacoes, "perigo")}{lista("Violações (geometria)", r.laco2.violacoes, "perigo")}{lista("Avisos", r.laco2.avisos, "aviso")}
      {r.correcoes.length > 0 && <div><p className="text-xs font-medium">Correções do auditor (antes → depois)</p><ul className="text-xs text-text-2">{r.correcoes.map((c, i) => <li key={i} className="mono">{c.regra} · {c.elementoRef}.{c.campo}: {String(c.antes)} → {String(c.depois)}</li>)}</ul></div>}
    </div>
  );
}

export type { Elemento };
