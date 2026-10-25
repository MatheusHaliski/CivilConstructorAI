---
rf: RF17
titulo: Controle rígido de elevação e nivelamento de pilares
modulo: M7 Middleware & Agentes IA
status: especificado
cards:
  - https://trello.com/c/VVTtHXnO
paths:
  - geometria/auditor/tabela_c/disciplina_z.py
  - geometria/auditor/tabela_c/continuidade_vertical.py
  - geometria/tests/estresse/**
  - pyrevit_extension/**/disciplina_z.py
  - backend/src/main/java/br/com/civilconstructorai/auditoria/transicao/**
  - apps/web/src/app/(app)/obras/[obraId]/auditoria/**
---

# RF17 · Controle rígido de elevação e nivelamento de pilares

> **Disciplina Z**: todo pilar nasce e morre em níveis. A base fica presa à elevação do pavimento onde está
> (`Level.Elevation`), o topo à elevação do pavimento superior, com `BASE_OFFSET = 0` e `TOP_OFFSET = 0`. Isso
> elimina o "Efeito Z=0" (offsets negativos que afundam pilares no Revit) e o transpasse de pilares entre andares
> observados nos testes sem auditoria.

## 1. Descrição

Regra da Tabela C executada no laço 1 do RF16 (`geometria/auditor/tabela_c/disciplina_z.py`) e espelhada na
ponte pyRevit (RF15) para que o Revit receba o mesmo resultado.

Para cada pilar novo ou alterado:

1. **Ancoragem**: identifica o `nivelBase` (pavimento cuja elevação está mais próxima da cota de base proposta) e o
   `nivelTopo` = pavimento imediatamente acima. Se a IA propôs cotas absolutas (ex.: base em -0,35 m por erro de
   referência), elas são descartadas e substituídas pelos níveis.
2. **Zeramento de offsets**: `BASE_OFFSET = 0`, `TOP_OFFSET = 0`. A única exceção é o pilar do pavimento mais baixo
   apoiado em fundação, cuja base vai ao topo do bloco/sapata (RF10) por regra própria, também sem offset livre.
3. **Transpasse**: um pilar cujo topo passa da elevação do pavimento superior é **dividido** em um pilar por
   pavimento, mantendo seção e eixo, salvo se marcado explicitamente como `pilarContinuo = true` (ex.: pilar de
   pé-direito duplo, átrio) e houver justificativa.
4. **Continuidade vertical**: para cada pilar do pavimento n+1 procura-se o apoio no pavimento n na mesma posição
   (tolerância 0,02 m no plano). Pilar sem pilar abaixo (nasce em viga) gera **alerta de transição** para o
   engenheiro, que precisa confirmar a viga de transição — não é corrigido automaticamente.
5. **Variação de seção**: pilar superior com seção maior que o inferior gera alerta (redução de seção para cima é
   normal; aumento é suspeito).

Toda correção entra no relatório de auditoria com antes/depois (cota base, cota topo, offsets, divisão).

## 2. Atores

| Ator | Tipo | Responsabilidade |
|---|---|---|
| Auditor Python | sistema | aplicar a Disciplina Z e a verificação de continuidade |
| Engenheiro(a) responsável | `ENGENHEIRO` | confirmar ou rejeitar alertas de transição; marcar pilar contínuo |
| Arquiteto(a) | `ARQUITETO` | ver alertas (sem confirmar) |
| pyRevit | sistema externo | reaplicar a regra ao criar `FamilyInstance` no Revit |

## 3. Critérios de aceite

- [ ] **CA-01** 100 % dos pilares com base = elevação do seu pavimento e topo = elevação do pavimento superior (ou topo
  da fundação, no nível mais baixo), verificado por consulta em todas as versões aprovadas.
- [ ] **CA-02** Nenhum pilar atravessa mais de um pavimento sem `pilarContinuo = true` e justificativa preenchida.
- [ ] **CA-03** Pilar sem apoio inferior gera `AlertaTransicao` `ABERTO`; enquanto houver alerta aberto, o cálculo
  estrutural (RF05) não é liberado para aprovação.
- [ ] **CA-04** Correções no relatório com antes/depois e a regra `TABELA_C.DISCIPLINA_Z.*` aplicada.
- [ ] **CA-05** Mudar a elevação de um pavimento (RF03) reaplica a regra a todos os pilares ancorados nele, em um único
  comando, e gera nova versão.
- [ ] **CA-06** A ponte pyRevit cria os pilares com `FAMILY_BASE_LEVEL_OFFSET_PARAM` e
  `FAMILY_TOP_LEVEL_OFFSET_PARAM` = 0 (cenário E01 do RF16).
- [ ] **CA-07** Regra idempotente: reaplicar sobre modelo nivelado não gera correções.

## 4. Regras de negócio

- **RN-01** Tolerância de ancoragem: cota proposta a até 0,50 m de um nível é ancorada nele; acima disso, o comando é
  reprovado com "Cota de base fora de qualquer pavimento".
- **RN-02** Tolerância de continuidade no plano: 0,02 m entre centros de pilares sobrepostos.
- **RN-03** Pilar contínuo exige justificativa (texto) e confirmação do `ENGENHEIRO`; IA não pode marcar sozinha.
- **RN-04** Alerta de transição confirmado registra a viga de transição (`vigaTransicaoId`), que passa a ter verificação
  obrigatória no RF07.
- **RN-05** Ninguém edita offsets manualmente pelo canvas: o campo não existe na UI; vem só dos níveis.

## 5. Base legal e normas

- NBR 6118: continuidade de pilares, vigas de transição e consideração de cargas concentradas.
- NBR 15575 (desempenho) indiretamente, via correção de pé-direito.
- LGPD: não se aplica a dados de geometria; confirmação de alerta registra o id do engenheiro no registro de
  operações (Art. 37).

## 6. API

| Método e rota | Descrição | Respostas |
|---|---|---|
| `GET /api/v1/obras/{obraId}/auditorias/alertas-transicao?estado=ABERTO` | Lista alertas | 200, 404 |
| `POST /api/v1/obras/{obraId}/auditorias/alertas-transicao/{id}/confirmar` | Confirma com `vigaTransicaoId` | 200, 403, 409, 422 |
| `POST /api/v1/obras/{obraId}/auditorias/alertas-transicao/{id}/rejeitar` | Rejeita: exige reposicionar o pilar | 200, 403, 409 |
| `POST /api/v1/obras/{obraId}/modelo/comandos` com `VI(PILAR_CONTINUO, id, justificativa)` | Marca pilar contínuo | 201, 403, 422 |

## 7. Diagramas

### 7.1 Casos de uso

```mermaid
flowchart LR
  eng(["👤 Engenheiro(a)"])
  arq(["👤 Arquiteto(a)"])
  aud(["⚙️ Auditor Python"])
  rvt(["🧩 pyRevit"])
  subgraph RF17["RF17 · Disciplina Z"]
    uc1["Ancorar pilar nos níveis"]
    uc2["Zerar BASE_OFFSET e TOP_OFFSET"]
    uc3["Dividir pilar que transpassa andar"]
    uc4["Verificar continuidade vertical"]
    uc5["Confirmar ou rejeitar alerta de transição"]
    uc6["Marcar pilar contínuo"]
    uc7["Ver alertas"]
  end
  aud --> uc1 & uc2 & uc3 & uc4
  rvt --> uc2
  eng --> uc5 & uc6 & uc7
  arq --> uc7
  uc4 -. gera .-> uc5
```

### 7.2 Atividades

```mermaid
flowchart TD
  ini((início)) --> a1["Para cada pilar novo ou alterado"]
  a1 --> d1{"Cota de base a até 0,50 m de um nível?"}
  d1 -- não --> r1["Reprovar: cota fora de pavimento"] --> fim((fim))
  d1 -- sim --> a2["nivelBase = nível mais próximo; nivelTopo = nível acima"]
  a2 --> a3["BASE_OFFSET = 0 e TOP_OFFSET = 0"]
  a3 --> d2{"Topo passa do nível superior?"}
  d2 -- sim --> d3{"pilarContinuo confirmado?"}
  d3 -- não --> a4["Dividir em um pilar por pavimento"]
  d3 -- sim --> a5
  d2 -- não --> a5["Procurar pilar abaixo a até 0,02 m"]
  a4 --> a5
  a5 --> d4{"Tem apoio inferior ou fundação?"}
  d4 -- não --> a6["Abrir AlertaTransicao para o engenheiro"]
  d4 -- sim --> d5{"Seção maior que a do pilar inferior?"}
  a6 --> a7
  d5 -- sim --> a8["Alerta: aumento de seção"] --> a7
  d5 -- não --> a7["Registrar correções antes/depois"]
  a7 --> fim
```

### 7.3 Classes

```mermaid
classDiagram
  direction LR
  class Pavimento {
    +UUID id
    +String nome
    +double elevacaoZ
    +int ordem
  }
  class Pilar {
    +UUID id
    +Ponto2D centro
    +double b
    +double h
    +UUID nivelBaseId
    +UUID nivelTopoId
    +double baseOffset = 0
    +double topOffset = 0
    +boolean pilarContinuo
    +String justificativaContinuo
  }
  class DisciplinaZ {
    +double toleranciaAncoragem = 0.50
    +ancorar(Pilar, List~Pavimento~) Correcao
    +zerarOffsets(Pilar) Correcao
    +dividirTranspasse(Pilar) List~Pilar~
  }
  class VerificadorContinuidade {
    +double toleranciaPlano = 0.02
    +verificar(Pilar, Pavimento) Optional~AlertaTransicao~
  }
  class AlertaTransicao {
    +UUID id
    +UUID pilarId
    +TipoAlerta tipo
    +EstadoAlerta estado
    +UUID vigaTransicaoId
    +UUID confirmadoPor
    +confirmar(UUID viga, UUID engenheiro)
    +rejeitar(UUID engenheiro)
  }
  class TipoAlerta {
    <<enumeration>>
    SEM_APOIO_INFERIOR
    AUMENTO_DE_SECAO
  }
  class Correcao {
    +String regraTabelaC
    +String antes
    +String depois
  }
  Pavimento "1" <-- "0..*" Pilar : base
  Pavimento "1" <-- "0..*" Pilar : topo
  DisciplinaZ ..> Pilar : corrige
  DisciplinaZ ..> Correcao : produz
  VerificadorContinuidade ..> AlertaTransicao : abre
  AlertaTransicao --> TipoAlerta
  AlertaTransicao --> Pilar
```

### 7.4 Sequência

```mermaid
sequenceDiagram
  autonumber
  participant OR as OrquestradorAuditoria (core-api)
  participant TC as tabela_c (Python)
  participant DZ as disciplina_z.py
  participant VC as continuidade_vertical.py
  participant DB as PostgreSQL
  actor E as Engenheiro(a)
  participant W as Web
  OR->>TC: laço 1 com pilares do comando
  TC->>DZ: ancorar + zerar offsets + dividir transpasse
  DZ-->>TC: correções[]
  TC->>VC: verificar continuidade
  VC-->>TC: alertas[] (SEM_APOIO_INFERIOR)
  TC-->>OR: aprovado + correções + alertas
  OR->>DB: grava versão + alerta_transicao ABERTO
  W->>E: aviso "1 pilar sem apoio inferior"
  E->>W: confirmar viga de transição V12
  W->>OR: POST /alertas-transicao/{id}/confirmar
  OR->>DB: alerta CONFIRMADO + registro de operações
  OR-->>W: 200
```

### 7.5 Máquina de estados (AlertaTransicao)

```mermaid
stateDiagram-v2
  [*] --> ABERTO: auditor detecta pilar sem apoio
  ABERTO --> CONFIRMADO: engenheiro informa viga de transição
  ABERTO --> REJEITADO: engenheiro rejeita
  REJEITADO --> RESOLVIDO: pilar reposicionado com apoio
  ABERTO --> RESOLVIDO: novo comando cria apoio inferior
  CONFIRMADO --> ABERTO: viga de transição removida
  CONFIRMADO --> [*]
  RESOLVIDO --> [*]
```

### 7.6 Componentes

```mermaid
flowchart LR
  subgraph WEB["apps/web"]
    PA["Painel de alertas de auditoria"]
  end
  subgraph CORE["core-api (Spring Boot 4.1)"]
    OR["auditoria/OrquestradorAuditoria"]
    TR["auditoria/transicao/AlertaTransicaoService"]
    OA["ObraAccess"]
    RO["Registro de operações"]
  end
  subgraph GEO["geometria (Python)"]
    TC["auditor/tabela_c"]
    DZ["disciplina_z.py"]
    VC["continuidade_vertical.py"]
  end
  subgraph RVT["pyrevit_extension"]
    PZ["disciplina_z.py (IronPython)"]
    RAPI["Revit API: Level, FamilyInstance"]
  end
  DB[("PostgreSQL")]
  PA -- "HTTPS + CSRF" --> TR
  TR --> OA
  TR --> RO
  TR --> DB
  OR -- "HTTP interno" --> TC
  TC --> DZ
  TC --> VC
  OR --> DB
  PZ --> RAPI
```

## 8. Testes previstos

| Tipo | Caso |
|---|---|
| Unitário (Python) | pilar com base em -0,35 m no térreo (Efeito Z=0) → ancorado em 0,00 com offset 0 |
| Unitário (Python) | pilar de 0,00 a 6,00 m com pavimentos a 0 e 3 m → dividido em dois pilares |
| Unitário (Python) | mesmo pilar com `pilarContinuo = true` e justificativa → mantido inteiro |
| Unitário (Python) | pilar do 2º andar deslocado 0,30 m do inferior → `SEM_APOIO_INFERIOR` |
| Unitário (Python) | reaplicar a regra em modelo nivelado → zero correções |
| Estresse (RF16 E01, E05, E06) | prédio de 4 pavimentos e malha 5x3 com offsets aleatórios → 100 % nivelado |
| Integração `*IT` | confirmar alerta por `ARQUITETO` → 403; por `ENGENHEIRO` sem selo → 403 |
| Integração `*IT` | alterar elevação do pavimento 2 reancora todos os pilares ligados a ele |

## 9. Rastreabilidade

| Camada | Artefato | Status |
|---|---|---|
| Documento | `docs/rf/RF17-controle-elevacao-pilares.md` | ✅ |
| Card | [Trello VVTtHXnO](https://trello.com/c/VVTtHXnO) | ✅ |
| Geometria | `geometria/auditor/tabela_c/disciplina_z.py`, `continuidade_vertical.py` | ⏳ |
| pyRevit | `pyrevit_extension/.../disciplina_z.py` | ⏳ |
| API | `auditoria/transicao/AlertaTransicaoService.java` | ⏳ |
| Banco | `Vn__auditoria.sql` (alerta_transicao) | ⏳ |
| Web | painel de alertas em `obras/[obraId]/auditoria/` | ⏳ |
| Testes | `test_disciplina_z.py`, `AlertaTransicaoIT` | ⏳ |
