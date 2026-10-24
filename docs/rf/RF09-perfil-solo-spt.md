---
rf: RF09
titulo: Cadastro de perfil estratigráfico de solo e ensaio SPT
modulo: M4 Geotecnia & Fundações
status: especificado
cards:
  - https://trello.com/c/LzHFBr6H
paths:
  - backend/src/main/java/br/com/civilconstructorai/geotecnia/**
  - backend/src/main/java/br/com/civilconstructorai/ia/rotas/ExtracaoBoletimSpt*.java
  - geometria/calculations/fundacoes/perfil.py
  - apps/web/src/app/(app)/obras/[obraId]/geotecnia/**
  - backend/src/main/resources/db/migration/*geotecnia*
---

# RF09 · Cadastro de perfil estratigráfico de solo e ensaio SPT

> O engenheiro cadastra os furos de sondagem SPT da obra (posição, cota, nível d'água, camadas e Nspt por
> metro), digitando ou importando o boletim em PDF. A IA lê o boletim **com citações** de página e trecho, e
> nada é gravado sem conferência humana. Os furos alimentam o perfil estratigráfico interpolado usado no
> dimensionamento de fundações (RF10).

## 1. Descrição

Cada `FuroSondagem` tem código (SP-01…), localização `geometry(Point, 4674)` (SIRGAS 2000) que precisa estar
dentro do perímetro da obra (`ST_Within`), cota da boca do furo, data do ensaio, empresa executora, nível
d'água (inicial e após 24 h) e critério de paralisação. As `CamadaSolo` registram cota de topo e base, tipo
(argila, silte, areia, pedregulho, rocha, com adjetivos de cor e consistência/compacidade) e as `LeituraSpt`
registram por metro o Nspt (soma dos golpes dos dois últimos 15 cm) e os golpes/penetração de cada trecho.
"Impenetrável" e "limite de sondagem" são registrados explicitamente, não como número.

**Importação do boletim**: o PDF sobe por URL pré-assinada para o S3 (`uploads/` com expiração de 1 dia até
ser confirmado; depois movido para `obras/{id}/sondagens/`). A extração usa duas chamadas separadas à IA,
porque citações não combinam com structured outputs:

1. **Extrair dados** (`effort: high`, structured outputs com o JSON Schema de `FuroSondagemExtraido`), PDF
   como bloco `document`.
2. **Apontar fontes** (`citations: {enabled: true}`) para cada camada/leitura extraída, retornando página e
   trecho, exibidos lado a lado na tela de conferência.

O resultado é um `RascunhoIA` (`GERADO_POR_IA`) que o engenheiro confere campo a campo e confirma; só então os
dados viram `FuroSondagem` com estado `CONFIRMADO`. O perfil estratigráfico entre furos é interpolado no
serviço Python (`calculations/fundacoes/perfil.py`) por cotas de topo das camadas correlacionadas.

## 2. Atores

| Ator | Papel na obra | Pode |
|---|---|---|
| Engenheiro(a) responsável | `ENGENHEIRO` | cadastrar, importar, conferir, confirmar e editar furos |
| Arquiteto(a), Orçamentista | `ARQUITETO`, `ORCAMENTISTA` | visualizar perfil confirmado |
| Serviço de IA | sistema (`ia`) | extrair dados do boletim e apontar citações |
| Serviço de cálculo | sistema (`geometria`) | interpolar perfil estratigráfico |

## 3. Critérios de aceite

- [ ] **CA-01** Furo fora do perímetro PostGIS da obra → `422` "Furo SP-03 fora do perímetro da obra".
- [ ] **CA-02** Camadas de um furo são contíguas (base de uma = topo da próxima), sem sobreposição nem lacuna,
  e a última termina na cota final do furo.
- [ ] **CA-03** Nspt é inteiro ≥ 0; leituras com "impenetrável" usam o marcador `IMPENETRAVEL` e encerram o
  furo; todos os erros de um furo voltam juntos em `422`.
- [ ] **CA-04** A extração por IA mostra para cada valor a página e o trecho citado; o engenheiro precisa
  confirmar (ou corrigir) antes de gravar; nada vai para `FuroSondagem` sem confirmação.
- [ ] **CA-05** O boletim original fica no S3 e é baixado por URL pré-assinada de 15 min; o tipo é conferido
  pelos bytes (PDF) e o tamanho máximo é 32 MB.
- [ ] **CA-06** A chamada à IA registra rota, finalidade ("extração de boletim SPT"), base legal, tokens e
  `stop_reason`; recusa (`refusal`) devolve ao usuário a opção de cadastro manual.
- [ ] **CA-07** Alterar um furo confirmado marca como `OBSOLETO` os dimensionamentos de fundação (RF10) que o
  usaram.

## 4. Regras de negócio

- **RN-01** Mínimo de furos segundo NBR 8036: 2 furos até 200 m² de projeção, 3 até 400 m², +1 a cada 200 m²
  até 1.200 m²; abaixo do mínimo o RF10 mostra alerta (não bloqueia o cadastro).
- **RN-02** Nível d'água ausente deve ser declarado "não encontrado até a cota X".
- **RN-03** Classificação de compacidade/consistência derivada do Nspt pela NBR 6484 (Anexo A) é calculada,
  não digitada.
- **RN-04** Antes de enviar PDF grande, `count_tokens` estima o custo; acima do limite da rota a extração vai
  para a Batch API (processamento noturno) com aviso ao usuário.
- **RN-05** O boletim pode conter nome/CREA do sondador: esses campos não são extraídos nem enviados em
  prompts além do próprio documento; o registro guarda apenas a empresa executora.

## 5. Base legal e normas

- NBR 6484:2020 (SPT — método de ensaio), NBR 8036 (programação de sondagens), NBR 6502 (terminologia de
  solos).
- LGPD: o PDF pode conter dados pessoais do responsável pela sondagem; tratamento para execução de contrato
  (Art. 7º, V) e envio ao modelo com transferência internacional documentada (Art. 33); finalidade registrada.
- Responsabilidade técnica: dados confirmados pelo engenheiro(a) da obra.

## 6. API

| Método e rota | Descrição | Respostas |
|---|---|---|
| `POST /api/v1/obras/{obraId}/sondagens/uploads` | URL pré-assinada para o PDF (tipo e tamanho na assinatura) | 201, 403, 404 |
| `POST /api/v1/obras/{obraId}/sondagens/extracoes` | Inicia extração por IA do boletim enviado | 202 + id, 403, 422 |
| `GET /api/v1/obras/{obraId}/sondagens/extracoes/{id}` | Rascunho extraído + citações | 200, 404 |
| `POST /api/v1/obras/{obraId}/sondagens/extracoes/{id}/confirmacao` | Confirma (com correções) e cria furos | 201, 409, 422 |
| `POST /api/v1/obras/{obraId}/sondagens` | Cadastro manual de furo | 201, 403, 422 |
| `PUT /api/v1/obras/{obraId}/sondagens/{furoId}` | Edição de furo confirmado | 200, 409, 422 |
| `GET /api/v1/obras/{obraId}/perfil-estratigrafico?secao=` | Perfil interpolado em uma seção | 200, 404 |

## 7. Diagramas

### 7.1 Casos de uso

```mermaid
flowchart LR
  eng(["👤 Engenheiro(a)"])
  leit(["👤 Arquiteto(a) / Orçamentista"])
  ia(["⚙️ Serviço de IA"])
  py(["⚙️ Serviço de cálculo"])
  subgraph RF09["RF09 · Sondagem SPT"]
    uc1["Cadastrar furo manualmente"]
    uc2["Importar boletim PDF"]
    uc3["Conferir extração com citações"]
    uc4["Confirmar furos"]
    uc5["Visualizar perfil estratigráfico"]
    uc6["Extrair dados do boletim"]
    uc7["Interpolar perfil"]
  end
  eng --> uc1 & uc2 & uc3 & uc4 & uc5
  leit --> uc5
  uc2 -. inclui .-> uc6
  uc5 -. inclui .-> uc7
  uc6 --- ia
  uc7 --- py
```

### 7.2 Atividades

```mermaid
flowchart TD
  ini((início)) --> d0{"Forma de entrada"}
  d0 -- manual --> m1["Preencher furo, camadas e leituras"]
  d0 -- boletim --> b1["Pedir URL pré-assinada e enviar PDF"]
  b1 --> b2["Verificar tipo pelos bytes e tamanho"]
  b2 --> b3["count_tokens e escolha: síncrono ou Batch"]
  b3 --> b4["Chamada 1: structured outputs extrai furos"]
  b4 --> d1{"stop_reason = refusal?"}
  d1 -- sim --> m1
  d1 -- não --> b5["Validar JSON no backend"]
  b5 --> b6["Chamada 2: citações por valor extraído"]
  b6 --> b7["Rascunho GERADO_POR_IA na tela de conferência"]
  b7 --> b8["Engenheiro confere e corrige"]
  b8 --> v1
  m1 --> v1{"Dentro do perímetro, camadas contíguas, Nspt válido?"}
  v1 -- não --> e1["422 com todos os erros"] --> d0
  v1 -- sim --> s1["Gravar FuroSondagem CONFIRMADO"]
  s1 --> s2["Mover PDF para pasta definitiva no S3"]
  s2 --> s3["Marcar fundações dependentes como OBSOLETO"]
  s3 --> fim((fim))
```

### 7.3 Classes

```mermaid
classDiagram
  direction LR
  class FuroSondagem {
    +UUID id
    +UUID obraId
    +String codigo
    +Point localizacao
    +double cotaBoca
    +LocalDate dataEnsaio
    +String empresaExecutora
    +Double nivelAguaInicial
    +Double nivelAgua24h
    +EstadoFuro estado
    +validar() List~Violacao~
  }
  class CamadaSolo {
    +double cotaTopo
    +double cotaBase
    +TipoSolo tipo
    +String descricao
    +String compacidadeOuConsistencia
  }
  class LeituraSpt {
    +double profundidadeM
    +Integer nspt
    +int golpes1
    +int golpes2
    +int golpes3
    +boolean impenetravel
  }
  class BoletimSpt {
    +UUID id
    +String chaveS3
    +String sha256
    +long tamanhoBytes
  }
  class ExtracaoBoletim {
    +UUID id
    +UUID boletimId
    +EstadoRascunho estado
    +String jsonExtraido
    +int tokensEntrada
    +String stopReason
  }
  class Citacao {
    +String campo
    +int pagina
    +String trecho
  }
  class PerfilEstratigrafico {
    <<Python>>
    +interpolar(furos, secao) List~CamadaInterpolada~
  }
  class ModeloLinguagem {
    <<interface>>
    +extrairEstruturado(doc, schema, effort)
    +citar(doc, perguntas)
  }
  FuroSondagem "1" *-- "1..*" CamadaSolo
  FuroSondagem "1" *-- "1..*" LeituraSpt
  FuroSondagem "0..*" --> "0..1" BoletimSpt : origem
  ExtracaoBoletim --> BoletimSpt
  ExtracaoBoletim "1" *-- "0..*" Citacao
  ExtracaoBoletim ..> FuroSondagem : confirmação cria
  ExtracaoBoletim ..> ModeloLinguagem : usa
  PerfilEstratigrafico ..> FuroSondagem : lê
```

### 7.4 Sequência

```mermaid
sequenceDiagram
  autonumber
  actor E as Engenheiro(a)
  participant W as Web (geotecnia)
  participant API as core-api geotecnia
  participant S3 as S3
  participant IA as ia ClaudeModeloLinguagem
  participant DB as PostgreSQL + PostGIS
  E->>W: Importar boletim
  W->>API: POST /sondagens/uploads {tamanho, tipo}
  API-->>W: URL pré-assinada (15 min)
  W->>S3: PUT PDF
  W->>API: POST /sondagens/extracoes {chave}
  API->>S3: lê bytes, confere PDF
  API->>IA: messages.stream (document + JSON Schema, effort high)
  IA-->>API: JSON furos + stop_reason end_turn
  API->>API: valida JSON Schema e regras
  API->>IA: messages (document + citations enabled)
  IA-->>API: citações página e trecho
  API->>DB: INSERT extracao GERADO_POR_IA
  API-->>W: 202 {id}
  W->>API: GET /extracoes/{id}
  API-->>W: rascunho + citações
  E->>W: corrige e confirma
  W->>API: POST /extracoes/{id}/confirmacao
  API->>DB: ST_Within(furo, perimetro_obra) e contiguidade
  alt válido
    API->>DB: INSERT furo, camadas, leituras (CONFIRMADO)
    API-->>W: 201
  else inválido
    API-->>W: 422 errors completo
  end
```

### 7.5 Máquina de estados (ExtracaoBoletim)

```mermaid
stateDiagram-v2
  [*] --> PENDENTE: PDF enviado
  PENDENTE --> EM_EXECUCAO: worker chama IA
  EM_EXECUCAO --> GERADO_POR_IA: JSON válido + citações
  EM_EXECUCAO --> FALHOU: recusa, erro ou JSON inválido
  FALHOU --> PENDENTE: tentar de novo
  GERADO_POR_IA --> EM_REVISAO: engenheiro abre conferência
  EM_REVISAO --> APROVADO: confirma e cria furos
  EM_REVISAO --> REJEITADO: descarta extração
  APROVADO --> [*]
  REJEITADO --> [*]
```

### 7.6 Componentes

```mermaid
flowchart LR
  subgraph WEB["apps/web"]
    UP["Upload de boletim"]
    CF["Conferência lado a lado PDF e dados"]
    PF["Visualizador de perfil"]
  end
  subgraph CORE["core-api"]
    GC["SondagemController"]
    GS["SondagemService"]
    EX["ExtracaoBoletimSptRota"]
    ML["ModeloLinguagem - ClaudeModeloLinguagem"]
    AR["ArquivoService S3"]
    OA["ObraAccess"]
  end
  subgraph PY["geometria"]
    PE["calculations/fundacoes/perfil.py"]
  end
  CL["Claude API claude-opus-5-5"]
  DB[("PostgreSQL + PostGIS")]
  S3[("S3 sa-east-1")]
  UP --> GC
  CF --> GC
  PF --> GC
  GC --> OA
  GC --> GS --> DB
  GS --> EX --> ML --> CL
  GS --> AR --> S3
  GS -- "HTTP interno" --> PE
```

## 8. Testes previstos

| Tipo | Caso |
|---|---|
| Unitário (Java) | camadas com lacuna de 0,20 m → violação "camadas não contíguas" |
| Unitário (Java) | furo com `ST_Within` falso → 422 |
| Unitário (Java) | `FakeModeloLinguagem` devolve JSON fixo → rascunho com citações mapeadas |
| Unitário (Java) | `stop_reason = refusal` → extração `FALHOU` com sugestão de cadastro manual |
| Integração `*IT` | upload com bytes de PNG declarado como PDF → 422 |
| Integração `*IT` | confirmar extração cria furo e marca fundação dependente `OBSOLETO` |
| Avaliação (fora do CI) | 30 boletins reais conferidos: acurácia de Nspt ≥ 98 % |

## 9. Rastreabilidade

| Camada | Artefato | Status |
|---|---|---|
| Documento | `docs/rf/RF09-perfil-solo-spt.md` | ✅ |
| Card | [Trello LzHFBr6H](https://trello.com/c/LzHFBr6H) | ✅ |
| Web | `apps/web/src/app/(app)/obras/[obraId]/geotecnia/` | ⏳ |
| API | `geotecnia/SondagemController.java`, `SondagemService.java` | ⏳ |
| IA | `ia/rotas/ExtracaoBoletimSptRota.java` + schema JSON | ⏳ |
| Banco | `V13__geotecnia.sql` (furo_sondagem, camada_solo, leitura_spt, extracao_boletim) | ⏳ |
| Cálculo | `geometria/calculations/fundacoes/perfil.py` | ⏳ |
| Testes | `SondagemIT`, `ExtracaoBoletimSptRotaTest`, `test_perfil.py` | ⏳ |
