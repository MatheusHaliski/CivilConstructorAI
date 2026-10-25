---
rf: RF16
titulo: Loop duplo de validação (código e geometria de sólido)
modulo: M7 Middleware & Agentes IA
status: especificado
cards:
  - https://trello.com/c/o2wBmxKy
paths:
  - geometria/auditor/**
  - geometria/kernel/**
  - geometria/api/**
  - geometria/tests/estresse/**
  - backend/cca-domain/src/main/java/br/com/civilconstructorai/modelo/**
  - backend/cca-application/src/main/java/br/com/civilconstructorai/modelo/**
  - backend/cca-infrastructure/src/main/java/br/com/civilconstructorai/modelo/**
  - backend/cca-web/src/main/java/br/com/civilconstructorai/api/modelo/**
  - backend/cca-infrastructure/src/main/resources/db/migration/V7__modelo_bim.sql
---

# RF16 · Loop duplo de validação (código e geometria de sólido)

> Coração da **Auditoria Paramétrica Híbrida**: LLMs são bons extratores semânticos, mas erram raciocínio
> espacial euclidiano. Nada entra no modelo sem passar por **dois laços** — o do **código** (o comando está bem
> formado e respeita as regras?) e o da **geometria** (o sólido resultante existe e é válido no kernel?). Quando o
> comando veio da IA, o erro estruturado volta ao modelo de linguagem para correção, no máximo 3 vezes.

## 1. Descrição

Entrada: um ou mais `ComandoVI` (RF01/RF02) em JSON, com `origem` = `USUARIO` (canvas/console) ou `IA` (texto
natural traduzido pelo Claude).

**Laço 1 — código** (`geometria/auditor/laco_codigo.py`):

1. **Sanitização do transporte**: normaliza Unicode (NFC), remove caracteres de controle, troca `º`/`ª` e
   símbolos fora do alfabeto VI por equivalentes ASCII em identificadores, evitando `UnicodeDecodeError` no
   middleware (texto livre de descrição é preservado em UTF-8).
2. **JSON Schema** do comando (draft 2020-12, `additionalProperties: false`), por código VI.
3. **Gramática VI**: código existe na Tabela B (funções), aridade e tipos dos argumentos, referências resolvidas
   (pavimento, eixo, material da Tabela A, parede hospedeira).
4. **Tabela C (controle e regras construtivas)**: ShortCurveTolerance (RF08), seções mínimas (RF08), Caixa
   Cartesiana Sanitizada e contagem de apoios (RF06), Disciplina Z (RF17). Essas regras **corrigem** o que é
   corrigível de forma determinística (ex.: zerar `BASE_OFFSET`, recortar à envoltória real) e **reprovam** o que
   não é (ex.: pilar de 10 cm).

**Laço 2 — geometria** (`geometria/auditor/laco_geometria.py`, kernel CadQuery/OpenCASCADE):

1. Gera os sólidos BRep dos elementos novos/alterados no contexto do pavimento.
2. `BRepCheck_Analyzer` válido; sem faces degeneradas; volume > 0.
3. Sem auto-interseção e sem interpenetração indevida entre elementos (pilar×pilar, parede×parede; pilar×viga e
   viga×laje são juntas esperadas e tratadas pela regra de prioridade de corte).
4. **Fechamento da envoltória** (tolerância 1 mm): durante a edição incremental o modelo pode estar
   **incompleto** (a primeira parede externa não forma anel), então aqui a regra gera só um **aviso**
   `ENVOLTORIA_ABERTA` com o ponto e a folga, e o comando é aprovado. A mesma regra **reprova** (`422`) nas
   **verificações de conclusão**, que rodam o laço 2 sobre o pavimento inteiro: análise estrutural (RF05),
   emissão de prancha (RF14), exportação IFC/STEP (RF15) e passagem da obra para `EM_ANDAMENTO` (RF22).
5. **Projeção vetorial de aberturas**: cada abertura é projetada na face da parede hospedeira (`NewOpening`); a
   abertura precisa caber inteira na face, com afastamento ≥ 0,10 m das bordas.

**Correção com o LLM**: se a origem é `IA` e algum laço reprova, o core-api monta uma mensagem com as violações
estruturadas (`codigo`, `elementoRef`, `regra`, `esperado`, `obtido`) e pede ao modelo uma nova versão do comando
(structured outputs, mesmo JSON Schema). Máximo de **3 iterações**; depois, `REPROVADO` com o relatório completo
para o usuário. Comandos de origem `USUARIO` não vão ao LLM: reprovação volta direto com `422`.

Cada iteração gera uma `IteracaoAuditoria` registrada: número, laço que falhou, violações, e — quando houve
chamada ao modelo — rota, modelo, `effort`, tokens de entrada/saída/cache, `stop_reason` e latência, **sem o
conteúdo** do prompt (que não contém dado pessoal, mas pode conter dado de projeto do cliente).

## 2. Atores

| Ator | Tipo | Responsabilidade |
|---|---|---|
| Arquiteto(a), Engenheiro(a) | `ARQUITETO`, `ENGENHEIRO` | originam comandos; recebem o relatório de auditoria |
| Serviço de IA (`ia.ModeloLinguagem`, impl. Claude) | sistema | traduz intenção e corrige o comando a partir das violações |
| Auditor Python (`geometria/auditor`) | sistema | executa laço 1 e regras da Tabela C |
| Kernel BIM (`geometria/kernel`, CadQuery/OCC) | sistema | executa laço 2 |

## 3. Critérios de aceite

- [ ] **CA-01** Nada é persistido no modelo sem `CicloAuditoria` em `APROVADO` para aquele comando (garantido por
  constraint `comando_vi.ciclo_auditoria_id NOT NULL` + estado verificado na transação).
- [ ] **CA-02** No máximo 3 iterações de correção com o LLM; a 4ª reprovação não chama o modelo e devolve `422` com
  o relatório completo.
- [ ] **CA-03** Cada iteração registrada com laço, violações, tokens, `stop_reason`, `effort` e latência; o
  registro não contém o texto do prompt nem dados pessoais.
- [ ] **CA-04** `stop_reason = refusal` ou `max_tokens` encerra o ciclo como `REPROVADO` com motivo explícito, sem
  tentar ler conteúdo parcial.
- [ ] **CA-05** Suíte de estresse com **15+ cenários** passa 100 %, incluindo: prédio de 4 pavimentos; malha 5x3;
  marquises escalonadas de 1,0 m a 2,0 m por andar; aberturas no térreo; extrapolação de 4 m numa malha 4x4
  (RF06); pilares com offset negativo, o "Efeito Z=0" (RF17); vetor de comprimento nulo (RF08).
- [ ] **CA-06** Auditoria idempotente: auditar de novo um comando aprovado não produz correções.
- [ ] **CA-07** Laço 1 + laço 2 de um comando de até 50 elementos em ≤ 2 s (p95), sem contar a chamada ao LLM.
- [ ] **CA-08** O relatório de auditoria lista cada correção automática com antes/depois e a regra da Tabela C.
- [ ] **CA-09** Edição incremental nunca é bloqueada por envoltória aberta: desenhar paredes externas uma a uma
  (ou abrir temporariamente uma delas) aprova cada comando com o aviso `ENVOLTORIA_ABERTA`; o aviso desaparece
  quando o anel fecha. Análise, prancha, exportação e `EM_ANDAMENTO` com envoltória aberta → `422`
  `ENVOLTORIA_ABERTA` listando pavimento, ponto e folga.

## 4. Regras de negócio

- **RN-01** Ordem fixa: sanitização → schema → gramática → Tabela C → geometria. Falha em uma etapa interrompe as
  seguintes da mesma iteração, mas a etapa reúne **todas** as violações dela (o usuário vê todos os erros de uma
  vez).
- **RN-02** Correção determinística (Tabela C) não conta como iteração do LLM.
- **RN-03** A IA nunca recebe dados pessoais: o contexto enviado contém só geometria, códigos e materiais.
- **RN-04** Rota `correcao-auditoria`: `claude-opus-5-5`, `effort: high`, structured outputs com o schema do
  comando, prompt de sistema fixo em cache (prompt caching), sem `temperature`; fallback do servidor ligado.
- **RN-05** Comando aprovado vindo da IA continua `RascunhoIA` (`GERADO_POR_IA`) até o profissional confirmar
  (RF27); auditoria aprovada ≠ aprovação técnica.

## 5. Base legal e normas

- LGPD Art. 6º, III (necessidade) e Art. 33 (transferência internacional ao provedor do modelo): só dado de projeto
  sem dado pessoal; finalidade e base legal da rota registradas.
- LGPD Art. 20: resultado de IA sujeito a revisão humana (RF27).
- NBR 6118 (seções mínimas, via RF08), NBR 6492, ISO 10303 (validade de BRep).

## 6. API

Interna (core-api → geometria, rede privada, mTLS):

| Método e rota | Descrição | Respostas |
|---|---|---|
| `POST /interno/auditoria/laco-codigo` | Sanitização, schema, gramática VI, Tabela C | 200 `{aprovado, correcoes[], violacoes[]}` |
| `POST /interno/auditoria/laco-geometria` | BRep, interseções, envoltória, aberturas | 200 `{aprovado, violacoes[], malhas[]}` |

Pública:

| Método e rota | Descrição | Respostas |
|---|---|---|
| `GET /api/v1/obras/{obraId}/auditorias/{cicloId}` | Relatório do ciclo com iterações e correções | 200, 404 |
| `GET /api/v1/obras/{obraId}/auditorias?comandoId=` | Ciclos de um comando | 200 |

## 7. Diagramas

### 7.1 Casos de uso

```mermaid
flowchart LR
  usr(["👤 Arquiteto(a) / Engenheiro(a)"])
  llm(["🤖 Serviço de IA (Claude)"])
  aud(["⚙️ Auditor Python"])
  ker(["⚙️ Kernel CadQuery/OCC"])
  subgraph RF16["RF16 · Loop duplo de validação"]
    uc1["Submeter comando VI"]
    uc2["Validar código (laço 1)"]
    uc3["Validar geometria (laço 2)"]
    uc4["Corrigir comando com o LLM"]
    uc5["Consultar relatório de auditoria"]
  end
  usr --> uc1 & uc5
  uc1 -. inclui .-> uc2
  uc2 -. inclui .-> uc3
  uc4 -. estende .-> uc2
  uc2 --- aud
  uc3 --- ker
  uc4 --- llm
```

### 7.2 Atividades

```mermaid
flowchart TD
  ini((início)) --> a0["Receber ComandoVI e criar CicloAuditoria"]
  a0 --> a1["Sanitizar texto do transporte"]
  a1 --> a2["Validar JSON Schema"]
  a2 --> a3["Validar gramática VI e referências"]
  a3 --> a4["Aplicar Tabela C: corrigir o corrigível"]
  a4 --> d1{"Laço 1 sem violações?"}
  d1 -- sim --> b1["Gerar BRep no kernel"]
  b1 --> b2["BRepCheck, interseções, envoltória"]
  b2 --> b3["Projetar aberturas na face (NewOpening)"]
  b3 --> d2{"Laço 2 sem violações?"}
  d2 -- sim --> ok["APROVADO: persistir comando e versão"] --> fim((fim))
  d1 -- não --> d3{"Origem IA e iteração menor que 3?"}
  d2 -- não --> d3
  d3 -- não --> rep["REPROVADO: 422 com relatório completo"] --> fim
  d3 -- sim --> c1["Montar violações estruturadas"]
  c1 --> c2["Chamar ModeloLinguagem: corrigir-comando-vi"]
  c2 --> d4{"stop_reason = end_turn?"}
  d4 -- não --> rep
  d4 -- sim --> c3["Registrar IteracaoAuditoria e iteração + 1"]
  c3 --> a1
```

### 7.3 Classes

```mermaid
classDiagram
  direction LR
  class CicloAuditoria {
    +UUID id
    +UUID comandoId
    +OrigemComando origem
    +EstadoCiclo estado
    +int iteracoes
    +int maxIteracoes = 3
    +Instant inicio
    +Instant fim
  }
  class IteracaoAuditoria {
    +int numero
    +Laco lacoReprovado
    +List~Violacao~ violacoes
    +List~Correcao~ correcoes
    +ChamadaModeloRegistro chamada
  }
  class Violacao {
    +String codigo
    +String elementoRef
    +String regra
    +String esperado
    +String obtido
    +String mensagemPtBr
  }
  class Correcao {
    +String regraTabelaC
    +String elementoRef
    +String antes
    +String depois
  }
  class ChamadaModeloRegistro {
    +String rota
    +String modelo
    +String effort
    +int tokensEntrada
    +int tokensSaida
    +int tokensCacheLidos
    +String stopReason
    +long latenciaMs
  }
  class LacoCodigo {
    +sanitizar(str) str
    +validarSchema(dict) List~Violacao~
    +validarGramatica(dict) List~Violacao~
    +aplicarTabelaC(dict) ResultadoLaco
  }
  class LacoGeometria {
    +gerarBrep(dict) Solido
    +verificar(Solido) List~Violacao~
    +projetarAberturas(Parede) List~Violacao~
  }
  class ModeloLinguagem {
    <<interface>>
    +corrigirComando(ComandoVI, List~Violacao~) RespostaModelo
  }
  class ClaudeModeloLinguagem
  class FakeModeloLinguagem
  class OrquestradorAuditoria {
    +auditar(ComandoVI) CicloAuditoria
  }
  CicloAuditoria "1" *-- "1..4" IteracaoAuditoria
  IteracaoAuditoria "1" *-- "0..*" Violacao
  IteracaoAuditoria "1" *-- "0..*" Correcao
  IteracaoAuditoria "1" o-- "0..1" ChamadaModeloRegistro
  ModeloLinguagem <|.. ClaudeModeloLinguagem
  ModeloLinguagem <|.. FakeModeloLinguagem
  OrquestradorAuditoria --> LacoCodigo
  OrquestradorAuditoria --> LacoGeometria
  OrquestradorAuditoria --> ModeloLinguagem
  OrquestradorAuditoria ..> CicloAuditoria : cria
```

### 7.4 Sequência

```mermaid
sequenceDiagram
  autonumber
  participant API as core-api ComandoViService
  participant OR as OrquestradorAuditoria
  participant L1 as auditor laço 1 (Python)
  participant L2 as kernel laço 2 (Python)
  participant IA as ModeloLinguagem (Claude)
  participant DB as PostgreSQL
  API->>OR: auditar(comando origem IA)
  OR->>DB: INSERT ciclo_auditoria INICIADO
  loop até aprovar ou 3 iterações do LLM
    OR->>L1: POST /interno/auditoria/laco-codigo
    L1-->>OR: correcoes[] + violacoes[]
    opt laço 1 aprovado
      OR->>L2: POST /interno/auditoria/laco-geometria
      L2-->>OR: violacoes[] + malhas[]
    end
    alt aprovado
      OR->>DB: ciclo APROVADO
    else reprovado e iteração menor que 3
      OR->>IA: corrigirComando(comando, violacoes) effort high
      IA-->>OR: stop_reason + comando corrigido (JSON)
      OR->>DB: INSERT iteracao_auditoria (tokens, stop_reason)
    else reprovado e limite atingido
      OR->>DB: ciclo REPROVADO
    end
  end
  OR-->>API: CicloAuditoria
  alt APROVADO
    API->>DB: INSERT comando_vi, elementos, versão + 1
  else REPROVADO
    API-->>API: 422 com relatório
  end
```

### 7.5 Máquina de estados (CicloAuditoria)

```mermaid
stateDiagram-v2
  [*] --> INICIADO
  INICIADO --> LACO_CODIGO
  LACO_CODIGO --> LACO_GEOMETRIA: sem violações
  LACO_CODIGO --> CORRIGINDO_COM_LLM: violações, origem IA, iteração menor que 3
  LACO_CODIGO --> REPROVADO: violações, origem USUARIO ou limite
  LACO_GEOMETRIA --> APROVADO: sem violações
  LACO_GEOMETRIA --> CORRIGINDO_COM_LLM: violações, origem IA, iteração menor que 3
  LACO_GEOMETRIA --> REPROVADO: violações, origem USUARIO ou limite
  CORRIGINDO_COM_LLM --> LACO_CODIGO: end_turn com comando novo
  CORRIGINDO_COM_LLM --> REPROVADO: refusal, max_tokens ou erro não retentável
  APROVADO --> [*]
  REPROVADO --> [*]
```

### 7.6 Componentes

```mermaid
flowchart LR
  subgraph CORE["core-api (Spring Boot 4.1)"]
    CS["modelo/ComandoViService"]
    OR["auditoria/OrquestradorAuditoria"]
    subgraph IA["ia"]
      ML["ModeloLinguagem"]
      CL["ClaudeModeloLinguagem (anthropic-java)"]
      FK["FakeModeloLinguagem (testes)"]
    end
    RA["auditoria/RelatorioController"]
  end
  subgraph GEO["geometria (Python 3.13)"]
    API2["api FastAPI interna"]
    L1["auditor/laco_codigo.py"]
    TC["auditor/tabela_c/ (RF06, RF08, RF17)"]
    L2["auditor/laco_geometria.py"]
    KE["kernel CadQuery/OCC"]
    SCH["data_model/schemas VI e Tabela A"]
  end
  CLAUDE[("Claude API")]
  DB[("PostgreSQL")]
  CS --> OR
  OR --> ML
  ML --> CL -- "HTTPS" --> CLAUDE
  ML -.-> FK
  OR -- "HTTP interno mTLS" --> API2
  API2 --> L1 --> SCH
  L1 --> TC
  API2 --> L2 --> KE
  OR --> DB
  RA --> DB
```

## 8. Testes previstos

Suíte de estresse em `geometria/tests/estresse/` (cada cenário é um conjunto de comandos VI + resultado esperado),
executada também no core-api com `FakeModeloLinguagem` determinístico:

| # | Cenário | Esperado |
|---|---|---|
| E01 | Prédio de 4 pavimentos, malha 5x3, pé-direito 3,0 m | aprovado; 60 pilares com base/topo nos níveis |
| E02 | Marquises escalonadas de 1,0 a 2,0 m por andar | aprovado; balanços conferidos por pavimento |
| E03 | Aberturas no térreo (portas e vitrines) | todas projetadas na face hospedeira |
| E04 | Extrapolação de 4 m numa malha 4x4 (Fencepost) | corrigido pela Caixa Cartesiana (RF06) |
| E05 | Pilares com offset negativo (Efeito Z=0) | `BASE_OFFSET` zerado (RF17) |
| E06 | Pilar transpassando dois andares sem marcação de contínuo | corrigido e dividido (RF17) |
| E07 | Vetor de comprimento nulo | descartado antes do kernel (RF08) |
| E08 | Parede de 0,03 m | descartada por ShortCurveTolerance |
| E09 | Pilar 15x15 cm | reprovado: seção mínima (RF08) |
| E10 | Janela maior que a parede | reprovado no laço 2 |
| E11 | Envoltória com folga de 2 cm | aviso `ENVOLTORIA_ABERTA` na edição; reprovado ao emitir prancha ou rodar análise |
| E11b | Desenhar o térreo parede a parede, uma por comando | todas aprovadas com aviso; aviso some quando o anel fecha |
| E12 | Dois pilares sobrepostos | reprovado: interpenetração |
| E13 | Comando com material inexistente | reprovado no laço 1 (gramática) |
| E14 | Texto com `º`, `ª` e emojis nos identificadores | sanitizado; sem `UnicodeDecodeError` |
| E15 | LLM corrige na 2ª iteração (fake) | aprovado com 2 iterações registradas |
| E16 | LLM falha 3 vezes (fake) | reprovado; 4ª chamada não acontece |
| E17 | Fake retorna `refusal` | reprovado com motivo; conteúdo não lido |
| E18 | Reauditar comando aprovado | zero correções (idempotência) |

Outros: `CicloAuditoriaIT` verifica que nenhum `comando_vi` existe sem ciclo `APROVADO`; teste de log garante que
`iteracao_auditoria` não guarda texto de prompt.

## 9. Rastreabilidade

| Camada | Artefato | Status |
|---|---|---|
| Documento | `docs/rf/RF16-loop-duplo-validacao.md` | ✅ |
| Card | [Trello o2wBmxKy](https://trello.com/c/o2wBmxKy) | ✅ |
| Domínio | `cca-domain · modelo/Auditor.java` (laço 1: Tabela C mínimos 0,19/0,036/0,12/0,08/0,05, `C-SHORT_CURVE`, Caixa Cartesiana RECORTAR/BLOQUEAR, Disciplina Z), `ServicoGeometriaLocal.java` (laço 2 JTS: G-AUTO_INTERSECAO, G-INTERPENETRACAO, G-SEM_HOSPEDEIRA, ENVOLTORIA_ABERTA aviso→violação na conclusão), `RelatorioAuditoria.java` | ✅ |
| Aplicação | `cca-application · modelo/AuditorService.java` (ciclo APROVADO/REPROVADO persistido mesmo no rollback do 422, correção LLM ≤ 3 iterações só para origem IA), `ModeloService#verificarConclusao/#exigirConclusao` | ✅ |
| Geometria | `cca-infrastructure · modelo/ServicoGeometriaHttp.java` (kernel Python com fallback local + aviso `G-KERNEL_INDISPONIVEL`); `geometria/auditor/*.py` | 🟡 |
| IA | `ia/ModeloLinguagem.java`/`RotaIa.CORRECAO_AUDITORIA` (fork de IA) | ✅ |
| API | `cca-web · api/modelo/ModeloExceptionHandler.java` (422 `auditoria_reprovada` com relatório completo + `errors`), `ModeloController` (`POST /modelo/verificar-conclusao`) | ✅ |
| Banco | `V7__modelo_bim.sql` (ciclo_auditoria com iterações em jsonb) | ✅ |
| Testes | `EstresseAuditoriaTest` (E01–E18 em Java), `ModeloBimTest`, `ModeloComandoIT#reprovacaoDevolve422ComRelatorioCompleto`; `geometria/tests/estresse/` (Python) | 🟡 |
