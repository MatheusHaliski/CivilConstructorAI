# Diagrama de atividades (fluxos ponta a ponta)

Fluxos que atravessam vários RFs. O fluxo (a) cobre [RF01](rf/RF01-modelagem-vetorial-canvas-webgl.md),
[RF02](rf/RF02-comandos-notacao-vi.md), [RF06](rf/RF06-auditoria-vaos-fencepost.md),
[RF08](rf/RF08-filtro-tolerancias-secoes-minimas.md), [RF13](rf/RF13-renderizacao-cena-3d.md),
[RF16](rf/RF16-loop-duplo-validacao.md) e [RF17](rf/RF17-controle-elevacao-pilares.md); o fluxo (b) cobre
[RF03](rf/RF03-grids-e-pavimentos.md)–[RF12](rf/RF12-associacao-sinapi.md) e
[RF27](rf/RF27-transparencia-ia-revisao-humana.md); o fluxo (c) cobre
[RF18](rf/RF18-cadastro-e-verificacao-email.md), [RF19](rf/RF19-login-2fa-sessao.md),
[RF24](rf/RF24-termos-e-consentimentos.md), [RF25](rf/RF25-direitos-do-titular.md) e
[RF26](rf/RF26-exclusao-conta-carencia.md). As atividades de cada RF isolado estão na seção 7.2 do seu doc.

## (a) Fluxo em três tempos da Notação VI com loop duplo

1. **Intenção (usuário):** desenha no canvas ou digita código VI / texto natural.
2. **Processamento (Função VI / LLM):** parser VI ou Claude com structured outputs produz o comando; a Tabela C
   valida regras e a Tabela A resolve materiais; o auditor Python aplica as correções geométricas.
3. **Saída pronta (JS/JSX/BIM):** o kernel gera o sólido, a versão do modelo é gravada e a cena WebGL recebe o diff.

```mermaid
flowchart TD
  ini((início)) --> i1{"Tipo de entrada"}
  subgraph T1["1 · Intenção"]
    i1 -- "gesto no canvas" --> i2["Canvas monta ComandoVI"]
    i1 -- "código VI" --> i3["Parser VI determinístico"]
    i1 -- "texto natural" --> i4["Sanitizar texto e remover dados pessoais"]
  end
  subgraph T2["2 · Processamento"]
    i4 --> p1["Claude claude-opus-5-5 com structured outputs<br/>effort definido pela rota"]
    p1 --> p2{"stop_reason = refusal?"}
    p2 -- sim --> p2e["Informar recusa ao usuário"]
    p2 -- não --> p3["JSON de comandos VI"]
    i2 --> l1
    i3 --> l1
    p3 --> l1["Laço 1 · código: JSON Schema, gramática VI,<br/>regras da Tabela C, materiais da Tabela A"]
    l1 --> d1{"Código válido?"}
    d1 -- não --> r1{"Origem IA e tentativas < 3?"}
    r1 -- sim --> p1c["Devolver erro estruturado ao LLM"] --> p1
    r1 -- não --> e422["422 com todos os erros"]
    d1 -- sim --> a1["Auditor: Caixa Cartesiana Sanitizada - RF06"]
    a1 --> a2["ShortCurveTolerance e seções mínimas - RF08"]
    a2 --> a3["Disciplina Z - RF17"]
    a3 --> l2["Laço 2 · geometria: BRep válido, sem auto-interseção,<br/>envoltória (aviso na edição, reprova na emissão), NewOpening projetado"]
    l2 --> d2{"Sólido válido?"}
    d2 -- não --> r1
  end
  subgraph T3["3 · Saída pronta"]
    d2 -- sim --> s0{"Origem IA?"}
    s0 -- sim --> s1["Prévia marcada gerado por IA"] --> s2{"Usuário confirma?"}
    s2 -- não --> fim((fim))
    s0 -- não --> s3
    s2 -- sim --> s3["Gravar comando, relatório de auditoria e versão+1"]
    s3 --> s4["Devolver DiffModelo"]
    s4 --> s5["H9_scene3d aplica o diff - RF13"]
  end
  s5 --> fim
  e422 --> fim
  p2e --> fim
```

## (b) Do modelo ao orçamento aprovado

```mermaid
flowchart TD
  ini((início)) --> a["RF03 Gerar eixos e pavimentos"]
  a --> b["RF01/RF02 Modelar arquitetura"]
  b --> c["RF04 Lançar pilares, vigas, lajes e cargas"]
  c --> c1{"Lançamento sugerido por IA?"}
  c1 -- sim --> c2["RascunhoIA - engenheiro revisa"] --> c3{"Aprovado?"}
  c3 -- não --> c
  c1 -- não --> d
  c3 -- sim --> d["RF16 Loop duplo + RF06/RF08/RF17"]
  d --> e["RF05 Análise MEF - job assíncrono"]
  e --> e1{"Estrutura estável?"}
  e1 -- não --> e2["Erro em pt-BR: matriz singular / hipostática"] --> c
  e1 -- sim --> f["RF07 Dimensionar armaduras ELU/ELS"]
  f --> f1{"Todos os elementos verificam?"}
  f1 -- não --> f2["Ajustar seções"] --> c
  f1 -- sim --> g{"RF09 Perfil SPT disponível?"}
  g -- não --> g1["Cadastrar sondagem / importar boletim com citações"] --> h
  g -- sim --> h["RF10 Dimensionar fundações com reações de apoio"]
  h --> i["RF11 Quantitativos: concreto m³, fôrma m², aço kg"]
  i --> j["RF12 Associar composições SINAPI da data-base"]
  j --> j1["Vínculos sugeridos por IA ficam marcados gerado por IA"]
  j1 --> k["Rascunho de orçamento"]
  k --> l{"Aprovador tem papel ENGENHEIRO,<br/>selo VERIFIED, 2FA e ART?"}
  l -- não --> l1["403 com motivo; segue rascunho"] --> fim((fim))
  l -- sim --> m["Aprovação em modo sudo"]
  m --> n["Orçamento APROVADO + registro de operações"]
  n --> fim
```

Qualquer mudança no modelo depois da aprovação marca análise, dimensionamento, quantitativo e orçamento como
`OBSOLETO` (ver [MAQUINA_ESTADOS.md](MAQUINA_ESTADOS.md)).

## (c) Ciclo de vida da conta

```mermaid
flowchart TD
  ini((início)) --> a["RF18 Cadastro: dados, 18+, aceite de termos e política"]
  a --> b["Conta PENDING_VERIFICATION + sessão aberta"]
  b --> c{"Código de e-mail correto em 30 min<br/>e até 5 tentativas?"}
  c -- não --> c1["Reenviar código - 1 por minuto"] --> c
  c -- sim --> d["Conta ACTIVE"]
  d --> e["RF19 Login em etapas: senha → 2FA → empresa ativa"]
  e --> f["Uso do app: obras, modelo, orçamento"]
  f --> g{"Nova versão de Termos?"}
  g -- sim --> g1["RF24 Bloquear até reaceite"] --> f
  g -- não --> h{"Pede exclusão?"}
  h -- não --> f
  h -- sim --> i["RF26 alertdialog: consequências, baixar cópia RF25,<br/>caixa Entendo + senha + código"]
  i --> j["Conta PENDING_DELETION por 30 dias; sessões encerradas"]
  j --> k{"Reativa no login dentro de 30 dias?"}
  k -- sim --> d
  k -- não --> l["Job diário 03:15: saga de eliminação"]
  subgraph SAGA["Saga idempotente"]
    l --> s1["Anonimizar pessoa no PostgreSQL"]
    s1 --> s2["Manter registros com obrigação legal + base legal"]
    s2 --> s3["Após commit: remover objetos S3"]
    s3 --> s4["Limpar Cassandra e Valkey"]
    s4 --> s5["Apagar KEK no cofre de chaves + tombstone - crypto-shredding"]
    s5 --> s6["Recibo sem dado pessoal"]
  end
  s6 --> z["Conta ANONYMOUS_LGPD_DELETED - terminal"]
  z --> fim((fim))
```
