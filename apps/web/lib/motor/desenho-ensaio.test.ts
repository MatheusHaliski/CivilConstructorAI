import { it, expect } from "vitest";
import { readFileSync } from "node:fs";
import path from "node:path";
import { ModeloBim, parseVI } from "@cca/notacao-vi";

it("os comandos desenham a mesma geometria do ensaio integrado D1", () => {
  const root = path.resolve("../..");
  const d = JSON.parse(readFileSync(path.join(root,"docs/validacao/casos-estruturais.json"),"utf8")).desenhoIntegrado;
  const m = new ModeloBim();
  for (const comando of parseVI(readFileSync(path.join(root,"docs/validacao/desenho-base.vi"),"utf8")).comandos) m.aplicar(comando.codigo,comando.args);
  const cobertura = m.pavimentos.find(p => p.nome === "Cobertura")!;
  expect(cobertura.elevacao).toBe(d.altura);
  for (const comando of parseVI(readFileSync(path.join(root,"docs/validacao/desenho-cobertura.vi"),"utf8")).comandos) m.aplicar(comando.codigo,comando.args,{pavimentoId:cobertura.id});
  const pilares = m.elementos.filter(e => e.tipo === "PILAR");
  const vigas = m.elementos.filter(e => e.tipo === "VIGA");
  const lajes = m.elementos.filter(e => e.tipo === "LAJE");
  expect(pilares).toHaveLength(4); expect(vigas).toHaveLength(4); expect(lajes).toHaveLength(1);
  expect(pilares.map(p => [p.centro.x,p.centro.y])).toEqual([[0,0],[d.largura,0],[d.largura,d.profundidade],[0,d.profundidade]]);
  for (const p of pilares) { expect(p.b).toBe(d.bPilar); expect(p.h).toBe(d.hPilar); expect(p.nivelTopoId).toBe(cobertura.id); }
  for (const v of vigas) { expect(v.b).toBe(d.bViga); expect(v.h).toBe(d.hViga); expect(v.pavimentoId).toBe(cobertura.id); }
  expect(lajes[0]!.h).toBe(d.hLaje);
  expect(lajes[0]!.contorno).toEqual(pilares.map(p => p.centro));
});
