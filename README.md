# CivilConstructorAI

CAD/BIM generativo para construção civil: o projetista desenha ou descreve em texto o que quer, a IA traduz para
a **Notação VI**, um **auditor paramétrico em Python** corrige a geometria e o modelo auditado alimenta análise
estrutural, dimensionamento, fundações, quantitativos e orçamento SINAPI. Todo resultado de IA é **rascunho**
até a aprovação de um profissional habilitado (ART/RRT).

> **Fase atual: projeto.** O repositório contém a especificação (RFs, diagramas e ADRs). O código entra na ordem
> definida em [`docs/TRELLO_BACKLOG.md`](docs/TRELLO_BACKLOG.md).

## Onde está cada coisa

| Documento | Conteúdo |
|---|---|
| [`docs/rf/README.md`](docs/rf/README.md) | Catálogo dos 28 requisitos funcionais, um doc por RF com 6 diagramas Mermaid |
| [`docs/CATALOGO_COMANDOS.md`](docs/CATALOGO_COMANDOS.md) | Comandos e atalhos do motor de desenho (AutoCAD/Revit/Civil 3D → Notação VI) |
| [`docs/DECISOES_DE_NEGOCIO.md`](docs/DECISOES_DE_NEGOCIO.md) | Decisões de negócio tomadas (DN-01…DN-07) |
| [`docs/PESQUISA_TECNOLOGIAS.md`](docs/PESQUISA_TECNOLOGIAS.md) | ADRs e levantamento de mercado |
| [`docs/TAXONOMIA_ATORES_E_CARDS.md`](docs/TAXONOMIA_ATORES_E_CARDS.md) | Atores, papéis por obra e anatomia dos cards |
| [`docs/DIAGRAMA_CASOS_DE_USO.md`](docs/DIAGRAMA_CASOS_DE_USO.md) | Casos de uso por ator e módulo |
| [`docs/DIAGRAMA_ATIVIDADES.md`](docs/DIAGRAMA_ATIVIDADES.md) | Fluxos ponta a ponta |
| [`docs/DIAGRAMA_CLASSES.md`](docs/DIAGRAMA_CLASSES.md) | Modelo de domínio |
| [`docs/DIAGRAMA_SEQUENCIA.md`](docs/DIAGRAMA_SEQUENCIA.md) | Sequências principais |
| [`docs/MAQUINA_ESTADOS.md`](docs/MAQUINA_ESTADOS.md) | Todas as máquinas de estado |
| [`docs/DIAGRAMA_COMPONENTES.md`](docs/DIAGRAMA_COMPONENTES.md) | Arquitetura, implantação e layout de pastas |
| [`docs/TRELLO_BACKLOG.md`](docs/TRELLO_BACKLOG.md) | Espelho do quadro do Trello |

Quadro do Trello: [CivilConstructorAI · CAD/BIM Generativo](https://trello.com/b/Ep08cZde/civilconstructorai-cad-bim-generativo).

## Módulos

| Módulo | RFs |
|---|---|
| M0 Identidade & Acesso | RF18–RF23 |
| M1 Concepção Arquitetônica | RF01–RF03, RF28 |
| M2 Análise Estrutural & Esforços | RF04–RF06 |
| M3 Dimensionamento Normativo | RF07–RF08 |
| M4 Geotecnia & Fundações | RF09–RF10 |
| M5 Extração Quantitativa (5D) | RF11–RF12 |
| M6 Visualização & Pranchas | RF13–RF15 |
| M7 Middleware & Agentes IA | RF16–RF17 |
| M8 Privacidade LGPD & IA responsável | RF24–RF27 |

## Stack

Java 21/25 + Spring Boot 4.1 (core-api) · Python 3.13 (geometria: auditor, MEF, CadQuery/OpenCASCADE,
IfcOpenShell) · PostgreSQL 17 + PostGIS 3.5 com RLS · Valkey 9 · Cassandra 5 · S3 · Claude API
(`claude-opus-5-5`, SDK Java oficial) · Next.js 16 + Three.js · Expo SDK 58 · Turborepo. Justificativas nos ADRs.

## Verificar a documentação

```bash
python3 scripts/check_rf_docs.py            # estrutura de todos os RFs
python3 scripts/check_rf_docs.py --render   # + renderiza todo Mermaid (precisa de mmdc no PATH)
```
