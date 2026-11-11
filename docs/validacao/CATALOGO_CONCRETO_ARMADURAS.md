# Catálogo histórico de concreto e armaduras

Versão do catálogo: **2026-11-11.1**. Natureza: referência técnica histórica. **`habilitado_para_calculo=false`**. Nenhum coeficiente deste catálogo foi transferido para o motor. A data do catálogo não representa a vigência da norma.

Fonte principal: [cópia fornecida no GitHub](https://github.com/MatheusHaliski/CivilConstructorAI/blob/main/docs/normas/Copy%20of%20NBR%206118%20%E2%80%93%20Projeto%20de%20estruturas%20de%20concreto..pdf), **ABNT NBR 6118:2014, terceira edição de 29.04.2014, versão corrigida de 07.08.2014**, 256 páginas PDF / 238 páginas impressas. SHA-256: `220bf88c73edee537ab60a4314844e24aba7b50cf55c807b8e941eec0c158a29`, conferido sobre o arquivo local. O campo de validade de 2014 na capa não comprova validade em 2026.

O [catálogo estruturado](catalogo-materiais-historico.json) contém valores, equações, domínios, unidades e referências por registro. Origem documental geral: [ORIGEM_ACERVO.json](ORIGEM_ACERVO.json), [INVENTARIO_ACERVO.json](INVENTARIO_ACERVO.json) e [FONTES_EXTRAIDAS.json](FONTES_EXTRAIDAS.json). Os relatórios [CONSULTA_NORMAS_CONCRETO.md](CONSULTA_NORMAS_CONCRETO.md) e [LEITURA_MEMORIAIS_GRUPO_C.md](LEITURA_MEMORIAIS_GRUPO_C.md) permanecem como análises próprias das fontes auxiliares; exemplos acadêmicos não estabelecem propriedades normativas universais.

## Escopo efetivamente lido

Leitura sequencial integral do texto dos capítulos **6–9** (PDF 33–72 / impressas 15–54), **17–25** (PDF 135–224 / impressas 117–206) e **Anexo A informativo** (PDF 225–234 / impressas 207–216). Inclui exigências qualitativas, notas e limitações, além de buscas de propriedades. O Anexo B foi identificado como índice remissivo. Esta entrega não afirma leitura integral das 256 páginas nem das normas referenciadas. No corpo, `página PDF = página impressa + 18`, com ambas as contagens iniciadas em 1.

Extração textual `pdftotext -layout`; Tabela 17.3, Tabela A.1, expressão de βs e Tabela A.2 conferidas em páginas renderizadas. Figuras de arranjos não foram convertidas em regras escalares quando dependem da geometria. Expressões integrais fragmentadas do Anexo A foram mantidas como remissão. Esses limites são distintos de ausência de leitura.

## Concreto: classes, tipos e resistência

| Tema | Dado extraído e domínio | Item | PDF / impressa |
|---|---|---|---|
| Classes | Grupos I e II da NBR 8953, até C90; C20 ou superior com armadura passiva e C25 ou superior com ativa | 8.2.1 | 40 / 22 |
| C15 | 8.2.1 restringe a obras provisórias ou sem fins estruturais; 24.3 cita C15–C40 para concreto simples estrutural. Diferença de escopo do capítulo específico de concreto simples; sem extensão automática ao armado/protendido | 8.2.1; 24.3 | 40; 218 / 22; 200 |
| Massa específica normal | Seco em estufa: >2 000 e ≤2 800 kg/m³ (escopo 1.2). Sem valor real: simples 2 400 e armado 2 500 kg/m³; com dado do simples, incremento de 100–150 kg/m³ para armado | 1.2; 8.2.2 | 19; 40 / 1; 22 |
| Tipos estruturais | Simples, armado e protendido; pré-tração, pós-tração aderente/não aderente e protensão externa têm condições diferentes | 8.2; 18.6; 24 | 40; 170–173; 218–224 / 22; 152–155; 200–206 |
| Compressão | Corpos de prova cilíndricos, moldagem NBR 5738, ruptura NBR 5739; idade de referência 28 dias quando não especificada; fcmj remete NBR 12655 | 8.2.4 | 41 / 23 |
| Tração | fct = 0,9 fct,sp ou 0,7 fct,f; ensaios NBR 7222/NBR 12142 | 8.2.5 | 41 / 23 |
| Estimativa à tração | fct,m = 0,3 fck^(2/3) até C50; 2,12 ln(1+0,11 fck) de C55 a C90; fctk,inf=0,7 fct,m e fctk,sup=1,3 fct,m | 8.2.5 | 41 / 23 |

As equações de tração usam MPa e substituem a falta de ensaios. Para outras idades, exigem fckj ≥ 7 MPa. Resistência média, característica e de cálculo são grandezas diferentes. A edição estudada não fornece catálogo de dosagens, consistências comerciais ou traços capazes de garantir determinada classe. A classe isolada tampouco comprova recebimento, durabilidade ou módulo medido.

Em 8.2.6 (PDF 41–42 / impressas 23–24), o critério multiaxial adota compressão positiva, σ3≥σ2≥σ1, σ1≥−fctk e σ3≤fck+4σ1. Essa convenção de sinais deve acompanhar a fórmula.

## Elasticidade e diagramas

Para estimativas aos 28 dias, 8.2.8 (PDF 42–43 / impressas 24–25) estabelece Eci = αE·5600·√fck para 20–50 MPa e Eci = 21500·αE·(fck/10+1,25)^(1/3) para 55–90 MPa, com Eci e fck em MPa. αE depende do agregado: basalto/diabásio 1,2; granito/gnaisse 1,0; calcário 0,9; arenito 0,7. Ecs = αi Eci, com αi = min(0,8+0,2 fck/80; 1). Ensaio pela NBR 8522 é a referência prioritária.

Tabela 8.1, estimativas **arredondadas para granito**, PDF 43 / impressa 25:

| Classe | C20 | C25 | C30 | C35 | C40 | C45 | C50 | C60 | C70 | C80 | C90 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Eci (GPa) | 25 | 28 | 31 | 33 | 35 | 38 | 40 | 42 | 43 | 45 | 47 |
| Ecs (GPa) | 21 | 24 | 27 | 29 | 32 | 34 | 37 | 40 | 42 | 45 | 47 |

C55/C65/C75/C85 não possuem coluna nessa tabela; não foram preenchidos silenciosamente. Entre 7 e 28 dias, a estimativa de Eci(t) tem expoente 0,5 em fckj/fck para C20–C45 e 0,3 para C50–C90. Para compressão <0,5fc e tração <fct, 8.2.9 admite ν=0,2 e Gc=Ecs/2,4. A dilatação térmica do concreto pode ser tomada como 10⁻⁵/°C em análise estrutural (8.2.3, PDF 41 / impressa 23).

O diagrama de compressão em 8.2.10.1 (PDF 44 / impressa 26) tem pico 0,85fcd; até C50, n=2, εc2=2‰ e εcu=3,5‰. De C55 a C90, n, εc2 e εcu variam com fck; equações e unidades estão no JSON. A tração bilinear de 8.2.10.2 é para concreto **não fissurado**. O bloco retangular equivalente de 17.2.2 (PDF 139 / impressa 121) altera λ e αc acima de C50 e depende da largura da seção. Não confundir esse bloco de ELU com propriedade elástica de serviço.

## Durabilidade e proteção

Os capítulos 6–7 exigem considerar lixiviação, sulfatos, reação álcali-agregado, carbonatação, cloretos e deterioração mecânica/reológica (PDF 33–39 / impressas 15–21). Qualidade do cobrimento, fissuração, drenagem, formas, acessibilidade e manutenção atuam em conjunto.

Tabela 7.1 (7.4.2, PDF 36 / impressa 18), aplicável na falta de ensaio comprobatório de desempenho:

| CAA | I | II | III | IV |
|---|---:|---:|---:|---:|
| Agressividade | Fraca | Moderada | Forte | Muito forte |
| a/c máxima, armado | 0,65 | 0,60 | 0,55 | 0,45 |
| Classe mínima, armado | C20 | C25 | C30 | C40 |
| a/c máxima, protendido | 0,60 | 0,55 | 0,50 | 0,45 |
| Classe mínima, protendido | C25 | C30 | C35 | C40 |

A relação água/cimento é em massa. Classificação ambiental requer considerar as notas de microclima da Tabela 6.1 (PDF 35 / impressa 17), incluindo UR≤65% em condições específicas, e pode ser agravada pelo projetista. Uso das tabelas depende das demais condições de 7.4 e NBR 12655. Aditivos à base de cloreto são vedados em 7.4.4; as listas de normas de cimento em 7.4.3 refletem 2014.

Tabela 7.2, cobrimento nominal em **mm para Δc=10 mm**, PDF 38 / impressa 20:

| Estrutura / elemento | I | II | III | IV |
|---|---:|---:|---:|---:|
| Armado / laje | 20 | 25 | 35 | 45 |
| Armado / viga ou pilar | 25 | 30 | 40 | 50 |
| Armado / contato com solo | 30 | 30 | 40 | 50 |
| Protendido / laje | 25 | 30 | 40 | 50 |
| Protendido / viga ou pilar | 30 | 35 | 45 | 55 |

As notas são parte da tabela: pilar junto à fundação em contato com solo ≥45 mm; superfícies intensamente agressivas seguem IV; face superior revestida possui alternativa condicionada ≥15 mm; passiva de elemento protendido segue cobrimento de armado; pré-fabricados remetem NBR 9062. `cnom=cmin+Δc`; Δc corrente ≥10 mm, ou 5 mm com controle rigoroso explicitado. Regras de diâmetro/barra/feixe/bainha e agregado também limitam o cobrimento. Reduções condicionadas por resistência superior ou tolerância não foram combinadas automaticamente.

## Fluência, retração e cimentos

8.2.11/Tabela 8.2 (PDF 45–46 / impressas 27–28) apresenta matrizes transcritas integralmente no JSON: UR 40/55/75/90%, espessuras 2Ac/u de 20/60 cm, idades iniciais 5/30/60 dias, classes C20–C45 e C50–C90. Os valores de φ são adimensionais; εcs está em **‰, com sinal negativo**. Exemplo verificável: UR40%, 20 cm e t0=5 dias dão φ=4,6 para C20–C45, φ=2,7 para C50–C90 e εcs=−0,53‰. Valem para concreto plástico/cimento Portland comum e tensão inicial <0,5fc, em avaliação sem grande precisão. Temperaturas de referência 10–20°C, admitidas 0–40°C; interpolação linear permitida.

O **Anexo A é informativo**, não tabela de coeficientes universais. A.2.2 trata fluência rápida, lenta irreversível e lenta reversível, superposição e dependência de umidade/consistência/idade. A.2.3 contém retração, Tabela A.1 e βs; A.2.4 distingue idade fictícia e `hfic=γ·2Ac/uar`. Esse γ não pode ser omitido para igualar o Anexo à espessura simplificada da Tabela 8.2. Os polinômios βf/βs usam h em **metros**, limitado a 0,05–1,6 m, enquanto φ2c/ε2s usam hfic em **centímetros**; t≥3 dias.

Tabela A.2 (PDF 232 / impressa 214), conferida visualmente: α de idade fictícia para fluência é 1 em CP III/IV de endurecimento lento, 2 em CP I/II normal e 3 em CP V-ARI rápido; para retração é 1 nos três casos. RS é propriedade de resistência a sulfatos, não classe de concreto. A fórmula de idade fictícia não se aplica à cura a vapor. A consistência considerada na Tabela A.1 é a do mesmo traço **sem** superplastificante/superfluidificante. A.2.5/A.3 exigem considerar história de tensões, impedimentos e interação com armaduras; α de interação não é α de idade do cimento.

## Armadura passiva e ativa

| Propriedade | Passiva | Ativa | Referência PDF / impressa |
|---|---|---|---|
| Categorias/produto | CA-25, CA-50, CA-60; diâmetros/áreas pela NBR 7480 | Fios NBR 7482, cordoalhas NBR 7483; valores nominais de resistência/área/relaxação | 8.3.1; 8.4.1 — 46; 48 / 28; 30 |
| Massa específica | 7 850 kg/m³ | 7 850 kg/m³ | 8.3.3; 8.4.2 — 47; 48 / 29; 30 |
| Módulo na falta de dado | 210 GPa | 200 GPa para fios/cordoalhas | 8.3.5; 8.4.4 — 47; 48 / 29; 30 |
| Dilatação | 10⁻⁵/°C, −20 a 150°C | 10⁻⁵/°C, −20 a 100°C | 8.3.4; 8.4.3 — 47; 48 / 29; 30 |
| Diagramas simplificados | Tração/compressão, −20 a 150°C | −20 a 150°C | 8.3.6; 8.4.5 — 47–49 / 29–31 |
| Ductilidade | CA-25/CA-50 alta se atender mínimos de produto; CA-60 normal se atender norma | Normal condicionada a εuk de produto | 8.3.7; 8.4.6 — 48–49 / 30–31 |

Não foram inferidos valores numéricos de fyk a partir do nome CA, nem inventadas classes CP comerciais. Resistências, ductilidade, diâmetros, áreas, soldabilidade e certificados dependem das normas de produto e ensaios. Para aço sem patamar, 8.3.6 define escoamento convencional por deformação permanente de 0,2%. Solda exige composição e ensaio; 8.3.9 remete NBR 8965/NBR 8548 e alongamento total plástico mínimo de 2% na barra soldada.

A capacidade aderente depende da **superfície**, não apenas da categoria CA. Tabela 8.3 (PDF 47 / impressa 29): η1=1,0 lisa; 1,4 entalhada; 2,25 nervurada. Em 9.3.2 (PDF 52–53 / impressas 34–35), fbd=η1η2η3fctd; η2=1,0 boa/0,7 má aderência e η3 depende de φ em mm a partir de 32 mm. A posição na concretagem de 9.3.1 é indispensável. Para ativa pré-tracionada, ηp1=1,0 fio liso/1,2 cordoalha 3 ou 7 fios/1,4 fio dentado; idade do concreto depende de transferência versus ancoragem.

Tabela 8.4 (PDF 50 / impressa 32), ψ1000 em **% após 1000 h a 20°C**:

| σpo/fptk | Cordoalha RN | Cordoalha RB | Fio RN | Fio RB | Barra |
|---|---:|---:|---:|---:|---:|
| 0,5 | 0 | 0 | 0 | 0 | 0 |
| 0,6 | 3,5 | 1,3 | 2,5 | 1,0 | 1,5 |
| 0,7 | 7,0 | 2,5 | 5,0 | 2,0 | 4,0 |
| 0,8 | 12,0 | 3,5 | 8,5 | 3,0 | 7,0 |

RN=relaxação normal; RB=baixa. 9.6.3.4.5 (PDF 71–72 / impressas 53–54) fornece regra temporal a 20°C, sem relaxação admitida abaixo de 0,5fptk e aproximação infinita 2,5ψ1000. O texto menciona Tabela 8.3 na interpolação; a tabela de relaxação é 8.4. A remissão inconsistente foi registrada. Relaxação pura não representa perda total: 9.6 inclui atrito, acomodação, encurtamento, retração e fluência e exige interação entre causas.

## Dimensionamento e detalhamento: conclusões de leitura

Os capítulos 17–25 foram lidos para separar propriedades de material, verificações de resistência e condições construtivas. O JSON registra limites e remissões, sem pretender substituir um projeto executivo.

- **17:** domínios de deformação, flexão/normal, ELS, corte, torção e interações. Tabela 17.3 tem ρmín retangular de 0,150% a 0,256% conforme fck; pressupõe CA-50, d/h=0,8, γc=1,4 e γs=1,15 (PDF 148 / impressa 130). Portanto 0,15% não é mínimo universal. Estribos no corte/torção têm tetos de resistência de cálculo, inclusive 435 MPa, que não alteram a resistência característica do produto.
- **18:** adensamento, barras curvas, proteção contra flambagem, espaçamento/ancoragem, armadura de pele, suspensão, pilares e cabos. Pilares exigem barra longitudinal ≥10 mm e ≤menor dimensão/8; requisitos de estribos variam com aço, dimensão, emenda e classe do concreto (PDF 169–170 / impressas 151–152). Detalhes não podem ser reduzidos a um diâmetro padrão.
- **19–20:** lajes têm mínimos diferentes para flexão positiva/negativa, uma/duas direções e armadura ativa aderente/não aderente. Punção verifica C/C′/C″ e pode exigir armadura mesmo com tensão baixa se a estabilidade global depender da ligação. Detalhes contra colapso progressivo, aberturas, studs, telas e cabos são adicionais.
- **21–22:** descontinuidades, pressão localizada, juntas, chumbadores, nós, bielas/tirantes, vigas-parede, consolos/Gerber, sapatas e blocos exigem modelos próprios. Resistência de nó CCC difere de CCT/CTT; a pressão localizada de 21.2.1 não vale para certificar ancoragem de protensão.
- **23:** fadiga não é resistência estática. Variações admissíveis dependem de ciclos, diâmetro, dobramento, relevo, solda, ambiente e produto. Tabela 23.2 é de armadura passiva **CA-50**, não de todos os CA. Fadiga de alta intensidade com menos de 20 mil repetições está fora do escopo tratado.
- **24:** concreto simples estrutural exige confinamento/compressão/apoio contínuo conforme caso, tem restrições de ductilidade, sismos, explosões e blocos sobre estacas, além de verificações próprias. Armadura abaixo da mínima não permite usar diretamente regras de concreto armado.
- **25:** recebimento de concreto/aços remete às NBR 12655 e 7480–7483; aceitação do projeto e manual por profissional habilitado, com manutenção conforme NBR 5674, integram a entrega.

## Lacunas para habilitar catálogo atual

É necessário obter os textos vigentes, corrigendas/emendas e comprovar sua aplicabilidade: **NBR 6118, 7480, 7481, 7482, 7483, 7484, 12655, 14931, 8953**, além das normas de ensaio. As referências históricas a cimento e ensaios devem ser confrontadas com eventuais substituições/divisões atuais. Pré-moldados (9062), fogo (15200), reação álcali-agregado (15577), manutenção (5674) e fundações possuem requisitos adicionais.

A importação de um texto extraído, ou a existência de uma referência no documento de 2014, não confirma revisão vigente nem leitura de norma externa. Antes de habilitar cálculo, cada regra precisará de revisão técnica com edição atual, unidades, domínio, hipóteses, evidência experimental e validação independente. Até lá, todos os registros permanecem somente históricos.

## Índice dos registros estruturados

| ID | Conteúdo | Item | PDF / impressa |
|---|---|---|---|
| `classes` | Classes e aplicação | 8.2.1 | 40 / 22 |
| `densidade` | Massa específica | 1.2; 8.2.2 | 19; 40 / 1; 22 |
| `dilatacao_concreto` | Dilatação térmica | 8.2.3 | 41 / 23 |
| `compressao_ensaio` | Resistência à compressão | 8.2.4 | 41 / 23 |
| `tracao` | Tração direta, indireta e flexão | 8.2.5 | 41 / 23 |
| `multiaxial` | Resistência multiaxial | 8.2.6 | 41, 42 / 23, 24 |
| `elasticidade` | Módulos inicial e secante | 8.2.8 | 42, 43 / 24, 25 |
| `tabela81` | Estimativas arredondadas para granito | 8.2.8 / Tabela 8.1 | 43 / 25 |
| `elasticidade_idade` | Módulo antes de 28 dias | 8.2.8 | 43 / 25 |
| `poisson` | Poisson e cisalhamento elástico | 8.2.9 | 43 / 25 |
| `diagrama_compressao` | Parábola-retângulo | 8.2.10.1 | 44 / 26 |
| `diagrama_tracao` | Diagrama bilinear de tração | 8.2.10.2 | 45 / 27 |
| `fluencia_dominio` | Fluência/retração simplificadas | 8.2.11 | 45, 46 / 27, 28 |
| `tabela82` | Matrizes de fluência e retração | 8.2.11 / Tabela 8.2 | 46 / 28 |
| `CAA` | Agressividade ambiental | 6.4 / Tabela 6.1 | 35 / 17 |
| `qualidade_durabilidade` | Água/cimento e classe mínima | 7.4.2 / Tabela 7.1 | 36 / 18 |
| `cobrimento` | Cobrimentos nominais | 7.4.7 / Tabela 7.2 | 37, 38 / 19, 20 |
| `passiva` | Aço passivo | 8.3.1–8.3.9 | 46, 47, 48 / 28, 29, 30 |
| `aderencia_passiva` | Aderência passiva | 8.3.2 / Tabela 8.3; 9.3.1–9.3.2.1 | 47, 52, 53 / 29, 34, 35 |
| `ativa` | Aço ativo | 8.4.1–8.4.7 | 48, 49 / 30, 31 |
| `relaxacao` | Relaxação após 1000 h a 20°C | 8.4.8 / Tabela 8.4 | 49, 50 / 31, 32 |
| `relaxacao_tempo` | Relaxação em outros tempos | 9.6.3.4.5 | 71, 72 / 53, 54 |
| `aderencia_ativa` | Aderência ativa pré-tracionada | 9.3.2.2–9.3.2.3 | 53 / 35 |
| `ancoragem_passiva` | Ancoragem e dobramento | 9.4.2.3–9.4.2.6 | 54, 55, 56 / 36, 37, 38 |
| `emendas` | Emendas passivas | 9.5 | 60, 61, 62, 63, 64 / 42, 43, 44, 45, 46 |
| `protensao_limites` | Limites no aparelho de tração | 9.6.1.2 | 65 / 47 |
| `perdas_protensao` | Famílias de perdas | 9.6.3 | 67, 68, 69, 70, 71, 72 / 49, 50, 51, 52, 53, 54 |
| `bloco_retangular_ELU` | Hipóteses e domínios ELU | 17.2.2 | 138, 139, 140 / 120, 121, 122 |
| `ELS_rigidez` | Fissuração e deformações | 17.3.1–17.3.3 | 142, 143, 144, 145, 146, 147 / 124, 125, 126, 127, 128, 129 |
| `armadura_min_viga` | Armadura mínima à flexão | 17.3.5.2.1 / Tabela 17.3 | 148 / 130 |
| `armadura_pele_max_pilar` | Pele, limites de vigas e pilares | 17.3.5.2.3–17.3.5.3 | 150 / 132 |
| `cisalhamento_acos` | Armaduras de esforço tangencial | 17.4.1–17.4.2; 17.5.1 | 151, 152, 153, 154, 155, 156, 157, 158, 159 / 133, 134, 135, 136, 137, 138, 139, 140, 141 |
| `detalhamento_vigas` | Disposição de barras em vigas | 18.2–18.3 | 163, 164, 165, 166, 167, 168 / 145, 146, 147, 148, 149, 150 |
| `detalhamento_pilares` | Disposição de barras em pilares | 18.4–18.5 | 168, 169, 170 / 150, 151, 152 |
| `cabos_disposicao` | Tipos de protensão e disposição | 18.6 | 170, 171, 172, 173 / 152, 153, 154, 155 |
| `lajes` | Lajes, corte e punção | 19.2–19.5; 20.1 | 174, 175, 176, 177, 178, 183, 184, 185, 186, 187 / 156, 157, 158, 159, 160, 165, 166, 167, 168, 169 |
| `lajes_protendidas` | Disposição em lajes protendidas e telas | 20.3–20.5 | 188, 189, 190, 191 / 170, 171, 172, 173 |
| `regioes_especiais` | Regiões descontínuas e interfaces | 21 | 191, 192, 193, 194, 195, 196 / 173, 174, 175, 176, 177, 178 |
| `bielas` | Regiões D, bielas e nós | 22.2–22.3 | 197, 198, 199 / 179, 180, 181 |
| `elementos_especiais` | Armaduras de elementos especiais | 22.4–22.7 | 199, 200, 201, 202, 203, 204, 205, 206, 207, 208, 209, 210 / 181, 182, 183, 184, 185, 186, 187, 188, 189, 190, 191, 192 |
| `fadiga_concreto` | Fadiga do concreto | 23.5.1–23.5.4 | 211, 212, 213, 214 / 193, 194, 195, 196 |
| `fadiga_acos` | Variações de tensão de fadiga | 23.5.5 / Tabelas 23.2–23.3 | 215, 216, 217 / 197, 198, 199 |
| `simples` | Concreto simples estrutural | 24.2–24.6 | 218, 219, 220, 221, 222, 223, 224 / 200, 201, 202, 203, 204, 205, 206 |
| `aceitacao` | Recebimento, utilização e manutenção | 25.1–25.3 | 224 / 206 |
| `anexoA_fluencia` | Modelo informativo de fluência | Anexo A.1–A.2.2 | 225, 226, 227, 228, 229 / 207, 208, 209, 210, 211 |
| `anexoA_tabelaA1` | Consistência, ambiente e deformações diferidas | Anexo A.2.3 / Tabela A.1 | 230 / 212 |
| `anexoA_retracao` | Modelo informativo de retração | Anexo A.2.3 | 229, 230, 231 / 211, 212, 213 |
| `anexoA_idade_cimento` | Cimentos, idade e espessura fictícias | Anexo A.2.4 / Tabela A.2 | 231, 232 / 213, 214 |
| `anexoA_interacao` | Deformação com variação de tensão e armadura | Anexo A.2.5–A.3 | 233, 234 / 215, 216 |
