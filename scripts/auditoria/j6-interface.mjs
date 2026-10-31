// AUD-CC-3D-001 — jornada pela interface (Playwright, Chromium com WebGL por software): planta com objetos,
// biblioteca, inserção e conexão feitas na tela, propriedades (lógica × física × gráfica), os dez modos do 3D,
// vistas, sol, luminárias, 1ª pessoa com colisão, isolamento por disciplina, painel de falhas → "ver no modelo" e
// recarga sem perda. Cada passo confere o efeito no servidor pela API. Depende da obra criada por j6-diagnostico
// (CCA_SESSAO aponta para o arquivo que ele grava). Prints em docs/auditoria/evidencias/telas/aud3d.
import { chromium } from "playwright";
import AxeBuilder from "@axe-core/playwright";
import fs from "node:fs";
import { BASE, espera, registrar, salvar, SENHA, totp } from "./cliente.mjs";

const ses = JSON.parse(fs.readFileSync(process.env.CCA_SESSAO, "utf8"));
const OUT = new URL("../../docs/auditoria/evidencias/telas/aud3d/", import.meta.url).pathname;
fs.mkdirSync(OUT, { recursive: true });
const O = `/obras/${ses.obra}`;
let api;
const modelo = async () => (await api.call("GET", `${O}/modelo`)).j;
const props = (e) => ({ ...e, ...(e.props ?? {}) });

const b = await chromium.launch({ executablePath: process.env.PW_CHROMIUM || undefined, args: ["--use-gl=angle", "--use-angle=swiftshader", "--enable-unsafe-swiftshader", "--ignore-gpu-blocklist"] });
const ctx = await b.newContext({ viewport: { width: 1600, height: 1000 }, locale: "pt-BR" });
const pg = await ctx.newPage();
const erros = [];
pg.on("pageerror", (e) => erros.push(e.message));
pg.on("console", (m) => { if (m.type() === "error" && !/favicon|401|Failed to load resource/.test(m.text())) erros.push(m.text()); });
const shot = async (nome, ms = 900) => { await espera(ms); await pg.screenshot({ path: `${OUT}${nome}.png` }); };
const linha = async (t) => { const l = pg.locator("#linha-comando"); await l.click(); await l.fill(t); await l.press("Enter"); await espera(500); };

// login com 2FA (o servidor recusa replay do código usado pela sessão da API: tenta a janela vizinha)
await pg.goto(`${BASE}/entrar`);
await pg.getByLabel("E-mail").fill(ses.email);
await pg.getByLabel("Senha").fill(SENHA);
await pg.getByRole("button", { name: "Entrar" }).click();
// o servidor só aceita um passo TOTP posterior ao último usado (anti-replay): espera a janela seguinte à do j6-diagnostico
await espera(31000);
for (const d of [0, 1]) {
  await pg.getByLabel("Código").fill(totp(ses.segredo, d));
  await pg.getByRole("button", { name: "Confirmar" }).click();
  await espera(1800);
  if (!/entrar/.test(pg.url())) break;
}
// confere no servidor o efeito de cada passo com a própria sessão do navegador (mesmos cookies; um segundo login
// esbarraria no anti-replay do TOTP, que só aceita passo posterior ao último usado)
api = { call: async (metodo, url, data) => { const xsrf = (await ctx.cookies()).find((c) => c.name === "XSRF-TOKEN")?.value ?? ""; const r = await ctx.request.fetch(`${BASE}/api/v1${url}`, { method: metodo, data, headers: { "X-XSRF-TOKEN": xsrf } }); const t = await r.text(); let j; try { j = JSON.parse(t); } catch { j = t; } return { s: r.status(), j }; } };

// 1. planta com objetos, conectores e percursos
await pg.goto(`${BASE}${O}/modelo`);
await pg.locator("canvas").first().waitFor({ timeout: 20000 }).catch(async (e) => { await pg.screenshot({ path: `${OUT}erro.png` }); console.error(pg.url(), (await pg.locator("body").innerText()).replace(/\s+/g, " ").slice(0, 600)); throw e; });
await espera(1500);
await linha("ZE");
await shot("01-planta-objetos-conexoes", 1200);
const m0 = await modelo();
registrar("RF01", "ui-planta", "planta abre com os objetos e conexões gravados", m0.elementos.some((e) => e.tipo === "OBJETO") && (await pg.locator("canvas").count()) > 0, { objetos: m0.elementos.filter((e) => e.tipo === "OBJETO").length, conexoes: m0.elementos.filter((e) => e.tipo === "CONEXAO").length });

// acessibilidade da tela do modelo (WCAG 2.1 A/AA pelo axe; o conteúdo do canvas é descrito por aria-label)
const axe = await new AxeBuilder({ page: pg }).withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"]).analyze();
const graves = axe.violations.filter((v) => v.impact === "serious" || v.impact === "critical");
registrar("IHC-9", "ui-axe-modelo", "tela do modelo sem violações graves ou críticas de WCAG 2.1 AA (axe)", graves.length === 0, graves.map((v) => `${v.id}: ${v.nodes.map((n) => n.target.join(" ")).join(", ")}`));

// 2. biblioteca: busca, prévia e inserção por coordenada digitada
await pg.getByRole("tab", { name: "Biblioteca" }).click();
await pg.getByLabel("Buscar na biblioteca").fill("geladeira 70");
await shot("02-biblioteca-busca-geladeira-70");
const achados = await pg.getByRole("listitem").filter({ hasText: "COZINHA." }).count();
await pg.getByRole("button", { name: "Inserir na planta" }).first().click();
await linha("4.6,3");
await linha(""); // rotação 0
await espera(2500);
const m1 = await modelo();
const nova = m1.elementos.map(props).find((e) => e.tipo === "OBJETO" && e.item === "COZINHA.GELADEIRA" && Math.abs(e.centro.x - 4.6) < 1e-6 && Math.abs(e.centro.y - 3) < 1e-6);
registrar("AUD-02", "ui-inserir", "pela biblioteca: busca \"geladeira 70\", prévia e inserção na planta gravam o objeto no servidor", achados === 1 && !!nova, { achados, id: nova?.id, versao: m1.versao });
await shot("03-geladeira-inserida");

// 3. propriedades e "Conectar E1 a…" pela interface
await pg.goto(`${BASE}${O}/modelo?sel=${nova.id}`);
await pg.getByText("Conectores").waitFor({ timeout: 20000 });
await shot("04-propriedades-geladeira-conectores");
await pg.getByRole("button", { name: "Conectar E1 a…" }).click();
await linha(ses.ids.tomLivre);
await linha(""); // circuito: Enter = o da tomada
await espera(2500);
const m2 = await modelo();
const cx = m2.elementos.map(props).find((e) => e.tipo === "CONEXAO" && e.para?.id === nova.id);
registrar("AUD-03", "ui-conectar", "\"Conectar E1 a…\" + seleção da tomada grava a conexão tomada.S1 → geladeira.E1 no circuito da tomada (C5)", cx?.de?.id === ses.ids.tomLivre && cx?.de?.conector === "S1" && cx?.para?.conector === "E1" && cx?.circuito === "C5", { de: cx?.de, para: cx?.para, circuito: cx?.circuito });
// propriedade editada no painel chega ao servidor (antes o texto VI sem objetos aninhados era preferido e a edição se perdia)
await pg.goto(`${BASE}${O}/modelo?sel=${nova.id}`);
await pg.getByText("Conectores").waitFor({ timeout: 20000 });
const rot = pg.getByLabel("Rotação (°)").first();
await rot.fill("90"); await rot.press("Enter"); await espera(2500);
const girada = props((await modelo()).elementos.find((e) => e.id === nova.id));
registrar("RF28", "ui-propriedade", "editar a rotação no painel de propriedades grava no servidor (90°)", girada.rotacao === 90, { rotacao: girada.rotacao });
await pg.goto(`${BASE}${O}/modelo?sel=${cx.id}`);
await pg.getByText("Ligação lógica").waitFor({ timeout: 20000 });
await shot("05-propriedades-conexao-logica-fisica-grafica");
const tCx = await pg.getByRole("complementary", { name: "Painéis do motor" }).innerText();
registrar("AUD-03", "ui-separacao", "propriedades da conexão separam ligação lógica, conexão física e representação gráfica", /Ligação lógica/.test(tCx) && /Conexão física/.test(tCx) && /Representação gráfica/.test(tCx) && /Cabo do próprio aparelho/.test(tCx), tCx.replace(/\s+/g, " ").slice(0, 300));

// 4. os dez modos do 3D
await pg.getByRole("button", { name: "3D" }).click();
await pg.getByLabel("Modo de exibição").waitFor({ timeout: 20000 });
await espera(4000);
// corte horizontal a 2,20 m para ver o interior (a laje da cobertura o esconde)
await pg.getByRole("checkbox", { name: "Corte horizontal" }).check();
await pg.getByLabel("Altura do corte horizontal (m)").fill("2.2");
const modos = await pg.getByLabel("Modo de exibição").locator("option").evaluateAll((os) => os.map((o) => ({ v: o.value, t: o.textContent })));
const descricoes = {};
for (const [i, mo] of modos.entries()) {
  await pg.getByLabel("Modo de exibição").selectOption(mo.v);
  await shot(`06-3d-${String(i + 1).padStart(2, "0")}-${mo.v}`, 3500);
  descricoes[mo.v] = (await pg.getByRole("group", { name: "Opções da vista 3D" }).innerText()).replace(/\s+/g, " ").slice(-220);
}
registrar("AUD-06", "ui-modos", "dez modos de exibição selecionáveis, cada um com descrição; malha, nós e pontos avisam que não são MEF nem nuvem de pontos", modos.length === 10 && /elementos finitos/.test(descricoes.malha) && /nuvem de pontos/.test(descricoes.pontos), { modos: modos.map((x) => x.t) });

// desempenho: quadros por segundo em 3 s (WebGL por software, SwiftShader, sem GPU: só referência relativa entre modos)
const fps = {};
for (const mo of ["realista-dia", "solido", "malha"]) {
  await pg.getByLabel("Modo de exibição").selectOption(mo); await espera(1500);
  fps[mo] = await pg.evaluate(() => new Promise((ok) => { let n = 0; const t0 = performance.now(); const f = () => { n++; if (performance.now() - t0 < 3000) requestAnimationFrame(f); else ok(+(n / ((performance.now() - t0) / 1000)).toFixed(1)); }; requestAnimationFrame(f); }));
}
registrar("IHC-10", "ui-fps", "quadros por segundo medidos (WebGL por software; não representa GPU real)", null, fps);

// 5. sol pela hora, luminárias à noite
await pg.getByLabel("Modo de exibição").selectOption("realista-dia");
for (const h of ["8", "12", "17"]) { await pg.getByLabel("Hora solar").fill(h); await shot(`07-sol-${h.padStart(2, "0")}h`, 3000); }
const tSol = (await pg.getByRole("group", { name: "Opções da vista 3D" }).innerText()).replace(/\s+/g, " ");
registrar("AUD-07", "ui-sol", "posição do sol muda com a hora (elevação e azimute mostrados)", /elevação -?\d/.test(tSol) && /azimute \d/.test(tSol), tSol.match(/Sol:[^·]*/)?.[0]);
await pg.getByLabel("Modo de exibição").selectOption("realista-noite");
await shot("08-noite-luminarias", 3500);
const tNoite = (await pg.getByRole("group", { name: "Opções da vista 3D" }).innerText()).replace(/\s+/g, " ");
registrar("AUD-07", "ui-luminarias", "à noite a cena conta as luminárias ligadas do modelo", /\d+ de \d+ luminária/.test(tNoite), tNoite.match(/\d+ de \d+ luminária\S*/)?.[0]);

// 6. vistas e corte
await pg.getByRole("checkbox", { name: "Corte horizontal" }).uncheck();
await pg.getByLabel("Modo de exibição").selectOption("realista-dia");
await pg.getByLabel("Hora solar").fill("10");
for (const v of ["fachada-sul", "fachada-leste", "topo"]) { await pg.getByLabel("Vista da câmera").selectOption(v); await shot(`09-vista-${v}`, 3000); }
await pg.getByLabel("Vista da câmera").selectOption("perspectiva");
await pg.getByRole("checkbox", { name: "Corte vertical" }).check();
await shot("10-corte-vertical", 3000);
await pg.getByRole("checkbox", { name: "Corte vertical" }).uncheck();
await pg.getByRole("checkbox", { name: "Corte horizontal" }).check();
await shot("11-corte-horizontal", 3000);
await pg.getByRole("checkbox", { name: "Corte horizontal" }).uncheck();

// 7. 1ª pessoa e colisão
await pg.goto(`${BASE}${O}/modelo?sel=${ses.ids.adulto}`);
await pg.getByRole("button", { name: "Ver em 1ª pessoa (3D)" }).click();
await pg.getByLabel("Modo de exibição").waitFor({ timeout: 20000 });
await shot("12-primeira-pessoa", 4000);
await pg.getByRole("img", { name: /Cena 3D/ }).focus();
let colidiu = null;
for (let i = 0; i < 30 && !colidiu; i++) { await pg.keyboard.press("w"); await espera(120); colidiu = await pg.getByRole("alert").filter({ hasText: "Colisão" }).first().textContent({ timeout: 300 }).catch(() => null); }
await shot("13-primeira-pessoa-colisao", 1500);
registrar("AUD-08", "ui-colisao", "andando em 1ª pessoa a pessoa para em parede ou objeto, com aviso", !!colidiu, colidiu);

// 7b. subir a escada em 1ª pessoa e gravar a posição alcançada
await pg.goto(`${BASE}${O}/modelo?sel=${ses.ids.subindo}`);
await pg.getByRole("button", { name: "Ver em 1ª pessoa (3D)" }).click();
await pg.getByLabel("Modo de exibição").waitFor({ timeout: 20000 });
await espera(3000);
await pg.getByRole("img", { name: /Cena 3D/ }).focus();
for (let i = 0; i < 10; i++) { await pg.keyboard.press("w"); await espera(120); }
const pisoTopo = (await pg.getByText(/Piso a \d/).textContent().catch(() => "")) ?? "";
await shot("13b-primeira-pessoa-topo-escada", 3000);
await pg.getByRole("button", { name: "Gravar posição da pessoa" }).click();
await espera(2500);
const subiu = props((await modelo()).elementos.find((e) => e.id === ses.ids.subindo));
registrar("AUD-08", "ui-escada", "em 1ª pessoa a pessoa sobe a escada até o nível de cima (3,00 m) e a posição gravada fica no servidor", /Piso a 3\.00/.test(pisoTopo) && Math.abs(subiu.elevacao - 3) < 0.002 && subiu.centro.y > 7.6, { piso: pisoTopo, elevacao: subiu.elevacao, centro: subiu.centro });

// 8. isolar disciplina
await pg.getByRole("tab", { name: "Camadas" }).click();
await pg.getByRole("button", { name: "Isolar disciplina Elétrica" }).click();
await pg.getByLabel("Vista da câmera").selectOption("perspectiva");
await pg.getByLabel("Modo de exibição").selectOption("solido");
await shot("14-isolar-eletrica", 3500);
await pg.getByRole("button", { name: "Isolar disciplina Hidrossanitária" }).click();
await shot("15-isolar-hidrossanitaria", 3500);
await pg.getByRole("button", { name: /Reexibir tudo/ }).click().catch(async () => { await linha("EU"); });

// 9. painel de falhas → ver no modelo
await pg.goto(`${BASE}${O}/falhas`);
await pg.getByRole("button", { name: "Verificar agora" }).click();
await espera(3000);
await shot("16-falhas-conflitos", 1200);
const linhaConflito = pg.getByRole("row").filter({ hasText: "impede a abertura da porta" }).first();
const temConflito = await linhaConflito.count();
await pg.getByText("Instalações prediais (proposta P-MEP-01)").scrollIntoViewIfNeeded();
await shot("17-falhas-instalacoes", 1500);
await linhaConflito.getByRole("link", { name: /Ver no modelo/ }).click();
await pg.getByLabel("Modo de exibição").waitFor({ timeout: 20000 });
await shot("18-conflito-porta-geladeira-no-modelo", 4000);
registrar("AUD-05", "ui-destacar", "do painel de falhas, \"Ver no modelo\" abre o 3D com os objetos do conflito selecionados", temConflito === 1 && /sel=/.test(pg.url()) && pg.url().includes(ses.ids.gelPorta), pg.url());

// 10. recarregar: nada se perde
const antes = await modelo();
await pg.reload();
await pg.locator("canvas").first().waitFor({ timeout: 20000 });
await espera(2000);
const depois = await modelo();
registrar("CT-10", "ui-recarregar", "recarregar a página não perde objetos nem conexões feitos pela interface", depois.versao === antes.versao && depois.elementos.length === antes.elementos.length && depois.elementos.some((e) => e.id === nova.id) && depois.elementos.some((e) => e.id === cx.id), { versao: depois.versao, elementos: depois.elementos.length });

registrar("UX", "ui-erros-js", "nenhum erro de JavaScript na jornada", erros.length === 0, erros.slice(0, 5));
salvar(new URL("../../docs/auditoria/evidencias/j6-interface.json", import.meta.url).pathname);
await b.close();
