import { describe, expect, it } from "vitest";
import { buscarNavegacao, gruposGlobais, gruposObra, itemAtivo, obraDoCaminho } from "./navegacao";

describe("navegação", () => {
  it("toda sub-aba tem rota própria e única", () => {
    const hrefs = [...gruposGlobais({ suportePlataforma: true }), ...gruposObra("o1")].flatMap((g) => g.itens.map((i) => i.href));
    expect(new Set(hrefs).size).toBe(hrefs.length);
  });
  it("obraDoCaminho ignora /obras/nova", () => {
    expect(obraDoCaminho("/obras/abc/geotecnia/furos")).toBe("abc");
    expect(obraDoCaminho("/obras/nova")).toBeNull();
    expect(obraDoCaminho("/conta")).toBeNull();
  });
  it("item exato não acende em sub-rotas", () => {
    const painel = gruposObra("o1")[0]!.itens[0]!;
    expect(itemAtivo(painel, "/obras/o1")).toBe(true);
    expect(itemAtivo(painel, "/obras/o1/modelo")).toBe(false);
  });
  it("busca por palavra-chave sem acento", () => {
    const r = buscarNavegacao(gruposObra("o1"), "sondagem");
    expect(r[0]?.href).toBe("/obras/o1/geotecnia/furos");
    expect(buscarNavegacao(gruposObra("o1"), "orcamento")[0]?.rotulo).toBe("Orçamento SINAPI");
  });
});

import { destinoSeguro } from "./formatos";
describe("destinoSeguro", () => {
  it("aceita só caminhos internos", () => {
    expect(destinoSeguro("/convite/abc")).toBe("/convite/abc");
    expect(destinoSeguro("https://mal.example/x")).toBe("/obras");
    expect(destinoSeguro("//mal.example")).toBe("/obras");
    expect(destinoSeguro("/\\mal.example")).toBe("/obras");
    expect(destinoSeguro(null)).toBe("/obras");
  });
});
