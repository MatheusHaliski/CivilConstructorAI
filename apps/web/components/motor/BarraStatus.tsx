"use client";
/** Barra de status: coordenadas, modos (F3/F7/F8/F9/F10), perfil (KS), pavimento, vista 2D/3D, versão. */
import { Box, LayoutGrid } from "lucide-react";
import type { PerfilAtalhos } from "@cca/notacao-vi";
import { useMotor } from "@/lib/motor/estado";
import { cx } from "@/components/ui";

function Modo({ ligado, onClick, rotulo, tecla }: { ligado: boolean; onClick: () => void; rotulo: string; tecla: string }) {
  return (
    <button type="button" role="switch" aria-checked={ligado} onClick={onClick} title={`${rotulo} (${tecla})`}
      className={cx("min-h-8 rounded px-2 text-xs font-medium border", ligado ? "bg-brand-soft text-brand-text border-brand/40" : "bg-bg text-text-3 border-line-soft hover:bg-surface")}>
      {rotulo} <span className="font-normal">{tecla}</span>
    </button>
  );
}

export function BarraStatus() {
  const s = useMotor();
  const pav = s.modelo.pavimentosOrdenados();
  return (
    <div className="flex flex-wrap items-center gap-2 border-t border-line-soft bg-surface px-2 py-1 text-xs">
      <span className="mono min-w-44 text-text-2" aria-live="off">{s.cursor ? `${s.cursor.x.toFixed(3).replace(".", ",")}; ${s.cursor.y.toFixed(3).replace(".", ",")} m` : "—"}</span>
      <div className="flex gap-1" role="group" aria-label="Modos do cursor">
        <Modo ligado={s.modos.osnap} onClick={() => s.alternar("osnap")} rotulo="OSNAP" tecla="F3" />
        <Modo ligado={s.modos.grade} onClick={() => s.alternar("grade")} rotulo="GRADE" tecla="F7" />
        <Modo ligado={s.modos.orto} onClick={() => s.alternar("orto")} rotulo="ORTO" tecla="F8" />
        <Modo ligado={s.modos.snapGrade} onClick={() => s.alternar("snapGrade")} rotulo="SNAP" tecla="F9" />
        <Modo ligado={s.modos.polar} onClick={() => s.alternar("polar")} rotulo="POLAR" tecla="F10" />
        <Modo ligado={s.modos.otrack} onClick={() => s.alternar("otrack")} rotulo="OTRACK" tecla="Ctrl+Alt+1" />
        <Modo ligado={s.modos.entradaDinamica} onClick={() => s.alternar("entradaDinamica")} rotulo="DYN" tecla="Ctrl+Alt+2" />
      </div>
      <label className="flex items-center gap-1 text-text-3">Perfil
        <select aria-label="Perfil de atalhos (KS)" value={s.perfil} onChange={(e) => s.setPerfil(e.target.value as PerfilAtalhos)} className="min-h-8 rounded border border-line-soft bg-bg px-1 text-xs text-text">
          <option value="CIVILCONSTRUCTOR">CivilConstructor</option><option value="AUTOCAD">AutoCAD</option><option value="REVIT">Revit</option>
        </select>
      </label>
      <label className="flex items-center gap-1 text-text-3">Pavimento
        <select aria-label="Pavimento ativo" value={s.pavimentoId} onChange={(e) => s.setPavimento(e.target.value)} className="min-h-8 rounded border border-line-soft bg-bg px-1 text-xs text-text">
          {pav.map((p) => <option key={p.id} value={p.id}>{p.nome} ({p.elevacao >= 0 ? "+" : ""}{p.elevacao.toFixed(2)})</option>)}
        </select>
      </label>
      <div className="ml-auto flex items-center gap-1" role="group" aria-label="Vista">
        <button type="button" aria-pressed={s.vista === "2D"} onClick={() => s.setVista("2D")} className={cx("min-h-8 inline-flex items-center gap-1 rounded px-2 border", s.vista === "2D" ? "bg-brand-soft text-brand-text border-brand/40" : "border-line-soft text-text-3")}><LayoutGrid className="size-3.5" aria-hidden />Planta</button>
        <button type="button" aria-pressed={s.vista === "3D"} onClick={() => s.setVista("3D")} className={cx("min-h-8 inline-flex items-center gap-1 rounded px-2 border", s.vista === "3D" ? "bg-brand-soft text-brand-text border-brand/40" : "border-line-soft text-text-3")}><Box className="size-3.5" aria-hidden />3D</button>
      </div>
      <span className="text-text-3">v{s.obraId ? s.versaoServidor : s.modelo.versao}{s.obraId ? "" : " · local"} · {s.selecao.size} sel.</span>
    </div>
  );
}
