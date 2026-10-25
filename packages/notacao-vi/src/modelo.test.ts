import { describe, expect, it } from "vitest";
import { ErroComandoVI, ModeloBim } from "./modelo";
import { formatarVI, parseVI } from "./vi";
import { conflitosDeAlias, resolverComando } from "./catalogo";
import { quantificar } from "./quantitativos";
import { verificarConclusao } from "./auditor";

describe("Notação VI · parser", () => {
  it("lê comando com ponto, número com vírgula, relativo e polar", () => {
    const p = parseVI('VI(PAREDE, p1=[0,0], p2=@5<0, esp=0,15, h=2.8, mat=MAT_ALV_BLOCO14)');
    expect(p.erros).toEqual([]);
    expect(p.comandos[0]?.codigo).toBe("PAREDE");
    expect(p.comandos[0]?.args["p1"]).toEqual({ x: 0, y: 0 });
    expect(p.comandos[0]?.args["p2"]).toEqual({ polar: { d: 5, ang: 0 } });
    expect(p.comandos[0]?.args["esp"]).toBe(0.15);
  });
  it("serializa de volta", () => {
    expect(formatarVI("VIGA", { p1: { x: 0, y: 0 }, p2: { x: 5, y: 0 }, b: 0.12, h: 0.4, mat: "MAT_CONC_FCK30" })).toBe("VI(VIGA, p1=[0,0], p2=[5,0], b=0.12, h=0.4, mat=MAT_CONC_FCK30)");
  });
  it("erra com mensagem e linha", () => {
    const p = parseVI("PAREDE p1=[0,0]");
    expect(p.erros[0]?.codigo).toBe("VI-000");
    expect(p.erros[0]?.linha).toBe(1);
  });
});

describe("Catálogo de comandos (RF28)", () => {
  it("resolve BR por perfil", () => {
    expect(resolverComando("BR", "CIVILCONSTRUCTOR")?.codigo).toBe("CONTRAVENTAMENTO");
    expect(resolverComando("BR", "AUTOCAD")?.codigo).toBe("QUEBRAR");
    expect(resolverComando("WALL", "AUTOCAD")?.codigo).toBe("PAREDE");
    expect(resolverComando("parede", "REVIT")?.codigo).toBe("PAREDE");
  });
  it("lista os conflitos de alias", () => {
    expect(conflitosDeAlias().map((c) => c.alias)).toEqual(expect.arrayContaining(["BR", "EL"]));
  });
});

describe("ModeloBim · aplicar + auditor", () => {
  it("cria malha, pilares nos eixos e viga; versiona; desfaz", () => {
    const m = new ModeloBim();
    m.aplicar("MALHA", { nx: 3, dx: 5, ny: 2, dy: 6 });
    expect(m.eixos).toHaveLength(5);
    expect(m.intersecoesDeEixos()).toHaveLength(6);
    const r = m.aplicar("PILAR", { centro: { x: 0, y: 0 }, b: 0.19, h: 0.4 });
    expect(r.relatorio.aprovado).toBe(true);
    expect(m.versao).toBe(2);
    m.aplicar("VIGA", { p1: { x: 0, y: 0 }, p2: { x: 5, y: 0 } });
    expect(m.elementos).toHaveLength(2);
    m.desfazer();
    expect(m.elementos).toHaveLength(1);
    m.refazer();
    expect(m.elementos).toHaveLength(2);
  });
  it("reprova pilar 15x15 (seção mínima) com todos os erros de uma vez", () => {
    const m = new ModeloBim();
    try { m.aplicar("PILAR", { centro: { x: 0, y: 0 }, b: 0.15, h: 0.15 }); throw new Error("deveria reprovar"); }
    catch (e) {
      expect(e).toBeInstanceOf(ErroComandoVI);
      const codigos = (e as ErroComandoVI).relatorio!.laco1.violacoes.map((v) => v.codigo);
      expect(codigos).toContain("C-SECAO_MIN_PILAR");
      expect(codigos).toContain("C-AREA_MIN_PILAR");
    }
    expect(m.versao).toBe(0);
  });
  it("descarta vetor nulo e parede curta (ShortCurveTolerance)", () => {
    const m = new ModeloBim();
    expect(() => m.aplicar("PAREDE", { p1: { x: 0, y: 0 }, p2: { x: 0, y: 0 } })).toThrow(ErroComandoVI);
    expect(() => m.aplicar("PAREDE", { p1: { x: 0, y: 0 }, p2: { x: 0.03, y: 0 } })).toThrow(/tolerância/);
  });
  it("envoltória aberta é aviso na edição e violação na conclusão (RF16 CA-09)", () => {
    const m = new ModeloBim();
    const r = m.aplicar("PAREDE", { p1: { x: 0, y: 0 }, p2: { x: 5, y: 0 } });
    expect(r.relatorio.aprovado).toBe(true);
    expect(r.relatorio.laco2.avisos.map((a) => a.codigo)).toContain("ENVOLTORIA_ABERTA");
    expect(verificarConclusao(m, "pav_terreo").some((v) => v.codigo === "ENVOLTORIA_ABERTA" && v.severidade === "VIOLACAO")).toBe(true);
    m.aplicar("PAREDE", { p1: { x: 5, y: 0 }, p2: { x: 5, y: 4 } });
    m.aplicar("PAREDE", { p1: { x: 5, y: 4 }, p2: { x: 0, y: 4 } });
    const fim = m.aplicar("PAREDE", { p1: { x: 0, y: 4 }, p2: { x: 0, y: 0 } });
    expect(fim.relatorio.laco2.avisos).toHaveLength(0);
    expect(verificarConclusao(m, "pav_terreo").filter((v) => v.severidade === "VIOLACAO")).toHaveLength(0);
  });
  it("Disciplina Z zera offsets e prende topo ao pavimento acima (RF17)", () => {
    const m = new ModeloBim();
    m.aplicar("PAVIMENTO", { nome: "1º", z: 3 });
    const r = m.aplicar("PILAR", { centro: { x: 1, y: 1 }, baseOffset: -0.5 });
    expect(r.relatorio.correcoes.some((c) => c.campo === "baseOffset" && c.depois === 0)).toBe(true);
    const pilar = m.elementos[0]!;
    expect(pilar.tipo === "PILAR" && pilar.nivelTopoId).toBe(m.pavimentos[1]!.id);
  });
  it("conflito de versão → CONFLITO (409)", () => {
    const m = new ModeloBim();
    m.aplicar("LINHA", { p1: { x: 0, y: 0 }, p2: { x: 1, y: 0 } });
    expect(() => m.aplicar("LINHA", { p1: { x: 0, y: 0 }, p2: { x: 1, y: 1 } }, { versaoBase: 0 })).toThrow(/versão/);
  });
  it("porta precisa caber na parede com afastamento", () => {
    const m = new ModeloBim();
    m.aplicar("PAREDE", { p1: { x: 0, y: 0 }, p2: { x: 1, y: 0 } });
    expect(() => m.aplicar("PORTA", { pos: { x: 0.5, y: 0 }, larg: 0.9 })).toThrow(ErroComandoVI);
    m.aplicar("PAREDE", { p1: { x: 0, y: 2 }, p2: { x: 4, y: 2 } });
    const r = m.aplicar("PORTA", { pos: { x: 2, y: 2 } });
    expect(r.diff.criados[0]?.tipo).toBe("PORTA");
  });
  it("quantitativo determinístico: mesmo modelo, mesmo hash", () => {
    const m = new ModeloBim();
    m.aplicar("PILAR", { centro: { x: 0, y: 0 }, b: 0.2, h: 0.5 });
    m.aplicar("VIGA", { p1: { x: 0, y: 0 }, p2: { x: 5, y: 0 }, b: 0.15, h: 0.5 });
    m.aplicar("LAJE", { contorno: [{ x: 0, y: 0 }, { x: 5, y: 0 }, { x: 5, y: 4 }, { x: 0, y: 4 }], h: 0.12 });
    const q1 = quantificar(m), q2 = quantificar(m);
    expect(q1.hash).toBe(q2.hash);
    expect(q1.totais["pav_terreo"]!.concreto_m3).toBeGreaterThan(2);
  });
});
