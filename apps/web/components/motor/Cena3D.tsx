"use client";
/**
 * H9_scene3d (RF13, AUD-06/07/08): cena Three.js reconstruída do modelo, com dez modos de exibição, vistas
 * (perspectiva, topo, fachadas, corte), sol pela hora/data/latitude, luminárias do modelo com fluxo e temperatura de
 * cor do catálogo, sombras e exposição, e visita em primeira pessoa a partir de uma pessoa do modelo (com colisão).
 * Os modos "malha", "nós" e "pontos" mostram a malha de renderização da GPU — não são malha de elementos finitos
 * nem nuvem de pontos de levantamento. Desligar uma luminária aqui é efeito visual: não altera circuito nem conexão.
 * prefers-reduced-motion respeitado (sem damping) e fallback quando WebGL não existe.
 */
import { Suspense, useEffect, useMemo, useRef, useState } from "react";
import { useDados } from "@/lib/dados";
import type { Dimensionamento } from "@/lib/tipos";
import { Canvas, useThree } from "@react-three/fiber";
import { Grid, OrbitControls, Text } from "@react-three/drei";
import type { OrbitControls as OrbitControlsImpl } from "three-stdlib";
import * as THREE from "three";
import { alturaCirculacao, cantosObjeto, itemObjeto, pontoDentroPoligono, posicaoNaCirculacao, type Elemento, type Objeto, type Ponto2D } from "@cca/notacao-vi";
import { elementosVisiveis, useMotor } from "@/lib/motor/estado";
import { alturaPav, luzesDoModelo, pecasDoElemento, type Peca3D } from "@/lib/motor/cena";
import { PBR, corDaTemperatura, posicaoSolar, vetorSolar } from "@/lib/motor/formas";

const SEL = "#F06000";
/** luminárias que projetam sombra (cada uma custa seis renderizações de mapa de sombra por quadro) */
const SOMBRAS_LUZES = 4;

export type Preset = "realista-dia" | "realista-noite" | "luzes-off" | "unlit" | "solido" | "linhas-ocultas" | "arestas" | "malha" | "nos" | "pontos";
export const PRESETS: { id: Preset; rotulo: string; desc: string }[] = [
  { id: "realista-dia", rotulo: "Realista · dia", desc: "Materiais PBR, sol pela hora, data e latitude, sombras e exposição." },
  { id: "realista-noite", rotulo: "Realista · noite", desc: "Céu noturno: iluminam só as luminárias ligadas, com fluxo (lm) e temperatura de cor (K) do catálogo; as 4 primeiras projetam sombra." },
  { id: "luzes-off", rotulo: "Luzes artificiais desligadas", desc: "Noite com todas as luminárias apagadas (efeito visual: circuitos e conexões não mudam)." },
  { id: "unlit", rotulo: "Sem iluminação (unlit)", desc: "Cor própria de cada material, sem cálculo de luz nem sombra." },
  { id: "solido", rotulo: "Sólido simplificado", desc: "Sólidos com sombreamento simples e cor por disciplina, sem materiais nem sombras." },
  { id: "linhas-ocultas", rotulo: "Linhas ocultas", desc: "Faces brancas e arestas visíveis; o que está atrás de uma face não aparece." },
  { id: "arestas", rotulo: "Wireframe (somente arestas)", desc: "Somente arestas, inclusive as que ficam atrás de outras faces." },
  { id: "malha", rotulo: "Malha de renderização", desc: "Triângulos que a GPU desenha. Não é malha de elementos finitos (MEF) nem é usada em cálculo." },
  { id: "nos", rotulo: "Nós e arestas", desc: "Vértices e arestas da malha de renderização (não são nós de cálculo estrutural)." },
  { id: "pontos", rotulo: "Somente pontos", desc: "Só os vértices da malha de renderização. Não é nuvem de pontos de levantamento a laser." },
];
const REALISTA = new Set<Preset>(["realista-dia", "realista-noite", "luzes-off"]);
const TECNICO = new Set<Preset>(["solido", "linhas-ocultas", "arestas", "malha", "nos", "pontos"]);
type Vista = "perspectiva" | "topo" | "fachada-sul" | "fachada-norte" | "fachada-leste" | "fachada-oeste";
const VISTAS: { id: Vista; rotulo: string }[] = [
  { id: "perspectiva", rotulo: "Perspectiva" }, { id: "topo", rotulo: "Topo" }, { id: "fachada-sul", rotulo: "Fachada sul" },
  { id: "fachada-norte", rotulo: "Fachada norte" }, { id: "fachada-leste", rotulo: "Fachada leste" }, { id: "fachada-oeste", rotulo: "Fachada oeste" },
];

function PecaView({ p, preset, sel, onClick, corte, raioX }: { p: Peca3D; preset: Preset; sel: boolean; onClick: (id: string, shift: boolean) => void; corte: THREE.Plane[]; raioX: boolean }) {
  const click = (ev: { stopPropagation: () => void; nativeEvent: MouseEvent }) => { ev.stopPropagation(); onClick(p.id, ev.nativeEvent.shiftKey); };
  const bordas = useMemo(() => (preset === "linhas-ocultas" || preset === "arestas" ? new THREE.EdgesGeometry(p.geo, 25) : null), [p.geo, preset]);
  const cor = sel ? SEL : REALISTA.has(preset) || preset === "unlit" ? p.corReal : preset === "linhas-ocultas" ? "#FFFFFF" : p.corDisciplina;
  const pbr = PBR[p.acab];
  const opac = sel ? 1 : raioX ? 0.18 : p.camada === "ia-rascunho" ? 0.55 : pbr.opac ?? 1;
  const transp = opac < 1;
  const luz = p.acab === "luz" && preset !== "luzes-off";
  const comum = { transparent: transp, opacity: opac, depthWrite: !transp, clippingPlanes: corte };
  const faces = preset !== "arestas" && preset !== "pontos" && preset !== "nos";
  const linhaCor = sel ? SEL : preset === "linhas-ocultas" ? "#111827" : p.corDisciplina;
  return (
    <group position={p.pos} quaternion={p.quat}>
      {faces && (
        <mesh geometry={p.geo} onClick={click} castShadow={REALISTA.has(preset)} receiveShadow={REALISTA.has(preset)}>
          {REALISTA.has(preset) ? <meshStandardMaterial color={cor} roughness={pbr.rough} metalness={pbr.metal} emissive={luz ? "#FFE7B0" : "#000000"} emissiveIntensity={luz ? 2 : 0} {...comum} />
            : preset === "solido" ? <meshLambertMaterial color={cor} flatShading {...comum} />
            : preset === "malha" ? <meshBasicMaterial color={cor} wireframe {...comum} />
            : <meshBasicMaterial color={cor} polygonOffset polygonOffsetFactor={1} polygonOffsetUnits={1} {...comum} />}
        </mesh>
      )}
      {bordas && <lineSegments geometry={bordas} onClick={click}><lineBasicMaterial color={linhaCor} clippingPlanes={corte} /></lineSegments>}
      {preset === "nos" && <mesh geometry={p.geo} onClick={click}><meshBasicMaterial color={linhaCor} wireframe clippingPlanes={corte} /></mesh>}
      {(preset === "nos" || preset === "pontos") && <points geometry={p.geo} onClick={click}><pointsMaterial color={preset === "pontos" ? linhaCor : "#111827"} size={preset === "pontos" ? 0.05 : 0.07} clippingPlanes={corte} /></points>}
    </group>
  );
}

function Exposicao({ valor, realista }: { valor: number; realista: boolean }) {
  const { gl } = useThree();
  useEffect(() => { gl.toneMapping = realista ? THREE.ACESFilmicToneMapping : THREE.NoToneMapping; gl.toneMappingExposure = realista ? valor : 1; }, [gl, valor, realista]);
  return null;
}

/** posiciona câmera e alvo para a vista pedida ou para a primeira pessoa */
function Camera({ vista, alvo, raio, olho, olhar, controles }: { vista: Vista; alvo: THREE.Vector3; raio: number; olho: THREE.Vector3 | null; olhar: THREE.Vector3 | null; controles: React.RefObject<OrbitControlsImpl | null> }) {
  const { camera } = useThree();
  useEffect(() => {
    if (olho && olhar) { camera.position.copy(olho); controles.current?.target.copy(olhar); controles.current?.update(); return; }
    const d = raio * 1.6;
    const pos: Record<Vista, [number, number, number]> = {
      perspectiva: [alvo.x + d * 0.7, alvo.y + d * 0.55, alvo.z + d * 0.7], topo: [alvo.x, alvo.y + d * 1.4, alvo.z + 0.001],
      "fachada-sul": [alvo.x, alvo.y, alvo.z + d], "fachada-norte": [alvo.x, alvo.y, alvo.z - d], "fachada-leste": [alvo.x + d, alvo.y, alvo.z], "fachada-oeste": [alvo.x - d, alvo.y, alvo.z],
    };
    camera.position.set(...pos[vista]); controles.current?.target.copy(alvo); controles.current?.update();
  }, [vista, alvo, raio, olho, olhar, camera, controles]);
  return null;
}

function diaDoAno(iso: string) { const d = new Date(`${iso}T12:00:00`); const ini = new Date(d.getFullYear(), 0, 0); return Math.round((d.getTime() - ini.getTime()) / 86400000); }

export function Cena3D() {
  const s = useMotor();
  const [webgl] = useState(() => { try { const c = document.createElement("canvas"); return !!(c.getContext("webgl2") || c.getContext("webgl")); } catch { return false; } });
  const reduzido = typeof window !== "undefined" && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  const pav = s.modelo.pavimentos;
  const [preset, setPreset] = useState<Preset>("realista-dia");
  const [vista, setVista] = useState<Vista>("perspectiva");
  const [hora, setHora] = useState(10);
  const [data, setData] = useState(() => new Date().toISOString().slice(0, 10));
  const [latitude, setLatitude] = useState(-23.55);
  const [exposicao, setExposicao] = useState(1);
  const [luzesArtificiais, setLuzesArtificiais] = useState(true);
  const [pessoasTecnicas, setPessoasTecnicas] = useState(false);
  const [raioX, setRaioX] = useState(() => useMotor.getState().destacar); // link de conflito: elementos do conflito à vista
  // seleção a enquadrar, fixada ao abrir (cliques posteriores não movem a câmera)
  const [foco] = useState(() => { const st = useMotor.getState(); return st.destacar && st.selecao.size ? new Set(st.selecao) : null; });
  const [corteZ, setCorteZ] = useState<number | null>(null);
  const [corteX, setCorteX] = useState<number | null>(null);
  const [verArmaduras, setVerArmaduras] = useState(false);
  const [aviso, setAviso] = useState<string | null>(null);
  const controles = useRef<OrbitControlsImpl | null>(null);
  const alvoSol = useMemo(() => new THREE.Object3D(), []);

  const visiveis = useMemo(() => {
    const todos: { e: Elemento; z: number; h: number }[] = [];
    for (const p of pav) { const z = p.elevacao, h = alturaPav(pav, p); for (const e of elementosVisiveis({ ...s, pavimentoId: p.id })) todos.push({ e, z, h }); }
    return todos;
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [s.tick, s.camadasVisiveis, s.ocultos, s.isolados, pav]);
  const pecas = useMemo(() => visiveis.flatMap(({ e, z, h }) => pecasDoElemento(s.modelo, e, z, h, pav)), [visiveis, s.modelo, pav]);
  const objetos = useMemo(() => visiveis.map((v) => v.e).filter((e): e is Objeto => e.tipo === "OBJETO"), [visiveis]);
  const luzes = useMemo(() => luzesDoModelo(s.modelo, objetos), [s.modelo, objetos]);
  const mostrarPessoas = !TECNICO.has(preset) || pessoasTecnicas;
  const desenhadas = pecas.filter((p) => (mostrarPessoas || !p.humano) && p.id !== s.primeiraPessoa); // em 1ª pessoa a própria figura não tapa a câmera
  const onClick = (id: string, shift: boolean) => s.selecionar([id], shift ? "alternar" : "substituir");
  const corte = useMemo(() => [...(corteZ == null ? [] : [new THREE.Plane(new THREE.Vector3(0, -1, 0), corteZ)]), ...(corteX == null ? [] : [new THREE.Plane(new THREE.Vector3(-1, 0, 0), corteX)])], [corteZ, corteX]);
  const dims = useDados<Dimensionamento[]>(["dims", s.obraId], `/obras/${s.obraId}/dimensionamentos`, { ativo: !!s.obraId && verArmaduras });
  const ultimo = (dims.data ?? []).find((d) => d.estado !== "FALHOU" && d.estado !== "PENDENTE" && d.estado !== "EM_EXECUCAO");
  const res = useDados<{ elementoId: string; detalhamento?: { posicoes: { posicao: string; funcao: string; geometria?: number[][][] }[] } }[]>(["dim", s.obraId, ultimo?.id], `/obras/${s.obraId}/dimensionamentos/${ultimo?.id}/resultados`, { ativo: !!ultimo && verArmaduras });
  const barras = useMemo(() => (res.data ?? []).flatMap((r) => (r.detalhamento?.posicoes ?? []).flatMap((p) => (p.geometria ?? []).map((g, i) => ({ chave: `${r.elementoId}-${p.posicao}-${i}`, longitudinal: !/estribo|transvers/i.test(p.funcao), pts: g.map((c) => new THREE.Vector3(c[0] ?? 0, c[2] ?? 0, -(c[1] ?? 0))) })))), [res.data]);
  const zMax = Math.max(3, ...pav.map((p) => p.elevacao + alturaPav(pav, p)));

  // enquadramento: caixa das peças visíveis
  const caixa = useMemo(() => { const b = new THREE.Box3(); for (const p of pecas) { p.geo.computeBoundingBox(); const bb = p.geo.boundingBox!.clone().applyMatrix4(new THREE.Matrix4().compose(p.pos, p.quat, new THREE.Vector3(1, 1, 1))); b.union(bb); } if (b.isEmpty()) b.set(new THREE.Vector3(0, 0, -16), new THREE.Vector3(20, 3, 0)); return b; }, [pecas]);
  // enquadra a seleção quando se chega por link de conflito; senão, o modelo todo
  const caixaFoco = useMemo(() => {
    if (!foco) return caixa;
    const b = new THREE.Box3(); for (const p of pecas) { if (!foco.has(p.id)) continue; p.geo.computeBoundingBox(); b.union(p.geo.boundingBox!.clone().applyMatrix4(new THREE.Matrix4().compose(p.pos, p.quat, new THREE.Vector3(1, 1, 1)))); }
    return b.isEmpty() ? caixa : b;
  }, [caixa, pecas, foco]);
  const alvo = useMemo(() => caixaFoco.getCenter(new THREE.Vector3()), [caixaFoco]);
  const raio = useMemo(() => Math.max(caixaFoco === caixa ? 6 : 2.5, caixaFoco.getSize(new THREE.Vector3()).length() / 2), [caixaFoco, caixa]);
  const xMin = caixa.min.x, xMax = caixa.max.x;

  // sol
  const sol = posicaoSolar(latitude, diaDoAno(data), hora);
  const dirSol = vetorSolar(sol.elevacao, sol.azimute);
  const diurno = preset === "realista-dia" && sol.elevacao > 0;
  const fundo = preset === "realista-dia" ? (sol.elevacao > 0 ? "#BFD9F2" : "#1E293B") : REALISTA.has(preset) ? "#0B1120" : "#F3F4F6";
  const luzesAcesas = (preset === "realista-noite" || (preset === "realista-dia" && luzesArtificiais)) ? luzes.filter((l) => l.ligada).slice(0, 16) : [];

  // primeira pessoa: olho a 93% da altura da pessoa, olhando para a frente dela
  const pessoa = s.primeiraPessoa ? s.modelo.elemento(s.primeiraPessoa) as Objeto | undefined : undefined;
  // piso: cota absoluta sob os pés (sobe e desce escadas e rampas do pavimento da pessoa)
  const [passo, setPasso] = useState<{ x: number; y: number; ang: number; piso: number } | null>(null);
  const elevPav = pessoa ? s.modelo.pavimento(pessoa.pavimentoId)?.elevacao ?? 0 : 0;
  useEffect(() => { setPasso(pessoa ? { x: pessoa.centro.x, y: pessoa.centro.y, ang: ((pessoa.rotacao + 90) * Math.PI) / 180, piso: elevPav + pessoa.elevacao } : null); }, [pessoa?.id]); // eslint-disable-line react-hooks/exhaustive-deps
  const zPessoa = pessoa && passo ? passo.piso + pessoa.dim.a * 0.93 : 0;
  /** novo piso ao pisar em (x, y): sobre escada/rampa segue o degrau; saindo pelo topo fica no nível de cima, pelo pé volta ao piso */
  const pisoEm = (x: number, y: number, anterior: number): number => {
    if (!pessoa) return anterior;
    const p = { x, y }; let sobre = false, piso = anterior;
    for (const e of s.modelo.elementosDo(pessoa.pavimentoId)) {
      if (e.tipo !== "ESCADA" && e.tipo !== "RAMPA") continue;
      const h = alturaCirculacao(e, p);
      if (h != null) { sobre = true; piso = elevPav + h; break; }
      const { t, dentroLateral } = posicaoNaCirculacao(e, p);
      if (dentroLateral && t > 1 && t < 1 + 0.5 / Math.max(0.3, Math.hypot(e.p2.x - e.p1.x, e.p2.y - e.p1.y)) && Math.abs(anterior - (elevPav + e.desnivel)) < 0.3) { sobre = true; piso = elevPav + e.desnivel; }
      if (dentroLateral && t < 0 && t > -0.5 && anterior - elevPav < 0.3) { sobre = true; piso = elevPav; }
    }
    return sobre ? Math.round(piso * 1000) / 1000 : anterior;
  };
  /** pavimento cujo piso é o de referência para colisão (o mais alto com elevação ≤ piso atual) */
  const pavDoPiso = (piso: number) => [...s.modelo.pavimentos].sort((a, b) => a.elevacao - b.elevacao).filter((p) => p.elevacao <= piso + 0.05).pop()?.id ?? pessoa?.pavimentoId ?? "";
  const olho = pessoa && passo ? new THREE.Vector3(passo.x, zPessoa, -passo.y) : null;
  const olhar = pessoa && passo ? new THREE.Vector3(passo.x + Math.cos(passo.ang) * 2, zPessoa, -(passo.y + Math.sin(passo.ang) * 2)) : null;
  const caixaRef = useRef<HTMLDivElement>(null);
  const andar = (frente: number, giro: number) => {
    if (!pessoa || !passo) return;
    const ang = passo.ang + giro, nx = passo.x + Math.cos(ang) * frente, ny = passo.y + Math.sin(ang) * frente;
    const piso = frente !== 0 ? pisoEm(nx, ny, passo.piso) : passo.piso;
    const obstaculo = frente !== 0 ? colisao(nx, ny, pessoa, pavDoPiso(piso)) : null;
    if (obstaculo) { setAviso(`Colisão: ${obstaculo}. A pessoa não atravessa paredes nem objetos.`); setPasso({ ...passo, ang }); return; }
    setAviso(null); setPasso({ x: nx, y: ny, ang, piso });
  };
  /** grava no modelo a posição, a direção e a altura do piso alcançadas na caminhada (MOVER + ALTERAR, passam pelo auditor) */
  const gravarPosicao = async () => {
    if (!pessoa || !passo) return;
    const rot = Math.round(((((passo.ang * 180) / Math.PI - 90) % 360) + 360) % 360 * 1000) / 1000;
    const ok = await s.aplicarLote([{ codigo: "MOVER", args: { sel: [pessoa.id], base: pessoa.centro, destino: { x: Math.round(passo.x * 1000) / 1000, y: Math.round(passo.y * 1000) / 1000 } } },
      { codigo: "ALTERAR", args: { id: pessoa.id, props: { rotacao: rot, elevacao: Math.round((passo.piso - elevPav) * 1000) / 1000 } } }]);
    setAviso(ok ? null : "Não foi possível gravar a posição: veja o motivo na linha de comando.");
  };
  function colisao(x: number, y: number, eu: Objeto, pavId: string): string | null {
    const r = Math.max(eu.dim.l, eu.dim.p) / 2, p: Ponto2D = { x, y };
    // acima do pavimento da pessoa só se anda sobre laje (ou escada/rampa): fora dela é a borda
    if (pavId !== eu.pavimentoId && !s.modelo.elementosDo(pavId).some((e) => e.tipo === "LAJE" && pontoDentroPoligono(p, e.contorno))
      && !s.modelo.elementosDo(eu.pavimentoId).some((e) => (e.tipo === "ESCADA" || e.tipo === "RAMPA") && alturaCirculacao(e, p) != null)) return "borda da laje (não há piso adiante)";
    for (const e of s.modelo.elementosDo(pavId)) {
      if (e.id === eu.id) continue;
      if (e.tipo === "PAREDE") {
        const vaos = s.modelo.elementos.filter((a) => a.tipo === "PORTA" && a.paredeId === e.id) as { posicao: number; largura: number }[];
        const L = Math.hypot(e.p2.x - e.p1.x, e.p2.y - e.p1.y) || 1, ux = (e.p2.x - e.p1.x) / L, uy = (e.p2.y - e.p1.y) / L;
        const t = (x - e.p1.x) * ux + (y - e.p1.y) * uy, d = Math.abs(-(x - e.p1.x) * uy + (y - e.p1.y) * ux);
        if (t < -r || t > L + r || d > e.espessura / 2 + r) continue;
        if (vaos.some((v) => Math.abs(t - v.posicao * L) < v.largura / 2 - r * 0.5)) continue; // passa pelo vão da porta
        return `parede ${e.rotulo ?? s.modelo.rotuloAutomatico(e)}`;
      }
      if (e.tipo === "OBJETO") {
        const it = itemObjeto(e.item); if (!it || it.sobreposicaoPermitida || e.elevacao > 1.5 || e.elevacao + e.dim.a < 0.3) continue;
        const c = cantosObjeto(e), cx = c.reduce((a, q) => a + q.x / 4, 0), cy = c.reduce((a, q) => a + q.y / 4, 0);
        const exp = c.map((q) => { const dx = q.x - cx, dy = q.y - cy, n = Math.hypot(dx, dy) || 1; return { x: q.x + (dx / n) * r, y: q.y + (dy / n) * r }; });
        if (pontoDentroPoligono(p, exp)) return `${it.nome} (${e.rotulo ?? s.modelo.rotuloAutomatico(e)})`;
      }
    }
    return null;
  }
  useEffect(() => {
    if (!pessoa) return;
    const f = (ev: KeyboardEvent) => {
      if (!caixaRef.current?.contains(document.activeElement)) return;
      const k = ev.key.toLowerCase(); const m: Record<string, [number, number]> = { w: [0.25, 0], arrowup: [0.25, 0], s: [-0.25, 0], arrowdown: [-0.25, 0], a: [0, Math.PI / 12], arrowleft: [0, Math.PI / 12], d: [0, -Math.PI / 12], arrowright: [0, -Math.PI / 12] };
      if (m[k]) { ev.preventDefault(); andar(...m[k]!); }
    };
    window.addEventListener("keydown", f); return () => window.removeEventListener("keydown", f);
  });

  if (!webgl) return <div role="status" className="h-full grid place-items-center p-6 text-sm text-text-3">WebGL indisponível neste dispositivo: use a vista Planta (2D).</div>;
  const eixos = s.modelo.eixos;
  const desc = PRESETS.find((p) => p.id === preset)!.desc;
  return (
    <div className="h-full w-full relative" ref={caixaRef}>
      <div className="absolute z-10 top-2 left-2 right-2 flex flex-col gap-1 rounded bg-bg/90 border border-line-soft px-2 py-1 text-xs max-w-[46rem]" role="group" aria-label="Opções da vista 3D">
        <div className="flex flex-wrap items-center gap-2">
          <label className="flex items-center gap-1 font-medium">Modo
            <select aria-label="Modo de exibição" value={preset} onChange={(e) => setPreset(e.target.value as Preset)} className="min-h-8 rounded border border-line-soft bg-bg px-1">
              {PRESETS.map((p) => <option key={p.id} value={p.id}>{p.rotulo}</option>)}
            </select>
          </label>
          <label className="flex items-center gap-1">Vista
            <select aria-label="Vista da câmera" value={pessoa ? "primeira" : vista} onChange={(e) => { s.setPrimeiraPessoa(null); setVista(e.target.value as Vista); }} className="min-h-8 rounded border border-line-soft bg-bg px-1">
              {VISTAS.map((v) => <option key={v.id} value={v.id}>{v.rotulo}</option>)}
              {pessoa && <option value="primeira">1ª pessoa</option>}
            </select>
          </label>
          <label className="flex items-center gap-1"><input type="checkbox" checked={raioX} onChange={(e) => setRaioX(e.target.checked)} />Raio X</label>
          <label className="flex items-center gap-1"><input type="checkbox" checked={corteZ != null} onChange={(e) => setCorteZ(e.target.checked ? Math.min(zMax, 1.5) : null)} />Corte horizontal</label>
          {corteZ != null && <label className="flex items-center gap-1">z {corteZ.toFixed(2)} m<input type="range" min={-1} max={zMax} step={0.05} value={corteZ} onChange={(e) => setCorteZ(Number(e.target.value))} aria-label="Altura do corte horizontal (m)" /></label>}
          <label className="flex items-center gap-1"><input type="checkbox" checked={corteX != null} onChange={(e) => setCorteX(e.target.checked ? (xMin + xMax) / 2 : null)} />Corte vertical</label>
          {corteX != null && <label className="flex items-center gap-1">x {corteX.toFixed(2)} m<input type="range" min={xMin} max={xMax} step={0.05} value={corteX} onChange={(e) => setCorteX(Number(e.target.value))} aria-label="Posição do corte vertical (m)" /></label>}
          {TECNICO.has(preset) && <label className="flex items-center gap-1"><input type="checkbox" checked={pessoasTecnicas} onChange={(e) => setPessoasTecnicas(e.target.checked)} />Pessoas nesta vista</label>}
          {s.obraId && <label className="flex items-center gap-1"><input type="checkbox" checked={verArmaduras} onChange={(e) => setVerArmaduras(e.target.checked)} />Armaduras do dimensionamento</label>}
          {verArmaduras && <span className="text-text-3">{!ultimo ? "nenhum dimensionamento" : `${barras.length} barras · ${ultimo.estado === "OBSOLETO" ? "resultado desatualizado" : "último dimensionamento"}`}</span>}
        </div>
        {REALISTA.has(preset) && (
          <div className="flex flex-wrap items-center gap-2">
            {preset === "realista-dia" && <>
              <label className="flex items-center gap-1">Hora solar {String(Math.floor(hora)).padStart(2, "0")}:{String(Math.round((hora % 1) * 60)).padStart(2, "0")}<input type="range" min={0} max={24} step={0.25} value={hora} onChange={(e) => setHora(Number(e.target.value))} aria-label="Hora solar" /></label>
              <label className="flex items-center gap-1">Data<input type="date" value={data} onChange={(e) => setData(e.target.value)} className="min-h-8 rounded border border-line-soft bg-bg px-1" /></label>
              <label className="flex items-center gap-1">Latitude<input type="number" step={0.1} value={latitude} onChange={(e) => setLatitude(Number(e.target.value))} className="w-16 min-h-8 rounded border border-line-soft bg-bg px-1" aria-label="Latitude em graus (sul negativo)" /></label>
              <label className="flex items-center gap-1"><input type="checkbox" checked={luzesArtificiais} onChange={(e) => setLuzesArtificiais(e.target.checked)} />Luzes artificiais</label>
            </>}
            <label className="flex items-center gap-1">Exposição {exposicao.toFixed(2)}<input type="range" min={0.2} max={3} step={0.05} value={exposicao} onChange={(e) => setExposicao(Number(e.target.value))} aria-label="Exposição" /></label>
            <span className="text-text-3">{preset === "realista-dia" ? `Sol: elevação ${sol.elevacao.toFixed(1)}°, azimute ${sol.azimute.toFixed(0)}° (norte = +y da planta)` : `${luzes.filter((l) => l.ligada).length} de ${luzes.length} luminária(s) ligada(s)`}</span>
          </div>
        )}
        <p className="text-text-3" aria-live="polite">{desc}{pessoa ? " · 1ª pessoa: clique na cena e use W/S para andar e A/D para girar; escadas e rampas sobem e descem." : ""}</p>
        {pessoa && passo && <div className="flex flex-wrap items-center gap-2"><span className="text-text-2">Piso a {passo.piso.toFixed(2)} m</span><button type="button" onClick={() => void gravarPosicao()} disabled={!s.podeEditar} className="min-h-8 rounded border border-line-soft px-2 hover:bg-surface disabled:opacity-50">Gravar posição da pessoa</button></div>}
        {aviso && <p role="alert" className="text-danger">{aviso}</p>}
      </div>
      <div className="h-full w-full" aria-label={`Cena 3D em modo ${PRESETS.find((p) => p.id === preset)!.rotulo}, ${visiveis.length} elementos${verArmaduras ? ` e ${barras.length} barras` : ""}`} role="img" tabIndex={0} data-teclado-proprio={pessoa ? "" : undefined}>
      <Canvas shadows gl={{ localClippingEnabled: true, preserveDrawingBuffer: true }} camera={{ position: [18, 14, 18], fov: 50, near: 0.05, far: 800 }} dpr={[1, 1.5]} frameloop={reduzido ? "demand" : "always"} onPointerMissed={() => s.selecionar([], "substituir")}>
        <color attach="background" args={[fundo]} />
        <Exposicao valor={exposicao} realista={REALISTA.has(preset)} />
        <Camera vista={vista} alvo={alvo} raio={raio} olho={olho} olhar={olhar} controles={controles} />
        {preset === "realista-dia" && <>
          <hemisphereLight args={["#DCEBFA", "#8A7F6E", diurno ? 0.6 : 0.08]} />
          {diurno && <directionalLight position={[alvo.x + dirSol[0] * 60, alvo.y + dirSol[1] * 60, alvo.z + dirSol[2] * 60]} intensity={0.6 + 2.4 * Math.sin((sol.elevacao * Math.PI) / 180)} color={sol.elevacao < 10 ? "#FFD6A0" : "#FFF8EC"} castShadow shadow-mapSize={[2048, 2048]} shadow-camera-left={-raio * 1.5} shadow-camera-right={raio * 1.5} shadow-camera-top={raio * 1.5} shadow-camera-bottom={-raio * 1.5} shadow-camera-far={200} shadow-bias={-0.0005} target={alvoSol} />}
          <primitive object={alvoSol} position={[alvo.x, 0, alvo.z]} />
        </>}
        {(preset === "realista-noite" || preset === "luzes-off") && <><ambientLight intensity={0.04} color="#93A4C3" /><directionalLight position={[alvo.x - 30, 40, alvo.z + 20]} intensity={0.05} color="#9DB4E0" /></>}
        {preset === "solido" && <><ambientLight intensity={0.55} /><directionalLight position={[20, 30, 10]} intensity={1.6} /></>}
        {/* à noite as 4 primeiras luminárias acesas projetam sombra (a luz não atravessa paredes); de dia o sol domina e as luminárias só iluminam, por desempenho */}
        {luzesAcesas.map((l, i) => <pointLight key={l.id} position={l.pos} intensity={l.candela} distance={l.alcance} decay={2} color={corDaTemperatura(l.temperaturaK)} castShadow={preset === "realista-noite" && i < SOMBRAS_LUZES} shadow-mapSize={[512, 512]} shadow-bias={-0.002} shadow-camera-near={0.05} shadow-camera-far={l.alcance} />)}
        <Suspense fallback={null}>
          {!REALISTA.has(preset) && <Grid args={[80, 80]} cellSize={1} sectionSize={5} cellColor="#D1D5DB" sectionColor="#9CA3AF" fadeDistance={80} position={[0, -0.01, 0]} />}
          {REALISTA.has(preset) && <mesh rotation={[-Math.PI / 2, 0, 0]} position={[alvo.x, -0.02, alvo.z]} receiveShadow><planeGeometry args={[raio * 8, raio * 8]} /><meshStandardMaterial color={preset === "realista-dia" ? "#9CA38F" : "#1F2933"} roughness={1} /></mesh>}
          {eixos.map((ex) => { const a = ex.direcao === "X" ? [ex.posicao, 0.02, -ex.inicio] : [ex.inicio, 0.02, -ex.posicao]; const b = ex.direcao === "X" ? [ex.posicao, 0.02, -ex.fim] : [ex.fim, 0.02, -ex.posicao]; const geo = new THREE.BufferGeometry().setFromPoints([new THREE.Vector3(...(a as [number, number, number])), new THREE.Vector3(...(b as [number, number, number]))]); return <group key={ex.id}><primitive object={new THREE.Line(geo, new THREE.LineDashedMaterial({ color: "#6B7280", dashSize: 0.5, gapSize: 0.25 }))} /><Text position={[b[0]! + (ex.direcao === "Y" ? 0.6 : 0), 0.05, b[2]! - (ex.direcao === "X" ? 0.6 : 0)]} rotation={[-Math.PI / 2, 0, 0]} fontSize={0.4} color="#374151">{ex.nome}</Text></group>; })}
          {desenhadas.map((p) => <PecaView key={p.chave} p={p} preset={preset} sel={s.selecao.has(p.id)} onClick={onClick} corte={corte} raioX={raioX} />)}
          {visiveis.filter(({ e }) => e.tipo === "AMBIENTE" || e.tipo === "TEXTO" || e.tipo === "ETIQUETA").map(({ e, z }) => { const p = e.tipo === "AMBIENTE" ? e.ponto : (e as { p: Ponto2D }).p; return <Text key={e.id} position={[p.x, z + 0.05, -p.y]} rotation={[-Math.PI / 2, 0, 0]} fontSize={0.35} color={REALISTA.has(preset) && preset !== "realista-dia" ? "#E5E7EB" : "#111827"} anchorX="center">{e.tipo === "AMBIENTE" ? e.nome : (e as { texto: string }).texto}</Text>; })}
          {verArmaduras && barras.map((b) => <primitive key={b.chave} object={new THREE.Line(new THREE.BufferGeometry().setFromPoints(b.pts), new THREE.LineBasicMaterial({ color: b.longitudinal ? "#B91C1C" : "#0F766E", clippingPlanes: corte }))} />)}
        </Suspense>
        <OrbitControls ref={controles} makeDefault enableDamping={!reduzido} enablePan={!pessoa} enableZoom={!pessoa} />
      </Canvas>
      </div>
    </div>
  );
}
