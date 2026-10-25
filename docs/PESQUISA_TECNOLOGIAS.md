# Pesquisa de tecnologias e decisões de arquitetura (ADRs)

Este documento junta as duas fontes que definem o CivilConstructorAI e registra, como ADR, cada decisão de
tecnologia. Regra do projeto: **nenhuma tecnologia nova entra sem ADR aqui**.

| Fonte | O que define |
|---|---|
| *Diretrizes de Arquitetura e Desenvolvimento do Repositório* v1.0 (PDF) | Produto CAD/BIM generativo: Notação VI, VSL, Tabela A de materiais, auditoria paramétrica em Python, 17 RFs em 7 módulos, layout de pastas |
| *Prompt CivilConstructorAI* | Stack obrigatória (Java/Spring, Valkey, PostGIS, Claude API), identidade, controle de acesso, LGPD, tokens de design, documentação por RF |
| *Aplicações e Ferramentas de IA e BIM na Construção Civil* (planilha) | Levantamento de 60 tecnologias e ferramentas de mercado (seção 3 abaixo) |

## 1. Visão em uma frase

Um ambiente de modelagem BIM no navegador em que o projetista desenha **ou descreve em texto** o que quer; a IA
traduz a intenção para código na **Notação VI**, um **auditor determinístico em Python** corrige o que a IA
erra em geometria (Fencepost Error, Efeito Z=0, vetores nulos), e o modelo auditado alimenta análise
estrutural, dimensionamento, fundações, quantitativos e orçamento SINAPI — sempre como **rascunho** até a
aprovação de um profissional habilitado.

## 2. ADRs

### ADR-001 · Monorepo com núcleo Java e serviço de geometria em Python

- **Contexto.** O prompt exige backend Java 21/25 + Spring Boot 4.1. O PDF exige Python para auditoria,
  MEF, dimensionamento e kernel geométrico (CadQuery/OpenCASCADE) e IronPython para o Revit.
- **Decisão.** Dois serviços de backend no mesmo repositório:
  - `backend/` (**core-api**, Spring Boot 4.1): identidade, acesso, empresas/obras, modelo BIM como fonte da
    verdade, comandos VI, camada de IA, orçamento, LGPD, arquivos e registro de operações.
  - `geometria/` (Python 3.13, FastAPI **interna**, sem acesso público): `auditor/`, `data_model/`,
    `calculations/{mef,armaduras,fundacoes}/`, `kernel/`, `pranchas/`, `interop/`.
  - `pyrevit_extension/` (IronPython) para a ponte opcional com o Revit.
  - Front em Turborepo: `apps/web`, `apps/mobile`, `packages/*`.
- **Consequências.** O serviço Python é **sem estado** e não fala com o banco: recebe o recorte do modelo em
  JSON (schemas da Tabela A) e devolve resultado + relatório. Autorização, RLS e LGPD ficam só no core-api.
  Chamadas internas com mTLS e timeout; trabalhos longos (MEF, pranchas, IFC) viram jobs.

### ADR-002 · Claude API no lugar de gpt-4o-mini

- **Contexto.** O PDF cita `gpt-4o-mini` (OpenAI) com temperatura 0.0 como extrator semântico. O prompt, mais
  recente e obrigatório, exige o SDK oficial `com.anthropic:anthropic-java` e proíbe shim para outro provedor.
- **Decisão.** Modelo padrão **`claude-opus-5-5`**, escrito exatamente assim. A IA fica no core-api, módulo
  `ia`, atrás da interface própria `ModeloLinguagem` (implementação `ClaudeModeloLinguagem`; nos testes, uma
  implementação falsa e determinística).
- **Consequências.**
  - "Temperatura 0.0" não existe aqui: a API rejeita `temperature`, `top_p` e `top_k`. O determinismo que o
    PDF buscava vem de **structured outputs** (JSON Schema em `output_config.format`) + validação no backend +
    o auditor Python, que é determinístico.
  - Sem prefill de resposta e sem `tool_choice` forçado (`auto` + instrução no prompt).
  - Sempre checar `stop_reason` (tratar `refusal`) e ligar o fallback do servidor (`fallbacks: "default"`).

### ADR-003 · `effort` explícito por rota de IA

| Rota | RF | Uso | `effort` | Saída | Observações |
|---|---|---|---|---|---|
| `comando-vi` | RF02 | texto natural → comandos VI | `medium` | structured outputs | streaming; até 3 correções no loop duplo (RF16) |
| `lancamento-estrutural` | RF04 | sugerir pilares/vigas/lajes a partir da arquitetura | `high` | structured outputs | rascunho para o engenheiro |
| `extracao-boletim-spt` | RF09 | ler boletim de sondagem em PDF | `high` | 1ª chamada com structured outputs; 2ª com **citações** (página e trecho) | citações e structured outputs nunca na mesma chamada |
| `associacao-sinapi` | RF12 | vincular quantitativo a composição | `high` | structured outputs + tool use (`consultar_sinapi`, `strict: true`) | prompt caching da parte fixa; Batch API para reprocessar à noite |
| `correcao-auditoria` | RF16 | corrigir comando reprovado pelo auditor | `high` | structured outputs | recebe só as violações estruturadas; máx. 3 iterações |
| `classificar-foto` | (futuro) | classificar foto de canteiro | `low` | structured outputs | só com consentimento (RF24) |

Mudar modelo, prompt ou `effort` de uma rota exige rodar o **conjunto de avaliação** da rota antes.

### ADR-004 · O que vai e o que nunca vai para o modelo

- **Vai:** geometria, eixos, pavimentos, materiais (Tabela A), quantitativos, trechos de documentos técnicos
  da obra, mensagens de erro do auditor.
- **Nunca vai:** CPF, nome de trabalhador, ASO/afastamentos (dado de saúde), e-mail, telefone, fotos com
  pessoas, IP, ids de sessão.
- Cada rota registra **finalidade e base legal**; cada chamada registra rota, modelo, `effort`, tokens
  (incluindo `cache_read_input_tokens`) e `stop_reason`, **sem conteúdo**. Transferência internacional
  documentada na Política de Privacidade e no contrato com a construtora (LGPD Art. 33). Detalhes no RF27.

### ADR-005 · Auditoria Paramétrica Híbrida (LLM extrai, Python decide)

- **Contexto.** Nos 15 testes experimentais do PDF, a IA pura errou de forma recorrente: Fencepost Error
  (extrapolação de 4 m além da estrutura em malhas 4x4), transpasse de pilares entre andares pelo Efeito Z=0 e
  travamento por vetores de comprimento nulo. Com o auditor Python, 100 % das inconsistências foram
  eliminadas, inclusive no teste de estresse (4 andares, malha 5x3, marquises escalonadas).
- **Decisão.** Toda geometria — vinda da IA **ou** do canvas — passa pelo loop duplo (RF16): laço de código
  (schema, gramática VI, Tabela C) e laço de sólido (kernel). O auditor aplica Caixa Cartesiana Sanitizada
  (RF06), ShortCurveTolerance e seções mínimas (RF08), Disciplina Z (RF17) e projeção vetorial de aberturas.
- **Consequências.** O LLM nunca grava nada; a fonte da verdade é o modelo auditado no PostgreSQL.

### ADR-006 · Kernel geométrico e interoperabilidade

- **CadQuery 2 / OCP (OpenCASCADE)** para sólidos BRep, booleanas e exportação STEP.
- **IfcOpenShell** para IFC 4.3 (exportar e importar).
- **NumPy/SciPy** (matrizes esparsas) para o MEF de pórtico espacial.
- **pyRevit** como ponte opcional com o Revit (IronPython); não é dependência do núcleo (decisão pendente:
  obrigatório no MVP?).

### ADR-007 · Front: Next.js 16 + Three.js para o canvas

- O prompt fixa Next.js 16 / React 19, Tailwind v4, shadcn/ui, TanStack Query, Zustand, Motion.
- O PDF fixa WebGL/Three.js com o gerenciador `H9_scene3d` e componentes gerados pela **Tabela H**
  (H5 formulários, H6 cards/modais, H10 modificadores de estado).
- **Decisão:** `three` direto (sem react-three-fiber) dentro de um componente cliente que só recebe diffs do
  modelo; a UI ao redor segue o design system. Motivo: controle fino de instancing/LOD e de memória da GPU.

### ADR-008 · Identidade, sessão e segurança

Exatamente como no prompt (sem alternativas): Spring Security 7 com `csrf.spa()`; sessão **opaca** no
Spring Session Data Redis sobre **Valkey 9** (`indexed`), cookie `__Host-cca_session`; nunca JWT nem token em
`localStorage`; Argon2id com BouncyCastle; TOTP + 10 códigos de recuperação; segredo TOTP com AES-256-GCM;
modo sudo; limite de tentativas no Valkey; Problem Details em pt-BR. Detalhes em RF18–RF23.

### ADR-009 · Dados

PostgreSQL 17 + PostGIS 3.5 (fonte da verdade; perímetro da obra e furos de sondagem), JPA com
`ddl-auto: validate`, Flyway, **RLS por `tenant_id`**, UUID v7. Valkey para sessões, limites e cota de IA por
empresa. Cassandra 5 para registro de atividades e notificações com TTL. S3 (`sa-east-1`) / Garage em dev, com
URL pré-assinada de 15 min. Modelo BIM versionado: cada `ComandoVI` aplicado incrementa `versao`.

Plano de migrações Flyway (segue a ordem de entrega do backlog; os docs de RF usam estes nomes):

| Migração | RFs |
|---|---|
| `V1__contas_empresas_obras.sql` | RF18, RF19, RF21, RF22, RF23, RF26 |
| `V2__rls.sql` | RF21 |
| `V3__convites.sql` | RF20 |
| `V4__lgpd_consentimentos.sql` | RF24 |
| `V5__lgpd_titular.sql` | RF25 |
| `V6__lgpd_eliminacao.sql` | RF26 |
| `V7__modelo_bim.sql` | RF01, RF03, RF06 |
| `V8__rascunho_ia.sql` | RF27 |
| `V9__estrutura.sql` | RF04 |
| `V10__restricoes_projeto.sql` | RF08 |
| `V11__analise_estrutural.sql` | RF05 |
| `V12__dimensionamento.sql` | RF07 |
| `V13__geotecnia.sql` | RF09 |
| `V14__fundacoes.sql` | RF10 |
| `V15__quantitativos.sql` | RF11 |
| `V16__sinapi.sql` | RF12 |
| `V17__orcamento.sql` | RF12 |

### ADR-010 · Documentação como código

Cada RF tem `docs/rf/RFnn-slug.md` com seis diagramas Mermaid. `scripts/check_rf_docs.py` verifica a estrutura,
exige doc atualizado quando o código de um RF muda e renderiza todo Mermaid com `@mermaid-js/mermaid-cli`
(job "Docs por RF" em `.github/workflows/docs-rf.yml`). O Trello é espelhado em `docs/TRELLO_BACKLOG.md`.

### ADR-011 · Motor de desenho no modelo AutoCAD/Revit

- **Contexto.** O guia de comandos e atalhos (AutoCAD, Revit, Civil 3D) define o vocabulário que os
  projetistas já dominam. Reinventar a interação do canvas custaria adoção.
- **Decisão.** O canvas adota linha de comando, aliases por perfil, entrada dinâmica, modos de cursor e
  seleção substantivo-verbo (RF28). Cada comando de desenho/modificação é uma função da Tabela B, a **mesma**
  que o console de texto natural (RF02) produz; comandos de vista/visibilidade agem só na cena (RF13) e os de
  anotação na prancha (RF14). Catálogo em `docs/CATALOGO_COMANDOS.md`; pacote `packages/notacao-vi` é a fonte
  única das funções e aliases, consumida pelo front e pelo backend.
- **Consequências.** Motor determinístico (sem IA); teclas reservadas pelo navegador (F1, F5, F11, F12) ganham
  aliases alternativos; o mesmo `ComandoVI` chega ao auditor por três caminhos (mouse, linha de comando, IA).

### ADR-012 · Decisões de negócio

As decisões que dependiam do negócio (controladora/DPO, visão do cliente, retenção, rotas de IA, camadas D/E/F,
pyRevit, tipografia e marca) estão em [`docs/DECISOES_DE_NEGOCIO.md`](DECISOES_DE_NEGOCIO.md) (DN-01 a DN-07).

## 3. Levantamento de mercado (planilha)

Resumo das 60 entradas da planilha, agrupadas pelo que inspira cada módulo do produto.

| Módulo | Referências de mercado e pesquisa | O que aproveitamos |
|---|---|---|
| M1 Concepção | BIM paramétrico (Revit), Object CAD, Autodesk Forma, Generative Design, estudo de caso "linguagem natural + Python" (ref. 7) | modelagem paramétrica orientada a objeto; prompt restritivo → JSON → script; auditoria do que a IA gera |
| M2/M3 Estrutura | Revit com IA (detecção prévia de inconformidades), Navisworks (clash detection) | validar antes de persistir; relatório de conflitos |
| M4 Fundações | — (pouca oferta com IA) | oportunidade: leitura assistida de boletim SPT com citação de página |
| M5 Quantitativos e orçamento | K-Visus, eCustos/Gestor Obras, Construmanager, BIM 5D, modelos autoregressivos/RNN para índice de custo | quantitativo determinístico do modelo + vínculo SINAPI sugerido por IA e aprovado por profissional |
| M6 Visualização | BIM 4D, Rendair, GAN/difusão para renderização, StructionSite/Fologram (RA) | cena 3D por camadas; renderização fotorrealista fica fora do MVP |
| M7 IA | BIM-GPT (LLM sobre dados BIM), ChatGPT para cronograma, RoboGPT | LLM como extrator semântico, nunca como fonte da verdade |
| Canteiro (futuro) | viAct, Indus.ai, Skycatch, drones + visão computacional, Construpoint | fora do escopo atual; exigirão consentimento e RIPD (fotos com pessoas) |
| Otimização (futuro) | GA, PSO, ACO, Dijkstra, híbridos para guindastes e layout | candidatos para layout de canteiro em versão posterior |

Fontes: as 11 referências listadas na aba "Referências de fontes" da planilha (Mackenzie, Revista DCS,
Crea-SP, beAnalytic, arXiv "Generative AI in the Construction Industry", estudo de caso de automação BIM com
linguagem natural e Python, Sienge, Autodesk, INBEC, Alves Oliveira).
