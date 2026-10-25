# Diagrama de casos de uso (visão geral)

Visão consolidada de **ator × módulo** do CivilConstructorAI. Cobre todos os RFs do
[catálogo](rf/README.md): M0 Identidade & Acesso ([RF18](rf/RF18-cadastro-e-verificacao-email.md)–[RF23](rf/RF23-perfil-sessoes-modo-sudo.md)),
M1 Concepção ([RF01](rf/RF01-modelagem-vetorial-canvas-webgl.md)–[RF03](rf/RF03-grids-e-pavimentos.md)),
M2 Análise estrutural ([RF04](rf/RF04-lancamento-elementos-e-cargas.md)–[RF06](rf/RF06-auditoria-vaos-fencepost.md)),
M3 Dimensionamento ([RF07](rf/RF07-dimensionamento-armaduras.md)–[RF08](rf/RF08-filtro-tolerancias-secoes-minimas.md)),
M4 Geotecnia ([RF09](rf/RF09-perfil-solo-spt.md)–[RF10](rf/RF10-dimensionamento-fundacoes.md)),
M5 Quantitativos 5D ([RF11](rf/RF11-levantamento-quantitativos.md)–[RF12](rf/RF12-associacao-sinapi.md)),
M6 Visualização ([RF13](rf/RF13-renderizacao-cena-3d.md)–[RF15](rf/RF15-interoperabilidade-openbim.md)),
M7 Middleware IA ([RF16](rf/RF16-loop-duplo-validacao.md)–[RF17](rf/RF17-controle-elevacao-pilares.md)) e
M8 LGPD ([RF24](rf/RF24-termos-e-consentimentos.md)–[RF27](rf/RF27-transparencia-ia-revisao-humana.md)) e o motor de desenho
([RF28](rf/RF28-motor-desenho-comandos.md)).
O detalhe de cada caso de uso está no diagrama 7.1 do doc do RF.

> **Revisão da auditoria 2026-10** ([relatório](auditoria/AUDITORIA_2026-10.md)). Este diagrama descreve o que o código faz hoje;
> o que está só especificado aparece marcado como *não implementado*. Correções desta revisão: atores mestre de obras, técnico(a)
> de segurança, fornecedor e trabalhador entraram nos fluxos com o que o código de fato permite; RF21 passou a ter o nome do doc
> (controle de acesso por papel, que inclui o selo); RF06/RF08 deixaram de ser "middleware" — rodam no auditor do `ModeloBim`
> (Java) a cada comando, junto com RF16/RF17; o auditor e o MEF são Java (não há serviço Python no repositório); a saga de
> eliminação não "estende" a exclusão: é disparada pelo job quando a carência acaba, e a reativação é um caso próprio; a
> visibilidade do cliente deixou de ser pendente (liberação explícita por versão); colaboração e revisão de versões ganharam
> casos de uso, com o que existe e o que falta.

## 1. Identidade, acesso e LGPD (M0 + M8)

```mermaid
flowchart LR
  vis(["👤 Visitante"])
  usr(["👤 Usuário autenticado"])
  dono(["👤 Dono / admin da empresa"])
  resp(["👤 Engenheiro(a) / arquiteto(a)"])
  mestre(["👤 Mestre de obras"])
  dpo(["👤 Encarregado - DPO"])
  sup(["👤 Suporte da plataforma"])
  job(["⚙️ Job agendado"])
  subgraph M0["M0 · Identidade & Acesso"]
    u18["RF18 Cadastrar conta e verificar e-mail"]
    u19["RF19 Entrar com 2FA e escolher empresa"]
    u20["RF20 Convidar / aceitar convite"]
    u21["RF21 Controlar acesso por papel na obra"]
    u21s["RF21 Pedir e decidir selo CREA/CAU"]
    u22["RF22 Gerir empresa, obras e membros"]
    u22t["RF22 Vincular trabalhador sem login"]
    u23["RF23 Perfil, sessões, 2FA e modo sudo"]
  end
  subgraph M8["M8 · Privacidade LGPD & IA responsável"]
    u24["RF24 Aceitar termos e gerir consentimentos"]
    u25["RF25 Ver, corrigir e exportar dados"]
    u26["RF26 Pedir exclusão da conta"]
    u26r["RF26 Reativar conta na carência"]
    u26s["RF26 Saga de eliminação"]
    u27["RF27 Ver uso de IA e pedir revisão humana"]
  end
  vis --> u18
  vis --> u20
  usr --> u19 & u23 & u24 & u25 & u26 & u27
  usr --> u26r
  dono --> u20 & u22 & u25
  resp --> u20 & u21s
  mestre --> u22t
  sup --> u21s
  dpo --> u25 & u27
  job --> u26s
  u18 -. inclui .-> u24
  u19 -. inclui .-> u21
  u26r -. estende .-> u19
  u26 -. "após 30 dias, sem reativação" .-> u26s
```

## 2. Projeto, cálculo e orçamento (M1–M7)

```mermaid
flowchart LR
  arq(["👤 Arquiteto(a)"])
  eng(["👤 Engenheiro(a) responsável"])
  orc(["👤 Orçamentista"])
  mestre(["👤 Mestre de obras / técnico(a) de segurança"])
  cli(["👤 Cliente / fornecedor"])
  ia(["⚙️ Serviço de IA - Claude"])
  aud(["⚙️ Auditor do ModeloBim - Java"])
  rev(["⚙️ Revit / ferramentas IFC"])
  subgraph M1["M1 · Concepção"]
    c1["RF01 Modelar no canvas 2D/3D"]
    c2["RF02 Comandar por Notação VI ou texto"]
    c3["RF03 Gerar eixos e pavimentos"]
    c28["RF28 Usar o motor de comandos"]
  end
  subgraph M2M3["M2/M3 · Estrutura"]
    s4["RF04 Lançar elementos e cargas"]
    s5["RF05 Calcular esforços MEF"]
    s7["RF07 Dimensionar armaduras"]
  end
  subgraph M4["M4 · Geotecnia"]
    g9["RF09 Cadastrar sondagem SPT"]
    g10["RF10 Dimensionar fundações"]
  end
  subgraph M5["M5 · 5D"]
    q11["RF11 Levantar quantitativos"]
    q12["RF12 Associar SINAPI e orçar"]
  end
  subgraph M6["M6 · Visualização"]
    v13["RF13 Visualizar cena 3D"]
    v13l["Liberar versão ao cliente"]
    v14["RF14 Gerar pranchas"]
    v15["RF15 Exportar / importar IFC"]
  end
  subgraph AUD["Auditor (a cada comando)"]
    m16["RF16 Loop duplo de validação"]
    m6["RF06 Corrigir vãos - Fencepost"]
    m8["RF08 Filtrar tolerâncias e seções"]
    m17["RF17 Nivelar pilares - Disciplina Z"]
  end
  subgraph COL["Colaboração e revisão"]
    h1["RF01 Ver histórico de comandos com autor e versão"]
    h27["RF27 Aprovar ou descartar rascunho de IA"]
    hx["Comentar, bloquear elemento, comparar e restaurar versão — não implementado"]
  end
  arq --> c1 & c2 & c3 & c28 & v14 & v15 & h1 & h27
  eng --> c1 & c2 & c3 & c28 & s4 & s5 & s7 & g9 & g10 & q11 & v14 & v15 & h1 & h27
  eng --> v13l
  orc --> q11 & q12
  eng --> q12
  mestre --> v13
  cli --> v13
  arq --> v13
  eng --> v13
  c1 -. inclui .-> m16
  c2 -. inclui .-> m16
  c28 -. inclui .-> m16
  s4 -. inclui .-> m16
  m16 -. inclui .-> m6 & m8 & m17
  v13l -. "cliente só vê o liberado" .-> v13
  c2 --- ia
  s4 --- ia
  q12 --- ia
  g9 --- ia
  m16 --- aud
  v15 --- rev
```

## 3. Matriz ator × módulo

| Ator | M0 | M1 | M2 | M3 | M4 | M5 | M6 | M7 | M8 |
|---|---|---|---|---|---|---|---|---|---|
| Visitante | cadastro, convite | — | — | — | — | — | — | — | aceite de termos |
| Dono / admin da empresa | empresa, obras, convites (até o próprio nível; nunca DONO) | editar | lançar, calcular | dimensionar, aprovar¹ | sondagem, fundações | aprovar orçamento¹ | pranchas, IFC, liberar ao cliente | — | dados dos trabalhadores |
| Engenheiro(a) responsável | convites para a obra | editar | lançar, calcular | dimensionar, aprovar¹ | sondagem, fundações | aprovar orçamento¹ | pranchas, IFC, liberar ao cliente | — | aprovar rascunho de IA¹ |
| Arquiteto(a) | convites para a obra | editar | ver | ver | ver | ver | pranchas, IFC | — | aprovar rascunho de IA¹ |
| Orçamentista | — | ver | — | — | — | preparar orçamento | ver | — | — |
| Mestre de obras | vincular trabalhador sem login | ver | — | — | — | — | ver | — | — |
| Técnico(a) de segurança | — | ver | — | — | — | — | ver | — | —³ |
| Cliente | — | versão liberada² | — | — | — | totais (ou detalhe) liberados² | versão liberada² | — | — |
| Fornecedor | — | versão liberada² | — | — | — | —⁴ | versão liberada² | — | — |
| Trabalhador | — (sem login) | — | — | — | — | — | — | — | titular dos dados |
| Encarregado (DPO) | — | — | — | — | — | — | — | — | pedidos de titulares, revisão humana |
| Suporte da plataforma | decidir selos (motivo obrigatório na recusa) | — | — | — | — | — | — | — | — |

¹ Exige selo profissional `VERIFIED`, 2FA e ART/RRT da obra ([RF21](rf/RF21-controle-acesso-papel-obra.md), [RF27](rf/RF27-transparencia-ia-revisao-humana.md)).
² Liberação explícita por versão (modelo, quantitativo, orçamento); rascunhos de IA nunca aparecem. Verificado na auditoria
2026-10 (DN-02, jornada j3).
³ `PapelObra.veDadoDeSaude()` existe, mas nenhum caso de uso o consome: permissão sem fluxo (pendência).
⁴ "Pedidos do fornecedor" está na especificação de negócio, mas não há módulo de pedidos: fornecedor hoje só vê o que é liberado.
