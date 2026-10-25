# Design system · tokens e contraste

Tokens em três níveis (DTCG 2025.10): **primitivos → semânticos → componentes**. Git é a fonte da verdade
(`packages/tokens/src/*.json`); Style Dictionary 5 gera `tokens.css` (web, `@theme` do Tailwind v4) e
`tokens.ts` (mobile). Fontes e cores decididas na [DN-07](DECISOES_DE_NEGOCIO.md#dn-07--tipografia-e-cor-de-marca).

## Tema claro (padrão; alto contraste no mobile)

```css
:root {
  /* primitivos */
  --color-cca-500: #1D4ED8;  --color-cca-700: #1E40AF;  --color-cca-300: #93C5FD;
  --color-canteiro-600: #C2410C;
  --color-neutral-0: #FFFFFF;  --color-neutral-100: #F3F4F6;  --color-neutral-400: #9CA3AF;
  --color-neutral-500: #6B7280; --color-neutral-600: #4B5563; --color-neutral-700: #374151;
  --color-neutral-900: #111827; --color-neutral-950: #0B1220;
  /* semânticos */
  --bg: var(--color-neutral-0);
  --surface: var(--color-neutral-100);
  --line: var(--color-neutral-500);      --line-soft: #E5E7EB;
  --text: var(--color-neutral-900);      --text-2: var(--color-neutral-700);  --text-3: var(--color-neutral-600);
  --brand: var(--color-cca-500);
  --brand-text: var(--color-cca-700);
  --on-brand: #FFFFFF;
  --focus-ring: var(--color-cca-500);
  --success: #166534; --warning: #92400E; --danger: #B91C1C; --info: var(--color-cca-700);
  /* estados da obra: cor + ícone Lucide + rótulo */
  --obra-planejada: #4B5563;  --obra-andamento: #C2410C;  --obra-paralisada: #B45309;  --obra-concluida: #166534;
  /* risco em checklist de segurança */
  --risco-baixo: #166534; --risco-medio: #92400E; --risco-alto: #B91C1C;
  /* conteúdo gerado por IA, ainda sem aprovação de profissional */
  --ai-draft-bg: #FAF5FF; --ai-draft-line: #A855F7; --ai-draft-text: #6B21A8;
  /* forma, espaço, tipografia, movimento */
  --radius-sm: 8px; --radius-md: 12px; --radius-lg: 20px;
  --space-1: 4px; --space-2: 8px; --space-3: 12px; --space-4: 16px; --space-6: 24px; --space-8: 32px;
  --font-sans: "Inter", system-ui, sans-serif;
  --font-mono: "JetBrains Mono", ui-monospace, monospace;
  --text-sm: .875rem; --text-md: 1rem; --text-lg: 1.25rem; --text-xl: 1.5rem;
  --tap-min: 48px;
  --dur-fast: 120ms; --dur-base: 200ms; --ease-out: cubic-bezier(.2,.8,.2,1);
}
@media (prefers-color-scheme: dark) { :root:not([data-theme="light"]) {
  --bg: #0B1220; --surface: #111827; --line: #6B7280; --line-soft: #1F2937;
  --text: #F9FAFB; --text-2: #D1D5DB; --text-3: #9CA3AF;
  --brand: #60A5FA; --brand-text: #93C5FD; --on-brand: #0B1220; --focus-ring: #93C5FD;
  --success: #4ADE80; --warning: #FBBF24; --danger: #F87171; --info: #93C5FD;
  --obra-planejada: #9CA3AF; --obra-andamento: #FB923C; --obra-paralisada: #FBBF24; --obra-concluida: #4ADE80;
  --risco-baixo: #4ADE80; --risco-medio: #FBBF24; --risco-alto: #F87171;
  --ai-draft-bg: #2E1065; --ai-draft-line: #C084FC; --ai-draft-text: #E9D5FF;
} }
:root[data-theme="dark"] {
  --bg: #0B1220; --surface: #111827; --line: #6B7280; --line-soft: #1F2937;
  --text: #F9FAFB; --text-2: #D1D5DB; --text-3: #9CA3AF;
  --brand: #60A5FA; --brand-text: #93C5FD; --on-brand: #0B1220; --focus-ring: #93C5FD;
  --success: #4ADE80; --warning: #FBBF24; --danger: #F87171; --info: #93C5FD;
  --obra-planejada: #9CA3AF; --obra-andamento: #FB923C; --obra-paralisada: #FBBF24; --obra-concluida: #4ADE80;
  --risco-baixo: #4ADE80; --risco-medio: #FBBF24; --risco-alto: #F87171;
  --ai-draft-bg: #2E1065; --ai-draft-line: #C084FC; --ai-draft-text: #E9D5FF;
}
@media (prefers-reduced-motion: reduce) { :root { --dur-fast: 0ms; --dur-base: 0ms; } }
```

## Tabela de contraste (WCAG 2.2)

Calculada pela fórmula de luminância relativa do WCAG (`scripts/wcag_contrast.py` reproduz esta tabela no CI).
Texto: ≥ 4,5:1; texto grande e componentes não textuais (1.4.11): ≥ 3:1.

| Par | Razão | Exigido | Passa |
|---|---|---|---|
| `--text` #111827 / `--bg` #FFFFFF | 17,74 | 4,5 | ✅ |
| `--text-2` #374151 / `--bg` | 10,31 | 4,5 | ✅ |
| `--text-3` #4B5563 / `--bg` | 7,56 | 4,5 | ✅ |
| `--text-3` #4B5563 / `--surface` #F3F4F6 | 6,87 | 4,5 | ✅ |
| `--brand-text` #1E40AF / `--bg` | 8,72 | 4,5 | ✅ |
| `--on-brand` #FFFFFF / `--brand` #1D4ED8 | 6,70 | 4,5 | ✅ |
| `--focus-ring` #1D4ED8 / `--bg` | 6,70 | 3 | ✅ |
| `--line` #6B7280 / `--bg` (borda de controle) | 4,83 | 3 | ✅ |
| `--success` #166534 / `--bg` | 7,13 | 4,5 | ✅ |
| `--warning` #92400E / `--bg` | 7,09 | 4,5 | ✅ |
| `--danger` #B91C1C / `--bg` | 6,47 | 4,5 | ✅ |
| `--info` #1E40AF / `--bg` | 8,72 | 4,5 | ✅ |
| `--obra-planejada` #4B5563 / `--bg` | 7,56 | 4,5 | ✅ |
| `--obra-andamento` #C2410C / `--bg` | 5,18 | 4,5 | ✅ |
| `--obra-paralisada` #B45309 / `--bg` | 5,02 | 4,5 | ✅ |
| `--obra-concluida` #166534 / `--bg` | 7,13 | 4,5 | ✅ |
| `--risco-baixo` / `--risco-medio` / `--risco-alto` sobre `--bg` | 7,13 / 7,09 / 6,47 | 4,5 | ✅ |
| `--ai-draft-text` #6B21A8 / `--ai-draft-bg` #FAF5FF | 8,13 | 4,5 | ✅ |
| `--ai-draft-line` #A855F7 / `--ai-draft-bg` | 3,69 | 3 | ✅ |
| Escuro: `--text` #F9FAFB / `--bg` #0B1220 | 17,92 | 4,5 | ✅ |
| Escuro: `--text-2` #D1D5DB / `--bg` | 12,71 | 4,5 | ✅ |
| Escuro: `--text-3` #9CA3AF / `--bg` | 7,37 | 4,5 | ✅ |
| Escuro: `--brand-text` #93C5FD / `--bg` | 10,38 | 4,5 | ✅ |
| Escuro: `--on-brand` #0B1220 / `--brand` #60A5FA | 7,36 | 4,5 | ✅ |
| Escuro: `--success` / `--risco-baixo` / `--obra-concluida` #4ADE80 / `--bg` | 10,74 | 4,5 | ✅ |
| Escuro: `--warning` / `--risco-medio` / `--obra-paralisada` #FBBF24 / `--bg` | 11,22 | 4,5 | ✅ |
| Escuro: `--danger` / `--risco-alto` #F87171 / `--bg` | 6,77 | 4,5 | ✅ |
| Escuro: `--info` #93C5FD / `--bg` | 10,38 | 4,5 | ✅ |
| Escuro: `--obra-planejada` #9CA3AF / `--bg` | 7,37 | 4,5 | ✅ |
| Escuro: `--obra-andamento` #FB923C / `--bg` | 8,27 | 4,5 | ✅ |
| Escuro: `--ai-draft-text` #E9D5FF / `--ai-draft-bg` #2E1065 | 11,19 | 4,5 | ✅ |
| Escuro: `--ai-draft-line` #C084FC / `--ai-draft-bg` #2E1065 | 5,77 | 3 | ✅ |
| Escuro: estados e riscos sobre `--surface` #111827 | ≥ 6,41 (menor: `--danger`) | 4,5 | ✅ |

`--line-soft` é decorativo (separadores) e não precisa atingir 3:1; nunca é a única borda de um controle.

## Regras

- A cor nunca é o único sinal (WCAG 1.4.1): estado da obra, risco, selo e rascunho de IA levam cor + ícone
  Lucide + texto.
- Foco visível em todo controle; alvo de toque ≥ 48 px; fonte ajustável de 85 a 150 %;
  `font-variant-numeric: tabular-nums` em tabelas de orçamento e na linha de comando (RF28).
- Botão de marca usa `--on-brand`; no tema escuro a marca clareia e o texto do botão escurece (7,4:1).
