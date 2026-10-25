"use client";
/** Motor de desenho 2D/3D: composição (canvas/cena, linha de comando, barra de status, paleta, painéis) + teclado global. */
import { useEffect, useState } from "react";
import { BookOpen, ClipboardCheck, Code2, Keyboard, Layers, SlidersHorizontal, X } from "lucide-react";
import { useMotor, type Painel } from "@/lib/motor/estado";
import { Canvas2D } from "./Canvas2D";
import { Cena3D } from "./Cena3D";
import { LinhaComando } from "./LinhaComando";
import { BarraStatus } from "./BarraStatus";
import { Paleta } from "./Paleta";
import { ConsoleVI } from "./ConsoleVI";
import { PainelAjuda, PainelAtalhos, PainelAuditoria, PainelCamadas, PainelPropriedades } from "./Paineis";
import { cx } from "@/components/ui";

type Aba = Exclude<Painel, "nenhum" | "historico"> | "console" | "auditoria";
const ABAS: { id: Aba; rotulo: string; Icone: typeof Layers }[] = [
  { id: "propriedades", rotulo: "Propriedades", Icone: SlidersHorizontal }, { id: "console", rotulo: "VI / IA", Icone: Code2 }, { id: "camadas", rotulo: "Camadas", Icone: Layers },
  { id: "auditoria", rotulo: "Auditoria", Icone: ClipboardCheck }, { id: "atalhos", rotulo: "Atalhos", Icone: Keyboard }, { id: "ajuda", rotulo: "Ajuda", Icone: BookOpen },
];

export function Motor({ obraId, podeEditar = true, altura = "calc(100dvh - 7rem)" }: { obraId: string | null; podeEditar?: boolean; altura?: string }) {
  const s = useMotor();
  const [aba, setAba] = useState<Aba | null>("propriedades");
  useEffect(() => { void s.carregar(obraId, podeEditar); // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [obraId, podeEditar]);
  useEffect(() => { if (s.painel !== "nenhum" && s.painel !== "historico") { setAba(s.painel); s.setPainel("nenhum"); } }, [s.painel, s]);

  useEffect(() => {
    const f = (e: KeyboardEvent) => {
      const alvo = e.target as HTMLElement;
      const emTexto = (alvo.tagName === "TEXTAREA") || (alvo.tagName === "INPUT" && alvo.id !== "linha-comando") || alvo.tagName === "SELECT";
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "k") { e.preventDefault(); s.setPaleta(true); return; }
      if (emTexto) return;
      if (e.key === "Escape") { if (s.paletaAberta) s.setPaleta(false); else if (s.comando) s.cancelar(); else s.selecionar([], "substituir"); return; }
      const fn: Record<string, () => void> = { F2: () => setAba((a) => (a === "ajuda" ? null : "ajuda")), F3: () => s.alternar("osnap"), F4: () => s.alternar("osnap3d"), F6: () => s.alternar("ucsDinamico"), F7: () => s.alternar("grade"), F8: () => s.alternar("orto"), F9: () => s.alternar("snapGrade"), F10: () => s.alternar("polar") };
      if (fn[e.key]) { e.preventDefault(); fn[e.key]!(); return; }
      if (e.ctrlKey && e.altKey && e.key === "1") { e.preventDefault(); s.alternar("otrack"); return; }
      if (e.ctrlKey && e.altKey && e.key === "2") { e.preventDefault(); s.alternar("entradaDinamica"); return; }
      if (e.ctrlKey && e.altKey && e.key === "5") { e.preventDefault(); s.setVista(s.vista === "2D" ? "3D" : "2D"); return; }
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "z") { e.preventDefault(); if (e.shiftKey) s.refazer(); else void s.desfazer(); return; }
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "y") { e.preventDefault(); s.refazer(); return; }
      if (e.key === "Delete" && s.selecao.size && !s.comando) { e.preventDefault(); s.iniciar("E"); return; }
      if (e.key === "Enter" && alvo.id !== "linha-comando" && !s.comando) { e.preventDefault(); s.repetirUltimo(); }
    };
    window.addEventListener("keydown", f); return () => window.removeEventListener("keydown", f);
  }, [s]);

  return (
    <div className="relative flex flex-col rounded-[var(--radius-md)] border border-line-soft bg-bg overflow-hidden" style={{ height: altura }}>
      <div className="flex min-h-0 flex-1">
        <div className="relative min-w-0 flex-1">{s.vista === "2D" ? <Canvas2D /> : <Cena3D />}</div>
        <aside className={cx("hidden md:flex flex-col border-l border-line-soft bg-bg", aba ? "w-80" : "w-12")} aria-label="Painéis do motor">
          <div role="tablist" aria-orientation="vertical" className="flex flex-col items-stretch border-b border-line-soft">
            {ABAS.map((a) => <button key={a.id} role="tab" aria-selected={aba === a.id} title={a.rotulo} onClick={() => setAba((x) => (x === a.id ? null : a.id))} className={cx("tap flex items-center gap-2 px-3 text-xs", aba === a.id ? "bg-surface text-brand-text font-medium" : "text-text-3 hover:bg-surface")}><a.Icone className="size-4 shrink-0" aria-hidden />{aba && <span>{a.rotulo}</span>}</button>)}
          </div>
          {aba && (
            <div role="tabpanel" className="min-h-0 flex-1 overflow-y-auto p-3">
              {aba === "propriedades" && <PainelPropriedades />}{aba === "console" && <ConsoleVI />}{aba === "camadas" && <PainelCamadas />}{aba === "auditoria" && <PainelAuditoria />}{aba === "atalhos" && <PainelAtalhos />}{aba === "ajuda" && <PainelAjuda />}
            </div>
          )}
          {aba && <button type="button" onClick={() => setAba(null)} aria-label="Recolher painel" className="tap flex items-center justify-center text-text-3 border-t border-line-soft hover:bg-surface"><X className="size-4" aria-hidden /></button>}
        </aside>
      </div>
      <LinhaComando />
      <BarraStatus />
      <Paleta />
      {s.erroSync && <p role="alert" className="absolute top-2 right-[21rem] max-w-sm rounded-[var(--radius-sm)] border border-danger bg-danger-bg px-3 py-1 text-xs text-danger">{s.erroSync}</p>}
    </div>
  );
}
