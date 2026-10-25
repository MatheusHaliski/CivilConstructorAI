---
rf: RF25
titulo: "Direitos do titular: ver, corrigir, exportar e saber compartilhamentos"
modulo: M8 Privacidade LGPD & IA responsável
status: especificado
cards:
  - https://trello.com/c/BQtSoryZ
paths:
  - backend/src/main/java/br/com/civilconstructorai/lgpd/titular/**
  - backend/src/main/java/br/com/civilconstructorai/lgpd/exportacao/**
  - backend/src/main/java/br/com/civilconstructorai/lgpd/inventario/**
  - backend/src/main/java/br/com/civilconstructorai/arquivo/**
  - backend/src/main/java/br/com/civilconstructorai/registro/**
  - backend/src/main/resources/db/migration/*titular*
  - apps/web/src/app/(app)/conta/privacidade/seus-dados/**
  - apps/web/src/app/(app)/empresa/[empresaId]/privacidade/**
---

# RF25 · Direitos do titular: ver, corrigir, exportar e saber compartilhamentos

> A pessoa vê o que o CivilConstructorAI guarda sobre ela, corrige o que estiver errado, baixa uma cópia
> completa e sabe com quem os dados foram compartilhados. A construtora, como controladora dos dados dos seus
> trabalhadores, tem o inventário desses dados e uma fila para atender os pedidos dos titulares.

## 1. Descrição

O RF cobre os direitos do Art. 18 em duas visões:

**Visão do titular — tela "Seus dados"** (`/conta/privacidade/seus-dados`):

1. **Ver** (Art. 18, I e II): resumo por categoria — conta e perfil, empresas e papéis por obra, registros
   profissionais (CREA/CAU), consentimentos (RF24), sessões ativas, comandos e aprovações de autoria própria,
   fotos enviadas, presença. Cada categoria mostra finalidade, base legal e prazo de retenção.
2. **Corrigir** (Art. 18, III): campos de perfil editáveis direto; dados mantidos por uma empresa (ex.: cadastro de
   trabalhador, função, presença) geram um **pedido de correção** para aquela empresa, porque ela é a
   controladora. Correção de nome ou registro em conta com selo `VERIFIED` volta o selo a `PENDING` (RF21).
3. **Exportar** (Art. 18, V): job assíncrono gera `export.json` (schema versionado `cca-export/v1`) com todas as
   categorias + os arquivos do titular, empacotados num **ZIP cifrado (AES-256)**; a senha aleatória é mostrada
   **uma única vez** na tela, e o arquivo fica no S3 sob `exports/` por 24 h, baixado por URL pré-assinada de
   15 min. Exige **modo sudo** (RF23).
4. **Compartilhamentos** (Art. 18, VII): lista das entidades com quem os dados foram compartilhados — empresas
   e obras onde a pessoa é membro, operadores (provedor de nuvem, provedor de e-mail, Anthropic para a camada
   de IA — com indicação de transferência internacional, Art. 33) — e o histórico de exportações feitas pela
   empresa que incluíram dados dela.
5. **Pedidos** (Art. 18, IV, VI e IX; Art. 20): abrir pedido de anonimização, eliminação de dado tratado com
   consentimento, oposição ou revisão de decisão automatizada (encaminha para RF27). Eliminação da conta inteira
   é o RF26.

**Visão da empresa — "Dados dos trabalhadores"** (`/empresa/{id}/privacidade`), só dono/admin:

- **Inventário** (Art. 37): por categoria de dado de trabalhador (identificação, contato, função, presença,
  fotos, documentos trabalhistas, ASO/afastamento), com finalidade, base legal, prazo de retenção configurado
  pela empresa e quem acessa. Dado de saúde aparece só como **categoria e contagem**, nunca o conteúdo.
- **Fila de pedidos dos titulares** (Art. 18 §3º–§5º): pedidos dirigidos à empresa, com prazo de **15 dias**
  (Art. 19, II), estado e resposta registrada.
- Canal do **Encarregado** da plataforma (Art. 41) para pedidos sobre dados que o CivilConstructorAI trata como
  controlador (conta, login, cobrança).

Dado de saúde (ASO, afastamento): o titular vê os próprios; na empresa, só técnico(a) de segurança e RH da obra
veem o conteúdo (RF21). Nunca entra em URL, log, analytics, notificação push nem no contexto da IA.

## 2. Atores

| Ator | Pode |
|---|---|
| Titular (usuário com conta) | ver, corrigir, exportar, ver compartilhamentos, abrir pedidos |
| Trabalhador sem login | pedir pelo canal do Encarregado ou pela empresa; a empresa registra o pedido em nome dele |
| Dono / administrador da empresa (controladora) | ver inventário, configurar retenção, atender a fila de pedidos |
| Técnico(a) de segurança / RH da obra | ver e corrigir ASO dos trabalhadores daquela obra |
| Encarregado (DPO) da plataforma | atender pedidos sobre dados de conta; acompanhar prazos vencendo |
| Job de exportação (sistema) | gerar ZIP, publicar no S3, expirar |

## 3. Critérios de aceite

- [ ] **CA-01** "Seus dados" lista todas as categorias com finalidade, base legal e retenção; nenhuma categoria
  existente no banco fica fora (teste compara o inventário com o mapa de entidades pessoais).
- [ ] **CA-02** Correção de perfil grava e devolve `200`; correção de dado mantido pela empresa cria
  `PedidoTitular(tipo=CORRECAO)` para aquela empresa, visível na fila dela.
- [ ] **CA-03** Exportar exige modo sudo (sem sudo: `403 sudo_required`); o job gera ZIP cifrado com
  `export.json` válido contra o JSON Schema `cca-export/v1` e os arquivos do titular.
- [ ] **CA-04** A senha do ZIP aparece uma única vez; o servidor guarda só o hash; a URL pré-assinada vale
  15 min; o objeto expira em 24 h por lifecycle do bucket.
- [ ] **CA-05** Exportação, download e atendimento de pedido vão para o registro de operações (Art. 37) **sem**
  valores pessoais (só ids, tipo, data, resultado).
- [ ] **CA-06** Pedido de titular tem prazo de 15 dias; faltando 3 dias, o Encarregado e o admin da empresa
  recebem alerta; vencido sem resposta fica destacado como `ATRASADO`.
- [ ] **CA-07** ASO aparece para o próprio titular e para técnico de segurança/RH da obra; para os demais, o
  inventário mostra só "Dado de saúde · N registros"; nenhuma rota leva id de ASO na URL.
- [ ] **CA-08** "Compartilhamentos" mostra empresas, operadores e transferência internacional (Anthropic,
  EUA) com a base do Art. 33 indicada.
- [ ] **CA-09** Máximo de 1 exportação em andamento por titular e 3 por dia (`429` + `Retry-After`).

## 4. Regras de negócio

- **RN-01** O CivilConstructorAI é **operador** dos dados que a construtora guarda dos trabalhadores e
  **controlador** dos dados de conta; o pedido vai para quem é controlador do dado (Art. 18 §5º).
- **RN-02** Resposta a pedido sempre em formato simplificado imediato ou completo em até 15 dias (Art. 19).
- **RN-03** O `export.json` não inclui dados de **outras** pessoas (ex.: nomes de colegas em um RDO viram
  `pessoa:<pseudônimo>`).
- **RN-04** A empresa não pode configurar retenção abaixo do prazo legal das categorias trabalhistas e de
  segurança; os mínimos são os da [DN-03](../DECISOES_DE_NEGOCIO.md#dn-03--prazos-de-retenção-de-dados-de-trabalhadores):
  saúde ocupacional e treinamentos de segurança **20 anos** após o desligamento (NR-7, NR-1), documentos
  trabalhistas **5 anos** (CLT), fotos com trabalhadores identificáveis obra + 5 anos, contato operacional
  membro + 90 dias. Configurar abaixo → `422` com a base legal na mensagem.
- **RN-05** Exportação gerada com chave de dados do usuário (DEK) — se a conta for eliminada (RF26) antes do
  download, o arquivo fica ilegível e é apagado.

## 5. Base legal e normas

| Tema | Fundamento |
|---|---|
| Confirmação e acesso | LGPD Art. 18, I e II · Art. 19 |
| Correção | Art. 18, III |
| Anonimização, bloqueio, eliminação | Art. 18, IV e VI |
| Portabilidade / cópia | Art. 18, V |
| Informação sobre compartilhamento | Art. 18, VII |
| Prazo de resposta (15 dias) | Art. 19, II |
| Registro das operações | Art. 37 |
| Encarregado | Art. 41 |
| Dado sensível (saúde) | Art. 5º, II · Art. 11 |
| Transferência internacional | Art. 33 |
| Segurança | Art. 46 (cifra do ZIP, URL curta) |

## 6. API

| Método e rota | Descrição | Respostas |
|---|---|---|
| `GET /api/v1/me/dados` | Resumo por categoria (finalidade, base legal, retenção) | 200 |
| `PATCH /api/v1/me/perfil` | Correção direta de perfil | 200, 422 |
| `POST /api/v1/me/pedidos` | `{tipo, empresaId?, categoria, descricao}` | 201, 422 |
| `GET /api/v1/me/pedidos` | Meus pedidos e estado | 200 |
| `POST /api/v1/me/exportacoes` | Inicia exportação (modo sudo) → `{id, senhaZip}` (uma vez) | 202, 403 `sudo_required`, 429 |
| `GET /api/v1/me/exportacoes/{id}` | Estado do job; quando pronto, `urlDownload` (15 min) | 200, 404 |
| `GET /api/v1/me/compartilhamentos` | Empresas, operadores, transferências | 200 |
| `GET /api/v1/empresas/{empresaId}/privacidade/inventario` | Inventário por categoria (admin) | 200, 403, 404 |
| `PUT /api/v1/empresas/{empresaId}/privacidade/retencao/{categoria}` | Configurar retenção | 200, 422 |
| `GET /api/v1/empresas/{empresaId}/privacidade/pedidos` | Fila de pedidos | 200, 403, 404 |
| `POST /api/v1/empresas/{empresaId}/privacidade/pedidos/{id}/resposta` | `{decisao, resposta}` | 200, 409, 422 |

## 7. Telas

| Tela | Conteúdo | Base legal |
|---|---|---|
| Seus dados | categorias em acordeão; botão "Corrigir" por campo; "Baixar uma cópia" (pede senha + código: modo sudo); aba "Compartilhamentos"; aba "Meus pedidos" com prazo | Art. 18 |
| Exportação pronta | senha do ZIP exibida uma vez com botão copiar e aviso "não mostraremos de novo"; link de download com contador de validade | Art. 18, V · Art. 46 |
| Dados dos trabalhadores (empresa) | inventário por categoria com retenção editável, contagem de registros; fila de pedidos ordenada por prazo, com selo de estado (ícone + texto + cor) | Art. 37 · Art. 39 |
| Contato com o Encarregado | nome/canal do Encarregado, prazo de resposta, formulário | Art. 41 |

## 8. Diagramas

### 8.1 Casos de uso

```mermaid
flowchart LR
  tit(["👤 Titular"])
  adm(["👤 Admin da empresa"])
  seg(["👤 Técnico de segurança / RH"])
  dpo(["👤 Encarregado DPO"])
  job(["⚙️ Job de exportação"])
  subgraph RF25["RF25 · Direitos do titular"]
    uc1["Ver dados por categoria"]
    uc2["Corrigir dado"]
    uc3["Exportar cópia em ZIP cifrado"]
    uc4["Ver compartilhamentos"]
    uc5["Abrir pedido"]
    uc6["Atender fila de pedidos"]
    uc7["Ver inventário e configurar retenção"]
    uc8["Corrigir ASO da obra"]
    uc9["Confirmar modo sudo"]
    uc10["Registrar operação Art. 37"]
  end
  tit --> uc1 & uc2 & uc3 & uc4 & uc5
  adm --> uc6 & uc7
  seg --> uc8
  dpo --> uc6
  job --> uc3
  uc3 -. inclui .-> uc9
  uc3 -. inclui .-> uc10
  uc6 -. inclui .-> uc10
  uc2 -. "estende: dado da empresa" .-> uc5
```

### 8.2 Atividades

```mermaid
flowchart TD
  ini((início)) --> a1["Titular clica em Baixar uma cópia"]
  a1 --> d1{"Sessão em modo sudo?"}
  d1 -- não --> a2["Pedir senha + código TOTP (RF23)"] --> d1
  d1 -- sim --> d2{"Já há exportação em andamento<br/>ou limite diário atingido?"}
  d2 -- sim --> e1["429 com Retry-After"] --> fim((fim))
  d2 -- não --> a3["Criar Exportacao SOLICITADA<br/>gerar senha do ZIP e guardar só o hash"]
  a3 --> a4["Responder 202 + senha (exibida uma vez)"]
  a4 --> a5["Job coleta categorias do titular"]
  a5 --> a6["Pseudonimizar dados de terceiros"]
  a6 --> a7["Montar export.json cca-export/v1<br/>e validar no JSON Schema"]
  a7 --> d3{"Válido?"}
  d3 -- não --> e2["Exportacao FALHOU<br/>alerta para o Encarregado"] --> fim
  d3 -- sim --> a8["Empacotar ZIP AES-256 com os arquivos"]
  a8 --> a9["PUT no S3 em exports/ com lifecycle de 24 h"]
  a9 --> a10["Exportacao PRONTA + registro de operações"]
  a10 --> a11["Titular baixa por URL pré-assinada de 15 min"]
  a11 --> fim
```

### 8.3 Classes

```mermaid
classDiagram
  direction LR
  class CategoriaDado {
    +String codigo
    +String finalidade
    +String baseLegal
    +Duration retencaoMinimaLegal
    +boolean sensivel
  }
  class RetencaoEmpresa {
    +UUID empresaId
    +String categoria
    +Duration retencao
  }
  class PedidoTitular {
    +UUID id
    +UUID titularId
    +UUID empresaId
    +TipoPedido tipo
    +EstadoPedido estado
    +LocalDate prazo
    +String resposta
    +responder(Decisao, String)
  }
  class TipoPedido {
    <<enumeration>>
    CORRECAO
    ANONIMIZACAO
    ELIMINACAO_CONSENTIMENTO
    OPOSICAO
    REVISAO_DECISAO_AUTOMATIZADA
  }
  class Exportacao {
    +UUID id
    +UUID titularId
    +EstadoExportacao estado
    +String hashSenhaZip
    +String chaveS3
    +Instant expiraEm
  }
  class ExportadorTitular {
    +coletar(titularId) ExportJson
  }
  class ColetorCategoria {
    <<interface>>
    +categoria() String
    +coletar(titularId) Map
  }
  class Compartilhamento {
    +String destinatario
    +TipoDestinatario tipo
    +boolean transferenciaInternacional
    +String baseLegal
  }
  class RegistroOperacao {
    +UUID id
    +String acao
    +UUID atorId
    +UUID alvoId
    +Instant em
  }
  ExportadorTitular "1" o-- "1..*" ColetorCategoria
  ColetorCategoria ..> CategoriaDado
  RetencaoEmpresa ..> CategoriaDado : respeita mínimo
  PedidoTitular --> TipoPedido
  Exportacao ..> ExportadorTitular : job usa
  Exportacao ..> RegistroOperacao : registra
  PedidoTitular ..> RegistroOperacao : registra
```

### 8.4 Sequência

```mermaid
sequenceDiagram
  autonumber
  actor T as Titular
  participant W as Web (Seus dados)
  participant API as core-api
  participant SU as SudoGuard (RF23)
  participant J as ExportacaoJob
  participant DB as PostgreSQL + RLS
  participant S3 as S3 sa-east-1
  participant REG as Registro de operações (Cassandra)
  T->>W: Baixar uma cópia
  W->>API: POST /me/exportacoes
  API->>SU: exigirSudo(sessão)
  SU-->>API: ok (sudo válido por 10 min)
  API->>DB: INSERT exportacao (SOLICITADA, hash da senha)
  API-->>W: 202 {id, senhaZip}
  W-->>T: mostra a senha uma única vez
  J->>DB: SELECT exportacao SOLICITADA FOR UPDATE SKIP LOCKED
  J->>DB: coletar categorias do titular
  J->>J: pseudonimizar terceiros, validar cca-export/v1, ZIP AES-256
  J->>S3: PUT exports/{id}.zip
  J->>DB: UPDATE exportacao PRONTA
  J->>REG: EXPORTACAO_GERADA (ids, sem valores pessoais)
  W->>API: GET /me/exportacoes/{id}
  API->>S3: presign GET (15 min)
  API-->>W: 200 {estado: PRONTA, urlDownload}
  T->>S3: download do ZIP
```

### 8.5 Máquina de estados (PedidoTitular)

```mermaid
stateDiagram-v2
  [*] --> ABERTO: titular ou empresa registra
  ABERTO --> EM_ANALISE: responsável assume
  EM_ANALISE --> AGUARDANDO_TITULAR: falta informação
  AGUARDANDO_TITULAR --> EM_ANALISE: titular responde
  EM_ANALISE --> ATENDIDO: correção aplicada ou dado eliminado
  EM_ANALISE --> NEGADO: obrigação legal impede, com fundamento
  ABERTO --> ATRASADO: prazo de 15 dias vencido
  EM_ANALISE --> ATRASADO: prazo de 15 dias vencido
  ATRASADO --> ATENDIDO: resposta tardia
  ATRASADO --> NEGADO: resposta tardia
  ATENDIDO --> [*]
  NEGADO --> [*]
```

### 8.6 Componentes

```mermaid
flowchart LR
  subgraph WEB["apps/web (Next.js 16)"]
    SD["Seus dados"]
    EP["Exportação pronta"]
    DT["Dados dos trabalhadores (empresa)"]
    EN["Contato com o Encarregado"]
  end
  subgraph CORE["backend core-api (Spring Boot 4.1)"]
    TCt["TitularController"]
    ECt["PrivacidadeEmpresaController"]
    SG["SudoGuard"]
    EX["ExportadorTitular + coletores"]
    JB["ExportacaoJob (@Scheduled)"]
    PS["PedidoTitularService"]
    OA["ObraAccess (RF21)"]
    RG["RegistroOperacoes"]
    ARQ["ArquivoService (S3 presign)"]
  end
  DB[("PostgreSQL + RLS")]
  CS[("Cassandra<br/>registro de atividades")]
  S3[("S3 sa-east-1<br/>exports/ 24 h")]
  SD --> TCt
  EP --> TCt
  EN --> TCt
  DT --> ECt
  TCt --> SG
  TCt --> PS
  ECt --> OA
  ECt --> PS
  TCt --> JB
  JB --> EX --> DB
  JB --> ARQ --> S3
  PS --> DB
  PS --> RG
  JB --> RG
  RG --> CS
```

## 9. Testes previstos

| Tipo | Caso |
|---|---|
| Unitário (Java) | cada entidade com dado pessoal tem `ColetorCategoria` (reflexão sobre o mapa de entidades) |
| Unitário (Java) | `export.json` valida contra `cca-export/v1`; dados de terceiros pseudonimizados |
| Integração `*IT` | exportar sem sudo → `403 sudo_required`; com sudo → `202`, job → `PRONTA`, ZIP abre com a senha e não abre sem ela |
| Integração `*IT` | URL pré-assinada expira em 15 min; objeto com tag de lifecycle `exports-24h` no Garage |
| Integração `*IT` | usuário sem papel de segurança/RH na obra não vê conteúdo de ASO no inventário (só contagem) |
| Integração `*IT` | admin de outra empresa consulta fila de pedidos → `404`; RLS devolve zero linhas |
| Integração `*IT` | registro de operações da exportação não contém nome, CPF nem e-mail |
| E2E (Playwright + axe) | fluxo "Baixar uma cópia" com sudo, senha exibida uma vez, sem violação axe |

## 10. Rastreabilidade

| Camada | Artefato | Status |
|---|---|---|
| Documento | `docs/rf/RF25-direitos-do-titular.md` | ✅ |
| Card | [Trello BQtSoryZ](https://trello.com/c/BQtSoryZ) | ✅ |
| Web | `apps/web/src/app/(app)/conta/privacidade/seus-dados/page.tsx`, `apps/web/src/app/(app)/empresa/[empresaId]/privacidade/page.tsx` | ⏳ |
| API | `lgpd/titular/TitularController.java`, `lgpd/titular/PedidoTitularService.java` | ⏳ |
| API | `lgpd/exportacao/ExportadorTitular.java`, `ExportacaoJob.java`, `lgpd/inventario/InventarioService.java` | ⏳ |
| Schema | `backend/src/main/resources/schemas/cca-export-v1.json` | ⏳ |
| Banco | `V5__lgpd_titular.sql` (pedido_titular, exportacao, retencao_empresa, categoria_dado) | ⏳ |
| Testes | `ExportacaoIT`, `PedidoTitularIT`, `InventarioAsoIT`, `seus-dados.spec.ts` | ⏳ |
