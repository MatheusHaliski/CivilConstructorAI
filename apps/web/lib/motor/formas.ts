/**
 * Formas 3D procedurais dos objetos da biblioteca (AUD-02): cada `forma` do catálogo vira um conjunto de peças
 * (caixas, cilindros, esferas) em coordenadas locais do objeto — x ao longo da largura l, y ao longo da profundidade p
 * (+y é a frente), z para cima a partir da base. As peças escalam com as dimensões do objeto, então o 3D mostra a
 * mesma posição, dimensões e identificação da planta. Não são modelos de fabricante.
 */
import type { Objeto } from "@cca/notacao-vi";

export type Acabamento = "padrao" | "tecido" | "madeira" | "metal" | "ceramica" | "vidro" | "tela" | "pele" | "folha" | "luz";
export type Peca = { g: "caixa" | "cilindro" | "esfera"; c: [number, number, number]; s: [number, number, number]; eixo?: "x" | "y" | "z"; acab: Acabamento; cor?: string };

/** rugosidade e metalicidade por acabamento (PBR); vidro é transparente */
export const PBR: Record<Acabamento, { rough: number; metal: number; opac?: number }> = {
  padrao: { rough: 0.7, metal: 0 }, tecido: { rough: 0.95, metal: 0 }, madeira: { rough: 0.6, metal: 0 }, metal: { rough: 0.3, metal: 0.8 },
  ceramica: { rough: 0.15, metal: 0 }, vidro: { rough: 0.05, metal: 0, opac: 0.3 }, tela: { rough: 0.2, metal: 0.2 }, pele: { rough: 0.8, metal: 0 },
  folha: { rough: 0.9, metal: 0 }, luz: { rough: 0.4, metal: 0 },
};

const box = (c: [number, number, number], s: [number, number, number], acab: Acabamento = "padrao", cor?: string): Peca => ({ g: "caixa", c, s, acab, cor });
const cil = (c: [number, number, number], d: number, h: number, acab: Acabamento = "padrao", cor?: string, eixo: "x" | "y" | "z" = "z"): Peca => ({ g: "cilindro", c, s: [d, d, h], acab, cor, eixo });
const esf = (c: [number, number, number], d: number, acab: Acabamento = "padrao", cor?: string): Peca => ({ g: "esfera", c, s: [d, d, d], acab, cor });

export function pecasDaForma(forma: string, l: number, p: number, a: number, cor: string): Peca[] {
  const pernas = (h: number, d = 0.045) => [[-1, -1], [1, -1], [1, 1], [-1, 1]].map(([i, j]) => cil([i! * (l / 2 - 0.06), j! * (p / 2 - 0.06), h / 2], d, h, "madeira", "#6B4F3A"));
  switch (forma) {
    case "sofa": case "poltrona": {
      const braco = Math.min(0.18, l * 0.12);
      return [box([0, 0.05 * p, 0.21 * a], [l, 0.9 * p, 0.42 * a], "tecido", cor), box([0, -0.4 * p, a / 2], [l, 0.2 * p, a], "tecido", cor),
        box([-(l / 2 - braco / 2), 0, 0.33 * a], [braco, p, 0.66 * a], "tecido", cor), box([l / 2 - braco / 2, 0, 0.33 * a], [braco, p, 0.66 * a], "tecido", cor)];
    }
    case "mesa": return [box([0, 0, a - 0.02], [l, p, 0.04], "madeira", cor), ...pernas(a - 0.04)];
    case "cadeira": return [box([0, 0, 0.5 * a], [l, p, 0.04], "madeira", cor), box([0, -p / 2 + 0.02, 0.75 * a], [l, 0.04, 0.5 * a], "madeira", cor), ...pernas(0.5 * a - 0.02, 0.035)];
    case "cama": return [box([0, 0, 0.17 * a], [l, p, 0.34 * a], "madeira", "#8B6B4A"), box([0, 0.03, 0.45 * a], [l - 0.04, p - 0.1, 0.22 * a], "tecido", cor), box([0, -p / 2 + 0.03, a / 2], [l, 0.06, a], "madeira", "#7A5C3E")];
    case "armario": return [box([0, 0, a / 2], [l, p, a], "madeira", cor), cil([-0.04, p / 2 + 0.01, a / 2], 0.02, 0.3, "metal", "#9CA3AF"), cil([0.04, p / 2 + 0.01, a / 2], 0.02, 0.3, "metal", "#9CA3AF")];
    case "geladeira": return [box([0, 0, a / 2], [l, p, a], "metal", cor), cil([l / 2 - 0.08, p / 2 + 0.02, 0.6 * a], 0.025, 0.5, "metal", "#6B7280"), box([0, p / 2 + 0.001, 0.68 * a], [l - 0.02, 0.004, 0.01], "metal", "#9CA3AF")];
    case "fogao": return [box([0, 0, a / 2], [l, p, a], "metal", cor), ...[[-1, -1], [1, -1], [1, 1], [-1, 1]].map(([i, j]) => cil([i! * l / 4, j! * p / 4, a + 0.01], Math.min(l, p) * 0.22, 0.02, "metal", "#111827"))];
    case "maquina": return [box([0, 0, a / 2], [l, p, a], "metal", cor), cil([0, p / 2 + 0.01, 0.45 * a], Math.min(l, a) * 0.55, 0.02, "vidro", "#93C5FD", "y")];
    case "pia": return [box([0, 0, a / 2], [l, p, a], "padrao", cor), box([0, 0.02, a + 0.005], [Math.min(0.6, l * 0.5), p * 0.7, 0.01], "metal", "#9CA3AF"), cil([0, -p / 2 + 0.08, a + 0.12], 0.025, 0.24, "metal", "#D1D5DB")];
    case "lavatorio": return [box([0, 0, a / 2], [l, p, a], "ceramica", cor), box([0, 0.02, a - 0.02], [l * 0.7, p * 0.6, 0.04], "ceramica", "#E5E7EB"), cil([0, -p / 2 + 0.06, a + 0.08], 0.025, 0.16, "metal", "#D1D5DB")];
    case "vaso": return [cil([0, 0.08 * p, 0.2 * a], Math.min(l, p) * 0.9, 0.4 * a, "ceramica", cor), box([0, -p / 2 + 0.09, 0.5 * a], [l, 0.18, 0.6 * a], "ceramica", cor)];
    case "chuveiro": return [cil([0, 0, a / 2], Math.max(l, p), a, "metal", cor), cil([0, -p / 2, a / 2], 0.02, 0.3, "metal", "#9CA3AF", "y")];
    case "ralo": case "tapete": return [box([0, 0, a / 2], [l, p, a], forma === "tapete" ? "tecido" : "metal", cor)];
    case "tela": return [box([0, 0, a / 2], [l, p, a], "tela", "#111827"), box([0, p / 2 + 0.001, a / 2], [l - 0.04, 0.002, a - 0.04], "tela", "#1F2937")];
    case "quadro": return [box([0, 0, a / 2], [l, p, a], "madeira", "#78350F"), box([0, p / 2 + 0.001, a / 2], [l - 0.06, 0.002, a - 0.06], "padrao", cor)];
    case "planta": return [cil([0, 0, 0.18 * a], Math.min(l, p) * 0.6, 0.36 * a, "ceramica", "#B45309"), esf([0, 0, 0.66 * a], Math.min(l, p, 0.64 * a), "folha", cor)];
    case "escultura": return [box([0, 0, 0.35 * a], [l * 0.7, p * 0.7, 0.7 * a], "padrao", "#E5E7EB"), esf([0, 0, 0.85 * a], Math.min(l, p, 0.3 * a), "metal", cor)];
    case "pendente": return [cil([0, 0, 0.65 * a], 0.01, 0.7 * a, "metal", "#374151"), cil([0, 0, 0.15 * a], l, 0.3 * a, "metal", cor), esf([0, 0, 0.08 * a], Math.min(l, 0.12), "luz", "#FEF3C7")];
    case "plafon": return [cil([0, 0, a / 2], l, a, "luz", "#FEF3C7")];
    case "spot": return [cil([0, 0, a / 2], l, a, "metal", cor), cil([0, 0, 0.005], l * 0.6, 0.01, "luz", "#FEF3C7")];
    case "arandela": return [box([0, -p / 2 + 0.01, a / 2], [l * 0.6, 0.02, a], "metal", cor), cil([0, 0.02, a / 2], Math.min(l, p) * 0.8, a * 0.6, "luz", "#FEF3C7")];
    case "tomada": return [box([0, 0, a / 2], [l, p, a], "padrao", "#F9FAFB"), box([0, p / 2 + 0.001, a / 2], [l * 0.5, 0.002, a * 0.4], "padrao", "#6B7280")];
    case "humano": {
      // proporções aproximadas de figura humana (escala uniforme pela altura)
      const perna = 0.47 * a, tronco = 0.33 * a, cabeca = 0.13 * a;
      return [cil([-l * 0.18, 0, perna / 2], l * 0.24, perna, "tecido", "#1F2937"), cil([l * 0.18, 0, perna / 2], l * 0.24, perna, "tecido", "#1F2937"),
        box([0, 0, perna + tronco / 2], [l * 0.75, p * 0.8, tronco], "tecido", cor), cil([-l * 0.44, 0, perna + tronco * 0.55], l * 0.14, tronco * 1.05, "pele", "#D6A77A"),
        cil([l * 0.44, 0, perna + tronco * 0.55], l * 0.14, tronco * 1.05, "pele", "#D6A77A"), cil([0, 0, perna + tronco + 0.02 * a], l * 0.18, 0.04 * a, "pele", "#D6A77A"),
        esf([0, 0, a - cabeca / 2], cabeca, "pele", "#D6A77A")];
    }
    default: return [box([0, 0, a / 2], [l, p, a], "padrao", cor)];
  }
}

export function pecasDoObjeto(o: Objeto, forma: string, cor: string): Peca[] {
  return pecasDaForma(forma, o.dim.l, o.dim.p, o.dim.a, cor);
}

/** temperatura de cor (K) → RGB aproximado (ajuste de curva de Tanner Helland sobre o corpo negro), para a cor da luz */
export function corDaTemperatura(k: number): string {
  const t = Math.max(1000, Math.min(40000, k)) / 100;
  const r = t <= 66 ? 255 : 329.698727446 * Math.pow(t - 60, -0.1332047592);
  const g = t <= 66 ? 99.4708025861 * Math.log(t) - 161.1195681661 : 288.1221695283 * Math.pow(t - 60, -0.0755148492);
  const b = t >= 66 ? 255 : t <= 19 ? 0 : 138.5177312231 * Math.log(t - 10) - 305.0447927307;
  const h = (v: number) => Math.round(Math.max(0, Math.min(255, v))).toString(16).padStart(2, "0");
  return `#${h(r)}${h(g)}${h(b)}`;
}

/**
 * Posição solar aproximada (declinação de Cooper e ângulo horário, hora solar local): elevação e azimute em graus,
 * azimute a partir do norte no sentido horário. Norte do projeto = +y da planta. Sem equação do tempo nem refração.
 */
export function posicaoSolar(latitudeGraus: number, diaDoAno: number, horaSolar: number): { elevacao: number; azimute: number } {
  const rad = Math.PI / 180;
  const dec = 23.45 * Math.sin(rad * (360 / 365) * (284 + diaDoAno));
  const h = 15 * (horaSolar - 12);
  const lat = latitudeGraus * rad, d = dec * rad, hr = h * rad;
  const sinEl = Math.sin(lat) * Math.sin(d) + Math.cos(lat) * Math.cos(d) * Math.cos(hr);
  const el = Math.asin(Math.max(-1, Math.min(1, sinEl)));
  const cosAz = (Math.sin(d) - Math.sin(el) * Math.sin(lat)) / (Math.cos(el) * Math.cos(lat) || 1e-9);
  let az = Math.acos(Math.max(-1, Math.min(1, cosAz))) / rad;
  if (h > 0) az = 360 - az;
  return { elevacao: el / rad, azimute: az };
}

/** direção para o sol em coordenadas da cena three (x = leste, y = cima, z = −norte) */
export function vetorSolar(elevacao: number, azimute: number): [number, number, number] {
  const rad = Math.PI / 180, e = elevacao * rad, a = azimute * rad;
  return [Math.cos(e) * Math.sin(a), Math.sin(e), -Math.cos(e) * Math.cos(a)];
}
