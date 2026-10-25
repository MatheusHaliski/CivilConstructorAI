/**
 * Parser e serializador da Notação VI: `VI(COD, nome=valor, …)`.
 * Valores: número (`5`, `0.15`, vírgula decimal aceita), texto (`MAT_CONC_FCK30`, `"Pilar P1"`), booleano
 * (`true`/`false`), ponto (`[x,y]`), lista (`[a,b,c]`), referência (`@P1` → id/rótulo de elemento), coordenada
 * relativa (`@2,0`) e polar (`@5<30`) — estas duas são resolvidas pelo motor, aqui ficam como objeto.
 */

export type ValorVI = number | string | boolean | ValorVI[] | { ref: string } | { rel: { dx: number; dy: number } } | { polar: { d: number; ang: number } } | { x: number; y: number };

export type ErroVI = { codigo: "VI-000" | "VI-001" | "VI-002" | "VI-003"; mensagem: string; linha: number; coluna: number };

export type ProgramaVI = { comandos: { codigo: string; args: Record<string, ValorVI>; linha: number }[]; erros: ErroVI[] };

const RE_COMANDO = /^\s*VI\s*\(\s*([A-Z_][A-Z0-9_]*)\s*(?:,\s*(.*))?\)\s*;?\s*$/i;

function parseValor(texto: string): ValorVI {
  const t = texto.trim();
  if (/^".*"$/.test(t) || /^'.*'$/.test(t)) return t.slice(1, -1);
  if (t === "true" || t === "false") return t === "true";
  if (/^@-?[\d.,]+<-?[\d.,]+$/.test(t)) {
    const [d, ang] = t.slice(1).split("<");
    return { polar: { d: num(d!), ang: num(ang!) } };
  }
  if (/^@-?[\d.,]+\s*,\s*-?[\d.,]+$/.test(t)) {
    const [dx, dy] = t.slice(1).split(",");
    return { rel: { dx: num(dx!), dy: num(dy!) } };
  }
  if (/^@[A-Za-z_][\w-]*$/.test(t)) return { ref: t.slice(1) };
  if (t.startsWith("[") && t.endsWith("]")) {
    const itens = dividir(t.slice(1, -1)).map(parseValor);
    if (itens.length === 2 && itens.every((v) => typeof v === "number")) return { x: itens[0] as number, y: itens[1] as number };
    return itens;
  }
  if (/^-?\d+([.,]\d+)?$/.test(t)) return num(t);
  return t;
}

function num(t: string): number {
  return Number(t.trim().replace(",", "."));
}

/** divide por vírgulas de nível zero (respeita colchetes e aspas) */
function dividir(texto: string): string[] {
  const partes: string[] = [];
  let nivel = 0, atual = "", aspas: string | null = null;
  for (const ch of texto) {
    if (aspas) { atual += ch; if (ch === aspas) aspas = null; continue; }
    if (ch === '"' || ch === "'") { aspas = ch; atual += ch; continue; }
    if (ch === "[") nivel++;
    if (ch === "]") nivel--;
    if (ch === "," && nivel === 0) { partes.push(atual); atual = ""; continue; }
    atual += ch;
  }
  if (atual.trim() !== "") partes.push(atual);
  return partes;
}

export function parseVI(fonte: string): ProgramaVI {
  const comandos: ProgramaVI["comandos"] = [];
  const erros: ErroVI[] = [];
  fonte.split(/\r?\n/).forEach((linhaTexto, i) => {
    const linha = i + 1;
    if (linhaTexto.trim() === "" || linhaTexto.trim().startsWith("#")) return;
    const m = RE_COMANDO.exec(linhaTexto);
    if (!m) {
      erros.push({ codigo: "VI-000", mensagem: "Esperado VI(CODIGO, arg=valor, …)", linha, coluna: 1 });
      return;
    }
    const codigo = m[1]!.toUpperCase();
    const args: Record<string, ValorVI> = {};
    const posicionais: ValorVI[] = [];
    for (const parte of m[2] ? dividir(m[2]) : []) {
      const eq = parte.indexOf("=");
      if (eq < 0) { posicionais.push(parseValor(parte)); continue; }
      const nome = parte.slice(0, eq).trim();
      if (!/^[a-zA-Z_][\w]*$/.test(nome)) {
        erros.push({ codigo: "VI-003", mensagem: `Nome de argumento inválido: "${nome}"`, linha, coluna: linhaTexto.indexOf(parte) + 1 });
        continue;
      }
      args[nome] = parseValor(parte.slice(eq + 1));
    }
    if (posicionais.length) args["_"] = posicionais;
    comandos.push({ codigo, args, linha });
  });
  return { comandos, erros };
}

export function formatarValor(v: unknown): string {
  if (typeof v === "number") return Number.isInteger(v) ? String(v) : v.toFixed(3).replace(/\.?0+$/, "");
  if (typeof v === "boolean") return v ? "true" : "false";
  if (typeof v === "string") return /^[A-Z0-9_]+$/.test(v) ? v : `"${v}"`;
  if (Array.isArray(v)) return `[${v.map(formatarValor).join(",")}]`;
  if (v && typeof v === "object") {
    const o = v as Record<string, unknown>;
    if ("x" in o && "y" in o) return `[${formatarValor(o.x)},${formatarValor(o.y)}]`;
    if ("ref" in o) return `@${o.ref}`;
    if ("rel" in o) { const r = o.rel as { dx: number; dy: number }; return `@${formatarValor(r.dx)},${formatarValor(r.dy)}`; }
    if ("polar" in o) { const p = o.polar as { d: number; ang: number }; return `@${formatarValor(p.d)}<${formatarValor(p.ang)}`; }
  }
  return String(v);
}

export function formatarVI(codigo: string, args: Record<string, unknown>): string {
  const partes = Object.entries(args)
    .filter(([, v]) => v !== undefined && v !== null)
    .map(([k, v]) => (k === "_" ? (v as unknown[]).map(formatarValor).join(", ") : `${k}=${formatarValor(v)}`));
  return `VI(${codigo}${partes.length ? ", " + partes.join(", ") : ""})`;
}
