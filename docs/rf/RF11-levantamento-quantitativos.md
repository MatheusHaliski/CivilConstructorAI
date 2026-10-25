---
rf: RF11
titulo: Levantamento automatizado de concreto (m³), fôrma (m²) e aço (kg)
modulo: M5 Extração Quantitativa (5D)
status: em-andamento
cards:
  - https://trello.com/c/ty5wWDp9
paths:
  - geometria/kernel/quantitativos.py
  - apps/web/src/app/(app)/obras/[obraId]/quantitativos/**
  - backend/cca-domain/src/main/java/br/com/civilconstructorai/quantitativo/**
  - backend/cca-application/src/main/java/br/com/civilconstructorai/quantitativo/QuantitativoService.java
  - backend/cca-web/src/main/java/br/com/civilconstructorai/api/quantitativo/QuantitativoController.java
  - backend/cca-infrastructure/src/main/resources/db/migration/V15__quantitativos.sql
---

# RF11 · Levantamento automatizado de concreto (m³), fôrma (m²) e aço (kg)

> Extração **determinística** (sem IA) dos quantitativos do modelo BIM auditado: volume de concreto bruto e
> líquido, área de fôrma por face e peso de aço por bitola, organizados por pavimento, elemento e material
> (`baseQuantities` da Tabela A). É a ponte entre o modelo 3D e o orçamento (RF12).

## 1. Descrição

O `LevantamentoQuantitativo` é um job sobre uma **versão** do modelo. O kernel (`geometria/kernel/`,
CadQuery/OpenCASCADE) gera o sólido de cada elemento e calcula:

- **Concreto**: volume bruto (sólido do elemento isolado) e **líquido** — após a regra de prioridade nas
  interseções (pilar > viga > laje: o nó pilar×viga é contado no pilar; o trecho viga×laje é contado na
  laje) e descontando aberturas (`NewOpening`) com área > 0,25 m² (critério de desconto configurável). Unidade
  m³ com 3 casas.
- **Fôrma**: área das faces em contato com fôrma — laterais e fundo de vigas, laterais de pilares, fundo e
  bordas de lajes; faces em contato com outro elemento ou com o solo (sapatas assentadas em lastro) não
  entram. Unidade m².
- **Aço**: a partir da armadura **aprovada** (RF07/RF10): Σ comprimento × peso linear por bitola (ex.:
  φ10 = 0,617 kg/m, φ12,5 = 0,963 kg/m) + perda configurável (padrão 10 %). Sem dimensionamento aprovado,
  usa taxa de armadura estimada (kg/m³) marcada como **estimativa**.
- **Estacas**: metros lineares por tipo e diâmetro.
- **Alvenaria e revestimento** (arquitetura): área de parede líquida de vãos, por material.

O resultado é imutável e ligado à `versaoModelo` e ao `hashEntradas`; nova versão do modelo marca o
levantamento como `OBSOLETO`. A exportação CSV/XLSX usa números tabulares e unidade em coluna própria.

## 2. Atores

| Ator | Papel na obra | Pode |
|---|---|---|
| Engenheiro(a) responsável | `ENGENHEIRO` | gerar, configurar critérios de desconto, exportar |
| Orçamentista | `ORCAMENTISTA` | gerar, exportar, usar no orçamento (RF12) |
| Cliente / proprietário | `CLIENTE` | ver totais liberados pelo engenheiro (decisão de negócio pendente) |
| Serviço de geometria | sistema | calcular sólidos e quantidades |

## 3. Critérios de aceite

- [ ] **CA-01** Mesmo modelo (versão + configuração) gera exatamente o mesmo quantitativo (hash idêntico).
- [ ] **CA-02** Interseções pilar×viga×laje não são contadas duas vezes: soma dos volumes líquidos = volume da
  união booleana dos sólidos (tolerância 0,001 m³).
- [ ] **CA-03** Aberturas acima do limite de desconto são descontadas do volume/área; abaixo, não.
- [ ] **CA-04** Aço usa armaduras aprovadas; quando ausentes, a linha mostra "estimativa por taxa" e o
  orçamento herda o aviso.
- [ ] **CA-05** Exportação CSV/XLSX com colunas pavimento, elemento, material, serviço, unidade, quantidade;
  números tabulares; arquivo no S3 com URL pré-assinada de 15 min.
- [ ] **CA-06** Mudança no modelo marca o levantamento `OBSOLETO` e o RF12 mostra aviso no orçamento.
- [ ] **CA-07** Modelo de 4 pavimentos com malha 5x3 processado em ≤ 30 s.

## 4. Regras de negócio

- **RN-01** Regra de prioridade de interseção configurável por obra, padrão pilar > viga > laje > parede.
- **RN-02** Critério de desconto de vãos padrão 0,25 m² para fôrma e alvenaria (alinhado ao critério SINAPI de
  medição); para concreto, desconta sempre.
- **RN-03** Quantitativos agregam por `materialId` e por "serviço" (concretagem, fôrma, armação), chaves usadas
  na associação SINAPI.
- **RN-04** Nenhuma IA participa do cálculo (resultado auditável e reprodutível).

## 5. Base legal e normas

- NBR 12721 (avaliação de custos e quantitativos para incorporação), critérios de medição do SINAPI (cadernos
  técnicos da Caixa).
- NBR 7480 (massa linear nominal das barras).
- Sem dados pessoais.

## 6. API

| Método e rota | Descrição | Respostas |
|---|---|---|
| `POST /api/v1/obras/{obraId}/quantitativos` | Gera levantamento da versão atual (ou `versao`) | 202 + id, 403, 404 |
| `GET /api/v1/obras/{obraId}/quantitativos/{id}` | Estado, totais e linhas (filtro por pavimento/material) | 200, 404 |
| `GET /api/v1/obras/{obraId}/quantitativos/{id}/exportacao?formato=csv\|xlsx` | URL pré-assinada | 200, 404 |
| `PUT /api/v1/obras/{obraId}/quantitativos/configuracao` | Prioridade de interseção, desconto, perda de aço | 200, 403, 422 |

## 7. Diagramas

### 7.1 Casos de uso

```mermaid
flowchart LR
  eng(["👤 Engenheiro(a)"])
  orc(["👤 Orçamentista"])
  cli(["👤 Cliente"])
  geo(["⚙️ Kernel de geometria"])
  subgraph RF11["RF11 · Quantitativos"]
    uc1["Gerar levantamento"]
    uc2["Configurar critérios de medição"]
    uc3["Exportar CSV ou XLSX"]
    uc4["Consultar totais"]
    uc5["Calcular sólidos e interseções"]
  end
  eng --> uc1 & uc2 & uc3 & uc4
  orc --> uc1 & uc3 & uc4
  cli --> uc4
  uc1 -. inclui .-> uc5
  uc5 --- geo
```

### 7.2 Atividades

```mermaid
flowchart TD
  ini((início)) --> a1["Selecionar versão do modelo"]
  a1 --> d0{"Já existe levantamento com mesmo hash?"}
  d0 -- sim --> r0["Reutilizar resultado"] --> fim((fim))
  d0 -- não --> a2["Job PENDENTE"]
  a2 --> a3["Gerar sólidos BRep de cada elemento"]
  a3 --> a4["Aplicar prioridade de interseção"]
  a4 --> a5["Descontar aberturas acima do limite"]
  a5 --> a6["Volumes de concreto e áreas de fôrma"]
  a6 --> d1{"Armadura aprovada disponível?"}
  d1 -- sim --> a7["Peso de aço por bitola + perda"]
  d1 -- não --> a8["Estimativa por taxa kg/m³"]
  a7 & a8 --> a9["Agregar por pavimento, material e serviço"]
  a9 --> d2{"Soma líquida = união booleana?"}
  d2 -- não --> e1["FALHOU: inconsistência de interseção"] --> fim
  d2 -- sim --> a10["CONCLUIDO + hash"]
  a10 --> a11["Disponibilizar exportação"] --> fim
```

### 7.3 Classes

```mermaid
classDiagram
  direction LR
  class LevantamentoQuantitativo {
    +UUID id
    +UUID obraId
    +long versaoModelo
    +String hashEntradas
    +EstadoJob estado
    +marcarObsoleto() void
  }
  class ConfiguracaoMedicao {
    +List~TipoElemento~ prioridadeIntersecao
    +double limiteDescontoM2
    +double perdaAcoPercentual
    +double taxaAcoEstimadaKgM3
  }
  class LinhaQuantitativo {
    +UUID elementoId
    +UUID pavimentoId
    +String materialId
    +Servico servico
    +Unidade unidade
    +BigDecimal quantidadeBruta
    +BigDecimal quantidadeLiquida
    +boolean estimativa
  }
  class TotalPorServico {
    +Servico servico
    +Unidade unidade
    +BigDecimal total
  }
  class Servico {
    <<enumeration>>
    CONCRETAGEM
    FORMA
    ARMACAO
    ESTACA
    ALVENARIA
  }
  class Unidade {
    <<enumeration>>
    M3
    M2
    KG
    M
  }
  class CalculadoraQuantitativos {
    <<Python>>
    +solidos(modelo) dict
    +volumes_liquidos(solidos, prioridade) dict
    +areas_forma(solidos) dict
    +peso_aco(armaduras, perda) dict
  }
  LevantamentoQuantitativo "1" *-- "1" ConfiguracaoMedicao
  LevantamentoQuantitativo "1" *-- "0..*" LinhaQuantitativo
  LevantamentoQuantitativo "1" *-- "0..*" TotalPorServico
  LinhaQuantitativo --> Servico
  LinhaQuantitativo --> Unidade
  CalculadoraQuantitativos ..> LinhaQuantitativo : produz
```

### 7.4 Sequência

```mermaid
sequenceDiagram
  autonumber
  actor O as Orçamentista
  participant W as Web (quantitativos)
  participant API as core-api quantitativo
  participant PY as geometria kernel
  participant DB as PostgreSQL
  participant S3 as S3
  O->>W: Gerar quantitativos
  W->>API: POST /quantitativos
  API->>DB: hash existente para versão e configuração?
  alt já calculado
    API-->>W: 200 levantamento existente
  else novo
    API->>DB: INSERT PENDENTE
    API-->>W: 202 {id}
    API->>PY: POST /quantitativos {modelo, armaduras aprovadas, cfg}
    PY->>PY: sólidos, interseções, aberturas, aço
    PY-->>API: linhas + totais + verificação da união
    API->>DB: CONCLUIDO
  end
  O->>W: Exportar XLSX
  W->>API: GET /exportacao?formato=xlsx
  API->>S3: grava arquivo
  API-->>W: URL pré-assinada 15 min
```

### 7.5 Máquina de estados (LevantamentoQuantitativo)

```mermaid
stateDiagram-v2
  [*] --> PENDENTE
  PENDENTE --> EM_EXECUCAO: worker inicia
  EM_EXECUCAO --> CONCLUIDO: verificação da união ok
  EM_EXECUCAO --> FALHOU: inconsistência ou erro do kernel
  FALHOU --> PENDENTE: reprocessar
  CONCLUIDO --> OBSOLETO: nova versão do modelo ou armadura
  OBSOLETO --> [*]
```

### 7.6 Componentes

```mermaid
flowchart LR
  subgraph WEB["apps/web"]
    QT["Tabela de quantitativos tabular-nums"]
    EX["Botão exportar"]
  end
  subgraph CORE["core-api"]
    QC["QuantitativoController"]
    QS["QuantitativoService"]
    XP["ExportadorPlanilha"]
    OA["ObraAccess"]
  end
  subgraph PY["geometria"]
    AP["api FastAPI interna"]
    KQ["kernel/quantitativos.py"]
    CQ["CadQuery + OpenCASCADE"]
  end
  DB[("PostgreSQL")]
  S3[("S3")]
  ORC["orcamento - RF12"]
  QT & EX --> QC --> OA
  QC --> QS --> DB
  QS -- "HTTP interno" --> AP --> KQ --> CQ
  QS --> XP --> S3
  ORC --> QS
```

## 8. Testes previstos

| Tipo | Caso |
|---|---|
| Unitário (Python) | viga 20x50x500 cm → 0,500 m³ e fôrma 6,0 m² (2 laterais + fundo) |
| Unitário (Python) | nó pilar×viga contado só no pilar; soma = união booleana |
| Unitário (Python) | janela 1,2x1,0 m descontada da alvenaria; vão 0,4x0,4 m não descontado |
| Unitário (Python) | 100 m de φ10 com 10 % de perda → 67,87 kg |
| Integração `*IT` | gerar duas vezes a mesma versão reaproveita (mesmo id) |
| Integração `*IT` | comando VI novo marca levantamento `OBSOLETO` |
| Desempenho | prédio de 4 pavimentos, malha 5x3 ≤ 30 s |

## 9. Rastreabilidade

| Camada | Artefato | Status |
|---|---|---|
| Documento | `docs/rf/RF11-levantamento-quantitativos.md` | ✅ |
| Card | [Trello ty5wWDp9](https://trello.com/c/ty5wWDp9) | ✅ |
| Web | `apps/web/src/app/(app)/obras/[obraId]/quantitativos/` | ⏳ |
| API | `cca-web/api/quantitativo/QuantitativoController.java` (`GET /quantitativos`, `/export?formato=csv|xlsx`, `/{id}/liberar-cliente`), `cca-application/quantitativo/QuantitativoService.java` | ✅ |
| Banco | `V15__quantitativos.sql` (levantamento_quantitativo com linhas/totais JSON, hash FNV-1a, liberado_cliente) | ✅ |
| Geometria | port fiel de `packages/notacao-vi/src/quantitativos.ts` em `cca-domain/quantitativo/Quantificador.java` (mesmo hash FNV-1a e prioridade pilar > viga > laje) | ✅ Java · ⏳ python |
| Testes | `QuantificadorTest` (hash igual ao TS, rascunho IA não entra, aço aprovado substitui taxa), `OrcamentoIT.quantitativosDoModeloEExportacao` (CSV/XLSX, DN-02 para CLIENTE) | ✅ |

## Auditoria 2026-10 · 2ª rodada

Estrutura, materiais e conformidade ([relatório](../auditoria/AUDITORIA_2026-10_ESTRUTURA_INSTALACOES_MATERIAIS.md)).

- **Corrigido:** pilar circular quantificado por π·d²/4 e perímetro π·d; contraventamento com prefixo C; aço do dimensionamento = Σ massas das posições detalhadas.
- **Evidência:** `QuantificadorTest`, `DimensionamentoReferenciaTest`.
- **Pendente:** perdas de corte só no orçamento.
