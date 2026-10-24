# Backlog (espelho do Trello)

Quadro: [CivilConstructorAI · CAD/BIM Generativo](https://trello.com/b/Ep08cZde/civilconstructorai-cad-bim-generativo)
(Área de trabalho 2). Este arquivo espelha o quadro; quando um card muda de lista, este arquivo muda no mesmo
commit do código ou doc correspondente.

## Listas do quadro

| # | Lista | Conteúdo |
|---|---|---|
| 1 | 📌 Visão, ADRs & Docs transversais | cards `DOC` e decisões pendentes |
| 2 | M0 · Identidade & Acesso | RF18–RF23 |
| 3 | M1 · Concepção Arquitetônica | RF01–RF03 |
| 4 | M2 · Análise Estrutural & Esforços | RF04–RF06 |
| 5 | M3 · Dimensionamento Normativo | RF07–RF08 |
| 6 | M4 · Geotecnia & Fundações | RF09–RF10 |
| 7 | M5 · Extração Quantitativa 5D | RF11–RF12 |
| 8 | M6 · Visualização & Pranchas | RF13–RF15 |
| 9 | M7 · Middleware & Agentes IA | RF16–RF17 |
| 10 | M8 · Privacidade LGPD & IA responsável | RF24–RF27 |
| 11 | 🔨 Em andamento | card em implementação |
| 12 | 👀 Revisão | PR aberto |
| 13 | ✅ Concluído | testes com login real + Mermaid no CI + doc com rastreabilidade ✅ |

Cada card RF tem duas checklists: **Documentação (docs/rf)** (doc + 6 diagramas) e **Entrega** (migração,
backend, testes `*IT`, front, rastreabilidade).

## Cards

| Card | Lista | Documentação | Entrega |
|---|---|---|---|
| [DOC · Visão, arquitetura e ADRs](https://trello.com/c/ariuFHjW) | 📌 | ✅ `PESQUISA_TECNOLOGIAS.md`, `README.md` | — |
| [DOC · Diagramas transversais](https://trello.com/c/OH410Kxs) | 📌 | ✅ 6 arquivos `DIAGRAMA_*` / `MAQUINA_ESTADOS.md` | — |
| [DOC · CI "Docs por RF"](https://trello.com/c/7kFA6Jlq) | 📌 | ✅ `scripts/check_rf_docs.py`, `.github/workflows/docs-rf.yml` | — |
| [❓ Decisões de negócio pendentes](https://trello.com/c/Iwnugds9) | 📌 | aberto | — |
| [RF01 · Modelagem vetorial 2D/3D no canvas WebGL](https://trello.com/c/3WgN7lzt) | M1 | ✅ | ⏳ |
| [RF02 · Comandos via Notação VI e prompts JSON](https://trello.com/c/W4pJDtvv) | M1 | ✅ | ⏳ |
| [RF03 · Malhas de eixos e pavimentos Z](https://trello.com/c/5aEYPZHD) | M1 | ✅ | ⏳ |
| [RF04 · Lançamento de elementos e cargas](https://trello.com/c/WOIUHFDD) | M2 | ✅ | ⏳ |
| [RF05 · Diagramas de esforços (MEF)](https://trello.com/c/mNjp8fP4) | M2 | ✅ | ⏳ |
| [RF06 · Auditoria de vãos (Fencepost Error)](https://trello.com/c/OGjcAg5E) | M2 | ✅ | ⏳ |
| [RF07 · Dimensionamento de armaduras](https://trello.com/c/0VapxWRZ) | M3 | ✅ | ⏳ |
| [RF08 · Tolerâncias e seções mínimas](https://trello.com/c/Dgtma9TN) | M3 | ✅ | ⏳ |
| [RF09 · Perfil de solo e SPT](https://trello.com/c/LzHFBr6H) | M4 | ✅ | ⏳ |
| [RF10 · Sapatas, blocos e radier](https://trello.com/c/C7Lgejlv) | M4 | ✅ | ⏳ |
| [RF11 · Concreto, fôrma e aço](https://trello.com/c/ty5wWDp9) | M5 | ✅ | ⏳ |
| [RF12 · Associação SINAPI](https://trello.com/c/64v8Sq35) | M5 | ✅ | ⏳ |
| [RF13 · Cena 3D H9_scene3d](https://trello.com/c/S0TACTtJ) | M6 | ✅ | ⏳ |
| [RF14 · Plantas e cortes](https://trello.com/c/MCxqnKl2) | M6 | ✅ | ⏳ |
| [RF15 · OpenBIM IFC / STEP](https://trello.com/c/aLnGEB4O) | M6 | ✅ | ⏳ |
| [RF16 · Loop duplo de validação](https://trello.com/c/o2wBmxKy) | M7 | ✅ | ⏳ |
| [RF17 · Elevação e nivelamento de pilares](https://trello.com/c/VVTtHXnO) | M7 | ✅ | ⏳ |
| [RF18 · Cadastro e verificação de e-mail](https://trello.com/c/v3Z3WzgD) | M0 | ✅ | ⏳ |
| [RF19 · Login 2FA, sessão e empresa ativa](https://trello.com/c/IEf3vByh) | M0 | ✅ | ⏳ |
| [RF20 · Convites](https://trello.com/c/22GiOPjC) | M0 | ✅ | ⏳ |
| [RF21 · Acesso por papel, selo e RLS](https://trello.com/c/BUNJmEJI) | M0 | ✅ | ⏳ |
| [RF22 · Empresas, obras e membros](https://trello.com/c/4vumPt2g) | M0 | ✅ | ⏳ |
| [RF23 · Perfil, sessões e modo sudo](https://trello.com/c/u9tL8Fqy) | M0 | ✅ | ⏳ |
| [RF24 · Termos e consentimentos](https://trello.com/c/HbUcwgdJ) | M8 | ✅ | ⏳ |
| [RF25 · Direitos do titular](https://trello.com/c/BQtSoryZ) | M8 | ✅ | ⏳ |
| [RF26 · Exclusão com carência e saga](https://trello.com/c/9s245tLM) | M8 | ✅ | ⏳ |
| [RF27 · Transparência de IA e revisão humana](https://trello.com/c/7dTIZbIs) | M8 | ✅ | ⏳ |

## Ordem de entrega

1. Compose + esqueleto Spring + Flyway V1 (contas, empresas, obras, membros, termos, consentimentos,
   preferências, `user_key`) + RLS + teste de schema → RF22 (base).
2. Cadastro, verificação de e-mail, login/logout, sessão no Valkey, CSRF, limite de tentativas → RF18, RF19.
3. Convites, empresa ativa, 2FA, modo sudo, sessões, troca de senha → RF19, RF20, RF23.
4. Acesso por papel na obra e selo + matriz testada + isolamento entre empresas → RF21.
5. Telas e endpoints de LGPD + saga de eliminação → RF24–RF26.
6. Tokens DTCG → CSS/TS, tabela de contraste, Storybook com addon-a11y.
7. Núcleo BIM: serviço `geometria` + auditor + loop duplo → RF16, RF17, RF06, RF08; canvas e comandos VI →
   RF01, RF02, RF03; cena 3D → RF13.
8. Camada de IA atrás de `ModeloLinguagem` (primeira rota: `comando-vi`) + rascunho "gerado por IA" +
   aprovação profissional + conjunto de avaliação → RF02, RF27.
9. Estrutura e fundações → RF04, RF05, RF07, RF09, RF10.
10. 5D e entregáveis → RF11, RF12, RF14, RF15.
