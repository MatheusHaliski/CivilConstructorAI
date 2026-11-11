# Leitura integral dos memoriais do Grupo C

Data da análise: 10/11/2026. Fontes: dois DOCX fornecidos em `/workspace/referencias/normas`. A análise abaixo se refere ao conteúdo efetivamente contido nesses arquivos. São trabalhos acadêmicos de 2020, de pré-dimensionamento e cálculo de esforços, e não os textos das normas ABNT citadas.

**Conclusão:** foi possível ler todo o texto técnico incorporado aos dois arquivos e inspecionar todas as suas imagens únicas. Os documentos não contêm os resultados numéricos necessários para reproduzir os esforços finais nem verificar a segurança dos elementos: remetem esses dados a planilhas EXCEL e desenhos DWG externos. Há uma explicação tecnicamente invertida sobre a direção de trabalho das lajes unidirecionais, considerando a definição usual de razão entre vãos. A leitura destes memoriais não constitui consulta integral das normas nem comprova conformidade normativa atual.

## 1. Identificação e integridade das fontes

| ID | Arquivo | Tamanho | SHA256 |
|---|---|---:|---|
| D1 | `Copy of MEMORIAL DE CÁLCULO DOS ESFORÇOS - GRUPO C.docx` | 223.038 bytes | `af787a16cff182f883104de5041663e239b1b53be537ae393da544b1525ca4df` |
| D2 | `Copy of MEMORIAL DE CÁLCULO GRUPO C.docx` | 257.625 bytes | `512d0c9d9012447ac50438a0b5080987e86d49bb30e13d944a2b5e1d5552a60c` |

Ambos se identificam como trabalhos da Universidade Positivo, Engenharia Civil, Curitiba, 2020. A obra acadêmica de referência é denominada “condomínio edifício vertical -192 unidades”. D1 informa modificação em 15/04/2020 e D2 em 25/03/2020 nos metadados internos; esses metadados não autenticam autoria ou aprovação profissional.

## 2. Método e cobertura efetiva da leitura

Os pacotes ZIP dos DOCX foram abertos com `zipfile`, e os componentes XML foram analisados com `xml.etree.ElementTree`. Foram extraídos os nós de texto Word `w:t`, pesquisadas equações `m:oMath`/`m:t`, tabelas `w:tbl`, desenhos e suas relações internas. Nenhum conteúdo executável, macro ou instrução do documento foi executado. As menções a EXCEL/DWG foram tratadas como referências documentais, sem presumir que seu conteúdo estivesse incorporado.

| Conteúdo inspecionado | D1 | D2 |
|---|---:|---:|
| Partes XML analisadas, incluindo propriedades e formatação | 10 | 10 |
| Parágrafos `w:p` em `word/document.xml` | 229 | 478 |
| Nós `w:t` em `word/document.xml` | 221 | 348 |
| Caracteres nesses nós, antes de eliminar repetições de apresentação | 5.676 | 10.515 |
| Tabelas de conteúdo `w:tbl` em `word/document.xml` | 0 | 0 |
| Equações OMML `m:oMath` e textos matemáticos `m:t` | 0 | 0 |
| Imagens incorporadas | 2 | 3 |
| Páginas declaradas em `docProps/app.xml` | 6 | 12 |

Há textos repetidos nos cabeçalhos desenhados e nas representações alternativas DrawingML/VML. A contagem XML não corresponde à quantidade de parágrafos visualmente distintos; a repetição não foi interpretada como informação técnica adicional. Há estilos de tabela em `word/styles.xml`, mas não tabelas de cálculo no corpo dos documentos. Não há partes de cabeçalho/rodapé independentes, objetos EXCEL incorporados ou anexos DWG nestes pacotes.

Todos os textos técnicos extraídos foram lidos. Os três conteúdos visuais únicos foram abertos e inspecionados:

| Arquivo interno e localização | Conteúdo e legibilidade |
|---|---|
| D1 `word/media/image1.png` e `image10.png`; D2 `image1.png` | Logotipo da universidade. Os três arquivos são idênticos, SHA256 `15de102ec214964a485303bbee65f3b125168b29f2efe8b5306c31f75dc72c99`. Não contêm dados de cálculo. |
| D2 `word/media/image2.png`, relação `rId6`, P0317 | Captura parcial de desenho CAD, 671 × 429 pixels; percentuais 45%, 55%, 50% e 100% legíveis. Sem cotas métricas, nomes dos pilares ou áreas numéricas. SHA256 `ea2bfe312a591a19516e95ae61500ee90dc8c0fbe54b21aee4ac5f03e2992cd7`. |
| D2 `word/media/image3.png`, relação `rId7`, P0358 | Outra captura parcial de CAD, 763 × 483 pixels, com os mesmos tipos de percentuais. Não apresenta os dados ausentes da planilha ou o dimensionamento dos pilares. SHA256 `e87479e9bca713c32abf4b5c1f6282046e1afe3067bbc1a2fe0a7dd60caf5c2b`. |

Os desenhos são recortados e não têm escala/cotas utilizáveis; inferir dimensões por pixels produziria falsa precisão. Não houve equação ilegível encontrada: as equações numéricas de dimensionamento simplesmente não estão incorporadas. A numeração de páginas abaixo é a impressa no próprio documento. O localizador `Pnnnn` significa o índice, começando em 1, na sequência `.//w:p` de `word/document.xml`, incluindo parágrafos em caixas de texto e suas alternativas; permite reencontrar o trecho sem depender da paginação do editor.

## 3. Estrutura e conteúdo efetivamente lido

### D1 — Memorial de esforços nas lajes

| Localizador / página impressa | Conteúdo |
|---|---|
| P0001–P0017 / capa | Instituição, autores, objeto e data. |
| P0056–P0062 / 2 | Índice. Anuncia desenho CAD, sem incorporar a planta de momentos. |
| P0103–P0106 / 3 | Objetivo: momentos fletores nas lajes; L4 e L11 unidirecionais; L1, L2, L3, L5–L10, L12–L14 bidirecionais. |
| P0143–P0148 e P0185–P0188 / 4–5 | Referências arquitetônicas, normas e livro de Mecânica dos Materiais. |
| P0190–P0191 e P0227 / 5–6 | L4/L11 modeladas como vigas com dois extremos engastados; integração com condições de contorno; menção a tabelas/fórmulas de Czerny para lajes bidirecionais. Resultados remetidos à página 3 de EXCEL e à planta DWG. |

D1 não fornece dimensões das lajes, espessuras, cargas numéricas, coeficientes de tabelas, momentos obtidos, sinais, unidades, transferência/compatibilização entre painéis, armaduras ou verificações de resistência/serviço.

### D2 — Memorial de pré-dimensionamento

| Localizador / página impressa | Conteúdo |
|---|---|
| P0001–P0017 / capa | Instituição, autores, objeto e data. |
| P0056–P0065 / 2 | Índice. |
| P0106–P0107 / 3 | Objetivo: pré-dimensionar superestrutura, escolher materiais e estabelecer ELU/ELS. |
| P0145–P0151 e P0187–P0188 / 4–5 | Referências. |
| P0190–P0193 / 5 | Desenho de fôrmas em AutoCAD; cálculo em planilha EXCEL de três páginas. |
| P0231–P0235 / 6 | Materiais, exposição urbana, concreto, aço, cobrimento; propriedades remetidas à página 3 do EXCEL. |
| P0273–P0274 / 7 | Pilares por áreas de influência; carga estimada de 10 kN/m² em cada um de quatro pavimentos; cálculo remetido à página 1 do EXCEL. |
| P0317 e P0358 / 8–9 | Duas capturas parciais com percentuais de distribuição de áreas. |
| P0397–P0399 / 10 | Categorias de vigas, largura de 160,40 mm, estimativas de alturas na página 2 do EXCEL; expressão de altura da laje. |
| P0436–P0437 / 11 | Classificação das lajes pela razão λ. |
| P0438–P0439 e P0475 / 11–12 | Cargas e combinações ELU/ELS remetidas ao EXCEL; valores e tipos de borda remetidos ao DWG. |

## 4. Normas e hipóteses identificadas

As duas fontes citam as mesmas referências abaixo. A coluna “edição” reproduz a bibliografia do memorial; não certifica que a edição exista ou seja vigente:

| Referência como citada | Edição declarada | D1 | D2 |
|---|---:|---|---|
| ABNT NBR 6118 — Projeto de estruturas de concreto — Procedimento | 2014 | P0147 | P0149 |
| ABNT NBR 6120 — Cargas para cálculo de estruturas de edificações — Procedimento | 2014 | P0148 | P0150 |
| ABNT NBR 12655 — Concreto de cimento Portland — Preparo, controle e recebimento — Procedimento | 2006 | P0185 | P0151 |
| ABNT NBR 7480 — Aço destinado a armaduras para estruturas de concreto armado — Especificação | 2007 | P0186 | P0187 |

Nenhum texto integral dessas normas está reproduzido. Não há indicação de cláusula para a maioria dos procedimentos. A referência “NBR 6120:2014” requer correção/verificação bibliográfica no catálogo oficial; a simples presença do ano no memorial não o valida. Os documentos não identificam edição da NBR 8681, nem norma de vento, execução, ensaios, incêndio ou fundações. A ausência de citação não prova que o autor não as tenha consultado, mas impede rastrear sua aplicação a partir destes arquivos.

Hipóteses explícitas de D2:

- Concreto com `fck = 30 MPa`; relação água/cimento `a/c ≤ 0,60`; justificativa de localização urbana e referência a mínimo de 25 MPa (P0232).
- Aço CA50, diâmetro mencionado de 10 mm (P0233), sem quantitativo e disposição de barras.
- Cobrimento mínimo de lajes mencionado como 25 mm; cobrimento adotado para “vigas e lajes” de 30 mm (P0234). Não há cobrimento nominal declarado para pilares nesse trecho.
- Quatro pavimentos, carga estimada uniforme de 10 kN/m² por pavimento para pré-dimensionar pilares (P0274). Não se informa se esse valor contém majoração ou decomposição permanente/variável.
- Pré-dimensionamento de pilares por áreas de influência; de vigas por categoria de apoio; de lajes por bordas apoiadas/engastadas (P0274, P0397–P0399).
- D1 assume dois engastes nas lajes unidirecionais L4/L11 (P0191). Nenhum dos documentos fornece rigidezes, rotação de apoios ou compatibilidade que demonstrem essa condição.

## 5. Reprodução independente dos números disponíveis

Os resultados abaixo foram calculados por aritmética independente, sem ler fórmulas de uma planilha externa e sem utilizar o motor do CivilConstructorAI. Verificam apenas consequências dos dados presentes; não são esforços finais ou aprovação estrutural.

| Verificação | Cálculo reproduzido | Alcance |
|---|---|---|
| Acumulação das cargas estimadas de quatro pavimentos | `4 × 10 = 40 kN/m²` | Para a mesma área de influência `A` em todos os níveis, a parcela estimada seria `N = 40 A kN`, com `A` em m². Faltam `A`, pesos próprios discriminados e coeficientes para obter `Nsd`. |
| Área geométrica de uma barra Ø10 | `π × 10² / 4 = 78,5398 mm² = 0,785398 cm²` | Não identifica área de aço adotada/necessária: faltam quantidade, espaçamento e camadas. |
| Relação entre resistência escolhida e mínimo declarado | `30 / 25 = 1,20` | O fck escolhido é 20% superior ao mínimo citado no próprio trabalho. Isso não valida classe de agressividade, resistência de cálculo ou segurança. |
| Expressão declarada de altura da laje | `h = d + c + 10 + 10/2 = d + c + 15 mm` | Com o cobrimento adotado de 30 mm, resulta `h = d + 45 mm`. Se se usasse apenas o mínimo citado de 25 mm, seria `h = d + 40 mm`. O documento não dá valores de `d`/`h` nem identifica geometricamente o termo adicional de 10 mm. |
| Partições indicadas nos desenhos | `45% + 55% = 100%`; `50% + 50% = 100%` | Confirma somente as somas locais anotadas. Não demonstra conservação da área total, contagem de áreas ou soma das reações porque faltam cotas e correspondência com pilares. |

Para a hipótese ideal de D1 — faixa prismática, elástica, carga uniforme `q`, vão `L`, dois extremos perfeitamente engastados — a solução analítica é `RA = RB = qL/2`, momentos extremos `−qL²/12` e momento positivo central `+qL²/24`. Essas expressões permitem verificar o método, mas D1 não informa `q`, `L` nem um resultado numérico para comparação. Não se pode apresentar um número de momento do edifício a partir deste DOCX.

## 6. Achados concretos e limites de verificação

| Achado | Evidência | Consequência / correção necessária |
|---|---|---|
| Direção de trabalho da laje unidirecional descrita de forma invertida | D2 P0437: lajes com `λ > 2` “concentram os esforços para o maior vão”. | Com a definição usual `λ = ly/lx`, `ly ≥ lx`, a laje unidirecional trabalha predominantemente na direção do menor vão entre apoios. O texto precisa ser corrigido e as direções/vãos usados no EXCEL/DWG conferidos. A definição de λ também deve ser explicitada. Isto é um achado conceitual; não foi possível demonstrar que a planilha incorre no mesmo erro. |
| Caso de fronteira sem classificação | D2 P0437 usa `λ > 2` e `λ < 2`. | `λ = 2` fica sem regra declarada. O critério usado deve cobrir a igualdade e ser documentado com sua fonte e hipóteses. |
| Resultados de cálculo externos, não incorporados | D1 P0227; D2 P0193, P0235, P0274, P0397–P0399, P0439/P0475. | Faltam tabelas de entrada/saída, fórmulas, coeficientes e identificação das células. Não é possível reproduzir áreas de pilares, altura/vão, combinações, momentos ou armaduras destes arquivos. |
| Condições de apoio assumidas sem demonstração | D1 P0191; D2 P0398/P0475. | Apoio/engaste não deve ser inferido apenas pela legenda de CAD. Conferir continuidade, rigidez de vigas/pilares, painéis adjacentes, compatibilidade e cargas alternadas no modelo efetivo. |
| Afirmação normativa sem cláusula identificada | D1 P0227 afirma que a norma “demanda” tabelas/fórmulas de Czerny. | O memorial não fornece artigo, tabela, coeficientes nem condições de validade. Não há base nestes arquivos para tratar Czerny como procedimento universal obrigatório. Verificar a norma licenciada e a fonte original do método antes de adotá-lo. |
| Pré-dimensionamento de pilares não equivale à segurança em ELU | D2 P0274 informa somente carga estimada e áreas de influência. | Faltam dimensões/alturas, momentos e excentricidades, esbeltez, efeitos de segunda ordem, armadura, interação N–M e estabilidade global. |
| Seleção genérica de bitola sem detalhamento | D2 P0233 e P0399. | Ø10 não dimensiona todos os elementos. Faltam As calculada/adotada, mínimos/máximos, espaçamento, camadas, ancoragens, emendas e verificações de cisalhamento/punção aplicáveis. O termo adicional de 10 mm na expressão de `h` exige desenho da seção para confirmação. |
| Justificativa de durabilidade incompleta | D2 P0232–P0234. | A indicação “zona urbana” e o fck escolhido não substituem classificação documentada de exposição, condições de produção/execução e cobrimentos por tipo de elemento. O cobrimento dos pilares não foi declarado. Não se demonstra falha numérica de cobrimento sem esses dados. |
| ELU/ELS apenas anunciados | D2 P0438–P0475. | Não aparecem famílias de ações, valores de γ/ψ, ação variável principal, combinações raras/frequentes/quase permanentes ou verificação de fissuração e deformação. Não há como conferir seus coeficientes ou resultados. |
| Capturas de CAD insuficientes para reconstruir geometria | D2 P0317/P0358. | Desenhos sem cotas, nomes de pilares, escala utilizável e bordas completas não permitem obter áreas de influência e conferir cargas/reação total. |

## 7. O que estes arquivos permitem e o que falta

Os memoriais permitem entender a intenção do modelo acadêmico e apontar erros de conceito e de rastreabilidade. Não demonstram capacidade resistente final de vigas, lajes ou pilares; não abrangem um pórtico completo, verificações de aço estrutural ou certificação de materiais por ensaios. Não apresentam cálculo `Sd ≤ Rd`, envoltórias, índices de utilização ou detalhamento executivo suficiente para declarar um elemento seguro.

Para tornar a verificação concreta, é necessário identificar os EXCEL/DWG efetivamente correspondentes a estes SHA256 e revisar suas entradas, fórmulas e desenhos; obter geometria/cargas completas; rastrear as combinações e métodos à edição aplicável dos textos normativos autorizados; reproduzir esforços e resistências independentemente; e verificar ELU, ELS, estabilidade, ligações/apoios, detalhamento e durabilidade para cada elemento e situação. Essa lista indica o que não está demonstrado pelos DOCX; não afirma que o restante do acervo já tenha sido auditado ou que a construção possa ser liberada.

**Estado da análise:** leitura integral do conteúdo técnico incorporado concluída; verificação aritmética limitada aos dados disponíveis concluída; esforços finais e conformidade normativa integral não demonstráveis por estes dois arquivos.
