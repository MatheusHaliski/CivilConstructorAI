# Taxonomia de atores, papéis e cards

Atores, papéis por obra, atributos e os tipos de card do quadro do Trello e da interface do app. Base para o
controle de acesso de [RF21](rf/RF21-controle-acesso-papel-obra.md), os convites de
[RF20](rf/RF20-convites-empresa-obra.md), a gestão de membros de [RF22](rf/RF22-empresas-e-obras.md), os
rascunhos de IA de [RF27](rf/RF27-transparencia-ia-revisao-humana.md) e as telas de
[RF01](rf/RF01-modelagem-vetorial-canvas-webgl.md) e [RF13](rf/RF13-renderizacao-cena-3d.md). Os atores de
cada RF estão na seção 2 do seu doc.

## 1. Modelo multiempresa

**Empresa (tenant) → Obras → Membros da obra.** Uma pessoa (Conta) pode estar em várias empresas e ter um papel
diferente em cada obra. A empresa ativa é escolhida no login ([RF19](rf/RF19-login-2fa-sessao.md)) e fica na
sessão; o `tenant_id` é definido na conexão a cada transação (RLS).

## 2. Atores

| Ator | Escopo | Verificação | Pode |
|---|---|---|---|
| Visitante | — | — | ver a página pública e aceitar convite |
| Dono / administrador da empresa | empresa | e-mail + **CNPJ** conferido, 2FA obrigatório | criar obras, convidar, definir papéis, ver cobrança |
| Engenheiro(a) responsável | obra | **CREA** + ART da obra | aprovar orçamento, medição, cálculo e checklist; assinar o que a IA rascunhou |
| Arquiteto(a) | obra | **CAU** + RRT | projetos, revisões, aprovar o que é da sua responsabilidade |
| Orçamentista | obra | e-mail | preparar quantitativos e orçamento; **não aprova** |
| Mestre de obras / encarregado | obra | e-mail | RDO, fotos, presença, checklist do dia |
| Técnico(a) de segurança | obra | registro profissional | checklists NR-18; vê ASO só dos trabalhadores daquela obra |
| Cliente / proprietário | obra | e-mail | acompanhar andamento, fotos e medições liberadas; aprovar aditivos |
| Fornecedor | obra (convite) | CNPJ | ver pedidos e entregas dele |
| Trabalhador da obra | obra | — (cadastrado pela empresa, pode ou não ter login) | ver os próprios dados e pedir correção |
| Suporte / Admin da plataforma | plataforma | interno, 2FA obrigatório | só com acesso temporário concedido pela empresa, sempre registrado |
| Encarregado (DPO) | plataforma | interno | atender pedidos do titular, registrar incidentes |
| Sistemas | — | credencial de serviço | Serviço de IA (Claude), Auditor Python, Kernel BIM, Job agendado |

## 3. Papéis

### 3.1 Papéis na empresa e na obra (`PapelObra`)

| Papel | Escopo | Exige selo para aprovar | 2FA obrigatório | Pode convidar até |
|---|---|---|---|---|
| `DONO` | empresa (todas as obras) | CNPJ | sim | qualquer papel |
| `ADMIN` | empresa (todas as obras) | — | sim | qualquer papel exceto `DONO` |
| `ENGENHEIRO` | obra | CREA `VERIFIED` | sim (aprova) | `ARQUITETO` e abaixo na obra |
| `ARQUITETO` | obra | CAU `VERIFIED` | sim (aprova) | `ORCAMENTISTA` e abaixo na obra |
| `ORCAMENTISTA` | obra | — | não | — |
| `MESTRE_OBRAS` | obra | — | não | `TRABALHADOR` |
| `TECNICO_SEGURANCA` | obra | registro profissional | não | — |
| `CLIENTE` | obra | — | sim (aprova aditivo) | — |
| `FORNECEDOR` | obra | CNPJ | não | — |
| `TRABALHADOR` | obra | — | não | — |

Regra: um convite **nunca** promove alguém acima do papel de quem convidou.

### 3.2 Papéis na plataforma

| Papel | Pode | Restrições |
|---|---|---|
| `SUPORTE_PLATAFORMA` | verificar selos; suporte técnico | dados da empresa só com acesso temporário consentido, prazo de expiração e registro |
| `DPO` | atender pedidos de titulares, registrar incidentes, responder revisão humana de decisão automatizada | não edita modelo nem orçamento |

## 4. Atributos

| Entidade | Atributos | Dado pessoal? |
|---|---|---|
| Conta | id (UUID v7), e-mail, nome, nascimento, tipo (pessoa/empresa), estado, senha (Argon2id), 2FA | sim |
| Selo profissional | tipo (CREA/CAU/CNPJ), número, UF, estado (`NONE`/`PENDING`/`VERIFIED`/`REJECTED`) | sim |
| Membro da obra | conta, obra, papel, desde | sim (vínculo) |
| Trabalhador | nome, CPF, contato, fotos, presença; **ASO e afastamentos = dado de saúde sensível** | sim (sensível) |
| Obra | nome, endereço, perímetro (PostGIS), ART/RRT, estado | não |
| Modelo BIM | versão, pavimentos, elementos, autor dos comandos (id) | só o id do autor |

Dado de saúde nunca aparece em logs, URLs, analytics, notificações push nem no contexto enviado à IA.

## 5. Cards do Trello

Quadro: [CivilConstructorAI · CAD/BIM Generativo](https://trello.com/b/Ep08cZde/civilconstructorai-cad-bim-generativo),
espelhado em [TRELLO_BACKLOG.md](TRELLO_BACKLOG.md).

### 5.1 Tipos

| Tipo | Prefixo do título | Lista | Exemplo |
|---|---|---|---|
| Requisito funcional | `RFnn · ` | lista do módulo (M0…M8) → 🔨 Em andamento → 👀 Revisão → ✅ Concluído | `RF01 · Modelagem vetorial 2D/3D…` |
| Documentação transversal | `DOC · ` | 📌 Visão, ADRs & Docs transversais | `DOC · Diagramas transversais` |
| Decisão pendente | `❓ ` | 📌 Visão, ADRs & Docs transversais | `❓ Decisões de negócio pendentes` |

### 5.2 Anatomia de um card RF

```text
┌─────────────────────────────────────────────────────────────┐
│ ① RF01 · Modelagem vetorial 2D/3D interativa no canvas WebGL │
├─────────────────────────────────────────────────────────────┤
│ ② Módulo: M1 Concepção Arquitetônica                         │
│ ③ Objetivo: o que o RF entrega, em uma frase ou duas         │
│ ④ Atores: quem usa                                           │
│ ⑤ Doc: link para docs/rf/RFnn-slug.md no GitHub              │
│ ⑥ Critérios de aceite: lista curta (o doc tem a completa)    │
│ ⑦ Diagramas: casos de uso · atividades · classes ·           │
│              sequência · estados · componentes               │
├─────────────────────────────────────────────────────────────┤
│ ⑧ Checklist "Documentação (docs/rf)"                         │
│     ☐ doc RF · ☐ casos de uso · ☐ atividades · ☐ classes      │
│     ☐ sequência · ☐ estados · ☐ componentes                  │
│ ⑨ Checklist "Entrega"                                        │
│     ☐ migração Flyway · ☐ API + testes *IT · ☐ web/mobile     │
│     ☐ rastreabilidade atualizada no doc                      │
└─────────────────────────────────────────────────────────────┘
```

1. **Título**: `RFnn · ` + título igual ao do catálogo.
2. **Módulo**: M0–M8, igual à lista de origem.
3. **Objetivo**: resumo do comportamento.
4. **Atores**: atores da seção 2.
5. **Doc**: link para o documento; o cabeçalho do doc aponta de volta para o card (`cards:`).
6. **Critérios de aceite**: resumo dos `CA-nn` do doc.
7. **Diagramas**: os seis diagramas obrigatórios.
8. **Checklist "Documentação (docs/rf)"**: marcada quando o doc e cada diagrama estão no repositório e renderizam no CI.
9. **Checklist "Entrega"**: marcada conforme o código é entregue; o card vai para ✅ quando tudo está marcado e o status do doc é `implementado`.

## 6. Cards da interface do app

Todo estado é comunicado por **cor + ícone Lucide + texto** (WCAG 1.4.1); alvo de toque ≥ 48 px.

### 6.1 Card de obra

```text
┌────────────────────────────────────────────┐
│ ① Residencial Jardim · ② [▶ Em andamento]   │
│ ③ Rua Exemplo, 100 · Curitiba/PR            │
│ ④ Seu papel: Engenheiro(a) · ⑤ ART 123456   │
│ ⑥ Modelo v42 · atualizado há 2 h            │
│ ⑦ [Abrir modelo]  [Orçamento]  [⋯]          │
└────────────────────────────────────────────┘
```

1. Nome da obra. 2. Estado: `PLANEJADA` (ícone `calendar`), `EM_ANDAMENTO` (`hammer`), `PARALISADA` (`pause-circle`),
`CONCLUIDA` (`check-circle`), `ARQUIVADA` (`archive`), com tokens `--obra-*`. 3. Endereço. 4. Papel da pessoa nesta
obra. 5. ART/RRT. 6. Versão do modelo e última atualização. 7. Ações permitidas pelo papel (as demais não aparecem).

### 6.2 Card de elemento (painel do canvas)

```text
┌────────────────────────────────────────────┐
│ ① Pilar P12 · ② ▭ Estrutura                 │
│ ③ 20 × 50 cm · Térreo → 1º pavimento        │
│ ④ Material: MAT_CONC_FCK30                  │
│ ⑤ [✓ Auditado] ⑥ 2 correções aplicadas      │
│ ⑦ [Editar] [Ver relatório]                  │
└────────────────────────────────────────────┘
```

1. Identificação. 2. Domínio VSL (losango = identidade/material, retângulo = estrutura, hexágono = sistemas).
3. Dimensões e níveis. 4. Material da Tabela A. 5. Estado da auditoria. 6. Resumo do relatório. 7. Ações.

### 6.3 Card de rascunho de IA

```text
┌────────────────────────────────────────────┐
│ ① [✨ Gerado por IA · aguardando revisão]   │
│ ② Orçamento — 214 itens · R$ 1.234.567,89   │
│ ③ 18 vínculos SINAPI sugeridos pela IA      │
│ ④ Base: SINAPI PR 09/2026 não desonerado    │
│ ⑤ Revisor: — · ⑥ [Revisar] [Pedir revisão]  │
└────────────────────────────────────────────┘
```

1. Selo de estado com tokens `--ai-draft-*`: `GERADO_POR_IA` (ícone `sparkles`, "Gerado por IA · aguardando
revisão"), `EM_REVISAO` (`eye`, "Em revisão"), `APROVADO` (`badge-check`, "Aprovado por profissional"),
`REJEITADO` (`x-circle`, "Rejeitado"), `OBSOLETO` (`history`, "Desatualizado — o modelo mudou"). 2. O que foi gerado.
3. Quanto veio da IA. 4. Fonte/versão de referência. 5. Profissional que aprovou (nome e registro), quando houver.
6. Ações: revisar (só quem tem papel, selo `VERIFIED` e 2FA) e pedir revisão humana (Art. 20).
