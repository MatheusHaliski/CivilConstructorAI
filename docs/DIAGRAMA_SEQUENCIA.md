# Diagramas de sequência (transversais)

Sequências obrigatórias e fluxos que cruzam vários RFs, em versão resumida. Cobre
[RF18](rf/RF18-cadastro-e-verificacao-email.md) (cadastro), [RF19](rf/RF19-login-2fa-sessao.md) (login com 2FA e
escolha da empresa), [RF20](rf/RF20-convites-empresa-obra.md) (convite), [RF26](rf/RF26-exclusao-conta-carencia.md)
(exclusão com carência), [RF02](rf/RF02-comandos-notacao-vi.md) + [RF16](rf/RF16-loop-duplo-validacao.md) +
[RF13](rf/RF13-renderizacao-cena-3d.md) (texto natural → cena) e [RF12](rf/RF12-associacao-sinapi.md) +
[RF21](rf/RF21-controle-acesso-papel-obra.md) + [RF27](rf/RF27-transparencia-ia-revisao-humana.md) (orçamento por IA →
aprovação). A versão completa de cada uma está na seção 7.4 do doc do RF.

Todas as escritas levam `X-XSRF-TOKEN`; a sessão é opaca no Valkey (cookie `__Host-cca_session`), nunca JWT.

## 1. Cadastro (RF18)

```mermaid
sequenceDiagram
  autonumber
  actor V as Visitante
  participant W as Web
  participant API as core-api
  participant DB as PostgreSQL
  participant VK as Valkey
  participant M as E-mail
  W->>API: GET /api/v1/auth/csrf
  API-->>W: cookie XSRF-TOKEN
  V->>W: preenche dados, 18+, aceita Termos e Política
  W->>API: POST /auth/register
  API->>API: valida tudo de uma vez e política de senha
  alt erros
    API-->>W: 422 errors[]
  else ok
    API->>DB: INSERT conta PENDING_VERIFICATION, aceite_termos com versão/data/IP, user_key
    API->>VK: cria sessão e código de verificação - 30 min, 5 tentativas
    API->>M: envia código
    API-->>W: 201 resposta neutra + Set-Cookie sessão
    V->>W: digita código
    W->>API: POST /me/verify-email
    API->>DB: conta ACTIVE
    API-->>W: 204
  end
```

## 2. Login com 2FA e escolha da empresa (RF19)

```mermaid
sequenceDiagram
  autonumber
  actor U as Usuário
  participant W as Web
  participant API as core-api
  participant RL as Limitador - Valkey
  participant VK as Spring Session - Valkey
  participant DB as PostgreSQL
  U->>W: e-mail + senha
  W->>API: POST /auth/login
  API->>RL: tentativas por conta e por IP
  alt excedeu
    API-->>W: 429 + Retry-After
  else dentro do limite
    API->>DB: busca conta e verifica Argon2id
    alt senha incorreta
      API-->>W: 401 E-mail ou senha incorretos
    else ok
      API->>VK: troca id de sessão, etapa = AGUARDANDO_2FA
      API-->>W: 200 proximaEtapa = 2fa
      U->>W: código TOTP ou de recuperação
      W->>API: POST /auth/login/2fa
      API->>VK: etapa = AUTENTICADA
      opt conta PENDING_DELETION
        API-->>W: oferta de reativação
        W->>API: POST /auth/reactivate
      end
      alt mais de uma empresa
        API-->>W: 200 proximaEtapa = escolher-empresa
        W->>API: POST /auth/session/empresa
      end
      API->>VK: empresaAtiva, papéis por obra
      W->>API: GET /auth/session
      API-->>W: empresa ativa, papéis, 2FA exigido
    end
  end
```

## 3. Convite para empresa e obra (RF20)

```mermaid
sequenceDiagram
  autonumber
  actor A as Dono / admin
  participant API as core-api
  participant DB as PostgreSQL
  participant M as E-mail
  actor C as Convidado
  A->>API: POST /companies/{id}/invites {email, obraId, papel}
  API->>API: papel ≤ papel de quem convida?
  alt papel acima
    API-->>A: 403 não pode promover acima do próprio papel
  else ok
    API->>DB: INSERT convite com hash do token, expira em 7 dias
    API->>DB: registro de operações
    API->>M: link com token
    API-->>A: 201
    C->>API: GET /invites/{token}
    API-->>C: válido, sem expor dados da empresa além do nome
    alt sem conta
      C->>API: POST /auth/register com token
    else com conta
      C->>API: POST /auth/login
    end
    C->>API: POST /invites/{token}/accept
    API->>DB: e-mail confere? convite PENDENTE? cria membro_obra, convite ACEITO
    API-->>C: 200
  end
```

## 4. Exclusão com carência (RF26)

```mermaid
sequenceDiagram
  autonumber
  actor U as Titular
  participant W as Web
  participant API as core-api
  participant DB as PostgreSQL
  participant KC as Cofre de chaves (KEK)
  participant VK as Valkey
  participant J as Job 03:15
  participant S3 as S3
  participant CA as Cassandra
  U->>W: Excluir conta - marca Entendo, senha e código
  W->>API: POST /me/deletion - modo sudo
  API->>DB: conta PENDING_DELETION, exclusaoAgendadaEm = hoje + 30 dias
  API->>VK: encerra todas as sessões
  API-->>W: 202 com data final
  Note over U,API: dentro de 30 dias o login oferece POST /auth/reactivate
  J->>DB: contas com prazo vencido
  loop cada conta - idempotente
    J->>DB: anonimiza pessoa e mantém registros com obrigação legal
    J->>DB: commit
    J->>S3: remove objetos do usuário
    J->>CA: remove atividades e notificações
    J->>VK: remove chaves residuais
    J->>DB: anonimiza e marca ANONYMOUS_LGPD_DELETED
    J->>KC: apaga KEK do cofre de chaves e grava tombstone (crypto-shredding)
    J->>DB: apaga user_key
    J->>J: recibo sem dado pessoal no log
  end
```

## 5. Texto natural → Claude → auditor → kernel → cena (RF02, RF16, RF13)

```mermaid
sequenceDiagram
  autonumber
  actor U as Arquiteto(a)
  participant W as Web - console VI + H9_scene3d
  participant API as core-api
  participant IA as Módulo ia - SDK anthropic-java
  participant CL as Claude API
  participant GEO as geometria - auditor
  participant K as kernel CadQuery/OCC
  participant DB as PostgreSQL
  U->>W: "pórtico 5x3 com vãos de 4 m e 4 pavimentos"
  W->>API: POST /obras/{id}/modelo/prompt
  API->>IA: rota notacao-vi, effort high, só contexto do modelo
  IA->>CL: messages.stream com output_config.format JSON Schema, system em cache
  CL-->>IA: mensagem final + stop_reason
  alt refusal
    IA-->>API: recusa
    API-->>W: 422 com motivo
  else end_turn
    IA->>IA: valida JSON no backend
    API->>GEO: POST /auditar comandos
    GEO->>GEO: laço 1 - código e Tabela C
    GEO->>GEO: RF06 caixa cartesiana, RF08 tolerâncias, RF17 disciplina Z
    GEO->>K: laço 2 - gerar sólidos
    K-->>GEO: BRep válido ou falha
    alt falha e tentativas < 3
      GEO-->>API: erro estruturado
      API->>IA: nova iteração com o erro
    else aprovado
      GEO-->>API: comandos corrigidos + relatório
      API->>DB: grava RascunhoIA e ChamadaModelo
      API-->>W: prévia marcada gerado por IA
      U->>W: confirmar
      W->>API: POST /modelo/comandos {rascunhoId}
      API->>DB: aplica, versão+1
      API-->>W: DiffModelo
      W->>W: atualiza cena incrementalmente
    end
  end
```

## 6. Rascunho de orçamento por IA → aprovação (RF12, RF21, RF27)

```mermaid
sequenceDiagram
  autonumber
  actor O as Orçamentista
  actor E as Engenheiro(a)
  participant API as core-api
  participant IA as Módulo ia
  participant CL as Claude API
  participant DB as PostgreSQL
  O->>API: POST /obras/{id}/orcamentos {uf, dataBase}
  API->>DB: quantitativos da versão atual - RF11
  API->>IA: itens sem vínculo SINAPI
  IA->>CL: tool use consultar_sinapi strict, tool_choice auto, structured outputs
  CL->>IA: tool_use consultar_sinapi
  IA->>DB: busca composições vigentes
  IA->>CL: tool_result
  CL-->>IA: vínculos sugeridos
  IA->>API: valida código existe e unidade compatível
  API->>DB: Orcamento GERADO_POR_IA, itens geradoPorIA = true
  API-->>O: 201 rascunho
  E->>API: POST /orcamentos/{id}/aprovar
  API->>API: papel ENGENHEIRO na obra?
  API->>API: selo VERIFIED, 2FA ativo, modo sudo, ART da obra?
  alt falta requisito
    API-->>E: 403 com motivo em pt-BR
  else ok
    API->>DB: Orcamento APROVADO, aprovadoPor
    API->>DB: registro de operações - sem valores pessoais
    API-->>E: 200
  end
```
