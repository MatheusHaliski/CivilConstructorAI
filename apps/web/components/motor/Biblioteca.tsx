"use client";
/**
 * Biblioteca de objetos (AUD-02): busca por nome, categoria, palavra-chave e dimensão (ex.: "geladeira 70"), prévia em
 * planta e elevação desenhada a partir das mesmas peças do 3D, conectores do item e inserção por clique na planta.
 * Fabricante: só "genérico" — dimensões típicas adotadas, não catálogo de fabricante (dito na tela).
 */
import { useMemo, useState } from "react";
import { CATALOGO_OBJETOS, buscarObjetos, rotuloSistema, type ItemObjeto } from "@cca/notacao-vi";
import { useMotor } from "@/lib/motor/estado";
import { pecasDaForma } from "@/lib/motor/formas";
import { Botao } from "@/components/ui";

function Previa({ it }: { it: ItemObjeto }) {
  const { l, p, a } = it.dim; const pecas = pecasDaForma(it.forma, l, p, a, it.cor);
  const W = 140, H = 64, k = Math.min((W / 2 - 8) / Math.max(l, 0.1), (H - 8) / Math.max(p, a, 0.1));
  // planta (esquerda, frente para baixo) e elevação frontal (direita)
  return (
    <svg viewBox={`0 0 ${W} ${H}`} width={W} height={H} role="img" aria-label={`Prévia de ${it.nome}: ${l} × ${p} × ${a} m`} className="shrink-0 rounded border border-line-soft bg-bg">
      <g transform={`translate(${W / 4} ${H / 2})`}>{pecas.map((pc, i) => pc.g === "caixa" ? <rect key={i} x={(pc.c[0] - pc.s[0] / 2) * k} y={(-pc.c[1] - pc.s[1] / 2) * k} width={pc.s[0] * k} height={pc.s[1] * k} fill={pc.cor ?? it.cor} fillOpacity={0.5} stroke="#374151" strokeWidth={0.6} /> : <circle key={i} cx={pc.c[0] * k} cy={-pc.c[1] * k} r={(pc.s[0] / 2) * k} fill={pc.cor ?? it.cor} fillOpacity={0.5} stroke="#374151" strokeWidth={0.6} />)}</g>
      <g transform={`translate(${(3 * W) / 4} ${H - 4})`}>{pecas.map((pc, i) => <rect key={i} x={(pc.c[0] - pc.s[0] / 2) * k} y={-(pc.c[2] + pc.s[2] / 2) * k} width={pc.s[0] * k} height={pc.s[2] * k} rx={pc.g === "caixa" ? 0 : 2} fill={pc.cor ?? it.cor} fillOpacity={0.5} stroke="#374151" strokeWidth={0.6} />)}</g>
      <text x={W / 4} y={9} fontSize={7} textAnchor="middle" fill="#6B7280">planta</text><text x={(3 * W) / 4} y={9} fontSize={7} textAnchor="middle" fill="#6B7280">elevação</text>
    </svg>
  );
}

export function PainelBiblioteca() {
  const s = useMotor();
  const [termo, setTermo] = useState("");
  const [categoria, setCategoria] = useState("");
  const itens = useMemo(() => buscarObjetos(termo, categoria || undefined), [termo, categoria]);
  const inserir = (it: ItemObjeto) => { s.setVista("2D"); s.iniciar("OBJETO", { valores: { item: it.id } }); };
  return (
    <div className="flex flex-col gap-2 text-sm">
      <h3 className="text-sm font-semibold">Biblioteca de objetos</h3>
      <input type="search" value={termo} onChange={(e) => setTermo(e.target.value)} placeholder="Nome, categoria ou medida (ex.: geladeira 70)" aria-label="Buscar na biblioteca" className="min-h-9 rounded border border-line bg-bg px-2 text-sm" />
      <div className="flex gap-2">
        <label className="flex flex-1 flex-col gap-1 text-xs"><span className="text-text-3">Categoria</span>
          <select value={categoria} onChange={(e) => setCategoria(e.target.value)} className="min-h-9 rounded border border-line bg-bg px-1 text-sm"><option value="">Todas</option>{CATALOGO_OBJETOS.categorias.map((c) => <option key={c.id} value={c.id}>{c.nome}</option>)}</select>
        </label>
        <label className="flex flex-1 flex-col gap-1 text-xs"><span className="text-text-3">Fabricante</span>
          <select className="min-h-9 rounded border border-line bg-bg px-1 text-sm" aria-describedby="aviso-fabricante"><option>Genérico</option></select>
        </label>
      </div>
      <p id="aviso-fabricante" className="text-[11px] text-text-3">{CATALOGO_OBJETOS.aviso}</p>
      <p className="text-xs text-text-3" aria-live="polite">{itens.length} item(ns)</p>
      <ul className="flex flex-col gap-2">
        {itens.map((it) => (
          <li key={it.id} className="flex flex-col gap-1 rounded border border-line-soft p-2">
            <div className="flex items-start gap-2">
              <Previa it={it} />
              <div className="min-w-0 flex-1">
                <p className="font-medium leading-tight">{it.nome}</p>
                <p className="mono text-[11px] text-text-3">{it.id}</p>
                <p className="text-xs text-text-2">{it.dim.l} × {it.dim.p} × {it.dim.a} m · {it.montagem === "PISO" ? "piso" : it.montagem === "PAREDE" ? `parede${it.elevacao != null ? `, a ${it.elevacao} m` : ""}` : "teto"}{it.redimensionavel ? " · redimensionável" : ""}{it.alturaVariavel ? ` · altura ${it.alturaVariavel.min}–${it.alturaVariavel.max} m` : ""}</p>
              </div>
            </div>
            <p className="text-[11px] text-text-3">{it.conectores.length ? it.conectores.map((k) => `${k.id} ${rotuloSistema(k.sistema)} ${k.fluxo === "SAI" ? "saída" : "entrada"}${k.tensaoV ? ` ${k.tensaoV} V` : ""}${k.potenciaW ? ` ${k.potenciaW} W` : ""}${k.dnMm ? ` DN${k.dnMm}` : ""}`).join(" · ") : "Sem conexões elétricas ou hidráulicas."}{it.nota ? ` ${it.nota}` : ""}</p>
            <Botao pequeno variante="secundario" onClick={() => inserir(it)} disabled={!s.podeEditar}>Inserir na planta</Botao>
          </li>
        ))}
      </ul>
    </div>
  );
}
