"use client";
/** Linha de comando (RF28): prompt do passo atual, opções entre colchetes, histórico em role="log" (CA-12). */
import { useEffect, useRef, useState } from "react";
import { ChevronRight, CornerDownLeft } from "lucide-react";
import { useMotor } from "@/lib/motor/estado";
import { cx } from "@/components/ui";

export function LinhaComando() {
  const { comando, log, entradaTexto, opcao, cancelar, maquina, sincronizando, painel } = useMotor();
  // F2 / HISTORICO expande o histórico (como a janela de texto do AutoCAD); repetir fecha
  const expandido = painel === "historico";
  const [texto, setTexto] = useState("");
  const [hist, setHist] = useState<string[]>([]);
  const [idx, setIdx] = useState(-1);
  const input = useRef<HTMLInputElement>(null);
  const logRef = useRef<HTMLDivElement>(null);
  const passo = comando?.def.roteiro[comando.passo];

  useEffect(() => { logRef.current?.scrollTo({ top: logRef.current.scrollHeight }); }, [log, expandido]);
  useEffect(() => {
    const f = (e: KeyboardEvent) => {
      const alvo = e.target as HTMLElement;
      const emCampo = ["INPUT", "TEXTAREA", "SELECT"].includes(alvo.tagName) || alvo.isContentEditable;
      if (emCampo && alvo !== input.current) return;
      if (alvo.closest?.("[data-teclado-proprio]")) return; // ex.: 3D em 1ª pessoa usa W/A/S/D para andar
      if (e.key.length === 1 && !e.ctrlKey && !e.metaKey && !e.altKey && alvo !== input.current) { input.current?.focus(); }
    };
    window.addEventListener("keydown", f); return () => window.removeEventListener("keydown", f);
  }, []);

  const enviar = () => { const t = texto; setTexto(""); if (t.trim()) { setHist((h) => [t, ...h].slice(0, 50)); } setIdx(-1); void entradaTexto(t); };
  const onKey = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === "Enter") { e.preventDefault(); enviar(); }
    else if (e.key === "Escape") { e.preventDefault(); setTexto(""); cancelar(); }
    else if (e.key === "ArrowUp") { e.preventDefault(); const n = Math.min(idx + 1, hist.length - 1); setIdx(n); setTexto(hist[n] ?? ""); }
    else if (e.key === "ArrowDown") { e.preventDefault(); const n = Math.max(idx - 1, -1); setIdx(n); setTexto(n < 0 ? "" : hist[n] ?? ""); }
    else if (e.key === " " && texto === "" ) { e.preventDefault(); enviar(); }
  };

  return (
    <div className="flex flex-col border-t border-line-soft bg-bg">
      <div ref={logRef} role="log" aria-live="polite" aria-label="Histórico de comandos" className={cx(expandido ? "h-72" : "h-24", "overflow-y-auto px-3 py-1 mono text-[12px] leading-5")}>
        {log.map((l) => (
          <p key={l.id} className={cx(l.tipo === "erro" && "text-danger", l.tipo === "ok" && "text-success", l.tipo === "entrada" && "text-text-3", l.tipo === "prompt" && "text-text", l.tipo === "info" && "text-text-2", l.tipo === "ia" && "text-ai-text")}>
            {l.tipo === "entrada" ? "› " : l.tipo === "erro" ? "✖ " : l.tipo === "ok" ? "✔ " : l.tipo === "ia" ? "✦ " : ""}{l.texto}
          </p>
        ))}
      </div>
      <div className="flex items-center gap-2 border-t border-line-soft px-2 py-1.5">
        <span className="inline-flex items-center gap-1 rounded bg-surface px-2 py-1 text-xs font-medium text-text-2 whitespace-nowrap" aria-hidden>
          <ChevronRight className="size-3.5" />{comando ? comando.def.nomePtBr : maquina === "SELECIONANDO" ? "Seleção" : "Comando"}
        </span>
        {passo?.opcoes?.length ? (
          <div className="hidden sm:flex gap-1" role="group" aria-label="Opções do passo">
            {passo.opcoes.map((o) => <button key={o} type="button" onClick={() => void opcao(o)} className="min-h-8 rounded border border-line-soft px-2 text-xs text-text-2 hover:bg-surface">[{o}]</button>)}
          </div>
        ) : null}
        <label className="sr-only" htmlFor="linha-comando">Linha de comando</label>
        <input id="linha-comando" ref={input} value={texto} onChange={(e) => setTexto(e.target.value)} onKeyDown={onKey} autoComplete="off" spellCheck={false}
          aria-invalid={!!comando?.erro} aria-describedby="linha-comando-dica" placeholder={passo ? passo.mensagem : "Digite um comando (WA, CL, BM, MALHA, M, CO, E…) ou VI(...)"}
          className="mono min-h-9 flex-1 bg-transparent text-sm text-text outline-none placeholder:text-text-3" />
        <button type="button" onClick={enviar} aria-label="Executar" className="min-h-8 rounded px-2 text-text-3 hover:bg-surface"><CornerDownLeft className="size-4" aria-hidden /></button>
        {sincronizando && <span className="text-xs text-text-3" role="status">gravando…</span>}
      </div>
      <p id="linha-comando-dica" className={cx("px-3 pb-1 text-[11px]", comando?.erro ? "text-danger" : "text-text-3")}>{comando?.erro ?? "Esc cancela · Enter/espaço repete o último · clique direito confirma · @dx,dy relativo · @d<ang polar"}</p>
    </div>
  );
}
