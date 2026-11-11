# Matriz de revisão normativa dos procedimentos de cálculo

Atualização: 11/11/2026. Esta matriz distingue confirmação de edição, implementação e validação de cláusulas. **O acervo fornecido inclui a NBR 6118:2014 corrigida, mas não o conjunto das edições atuais; nenhuma linha está certificada como integralmente conforme.** A revisão técnica deve ser concluída por profissional habilitado antes de habilitar aprovação. O programa não pode alterar o texto de uma norma: deve implementar e comprovar os requisitos da edição adotada para a obra.

## Qualidade e alcance das evidências

| Tipo de evidência | O que foi efetivamente examinado | O que permite concluir |
|---|---|---|
| Norma vigente integral | Nenhuma norma vigente foi lida integralmente nesta etapa | Consulta de cláusulas, tabelas, notas e exceções permanece aberta; não há procedimento homologado apenas pela pesquisa de edição |
| Norma histórica fornecida | NBR 6118:2014 corrigida, com leitura dos capítulos distribuída e catalogada por páginas; projeto de revisão da NBR 6122 de dezembro/2018, 113 páginas | Referência técnica rastreável e identificação de divergências históricas; não valida a edição atual. Extensão e limitações da leitura constam dos catálogos |
| Fonte oficial/editorial | Catálogo ABNT e comunicados ABNT/ABECE/CBIC; ficha editorial da NBR 12655 | Identificação de edição, publicação, correção e rotas de acesso; não comprova conformidade das fórmulas |
| Trabalho acadêmico | TCC UFRJ REL_UFRJ (2019), 81 páginas integralmente lidas, com NBR 6118:2014 na bibliografia; no TCC UFRGS (2015), apenas páginas de identificação, 68–71 e referências da p.87 | Exemplos e contraprovas delimitados pela edição e pelas hipóteses; não substituem a norma vigente |
| Código e ensaios independentes | Snapshot do motor, reprodução de defeitos e benchmarks mecânicos | Comportamento computacional nos casos examinados; não demonstra segurança de todos os modelos ou execução |

Relatórios detalhados: [concreto e fonte acadêmica integral](CONSULTA_NORMAS_CONCRETO.md), [ações, combinações e vento](CONSULTA_NORMAS_ACOES.md) e [revisão independente do motor](REVISAO_INDEPENDENTE_MOTOR.md). Os relatórios distinguem o snapshot que originou um achado das correções posteriores.

O lote ampliado de `docs/normas` está inventariado em `/workspace/referencias/normas`: 31 arquivos únicos, 18 PDFs e 13 DOCX. Inclui NBR 6118:2014 corrigida, projeto de revisão de fundações de 2018, normas de outras disciplinas e memoriais. **Não contém as edições atuais de 6118/8681/6120/7480 nem texto específico da NBR 8800**. A presença de um PDF ou uma ocorrência temática não significa auditoria integral de seus procedimentos. O [catálogo de fontes](CATALOGO_FONTES_ENGENHARIA.md) distingue uso, edição e lacunas.

Rastreabilidade do lote recebido: [origem do acervo](ORIGEM_ACERVO.json), [inventário dos PDFs](INVENTARIO_ACERVO.json) e [índice extraído de PDF/DOCX](FONTES_EXTRAIDAS.json). Catálogos de [materiais](CATALOGO_CONCRETO_ARMADURAS.md), [esforços](CATALOGO_ESFORCOS_ANALISE.md) e [aço estrutural](CATALOGO_ACO_ESTRUTURAL.md) localizam as fontes. Título interno, edição e integridade do arquivo devem orientar a consulta, sem inferir o conteúdo pelo nome externo.

No exemplo da viga do TCC UFRJ, a contraprova geométrica altera d de 575 para 555 mm e a capacidade idealizada de 207,963 para 199,768 kN·m, abaixo de Md=200 kN·m. Esse achado demonstra que a leitura integral de uma fonte acadêmica também exige crítica e reprodução; não fornece uma certificação normativa do exemplo ou do produto.

## Base normativa identificada

| Referência | Finalidade | Evidência consultada | Estado da revisão |
|---|---|---|---|
| ABNT NBR 6118:2026 / NBR 6118:2023 + Emenda 1:2026 | Projeto de estruturas de concreto, ELU/ELS, detalhamento e avaliação técnica | [Catálogo ABNT](https://www.abntcatalogo.com.br/), [comunicado ABECE](https://site.abece.com.br/nbr-6118-tem-emenda-publicada-pela-abnt/) | Publicação confirmada; cláusulas e diferenças de cálculo ainda precisam de auditoria no texto integral |
| ABNT NBR 8681:2025 | Ações, segurança e combinações | [ABECE](https://site.abece.com.br/em-vigor-a-nova-nbr-8681/) | Publicação confirmada; não presumir que os fatores fixos do código estejam validados para esta edição |
| ABNT NBR 6120:2019, com correções aplicáveis | Ações para cálculo de estruturas de edificações | [comunicado CBIC](https://cbic.org.br/norma-para-calculo-de-estruturas-de-edificacoes-e-publicada/) | Publicação de 2019 confirmada; tabelas de ocupação, correções e aplicabilidade ainda precisam de conferência |
| ABNT NBR 6123:2023, com errata de 08/04/2025 | Ações de vento | [ABECE](https://site.abece.com.br/em-vigor-a-nova-nbr-6123/), [boletim CBIC de 2025](https://cbic.org.br/cbic-divulga-boletim-de-normas-tecnicas-de-abril-e-maio-3/) | Publicação da edição e da errata identificadas; texto corrigido não lido; fora das ações dos ensaios atuais e vento do produto incompleto |
| ABNT NBR 7480:2024 | Requisitos do aço para armaduras | [certificado de produto publicado pela Gerdau](https://www2.gerdau.com.br/wp-content/uploads/2025/09/28-019_24-certificado-13_06_27-ca50-m-adequacao.pdf) | Referência de edição corroborada por certificação primária; isso não valida barras efetivamente usadas em uma obra nem substitui o texto integral |
| ABNT NBR 8800:2024, versão corrigida 2025 | Estruturas de aço/mistas | [ABECE](https://site.abece.com.br/nbr-8800-tem-versao-corrigida-publicada/) | Publicação/correção confirmadas; motor de aço ainda não implementado; bloqueio mantido |
| ABNT NBR 14931:2023 | Execução de estruturas de concreto | [boletim CBIC](https://cbic.org.br/inovacao/2023/06/05/comat-gant-apresenta-boletim-de-alteracoes-de-normas-tecnicas-da-cbic/) | Publicação identificada; execução, tolerâncias e inspeção não demonstradas pelos testes computacionais |
| ABNT NBR 12655:2022-02-25 | Preparo, controle, recebimento e aceitação do concreto | [Ficha editorial DIN Media](https://www.dinmedia.de/en/standard/abnt-nbr-12655/352698418), marcada como corrente | Metadados consultados; critérios e texto integral não lidos; não há ensaios de materiais nesta entrega |
| NBR 5738, 5739, partes da 8522, 8953 | Ensaios e propriedades do concreto | Consulta ao [catálogo autorizado](https://www.abntcatalogo.com.br/) necessária | Fixar edição/partes/correções e critérios de aceitação; leitura integral pendente |
| NBR 6122, 6484, 9062, 15200, 14323, 15421 e normas específicas de sistema | Fundações, sondagens, pré-moldados, incêndio e sismo, conforme aplicação | Consulta ao catálogo autorizado necessária | Fora da comprovação dos cinco ensaios; avaliar aplicabilidade, edições e requisitos antes de uso |

As fontes públicas acima sustentam identificação de publicação/escopo, não uma auditoria de todas as fórmulas. Regras de transição, situação da obra e cláusulas específicas devem ser registradas no processo de revisão.

## Situação dos defeitos reproduzidos

| Achado | Estado desta consolidação | Evidência e limite do fechamento |
|---|---|---|
| A1 — demandas e conclusões fixas no memorial | Corrigido, com quatro regressões Python e evidências regeneradas | Demandas/geometria vêm das entradas e saída Java atuais; hipóteses resistentes explícitas. Triplicar B1 resulta em Md189 kN·m, Vd126 kN e flexão parcial não atende; entradas/estados incompatíveis geram erro |
| A2 — extremo de deslocamento subestimado pela amostragem | Corrigido, com regressões e suíte `cca-domain` 86/86 | Candidatas extremas da resultante espacial por `vy·vy′+vz·vz′=0`; P1: 2,763916257720 mm. JSON/memorial regenerados; máximo elástico corrigido ainda não equivale a flecha final normativa |
| A3 — peso próprio do pilar concentrado no topo | Corrigido, com teste de regressão | `MontadorPesoProprioPilarTest` verifica reação, compressão variável e encurtamento de 3,75 µm no prisma de referência; confirma esta correção mecânica, sem encerrar estabilidade ou verificação completa do pilar |
| A4 — coeficiente algébrico arredondado na flexão | Corrigido, com contraprova independente | O bloco declarado exige `2/0,85`, substituindo `2,353`. A seção com x=90 mm, b=200 mm e d=450 mm recupera x/d=0,2 e As=6,032571428571 cm². Não altera a hipótese normativa do bloco nem amplia o domínio do método |

As descrições originais A1–A3 da [revisão independente](REVISAO_INDEPENDENTE_MOTOR.md) registram o snapshot em que os problemas foram reproduzidos. O estado acima incorpora a evolução informada pela equipe; não amplia o escopo de conformidade.

## Rastreabilidade por procedimento

| ID | Procedimento e regra de aceitação | Norma/base a revisar | Implementação/evidência atual | Pendência que impede declaração global de conformidade |
|---|---|---|---|---|
| N01 | Unidades, grandezas e convenções coerentes em toda conversão | NBR 6118/8681 + contrato de interoperabilidade | Ensaios em m, kN, kN·m e kN/m²; comparações Java/Python | Quantidades tipadas e testes de todas as interfaces/formas de entrada |
| N02 | Material, resistência característica/de cálculo, E/G e modelo constitutivo rastreáveis | NBR 6118, 7480, 12655, 8522, 8953 | Cadastro C25/C30; materiais incompatíveis bloqueados; E=30 GPa explícito no ensaio | Revisão integral do cadastro e origem/idade/ambiente/ensaios; propriedades de projeto não podem ser inventadas pelo ID |
| N03 | Ações permanentes/variáveis por uso, peso e situações de projeto | NBR 6120 | Cargas artificiais explícitas; A3 corrigido com regressão do peso próprio axial distribuído do pilar | Classificar uso real e tabelas aplicáveis; revisar peso, equipamentos, paredes, cobertura e situações especiais |
| N04 | Todas as combinações relevantes; valores favoráveis/desfavoráveis e ações principais/acompanhantes | NBR 8681:2025 e NBR 6118 | Quatro combinações de teste com fatores declarados no JSON | Auditoria dos fatores e regras da edição atual; geração por caso e uso; não rotular 1,4G+1,4Q como regra universal |
| N05 | Caminho de cargas, apoios, vínculos, liberações, excentricidades e diafragmas compatíveis com a construção | NBR 6118, análise estrutural | Modelos isolados têm vínculos explícitos; D1 confere o montador e a geometria dos comandos VI do desenho | Conversão automática BIM→MEF ainda tem tolerâncias e distribuições simplificadas; a transferência de laje em D1 é uma hipótese simplificada, sem comprovação de placa e dos casos gerais |
| N06 | Equilíbrio, compatibilidade, resistência dos materiais e precisão numérica | Mecânica estrutural + requisitos de análise da NBR 6118 | Comparações independentes; extremos de momento, A2 e A3 corrigidos com regressões e evidências regeneradas | Não linearidades, rigidez fissurada, efeitos dependentes do tempo e limites de discretização/deslocamento ainda incompletos |
| N07 | Estabilidade e efeitos globais/locais de segunda ordem | NBR 6118, estabilidade | C1 e P1 calculados apenas em primeira ordem | Segunda ordem, imperfeições, fluência e critérios de dispensa não demonstrados; não aprovar pilares pelo esforço normal isolado |
| N08 | Viga em flexão: Sd≤Rd, domínio, armaduras mínima/máxima e geometria efetiva | NBR 6118, flexão/detalhamento | Comparação parcial B1/B2 vinculada aos esforços atuais após A1; hipóteses em `hipoteses-resistencia.json`; regressão altera a conclusão ao aumentar carga | Conferir cláusulas atuais, CAA/cobrimento, ancoragem, apoio, montagem e posição real; balanço requer armadura superior e revisão específica |
| N09 | Cortante e torção, bielas, estribos e interações | NBR 6118 | Modelo I parcial de B1/B2; estribos inviáveis bloqueados | Limites, torção, zonas especiais, detalhes e interação com demais esforços precisam de cobertura completa |
| N10 | Pilar em N–Mx–My, sinais e concomitância, dois eixos e vínculos | NBR 6118 | Esforços de C1/P1 obtidos e comparados | Superfície resistente e segunda ordem não validadas; resultados globais não verificados |
| N11 | Lajes/faixas/placas, duas direções, apoios, cortante, punção e aberturas | NBR 6118 | L1 é faixa de 1 m com apoio contínuo em duas bordas; flexão parcial calculada | Não extrapolar para placa 6×4 do desenho, bordas livres, pilares, punção ou furos; motor atual de lajes gerais não validado |
| N12 | Flechas, fissuras e demais estados de serviço sob combinações apropriadas | NBR 6118/8681 | Extremos do deslocamento elástico corrigidos em A2; não é flecha final normativa | Rigidez fissurada, fluência/retração, história de cargas, limites por uso e dano a componentes não estruturais |
| N13 | Espaçamento, cobrimento, emendas, ancoragem, confinamento, armaduras de pele/distribuição | NBR 6118, 7480 e 14931 | Correção do catálogo de bitolas e exemplo explícito de d | Falta detalhamento completo e comprovação construtiva; área de aço suficiente não basta |
| N14 | Estruturas metálicas e ligações | NBR 8800 e normas específicas | Materiais metálicos rejeitados no motor de concreto | Implementar motor, perfis, instabilidades, ligações e validação independente; nenhum caso atual comprova aço estrutural |
| N15 | Fundações e interação solo–estrutura | NBR 6122/6484 e NBR 6118 | Bases dos ensaios são vínculos ideais, sem fundação dimensionada | Investigações, capacidade, recalques, planta de cargas e projeto de fundações |
| N16 | Incêndio, sismo, fadiga, fases construtivas e pré-moldados quando aplicáveis | Normas específicas + NBR 6118/8681 | Não incluídos nos cinco casos didáticos | Determinar aplicabilidade, implementar/verificar ou bloquear o escopo |
| N17 | Controle tecnológico e execução | NBR 12655/5738/5739/7480/14931 | Não executado em laboratório/campo | Ensaios e rastreabilidade dos lotes, inspeções, cura, geometria executada e registros de qualidade |
| N18 | Revisão independente/ATP, responsabilidade e versão das entradas | NBR 6118:2026 e processo técnico | Aprovação do protótipo bloqueada; relatório e casos reproduzíveis | Texto integral, responsável habilitado, revisão/ATP aplicável e snapshot completo das entradas |

## Política de revisão antes de liberar cada procedimento

Para cada linha, registrar: edição/erratas, item exato e versão da regra; escopo e hipóteses; entradas e unidades; resultados esperados calculados independentemente; testes de fronteira; responsável e evidência de revisão. O item da versão antiga citado em comentários do código é apenas um localizador a conferir, não evidência de conformidade da edição de 2026.

Mudanças normativas não podem ser aplicadas trocando apenas o ano de um comentário. Sem texto integral, sem dados ou com rotina incompleta, a resposta correta do produto permanece `NAO_VERIFICADO`/`NAO_SUPORTADO`. Para comprovar segurança global, todas as verificações aplicáveis devem ter cobertura técnica demonstrada, além de aprovação profissional. Os testes desta entrega não habilitam essa aprovação.
