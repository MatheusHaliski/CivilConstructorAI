import { describe, expect, it } from "vitest";
import { ErroComandoVI, ModeloBim } from "./modelo";
import { buscarObjetos, comprimentoPercurso, percursoConexao, posicaoConector } from "./objetos";
import type { Conexao, Cota, Objeto, Parede } from "./tipos";

const p = (x: number, y: number) => ({ x, y });

function cozinha() {
  const m = new ModeloBim();
  const pav = m.pavimentos[0]!.id;
  const ap = (codigo: string, args: Record<string, unknown>) => m.aplicar(codigo, args, { pavimentoId: pav });
  const id = (codigo: string, args: Record<string, unknown>) => ap(codigo, args).diff.criados[0]!.id;
  const sul = id("PAREDE", { p1: p(0, 0), p2: p(10, 0) });
  id("PAREDE", { p1: p(10, 0), p2: p(10, 8) }); id("PAREDE", { p1: p(10, 8), p2: p(0, 8) }); id("PAREDE", { p1: p(0, 8), p2: p(0, 0) });
  const leste = id("PAREDE", { p1: p(3, 0), p2: p(3, 3.5) });
  const norte = id("PAREDE", { p1: p(0, 3.5), p2: p(3, 3.5) });
  const banho = id("PAREDE", { p1: p(3, 3.5), p2: p(3, 5.5) });
  const porta = id("PORTA", { pos: p(1.5, 3.5) });
  const cota = id("COTA_LINEAR", { p1: p(0, -0.5), p2: p(3, -0.5) });
  return { m, pav, ap, id, sul, leste, norte, banho, porta, cota };
}

describe("AUD-01 · parede deslocada leva o que depende dela", () => {
  it("ampliar a cozinha em 60 cm atualiza parede ligada, porta, cota e objeto fixado na parede", () => {
    const c = cozinha();
    const quadro = c.id("OBJETO", { item: "DECORACAO.QUADRO", pos: p(3.2, 1.5) });
    const q0 = (c.m.elemento(quadro) as Objeto).centro;
    const r = c.ap("MOVER", { sel: [c.leste], base: p(3, 0), destino: p(3.6, 0) });
    const norte = c.m.elemento(c.norte) as Parede, banho = c.m.elemento(c.banho) as Parede, cota = c.m.elemento(c.cota) as Cota;
    expect(Math.max(norte.p1.x, norte.p2.x)).toBeCloseTo(3.6, 6);
    expect(banho.p1.x).toBeCloseTo(3, 6); // paralela: não é arrastada
    const porta = c.m.elemento(c.porta) as { posicao: number };
    expect(porta.posicao * 3.6).toBeCloseTo(1.5, 6); // posição absoluta mantida
    expect(Math.abs(cota.p2.x - cota.p1.x)).toBeCloseTo(3.6, 6);
    expect((c.m.elemento(quadro) as Objeto).centro.x).toBeCloseTo(q0.x + 0.6, 6);
    expect(r.diff.alterados.length).toBeGreaterThanOrEqual(5);
    expect(r.relatorio.laco2.avisos.filter((a) => a.codigo === "ENVOLTORIA_ABERTA" && a.mensagem.includes("0.60"))).toHaveLength(0);
  });
});

describe("AUD-02/08 · objetos da biblioteca", () => {
  it("busca por nome, categoria, palavra-chave e dimensão", () => {
    expect(buscarObjetos("sofá").map((i) => i.id)).toContain("SALA.SOFA_3L");
    expect(buscarObjetos("", "BANHEIRO").length).toBeGreaterThanOrEqual(4);
    expect(buscarObjetos("geladeira 70").map((i) => i.id)).toEqual(["COZINHA.GELADEIRA"]);
    expect(buscarObjetos("generico").length).toBeGreaterThan(30);
  });
  it("objeto de parede encosta na face e olha para fora; luminária vai ao teto; pessoa escala sem deformar", () => {
    const c = cozinha();
    const arandela = c.m.elemento(c.id("OBJETO", { item: "ILUMINACAO.ARANDELA", pos: p(1.5, 0.4) })) as Objeto;
    expect(arandela.paredeId).toBe(c.sul);
    expect(arandela.centro.y).toBeCloseTo(0.075 + 0.06, 6); // meia espessura + meia profundidade
    expect(arandela.rotacao).toBeCloseTo(0, 6); // frente para +y (para dentro da cozinha)
    expect(arandela.elevacao).toBe(1.8);
    const pend = c.m.elemento(c.id("OBJETO", { item: "ILUMINACAO.PENDENTE", pos: p(1.5, 1.5) })) as Objeto;
    expect(pend.elevacao).toBeCloseTo(2.8 - 0.6, 6);
    expect(pend.camada).toBe("eletrica");
    const pessoa = c.m.elemento(c.id("OBJETO", { item: "PESSOAS.ADULTO", pos: p(5, 2), altura: 1.6 })) as Objeto;
    expect(pessoa.dim.a).toBe(1.6);
    expect(pessoa.dim.l / pessoa.dim.a).toBeCloseTo(0.5 / 1.75, 2);
    expect(pessoa.camada).toBe("humanos");
  });
  it("equipamento não deforma; móvel redimensionável aceita; objeto de parede sem parede é recusado", () => {
    const c = cozinha();
    const gel = c.id("OBJETO", { item: "COZINHA.GELADEIRA", pos: p(0.5, 2.8) });
    expect(() => c.ap("ALTERAR", { id: gel, props: { dim: { l: 0.7, p: 0.75, a: 2.5 } } })).toThrow(/não pode ser deformado/);
    expect(() => c.ap("ESCALAR", { sel: [gel], base: p(0, 0), fator: 2 })).toThrow(ErroComandoVI);
    const mesa = c.id("OBJETO", { item: "SALA.MESA_JANTAR_6", pos: p(6, 6), l: 2.2 });
    expect((c.m.elemento(mesa) as Objeto).dim.l).toBe(2.2);
    expect(() => c.ap("OBJETO", { item: "ILUMINACAO.ARANDELA", pos: p(6, 6) })).toThrow(/fixado em parede/);
    expect(() => c.ap("OBJETO", { item: "SALA.NAO_EXISTE", pos: p(6, 6) })).toThrow(/não existe na biblioteca/);
  });
});

describe("AUD-03/04 · conexões entre conectores", () => {
  function instalacoes() {
    const c = cozinha();
    const qd = c.id("OBJETO", { item: "INSTALACOES.QUADRO_DISTRIBUICAO", pos: p(9.8, 1) });
    const tom = c.id("OBJETO", { item: "INSTALACOES.TOMADA_10A", pos: p(0.5, 3.3) });
    const gel = c.id("OBJETO", { item: "COZINHA.GELADEIRA", pos: p(0.5, 2.8) });
    return { ...c, qd, tom, gel };
  }
  it("geladeira → tomada → quadro: conectores escolhidos automaticamente, sentido saída → entrada", () => {
    const c = instalacoes();
    const t = c.m.elemento(c.id("CONECTAR", { de: c.qd, para: c.tom, circuito: "C2", disjuntor: 10 })) as Conexao;
    expect(t.de).toEqual({ id: c.qd, conector: "S1" });
    expect(t.para).toEqual({ id: c.tom, conector: "E1" });
    const g = c.m.elemento(c.id("CONECTAR", { de: c.gel, para: c.tom })) as Conexao; // ordem invertida de propósito
    expect(g.de).toEqual({ id: c.tom, conector: "S1" });
    expect(g.para).toEqual({ id: c.gel, conector: "E1" });
    expect(g.camada).toBe("eletrica");
    expect(g.circuito).toBe("C2"); // herda o circuito que alimenta a tomada
    const pts = percursoConexao(c.m, g);
    const a = posicaoConector(c.m, c.m.elemento(c.tom) as Objeto, "S1")!, b = posicaoConector(c.m, c.m.elemento(c.gel) as Objeto, "E1")!;
    expect(pts[0]).toEqual({ x: a.x, y: a.y, z: a.z });
    expect(pts[pts.length - 1]).toEqual({ x: Math.round(b.x * 1000) / 1000, y: Math.round(b.y * 1000) / 1000, z: Math.round(b.z * 1000) / 1000 });
    expect(pts).toHaveLength(2); // cabo do aparelho: tomada → plugue em linha direta
    expect(comprimentoPercurso(pts)).toBeCloseTo(Math.hypot(b.x - a.x, b.y - a.y, b.z - a.z), 2);
    // quadro → tomada é eletroduto: sobe ao forro
    const pq = percursoConexao(c.m, t), qa = posicaoConector(c.m, c.m.elemento(c.qd) as Objeto, "S1")!, qb = posicaoConector(c.m, c.m.elemento(c.tom) as Objeto, "E1")!;
    expect(comprimentoPercurso(pq)).toBeCloseTo((2.75 - qa.z) + Math.abs(qb.x - qa.x) + Math.abs(qb.y - qa.y) + (2.75 - qb.z), 2);
  });
  it("sofá não oferece conexão; chuveiro de 5500 W não vai em tomada de 10 A; 127 V × 220 V é recusado", () => {
    const c = instalacoes();
    const sofa = c.id("OBJETO", { item: "SALA.SOFA_3L", pos: p(6, 1) });
    expect(() => c.ap("CONECTAR", { de: c.tom, para: sofa })).toThrow(/Não há par de conectores compatível/);
    const chuv = c.id("OBJETO", { item: "BANHEIRO.CHUVEIRO_ELETRICO", pos: p(2.9, 4.5) });
    try { c.ap("CONECTAR", { de: `${c.tom}:S1`, para: `${chuv}:E1` }); expect.unreachable(); } catch (e) {
      const msgs = (e as ErroComandoVI).relatorio!.laco1.violacoes.map((x) => x.codigo);
      expect(msgs).toContain("CON-TENSAO"); expect(msgs).toContain("CON-CORRENTE");
    }
    const lav = c.id("OBJETO", { item: "BANHEIRO.LAVATORIO", pos: p(1.5, 7.8) });
    expect(() => c.ap("CONECTAR", { de: `${c.tom}:S1`, para: `${lav}:AF1` })).toThrow(/Sistemas incompatíveis/);
  });
  it("mover a geladeira conectada mantém a ligação e avisa o percurso recalculado; apagar leva as conexões", () => {
    const c = instalacoes();
    c.id("CONECTAR", { de: c.tom, para: c.gel });
    const r = c.ap("MOVER", { sel: [c.gel], base: p(0, 0), destino: p(1, 0) });
    expect(r.relatorio.laco2.avisos.some((a) => a.codigo === "CON-RECALCULADA")).toBe(true);
    const apagar = c.ap("APAGAR", { sel: [c.gel] });
    expect(apagar.diff.removidos).toHaveLength(2);
    expect(c.m.elementos.some((e) => e.tipo === "CONEXAO")).toBe(false);
  });
  it("esgoto corre sob o piso com a declividade da conexão", () => {
    const c = instalacoes();
    const lav = c.id("OBJETO", { item: "BANHEIRO.LAVATORIO", pos: p(1.5, 7.8) });
    const ci = c.id("OBJETO", { item: "INSTALACOES.CAIXA_INSPECAO", pos: p(1.5, 9) });
    const e = c.m.elemento(c.id("CONECTAR", { de: lav, para: ci, sistema: "ESGOTO", declividade: 0.02 })) as Conexao;
    const pts = percursoConexao(c.m, e);
    expect(pts[1]!.z).toBeCloseTo(-0.25, 6);
    const horizontal = Math.abs(pts[pts.length - 2]!.x - pts[1]!.x) + Math.abs(pts[pts.length - 2]!.y - pts[1]!.y);
    expect(pts[1]!.z - pts[pts.length - 2]!.z).toBeCloseTo(0.02 * horizontal, 3);
  });
});
