/** RF11 · quantitativos determinísticos do modelo (concreto m³, fôrma m², aço kg) por pavimento e elemento. */
import type { Elemento, Laje, Parede, Pilar, Viga } from "./tipos";
import type { ModeloBim } from "./modelo";
import { areaPoligono, dist, perimetroPoligono } from "./geometria";
import { material } from "./tabela-a";

export type LinhaQuantitativo = {
  pavimentoId: string; elementoId: string; tipo: Elemento["tipo"]; rotulo: string; materialId?: string;
  concreto_m3: number; forma_m2: number; aco_kg: number; alvenaria_m2: number; sinapi?: string;
};

export type Quantitativo = { versao: number; linhas: LinhaQuantitativo[]; totais: Record<string, { concreto_m3: number; forma_m2: number; aco_kg: number; alvenaria_m2: number }>; hash: string };

/** taxas de aço indicativas (kg/m³) por tipo, usadas até o RF07 fornecer armaduras calculadas */
const TAXA_ACO = { PILAR: 110, VIGA: 90, LAJE: 70, SAPATA: 60, BLOCO: 80, RADIER: 75 } as const;

export function quantificar(modelo: ModeloBim): Quantitativo {
  const linhas: LinhaQuantitativo[] = [];
  for (const e of modelo.elementos) {
    if (e.rascunhoIaId) continue; // rascunho de IA não entra no quantitativo
    const pav = modelo.pavimento(e.pavimentoId); const acima = modelo.pavimentoAcima(e.pavimentoId);
    const rotulo = e.rotulo ?? modelo.rotuloAutomatico(e);
    const mat = e.materialId ? material(e.materialId) : undefined;
    const l: LinhaQuantitativo = { pavimentoId: e.pavimentoId, elementoId: e.id, tipo: e.tipo, rotulo, materialId: e.materialId, concreto_m3: 0, forma_m2: 0, aco_kg: 0, alvenaria_m2: 0, sinapi: mat?.baseQuantities.sinapi };
    switch (e.tipo) {
      case "PILAR": {
        const p = e as Pilar; const base = modelo.pavimento(p.nivelBaseId), topo = modelo.pavimento(p.nivelTopoId);
        const altura = base && topo && topo.id !== base.id ? topo.elevacao - base.elevacao : 3.0;
        const area = p.secao === "CIRCULAR" ? Math.PI * (p.b / 2) ** 2 : p.b * p.h;
        const per = p.secao === "CIRCULAR" ? Math.PI * p.b : 2 * (p.b + p.h);
        l.concreto_m3 = area * altura; l.forma_m2 = per * altura; l.aco_kg = l.concreto_m3 * TAXA_ACO.PILAR; break;
      }
      case "VIGA": {
        const v = e as Viga; const L = dist(v.p1, v.p2);
        const lajeH = (modelo.elementosDo(e.pavimentoId).find((x) => x.tipo === "LAJE") as Laje | undefined)?.h ?? 0;
        // desconta a laje apoiada no topo da viga e a interseção com pilares nas extremidades (prioridade pilar > viga > laje)
        const pilares = modelo.elementosDo(e.pavimentoId).filter((x): x is Pilar => x.tipo === "PILAR" && (dist(x.centro, v.p1) < 0.3 || dist(x.centro, v.p2) < 0.3));
        const Lliq = Math.max(0, L - pilares.reduce((s, p) => s + Math.max(p.b, p.h) / 2, 0));
        l.concreto_m3 = v.b * (v.h - lajeH) * Lliq + v.b * lajeH * Lliq; // volume bruto da viga; laje descontada abaixo na laje
        l.forma_m2 = (2 * v.h + v.b) * Lliq - lajeH * Lliq; l.aco_kg = l.concreto_m3 * TAXA_ACO.VIGA; break;
      }
      case "LAJE": {
        const s = e as Laje; const area = areaPoligono(s.contorno);
        // desconta as faixas de viga dentro do contorno (prioridade viga > laje)
        const vigas = modelo.elementosDo(e.pavimentoId).filter((x): x is Viga => x.tipo === "VIGA");
        const areaVigas = vigas.reduce((acc, v) => acc + v.b * dist(v.p1, v.p2), 0);
        const areaLiq = Math.max(0, area - Math.min(areaVigas, area * 0.3));
        l.concreto_m3 = areaLiq * s.h; l.forma_m2 = areaLiq + perimetroPoligono(s.contorno) * s.h; l.aco_kg = l.concreto_m3 * TAXA_ACO.LAJE; break;
      }
      case "PAREDE": {
        const w = e as Parede; const L = dist(w.p1, w.p2);
        const aberturas = modelo.elementos.filter((x) => (x.tipo === "PORTA" || x.tipo === "JANELA") && x.paredeId === w.id).reduce((s, x) => s + ("largura" in x ? x.largura * x.altura : 0), 0);
        l.alvenaria_m2 = Math.max(0, L * w.altura - aberturas); break;
      }
      case "SAPATA": case "BLOCO": case "RADIER": {
        l.concreto_m3 = e.bx * e.by * e.h; l.forma_m2 = 2 * (e.bx + e.by) * e.h; l.aco_kg = l.concreto_m3 * TAXA_ACO[e.tipo]; break;
      }
      default: continue;
    }
    for (const k of ["concreto_m3", "forma_m2", "aco_kg", "alvenaria_m2"] as const) l[k] = Math.round(l[k] * 1000) / 1000;
    linhas.push(l);
    void pav; void acima;
  }
  const totais: Quantitativo["totais"] = {};
  for (const l of linhas) {
    const t = (totais[l.pavimentoId] ??= { concreto_m3: 0, forma_m2: 0, aco_kg: 0, alvenaria_m2: 0 });
    t.concreto_m3 += l.concreto_m3; t.forma_m2 += l.forma_m2; t.aco_kg += l.aco_kg; t.alvenaria_m2 += l.alvenaria_m2;
  }
  for (const t of Object.values(totais)) for (const k of Object.keys(t) as (keyof typeof t)[]) t[k] = Math.round(t[k] * 1000) / 1000;
  const hash = hashSimples(JSON.stringify(linhas));
  return { versao: modelo.versao, linhas, totais, hash };
}

function hashSimples(s: string): string {
  let h = 0x811c9dc5;
  for (let i = 0; i < s.length; i++) { h ^= s.charCodeAt(i); h = Math.imul(h, 0x01000193) >>> 0; }
  return h.toString(16).padStart(8, "0");
}
