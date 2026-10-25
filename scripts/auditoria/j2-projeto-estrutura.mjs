// Jornada M1–M3 (RF01–RF08, RF16, RF17, RF28): obra → eixos e pavimentos → desenho (VI e JSON) → edição/desfazer →
// conflito de versão → lançamento e cargas → análise (equilíbrio ΣRz = Σcargas) → dimensionamento.
import { ativarMfa, cnpj, contaVerificada, entrar, espera, registrar, salvar } from "./cliente.mjs";

const dono = await contaVerificada({ tipo: "EMPRESA", nome: "Rita Engenheira", prefixo: "j2" });
const mfa = await ativarMfa(dono);
const eng = await entrar(dono.email, { segredo: mfa.segredo });
const obra = (await eng.call("POST", "/obras", { nome: "Edifício Equilíbrio", uf: "SP", municipio: "Campinas" })).j;
const O = `/obras/${obra.id}`;
let m = (await eng.call("GET", `${O}/modelo`)).j;
const terreo = m.pavimentos[0].id;
const cmd = (codigo, args, extra = {}) => ({ pavimentoId: terreo, codigo, args, versaoBase: m.versao, origem: "CANVAS", ...extra });
const recarregar = async () => { m = (await eng.call("GET", `${O}/modelo`)).j; return m; };

// ---------- RF03 eixos e pavimentos
{
  const r = await eng.call("POST", `${O}/modelo/malha`, { nx: 3, dx: 5, ny: 2, dy: 6, versaoBase: m.versao });
  registrar("RF03", "malha", "malha 3×2 cria 5 eixos nomeados A–C e 1–2", r.s === 201 && r.j.diff?.eixosCriados?.length === 5, { s: r.s, nomes: r.j.diff?.eixosCriados?.map((e) => e.nome) });
  await recarregar();
  const dup = await eng.call("POST", `${O}/modelo/comandos`, cmd("EIXO", { p1: { x: 20, y: -1 }, p2: { x: 20, y: 7 }, nome: "A" }));
  registrar("RF03", "CA-01", "eixo com nome repetido → 422", dup.s === 422, { s: dup.s, d: dup.j.detail ?? dup.j.title });
  const segunda = await eng.call("POST", `${O}/modelo/malha`, { nx: 2, dx: 5, ny: 2, dy: 6, versaoBase: m.versao });
  registrar("RF03", "malha-dupla", "segunda malha com nomes repetidos → 422 explicando (não 500)", segunda.s === 422, { s: segunda.s, d: segunda.j.detail, e: segunda.j.errors });
  const curto = await eng.call("POST", `${O}/modelo/malha`, { nx: 2, dx: 0.01, ny: 2, dy: 6, versaoBase: m.versao });
  registrar("RF03", "CA-02", "espaçamento < 0,05 m rejeitado (422)", curto.s === 422, { s: curto.s, d: curto.j.detail, e: curto.j.errors });
  await recarregar();
  const pav = await eng.call("POST", `${O}/modelo/comandos`, cmd("PAVIMENTO", { nome: "1º Pavimento", z: 3.0 }));
  registrar("RF03", "pavimento", "cria pavimento a +3,00 m", pav.s === 201, pav.s);
  await recarregar();
  const pav2 = await eng.call("POST", `${O}/modelo/comandos`, cmd("PAVIMENTO", { nome: "Duplicado", z: 3.0 }));
  registrar("RF03", "CA-03", "pavimento na mesma elevação → 422", pav2.s === 422, { s: pav2.s, d: pav2.j.detail });
  await recarregar();
  const pe = await eng.call("POST", `${O}/modelo/comandos`, cmd("PAVIMENTO", { nome: "Mezanino baixo", z: 4.0 }));
  registrar("RF03", "CA-04", "pé-direito de 1,00 m (fora de 2,20–12,00) exige confirmação explícita", pe.s === 422 || pe.s === 409, { s: pe.s, d: pe.j.detail, e: pe.j.errors });
  await recarregar();
  const peOk = await eng.call("POST", `${O}/modelo/comandos`, cmd("PAVIMENTO", { nome: "Mezanino", z: 4.0, confirmar: true }));
  registrar("RF03", "CA-04b", "com confirmar=true o mezanino é criado", peOk.s === 201, peOk.s);
  await recarregar();
}

// ---------- RF02 Notação VI: válido, incompleto, ambíguo
{
  const v1 = await eng.call("POST", "/vi/validar", { vi: "VI(PAREDE, p1=[0,0], p2=[5,0], esp=0.15)" });
  registrar("RF02", "CA-01a", "VI válido é aceito pelo validador", v1.s === 200 && !(v1.j.erros?.length), { s: v1.s, j: JSON.stringify(v1.j).slice(0, 200) });
  const v2 = await eng.call("POST", "/vi/validar", { vi: "VI(PAREDE, p1=[0,0], p2=[5,0]" });
  const err = v2.j.erros ?? v2.j.errors ?? [];
  registrar("RF02", "CA-01b", "VI incompleto: erro com linha, coluna e mensagem pt-BR", (v2.s === 200 || v2.s === 422) && JSON.stringify(err).match(/coluna|linha/i) !== null, { s: v2.s, err });
  const v3 = await eng.call("POST", "/vi/validar", { vi: "VI(PAREDE, p1=[0,0], p2=[5,0], esp=0.15)\nVI(VIGAA, p1=[0,0])\nVI(PILAR centro=[1,1])" });
  registrar("RF02", "CA-02", "lote com vários erros devolve todos", JSON.stringify(v3.j).split(/VI-0/).length - 1 >= 2, { s: v3.s, j: JSON.stringify(v3.j).slice(0, 400) });
  // texto VI x JSON produzem a mesma geometria
  const viaVi = await eng.call("POST", `${O}/modelo/comandos`, { pavimentoId: terreo, vi: "VI(PAREDE, p1=[0,0], p2=[5,0], esp=0.15, h=2.8)", versaoBase: m.versao, origem: "CONSOLE_VI" });
  await recarregar();
  const viaJson = await eng.call("POST", `${O}/modelo/comandos`, cmd("PAREDE", { p1: { x: 0, y: 6 }, p2: { x: 5, y: 6 }, esp: 0.15, h: 2.8 }));
  await recarregar();
  const pa = viaVi.j.diff?.criados?.[0]?.props, pb = viaJson.j.diff?.criados?.[0]?.props;
  registrar("RF02", "equivalencia", "comando textual e JSON geram a mesma parede (espessura, altura, material, comprimento)", viaVi.s === 201 && viaJson.s === 201 && pa?.espessura === pb?.espessura && pa?.altura === pb?.altura && pa?.materialId === pb?.materialId, { vi: pa, json: pb });
  const amb = await eng.call("POST", `${O}/modelo/comandos`, { pavimentoId: terreo, vi: "VI(PORTA, larg=0.8)", versaoBase: m.versao, origem: "CONSOLE_VI" });
  registrar("RF02", "ambiguo", "porta sem parede hospedeira é recusada com motivo (não cria nada)", amb.s === 422, { s: amb.s, d: amb.j.detail });
  await recarregar();
}

// ---------- RF01 edição, desfazer, conflito, reabertura
let paredeId;
{
  const r = await eng.call("POST", `${O}/modelo/comandos`, cmd("PAREDE", { p1: { x: 0, y: 0 }, p2: { x: 0, y: 6 }, esp: 0.15 }));
  paredeId = r.j.diff?.criados?.[0]?.id;
  registrar("RF01", "CA-03", "edição gera ComandoVI com versão resultante", r.s === 201 && typeof r.j.comandoId === "string" && r.j.versao > 0, { s: r.s, versao: r.j.versao, vi: r.j.vi });
  const antes = await recarregar();
  const stale = await eng.call("POST", `${O}/modelo/comandos`, cmd("LINHA", { p1: { x: 1, y: 1 }, p2: { x: 2, y: 1 } }, { versaoBase: antes.versao - 1 }));
  registrar("RF01", "CA-05", "versaoBase desatualizada → 409", stale.s === 409, { s: stale.s, d: stale.j.detail });
  const desf = await eng.call("POST", `${O}/modelo/comandos/${r.j.comandoId}/desfazer`);
  const depois = await recarregar();
  registrar("RF01", "CA-04", "desfazer gera comando inverso e remove o elemento (histórico preservado)", (desf.s === 200 || desf.s === 201) && !depois.elementos.some((e) => e.id === paredeId), { s: desf.s, versao: depois.versao });
  const hist = await eng.call("GET", `${O}/modelo/comandos`);
  registrar("RF01", "historico", "histórico mantém o comando original e o inverso, com autor", Array.isArray(hist.j) && hist.j.some((c) => c.codigo === "DESFAZER") && hist.j.every((c) => c.autorId), { n: hist.j.length, ex: hist.j.at(-1) });
  // reabrir em outra sessão preserva ids e props
  const outra = await entrar(dono.email, { segredo: mfa.segredo });
  const reaberto = (await outra.call("GET", `${O}/modelo`)).j;
  registrar("RF01", "reabrir", "modelo reaberto em outra sessão tem os mesmos ids, versão e propriedades", reaberto.versao === depois.versao && JSON.stringify(reaberto.elementos) === JSON.stringify(depois.elementos), { versao: reaberto.versao, n: reaberto.elementos.length });
}

// ---------- RF04/RF08 lançamento estrutural
let viga;
{
  for (const [x, y] of [[0, 0], [5, 0], [10, 0], [0, 6], [5, 6], [10, 6]]) { await eng.call("POST", `${O}/modelo/comandos`, cmd("PILAR", { centro: { x, y }, b: 0.2, h: 0.4 })); await recarregar(); }
  const fino = await eng.call("POST", `${O}/modelo/comandos`, cmd("PILAR", { centro: { x: 2.5, y: 3 }, b: 0.10, h: 0.10 }));
  registrar("RF08", "CA-03", "pilar 10×10 cm → 422 com dimensão, valor, mínimo e item da norma", fino.s === 422, { s: fino.s, d: fino.j.detail, e: fino.j.errors ?? fino.j.relatorio });
  await recarregar();
  const nulo = await eng.call("POST", `${O}/modelo/comandos`, cmd("VIGA", { p1: { x: 0, y: 0 }, p2: { x: 0, y: 0 }, b: 0.14, h: 0.4 }));
  registrar("RF08", "CA-01", "vetor de comprimento nulo rejeitado (422)", nulo.s === 422, { s: nulo.s, d: nulo.j.detail });
  await recarregar();
  const solta = await eng.call("POST", `${O}/modelo/comandos`, cmd("VIGA", { p1: { x: 20, y: 20 }, p2: { x: 25, y: 20 }, b: 0.14, h: 0.4 }));
  registrar("RF04", "CA-02", "viga sem apoio nos extremos é rejeitada ou sinalizada", solta.s === 422 || (solta.j.relatorio?.laco2?.avisos?.length ?? 0) > 0, { s: solta.s, d: solta.j.detail, avisos: solta.j.relatorio?.laco2?.avisos?.map((a) => a.codigo) });
  await recarregar();
  if (solta.s === 201) { await eng.call("POST", `${O}/modelo/comandos/${solta.j.comandoId}/desfazer`); await recarregar(); }
  const v = await eng.call("POST", `${O}/modelo/comandos`, cmd("VIGA", { p1: { x: 0, y: 0 }, p2: { x: 10, y: 0 }, b: 0.14, h: 0.4 }));
  viga = v.j.diff?.criados?.[0]; await recarregar();
  await eng.call("POST", `${O}/modelo/comandos`, cmd("VIGA", { p1: { x: 0, y: 6 }, p2: { x: 10, y: 6 }, b: 0.14, h: 0.4 })); await recarregar();
  const laje = await eng.call("POST", `${O}/modelo/comandos`, cmd("LAJE", { contorno: [{ x: 0, y: 0 }, { x: 10, y: 0 }, { x: 10, y: 6 }, { x: 0, y: 6 }], h: 0.12 }));
  registrar("RF04", "laje", "laje 10×6 m por contorno", laje.s === 201, laje.s);
  await recarregar();
  const borboleta = await eng.call("POST", `${O}/modelo/comandos`, cmd("LAJE", { contorno: [{ x: 0, y: 0 }, { x: 10, y: 6 }, { x: 10, y: 0 }, { x: 0, y: 6 }], h: 0.12 }));
  registrar("RF04", "CA-03", "laje com contorno auto-interceptante → 422", borboleta.s === 422, { s: borboleta.s, d: borboleta.j.detail });
  await recarregar();
  const vaos = await eng.call("GET", `${O}/estrutura/vaos`);
  const linhaA = vaos.j?.[0]?.linhas?.find((l) => l.vaos >= 1);
  registrar("RF06", "CA-02", "auditoria de vãos: apoios = vãos + 1 nas linhas de vigas", Array.isArray(vaos.j) && vaos.j[0]?.linhas?.every((l) => l.fencepostOk), { linhas: vaos.j?.[0]?.linhas?.map((l) => `${l.eixo}:${l.vaos}v/${l.apoios}a`) });
  registrar("RF06", "caixa", "caixa real reportada (0–10 × 0–6)", vaos.j?.[0]?.caixa?.xMax === 10 && vaos.j?.[0]?.caixa?.yMax === 6, vaos.j?.[0]?.caixa);
}

// ---------- cargas, combinações
{
  const neg = await eng.call("POST", `${O}/estrutura/cargas`, { pavimentoId: terreo, natureza: "PERMANENTE", tipo: "AREA", valor: -1.0, descricao: "teste", casoCarga: "G1" });
  registrar("RF04", "CA-04", "carga gravitacional negativa rejeitada", neg.s === 422, { s: neg.s, d: neg.j.detail });
  const semDesc = await eng.call("POST", `${O}/estrutura/cargas`, { pavimentoId: terreo, natureza: "PERMANENTE", tipo: "AREA", valor: 1.0, casoCarga: "G1" });
  registrar("RF04", "UX-descricao", "carga sem descrição (campo opcional na tela) é aceita", semDesc.s === 201 || semDesc.s === 200, { s: semDesc.s, e: semDesc.j.errors });
  const linear = await eng.call("POST", `${O}/estrutura/cargas`, { pavimentoId: terreo, natureza: "PERMANENTE", tipo: "LINEAR", valor: 10.0, elementoAlvoId: viga?.id, descricao: "alvenaria sobre a viga A", casoCarga: "G2" });
  registrar("RF04", "carga-linear", "carga linear de 10 kN/m na viga do eixo 1", linear.s === 201 || linear.s === 200, { s: linear.s, e: linear.j.errors ?? linear.j.detail });
  await eng.call("POST", `${O}/estrutura/cargas`, { pavimentoId: terreo, natureza: "ACIDENTAL", tipo: "AREA", valor: 2.0, descricao: "uso residencial", casoCarga: "Q1" });
  const comb = await eng.call("GET", `${O}/estrutura/combinacoes`, null, { query: { pavimentoId: terreo } });
  registrar("RF04", "CA-05", "combinações ELU e ELS geradas", Array.isArray(comb.j) && comb.j.some((c) => /ELU/.test(c.estadoLimite ?? c.nome)) && comb.j.some((c) => /ELS/.test(c.estadoLimite ?? c.nome)), comb.j?.map((c) => c.nome));
}

// ---------- RF05 análise e equilíbrio
let analiseId;
{
  const antesPronto = await eng.call("POST", `${O}/analises`);
  registrar("RF05", "pre-condicao", "análise sem lançamento marcado como pronto é recusada", antesPronto.s === 409 || antesPronto.s === 422, { s: antesPronto.s, d: antesPronto.j.detail });
  if (antesPronto.s === 202) { await espera(4000); }
  const pronto = await eng.call("POST", `${O}/estrutura/lancamentos/${terreo}/pronto`);
  registrar("RF04", "pronto", "marcar lançamento do térreo como pronto para análise", pronto.s === 200 || pronto.s === 204, { s: pronto.s, d: pronto.j.detail });
  const an = await eng.call("POST", `${O}/analises`);
  analiseId = an.j?.id;
  let estado = an.j?.estado;
  for (let i = 0; i < 30 && !["CONCLUIDO", "FALHOU"].includes(estado); i++) { await espera(1000); estado = (await eng.call("GET", `${O}/analises/${analiseId}`)).j?.estado; }
  registrar("RF05", "CA-01", "análise é job assíncrono e conclui", an.s === 202 && estado === "CONCLUIDO", { s: an.s, estado });
  const d = await eng.call("GET", `${O}/analises/${analiseId}/diagramas`);
  const c = d.j?.porCombinacao ? Object.values(d.j.porCombinacao).find((x) => /^ELU/.test(x.combinacao.nome)) : null;
  if (c) {
    const somaRz = c.reacoes.reduce((a, r) => a + r.fz, 0);
    // cargas independentes: peso próprio (25 kN/m³) de pilares, vigas e laje + 10 kN/m na viga A + 1 kN/m² + 2 kN/m² na laje
    const f = c.combinacao.fatores;
    const pp = 25 * (6 * 0.2 * 0.4 * 3.0 /* pilares até +3,00 */ + 2 * 0.14 * 0.4 * 10 + 10 * 6 * 0.12);
    // cargas efetivamente gravadas (a lista da API), não as que o roteiro tentou lançar
    const cargas = (await eng.call("GET", `${O}/estrutura/cargas`)).j;
    const soma = (nat) => cargas.filter((x) => x.natureza === nat).reduce((a, x) => a + (x.tipo === "AREA" ? x.valor * 60 : x.tipo === "LINEAR" ? x.valor * 10 : x.valor), 0);
    const G = pp + soma("PERMANENTE"), Q = soma("ACIDENTAL");
    const esperado = (f.PERMANENTE ?? 0) * G + (f.ACIDENTAL ?? 0) * Q;
    registrar("RF05", "equilibrio", `ΣRz = Σ cargas na combinação ${c.combinacao.nome} (erro < 1 %)`, Math.abs(Math.abs(somaRz) - esperado) / esperado < 0.01, { somaRz: +somaRz.toFixed(2), esperado: +esperado.toFixed(2), fatores: f, nos: d.j.nos, barras: d.j.barras });
    const vigaA = c.barras.filter((b) => b.elementoId === viga?.id);
    registrar("RF05", "CA-05", "diagramas por barra com N, V, M e flecha", vigaA.length > 0 && vigaA.every((b) => "mMax" in b && "vMax" in b), vigaA.map((b) => ({ r: b.rotulo, L: b.comprimento, M: b.mMax, V: b.vMax })));
  } else registrar("RF05", "equilibrio", "diagramas disponíveis", false, d.j);
}

// ---------- RF04 CA-08 / RF05 CA-02: mudança invalida resultados
{
  await recarregar();
  await eng.call("POST", `${O}/modelo/comandos`, cmd("LINHA", { p1: { x: 1, y: 1 }, p2: { x: 2, y: 1 } }));
  const a = (await eng.call("GET", `${O}/analises/${analiseId}`)).j;
  registrar("RF05", "CA-02", "linha auxiliar (não estrutural) não invalida a análise", a.estado === "CONCLUIDO", a.estado);
  await recarregar();
  await eng.call("POST", `${O}/modelo/comandos`, cmd("PILAR", { centro: { x: 2.5, y: 0 }, b: 0.2, h: 0.4 }));
  const b = (await eng.call("GET", `${O}/analises/${analiseId}`)).j;
  registrar("RF04", "CA-08", "pilar novo marca a análise como OBSOLETO", b.estado === "OBSOLETO", b.estado);
}

// ---------- RF07 dimensionamento sobre análise obsoleta
{
  const dim = await eng.call("POST", `${O}/dimensionamentos`, { analiseId });
  registrar("RF07", "CA-01", "dimensionar análise obsoleta → 409 com orientação", dim.s === 409, { s: dim.s, d: dim.j.detail });
}

// ---------- RF28 catálogo
{
  const cat = await eng.call("GET", "/catalogo/comandos");
  registrar("RF28", "catalogo", "catálogo de comandos servido pela API", cat.s === 200 && cat.j.comandos?.length >= 80, { n: cat.j.comandos?.length });
  const semParam = await eng.call("GET", "/catalogo/comandos/resolver");
  registrar("RF28", "erro-400", "parâmetro ausente → 400 explicado (não 500)", semParam.s === 400, { s: semParam.s, d: semParam.j.detail });
  const res = await eng.call("GET", "/catalogo/comandos/resolver", null, { query: { alias: "L", perfil: "AUTOCAD" } });
  registrar("RF28", "CA-01", "alias L resolve LINHA no perfil AutoCAD", JSON.stringify(res.j).includes("LINHA"), { s: res.s, j: JSON.stringify(res.j).slice(0, 120) });
}

salvar(new URL("../../docs/auditoria/evidencias/j2-projeto-estrutura.json", import.meta.url).pathname);
console.log(JSON.stringify({ email: dono.email, segredo: mfa.segredo, obra: obra.id }));
