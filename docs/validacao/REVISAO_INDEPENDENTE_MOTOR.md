# Revisão independente do motor estrutural e das evidências

Data: 10/11/2026. Escopo: código disponível nesta árvore de trabalho e ensaios publicados em `docs/validacao`. **A resposta atual é NÃO VERIFICADO: obter o texto integral das normas é necessário à auditoria normativa, mas não completa os métodos, dados e testes ausentes do programa.** Este documento não certifica estruturas nem confirma cláusulas não consultadas.

Os achados A1–A3 preservam o snapshot anterior às correções desta sessão. Referências de linha nessa reprodução são do snapshot; inserções posteriores podem deslocá-las. O adendo ao final registra o estado verificado após as correções, sem apagar a evidência original.

## Evidências e limites da revisão

Foram examinados `MEMORIAL_ENSAIOS.md`, `MATRIZ_NORMATIVA_ABNT.md`, o domínio `estrutura/mef`, `Nbr6118`, `GeradorCombinacoes`, `AnaliseService`, `DimensionamentoService` e o comparador Python. Os comentários que mencionam itens da ABNT foram tratados como localizadores a conferir, não como fonte normativa. As reproduções abaixo usam os JSON existentes, cópias em memória e formulação mecânica independente; não foi alterada uma obra.

Os seis casos demonstram resposta elástica de modelos idealizados: cinco referências planas e um conjunto espacial simétrico. O equilíbrio e as pequenas diferenças Java/Python são evidência útil de implementação dessa hipótese, mas não verificam o modelo de uma construção real, resistência global, instabilidade ou detalhamento. A decomposição de D1 repete hipóteses do montador, incluindo repartição por comprimento de vigas e peso próprio dos pilares aplicado no topo.

Os bloqueios atuais são apropriados: `DimensionamentoService.java:42–56` impede que resultados legados ou estimativas simplificadas se tornem `ATENDE`; `:279–283` rejeita aprovação sem cobertura e `:310–315` exclui esses resultados de quantitativos aprovados. Material incompatível é rejeitado no montador. Estes controles devem permanecer enquanto os métodos abaixo não forem demonstrados.

## Achados concretos reproduzidos

### A1 — demanda resistente independente dos esforços atuais

No snapshot examinado, `scripts/validar_estruturas.py:152–176` não recebia entradas ou resultado Java: usava Md=63 kN·m e Vd=42 kN para B1/B2 e Md=11,025 kN·m para L1, escritos no código. A chamada em `:283` não dependia do resultado atual. Em `:259`, o texto «Flexão idealizada atende» era literal, independente do booleano calculado.

Reprodução somente em memória: multiplicar G/Q de B1 e todos os seus esforços/deslocamentos da saída linear por três. A função `conferir` continuou passando, coerentemente com a linearidade; a demanda real passou a **189 kN·m**, mas `resistencia_parcial` manteve **63 kN·m**, MRd=110,069410550 kN·m e `atende_equilibrio_flexao=True`. Isso pode deixar a conclusão parcial desatualizada mesmo quando a comparação mecânica passa. Não foi demonstrado desbloqueio de aprovação global: o estado global continuava `NAO_VERIFICADO`.

Também havia resultados narrativos fixos no gerador de memorial: `:238` (pesos, reações e cargas D1), `:243` (q/R/M dos casos e somas P1) e `:260` (Vsd/VRd e estribos). As tabelas calculadas podem mudar sem atualizar essas frases. Hipóteses deliberadas de armadura/material são distintas desse problema e devem continuar explícitas.

**Correção em andamento pela equipe principal:** ligar Md/Vd à saída atual por caso/combinação; obter dimensões da fixture; produzir status e narrativa a partir dos resultados; adicionar regressão de carga triplicada. Critério de aceitação: a demanda 189 excede MRd e a conclusão parcial muda para não atende, mantendo o estado global não verificado. Exigir também erro explícito para caso/combinação ausente, sem reutilizar números anteriores.

### A2 — máximo de deslocamento ainda é amostrado

`SolverMef.java:198–216` calculava `flechaMax` nas 11 estações e nas raízes do cortante adicionadas para extremos de momento. Uma raiz de cortante não determina o extremo de deslocamento. A interpolação de deslocamento em cada estação pode estar correta e seu máximo ainda ser subestimado.

Reprodução em P1/V1, ELU_TESTE, usando a formulação independente Euler–Bernoulli em 100.001 posições:

| Grandeza | Resultado |
|---|---:|
| `flechaMax` Java | 2,761996975 mm |
| Maior valor da referência densa | 2,763916257 mm |
| Posição da referência | s=2,4405 m |
| Subestimação neste caso | 0,0694407% |

A dimensão pequena deste erro específico não estabelece limite para outros casos. A referência densa localiza aproximadamente o extremo; não é prova de tolerância universal. Para deslocamento transversal espacial, procurar os zeros de `vy·vy' + vz·vz'`, além das extremidades, em vez de usar separadamente apenas extremos de cada componente. A correção deve declarar a tolerância e testar perfis assimétricos e os dois planos simultâneos. A equipe implementará a correção após esta revisão. Mesmo o máximo elástico correto não substitui a verificação normativa de flecha final.

### A3 — peso próprio de pilar concentrado no topo

`MontadorModeloAnalitico.java:64–65` concentra todo o peso próprio do pilar no nó superior. Para prisma vertical isolado de área A, comprimento L, módulo E e peso total W, o encurtamento por peso próprio uniformemente distribuído é `WL/(2EA)`; aplicar W inteiro no topo dá `WL/(EA)`. A reação de base se conserva, mas o diagrama de normal e a parcela de encurtamento não representam o peso distribuído.

Para A=0,25×0,25 m², L=3 m, E=30 GPa e peso específico 25 kN/m³: W=4,6875 kN; encurtamento correto dessa parcela=3,75 µm; hipótese atual=7,5 µm. A referência D1 em `scripts/validar_estruturas.py:119–130` repete a concentração e, por isso, não detecta a diferença. Trata-se de limitação física explícita do montador, não de falha do equilíbrio do solver para a carga que recebeu.

Próxima correção: representar carga axial distribuída com forças nodais consistentes e recuperação do esforço/deslocamento interno correspondente. Critérios: normal nula no topo livre, W na base, reação W e encurtamento `WL/(2EA)`, em orientações diferentes e com sobrecarga nodal adicional. Não foi corrigido nesta revisão.

## Bloqueadores de uma verificação completa

As referências abaixo são locais de implementação, não confirmação de exigência ou número de cláusula. As cláusulas e suas condições deverão ser ligadas a cada método quando o acervo autorizado estiver disponível.

| Prioridade / procedimento | Evidência no código atual | O que falta demonstrar antes de `ATENDE` |
|---|---|---|
| Alta — pilares | `DimensionamentoService.java:212–223` usa apenas Nk; `Nbr6118.java:146–180` recebe Nk/geometria, sem momentos reais, dois eixos ou arranjo de barras | Resistência N–My–Mz por seção, sinais e concomitância; vínculos/comprimentos efetivos; segunda ordem local/global, imperfeições e critérios de aplicação/dispensa |
| Alta — laje real | `DimensionamentoService.java:226–244` usa caixa envolvente, q fixo em `:232` e coeficiente aproximado; não recebe resultado analítico da placa | Ações reais, apoios, direções, continuidade, bordas/aberturas e modelo válido de placa/faixas; flexão, serviço, cortante/punção aplicáveis; L1 não comprova D1 |
| Alta — caminho de cargas | `MontadorModeloAnalitico.java:27,45,54–65,96–154` funde nós até 0,30 m, supõe engastes, mantém pilar sem subdivisão e distribui laje por comprimentos | Regras explícitas e auditáveis para vínculos, cruzamentos, pavimentos, excentricidades e diafragmas; carga sem suporte/alvo válido deve gerar diagnóstico; conferir conservação e localização das ações |
| Alta — ações/combinações | `GeradorCombinacoes.java:18–44` agrupa G/Q/W com fatores constantes; `MontadorModeloAnalitico.java:158–172` aplica vento simplificado em +X | Edição e condições das tabelas; independência e simultaneidade de casos; ações favoráveis/desfavoráveis, principais/acompanhantes, padrões e sentidos relevantes; situações especiais conforme escopo |
| Alta — interação de esforços | `SolverMef.java:212–215` conserva máximos escalares; `DimensionamentoService.java:120–129` agrega máximos independentes e não consome Mz/T | Tuplas assinadas N, Vy, Vz, T, My, Mz por seção e combinação; combinações concomitantes para cada interação e orientação de armaduras; não substituir domínio resistente por máximos soltos |
| Alta — serviço | `DimensionamentoService.java:186–198` usa primeira combinação QP/frequente e converte M positivo em q equivalente; `Nbr6118.java:187–224` aplica solução biapoiada e constantes assumidas | Aplicabilidade ao vão/balanço/pórtico real; face tracionada e sinal; EI fissurado, fluência/retração, idade/história de cargas, armadura efetiva e limites conforme uso; envolver todas as combinações pertinentes |
| Alta — detalhamento | `DimensionamentoService.java:173,183,205–218,235–243` adota d/cobrimentos e quantitativos aproximados | Geometria real da seção e das camadas, cobrimento/ambiente, espaçamento e alojamento, ancoragens/emendas, apoios, confinamento e armaduras complementares; uma área de aço suficiente não libera desenho executivo |
| Alta — material e escopo | `MontadorModeloAnalitico.java:38–41,179–181`; `AnaliseService.java:70–71` seleciona material/E por cadastro | Propriedades e origem rastreáveis, idade/ambiente/controle tecnológico; motor próprio para aço estrutural e ligações antes de aceitar esse material; não extrapolar concreto/armaduras para perfis metálicos |
| Alta — vínculo da aprovação às entradas | `AnaliseService.java:55–74` e `DimensionamentoService.java:89–91,266–269` usam versão geométrica como referência principal | Snapshot imutável também de ações, vento, materiais, vínculos, norma/motor e hipóteses; alterações em cada entrada devem invalidar resultados/aprovação anteriores |
| Média — precisão e robustez | `SolverMef.java:303–385` usa LU densa e base local automática | Casos mal condicionados/unidades escaladas, resíduos do sistema e equilíbrio publicados; orientação de seção retangular; malha e estabilidade numérica; não confundir solução algébrica com estabilidade estrutural |
| Alta quando aplicável — restante do sistema | Não coberto por B1/B2/C1/P1/L1/D1 | Fundação/solo, ligações, execução e controle; incêndio, sismo, fadiga, fases construtivas e sistemas especiais conforme decisão documentada de aplicabilidade |

Observação adicional sobre completude dos esforços: `DimensionamentoService.java:145–148` verifica a presença de algum resultado por elemento nas combinações obrigatórias, mas um elemento dividido em várias barras precisa de cobertura completa de todos os trechos e verificações requeridas. A laje está excluída desse teste de presença. A rotina de aprovação não deve aceitar cobertura nominal como cobertura técnica.

## Ensaios necessários, além dos exemplos atuais

1. **Mecânica:** soluções fechadas assinadas para carga axial distribuída, vigas biapoiadas/engastadas, torção e flexão nos dois planos; pórtico espacial assimétrico com orientação de seção; equilíbrio global e por elemento; máximos internos fora da malha de publicação.
2. **Montagem:** dois ou mais pavimentos e pilar atravessando nível; viga apoiada no meio de outra; regiões de laje distintas, borda livre, suporte fora do polígono, abertura, carga em alvo inexistente e mudança de tolerância. Conferir carga total, posição/resultante e conectividade, não somente reação total.
3. **Resistência:** comparar seções e pilares contra cálculo independente com materiais/armaduras explícitos; testar fronteiras de domínio e desigualdades, inversão do sinal, redução de armadura e aumento de carga. Exercitar N–My–Mz, torção/cortante e zonas especiais dentro do escopo implementado.
4. **Serviço:** casos biapoiado, balanço e contínuo com mesmo momento máximo e respostas distintas; faces tracionadas opostas; várias combinações; idade de carregamento e fissuração alterando resultados.
5. **Rastreabilidade:** mudar isoladamente uma carga, parâmetro de vento, material ou vínculo sem editar a geometria e exigir invalidação; resultado antigo/ausente/parcial deve permanecer bloqueado; hash e versão reproduzíveis.
6. **Normas:** para cada procedimento da matriz N01–N18, registrar edição/errata/item, hipótese e exceções, regra executável, exemplo independente e testes de fronteira. Uma tabela de títulos ou extração integral de PDF não encerra esta etapa.

## Evolução observada durante a auditoria

A equipe principal acrescentou em `Nbr6118.java` validações de finitude, magnitude, geometria e faixa de implementação (`:84–85`, `:120–121`, `:147–148`, `:188–190`, `:215–217`, `:226–243`) e retirou arredondamentos dos retornos internos. Essas mudanças reduzem falso resultado por entrada inválida e alteração de comparação perto do limite. Elas não validam coeficientes normativos nem resolvem os procedimentos ausentes acima. A revisão mantém o diagnóstico normativo separado dessa correção matemática.

O fechamento de uma verificação deve ser feito por escopo implementado e demonstrado, com rastreabilidade das fontes autorizadas e revisão técnica independente. Até então, o resultado global dos ensaios e do pré-dimensionamento deve continuar `NAO_VERIFICADO`, e aço estrutural/procedimentos não implementados devem continuar `NAO_SUPORTADO`.

## Adendo — correções e regressões executadas nesta sessão

**A2 corrigido.** `SolverMef.adicionarExtremosFlecha` constrói os polinômios transversais em `t=s/L`, normaliza os coeficientes com escala comum e acrescenta as raízes candidatas de `vy·vy' + vz·vz'` às seções publicadas. O helper `RaizesPolinomiais` particiona `[0,1]` pelas raízes recursivas da derivada, usa bisseção nos intervalos com troca de sinal e guarda candidatos de raiz múltipla próximos do erro de avaliação. Tolerância da posição adimensional: 2×10⁻¹³, com no máximo 64 passos por intervalo; isso não é tolerância de ELS e não amplia o domínio físico das barras prismáticas lineares com carga uniforme. A avaliação da resultante passou a usar `Math.hypot`.

As regressões falharam antes da correção e passaram depois:

| Ensaio | Antes | Depois / referência independente |
|---|---:|---:|
| P1/V1, ELU_TESTE | 2,761996975 mm | 2,763916258 mm, s≈2,4404787 m |
| Viga espacial, carga e momentos nos dois planos | 3,765239036 mm | 3,773064584 mm, s=3,125063962367 m |

No ensaio espacial, integrar `EI v''=M` com translações nulas nos apoios produz `vy=-0,00264t−0,009t²+0,02028t³−0,00864t⁴` e `vz=-0,01056t+0,0192t³−0,00864t⁴`, em metros. A referência da resultante foi calculada em precisão decimal de 50 dígitos. Seus extremos componentes ficam em s≈3,260929564 m e 3,090682497 m, distintos do máximo da resultante: o teste impede uma correção incompleta baseada apenas em cada plano separadamente.

`SolverMefFlechaTest` também confere soluções fechadas de viga biapoiada e balanço nos dois planos. `RaizesPolinomiaisTest` exercita sete raízes, extremidades, raiz sem troca de sinal, coeficientes escalados por 10⁻²⁵⁰/10²⁵⁰, polinômio nulo/constante e valores não finitos. A precisão em posição é numérica, sujeita ao condicionamento do polinômio em ponto flutuante; não constitui garantia simbólica para raízes arbitrariamente próximas.

**A3 corrigido pela equipe principal e incluído na execução abaixo.** O montador agora representa o peso próprio do pilar como carga distribuída. `MontadorPesoProprioPilarTest` verifica reação de 4,6875 kN, encurtamento de 3,75 µm e `N(s)=−1,5625(3−s) kN`, incluindo normal nula no topo. A referência D1 foi adaptada para a mesma representação distribuída.

Validação executada após essas alterações: `/workspace/.tools/cca-mvn -q -o -f backend/pom.xml -pl cca-domain test` — **86 testes, zero falhas, zero erros, zero ignorados**. As regressões A1 e a regeneração do memorial/JSON são coordenadas pela equipe principal; o estado final desses artefatos deve ser consultado no registro principal da entrega. Nenhuma dessas correções encerra os bloqueadores de conformidade global listados nesta revisão.
