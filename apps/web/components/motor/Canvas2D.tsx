"use client";
/**
 * Vista de planta (RF01): canvas 2D com grade, eixos com bolhas recuadas (RF03), elementos por camada,
 * seleção substantivo-verbo (clique, janela esquerda→direita, cruzamento direita→esquerda), Osnap com marcador
 * (cor + forma + texto), Orto/Polar, prévia fantasma do comando ativo e entrada dinâmica junto ao cursor.
 */
import { useCallback, useEffect, useRef, useState } from "react";
import { cantosObjeto, dist, itemObjeto, percursoConexao, pontoDentroPoligono, posicaoConector, type Elemento, type Ponto2D } from "@cca/notacao-vi";
import { COR_SISTEMA } from "@/lib/motor/cena";
import { elementosVisiveis, useMotor } from "@/lib/motor/estado";
import { restringir } from "@/lib/motor/entrada";
import { caixaElemento, candidatos, distanciaAoElemento, melhor, snapGrade, type Candidato } from "@/lib/motor/snap";

type Cores = { bg: string; grade: string; gradeForte: string; eixo: string; sel: string; snap: string; texto: string; texto3: string; brand: string; ia: string; fund: string; anot: string; prev: string };

function lerCores(el: HTMLElement): Cores {
  const cs = getComputedStyle(el);
  const v = (n: string, pad: string) => cs.getPropertyValue(n).trim() || pad;
  return { bg: v("--canvas-bg", "#fff"), grade: v("--canvas-grid", "#e5e7eb"), gradeForte: v("--line", "#6b7280"), eixo: v("--canvas-eixo", "#6b7280"), sel: v("--canvas-sel", "#1d4ed8"), snap: v("--canvas-snap", "#c2410c"), texto: v("--text", "#111827"), texto3: v("--text-3", "#4b5563"), brand: v("--brand-text", "#1e40af"), ia: v("--ai-draft-line", "#a855f7"), fund: "#7c4a1e", anot: v("--success", "#166534"), prev: v("--brand", "#1d4ed8") };
}

export function Canvas2D() {
  const ref = useRef<HTMLCanvasElement>(null);
  const caixaRef = useRef<HTMLDivElement>(null);
  const [tam, setTam] = useState({ w: 800, h: 600 });
  const arrasto = useRef<{ tipo: "pan" | "janela"; inicioTela: Ponto2D; inicioMundo: Ponto2D; vp: { cx: number; cy: number } } | null>(null);
  const espaco = useRef(false);
  const [snapAtual, setSnapAtual] = useState<Candidato | null>(null);
  const [janela, setJanela] = useState<{ a: Ponto2D; b: Ponto2D } | null>(null);
  const [cursorTela, setCursorTela] = useState<Ponto2D | null>(null);
  const [hover, setHover] = useState<string | null>(null);

  const s = useMotor();
  const { viewport, modelo, tick, pavimentoId, comando, selecao, modos, cursor } = s;

  // tamanho
  useEffect(() => {
    const el = caixaRef.current; if (!el) return;
    const ro = new ResizeObserver(() => setTam({ w: Math.max(200, el.clientWidth), h: Math.max(200, el.clientHeight) }));
    ro.observe(el); return () => ro.disconnect();
  }, []);

  const paraTela = useCallback((p: Ponto2D): Ponto2D => ({ x: (p.x - viewport.cx) * viewport.escala + tam.w / 2, y: tam.h / 2 - (p.y - viewport.cy) * viewport.escala }), [viewport, tam]);
  const paraMundo = useCallback((p: Ponto2D): Ponto2D => ({ x: (p.x - tam.w / 2) / viewport.escala + viewport.cx, y: (tam.h / 2 - p.y) / viewport.escala + viewport.cy }), [viewport, tam]);

  // ZOOM_TUDO pedido pelo comando
  useEffect(() => { if (comando?.valores["enquadrar"]) s.enquadrarTudo(tam.w, tam.h); }, [comando, s, tam]);

  const passoAtual = comando?.def.roteiro[comando.passo];
  const aguardaPonto = passoAtual?.tipo === "PONTO";
  const aguardaSelecao = passoAtual?.tipo === "SELECAO";
  const base: Ponto2D | null = comando ? (comando.pontos[comando.pontos.length - 1] ?? (comando.valores["p1"] as Ponto2D) ?? (comando.valores["base"] as Ponto2D) ?? (comando.valores["centro"] as Ponto2D) ?? null) : null;

  /** ponto efetivo do cursor com snap/orto/polar */
  const resolverCursor = useCallback((tela: Ponto2D): { p: Ponto2D; snap: Candidato | null } => {
    let p = paraMundo(tela);
    const raio = 10 / viewport.escala;
    let snap: Candidato | null = null;
    if (modos.osnap) snap = melhor(candidatos(modelo, pavimentoId, p, raio, modos.osnapModos, s.ocultos, s.ultimoPonto), p);
    if (snap) p = snap.p;
    else {
      if (modos.snapGrade) p = snapGrade(p, modos.passoGrade);
      p = restringir(p, aguardaPonto ? base : null, modos.orto ? "orto" : modos.polar ? "polar" : "nenhum", modos.incrementoPolar);
      p = { x: Math.round(p.x * 1000) / 1000, y: Math.round(p.y * 1000) / 1000 };
    }
    return { p, snap };
  }, [paraMundo, viewport.escala, modos, modelo, pavimentoId, s.ocultos, s.ultimoPonto, aguardaPonto, base]);

  const elementoEm = useCallback((p: Ponto2D): Elemento | null => {
    const tol = 6 / viewport.escala;
    let melhorE: Elemento | null = null, md = Infinity;
    for (const e of elementosVisiveis(s)) { const d = distanciaAoElemento(e, p, modelo); if (d <= tol && d < md) { md = d; melhorE = e; } }
    return melhorE;
  }, [s, modelo, viewport.escala]);

  // ---------------------------------------------------------------- desenho
  useEffect(() => {
    const cv = ref.current; if (!cv) return;
    const dpr = window.devicePixelRatio || 1;
    cv.width = tam.w * dpr; cv.height = tam.h * dpr;
    const ctx = cv.getContext("2d"); if (!ctx) return;
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    const C = lerCores(cv);
    ctx.fillStyle = C.bg; ctx.fillRect(0, 0, tam.w, tam.h);
    const T = paraTela;

    // grade
    if (modos.grade) {
      const passo = viewport.escala > 60 ? 0.5 : viewport.escala > 15 ? 1 : viewport.escala > 4 ? 5 : 10;
      const m0 = paraMundo({ x: 0, y: tam.h }), m1 = paraMundo({ x: tam.w, y: 0 });
      ctx.lineWidth = 1;
      for (let x = Math.floor(m0.x / passo) * passo; x <= m1.x; x += passo) { const forte = Math.abs(x % (passo * 5)) < 1e-6; ctx.strokeStyle = forte ? C.grade : C.grade; ctx.globalAlpha = forte ? 1 : 0.5; const sx = T({ x, y: 0 }).x; ctx.beginPath(); ctx.moveTo(sx, 0); ctx.lineTo(sx, tam.h); ctx.stroke(); }
      for (let y = Math.floor(m0.y / passo) * passo; y <= m1.y; y += passo) { const forte = Math.abs(y % (passo * 5)) < 1e-6; ctx.globalAlpha = forte ? 1 : 0.5; const sy = T({ x: 0, y }).y; ctx.beginPath(); ctx.moveTo(0, sy); ctx.lineTo(tam.w, sy); ctx.stroke(); }
      ctx.globalAlpha = 1;
      // origem
      const o = T({ x: 0, y: 0 }); ctx.strokeStyle = C.gradeForte; ctx.beginPath(); ctx.moveTo(o.x - 8, o.y); ctx.lineTo(o.x + 8, o.y); ctx.moveTo(o.x, o.y - 8); ctx.lineTo(o.x, o.y + 8); ctx.stroke();
    }

    // eixos (RF03) com bolhas recuadas
    ctx.font = "12px var(--font-mono), monospace"; ctx.textAlign = "center"; ctx.textBaseline = "middle";
    for (const ex of modelo.eixos) {
      const a = ex.direcao === "X" ? { x: ex.posicao, y: ex.inicio } : { x: ex.inicio, y: ex.posicao };
      const b = ex.direcao === "X" ? { x: ex.posicao, y: ex.fim } : { x: ex.fim, y: ex.posicao };
      const ta = T(a), tb = T(b);
      ctx.strokeStyle = C.eixo; ctx.setLineDash([10, 4, 2, 4]); ctx.lineWidth = 1; ctx.beginPath(); ctx.moveTo(ta.x, ta.y); ctx.lineTo(tb.x, tb.y); ctx.stroke(); ctx.setLineDash([]);
      const bolha = ex.direcao === "X" ? { x: ex.posicao, y: ex.fim + ex.recuoBolha } : { x: ex.inicio - ex.recuoBolha, y: ex.posicao };
      const tbo = T(bolha); const r = 11;
      ctx.beginPath(); ctx.moveTo(tb.x, tb.y); ctx.lineTo(tbo.x, tbo.y); ctx.stroke();
      ctx.fillStyle = C.bg; ctx.beginPath(); ctx.arc(tbo.x, tbo.y, r, 0, Math.PI * 2); ctx.fill(); ctx.stroke();
      ctx.fillStyle = C.texto; ctx.fillText(ex.nome, tbo.x, tbo.y + 0.5);
    }

    const corCamada = (e: Elemento) => e.camada === "estrutura" ? C.brand : e.camada === "fundacao" ? C.fund : e.camada === "anotacao" ? C.anot : e.camada === "ia-rascunho" ? C.ia : e.camada === "desenho" ? C.texto3 : C.texto;
    const paredeRect = (p1: Ponto2D, p2: Ponto2D, esp: number) => { const v = { x: p2.x - p1.x, y: p2.y - p1.y }; const l = Math.hypot(v.x, v.y) || 1; const n = { x: (-v.y / l) * esp / 2, y: (v.x / l) * esp / 2 }; return [{ x: p1.x + n.x, y: p1.y + n.y }, { x: p2.x + n.x, y: p2.y + n.y }, { x: p2.x - n.x, y: p2.y - n.y }, { x: p1.x - n.x, y: p1.y - n.y }]; };
    const poly = (pts: Ponto2D[], fechar = true) => { ctx.beginPath(); pts.forEach((p, i) => { const t = T(p); if (i) ctx.lineTo(t.x, t.y); else ctx.moveTo(t.x, t.y); }); if (fechar) ctx.closePath(); };
    const hachura = (pts: Ponto2D[], cor: string) => { ctx.save(); poly(pts); ctx.clip(); ctx.strokeStyle = cor; ctx.globalAlpha = 0.35; ctx.lineWidth = 1; const c = pts.reduce((a, p) => ({ x: a.x + p.x / pts.length, y: a.y + p.y / pts.length }), { x: 0, y: 0 }); const ct = T(c); const R = Math.max(...pts.map((p) => dist(T(p), ct))) + 5; for (let d = -R; d <= R; d += 6) { ctx.beginPath(); ctx.moveTo(ct.x - R + d, ct.y - R); ctx.lineTo(ct.x + R + d, ct.y + R); ctx.stroke(); } ctx.restore(); };

    const desenharElemento = (e: Elemento, fantasma = false, deslocamento?: Ponto2D) => {
      const D = (p: Ponto2D) => (deslocamento ? { x: p.x + deslocamento.x, y: p.y + deslocamento.y } : p);
      const cor = fantasma ? C.prev : corCamada(e);
      const selecionado = selecao.has(e.id) || hover === e.id;
      ctx.strokeStyle = selecionado && !fantasma ? C.sel : cor; ctx.fillStyle = cor; ctx.lineWidth = selecionado ? 2.5 : e.tipo === "PAREDE" || e.tipo === "PILAR" || e.tipo === "VIGA" ? 1.5 : 1;
      ctx.setLineDash(fantasma ? [6, 4] : e.camada === "ia-rascunho" ? [4, 3] : e.rotulo === "construcao" ? [2, 4] : []);
      ctx.globalAlpha = fantasma ? 0.7 : 1;
      switch (e.tipo) {
        case "PAREDE": { const r = paredeRect(D(e.p1), D(e.p2), e.espessura); poly(r); ctx.globalAlpha = fantasma ? 0.15 : 0.12; ctx.fill(); ctx.globalAlpha = fantasma ? 0.7 : 1; ctx.stroke(); break; }
        case "VIGA": { const r = paredeRect(D(e.p1), D(e.p2), e.b); poly(r); ctx.stroke(); const a = T(D(e.p1)), b = T(D(e.p2)); ctx.setLineDash([3, 3]); ctx.beginPath(); ctx.moveTo(a.x, a.y); ctx.lineTo(b.x, b.y); ctx.stroke(); ctx.setLineDash([]); break; }
        case "PILAR": { const c = D(e.centro); if (e.secao === "CIRCULAR") { const t = T(c); ctx.beginPath(); ctx.arc(t.x, t.y, (e.b / 2) * viewport.escala, 0, Math.PI * 2); ctx.globalAlpha = 0.25; ctx.fill(); ctx.globalAlpha = 1; ctx.stroke(); } else { const b = e.b / 2, h = e.h / 2, ang = ((e.rotacao ?? 0) * Math.PI) / 180, ca = Math.cos(ang), sa = Math.sin(ang); const r = [[-b, -h], [b, -h], [b, h], [-b, h]].map(([u, v]) => ({ x: c.x + u! * ca - v! * sa, y: c.y + u! * sa + v! * ca })); poly(r); ctx.globalAlpha = fantasma ? 0.2 : 0.3; ctx.fill(); ctx.globalAlpha = fantasma ? 0.7 : 1; ctx.stroke(); hachura(r, cor); } if (e.rotulo && viewport.escala > 12) { const t = T(c); ctx.fillStyle = C.texto; ctx.fillText(e.rotulo, t.x, t.y - (e.h / 2) * viewport.escala - 8); } break; }
        case "LAJE": poly(e.contorno.map(D)); ctx.globalAlpha = 0.08; ctx.fill(); ctx.globalAlpha = 1; ctx.setLineDash([8, 4]); ctx.stroke(); ctx.setLineDash([]); break;
        case "HACHURA": hachura(e.contorno.map(D), cor); poly(e.contorno.map(D)); ctx.stroke(); break;
        case "LINHA": case "PLANO_REF": case "CONTRAVENTAMENTO": { const a = T(D(e.p1)), b = T(D(e.p2)); if (e.tipo === "PLANO_REF") ctx.setLineDash([12, 6]); ctx.beginPath(); ctx.moveTo(a.x, a.y); ctx.lineTo(b.x, b.y); ctx.stroke(); break; }
        case "POLILINHA": poly(e.pontos.map(D), e.fechada); ctx.stroke(); break;
        case "RETANGULO": { const a = D(e.p1), b = D(e.p2); poly([a, { x: b.x, y: a.y }, b, { x: a.x, y: b.y }]); ctx.stroke(); break; }
        case "CIRCULO": { const t = T(D(e.centro)); ctx.beginPath(); ctx.arc(t.x, t.y, e.raio * viewport.escala, 0, Math.PI * 2); ctx.stroke(); break; }
        case "ARCO": { const a = T(D(e.p1)), m = T(D(e.p2)), b = T(D(e.p3)); ctx.beginPath(); ctx.moveTo(a.x, a.y); ctx.quadraticCurveTo(2 * m.x - (a.x + b.x) / 2, 2 * m.y - (a.y + b.y) / 2, b.x, b.y); ctx.stroke(); break; }
        case "PONTO": { const t = T(D(e.p)); ctx.beginPath(); ctx.moveTo(t.x - 5, t.y); ctx.lineTo(t.x + 5, t.y); ctx.moveTo(t.x, t.y - 5); ctx.lineTo(t.x, t.y + 5); ctx.stroke(); if (e.rotulo) { ctx.fillStyle = C.texto; ctx.fillText(e.rotulo, t.x, t.y - 10); } break; }
        case "AMBIENTE": { const t = T(D(e.ponto)); ctx.fillStyle = C.texto; ctx.font = "bold 12px var(--font-sans), sans-serif"; ctx.fillText(e.nome, t.x, t.y); ctx.font = "12px var(--font-mono), monospace"; break; }
        case "PORTA": case "JANELA": { const w = modelo.elemento(e.paredeId); if (!w || w.tipo !== "PAREDE") break; const v = { x: w.p2.x - w.p1.x, y: w.p2.y - w.p1.y }; const l = Math.hypot(v.x, v.y) || 1; const u = { x: v.x / l, y: v.y / l }; const c = { x: w.p1.x + v.x * e.posicao, y: w.p1.y + v.y * e.posicao }; const n = { x: -u.y, y: u.x }; const h = e.largura / 2, esp = w.espessura / 2 + 0.02; const r = [{ x: c.x - u.x * h + n.x * esp, y: c.y - u.y * h + n.y * esp }, { x: c.x + u.x * h + n.x * esp, y: c.y + u.y * h + n.y * esp }, { x: c.x + u.x * h - n.x * esp, y: c.y + u.y * h - n.y * esp }, { x: c.x - u.x * h - n.x * esp, y: c.y - u.y * h - n.y * esp }].map(D); ctx.fillStyle = C.bg; poly(r); ctx.fill(); ctx.strokeStyle = selecionado ? C.sel : C.texto; ctx.stroke(); if (e.tipo === "PORTA") { const a = T(D({ x: c.x - u.x * h, y: c.y - u.y * h })); ctx.beginPath(); ctx.arc(a.x, a.y, e.largura * viewport.escala, Math.atan2(-u.y, u.x) - Math.PI / 2, Math.atan2(-u.y, u.x)); ctx.stroke(); } break; }
        case "SAPATA": case "BLOCO": case "RADIER": case "ESTACA": { const c = D(e.centro); const b = e.bx / 2, h = e.by / 2; poly([{ x: c.x - b, y: c.y - h }, { x: c.x + b, y: c.y - h }, { x: c.x + b, y: c.y + h }, { x: c.x - b, y: c.y + h }]); ctx.setLineDash([5, 3]); ctx.stroke(); ctx.setLineDash([]); break; }
        case "COTA": { const a = D(e.p1), b = D(e.p2); const v = { x: b.x - a.x, y: b.y - a.y }; const l = Math.hypot(v.x, v.y) || 1; const n = { x: (-v.y / l) * e.afastamento, y: (v.x / l) * e.afastamento }; const a2 = { x: a.x + n.x, y: a.y + n.y }, b2 = { x: b.x + n.x, y: b.y + n.y }; const ta = T(a), tb = T(b), ta2 = T(a2), tb2 = T(b2); ctx.beginPath(); ctx.moveTo(ta.x, ta.y); ctx.lineTo(ta2.x, ta2.y); ctx.moveTo(tb.x, tb.y); ctx.lineTo(tb2.x, tb2.y); ctx.moveTo(ta2.x, ta2.y); ctx.lineTo(tb2.x, tb2.y); ctx.stroke(); ctx.fillStyle = C.texto; const texto = e.estilo === "NIVEL" ? `${a.y >= 0 ? "+" : ""}${a.y.toFixed(2).replace(".", ",")}` : l.toFixed(2).replace(".", ","); ctx.fillText(texto, (ta2.x + tb2.x) / 2, (ta2.y + tb2.y) / 2 - 8); break; }
        case "OBJETO": {
          // símbolo em planta: contorno real (l × p girado), frente marcada, nome curto, conectores pela cor do sistema
          const it = itemObjeto(e.item); const q = cantosObjeto(e).map(D); const corObj = fantasma ? C.prev : it?.cor ?? cor;
          poly(q); ctx.globalAlpha = fantasma ? 0.12 : e.camada === "humanos" ? 0.25 : 0.18; ctx.fillStyle = corObj; ctx.fill(); ctx.globalAlpha = fantasma ? 0.7 : 1;
          ctx.strokeStyle = selecionado && !fantasma ? C.sel : e.camada === "mobiliario" ? C.texto3 : corObj; ctx.stroke();
          const c = D(e.centro), tc = T(c);
          if (it?.forma === "humano") { ctx.beginPath(); ctx.arc(tc.x, tc.y, Math.max(3, (e.dim.p / 2) * viewport.escala), 0, Math.PI * 2); ctx.stroke(); }
          else if (it?.luz) { const r = Math.max(4, (Math.min(e.dim.l, e.dim.p) / 2) * viewport.escala); ctx.beginPath(); ctx.arc(tc.x, tc.y, r, 0, Math.PI * 2); ctx.moveTo(tc.x - r * 0.7, tc.y - r * 0.7); ctx.lineTo(tc.x + r * 0.7, tc.y + r * 0.7); ctx.moveTo(tc.x + r * 0.7, tc.y - r * 0.7); ctx.lineTo(tc.x - r * 0.7, tc.y + r * 0.7); ctx.stroke(); if (e.ligada === false) { ctx.fillStyle = C.texto3; ctx.fillText("desl.", tc.x, tc.y + r + 8); } }
          // frente (+y local): traço do centro para a face frontal
          const r0 = (e.rotacao * Math.PI) / 180, f = T(D({ x: e.centro.x - Math.sin(r0) * e.dim.p / 2, y: e.centro.y + Math.cos(r0) * e.dim.p / 2 }));
          ctx.beginPath(); ctx.moveTo(tc.x, tc.y); ctx.lineTo(f.x, f.y); ctx.stroke();
          if (it && !fantasma) for (const k of it.conectores) { const g = posicaoConector(modelo, e, k.id); if (!g) continue; const t = T(g); ctx.fillStyle = COR_SISTEMA[k.sistema] ?? cor; ctx.beginPath(); if (k.fluxo === "SAI") ctx.rect(t.x - 3, t.y - 3, 6, 6); else ctx.arc(t.x, t.y, 3, 0, Math.PI * 2); ctx.fill(); }
          if (viewport.escala > 25 && it) { ctx.fillStyle = C.texto; ctx.font = "10px var(--font-sans), sans-serif"; ctx.fillText(it.nome.length > 18 ? `${it.nome.slice(0, 17)}…` : it.nome, tc.x, tc.y - Math.max(e.dim.p, 0.2) / 2 * viewport.escala - 6); ctx.font = "12px var(--font-mono), monospace"; }
          break;
        }
        case "CONEXAO": {
          // representação gráfica derivada: projeção do percurso esquemático, tracejada, na cor do sistema
          const pts = percursoConexao(modelo, e); if (pts.length < 2) break;
          ctx.strokeStyle = selecionado ? C.sel : COR_SISTEMA[e.sistema] ?? cor; ctx.lineWidth = selecionado ? 3 : 2; ctx.setLineDash(e.sistema === "ESGOTO" ? [8, 3] : e.sistema === "ELETRICA" ? [2, 3] : [6, 3]);
          poly(pts.map((p) => D({ x: p.x, y: p.y })), false); ctx.stroke(); ctx.setLineDash([]);
          if (e.sistema === "ELETRICA" && e.circuito && viewport.escala > 20) { const m = pts[Math.floor(pts.length / 2)]!; const t = T(D(m)); ctx.fillStyle = COR_SISTEMA.ELETRICA!; ctx.fillText(e.circuito, t.x + 10, t.y - 6); }
          break;
        }
        case "TEXTO": case "ETIQUETA": { const t = T(D(e.p)); ctx.fillStyle = C.texto; ctx.font = `${Math.max(10, e.altura * viewport.escala)}px var(--font-sans), sans-serif`; ctx.fillText(e.texto, t.x, t.y); ctx.font = "12px var(--font-mono), monospace"; break; }
      }
      ctx.setLineDash([]); ctx.globalAlpha = 1;
    };

    for (const e of elementosVisiveis(s)) desenharElemento(e);

    // prévia fantasma do comando ativo (RF01 CA-03)
    if (comando && cursor && aguardaPonto) {
      const def = comando.def.codigo; const v = comando.valores; const c = cursor;
      const ghost = (e: Elemento) => desenharElemento(e, true);
      const b = base;
      const g = (tipo: string, extra: Record<string, unknown>) => ({ id: "_g", tipo, pavimentoId, camada: "desenho", origem: "CANVAS", ...extra } as unknown as Elemento);
      if (["LINHA", "LINHA_DETALHE", "LINHA_QUEBRA", "POLILINHA", "SPLINE", "LAJE", "COTA_CONTINUA"].includes(def)) { const todos = [v["p1"] as Ponto2D, ...comando.pontos].filter(Boolean); if (todos.length) { ghost(g("POLILINHA", { pontos: [...todos, c], fechada: false })); } }
      else if (def === "PAREDE" && b) ghost(g("PAREDE", { p1: b, p2: c, espessura: 0.15, altura: 2.8, camada: "arquitetura" }));
      else if (def === "VIGA" && b) ghost(g("VIGA", { p1: b, p2: c, b: 0.12, h: 0.4, camada: "estrutura" }));
      else if ((def === "RETANGULO" || def === "ZOOM") && b) ghost(g("RETANGULO", { p1: b, p2: c }));
      else if (def === "CIRCULO" && b) ghost(g("CIRCULO", { centro: b, raio: dist(b, c) }));
      else if (def === "OBJETO" && itemObjeto(String(v["item"] ?? ""))) { const it = itemObjeto(String(v["item"]))!; ghost(g("OBJETO", { item: it.id, centro: c, rotacao: Number(v["rot"] ?? 0), elevacao: it.elevacao ?? 0, dim: it.dim, camada: "desenho" })); }
      else if (def === "PILAR") ghost(g("PILAR", { centro: c, b: 0.19, h: 0.4, secao: "RETANGULAR", camada: "estrutura" }));
      else if (def === "EIXO" && b) ghost(g("LINHA", { p1: b, p2: c }));
      else if ((def === "PLANO_REF" || def === "ESPELHAR" || def === "ARCO" || def === "COTA_LINEAR" || def === "COTA_ALINHADA" || def === "RAIO_CONSTRUCAO" || def === "LINHA_CONSTRUCAO") && b) ghost(g("LINHA", { p1: b, p2: c }));
      else if ((def === "MOVER" || def === "COPIAR" || def === "ESTICAR") && v["base"]) { const d = { x: c.x - (v["base"] as Ponto2D).x, y: c.y - (v["base"] as Ponto2D).y }; const ids = comando.selecoes[0] ?? [...selecao]; for (const id of ids) { const e = modelo.elemento(id); if (e) desenharElemento(structuredClone(e), true, d); } }
      if (b) { const tb = T(b), tc = T(c); ctx.strokeStyle = C.prev; ctx.setLineDash([2, 4]); ctx.beginPath(); ctx.moveTo(tb.x, tb.y); ctx.lineTo(tc.x, tc.y); ctx.stroke(); ctx.setLineDash([]); ctx.fillStyle = C.texto3; const d = dist(b, c); const ang = (Math.atan2(c.y - b.y, c.x - b.x) * 180) / Math.PI; ctx.textAlign = "left"; ctx.fillText(`${d.toFixed(2)} m  ∠${ang.toFixed(1)}°`, tc.x + 14, tc.y + 18); ctx.textAlign = "center"; }
    }

    // janela de seleção
    if (janela) { const a = T(janela.a), b = T(janela.b); const cruz = janela.b.x < janela.a.x; ctx.strokeStyle = C.sel; ctx.fillStyle = C.sel; ctx.globalAlpha = 0.08; ctx.fillRect(Math.min(a.x, b.x), Math.min(a.y, b.y), Math.abs(b.x - a.x), Math.abs(b.y - a.y)); ctx.globalAlpha = 1; ctx.setLineDash(cruz ? [5, 3] : []); ctx.strokeRect(Math.min(a.x, b.x), Math.min(a.y, b.y), Math.abs(b.x - a.x), Math.abs(b.y - a.y)); ctx.setLineDash([]); }

    // marcador de snap (cor + forma + texto)
    if (snapAtual && cursorTela) {
      const t = T(snapAtual.p); ctx.strokeStyle = C.snap; ctx.lineWidth = 2; ctx.beginPath();
      switch (snapAtual.modo) {
        case "EXT": ctx.rect(t.x - 6, t.y - 6, 12, 12); break;
        case "MED": ctx.moveTo(t.x, t.y - 7); ctx.lineTo(t.x + 7, t.y + 6); ctx.lineTo(t.x - 7, t.y + 6); ctx.closePath(); break;
        case "CEN": case "NOD": ctx.arc(t.x, t.y, 6, 0, Math.PI * 2); break;
        case "INT": ctx.moveTo(t.x - 6, t.y - 6); ctx.lineTo(t.x + 6, t.y + 6); ctx.moveTo(t.x + 6, t.y - 6); ctx.lineTo(t.x - 6, t.y + 6); break;
        case "EIX": for (let i = 0; i < 6; i++) { const a = (Math.PI / 3) * i; const px = t.x + 7 * Math.cos(a), py = t.y + 7 * Math.sin(a); if (i) ctx.lineTo(px, py); else ctx.moveTo(px, py); } ctx.closePath(); break;
        case "PER": ctx.moveTo(t.x - 6, t.y - 6); ctx.lineTo(t.x - 6, t.y + 6); ctx.lineTo(t.x + 6, t.y + 6); ctx.moveTo(t.x - 6, t.y); ctx.lineTo(t.x, t.y); ctx.lineTo(t.x, t.y + 6); break;
        default: ctx.moveTo(t.x - 6, t.y - 6); ctx.lineTo(t.x + 6, t.y - 6); ctx.lineTo(t.x - 6, t.y + 6); ctx.lineTo(t.x + 6, t.y + 6); ctx.closePath();
      }
      ctx.stroke(); ctx.lineWidth = 1;
      ctx.fillStyle = C.snap; ctx.textAlign = "left"; ctx.font = "11px var(--font-sans), sans-serif"; ctx.fillText(`◆ ${snapAtual.modo} · ${snapAtual.rotulo}`, t.x + 12, t.y - 12); ctx.textAlign = "center"; ctx.font = "12px var(--font-mono), monospace";
    }
    // cursor em cruz
    if (cursorTela && !arrasto.current) { ctx.strokeStyle = C.texto3; ctx.globalAlpha = 0.5; ctx.beginPath(); ctx.moveTo(cursorTela.x - 12, cursorTela.y); ctx.lineTo(cursorTela.x + 12, cursorTela.y); ctx.moveTo(cursorTela.x, cursorTela.y - 12); ctx.lineTo(cursorTela.x, cursorTela.y + 12); ctx.stroke(); ctx.globalAlpha = 1; }
  }, [tam, viewport, modelo, tick, pavimentoId, comando, selecao, modos, cursor, snapAtual, janela, cursorTela, hover, paraTela, paraMundo, s, aguardaPonto, base]);

  // ---------------------------------------------------------------- eventos
  const tela = (ev: React.PointerEvent) => { const r = ref.current!.getBoundingClientRect(); return { x: ev.clientX - r.left, y: ev.clientY - r.top }; };

  const onPointerDown = (ev: React.PointerEvent) => {
    ref.current?.focus();
    const t = tela(ev);
    if (ev.button === 1 || (ev.button === 0 && espaco.current)) { arrasto.current = { tipo: "pan", inicioTela: t, inicioMundo: paraMundo(t), vp: { cx: viewport.cx, cy: viewport.cy } }; ref.current?.setPointerCapture(ev.pointerId); return; }
    if (ev.button === 2) return;
    if (ev.button !== 0) return;
    const { p } = resolverCursor(t);
    if (aguardaPonto) { void s.entradaPonto(p); return; }
    // seleção (ou passo de seleção)
    const e = elementoEm(paraMundo(t));
    if (e) {
      if (aguardaSelecao) { if (ev.shiftKey) s.selecionar([e.id], "alternar"); else void s.entradaSelecao([...new Set([...selecao, e.id])], true); return; }
      s.selecionar([e.id], ev.shiftKey ? "alternar" : "substituir"); return;
    }
    arrasto.current = { tipo: "janela", inicioTela: t, inicioMundo: paraMundo(t), vp: { cx: viewport.cx, cy: viewport.cy } };
    ref.current?.setPointerCapture(ev.pointerId);
  };
  const onPointerMove = (ev: React.PointerEvent) => {
    const t = tela(ev); setCursorTela(t);
    const a = arrasto.current;
    if (a?.tipo === "pan") { s.setViewport({ cx: a.vp.cx - (t.x - a.inicioTela.x) / viewport.escala, cy: a.vp.cy + (t.y - a.inicioTela.y) / viewport.escala }); return; }
    if (a?.tipo === "janela") { setJanela({ a: a.inicioMundo, b: paraMundo(t) }); return; }
    const { p, snap } = resolverCursor(t);
    setSnapAtual(snap); s.setCursor(p);
    if (!aguardaPonto) { const e = elementoEm(paraMundo(t)); setHover(e?.id ?? null); } else setHover(null);
  };
  const onPointerUp = (ev: React.PointerEvent) => {
    const a = arrasto.current; arrasto.current = null; ref.current?.releasePointerCapture(ev.pointerId);
    if (a?.tipo !== "janela") return;
    const fim = paraMundo(tela(ev)); setJanela(null);
    if (dist(a.inicioMundo, fim) * viewport.escala < 4) { if (!aguardaSelecao && !ev.shiftKey) s.selecionar([], "substituir"); return; }
    const cruz = fim.x < a.inicioMundo.x;
    const min = { x: Math.min(a.inicioMundo.x, fim.x), y: Math.min(a.inicioMundo.y, fim.y) }, max = { x: Math.max(a.inicioMundo.x, fim.x), y: Math.max(a.inicioMundo.y, fim.y) };
    const dentro = (e: Elemento) => { const c = caixaElemento(e, modelo); return cruz ? !(c.max.x < min.x || c.min.x > max.x || c.max.y < min.y || c.min.y > max.y) : c.min.x >= min.x && c.max.x <= max.x && c.min.y >= min.y && c.max.y <= max.y; };
    const ids = elementosVisiveis(s).filter(dentro).map((e) => e.id);
    if (aguardaSelecao) { if (ids.length) void s.entradaSelecao([...new Set([...selecao, ...ids])], true); return; }
    s.selecionar(ids, ev.shiftKey ? "adicionar" : "substituir");
    if (ids.length) s.registrar("info", `${ids.length} selecionado(s) por ${cruz ? "cruzamento" : "janela"}.`);
  };
  const onWheel = (ev: React.WheelEvent) => {
    const t = tela(ev as unknown as React.PointerEvent); const antes = paraMundo(t);
    const fator = ev.deltaY < 0 ? 1.15 : 1 / 1.15; const escala = Math.max(2, Math.min(800, viewport.escala * fator));
    const cx = antes.x - (t.x - tam.w / 2) / escala, cy = antes.y + (t.y - tam.h / 2) / escala;
    s.setViewport({ cx, cy, escala });
  };
  const onContextMenu = (ev: React.MouseEvent) => {
    ev.preventDefault();
    if (comando) { const p = comando.def.roteiro[comando.passo]; if (p?.repetir) void s.opcao(p.opcoes?.includes("Fechar") ? "Fechar" : "Sair"); else if (p?.tipo === "SELECAO") void s.entradaTexto(""); }
    else s.repetirUltimo();
  };
  useEffect(() => {
    const kd = (e: KeyboardEvent) => { if (e.code === "Space" && document.activeElement === ref.current) { espaco.current = true; e.preventDefault(); } };
    const ku = (e: KeyboardEvent) => { if (e.code === "Space") espaco.current = false; };
    window.addEventListener("keydown", kd); window.addEventListener("keyup", ku);
    return () => { window.removeEventListener("keydown", kd); window.removeEventListener("keyup", ku); };
  }, []);

  const dica = passoAtual ? `${passoAtual.mensagem}${comando?.erro ? ` ⚠ ${comando.erro}` : ""}` : hover ? `${modelo.elemento(hover)?.tipo ?? ""} ${modelo.elemento(hover)?.rotulo ?? ""}` : "";

  return (
    <div ref={caixaRef} className="relative h-full w-full overflow-hidden">
      <canvas ref={ref} tabIndex={0} role="img" aria-label={`Planta do pavimento ${modelo.pavimento(pavimentoId)?.nome ?? ""}: ${elementosVisiveis(s).length} elementos, ${modelo.eixos.length} eixos`}
        style={{ width: tam.w, height: tam.h }} className="canvas-motor block"
        onPointerDown={onPointerDown} onPointerMove={onPointerMove} onPointerUp={onPointerUp} onPointerLeave={() => { setCursorTela(null); setSnapAtual(null); setHover(null); }} onWheel={onWheel} onContextMenu={onContextMenu} />
      {modos.entradaDinamica && cursorTela && (dica || cursor) && !arrasto.current && (
        <div aria-hidden className="pointer-events-none absolute rounded-[var(--radius-sm)] border border-line-soft bg-bg/95 px-2 py-1 text-[11px] shadow-sm max-w-xs" style={{ left: Math.min(cursorTela.x + 16, tam.w - 220), top: Math.min(cursorTela.y + 22, tam.h - 48) }}>
          {dica && <p className="font-medium text-text truncate">{dica}</p>}
          {cursor && <p className="mono text-text-3">{cursor.x.toFixed(2).replace(".", ",")}; {cursor.y.toFixed(2).replace(".", ",")}</p>}
        </div>
      )}
      {!s.podeEditar && <p className="absolute top-2 left-2 rounded-full bg-surface px-3 py-1 text-xs text-text-2 border border-line-soft">Somente visualização</p>}
    </div>
  );
}

export { pontoDentroPoligono };
