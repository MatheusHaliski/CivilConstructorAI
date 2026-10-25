/**
 * Estado do motor de desenho (RF28 §7.5): máquina OCIOSO → SELECIONANDO → COMANDO_ATIVO → AGUARDANDO_ENTRADA → ENVIANDO.
 * O ModeloBim (pacote @cca/notacao-vi) é a fonte da verdade local; com `obraId`, cada comando aprovado localmente é
 * enviado ao backend (POST /modelo/comandos) e o modelo é recarregado do servidor para adotar os ids persistidos.
 */
import { create } from "zustand";
import { CATALOGO, ErroComandoVI, ModeloBim, PRIORIDADE_OSNAP, buscarComandos, dist, formatarVI, intersecaoSegmentos, parseVI, projetarNoSegmento, resolverComando, type Camada, type Comando, type Elemento, type EstadoModelo, type ModoOsnap, type PerfilAtalhos, type Ponto2D, type RelatorioAuditoria, type Violacao } from "@cca/notacao-vi";
import { ErroApi, get as apiGet, mensagemDeErro, post } from "@/lib/api";
import type { ResultadoComando, VisaoModelo } from "@/lib/tipos";
import { interpretarPonto, interpretarValor, restringir } from "./entrada";
import { caixaElemento } from "./snap";
import { deServidor } from "./servidor";

export type EstadoMaquina = "OCIOSO" | "SELECIONANDO" | "COMANDO_ATIVO" | "AGUARDANDO_ENTRADA" | "ENVIANDO";
export type LinhaLog = { id: number; tipo: "prompt" | "entrada" | "erro" | "ok" | "info" | "ia"; texto: string };
export type ComandoAtivo = {
  def: Comando; passo: number; valores: Record<string, unknown>;
  /** pontos acumulados em passos `repetir` (polilinha, laje, cópia múltipla) */
  pontos: Ponto2D[];
  /** seleções acumuladas (APARAR tem duas) */
  selecoes: string[][];
  /** erro mostrado no prompt (CA-02) */
  erro?: string;
};
export type Viewport = { cx: number; cy: number; escala: number };
export type Painel = "nenhum" | "camadas" | "propriedades" | "atalhos" | "historico" | "ajuda";

export type Modos = {
  osnap: boolean; osnapModos: Set<ModoOsnap>; orto: boolean; polar: boolean; incrementoPolar: number;
  snapGrade: boolean; passoGrade: number; grade: boolean; otrack: boolean; entradaDinamica: boolean; osnap3d: boolean; ucsDinamico: boolean;
};

type Estado = {
  modelo: ModeloBim; tick: number; obraId: string | null; versaoServidor: number; sincronizando: boolean; erroSync: string | null;
  pavimentoId: string; vista: "2D" | "3D"; perfil: PerfilAtalhos; modos: Modos; camadasVisiveis: Set<Camada>; ocultos: Set<string>; isolados: Set<string> | null;
  selecao: Set<string>; maquina: EstadoMaquina; comando: ComandoAtivo | null; ultimoComando: Comando | null; log: LinhaLog[]; viewport: Viewport;
  cursor: Ponto2D | null; ultimoPonto: Ponto2D | null; painel: Painel; relatorio: RelatorioAuditoria | null; comandosServidor: string[]; paletaAberta: boolean;
  podeEditar: boolean;
  // ações
  carregar: (obraId: string | null, podeEditar: boolean) => Promise<void>;
  registrar: (tipo: LinhaLog["tipo"], texto: string) => void;
  iniciar: (texto: string) => void;
  entradaTexto: (texto: string) => Promise<void>;
  entradaPonto: (p: Ponto2D) => Promise<void>;
  entradaSelecao: (ids: string[], substituir?: boolean) => Promise<void>;
  opcao: (nome: string) => Promise<void>;
  cancelar: () => void;
  repetirUltimo: () => void;
  alternar: (m: keyof Omit<Modos, "osnapModos" | "incrementoPolar" | "passoGrade">) => void;
  alternarOsnap: (m: ModoOsnap) => void;
  setPerfil: (p: PerfilAtalhos) => void;
  setPavimento: (id: string) => void;
  setVista: (v: "2D" | "3D") => void;
  setViewport: (v: Partial<Viewport>) => void;
  setCursor: (p: Ponto2D | null) => void;
  setPainel: (p: Painel) => void;
  setPaleta: (aberta: boolean) => void;
  alternarCamada: (c: Camada) => void;
  selecionar: (ids: string[], modo?: "substituir" | "adicionar" | "alternar") => void;
  aplicar: (codigo: string, args: Record<string, unknown>, origem?: "CANVAS" | "CONSOLE_VI" | "IA") => Promise<boolean>;
  aplicarLote: (lote: { codigo: string; args: Record<string, unknown> }[], origem?: "CANVAS" | "CONSOLE_VI" | "IA") => Promise<boolean>;
  executarVI: (fonte: string) => Promise<void>;
  textoNatural: (texto: string) => Promise<{ aviso: string; comandos: ResultadoComando[] } | null>;
  desfazer: () => Promise<void>;
  refazer: () => void;
  enquadrarTudo: (largura: number, altura: number) => void;
  alterarPropriedade: (id: string, campo: string, valor: unknown) => Promise<boolean>;
};

let seqLog = 0;
const TODOS_OSNAP = new Set<ModoOsnap>(PRIORIDADE_OSNAP);
const CAMADAS: Camada[] = ["arquitetura", "estrutura", "fundacao", "desenho", "anotacao", "ia-rascunho"];
const CONSTRUTIVOS = new Set(["PAREDE", "PILAR", "VIGA", "LAJE", "PORTA", "JANELA", "SAPATA", "BLOCO", "RADIER", "ESTACA"]);

function resumoViolacoes(v: Violacao[]) { return v.map((x) => `${x.codigo}: ${x.mensagem}`).join(" · "); }

export const useMotor = create<Estado>((set, get) => {
  const log = (tipo: LinhaLog["tipo"], texto: string) => set((s) => ({ log: [...s.log.slice(-199), { id: ++seqLog, tipo, texto }] }));
  const bump = () => set((s) => ({ tick: s.tick + 1 }));
  const prompt = (c: ComandoAtivo) => {
    const passo = c.def.roteiro[c.passo];
    if (passo) log("prompt", `${c.def.nomePtBr} › ${passo.mensagem}${passo.opcoes?.length ? ` [${passo.opcoes.join("/")}]` : ""}`);
  };

  async function recarregarServidor(obraId: string) {
    const v = await apiGet<VisaoModelo>(`/obras/${obraId}/modelo`);
    const estado = deServidor(v);
    const atual = get();
    const modelo = new ModeloBim(estado);
    modelo.opcoesAuditoria = atual.modelo.opcoesAuditoria;
    const pav = modelo.pavimentos.some((p) => p.id === atual.pavimentoId) ? atual.pavimentoId : modelo.pavimentosOrdenados()[0]!.id;
    set({ modelo, versaoServidor: v.versao, pavimentoId: pav, selecao: new Set([...atual.selecao].filter((id) => modelo.elemento(id))) });
    bump();
  }

  /** aplica localmente (auditor) e sincroniza; devolve true se gravou */
  async function aplicarInterno(lote: { codigo: string; args: Record<string, unknown> }[], origem: "CANVAS" | "CONSOLE_VI" | "IA"): Promise<boolean> {
    const s = get();
    if (!s.podeEditar) { log("erro", "Seu papel nesta obra não permite editar o modelo (RF21)."); return false; }
    const modelo = s.modelo;
    const aplicados: string[] = [];
    set({ maquina: "ENVIANDO" });
    try {
      for (const c of lote) {
        const r = modelo.aplicar(c.codigo, c.args, { origem, pavimentoId: s.pavimentoId });
        aplicados.push(r.vi);
        set({ relatorio: r.relatorio });
        log("ok", `${r.vi}  → v${r.diff.versaoNova}${r.relatorio.correcoes.length ? ` · ${r.relatorio.correcoes.length} correção(ões) do auditor` : ""}${r.relatorio.laco2.avisos.length ? ` · aviso: ${resumoViolacoes(r.relatorio.laco2.avisos)}` : ""}`);
      }
    } catch (e) {
      for (let i = 0; i < aplicados.length; i++) modelo.desfazer();
      if (e instanceof ErroComandoVI) {
        const rel = e.relatorio;
        const viol = rel ? [...rel.laco1.violacoes, ...rel.laco2.violacoes] : [];
        log("erro", `${e.codigo} · ${e.message}${viol.length ? ` — ${resumoViolacoes(viol)}` : ""}`);
        set({ relatorio: rel ?? null });
      } else log("erro", mensagemDeErro(e));
      set({ maquina: "OCIOSO" }); bump();
      return false;
    }
    bump();
    if (s.obraId) {
      set({ sincronizando: true, erroSync: null });
      try {
        const corpo = lote.map((c, i) => ({ pavimentoId: s.pavimentoId, codigo: c.codigo, args: c.args, vi: aplicados[i], origem, versaoBase: get().versaoServidor }));
        const res = lote.length === 1 ? [await post<ResultadoComando>(`/obras/${s.obraId}/modelo/comandos`, corpo[0])] : await post<ResultadoComando[]>(`/obras/${s.obraId}/modelo/comandos/lote`, { comandos: corpo });
        set((st) => ({ comandosServidor: [...st.comandosServidor, ...res.map((r) => r.comandoId)] }));
        await recarregarServidor(s.obraId);
      } catch (e) {
        for (let i = 0; i < aplicados.length; i++) modelo.desfazer();
        if (e instanceof ErroApi && e.status === 409) { log("erro", "Conflito: o modelo avançou em outra sessão. Recarregando…"); await recarregarServidor(s.obraId).catch(() => undefined); }
        else if (e instanceof ErroApi && e.status === 422) log("erro", `Auditor do servidor reprovou: ${mensagemDeErro(e)}`);
        else log("erro", `Não foi possível gravar: ${mensagemDeErro(e)}`);
        set({ erroSync: mensagemDeErro(e), sincronizando: false, maquina: "OCIOSO" }); bump();
        return false;
      }
      set({ sincronizando: false });
    }
    set({ maquina: "OCIOSO" });
    return true;
  }

  /** monta os comandos VI a partir dos valores coletados pelo roteiro */
  function montar(c: ComandoAtivo, fechar: boolean): { codigo: string; args: Record<string, unknown> }[] {
    const v = c.valores, pts = c.pontos, sels = c.selecoes;
    const sel = sels[0] ?? [...get().selecao];
    switch (c.def.codigo) {
      case "LINHA": case "LINHA_DETALHE": case "LINHA_QUEBRA": { const todos = [v["p1"] as Ponto2D, ...pts]; const out = []; for (let i = 0; i < todos.length - 1; i++) out.push({ codigo: c.def.codigo === "LINHA_QUEBRA" ? "LINHA" : c.def.codigo, args: { p1: todos[i], p2: todos[i + 1], ...(c.def.codigo === "LINHA_QUEBRA" ? { rotulo: "quebra" } : {}) } }); if (fechar && todos.length > 2) out.push({ codigo: "LINHA", args: { p1: todos[todos.length - 1], p2: todos[0] } }); return out; }
      case "POLILINHA": case "SPLINE": return [{ codigo: c.def.codigo, args: { pontos: [v["p1"], ...pts], fechar } }];
      case "LAJE": return [{ codigo: "LAJE", args: { contorno: [v["p1"], ...pts], h: v["h"] } }];
      case "COPIAR": return pts.map((d) => ({ codigo: "COPIAR", args: { sel, base: v["base"], destino: d } }));
      case "DESLOCAR": return pts.map((lado) => ({ codigo: "DESLOCAR", args: { sel, dist: v["dist"], lado } }));
      case "APARAR": case "ESTENDER": return [{ codigo: c.def.codigo, args: { arestas: sels[0] ?? [], sel: sels[1] ?? [] } }];
      case "COTA_CONTINUA": { const ult = get().modelo.elementos.filter((e) => e.tipo === "COTA").pop(); let a = ult && "p2" in ult ? ult.p2 : null; const out = []; for (const p of pts) { if (a) out.push({ codigo: "COTA_LINEAR", args: { p1: a, p2: p, afast: 1 } }); a = p; } return out; }
      case "COTA_LINEAR": case "COTA_ALINHADA": { const p1 = v["p1"] as Ponto2D, pos = v["pos"] as Ponto2D; const afast = pos ? Math.hypot(pos.x - p1.x, pos.y - p1.y) : 1; return [{ codigo: c.def.codigo, args: { p1, p2: v["p2"], afast: Number(afast.toFixed(2)) } }]; }
      case "COTA_NIVEL": return [{ codigo: "COTA_NIVEL", args: { p: v["p"], afast: 0 } }];
      case "EIXO": return [{ codigo: "EIXO", args: { p1: v["p1"], p2: v["p2"], ...(v["nome"] ? { nome: v["nome"] } : {}) } }];
      case "PAVIMENTO": return [{ codigo: "PAVIMENTO", args: { nome: v["nome"], z: v["z"] } }];
      case "FUNDACAO": return [{ codigo: "FUNDACAO", args: { sel, tipo: v["tipo"] } }];
      case "ESPELHAR": return [{ codigo: "ESPELHAR", args: { sel, p1: v["p1"], p2: v["p2"], apagar: v["apagar"] === "S" } }];
      case "MATRIZ": return [{ codigo: "MATRIZ", args: { sel, nx: v["nx"], dx: v["dx"], ny: v["ny"], dy: v["dy"] } }];
      case "HACHURA": case "ETIQUETA": case "REGIAO": case "APAGAR": case "EXPLODIR": case "UNIR": return [{ codigo: c.def.codigo === "REGIAO" ? "HACHURA" : c.def.codigo, args: { sel, ...(c.def.codigo === "HACHURA" ? { padrao: v["padrao"] } : {}), ...(c.def.codigo === "REGIAO" ? { padrao: "REGIAO" } : {}) } }];
      case "QUEBRAR": { const e = get().modelo.elemento(sel[0] ?? ""); if (!e || !("p1" in e && "p2" in e)) return []; const a = projetarNoSegmento(v["p1"] as Ponto2D, e.p1, e.p2), b = projetarNoSegmento(v["p2"] as Ponto2D, e.p1, e.p2); const [t1, t2] = a.t < b.t ? [a, b] : [b, a]; return [{ codigo: "APAGAR", args: { sel: [e.id] } }, { codigo: "LINHA", args: { p1: e.p1, p2: t1.ponto } }, { codigo: "LINHA", args: { p1: t2.ponto, p2: e.p2 } }]; }
      case "CONCORDAR": case "CHANFRAR": { const m = get().modelo; const a = m.elemento(sels[0]?.[0] ?? ""), b = m.elemento(sels[1]?.[0] ?? ""); if (!a || !b || !("p1" in a && "p2" in a) || !("p1" in b && "p2" in b)) return []; const ip = intersecaoSegmentos(a.p1, a.p2, b.p1, b.p2, true); if (!ip) return []; const out = []; for (const e of [a, b]) { const campo = dist(ip, e.p1) < dist(ip, e.p2) ? "p1" : "p2"; out.push({ codigo: "ALTERAR", args: { id: e.id, props: { [campo]: ip } } }); } return out; }
      case "ALINHAR": { const m = get().modelo; const ref = m.elemento(sels[0]?.[0] ?? ""); const alvos = sels[1] ?? []; if (!ref) return []; const pr = m.pontoRepresentativo(ref); return alvos.map((id) => { const e = m.elemento(id)!; const pe = m.pontoRepresentativo(e); const vertical = "p1" in ref && "p2" in ref ? Math.abs(ref.p2.x - ref.p1.x) < Math.abs(ref.p2.y - ref.p1.y) : true; const d = vertical ? { x: pr.x - pe.x, y: 0 } : { x: 0, y: pr.y - pe.y }; return { codigo: "MOVER", args: { sel: [id], base: pe, destino: { x: pe.x + d.x, y: pe.y + d.y } } }; }); }
      case "COPIAR_PROPRIEDADES": { const m = get().modelo; const o = m.elemento(sels[0]?.[0] ?? ""); if (!o) return []; return (sels[1] ?? []).map((id) => ({ codigo: "ALTERAR", args: { id, props: { camada: o.camada, materialId: o.materialId } } })); }
      case "ALONGAR": return [{ codigo: "ALONGAR", args: { sel, delta: v["delta"] } }];
      case "ESTICAR": return [{ codigo: "ESTICAR", args: { sel, base: v["base"], destino: v["destino"] } }];
      case "MOVER": case "ROTACIONAR": case "ESCALAR": return [{ codigo: c.def.codigo, args: { sel, ...v } }];
      default: return [{ codigo: c.def.codigo, args: { ...v, ...(c.def.roteiro.some((p) => p.tipo === "SELECAO") ? { sel } : {}) } }];
    }
  }

  /** comandos que não geram ComandoVI (CA-10): visualização, modo, configuração */
  function executarImediato(def: Comando, valores: Record<string, unknown>, sel: string[]): boolean {
    const s = get();
    const toggle = (k: keyof Modos, nome: string) => { get().alternar(k as never); log("info", `${nome}: ${get().modos[k] ? "ligado" : "desligado"}`); };
    switch (def.codigo) {
      case "ZOOM_TUDO": set({ comando: { def, passo: 0, valores: { enquadrar: true }, pontos: [], selecoes: [] } }); return true; // o canvas lê e enquadra
      case "ZOOM": { const a = valores["p1"] as Ponto2D, b = valores["p2"] as Ponto2D; const w = Math.abs(b.x - a.x) || 1, h = Math.abs(b.y - a.y) || 1; set({ viewport: { cx: (a.x + b.x) / 2, cy: (a.y + b.y) / 2, escala: Math.min(s.viewport.escala * 4, 800 / Math.max(w, h)) } }); return true; }
      case "PAN": { const a = valores["p1"] as Ponto2D, b = valores["p2"] as Ponto2D; set({ viewport: { ...s.viewport, cx: s.viewport.cx - (b.x - a.x), cy: s.viewport.cy - (b.y - a.y) } }); return true; }
      case "REGENERAR": bump(); log("info", "Cena reconstruída a partir do modelo."); return true;
      case "OCULTAR": set({ ocultos: new Set([...s.ocultos, ...sel]), selecao: new Set() }); log("info", `${sel.length} elemento(s) oculto(s) nesta sessão.`); return true;
      case "ISOLAR": set({ isolados: new Set(sel), selecao: new Set() }); log("info", `${sel.length} elemento(s) isolado(s). EU reexibe.`); return true;
      case "REEXIBIR": case "REEXIBIR_CATEGORIA": set({ ocultos: new Set(), isolados: null, camadasVisiveis: new Set(CAMADAS) }); log("info", "Todos os elementos visíveis."); return true;
      case "VISIBILIDADE": case "CAMADAS": set({ painel: "camadas" }); return true;
      case "PROPRIEDADES": set({ painel: "propriedades" }); return true;
      case "HISTORICO": set({ painel: s.painel === "historico" ? "nenhum" : "historico" }); return true;
      case "AJUDA": set({ painel: "ajuda" }); return true;
      case "ATALHOS": set({ painel: "atalhos" }); return true;
      case "UNIDADES": log("info", "Unidades do projeto: metros, kN, MPa (SI). Conversão só na exibição."); return true;
      case "OSNAP": toggle("osnap", "Osnap (F3)"); return true;
      case "OSNAP3D": toggle("osnap3d", "Osnap 3D (F4)"); return true;
      case "ORTO": toggle("orto", "Orto (F8)"); return true;
      case "POLAR": toggle("polar", "Polar (F10)"); return true;
      case "SNAP_GRADE": toggle("snapGrade", "Snap de grade (F9)"); return true;
      case "GRADE": toggle("grade", "Grade (F7)"); return true;
      case "OTRACK": toggle("otrack", "Rastreamento de objetos"); return true;
      case "ENTRADA_DINAMICA": toggle("entradaDinamica", "Entrada dinâmica"); return true;
      case "UCS_DINAMICO": toggle("ucsDinamico", "UCS dinâmico (F6)"); return true;
      case "PLANO_TRABALHO": set({ vista: s.vista === "2D" ? "3D" : "2D" }); log("info", `Plano de trabalho: ${get().vista === "2D" ? "Topo (planta)" : "Perspectiva 3D"}.`); return true;
      case "DESFAZER": void get().desfazer(); return true;
      case "REFAZER": get().refazer(); return true;
      case "ESTILO_COTA": case "BLOCO": case "INSERIR": case "LOCALIZAR": log("info", `${def.nomePtBr}: age na prancha (RF14). Abra a tela Pranchas desta obra.`); return true;
      default: return false;
    }
  }

  async function avancar(c: ComandoAtivo) {
    const def = c.def;
    const passoAtual = def.roteiro[c.passo];
    const fim = c.passo >= def.roteiro.length || (passoAtual?.repetir && c.valores["__fechar"] !== undefined);
    if (!fim) { set({ comando: { ...c, erro: undefined }, maquina: "AGUARDANDO_ENTRADA" }); prompt(c); return; }
    const fechar = c.valores["__fechar"] === "Fechar";
    set({ comando: null, ultimoComando: def });
    const sel = c.selecoes[0] ?? [...get().selecao];
    if (def.categoria === "VISUALIZACAO" || def.categoria === "MODO" || def.categoria === "CONFIG" || def.codigo === "DESFAZER" || def.codigo === "REFAZER") { executarImediato(def, c.valores, sel); set({ maquina: "OCIOSO" }); if (def.codigo !== "ZOOM_TUDO") set({ comando: null }); return; }
    // regras de domínio (CA-08)
    const m = get().modelo;
    if ((def.codigo === "ESCALAR" || def.codigo === "EXPLODIR") && sel.some((id) => CONSTRUTIVOS.has(m.elemento(id)?.tipo ?? ""))) { log("erro", `${def.nomePtBr} não se aplica a elementos construtivos: use as propriedades (H5).`); set({ maquina: "OCIOSO" }); return; }
    if (def.codigo === "APAGAR") { const n = m.elementos.filter((e) => (e.tipo === "PORTA" || e.tipo === "JANELA") && sel.includes(e.paredeId)).length; if (n) log("info", `Atenção: ${n} abertura(s) hospedada(s) serão apagadas junto com a parede.`); }
    const lote = montar(c, fechar);
    if (!lote.length) { log("erro", `${def.nomePtBr}: nada a fazer com essa entrada.`); set({ maquina: "OCIOSO" }); return; }
    await aplicarInterno(lote, "CANVAS");
    if (["APAGAR", "EXPLODIR", "UNIR", "QUEBRAR"].includes(def.codigo)) set({ selecao: new Set() });
  }

  return {
    modelo: new ModeloBim(), tick: 0, obraId: null, versaoServidor: 0, sincronizando: false, erroSync: null,
    pavimentoId: "pav_terreo", vista: "2D", perfil: "CIVILCONSTRUCTOR",
    modos: { osnap: true, osnapModos: new Set(TODOS_OSNAP), orto: false, polar: false, incrementoPolar: 15, snapGrade: false, passoGrade: 0.5, grade: true, otrack: false, entradaDinamica: true, osnap3d: false, ucsDinamico: false },
    camadasVisiveis: new Set(CAMADAS), ocultos: new Set(), isolados: null, selecao: new Set(), maquina: "OCIOSO", comando: null, ultimoComando: null,
    log: [{ id: 0, tipo: "info", texto: "Motor pronto. Digite um comando (ex.: WA parede, CL pilar, MALHA) ou Ctrl+K para a paleta." }],
    viewport: { cx: 10, cy: 8, escala: 40 }, cursor: null, ultimoPonto: null, painel: "nenhum", relatorio: null, comandosServidor: [], paletaAberta: false, podeEditar: true,

    registrar: log,
    carregar: async (obraId, podeEditar) => {
      set({ obraId, podeEditar });
      if (!obraId) { const m = new ModeloBim(); set({ modelo: m, pavimentoId: m.pavimentos[0]!.id }); bump(); return; }
      try { await recarregarServidor(obraId); log("info", `Modelo da obra carregado (v${get().versaoServidor}).`); }
      catch (e) { log("erro", `Não foi possível carregar o modelo: ${mensagemDeErro(e)}`); set({ erroSync: mensagemDeErro(e) }); }
    },
    iniciar: (texto) => {
      const s = get();
      const t = texto.trim();
      if (!t) { get().repetirUltimo(); return; }
      if (/^VI\s*\(/i.test(t)) { void get().executarVI(t); return; }
      const def = resolverComando(t, s.perfil);
      if (!def) { const sug = buscarComandos(t, s.perfil, 3).map((c) => `${(c.aliases[s.perfil] ?? [])[0] ?? c.codigo} (${c.nomePtBr})`); log("erro", `Comando desconhecido: "${t}".${sug.length ? ` Você quis dizer ${sug.join(", ")}?` : ""}`); return; }
      log("entrada", t.toUpperCase());
      const alteraModelo = ["DESENHO", "MODIFICACAO", "ANOTACAO"].includes(def.categoria) && !["ESTILO_COTA", "BLOCO", "INSERIR", "LOCALIZAR"].includes(def.codigo);
      if (alteraModelo && !s.podeEditar) { log("erro", `${def.nomePtBr}: seu papel nesta obra permite só visualizar o modelo (RF21).`); return; }
      if (def.papeis && !s.podeEditar) { log("erro", `${def.nomePtBr} exige papel ${def.papeis.join(" ou ")} nesta obra.`); return; }
      const c: ComandoAtivo = { def, passo: 0, valores: {}, pontos: [], selecoes: [] };
      // seleção prévia (substantivo-verbo, PICKFIRST): pula o passo de seleção
      if (def.roteiro[0]?.tipo === "SELECAO" && s.selecao.size && def.roteiro.filter((p) => p.tipo === "SELECAO").length === 1) { c.selecoes.push([...s.selecao]); c.passo = 1; }
      set({ comando: c, maquina: "COMANDO_ATIVO" });
      void avancar(c);
    },
    entradaTexto: async (texto) => {
      const s = get(); const c = s.comando;
      if (!c) { get().iniciar(texto); return; }
      const passo = c.def.roteiro[c.passo]; if (!passo) return;
      const t = texto.trim();
      log("entrada", t || "⏎");
      if (passo.opcoes?.some((o) => o.toUpperCase() === t.toUpperCase() || (t.length === 1 && o[0]!.toUpperCase() === t.toUpperCase()))) { await get().opcao(passo.opcoes.find((o) => o.toUpperCase() === t.toUpperCase() || o[0]!.toUpperCase() === t.toUpperCase())!); return; }
      if (passo.tipo === "PONTO") {
        if (t === "" && passo.repetir) { await get().opcao(passo.opcoes?.includes("Fechar") ? "Sair" : "Sair"); return; }
        const r = interpretarPonto(t, s.ultimoPonto);
        if (r.tipo === "ponto") { await get().entradaPonto(r.ponto); return; }
        set({ comando: { ...c, erro: r.tipo === "erro" ? r.mensagem : "Entrada inválida." } }); log("erro", r.tipo === "erro" ? r.mensagem : "Entrada inválida."); return;
      }
      if (passo.tipo === "SELECAO") {
        if (t === "" ) { const ids = [...s.selecao]; if (!ids.length) { set({ comando: { ...c, erro: "Selecione ao menos um objeto." } }); return; } await get().entradaSelecao(ids, true); return; }
        const ids = t.split(/[\s,]+/).map((r) => s.modelo.elementos.find((e) => e.id === r || e.rotulo === r)?.id).filter((x): x is string => !!x);
        if (!ids.length) { set({ comando: { ...c, erro: "Nenhum elemento com esse id/rótulo." } }); return; }
        await get().entradaSelecao(ids, true); return;
      }
      const r = interpretarValor(t, passo.opcoes, passo.padrao, passo.tipo === "ANGULO" ? "ANGULO" : passo.tipo === "TEXTO" ? "TEXTO" : passo.tipo === "OPCAO" ? "OPCAO" : "VALOR");
      if (r.tipo === "erro") { set({ comando: { ...c, erro: r.mensagem } }); log("erro", r.mensagem); return; }
      if (passo.opcional && t === "" && passo.padrao === "") { const nc = { ...c, passo: c.passo + 1 }; await avancar(nc); return; }
      const nc = { ...c, valores: { ...c.valores, [passo.nome]: r.tipo === "ponto" ? r.ponto : r.valor }, passo: c.passo + 1 };
      await avancar(nc);
    },
    entradaPonto: async (p) => {
      const s = get(); const c = s.comando; if (!c) return;
      const passo = c.def.roteiro[c.passo]; if (!passo || passo.tipo !== "PONTO") return;
      set({ ultimoPonto: p });
      if (passo.repetir) { const nc = { ...c, pontos: [...c.pontos, p], erro: undefined }; set({ comando: nc }); log("entrada", `${p.x.toFixed(2)},${p.y.toFixed(2)}`); prompt(nc); return; }
      const nc = { ...c, valores: { ...c.valores, [passo.nome]: p }, passo: c.passo + 1 };
      log("entrada", `${p.x.toFixed(2)},${p.y.toFixed(2)}`);
      await avancar(nc);
    },
    entradaSelecao: async (ids, substituir) => {
      const s = get(); const c = s.comando;
      if (!c) { get().selecionar(ids, substituir ? "substituir" : "adicionar"); return; }
      const passo = c.def.roteiro[c.passo]; if (!passo || passo.tipo !== "SELECAO") return;
      const nc = { ...c, selecoes: [...c.selecoes, ids], passo: c.passo + 1 };
      log("entrada", `${ids.length} selecionado(s)`);
      await avancar(nc);
    },
    opcao: async (nome) => {
      const c = get().comando; if (!c) return;
      if (nome === "Desfazer") {
        if (c.pontos.length) { const nc = { ...c, pontos: c.pontos.slice(0, -1) }; set({ comando: nc, ultimoPonto: nc.pontos[nc.pontos.length - 1] ?? (nc.valores["p1"] as Ponto2D) ?? null }); log("info", "Voltou um ponto."); prompt(nc); return; }
        if (c.passo > 0) { const prev = c.def.roteiro[c.passo - 1]!; const valores = { ...c.valores }; delete valores[prev.nome]; const nc = { ...c, passo: c.passo - 1, valores }; set({ comando: nc }); prompt(nc); return; }
        get().cancelar(); return;
      }
      if (nome === "Fechar" || nome === "Sair") { await avancar({ ...c, valores: { ...c.valores, __fechar: nome } }); return; }
      const passo = c.def.roteiro[c.passo];
      if (passo) await avancar({ ...c, valores: { ...c.valores, [passo.nome]: nome }, passo: c.passo + 1 });
    },
    cancelar: () => { const c = get().comando; if (c) log("info", `*Cancelado* ${c.def.nomePtBr}`); set({ comando: null, maquina: "OCIOSO" }); },
    repetirUltimo: () => { const u = get().ultimoComando; if (u) get().iniciar((u.aliases[get().perfil] ?? [])[0] ?? u.codigo); },
    alternar: (m) => set((s) => {
      const modos = { ...s.modos, [m]: !s.modos[m] } as Modos;
      if (m === "orto" && modos.orto) modos.polar = false;
      if (m === "polar" && modos.polar) modos.orto = false;
      return { modos };
    }),
    alternarOsnap: (m) => set((s) => { const n = new Set(s.modos.osnapModos); if (n.has(m)) n.delete(m); else n.add(m); return { modos: { ...s.modos, osnapModos: n } }; }),
    setPerfil: (perfil) => { set({ perfil }); log("info", `Perfil de atalhos: ${perfil}.`); },
    setPavimento: (pavimentoId) => set({ pavimentoId, selecao: new Set() }),
    setVista: (vista) => set({ vista }),
    setViewport: (v) => set((s) => ({ viewport: { ...s.viewport, ...v } })),
    setCursor: (cursor) => set({ cursor }),
    setPainel: (painel) => set({ painel }),
    setPaleta: (paletaAberta) => set({ paletaAberta }),
    alternarCamada: (c) => set((s) => { const n = new Set(s.camadasVisiveis); if (n.has(c)) n.delete(c); else n.add(c); return { camadasVisiveis: n }; }),
    selecionar: (ids, modo = "substituir") => set((s) => {
      const n = modo === "substituir" ? new Set(ids) : new Set(s.selecao);
      if (modo === "adicionar") ids.forEach((i) => n.add(i));
      if (modo === "alternar") ids.forEach((i) => (n.has(i) ? n.delete(i) : n.add(i)));
      return { selecao: n, maquina: n.size && !s.comando ? "SELECIONANDO" : s.comando ? s.maquina : "OCIOSO" };
    }),
    aplicar: (codigo, args, origem = "CANVAS") => aplicarInterno([{ codigo, args }], origem),
    aplicarLote: (lote, origem = "CANVAS") => aplicarInterno(lote, origem),
    executarVI: async (fonte) => {
      const prog = parseVI(fonte);
      if (prog.erros.length) { prog.erros.forEach((e) => log("erro", `${e.codigo} linha ${e.linha}:${e.coluna} · ${e.mensagem}`)); return; }
      if (!prog.comandos.length) return;
      await aplicarInterno(prog.comandos.map((c) => ({ codigo: c.codigo, args: c.args as Record<string, unknown> })), "CONSOLE_VI");
    },
    textoNatural: async (texto) => {
      const s = get();
      if (!s.obraId) { log("erro", "Texto natural precisa de uma obra (a IA roda no servidor)."); return null; }
      set({ sincronizando: true });
      try {
        const r = await post<{ geradoPorIa: boolean; aviso: string; comandos: ResultadoComando[] }>(`/obras/${s.obraId}/modelo/texto-natural`, { pavimentoId: s.pavimentoId, texto });
        log("ia", `${r.aviso} ${r.comandos.map((c) => c.vi).join(" ; ")}`);
        await recarregarServidor(s.obraId);
        return r;
      } catch (e) { log("erro", `IA: ${mensagemDeErro(e)}`); return null; } finally { set({ sincronizando: false }); }
    },
    desfazer: async () => {
      const s = get();
      if (s.obraId && s.comandosServidor.length) {
        const id = s.comandosServidor[s.comandosServidor.length - 1]!;
        try { await post(`/obras/${s.obraId}/modelo/comandos/${id}/desfazer`); set({ comandosServidor: s.comandosServidor.slice(0, -1) }); await recarregarServidor(s.obraId); log("ok", "Desfeito no servidor (comando inverso)."); }
        catch (e) { log("erro", `Desfazer: ${mensagemDeErro(e)}`); }
        return;
      }
      const d = s.modelo.desfazer();
      log(d ? "ok" : "info", d ? `Desfeito → v${d.versaoNova}` : "Nada a desfazer.");
      set({ selecao: new Set() }); bump();
    },
    refazer: () => { const d = get().modelo.refazer(); log(d ? "ok" : "info", d ? `Refeito → v${d.versaoNova}` : "Nada a refazer."); bump(); },
    enquadrarTudo: (largura, altura) => {
      const s = get(); const m = s.modelo;
      const pts: Ponto2D[] = [];
      for (const e of m.elementosDo(s.pavimentoId)) { const c = caixaElemento(e, m); pts.push(c.min, c.max); }
      for (const ex of m.eixos) pts.push(ex.direcao === "X" ? { x: ex.posicao, y: ex.inicio } : { x: ex.inicio, y: ex.posicao }, ex.direcao === "X" ? { x: ex.posicao, y: ex.fim } : { x: ex.fim, y: ex.posicao });
      if (!pts.length) { set({ viewport: { cx: 10, cy: 8, escala: 40 }, comando: null }); return; }
      const minx = Math.min(...pts.map((p) => p.x)), maxx = Math.max(...pts.map((p) => p.x)), miny = Math.min(...pts.map((p) => p.y)), maxy = Math.max(...pts.map((p) => p.y));
      const w = Math.max(maxx - minx, 2), h = Math.max(maxy - miny, 2);
      set({ viewport: { cx: (minx + maxx) / 2, cy: (miny + maxy) / 2, escala: Math.max(2, Math.min(400, 0.85 * Math.min(largura / w, altura / h))) }, comando: null });
    },
    alterarPropriedade: (id, campo, valor) => aplicarInterno([{ codigo: "ALTERAR", args: { id, props: { [campo]: valor } } }], "CANVAS"),
  };
});

/** elementos visíveis no pavimento ativo (camadas, ocultos, isolados) */
export function elementosVisiveis(s: Pick<Estado, "modelo" | "pavimentoId" | "camadasVisiveis" | "ocultos" | "isolados">): Elemento[] {
  return s.modelo.elementosDo(s.pavimentoId).filter((e) => s.camadasVisiveis.has(e.camada) && !s.ocultos.has(e.id) && (!s.isolados || s.isolados.has(e.id)));
}

export const formatarComandoVI = formatarVI;
export const catalogo = CATALOGO;
export { restringir };
