"use client";
/** Paleta de comandos Ctrl+K (RF28 CA-01/CA-06): busca por alias do perfil, nome pt-BR e nome em inglês. */
import { useEffect, useMemo, useRef, useState } from "react";
import { Search } from "lucide-react";
import { buscarComandos, TECLAS_RESERVADAS_NAVEGADOR } from "@cca/notacao-vi";
import { useMotor } from "@/lib/motor/estado";
import { cx } from "@/components/ui";

const CAT: Record<string, string> = { DESENHO: "Desenho", MODIFICACAO: "Modificação", VISUALIZACAO: "Visualização", ANOTACAO: "Anotação", MODO: "Modo", CONFIG: "Configuração" };

export function Paleta() {
  const { paletaAberta, setPaleta, perfil, iniciar } = useMotor();
  const [q, setQ] = useState("");
  const [i, setI] = useState(0);
  const input = useRef<HTMLInputElement>(null);
  const lista = useMemo(() => buscarComandos(q, perfil, 14), [q, perfil]);
  useEffect(() => { if (paletaAberta) { setQ(""); setI(0); setTimeout(() => input.current?.focus(), 0); } }, [paletaAberta]);
  if (!paletaAberta) return null;
  const executar = (codigo: string) => { setPaleta(false); iniciar(codigo); };
  return (
    <div className="absolute inset-0 z-30 flex items-start justify-center bg-black/30 p-4 pt-16" onClick={() => setPaleta(false)}>
      <div role="dialog" aria-modal="true" aria-label="Paleta de comandos" className="w-full max-w-xl rounded-[var(--radius-lg)] border border-line-soft bg-bg shadow-xl" onClick={(e) => e.stopPropagation()}>
        <div className="flex items-center gap-2 border-b border-line-soft px-4">
          <Search className="size-4 text-text-3" aria-hidden />
          <input ref={input} value={q} onChange={(e) => { setQ(e.target.value); setI(0); }} placeholder="Buscar comando: parede, WA, wall…" aria-label="Buscar comando" className="min-h-12 flex-1 bg-transparent text-sm outline-none"
            onKeyDown={(e) => { if (e.key === "ArrowDown") { e.preventDefault(); setI((v) => Math.min(v + 1, lista.length - 1)); } if (e.key === "ArrowUp") { e.preventDefault(); setI((v) => Math.max(v - 1, 0)); } if (e.key === "Enter" && lista[i]) executar(lista[i]!.codigo); if (e.key === "Escape") setPaleta(false); }} />
        </div>
        <ul role="listbox" className="max-h-96 overflow-y-auto p-2">
          {lista.map((c, k) => {
            const al = c.aliases[perfil] ?? [];
            const reservado = al.some((a) => (TECLAS_RESERVADAS_NAVEGADOR as readonly string[]).includes(a));
            return (
              <li key={c.codigo} role="option" aria-selected={k === i}>
                <button type="button" onClick={() => executar(c.codigo)} onMouseEnter={() => setI(k)} className={cx("tap flex w-full items-center gap-3 rounded-[var(--radius-sm)] px-3 text-left", k === i ? "bg-surface" : "hover:bg-surface/60")}>
                  <span className="mono w-20 shrink-0 text-xs text-brand-text">{al.slice(0, 2).join(" · ") || "—"}</span>
                  <span className="flex-1 min-w-0"><span className="block text-sm font-medium text-text">{c.nomePtBr} <span className="text-text-3 font-normal">· {c.nomeEn}</span></span><span className="block truncate text-xs text-text-3">{c.descricao}</span></span>
                  <span className="text-[10px] uppercase tracking-wide text-text-3">{CAT[c.categoria]}{reservado && " · navegador"}</span>
                </button>
              </li>
            );
          })}
          {!lista.length && <li className="px-3 py-4 text-sm text-text-3">Nenhum comando encontrado.</li>}
        </ul>
      </div>
    </div>
  );
}
