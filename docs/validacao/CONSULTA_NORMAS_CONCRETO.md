# Consulta de normas de concreto e contraprova acadêmica

Data: 10/11/2026. Escopo: disponibilidade das fontes, identificação de edição e leitura acadêmica autorizada. **Não foi consultado o texto integral da NBR 6118 vigente; nenhuma cláusula dessa edição foi homologada por esta pesquisa.** A leitura de um TCC não substitui a norma nem permite declarar segurança global do CivilConstructorAI.

## 1. Fontes efetivamente consultadas

| Fonte primária/editorial | Evidência obtida | Limite da consulta |
|---|---|---|
| [Catálogo ABNT](https://www.abntcatalogo.com.br/) | A página pública anuncia NBR 6118:2026, projeto de estruturas de concreto | Metadados/oferta comercial; não apresenta o conteúdo integral |
| [ABECE — Emenda da NBR 6118](https://site.abece.com.br/nbr-6118-tem-emenda-publicada-pela-abnt/) | A entidade participante da revisão informa Emenda 1 de 11/03/2026; o conjunto NBR 6118:2023 + Emenda 1:2026 equivale à NBR 6118:2026 | Comunicado de publicação. As referências a marquises, figuras e ATP não fornecem os requisitos completos |
| [ABNT Coleção](https://www.abntcolecao.com.br/) | Plataforma oficial de coleção/licença institucional, com campos de empresa, usuário e senha | Sem sessão institucional autenticada disponível; nenhum documento aberto |
| [Crea-SP — acesso ABNT e AMN](https://www.creasp.org.br/servico/acesso-a-normas-tecnicas-abnt-e-amn/) | Consulta gratuita e ilimitada para profissionais com registro ativo e pagamentos regulares; procedimento de acesso publicado | Elegibilidade do usuário não presumida; não houve login |
| [DIN Media — NBR 12655:2022-02-25](https://www.dinmedia.de/en/standard/abnt-nbr-12655/352698418) | Catálogo editorial marca a edição de 25/02/2022 como corrente, 28 páginas | Somente ficha comercial; PDF licenciado não adquirido/aberto |
| [ABECE — publicação da NBR 14931](https://site.abece.com.br/nova-nbr-14931-e-publicada/) | Comunicado de maio/2023 confirma revisão de execução de estruturas de concreto, incluindo fibras | Comunicação técnica; não é leitura da norma |
| [CBIC — boletim de abril–junho/2023](https://cbic.org.br/inovacao/2023/06/05/comat-gant-apresenta-boletim-de-alteracoes-de-normas-tecnicas-da-cbic/) | Registra NBR 14931, publicação de 10/04/2023 | Identificação de publicação, sem cláusulas consultadas |

O link de detalhes da NBR 6118 divulgado no comunicado ABECE levou a uma página ABNT com `LINKEXPIRED` nesta sessão. Isso limita aquele link, não significa inexistência do documento. A navegação pública, isoladamente, não demonstrou disponibilidade de uma licença de leitura integral no ambiente.

O inventário local inicial não encontrou PDFs integrais dessas normas. O ZIP informado pelo usuário não foi lido: a ferramenta de download rejeitou o arquivo por exceder 32 MiB. A publicação de `docs/normas` no GitHub ainda estava pendente na informação recebida do agente principal. Esses estados devem ser reavaliados assim que os arquivos estiverem disponíveis; não equivalem a uma conclusão permanente de indisponibilidade.

## 2. Caminhos concretos para completar a consulta

1. Arquivos fornecidos pelo usuário: inventariar cada PDF por título interno, edição, emendas/erratas, páginas e SHA-256; ler conteúdo, tabelas e figuras. O nome do arquivo e o ano isolados não autenticam a edição.
2. [Catálogo ABNT](https://www.abntcatalogo.com.br/): localizar a edição consolidada de 2026 ou o conjunto de 2023 com a Emenda 1:2026, além das normas correlatas aplicáveis; usar a licença disponível ao usuário.
3. Convênio [Crea-SP](https://www.creasp.org.br/servico/acesso-a-normas-tecnicas-abnt-e-amn/), caso elegível: CPF/RNP no portal oficial, cadastro inicial e senha; depois Pesquisa Avançada → norma → Ver Detalhes → Visualizar Norma. A autenticação deve ocorrer na interface do serviço, sem compartilhar senha no chat.
4. [ABNT Coleção](https://www.abntcolecao.com.br/): utilizar sessão institucional legitimamente disponível. Não foi identificada sessão aberta que permitisse este acesso.

Não foram comprados documentos, contornados controles de acesso ou utilizadas credenciais de terceiros. Uma biblioteca universitária pode oferecer licença aos seus próprios usuários; a existência de um convênio não concede acesso externo automaticamente.

## 3. Documento acadêmico lido integralmente

[Jardiel da Silva Araújo Júnior, **REL_UFRJ**, UFRJ/Escola Politécnica, julho/2019](https://monografias.poli.ufrj.br/monografias/monopoli10029355.pdf). Foram lidos o texto das 81 páginas e suas figuras; cálculos e desenhos relevantes foram ampliados. Paginação impressa = página PDF menos 10. Bibliografia: NBR 6118:2014, p.71/PDF81. SHA-256: `2455db8661ddf09158ef8defe9ab6d5e34354ecc21c158ab542de5a2839cf329`.

| Evidência | Dados e limite |
|---|---|
| Pilar, pp.19–23/PDF29–33 | 200×200 mm, C20, CA50, 8Ø12,5; Nk642 kN. Modelo curto centrado, sem prova de estabilidade global |
| Viga, pp.38–41/PDF48–51 | 200×600 mm, C25, CA50, 3Ø20, estriboØ10, cobrimento25 mm, Md200 kN·m; desenho usa d575 mm |
| Comparações, pp.55–58/PDF65–68 | Comparação entre programas de confiabilidade; não comprova detalhamento/ELS |

**Contraprova própria**, com γc1,4, γs1,15 e bloco0,85×0,8 adotados apenas neste benchmark:

```text
As=3π20²/4=942,477796 mm²; fyd=500/1,15; fcd=25/1,4
x=As·fyd/(0,85·0,8·200·fcd)=168,730040 mm
MRd=As·fyd·(d−0,4x)/10^6
d575 → MRd207,963046 kN·m
d=600−25−10−20/2=555 → MRd199,767587 kN·m < 200
```

A geometria corrigida muda a conclusão da comparação resistente. Não arredondar antes de comparar. O exemplo não certifica o software nem a edição vigente.

## 4. Implicações técnicas e pendências

A contraprova acima usa equilíbrio de seção para mostrar uma inconsistência geométrica. Não se trata de verificação completa da viga: ancoragem, cortante, torção, ELS, cobrimento por ambiente e montagem não foram demonstrados por esse cálculo. Também não basta substituir a edição citada no TCC de 2014 para 2026: é necessário conferir o texto efetivo de cada procedimento.

No CivilConstructorAI, a leitura de `Nbr6118.java` e da matriz normativa encontrou rotinas parciais e hipóteses fixas. Esta pesquisa não homologou suas fórmulas. Antes de habilitar aprovação, continuam necessárias:

| Área | Evidência técnica ainda necessária |
|---|---|
| Edição e aplicabilidade | Texto integral, emendas/erratas, regras de transição e edição adotada na obra; vínculo entre cada fórmula e item efetivamente lido |
| Materiais e resistência | fck/idade, fyk/categoria, resistências de cálculo, módulo e agregado, modelos constitutivos e limites de uso; dados de ensaio e lotes rastreáveis |
| Vigas | Altura útil a partir da posição das barras selecionadas, camadas e estribos; domínio/ductilidade, mínimos/máximos, cortante/torção/interações e detalhamento |
| Pilares e pórticos | Esforços concomitantes N–Mx–My, imperfeições, esbeltez nos dois eixos, estabilidade, efeitos de segunda ordem e fluência; resistência da seção e ligação viga–pilar |
| Lajes | Modelo e distribuição de cargas coerentes com apoios, duas direções quando aplicável, cortante, punção, aberturas, bordas e armaduras construtivas |
| ELS | Combinações próprias, rigidez fissurada, flecha imediata/diferida, retração/fluência, limites por uso e fissuração com geometria e distribuição das barras |
| Execução e materiais | NBR 12655 e 14931; edições/erratas da NBR 5738, 5739, partes da 8522, 8953 e 7480 ainda precisam de fechamento no catálogo e leitura integral |
| Casos especiais | Verificar aplicabilidade de incêndio, fadiga, sismo, fases construtivas, protensão, pré-moldados, fibras e fundações; suportar ou declarar fora do escopo |
| Aceitação | Testes independentes com casos favoráveis, desfavoráveis e limítrofes; revisão por profissional habilitado e ATP quando aplicável, depois de ler os requisitos correspondentes |

As constantes atuais de segurança, a classificação simplificada de domínios, os limiares de esbeltez, a aproximação de resistência de pilares e os modelos de ELS exigem revisão específica. Não assumir que uma expressão comentada com número de item antigo está validada na edição de 2026. Para geometria, conferir novamente a capacidade depois da seleção de bitola/arranjo, sem usar área arredondada como critério resistente.

Para cada procedimento, o registro de encerramento deve conter: norma/edição e item; escopo e exceções; entradas/unidades; fórmula/modelo; resultado independente; tolerância numérica; testes de fronteira; situação `VERIFICADO`, `NAO_VERIFICADO` ou `NAO_SUPORTADO`; revisor e data. Até esse fechamento, os bloqueios de aprovação da matriz existente devem permanecer.

## 5. Estado desta entrega

- Concluído: identificação documental da NBR 6118:2026, caminhos oficiais de acesso, leitura acadêmica integral, contraprova numérica e registro das limitações.
- Pendente: leitura integral das normas vigentes e verificação item a item do motor. Nenhuma norma foi reescrita ou substituída por TCC; nenhuma conformidade global foi declarada.
- Complemento do trabalho em equipe: [matriz normativa](MATRIZ_NORMATIVA_ABNT.md), [consulta das ações](CONSULTA_NORMAS_ACOES.md) e [memorial dos ensaios](MEMORIAL_ENSAIOS.md).
