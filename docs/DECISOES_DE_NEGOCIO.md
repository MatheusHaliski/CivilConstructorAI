# Decisões de negócio (DN)

Registro das decisões que dependiam do negócio e estavam no card
[❓ Decisões de negócio pendentes](https://trello.com/c/Iwnugds9). Em 2026-10-25 o responsável pelo produto
decidiu **seguir a opção recomendada em todas**. Cada DN diz o que foi decidido, por quê, e onde isso é aplicado.
Mudar uma DN = nova versão aqui + atualizar os RFs listados no mesmo commit.

| DN | Tema | Decisão (resumo) | Aplica em |
|---|---|---|---|
| DN-01 | Controladora, Encarregado e operador | papéis por contrato; nomes em configuração, nunca no código | RF24, RF25, RF26, RF27, `docs/legal/` |
| DN-02 | O que o cliente vê de cada obra | só o que a empresa **liberou**, por artefato; nunca rascunho de IA nem dado de trabalhador | RF12, RF13, RF14, RF21, RF22 |
| DN-03 | Retenção de dados de trabalhadores | prazos legais mínimos por categoria, configuráveis só para cima | RF25, RF26 |
| DN-04 | Rotas que usam IA | só as cinco catalogadas no MVP; o resto é determinístico | RF02, RF04, RF09, RF12, RF16, RF27 |
| DN-05 | Camadas D, E e F da Notação VI | D = Desenho técnico, E = Engenharia (cálculo), F = Fabricação e custos | RF02, RF28, RF05–RF12, RF14 |
| DN-06 | pyRevit no MVP | **opcional**, fase 2; IFC/STEP cobrem a interoperabilidade do MVP | RF15 |
| DN-07 | Tipografia e cor de marca | Inter + JetBrains Mono; marca azul `#1D4ED8` com texto `#1E40AF`; acento laranja `#C2410C` | `docs/DESIGN_SYSTEM.md`, `packages/tokens` |

## DN-01 · Controladora, Encarregado (DPO) e operador

**Decisão.** Os papéis da LGPD são definidos **por contrato e por tipo de dado**, e os nomes reais ficam em
configuração (`APP_LGPD_*`) e nos marcadores `{{…}}` de `docs/legal/`, nunca no código:

| Dado | Controladora | Operador |
|---|---|---|
| Dados da conta do usuário (e-mail, senha, 2FA, preferências, registro de operações) | **CivilConstructorAI** (`{{CONTROLADORA_RAZAO_SOCIAL}}`, CNPJ `{{CONTROLADORA_CNPJ}}`) | — |
| Dados de trabalhadores, clientes e fornecedores cadastrados pela construtora (CPF, presença, fotos, ASO) | **a construtora cliente** (tenant) | CivilConstructorAI |
| Dados enviados à IA | quem for controladora do dado de origem | CivilConstructorAI → Anthropic (suboperador, transferência internacional, Art. 33) |

Encarregado da plataforma: `{{DPO_NOME}}`, canal `{{DPO_EMAIL}}` (recomendado `privacidade@<domínio>`), prazo de
resposta **15 dias** (Art. 18, §5º). Cada construtora indica o próprio encarregado no cadastro da empresa
(campo `encarregado_contato`, obrigatório para sair de `PLANEJADA` na primeira obra).

**Por quê.** Não há como inventar nomes jurídicos; a estrutura, sim, é a mesma qualquer que seja o nome. A
separação por tipo de dado é a que a LGPD exige (Art. 5º, VI e VII) e a que o prompt descreve.

## DN-02 · O que o cliente / proprietário vê de cada obra

**Decisão.** O cliente (`CLIENTE`) vê **apenas o que a empresa liberou**, por artefato, com um controle único
`liberadoCliente` que só `DONO`, `ADMIN` ou `ENGENHEIRO` da obra alteram (registrado no registro de operações):

| Artefato | Padrão | Pode ser liberado? |
|---|---|---|
| Estado da obra, cronograma resumido, % por etapa | visível | — |
| Fotos do canteiro **sem trabalhadores identificáveis** | não | sim |
| Modelo 3D (versão `liberadaCliente`, somente leitura, sem resultados de cálculo nem camada `ia-rascunho`) | não | sim |
| Pranchas `EMITIDAS` | não | sim |
| Orçamento: **totais por etapa** e aditivos para aprovar | não | sim |
| Orçamento detalhado (composições SINAPI, custos unitários) | não | sim, explicitamente |
| Medições liberadas | não | sim |
| Rascunhos de IA, memórias de cálculo, sondagens, dados de trabalhadores, ASO | **nunca** | não |

**Por quê.** Mantém a regra "404 para o que não é seu" simples (uma flag), protege o segredo comercial da
construtora por padrão e nunca expõe dado pessoal de terceiros ao cliente.

## DN-03 · Prazos de retenção de dados de trabalhadores

**Decisão.** Mínimos legais por categoria, pré-configurados; a empresa só pode **aumentar** (RF25 RN-04):

| Categoria | Mínimo | Base |
|---|---|---|
| Registros de saúde ocupacional (ASO, PCMSO, afastamentos) | **20 anos** após o desligamento | NR-7 |
| Registros de treinamento e de segurança (NR-18, NR-35, ordens de serviço) | **20 anos** após o desligamento | NR-1 |
| Documentos trabalhistas (contrato, presença/ponto, folha) | **5 anos** após o desligamento | CLT art. 7º, XXIX da CF; art. 11 |
| Fotos do canteiro com trabalhadores identificáveis | duração da obra + **5 anos** | prazo prescricional de ações da obra |
| Dados de contato para operação (telefone, e-mail) | enquanto for membro da obra + 90 dias | Art. 15, I |

Enquanto o prazo corre, o dado fica **bloqueado** (não aparece nas telas operacionais, só no inventário da
empresa e para atender fiscalização); vencido o prazo, o job de eliminação (RF26) anonimiza. Marcar para
revisão do jurídico da construtora: a base legal de cada linha aparece na tela "Dados dos trabalhadores".

**Por quê.** São os prazos que a legislação trabalhista e as NRs exigem; configurar abaixo disso exporia a
construtora. Prazos maiores são decisão dela.

## DN-04 · Rotas que usam IA

**Decisão.** No MVP, **exatamente cinco** rotas, todas em `rotas.yml` (RF27) com `effort` fixado no ADR-003:
`comando-vi` (RF02), `lancamento-estrutural` (RF04), `extracao-boletim-spt` (RF09), `associacao-sinapi` (RF12)
e `correcao-auditoria` (RF16). Tudo o mais — motor de desenho (RF28), auditoria, MEF, dimensionamento,
fundações, quantitativos, pranchas, IFC — é **determinístico**. Candidatas à fase 2, cada uma com RF, conjunto
de avaliação e consentimento quando envolver pessoas: `classificar-foto` (canteiro), `resumo-rdo`, `leitura-
memorial` (PDF com citações).

**Por quê.** Cada rota custa avaliação, LGPD e dinheiro; cinco cobrem o fluxo "intenção → modelo → orçamento"
do produto sem colocar IA onde um algoritmo resolve melhor.

## DN-05 · Camadas D, E e F da Notação VI

O PDF de diretrizes cita 7 camadas e detalha A (Data Model), B (Funções), C (Controle e fluxo), H (UI) e J
(Objetos nativos). **Decisão** para as três restantes, seguindo a lógica "uma camada por família de saída":

| Tabela | Nome | Conteúdo | RFs |
|---|---|---|---|
| **D** | Desenho técnico | cotas, textos, etiquetas, hachuras, blocos, escalas, formatos ABNT; funções `COTA_*`, `TEXTO`, `ETIQUETA`, `HACHURA`, `BLOCO` | RF14, RF28 |
| **E** | Engenharia | cargas, combinações, análise (MEF), dimensionamento, fundações; funções `CARGA`, `COMBINACAO`, `ANALISAR`, `DIMENSIONAR`, `FUNDACAO` | RF04–RF07, RF10 |
| **F** | Fabricação e custos | quantitativos, composições SINAPI, orçamento, exportação IFC/STEP/DXF; funções `QUANTIFICAR`, `ASSOCIAR_SINAPI`, `EXPORTAR` | RF11, RF12, RF15 |

A Tabela B continua sendo a das funções geométricas (desenho e modificação, catálogo do RF28); D, E e F são
funções de nível mais alto que **consomem** o modelo auditado. Numeração G fica reservada.

**Por quê.** Mapeia 1:1 nos módulos M6, M2–M4 e M5/M6, mantém as cinco tabelas já definidas intactas e
deixa claro o que a IA pode gerar (B) e o que só o sistema executa (E e F são sempre determinísticas).

## DN-06 · pyRevit no MVP

**Decisão.** A ponte pyRevit (RF15) é **opcional e fica para a fase 2**. No MVP, interoperabilidade é
**IFC 4.3** (ida e volta) e **STEP AP242** (sólidos) — ambos sem licença nem Windows. A extensão
`pyrevit_extension/` permanece no layout do repositório e no RF15 como "fase 2", com o pareamento por código de
uso único já especificado.

**Por quê.** Revit exige Windows e licença; IFC cobre Revit, Navisworks e os validadores buildingSMART.
Entregar o MVP sem depender do Revit reduz o escopo e o custo de teste.

## DN-07 · Tipografia e cor de marca

**Decisão.** Fontes **Inter** (texto) e **JetBrains Mono** (códigos SINAPI, coordenadas e quantitativos; ambas
OFL, auto-hospedadas). Marca "azul engenharia": `--color-cca-500: #1D4ED8` (botões), `--brand-text: #1E40AF`
(texto, 8,7:1 sobre branco), `--on-brand: #FFFFFF` (6,7:1). Acento "laranja canteiro" `#C2410C` para "obra em
andamento" e avisos (5,2:1). Tema claro de alto contraste é o padrão no mobile. Tabela completa de contraste
em [`docs/DESIGN_SYSTEM.md`](DESIGN_SYSTEM.md).

**Por quê.** Inter e JetBrains Mono têm números tabulares e boa legibilidade em tela suja e sol; o azul passa
4,5:1 como texto e como fundo de botão, o que elimina a exceção "botão de marca com texto escuro".
