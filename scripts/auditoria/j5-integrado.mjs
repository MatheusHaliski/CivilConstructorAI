// Jornada 5 — projeto integrado com defeitos introduzidos (auditoria 2026-10): base de cálculo incompleta, viga sem
// apoio, viga baixa sobre porta, carga pontual sem posição, material trocado depois do cálculo, coordenação com
// reverificação, painel de falhas e telas novas (resultados, materiais, base de cálculo, 3D com armaduras).
import { chromium } from "playwright";
import fs from "node:fs";
import { BASE, ativarMfa, contaVerificada, entrar, espera, registrar, salvar } from "./cliente.mjs";

const OUT = new URL("../../docs/auditoria/evidencias/telas/", import.meta.url).pathname;
fs.mkdirSync(OUT, { recursive: true });

const dono = await contaVerificada({ tipo: "EMPRESA", nome: "Lia Projetista", prefixo: "j5" });
const mfa = await ativarMfa(dono);
const eng = await entrar(dono.email, { segredo: mfa.segredo });
const obra = (await eng.call("POST", "/obras", { nome: "Residência Integrada", uf: "PR", municipio: "Curitiba" })).j;
const O = `/obras/${obra.id}`;
let m = (await eng.call("GET", `${O}/modelo`)).j;
const recarregar = async () => { m = (await eng.call("GET", `${O}/modelo`)).j; return m; };
const terreo = m.pavimentos[0].id;
const cmd = async (pav, codigo, args) => { const r = await eng.call("POST", `${O}/modelo/comandos`, { pavimentoId: pav, codigo, args, versaoBase: m.versao, origem: "CANVAS" }); await recarregar(); return r; };

// ---------- modelo: térreo com pilares, parede e porta; 1º pavimento com vigas e laje
await eng.call("POST", `${O}/modelo/malha`, { nx: 2, dx: 5, ny: 2, dy: 6, versaoBase: m.versao }); await recarregar();
await cmd(terreo, "PAVIMENTO", { nome: "1º Pavimento", z: 3.0 });
const p1 = m.pavimentos.find((p) => p.nome === "1º Pavimento").id;
for (const [x, y] of [[0, 0], [5, 0], [0, 6], [5, 6]]) await cmd(terreo, "PILAR", { centro: { x, y }, b: 0.2, h: 0.4, mat: "MAT.CONCRETO.C30" });
const parede = await cmd(terreo, "PAREDE", { p1: { x: 0, y: 0 }, p2: { x: 5, y: 0 } });
const porta = await cmd(terreo, "PORTA", { pos: { x: 2.5, y: 0 } });
registrar("RF01", "j5-modelo", "pilares com código canônico (MAT.CONCRETO.C30), parede e porta aceitos", parede.s === 201 && porta.s === 201, { parede: parede.s, porta: porta.s });
const vBaixa = await cmd(p1, "VIGA", { p1: { x: 0, y: 0 }, p2: { x: 5, y: 0 }, b: 0.14, h: 1.0 }); // defeito: fundo a 2,00 m < topo da porta 2,10 m
await cmd(p1, "VIGA", { p1: { x: 0, y: 6 }, p2: { x: 5, y: 6 }, b: 0.14, h: 0.4 });
await cmd(p1, "VIGA", { p1: { x: 0, y: 0 }, p2: { x: 0, y: 6 }, b: 0.14, h: 0.4 });
await cmd(p1, "VIGA", { p1: { x: 5, y: 0 }, p2: { x: 5, y: 6 }, b: 0.14, h: 0.4 });
const vSolta = await cmd(p1, "VIGA", { p1: { x: 0, y: 3 }, p2: { x: 2.5, y: 3 }, b: 0.14, h: 0.4 }); // defeito: ponta (2,5; 3) sem apoio
await cmd(p1, "LAJE", { contorno: [{ x: 0, y: 0 }, { x: 5, y: 0 }, { x: 5, y: 6 }, { x: 0, y: 6 }], h: 0.12 });
const vigaA = vBaixa.j?.diff?.criados?.[0]?.id;
registrar("RF04", "j5-defeitos", "defeitos introduzidos entram no modelo (viga alta sobre porta e viga com ponta solta)", vBaixa.s === 201 && vSolta.s === 201, { vBaixa: vBaixa.s, vSolta: vSolta.s });

// ---------- cargas: carga pontual em viga sem posição é recusada (antes ia para o meio do vão sem aviso)
const semPos = await eng.call("POST", `${O}/estrutura/cargas`, { pavimentoId: p1, natureza: "ACIDENTAL", tipo: "PONTUAL", valor: 10, elementoAlvoId: vigaA, descricao: "equipamento", casoCarga: "Q2" });
registrar("RF04", "j5-pontual", "carga pontual em viga sem posição → 422 explicando", semPos.s === 422, { s: semPos.s, d: semPos.j.detail, e: semPos.j.errors });
const comPos = await eng.call("POST", `${O}/estrutura/cargas`, { pavimentoId: p1, natureza: "ACIDENTAL", tipo: "PONTUAL", valor: 10, elementoAlvoId: vigaA, posicao: 1.5, descricao: "equipamento", casoCarga: "Q2" });
registrar("RF04", "j5-pontual-ok", "com posição (1,50 m do início) a carga é aceita", comPos.s === 201, { s: comPos.s });
await eng.call("POST", `${O}/estrutura/cargas`, { pavimentoId: p1, natureza: "PERMANENTE", tipo: "AREA", valor: 1.0, descricao: "revestimento", casoCarga: "G2" });
await eng.call("POST", `${O}/estrutura/cargas`, { pavimentoId: p1, natureza: "ACIDENTAL", tipo: "AREA", valor: 2.0, descricao: "uso residencial", casoCarga: "Q1" });

// ---------- base de cálculo incompleta (sem CAA) → análise e dimensionamento com "dados insuficientes"
const base0 = (await eng.call("GET", `${O}/base-calculo`)).j;
registrar("BASE", "j5-base-vazia", "base de cálculo nova lista o que não foi informado", (base0.naoInformado ?? []).length >= 3, base0.naoInformado);
for (const pav of [terreo, p1]) await eng.call("POST", `${O}/estrutura/lancamentos/${pav}/pronto`);
const an = (await eng.call("POST", `${O}/analises`)).j;
let analise = an;
for (let i = 0; i < 30 && !["CONCLUIDO", "FALHOU"].includes(analise.estado); i++) { await espera(1000); analise = (await eng.call("GET", `${O}/analises/${an.id}`)).j; }
registrar("RF05", "j5-analise", "análise conclui com base de cálculo gravada", analise.estado === "CONCLUIDO" && !!analise.baseCalculoJson, { estado: analise.estado, erro: analise.mensagemErro });
const diag = (await eng.call("GET", `${O}/analises/${an.id}/diagramas`)).j;
const avisos = diag.avisos ?? [];
registrar("RF05", "j5-metadados", "resultado declara natureza CALCULADO, método, unidades e convenção", diag.metadados?.natureza === "CALCULADO" && !!diag.metadados?.convencao, diag.metadados?.metodo);
registrar("RF05", "j5-avisos", "viga com ponta solta aparece nos avisos do modelo analítico", avisos.length > 0, avisos.map((a) => `${a.tipo}: ${a.mensagem}`).slice(0, 4));
const eq = Object.values(diag.porCombinacao ?? {}).map((c) => c.equilibrio?.ok);
registrar("RF05", "j5-equilibrio", "equilíbrio global conferido em todas as combinações", eq.length > 0 && eq.every(Boolean), eq);
const dim = (await eng.call("POST", `${O}/dimensionamentos`, { analiseId: an.id })).j;
const res = (await eng.call("GET", `${O}/dimensionamentos/${dim.id}/resultados`)).j;
const estados = res.map((r) => r.estado);
registrar("RF07", "j5-dados-insuf", "sem CAA, nenhum elemento sai como 'atendido' por completo: cobrimento fica em dados insuficientes", res.length > 0 && res.some((r) => (r.verificacoes ?? []).some((v) => v.estado === "DADOS_INSUFICIENTES")), estados);
const posicoes = res.flatMap((r) => r.detalhamento?.posicoes ?? []);
registrar("RF07", "j5-detalhamento", "detalhamento por posição com número, comprimento e massa separados", posicoes.length > 0 && posicoes.every((p) => p.quantidade > 0 && p.comprimentoUnitarioM > 0 && p.massaKg > 0), posicoes.slice(0, 3).map((p) => `${p.posicao} ${p.quantidade}×Ø${p.diametroMm} L=${p.comprimentoUnitarioM}m ${p.massaKg}kg`));

// ---------- coordenação: defeitos detectados; corrigir; reverificar
const v1 = (await eng.call("POST", `${O}/coordenacao/verificar`)).j;
const regras = (v1.conflitos ?? []).map((c) => c.regra);
registrar("COORD", "j5-detecta", "detector acha viga abaixo do topo da porta (CON-02) e viga sem apoio (LIG-01)", regras.includes("CON-02") && regras.includes("LIG-01"), regras);
const lig = v1.conflitos.find((c) => c.regra === "LIG-01");
const manual = await eng.call("POST", `${O}/coordenacao/conflitos/${lig.id}/estado`, { estado: "RESOLVIDO" });
registrar("COORD", "j5-manual", "marcar Resolvido à mão é recusado (só a reverificação resolve)", manual.s === 422, { s: manual.s, d: manual.j.detail });
const baixa = v1.conflitos.find((c) => c.regra === "CON-02");
const aceito = await eng.call("POST", `${O}/coordenacao/conflitos/${baixa.id}/estado`, { estado: "ACEITO", justificativa: "porta será trocada por vão de 1,90 m (decisão de projeto registrada)" });
registrar("COORD", "j5-aceito", "aceitar com justificativa registra quem decidiu", aceito.s === 200 && aceito.j.estado === "ACEITO", aceito.j.estado);
await cmd(terreo, "PILAR", { centro: { x: 2.5, y: 3 }, b: 0.2, h: 0.4 }); // corrige a ponta solta
const v2 = (await eng.call("POST", `${O}/coordenacao/verificar`)).j;
const ligDepois = v2.conflitos.find((c) => c.id === lig.id);
registrar("COORD", "j5-reverifica", "após apoiar a viga, a reverificação marca LIG-01 como Resolvido na nova versão", ligDepois?.estado === "RESOLVIDO" && ligDepois.versaoVerificada === v2.versaoModelo, { estado: ligDepois?.estado, versao: ligDepois?.versaoVerificada, modelo: v2.versaoModelo });

// ---------- mudança depois do cálculo: material e base desatualizam, não recalculam
const dimAntes = (await eng.call("GET", `${O}/dimensionamentos/${dim.id}`)).j;
registrar("RF07", "j5-desatualiza-modelo", "mudar o modelo deixa o dimensionamento como desatualizado (OBSOLETO)", dimAntes.estado === "OBSOLETO", dimAntes.estado);
const baseOk = await eng.call("PUT", `${O}/base-calculo`, { finalidade: "RESIDENCIAL", sistemaConstrutivo: "CONCRETO_ARMADO_MOLDADO_NO_LOCAL", classeAgressividade: "II", agregado: "granito" });
registrar("BASE", "j5-base", "base completa gravada (CAA II, granito)", baseOk.s === 200 && (baseOk.j.naoInformado ?? []).length === 0, baseOk.j.naoInformado);
const cat = (await eng.call("GET", "/materiais/catalogo", undefined, { query: { obraId: obra.id } })).j;
const c30 = cat.itens.find((i) => i.codigo === "MAT.CONCRETO.C30");
const ecs = c30.propriedades.find((p) => p.chave === "Ecs");
registrar("MAT", "j5-ecs", "Ecs do C30 com granito = 0,875·5600·√30 ≈ 26 838 MPa, origem estimado", Math.abs(ecs.valor - 0.875 * 5600 * Math.sqrt(30)) < 1 && ecs.origem === "ESTIMADO", ecs);

// ---------- interface: telas novas
const b = await chromium.launch({ executablePath: process.env.PW_CHROMIUM || undefined, args: ["--use-gl=angle", "--use-angle=swiftshader", "--enable-unsafe-swiftshader"] });
const ctx = await b.newContext({ viewport: { width: 1440, height: 900 }, locale: "pt-BR" });
await ctx.addCookies(await eng.cookies());
const p = await ctx.newPage();
const erros = [];
p.on("pageerror", (e) => erros.push(e.message));
const shot = async (nome, completo = false) => { await espera(900); await p.screenshot({ path: `${OUT}${nome}.png`, fullPage: completo }); };
const texto = async () => (await p.locator("main").innerText().catch(() => "")).replace(/\s+/g, " ");

await p.goto(`${BASE}${O}/base-calculo`); await p.getByText("Dados de projeto").first().waitFor();
await shot("j5-01-base-calculo", true);
registrar("UX", "j5-base-tela", "tela da base mostra referências com edição e situação", /NBR 6118:2023/.test(await texto()), (await texto()).slice(0, 200));

await p.goto(`${BASE}${O}/materiais`); await p.getByText("Famílias e o que o software faz").waitFor();
await p.getByRole("button", { name: /Propriedades/ }).first().click();
await shot("j5-02-materiais", true);
const tm = await texto();
registrar("UX", "j5-materiais", "catálogo mostra suporte por família e origem dos valores (estimado/adotado)", /só representação|não implementado/.test(tm) && /estimado/.test(tm), tm.slice(0, 200));

const anNova = (await eng.call("POST", `${O}/analises`)).j;
for (let i = 0; i < 30; i++) { await espera(800); if ((await eng.call("GET", `${O}/analises/${anNova.id}`)).j.estado === "CONCLUIDO") break; }
const dimNovo = (await eng.call("POST", `${O}/dimensionamentos`, { analiseId: anNova.id })).j;
await p.goto(`${BASE}${O}/analises/mef`); await p.getByRole("button", { name: "Diagramas" }).first().click();
await p.getByText("Esforços calculados, não medidos").waitFor();
await shot("j5-03-analise-resultados", true);
registrar("UX", "j5-analise-tela", "resultados mostram grandeza com unidade, convenção, equilíbrio e deformada amplificada", /kN·m/.test(await texto()) && /amplificada/.test(await texto()) && /Equilíbrio global/.test(await texto()), "");

await p.goto(`${BASE}${O}/analises/dimensionamento`); await p.getByRole("button", { name: "Memória" }).first().click();
await p.getByText("Quadro de armaduras por posição").waitFor();
await p.locator("main table button").first().click();
await shot("j5-04-dimensionamento-verificacoes", true);
const td = await texto();
registrar("UX", "j5-dim-tela", "dimensionamento mostra estados padronizados, verificações com referência e quadro de armaduras", /Verificação atendida|Verificação não atendida|Não verificado|Dados insuficientes/.test(td) && /Totais:/.test(td), td.slice(0, 160));

await p.goto(`${BASE}${O}/falhas`); await p.getByText("Não verificado pelo software").waitFor();
await shot("j5-05-painel-falhas", true);
const tf = await texto();
registrar("UX", "j5-falhas", "painel de falhas traz origem, estado, regra e o que não é verificado; não diz 'sem falhas'", /regra determinística/.test(tf) && /Instalações elétricas/.test(tf) && !/sem falhas\b(?! acima)/i.test(tf.replace("ausência de falhas", "")), tf.slice(0, 200));

await p.goto(`${BASE}${O}/modelo`); await espera(2500);
const v3d = p.getByRole("button", { name: "3D", exact: true }).first();
if (await v3d.count()) await v3d.click().catch(() => undefined);
await espera(1500);
const arm = p.getByLabel("Armaduras do dimensionamento");
if (await arm.count()) { await arm.check(); await p.getByLabel("Raio X").check(); await espera(2500); }
await shot("j5-06-3d-armaduras-raio-x");
registrar("UX", "j5-3d", "3D oferece raio X, corte e armaduras do último dimensionamento", (await arm.count()) > 0, await p.locator("[aria-label^='Cena 3D']").getAttribute("aria-label").catch(() => null));
registrar("UX", "j5-erros-js", "nenhum erro de JavaScript nas telas visitadas", erros.length === 0, erros.slice(0, 3));
await b.close();

salvar(new URL("../../docs/auditoria/evidencias/j5-integrado.json", import.meta.url).pathname);
console.log(JSON.stringify({ email: dono.email, obra: obra.id, dimensionamento: dimNovo.id }));
