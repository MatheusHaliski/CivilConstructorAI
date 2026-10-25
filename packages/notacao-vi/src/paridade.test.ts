import { describe, expect, it } from "vitest";
import fs from "node:fs";
import path from "node:path";
import { ModeloBim } from "./modelo";

/**
 * Paridade com o backend: o mesmo arquivo de casos roda em ModeloBimParidadeTest (Java). Se a prévia do navegador e a
 * gravação no servidor divergirem, um dos dois testes quebra (auditoria 2026-10: 56 comandos sem teste algum).
 */
type Caso = {
  codigo: string; args: Record<string, unknown>; preparo?: Omit<Caso, "espera" | "preparo">[];
  espera: { criados?: number; alterados?: number; removidos?: number; eixos?: number; tipo?: string; campo?: string; valor?: number; materialId?: string;
    igual?: Record<string, string>; verifica?: { ref: string; campo: string; valor: number }[] };
};
const fixture = JSON.parse(fs.readFileSync(path.join(__dirname, "../fixtures/comandos-paridade.json"), "utf8")) as { setup: Omit<Caso, "espera">[]; casos: Caso[] };

function trocarRefs(v: unknown, ids: string[]): unknown {
  if (typeof v === "string" && /^\$\d+$/.test(v)) return ids[Number(v.slice(1))];
  if (Array.isArray(v)) return v.map((x) => trocarRefs(x, ids));
  if (v && typeof v === "object") return Object.fromEntries(Object.entries(v).map(([k, x]) => [k, trocarRefs(x, ids)]));
  return v;
}
const lerCampo = (e: unknown, campo: string) => campo.split(".").reduce<unknown>((o, k) => (o as Record<string, unknown>)?.[k], e);

describe("paridade de comandos com o backend", () => {
  for (const c of fixture.casos) {
    it(`${c.codigo}`, () => {
      const m = new ModeloBim();
      const pav = m.pavimentos[0]!.id;
      const ids: string[] = [];
      for (const s of fixture.setup) ids.push(m.aplicar(s.codigo, s.args, { pavimentoId: pav }).diff.criados[0]!.id);
      for (const s of c.preparo ?? []) ids.push(m.aplicar(s.codigo, trocarRefs(s.args, ids) as Record<string, unknown>, { pavimentoId: pav }).diff.criados[0]!.id);
      const r = m.aplicar(c.codigo, trocarRefs(c.args, ids) as Record<string, unknown>, { pavimentoId: pav });
      const e = c.espera;
      if (e.criados !== undefined) expect(r.diff.criados.length, "criados").toBe(e.criados);
      if (e.alterados !== undefined) expect(r.diff.alterados.length, "alterados").toBe(e.alterados);
      if (e.removidos !== undefined) expect(r.diff.removidos.length, "removidos").toBe(e.removidos);
      if (e.eixos !== undefined) expect(r.diff.eixosCriados?.length ?? 0, "eixos").toBe(e.eixos);
      if (e.tipo) expect(r.diff.criados[0]?.tipo).toBe(e.tipo);
      if (e.campo) expect(Number(lerCampo(r.diff.criados[0] ?? r.diff.alterados[0], e.campo))).toBeCloseTo(e.valor!, 6);
      if (e.materialId) expect((r.diff.criados[0] ?? r.diff.alterados[0])?.materialId).toBe(e.materialId);
      for (const [campo, valor] of Object.entries(e.igual ?? {})) expect(String(lerCampo(r.diff.criados[0] ?? r.diff.alterados[0], campo)), campo).toBe(trocarRefs(valor, ids));
      for (const v of e.verifica ?? []) expect(Number(lerCampo(m.elemento(trocarRefs(v.ref, ids) as string), v.campo)), `${v.ref}.${v.campo}`).toBeCloseTo(v.valor, 6);
    });
  }
});
