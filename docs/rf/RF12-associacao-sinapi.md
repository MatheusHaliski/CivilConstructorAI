---
rf: RF12
titulo: Associação automática a tabelas orçamentárias (SINAPI / Tabela A)
modulo: M5 Extração Quantitativa (5D)
status: especificado
cards:
  - https://trello.com/c/64v8Sq35
paths:
  - backend/src/main/java/br/com/civilconstructorai/orcamento/**
  - backend/src/main/java/br/com/civilconstructorai/ia/rotas/AssociacaoSinapi*.java
  - backend/src/main/java/br/com/civilconstructorai/ia/ferramentas/ConsultaSinapi*.java
  - apps/web/src/app/(app)/obras/[obraId]/orcamento/**
  - backend/src/main/resources/db/migration/*orcamento*
  - backend/src/main/resources/db/migration/*sinapi*
---

# RF12 · Associação automática a tabelas orçamentárias (SINAPI / Tabela A)

> Cada linha do quantitativo (RF11) é associada a uma composição SINAPI vigente — primeiro pelo vínculo
> determinístico da Tabela A, depois, quando não houver, por **sugestão da IA** usando uma ferramenta de busca
> na tabela SINAPI interna. O resultado é o **rascunho de orçamento**, marcado "gerado por IA" onde couber, que
> só vale após aprovação de profissional habilitado.

## 1. Descrição

**Base SINAPI**: importação mensal (job agendado + upload manual pelo admin da plataforma) das planilhas de
composições e insumos da Caixa por UF, mês de referência e regime (desonerado / não desonerado). Cada
importação é uma `TabelaSinapi` versionada e imutável; o orçamento guarda a versão usada.

**Associação em duas etapas:**

1. **Determinística**: o material da Tabela A pode trazer `baseQuantities.sinapi` por serviço (ex.:
   `MAT_CONC_FCK30` + `CONCRETAGEM` → composição de concreto usinado fck 30 com lançamento e adensamento).
   Linhas com vínculo direto não passam pela IA.
2. **Sugestão por IA** (rota `AssociacaoSinapiRota`, `claude-opus-5-5`, `effort: high`, streaming): para as
   linhas restantes, o modelo recebe descrição do serviço, material e unidade (sem dados de pessoas) e usa a
   ferramenta `consultar_sinapi` (`strict: true`, `additionalProperties: false`; parâmetros: termo, unidade,
   UF, mês) para buscar candidatas; `tool_choice` fica `auto`, com instrução no system prompt para sempre
   consultar antes de responder. A resposta final vem em **structured outputs** (`codigo`, `justificativa`,
   `confianca`). O system prompt e o catálogo de unidades usam **prompt caching**.

O backend **revalida** toda sugestão: código existe na versão da tabela, unidade compatível com a linha
(m³↔m³, m²↔m², kg↔kg) e serviço coerente. Sugestões inválidas viram "sem vínculo" para o orçamentista.

O `Orcamento` nasce em `GERADO_POR_IA` (se algum item veio da IA) ou `RASCUNHO`; itens vindos da IA levam o
selo visual "gerado por IA" (token `--ai-draft-*`). A aprovação exige papel `ENGENHEIRO`, selo `VERIFIED`, 2FA
e modo sudo. Cliente aprova **aditivos** (diferenças entre orçamento aprovado e nova versão).

## 2. Atores

| Ator | Papel na obra | Pode |
|---|---|---|
| Orçamentista | `ORCAMENTISTA` | gerar, revisar vínculos, editar BDI e preços, enviar para aprovação |
| Engenheiro(a) responsável | `ENGENHEIRO` (selo `VERIFIED` + 2FA) | aprovar o orçamento |
| Cliente / proprietário | `CLIENTE` | ver orçamento liberado, aprovar aditivos |
| Admin da plataforma | plataforma | importar tabela SINAPI mensal |
| Serviço de IA | sistema (`ia`) | sugerir composições via ferramenta |

## 3. Critérios de aceite

- [ ] **CA-01** Item de orçamento contém código SINAPI, descrição, unidade, quantidade, custo unitário, total,
  UF, mês de referência e regime; totais por etapa e geral com BDI.
- [ ] **CA-02** Vínculo da Tabela A tem precedência e não consome IA.
- [ ] **CA-03** Sugestão da IA é marcada "gerado por IA", traz justificativa e confiança, e é rejeitada pelo
  backend se o código não existir na versão da tabela ou a unidade for incompatível.
- [ ] **CA-04** A ferramenta `consultar_sinapi` só lê a tabela vigente da UF da obra e respeita o escopo da
  obra (mesma checagem de permissão de um endpoint).
- [ ] **CA-05** Orçamento só passa a `APROVADO` com engenheiro(a) de selo `VERIFIED`, 2FA e modo sudo;
  registro de operações sem valores pessoais.
- [ ] **CA-06** Orçamento guarda `tabelaSinapiId` e `levantamentoId`; quantitativo `OBSOLETO` gera aviso e
  permite nova versão do orçamento; diferença com o aprovado vira proposta de aditivo.
- [ ] **CA-07** Cada chamada à IA registra rota, finalidade, base legal, tokens (incluindo
  `cache_read_input_tokens`) e `stop_reason`; `refusal` deixa as linhas sem vínculo, sem quebrar o orçamento.
- [ ] **CA-08** Avaliação: conjunto de 50 projetos com orçamento conferido; acurácia de código SINAPI ≥ 90 %
  antes de mudar prompt, modelo ou `effort`.

## 4. Regras de negócio

- **RN-01** Importação SINAPI é idempotente por (UF, mês, regime); reimportar substitui só se o hash mudar,
  criando nova versão.
- **RN-02** Custo unitário do item congela no momento da geração; mudança de tabela exige nova versão do
  orçamento.
- **RN-03** BDI padrão por obra (faixas de referência do TCU, Acórdão 2622/2013), editável pelo orçamentista
  com justificativa.
- **RN-04** Preço editado manualmente fica marcado "preço próprio" e sai da referência SINAPI.
- **RN-05** Sem `temperature`, `top_p`, `top_k` nem prefill; erros da API tratados pela cadeia de exceções do
  SDK (limite de taxa → nova tentativa com backoff; requisição inválida → falha imediata); fallback do servidor
  ligado.

## 5. Base legal e normas

- Decreto 7.983/2013 (SINAPI como referência de custos em obras com recursos federais), Acórdão TCU
  2622/2013 (BDI), NBR 12721.
- LGPD: nenhum dado pessoal no contexto enviado à IA (Art. 6º, III — necessidade); transferência internacional
  documentada (Art. 33); conteúdo "gerado por IA" com revisão humana (Art. 20).
- Responsabilidade técnica pelo orçamento: profissional habilitado com ART/RRT.

## 6. API

| Método e rota | Descrição | Respostas |
|---|---|---|
| `POST /api/v1/obras/{obraId}/orcamentos` | Gera orçamento a partir de `levantamentoId` (UF, mês, regime) | 202 + id, 403, 404, 409 |
| `GET /api/v1/obras/{obraId}/orcamentos/{id}` | Itens, totais, marcações de IA | 200, 404 |
| `PATCH /api/v1/obras/{obraId}/orcamentos/{id}/itens/{itemId}` | Troca código, preço próprio, aceita sugestão | 200, 409, 422 |
| `POST /api/v1/obras/{obraId}/orcamentos/{id}/aprovacao` | Aprova (selo, 2FA, sudo) | 200, 403, 409 |
| `POST /api/v1/obras/{obraId}/orcamentos/{id}/aditivos/{aditivoId}/aprovacao` | Cliente aprova aditivo | 200, 403, 409 |
| `GET /api/v1/sinapi/composicoes?uf=&mes=&termo=` | Busca na tabela (usada pela UI e pela ferramenta) | 200 |
| `POST /api/v1/plataforma/sinapi/importacoes` | Importa tabela mensal (admin da plataforma) | 202, 403 |

## 7. Diagramas

### 7.1 Casos de uso

```mermaid
flowchart LR
  orc(["👤 Orçamentista"])
  eng(["👤 Engenheiro(a) com selo"])
  cli(["👤 Cliente"])
  adm(["👤 Admin da plataforma"])
  ia(["⚙️ Serviço de IA"])
  subgraph RF12["RF12 · Orçamento SINAPI"]
    uc1["Gerar orçamento do quantitativo"]
    uc2["Revisar vínculos e preços"]
    uc3["Aprovar orçamento"]
    uc4["Aprovar aditivo"]
    uc5["Importar tabela SINAPI"]
    uc6["Sugerir composição"]
    uc7["Consultar SINAPI - ferramenta"]
    uc8["Revalidar sugestão no backend"]
  end
  orc --> uc1 & uc2
  eng --> uc3
  cli --> uc4
  adm --> uc5
  uc1 -. inclui .-> uc6
  uc6 -. inclui .-> uc7
  uc6 -. inclui .-> uc8
  uc6 --- ia
```

### 7.2 Atividades

```mermaid
flowchart TD
  ini((início)) --> a1["Selecionar levantamento CONCLUIDO, UF, mês e regime"]
  a1 --> a2["Carregar TabelaSinapi vigente"]
  a2 --> a3["Para cada linha: há vínculo na Tabela A?"]
  a3 -- sim --> a4["Item com vínculo determinístico"]
  a3 -- não --> a5["Enfileirar para IA"]
  a5 --> a6["Chamada IA com ferramenta consultar_sinapi"]
  a6 --> d1{"stop_reason"}
  d1 -- refusal --> a7["Linha sem vínculo"]
  d1 -- end_turn --> a8["Validar JSON e revalidar código e unidade"]
  a8 --> d2{"Válido?"}
  d2 -- não --> a7
  d2 -- sim --> a9["Item marcado gerado por IA"]
  a4 & a7 & a9 --> a10["Calcular totais e BDI"]
  a10 --> a11["Orçamentista revisa e ajusta"]
  a11 --> a12["Enviar para aprovação"]
  a12 --> d3{"Selo VERIFIED, 2FA e sudo?"}
  d3 -- não --> e1["403 com motivo"] --> fim((fim))
  d3 -- sim --> d4{"Itens sem vínculo restantes?"}
  d4 -- sim --> e2["409: resolver itens pendentes"] --> a11
  d4 -- não --> a13["APROVADO + registro de operações"] --> fim
```

### 7.3 Classes

```mermaid
classDiagram
  direction LR
  class TabelaSinapi {
    +UUID id
    +String uf
    +YearMonth mesReferencia
    +Regime regime
    +String hash
  }
  class ComposicaoSinapi {
    +String codigo
    +String descricao
    +Unidade unidade
    +BigDecimal custoUnitario
  }
  class Orcamento {
    +UUID id
    +UUID obraId
    +UUID levantamentoId
    +UUID tabelaSinapiId
    +int versao
    +BigDecimal bdiPercentual
    +EstadoOrcamento estado
    +aprovar(Aprovador) void
  }
  class ItemOrcamento {
    +String codigoSinapi
    +String descricao
    +Unidade unidade
    +BigDecimal quantidade
    +BigDecimal custoUnitario
    +OrigemVinculo origem
    +boolean precoProprio
    +total() BigDecimal
  }
  class SugestaoIA {
    +String codigo
    +String justificativa
    +double confianca
    +boolean validadaBackend
  }
  class Aditivo {
    +UUID orcamentoBaseId
    +UUID orcamentoNovoId
    +BigDecimal diferenca
    +EstadoAditivo estado
  }
  class ModeloLinguagem {
    <<interface>>
    +completarComFerramentas(req) RespostaEstruturada
  }
  class ClaudeModeloLinguagem
  class FakeModeloLinguagem
  class ConsultaSinapiFerramenta {
    +String nome
    +executar(termo, unidade, uf, mes) List~ComposicaoSinapi~
  }
  class AssociacaoSinapiRota {
    +String modelo
    +String effort
    +sugerir(linhas) List~SugestaoIA~
  }
  TabelaSinapi "1" *-- "1..*" ComposicaoSinapi
  Orcamento "1" *-- "1..*" ItemOrcamento
  Orcamento --> TabelaSinapi
  ItemOrcamento "1" o-- "0..1" SugestaoIA
  Aditivo --> Orcamento
  ModeloLinguagem <|.. ClaudeModeloLinguagem
  ModeloLinguagem <|.. FakeModeloLinguagem
  AssociacaoSinapiRota --> ModeloLinguagem
  AssociacaoSinapiRota --> ConsultaSinapiFerramenta
  ConsultaSinapiFerramenta ..> TabelaSinapi : lê
```

### 7.4 Sequência

```mermaid
sequenceDiagram
  autonumber
  actor O as Orçamentista
  participant W as Web (orçamento)
  participant API as core-api orcamento
  participant R as AssociacaoSinapiRota
  participant CL as Claude API
  participant F as ConsultaSinapiFerramenta
  participant DB as PostgreSQL
  O->>W: Gerar orçamento
  W->>API: POST /orcamentos {levantamentoId, uf, mes}
  API->>DB: vínculos Tabela A
  API-->>W: 202 {id}
  API->>R: sugerir(linhas sem vínculo)
  R->>CL: messages.stream (system cacheado, tools strict, effort high)
  CL-->>R: tool_use consultar_sinapi
  R->>F: executar(termo, unidade, uf, mes)
  F->>DB: SELECT composições
  F-->>R: candidatas
  R->>CL: tool_result
  CL-->>R: JSON {codigo, justificativa, confianca}
  R->>R: checar stop_reason e validar schema
  R-->>API: sugestões
  API->>DB: revalida código e unidade, grava itens GERADO_POR_IA
  O->>W: revisa e envia para aprovação
  W->>API: POST /aprovacao (engenheiro com sudo)
  API->>DB: APROVADO + registro de operações
  API-->>W: 200
```

### 7.5 Máquina de estados (Orcamento)

```mermaid
stateDiagram-v2
  [*] --> PENDENTE: pedido de geração
  PENDENTE --> EM_EXECUCAO: associação em andamento
  EM_EXECUCAO --> GERADO_POR_IA: há itens sugeridos pela IA
  EM_EXECUCAO --> RASCUNHO: só vínculos determinísticos
  EM_EXECUCAO --> FALHOU: erro
  FALHOU --> PENDENTE: reprocessar
  GERADO_POR_IA --> EM_REVISAO: orçamentista revisa
  RASCUNHO --> EM_REVISAO: orçamentista revisa
  EM_REVISAO --> APROVADO: engenheiro com selo e 2FA
  EM_REVISAO --> REJEITADO: engenheiro rejeita
  REJEITADO --> EM_REVISAO: nova revisão
  APROVADO --> OBSOLETO: quantitativo ou tabela mudou
  EM_REVISAO --> OBSOLETO: quantitativo mudou
  OBSOLETO --> [*]
```

### 7.6 Componentes

```mermaid
flowchart LR
  subgraph WEB["apps/web"]
    OT["Tabela de orçamento com selo IA"]
    AP["Diálogo de aprovação"]
  end
  subgraph CORE["core-api"]
    OC["OrcamentoController"]
    OS["OrcamentoService"]
    subgraph IA["ia"]
      AR["AssociacaoSinapiRota"]
      ML["ModeloLinguagem"]
      FE["ConsultaSinapiFerramenta"]
      UR["RegistroUsoIA"]
    end
    SI["SinapiImportador @Scheduled"]
    OA["ObraAccess + SeloService"]
    QS["QuantitativoService - RF11"]
  end
  CL["Claude API"]
  DB[("PostgreSQL")]
  VK[("Valkey - cota de IA por empresa")]
  OT & AP --> OC --> OA
  OC --> OS --> QS
  OS --> AR --> ML --> CL
  AR --> FE --> DB
  AR --> UR --> DB
  ML --> VK
  SI --> DB
  OS --> DB
```

## 8. Testes previstos

| Tipo | Caso |
|---|---|
| Unitário (Java) | linha com vínculo na Tabela A não chama `ModeloLinguagem` |
| Unitário (Java) | `FakeModeloLinguagem` sugere código inexistente → item sem vínculo |
| Unitário (Java) | sugestão m² para linha em m³ → rejeitada |
| Unitário (Java) | `stop_reason = refusal` → orçamento segue com linhas sem vínculo |
| Integração `*IT` | ferramenta chamada com UF diferente da obra → resultado vazio |
| Integração `*IT` | orçamentista tenta aprovar → 403; engenheiro sem sudo → 403 `sudo_required` |
| Integração `*IT` | reimportar SINAPI com mesmo hash não cria versão |
| Avaliação (fora do CI) | 50 projetos conferidos, acurácia ≥ 90 %, chamada real à API |

## 9. Rastreabilidade

| Camada | Artefato | Status |
|---|---|---|
| Documento | `docs/rf/RF12-associacao-sinapi.md` | ✅ |
| Card | [Trello 64v8Sq35](https://trello.com/c/64v8Sq35) | ✅ |
| Web | `apps/web/src/app/(app)/obras/[obraId]/orcamento/` | ⏳ |
| API | `orcamento/OrcamentoController.java`, `OrcamentoService.java`, `SinapiImportador.java` | ⏳ |
| IA | `ia/rotas/AssociacaoSinapiRota.java`, `ia/ferramentas/ConsultaSinapiFerramenta.java` | ⏳ |
| Banco | `V11__sinapi.sql`, `V12__orcamento.sql` | ⏳ |
| Testes | `OrcamentoIT`, `AssociacaoSinapiRotaTest`, avaliação `eval/sinapi/` | ⏳ |
