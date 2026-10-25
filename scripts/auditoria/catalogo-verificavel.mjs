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
const testes = [...todos("backend", ["Test.java", "IT.java"]).filter((f) => !f.includes("/target/")), ...todos("packages/notacao-vi/src", [".test.ts"]), ...todos("apps/web/lib", [".test.ts"]), ...todos("packages/notacao-vi/fixtures", [".json"])].filter((f) => !(process.env.CATALOGO_IGNORAR ?? "").split(",").filter(Boolean).some((x) => f.endsWith(x))).map((f) => ({ f, t: ler(f) }));
// traduzidos pelo motor do front em comandos que o backend grava (ver montar() em apps/web/lib/motor/estado.ts)
const TRADUZIDOS = { CONCORDAR: "ALTERAR + ARCO", CHANFRAR: "ALTERAR + LINHA", QUEBRAR: "APAGAR + LINHA", COPIAR_PROPRIEDADES: "ALTERAR", COTA_CONTINUA: "COTA_LINEAR", LINHA_QUEBRA: "LINHA_DETALHE", ALINHAR: "MOVER", REGIAO: "HACHURA", DESFAZER: "desfazer do servidor", REFAZER: "refazer local" };
// o motor só mostra um aviso ("age na prancha") — não há execução
const SO_AVISO = new Set(["ESTILO_COTA", "BLOCO", "INSERIR", "LOCALIZAR"]);
// executa e tem teste, mas faz menos que a referência — fica como parcial, com o motivo
const LIMITES = {
  REEXIBIR_CATEGORIA: "reexibe tudo; o VU do Revit reexibe só a categoria escolhida",
  VISIBILIDADE: "abre o painel de camadas; não há estilos gráficos por categoria (V/G)",
  LINHA_QUEBRA: "gravada como linha de detalhe; sem o símbolo em zigue-zague",
  UNIDADES: "só informa (m, kN, MPa); não é configurável",
  ZOOM_TUDO: "enquadra o pavimento ativo; sem ZOOM janela/anterior no catálogo",
  ESTICAR: "a janela de captura só existe via Notação VI; no canvas estica a seleção inteira",
  REGIAO: "vira hachura do contorno; não há operações booleanas de região",
  OSNAP3D: "liga o modo; a vista 3D só inspeciona e seleciona — não recebe pontos, então não há o que atrair",
};

const ALIAS_FALSO = /^(L|C|A)$/;
const linhas = comandos.map((c) => {
  const java = casosJava.has(c.codigo), ts = casosTs.has(c.codigo);
  const front = new RegExp(`["'\`]${c.codigo}["'\`]`).test(motorFront);
  const t = testes.filter(({ t }) => new RegExp(`["'(]${c.codigo}["',)]`).test(t)).map(({ f }) => path.basename(f));
  const persiste = c.cat === "DESENHO" || c.cat === "MODIFICACAO" || (c.cat === "ANOTACAO" && !["ESTILO_COTA", "LOCALIZAR"].includes(c.codigo));
  let estado;
  if (SO_AVISO.has(c.codigo)) estado = "Ausente";
  else if (LIMITES[c.codigo]) estado = (persiste && !TRADUZIDOS[c.codigo] ? java || ts : front) ? "Parcialmente implementado" : "Ausente";
  else if (TRADUZIDOS[c.codigo]) estado = front && t.length ? "Implementado e verificado" : front ? "Parcialmente implementado" : "Ausente";
  else if (persiste) estado = java && ts && t.length ? "Implementado e verificado" : java || ts ? "Parcialmente implementado" : "Ausente";
  else estado = front ? (t.length ? "Implementado e verificado" : "Parcialmente implementado") : "Ausente";
  const obs = [SO_AVISO.has(c.codigo) ? "só aviso no motor (\"age na prancha\"); sem execução" + (c.codigo === "BLOCO" ? ". O mesmo código, enviado em VI, cria um bloco de fundação no backend — ambiguidade a resolver" : "") : "", TRADUZIDOS[c.codigo] ? `traduzido no front → ${TRADUZIDOS[c.codigo]}` : "", LIMITES[c.codigo] ?? ""].filter(Boolean).join("; ");
  return { ...c, java, ts, front, t, estado, persiste, obs };
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
front o trata e há teste. Comando **traduzido no front** (ex.: \`CONCORDAR\` → \`ALTERAR\` + \`ARCO\`) conta pelo teste da tradução
(\`apps/web/lib/motor/estado.test.ts\`) — o que vai ao servidor é o comando traduzido, verificado à parte. A fixture
\`packages/notacao-vi/fixtures/comandos-paridade.json\` roda nos dois motores (\`paridade.test.ts\` e \`ModeloBimParidadeTest\`) e
prova que prévia e gravação dão o mesmo resultado. "Parcial" = executa em um dos lados, ou não tem teste.

## 1. Comandos do catálogo

| Código | pt-BR | Referência (EN) | Categoria | Backend | Motor TS | Front | Testes | Estado | Observação | RF |
|---|---|---|---|---|---|---|---|---|---|---|
${linhas.map((l) => `| \`${l.codigo}\` | ${l.pt} | ${l.en} | ${l.cat.toLowerCase()} | ${l.persiste && !TRADUZIDOS[l.codigo] ? sim(l.java) : "n/a"} | ${l.persiste && !TRADUZIDOS[l.codigo] ? sim(l.ts) : "n/a"} | ${sim(l.front)} | ${l.t.length ? l.t.slice(0, 3).join(", ") : "—"} | ${l.estado} | ${l.obs} | ${l.rf} |`).join("\n")}

## 2. Comandos executados pelo backend que não estão no catálogo

${[...casosJava].filter((c) => !comandos.some((x) => x.codigo === c)).map((c) => `\`${c}\``).join(", ") || "—"}

(Alguns são internos — \`ALTERAR\`, \`MOVER_EIXO\` — e outros são sinônimos de domínio, como \`SAPATA\`/\`BLOCO\`/\`RADIER\` para \`FUNDACAO\`.)

## 3. Famílias de referência (AutoCAD 2026 / Revit 2026) fora do catálogo

Classificação por família, para o que o catálogo **não** cobre. "Fora do escopo" = decisão de produto (o CivilConstructorAI não é um
substituto geral do AutoCAD); "Dependente de integração" = exige biblioteca/serviço externo ainda não contratado; "Ausente" = deveria
existir para o fluxo de projeto civil e não existe.

| Família | Exemplos na referência | Situação no CivilConstructorAI | Classificação |
|---|---|---|---|
| Desenho 2D | XLINE, RAY, DONUT, REVCLOUD, WIPEOUT, MLINE | XLINE/RAY/DONUT presentes; nuvem de revisão, wipeout e multilinha não existem | Ausente (REVCLOUD é útil em revisão) / Fora do escopo (MLINE — a parede já é paramétrica) |
| Edição | LENGTHEN (DY, TOTAL), PEDIT, SPLINEDIT, OVERKILL, GRIPS | ALONGAR só por incremento; sem edição de vértices de polilinha nem limpeza de duplicados | Parcial / Ausente |
| Precisão | OSNAP (todos os modos), TRACKING, FROM, CAL, UCS 3 pontos | Osnap de 9 modos, polar, orto, grade, entrada dinâmica (absoluta, relativa, polar); sem FROM e sem UCS por 3 pontos | Parcial |
| Organização | LAYER (estados, filtros), LAYISO, GROUP, XREF, PURGE | Camadas fixas por disciplina (desenho, anotação, estrutura, arquitetura, fundação, ia-rascunho); sem grupos, sem estados de camada | Parcial |
| Componentes | BLOCK, INSERT, ATTDEF, dynamic blocks; famílias do Revit | Sem biblioteca de blocos; elementos construtivos paramétricos (parede, pilar, viga, laje, porta, janela, fundação) cumprem o papel de família | Ausente (blocos) / Parcial (famílias) |
| Referências externas | XREF, ATTACH (PDF/DWG/imagem), Link Revit/IFC | Importação de IFC (RF17) cria elementos; não há vínculo vivo | Dependente de integração |
| Anotações | DIM*, MLEADER, TEXT/MTEXT, TABLE, tags e schedules do Revit | Cotas linear, alinhada, contínua, de nível; texto; etiqueta; quadros nas pranchas; sem tabela editável | Parcial |
| 3D | EXTRUDE, PRESSPULL, BOOLEAN, SECTIONPLANE; vistas 3D/cortes do Revit | Vista 3D de inspeção gerada do modelo; sem modelagem sólida livre nem corte interativo | Fora do escopo (sólidos livres) / Ausente (corte) |
| Inspeção | DIST, AREA, MEASUREGEOM, LIST, ID, interferências | Propriedades mostram medidas; auditor de regras (vãos, níveis, aberturas); quantitativos calculam áreas/volumes; sem detecção de interferência 3D | Parcial |
| Documentação | LAYOUT, VIEWPORTS, PLOT, PUBLISH; folhas do Revit | Pranchas geradas (RF14) com selo e emissão; SVG e DXF; sem PDF e sem viewport editável | Parcial |
| Automação | SCRIPT, LISP, ACTION RECORDER, Dynamo | Notação VI em lote (console), comandos por texto natural (simulação em dev), ações construtivas (RF29) | Parcial |
| Colaboração | Sheet Set, DWG compare, Worksharing/Central model, BIM Collaborate | Papéis por obra, histórico de comandos com autor, rascunhos de IA aprovados por profissional; sem comentários em elemento, sem bloqueio de elemento, sem comparar/restaurar versão | Ausente |
| Interoperabilidade | DWG, DXF, PDF, DWF, IFC, BCF, NWC | IFC4X3_ADD2 (escrita e leitura, sem materiais), DXF/SVG (escrita), CSV/XLSX (quantitativos/orçamento); sem DWG, PDF, BCF e STEP | Parcial / Dependente de integração (DWG — biblioteca ODA) |
`;
fs.writeFileSync(path.join(R, "docs/auditoria/CATALOGO_VERIFICAVEL.md"), md);
console.log(JSON.stringify({ total: comandos.length, verificado: cont("Implementado e verificado"), parcial: cont("Parcialmente implementado"), ausente: cont("Ausente") }));
console.log(linhas.filter((l) => l.estado !== "Implementado e verificado").map((l) => `${l.codigo}:${l.estado}${l.persiste ? ` j=${l.java} t=${l.ts}` : ` f=${l.front}`} testes=${l.t.length}`).join("\n"));
