// AUD-CC-3D-001 — diagnóstico: executa os cenários contra a instância e registra o que acontece de fato.
// Rodado antes e depois das correções (CCA_FASE=antes|depois). Resultado em docs/auditoria/evidencias/j6-<fase>.json.
import fs from "node:fs";
import { ativarMfa, contaVerificada, entrar, espera, registrar, salvar } from "./cliente.mjs";

const FASE = process.env.CCA_FASE ?? "antes";
const dono = await contaVerificada({ tipo: "EMPRESA", nome: "Davi Interiores", prefixo: `j6${FASE}` });
const mfa = await ativarMfa(dono);
const eng = await entrar(dono.email, { segredo: mfa.segredo });
const obra = (await eng.call("POST", "/obras", { nome: `Apartamento ${FASE}`, uf: "SP", municipio: "Santos" })).j;
const O = `/obras/${obra.id}`;
let m = (await eng.call("GET", `${O}/modelo`)).j;
const terreo = m.pavimentos[0].id;
const recarregar = async () => { m = (await eng.call("GET", `${O}/modelo`)).j; return m; };
const cmd = async (codigo, args) => { const r = await eng.call("POST", `${O}/modelo/comandos`, { pavimentoId: terreo, codigo, args, versaoBase: m.versao, origem: "CANVAS" }); await recarregar(); return r; };
const criado = (r) => r.j?.diff?.criados?.[0];
const el = (id) => { const e = m.elementos.find((x) => x.id === id); return e ? { ...e, ...(e.props ?? {}) } : undefined; };
const p = (x, y) => ({ x, y });

// ---------- CT-01: sala, cozinha, banheiro e dois quartos (10 × 8 m)
const paredes = {};
const W = async (nome, a, b, esp = 0.15) => { const r = await cmd("PAREDE", { p1: a, p2: b, esp }); paredes[nome] = criado(r)?.id; return r; };
await W("sul", p(0, 0), p(10, 0)); await W("leste", p(10, 0), p(10, 8)); await W("norte", p(10, 8), p(0, 8)); await W("oeste", p(0, 8), p(0, 0));
await W("cozinhaLeste", p(3, 0), p(3, 3.5));   // divisa cozinha | sala
await W("cozinhaNorte", p(0, 3.5), p(3, 3.5)); // divisa cozinha | banheiro
await W("banhoLeste", p(3, 3.5), p(3, 5.5));
await W("banhoNorte", p(0, 5.5), p(3, 5.5));
await W("quartos", p(6, 4), p(10, 4));
await W("quartosOeste", p(6, 4), p(6, 8));
await W("quartosMeio", p(8, 4), p(8, 8));
const divisoria = await cmd("PAREDE", { p1: p(6, 0.5), p2: p(6, 2), esp: 0.12, mat: "MAT.ALVENARIA.CERAMICO_VEDACAO_9" });
registrar("AUD-01", "divisoria-12cm", "divisória de 12 cm com bloco cerâmico de 9 cm (material disponível no catálogo)", divisoria.s === 201, { s: divisoria.s, d: divisoria.j?.relatorio?.laco1?.violacoes?.[0]?.mensagem ?? divisoria.j?.detail });
if (divisoria.s === 201) await cmd("APAGAR", { sel: [criado(divisoria).id] });
const portaCoz = await cmd("PORTA", { pos: p(1.5, 3.5) });
await cmd("PORTA", { pos: p(3, 4.5) });
await cmd("PORTA", { pos: p(7, 4) }); await cmd("PORTA", { pos: p(9, 4) });
await cmd("JANELA", { pos: p(1.5, 0) }); await cmd("JANELA", { pos: p(6, 0) });
for (const [nome, x, y] of [["Cozinha", 1.5, 1.7], ["Banheiro", 1.5, 4.5], ["Sala", 5, 2], ["Quarto 1", 7, 6], ["Quarto 2", 9, 6]]) await cmd("AMBIENTE", { ponto: p(x, y), nome });
const cota = await cmd("COTA_LINEAR", { p1: p(0, -0.5), p2: p(3, -0.5), afast: 0.5 });
const nParedes = m.elementos.filter((e) => e.tipo === "PAREDE").length;
registrar("CT-01", "planta", "planta com sala, cozinha, banheiro e dois quartos (11 paredes, 4 portas, 2 janelas, 5 ambientes)",
  nParedes === 11 && m.elementos.filter((e) => e.tipo === "PORTA").length === 4 && m.elementos.filter((e) => e.tipo === "AMBIENTE").length === 5, { paredes: nParedes, versao: m.versao });

// ---------- CT-02 / AUD-01: ampliar a cozinha deslocando a parede em 60 cm
const portaAntes = el(criado(portaCoz)?.id);
const cozNorteAntes = el(paredes.cozinhaNorte);
const xPortaAntes = cozNorteAntes.p1.x + (cozNorteAntes.p2.x - cozNorteAntes.p1.x) * portaAntes.posicao;
const mov = await cmd("MOVER", { sel: [paredes.cozinhaLeste], base: p(3, 0), destino: p(3.6, 0) });
const leste = el(paredes.cozinhaLeste), norte = el(paredes.cozinhaNorte), banho = el(paredes.banhoLeste), cotaDepois = el(criado(cota)?.id);
const portaDepois = el(criado(portaCoz)?.id);
const xPortaDepois = norte.p1.x + (norte.p2.x - norte.p1.x) * portaDepois.posicao;
registrar("AUD-01", "mover-parede", "parede da cozinha deslocada 60 cm", mov.s === 201 && Math.abs(leste.p1.x - 3.6) < 1e-6, { s: mov.s, p1: leste.p1, p2: leste.p2 });
registrar("AUD-01", "parede-ligada", "parede ligada (divisa cozinha|banheiro) acompanha: termina em x = 3,60", Math.abs(Math.max(norte.p1.x, norte.p2.x) - 3.6) < 1e-6, { norte });
registrar("AUD-01", "porta-dependente", "porta da cozinha mantém a posição absoluta (x = 1,50 m)", Math.abs(xPortaDepois - xPortaAntes) < 0.01, { antes: xPortaAntes, depois: +xPortaDepois.toFixed(3) });
registrar("AUD-01", "cota", "cota da largura da cozinha passa a medir 3,60 m", cotaDepois && Math.abs(Math.abs(cotaDepois.p2.x - cotaDepois.p1.x) - 3.6) < 0.01, { cota: cotaDepois && [cotaDepois.p1, cotaDepois.p2] });
registrar("AUD-01", "parede-continua", "parede do banheiro, que continuava a da cozinha, não é deslocada junto", Math.abs(banho.p1.x - 3) < 1e-6, { banho: [banho.p1, banho.p2] });

// ---------- pavimento de cima (laje e viga) — teto livre, AUD-05 e luminárias
await cmd("PAVIMENTO", { nome: "Cobertura", z: 3 });
const cobertura = m.pavimentos.find((x) => x.nome === "Cobertura")?.id;
const cmdPav = async (pav, codigo, args) => { const r = await eng.call("POST", `${O}/modelo/comandos`, { pavimentoId: pav, codigo, args, versaoBase: m.versao, origem: "CANVAS" }); await recarregar(); return r; };
await cmdPav(cobertura, "LAJE", { contorno: [p(0, 0), p(10, 0), p(10, 8), p(0, 8)], h: 0.12 });
const viga = criado(await cmdPav(cobertura, "VIGA", { p1: p(0, 2), p2: p(10, 2), h: 0.5 }))?.id;

// ---------- CT-03 / AUD-02: mobiliário e equipamentos
const ob = async (item, pos, extra = {}) => { const r = await cmd("OBJETO", { item, pos, ...extra }); return { r, id: criado(r)?.id }; };
const mob = {
  sofa: await ob("SALA.SOFA_3L", p(5, 1)), mesa: await ob("SALA.MESA_JANTAR_6", p(8, 2.6)), tv: await ob("SALA.TV_55", p(3.7, 1.6), { rot: 0 }),
  tapete: await ob("DECORACAO.TAPETE", p(5, 1.6)), quadro: await ob("DECORACAO.QUADRO", p(9.9, 2.6)),
  gelD: await ob("COZINHA.GELADEIRA_DISPENSER", p(0.55, 3.04)), fogao: await ob("COZINHA.FOGAO_4B", p(1.4, 3.1)), pia: await ob("COZINHA.PIA", p(2.6, 3.1)),
  cama: await ob("DORMITORIO.CAMA_CASAL", p(7, 6.9)), guarda: await ob("DORMITORIO.GUARDA_ROUPA", p(9, 7.6)), solteiro: await ob("DORMITORIO.CAMA_SOLTEIRO", p(9.4, 6.0)),
  vaso: await ob("BANHEIRO.VASO", p(0.5, 4.2)), lav: await ob("BANHEIRO.LAVATORIO", p(2.4, 5.4)), chuveiro: await ob("BANHEIRO.CHUVEIRO_ELETRICO", p(0.1, 5.0)),
  maq: await ob("SERVICO.MAQUINA_LAVAR", p(1.0, 0.6)),
};
const falhos = Object.entries(mob).filter(([, v]) => v.r.s !== 201).map(([k, v]) => ({ k, s: v.r.s, d: v.r.j?.detail ?? v.r.j?.relatorio?.laco1?.violacoes?.map((x) => x.mensagem) }));
registrar("CT-03", "mobiliario", "15 objetos inseridos (sala, cozinha, dormitórios, banheiro, serviço, decoração)", falhos.length === 0, { falhos });
const tv = el(mob.tv.id);
registrar("AUD-02", "encaixe-parede", "TV de parede encosta na face da parede e fica a 1,00 m do piso", !!tv?.paredeId && tv.elevacao === 1 && Math.abs(tv.centro.x - (3.6 + 0.075 + 0.04)) < 0.002, { centro: tv?.centro, elevacao: tv?.elevacao, paredeId: tv?.paredeId });
const def = await cmd("ALTERAR", { id: mob.gelD.id, props: { dim: { l: 0.9, p: 0.75, a: 2.4 } } });
registrar("AUD-02", "sem-deformacao", "deformar a geladeira (altura 2,40 m) é recusado; dimensões do produto preservadas", def.s === 422 && el(mob.gelD.id).dim.a === 1.85, { s: def.s, d: def.j?.relatorio?.laco1?.violacoes?.[0]?.mensagem ?? def.j?.detail });
const mesaL = await cmd("ALTERAR", { id: mob.mesa.id, props: { dim: { l: 2.2, p: 0.9, a: 0.75 } } });
registrar("AUD-02", "redimensionavel", "mesa (móvel redimensionável) aceita 2,20 m de comprimento", mesaL.s === 201 && el(mob.mesa.id).dim.l === 2.2, { s: mesaL.s });
const lum = await ob("ILUMINACAO.PENDENTE", p(5, 3)), lumC = await ob("ILUMINACAO.PLAFON", p(1.8, 1.8)), lumQ = await ob("ILUMINACAO.PLAFON", p(7, 6));
registrar("AUD-02", "teto", "pendente vai ao teto livre: elevação = 3,00 − 0,12 (laje) − 0,60 (altura) = 2,28 m", Math.abs(el(lum.id).elevacao - 2.28) < 1e-6, { elevacao: el(lum.id)?.elevacao });
const bib = await eng.call("GET", "/catalogo/objetos", undefined, { query: { q: "geladeira 70" } });
const bibCat = await eng.call("GET", "/catalogo/objetos", undefined, { query: { categoria: "BANHEIRO" } });
registrar("AUD-02", "biblioteca", "biblioteca pela API: busca \"geladeira 70\" acha só a geladeira de 0,70 m; categoria BANHEIRO lista louças", bib.s === 200 && bib.j.itens.map((x) => x.id).join() === "COZINHA.GELADEIRA" && bibCat.j.itens.length >= 4, { s: bib.s, achados: bib.j?.itens?.map((x) => x.id), banheiro: bibCat.j?.itens?.length, fabricante: bib.j?.fabricante });
const rota = await eng.call("GET", "/objetos/catalogo");
registrar("AUD-02", "rota-inexistente", "endereço inexistente responde 404 (antes: 500)", rota.s === 404, { s: rota.s });

// ---------- CT-04 / AUD-03: elétrica
const qd = await ob("INSTALACOES.QUADRO_DISTRIBUICAO", p(0.1, 6.5));
const tomGel = await ob("INSTALACOES.TOMADA_10A", p(0.1, 2.4)), tomMaq = await ob("INSTALACOES.TOMADA_10A", p(0.5, 0.1)), tomTv = await ob("INSTALACOES.TOMADA_10A", p(3.7, 0.6));
const tomBanho = await ob("INSTALACOES.TOMADA_10A", p(2.5, 5.4));
const liga = async (de, para, extra = {}) => { const r = await cmd("CONECTAR", { de, para, ...extra }); return { r, id: criado(r)?.id }; };
const ligs = {
  qdTomGel: await liga(qd.id, tomGel.id, { circuito: "C2", disjuntor: 10 }), qdTomMaq: await liga(qd.id, tomMaq.id, { circuito: "C3", disjuntor: 10 }),
  qdTomTv: await liga(qd.id, tomTv.id, { circuito: "C2" }), qdTomBanho: await liga(qd.id, tomBanho.id, { circuito: "C2" }),
  gel: await liga(mob.gelD.id + ":E1", tomGel.id), maq: await liga(tomMaq.id, mob.maq.id), tv: await liga(tomTv.id, mob.tv.id),
  lum: await liga(qd.id, lum.id, { circuito: "C1", disjuntor: 10 }), lumC: await liga(qd.id, lumC.id, { circuito: "C1" }), lumQ: await liga(qd.id, lumQ.id, { circuito: "C1" }),
  chuv: await liga(qd.id, mob.chuveiro.id + ":E1", { circuito: "C4", disjuntor: 32 }),
};
const tomLivre = await ob("INSTALACOES.TOMADA_10A", p(3.5, 0.6)); // fica livre para a ligação feita pela interface (j6-interface)
ligs.qdTomLivre = await liga(qd.id, tomLivre.id, { circuito: "C5", disjuntor: 10 });
const lf = Object.entries(ligs).filter(([, v]) => v.r.s !== 201).map(([k, v]) => ({ k, s: v.r.s, d: v.r.j?.detail ?? v.r.j?.relatorio?.laco1?.violacoes?.map((x) => x.mensagem) }));
registrar("CT-04", "eletrica", "12 ligações elétricas (quadro → tomadas → aparelhos; quadro → luminárias e chuveiro)", lf.length === 0, { falhos: lf });
const cg = el(ligs.gel.id);
registrar("AUD-03", "sentido", "ligação pedida na ordem geladeira → tomada é gravada no sentido saída → entrada (tomada.S1 → geladeira.E1)", cg?.de?.id === tomGel.id && cg?.de?.conector === "S1" && cg?.para?.conector === "E1", { de: cg?.de, para: cg?.para });
const chuvTom = await cmd("CONECTAR", { de: tomBanho.id + ":S1", para: mob.chuveiro.id + ":E1" });
const codigosChuv = (chuvTom.j?.relatorio?.laco1?.violacoes ?? []).map((v) => v.codigo);
registrar("AUD-03", "incompativel", "chuveiro 5500 W/220 V numa tomada de 10 A/127 V é recusado com motivo (tensão e corrente)", chuvTom.s === 422 && codigosChuv.includes("CON-TENSAO") && codigosChuv.includes("CON-CORRENTE"), { s: chuvTom.s, codigos: codigosChuv, msg: chuvTom.j?.relatorio?.laco1?.violacoes?.map((v) => v.mensagem) });
const sofaCon = await cmd("CONECTAR", { de: tomTv.id, para: mob.sofa.id });
registrar("AUD-04", "sofa", "sofá não oferece conexão: pedido recusado explicando que não há conectores compatíveis", sofaCon.s === 422 && /par de conectores compat/i.test(JSON.stringify(sofaCon.j)), { s: sofaCon.s, d: sofaCon.j?.detail });
const movGel = await cmd("MOVER", { sel: [mob.gelD.id], base: p(0, 0), destino: p(0, -0.3) });
const avisos = (movGel.j?.relatorio?.laco2?.avisos ?? []).map((a) => a.mensagem);
registrar("AUD-03", "mover-conectado", "mover a geladeira conectada mantém a conexão e avisa o percurso recalculado", movGel.s === 201 && !!el(ligs.gel.id) && avisos.some((a) => /recalculado/.test(a)), { s: movGel.s, avisos });
const desl = await cmd("ALTERAR", { id: lum.id, props: { ligada: false } });
registrar("AUD-07", "luminaria-desligada", "apagar a luminária (efeito visual) não remove a conexão nem a carga do circuito", desl.s === 201 && el(lum.id).ligada === false && !!el(ligs.lum.id), { s: desl.s });

// ---------- CT-05 / AUD-04: hidrossanitária
const ent = await ob("INSTALACOES.ENTRADA_AGUA", p(2.4, 0.1));
const aq = await ob("INSTALACOES.AQUECEDOR", p(3.5, 2.6));
const ci = await ob("INSTALACOES.CAIXA_INSPECAO", p(1.5, -1.2));
const vent = await ob("INSTALACOES.TERMINAL_VENTILACAO", p(0.2, 3.7));
const hid = {
  lav: await liga(ent.id, mob.lav.id), vaso: await liga(ent.id, mob.vaso.id + ":AF1"), chuv: await liga(ent.id, mob.chuveiro.id + ":AF1"), gel: await liga(ent.id, mob.gelD.id + ":AF1"),
  maq: await liga(ent.id, mob.maq.id + ":AF1"), aq: await liga(ent.id, aq.id), piaAF: await liga(ent.id, mob.pia.id + ":AF1"), piaAQ: await liga(aq.id, mob.pia.id + ":AQ1"),
  esLav: await liga(mob.lav.id + ":ES1", ci.id, { declividade: 0.02 }), esVaso: await liga(mob.vaso.id + ":ES1", ci.id, { declividade: 0.01 }), esPia: await liga(mob.pia.id + ":ES1", ci.id), esMaq: await liga(mob.maq.id + ":ES1", ci.id),
  ventVaso: await liga(mob.vaso.id + ":V1", vent.id),
};
const hf = Object.entries(hid).filter(([, v]) => v.r.s !== 201).map(([k, v]) => ({ k, s: v.r.s, d: v.r.j?.detail ?? v.r.j?.relatorio?.laco1?.violacoes?.map((x) => x.mensagem) }));
registrar("CT-05", "hidrossanitaria", "13 ligações de água fria, água quente, esgoto e ventilação", hf.length === 0, { falhos: hf });
const misto = await cmd("CONECTAR", { de: ent.id + ":S1", para: mob.pia.id + ":AQ1" });
registrar("AUD-04", "sistema-incompativel", "água fria direto na entrada de água quente da pia é recusada", misto.s === 422, { s: misto.s, msg: misto.j?.relatorio?.laco1?.violacoes?.map((v) => v.mensagem) });
const redes = await eng.call("GET", `${O}/coordenacao/instalacoes`, undefined, { query: { tensao: 127, fp: 1 } });
const porCodigo = {}; for (const it of redes.j?.itens ?? []) { const k = `${it.verificacao.codigo}:${it.verificacao.estado}`; porCodigo[k] = (porCodigo[k] ?? 0) + 1; }
registrar("AUD-03", "rede-verificada", "verificador de redes lê as conexões do modelo (componentes, trechos, circuitos, declividade)", redes.s === 200 && redes.j.trechos === 25 && !Object.keys(porCodigo).some((k) => /^MEP-(CON|SIS)-01:NAO_ATENDIDA/.test(k)), { s: redes.s, componentes: redes.j?.componentes, trechos: redes.j?.trechos, estado: redes.j?.estado, porCodigo });

// ---------- CT-06 / AUD-05: conflitos entre disciplinas
const gelPorta = await ob("COZINHA.GELADEIRA", p(7.0, 4.6)); // dentro do giro da porta do quarto 1
await cmd("ALTERAR", { id: lum.id, props: { ligada: true } });
const lumLaje = await ob("ILUMINACAO.PENDENTE", p(5, 1.0));
await cmd("ALTERAR", { id: lumLaje.id, props: { elevacao: 2.5 } }); // topo a 3,10 m: entra na laje
await cmd("MOVER", { sel: [mob.maq.id], base: p(0, 0), destino: p(7.5, 0) }); // máquina ligada à tomada da cozinha vai para a sala
const coord = await eng.call("POST", `${O}/coordenacao/verificar`);
const regras = (coord.j?.conflitos ?? []).filter((c) => c.estado !== "RESOLVIDO");
const tem = (regra, ids) => regras.find((c) => c.regra === regra && ids.every((i) => c.elementos.includes(i)));
const casos = {
  "tubulação × viga": tem("MEP-01", [hid.lav.id, viga]), "eletroduto × tubulação": tem("MEP-02", [hid.lav.id, ligs.qdTomBanho.id]),
  "porta × geladeira": tem("OBJ-03", [gelPorta.id]), "luminária × laje": tem("OBJ-04", [lumLaje.id]), "equipamento em outro ambiente": tem("MEP-03", [mob.maq.id, tomMaq.id]),
};
registrar("AUD-05", "cenario", "os cinco conflitos do cenário AUD-05 são detectados, com objetos envolvidos e explicação", Object.values(casos).every(Boolean),
  Object.fromEntries(Object.entries(casos).map(([k, c]) => [k, c ? { regra: c.regra, tipo: c.tipo, mensagem: c.mensagem, evidencia: c.evidencia } : null])));
registrar("AUD-05", "sem-furo", "nenhuma mensagem propõe abrir furo automaticamente; passagem em estrutura exige validação", regras.every((c) => !/abra (um )?furo|furar a viga/i.test(c.mensagem)) && /validação do engenheiro estrutural/.test(casos["tubulação × viga"]?.mensagem ?? ""), {});
registrar("CT-07", "coordenacao", "verificação de conflitos roda no modelo", coord.s === 200, { s: coord.s, detectados: coord.j?.detectados, regras: [...new Set(regras.map((c) => c.regra))], naoVerificado: coord.j?.naoVerificado, conflitos: regras.map((c) => `${c.regra}: ${c.mensagem}`) });

// ---------- CT-09 / AUD-08: pessoas
const adulto = await ob("PESSOAS.ADULTO", p(6, 3), { altura: 1.6 }), crianca = await ob("PESSOAS.CRIANCA", p(9, 5.0));
const ad = el(adulto.id);
registrar("CT-09", "humano", "pessoa adulta com altura 1,60 m: escala uniforme (largura proporcional) na camada humanos", adulto.r.s === 201 && ad.dim.a === 1.6 && Math.abs(ad.dim.l / ad.dim.a - 0.5 / 1.75) < 0.002 && ad.camada === "humanos" && crianca.r.s === 201, { dim: ad?.dim, camada: ad?.camada });
const deformar = await cmd("ALTERAR", { id: adulto.id, props: { dim: { l: 1.2, p: 0.3, a: 1.6 } } });
registrar("AUD-08", "sem-deformar", "deformar a pessoa (largura 1,20 m) é recusado", deformar.s === 422, { s: deformar.s, d: deformar.j?.relatorio?.laco1?.violacoes?.[0]?.mensagem });
const altInv = await cmd("ALTERAR", { id: adulto.id, props: { altura: 2.6 } });
registrar("AUD-08", "altura-limite", "altura fora de 1,40–2,10 m é recusada", altInv.s === 422, { s: altInv.s, d: altInv.j?.relatorio?.laco1?.violacoes?.[0]?.mensagem });
const quant = await eng.call("GET", `${O}/quantitativos`);
const txtQ = JSON.stringify(quant.j);
registrar("AUD-08", "quantitativos", "pessoas não entram em quantitativos nem orçamento", quant.s === 200 && !/PESSOAS\.|humanos/i.test(txtQ), { s: quant.s });

// ---------- AUD-08: escada até a cobertura, pessoa sobre o degrau; AUD-01: cota ancorada
const escada = await cmd("ESCADA", { p1: p(4.5, 4.3), p2: p(4.5, 7.6) });
const esc = el(criado(escada)?.id);
registrar("AUD-08", "escada", "escada do térreo à cobertura: desnível 3,00 m, 17 degraus de 17,6 cm (espelho adotado 17,5 cm, ajustável)", escada.s === 201 && esc?.desnivel === 3 && esc?.degraus === 17, { s: escada.s, desnivel: esc?.desnivel, degraus: esc?.degraus, d: escada.j?.detail });
const rampa = await cmd("RAMPA", { p1: p(10.5, 0.5), p2: p(16.5, 0.5), desnivel: 0.5 });
registrar("AUD-08", "rampa", "rampa externa de 6 m com 0,50 m de desnível gravada (inclinação calculada 8,33 %)", rampa.s === 201, { s: rampa.s, d: rampa.j?.detail });
const subindo = await ob("PESSOAS.ADULTO", p(4.5, 5.9));
registrar("AUD-08", "pessoa-na-escada", "pessoa posta sobre a escada fica na altura do degrau (9º de 17: 1,588 m)", subindo.r.s === 201 && Math.abs(el(subindo.id).elevacao - 1.588) < 0.002, { elevacao: el(subindo.id)?.elevacao });
const coord2 = await eng.call("POST", `${O}/coordenacao/verificar`);
const cir = (coord2.j?.conflitos ?? []).find((c) => c.regra === "CIR-01" && c.elementos.includes(esc.id) && c.estado !== "RESOLVIDO");
registrar("AUD-05", "escada-laje", "escada que atravessa a laje da cobertura sem abertura é apontada (o modelo ainda não tem abertura em laje)", !!cir, { mensagem: cir?.mensagem, evidencia: cir?.evidencia });
const aux = criado(await cmd("LINHA", { p1: p(12, -3), p2: p(14, -3) }))?.id;
const cotaAnc = criado(await cmd("COTA_LINEAR", { p1: p(12, -3), p2: p(14, -3), afast: 0.5 }))?.id;
await cmd("ALTERAR", { id: aux, props: { p2: p(15.25, -3) } });
const ca = el(cotaAnc);
registrar("AUD-01", "cota-ancorada", "cota ancorada nas extremidades acompanha qualquer edição do elemento (2,00 → 3,25 m)", ca?.p2?.x === 15.25 && !!ca?.ancoras?.p2, { p1: ca?.p1, p2: ca?.p2, ancoras: ca?.ancoras });

// ---------- CT-10: salvar, reabrir, exportar
const reaberto = (await entrar(dono.email, { segredo: mfa.segredo }));
const m2 = (await reaberto.call("GET", `${O}/modelo`)).j;
const chave = (e) => JSON.stringify({ t: e.tipo, c: e.camada, p: e.props });
const difs = m.elementos.filter((e) => { const o = m2.elementos.find((x) => x.id === e.id); return !o || chave(o) !== chave(e); }).map((e) => e.id);
registrar("CT-10", "reabrir", "reabrir em outra sessão preserva todos os elementos, propriedades, objetos e conexões", m2.versao === m.versao && m2.elementos.length === m.elementos.length && difs.length === 0,
  { versao: m2.versao, elementos: m2.elementos.length, objetos: m2.elementos.filter((e) => e.tipo === "OBJETO").length, conexoes: m2.elementos.filter((e) => e.tipo === "CONEXAO").length, diferentes: difs.length });
const ex = await eng.call("POST", `${O}/exportacoes`, { formato: "IFC4X3" });
registrar("RF15", "exportar", "pedido de exportação IFC aceito para a planta fechada", ex.s === 202 || ex.s === 201, { s: ex.s, d: ex.j?.detail, v: (ex.j?.relatorio?.laco2?.violacoes ?? []).map((v) => v.mensagem) });
await eng.call("POST", `${O}/exportacoes/processar`);
let e;
for (let i = 0; i < 10; i++) { await espera(700); e = (await eng.call("GET", `${O}/exportacoes`)).j.find((x) => x.id === ex.j.id); if (e?.estado === "CONCLUIDO" || e?.estado === "FALHOU") break; }
let ifc = "";
if (e?.estado === "CONCLUIDO") { const dl = await eng.call("GET", `${O}/exportacoes/${e.id}/download`); ifc = dl.j?.url ? await (await fetch(dl.j.url)).text() : ""; }
const arqIfc = new URL(`../../docs/auditoria/evidencias/arquivos/j6-${FASE}.ifc`, import.meta.url).pathname;
fs.writeFileSync(arqIfc, ifc);
const conta = (ent) => (ifc.match(new RegExp(`=\\s*${ent}\\(`, "g")) ?? []).length;
registrar("RF15", "ifc-circulacao", "IFC com escada (IfcStair) e rampa (IfcRamp), com número e altura de espelhos", conta("IFCSTAIR") === 1 && conta("IFCRAMP") === 1 && /'NumberOfRiser'/.test(ifc), { escadas: conta("IFCSTAIR"), rampas: conta("IFCRAMP") });
registrar("RF15", "ifc-arquitetura", "IFC com paredes, portas, janelas e ambientes", conta("IFCWALL") === nParedes && conta("IFCDOOR") === 4 && conta("IFCSPACE") === 5, { paredes: conta("IFCWALL"), portas: conta("IFCDOOR"), janelas: conta("IFCWINDOW"), espacos: conta("IFCSPACE") });
const nObj = m.elementos.filter((x) => x.tipo === "OBJETO" && !String(x.props?.item ?? "").startsWith("PESSOAS.")).length;
const nCon = m.elementos.filter((x) => x.tipo === "CONEXAO").length;
const ifcObjetos = ["IFCFURNITURE", "IFCFURNISHINGELEMENT", "IFCELECTRICAPPLIANCE", "IFCSANITARYTERMINAL", "IFCLIGHTFIXTURE", "IFCOUTLET", "IFCELECTRICDISTRIBUTIONBOARD", "IFCFLOWMETER", "IFCBOILER", "IFCDISTRIBUTIONCHAMBERELEMENT", "IFCFLOWTERMINAL"].reduce((a, k) => a + conta(k), 0);
registrar("RF15", "ifc-objetos", "IFC com todos os objetos (exceto pessoas), uma porta por conector e uma IfcRelConnectsPorts por conexão", ifcObjetos === nObj && conta("IFCRELCONNECTSPORTS") === nCon && conta("IFCDISTRIBUTIONPORT") > nCon && !/PESSOAS\./.test(ifc),
  { objetosModelo: nObj, objetosIfc: ifcObjetos, conexoesModelo: nCon, relConnectsPorts: conta("IFCRELCONNECTSPORTS"), portas: conta("IFCDISTRIBUTIONPORT"), circuitos: conta("IFCDISTRIBUTIONCIRCUIT"), sistemas: conta("IFCDISTRIBUTIONSYSTEM") });

salvar(new URL(`../../docs/auditoria/evidencias/j6-${FASE}.json`, import.meta.url).pathname);
// dados da sessão para a jornada pela interface (j6-interface.mjs); fora do repositório
if (process.env.CCA_SESSAO) fs.writeFileSync(process.env.CCA_SESSAO, JSON.stringify({ email: dono.email, segredo: mfa.segredo, obra: obra.id, ids: { subindo: subindo.id, gelD: mob.gelD.id, tomLivre: tomLivre.id, adulto: adulto.id, lum: lum.id, gelPorta: gelPorta.id, lumLaje: lumLaje.id, lav: hid.lav.id, viga, maq: mob.maq.id } }));
console.log(JSON.stringify({ email: dono.email, obra: obra.id, ifc: arqIfc }));
