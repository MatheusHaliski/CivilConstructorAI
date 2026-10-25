# Diagrama de componentes e implantação

Arquitetura do CivilConstructorAI: três camadas das Diretrizes (Engine visual 3D/CAD, Interpretador semântico
VI/VSL, Backend BIM e motor geométrico) sobre a stack obrigatória do prompt (Spring Boot 4.1, Valkey, PostGIS,
Claude API). Cobre todos os RFs do [catálogo](rf/README.md); o componente de cada RF está na seção 7.6 do seu doc.
As decisões de tecnologia estão registradas como ADRs em [PESQUISA_TECNOLOGIAS.md](PESQUISA_TECNOLOGIAS.md).

## 1. Componentes lógicos

```mermaid
flowchart LR
  subgraph CLIENTES["Clientes"]
    subgraph WEB["apps/web · Next.js 16 / React 19"]
      W1["Console de comandos VI"]
      W2["H9_scene3d · Three.js WebGL 2"]
      W3["Componentes Tabela H<br/>H5 formulários · H6 cards/modais · H10 estados"]
      W4["Telas LGPD e conta"]
      W5["TanStack Query + Zustand"]
    end
    subgraph MOB["apps/mobile · Expo SDK 58"]
      MB1["Canteiro offline: RDO, fotos, presença"]
      MB2["Visualizador do modelo"]
    end
    subgraph PKG["packages/*"]
      P1["tokens · DTCG → Style Dictionary"]
      P2["ui-web / ui-native"]
      P3["api · cliente gerado do OpenAPI"]
      P4["i18n · pt-BR"]
    end
  end
  subgraph CORE["backend core-api · Java 21/25 · Spring Boot 4.1"]
    direction TB
    C0["Segurança: SecurityFilterChain, CSRF SPA, CSP"]
    C1["identidade · acesso · empresa · obra"]
    C2["modelo · notacao"]
    C3["ia · SDK anthropic-java"]
    C4["estrutura · geotecnia"]
    C5["quantitativo · orcamento"]
    C6["prancha · interop"]
    C7["lgpd · arquivo · registro"]
  end
  subgraph GEO["geometria · Python 3.13 · FastAPI interna"]
    G1["auditor: caixa cartesiana, ShortCurve, disciplina Z, projeção vetorial"]
    G2["data_model: schemas da Tabela A"]
    G3["calculations: mef, armaduras, fundacoes"]
    G4["kernel: CadQuery / OpenCASCADE"]
    G5["pranchas: plantas e cortes SVG/PDF/DXF"]
    G6["interop: IfcOpenShell IFC 4.3, STEP"]
  end
  subgraph REVIT["pyrevit_extension · IronPython"]
    R1["Ponte web API → Revit API"]
  end
  subgraph DADOS["Dados"]
    D1[("PostgreSQL 17 + PostGIS 3.5<br/>RLS por tenant_id")]
    D2[("Valkey 9<br/>sessões, limites, cota de IA")]
    D3[("Cassandra 5<br/>atividades e notificações com TTL")]
    D4[("S3 sa-east-1 / Garage<br/>fotos, pranchas, IFC, exportações")]
  end
  subgraph EXT["Externos"]
    X1["Claude API · claude-opus-5-5"]
    X2["E-mail transacional"]
    X3["Tabela SINAPI mensal"]
  end
  WEB -- "HTTPS, cookie de sessão, X-XSRF-TOKEN" --> C0
  MOB -- "HTTPS, mesmo cookie no armazenamento seguro" --> C0
  PKG -.-> WEB
  PKG -.-> MOB
  C0 --> C1 & C2 & C4 & C5 & C6 & C7
  C2 --> C3
  C4 --> C3
  C5 --> C3
  C3 -- "streaming, structured outputs, tool use" --> X1
  C2 -- "HTTP interno mTLS" --> G1
  C4 -- "HTTP interno" --> G3
  C6 -- "HTTP interno" --> G5 & G6
  G1 --> G2 & G4
  G3 --> G4
  G6 --> G4
  G6 -. "IFC / comandos" .-> R1
  C1 & C2 & C4 & C5 & C7 --> D1
  C0 --> D2
  C3 --> D2
  C7 --> D3
  C7 --> D4
  C6 --> D4
  WEB -. "upload por URL pré-assinada" .-> D4
  C1 --> X2
  C5 --> X3
```

### Responsabilidades dos módulos do core-api

| Módulo | Responsabilidade | RFs |
|---|---|---|
| `identidade` | cadastro, login em etapas, 2FA, sessões, sudo | RF18, RF19, RF23 |
| `acesso` | `ObraAccess` (`hasObraRole`), selo profissional, matriz de acesso, RLS | RF21 |
| `empresa`, `obra` | tenants, obras, membros, convites, ciclo de vida da obra | RF20, RF22 |
| `modelo` | modelo BIM versionado, comandos, diff | RF01, RF03, RF04 |
| `notacao` | parser VI, gramática e validação da Tabela C | RF02, RF16 |
| `ia` | única porta para a Claude API: rotas, `effort`, caching, fallback, `ChamadaModelo` | RF02, RF04, RF09, RF12, RF16, RF27 |
| `estrutura` | cargas, combinações, jobs MEF e dimensionamento | RF04, RF05, RF07 |
| `geotecnia` | sondagens SPT, fundações | RF09, RF10 |
| `quantitativo`, `orcamento` | 5D, SINAPI, aprovação | RF11, RF12 |
| `prancha`, `interop` | pranchas, IFC/STEP, ponte Revit | RF14, RF15 |
| `lgpd` | consentimentos, termos, direitos do titular, saga de eliminação | RF24–RF27 |
| `arquivo` | URLs pré-assinadas S3, limpeza de imagens | RF09, RF14, RF23, RF25 |
| `registro` | registro de operações (Art. 37) e atividades da obra | todos |

Regra de dependência: módulos de negócio nunca chamam a Claude API diretamente, só via `ia`; nenhum módulo
fala com o banco de outro módulo sem passar pelo serviço dele; `geometria` é **sem estado** e não acessa banco.

## 2. Implantação

```mermaid
flowchart TB
  subgraph LOCAL["Desenvolvimento · Docker Compose"]
    L1["core-api - mvn spring-boot:run"]
    L2["geometria - uvicorn"]
    L3[("postgis/postgis:17-3.5")]
    L4[("valkey/valkey:9")]
    L5[("Garage v2.4 + init Admin API v2 - curlimages/curl")]
    L6[("cassandra:5 - profile full")]
    L7["apps/web - next dev"]
    L7 --> L1
    L1 --> L2
    L1 --> L3 & L4 & L5
    L1 -.-> L6
  end
  subgraph PROD["Produção · AWS sa-east-1"]
    P0["CDN + WAF"]
    P1["apps/web - Next.js"]
    P2["core-api - imagem Jib, N réplicas"]
    P3["geometria - imagem, N réplicas, rede privada"]
    P4[("PostgreSQL 17 + PostGIS gerenciado")]
    P5[("Valkey gerenciado")]
    P6[("Cassandra 5")]
    P7[("S3 sa-east-1 com lifecycle uploads/ 1 dia")]
    P8["Cofre de segredos: CCA_APP_SECRET, ANTHROPIC_API_KEY"]
    P0 --> P1 --> P2
    P2 --> P3
    P2 --> P4 & P5 & P6 & P7
    P8 -.-> P2
  end
  PROD -- "HTTPS" --> CL["Claude API"]
```

## 3. Layout do repositório

Mescla o *Git layout* das Diretrizes (`docs/`, `frontend/`, `backend/ai_engine|auditor|data_model|calculations`,
`pyrevit_extension/`, `tests/`) com o monorepo Turborepo do prompt. O front-end das Diretrizes vira `apps/web`, e
a parte Python de `backend/` vira o serviço `geometria/`; a camada de IA sai do Python e vai para o módulo `ia`
do core-api em Java (ADR em [PESQUISA_TECNOLOGIAS.md](PESQUISA_TECNOLOGIAS.md)).

```text
CivilConstructorAI/
├── apps/
│   ├── web/                       # Next.js 16 + React 19
│   │   └── src/
│   │       ├── canvas/            # H9_scene3d, CanvasController, SnapEngine (Three.js)
│   │       ├── components/h/      # componentes da Tabela H (H2…H18, H03)
│   │       └── app/               # rotas: (auth), (app)/obras/[obraId]/…
│   └── mobile/                    # Expo SDK 58 / Expo Router, modo offline no canteiro
├── packages/
│   ├── tokens/                    # DTCG 2025.10 → Style Dictionary 5 → tokens.css / tokens.ts
│   ├── ui-web/                    # shadcn/ui + Radix + Tailwind v4
│   ├── ui-native/                 # react-native-reusables + Uniwind
│   ├── api/                       # cliente gerado do OpenAPI do Spring
│   └── i18n/                      # i18next, pt-BR
├── backend/                       # core-api · Spring Boot 4.1 · Java 21/25
│   └── src/
│       ├── main/java/br/com/civilconstructorai/
│       │   ├── identidade/ acesso/ empresa/ obra/
│       │   ├── modelo/ notacao/ ia/
│       │   ├── estrutura/ geotecnia/ quantitativo/ orcamento/
│       │   └── prancha/ interop/ lgpd/ arquivo/ registro/
│       ├── main/resources/db/migration/   # Flyway V1..Vn (+ schema.sql verificado)
│       └── test/java/…            # *Test (unitário) e *IT (Testcontainers, login real)
├── geometria/                     # serviço Python 3.13 (FastAPI interna, sem estado)
│   ├── api/                       # rotas /auditar, /mef, /dimensionar, /pranchas, /ifc
│   ├── auditor/                   # BoundingBox, ShortCurveTolerance, cotas Z, projeção vetorial
│   ├── data_model/                # schemas JSON da Tabela A (materiais e custos)
│   ├── calculations/              # mef/, armaduras/, fundacoes/
│   ├── kernel/                    # CadQuery / OpenCASCADE
│   ├── pranchas/                  # plantas e cortes
│   ├── interop/                   # IfcOpenShell, STEP
│   └── tests/                     # 15+ testes unitários e de estresse de geometria
├── pyrevit_extension/             # scripts IronPython para a Revit API
├── design/prototype/              # protótipo HTML/CSS/JS + render.mjs (Playwright)
├── docs/
│   ├── rf/                        # RFnn-slug.md + catálogo README.md
│   ├── legal/                     # TERMOS_DE_USO.md, POLITICA_DE_PRIVACIDADE.md
│   ├── DIAGRAMA_*.md, MAQUINA_ESTADOS.md
│   ├── PESQUISA_TECNOLOGIAS.md    # ADRs
│   ├── TAXONOMIA_ATORES_E_CARDS.md
│   └── TRELLO_BACKLOG.md          # espelho do quadro do Trello
├── scripts/                       # check_rf_docs.py, wcag_contrast.py
├── compose.yaml                   # PostGIS, Valkey, Garage (+init), Cassandra (profile full)
├── turbo.json
└── .github/workflows/             # mvn verify JDK 21/25, Docs por RF, CodeQL, Gitleaks, Trivy
```
