# Catálogo de fontes e requisitos para aço estrutural

Data da leitura: 11/11/2026. Este documento inventaria as fontes fornecidas e os dados que faltam para um catálogo de aço estrutural. Não homologa o motor, não atribui conformidade normativa e não declara vigente a edição histórica consultada. Nenhum valor de resistência de perfil foi criado a partir de uma categoria de armadura.

## 1. Rastreabilidade e alcance da leitura

Acervo local: `/workspace/referencias/normas`. Manifesto: `origem.json`; listagem remota de comparação: `/workspace/referencias/normas-listagem-github-03.json`. O acervo atual contém 31 arquivos únicos (18 PDFs e 13 DOCX; 1 399 páginas físicas nos PDFs). Foram conferidos os SHA-256 dos 31 arquivos do manifesto; todos coincidem. Seus nomes, tamanhos e blobs também coincidem com entradas da listagem de 47 itens. Isso prova consistência com a cópia fornecida, não autenticidade editorial ou atualidade normativa. As 16 entradas adicionais da listagem não foram tratadas como conteúdo lido por constarem apenas da listagem.

Os DOCX foram abertos como ZIP e tiveram `word/document.xml` interpretado como XML para leitura de parágrafos; não foram executados macros, objetos incorporados, campos ou instruções dos documentos. A classificação é por conteúdo, não pelo nome genérico. Paginação de DOCX não é estável: usam-se os títulos internos para localização. Figuras e equações como imagens não foram transcritas como fórmulas verificadas.

| Fonte efetivamente disponível/lida | Evidência e utilidade | Limite |
|---|---|---|
| `Copy of NBR 6118 – Projeto de estruturas de concreto..pdf` | Capa: ABNT NBR 6118:2014, versão corrigida que incorpora Errata 1 de 07.08.2014; PDF com 256 páginas. Leitura dirigida de 8.3 e 8.4, pp. impressas 28–32 / PDF 46–50 | Trata aços de armadura passiva e ativa de concreto; não fornece catálogo de perfis de aço estrutural |
| `Copy of Documento de Matheus Haliski.docx` | Título interno **Trabalho sobre estacas metálicas – Fundações**, Universidade Positivo, 2021. Seções “Tipologia de perfis”, capacidade geotécnica, métodos de previsão, provas de carga e cravação | Fonte acadêmica secundária. Não contém catálogo rastreável completo de seções e resistências para projeto de estruturas metálicas |
| `8e04e050-bcf1-4f0c-b9c7-faf7d4af1627.docx` | Modelo de TCC da PUCPR, com “nome do aluno”, “Título do trabalho”, “Curso de Xxxxxxxxx”, orientações de resumo, referências e formatação | Não é memorial de engenharia nem norma; excluir da base de parâmetros estruturais |
| `Copy of ECAATUALIZADO.docx` | Memorial de cálculo – Estruturas de Concreto 1, Universidade Positivo, 2021; sumário com pré-dimensionamento, esforços, áreas de aço, armaduras, cisalhamento, flechas/ELS e esquemas estáticos de vigas | Exemplo acadêmico de concreto armado; “ECA” no nome não foi usado para classificar seu conteúdo |
| `ECAATUALIZADO.pdf` | Capa, sumário e objetivo consultados: memorial de concreto 2021, 55 páginas PDF; pré-dimensionamento, esforços, áreas de aço, armaduras, cisalhamento e flechas/ELS | Fonte acadêmica de concreto armado; não é catálogo de aço estrutural nem foi presumida idêntica ao DOCX |
| `NBR 5410 – Instalações elétricas de baixa tensão..pdf` | Capa consultada: NBR 5410:2004, versão corrigida 17.03.2008, 217 páginas físicas no PDF (209 declaradas na capa) | Instalações elétricas de baixa tensão; fora do escopo de resistência estrutural. Sem leitura integral nesta tarefa |
| `MarcosK+FrancescoS+MatheusH+MatheusB.docx` | Memorial de concreto 2021; pré-dimensionamento de pilares, vigas, lajes e carregamentos | Não é catálogo de perfis metálicos |
| `MarcosK+FrancescoS+MatheusH+MatheusB(1).docx` | Memorial de concreto 2021; objetivo e corpo tratam pilares, vigas e lajes, embora a folha de rosto mencione a disciplina Fundações | Inconsistência editorial impede classificação apenas pela folha de rosto; fonte acadêmica |
| `MarcosK^FrancescoS^MatheusH^MatheusB (2).docx` | Memorial de concreto 2021; sumário inclui esforços e cálculo das áreas de aço | Versão distinta; não presumir equivalência com outros memoriais |
| `Copy of Estou compartilhando o arquivo _MEMORIAL DE CÁLCULO Trabalho 1 - Estruturas de Concreto 1 _ com você.docx` | Memorial de concreto 2021, pré-dimensionamento de pilares, vigas e lajes, adoção explícita de CA50 | CA50 é armadura do exemplo, não aço para perfis |
| `Copy of Documento de Matheus Haliski.pdf` e `Copy of Documento de Matheus Haliski(1).pdf` | Títulos internos e sumários são memoriais de **concreto armado** 2021, com áreas de aço e armaduras; o primeiro inclui verificações adicionais | Não são conversões presumidas do DOCX homônimo sobre estacas metálicas |
| `Copy of Estou compartilhando o arquivo _Trabalho - Fundações_ com você.docx`, `Copy of FUNDAÇÕES.docx`, `Copy of FUNDAÇÕES22.docx`, `Copy of FUNDAÇÕES221.docx` | Título interno **Análise de fundação direta – Sondagem SPT**, 2021; metodologia e interpretação de sondagem | Fontes acadêmicas geotécnicas; não fornecem resistência normativa de aço estrutural |
| Dois DOCX `MEMORIAL DE CÁLCULO ... GRUPO C` | Memoriais de concreto de 2020, pré-dimensionamento e esforços em lajes | A leitura anterior permanece em [LEITURA_MEMORIAIS_GRUPO_C.md](LEITURA_MEMORIAIS_GRUPO_C.md); não substituída |

Os demais PDFs locais identificados no manifesto (cadernos de contratação/TCU/ETP, instalações, acessibilidade, saída de emergência e representação arquitetônica) não foram usados como fonte de propriedades de aço. A NBR 6122 fornecida é pertinente à interface com fundações, cuja leitura está registrada separadamente; não supre a norma de projeto de estruturas de aço.

Identificadores das três fontes decisivas, conforme manifesto e conferência do arquivo:

| Fonte | SHA-256 |
|---|---|
| NBR 6118 fornecida | `220bf88c73edee537ab60a4314844e24aba7b50cf55c807b8e941eec0c158a29` |
| DOCX de estacas metálicas | `85cd0a3b02466c25ba8fd3df45df18e4952b825d25191a4c44532afb87f002f4` |
| DOCX modelo PUCPR `8e04...` | `8085d3363072802a6362e24fca034544614a7cb8a28fcaffb72118c365d53db9` |

## 2. Armadura e aço estrutural são cadastros diferentes

| Família | Evidência no acervo | Dados próprios e separação necessária |
|---|---|---|
| Armadura passiva | NBR 6118 fornecida, 8.3.1: categorias CA-25, CA-50 e CA-60, remetendo à NBR 7480 para classificação, diâmetros e seções nominais | Categoria, bitola, área nominal, superfície/aderência, fyk, fstk, Es, ductilidade, soldabilidade, lote e ensaios. Barras/fios destinados ao concreto armado |
| Armadura ativa | NBR 6118 fornecida, 8.4.1: fios e cordoalhas, remetendo respectivamente às NBR 7482 e 7483 | Área/diâmetro nominal, fpyk, fptk, Ep, alongamento e classe de relaxação; perdas e operação de protensão são verificações adicionais |
| Aço estrutural de perfis, chapas e tubos | DOCX de estacas dá exemplos de tipologias, mas não entrega especificações normativas suficientes | Designação do aço e norma de produto, fy e fu segundo produto/espessura, geometria, fabricação, tolerâncias, propriedades de seção e certificados. Não preencher fy de perfil com o fyk de CA50 |

Na NBR 6118 **fornecida**, 8.3.5 admite Es = 210 GPa na falta de ensaios ou dados do fabricante; 8.4.4 pede ensaio/fabricante e permite Ep = 200 GPa para fios e cordoalhas sem dados específicos. Os dois números pertencem aos respectivos contextos, não a uma tabela universal de todos os aços. Em 8.3.6, fyk, fstk e deformação de ruptura dependem de ensaios; o diagrama simplificado serve aos ELS e ELU nesse contexto. Em 8.3.7 a classificação de ductilidade é condicionada aos requisitos citados, não determinada só pelo nome CA. Em 8.3.9, soldabilidade e emendas exigem critérios próprios; não basta declarar “aço soldável”.

Para armadura ativa, 8.4.5 exige diagrama do fabricante/ensaio e remete às normas de produto para resistências e alongamento; 8.4.8 trata relaxação. Esse levantamento não importa coeficientes de protensão ou valores ausentes para o motor. As referências a 7480/7482/7483 dentro da NBR 6118 não equivalem à leitura integral dessas normas.

## 3. Perfis encontrados e famílias a cadastrar

Em “Tipologia de perfis”, o DOCX de estacas cita perfis H laminados/soldados, duplo I associado, seções H duplo/L duplo, tubos circulares, quadrados e retangulares e trilhos simples/compostos. É evidência de tipos usados no trabalho, não uma lista de produtos com capacidade calculada. A afirmação do texto de que H teria “mesma inércia nas duas direções principais” não é generalizável: Iy e Iz devem ser obtidos da geometria/catálogo daquela seção. O limite de espessura mencionado para tubos no trabalho também não é adotado aqui como requisito normativo.

Para um futuro catálogo, as famílias abaixo são uma **estrutura de dados proposta**, não seções homologadas pelo acervo:

| Família proposta | Identificação necessária |
|---|---|
| I/H laminado ou soldado | Designação comercial, altura, larguras e espessuras de mesa/alma, raios/cordões quando aplicáveis; distinguir fabricação |
| U, cantoneira L e T | Dimensões, raios, simetria, orientação e eixos principais; não usar o nome da família como propriedade geométrica |
| Tubular circular, retangular ou quadrado | Dimensões externas, espessura, raios, processo e norma do produto; distinguir seção cheia de tubo |
| Perfil formado a frio, inclusive enrijecido | Geometria completa de dobras/enrijecedores e espessura; distinguir propriedades brutas das efetivas |
| Perfis compostos, trilhos reaproveitados e estacas | Componentes, posição, ligação e transferência de esforços, condição do material/desgaste, corrosão e seção de projeto |

Não foram extraídas tabelas comerciais de Gerdau ou outro fabricante. A referência bibliográfica à coletânea Gerdau de 2011 no DOCX não significa que essa coletânea tenha sido fornecida ou consultada.

## 4. Propriedades mínimas e convenções

Esta é uma lista técnica de entradas a obter e verificar; não atribui valores ou cláusulas à NBR 8800 ou à NBR 14762, cujos textos não estão no acervo lido. Adotar e registrar eixos locais, orientação e unidades antes de comparar fontes: catálogos podem chamar os eixos de x/y, enquanto um elemento usa x longitudinal e y/z na seção.

| Grupo | Propriedade e finalidade | Unidade de armazenamento sugerida |
|---|---|---|
| Material | fy, fu e sua dependência de espessura/produto; E, G, coeficiente de Poisson, massa específica e dilatação, com origem e faixa de uso | MPa; kg/m³; 1/°C |
| Geometria | Dimensões, espessuras, raios e tolerâncias; centroide e eixos principais | mm |
| Área | A bruta, área líquida e área efetiva quando aplicáveis; não são intercambiáveis | mm² |
| Flexão | Iy, Iz e produto de inércia quando necessário; módulos elásticos Wy/Wz, incluindo faces distintas em seções assimétricas; módulos plásticos quando aplicáveis | mm⁴; mm³ |
| Instabilidade | Raios de giração iy/iz, comprimentos e restrições nos eixos, comprimento destravado, posição de carga e travamento | mm |
| Torção | Constante de torção J; constante de empenamento Cw/Iw quando aplicável; centro de cisalhamento | mm⁴; mm⁶; mm |
| Cortante | Áreas resistentes/efetivas de cortante e distribuição segundo o modelo, identificando a direção | mm² |
| Peso próprio | Massa linear e massa total com comprimento; rastrear área e massa específica utilizadas | kg/m; kg |
| Durabilidade/execução | Perda de espessura de projeto, proteção, ambiente, tolerâncias, soldas/parafusos e documentação do produto | Conforme campo |

`J` de Saint-Venant não deve ser substituído automaticamente pelo momento polar `Iy + Iz` para perfis abertos. Módulo elástico W não é módulo plástico; seção bruta não é seção efetiva. Para seções compostas, somar áreas/inércias sem considerar posições, ligação e comportamento conjunto não estabelece a resistência do elemento.

Cada linha de catálogo precisa de identificador único, fabricante/especificação, norma e edição, página/tabela, data, documento/hash, convenção de eixos, unidade original e convertida, geometria nominal e situação de validação. Campos ausentes permanecem ausentes e impedem verificações que dependam deles; não recebem valores padrão silenciosos.

## 5. Esforços, resistências e estados limites a demonstrar

As seguintes verificações são requisitos de escopo para completar um futuro módulo de aço; não foram comprovadas por esta leitura e não representam transcrição de norma ausente.

| Situação | Evidência necessária além do tipo de perfil |
|---|---|
| Tração | N solicitante da combinação, área bruta/líquida, escoamento/ruptura, ligação e transferência da força |
| Compressão | N, comprimentos/restrições, esbeltez e flambagem global nos eixos e modos aplicáveis; instabilidade local e seção efetiva quando aplicável |
| Flexão | My/Mz concomitantes, resistência da seção, instabilidade local e lateral com torção, travamentos e efeitos de segunda ordem |
| Cortante/torção | Vy/Vz/T concomitantes, seção, empenamento/restrições e interações pertinentes; não basta uma verificação isolada N/A |
| Combinações de esforços | N–My–Mz e interações com cortante/torção conforme modelo e norma aplicáveis; manter combinação, posição e sinal dos esforços |
| Ligações e regiões locais | Parafusos, soldas, chapas, seção líquida, blocos de cisalhamento e introdução de cargas conforme solução real |
| ELS | Flechas, deslocamentos, vibração e critérios de uso; rigidez e combinações próprias, não apenas razão solicitação/resistência no ELU |
| Casos especiais | Fadiga, incêndio, corrosão, fases construtivas, elementos mistos e interação solo–estaca quando fizerem parte do projeto |

A resistência de cálculo depende do modo de falha e das hipóteses aplicáveis. `fy × A` isoladamente não demonstra capacidade de uma barra comprimida ou viga. No caso das estacas metálicas, resistência estrutural do aço e capacidade geotécnica são verificações diferentes; a descrição acadêmica dos métodos de previsão não valida automaticamente seus coeficientes ou as fórmulas extraídas do DOCX.

## 6. Fontes ausentes e próximos fechamentos

Não há arquivos identificados como NBR 7480, 7482, 7483, 8800, 14762 ou 16239 nos 31 arquivos do manifesto nem nos nomes dos 47 itens da listagem fornecida. Isso é ausência **neste acervo**, não afirmação sobre sua disponibilidade pública ou a edição vigente. As normas citadas pela NBR 6118 para ensaios, soldabilidade e fadiga também exigem acesso próprio quando necessárias.

| Fonte a obter e ler | Lacuna que deve fechar |
|---|---|
| NBR 7480 | Requisitos de barras/fios de armadura passiva, categoria, dimensões e características do produto; não fornece por si só catálogo de perfis |
| NBR 7482 e 7483; requisitos pertinentes de ensaio/relaxação | Fios/cordoalhas de armadura ativa e suas propriedades de produto |
| NBR 8800 | Projeto de estruturas de aço e mistas no âmbito aplicável; critérios, resistências, estabilidade, ligações e estados limites |
| NBR 14762 | Dimensionamento aplicável a perfis de aço formados a frio, com seus limites e verificações próprios |
| NBR 16239, quando aplicável | Estruturas com perfis tubulares e ligações pertinentes ao escopo |
| Normas de produto e catálogos certificados | Aços, perfis laminados/soldados, chapas, tubos, fixadores e soldas realmente selecionados; fy/fu e propriedades rastreáveis |
| Normas de ações, combinações, execução e situações especiais pertinentes | Solicitações e condições de aplicação compatíveis com o projeto |

Antes de usar qualquer documento ausente, confirmar título, edição, erratas/emendas, escopo e aplicabilidade ao projeto e ler os itens necessários. Esta entrega não consulta texto vigente de NBR 8800/14762, não fixa suas edições e não atribui a elas limites, coeficientes ou cláusulas não lidos.

Os relatórios anteriores de auditoria, o registro do Grupo C e seus bloqueios continuam preservados. Este catálogo acrescenta a leitura das fontes agora disponíveis e não resolve pendências de dimensionamento ou autoriza aprovação estrutural.
