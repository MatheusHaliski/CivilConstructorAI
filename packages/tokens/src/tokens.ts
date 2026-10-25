/** Tokens de design para o mobile (mesmos valores de tokens.css). Tema claro de alto contraste é o padrão. */
export const claro = {
  bg: "#FFFFFF", surface: "#F3F4F6", surface2: "#E5E7EB", line: "#6B7280", lineSoft: "#E5E7EB",
  text: "#111827", text2: "#374151", text3: "#4B5563",
  brand: "#1D4ED8", brandText: "#1E40AF", brandSoft: "#DBEAFE", onBrand: "#FFFFFF", focusRing: "#1D4ED8",
  success: "#166534", warning: "#92400E", danger: "#B91C1C", info: "#1E40AF",
  obra: { planejada: "#4B5563", andamento: "#C2410C", paralisada: "#B45309", concluida: "#166534" },
  risco: { baixo: "#166534", medio: "#92400E", alto: "#B91C1C" },
  aiDraft: { bg: "#FAF5FF", line: "#A855F7", text: "#6B21A8" },
} as const;

export const escuro: typeof claro = {
  bg: "#0B1220", surface: "#111827", surface2: "#1F2937", line: "#6B7280", lineSoft: "#1F2937",
  text: "#F9FAFB", text2: "#D1D5DB", text3: "#9CA3AF",
  brand: "#60A5FA", brandText: "#93C5FD", brandSoft: "#1E3A8A", onBrand: "#0B1220", focusRing: "#93C5FD",
  success: "#4ADE80", warning: "#FBBF24", danger: "#F87171", info: "#93C5FD",
  obra: { planejada: "#9CA3AF", andamento: "#FB923C", paralisada: "#FBBF24", concluida: "#4ADE80" },
  risco: { baixo: "#4ADE80", medio: "#FBBF24", alto: "#F87171" },
  aiDraft: { bg: "#2E1065", line: "#C084FC", text: "#E9D5FF" },
};

export const forma = { radiusSm: 8, radiusMd: 12, radiusLg: 20, tapMin: 48 } as const;
export const espaco = { s1: 4, s2: 8, s3: 12, s4: 16, s6: 24, s8: 32 } as const;
export const fonte = { sans: "Inter", mono: "JetBrains Mono" } as const;
