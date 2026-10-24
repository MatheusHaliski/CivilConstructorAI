---
rf: RF19
titulo: Login em etapas com 2FA (TOTP), sessão Valkey e escolha da empresa ativa
modulo: M0 Identidade & Acesso
status: especificado
cards:
  - https://trello.com/c/IEf3vByh
paths:
  - backend/src/main/java/br/com/civilconstructorai/identidade/login/**
  - backend/src/main/java/br/com/civilconstructorai/identidade/sessao/**
  - backend/src/main/java/br/com/civilconstructorai/acesso/SecurityConfig.java
  - backend/src/main/resources/application*.yml
  - apps/web/src/app/(auth)/entrar/**
---

# RF19 · Login em etapas com 2FA (TOTP), sessão Valkey e escolha da empresa ativa

> O login é uma pequena máquina de estados guardada **na sessão do servidor**: senha → 2FA (quando exigido) →
> oferta de reativação (se a conta estiver em exclusão) → escolha da empresa ativa. Nenhum token vai para o
> cliente além do cookie de sessão opaco.

## 1. Descrição

**Sessão.** Spring Session Data Redis sobre **Valkey 9**, repositório `indexed` (permite listar e encerrar as
sessões de um usuário, RF23). Cookie `__Host-cca_session`: `HttpOnly`, `Secure`, `SameSite=Lax`, `Path=/`,
**sem `Max-Age`** (cookie de sessão do navegador). Expiração **ociosa de 60 min** (deslizante) e **absoluta de
24 h** (filtro `SessaoAbsolutaFilter` compara `criadaEm` gravado na sessão). **Nunca JWT, nunca token em
`localStorage`.** O app mobile usa o mesmo cookie, guardado no armazenamento seguro do SO.

**CSRF.** Spring Security 7 `csrf.spa()`: cookie `XSRF-TOKEN` legível pelo JS + cabeçalho `X-XSRF-TOKEN` em
toda escrita. Cabeçalhos: CSP `default-src 'none'; frame-ancestors 'none'` na API, `Referrer-Policy: no-referrer`.

**Etapas.**

1. `POST /api/v1/auth/login {email, senha}`. Falha → `401` com a mensagem **neutra** "E-mail ou senha
   incorretos" (mesmo tempo de resposta para e-mail inexistente: hash Argon2id de referência). Sucesso troca o
   id da sessão e grava `etapaLogin` na sessão.
2. Se a conta tem 2FA ativo **ou** o 2FA é obrigatório (dono/admin de empresa, quem aprova orçamento ou
   medição, suporte da plataforma) → `etapa = MFA`. `POST /login/2fa {codigo}` aceita TOTP (RFC 6238, passo
   30 s, janela ±1) ou um dos 10 códigos de recuperação (uso único). Se o 2FA é obrigatório e ainda não foi
   configurado → `etapa = CONFIGURAR_MFA` (só rotas `/me/mfa/**` liberadas).
3. Conta `PENDING_DELETION` → `etapa = OFERTA_REATIVACAO`; `POST /reactivate` volta a `ACTIVE` (RF26); recusar
   encerra a sessão.
4. Pessoa com mais de uma empresa → `etapa = ESCOLHER_EMPRESA`; `POST /login/empresa {empresaId}`. Com uma só,
   a empresa é escolhida automaticamente. A empresa ativa define o `tenant_id` usado pela RLS (RF21).
5. `etapa = AUTENTICADO`. `GET /session` devolve usuário, empresa ativa, papéis por obra, estado do selo e se o
   2FA é exigido. `POST /logout` invalida a sessão e remove do índice.

**Limite de tentativas.** Contadores no Valkey por **conta** (5 falhas/15 min) e por **IP** (30/15 min) →
`429` + `Retry-After`. Mensagem continua neutra. Conta `SUSPENDED` recebe a mesma mensagem neutra no passo 1 e
um e-mail explicando a suspensão.

## 2. Atores

| Ator | Pode |
|---|---|
| Usuário cadastrado | entrar, informar 2FA, reativar conta, escolher empresa, sair |
| Dono/admin, aprovador de orçamento/medição, suporte | idem, com 2FA **obrigatório** |
| Valkey | guardar sessão, índice por usuário e contadores de tentativas |

## 3. Critérios de aceite

- [ ] **CA-01** Credencial errada ou e-mail inexistente → `401` "E-mail ou senha incorretos", tempo de resposta
  equivalente (diferença < 50 ms na mediana).
- [ ] **CA-02** 6ª falha por conta em 15 min ou 31ª por IP → `429` + `Retry-After` (segundos).
- [ ] **CA-03** O id da sessão muda depois da senha e de novo ao concluir o login (fixação de sessão).
- [ ] **CA-04** Etapa intermediária (`MFA`, `CONFIGURAR_MFA`, `OFERTA_REATIVACAO`, `ESCOLHER_EMPRESA`) fica só na
  sessão do servidor; qualquer rota protegida nessa etapa → `401` com `type=/problemas/login-incompleto`.
- [ ] **CA-05** TOTP válido na janela ±1 passo; código de recuperação consumido não serve de novo; código TOTP
  já usado no mesmo passo é rejeitado (replay).
- [ ] **CA-06** 2FA obrigatório para `DONO`, `ADMIN`, aprovadores e `SUPORTE_PLATAFORMA`; sem 2FA configurado,
  só `/me/mfa/**` responde.
- [ ] **CA-07** Cookie `__Host-cca_session` com `HttpOnly; Secure; SameSite=Lax; Path=/` e sem `Max-Age`/`Expires`.
- [ ] **CA-08** Sessão expira após 60 min sem uso e após 24 h desde o login, mesmo em uso.
- [ ] **CA-09** Toda escrita sem `X-XSRF-TOKEN` válido → `403`.
- [ ] **CA-10** `GET /session` traz empresa ativa, papéis por obra, estado do selo, `mfaExigido`, `mfaAtivo`.
- [ ] **CA-11** Login bem-sucedido, falhas em sequência e novo aparelho geram evento no registro de operações e
  alerta de login por e-mail (RF23).
- [ ] **CA-12** Nenhum JWT emitido; nenhuma resposta contém o id real da sessão no corpo.

## 4. Regras de negócio

- **RN-01** A empresa ativa só pode ser trocada por `POST /session/empresa` (gera novo id de sessão).
- **RN-02** Escolher empresa em que a pessoa não é membro → `404`.
- **RN-03** Usuário `PENDING_VERIFICATION` entra, mas cai na tela de verificação (RF18).
- **RN-04** Falha de 2FA conta no limite por conta, com o mesmo contador da senha.
- **RN-05** Logout de uma sessão não derruba as outras; troca de senha derruba (RF23).

## 5. Base legal e normas

- LGPD Art. 46 (medidas de segurança) e Art. 49 (sistemas estruturados para segurança).
- RFC 6238 (TOTP), RFC 4226 (HOTP), RFC 6265bis (prefixo `__Host-`, `SameSite`), RFC 9457 (erros).
- OWASP ASVS 5.0 V6 (autenticação), V7 (gestão de sessão), V3 (cookies), V4 (CSRF).
- OWASP Session Management Cheat Sheet (timeout ocioso e absoluto, troca de id).

## 6. API

| Método e rota | Descrição | Respostas |
|---|---|---|
| `GET /api/v1/auth/csrf` | Token CSRF | 204 |
| `POST /api/v1/auth/login` | Etapa senha | 200 `{proximaEtapa}`, 401, 429 |
| `POST /api/v1/auth/login/2fa` | TOTP ou código de recuperação | 200 `{proximaEtapa}`, 401, 429 |
| `POST /api/v1/auth/reactivate` | Reativa conta em exclusão | 200, 409 |
| `POST /api/v1/auth/login/empresa` | Escolhe empresa ativa | 200, 404 |
| `POST /api/v1/session/empresa` | Troca a empresa ativa depois do login | 200, 404 |
| `GET /api/v1/auth/session` | Estado da sessão | 200, 401 |
| `POST /api/v1/auth/logout` | Encerra a sessão | 204 |

## 7. Diagramas

### 7.1 Casos de uso

```mermaid
flowchart LR
  usr(["👤 Usuário"])
  adm(["👤 Dono/admin ou aprovador"])
  vk(["⚙️ Valkey"])
  subgraph RF19["RF19 · Login e sessão"]
    uc1["Entrar com e-mail e senha"]
    uc2["Informar código 2FA"]
    uc3["Usar código de recuperação"]
    uc4["Reativar conta em exclusão"]
    uc5["Escolher empresa ativa"]
    uc6["Consultar sessão"]
    uc7["Sair"]
    uc8["Limitar tentativas"]
  end
  usr --> uc1 & uc5 & uc6 & uc7 & uc4
  adm --> uc1
  adm --> uc2
  uc2 -. estende .-> uc3
  uc1 -. inclui .-> uc8
  uc2 -. inclui .-> uc8
  uc8 --- vk
```

### 7.2 Atividades

```mermaid
flowchart TD
  ini((início)) --> a1["POST /login e-mail e senha"]
  a1 --> d0{"Limite conta ou IP excedido?"}
  d0 -- sim --> e429["429 + Retry-After"] --> fim((fim))
  d0 -- não --> d1{"Senha confere e conta não suspensa?"}
  d1 -- não --> e401["401 E-mail ou senha incorretos"] --> fim
  d1 -- sim --> a2["Trocar id da sessão"]
  a2 --> d2{"2FA ativo ou obrigatório?"}
  d2 -- "obrigatório e não configurado" --> a3["etapa CONFIGURAR_MFA"] --> a3b["Ativar TOTP (RF23)"] --> d3
  d2 -- sim --> a4["etapa MFA: POST /login/2fa"]
  a4 --> d2b{"TOTP ou recuperação válido?"}
  d2b -- não --> e401
  d2b -- sim --> d3{"Conta PENDING_DELETION?"}
  d2 -- não --> d3
  d3 -- sim --> a5["Oferta de reativação"]
  a5 --> d4{"Reativar?"}
  d4 -- não --> a9["Encerrar sessão"] --> fim
  d4 -- sim --> a6["Conta ACTIVE (RF26)"] --> d5
  d3 -- não --> d5{"Mais de uma empresa?"}
  d5 -- sim --> a7["Escolher empresa ativa"] --> a8
  d5 -- não --> a8["Empresa única definida; trocar id; AUTENTICADO"]
  a8 --> fim
```

### 7.3 Classes

```mermaid
classDiagram
  direction LR
  class LoginService {
    +autenticarSenha(email, senha, sessao) ProximaEtapa
    +verificarSegundoFator(codigo, sessao) ProximaEtapa
    +reativar(sessao)
    +escolherEmpresa(empresaId, sessao)
  }
  class EstadoLogin {
    +UUID contaId
    +EtapaLogin etapa
    +Instant iniciadoEm
    +UUID empresaAtivaId
  }
  class EtapaLogin {
    <<enumeration>>
    SENHA_OK
    CONFIGURAR_MFA
    MFA
    OFERTA_REATIVACAO
    ESCOLHER_EMPRESA
    AUTENTICADO
  }
  class FatorTotp {
    +byte[] segredoCifrado
    +long ultimoPassoUsado
    +verificar(String) boolean
  }
  class CodigoRecuperacao {
    +String hash
    +Instant usadoEm
  }
  class LimiteTentativas {
    +consumir(String chave) Decisao
    +zerar(String chave)
  }
  class SessaoAbsolutaFilter {
    +Duration absoluto
    +doFilter()
  }
  class PoliticaMfa {
    +exigido(Conta) boolean
  }
  class SessaoDto {
    +UUID usuarioId
    +UUID empresaAtivaId
    +Map papeisPorObra
    +EstadoSelo selo
    +boolean mfaExigido
  }
  LoginService --> EstadoLogin
  EstadoLogin --> EtapaLogin
  LoginService --> FatorTotp
  LoginService --> CodigoRecuperacao
  LoginService --> LimiteTentativas
  LoginService --> PoliticaMfa
  LoginService ..> SessaoDto : monta
```

### 7.4 Sequência (login com 2FA e escolha da empresa)

```mermaid
sequenceDiagram
  autonumber
  actor U as Usuário
  participant W as Web
  participant SEC as SecurityFilterChain
  participant LS as LoginService
  participant RL as LimiteTentativas (Valkey)
  participant DB as PostgreSQL
  participant SS as Spring Session (Valkey)
  U->>W: e-mail e senha
  W->>SEC: POST /auth/login + X-XSRF-TOKEN
  SEC->>LS: autenticarSenha
  LS->>RL: consumir(login:conta, login:ip)
  RL-->>LS: permitido
  LS->>DB: SELECT conta e hash
  LS->>LS: Argon2id.matches
  alt senha errada
    LS-->>W: 401 E-mail ou senha incorretos
  else senha ok
    LS->>SS: changeSessionId + etapa MFA
    LS-->>W: 200 proximaEtapa MFA
    U->>W: código do app autenticador
    W->>SEC: POST /auth/login/2fa
    SEC->>LS: verificarSegundoFator
    LS->>DB: segredo TOTP cifrado
    LS->>LS: AES-256-GCM decifra e RFC 6238 confere
    LS->>SS: etapa ESCOLHER_EMPRESA
    LS-->>W: 200 proximaEtapa ESCOLHER_EMPRESA + lista de empresas
    U->>W: escolhe empresa
    W->>SEC: POST /auth/login/empresa
    SEC->>LS: escolherEmpresa
    LS->>DB: confere vínculo com a empresa
    LS->>SS: changeSessionId + empresaAtiva + AUTENTICADO
    LS-->>W: 200 SessaoDto
  end
```

### 7.5 Máquina de estados (EstadoLogin)

```mermaid
stateDiagram-v2
  [*] --> ANONIMO
  ANONIMO --> SENHA_OK: senha correta
  ANONIMO --> ANONIMO: falha ou 429
  SENHA_OK --> CONFIGURAR_MFA: 2FA obrigatório sem configurar
  SENHA_OK --> MFA: 2FA ativo
  SENHA_OK --> OFERTA_REATIVACAO: conta em exclusão
  SENHA_OK --> ESCOLHER_EMPRESA: várias empresas
  SENHA_OK --> AUTENTICADO: uma empresa
  CONFIGURAR_MFA --> MFA: TOTP ativado
  MFA --> OFERTA_REATIVACAO: código ok e conta em exclusão
  MFA --> ESCOLHER_EMPRESA: código ok e várias empresas
  MFA --> AUTENTICADO: código ok e uma empresa
  OFERTA_REATIVACAO --> ESCOLHER_EMPRESA: reativou
  OFERTA_REATIVACAO --> ANONIMO: recusou
  ESCOLHER_EMPRESA --> AUTENTICADO: empresa escolhida
  AUTENTICADO --> ANONIMO: logout ou expiração 60 min / 24 h
```

### 7.6 Componentes

```mermaid
flowchart LR
  subgraph WEB["apps/web e apps/mobile"]
    LG["Telas de login em etapas"]
    CL["packages/api"]
  end
  subgraph CORE["backend core-api"]
    SEC["SecurityFilterChain: csrf.spa, CSP, Referrer-Policy"]
    ABS["SessaoAbsolutaFilter 24 h"]
    AC["AuthController"]
    LS["LoginService"]
    MFA["FatorTotp + CodigoRecuperacao"]
    CRY["CifraAesGcm (subchave totp)"]
    LT["LimiteTentativas"]
    RO["RegistroOperacoes"]
  end
  VK[("Valkey 9: sessões indexed + contadores")]
  DB[("PostgreSQL 17")]
  LG --> CL -- "cookie __Host-cca_session + X-XSRF-TOKEN" --> SEC
  SEC --> ABS --> AC --> LS
  LS --> MFA --> CRY
  LS --> LT --> VK
  SEC -- "Spring Session" --> VK
  LS --> DB
  LS --> RO --> DB
```

## 8. Testes previstos

| Tipo | Caso |
|---|---|
| Unitário | `FatorTotp` aceita passo atual e ±1, rejeita ±2 e replay no mesmo passo |
| Unitário | `PoliticaMfa` exige 2FA para DONO, ADMIN e aprovadores |
| Integração `*IT` | login real com `ApiClient` (CSRF + cookie) → `/session` 200 com empresa ativa |
| Integração `*IT` | id da sessão diferente antes e depois do login |
| Integração `*IT` | 6 senhas erradas → `429` com `Retry-After`; e-mail inexistente → mesma mensagem 401 |
| Integração `*IT` | chamar `/obras` na etapa MFA → `401` login-incompleto |
| Integração `*IT` | sessão com `criadaEm` há 24 h + 1 min → `401` |
| Integração `*IT` | POST sem `X-XSRF-TOKEN` → `403` |
| E2E | login com TOTP gerado no teste e escolha entre 2 empresas |

## 9. Rastreabilidade

| Camada | Artefato | Status |
|---|---|---|
| Documento | `docs/rf/RF19-login-2fa-sessao.md` | ✅ |
| Card | [Trello IEf3vByh](https://trello.com/c/IEf3vByh) | ✅ |
| Web | `apps/web/src/app/(auth)/entrar/` (etapas senha, 2fa, reativar, empresa) | ⏳ |
| API | `identidade/login/LoginService.java`, `acesso/SecurityConfig.java`, `identidade/sessao/SessaoAbsolutaFilter.java` | ⏳ |
| Banco | `V1__contas_empresas_obras.sql` (fator_totp, codigo_recuperacao) | ⏳ |
| Infra | `compose.yaml` (Valkey 9), `application.yml` (timeout 60 min) | ⏳ |
| Testes | `LoginIT`, `SessaoIT`, `CsrfIT`, `FatorTotpTest` | ⏳ |
