---
rf: RF06
titulo: Auditoria de vãos reais corrigindo o Fencepost Error
modulo: M2 Análise Estrutural & Esforços
status: especificado
cards:
  - https://trello.com/c/OGjcAg5E
paths:
  - geometria/auditor/caixa_cartesiana.py
  - geometria/auditor/vaos.py
  - geometria/auditor/relatorio.py
  - backend/src/main/java/br/com/civilconstructorai/modelo/auditoria/**
---

# RF06 · Auditoria de vãos reais corrigindo o Fencepost Error

> **Caixa Cartesiana Sanitizada**: o auditor ignora o perímetro que a IA (ou o usuário) propôs e recalcula vãos
> e envoltória pelas extremidades reais (Xmin, Xmax, Ymin, Ymax) dos eixos e apoios, corrigindo o **erro do poste
> e cerca** (n vãos exigem n + 1 apoios) e a extrapolação de elementos além da estrutura.

## 1. Descrição

Nos testes experimentais das Diretrizes (seção 5), a geração não supervisionada extrapolou a estrutura em 4 m numa
malha 4x4: o LLM somou "4 vãos de 4 m = 16 m" a partir do primeiro eixo e lançou um quinto vão, ou contou
4 pilares para 4 vãos. O auditor corrige isso de forma determinística, no serviço Python
(`geometria/auditor/caixa_cartesiana.py`), sempre dentro do loop duplo (RF16):

1. **Caixa de referência**: calcula a bounding box real a partir das coordenadas dos eixos (RF03) e dos apoios
   existentes — nunca do perímetro informado no comando. `Xmin = min(eixos X)`, `Xmax = max(eixos X)`, idem Y.
2. **Recorte**: todo elemento proposto cuja geometria ultrapasse a caixa (+ tolerância de 0,05 m e marquises
   explicitamente declaradas como `balanco`) é **recortado** à caixa ou **descartado** se ficar inteiramente fora.
3. **Contagem de apoios**: para uma linha de vigas com n vãos, exige n + 1 apoios nas coordenadas dos eixos.
   Apoio faltante é inserido (pilar na interseção) se a regra do projeto permitir, senão vira violação; apoio
   duplicado (dois pilares a < 0,05 m) é fundido.
4. **Vãos reais**: recalcula o comprimento de cada viga e laje pela distância entre faces de apoio
   (vão livre) e entre eixos (vão teórico, NBR 6118 14.6.2.4), substituindo valores informados pela IA.
5. **Relatório**: cada correção é registrada (`antes`, `depois`, regra, elemento), vinculada ao `ComandoVI` e
   exibida ao engenheiro.

A auditoria é **idempotente**: aplicada a um resultado já auditado, não gera correções.

## 2. Atores

| Ator | Papel na obra | Pode |
|---|---|---|
| Auditor Python | sistema | executar a caixa cartesiana e corrigir |
| Engenheiro(a) responsável | `ENGENHEIRO` | ler o relatório, configurar se apoios faltantes são inseridos ou bloqueiam |
| Arquiteto(a) | `ARQUITETO` | ler o relatório dos seus comandos |

## 3. Critérios de aceite

- [ ] **CA-01** Nenhum elemento fica além da bounding box real da malha (exceto balanços declarados); caso de
  teste: comando da IA com extrapolação de 4 m em malha 4x4 é recortado à caixa.
- [ ] **CA-02** Em toda linha de vigas, número de apoios = número de vãos + 1, em cada direção.
- [ ] **CA-03** Vão teórico e vão livre recalculados a partir da geometria; valor de vão vindo da IA é ignorado.
- [ ] **CA-04** Relatório lista cada correção com antes/depois, regra (`FENCEPOST`, `FORA_DA_CAIXA`,
  `APOIO_DUPLICADO`, `APOIO_FALTANTE`) e fica vinculado ao comando VI.
- [ ] **CA-05** Idempotência: auditar duas vezes produz o mesmo modelo e relatório vazio na segunda.
- [ ] **CA-06** Apoio faltante com política `BLOQUEAR` gera violação `422`; com política `INSERIR` insere pilar
  padrão e registra a correção.
- [ ] **CA-07** Auditoria de malha 30x30 com 900 pilares em menos de 500 ms.

## 4. Regras de negócio

- **RN-01** A caixa é calculada por pavimento; pavimentos com projeção diferente (ex.: cobertura menor)
  usam os eixos ativos daquele pavimento.
- **RN-02** Balanço/marquise só passa da caixa se declarado (`balanco=true`) e até 3,0 m (marquises escalonadas de
  1,0 m a 2,0 m dos testes de estresse passam).
- **RN-03** Tolerância de coincidência = ShortCurveTolerance (0,05 m, Tabela A `geometricConstraints`).
- **RN-04** Política de apoio faltante por obra: `INSERIR` (padrão para comandos de IA) ou `BLOQUEAR`.
- **RN-05** O auditor não chama IA; as correções voltam ao LLM apenas como texto de erro no RF16.

## 5. Base legal e normas

- NBR 6118:2023, 14.6.2.4 (vão efetivo de vigas) e 14.7.2 (vãos de lajes).
- Responsabilidade técnica: correções automáticas são informadas ao engenheiro, que aprova o modelo.
- LGPD: sem dados pessoais.

## 6. API

| Método e rota | Descrição | Respostas |
|---|---|---|
| interno `POST geometria:/auditor/caixa-cartesiana` | Audita lote de elementos contra a malha | 200 `{elementos, correcoes, violacoes}`, 422 |
| `GET /api/v1/obras/{obraId}/modelo/comandos/{id}/auditoria` | Relatório de auditoria do comando | 200, 404 |
| `PUT /api/v1/obras/{obraId}/modelo/politicas/apoio-faltante` | `INSERIR` ou `BLOQUEAR` | 200, 403, 422 |

## 7. Diagramas

### 7.1 Casos de uso

```mermaid
flowchart LR
  eng(["👤 Engenheiro(a)"])
  arq(["👤 Arquiteto(a)"])
  aud(["⚙️ Auditor Python"])
  loop(["⚙️ Loop duplo RF16"])
  subgraph RF06["RF06 · Caixa Cartesiana Sanitizada"]
    uc1["Calcular caixa real dos eixos"]
    uc2["Recortar elementos fora da caixa"]
    uc3["Corrigir contagem de apoios"]
    uc4["Recalcular vãos reais"]
    uc5["Gerar relatório de correções"]
    uc6["Ler relatório do comando"]
    uc7["Definir política de apoio faltante"]
  end
  loop --> uc1
  uc1 -. inclui .-> uc2
  uc1 -. inclui .-> uc3
  uc1 -. inclui .-> uc4
  uc2 -. inclui .-> uc5
  uc3 -. inclui .-> uc5
  uc1 --- aud
  eng --> uc6 & uc7
  arq --> uc6
```

### 7.2 Atividades

```mermaid
flowchart TD
  ini((início)) --> a1["Receber elementos propostos e malha do pavimento"]
  a1 --> a2["Calcular Xmin, Xmax, Ymin, Ymax pelos eixos reais"]
  a2 --> a3["Para cada elemento"]
  a3 --> d1{"Dentro da caixa + 0,05 m<br/>ou balanço declarado?"}
  d1 -- sim --> a5
  d1 -- não --> d2{"Interseção com a caixa?"}
  d2 -- não --> c1["Descartar e registrar FORA_DA_CAIXA"] --> a8
  d2 -- sim --> c2["Recortar à caixa e registrar"] --> a5
  a5["Agrupar vigas por linha"] --> a6{"Apoios = vãos + 1?"}
  a6 -- sim --> a7
  a6 -- falta apoio --> d3{"Política INSERIR?"}
  d3 -- sim --> c3["Inserir pilar na interseção, registrar APOIO_FALTANTE"] --> a7
  d3 -- não --> v1["Violação APOIO_FALTANTE"] --> a7
  a6 -- apoio duplicado --> c4["Fundir apoios, registrar APOIO_DUPLICADO"] --> a7
  a7["Recalcular vão teórico e vão livre"] --> a8["Próximo elemento ou fim"]
  a8 --> d4{"Há violações?"}
  d4 -- sim --> r1["Devolver 422 com violações e correções"] --> fim((fim))
  d4 -- não --> r2["Devolver elementos corrigidos e relatório"] --> fim
```

### 7.3 Classes

```mermaid
classDiagram
  direction LR
  class CaixaCartesiana {
    +float xmin
    +float xmax
    +float ymin
    +float ymax
    +float tolerancia
    +de_eixos(malha) CaixaCartesiana
    +contem(geometria) bool
    +recortar(geometria) Geometria
  }
  class AuditorCaixaCartesiana {
    <<Python geometria.auditor>>
    +auditar(lote, malha, politica) ResultadoAuditoria
  }
  class VerificadorFencepost {
    +linhas_de_vigas(elementos) List~LinhaVigas~
    +verificar(linha) List~Correcao~
  }
  class LinhaVigas {
    +str eixo
    +List~Viga~ vigas
    +List~Apoio~ apoios
    +num_vaos() int
  }
  class CalculadoraVaos {
    +vao_teorico(viga) float
    +vao_livre(viga) float
  }
  class Correcao {
    +str elementoId
    +RegraAuditoria regra
    +dict antes
    +dict depois
  }
  class RegraAuditoria {
    <<enumeration>>
    FORA_DA_CAIXA
    FENCEPOST
    APOIO_FALTANTE
    APOIO_DUPLICADO
    VAO_RECALCULADO
  }
  class Violacao {
    +str elementoId
    +str codigo
    +str mensagemPtBr
  }
  class ResultadoAuditoria {
    +List~Elemento~ elementos
    +List~Correcao~ correcoes
    +List~Violacao~ violacoes
    +aprovado() bool
  }
  class PoliticaApoioFaltante {
    <<enumeration>>
    INSERIR
    BLOQUEAR
  }
  AuditorCaixaCartesiana --> CaixaCartesiana
  AuditorCaixaCartesiana --> VerificadorFencepost
  AuditorCaixaCartesiana --> CalculadoraVaos
  AuditorCaixaCartesiana --> PoliticaApoioFaltante
  VerificadorFencepost ..> LinhaVigas
  AuditorCaixaCartesiana ..> ResultadoAuditoria
  ResultadoAuditoria *-- Correcao
  ResultadoAuditoria *-- Violacao
  Correcao --> RegraAuditoria
```

### 7.4 Sequência

```mermaid
sequenceDiagram
  autonumber
  participant LD as LoopDuplo (core-api, RF16)
  participant API as geometria api
  participant CX as CaixaCartesiana
  participant FP as VerificadorFencepost
  participant VA as CalculadoraVaos
  participant DB as PostgreSQL
  LD->>API: POST /auditor/caixa-cartesiana {lote, malha, politica}
  API->>CX: de_eixos(malha)
  CX-->>API: caixa Xmin, Xmax, Ymin, Ymax
  loop cada elemento
    API->>CX: contem / recortar
    CX-->>API: elemento ajustado ou descartado
  end
  API->>FP: verificar linhas de vigas
  FP-->>API: correções e violações de apoio
  API->>VA: recalcular vãos
  VA-->>API: vãos teóricos e livres
  alt há violações
    API-->>LD: 422 {violacoes, correcoes}
  else aprovado
    API-->>LD: 200 {elementos, correcoes}
    LD->>DB: INSERT relatorio_auditoria vinculado ao comando
  end
```

### 7.5 Máquina de estados (ElementoProposto na auditoria)

```mermaid
stateDiagram-v2
  [*] --> PROPOSTO
  PROPOSTO --> DENTRO_DA_CAIXA: contido
  PROPOSTO --> RECORTADO: parcialmente fora
  PROPOSTO --> DESCARTADO: inteiramente fora
  DENTRO_DA_CAIXA --> APOIOS_VERIFICADOS
  RECORTADO --> APOIOS_VERIFICADOS
  APOIOS_VERIFICADOS --> VAOS_RECALCULADOS
  APOIOS_VERIFICADOS --> VIOLADO: apoio faltante com BLOQUEAR
  VAOS_RECALCULADOS --> AUDITADO
  AUDITADO --> [*]
  DESCARTADO --> [*]
  VIOLADO --> [*]
```

### 7.6 Componentes

```mermaid
flowchart LR
  subgraph CORE["backend core-api"]
    LD["LoopDuploValidacao (RF16)"]
    RA["RelatorioAuditoriaService"]
    PC["PoliticaController"]
  end
  subgraph GEO["geometria (Python)"]
    AP["api FastAPI /auditor"]
    CX["auditor/caixa_cartesiana.py"]
    VA["auditor/vaos.py"]
    RL["auditor/relatorio.py"]
    DM["data_model (Tabela A: tolerâncias)"]
  end
  subgraph WEB["apps/web"]
    PR["Painel de relatório de auditoria"]
  end
  DB[("PostgreSQL")]
  LD --> AP --> CX --> VA --> RL
  CX --> DM
  LD --> RA --> DB
  PC --> DB
  PR --> RA
```

## 8. Testes previstos

| Tipo | Caso |
|---|---|
| Unitário (Python) | malha 4x4 de 4 m com viga proposta até x = 20 m → recortada a x = 16 m, correção `FORA_DA_CAIXA` |
| Unitário (Python) | linha de 4 vãos com 4 pilares → insere o 5º pilar (`INSERIR`) ou violação (`BLOQUEAR`) |
| Unitário (Python) | dois pilares a 0,02 m → fundidos (`APOIO_DUPLICADO`) |
| Unitário (Python) | idempotência: `auditar(auditar(x)) == auditar(x)` com relatório vazio (teste por propriedade, Hypothesis) |
| Unitário (Python) | marquise escalonada de 1,5 m declarada como balanço passa sem recorte |
| Integração `*IT` | comando de IA com extrapolação gera relatório consultável em `/comandos/{id}/auditoria` |
| Desempenho | malha 30x30 com 900 pilares < 500 ms |

## 9. Rastreabilidade

| Camada | Artefato | Status |
|---|---|---|
| Documento | `docs/rf/RF06-auditoria-vaos-fencepost.md` | ✅ |
| Card | [Trello OGjcAg5E](https://trello.com/c/OGjcAg5E) | ✅ |
| Geometria | `geometria/auditor/caixa_cartesiana.py`, `vaos.py`, `relatorio.py` | ⏳ |
| API | `modelo/auditoria/RelatorioAuditoriaService.java` | ⏳ |
| Banco | `V7__modelo_bim.sql` (relatorio_auditoria, correcao_auditoria) | ⏳ |
| Web | painel de relatório em `apps/web/src/components/h/auditoria/` | ⏳ |
| Testes | `test_caixa_cartesiana.py`, `test_fencepost.py`, `AuditoriaComandoIT` | ⏳ |
