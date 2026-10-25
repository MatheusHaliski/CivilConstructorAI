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
M8 LGPD ([RF24](rf/RF24-termos-e-consentimentos.md)–[RF27](rf/RF27-transparencia-ia-revisao-humana.md)).
O detalhe de cada caso de uso está no diagrama 7.1 do doc do RF.

## 1. Identidade, acesso e LGPD (M0 + M8)

```mermaid
flowchart LR
  vis(["👤 Visitante"])
  usr(["👤 Usuário autenticado"])
  dono(["👤 Dono / admin da empresa"])
  dpo(["👤 Encarregado - DPO"])
  sup(["👤 Suporte da plataforma"])
  job(["⚙️ Job agendado"])
  subgraph M0["M0 · Identidade & Acesso"]
    u18["RF18 Cadastrar conta e verificar e-mail"]
    u19["RF19 Entrar com 2FA e escolher empresa"]
    u20["RF20 Convidar / aceitar convite"]
    u21["RF21 Verificar selo CREA/CAU/CNPJ"]
    u22["RF22 Gerir empresa, obras e membros"]
    u23["RF23 Perfil, sessões, 2FA e modo sudo"]
  end
  subgraph M8["M8 · Privacidade LGPD & IA responsável"]
    u24["RF24 Aceitar termos e gerir consentimentos"]
    u25["RF25 Ver, corrigir e exportar dados"]
    u26["RF26 Excluir ou reativar conta"]
    u26s["RF26 Saga de eliminação"]
    u27["RF27 Ver uso de IA e pedir revisão humana"]
  end
  vis --> u18
  vis --> u20
  usr --> u19 & u23 & u24 & u25 & u26 & u27
  dono --> u20 & u22 & u25
  sup --> u21
  dpo --> u25 & u27
  job --> u26s
  u18 -. inclui .-> u24
  u26 -. estende .-> u26s
```

## 2. Projeto, cálculo e orçamento (M1–M7)

```mermaid
flowchart LR
  arq(["👤 Arquiteto(a)"])
  eng(["👤 Engenheiro(a) responsável"])
  orc(["👤 Orçamentista"])
  cli(["👤 Cliente / proprietário"])
  ia(["⚙️ Serviço de IA - Claude"])
  aud(["⚙️ Auditor Python"])
  rev(["⚙️ Revit / ferramentas IFC"])
  subgraph M1["M1 · Concepção"]
    c1["RF01 Modelar no canvas 2D/3D"]
    c2["RF02 Comandar por Notação VI ou texto"]
    c3["RF03 Gerar eixos e pavimentos"]
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
    v14["RF14 Gerar pranchas"]
    v15["RF15 Exportar IFC / STEP"]
  end
  subgraph M7["M7 · Middleware"]
    m16["RF16 Loop duplo de validação"]
    m6["RF06 Corrigir vãos - Fencepost"]
    m8["RF08 Filtrar tolerâncias e seções"]
    m17["RF17 Nivelar pilares - Disciplina Z"]
  end
  arq --> c1 & c2 & c3 & v14 & v15
  eng --> c1 & c2 & c3 & s4 & s5 & s7 & g9 & g10 & q11 & v14 & v15
  orc --> q11 & q12
  eng --> q12
  cli --> v13
  arq --> v13
  eng --> v13
  c1 -. inclui .-> m16
  c2 -. inclui .-> m16
  s4 -. inclui .-> m16
  m16 -. inclui .-> m6 & m8 & m17
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
| Dono / admin da empresa | empresa, obras, convites | ver | ver | ver | ver | ver | ver | — | dados dos trabalhadores |
| Engenheiro(a) responsável | convites para a obra | editar | lançar, calcular | dimensionar, aprovar¹ | sondagem, fundações | aprovar orçamento¹ | pranchas, IFC | recebe relatórios | aprovar rascunho de IA¹ |
| Arquiteto(a) | — | editar | ver | ver | ver | ver | pranchas, IFC | recebe relatórios | aprovar rascunho de IA¹ |
| Orçamentista | — | ver | — | — | — | preparar orçamento | ver | — | — |
| Mestre de obras / técnico(a) de segurança | — | ver | — | — | — | — | ver | — | — |
| Cliente / proprietário | — | — | — | — | — | totais liberados² | ver liberado² | — | — |
| Fornecedor | — | — | — | — | — | pedidos dele | — | — | — |
| Encarregado (DPO) | — | — | — | — | — | — | — | — | pedidos de titulares, revisão humana |
| Suporte da plataforma | verificar selos (acesso temporário) | — | — | — | — | — | — | — | — |

¹ Exige selo profissional `VERIFIED`, 2FA e ART/RRT da obra ([RF21](rf/RF21-controle-acesso-papel-obra.md), [RF27](rf/RF27-transparencia-ia-revisao-humana.md)).
² O que o cliente vê é **decisão de negócio pendente** (card ❓ no Trello).
