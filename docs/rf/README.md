# Catálogo de Requisitos Funcionais (RF)

Cada RF tem um card no quadro do Trello
[CivilConstructorAI · CAD/BIM Generativo](https://trello.com/b/Ep08cZde/civilconstructorai-cad-bim-generativo)
e um documento `docs/rf/RFnn-slug.md` com o mesmo número. O backlog espelhado fica em
[`docs/TRELLO_BACKLOG.md`](../TRELLO_BACKLOG.md).

Origem dos RFs:

- **RF01–RF17**: matriz de requisitos das *Diretrizes de Arquitetura e Desenvolvimento do Repositório* v1.0
  (CAD/BIM generativo com Notação VI e auditoria paramétrica), mantida com a mesma numeração.
- **RF18–RF27**: base de identidade, controle de acesso e LGPD definida no prompt do CivilConstructorAI.

## Catálogo

| RF | Módulo | Título | Doc | Card | Status |
|---|---|---|---|---|---|
| RF01 | M1 Concepção Arquitetônica | Modelagem vetorial 2D/3D interativa no canvas WebGL | [doc](RF01-modelagem-vetorial-canvas-webgl.md) | [card](https://trello.com/c/3WgN7lzt) | especificado |
| RF02 | M1 Concepção Arquitetônica | Comandos via Notação VI e prompts de código JSON | [doc](RF02-comandos-notacao-vi.md) | [card](https://trello.com/c/W4pJDtvv) | especificado |
| RF03 | M1 Concepção Arquitetônica | Geração automática de malhas de eixos (Grids) e pavimentos Z | [doc](RF03-grids-e-pavimentos.md) | [card](https://trello.com/c/5aEYPZHD) | especificado |
| RF04 | M2 Análise Estrutural & Esforços | Lançamento de pilares, vigas, lajes e cargas | [doc](RF04-lancamento-elementos-e-cargas.md) | [card](https://trello.com/c/WOIUHFDD) | especificado |
| RF05 | M2 Análise Estrutural & Esforços | Diagramas de momentos, cortantes, normais e torção (MEF) | [doc](RF05-diagramas-esforcos-mef.md) | [card](https://trello.com/c/mNjp8fP4) | especificado |
| RF06 | M2 Análise Estrutural & Esforços | Auditoria de vãos reais corrigindo o Fencepost Error | [doc](RF06-auditoria-vaos-fencepost.md) | [card](https://trello.com/c/OGjcAg5E) | especificado |
| RF07 | M3 Dimensionamento Normativo | Dimensionamento de armaduras (ELU/ELS) em concreto/aço | [doc](RF07-dimensionamento-armaduras.md) | [card](https://trello.com/c/0VapxWRZ) | especificado |
| RF08 | M3 Dimensionamento Normativo | Filtro de tolerâncias geométricas e seções mínimas | [doc](RF08-filtro-tolerancias-secoes-minimas.md) | [card](https://trello.com/c/Dgtma9TN) | especificado |
| RF09 | M4 Geotecnia & Fundações | Perfil estratigráfico de solo e ensaio SPT | [doc](RF09-perfil-solo-spt.md) | [card](https://trello.com/c/LzHFBr6H) | especificado |
| RF10 | M4 Geotecnia & Fundações | Dimensionamento de sapatas, blocos sobre estacas e radier | [doc](RF10-dimensionamento-fundacoes.md) | [card](https://trello.com/c/C7Lgejlv) | especificado |
| RF11 | M5 Extração Quantitativa (5D) | Levantamento de concreto (m³), fôrma (m²) e aço (kg) | [doc](RF11-levantamento-quantitativos.md) | [card](https://trello.com/c/ty5wWDp9) | especificado |
| RF12 | M5 Extração Quantitativa (5D) | Associação automática a tabelas orçamentárias (SINAPI / Tabela A) | [doc](RF12-associacao-sinapi.md) | [card](https://trello.com/c/64v8Sq35) | especificado |
| RF13 | M6 Visualização & Pranchas | Renderização WebGL com gerenciador de cena 3D (H9_scene3d) | [doc](RF13-renderizacao-cena-3d.md) | [card](https://trello.com/c/S0TACTtJ) | especificado |
| RF14 | M6 Visualização & Pranchas | Geração autônoma de plantas e cortes com recuo de eixos | [doc](RF14-geracao-plantas-cortes.md) | [card](https://trello.com/c/MCxqnKl2) | especificado |
| RF15 | M6 Visualização & Pranchas | Interoperabilidade OpenBIM (IFC / STEP) | [doc](RF15-interoperabilidade-openbim.md) | [card](https://trello.com/c/aLnGEB4O) | especificado |
| RF16 | M7 Middleware & Agentes IA | Loop duplo de validação (código e geometria de sólido) | [doc](RF16-loop-duplo-validacao.md) | [card](https://trello.com/c/o2wBmxKy) | especificado |
| RF17 | M7 Middleware & Agentes IA | Controle rígido de elevação e nivelamento de pilares | [doc](RF17-controle-elevacao-pilares.md) | [card](https://trello.com/c/VVTtHXnO) | especificado |
| RF18 | M0 Identidade & Acesso | Cadastro de conta e verificação de e-mail | [doc](RF18-cadastro-e-verificacao-email.md) | [card](https://trello.com/c/v3Z3WzgD) | especificado |
| RF19 | M0 Identidade & Acesso | Login em etapas com 2FA, sessão e empresa ativa | [doc](RF19-login-2fa-sessao.md) | [card](https://trello.com/c/IEf3vByh) | especificado |
| RF20 | M0 Identidade & Acesso | Convites para empresa e obra | [doc](RF20-convites-empresa-obra.md) | [card](https://trello.com/c/22GiOPjC) | especificado |
| RF21 | M0 Identidade & Acesso | Controle de acesso por papel na obra, selo profissional e RLS | [doc](RF21-controle-acesso-papel-obra.md) | [card](https://trello.com/c/BUNJmEJI) | especificado |
| RF22 | M0 Identidade & Acesso | Gestão de empresas, obras e membros da obra | [doc](RF22-empresas-e-obras.md) | [card](https://trello.com/c/4vumPt2g) | especificado |
| RF23 | M0 Identidade & Acesso | Perfil, segurança da conta, sessões e modo sudo | [doc](RF23-perfil-sessoes-modo-sudo.md) | [card](https://trello.com/c/u9tL8Fqy) | especificado |
| RF24 | M8 Privacidade LGPD & IA responsável | Termos versionados e consentimentos por finalidade | [doc](RF24-termos-e-consentimentos.md) | [card](https://trello.com/c/HbUcwgdJ) | especificado |
| RF25 | M8 Privacidade LGPD & IA responsável | Direitos do titular: ver, corrigir, exportar, compartilhamentos | [doc](RF25-direitos-do-titular.md) | [card](https://trello.com/c/BQtSoryZ) | especificado |
| RF26 | M8 Privacidade LGPD & IA responsável | Exclusão de conta com carência, reativação e saga de eliminação | [doc](RF26-exclusao-conta-carencia.md) | [card](https://trello.com/c/9s245tLM) | especificado |
| RF27 | M8 Privacidade LGPD & IA responsável | Transparência do uso de IA, rascunho "gerado por IA" e revisão humana | [doc](RF27-transparencia-ia-revisao-humana.md) | [card](https://trello.com/c/7dTIZbIs) | especificado |

Status possíveis: `especificado` (doc e diagramas prontos, sem código) → `em-andamento` → `implementado`
(código + testes passando) → `homologado`.

## Estrutura obrigatória de cada doc

1. Cabeçalho YAML: `rf`, `titulo`, `modulo`, `status`, `cards` (URLs do Trello), `paths` (globs do código que
   implementa o RF; mudar um desses arquivos exige atualizar o doc **no mesmo commit**).
2. Descrição, atores, critérios de aceite numerados (`CA-01`…), regras de negócio, base legal e normas técnicas.
3. Contrato de API (rotas, códigos de resposta).
4. Diagramas Mermaid, nesta ordem:
   1. Casos de uso (`flowchart`)
   2. Atividades (`flowchart` com decisões)
   3. Classes (`classDiagram`)
   4. Sequência (`sequenceDiagram`)
   5. Máquina de estados (`stateDiagram-v2`)
   6. Componentes (`flowchart` com `subgraph` por camada)
5. Testes previstos.
6. Rastreabilidade: camada → artefato → ✅ feito / 🟡 parcial / ⏳ pendente.

## Convenções de nomes no código

| Camada | Raiz | Exemplo |
|---|---|---|
| API core (Java 21/25, Spring Boot 4.1) | `backend/src/main/java/br/com/civilconstructorai/<modulo>/` | `modelo/ComandoViService.java` |
| Migrações Flyway | `backend/src/main/resources/db/migration/` | `V7__modelo_bim.sql` |
| Serviço de geometria e cálculo (Python 3.13) | `geometria/<pacote>/` | `geometria/auditor/caixa_cartesiana.py` |
| Extensão Revit (IronPython) | `pyrevit_extension/` | `pyrevit_extension/CivilConstructor.extension/` |
| Web (Next.js 16 + Three.js) | `apps/web/src/` | `apps/web/src/canvas/H9Scene3d.ts` |
| Mobile (Expo) | `apps/mobile/` | `apps/mobile/app/(obra)/rdo.tsx` |
| Tokens / UI | `packages/tokens/`, `packages/ui-web/` | `packages/tokens/src/tokens.json` |
