# Auditoria 2026-10 · 2ª rodada — estrutura, instalações, materiais e conformidade técnica

Data: 2026-10-25 · Ramo: `claude/hopeful-carson-3lrvl3` · Complementa [AUDITORIA_2026-10.md](AUDITORIA_2026-10.md).

Três pedidos tratados juntos:

- **A.** Instalações elétricas, água fria, água quente, esgoto, análise estrutural e detalhamento de armaduras, com a
  integração entre desenho, modelo BIM, cálculo e visualização 3D.
- **B.** Catálogo, seleção, aplicação e rastreabilidade de materiais e componentes construtivos.
- **C.** Conformidade técnica, normativa e regulatória.

> **Validação de software não é aprovação técnica de projeto.** Os testes abaixo mostram que o programa calcula o que
> declara calcular, com os métodos e limites que declara. A responsabilidade técnica pelo projeto continua do
> profissional habilitado, com ART/RRT, e o software **não** declara conformidade integral com normas ABNT ou ISO.

---

## 1. Conclusão obrigatória (resposta direta)

**Tem evidência de atendimento** (teste automatizado com valor calculado à mão ou regra verificável, mais jornada):

- Modelo analítico de pórtico espacial (MEF linear de 1ª ordem): eixos locais do pilar, pilar girado e circular,
  carga concentrada na posição informada, laje retangular apoiada pelas charneiras a 45°, cargas triangulares,
  balanço, viga biengastada, treliça, **equilíbrio global conferido em toda análise** (análise falha se o resíduo
  passar de 1e-6).
- Dimensionamento de vigas (As inferior e superior, cisalhamento modelo I, estribos), pilares (flexo-compressão
  oblíqua simplificada com momentos da análise, pilar-padrão até λ ≤ 90), lajes (Grashof com as cargas reais, malha
  com φ ≤ h/8 e s ≤ mín(2h; 20 cm)), durabilidade por CAA (classe mínima e cobrimento) — conferido contra um pórtico
  resolvido à mão (Md = 66,93 kN·m, Vd = 46,9 kN, Nd = 80,5 kN).
- Detalhamento por posição (número de barras, comprimento unitário, comprimento total e massa em colunas separadas;
  ancoragem lb, traspasse com α0t = 2,0 declarado, ganchos, pontas de estribo) e geometria 3D das barras.
- Base de cálculo da obra gravada em cada resultado; mudança de base, modelo, cargas ou material **desatualiza** os
  resultados (estado *Resultado desatualizado*) e **não recalcula em silêncio**.
- Catálogo único de materiais (Java e TypeScript leem o mesmo JSON), propriedades com unidade, origem do valor e
  referência com edição; materiais personalizados com validação de unidade, exigência de documentação do produto,
  validação por profissional com selo e bloqueio de exclusão quando em uso.
- Caminho da IA: falha do provedor → 503 sem efeito parcial; resposta fora do esquema → 422 sem efeito; comando que a
  IA não pode executar → recusado; rascunho ligado à versão do modelo e à chamada registrada.
- Conflitos de coordenação (proposta P-COORD-01) com resolução só por reverificação.

**Tem limitações declaradas** (o software diz o que não faz):

- Sem efeitos de 2ª ordem globais (γz, P-Δ); lajes não são placas (só repartição de carga); vento só em +X; pilares
  esbeltos (λ > 90), torção, punção e continuidade de lajes em *Não verificado*; aço estrutural e contraventamentos
  sem verificação ABNT NBR 8800 (*Não verificado*); lajes não retangulares repartidas por comprimento de apoio
  (aviso de aproximação).
- Instalações: só um verificador experimental fora do modelo BIM (proposta P-MEP-01). Perdas de carga, pressões
  (NBR 5626:2020) e capacidade de condução (NBR 5410) ficam *Não verificado* / *Dados insuficientes* porque o texto
  normativo não foi consultado.
- Referências normativas conferidas em **fontes secundárias** (catálogos de distribuidores, órgãos públicos,
  artigos): a edição está registrada com a fonte e a data, mas não foi conferida no catálogo oficial da ABNT.

**Não pode ser considerado validado:**

- Qualquer resultado como projeto aprovado: falta revisão por profissional habilitado.
- Instalações elétricas, hidráulicas, sanitárias e de ventilação no modelo (não existem elementos de rede no BIM).
- Estruturas metálicas, pré-moldadas, alvenaria estrutural, protensão e cabos (não implementados).
- Verificações de fogo, desempenho térmico e acústico (removidas do catálogo de materiais por dependerem do elemento).
- Códigos SINAPI herdados do catálogo anterior (não conferidos na tabela vigente).

---

## 2. Método

| Fonte de evidência | Conteúdo | Resultado nesta rodada |
|---|---|---|
| Testes de domínio (`cca-domain`) | casos de referência calculados à mão; detector de conflitos; verificador de redes | **103 testes, 0 falhas** |
| Testes de aplicação (`cca-application`) | IFC, paridade TS↔Java (38), IA | 46, 0 falhas |
| Testes de integração (`cca-web`, Postgres/PostGIS real) | análise, dimensionamento, base de cálculo, coordenação, materiais, caminho da IA | 51, 0 falhas |
| Testes do motor TS e do app web | paridade de comandos (51), catálogo de ações (36) | 87, 0 falhas |
| Jornadas reais (backend + web no ar) | j1 identidade 50/50 · j2 projeto e estrutura 40/40 · j3 entrega, IA e LGPD 70/70 · j4 interface 22/22 | todas passam |
| **Projeto integrado j5** | defeitos introduzidos de propósito + telas novas (`scripts/auditoria/j5-integrado.mjs`) | 24/25 na 1ª execução → falha corrigida (seção 4.6) → **25/25** |
| Fontes normativas | registradas no catálogo com organismo, identificador, edição, situação, escopo, URL e data de consulta | seção 7.2 |

Escala das notas (0–10): conclusão do fluxo (30 %), precisão contra referência (25 %), clareza do estado e da
limitação (20 %), retorno ao usuário (15 %), consistência e acessibilidade (10 %). **Uma média alta não compensa
falha crítica:** qualquer função com falha crítica aberta tem nota limitada a 4.

---

## 3. A · Inventário por função

Classificação do que existe: **G** representação gráfica · **C** modelo com conectividade · **P** compatibilidade ·
**F** análise física · **D** dimensionamento · **T** detalhamento.

| Função / RF | Fluxo | Elementos e conexões | Motor de cálculo | Visualização | Teste | Nota antes → depois | Falha encontrada | Correção |
|---|---|---|---|---|---|---|---|---|
| Instalações elétricas (proposta, sem RF) | inexistente → verificador experimental | nenhum no BIM; no verificador: quadro, tomada, luminária, trecho com circuito, seção, F/N/PE | Ib = P/(V·FP), ΔV = 2ρLIb/S; Iz exigido de fonte | nenhuma | `VerificadorRedesTest` | 0 → 2 | não havia nada; risco de "linha colorida" ser lida como projeto | P-MEP-01 com estados honestos (Iz e limite de ΔV nunca inventados) |
| Água fria (proposta) | idem | entrada/reservatório, ponto de consumo, trechos | continuidade e material; pressão e perda **não verificadas** | nenhuma | idem | 0 → 2 | idem | idem |
| Água quente (proposta) | idem | aquecedor alimentado por AF, sistemas separados | idem | nenhuma | idem | 0 → 2 | idem | idem; PVC de água fria na quente → não atendida |
| Esgoto e ventilação (proposta) | idem | aparelho, caixa de inspeção, coletor, gravidade × pressurizado | declividade 2 %/1 %, contrapendência, DN a jusante, ventilação presente | nenhuma | idem | 0 → 2,5 | idem | idem |
| Cabos estruturais | inexistente | — | — | — | — | 0 → 0 | lacuna | registrada; contraventamento diz "Não é cabo" |
| Lançamento e cargas (RF04) | completo | pilar (girado, circular), viga, laje, contraventamento, carga pontual **com posição** | — | 2D e 3D | `ReferenciaEstruturalTest`, j5 | 6,5 → 8,5 | carga pontual sem posição ia para o apoio | posição obrigatória em viga (422 explicando); sem posição em montagem antiga → aviso "meio do vão" |
| Análise estrutural (RF05) | completo | barras com eixos locais, nós por posição de carga, treliça | MEF linear 1ª ordem, Euler-Bernoulli exato | diagrama por barra, deformada, tabela | 13 casos de referência + j2 + j5 | **3 (falha crítica)** → 8 | pilar com inércia trocada (δ 3,14 mm em vez de 12,58 mm); laje 36 kN em vez de 40 kN na viga longa; contraventamento ignorado | eixos locais corrigidos; charneiras a 45°; treliça; avisos; equilíbrio obrigatório; metadados (natureza CALCULADO, método, hipóteses, unidades, convenção) |
| Dimensionamento (RF07) | completo | por elemento, com material e base | NBR 6118:2023 (seção 3 deste relatório) | tabela com estado por elemento e verificações | `DimensionamentoReferenciaTest` (14), `DimensionamentoIT` (4), j5 | **4 (falha crítica)** → 7,5 | só As inferior em viga; pilar sem momento; laje com carga fictícia; "verifica" sem dizer o que não foi verificado | As+/As−; flexo-compressão oblíqua; Grashof com carga real; 7 estados padronizados; o que não é verificado aparece como tal |
| Detalhamento de armaduras (RF07/RF11) | completo | posições N1…Nn por elemento | lb, traspasse, ganchos, comprimentos comerciais de 12 m | quadro de armaduras; barras no 3D | idem | 2 → 7 | só massa total; nada de ancoragem, emenda, posição | detalhamento por posição com regra explícita; massa = Σ posições |
| Quantitativos (RF11) | completo | pilar circular e contraventamento | área π·d²/4 | tabela | `QuantificadorTest` | 7,5 → 8 | pilar circular quantificado como retangular | corrigido |
| Coordenação (proposta P-COORD-01) | inexistente → completo | 6 regras sobre o BIM atual | geometria determinística | Painel de falhas | `DetectorConflitosTest`, `CoordenacaoMateriaisIT`, j5 | 0 → 6,5 | sem detecção nenhuma | estados com reverificação, sem furo automático |
| Visualização 3D (RF13) | completo | todos os elementos estruturais | — | 3D | j5 (`j5-06`) | 5 → 7,5 | vigas e lajes desenhadas no pavimento de cima (diferente da análise); contraventamento como linha; pilar girado só no 3D | convenção de nível única; contraventamento sólido; rotação no 2D; raio X; corte horizontal; armaduras do dimensionamento (polilinhas, não textura) |
| Resultados (RF05/RF07) | completo | — | — | diagrama por grandeza, combinação, barra | j5 (`j5-03`, `j5-04`) | 4 → 8 | só barras de M máx sem unidade, convenção ou estado | grandeza e unidade, combinação, convenção, escala, extremos, versão, estado, deformada amplificada ×N |
| Painel de falhas (novo) | inexistente → completo | agrega base, avisos, verificações, alertas RF17, rascunhos de IA, conflitos | — | Obra › Painel de falhas | j5 (`j5-05`) | 0 → 7 | — | origem (regra, IA, decisão humana), estado, regra, evidência, impacto, ação; lista fixa do que **não** é verificado |

### 3.1 Defeitos introduzidos no projeto integrado j5 e o que o sistema fez

| Defeito introduzido | Esperado | Obtido | Estado |
|---|---|---|---|
| Base de cálculo sem CAA, finalidade, sistema e agregado | listar o que falta; cobrimento em "dados insuficientes" | 4 itens listados; elementos em `DADOS_INSUFICIENTES` | ✔ |
| Carga pontual em viga sem posição | 422 com a faixa válida | "Informe a posição… (0 a 5.00 m)" | ✔ |
| Viga de 1,00 m sobre porta de 2,10 m (pé-direito 3 m) | conflito construtivo | CON-02 com "fundo z = 2.00; topo z = 2.10" | ✔ |
| Viga com ponta solta | conflito de conectividade e aviso na análise | CON LIG-01 ✔; aviso na análise ✖ na 1ª execução → **corrigido** (seção 4.6) | ✔ após correção |
| Marcar conflito como resolvido à mão | recusar | 422 | ✔ |
| Corrigir o modelo (pilar sob a ponta) e reverificar | LIG-01 resolvido na versão nova | RESOLVIDO, versão 15 = modelo 15 | ✔ |
| Mudar o modelo depois do dimensionamento | resultado desatualizado, não recalculado | `OBSOLETO` | ✔ |

### 3.2 Casos de referência independentes (tolerância por grandeza)

| Caso | Valor de referência (fórmula) | Tolerância | Resultado |
|---|---|---|---|
| Pilar 20×40 em balanço, F = 10 kN no topo, H = 3 m | δ = F·H³/(3·E·I), I da direção carregada | 1 % | ✔ (antes: 3,14 mm em vez de 12,58 mm na direção fraca) |
| Laje 4 × 6 m, 5 kN/m², bordas apoiadas | charneiras a 45°: carga por viga = área do trapézio/triângulo × q | 0,5 kN | ✔ (antes: 36 kN em vez de 40 kN na viga longa) |
| Carga de 30 kN a 1,5 m do apoio | salto do cortante = 30 kN na posição | 0,05 kN | ✔ (antes: carga no apoio) |
| Balanço com carga na ponta | M = −F·L; δ = F·L³/(3EI) | 1e-3 | ✔ |
| Carga triangular em viga biapoiada | R = wL/6 e wL/3; Mmáx = wL²/(9√3); δ = 0,00652·wL⁴/EI | 1e-3 / 0,5 % | ✔ |
| Viga biengastada uniforme | M = −wL²/12 (apoio), wL²/24 (vão); δ = wL⁴/(384EI) | 1e-3 | ✔ |
| Treliça | N = −F/(2·senθ) por estática | 1e-3 | ✔ |
| Pórtico 2 pilares + viga, resolvido à mão | Md⁺ = 85,52 − 18,59 = 66,93 kN·m; Vd = 46,9 kN; Nd = 80,5 kN | 0,05 | ✔ |
| Seção 20×40 de concreto C30 com 4 φ 16, aço a 2 ‰ (420 MPa) | NRd,máx = 0,85·fcd·Ac + As·σs(2 ‰) = 1794,9 kN | 0,5 kN | ✔ |
| Ancoragem φ 12,5, boa aderência, C30, CA-50 | lb = (φ/4)·(fyd/fbd) = 0,417 m; má aderência ÷ 0,7 | 0,002 m | ✔ |
| Equilíbrio global de toda análise | Σ cargas = Σ reações | resíduo relativo ≤ 1e-6 | ✔ (j5: 5 combinações) |
| Ecs do C30, agregado granito | αi·αE·5600·√fck = 0,875·1,0·5600·√30 = 26 838,4 MPa | 1 MPa | ✔ |
| Circuito 2 500 W, 127 V, 25 m, 2,5 mm² | Ib = 19,69 A; ΔV = 6,77 V (5,33 %) | 0,01 | ✔ |

Concordância entre duas respostas da mesma IA não foi usada como validação em nenhum caso.

---

## 4. Defeitos reproduzíveis corrigidos (antes × depois)

| # | Defeito | Como reproduzir (antes) | Depois | Teste |
|---|---|---|---|---|
| 4.1 | Inércia do pilar trocada entre X e Y | pilar 20×40 com força em X | δ correto nas duas direções; pilar girado e circular | `ReferenciaEstruturalTest` |
| 4.2 | Laje repartida por comprimento de viga | laje 4×6 | charneiras a 45° (NBR 6118:2023, 14.7.6.1) quando todas as bordas apoiam | idem |
| 4.3 | Carga pontual aplicada no apoio | carga em viga sem nó intermediário | nó criado na posição; posição obrigatória na API | idem, j5 |
| 4.4 | Viga e laje desenhadas no pavimento de cima no 3D | comparar cena com análise | face superior na elevação do pavimento, como na análise | j5 `j5-06` |
| 4.5 | Sanitização trocava "." por "_" nos códigos de material | `VI(PILAR, mat=MAT.CONCRETO.C40)` pela API → auditor recusa `MAT_CONCRETO_C40` | `Sanitizador.codigo` preserva o ponto | `CoordenacaoMateriaisIT` |
| 4.6 | Viga com ponta livre analisada como balanço sem aviso | j5 (falha registrada na 1ª execução) | aviso de premissa "extremidade livre… analisada como balanço" | `ReferenciaEstruturalTest.extremidadeLivreDeVigaViraAviso` |
| 4.7 | Material personalizado não podia ser aplicado (auditor só conhecia o catálogo) | criar material e aplicá-lo a pilar → 422 | auditor aceita personalizado não rejeitado da empresa ativa; o editor também | `CoordenacaoMateriaisIT` |
| 4.8 | IA podia propor APAGAR/ALTERAR fora do rascunho | resposta da IA com `APAGAR` | só comandos de criação; lote inteiro recusado com explicação | `CaminhoIaIT` |
| 4.9 | Correção automática podia trocar o tipo do comando | correção com outro `codigo` | correção descartada; comando segue reprovado | revisão de código |
| 4.10 | Chamadas de IA do texto natural não iam para o registro de uso | comparar `chamada_ia` antes/depois | passam por `ServicoIa` (registro, recusa, truncamento) | `CaminhoIaIT` |
| 4.11 | Falha do provedor de IA virava 500 | provedor fora do ar | 503 `ia_indisponivel`, nada alterado | `CaminhoIaIT` |
| 4.12 | Formulário de ações (RF29) filtrava materiais pela categoria antiga | parede com material padrão | filtro pela aplicação do catálogo | `acoes.test.ts` |
| 4.13 | Tela de dimensionamento dizia "perfis NBR 8800" | abrir a tela | texto diz que NBR 8800 não é verificada | j5 |
| 4.14 | Catálogo dizia "CA-50 Ø 5,0 mm" | listar bitolas | CA-50 a partir de 6,3 mm; 5,0 mm é fio CA-60 (NBR 7480:2022 segundo DER-PR) | `DimensionamentoReferenciaTest` |
| 4.15 | IFC exportava "FireRating" do material | exportar IFC | removido; exporta versão do catálogo | `EscritorIfcTest` |
| 4.16 | Tubos e condutores marcados como "usados no cálculo" | catálogo | "só representação" + menção ao verificador experimental | revisão |
| 4.17 | Todos os pilares apareciam como "P-01a174" nos resultados (prefixo de UUID v7 igual) | j5, tela de análise | rótulo automático igual ao do BIM (P1, V5…) | j5 (`j5-03`, aviso "V5: extremidade livre…") |

---

## 5. B · Materiais e componentes

### 5.1 Distinções aplicadas

| Conceito | No software | Exemplo |
|---|---|---|
| Material | item do catálogo com família e propriedades | `MAT.CONCRETO.C30` |
| Produto comercial | fabricante + referência + documento, obrigatório para valor declarado ou de ensaio | material personalizado com ficha técnica |
| Composição | camadas de materiais | `COMP.PAREDE.CERAMICO_14_REVESTIDA` |
| Elemento | onde o material está aplicado (modelo BIM) | pilar P3 |
| Serviço | como é orçado (unidade, código SINAPI herdado, marcado como não conferido) | `SRV.CONCRETO.C30` |
| Configuração de armadura | separada do material: bitola, quantidade, espaçamento, camadas, comprimentos | posição N2 = 25 φ 10 |

### 5.2 Variações por família e suporte

| Família | Variações no catálogo 2026.10.1 | Suporte | Nota antes → depois |
|---|---|---|---|
| Cimento | CP II-E-32, CP II-F-32, CP III-40, CP IV-32, CP V-ARI (NBR 16697:2018) | só representação (não entra no cálculo do concreto) | 2 → 6 |
| Concreto | C20, C25, C30, C35, C40, C45, C50 (NBR 8953:2015, grupo I) | **cálculo** (fck; Eci e Ecs estimados pelo agregado da obra) | 5 → 8,5 |
| Argamassa | assentamento, revestimento (NBR 13281-1/-2:2023) | quantitativo | 2 → 5,5 |
| Aço para armaduras | CA-50 (6,3 a 32 mm; 32 mm "a conferir"), CA-60 (5,0 mm) | **cálculo** | 4 → 8 |
| Barras e conjuntos | posições do detalhamento | **detalhamento** | 2 → 7 |
| Telas soldadas | (NBR 7481:2022) | **não implementado** (declarado) | 0 → 2 |
| Aço estrutural | ASTM A36, A572 Gr 50 (edições não verificadas) | análise (treliça); **NBR 8800 não verificada** | 3 → 5 |
| Alvenaria | bloco cerâmico de vedação 14, bloco de concreto estrutural 19 (NBR 15270-1:2023, NBR 6136-1:2026) | quantitativo; fogo e U removidos do material | 3 → 6,5 |
| Revestimentos | só via composição | quantitativo | 1 → 3 |
| Tubos e condutores | PVC água fria, CPVC água quente, PVC esgoto, cobre/PVC 70 °C | só representação; verificador experimental | 1 → 3 |

### 5.3 Fluxo de descoberta testado

Selecionar elemento → *Trocar material…* (painel de propriedades do editor) → pesquisar (nome, código, família, código
legado) → comparar até 3 lado a lado (diferenças em negrito, com origem de cada valor) → ver propriedades essenciais
primeiro e todas com unidade, origem, método e referência com edição → escolher escopo (só este elemento ou todos do
tipo no pavimento) → **prévia** com quantos elementos mudam e aviso de que análises e dimensionamentos ficam
desatualizados → aplicar (VI ALTERAR auditado) → **desfazer**. Tela de catálogo: Projeto › Materiais.

### 5.4 Testes de materiais

| Caso | Resultado |
|---|---|
| Opções parecidas (C25 × C30 × C35) | comparação lado a lado com diferenças marcadas |
| Produto sem documentação com valor "declarado pelo fabricante" | 422 "exige documentação: fabricante, referência e documento" |
| Material personalizado | criado em *validação pendente*; não entra no cálculo até validação por selo verificado (*Dados insuficientes*) |
| Unidade errada (fck em kPa) | 422 "Unidade incorreta para … use MPa" |
| Referência desatualizada | NBR 6136:2016 marcada **CANCELADA** e substituída pela 6136-1:2026; NBR 5626:1998 cancelada |
| Mudança depois do cálculo | resultado vira *Resultado desatualizado*; valores emitidos inalterados (`DimensionamentoIT`) |
| Excluir material em uso | 409 com obra e elemento |
| Importar e reabrir | códigos legados (`MAT_CONC_FCK30`) resolvem para o canônico nos dois motores (paridade) |

A IA recebe a lista de códigos do catálogo como contexto e não grava material: o auditor recusa código inexistente.

---

## 6. Estados padronizados

| Estado interno | Texto na tela | Onde aparece |
|---|---|---|
| `NAO_VERIFICADA` | Não verificado | verificações, painel de falhas |
| `EM_PROCESSAMENTO` | Em processamento | jobs |
| `ATENDIDA` | Verificação atendida | verificações |
| `NAO_ATENDIDA` | Verificação não atendida | verificações, painel |
| `DADOS_INSUFICIENTES` | Dados insuficientes | verificações, base de cálculo |
| `DESATUALIZADA` / `OBSOLETO` | Resultado desatualizado | análises, dimensionamentos, painel |
| `NAO_APLICAVEL` | Não aplicável | verificações |

O estado do elemento é a consolidação das verificações: qualquer não atendida → não atendida; senão qualquer dado
insuficiente → dados insuficientes; senão qualquer não verificada → não verificado. A tela nunca diz "sem falhas":
quando nada foi registrado, diz que isso não significa projeto sem falhas e lista o que o software não verifica.

---

## 7. C · Conformidade técnica e normativa

### 7.1 Natureza das exigências

| Tipo | No software |
|---|---|
| Legislação | LGPD (RF24–RF26, auditoria anterior); responsabilidade técnica (ART/RRT, selo CREA/CAU) na aprovação |
| Normas técnicas ABNT | seção 7.2 — edição registrada por referência; motores declaram a edição que seguem |
| Exigências locais (código de obras, prevenção de incêndio) | **não implementadas**; município e UF só identificam a obra |
| Exigências contratuais | fora do escopo do software |
| Normas internacionais | ASTM A36/A572 como complemento de produto, sem edição verificada |
| Boas práticas | marcadas como tal (ex.: DN não diminui a jusante) |

### 7.2 Matriz de aplicabilidade normativa

Fontes: as URLs e datas de consulta estão em `packages/notacao-vi/catalogo/materiais.json` (campo `fonte`). Todas são
fontes secundárias, consultadas em 2026-10-25.

| Módulo / RF | Referência e edição | Requisito | Aplicabilidade | Implementação | Evidência | Situação | Correção / pendência |
|---|---|---|---|---|---|---|---|
| RF05 análise | ABNT NBR 6118:2023 (+ errata 01/2024; nova edição 03/2026 citada por terceiros, não confirmada) | análise linear; repartição de lajes por charneiras (14.7.6.1) | aplicável | pórtico espacial 1ª ordem; charneiras em laje retangular apoiada | `ReferenciaEstruturalTest` | **Parcial** | 2ª ordem global e lajes como placa: não implementadas |
| RF07 vigas | NBR 6118:2023 | ELU flexão (17.2), cisalhamento modelo I (17.4), estribos | aplicável | sim | `DimensionamentoReferenciaTest` | **Parcial** | torção: não verificado |
| RF07 pilares | NBR 6118:2023 | flexo-compressão, e1,mín, pilar-padrão (λ ≤ 90), φt ≥ máx(5 mm; φl/4) | aplicável | sim; λ > 90 não verificado | idem | **Parcial** | pilar esbelto |
| RF07 lajes | NBR 6118:2023 | flexão, VRd1, φ ≤ h/8, s ≤ mín(2h; 20 cm), armadura secundária | aplicável | sim | idem | **Parcial** | punção, continuidade |
| RF07 ELS | NBR 6118:2023 (17.3.2, tabela 13.3) | abertura de fissuras e flecha | aplicável | wk com limite por CAA; flecha total f∞ ≤ L/250 (lajes por faixa, aproximação declarada) | idem | **Parcial** | lajes como placa; limites de outras situações da tabela 13.3 |
| RF07 durabilidade | NBR 6118:2023 tabelas 7.1/7.2 | classe mínima e cobrimento por CAA | aplicável | sim; sem CAA → dados insuficientes | `DimensionamentoIT`, j5 | **Atendido** (para o que declara) | — |
| RF07 detalhamento | NBR 6118:2023 seção 9 | ancoragem, traspasse, ganchos | aplicável | lb, lb,mín, α0t = 2,0 (premissa declarada) | idem | **Parcial** | proporção de barras emendadas não analisada |
| RF04 cargas | ABNT NBR 6120:2019 | cargas mínimas de uso | aplicável | usuário informa; software **não** compara com a tabela | — | **Não verificado** | conferência contra tabela |
| RF04 combinações | ABNT NBR 8681:2003 | γ e ψ das combinações | aplicável | gerador ELU/ELS | `GeradorCombinacoesTest` | **Parcial** | coeficientes não conferidos no texto oficial |
| RF04 vento | ABNT NBR 6123:2023 | força de vento | aplicável | só +X com força informada | aviso na análise | **Parcial** | outras direções; mapa de isopletas não registrado |
| Materiais concreto | ABNT NBR 8953:2015 | classes C20–C50 (grupo I) | aplicável | sim | catálogo | **Atendido** (classificação) | grupo II não cadastrado |
| Materiais aço | ABNT NBR 7480:2022 (2024 citada pela Caixa, não confirmada) | categorias e bitolas | aplicável | CA-50 ≥ 6,3 mm; CA-60 5,0 mm | `DimensionamentoReferenciaTest` | **Parcial** | 32 mm "a conferir" |
| Materiais cimento | ABNT NBR 16697:2018 | tipos e classes | informativo | representação | catálogo | **Não aplicável** ao cálculo | — |
| Alvenaria | NBR 15270-1:2023; NBR 6136-1:2026 | requisitos de produto | informativo | representação e quantitativo | catálogo | **Não aplicável** ao cálculo | alvenaria estrutural não implementada |
| Aço estrutural | ABNT NBR 8800:2024 | tração, compressão, ligações | aplicável a contraventamento | **não** | verificações `NBR8800-*` em não verificado | **Não atendido** | implementar ou retirar o elemento |
| Execução | NBR 12655:2022; NBR 14931:2023 | controle e execução | fora do escopo | — | — | **Não aplicável** | — |
| Instalações elétricas | ABNT NBR 5410:2004 (revisão em consulta) | proteção, capacidade de condução, queda de tensão | aplicável (proposta) | verificador experimental, fora do modelo | `VerificadorRedesTest` | **Não verificado** no produto | P-MEP-01 |
| Água fria e quente | ABNT NBR 5626:2020 | vazão, pressão, perda de carga | aplicável (proposta) | **não** (método não consultado) | — | **Não verificado** | conferir método no texto oficial |
| Esgoto e ventilação | ABNT NBR 8160:1999 | declividade, ventilação, inspeção | aplicável (proposta) | declividade e ventilação presente (experimental) | `VerificadorRedesTest` | **Não verificado** no produto | P-MEP-01 |

### 7.3 Proveniência dos valores até o motor

`materiais.json` (fonte única, versão `2026.10.1`) → `CatalogoMateriais` (Java) / `tabela-a.ts` (TS) → base de cálculo
da obra (agregado → αE) → `AnaliseService.modulos` (Ecs) e `DimensionamentoService` (fck, fyk) → resultado com
**snapshot** da base (`baseCalculoJson`: finalidade, sistema, CAA, agregado, versão do catálogo, referências com
edição, local, o que não foi informado). Uma nova versão do catálogo não altera resultados emitidos: muda o aviso na
base de cálculo e os novos cálculos.

### 7.4 Caminho da IA (solicitação → dados → validação → cálculo → explicação → revisão)

| Etapa | Controle | Teste |
|---|---|---|
| Solicitação | texto sanitizado; contexto do modelo (pavimentos, eixos, códigos de material) na entrada | `CaminhoIaIT` |
| Provedor falha | 503 `ia_indisponivel`, nada gravado | `CaminhoIaIT` |
| Resposta inconsistente | campos obrigatórios, `args` objeto; senão 422 sem efeito | `CaminhoIaIT` |
| Comando não permitido | só criação (PAREDE, PILAR, VIGA, LAJE, PORTA, JANELA…); APAGAR/ALTERAR/MOVER recusam o lote | `CaminhoIaIT` |
| Validação | auditor determinístico (laços 1 e 2); correção pela IA não pode trocar o tipo do comando | `EstresseAuditoriaTest` |
| Registro | chamada em `chamada_ia` (rota, finalidade, base legal, modelo, tokens) | `CaminhoIaIT` |
| Rascunho e versão | rascunho com `versaoModeloBase`, `versaoModeloResultado` e `chamadaIaId`; elementos na camada de rascunho até aprovação | `CaminhoIaIT` |
| Revisão | aprovação por profissional (RF27) | `RascunhoIaIT` |

Não verificado: comportamento com o modelo real do provedor (em desenvolvimento a IA é simulada).

### 7.5 Classificação dos requisitos pedidos

| Situação | Quantidade | Exemplos |
|---|---:|---|
| Atendido | 21 | estados padronizados; base gravada em cada resultado; não recalcular em silêncio; equilíbrio; eixos locais; carga na posição; As+/As−; detalhamento com número/comprimento/massa separados; diagramas com unidade e convenção; deformada amplificada; resolução só por reverificação; sem furo automático; "esforços calculados, não medidos"; catálogo único; origem dos valores; personalizado com validação; exclusão em uso; IA não inventa material; falha de provedor; comando não permitido; vínculo com versão |
| Parcial | 13 | análise (sem 2ª ordem); pilares (λ ≤ 90); lajes (só retangulares apoiadas); ELS; detalhamento (proporção de emendas); combinações; vento; aço CA-50 32 mm; 3D (sem filtro por sistema MEP); coordenação (sem folga e funcional); painel de falhas (sem conflitos MEP); referências (fontes secundárias); seleção de material (personalizado só aparece após recarregar o editor) |
| Não atendido | 3 | instalações no modelo BIM; verificação NBR 8800 de contraventamento; cabos |
| Não verificado | 5 | NBR 5626 (método); NBR 5410 (Iz); cargas mínimas NBR 6120; IA real em produção; códigos SINAPI herdados |
| Não aplicável | 3 | execução (NBR 12655/14931); cimento no cálculo; exigências contratuais |

### 7.6 Prioridades

1. **Crítico:** contraventamento entra na análise sem verificação NBR 8800 — já sinalizado como *Não verificado*;
   decidir entre implementar a verificação ou impedir a aprovação com contraventamento.
2. Conferir no texto oficial da ABNT as edições e cláusulas usadas (NBR 6118:2023 e a possível edição 03/2026,
   NBR 8681, NBR 6123:2023) e registrar a conferência no catálogo.
3. Efeitos de 2ª ordem globais (γz) antes de liberar edifícios com mais de poucos pavimentos.
4. Instalações no modelo BIM (P-MEP-01, itens 1–6).
5. Folgas e conflitos funcionais na coordenação.

---

## 8. Documentação e responsabilidade

- Resultado de análise e de dimensionamento é **rascunho** até aprovação com ART/RRT por profissional com selo
  verificado e 2FA (RF07/RF21). A aprovação é recusada quando o resultado está desatualizado.
- Cada resultado guarda: versão do modelo, base de cálculo, versão do catálogo, método, hipóteses, unidades,
  convenção, avisos de exclusão, aproximação e premissa.
- Documentos alterados nesta rodada: RF01, RF02, RF04, RF05, RF07, RF11, RF13, RF15, RF16, RF22, RF27, RF28 (seção
  "Auditoria 2026-10 · 2ª rodada") e [proposta P-MEP-01/P-COORD-01](../propostas/P-MEP-01-instalacoes-e-coordenacao.md).

## 9. Capturas (j5)

| Tela | Arquivo |
|---|---|
| Base de cálculo e referências | `evidencias/telas/j5-01-base-calculo.png` |
| Catálogo de materiais | `evidencias/telas/j5-02-materiais.png` |
| Resultados da análise | `evidencias/telas/j5-03-analise-resultados.png` |
| Dimensionamento com verificações e quadro de armaduras | `evidencias/telas/j5-04-dimensionamento-verificacoes.png` |
| Painel de falhas e conflitos | `evidencias/telas/j5-05-painel-falhas.png` |
| 3D com raio X e armaduras | `evidencias/telas/j5-06-3d-armaduras-raio-x.png` |

## 10. Arquivos principais alterados

Backend: `estrutura/mef/*` (montador, solver, modelo analítico), `estrutura/dimensionamento/*` (dimensionador,
seção, detalhamento, bitolas, verificação), `notacao/CatalogoMateriais`, `notacao/TabelaA`, `notacao/Sanitizador`,
`obra/BaseCalculo*`, `material/*`, `coordenacao/*`, `instalacoes/VerificadorRedes`, `ia/ServicoIa`,
`modelo/ModeloService`, `modelo/AuditorService`, `modelo/Auditor`, migrações V21–V23.
Front: `components/obra/resultados-estrutura.tsx`, `components/motor/{Cena3D,Canvas2D,SeletorMaterial,Paineis}.tsx`,
páginas `base-calculo`, `materiais`, `falhas`, `analises/[aba]`, `lib/{acoes,dados,navegacao,motor/estado}.ts`.
Pacote: `packages/notacao-vi/catalogo/materiais.json`, `src/tabela-a.ts`.
Testes novos: `ReferenciaEstruturalTest` (13), `DimensionamentoReferenciaTest` (14), `DetectorConflitosTest` (4),
`VerificadorRedesTest` (5), `CoordenacaoMateriaisIT` (3), `CaminhoIaIT` (4), `DimensionamentoIT` (+1),
jornada `j5-integrado.mjs`.
