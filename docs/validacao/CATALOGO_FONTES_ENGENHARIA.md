# Fontes de concreto, aços e esforços

O acervo foi baixado de `docs/normas` do GitHub, com conferência do SHA do objeto Git e SHA-256 de cada arquivo. Foram processados **31 arquivos distintos: 18 PDFs, com 1.399 páginas, e 13 DOCX**. Cópias binárias repetidas foram agrupadas; documentos de conteúdo semelhante com hashes diferentes permanecem identificados separadamente.

O texto extraído está no cache local `/workspace/referencias/textos`, fora do repositório. O [índice pesquisável](FONTES_EXTRAIDAS.json) registra os temas e seus localizadores por página PDF ou parágrafo XML. O [registro de origem](ORIGEM_ACERVO.json) identifica arquivos, hashes e URLs. Ocorrências textuais são candidatos de consulta, não regras aprovadas de cálculo.

## Fontes utilizáveis e seus limites

| Fonte recebida | Identificação efetivamente conferida | Utilidade para o desenvolvimento |
|---|---|---|
| NBR 6118 | Terceira edição de 2014, corrigida em 07/08/2014; 256 páginas PDF, incluindo folhas preliminares e 238 páginas numeradas | Principal referência histórica para propriedades, análise, dimensionamento e detalhamento de concreto simples, armado e protendido. Precisa de confronto com a edição atual antes de habilitar regras normativas. |
| Arquivo denominado NBR 6122 | Projeto de revisão de dezembro de 2018; 113 páginas PDF; declara não ter valor normativo | Referência técnica histórica de fundações e mapa de verificações ausentes. Não é a norma publicada. |
| Memoriais de concreto de 2021 e Grupo C | Trabalhos acadêmicos; várias versões, incluindo DOCX e PDF | Exemplos para contraprova independente, com inconsistências já documentadas. Não são tabelas normativas ou resultados aprovados. |
| Trabalho sobre estacas metálicas | DOCX “Documento de Matheus Haliski”; fonte acadêmica de 2021 | Tipologias de estacas/perfis e referências bibliográficas; não fornece base suficiente para um catálogo resistente de aço estrutural. Os PDFs homônimos tratam de concreto e são documentos diferentes. |
| Material de Concreto Armado 1.1ºGQ | Enunciado de duas páginas em imagem | Exercício didático, sem gabarito ou edição normativa identificada. Exige leitura visual; sua extração textual é vazia. |
| NBR 5410, 5626, 10844 e 8160 | Exemplares de instalações elétricas/hidráulicas, com edições registradas no acervo | Compatibilização de instalações; não substituem normas de resistência de concreto/aço. Não foram integralmente auditadas nesta etapa estrutural. |
| NBR 6492, 9050 e 9077 | Representação, acessibilidade e saídas de emergência | Requisitos de outras disciplinas, sem validar capacidade resistente. O arquivo “9050_2021” é NBR 9050:2020 corrigida em 2021. |
| Cadernos, cartilha e modelo de TCC | Contratação/viabilidade/obras públicas e template acadêmico | Organização de projetos e documentos; não são fontes de parâmetros mecânicos. |

## Catálogos técnicos extraídos

- [Concreto e armaduras](CATALOGO_CONCRETO_ARMADURAS.md): tipos e classes, resistência à compressão/tração, elasticidade, efeitos do tempo, durabilidade, armaduras passivas/ativas e limites de aplicação.
- [Dados históricos de materiais](catalogo-materiais-historico.json): grandezas com unidade, fonte e domínio, expressamente desabilitadas para cálculo de produção.
- [Esforços e análise](CATALOGO_ESFORCOS_ANALISE.md): normal, cortantes, momentos fletores e torçor; convenções locais, ELU/ELS, concomitância, modelos e estabilidade.
- [Aço estrutural](CATALOGO_ACO_ESTRUTURAL.md): separação entre armadura e perfis, propriedades geométricas/mecânicas necessárias e fontes ainda ausentes.
- [Fundações](CONSULTA_NORMA_FUNDACOES.md) e [contraprovas dos memoriais Grupo C](LEITURA_MEMORIAIS_GRUPO_C.md).

### Tipos de concreto não são apenas valores de fck

A leitura dos itens 1–5 da NBR 6118:2014 identifica concreto simples, armado e protendido; os itens 3.1.5–3.1.9 distinguem armadura passiva e modalidades de protensão. São classificações diferentes da classe de resistência, massa específica, consistência e condições de exposição.

O escopo histórico do item 1.2 abrange concreto normal, com massa específica seca maior que 2.000 e até 2.800 kg/m³, nos grupos C20–C50 e C55–C90. O item 1.1 exclui concretos leves, pesados e outros especiais. Isso não autoriza aplicar o modelo atual do software a todos esses materiais: o protótipo permanece restrito ao seu cadastro e às hipóteses implementadas.

Referências: itens 1.1–1.5 e 2, página impressa 1–3/PDF 19–21; definições 3.1–3.2, impressas 3–6/PDF 21–24; símbolos e unidades SI, seção 4, impressas 6–13/PDF 24–31. Os requisitos de resistência, serviço, durabilidade e documentação são distintos na seção 5, impressas 13–15/PDF 31–33. Estes localizadores pertencem ao exemplar de 2014.

### Aços precisam de cadastros separados

CA-25, CA-50 e CA-60 são categorias de armadura passiva. Aço de protensão exige outras propriedades e normas. Perfis estruturais, chapas, tubos, parafusos e soldas precisam de graus de aço, propriedades e verificações próprios. Não é válido usar a resistência de uma barra CA-50 como resistência de um perfil W.

O catálogo deve distinguir valores característicos, médios, medidos e de cálculo; resistência ao escoamento e à ruptura; módulo de elasticidade; densidade; ductilidade; soldabilidade; forma do produto e certificado. Valores desconhecidos permanecem ausentes. O módulo de elasticidade não determina a resistência ao escoamento.

## O que ainda falta para uma base normativa de produção

| Família | Documentos necessários conforme o escopo | Estado no acervo examinado |
|---|---|---|
| Projeto de concreto | NBR 6118 atual, com emendas/correções e regra de edição por empreendimento | Apenas o exemplar histórico de 2014 |
| Classificação/controle do concreto | NBR 8953, 12655 e normas de cimento/agregados pertinentes | Não recebidas como textos integrais próprios |
| Ensaios e propriedades | NBR 5738, 5739, 7222, 12142 e série 8522, conforme a grandeza | Citadas por outras fontes; exemplares específicos ausentes |
| Execução | NBR 14931 e demais normas aplicáveis | Exemplar específico ausente |
| Armadura passiva | NBR 7480 e 7481, conforme produto | Citadas; exemplares específicos ausentes |
| Protensão | NBR 7482, 7483, 7484 e demais requisitos aplicáveis | Citadas; exemplares específicos ausentes; motor de protensão não implementado |
| Ações e segurança | NBR 6120, 6123 e 8681 atuais e corrigidas | Exemplares específicos ausentes |
| Aço estrutural | NBR 8800; NBR 14762/16239 e normas de produtos quando pertinentes | Não há conjunto suficiente para dimensionar perfis e ligações |
| Fundações | NBR 6122 publicada e normas de investigação/ensaios aplicáveis | Somente projeto de revisão de 2018 e trabalhos acadêmicos |

## Como aproveitar a extração no CivilConstructorAI

Cada regra deve vincular documento/hash, edição/item, entradas/unidades, domínio, fórmula ou procedimento, exemplo independente e estado de verificação. O catálogo histórico tem `habilitado_para_calculo=false`: ele serve à revisão e aos testes, sem substituir automaticamente os parâmetros do motor.

O índice temático não atribui vigência nem interpreta notas de tabelas. PDFs têm possíveis perdas em fórmulas/figuras; DOCX têm fórmulas OMML e imagens, além de não possuírem paginação estável. Essas lacunas estão registradas por arquivo. A extração de 1.399 páginas não significa leitura técnica integral das 1.399 páginas: os catálogos delimitam os trechos efetivamente examinados.

Para reproduzir a indexação com o acervo local e seu registro de origem:

```sh
python3 scripts/extrair_fontes_engenharia.py /workspace/referencias/normas \
  --origem /workspace/referencias/normas/origem.json \
  --listagem /workspace/referencias/normas-listagem-github-03.json \
  --cache /workspace/referencias/textos \
  --saida docs/validacao/FONTES_EXTRAIDAS.json
```

**Resultado:** fontes disponíveis extraídas e classificadas; lacunas identificadas. Segurança global e conformidade com normas vigentes continuam não comprovadas.
