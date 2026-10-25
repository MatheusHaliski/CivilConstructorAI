---
rf: RF04
titulo: Lançamento de pilares, vigas, lajes e cargas (permanentes/vento)
modulo: M2 Análise Estrutural & Esforços
status: em-andamento
cards:
  - https://trello.com/c/WOIUHFDD
paths:
  - apps/web/src/components/h/estrutura/**
  - geometria/calculations/cargas/**
  - backend/cca-domain/src/main/java/br/com/civilconstructorai/estrutura/carga/**
  - backend/cca-domain/src/main/java/br/com/civilconstructorai/estrutura/lancamento/**
  - backend/cca-domain/src/main/java/br/com/civilconstructorai/estrutura/modelo/**
  - backend/cca-application/src/main/java/br/com/civilconstructorai/estrutura/CargasService.java
  - backend/cca-application/src/main/java/br/com/civilconstructorai/estrutura/LancadorAutomatico.java
  - backend/cca-application/src/main/java/br/com/civilconstructorai/estrutura/ModeloEstruturalFonte.java
  - backend/cca-infrastructure/src/main/java/br/com/civilconstructorai/estrutura/ModeloEstruturalJdbc.java
  - backend/cca-web/src/main/java/br/com/civilconstructorai/api/estrutura/EstruturaController.java
  - backend/cca-infrastructure/src/main/resources/db/migration/V9__estrutura.sql
---

# RF04 · Lançamento de pilares, vigas, lajes e cargas (permanentes/vento)

> Monta o modelo estrutural sobre a malha de eixos (RF03): pilares nas interseções, vigas entre apoios, lajes por
> contorno fechado, e as ações — permanentes, acidentais e vento — com as combinações de cálculo. É a entrada do
> MEF (RF05).

## 1. Descrição

Três formas de lançar, todas convergindo para `ComandoVI` auditado:

1. **Manual** no canvas (RF01) ou console VI: `VI(PILAR, eixo=B2, b=0.20, h=0.40, mat=MAT_CONC_FCK30)`,
   `VI(VIGA, de=B2, ate=C2, b=0.15, h=0.50)`, `VI(LAJE, contorno=[B2,C2,C3,B3], h=0.12, tipo=MACICA)`.
2. **Automático por regra**: "pilar em todas as interseções do pavimento", "vigas sobre todas as paredes
   estruturais", "laje em cada painel fechado por vigas". Determinístico, sem IA.
3. **Sugestão por IA** (rota `lancamento-estrutural`, `effort: high`): a partir da arquitetura (paredes, vãos,
   aberturas), o modelo propõe a concepção estrutural em JSON (structured outputs). O resultado é um `RascunhoIA`
   que o engenheiro revisa elemento a elemento antes de aplicar.

**Cargas** (`CasoCarga`):

- **G — permanentes**: peso próprio calculado pelo volume × peso específico do material (Tabela A,
  `physicalProperties`); revestimento (kN/m²), alvenaria sobre viga (kN/m = γ × espessura × altura).
- **Q — acidentais**: por uso do ambiente conforme NBR 6120 (ex.: residencial dormitório 1,5 kN/m²; escritório
  2,5 kN/m²).
- **W — vento**: NBR 6123: V0 pela isopleta da localização da obra (PostGIS), S1 (topográfico), S2 (rugosidade,
  classe e altura), S3 (estatístico, grupo da edificação); pressão dinâmica q = 0,613·Vk² aplicada como forças
  nodais por pavimento em 0°, 90°, 180° e 270°.

As **combinações** seguem a NBR 8681/NBR 6118: ELU normais (γg = 1,4, γq = 1,4, ψ0 por ação) e ELS
(quase permanente com ψ2, frequente com ψ1), geradas automaticamente a partir dos casos existentes.

## 2. Atores

| Ator | Papel na obra | Pode |
|---|---|---|
| Engenheiro(a) responsável | `ENGENHEIRO` | lançar, editar, gerar combinações, aprovar sugestão da IA (com selo `VERIFIED`) |
| Arquiteto(a) | `ARQUITETO` | visualizar estrutura; não lança elementos estruturais (403) |
| Serviço de IA | sistema | sugerir concepção estrutural |
| Auditor Python | sistema | validar apoios, contornos e seções mínimas (RF08) |

## 3. Critérios de aceite

- [ ] **CA-01** Todo elemento estrutural referencia material da Tabela A (ex.: `MAT_CONC_FCK30`); sem material
  → `422 VI-010`.
- [ ] **CA-02** Viga só existe com os dois extremos apoiados em pilar, viga ou parede estrutural; balanço
  explícito (`balanco=true`) permite um extremo livre até 3,0 m.
- [ ] **CA-03** Laje com contorno fechado, sem auto-interseção, e todas as bordas apoiadas ou declaradas livres.
- [ ] **CA-04** Cargas sempre com unidade (kN/m², kN/m, kN) e caso (`G`, `Q`, `W0`, `W90`, `W180`, `W270`);
  valor negativo para gravitacional rejeitado.
- [ ] **CA-05** Combinações ELU e ELS geradas automaticamente e recalculadas quando um caso é criado/removido.
- [ ] **CA-06** Vento calculado com V0 obtido pela localização da obra; parâmetros S1, S2, S3 e categoria editáveis
  e registrados na memória.
- [ ] **CA-07** Sugestão da IA entra como `RascunhoIA` (`GERADO_POR_IA`); cada elemento pode ser aceito ou
  descartado; nada vira modelo sem aprovação do engenheiro.
- [ ] **CA-08** Qualquer alteração no lançamento marca a análise (RF05) e o dimensionamento (RF07/RF10) como
  `OBSOLETO`.

## 4. Regras de negócio

- **RN-01** Pilar lançado por regra automática usa seção padrão do projeto (padrão 20 × 40 cm), orientação pelo
  maior vão adjacente.
- **RN-02** Viga entre eixos herda ancoragem dos eixos (move quando o eixo muda, RF03).
- **RN-03** Peso próprio nunca é digitado: é derivado da geometria e do material (`fck`, γ = 25 kN/m³ para
  concreto armado).
- **RN-04** Carga de alvenaria só em viga ou laje; parede sem apoio estrutural gera alerta.
- **RN-05** Rota de IA envia somente geometria de arquitetura e uso dos ambientes, nunca dados de pessoas.
- **RN-06** Aprovar rascunho estrutural exige papel `ENGENHEIRO`, selo `VERIFIED` e 2FA ativo (RF21/RF27).

## 5. Base legal e normas

- NBR 6118:2023 (projeto de estruturas de concreto), NBR 6120:2019 (ações), NBR 6123 (vento),
  NBR 8681:2003 (ações e segurança), NBR 8800 (aço).
- Lei 6.496/1977 (ART): o lançamento aprovado é de responsabilidade do engenheiro com ART da obra.
- LGPD Art. 6º, III: contexto da IA sem dados pessoais.

## 6. API

| Método e rota | Descrição | Respostas |
|---|---|---|
| `POST /api/v1/obras/{obraId}/estrutura/lancamento/automatico` | Lança por regra (`regra`, `pavimentoId`) | 201 + diff, 403, 404, 422 |
| `POST /api/v1/obras/{obraId}/estrutura/lancamento/sugerir` | Sugestão por IA → `RascunhoIA` | 202, 403, 429 |
| `POST /api/v1/obras/{obraId}/estrutura/lancamento/rascunhos/{id}/aplicar` | Aplica elementos aceitos (`elementosAceitos[]`) | 201 + diff, 403, 409, 422 |
| `GET/POST /api/v1/obras/{obraId}/estrutura/cargas` | Lista/cria cargas | 200, 201, 422 |
| `PUT /api/v1/obras/{obraId}/estrutura/vento` | Parâmetros de vento (V0, S1, S2, S3) | 200, 422 |
| `GET /api/v1/obras/{obraId}/estrutura/combinacoes` | Combinações ELU/ELS geradas | 200 |

## 7. Diagramas

### 7.1 Casos de uso

```mermaid
flowchart LR
  eng(["👤 Engenheiro(a)"])
  ia(["🤖 ModeloLinguagem (Claude)"])
  aud(["⚙️ Auditor Python"])
  subgraph RF04["RF04 · Lançamento estrutural e cargas"]
    uc1["Lançar pilar, viga ou laje manualmente"]
    uc2["Lançar por regra automática"]
    uc3["Pedir sugestão de concepção à IA"]
    uc4["Revisar e aprovar rascunho estrutural"]
    uc5["Cadastrar cargas G e Q"]
    uc6["Configurar vento NBR 6123"]
    uc7["Gerar combinações ELU e ELS"]
    uc8["Validar apoios e contornos"]
  end
  eng --> uc1 & uc2 & uc3 & uc4 & uc5 & uc6
  uc3 --- ia
  uc5 -. inclui .-> uc7
  uc6 -. inclui .-> uc7
  uc1 -. inclui .-> uc8
  uc2 -. inclui .-> uc8
  uc4 -. inclui .-> uc8
  uc8 --- aud
```

### 7.2 Atividades

```mermaid
flowchart TD
  ini((início)) --> d0{"Forma de lançamento?"}
  d0 -- manual --> m1["ComandoVI via canvas ou console"]
  d0 -- regra --> r1["Aplicar regra no pavimento"]
  d0 -- IA --> i1["Rota lancamento-estrutural, effort high"]
  i1 --> i2["RascunhoIA GERADO_POR_IA"]
  i2 --> i3["Engenheiro aceita ou descarta cada elemento"]
  i3 --> d1{"Selo VERIFIED e 2FA?"}
  d1 -- não --> e1["403 com motivo"] --> fim((fim))
  d1 -- sim --> v1
  m1 --> v1["Auditar apoios, contornos e seções (RF08, RF16)"]
  r1 --> v1
  v1 --> d2{"Aprovado?"}
  d2 -- não --> e2["422 com todos os elementos afetados"] --> fim
  d2 -- sim --> g1["Gravar elementos e nova versão"]
  g1 --> c1["Calcular peso próprio pelo material"]
  c1 --> c2["Atualizar cargas e vento"]
  c2 --> c3["Regerar combinações ELU e ELS"]
  c3 --> o1["Marcar análise e dimensionamento como OBSOLETO"]
  o1 --> fim
```

### 7.3 Classes

```mermaid
classDiagram
  direction LR
  class ElementoEstrutural {
    <<abstract>>
    +UUID id
    +String materialId
    +UUID pavimentoId
  }
  class Pilar {
    +Ponto2D centro
    +double b
    +double h
    +double anguloGraus
  }
  class Viga {
    +UUID apoioInicialId
    +UUID apoioFinalId
    +double b
    +double h
    +boolean balanco
  }
  class Laje {
    +List~Ponto2D~ contorno
    +double espessura
    +TipoLaje tipo
  }
  class CasoCarga {
    +String codigo
    +NaturezaAcao natureza
    +String descricao
  }
  class NaturezaAcao {
    <<enumeration>>
    PERMANENTE
    ACIDENTAL
    VENTO
  }
  class Carga {
    <<abstract>>
    +UUID elementoId
    +double valor
    +String unidade
  }
  class CargaDistribuidaArea
  class CargaLinear
  class CargaPontual
  class ParametrosVento {
    +double v0
    +double s1
    +String categoriaS2
    +String classeS2
    +double s3
    +pressaoDinamica(double z) double
  }
  class Combinacao {
    +String nome
    +EstadoLimite estado
    +Map~String,Double~ fatores
  }
  class GeradorCombinacoes {
    +gerar(List~CasoCarga~ casos) List~Combinacao~
  }
  class LancadorAutomatico {
    +pilaresNasInterseccoes(UUID pavimentoId) List~ComandoVI~
    +lajesPorPainel(UUID pavimentoId) List~ComandoVI~
  }
  ElementoEstrutural <|-- Pilar
  ElementoEstrutural <|-- Viga
  ElementoEstrutural <|-- Laje
  Carga <|-- CargaDistribuidaArea
  Carga <|-- CargaLinear
  Carga <|-- CargaPontual
  CasoCarga "1" *-- "0..*" Carga
  CasoCarga --> NaturezaAcao
  CasoCarga ..> ParametrosVento : vento
  GeradorCombinacoes ..> Combinacao
  Carga --> ElementoEstrutural
```

### 7.4 Sequência

```mermaid
sequenceDiagram
  autonumber
  actor E as Engenheiro(a)
  participant W as Painel Estrutura (web)
  participant API as core-api LancamentoService
  participant ML as ModeloLinguagem (Claude)
  participant GEO as geometria auditor
  participant CG as geometria calculations/cargas
  participant DB as PostgreSQL
  E->>W: Sugerir concepção estrutural (térreo)
  W->>API: POST /estrutura/lancamento/sugerir
  API->>ML: gerarEstruturado(arquitetura, schema lancamento, effort high)
  ML-->>API: JSON com pilares, vigas e lajes
  API->>GEO: POST /auditar {elementos}
  GEO-->>API: aprovado + correções
  API->>DB: INSERT rascunho_ia GERADO_POR_IA
  API-->>W: 202 {rascunhoId}
  E->>W: aceita 18 de 20 elementos
  W->>API: POST /rascunhos/{id}/aplicar {elementosAceitos}
  API->>API: hasObraRole ENGENHEIRO, selo VERIFIED, 2FA
  API->>DB: INSERT elementos, versao+1, rascunho APROVADO
  API->>CG: POST /cargas/peso-proprio e /cargas/vento
  CG-->>API: cargas G e W por pavimento
  API->>DB: UPSERT cargas e combinacoes
  API->>DB: UPDATE analise SET estado OBSOLETO
  API-->>W: 201 DiffModelo
```

### 7.5 Máquina de estados (LancamentoEstrutural do pavimento)

```mermaid
stateDiagram-v2
  [*] --> VAZIO
  VAZIO --> EM_LANCAMENTO: primeiro elemento
  VAZIO --> SUGESTAO_IA: pedir sugestão
  SUGESTAO_IA --> EM_LANCAMENTO: rascunho aplicado
  SUGESTAO_IA --> VAZIO: rascunho rejeitado
  EM_LANCAMENTO --> CARREGADO: cargas e combinações completas
  CARREGADO --> EM_LANCAMENTO: elemento alterado
  CARREGADO --> PRONTO_PARA_ANALISE: auditoria sem pendências
  PRONTO_PARA_ANALISE --> EM_LANCAMENTO: elemento ou carga alterada
  PRONTO_PARA_ANALISE --> [*]: análise RF05 iniciada
```

### 7.6 Componentes

```mermaid
flowchart LR
  subgraph WEB["apps/web"]
    PE["Painel H5 Estrutura"]
    PC["Painel H5 Cargas e Vento"]
    RV["Revisão de rascunho IA"]
  end
  subgraph CORE["backend core-api"]
    LC["LancamentoController"]
    LS["LancamentoService"]
    LA["LancadorAutomatico"]
    CGS["CargaService"]
    GC["GeradorCombinacoes"]
    subgraph IA["módulo ia"]
      ML["ModeloLinguagem"]
    end
    RS["RascunhoIaService"]
  end
  subgraph GEO["geometria (Python)"]
    AU["auditor"]
    CA["calculations/cargas"]
  end
  DB[("PostgreSQL + PostGIS")]
  PE --> LC
  PC --> LC
  RV --> LC
  LC --> LS
  LS --> LA
  LS --> ML
  LS --> RS
  LS --> AU
  LC --> CGS --> CA
  CGS --> GC
  LS --> DB
  CGS --> DB
```

## 8. Testes previstos

| Tipo | Caso |
|---|---|
| Unitário (Java) | `GeradorCombinacoes` com G, Q, W0, W90 gera as combinações ELU e ELS esperadas (tabela de referência) |
| Unitário (Python) | peso próprio de viga 15 × 50 × 5 m em concreto = 9,375 kN |
| Unitário (Python) | pressão dinâmica para V0 = 40 m/s, S1 = 1, S2 cat. IV classe B a 10 m, S3 = 1 |
| Integração `*IT` | viga sem apoio no extremo → 422 com o id da viga |
| Integração `*IT` | aplicar rascunho com engenheiro sem selo `VERIFIED` → 403 `selo_nao_verificado` |
| Integração `*IT` | alterar pilar marca análise existente como `OBSOLETO` |

## 9. Rastreabilidade

| Camada | Artefato | Status |
|---|---|---|
| Documento | `docs/rf/RF04-lancamento-elementos-e-cargas.md` | ✅ |
| Card | [Trello WOIUHFDD](https://trello.com/c/WOIUHFDD) | ✅ |
| Web | `apps/web/src/components/h/estrutura/` | ⏳ |
| API | `cca-web/api/estrutura/EstruturaController.java` (`/estrutura/cargas`, `/vento/{pav}`, `/combinacoes`, `/lancamentos`, `/lancamento/sugerir|rascunhos`), `cca-application/estrutura/CargasService.java`, `LancadorAutomatico.java` (rota `LANCAMENTO_ESTRUTURAL`, rascunho gated por `podeAprovar`) | ✅ |
| IA | prompt e JSON Schema fixos em `LancadorAutomatico.SYSTEM`/`SCHEMA`; validação determinística das seções mínimas antes de virar `RascunhoLancamento` | ✅ |
| Banco | `V9__estrutura.sql` (carga, lancamento_estrutural, rascunho_lancamento; RLS via `app_aplicar_rls`) | ✅ |
| Geometria | `geometria/calculations/cargas/` | ⏳ |
| Testes | `GeradorCombinacoesTest` (NBR 8681/6123), `AnaliseIT` (lançamento de carga, combinações, 422 em carga inválida), `test_vento.py` | ✅ backend · ⏳ python |