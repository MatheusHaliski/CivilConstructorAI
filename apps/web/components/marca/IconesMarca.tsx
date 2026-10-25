/**
 * Ícones de navegação derivados do ícone oficial do CivilConstructorAI (apps/web/public/marca/civilconstructorai.webp):
 * traço grafite (#2A2420) de 1,75 px, preenchimento amarelo do capacete (#F0B000), nós laranja da malha (#F06000)
 * e disco creme do selo (#F5E6C8). Cada ícone usa `currentColor` no traço para herdar o estado (ativo/inativo).
 */
import type { SVGProps } from "react";

export type IconeMarcaProps = SVGProps<SVGSVGElement> & { tamanho?: number; ativo?: boolean };

const AM = "#F0B000", LA = "#F06000", CR = "#F5E6C8";

function Base({ tamanho = 22, ativo, children, ...rest }: IconeMarcaProps) {
  return (
    <svg width={tamanho} height={tamanho} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.75} strokeLinecap="round" strokeLinejoin="round" aria-hidden focusable="false" {...rest}>
      {ativo && <circle cx="12" cy="12" r="11.2" fill={CR} stroke="none" />}
      {children}
    </svg>
  );
}

/** Obras: capacete amarelo (centro do ícone oficial) */
export const IconeObras = (p: IconeMarcaProps) => (
  <Base {...p}><path d="M4 15a8 8 0 0 1 16 0" fill={AM} /><path d="M10 7.5V11M14 7.5V11M9.5 7.6h5" /><path d="M3 15h18M5 17.5h14" /></Base>
);
/** Modelo BIM: guindaste + grade de eixos */
export const IconeModelo = (p: IconeMarcaProps) => (
  <Base {...p}><path d="M4 20V9M4 9l9-4M13 5v3M8 20V11" /><path d="M13 8h7l-1.5 3" /><path d="M17 8v6" /><rect x="15.5" y="14" width="3" height="2.5" fill={AM} /><circle cx="4" cy="9" r="1.4" fill={LA} stroke="none" /><path d="M3 20h18" /></Base>
);
/** Estrutura: pórtico pilar-viga com nós da malha */
export const IconeEstrutura = (p: IconeMarcaProps) => (
  <Base {...p}><path d="M5 20V8M19 20V8M5 8h14M5 14h14" /><circle cx="5" cy="8" r="1.5" fill={LA} stroke="none" /><circle cx="19" cy="8" r="1.5" fill={LA} stroke="none" /><circle cx="5" cy="14" r="1.5" fill={AM} stroke="none" /><circle cx="19" cy="14" r="1.5" fill={AM} stroke="none" /><path d="M3 20h18" /></Base>
);
/** Dimensionamento: seção de viga com armaduras */
export const IconeDimensionamento = (p: IconeMarcaProps) => (
  <Base {...p}><rect x="5" y="4" width="14" height="16" rx="1.5" /><rect x="8" y="7" width="8" height="10" rx="1" strokeDasharray="2 1.5" /><circle cx="9" cy="16" r="1.3" fill={LA} stroke="none" /><circle cx="15" cy="16" r="1.3" fill={LA} stroke="none" /><circle cx="9" cy="8" r="1.3" fill={AM} stroke="none" /><circle cx="15" cy="8" r="1.3" fill={AM} stroke="none" /></Base>
);
/** Geotecnia: camadas de solo e furo SPT */
export const IconeGeotecnia = (p: IconeMarcaProps) => (
  <Base {...p}><path d="M3 9c3-1.5 6-1.5 9 0s6 1.5 9 0M3 14c3-1.5 6-1.5 9 0s6 1.5 9 0M3 19c3-1.5 6-1.5 9 0s6 1.5 9 0" /><path d="M12 3v15" /><circle cx="12" cy="4" r="1.6" fill={AM} stroke="none" /><circle cx="12" cy="9.5" r="1.2" fill={LA} stroke="none" /></Base>
);
/** Quantitativos: rolos de projeto (base do ícone oficial) */
export const IconeQuantitativos = (p: IconeMarcaProps) => (
  <Base {...p}><rect x="3" y="10" width="13" height="7" rx="3.5" fill={CR} /><rect x="7" y="7" width="13" height="7" rx="3.5" fill={CR} /><circle cx="16.5" cy="10.5" r="1.4" fill={AM} stroke="none" /><circle cx="6.5" cy="13.5" r="1.4" fill={LA} stroke="none" /><path d="M4 20h16" /></Base>
);
/** Orçamento: planilha com nós */
export const IconeOrcamento = (p: IconeMarcaProps) => (
  <Base {...p}><rect x="4" y="4" width="16" height="16" rx="2" /><path d="M4 10h16M10 4v16" /><circle cx="15" cy="15" r="1.6" fill={AM} stroke="none" /><circle cx="7" cy="7" r="1.3" fill={LA} stroke="none" /></Base>
);
/** Pranchas: folha com carimbo e eixo recuado */
export const IconePranchas = (p: IconeMarcaProps) => (
  <Base {...p}><rect x="3.5" y="4" width="17" height="16" rx="1.5" /><rect x="13" y="15" width="6" height="3.5" fill={AM} stroke="none" /><path d="M7 8h7M7 11.5h5" /><circle cx="6" cy="16" r="1.4" fill={LA} stroke="none" /></Base>
);
/** Interoperabilidade (IFC/STEP): cubo com nó */
export const IconeInterop = (p: IconeMarcaProps) => (
  <Base {...p}><path d="M12 3l8 4.5v9L12 21l-8-4.5v-9L12 3z" /><path d="M12 12l8-4.5M12 12v9M12 12L4 7.5" /><circle cx="12" cy="12" r="1.7" fill={LA} stroke="none" /><circle cx="12" cy="3.5" r="1.3" fill={AM} stroke="none" /></Base>
);
/** IA: malha de nós do anel do ícone oficial */
export const IconeIa = (p: IconeMarcaProps) => (
  <Base {...p}><path d="M6 7l6-3 6 3M6 7l6 5 6-5M6 7v8l6 5 6-5V7M12 12v8" /><circle cx="6" cy="7" r="1.6" fill={LA} stroke="none" /><circle cx="18" cy="7" r="1.6" fill={AM} stroke="none" /><circle cx="12" cy="4" r="1.6" fill={AM} stroke="none" /><circle cx="12" cy="12" r="1.6" fill={LA} stroke="none" /><circle cx="6" cy="15" r="1.6" fill="#4F9A2B" stroke="none" /><circle cx="18" cy="15" r="1.6" fill="#1E88E5" stroke="none" /></Base>
);
/** Empresa: prédio em construção */
export const IconeEmpresa = (p: IconeMarcaProps) => (
  <Base {...p}><rect x="5" y="4" width="14" height="16" /><path d="M9 8h2M13 8h2M9 12h2M13 12h2M9 16h2M13 16h2" /><rect x="11" y="17" width="2" height="3" fill={AM} stroke="none" /><path d="M3 20h18" /></Base>
);
/** Conta: capacete pequeno em medalhão */
export const IconeConta = (p: IconeMarcaProps) => (
  <Base {...p}><circle cx="12" cy="12" r="8.5" /><path d="M8 13a4 4 0 0 1 8 0" fill={AM} /><path d="M7 13h10" /><path d="M12 9v1.5" /></Base>
);
/** Privacidade: escudo com nó */
export const IconePrivacidade = (p: IconeMarcaProps) => (
  <Base {...p}><path d="M12 3l7 3v6c0 4.5-3 7.5-7 9-4-1.5-7-4.5-7-9V6l7-3z" /><circle cx="12" cy="11" r="1.8" fill={AM} stroke="none" /><path d="M12 13v3" /></Base>
);
/** Plataforma: engrenagem com nós */
export const IconePlataforma = (p: IconeMarcaProps) => (
  <Base {...p}><circle cx="12" cy="12" r="3" fill={AM} /><path d="M12 3v3M12 18v3M3 12h3M18 12h3M5.6 5.6l2.1 2.1M16.3 16.3l2.1 2.1M5.6 18.4l2.1-2.1M16.3 7.7l2.1-2.1" /><circle cx="12" cy="3.5" r="1.2" fill={LA} stroke="none" /><circle cx="20.5" cy="12" r="1.2" fill={LA} stroke="none" /></Base>
);
/** Membros: duas pessoas com capacete */
export const IconeMembros = (p: IconeMarcaProps) => (
  <Base {...p}><path d="M6 10a3 3 0 0 1 6 0" fill={AM} /><path d="M5 10h8" /><path d="M3 20a5 5 0 0 1 10 0" /><path d="M14 8a2.5 2.5 0 0 1 5 0" fill={AM} /><path d="M13.5 8h6" /><path d="M14 20a4.5 4.5 0 0 1 7-3.7" /></Base>
);
/** Painel da obra: medidor com nó */
export const IconePainel = (p: IconeMarcaProps) => (
  <Base {...p}><path d="M4 16a8 8 0 0 1 16 0" /><path d="M12 16l4-5" /><circle cx="12" cy="16" r="1.6" fill={LA} stroke="none" /><path d="M4 20h16" /><circle cx="6" cy="12" r="1" fill={AM} stroke="none" /><circle cx="18" cy="12" r="1" fill={AM} stroke="none" /></Base>
);

/** Logomarca compacta para a barra lateral: usa a imagem oficial. */
export function Logomarca({ tamanho = 36, className }: { tamanho?: number; className?: string }) {
  // eslint-disable-next-line @next/next/no-img-element
  return <img src="/marca/civilconstructorai.webp" width={tamanho} height={tamanho} alt="" className={className} />;
}
