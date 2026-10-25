"use client";
/**
 * H9_scene3d (RF13): cena Three.js por camadas, cores da Tabela H, isolamento por pavimento, seleção,
 * prefers-reduced-motion respeitado (sem damping/animações) e fallback quando WebGL não existe.
 * Convenção de nível (a mesma do modelo analítico): vigas e lajes de um pavimento têm a face superior na elevação
 * desse pavimento; pilares nascem nele e sobem até o pavimento de cima. Raio X, corte horizontal e armaduras do
 * último dimensionamento (polilinhas do detalhamento, não textura) são opções da vista.
 */
import { Suspense, useMemo, useState } from "react";
import { useDados } from "@/lib/dados";
import type { Dimensionamento } from "@/lib/tipos";
import { Canvas } from "@react-three/fiber";
import { Grid, OrbitControls, Text } from "@react-three/drei";
import * as THREE from "three";
import type { Elemento, Pavimento } from "@cca/notacao-vi";
import { elementosVisiveis, useMotor } from "@/lib/motor/estado";

const COR: Record<string, string> = { arquitetura: "#C8C2B6", estrutura: "#1D4ED8", fundacao: "#7C4A1E", desenho: "#9CA3AF", anotacao: "#166534", "ia-rascunho": "#A855F7" };
const SEL = "#F06000";

function alturaPav(pav: Pavimento[], p: Pavimento, padrao = 2.8) { const ord = [...pav].sort((a, b) => a.elevacao - b.elevacao); const i = ord.findIndex((x) => x.id === p.id); return ord[i + 1] ? ord[i + 1]!.elevacao - p.elevacao : padrao; }

type Opcoes = { raioX: boolean; corte: THREE.Plane[] };

function Elemento3D({ e, z, h, pav, sel, onClick, op }: { e: Elemento; z: number; h: number; pav: Pavimento[]; sel: boolean; onClick: (id: string, shift: boolean) => void; op: Opcoes }) {
  const cor = sel ? SEL : COR[e.camada] ?? "#888";
  const click = (ev: { stopPropagation: () => void; nativeEvent: MouseEvent }) => { ev.stopPropagation(); onClick(e.id, ev.nativeEvent.shiftKey); };
  const opac = sel ? 1 : op.raioX ? 0.18 : e.camada === "ia-rascunho" ? 0.55 : e.tipo === "LAJE" ? 0.8 : 1;
  const mat = <meshStandardMaterial color={cor} transparent={opac < 1} opacity={opac} depthWrite={opac >= 1} clippingPlanes={op.corte} />;
  switch (e.tipo) {
    case "PAREDE": case "VIGA": {
      const esp = e.tipo === "PAREDE" ? e.espessura : e.b, alt = e.tipo === "PAREDE" ? e.altura : e.h;
      const dx = e.p2.x - e.p1.x, dy = e.p2.y - e.p1.y, l = Math.hypot(dx, dy) || 0.01, ang = Math.atan2(dy, dx);
      const zc = e.tipo === "PAREDE" ? z + alt / 2 : z - alt / 2; // viga: face superior na elevação do pavimento
      return <mesh position={[(e.p1.x + e.p2.x) / 2, zc, -(e.p1.y + e.p2.y) / 2]} rotation={[0, ang, 0]} onClick={click}><boxGeometry args={[l, alt, esp]} />{mat}</mesh>;
    }
    case "PILAR": {
      const topo = pav.find((p) => p.id === e.nivelTopoId); const base = pav.find((p) => p.id === e.nivelBaseId);
      const z0 = (base?.elevacao ?? z) + e.baseOffset, z1 = (topo && topo.id !== base?.id ? topo.elevacao : (base?.elevacao ?? z) + h) + e.topoOffset;
      const alt = Math.max(0.1, z1 - z0);
      return <mesh position={[e.centro.x, z0 + alt / 2, -e.centro.y]} rotation={[0, (e.rotacao * Math.PI) / 180, 0]} onClick={click}>{e.secao === "CIRCULAR" ? <cylinderGeometry args={[e.b / 2, e.b / 2, alt, 24]} /> : <boxGeometry args={[e.b, alt, e.h]} />}{mat}</mesh>;
    }
    case "LAJE": {
      const shape = new THREE.Shape(e.contorno.map((p) => new THREE.Vector2(p.x, -p.y)));
      return <mesh position={[0, z - e.h, 0]} rotation={[-Math.PI / 2, 0, 0]} onClick={click}><extrudeGeometry args={[shape, { depth: e.h, bevelEnabled: false }]} />{mat}</mesh>;
    }
    case "SAPATA": case "BLOCO": case "RADIER": case "ESTACA":
      return <mesh position={[e.centro.x, z - e.h / 2 - 0.05, -e.centro.y]} onClick={click}><boxGeometry args={[e.bx, e.h, e.by]} />{mat}</mesh>;
    case "PORTA": case "JANELA": return null; // vazios ficam implícitos; representados na planta
    case "CONTRAVENTAMENTO": {
      // barra de treliça no nível do pavimento (como no modelo analítico); diâmetro gráfico pela aba do perfil
      const aba = Number(/L\s*(\d+(?:[.,]\d+)?)/i.exec(e.perfil)?.[1]?.replace(",", ".") ?? 50) / 1000;
      const a = new THREE.Vector3(e.p1.x, z, -e.p1.y), b = new THREE.Vector3(e.p2.x, z, -e.p2.y);
      const meio = a.clone().add(b).multiplyScalar(0.5), dir = b.clone().sub(a), l = dir.length() || 0.01;
      const q = new THREE.Quaternion().setFromUnitVectors(new THREE.Vector3(0, 1, 0), dir.normalize());
      return <mesh position={meio} quaternion={q} onClick={click}><cylinderGeometry args={[Math.max(0.02, aba / 2), Math.max(0.02, aba / 2), l, 8]} />{mat}</mesh>;
    }
    case "LINHA": case "PLANO_REF": case "COTA": {
      const pts = [new THREE.Vector3(e.p1.x, z + 0.01, -e.p1.y), new THREE.Vector3(e.p2.x, z + 0.01, -e.p2.y)];
      const geo = new THREE.BufferGeometry().setFromPoints(pts);
      return <primitive object={new THREE.Line(geo, new THREE.LineBasicMaterial({ color: cor }))} onClick={click} />;
    }
    case "POLILINHA": case "HACHURA": case "RETANGULO": {
      const pts = e.tipo === "RETANGULO" ? [e.p1, { x: e.p2.x, y: e.p1.y }, e.p2, { x: e.p1.x, y: e.p2.y }, e.p1] : e.tipo === "HACHURA" ? [...e.contorno, e.contorno[0]!] : e.fechada ? [...e.pontos, e.pontos[0]!] : e.pontos;
      const geo = new THREE.BufferGeometry().setFromPoints(pts.map((p) => new THREE.Vector3(p.x, z + 0.01, -p.y)));
      return <primitive object={new THREE.Line(geo, new THREE.LineBasicMaterial({ color: cor }))} onClick={click} />;
    }
    case "CIRCULO": return <mesh position={[e.centro.x, z + 0.01, -e.centro.y]} rotation={[-Math.PI / 2, 0, 0]} onClick={click}><ringGeometry args={[Math.max(0.01, e.raio - 0.02), e.raio, 48]} /><meshBasicMaterial color={cor} side={THREE.DoubleSide} /></mesh>;
    case "AMBIENTE": case "TEXTO": case "ETIQUETA": { const p = e.tipo === "AMBIENTE" ? e.ponto : e.p; return <Text position={[p.x, z + 0.05, -p.y]} rotation={[-Math.PI / 2, 0, 0]} fontSize={0.35} color="#111827" anchorX="center">{e.tipo === "AMBIENTE" ? e.nome : e.texto}</Text>; }
    default: return null;
  }
}

export function Cena3D() {
  const s = useMotor();
  const [webgl] = useState(() => { try { const c = document.createElement("canvas"); return !!(c.getContext("webgl2") || c.getContext("webgl")); } catch { return false; } });
  const reduzido = typeof window !== "undefined" && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  const pav = s.modelo.pavimentos;
  const itens = useMemo(() => {
    const todos: { e: Elemento; z: number; h: number }[] = [];
    for (const p of pav) { const z = p.elevacao, h = alturaPav(pav, p); for (const e of elementosVisiveis({ ...s, pavimentoId: p.id })) todos.push({ e, z, h }); }
    return todos;
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [s.tick, s.camadasVisiveis, s.ocultos, s.isolados, pav]);
  const onClick = (id: string, shift: boolean) => s.selecionar([id], shift ? "alternar" : "substituir");
  const [raioX, setRaioX] = useState(false);
  const [corteZ, setCorteZ] = useState<number | null>(null);
  const [verArmaduras, setVerArmaduras] = useState(false);
  const corte = useMemo(() => (corteZ == null ? [] : [new THREE.Plane(new THREE.Vector3(0, -1, 0), corteZ)]), [corteZ]);
  const dims = useDados<Dimensionamento[]>(["dims", s.obraId], `/obras/${s.obraId}/dimensionamentos`, { ativo: !!s.obraId && verArmaduras });
  const ultimo = (dims.data ?? []).find((d) => d.estado !== "FALHOU" && d.estado !== "PENDENTE" && d.estado !== "EM_EXECUCAO");
  const res = useDados<{ elementoId: string; detalhamento?: { posicoes: { posicao: string; funcao: string; geometria?: number[][][] }[] } }[]>(["dim", s.obraId, ultimo?.id], `/obras/${s.obraId}/dimensionamentos/${ultimo?.id}/resultados`, { ativo: !!ultimo && verArmaduras });
  const barras = useMemo(() => (res.data ?? []).flatMap((r) => (r.detalhamento?.posicoes ?? []).flatMap((p) => (p.geometria ?? []).map((g, i) => ({ chave: `${r.elementoId}-${p.posicao}-${i}`, longitudinal: !/estribo|transvers/i.test(p.funcao), pts: g.map((c) => new THREE.Vector3(c[0] ?? 0, c[2] ?? 0, -(c[1] ?? 0))) })))), [res.data]);
  const zMax = Math.max(3, ...pav.map((p) => p.elevacao + alturaPav(pav, p)));
  if (!webgl) return <div role="status" className="h-full grid place-items-center p-6 text-sm text-text-3">WebGL indisponível neste dispositivo: use a vista Planta (2D).</div>;
  const eixos = s.modelo.eixos;
  return (
    <div className="h-full w-full relative">
      <div className="absolute z-10 top-2 left-2 flex flex-wrap items-center gap-2 rounded bg-bg/90 border border-line-soft px-2 py-1 text-xs" role="group" aria-label="Opções da vista 3D">
        <label className="flex items-center gap-1"><input type="checkbox" checked={raioX} onChange={(e) => setRaioX(e.target.checked)} />Raio X</label>
        <label className="flex items-center gap-1"><input type="checkbox" checked={corteZ != null} onChange={(e) => setCorteZ(e.target.checked ? Math.min(zMax, 1.5) : null)} />Corte horizontal</label>
        {corteZ != null && <label className="flex items-center gap-1">z = {corteZ.toFixed(2)} m<input type="range" min={-1} max={zMax} step={0.05} value={corteZ} onChange={(e) => setCorteZ(Number(e.target.value))} aria-label="Altura do corte (m)" /></label>}
        {s.obraId && <label className="flex items-center gap-1"><input type="checkbox" checked={verArmaduras} onChange={(e) => setVerArmaduras(e.target.checked)} />Armaduras do dimensionamento</label>}
        {verArmaduras && <span className="text-text-3">{!ultimo ? "nenhum dimensionamento" : `${barras.length} barras · ${ultimo.estado === "OBSOLETO" ? "resultado desatualizado" : "último dimensionamento"}`}</span>}
      </div>
      <div className="h-full w-full" aria-label={`Cena 3D com ${itens.length} elementos${verArmaduras ? ` e ${barras.length} barras de armadura` : ""}`} role="img">
      <Canvas gl={{ localClippingEnabled: true }} camera={{ position: [18, 14, 18], fov: 45, near: 0.1, far: 500 }} dpr={[1, 1.5]} frameloop={reduzido ? "demand" : "always"} onPointerMissed={() => s.selecionar([], "substituir")}>
        <color attach="background" args={["#F3F4F6"]} />
        <ambientLight intensity={0.8} /><directionalLight position={[20, 30, 10]} intensity={1.2} castShadow />
        <Suspense fallback={null}>
          <Grid args={[80, 80]} cellSize={1} sectionSize={5} cellColor="#D1D5DB" sectionColor="#9CA3AF" fadeDistance={80} position={[0, -0.01, 0]} />
          {eixos.map((ex) => { const a = ex.direcao === "X" ? [ex.posicao, 0.02, -ex.inicio] : [ex.inicio, 0.02, -ex.posicao]; const b = ex.direcao === "X" ? [ex.posicao, 0.02, -ex.fim] : [ex.fim, 0.02, -ex.posicao]; const geo = new THREE.BufferGeometry().setFromPoints([new THREE.Vector3(...(a as [number, number, number])), new THREE.Vector3(...(b as [number, number, number]))]); return <group key={ex.id}><primitive object={new THREE.Line(geo, new THREE.LineDashedMaterial({ color: "#6B7280", dashSize: 0.5, gapSize: 0.25 }))} /><Text position={[b[0]! + (ex.direcao === "Y" ? 0.6 : 0), 0.05, b[2]! - (ex.direcao === "X" ? 0.6 : 0)]} rotation={[-Math.PI / 2, 0, 0]} fontSize={0.4} color="#374151">{ex.nome}</Text></group>; })}
          {itens.map(({ e, z, h }) => <Elemento3D key={e.id} e={e} z={z} h={h} pav={pav} sel={s.selecao.has(e.id)} onClick={onClick} op={{ raioX, corte }} />)}
          {verArmaduras && barras.map((b) => <primitive key={b.chave} object={new THREE.Line(new THREE.BufferGeometry().setFromPoints(b.pts), new THREE.LineBasicMaterial({ color: b.longitudinal ? "#B91C1C" : "#0F766E", clippingPlanes: corte }))} />)}
        </Suspense>
        <OrbitControls makeDefault enableDamping={!reduzido} target={[s.viewport.cx, 1.4, -s.viewport.cy]} />
      </Canvas>
      </div>
    </div>
  );
}
