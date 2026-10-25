/**
 * Geometria da cena 3D derivada do modelo BIM (RF13, AUD-01/02/03/06): cada elemento vira peças com geometria,
 * posição, cor "real" (acabamento, para os modos realistas) e cor da disciplina (modos técnicos). Coordenadas three:
 * X = x da planta, Y = cota, Z = −y da planta. Paredes saem recortadas pelos vãos de porta e janela, com folha e
 * vidro; objetos usam as formas procedurais de formas.ts; conexões, o percurso esquemático derivado (o mesmo da
 * planta e do servidor). Nada aqui é gravado: a cena é sempre reconstruída do modelo.
 */
import * as THREE from "three";
import { itemObjeto, percursoConexao, type Abertura, type Conexao, type Elemento, type ModeloBim, type Objeto, type Pavimento } from "@cca/notacao-vi";
import { pecasDoObjeto, type Acabamento } from "./formas";

export type Peca3D = {
  id: string; chave: string; geo: THREE.BufferGeometry; pos: THREE.Vector3; quat: THREE.Quaternion;
  corReal: string; corDisciplina: string; acab: Acabamento; camada: string; humano?: boolean; linha?: boolean;
};
export type Luz3D = { id: string; pos: THREE.Vector3; cor: string; candela: number; alcance: number; ligada: boolean; temperaturaK: number; fluxoLm: number };

export const COR_DISCIPLINA: Record<string, string> = {
  arquitetura: "#C8C2B6", estrutura: "#1D4ED8", fundacao: "#7C4A1E", desenho: "#9CA3AF", anotacao: "#166534", "ia-rascunho": "#A855F7",
  mobiliario: "#B45309", eletrica: "#CA8A04", hidrossanitaria: "#0284C7", humanos: "#BE185D",
};
export const COR_SISTEMA: Record<string, string> = { ELETRICA: "#EAB308", AGUA_FRIA: "#2563EB", AGUA_QUENTE: "#DC2626", ESGOTO: "#7C2D12", VENTILACAO: "#6B7280" };

const Y = new THREE.Vector3(0, 1, 0);
const qY = (ang: number) => new THREE.Quaternion().setFromAxisAngle(Y, ang);

export function alturaPav(pav: Pavimento[], p: Pavimento, padrao = 2.8) {
  const ord = [...pav].sort((a, b) => a.elevacao - b.elevacao); const i = ord.findIndex((x) => x.id === p.id);
  return ord[i + 1] ? ord[i + 1]!.elevacao - p.elevacao : padrao;
}

function caixaParede(id: string, chave: string, p1: { x: number; y: number }, ang: number, t0: number, t1: number, za: number, zb: number, esp: number, corReal: string, camada: string, acab: Acabamento = "padrao"): Peca3D {
  const u = { x: Math.cos(ang), y: Math.sin(ang) }, m = (t0 + t1) / 2;
  return { id, chave, geo: new THREE.BoxGeometry(Math.max(0.005, t1 - t0), Math.max(0.005, zb - za), esp), pos: new THREE.Vector3(p1.x + u.x * m, (za + zb) / 2, -(p1.y + u.y * m)), quat: qY(ang), corReal, corDisciplina: COR_DISCIPLINA[camada] ?? "#888", acab, camada };
}

/** parede recortada pelos vãos: trechos cheios, peitoril, verga, folha de porta e vidro de janela */
function parede(m: ModeloBim, e: Extract<Elemento, { tipo: "PAREDE" }>, z: number): Peca3D[] {
  const dx = e.p2.x - e.p1.x, dy = e.p2.y - e.p1.y, L = Math.hypot(dx, dy) || 0.01, ang = Math.atan2(dy, dx);
  const vaos = m.elementos.filter((a): a is Abertura => (a.tipo === "PORTA" || a.tipo === "JANELA") && a.paredeId === e.id)
    .map((a) => ({ a, t0: Math.max(0, a.posicao * L - a.largura / 2), t1: Math.min(L, a.posicao * L + a.largura / 2) })).sort((x, y) => x.t0 - y.t0);
  const out: Peca3D[] = []; const cor = "#E8E2D8"; let t = 0, k = 0;
  const add = (t0: number, t1: number, za: number, zb: number, corR = cor, acab: Acabamento = "padrao", esp = e.espessura, idEl = e.id) => { if (t1 - t0 > 1e-3 && zb - za > 1e-3) out.push(caixaParede(idEl, `${e.id}:${k++}`, e.p1, ang, t0, t1, z + za, z + zb, esp, corR, idEl === e.id ? e.camada : "arquitetura", acab)); };
  for (const v of vaos) {
    add(t, v.t0, 0, e.altura);
    add(v.t0, v.t1, 0, v.a.peitoril);
    add(v.t0, v.t1, Math.min(e.altura, v.a.peitoril + v.a.altura), e.altura);
    if (v.a.tipo === "PORTA") add(v.t0 + 0.01, v.t1 - 0.01, 0.005, Math.min(e.altura, v.a.altura) - 0.01, "#8B6B4A", "madeira", 0.035, v.a.id);
    else add(v.t0 + 0.02, v.t1 - 0.02, v.a.peitoril + 0.02, Math.min(e.altura, v.a.peitoril + v.a.altura) - 0.02, "#BFDBFE", "vidro", 0.012, v.a.id);
    t = Math.max(t, v.t1);
  }
  add(t, L, 0, e.altura);
  return out;
}

function objeto(m: ModeloBim, o: Objeto, z: number): Peca3D[] {
  const it = itemObjeto(o.item); if (!it) return [];
  const r = (o.rotacao * Math.PI) / 180, c = Math.cos(r), s = Math.sin(r), q = qY(r);
  const z0 = z + o.elevacao, humano = it.disciplina === "humanos";
  return pecasDoObjeto(o, it.forma, it.cor).map((pc, i) => {
    const geo = pc.g === "caixa" ? new THREE.BoxGeometry(pc.s[0], pc.s[2], pc.s[1]) : pc.g === "esfera" ? new THREE.SphereGeometry(pc.s[0] / 2, 20, 14) : new THREE.CylinderGeometry(pc.s[0] / 2, pc.s[0] / 2, pc.s[2], 20);
    let quat = q;
    if (pc.g === "cilindro" && pc.eixo === "y") quat = q.clone().multiply(new THREE.Quaternion().setFromAxisAngle(new THREE.Vector3(1, 0, 0), Math.PI / 2));
    if (pc.g === "cilindro" && pc.eixo === "x") quat = q.clone().multiply(new THREE.Quaternion().setFromAxisAngle(new THREE.Vector3(0, 0, 1), Math.PI / 2));
    const gx = o.centro.x + pc.c[0] * c - pc.c[1] * s, gy = o.centro.y + pc.c[0] * s + pc.c[1] * c;
    return { id: o.id, chave: `${o.id}:${i}`, geo, pos: new THREE.Vector3(gx, z0 + pc.c[2], -gy), quat, corReal: pc.cor ?? it.cor, corDisciplina: COR_DISCIPLINA[o.camada] ?? "#888", acab: pc.acab, camada: o.camada, humano };
  });
}

/** tubo/cabo ao longo do percurso esquemático, na cor do sistema */
function conexao(m: ModeloBim, cx: Conexao): Peca3D[] {
  const pts = percursoConexao(m, cx); const out: Peca3D[] = [];
  const raio = Math.max(0.008, (cx.dnMm ?? 20) / 2000);
  for (let i = 1; i < pts.length; i++) {
    const a = new THREE.Vector3(pts[i - 1]!.x, pts[i - 1]!.z, -pts[i - 1]!.y), b = new THREE.Vector3(pts[i]!.x, pts[i]!.z, -pts[i]!.y);
    const d = b.clone().sub(a), l = d.length(); if (l < 1e-4) continue;
    const quat = new THREE.Quaternion().setFromUnitVectors(Y, d.clone().normalize());
    out.push({ id: cx.id, chave: `${cx.id}:${i}`, geo: new THREE.CylinderGeometry(raio, raio, l, 10), pos: a.clone().add(b).multiplyScalar(0.5), quat, corReal: COR_SISTEMA[cx.sistema] ?? "#888", corDisciplina: COR_SISTEMA[cx.sistema] ?? "#888", acab: cx.sistema === "ELETRICA" ? "padrao" : "metal", camada: cx.camada });
  }
  return out;
}

export function pecasDoElemento(m: ModeloBim, e: Elemento, z: number, h: number, pav: Pavimento[]): Peca3D[] {
  const base = (geo: THREE.BufferGeometry, pos: THREE.Vector3, quat = new THREE.Quaternion(), corReal = "#B8B8B4", acab: Acabamento = "padrao"): Peca3D[] => [{ id: e.id, chave: e.id, geo, pos, quat, corReal, corDisciplina: COR_DISCIPLINA[e.camada] ?? "#888", acab, camada: e.camada }];
  switch (e.tipo) {
    case "PAREDE": return parede(m, e, z);
    case "VIGA": {
      const dx = e.p2.x - e.p1.x, dy = e.p2.y - e.p1.y, l = Math.hypot(dx, dy) || 0.01;
      return base(new THREE.BoxGeometry(l, e.h, e.b), new THREE.Vector3((e.p1.x + e.p2.x) / 2, z - e.h / 2, -(e.p1.y + e.p2.y) / 2), qY(Math.atan2(dy, dx))); // face superior na elevação do pavimento
    }
    case "PILAR": {
      const topo = pav.find((p) => p.id === e.nivelTopoId), bas = pav.find((p) => p.id === e.nivelBaseId);
      const z0 = (bas?.elevacao ?? z) + e.baseOffset, z1 = (topo && topo.id !== bas?.id ? topo.elevacao : (bas?.elevacao ?? z) + h) + e.topoOffset, alt = Math.max(0.1, z1 - z0);
      return base(e.secao === "CIRCULAR" ? new THREE.CylinderGeometry(e.b / 2, e.b / 2, alt, 24) : new THREE.BoxGeometry(e.b, alt, e.h), new THREE.Vector3(e.centro.x, z0 + alt / 2, -e.centro.y), qY((e.rotacao * Math.PI) / 180));
    }
    case "LAJE": {
      const shape = new THREE.Shape(e.contorno.map((p) => new THREE.Vector2(p.x, p.y)));
      const geo = new THREE.ExtrudeGeometry(shape, { depth: e.h, bevelEnabled: false }); geo.rotateX(-Math.PI / 2);
      return base(geo, new THREE.Vector3(0, z - e.h, 0), undefined, "#C9C9C4");
    }
    case "SAPATA": case "BLOCO": case "RADIER": case "ESTACA": return base(new THREE.BoxGeometry(e.bx, e.h, e.by), new THREE.Vector3(e.centro.x, z - e.h / 2 - 0.05, -e.centro.y), undefined, "#8B7355");
    case "CONTRAVENTAMENTO": {
      const aba = Number(/L\s*(\d+(?:[.,]\d+)?)/i.exec(e.perfil)?.[1]?.replace(",", ".") ?? 50) / 1000;
      const a = new THREE.Vector3(e.p1.x, z, -e.p1.y), b = new THREE.Vector3(e.p2.x, z, -e.p2.y), d = b.clone().sub(a), l = d.length() || 0.01;
      return base(new THREE.CylinderGeometry(Math.max(0.02, aba / 2), Math.max(0.02, aba / 2), l, 8), a.clone().add(b).multiplyScalar(0.5), new THREE.Quaternion().setFromUnitVectors(Y, d.normalize()), "#6B7280", "metal");
    }
    case "OBJETO": return objeto(m, e, z);
    case "CONEXAO": return conexao(m, e);
    default: return [];
  }
}

/** luminárias do modelo: posição da lâmpada, fluxo e temperatura do catálogo, estado ligada/desligada do objeto */
export function luzesDoModelo(m: ModeloBim, objetos: Objeto[]): Luz3D[] {
  const out: Luz3D[] = [];
  for (const o of objetos) {
    const it = itemObjeto(o.item); if (!it?.luz) continue;
    const z = (m.pavimento(o.pavimentoId)?.elevacao ?? 0) + o.elevacao + (it.montagem === "TETO" ? 0.05 : o.dim.a / 2);
    const r = (o.rotacao * Math.PI) / 180, frente = it.montagem === "PAREDE" ? o.dim.p : 0;
    out.push({ id: o.id, pos: new THREE.Vector3(o.centro.x - Math.sin(r) * frente, z, -(o.centro.y + Math.cos(r) * frente)), cor: "", candela: it.luz.fluxoLm / (4 * Math.PI), alcance: it.luz.alcanceM, ligada: o.ligada !== false, temperaturaK: it.luz.temperaturaK, fluxoLm: it.luz.fluxoLm });
  }
  return out;
}
