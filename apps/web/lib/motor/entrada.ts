/**
 * Entrada dinâmica (RF28 CA-02): coordenadas absolutas `x,y`, relativas `@dx,dy` e polares `@d<ang`, em metros,
 * com vírgula ou ponto decimal; números; opções entre colchetes.
 */
import type { Ponto2D } from "@cca/notacao-vi";

export type EntradaResolvida =
  | { tipo: "ponto"; ponto: Ponto2D; descricao: string }
  | { tipo: "numero"; valor: number }
  | { tipo: "texto"; valor: string }
  | { tipo: "opcao"; valor: string }
  | { tipo: "erro"; mensagem: string };

const num = (t: string) => Number(t.trim().replace(",", "."));
const RE_NUM = "-?\\d+(?:[.,]\\d+)?";
const RE_ABS = new RegExp(`^(${RE_NUM})\\s*[,;]\\s*(${RE_NUM})$`);
const RE_REL = new RegExp(`^@(${RE_NUM})\\s*[,;]\\s*(${RE_NUM})$`);
const RE_POL = new RegExp(`^@(${RE_NUM})\\s*<\\s*(${RE_NUM})$`);

export function interpretarPonto(texto: string, ultimo: Ponto2D | null): EntradaResolvida {
  const t = texto.trim();
  let m = RE_ABS.exec(t);
  if (m) return { tipo: "ponto", ponto: { x: num(m[1]!), y: num(m[2]!) }, descricao: `absoluto ${m[1]},${m[2]}` };
  m = RE_REL.exec(t);
  if (m) {
    if (!ultimo) return { tipo: "erro", mensagem: "Coordenada relativa precisa de um ponto anterior." };
    return { tipo: "ponto", ponto: { x: ultimo.x + num(m[1]!), y: ultimo.y + num(m[2]!) }, descricao: `relativo @${m[1]},${m[2]}` };
  }
  m = RE_POL.exec(t);
  if (m) {
    if (!ultimo) return { tipo: "erro", mensagem: "Coordenada polar precisa de um ponto anterior." };
    const d = num(m[1]!), ang = (num(m[2]!) * Math.PI) / 180;
    return { tipo: "ponto", ponto: { x: ultimo.x + d * Math.cos(ang), y: ultimo.y + d * Math.sin(ang) }, descricao: `polar @${m[1]}<${m[2]}` };
  }
  return { tipo: "erro", mensagem: `"${t}" não é um ponto. Use x,y · @dx,dy · @d<ângulo (em metros).` };
}

export function interpretarValor(texto: string, opcoes: string[] | undefined, padrao: unknown, tipo: "VALOR" | "TEXTO" | "OPCAO" | "ANGULO"): EntradaResolvida {
  const t = texto.trim();
  if (opcoes?.length) {
    const op = opcoes.find((o) => o.toUpperCase() === t.toUpperCase() || o[0]!.toUpperCase() === t.toUpperCase());
    if (op) return { tipo: "opcao", valor: op };
  }
  if (t === "" && padrao !== undefined) return typeof padrao === "number" ? { tipo: "numero", valor: padrao } : { tipo: "texto", valor: String(padrao) };
  if (tipo === "TEXTO") return { tipo: "texto", valor: t };
  if (tipo === "OPCAO") return opcoes?.length ? { tipo: "erro", mensagem: `Escolha uma opção: ${opcoes.join(" / ")}.` } : { tipo: "texto", valor: t };
  const n = num(t);
  if (t === "" || Number.isNaN(n)) return { tipo: "erro", mensagem: `"${t}" não é um número válido.` };
  return { tipo: "numero", valor: n };
}

/** Restrições de cursor: Ortho (90°) ou Polar (múltiplos de `incremento`). Mutuamente exclusivos (CA-05). */
export function restringir(p: Ponto2D, base: Ponto2D | null, modo: "nenhum" | "orto" | "polar", incremento = 15): Ponto2D {
  if (!base || modo === "nenhum") return p;
  const dx = p.x - base.x, dy = p.y - base.y;
  if (modo === "orto") return Math.abs(dx) >= Math.abs(dy) ? { x: p.x, y: base.y } : { x: base.x, y: p.y };
  const d = Math.hypot(dx, dy); if (d < 1e-9) return p;
  const passo = (incremento * Math.PI) / 180;
  const ang = Math.round(Math.atan2(dy, dx) / passo) * passo;
  return { x: base.x + d * Math.cos(ang), y: base.y + d * Math.sin(ang) };
}

export const formatarCoord = (p: Ponto2D) => `${p.x.toFixed(3).replace(".", ",")}; ${p.y.toFixed(3).replace(".", ",")}`;
