# Consulta do documento de fundações e confronto com o motor

Data: 10/11/2026. Escopo: leitura do PDF de fundações fornecido pelo usuário e análise de `CalculoFundacoes`, `FundacaoService` e dados associados. As constatações descrevem a implementação consultada nesta data; não houve alteração de código de fundações nesta tarefa.

## Identificação real do documento

**O arquivo fornecido não é a edição publicada vigente da ABNT NBR 6122.** Sua primeira página identifica **PROJETO DE REVISÃO ABNT NBR 6122 — DEZ 2018**, em Consulta Nacional, e declara expressamente: **“não tem valor normativo”**. Essa identificação também aparece nos cabeçalhos e rodapés. A capa foi conferida visualmente, além da extração do texto.

| Campo | Evidência |
| --- | --- |
| Nome recebido | `NBR 6122 – Projeto e execução de fundações..pdf` |
| Local | `/workspace/referencias/normas/NBR 6122 – Projeto e execução de fundações..pdf` |
| Origem indicada pelo usuário | [GitHub — docs/normas](https://github.com/MatheusHaliski/CivilConstructorAI/blob/main/docs/normas/NBR%206122%20%E2%80%93%20Projeto%20e%20execu%C3%A7%C3%A3o%20de%20funda%C3%A7%C3%B5es..pdf) |
| Identificação interna | Projeto de Revisão ABNT/CB-002, dezembro de 2018 |
| Extensão | 113 páginas PDF: 6 preliminares e páginas impressas 1–107 |
| Extração | Texto extraível, sem necessidade de OCR; figuras e tabelas presentes |
| Tamanho | 2.153.468 bytes |
| SHA-256 | `c64d55b8c35c6b26e3a6f8fbe6979248be1e285543df0f8e65c3c64b5ea77841` |
| Blob GitHub informado no inventário | `53fbeeb3fbb829aebd43335fcf34b183f81d88e1` |

A indicação interna de anexos como “normativos” pertence ao projeto de consulta; ela não confere valor normativo à publicação. Todas as referências a itens e páginas neste relatório se referem **exclusivamente ao projeto de dezembro de 2018**, salvo indicação expressa em contrário. A numeração não foi presumida idêntica à de edições publicadas posteriores.

### Edição publicada a conferir

O [boletim institucional CBIC de 27/04/2022](https://cbic.org.br/cbic-divulga-normas-tecnicas-da-construcao-em-boletim-e-portal-confira/) registra a publicação de emenda da NBR 6122 em **25/03/2022**. A [lista de referências da ANTT](https://www.gov.br/antt/pt-br/assuntos/rodovias/normativos-de-rodovias/organismos-de-inspecao-acreditados-oias-normas) identifica **ABNT NBR 6122:2022 — Projeto e execução de fundações**. São evidências institucionais de metadados, não leitura do texto consolidado nem confirmação de todas as alterações até a data deste relatório.

Falta obter e conferir o exemplar publicado aplicável, sua relação com a edição base e emendas, eventuais correções e situação atual no catálogo ABNT. **A leitura integral do projeto de 2018 não encerra a consulta integral da norma publicada.** Nenhum fator desse projeto foi adotado como regra vigente nesta tarefa.

## Cobertura da leitura

A equipe leu integralmente o documento fornecido: preliminares, seções 1–9, anexos A–R e bibliografia. O corpo principal foi confrontado com o código; os anexos foram divididos entre agentes para completar a leitura. As páginas abaixo permitem localizar o conteúdo sem reproduzir o PDF.

| Parte | Conteúdo | Páginas impressas | Páginas PDF |
| --- | --- | --- | --- |
| Preliminares | Apresentação, participantes e prefácio | sem numeração | 1–6 |
| Seções 1–3 | Escopo, referências e definições | 1–9 | 7–15 |
| Seção 4 | Investigações geológicas e geotécnicas | 9–13 | 15–19 |
| Seções 5–6 | Ações e segurança das fundações | 13–21 | 19–27 |
| Seção 7 | Fundações rasas | 21–25 | 27–31 |
| Seção 8 | Fundações profundas | 25–38 | 31–44 |
| Seção 9 | Desempenho e avaliação técnica | 38–41 | 44–47 |
| A | Fundação rasa: execução | 42 | 48 |
| B / C | Tubulões a céu aberto / a ar comprimido | 43–46 / 47–50 | 49–52 / 53–56 |
| D / E / F | Estacas de madeira / pré-moldadas de concreto / metálicas | 51–52 / 53–56 / 57–60 | 57–58 / 59–62 / 63–66 |
| G / H / I | Strauss / Franki / escavadas com trado sem fluido | 61–63 / 64–67 / 68–70 | 67–69 / 70–73 / 74–76 |
| J / K / L | Escavadas com fluido / raiz / trado vazado segmentado | 71–76 / 77–80 / 81–83 | 77–82 / 83–86 / 87–89 |
| M / N / O / P | Microestacas / hélice contínua / hélice de deslocamento / hélice com trado segmentado | 84–87 / 88–92 / 93–96 / 97–101 | 90–93 / 94–98 / 99–102 / 103–107 |
| Q / R | Estacas prensadas ou mega / simbologia informativa | 102–103 / 104–106 | 108–109 / 110–112 |
| Bibliografia | Referências bibliográficas | 107 | 113 |

## Incompatibilidades técnicas e lacunas observadas

O código faz estimativas de pré-dimensionamento. O campo `verifica` não representa uma verificação completa de fundações. As lacunas abaixo decorrem tanto da leitura do projeto de 2018 quanto da ausência dos respectivos dados e procedimentos na implementação. A confirmação de conformidade com a edição publicada continua pendente.

| Tema e referência no projeto DEZ/2018 | Evidência no código | Consequência e requisito a desenvolver |
| --- | --- | --- |
| Investigações: 4.1–4.6, pp. 9–13; região representativa: 6.1.1, p. 16 | `SondagemService.perfilEm()` interpola NSPT entre dois furos e usa camadas do mais próximo. `Perfil.nsptEm()` usa a leitura mais próxima, inclusive fora do trecho efetivamente ensaiado. | Faltam justificativa geológica das regiões, qualidade e extensão dos ensaios, incerteza, compatibilidade de cotas e tratamento explícito de informação insuficiente. Interpolação numérica não substitui interpretação geotécnica. |
| Ações da estrutura: 5.1, p. 13 | `FundacaoService.java:93–107` comenta ELS, mas seleciona somente ELU normal, toma `nMax()/1.4` e impõe um mínimo de 50 kN. | É necessário transportar conjuntos de esforços simultâneos por combinação e nível de interface, com sinais, horizontais e momentos. Dividir um máximo axial em módulo por uma constante não recupera universalmente as ações características. O piso de 50 kN pode ocultar ausência de dado e tração. |
| Água e ações do terreno: 5.2–5.4, p. 14; 7.2, p. 22; 8.2, p. 26 | `FundacaoService.java:109` constrói o perfil com nível d’água `null`; o cálculo não utiliza água, empuxos ou subpressão. | Faltam nível d’água e sua variação, condição drenada/não drenada, erosão, cargas laterais do terreno e solicitações durante execução, quando aplicáveis. |
| Interação fundação–estrutura: 5.5 e 6.1, pp. 14–16 | A superestrutura usa bases engastadas; a proposta de fundação não realimenta sua deformabilidade no modelo. | Identificar quando a interação altera a distribuição de esforços e implementar estudo compatível, ou registrar a exclusão justificada pelo caso. |
| Peso próprio: 5.6, p. 15 | Sapata considera acréscimo fixo de 10% de Nk; bloco e radier não têm fechamento explícito dos pesos e coberturas no fluxo de cargas. | A estimativa inicial precisa de verificação com geometria e materiais adotados. Não basta um percentual de qualquer esforço para representar automaticamente todos os pesos e aterros. |
| Segurança ELU e ELS: 6.2, pp. 16–20; 7.1/7.4 e 8.1/8.2.2 | `Sapata.verifica` compara pressão média com uma tensão estimada; estacas e radier são persistidos com `verifica=true`. | Faltam recalques totais/diferenciais, distorções, levantamentos e vibrações, além de verificações geotécnicas e estruturais de ruptura, deslizamento, tração e estabilidade aplicáveis. |
| Tensão admissível: 7.2–7.4, pp. 22–23 | `CalculoFundacoes.java:64–78` usa `mín(400,20×NSPT)` para qualquer solo, com média em um bulbo estimado. | O projeto admite métodos semiempíricos condicionados ao domínio de validade, dispersão e limitações regionais; não fornece essa fórmula como regra universal. Faltam fonte, calibração, aplicabilidade e controle por recalque. O bulbo escrito na memória usa o lado final, mas a média foi obtida com uma largura inicial diferente. |
| Excentricidade e horizontais: 7.6.2–7.6.3, pp. 23–24; 8.4, pp. 30–31 | Sapata recebe apenas Nk, bp, hp e cota; `FundacaoService` descarta momentos, cortantes e sinal axial. | Faltam pressão máxima de borda, área efetivamente comprimida com solo sem tração, tombamento, deslizamento e distribuição de esforços nas estacas/blocos. |
| Geometria e execução: 7.7, pp. 24–25; anexo A, p. 42 | A sapata pode ter lado inferior a 0,60 m; cota é entrada ou padrão de 1,50 m; altura não é especificação de lastro. | Conferir dimensões mínimas, divisas, clima/umidade, interferências entre cotas e sequência; detalhar lastro, inspeção do apoio, concretagem e reaterro. Ver reprodução abaixo. |
| Estrutura da sapata: 7.8.1, p. 25 | Calcula-se uma altura por critério geométrico de rigidez, sem armaduras. | Rigidez geométrica não comprova resistência. Faltam diagrama de reação compatível, flexão, cisalhamento/punção conforme aplicabilidade, ancoragem, ligação pilar–sapata, cobrimento e detalhamento pela edição aplicável da NBR 6118. |
| Capacidade das estacas: 6.2.1.2 e 8.2.1, pp. 18–19 e 26–28 | `CalculoFundacoes.java:81–124` fixa hipóteses de Aoki–Velloso/Décourt–Quaresma e usa `mín(Aoki,Décourt)/2`. | Faltam fonte/versão dos métodos, escolha explícita da estaca e processo, domínio dos coeficientes, região representativa, controle de dados, comprovação por ensaios e ELS. A coincidência de um fator global com uma situação descrita no projeto não valida o procedimento completo. |
| Comprimento e estratigrafia: 4.4/4.5.1 e 8.2 | `comprimentoResistente()` procura três NSPT ≥15, aceita impenetrável ou usa a última leitura; retorna 8 m se a lista estiver vazia. | Esses critérios automáticos não demonstram camada de apoio, natureza do impenetrável, atravessamento de matacão, continuidade do estrato ou recalque. Dados insuficientes precisam ser reconhecidos, em lugar de gerar comprimento aparentemente dimensionado. |
| Grupo e bloco: 8.3–8.5, pp. 30–32 | Número de estacas = teto de Nk/Qadm; espaçamento = 3d; bloco quadrado e altura heurística. | Faltam coordenadas e esforço em cada estaca, efeito de grupo, recalques, excentricidades executivas, ligação estaca–bloco, ancoragens e cálculo estrutural do bloco. Espaçamento isolado não comprova execução nem capacidade de grupo. |
| Estrutura da estaca: 8.6, pp. 33–38; anexo E, pp. 53–56 | O registro `Estaqueamento` não contém concreto, armaduras, emendas, idade, curvas de interação ou processo de cravação. | Faltam resistência estrutural, efeitos de manuseio/transporte/cravação, flexocompressão/flexotração, flambagem e durabilidade conforme o tipo e o terreno. |
| Radier: definição 3.35, p. 7; segurança 6.2 e fundações rasas 7 | `FundacaoService.java:123–136` troca a solução quando áreas de sapatas excedem 50% da área de referência, adota 0,40 m e dimensões em planta 0×0, com `verifica=true`. | É uma heurística de escolha, sem cálculo do radier: faltam contorno, contato solo–placa, rigidez, pressões, recalques, punção/flexão/cisalhamento e armaduras. O critério de área de sapatas não é comprovação de capacidade ou desempenho. |
| Desempenho/aceitação: 9.1–9.2, pp. 38–41; anexos executivos | Há aprovação de fluxo por usuário habilitado, mas não registros de ensaios e monitoramento vinculados às verificações. | Aprovação no software não substitui inspeções, ensaios, critérios de aceitação, monitoramento ou avaliação técnica exigíveis ao caso. |

### Reprodução executada: sapata pequena marcada como verificada

Foi invocado o código de domínio compilado, sem modificá-lo, para uma sapata com Nk = 50 kN, pilar 0,25×0,25 m, cota de assentamento 1,50 m, solo areia e leituras NSPT = 20 entre 1 e 4 m. O resultado foi:

```text
lado=0.4m; altura=0.3m; sigmaAdm=400.0kPa; pressao=344.0kPa; verifica=true
```

A conta interna é A = 1,1×50/400 = 0,1375 m²; o lado é arredondado para 0,40 m e a pressão exata é 343,75 kPa, apresentada arredondada. O item **7.7.1 do projeto DEZ/2018**, página impressa 24/PDF 30, registra lado mínimo de **0,60 m** para sapatas isoladas ou blocos. A página foi também conferida visualmente. Portanto, mesmo perante o texto histórico fornecido, o campo `verifica=true` cobre somente a comparação escalar de pressão, deixando a dimensão mínima sem verificação. A confirmação do item na edição publicada permanece necessária.

Essa reprodução é demonstração de comportamento do código; não é ensaio geotécnico nem validação de uma obra.

## Condições executivas identificadas nos anexos

Para a estaca pré-moldada presumida em `aokiVelloso()`, o anexo **E.2–E.10**, páginas impressas 53–56, aborda escolha da estaca/equipamento, esforços de manuseio e cravação, resistência do concreto em diferentes idades, emendas, controle de nega/repique/diagrama de cravação, arrasamento, ligação ao bloco e boletim individual. A regra de comprimento por três leituras NSPT não representa esses controles.

O processo executivo precisa ser identificado: o próprio anexo E admite percussão ou prensagem, e o anexo **Q**, páginas 102–103, pode ser pertinente à pré-moldada executada por prensagem. Nesse caso, há questões próprias de estrutura de reação, aferição do equipamento, carregamento, recalques e registros.

Os anexos B–D e F–P tratam de outras tipologias e processos. Não podem ser cobertos pelos coeficientes fixos de uma estaca pré-moldada/cravada. Entre as diferenças relevantes estão condições de solo/água para execução, sequência e proximidade temporal, estabilidade da perfuração, limpeza e contribuição da ponta, redução de seção em rocha/matacões, monitoração de perfuração e concretagem, armaduras e recebimento/aceitação de concreto, argamassa ou calda. O código precisa selecionar explicitamente os processos suportados e seus critérios.

O anexo **R** distingue resistência característica, resistência de cálculo, carga admissível, solicitação característica/de cálculo e parcelas de ponta/lateral. A origem e o significado dessas grandezas devem acompanhar os resultados. Arredondar capacidades para exibição não deve eliminar a precisão interna ou a rastreabilidade do cálculo.

## Pendências para uma verificação normativa efetiva

1. Obter o exemplar publicado aplicável da NBR 6122 e suas atualizações, preservando edição, fonte e hash. Comparar seus itens com o projeto de 2018; revisar a numeração e todas as regras antes de implementar.
2. Conferir as edições aplicáveis das referências necessárias: investigação/SPT, programação de sondagens, provas de carga, ensaios dinâmicos, projeto de concreto e pré-moldados, requisitos das estacas pré-fabricadas e ações/combinações. O PDF de 2018 lista referências, mas não fornece seus textos integrais.
3. Definir quais fundações/processos o produto suporta e os dados mínimos para cada um. Entrada incompleta deve produzir pendência técnica explícita.
4. Conectar reações e vetores simultâneos por combinação, sinais e nível de interface às verificações geotécnicas e estruturais; estabelecer a conversão entre métodos de valores admissíveis e de cálculo para cada caso justificado.
5. Especificar e verificar ELU e ELS para elemento isolado e conjunto, incluindo água, excentricidade, horizontais, tração, grupo e interação conforme aplicabilidade.
6. Vincular cada estado de verificação a cálculo, fonte/edição/item, dados, resultado e limite. Separar estimativa de capacidade, pré-dimensionamento geométrico, verificação estrutural e comprovação executiva.
7. Validar os procedimentos desenvolvidos com exemplos independentes que incluam compressão centrada, excentricidade, horizontais, tração, grupo de estacas, lençol d’água e recalques, selecionados conforme o escopo efetivamente suportado.

**Conclusão:** a leitura integral do documento fornecido foi concluída pela equipe e permitiu documentar lacunas concretas. O documento é um projeto de consulta de 2018, sem valor normativo. Não sustenta afirmar conformidade integral com a NBR 6122 publicada nem segurança das fundações geradas pelo motor atual.
