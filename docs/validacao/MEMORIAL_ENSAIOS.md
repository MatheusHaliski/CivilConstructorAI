# Memorial dos ensaios de desenho e análise estrutural

Resultado: **mecânica linear comparada; segurança global ABNT NÃO COMPROVADA**.

## Escopo e evidências

Foram comparados 5 casos isolados e o conjunto D1 montado a partir da geometria BIM. Todos permanecem NAO_VERIFICADO. As capturas registram o desenho de treino original; dimensões e esforços desta execução estão nas entradas e tabelas abaixo.

- [Planta](desenho-planta.png), [cena 3D](desenho-3d.png) e [diagramas](ensaios-estruturais.pdf).
- [Entradas](casos-estruturais.json), [resultados Java](resultados-motor.json), [comparação Python](comparacao-independente.json) e [hipóteses resistentes](hipoteses-resistencia.json).
- [Matriz normativa](MATRIZ_NORMATIVA_ABNT.md), [inventário dos PDFs fornecidos](INVENTARIO_ACERVO.json) e [revisão independente](REVISAO_INDEPENDENTE_MOTOR.md).

O teste de navegador cria elementos no motor real da área de treino local. A representação foi conferida por coordenadas, captura visual e pixels; isso não demonstra a segurança da estrutura.

## Hipóteses e entradas atuais

Unidades: m, kN, kN·m e kN/m² para o módulo de elasticidade. Barras Euler–Bernoulli, seção bruta, material linear, pequenas deformações e vínculos ideais. Deslocamentos não são flechas finais normativas.

- Somente as cargas explicitadas são aplicadas. B1/B2/L1 incluem o peso próprio indicado no memorial; C1/P1 não adicionam peso próprio dos pilares: são benchmarks idealizados.
- E=30 GPa, seção bruta, Euler-Bernoulli linear, pequenas deformações, sem fluência/fissuração/segunda ordem.
- Fatores são entradas do ensaio; não representam uma tabela normativa validada para todo uso.
- Faixa L1: apoio contínuo em duas bordas, fluxo unidirecional imposto; não é solução geral de placa.

Os casos isolados aplicam apenas as cargas explícitas abaixo. Uma ação horizontal artificial não representa vento calculado pela NBR 6123. D1 acrescenta o peso próprio no montador.

| Caso | E (kN/m²) | Nós X,Z (m) | Barras: id, b×h (m) | Cargas distribuídas G,Q (kN/m) | Cargas nodais (kN) |
|---|---:|---|---|---|---|
| B1 | 3e+07 | [[0, 0], [6, 0]] | V1: 0.2×0.5 | barra 0: 6, 4 | [] |
| B2 | 3e+07 | [[0, 0], [3, 0]] | V1: 0.2×0.5 | barra 0: 6, 4 | [] |
| C1 | 3e+07 | [[0, 0], [0, 3]] | P1: 0.25×0.25 | — | [{"no": 1, "gx": 0, "gz": -300, "qx": 10, "qz": -200}] |
| P1 | 3e+07 | [[0, 0], [0, 3], [5, 3], [5, 0]] | P1: 0.25×0.25; V1: 0.2×0.5; P2: 0.25×0.25 | barra 1: 10, 10 | [{"no": 1, "gx": 0, "gz": 0, "qx": 10, "qz": 0}] |
| L1 | 3e+07 | [[0, 0], [3, 0]] | L1-faixa: 1×0.15 | barra 0: 5, 2 | [] |

Vínculos completos no JSON. As combinações abaixo são hipóteses de teste; sua aplicabilidade por ocupação, situação e sinal favorável/desfavorável aguarda revisão integral da NBR 8681 e normas de ações.

| Combinação | G | Q |
|---|---:|---:|
| CARACTERISTICA_TESTE | 1 | 1 |
| ELU_TESTE | 1.4 | 1.4 |
| QP_TESTE | 1 | 0.3 |
| FREQUENTE_TESTE | 1 | 0.4 |

## Esforços e deslocamentos — ELU_TESTE

N e V são máximos em módulo. M segue eixos locais; o JSON conserva sinais por seção. A faixa L1 representa a largura indicada na entrada e não uma solução geral de placa. A flecha é a resultante transversal elástica, sem limites normativos verificados.

| Caso / barra | N (kN) | V (kN) | M mín. (kN·m) | M máx. (kN·m) | Flecha (mm) |
|---|---:|---:|---:|---:|---:|
| B1 / V1 | 0.000 | 42.000 | 0.000 | 63.000 | 3.780000 |
| B2 / V1 | 0.000 | 42.000 | -63.000 | 0.000 | 2.268000 |
| C1 / P1 | 700.000 | 14.000 | 0.000 | 42.000 | 12.902400 |
| P1 / P1 | 65.978 | 2.943 | 1.032 | 9.862 | 1.831884 |
| P1 / V1 | 16.943 | 74.022 | -29.972 | 67.872 | 2.763916 |
| P1 / P2 | 74.022 | 16.943 | -29.972 | 20.857 | 2.157716 |
| L1 / L1-faixa | 0.000 | 14.700 | -0.000 | 11.025 | 1.225000 |

### Reações de apoio

| Caso / nó | Rx (kN) | Rz (kN) | My global (kN·m) |
|---|---:|---:|---:|
| B1 / 0 | 0.000 | 42.000 | 0.000 |
| B1 / 1 | 0.000 | 42.000 | 0.000 |
| B2 / 0 | 0.000 | 42.000 | -63.000 |
| C1 / 0 | -14.000 | 700.000 | -42.000 |
| P1 / 0 | 2.943 | 65.978 | -1.032 |
| P1 / 3 | -16.943 | 74.022 | -20.857 |
| L1 / 0 | 0.000 | 14.700 | -0.000 |
| L1 / 1 | 0.000 | 14.700 | 0.000 |

### D1 — conjunto integrado

Geometria: 6×4×3 m; pilares 25×25 cm; vigas 20×50 cm; laje 15 cm. Revestimento 1.25 kN/m² e sobrecarga 2 kN/m².

O montador usa peso específico de 25 kN/m³ e transfere a carga da laje proporcionalmente ao comprimento das vigas de contorno. Isso não é solução de placa. A soma dos sólidos não desconta suas interseções. O peso próprio dos pilares é carga axial distribuída; o encurtamento é conferido separadamente por qL²/(2EA). A decomposição Python em dois pórticos depende da simetria deste ensaio.

Carga linear nas vigas: G=8.500000 kN/m e Q=2.400000 kN/m. Reação vertical ELU total=331.450000 kN; por apoio=82.862500 kN.

| Barra D1 | N (kN) | V (kN) | My mín. (kN·m) | My máx. (kN·m) | Mz abs. (kN·m) | T abs. (kN·m) |
|---|---:|---:|---:|---:|---:|---:|
| P1 | 82.862 | 8.777 | -8.758 | 17.573 | 5.975 | 0.000 |
| P2 | 82.862 | 8.777 | -17.573 | 8.758 | 5.975 | 0.000 |
| P3 | 82.862 | 8.777 | -17.573 | 8.758 | 5.975 | 0.000 |
| P4 | 82.863 | 8.777 | -8.758 | 17.573 | 5.975 | 0.000 |
| V1 | 8.777 | 45.780 | -17.573 | 51.097 | 0.000 | 0.000 |
| V2 | 2.985 | 30.520 | -5.975 | 24.545 | 0.000 | 0.000 |
| V3 | 8.777 | 45.780 | -17.573 | 51.097 | 0.000 | 0.000 |
| V4 | 2.985 | 30.520 | -5.975 | 24.545 | 0.000 | 0.000 |

## Resistência parcial sob hipóteses explícitas

Coeficientes e armaduras estão no arquivo de hipóteses, com hash abaixo; não se presumiu sua conformidade com a edição vigente. A geometria vem da entrada atual e Md/Vd vêm das seções da saída Java atual, após comparação mecânica. Demanda ausente, não finita, interação N–M, torção ou face tracionada não coberta impedem este cálculo parcial.

Equilíbrio: T=As·fyd; x=T/(α·fcd·λ·b); z=d−λx/2; MRd=T·z. O resultado restrito compara Md≤MRd e x/d com o limite assumido; não verifica todo o detalhamento, aderência ou capacidade global.

| Caso | Md (kN·m) | MRd idealizado (kN·m) | Md/MRd | x/d | Resultado restrito |
|---|---:|---:|---:|---:|---|
| B1 | 63.000 | 110.069 | 0.572 | 0.197 | ATENDE sob hipóteses |
| B2 | 63.000 | 110.069 | 0.572 | 0.197 | ATENDE sob hipóteses |
| L1 | 11.025 | 25.896 | 0.426 | 0.130 | ATENDE sob hipóteses |

| Caso | b×h (mm) | d (mm) | As (cm²) | Face | fcd (MPa) | fyd (MPa) |
|---|---:|---:|---:|---|---:|---:|
| B1 | 200×500 | 455.700 | 6.031858 | inferior | 21.428571 | 434.782609 |
| B2 | 200×500 | 455.700 | 6.031858 | superior | 21.428571 | 434.782609 |
| L1 | 1000×150 | 120.000 | 5.235988 | inferior | 21.428571 | 434.782609 |

Modelo I idealizado de cisalhamento, apenas nos casos com estribos:

| Caso | Vsd (kN) | VRd2 (kN) | VRd3 (kN) | Asw/s (cm²/m) | Mínimo assumido (cm²/m) | Resultado restrito |
|---|---:|---:|---:|---:|---:|---|
| B1 | 42.000 | 464.033 | 134.781 | 3.117245 | 2.317175 | ATENDE sob hipóteses |
| B2 | 42.000 | 464.033 | 134.781 | 3.117245 | 2.317175 | ATENDE sob hipóteses |

## Comparação e pendências

A referência Python usa montagem matricial própria, sem chamar o solver Java. Reações e esforços são comparados com tolerância absoluta 1,1×10⁻⁴ e relativa 10⁻⁸; deslocamentos por seção, com 10⁻¹⁰ m e relativa 10⁻⁷. Máximos de B1/B2/L1 têm soluções fechadas. Os testes Java adicionais incluem pórtico e flexão espacial: extremos da resultante de deslocamento são isolados pelo polinômio da derivada, além dos extremos de momento V=0.

| Caso | Maior diferença de esforços/reações | Resíduo da referência |
|---|---:|---:|
| B1 | 5.684e-14 | 7.105e-15 |
| B2 | 4.263e-14 | 1.066e-14 |
| C1 | 7.105e-15 | 1.137e-13 |
| P1 | 1.235e-13 | 1.137e-13 |
| L1 | 7.105e-15 | 0.000e+00 |

D1: maior diferença de esforços=5.684e-14.

Correções verificadas: extremos de momento entre estações; máximo da resultante transversal; carga axial distribuída de peso próprio do pilar; precisão sem arredondamento nas decisões; coeficiente algébrico exato do bloco retangular declarado; memorial alimentado pela demanda atual. A regressão triplica as cargas de B1 e exige reprovação resistente com Md atualizado.

Persistem: combinações normativas por ação/situação, N–Mx–My, estabilidade e segunda ordem, ligações, detalhamento/ancoragem, durabilidade, verificações da placa/punção quando aplicáveis, fissuração e deformação no tempo, materiais/execução e fundações com investigação geotécnica. Nem C1/P1/D1 nem a laje real têm comprovação resistente global. A faixa L1 não valida a laje D1.

O acervo contém NBR 6118:2014 corrigida, utilizada como referência histórica catalogada; não contém as edições atuais de 6118/8681/6120/7480 nem norma específica de aço estrutural. O arquivo intitulado NBR 6122 é projeto de revisão de dezembro/2018, sem valor normativo. Memoriais/TCCs são fontes técnicas secundárias e requerem contraprova; não substituem as edições vigentes. O estado de leitura e cada pendência estão nos relatórios e na matriz.

Resultado global de todos os casos: **NAO_VERIFICADO**.

## Reprodução

```sh
mvn -q -f backend/pom.xml -pl cca-application -am -Dcca.validacao.saida=$PWD/docs/validacao/resultados-motor.json test
python3 -m unittest discover -s scripts -p test_validar_estruturas.py
python3 scripts/validar_estruturas.py
```

Requer Java 21/Maven e dependências de requirements-validacao.txt. Para repetir o desenho local: iniciar o frontend e executar e2e/motor-estruturas.spec.ts com Playwright.

SHA-256 das entradas: `6492e5c181df8c9d5a31886e678ce77f87e24488a2b4429675ed077c4fc08df6`.

SHA-256 da saída Java: `4e3d9209026a291b1c8883ab3cdcba6b0d7c958319c43485ee1c2c9112d31656`.

SHA-256 das hipóteses resistentes: `1aa0325602874e42bd37e1c8df6ffbc4f7e8725855ce76efa7f095568fd302ee`.
