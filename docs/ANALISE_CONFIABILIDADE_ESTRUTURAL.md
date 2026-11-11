# Análise de confiabilidade estrutural: concreto, aço e resistência dos materiais

Data da análise: 9 de novembro de 2026 (America/Sao_Paulo).

## Conclusão e limites

O CivilConstructorAI contém um protótipo funcional de análise linear de pórticos e dimensionamento simplificado de concreto armado. A implementação inspecionada **não sustenta uma declaração de projeto estrutural completo, conformidade normativa integral ou ausência de erros técnicos**. Existem caminhos que podem produzir `Verifica` com verificações ausentes, hipóteses inadequadas ou informações incompletas. Recomenda-se bloquear aprovação/emissão como projeto executivo enquanto esses caminhos não forem corrigidos e validados.

A meta verificável deve ser: escopo de aplicação declarado; entradas completas e rastreáveis; verificações obrigatórias cobertas; recusa explícita de casos não suportados; validação independente; revisão por profissional habilitado. Nem BIM, nem IA, nem código determinístico eliminam incertezas de materiais, execução, solo e modelagem.

As seções 1–6 registram a análise e o plano da revisão original. As correções posteriores no motor e a extração de fontes estão descritas nos adendos 7–9. O conjunto não certifica uma estrutura nem valida integralmente as fórmulas contra as edições atuais das normas.

## Fontes e versão examinada

- PDF: [Estruturas_Paramétricas_e_IA.pdf](https://github.com/MatheusHaliski/CivilConstructorAI/blob/42ac4a36fb7167a53900cde4f01c281558e9b624/docs/rf/Estruturas_Parame%CC%81tricas_e_IA.pdf), 15 páginas, blob Git `81517e60b9eeea15dde76194aa77b2118ca706b4`.
- O PDF é composto por imagens: leitura visual das 15 páginas, com OCR auxiliar. Não contém um memorial de cálculo normativo completo nem referências suficientes para validar suas ilustrações numéricas.
- Código local: commit `5ad0fb5a0cc46dbcc5cc745df56caf09ff556c1b`. Comparação no GitHub com `42ac4a36fb7167a53900cde4f01c281558e9b624`: a única mudança é a inclusão do PDF; portanto os achados de cálculo também se aplicam àquela revisão remota.
- RF04, RF05, RF07, RF08 e classes citadas abaixo foram inspecionados. A existência de um requisito documentado não comprova sua implementação.
- Avisos oficiais/primários de publicação normativa e documentação Autodesk foram consultados; não houve acesso ao texto integral licenciado de todas as normas. Edição, emendas, itens e regras aplicáveis a cada obra precisam ser conferidos na fonte normativa autorizada.

## 1. O que corrigir ou complementar no próprio PDF

| Páginas | Observação | Complementação necessária |
|---|---|---|
| 1 | Mistura ilustração de pilar com fórmula de viga biapoiada e materiais C35/45/B500B, sem indicar referencial normativo. Para q=50 kN/m e L=8 m, qL²/8=400 kN·m; a capacidade ilustrada de 320, se expressa em kN·m, seria inferior a essa solicitação antes de discutir fatores. | Separar exemplo de viga e de pilar; definir apoios, unidades, cargas características/de cálculo, seção, materiais e código normativo. Não usar a ilustração como benchmark aprovado. |
| 2–4 | Um objeto BIM pode armazenar propriedades físicas; isso não significa que o software resolva automaticamente resistência, estabilidade e durabilidade. Coordenadas cartesianas/JSON não asseguram preservação de unidades, orientação ou ligações. | Especificar modelo analítico, unidades, tolerâncias, vínculos, leis constitutivas e relatório de perdas na interoperabilidade. |
| 3 e 6 | A NBR 7190 aparece junto de aço e madeira. | Separar madeira de aço estrutural e de aço para armaduras. NBR 7190 trata madeira; NBR 8800 trata aço/mistas no seu escopo; barras de armadura têm requisitos próprios. |
| 4 e 10 | O limite de 0,05 m é apresentado como se fosse universal; a página 10 propõe eliminar segmentos curtos e zerar offsets. | Consultar `Application.ShortCurveTolerance` no host Revit e converter unidades. Uma política de produto de 5 cm é distinta da tolerância do kernel e da tolerância estrutural. Bloquear alterações que afetem caminho de cargas; não apagar peças ou offsets fisicamente necessários. |
| 5–6 | Peso/centro de gravidade, componentes exatos e ausência de colisão não bastam para aprovar içamento ou fabricação. | Verificar estágios transitórios, resistência na idade de içamento, pontos de suspensão, efeitos dinâmicos, contraventamento temporário, capacidade do equipamento e ligações. CAM exige pós-processador validado, tolerâncias e controle de revisão. |
| 7 | SPT volumétrico e TIN são apresentados como determinantes automáticos de fundações e volumes exatos. | Tratar representatividade da investigação, interpretação geotécnica, nível d'água, recalques e incerteza espacial. TIN depende da qualidade e densidade dos dados; SPT isolado não determina uma fundação segura. |
| 8–10 e 14 | Separar LLM de auditor determinístico é adequado, mas “auditoria cartesiana” não equivale a validação completa da física. A relação apoios=vãos+1 vale para uma idealização específica, não para todo sistema. | Acrescentar equilíbrio, compatibilidade, constitutivas, estabilidade, ELU/ELS e domínio de aplicação. Permitir balanços, transferências, fundações e desníveis quando explicitamente modelados. Python ou Java não garantem correção por si mesmos. |
| 11–13 e 15 | Automação, dimensões BIM e redução da amostra de orçamento não demonstram segurança estrutural ou acurácia preditiva. | Separar evidências de produtividade, geometria, custo e resistência. Predição exige avaliação fora da amostra e incerteza; aprovação estrutural exige verificações de engenharia. |

A [documentação Autodesk de ShortCurveTolerance](https://help.autodesk.com/cloudhelp/2026/ENU/Revit-API-MainReference/files/html/0717b79e-efa9-e5ad-baed-999782c0e1e4.htm) descreve uma propriedade do aplicativo, não uma constante universal de 5 cm. A [documentação de unidades](https://help.autodesk.com/cloudhelp/2024/ENU/Revit-API/files/Revit_API_Developers_Guide/Introduction/Application_and_Document/Revit_API_Revit_API_Developers_Guide_Introduction_Application_and_Document_Units_html.html) registra comprimentos internos em pés e conversão por `UnitUtils`.

## 2. Achados prioritários no código

Referências são relativas à raiz do repositório e às linhas da revisão inspecionada. P0: pode comprometer o significado de uma aprovação técnica; P1: necessário para ampliar ou validar o escopo. Evidência estática e reprodução numérica são distinguidas.

### E01 — P0 — Material metálico pode seguir o cálculo de concreto

**Evidência:** `backend/cca-application/src/main/java/br/com/civilconstructorai/estrutura/DimensionamentoService.java:115` seleciona a rotina por tipo de elemento, sem despachar por família de material; `:201` extrai fck do texto do ID e assume 30 MPa se não houver correspondência. `AnaliseService.java:70` só fornece módulos de concreto C25/C30. `MontadorModeloAnalitico.java:172` também assume módulo de C30 para outros materiais, e `:27` fixa peso específico de concreto. `TabelaA.java` já admite `MAT_ACO_PERFIL_W` e CA-50.

**Consequência:** uma viga com perfil metálico não recebe propriedades reais do perfil, módulo de aço, peso próprio e verificações de aço. Aço CA-50 de armadura e aço estrutural não são intercambiáveis.

**Aceite:** cadastro tipado e versionado de materiais/seções; despachar por sistema estrutural; propriedades mecânicas e unidades explícitas; material incompatível/incompleto deve bloquear, sem fallback resistente. Até existir motor de aço validado, retornar `NAO_SUPORTADO`.

### E02 — P0 — Pilar dimensionado sem os momentos solicitantes e sem verificar ambos os eixos

**Evidência:** `DimensionamentoService.java:166` usa somente a normal da envoltória. `Nbr6118.java:140` não recebe Mx/My, dispõe de aproximação de capacidade e usa `lambda=3,46·l/h` em `:146`.

**Reprodução:** pilar b=0,20 m, h=0,80 m, l=3 m, Nk=600 kN e fck=30 retorna `Verifica` e λ=13. Para o mesmo comprimento efetivo adotado na rotina, a esbeltez geométrica do eixo fraco é aproximadamente 3,464·3/0,20=52. O exemplo demonstra a direção omitida; não estabelece por si só reprovação normativa do pilar.

**Aceite:** verificar tuplas concomitantes N–Mx–My por seção e combinação, sinais de tração/compressão, orientação, comprimentos efetivos e vínculos em ambos os eixos; superfície de interação e efeitos locais/globais de segunda ordem com hipóteses justificadas. Não aceitar a aproximação linear atual como verificação geral de flexocompressão.

### E03 — P0 — Lajes dimensionadas com cargas fixas e sem modelo resistente correspondente

**Evidência:** `DimensionamentoService.java:180` não recebe resultados da análise nem cargas cadastradas; `:186` fixa revestimento, alvenaria e sobrecarga. Vãos são obtidos pela caixa envolvente do contorno e momentos por coeficiente aproximado.

**Consequência:** alterar a carga de uso cadastrada pode alterar o pórtico sem alterar a armadura calculada da laje. Lajes irregulares, balanços, aberturas, continuidade e punção não são representados por esse procedimento.

**Aceite:** modelo de placa/casca ou método de faixas/tabelas com domínio estrito validado; cargas reais e combinações; armadura positiva/negativa nas direções e regiões pertinentes; punção, cortante, flechas, fissuras, aberturas e apoios. Caso fora do domínio deve ser recusado.

### E04 — P0 — Escolha de estribo pode violar as próprias entradas

**Evidência e reprodução executada:** `Bitolas.java:68`, chamada `estribo(100, 5)`: retorna Ø10 a cada 7,5 cm, fornecendo aproximadamente 20,93 cm²/m para uma necessidade de 100 cm²/m. Viola simultaneamente área necessária e espaçamento máximo de 5 cm.

**Aceite:** devolver solução comprovadamente suficiente ou erro “arranjo inviável”. Revalidar área e espaçamento depois da escolha, limites geométricos, diâmetro e número de ramos. Nenhum fallback pode apresentar armadura insuficiente como solução válida.

### E05 — P0 — Envoltórias perdem componentes, sinal e concomitância

**Evidência:** `SolverMef.java:208` transforma normal em máximo absoluto; `:210` resume momento apenas por My. `DimensionamentoService.java:100` usa quatro escalares e não utiliza Mz e torção. Em vigas, momentos positivos/negativos se tornam uma única necessidade de armadura.

**Aceite:** armazenar esforço assinado N, Vy, Vz, T, My, Mz com posição e combinação; verificar interações com valores concomitantes, e não máximos independentes. Detalhar faces superior/inferior e regiões de apoio/vão. Conservar máximos para visualização sem substituir os dados de dimensionamento.

### E06 — P0 — Caminho de cargas e vínculos usam hipóteses automáticas não justificadas

**Evidência:** `MontadorModeloAnalitico.java:26` usa tolerância de ligação de 30 cm; bases inferiores são engastadas automaticamente. `:116` reparte carga de lajes proporcionalmente ao comprimento das vigas de contorno; `:122` usa pilares do pavimento sem teste geométrico de inclusão no polígono. `:146` aplica carga pontual no último nó, sem posição na barra. Cargas sem alvo compatível são ignoradas por `continue`. Pilares contínuos não são subdivididos por todos os níveis intermediários nesse montador.

**Consequência:** pode haver conexão artificial, caminho incorreto ou perda silenciosa de cargas; a distribuição adotada não representa todas as condições de laje. A ausência de diafragma não é universalmente conservadora, apesar do comentário da classe.

**Aceite:** conectividade explícita validada pelo engenheiro, excentricidades/trechos rígidos/liberações, subdivisão de barras e pilares nos encontros, posição/direção real das cargas; contabilização de toda carga aplicada e transferida; erro para cargas sem caminho resistente. Justificar laje-diafragma, grelha/placa e interação solo–estrutura conforme o caso.

### E07 — P0 — Vento e combinações cobrem somente um subconjunto

**Evidência:** `MontadorModeloAnalitico.java:151` só aplica vento em +X. `GeradorCombinacoes.java:27` agrupa ações apenas como G/Q/W, usa fatores fixos e uma única expressão frequente. `casoCarga` não se torna caso independente no modelo analítico.

**Aceite:** direções e sentidos relevantes, pressão/sucção e torção/excentricidades quando aplicáveis; fatores dependentes de uso e norma; ações favoráveis/desfavoráveis, variáveis principais/acompanhantes, carregamentos alternados e combinações de serviço pertinentes. Separar casos independentes, ações e combinações. Incluir situações construtivas, excepcionais/dinâmicas/sísmicas quando exigidas pelo escopo ou bloquear esse uso.

### E08 — P0 — ELS de vigas reutiliza fórmula de viga biapoiada para o pórtico

**Evidência:** `DimensionamentoService.java:141` converte Mmax em q equivalente por 8M/L² e usa o comprimento total do elemento; `Nbr6118.java:171` em diante calcula flecha de viga biapoiada sob carga uniforme. `:194` fixa efeito diferido em 1,32. Fissuração usa Mmax positivo e limite fixo de 0,3 mm.

**Aceite:** resposta de serviço compatível com apoios, continuidade, trechos e cargas reais; rigidez fissurada e efeitos de tempo apropriados; flecha relativa ao referencial correto; limites por utilização/elementos não estruturais; fissuração por face, ambiente e arranjo efetivo. Concreto fissurado e fluência devem influir no modelo onde necessário, não só em fórmula isolada.

### E09 — P0 — Checagens ausentes podem ser interpretadas como aprovadas

**Evidência:** `DimensionamentoService.java:128` e `:169` tratam ausência de envoltória como solicitação zero/quase zero; ELS ausente pula verificações; elementos `rascunhoIa` são omitidos. A aprovação em `Dimensionamento.java:84` bloqueia pelo número de resultados falsos, sem matriz de cobertura obrigatória.

**Aceite:** estados distintos `ATENDE`, `NAO_ATENDE`, `NAO_VERIFICADO`, `NAO_APLICAVEL` e `NAO_SUPORTADO`; inventário de todos os elementos e verificações exigidas; dados/combinações faltantes bloqueiam; conjunto vazio de resultados não deve permitir aprovação. Mostrar cobertura e hipóteses na API, tela e relatório.

### E10 — P0 — Validade do resultado não inclui todas as entradas

**Evidência:** `AnaliseEstrutural` armazena versão do modelo, mas não snapshot/versionamento completo das cargas, vento, materiais, norma e solver. `CargasService.lancar/remover/definirVento` não invalida resultados nesse fluxo. `DimensionamentoService.decidir:216` não revalida a versão/snapshot no instante de aprovar.

**Aceite:** snapshot imutável e hash de geometria, topologia, seções, materiais, cargas, vínculos, malha, normas e versão do motor. Toda mudança relevante invalida análise, dimensionamento, aprovação e documentos dependentes; comparar atomicamente antes de aprovar ou emitir. Preservar resultados antigos como histórico, identificados como superados.

### E11 — P0 — Detalhamento e validade das fórmulas são incompletos

**Evidência:** `DimensionamentoService.java:34` fixa cobrimento em 3 cm e altura útil por desconto constante. `Bitolas.escolher:26` não recebe largura útil nem agregado; não pode comprovar acomodação, espaçamento ou camadas. `Nbr6118` fixa CA-50 e aplica parâmetros de flexão sem guarda explícita para a faixa de resistência. A chamada direta com fck=100 MPa retorna `Verifica`, embora a própria descrição limite parte das hipóteses a fck≤50. Esse último teste é da rotina isolada; não prova que o cadastro padrão exponha C100 pela interface.

**Aceite:** catálogo físico de concreto e armadura, faixa de validade explícita, modelos constitutivos adequados, ambiente/cobrimento/vida útil, bitolas e posições reais, iteração de d com o arranjo. Verificar ancoragens, emendas, ganchos, confinamento, nós, armadura de pele e interações de cortante/torção onde aplicáveis. Valores finitos, dimensões positivas, 0<d<h e unidades devem ser pré-condições. Não arredondar demanda/capacidade antes da decisão nem arredondar área requerida para baixo.

### E12 — P0 — Aço estrutural não tem motor normativo implementado no fluxo inspecionado

**Evidência:** RF07 promete NBR 8800, mas o fluxo examinado chama exclusivamente `Nbr6118`; seção analítica é retangular, sem catálogo real de perfis W/tubulares e sem verificação específica de ligações.

**Aceite:** propriedades A, Ix/Iy, W/Z, J e empenamento conforme perfil, aço fy/fu/E/G e orientação; tração/compressão, flambagens e instabilidades locais/lateral com torção, flexão, cortante e interações; comprimentos destravados e contraventamentos; parafusos/soldas/chapas/bases/chumbadores, rigidez das ligações e fases construtivas. Estruturas mistas, perfis formados a frio, fadiga e incêndio precisam de escopo e normas próprios quando aplicáveis. Certificação do material e inspeção de fabricação não são substituídas pelo catálogo BIM.

### E13 — P1 — Controle numérico e validação independente insuficientes

**Evidência:** `SolverMef.java:21` amostra 11 seções por barra, usa matriz densa e superposição linear. O fluxo não apresenta verificação automática de resíduos/equilíbrio global, estimativa de condicionamento e convergência de discretização. Arredondamento ocorre dentro do resultado consumido pelo dimensionamento.

**Aceite:** extremos de esforços obtidos analiticamente ou por refinamento controlado; testes de equilíbrio de forças/momentos, resíduos normalizados, simetria/energia e invariância de orientação; tratamento de mecanismos, quase-mecanismos, geometria degenerada e valores não finitos. Separar precisão interna de apresentação. Definir limite de tamanho e erro; não confundir convergência do solver com adequação física.

### E14 — P0 para uso executivo — Fundações precisam de análise além de correlação SPT

**Evidência:** `CalculoFundacoes.java` usa correlação fixa σadm≈20·Nspt, geometrias simplificadas e capacidade empírica de estacas. `FundacaoService.java:107` impõe carga mínima de 50 kN quando falta reação; `:136` gera radier de 40 cm marcado como verificado. Isso não demonstra verificação estrutural/geotécnica completa.

**Aceite:** planta de cargas com N, Mx/My, Hx/Hy concomitantes e convenção de ELU/ELS coerente; interpretação geotécnica autorizada, recalques e interação, capacidade/contato/excentricidade, estabilidade, punção/cortante/flexão e ancoragens. Grupos de estacas e blocos exigem distribuição, efeitos de grupo e modelo resistente; radier exige análise própria. Sem reação/sondagem suficiente, bloquear em vez de inventar entrada.

### E15 — P1 — Auditor geométrico também precisa de revisão

**Evidência:** `Auditor.java:113` zera offsets, `:150` recorta geometrias pela caixa de eixos e `:192` remove o aviso de apoio faltante. Esses comportamentos não comprovam caminho de cargas nem preservam necessariamente a intenção estrutural. Existem auditores em Java e TypeScript; equivalência precisa ser testada.

**Aceite:** diferenciar sugestão de reparo de alteração aplicada; preservar balanços e offsets intencionais, registrar diferenças e exigir nova análise após mudança. Usar grafo resistente e equilíbrio; não usar caixa envolvente ou distância euclidiana como prova de estabilidade. Alteração de elemento estrutural exige reauditoria de dependências.

## 3. Base normativa a revisar

O projeto deve fixar uma edição normativa por empreendimento, com emendas, erratas, data e regras de transição justificadas. Atualizar texto de README não atualiza cálculo.

- **Concreto:** verificar NBR 6118:2023 com Emenda 1:2026, equivalente à NBR 6118:2026 segundo [comunicado da ABECE](https://site.abece.com.br/nbr-6118-tem-emenda-publicada-pela-abnt/). O comunicado também destaca a revisão da Avaliação Técnica de Projeto (ATP); critérios concretos precisam ser extraídos do texto autorizado.
- **Ações e segurança:** a [ABECE informa publicação da NBR 8681:2025](https://site.abece.com.br/em-vigor-a-nova-nbr-8681/), substituindo a versão de 2003 referenciada no RF04. Revisar combinações e tabelas, sem presumir que todos os valores existentes devam mudar.
- **Aço/mistas:** NBR 8800:2024, versão corrigida 2025, conforme [comunicado da ABECE](https://site.abece.com.br/nbr-8800-tem-versao-corrigida-publicada/); houve correções também de fórmulas.
- **Vento:** NBR 6123:2023, publicação confirmada pela [ABECE](https://site.abece.com.br/em-vigor-a-nova-nbr-6123/); conferir correções e edição aplicável no catálogo antes de implementar.
- **Madeira:** a [ABECE confirma NBR 7190-1:2022](https://site.abece.com.br/nova-nbr-7190-e-publicada/); não aplicar esse código ao aço.
- **Demais famílias a incluir na matriz, conforme escopo:** NBR 6120 (ações), NBR 7480 (armaduras), NBR 12655 e NBR 14931 (concreto e execução), NBR 5738/5739 (ensaios), NBR 8522 (módulo), NBR 6122 e NBR 6484 (fundações/SPT), NBR 9062 (pré-moldados), normas de incêndio, desempenho, estruturas mistas e perfis formados a frio quando pertinentes. Edições dessas famílias não foram integralmente verificadas nesta análise.

Cada regra precisa de identificação, norma/edição/item, hipóteses, grandezas/unidades, intervalo de aplicação, exemplos independentes e teste. Não automatizar cláusulas a partir da memória de uma LLM ou das imagens do PDF.

## 4. Arquitetura mínima de confiabilidade

1. **Contrato de entrada:** sistema estrutural, materiais de projeto e ensaiados, geometria/unidades, orientação, vínculos e fundações, cargas/casos, ambiente e vida útil. Informações desconhecidas permanecem desconhecidas e bloqueiam verificações dependentes.
2. **Modelo físico e analítico distintos:** mapeamento rastreável de elemento BIM a barras/placas/nós, trechos, excentricidades e apoios; relatório de hipóteses e perdas de conversão.
3. **Núcleo determinístico versionado:** montagem, equilíbrio e análise estrutural com domínio declarado; motores específicos de concreto/aço; checagens ELU/ELS e detalhamento. A IA pode propor entradas, nunca transformar uma checagem ausente em aprovação.
4. **Relatório por verificação:** solicitação, resistência/limite, utilização, combinação e seção governantes, regra, precisão, estado, hipóteses e limitações. Manter todos os estados mesmo após uma falha.
5. **Liberação técnica:** cobertura completa no escopo suportado, versão íntegra, validação independente e revisão profissional/ATP quando aplicável. CREA/2FA e um campo ART são controles de autoria; não validam a matemática.
6. **Ciclo de vida:** resultados superados bloqueados para emissão, arquivos de fabricação vinculados à revisão aprovada, registro de alterações de campo, inspeções e controle tecnológico. Resistência nominal de catálogo não comprova material executado.

## 5. Programa de testes e critérios de liberação

| Grupo | Casos mínimos | Resultado exigido |
|---|---|---|
| Regressões P0 | Estribo inviável; perfil W; laje com carga alterada; pilar com momentos nos dois eixos; ausência de ELU/ELS; elemento sem esforço; revisão de cargas após análise | Falha anterior reproduzida; correção validada; casos incompletos não podem receber aprovação |
| Estática e resistência dos materiais | Viga biapoiada, engastada, balanço, contínua, carga pontual fora do meio, carga trapezoidal se suportada, pórtico espacial e torção | Comparar reações, esforços e deslocamentos com solução independente; conservar sinais e unidades |
| Propriedades/metamórficos | Rotação de 90°, inversão de nós, permutação de IDs, subdivisão de barra, mudança de unidade e escala de carga no regime linear | Resposta física equivalente sob a transformação; tolerâncias justificadas por grandeza |
| Concreto | Flexão positiva/negativa, compressão e tração excêntricas, cisalhamento/torção, duas direções de pilar, segunda ordem, lajes/punção, ELS, ancoragem e encaixe real das barras | Exemplos calculados independentemente e revisados por especialista; casos-limite dos domínios |
| Aço | Perfis e graus de aço distintos, instabilidades, comprimentos destravados, ligações e bases, ações combinadas | Benchmarks independentes por modo de falha e norma/edição; não reutilizar resposta de concreto |
| Numérico | Mecanismo, quase-mecanismo, barras curtas, propriedades inválidas, malhas/refinamento e extremos entre amostras | Diagnóstico explícito ou convergência demonstrada; sem resultados finitos artificiais para entrada inválida |
| Integração | Navegador → cargas/modelo → solver → dimensionamento → aprovação → documento; mudanças e concorrência | Mesmas entradas e versões em todas as etapas; invalidação atômica e bloqueios visíveis |
| Validação externa | Exemplos manuais e ao menos um solver independente, com hipóteses compatibilizadas | Explicar diferenças e incertezas; não considerar concordância de dois programas como prova absoluta |

Tolerâncias devem combinar limite absoluto e relativo por grandeza e escala, definidos antes dos resultados. Não adotar “1% em tudo” como critério universal; aprovação normativa deve usar valores internos sem arredondamento de exibição.

### Verificações efetivamente executadas nesta análise

- Leitura das 15 páginas e comparação da árvore Git remota com o checkout.
- Compilação isolada das classes atuais `Nbr6118` e `Bitolas` com Java 21 e quatro chamadas diretas, sem modificar o motor: estribo inviável, pilar 20×80 cm, fck=100 MPa, cortante negativo. A última também retorna `Verifica`, evidenciando falta de guarda da rotina; no serviço atual o cortante vem como magnitude positiva.
- Execução offline: `/workspace/.tools/cca-mvn -q -o -pl cca-domain -Dtest=Nbr6118Test,SolverMefTest,MontadorModeloAnaliticoTest,GeradorCombinacoesTest test`.
- Resultado: **17 testes passaram** (8 + 4 + 2 + 3). Esses testes comprovam apenas os cenários programados; não cobrem os achados acima. Não foram executados nesta análise ensaios físicos, revisão de uma obra real, testes com software estrutural externo ou validação normativa integral.

## 6. Ordem de implementação proposta

1. **Contenção:** estados de cobertura e bloqueio de casos não suportados; impedir fallback de material/esforço/armadura; snapshot e invalidação. Corrigir E04 e criar regressões.
2. **Base analítica:** materiais/seções/unidades, conectividade, caminho de cargas, combinações e vento; validação numérica e benchmarks.
3. **Concreto no escopo declarado:** pilares N–Mx–My e segunda ordem, vigas com faces/regiões, lajes reais, ELS e detalhamento executável.
4. **Aço em módulo próprio:** propriedades de perfis, estabilidade, resistências e ligações, com benchmarks e edição normativa fixada.
5. **Fundações e etapas construtivas:** resultados completos, investigação geotécnica, montagem/pré-moldados e fabricação quando incluídos no produto.
6. **Liberação controlada:** corpus independente revisado, revisão profissional/ATP aplicável, relatórios rastreáveis e teste completo até emissão. Expandir o escopo apenas quando cada família tiver evidência equivalente.

Até essa validação, apresentar o recurso como estudo/pre-dimensionamento com limitações explícitas e impedir que o status global sugira aprovação de verificações que não existem.


## 7. Primeiro lote de contenção — 10/11/2026

- E04: estribos inviáveis agora geram erro; arranjo retornado deve atender simultaneamente área e espaçamento. A escolha longitudinal rejeita entradas inválidas, respeita o catálogo e mantém área sem arredondamento interno.
- E01/E12: análise e dimensionamento usam o cadastro de concreto e bloqueiam materiais incompatíveis; o motor de aço ainda não foi implementado.
- E09: relatório distingue falha de cálculo, verificação incompleta e material não suportado. IA pendente não é omitida. Resultados vazios, combinações ou esforços ausentes não viram aprovação. Cálculos simplificados permanecem `NAO_VERIFICADO` mesmo quando suas fórmulas isoladas passam; a aprovação técnica permanece bloqueada.
- E10, parcial: aprovação compara a versão geométrica atual. **Ainda falta snapshot completo de cargas, materiais, vínculos, norma e solver.** Os bloqueios de pré-dimensionamento não substituem essa implementação futura.
- API reinterpreta resultados legados sem estado como não verificados; esses dados não alimentam o consumo de aço aprovado. A interface informa o escopo preliminar e os estados de cobertura.
- Não foram implementados neste lote pilares N–Mx–My, placas/lajes, vento completo, novos códigos normativos ou validação independente de engenharia. A lista original acima é o retrato da revisão analisada e continua servindo de backlog técnico; somente os itens explicitamente descritos neste adendo foram alterados.

Validação do primeiro lote: 89 testes unitários do backend e 5 testes de integração de dimensionamento passaram; estes últimos usam PostGIS e Valkey em Testcontainers. Os 20 testes do frontend, typecheck, build e estrutura dos documentos RF passaram. Um teste Playwright com respostas controladas confirmou o aviso de pré-dimensionamento, estado “NÃO VERIFICADO” e aprovação desabilitada; ele não substitui autenticação/API reais, exercitadas separadamente pelos testes de integração. Foram adicionados 13 testes unitários de regressão, além dos casos de API e navegador. Nenhum desses resultados certifica a segurança de uma obra.

## 8. Ensaios de desenho e cálculo — 10/11/2026

O [memorial reproduzível](validacao/MEMORIAL_ENSAIOS.md) documenta cinco benchmarks e o conjunto D1 desenhado de 6×4×3 m, com comparação independente. A [matriz normativa](validacao/MATRIZ_NORMATIVA_ABNT.md) rastreia 18 procedimentos e suas pendências. Foi corrigida a busca de extremos de momento de carga uniforme nos dois planos (E13, parcial), preservada precisão no solver e corrigida a representação 3D de vigas/lajes. A amostragem de flechas gerais desta rodada foi corrigida na etapa seguinte. Nenhum desses ensaios comprova segurança global ABNT ou libera aprovação. Detalhes e resultados efetivamente executados estão em `docs/validacao/README.md`.

## 9. Extração documental e revisão independente — 11/11/2026

O [catálogo de fontes](validacao/CATALOGO_FONTES_ENGENHARIA.md) organiza 31 arquivos únicos fornecidos no GitHub: 18 PDFs, totalizando 1.399 páginas, e 13 DOCX. Hashes, edições, localizadores e lacunas estão registrados. O exemplar de concreto é NBR 6118:2014 corrigida; o de fundações é projeto de revisão de dezembro/2018 sem valor normativo. As edições atuais e normas específicas ausentes permanecem pendências; referências secundárias e remissões não as substituem.

Foram corrigidos os máximos da resultante transversal entre estações, o peso próprio axial distribuído dos pilares, as demandas/conclusões fixas do memorial e o coeficiente algébrico arredondado do bloco de flexão declarado. Entradas não finitas ou fora do domínio das rotinas simplificadas são recusadas; as decisões preservam a precisão interna. Regressões reproduziram as falhas e conferiram as correções com referências independentes. O estado de cada achado e os resultados atualizados constam da matriz e do memorial.

Os dados históricos extraídos são referências rastreáveis e não passam automaticamente ao catálogo de produção. Pilares N–Mx–My, lajes gerais, combinações atuais, estabilidade, serviço, detalhamento, aço estrutural e fundações completas continuam sem comprovação global.
