"use client";
/** Console da Notação VI e texto natural (RF02): VI(...) determinístico ou prompt para a IA (rascunho "gerado por IA"). */
import { useState } from "react";
import { Code2, Sparkles } from "lucide-react";
import { parseVI } from "@cca/notacao-vi";
import { useMotor } from "@/lib/motor/estado";
import { Abas, Botao, Etiqueta, cx } from "@/components/ui";

const EXEMPLOS = ["VI(MALHA, nx=4, ny=3, dx=5, dy=6)", "VI(PILAR, centro=[0,0], b=0.19, h=0.4)", "VI(PAREDE, p1=[0,0], p2=[5,0], esp=0.15, h=2.8)", "VI(VIGA, p1=[0,0], p2=[5,0], b=0.12, h=0.4)", "VI(PAVIMENTO, nome=\"1º Pavimento\", z=3.0)"];

export function ConsoleVI() {
  const { executarVI, textoNatural, obraId, sincronizando } = useMotor();
  const [aba, setAba] = useState("vi");
  const [vi, setVi] = useState("");
  const [texto, setTexto] = useState("");
  const [resposta, setResposta] = useState<{ aviso: string; comandos: { vi: string }[] } | null>(null);
  const prog = vi.trim() ? parseVI(vi) : null;
  return (
    <div className="flex flex-col gap-3">
      <Abas ativa={aba} onChange={setAba} abas={[{ id: "vi", rotulo: "Notação VI", icone: <Code2 className="size-4" aria-hidden /> }, { id: "ia", rotulo: "Texto natural (IA)", icone: <Sparkles className="size-4" aria-hidden /> }]} />
      {aba === "vi" ? (
        <>
          <label htmlFor="console-vi" className="text-xs text-text-3">Uma função por linha: <code className="mono">VI(COD, arg=valor, …)</code>. Pontos como <code className="mono">[x,y]</code>, relativo <code className="mono">@2,0</code>, polar <code className="mono">@5&lt;30</code>.</label>
          <textarea id="console-vi" value={vi} onChange={(e) => setVi(e.target.value)} rows={5} spellCheck={false} className={cx("mono w-full rounded-[var(--radius-sm)] border bg-bg p-2 text-sm", prog?.erros.length ? "border-danger" : "border-line")} placeholder={EXEMPLOS[0]} />
          {prog?.erros.length ? <ul role="alert" className="text-xs text-danger">{prog.erros.map((e, i) => <li key={i}>{e.codigo} linha {e.linha}, coluna {e.coluna}: {e.mensagem}</li>)}</ul> : prog ? <p className="text-xs text-success">{prog.comandos.length} comando(s) válido(s).</p> : null}
          <div className="flex flex-wrap gap-1">{EXEMPLOS.map((ex) => <button key={ex} type="button" onClick={() => setVi((v) => (v ? v + "\n" : "") + ex)} className="mono rounded border border-line-soft px-2 py-1 text-[11px] text-text-2 hover:bg-surface">{ex.slice(3, 20)}…</button>)}</div>
          <Botao pequeno carregando={sincronizando} disabled={!prog || !!prog.erros.length} onClick={() => { void executarVI(vi); setVi(""); }}>Executar VI</Botao>
        </>
      ) : (
        <>
          <p className="text-xs text-text-3">Descreva o que quer desenhar. A IA traduz para funções VI validadas (JSON Schema + auditor) e o resultado entra como <Etiqueta tom="ia">gerado por IA</Etiqueta> até você aprovar (RF27). Nenhum dado pessoal é enviado.</p>
          <textarea value={texto} onChange={(e) => setTexto(e.target.value)} rows={4} className="w-full rounded-[var(--radius-sm)] border border-line bg-bg p-2 text-sm" placeholder="Ex.: malha 4x3 com 5 m por 6 m e um pilar 19x40 em cada cruzamento" aria-label="Pedido em texto natural" />
          <Botao pequeno carregando={sincronizando} disabled={!texto.trim() || !obraId} onClick={() => void textoNatural(texto).then((r) => { setResposta(r); if (r) setTexto(""); })} icone={<Sparkles className="size-4" aria-hidden />}>Gerar rascunho</Botao>
          {!obraId && <p className="text-xs text-warning">Disponível quando o motor está ligado a uma obra.</p>}
          {resposta && <div className="rounded-[var(--radius-sm)] border border-ai-line bg-ai-bg p-2 text-xs text-ai-text"><p className="font-medium">{resposta.aviso}</p><ul className="mono mt-1">{resposta.comandos.map((c, i) => <li key={i}>{c.vi}</li>)}</ul><p className="mt-1">Aprove ou rejeite na tela <strong>IA › Rascunhos</strong> desta obra.</p></div>}
        </>
      )}
    </div>
  );
}
