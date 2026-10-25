import { beforeEach, describe, expect, it } from "vitest";
import { useMotor } from "./estado";

// Comandos que o motor do front traduz para comandos do ModeloBim (CONCORDAR → ALTERAR + ARCO etc.). Roda a máquina de
// estados sem obra (modo local), como o usuário: inicia o comando, responde cada passo do roteiro e confere o modelo.
const m = () => useMotor.getState();
const ids = () => m().modelo.elementos.map((e) => e.id);
const elem = (id: string) => m().modelo.elemento(id) as unknown as Record<string, { x: number; y: number }> & { tipo: string; rotulo?: string; camada: string; materialId?: string };
const ultimoErro = () => [...m().log].reverse().find((l) => l.tipo === "erro")?.texto;

async function linha(x1: number, y1: number, x2: number, y2: number) {
  await m().aplicar("LINHA", { p1: { x: x1, y: y1 }, p2: { x: x2, y: y2 } });
  return ids().at(-1)!;
}

async function canto(codigo: "CONCORDAR" | "CHANFRAR", valores: string[]) {
  const a = await linha(0, 0, 5, 0), b = await linha(6, 1, 6, 5);
  m().iniciar(codigo);
  for (const v of valores) await m().entradaTexto(v);
  await m().entradaSelecao([a], true);
  await m().entradaSelecao([b], true);
  return { a, b };
}

beforeEach(async () => {
  useMotor.setState({ comando: null, maquina: "OCIOSO", selecao: new Set(), ultimoComando: null });
  await m().carregar(null, true);
});

describe("comandos traduzidos pelo motor do front (RF28)", () => {
  it("CONCORDAR raio 0 fecha o canto na interseção (FILLET R=0)", async () => {
    const { a, b } = await canto("CONCORDAR", ["0"]);
    expect(elem(a).p2).toEqual({ x: 6, y: 0 });
    expect(elem(b).p1).toEqual({ x: 6, y: 0 });
    expect(m().modelo.elementos).toHaveLength(2);
  });

  it("CONCORDAR raio 1 recua as linhas e cria arco tangente", async () => {
    const { a, b } = await canto("CONCORDAR", ["1"]);
    expect(elem(a).p2!.x).toBeCloseTo(5, 6);
    expect(elem(b).p1!.y).toBeCloseTo(1, 6);
    const arco = m().modelo.elementos.find((e) => e.tipo === "ARCO") as unknown as Record<string, { x: number; y: number }>;
    expect(arco).toBeDefined();
    // ponto médio do arco a 1 m do centro (5,1)
    expect(Math.hypot(arco.p2!.x - 5, arco.p2!.y - 1)).toBeCloseTo(1, 2);
  });

  it("CONCORDAR com raio maior que as linhas é recusado sem alterar o modelo", async () => {
    const { a } = await canto("CONCORDAR", ["50"]);
    expect(ultimoErro()).toMatch(/grande demais/);
    expect(elem(a).p2).toEqual({ x: 5, y: 0 });
  });

  it("CHANFRAR d1=0,5 d2=1 recua cada linha pela sua distância e liga com um segmento", async () => {
    const { a, b } = await canto("CHANFRAR", ["0.5", "1"]);
    expect(elem(a).p2!.x).toBeCloseTo(5.5, 6);
    expect(elem(b).p1!.y).toBeCloseTo(1, 6);
    expect(m().modelo.elementos.filter((e) => e.tipo === "LINHA")).toHaveLength(3);
  });

  it("linhas paralelas: CONCORDAR explica e não muda nada", async () => {
    const a = await linha(0, 0, 5, 0), b = await linha(0, 2, 5, 2);
    m().iniciar("CONCORDAR"); await m().entradaTexto("0");
    await m().entradaSelecao([a], true); await m().entradaSelecao([b], true);
    expect(ultimoErro()).toMatch(/paralelas/);
    expect(m().log.filter((l) => l.tipo === "erro" && /nada a fazer/.test(l.texto))).toHaveLength(0);
    expect(elem(a).p2).toEqual({ x: 5, y: 0 });
  });

  it("QUEBRAR divide a linha em duas, removendo o trecho entre os pontos (BREAK)", async () => {
    const a = await linha(0, 0, 10, 0);
    m().iniciar("QUEBRAR");
    await m().entradaSelecao([a], true);
    await m().entradaPonto({ x: 3, y: 0.2 });
    await m().entradaPonto({ x: 6, y: 0 });
    const ls = m().modelo.elementos.filter((e) => e.tipo === "LINHA") as unknown as { p1: { x: number }; p2: { x: number } }[];
    expect(ls.map((l) => [l.p1.x, l.p2.x])).toEqual([[0, 3], [6, 10]]);
  });

  it("COPIAR_PROPRIEDADES leva camada e material da origem para os destinos (MATCHPROP)", async () => {
    await m().aplicar("PAREDE", { p1: { x: 0, y: 5 }, p2: { x: 4, y: 5 }, esp: 0.15, h: 2.8 });
    const parede = ids().at(-1)!;
    const l = await linha(0, 0, 1, 0);
    m().iniciar("COPIAR_PROPRIEDADES");
    await m().entradaSelecao([parede], true);
    await m().entradaSelecao([l], true);
    expect(elem(parede).materialId).toBeTruthy();
    expect(elem(l).materialId).toBe(elem(parede).materialId);
    expect(elem(l).camada).toBe(elem(parede).camada);
  });

  it("COTA_CONTINUA encadeia cotas a partir da última cota (DIMCONTINUE)", async () => {
    await m().aplicar("COTA_LINEAR", { p1: { x: 0, y: 0 }, p2: { x: 2, y: 0 }, afast: 1 });
    m().iniciar("COTA_CONTINUA");
    await m().entradaPonto({ x: 5, y: 0 });
    await m().entradaPonto({ x: 9, y: 0 });
    await m().opcao("Sair");
    const cotas = m().modelo.elementos.filter((e) => e.tipo === "COTA") as unknown as { p1: { x: number }; p2: { x: number } }[];
    expect(cotas.map((c) => [c.p1.x, c.p2.x])).toEqual([[0, 2], [2, 5], [5, 9]]);
  });

  it("LINHA_QUEBRA e LINHA_DETALHE gravam linhas na camada de anotação", async () => {
    m().iniciar("LINHA_QUEBRA");
    await m().entradaPonto({ x: 0, y: 0 });
    await m().entradaPonto({ x: 2, y: 0 });
    m().iniciar("LINHA_DETALHE");
    await m().entradaPonto({ x: 0, y: 1 });
    await m().entradaPonto({ x: 2, y: 1 });
    const [q, d] = m().modelo.elementos;
    expect(q?.tipo).toBe("LINHA");
    expect(q?.camada).toBe("anotacao");
    expect(d?.camada).toBe("anotacao");
    expect(m().modelo.elementos).toHaveLength(2);
  });

  it("DESFAZER e REFAZER voltam e reaplicam o último comando", async () => {
    await linha(0, 0, 1, 1);
    m().iniciar("DESFAZER");
    await new Promise((r) => setTimeout(r, 0));
    expect(m().modelo.elementos).toHaveLength(0);
    m().iniciar("REFAZER");
    expect(m().modelo.elementos).toHaveLength(1);
  });

  it("ESTILO_COTA, BLOCO, INSERIR e LOCALIZAR não alteram o modelo e apontam para Pranchas", async () => {
    for (const c of ["ESTILO_COTA", "BLOCO", "INSERIR", "LOCALIZAR"]) {
      m().iniciar(c);
      for (let i = 0; i < 4 && m().comando; i++) await m().entradaTexto("x");
      m().cancelar();
    }
    expect(m().modelo.elementos).toHaveLength(0);
  });
});

describe("vista, modos e painéis (RF13/RF28)", () => {
  it("REGIAO vira hachura do contorno selecionado e ALINHAR move o alvo até a referência", async () => {
    await m().aplicar("POLILINHA", { pontos: [{ x: 0, y: 0 }, { x: 2, y: 0 }, { x: 2, y: 2 }], fechar: true });
    const pl = ids().at(-1)!;
    m().iniciar("REGIAO"); await m().entradaSelecao([pl], true);
    expect(m().modelo.elementos.some((e) => e.tipo === "HACHURA")).toBe(true);
    const ref = await linha(10, 0, 10, 5);
    await m().aplicar("PILAR", { centro: { x: 7, y: 3 }, b: 0.2, h: 0.2 });
    const pilar = ids().at(-1)!;
    m().iniciar("ALINHAR"); await m().entradaSelecao([ref], true); await m().entradaSelecao([pilar], true);
    expect((elem(pilar).centro as { x: number }).x).toBeCloseTo(10, 6);
  });

  it("modos alternam e voltam (F3–F10, entrada dinâmica, UCS, OTRACK)", () => {
    const pares = { "GRADE": "grade", "ORTO": "orto", "SNAP_GRADE": "snapGrade", "POLAR": "polar", "OTRACK": "otrack", "ENTRADA_DINAMICA": "entradaDinamica", "UCS_DINAMICO": "ucsDinamico", "OSNAP3D": "osnap3d" } as const;
    for (const [codigo, chave] of Object.entries(pares)) {
      const antes = m().modos[chave];
      m().iniciar(codigo);
      expect(m().modos[chave], codigo).toBe(!antes);
      m().iniciar(codigo);
      expect(m().modos[chave], codigo).toBe(antes);
    }
  });

  it("PLANO_TRABALHO alterna planta/3D; painéis abrem pelo comando", () => {
    useMotor.setState({ vista: "2D", painel: "nenhum" });
    m().iniciar("PLANO_TRABALHO"); expect(m().vista).toBe("3D");
    m().iniciar("PLANO_TRABALHO"); expect(m().vista).toBe("2D");
    const paineis = { "CAMADAS": "camadas", "VISIBILIDADE": "camadas", "PROPRIEDADES": "propriedades", "AJUDA": "ajuda", "ATALHOS": "atalhos", "HISTORICO": "historico" } as const;
    for (const [codigo, painel] of Object.entries(paineis)) { useMotor.setState({ painel: "nenhum" }); m().iniciar(codigo); expect(m().painel, codigo).toBe(painel); }
  });

  it("OCULTAR, ISOLAR e REEXIBIR controlam só a visibilidade da sessão, sem mexer no modelo", async () => {
    const a = await linha(0, 0, 1, 0), b = await linha(0, 1, 1, 1);
    const versao = m().modelo.versao;
    m().iniciar("OCULTAR"); await m().entradaSelecao([a], true);
    expect([...m().ocultos]).toEqual([a]);
    m().iniciar("ISOLAR"); await m().entradaSelecao([b], true);
    expect([...(m().isolados ?? [])]).toEqual([b]);
    m().iniciar("REEXIBIR");
    expect(m().ocultos.size).toBe(0); expect(m().isolados).toBeNull();
    m().iniciar("OCULTAR"); await m().entradaSelecao([a], true);
    m().iniciar("REEXIBIR_CATEGORIA");
    expect(m().ocultos.size).toBe(0);
    expect(m().modelo.versao).toBe(versao);
  });

  it("PAN desloca a vista pelo vetor; ZOOM_TUDO pede enquadramento; REGENERAR e UNIDADES respondem no histórico", async () => {
    useMotor.setState({ viewport: { cx: 10, cy: 8, escala: 40 } });
    m().iniciar("PAN"); await m().entradaPonto({ x: 0, y: 0 }); await m().entradaPonto({ x: 2, y: 1 });
    expect(m().viewport).toMatchObject({ cx: 8, cy: 7 });
    m().iniciar("ZOOM_TUDO");
    expect(m().comando?.valores["enquadrar"]).toBe(true);
    m().cancelar();
    const tick = m().tick;
    m().iniciar("REGENERAR"); expect(m().tick).toBeGreaterThan(tick);
    m().iniciar("UNIDADES"); expect(m().log.at(-1)?.texto).toMatch(/metros, kN, MPa/);
  });
});
