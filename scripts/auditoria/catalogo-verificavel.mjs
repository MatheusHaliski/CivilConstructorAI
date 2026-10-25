// Gera docs/auditoria/CATALOGO_VERIFICAVEL.md cruzando, para cada comando do catálogo (packages/notacao-vi), onde ele
// é executado de fato (backend ModeloBim, motor TS, estado do front) e onde é testado. Nada é declarado "coberto"
// só por estar na lista: a classificação sai do código.
import fs from "node:fs";
import path from "node:path";

const R = new URL("../../", import.meta.url).pathname;
const ler = (p) => fs.readFileSync(path.join(R, p), "utf8");
const todos = (dir, ext) => fs.readdirSync(path.join(R, dir), { recursive: true }).filter((f) => ext.some((e) => f.endsWith(e))).map((f) => path.join(dir, f));

const catTs = ler("packages/notacao-vi/src/catalogo.ts");
const comandos = [...catTs.matchAll(/\{ codigo: "([A-Z_0-9]+)", nomePtBr: "([^"]+)", nomeEn: "([^"]+)", categoria: "([A-Z]+)",\s*aliases: ([^\n]+?), rf: \[([^\]]*)\]/g)]
  .map((m) => ({ codigo: m[1], pt: m[2], en: m[3], cat: m[4], aliases: m[5], rf: m[6].replace(/"/g, "") }));

const casosJava = new Set([...ler("backend/cca-domain/src/main/java/br/com/civilconstructorai/modelo/ModeloBim.java").matchAll(/case ((?:"[A-Z_0-9]+"(?:, )?)+) ->/g)].flatMap((m) => [...m[1].matchAll(/"([A-Z_0-9]+)"/g)].map((x) => x[1])));
const casosTs = new Set([...ler("packages/notacao-vi/src/modelo.ts").matchAll(/case "([A-Z_0-9]+)"/g)].map((m) => m[1]));
const motorFront = ["apps/web/lib/motor/estado.ts", "apps/web/lib/motor/entrada.ts", "apps/web/components/motor/Motor.tsx", "apps/web/components/motor/Canvas2D.tsx", "apps/web/components/motor/Cena3D.tsx", "apps/web/components/motor/Paineis.tsx"].map(ler).join("\n");
const testes = [...todos("backend", ["Test.java", "IT.java"]).filter((f) => !f.includes("/target/")), ...todos("packages/notacao-vi/src", [".test.ts"]), ...todos("apps/web/lib", [".test.ts"])].map((f) => ({ f, t: ler(f) }));

const ALIAS_FALSO = /^(L|C|A)$/;
const linhas = comandos.map((c) => {
  const java = casosJava.has(c.codigo), ts = casosTs.has(c.codigo);
  const front = new RegExp(`["'\`]${c.codigo}["'\`]`).test(motorFront);
  const t = testes.filter(({ t }) => new RegExp(`["'(]${c.codigo}["',)]`).test(t)).map(({ f }) => path.basename(f));
  const persiste = c.cat === "DESENHO" || c.cat === "MODIFICACAO" || (c.cat === "ANOTACAO" && !["ESTILO_COTA", "LOCALIZAR"].includes(c.codigo));
  let estado;
  if (persiste) estado = java && ts && t.length ? "Implementado e verificado" : java || ts ? "Parcialmente implementado" : "Ausente";
  else estado = front ? (t.length ? "Implementado e verificado" : "Parcialmente implementado") : "Ausente";
  return { ...c, java, ts, front, t, estado, persiste };
});

const cont = (e) => linhas.filter((l) => l.estado === e).length;
const sim = (b) => (b ? "sim" : "—");
let md = `# Catálogo verificável de comandos

Gerado por \`scripts/auditoria/catalogo-verificavel.mjs\` em ${new Date().toISOString().slice(0, 10)} a partir do código (não da documentação).
Referências funcionais: **AutoCAD 2026** (Windows, produto base, sem toolsets; *Command Reference* da ajuda oficial) e **Revit 2026**
(Windows, disciplinas Arquitetura e Estrutura; atalhos padrão do diálogo *Keyboard Shortcuts*). O AutoCAD tem cerca de 1.590 comandos
(contagem de fonte secundária, CAD Forum) — este catálogo cobre **${comandos.length}**, escolhidos para projeto civil; os demais estão
classificados como fora do escopo na seção 3, por família.

| Estado | Comandos |
|---|---:|
| Implementado e verificado | ${cont("Implementado e verificado")} |
| Parcialmente implementado | ${cont("Parcialmente implementado")} |
| Ausente | ${cont("Ausente")} |

Critério: comando que altera o modelo só conta como **implementado e verificado** se for executado pelo backend (\`ModeloBim.java\`, fonte da
verdade), pelo motor local (\`modelo.ts\`, prévia) e aparecer em pelo menos um teste automatizado. Comando de vista/modo conta se o motor do
front o trata e há teste. "Parcial" = executa em um dos lados, ou não tem teste.

## 1. Comandos do catálogo

| Código | pt-BR | Referência (EN) | Categoria | Backend | Motor TS | Front | Testes | Estado | RF |
|---|---|---|---|---|---|---|---|---|---|
${linhas.map((l) => `| \`${l.codigo}\` | ${l.pt} | ${l.en} | ${l.cat.toLowerCase()} | ${l.persiste ? sim(l.java) : "n/a"} | ${l.persiste ? sim(l.ts) : "n/a"} | ${sim(l.front)} | ${l.t.length ? l.t.slice(0, 3).join(", ") : "—"} | ${l.estado} | ${l.rf} |`).join("\n")}

## 2. Comandos executados pelo backend que não estão no catálogo

${[...casosJava].filter((c) => !comandos.some((x) => x.codigo === c)).map((c) => `\`${c}\``).join(", ") || "—"}

(Alguns são internos — \`ALTERAR\`, \`MOVER_EIXO\` — e outros são sinônimos de domínio, como \`SAPATA\`/\`BLOCO\`/\`RADIER\` para \`FUNDACAO\`.)
`;
fs.writeFileSync(path.join(R, "docs/auditoria/CATALOGO_VERIFICAVEL.md"), md);
console.log(JSON.stringify({ total: comandos.length, verificado: cont("Implementado e verificado"), parcial: cont("Parcialmente implementado"), ausente: cont("Ausente") }));
console.log(linhas.filter((l) => l.estado !== "Implementado e verificado").map((l) => `${l.codigo}:${l.estado}${l.persiste ? ` j=${l.java} t=${l.ts}` : ` f=${l.front}`} testes=${l.t.length}`).join("\n"));
