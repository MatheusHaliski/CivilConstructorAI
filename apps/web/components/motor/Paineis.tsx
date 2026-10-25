"use client";
/** Painéis laterais: camadas (VG/LA), propriedades (PR, formulário H5), atalhos (KS), ajuda, auditoria. */
import { CATALOGO, CATALOGO_MATERIAIS, comprimentoPercurso, conflitosDeAlias, itemObjeto, MODOS_OSNAP, percursoConexao, problemasConexao, problemasObjeto, rotuloSistema, TECLAS_RESERVADAS_NAVEGADOR, type Camada, type Conexao, type Elemento, type Objeto } from "@cca/notacao-vi";
import { useMotor } from "@/lib/motor/estado";
import { Alternar, Botao, Etiqueta, cx } from "@/components/ui";
import { SeletorMaterial } from "./SeletorMaterial";

const CAMADAS: { id: Camada; rotulo: string; cor: string }[] = [
  { id: "arquitetura", rotulo: "Arquitetura", cor: "#2A2420" }, { id: "estrutura", rotulo: "Estrutura", cor: "#1D4ED8" }, { id: "fundacao", rotulo: "Fundação", cor: "#7C4A1E" },
  { id: "desenho", rotulo: "Desenho auxiliar", cor: "#6B7280" }, { id: "anotacao", rotulo: "Anotação", cor: "#166534" }, { id: "ia-rascunho", rotulo: "Rascunho de IA", cor: "#A855F7" },
  { id: "mobiliario", rotulo: "Mobiliário", cor: "#B45309" }, { id: "eletrica", rotulo: "Elétrica", cor: "#CA8A04" }, { id: "hidrossanitaria", rotulo: "Hidrossanitária", cor: "#0284C7" }, { id: "humanos", rotulo: "Pessoas (humanos)", cor: "#BE185D" },
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
          <button type="button" onClick={(ev) => { ev.preventDefault(); s.soCamada(c.id); }} className="rounded border border-line-soft px-1.5 text-[11px] text-text-3 hover:bg-surface" aria-label={`Isolar disciplina ${c.rotulo}`}>isolar</button>
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
  if (!e) return (
    <div className="flex flex-col gap-2 text-sm">
      <p className="text-text-2">{ids.length} elementos selecionados:</p>
      <ul className="flex flex-col gap-1">{ids.map((id) => { const x = s.modelo.elemento(id); if (!x) return null; const nome = x.tipo === "OBJETO" ? itemObjeto(x.item)?.nome ?? x.item : x.tipo; return <li key={id}><button type="button" className="underline text-left" onClick={() => s.selecionar([id], "substituir")}>{nome} ({x.rotulo ?? s.modelo.rotuloAutomatico(x)})</button></li>; })}</ul>
      <Botao pequeno variante="secundario" onClick={() => s.iniciar("E")} disabled={!s.podeEditar}>Apagar (E)</Botao>
    </div>
  );
  if (e.tipo === "OBJETO") return <PainelObjeto o={e} />;
  if (e.tipo === "CONEXAO") return <PainelConexao c={e} />;
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

function Campo({ rotulo, valor, onEnviar, desabilitado, unidade }: { rotulo: string; valor: number | string | undefined; onEnviar: (v: number) => void; desabilitado?: boolean; unidade?: string }) {
  return (
    <label className="flex flex-col gap-1 text-xs"><span className="text-text-3">{rotulo}{unidade ? ` (${unidade})` : ""}</span>
      <input key={String(valor)} defaultValue={String(valor ?? "")} disabled={desabilitado} inputMode="decimal" className="mono min-h-9 rounded border border-line bg-bg px-2 text-sm"
        onKeyDown={(ev) => { if (ev.key === "Enter") { const n = Number((ev.target as HTMLInputElement).value.replace(",", ".")); if (Number.isFinite(n)) onEnviar(n); } }}
        onBlur={(ev) => { const n = Number(ev.target.value.replace(",", ".")); if (Number.isFinite(n) && String(n) !== String(valor)) onEnviar(n); }} />
    </label>
  );
}

function Problemas({ lista }: { lista: { codigo: string; mensagem: string }[] }) {
  if (!lista.length) return null;
  return <ul role="alert" className="flex flex-col gap-1 rounded border border-danger bg-danger-bg p-2 text-xs text-danger">{lista.map((p, i) => <li key={i}><strong className="mono">{p.codigo}</strong> {p.mensagem}</li>)}</ul>;
}

/** objeto da biblioteca: dimensões (sem deformar equipamentos), montagem, estado da luminária e conectores com "Conectar a…" */
function PainelObjeto({ o }: { o: Objeto }) {
  const s = useMotor(); const m = s.modelo; const it = itemObjeto(o.item);
  const host = o.paredeId ? m.elemento(o.paredeId) : undefined;
  const conexoes = m.elementos.filter((x): x is Conexao => x.tipo === "CONEXAO" && (x.de.id === o.id || x.para.id === o.id));
  const enviar = (campo: string, valor: unknown) => void s.alterarPropriedade(o.id, campo, valor);
  const ed = !s.podeEditar;
  const nome = (id: string) => { const x = m.elemento(id); return x ? `${x.tipo === "OBJETO" ? itemObjeto(x.item)?.nome ?? x.item : x.tipo} (${x.rotulo ?? m.rotuloAutomatico(x)})` : id; };
  return (
    <div className="flex flex-col gap-3 text-sm">
      <div className="flex items-center gap-2 flex-wrap"><span className="font-semibold">{it?.nome ?? o.item}</span><Etiqueta tom="marca">{o.rotulo ?? m.rotuloAutomatico(o)}</Etiqueta><Etiqueta>{o.camada}</Etiqueta></div>
      <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1 text-xs text-text-2">
        <dt>Item</dt><dd className="mono">{o.item}</dd><dt>Centro</dt><dd className="mono">[{o.centro.x.toFixed(3)}; {o.centro.y.toFixed(3)}]</dd>
        <dt>Montagem</dt><dd>{it?.montagem === "PAREDE" ? `parede ${host ? host.rotulo ?? m.rotuloAutomatico(host) : "— sem parede"}` : it?.montagem === "TETO" ? "teto" : "piso"}</dd>
        <dt>IFC</dt><dd className="mono">{it?.ifc || "não exportado (pessoa)"}</dd><dt>Fabricante</dt><dd>genérico (dimensões típicas adotadas)</dd>
      </dl>
      <div className="grid grid-cols-2 gap-2">
        <Campo rotulo="Rotação" unidade="°" valor={o.rotacao} onEnviar={(v) => enviar("rotacao", v)} desabilitado={ed} />
        <Campo rotulo="Elevação" unidade="m" valor={o.elevacao} onEnviar={(v) => enviar("elevacao", v)} desabilitado={ed} />
        {it?.alturaVariavel ? <Campo rotulo={`Altura ${it.alturaVariavel.min}–${it.alturaVariavel.max}`} unidade="m" valor={o.dim.a} onEnviar={(v) => enviar("altura", v)} desabilitado={ed} />
          : (["l", "p", "a"] as const).map((k) => <Campo key={k} rotulo={k === "l" ? "Largura" : k === "p" ? "Profundidade" : "Altura"} unidade="m" valor={o.dim[k]} onEnviar={(v) => enviar("dim", { ...o.dim, [k]: v })} desabilitado={ed || !it?.redimensionavel} />)}
      </div>
      {!it?.redimensionavel && !it?.alturaVariavel && <p className="text-[11px] text-text-3">Equipamento com dimensões de produto: não é deformado. Troque por outro item da biblioteca se precisar de outra medida.</p>}
      {it?.alturaVariavel && <p className="text-[11px] text-text-3">Pessoas escalam de modo uniforme pela altura; não entram em quantitativos, orçamento nem cargas.</p>}
      {it?.luz && <><Alternar rotulo="Luminária acesa (efeito visual)" ligado={o.ligada !== false} onChange={(x) => enviar("ligada", x)} disabled={ed} /><p className="text-[11px] text-text-3">{it.luz.fluxoLm} lm · {it.luz.temperaturaK} K · alcance {it.luz.alcanceM} m. Apagar aqui não desliga o circuito nem remove a conexão.</p></>}
      {it?.disciplina === "humanos" && <Botao pequeno variante="secundario" onClick={() => s.setPrimeiraPessoa(o.id)}>Ver em 1ª pessoa (3D)</Botao>}
      {it && it.conectores.length > 0 && (
        <div className="flex flex-col gap-1"><h4 className="text-xs font-semibold">Conectores</h4>
          <ul className="flex flex-col gap-1">{it.conectores.map((k) => {
            const ligadas = conexoes.filter((c) => (c.de.id === o.id && c.de.conector === k.id) || (c.para.id === o.id && c.para.conector === k.id));
            const max = k.maxConexoes ?? 1;
            return (
              <li key={k.id} className="flex flex-col gap-1 rounded border border-line-soft p-1.5 text-xs">
                <span><strong className="mono">{k.id}</strong> · {rotuloSistema(k.sistema)} · {k.fluxo === "SAI" ? "saída" : "entrada"}{k.tensaoV ? ` · ${k.tensaoV} V` : ""}{k.potenciaW ? ` · ${k.potenciaW} W` : ""}{k.correnteMaxA ? ` · até ${k.correnteMaxA} A` : ""}{k.dnMm ? ` · DN${k.dnMm}` : ""}</span>
                <span className="text-text-3">{ligadas.length ? `ligado a ${ligadas.map((c) => nome(c.de.id === o.id ? c.para.id : c.de.id)).join(", ")}` : "livre"} ({ligadas.length}/{max})</span>
                {ligadas.length < max && <Botao pequeno variante="secundario" disabled={ed} onClick={() => { s.setVista("2D"); s.iniciar("CONECTAR", { valores: { de: `${o.id}:${k.id}` }, selecoes: [[o.id]] }); }}>Conectar {k.id} a…</Botao>}
              </li>
            );
          })}</ul>
        </div>
      )}
      {it && it.conectores.length === 0 && <p className="text-xs text-text-3">Este objeto não tem conexões elétricas nem hidráulicas.</p>}
      <Problemas lista={problemasObjeto(m, o)} />
      <div className="flex gap-2"><Botao pequeno variante="secundario" onClick={() => s.iniciar("M")} disabled={ed}>Mover</Botao><Botao pequeno variante="secundario" onClick={() => s.iniciar("RO")} disabled={ed}>Girar</Botao><Botao pequeno variante="perigo" onClick={() => s.iniciar("E")} disabled={ed}>Apagar</Botao></div>
    </div>
  );
}

/** conexão: ligação lógica (circuito/sistema), conexão física (conectores, condutores, DN) e representação gráfica (percurso derivado) */
function PainelConexao({ c }: { c: Conexao }) {
  const s = useMotor(); const m = s.modelo; const ed = !s.podeEditar;
  const enviar = (campo: string, valor: unknown) => void s.alterarPropriedade(c.id, campo, valor);
  const nome = (id: string) => { const x = m.elemento(id); return x?.tipo === "OBJETO" ? `${itemObjeto(x.item)?.nome ?? x.item} (${x.rotulo ?? m.rotuloAutomatico(x)})` : id; };
  const pts = percursoConexao(m, c); const cordao = pts.length === 2;
  return (
    <div className="flex flex-col gap-3 text-sm">
      <div className="flex items-center gap-2 flex-wrap"><span className="font-semibold">Conexão de {rotuloSistema(c.sistema)}</span><Etiqueta tom="marca">{c.rotulo ?? m.rotuloAutomatico(c)}</Etiqueta></div>
      <section className="flex flex-col gap-2 rounded border border-line-soft p-2"><h4 className="text-xs font-semibold">Ligação lógica</h4>
        {c.sistema === "ELETRICA" ? <div className="grid grid-cols-2 gap-2">
          <label className="flex flex-col gap-1 text-xs"><span className="text-text-3">Circuito</span><input key={c.circuito} defaultValue={c.circuito ?? "C1"} disabled={ed} className="mono min-h-9 rounded border border-line bg-bg px-2 text-sm" onKeyDown={(ev) => { if (ev.key === "Enter") enviar("circuito", (ev.target as HTMLInputElement).value.trim()); }} onBlur={(ev) => { if (ev.target.value.trim() !== (c.circuito ?? "C1")) enviar("circuito", ev.target.value.trim()); }} /></label>
          <Campo rotulo="Disjuntor" unidade="A" valor={c.disjuntorA} onEnviar={(v) => enviar("disjuntorA", v)} desabilitado={ed} />
        </div> : <p className="text-xs text-text-2">Sistema de {rotuloSistema(c.sistema)} (rede verificada pela coordenação → instalações).</p>}
      </section>
      <section className="flex flex-col gap-2 rounded border border-line-soft p-2"><h4 className="text-xs font-semibold">Conexão física</h4>
        <p className="text-xs text-text-2">{nome(c.de.id)}.<span className="mono">{c.de.conector}</span> (saída) → {nome(c.para.id)}.<span className="mono">{c.para.conector}</span> (entrada)</p>
        <div className="grid grid-cols-2 gap-2">
          {c.sistema === "ELETRICA" ? <><Campo rotulo="Seção" unidade="mm²" valor={c.secaoMm2} onEnviar={(v) => enviar("secaoMm2", v)} desabilitado={ed} /><p className="text-xs text-text-2 self-end">Condutores: {(c.condutores ?? []).join(", ") || "—"}</p></>
            : <><Campo rotulo="Diâmetro nominal" unidade="mm" valor={c.dnMm} onEnviar={(v) => enviar("dnMm", v)} desabilitado={ed} />{c.sistema === "ESGOTO" && <Campo rotulo="Declividade" unidade="m/m" valor={c.declividade} onEnviar={(v) => enviar("declividade", v)} desabilitado={ed} />}</>}
        </div>
        <p className="text-[11px] text-text-3">Material: <span className="mono">{c.materialId ?? "—"}</span></p>
      </section>
      <section className="flex flex-col gap-1 rounded border border-line-soft p-2"><h4 className="text-xs font-semibold">Representação gráfica</h4>
        <p className="text-xs text-text-2">{cordao ? "Cabo do próprio aparelho (tomada → plugue), em linha direta." : c.sistema === "ESGOTO" ? "Percurso esquemático sob o piso, com a declividade da conexão." : "Percurso esquemático: sobe ao forro, corre em X e Y e desce."} Comprimento {comprimentoPercurso(pts).toFixed(2)} m.</p>
        <p className="text-[11px] text-text-3">Derivado das posições dos conectores (não é traçado de obra): mover um dos objetos recalcula o desenho sem desfazer a ligação.</p>
      </section>
      <Problemas lista={problemasConexao(m, c)} />
      <Botao pequeno variante="perigo" onClick={() => s.iniciar("E")} disabled={ed}>Desfazer conexão (apagar)</Botao>
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
