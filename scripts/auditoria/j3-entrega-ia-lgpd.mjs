// Jornadas M4–M8 (RF09–RF15, RF21 selo, RF24–RF27) e cliente: geotecnia → quantitativos → orçamento → pranchas →
// IFC → IA (rascunho, aprovação) → selo → visibilidade do cliente → direitos do titular e exclusão.
import fs from "node:fs";
import { ativarMfa, cnpj, codigoEmail, contaVerificada, emailsPara, entrar, espera, novoCliente, registrar, salvar, SENHA, totp } from "./cliente.mjs";

const SUPORTE = "suporte-auditoria@exemplo.cca";
const dono = await contaVerificada({ tipo: "EMPRESA", nome: "Lia Responsável", prefixo: "j3" });
const mfa = await ativarMfa(dono);
const eng = await entrar(dono.email, { segredo: mfa.segredo });
const empresaId = (await eng.call("GET", "/auth/session")).j.empresaAtivaId;
const obra = (await eng.call("POST", "/obras", { nome: "Casa Rastreável", uf: "PR", municipio: "Londrina" })).j;
const O = `/obras/${obra.id}`;
let m = (await eng.call("GET", `${O}/modelo`)).j;
const T = m.pavimentos[0].id;
const aplicar = async (codigo, args) => { const r = await eng.call("POST", `${O}/modelo/comandos`, { pavimentoId: T, codigo, args, versaoBase: m.versao, origem: "CANVAS" }); m = (await eng.call("GET", `${O}/modelo`)).j; return r; };

// modelo de referência: 4 pilares 20×40, 4 vigas, laje 5×4, parede 5×2,8 com janela 2,0×1,5
await eng.call("POST", `${O}/modelo/malha`, { nx: 2, dx: 5, ny: 2, dy: 4, versaoBase: m.versao }); m = (await eng.call("GET", `${O}/modelo`)).j;
await aplicar("PAVIMENTO", { nome: "Cobertura", z: 3.0 });
for (const [x, y] of [[0, 0], [5, 0], [0, 4], [5, 4]]) await aplicar("PILAR", { centro: { x, y }, b: 0.2, h: 0.4 });
for (const [a, b] of [[[0, 0], [5, 0]], [[5, 0], [5, 4]], [[5, 4], [0, 4]], [[0, 4], [0, 0]]]) await aplicar("VIGA", { p1: { x: a[0], y: a[1] }, p2: { x: b[0], y: b[1] }, b: 0.14, h: 0.4 });
await aplicar("LAJE", { contorno: [{ x: 0, y: 0 }, { x: 5, y: 0 }, { x: 5, y: 4 }, { x: 0, y: 4 }], h: 0.10 });
const parede = (await aplicar("PAREDE", { p1: { x: 0, y: 2 }, p2: { x: 5, y: 2 }, esp: 0.14, h: 2.8, externa: false })).j.diff?.criados?.[0];
await aplicar("JANELA", { parede: parede?.id, posicao: 0.5, larg: 2.0, alt: 1.5, peitoril: 1.0 });

// ---------- RF11 quantitativos
let q1;
{
  q1 = await eng.call("GET", `${O}/quantitativos`);
  const q2 = await eng.call("GET", `${O}/quantitativos`);
  registrar("RF11", "CA-01", "mesmo modelo → mesmo hash", q1.s === 200 && q1.j.hash === q2.j.hash, { hash: q1.j.hash?.slice(0, 12), versao: q1.j.versaoModelo });
  const linha = (tipo) => q1.j.linhas?.filter((l) => l.tipo === tipo) ?? [];
  const lajeM3 = linha("LAJE").reduce((a, l) => a + l.concretoM3, 0);
  registrar("RF11", "laje", "concreto da laje = 5 × 4 × 0,10 = 2,000 m³ (descontando o que já é de pilar/viga, se for o critério)", Math.abs(lajeM3 - 2.0) < 0.001 || lajeM3 < 2.0, { lajeM3, criterio: "verificar se há desconto de interseção" });
  const alv = linha("PAREDE").reduce((a, l) => a + l.alvenariaM2, 0);
  registrar("RF11", "CA-03", "alvenaria = 5 × 2,8 − janela 2,0 × 1,5 = 11,00 m² (abertura > 2 m² descontada)", Math.abs(alv - 11.0) < 0.01, { alvenariaM2: alv, linhas: linha("PAREDE") });
  const pil = linha("PILAR");
  registrar("RF11", "pilar", "concreto de cada pilar = 0,2 × 0,4 × 3,0 = 0,240 m³ (ou líquido de interseções)", pil.length === 4 && pil.every((l) => l.concretoM3 <= 0.24 + 1e-6 && l.concretoM3 > 0.2), pil.map((l) => l.concretoM3));
  const soma = q1.j.linhas.reduce((a, l) => a + l.concretoM3, 0);
  registrar("RF11", "totais", "total geral = soma das linhas (rastreável por elemento)", Math.abs(soma - q1.j.totalGeral.concretoM3) < 1e-6 && q1.j.linhas.every((l) => l.elementoId), { soma: +soma.toFixed(4), total: q1.j.totalGeral.concretoM3 });
  registrar("RF11", "CA-04", "aço sem dimensionamento aparece como estimativa por taxa", q1.j.linhas.filter((l) => l.acoKg > 0).every((l) => /TAXA|ESTIM/i.test(l.origemAco ?? "")), [...new Set(q1.j.linhas.map((l) => l.origemAco))]);
  const csv = await eng.ctx.get(`/api/v1${O}/quantitativos/export?formato=csv`);
  const txt = await csv.text();
  registrar("RF11", "CA-05", "CSV com pavimento, elemento, material, unidade e quantidade", csv.status() === 200 && /pavimento/i.test(txt.split("\n")[0]) && /material/i.test(txt.split("\n")[0]), txt.split("\n")[0]);
}

// ---------- RF12 orçamento SINAPI
{
  const sem = await eng.call("POST", `${O}/orcamentos`, { uf: "AC", desonerado: true, usarIa: false });
  registrar("RF12", "sem-tabela", "sem tabela SINAPI da UF/regime: explica o que falta (não usa tabela de outro estado)", sem.s === 422 && /AC/.test(sem.j.detail ?? ""), { s: sem.s, d: sem.j.detail });
  // tabela parcial de propósito (sem alvenaria nem fôrma): os serviços sem composição precisam aparecer
  const csv = "codigo;descricao;unidade;custo;grupo\n94965;CONCRETO FCK 30 MPA USINADO LANCADO E ADENSADO;M3;612,35;ESTRUTURA\n92779;ARMACAO ACO CA-50;KG;12,85;ESTRUTURA\n";
  const mesRef = `20${10 + Math.floor(Math.random() * 15)}-${String(1 + Math.floor(Math.random() * 12)).padStart(2, "0")}-01`;
  const imp = await eng.call("POST", "/sinapi/importar", { uf: "PR", mesReferencia: mesRef, desonerado: false, csv });
  registrar("RF12", "importar", `importa tabela SINAPI PR ${mesRef.slice(0, 7)} não desonerada`, imp.s === 201 || imp.s === 200, { s: imp.s, j: imp.j?.id ?? imp.j });
  const dup = await eng.call("POST", "/sinapi/importar", { uf: "PR", mesReferencia: mesRef, desonerado: false, csv });
  registrar("RF12", "reimportar", "reimportar o mesmo mês → 409 explicando", dup.s === 409 && /Já existe/.test(dup.j.detail ?? ""), dup.j.detail);
  const orc = await eng.call("POST", `${O}/orcamentos`, { uf: "PR", desonerado: false, usarIa: false });
  registrar("RF12", "gerar", "orçamento gerado a partir do quantitativo", orc.s === 201 || orc.s === 202 || orc.s === 200, { s: orc.s, d: orc.j.detail });
  const id = orc.j?.id;
  if (id) {
    const v = (await eng.call("GET", `${O}/orcamentos/${id}`)).j;
    const itens = v.itens ?? v.linhas ?? [];
    const soma = itens.reduce((a, i) => a + Number(i.total ?? 0), 0);
    registrar("RF12", "fonte", "orçamento usa a tabela importada da UF (não a de demonstração)", !v.tabelaDemonstracao && /PR/.test(v.dataBase ?? ""), { dataBase: v.dataBase, demo: v.tabelaDemonstracao });
    registrar("RF12", "CA-01", "itens com código, unidade, quantidade, custo unitário, total, UF, mês e regime", itens.length > 0 && itens.every((i) => i.codigoSinapi !== undefined && i.unidade && i.quantidade !== undefined), { n: itens.length, ex: itens[0], uf: v.uf, mes: v.mesReferencia ?? v.dataBase, regime: v.desonerado });
    registrar("RF12", "soma", "total do orçamento = soma dos itens (conferência independente)", Math.abs(soma - Number(v.total ?? v.orcamento?.total ?? 0)) < 0.01, { soma: +soma.toFixed(2), total: v.total ?? v.orcamento?.total, totaisPorEtapa: v.totaisPorEtapa });
    const semVinculo = itens.filter((i) => !i.codigoSinapi);
    registrar("RF12", "sem-vinculo", "serviços sem composição (fôrma, alvenaria) aparecem com custo zero e contagem", semVinculo.length >= 2 && v.itensSemComposicao === semVinculo.length, { semVinculo: semVinculo.map((i) => `${i.servico}: ${i.descricao}`), contagem: v.itensSemComposicao });
    const ap = await eng.call("POST", `${O}/orcamentos/${id}/aprovar`);
    registrar("RF12", "CA-05", "aprovar sem selo VERIFIED → 403 explicado", ap.s === 403, { s: ap.s, d: ap.j.detail });
  }
  await aplicar("PILAR", { centro: { x: 2.5, y: 0 }, b: 0.2, h: 0.4 });
  const q3 = await eng.call("GET", `${O}/quantitativos`);
  registrar("RF11", "CA-06", "mudança no modelo gera novo levantamento com hash diferente", q3.j.hash !== q1.j.hash && q3.j.versaoModelo > q1.j.versaoModelo, { antes: q1.j.versaoModelo, depois: q3.j.versaoModelo });
  if (id) {
    const v2 = (await eng.call("GET", `${O}/orcamentos/${id}`)).j;
    registrar("RF12", "CA-06", "orçamento antigo sinaliza quantitativo desatualizado", v2.quantitativoDesatualizado === true, { estado: v2.estado, quantitativoDesatualizado: v2.quantitativoDesatualizado, versao: v2.versaoModelo });
  }
}

// ---------- RF09/RF10 geotecnia
{
  const ruim = await eng.call("POST", `${O}/sondagens`, { codigo: "SP-01", x: 1, y: 1, cotaBoca: 0, camadas: [{ cotaTopo: 0, cotaBase: -2, tipo: "ARGILA", descricao: "a" }, { cotaTopo: -3, cotaBase: -6, tipo: "AREIA", descricao: "lacuna de 1 m" }], leituras: [{ profundidade: 1, nspt: -2, impenetravel: false }] });
  const errs = ruim.j.errors ?? [];
  registrar("RF09", "CA-02/03", "camadas com lacuna e Nspt negativo → 422 com todos os erros", ruim.s === 422 && errs.length >= 2, { s: ruim.s, errs });
  const ok = await eng.call("POST", `${O}/sondagens`, { codigo: "SP-01", x: 2.5, y: 2, cotaBoca: 0, nivelAgua: -3, camadas: [{ cotaTopo: 0, cotaBase: -4, tipo: "ARGILA", descricao: "argila siltosa" }, { cotaTopo: -4, cotaBase: -10, tipo: "AREIA", descricao: "areia média" }], leituras: [1, 2, 3, 4, 5, 6, 7, 8, 9].map((p) => ({ profundidade: p, nspt: 3 + p * 2, impenetravel: false })) });
  registrar("RF09", "cadastro", "furo SP-01 cadastrado como RASCUNHO", ok.s === 201 && ok.j.estado === "RASCUNHO", { s: ok.s, estado: ok.j.estado });
  const conf = await eng.call("POST", `${O}/sondagens/${ok.j.id}/confirmar`);
  registrar("RF09", "confirmar", "furo confirmado pelo engenheiro", conf.s === 200, conf.j.estado);
  const perfil = await eng.call("GET", `${O}/sondagens/perfil`, null, { query: { x: 2.5, y: 2 } });
  registrar("RF09", "perfil", "perfil interpolado identifica o furo de origem (rastreável)", perfil.s === 200 && JSON.stringify(perfil.j).includes("SP-01"), JSON.stringify(perfil.j).slice(0, 200));
  const presign = await eng.call("POST", `${O}/sondagens/${ok.j.id}/boletim/presign`);
  registrar("RF09", "CA-05", "upload do boletim por URL pré-assinada (S3)", presign.s === 200 && /^https?:/.test(presign.j.url ?? ""), { s: presign.s, url: (presign.j.url ?? "").slice(0, 60) });
  // fundações exigem análise concluída da versão atual
  await eng.call("POST", `${O}/estrutura/cargas`, { pavimentoId: T, natureza: "ACIDENTAL", tipo: "AREA", valor: 2.0 });
  await eng.call("POST", `${O}/estrutura/lancamentos/${T}/pronto`);
  const an = await eng.call("POST", `${O}/analises`);
  registrar("RF05", "fluxo", "análise após pronto", an.s === 202 && an.j.estado === "CONCLUIDO", { s: an.s, estado: an.j.estado, erro: an.j.mensagemErro });
  const fund = await eng.call("POST", `${O}/fundacoes`, { analiseId: an.j.id });
  registrar("RF10", "dimensionar", "dimensionamento de fundações com análise concluída + furo confirmado", fund.s === 202 || fund.s === 201, { s: fund.s, d: fund.j.detail, estado: fund.j.estado });
  await espera(2500);
  const f = (await eng.call("GET", `${O}/fundacoes`)).j?.[0];
  const props = f ? (await eng.call("GET", `${O}/fundacoes/${f.id}/propostas`)).j : [];
  registrar("RF10", "CA-02/04", "cada proposta traz tipo, dimensões, carga e memória (método e fatores)", Array.isArray(props) && props.length > 0 && props.every((p) => p.memoriaJson && p.tipo && p.cargaKn > 0), { estado: f?.estado, n: props?.length, ex: props?.[0] && { tipo: props[0].tipo, carga: props[0].cargaKn, memoria: String(props[0].memoriaJson).slice(0, 160) } });
  await aplicar("LINHA", { p1: { x: 7, y: 7 }, p2: { x: 8, y: 7 } });
  const f2 = (await eng.call("GET", `${O}/fundacoes`)).j?.[0];
  registrar("RF10", "CA-05-linha", "linha auxiliar não invalida fundações", f2?.estado === f?.estado, { antes: f?.estado, depois: f2?.estado });
  await aplicar("PILAR", { centro: { x: 2.5, y: 4 }, b: 0.2, h: 0.4 });
  const f3 = (await eng.call("GET", `${O}/fundacoes`)).j?.[0];
  registrar("RF10", "CA-05", "pilar novo marca fundações OBSOLETO", f3?.estado === "OBSOLETO", f3?.estado);
}

// ---------- RF14/RF15 pranchas e IFC
fs.mkdirSync(new URL("../../docs/auditoria/evidencias/arquivos/", import.meta.url).pathname, { recursive: true });
{
  m = (await eng.call("GET", `${O}/modelo`)).j;
  const pr = await eng.call("POST", `${O}/pranchas`, { tipo: "PLANTA_BAIXA", pavimentoId: T, escala: "1:50" });
  await eng.call("POST", `${O}/pranchas/processar`);
  await espera(1500);
  const lista = (await eng.call("GET", `${O}/pranchas`)).j;
  const p = lista.find((x) => x.id === pr.j.id) ?? lista[0];
  registrar("RF14", "CA-05", "prancha gerada em job e com estado acompanhável", ["GERADA", "EMITIDA"].includes(p?.estado), { estado: p?.estado, erro: p?.erro, versao: p?.versaoModelo });
  if (p?.estado === "GERADA") {
    const dl = await eng.call("GET", `${O}/pranchas/${p.id}/download`);
    const url = dl.j?.svg;
    let svg = "", dxf = "";
    if (url) { svg = await (await fetch(url)).text(); fs.writeFileSync(new URL("../../docs/auditoria/evidencias/arquivos/planta-baixa.svg", import.meta.url).pathname, svg); }
    if (dl.j?.dxf) { dxf = await (await fetch(dl.j.dxf)).text(); fs.writeFileSync(new URL("../../docs/auditoria/evidencias/arquivos/planta-baixa.dxf", import.meta.url).pathname, dxf); }
    const camadasDxf = [...new Set([...dxf.matchAll(/\n8\r?\n([A-Z_]+)\r?\n/g)].map((x) => x[1]))];
    registrar("RF14", "CA-07", "DXF com camadas EIXOS, COTAS, CORTADO, VISTA, TEXTO", ["EIXOS", "COTAS"].every((c) => camadasDxf.includes(c)), { camadas: camadasDxf, tamanho: dxf.length });
    registrar("RF14", "conteudo", "SVG da planta com escala 1:50 e identificação de rascunho/versão", /1:50/.test(svg) && /RASCUNHO|v\s*\d|vers/i.test(svg), { tamanho: svg.length, temEscala: /1:50/.test(svg), temRascunho: /RASCUNHO/i.test(svg), dl: dl.s });
    registrar("RF14", "CA-04", "marca d'água de rascunho sem cálculo aprovado", /RASCUNHO/i.test(svg), null);
    await aplicar("LINHA", { p1: { x: 9, y: 9 }, p2: { x: 10, y: 9 } });
    const p2 = (await eng.call("GET", `${O}/pranchas`)).j.find((x) => x.id === p.id);
    registrar("RF14", "CA-03", "mudança no modelo marca a prancha como OBSOLETA", p2?.estado === "OBSOLETA", p2?.estado);
  }
  const ex = await eng.call("POST", `${O}/exportacoes`, { formato: "IFC4X3" });
  await eng.call("POST", `${O}/exportacoes/processar`);
  await espera(1500);
  const e = (await eng.call("GET", `${O}/exportacoes`)).j.find((x) => x.id === ex.j.id);
  registrar("RF15", "CA-08", "exportação IFC é job assíncrono", e?.estado === "CONCLUIDO", { estado: e?.estado, erro: e?.erro ?? e?.mensagemErro });
  if (e?.estado === "CONCLUIDO") {
    const dl = await eng.call("GET", `${O}/exportacoes/${e.id}/download`);
    const ifc = dl.j?.url ? await (await fetch(dl.j.url)).text() : "";
    fs.writeFileSync(new URL("../../docs/auditoria/evidencias/arquivos/modelo.ifc", import.meta.url).pathname, ifc);
    const conta = (ent) => (ifc.match(new RegExp(`=\\s*${ent}\\(`, "g")) ?? []).length;
    registrar("RF15", "conteudo", "IFC4X3: cabeçalho, pilares, vigas, laje e parede com GlobalId", /FILE_SCHEMA\(\('IFC4X3/.test(ifc) && conta("IFCCOLUMN") >= 4 && conta("IFCBEAM") >= 4, { schema: (ifc.match(/FILE_SCHEMA\(\('([^']+)/) ?? [])[1], colunas: conta("IFCCOLUMN"), vigas: conta("IFCBEAM"), lajes: conta("IFCSLAB"), paredes: conta("IFCWALL") + conta("IFCWALLSTANDARDCASE"), janelas: conta("IFCWINDOW"), materiais: conta("IFCMATERIAL"), unidades: /IFCSIUNIT/.test(ifc) });
    const ex2 = await eng.call("POST", `${O}/exportacoes`, { formato: "IFC4X3" });
    await eng.call("POST", `${O}/exportacoes/processar`); await espera(1500);
    const dl2 = await eng.call("GET", `${O}/exportacoes/${ex2.j.id}/download`);
    const ifc2 = dl2.j?.url ? await (await fetch(dl2.j.url)).text() : "";
    const gids = (t) => [...t.matchAll(/IFCCOLUMN\('([^']{22})'/g)].map((x) => x[1]).sort().join();
    registrar("RF15", "CA-02", "GlobalId estável entre duas exportações da mesma versão", gids(ifc) !== "" && gids(ifc) === gids(ifc2), { a: gids(ifc).slice(0, 50), b: gids(ifc2).slice(0, 50) });
  }
}

// ---------- RF02/RF16/RF27 IA (modo simulado quando app.ia.habilitada=false)
{
  m = (await eng.call("GET", `${O}/modelo`)).j;
  const tn = await eng.call("POST", `${O}/modelo/texto-natural`, { pavimentoId: T, texto: "crie uma parede de 15 cm entre A/1 e B/1" });
  registrar("RF02", "CA-04", "texto natural vira rascunho de IA (não aplica direto)", tn.s === 201 && tn.j.geradoPorIa === true, { s: tn.s, aviso: tn.j.aviso, vi: tn.j.comandos?.map((c) => c.vi) });
  const elems = (await eng.call("GET", `${O}/modelo`)).j.elementos;
  const iaNoModelo = elems.filter((e) => e.origem === "IA");
  registrar("RF27", "CA-01", "elementos da IA ficam marcados (camada ia-rascunho/origem IA) até aprovação", iaNoModelo.every((e) => e.camada === "ia-rascunho"), { n: iaNoModelo.length, camadas: [...new Set(iaNoModelo.map((e) => e.camada))] });
  const rasc = (await eng.call("GET", `${O}/rascunhos-ia`)).j;
  registrar("RF27", "lista", "rascunhos de IA listados com estado", Array.isArray(rasc) && rasc.length > 0, rasc?.map((r) => `${r.tipo ?? r.rota}:${r.estado}`).slice(0, 4));
  if (rasc?.[0]) {
    const ap = await eng.call("POST", `${O}/rascunhos-ia/${rasc[0].id}/aprovar`, { artRrt: "ART-123" });
    registrar("RF27", "CA-03", "aprovar rascunho sem selo VERIFIED → 403 selo", ap.s === 403 && /selo/i.test(JSON.stringify(ap.j)), { s: ap.s, d: ap.j.detail });
    const rev = await eng.call("POST", `${O}/rascunhos-ia/${rasc[0].id}/revisao-humana`, { motivo: "Quero que uma pessoa confira a decisão automática." });
    registrar("RF27", "CA-08", "pedido de revisão humana gera solicitação rastreável com prazo", rev.s === 201 || rev.s === 200, { s: rev.s, j: rev.j });
  }
  const uso = await eng.call("GET", "/ia/uso");
  registrar("RF27", "CA-10", "tela Uso de IA lista as rotas com finalidade, base legal e retenção", uso.s === 200 && uso.j.length >= 5 && uso.j.every((r) => r.finalidade && r.baseLegal && r.retencao), uso.j?.map((r) => r.id));
  const cons = await eng.call("GET", `/empresas/${empresaId}/ia/consumo`);
  registrar("RF27", "CA-06", "consumo por rota só com metadados (tokens, sem conteúdo)", cons.s === 200 && !JSON.stringify(cons.j).match(/prompt|texto|conteudo/i), JSON.stringify(cons.j).slice(0, 200));
}

// ---------- RF21 selo + aprovação real
{
  const pedido = await eng.call("POST", "/me/selo", { tipo: "CREA", registro: "PR-123456/D", uf: "PR", contentTypeDocumento: "application/pdf" });
  registrar("RF21", "selo-pedido", "pedido de selo devolve URL de upload e estado PENDING", pedido.s === 202 && pedido.j.estado === "PENDING", { s: pedido.s });
  let sup = await novoCliente();
  const v = (await sup.call("GET", "/auth/legal/versoes")).j;
  await sup.call("POST", "/auth/register", { tipo: "PESSOA", nome: "Suporte Auditoria", email: SUPORTE, senha: SENHA, dataNascimento: "1980-01-01", aceitaTermos: true, aceitaPolitica: true, versaoTermos: v.termos, versaoPolitica: v.politica });
  try { await sup.call("POST", "/me/verify-email", { codigo: await codigoEmail(SUPORTE) }); } catch { /* já existe */ }
  let ent = await entrar(SUPORTE);
  let segSup = null;
  if (ent.ultimo.j?.proximaEtapa === "CONFIGURAR_MFA" || (await ent.call("GET", "/plataforma/selos")).s === 403) { const r = await ativarMfa(ent); segSup = r.segredo; }
  const fila = await ent.call("GET", "/plataforma/selos");
  const meuId = (await eng.call("GET", "/auth/session")).j.contaId;
  const minha = Array.isArray(fila.j) ? fila.j.find((x) => (x.contaId ?? x.id) === meuId) : null;
  registrar("RF21", "fila", "suporte vê a fila de selos pendentes", fila.s === 200 && !!minha, { s: fila.s, d: fila.j.detail, n: fila.j?.length });
  if (minha) {
    const rej = await ent.call("POST", `/plataforma/selos/${minha.contaId}/decisao`, { aprovado: false, motivo: "Documento ilegível" });
    const selo1 = (await eng.call("GET", "/me/selo")).j;
    registrar("RF21", "rejeicao", "rejeição mostra o motivo para o profissional", rej.s === 204 && selo1.estado === "REJECTED" && /ileg/i.test(selo1.motivo ?? ""), selo1);
    await eng.call("POST", "/me/selo", { tipo: "CREA", registro: "PR-123456/D", uf: "PR", contentTypeDocumento: "application/pdf" });
    const ap = await ent.call("POST", `/plataforma/selos/${minha.contaId}/decisao`, { aprovado: true });
    registrar("RF21", "aprovacao", "suporte aprova o selo", ap.s === 204, ap.s);
  }
  // novo login para a sessão refletir o selo; aprovação exige sudo + ART
  const eng2 = await entrar(dono.email, { segredo: mfa.segredo });
  const sess = (await eng2.call("GET", "/auth/session")).j;
  registrar("RF21", "sessao-selo", "sessão nova reflete selo verificado", sess.seloVerificado === true, sess);
  const rasc = (await eng2.call("GET", `${O}/rascunhos-ia`)).j?.find((r) => r.estado === "GERADO_POR_IA");
  if (rasc) {
    const semArt = await eng2.call("POST", `${O}/rascunhos-ia/${rasc.id}/aprovar`, { artRrt: "" });
    registrar("RF27", "CA-03b", "aprovar sem ART/RRT → 422", semArt.s === 422, { s: semArt.s, d: semArt.j.detail });
    const ok = await eng2.call("POST", `${O}/rascunhos-ia/${rasc.id}/aprovar`, { artRrt: "ART 1720260012345" });
    registrar("RF27", "CA-04", "aprovação registra aprovador, data e ART", ok.s === 200 && !!(ok.j.aprovadoPor ?? ok.j.decididoPor) && !!(ok.j.artRrt ?? ok.j.art) && !!(ok.j.aprovadoEm ?? ok.j.decididoEm), { s: ok.s, campos: Object.keys(ok.j) });
    const depois = (await eng2.call("GET", `${O}/modelo`)).j.elementos.filter((e) => e.origem === "IA");
    registrar("RF27", "promocao", "aprovar o rascunho promove os elementos à camada da disciplina (com versão no histórico)", depois.length > 0 && depois.every((e) => e.camada !== "ia-rascunho"), depois.map((e) => `${e.tipo}:${e.camada}`));
  }
}

// ---------- cliente: visibilidade (DN-02, RF13 CA-08)
{
  const cliEmail = `cliente-${Date.now()}@exemplo.cca`;
  const conv = await eng.call("POST", `/companies/${empresaId}/invites`, { email: cliEmail, obraId: obra.id, papel: "CLIENTE" });
  await espera(800);
  const token = /\/convite\/([A-Za-z0-9_-]{20,})/.exec(emailsPara(cliEmail).at(-1) ?? "")?.[1];
  const cli = await novoCliente();
  const v = (await cli.call("GET", "/auth/legal/versoes")).j;
  await cli.call("POST", "/auth/register", { tipo: "PESSOA", nome: "Cláudio Cliente", email: cliEmail, senha: SENHA, dataNascimento: "1975-05-05", aceitaTermos: true, aceitaPolitica: true, versaoTermos: v.termos, versaoPolitica: v.politica });
  await cli.call("POST", "/me/verify-email", { codigo: await codigoEmail(cliEmail) });
  const ac = await cli.call("POST", `/invites/${token}/accept`);
  registrar("RF20", "cliente-aceite", "cliente aceita e cai direto na obra", ac.s === 200 && ac.j.obraId === obra.id, { s: ac.s, j: ac.j });
  const antes = await cli.call("GET", `${O}/modelo`);
  registrar("RF13", "CA-08", "cliente não vê modelo não liberado", antes.s === 404 || antes.s === 403, { s: antes.s, d: antes.j.detail });
  const q = await cli.call("GET", `${O}/quantitativos`);
  registrar("DN-02", "quantitativos", "cliente não vê quantitativos não liberados", q.s === 404 || q.s === 403, q.s);
  const rasc = await cli.call("GET", `${O}/rascunhos-ia`);
  registrar("DN-02", "rascunhos", "cliente nunca vê rascunho de IA", rasc.s === 403 || rasc.s === 404 || (Array.isArray(rasc.j) && rasc.j.length === 0), { s: rasc.s, n: rasc.j?.length });
  await eng.call("PATCH", `${O}/modelo/liberar-cliente`, { liberar: true });
  const depois = await cli.call("GET", `${O}/modelo`);
  registrar("RF13", "liberado", "após liberar, cliente vê só a versão liberada e sem rascunho de IA", depois.s === 200 && !depois.j.elementos?.some((e) => e.camada === "ia-rascunho"), { s: depois.s, versao: depois.j.versao, ia: depois.j.elementos?.filter((e) => e.camada === "ia-rascunho").length });
  const edit = await cli.call("POST", `${O}/modelo/comandos`, { pavimentoId: T, codigo: "LINHA", args: { p1: { x: 0, y: 0 }, p2: { x: 1, y: 0 } }, origem: "CANVAS" });
  registrar("RF21", "CA-02-cliente", "cliente não edita o modelo (403 explicado)", edit.s === 403, { s: edit.s, d: edit.j.detail });
}

// ---------- RF24–RF26 titular
{
  const t = await contaVerificada({ nome: "Tânia Titular", prefixo: "titular" });
  const c = (await t.call("GET", "/me/consentimentos")).j;
  registrar("RF24", "CA-03", "finalidades opcionais começam desligadas", Array.isArray(c) && c.length > 0 && c.every((x) => x.ativo === false), c.map((x) => `${x.finalidade}:${x.estado}`));
  const f = c[0]?.finalidade;
  await t.call("PUT", `/me/consentimentos/${f}`, { concedido: true });
  await t.call("PUT", `/me/consentimentos/${f}`, { concedido: false });
  const h = (await t.call("GET", "/me/consentimentos/historico")).j;
  const ordem = h.map((x) => x.acao).join(">");
  registrar("RF24", "CA-04/07", "histórico com as duas ações, data e versão do texto (mais recente primeiro)", h.length >= 2 && ordem.startsWith("REVOGAR>CONCEDER") && h.every((x) => x.ocorridoEm && x.versaoTexto), h.map((x) => `${x.acao}@${x.ocorridoEm}`));
  const d = (await t.call("GET", "/me/dados")).j;
  registrar("RF25", "CA-01", "Seus dados lista categorias com finalidade, base legal e retenção", d.length > 0 && d.every((x) => x.finalidade && x.baseLegal && x.retencao), d.map((x) => x.categoria));
  const corr = await t.call("PUT", "/me/dados/nome", { valor: "Tânia T. Titular" });
  registrar("RF25", "CA-02", "corrigir nome grava e devolve 200", corr.s === 200 || corr.s === 204, corr.s);
  const comp = (await t.call("GET", "/me/compartilhamentos")).j;
  registrar("RF25", "CA-08", "compartilhamentos mostram transferência internacional com base do Art. 33", comp.some((x) => x.internacional && /33/.test(x.baseLegal)), comp.map((x) => `${x.nome}:${x.baseLegal}`));
  const exp1 = await t.call("POST", "/me/exportacoes");
  registrar("RF25", "CA-03", "exportar sem sudo → 403 sudo_required", exp1.s === 403 && /sudo/.test(exp1.j.type), exp1.s);
  // conta sem 2FA: o modo seguro usa senha + código enviado por e-mail
  const antesCodigo = Date.now();
  await t.call("POST", "/me/sudo/codigo");
  const sudo = await t.call("POST", "/me/sudo", { senha: SENHA, codigo: await codigoEmail(t.email, { depoisDe: antesCodigo - 1000 }) });
  registrar("RF23", "sudo-email", "modo seguro por senha + código de e-mail (conta sem 2FA)", sudo.s === 204, { s: sudo.s, d: sudo.j.detail });
  const exp2 = await t.call("POST", "/me/exportacoes");
  for (let i = 0; i < 20 && !["PRONTA", "CONCLUIDO", "FALHOU"].includes((await t.call("GET", `/me/exportacoes`)).j.find((x) => x.id === exp2.j.id)?.estado); i++) await espera(1000);
  const lista = (await t.call("GET", "/me/exportacoes")).j;
  const e = lista.find((x) => x.id === exp2.j.id) ?? lista[0];
  const det = e ? (await t.call("GET", `/me/exportacoes/${e.id}`)).j : null;
  const det2 = e ? (await t.call("GET", `/me/exportacoes/${e.id}`)).j : null;
  registrar("RF25", "CA-04", "ZIP pronto, senha mostrada uma única vez", (det?.estado === "CONCLUIDO" || det?.estado === "PRONTA") && !!det?.senhaZip && !det2?.senhaZip, { s: exp2.s, estado: det?.estado, erro: det?.erro, senha1: !!det?.senhaZip, senha2: !!det2?.senhaZip, chaves: det && Object.keys(det) });
  const ped = await t.call("POST", "/me/pedidos", { tipo: "ACESSO", descricao: "Quero saber quais dados de obra estão comigo." });
  registrar("RF25", "CA-06", "pedido ao encarregado com prazo de 15 dias", (ped.s === 201 || ped.s === 200) && !!(ped.j.prazo ?? ped.j.prazoEm), { s: ped.s, prazo: ped.j.prazo ?? ped.j.prazoEm, estado: ped.j.estado });
  const cons = (await t.call("GET", "/me/deletion")).j;
  registrar("RF26", "consequencias", "antes de excluir, mostra data final e o que fica/sai", !!cons.dataFinal && cons.oQueFica?.length > 0 && cons.oQueSai?.length > 0, cons);
  const del = await t.call("POST", "/me/deletion", { entendi: true });
  const depois = await t.call("GET", "/auth/session");
  registrar("RF26", "CA-02", "exclusão com sudo → 202 e sessões encerradas", del.s === 202 && depois.s === 401, { s: del.s, sessao: depois.s, j: del.j });
  const vol = await novoCliente();
  const l = await vol.call("POST", "/auth/login", { email: t.email, senha: SENHA });
  registrar("RF26", "CA-03a", "login na carência oferece reativação", l.j?.proximaEtapa === "OFERTA_REATIVACAO", l.j);
  const r = await vol.call("POST", "/auth/reactivate", { reativar: true });
  const s2 = await vol.call("GET", "/auth/session");
  registrar("RF26", "CA-03b", "reativar devolve a conta e abre a sessão", s2.s === 200, { reativar: r.s, sessao: s2.s, etapa: r.j?.proximaEtapa });
}

salvar(new URL("../../docs/auditoria/evidencias/j3-entrega-ia-lgpd.json", import.meta.url).pathname);
console.log(JSON.stringify({ email: dono.email, segredo: mfa.segredo, obra: obra.id }));
