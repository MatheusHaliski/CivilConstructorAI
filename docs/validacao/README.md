# Evidências dos ensaios estruturais — 10/11/2026

Comece pelo [memorial](MEMORIAL_ENSAIOS.md). A [matriz normativa ABNT](MATRIZ_NORMATIVA_ABNT.md) distingue publicação de uma norma de auditoria de suas cláusulas. O resultado global permanece **NÃO VERIFICADO**: não há liberação para execução.

Para o acervo fornecido, comece pelo [catálogo de fontes de engenharia](CATALOGO_FONTES_ENGENHARIA.md): concreto, armaduras, esforços e aço estrutural, com itens/páginas e lacunas. Foram processados 31 arquivos únicos (18 PDFs, 1.399 páginas, e 13 DOCX); o [índice temático](FONTES_EXTRAIDAS.json) contém os localizadores e as limitações de extração. A NBR 6118 recebida é de 2014 corrigida; o arquivo de fundações é projeto de revisão de 2018 sem valor normativo.

## Relatórios da revisão por agentes

- [Consulta de concreto e contraprova acadêmica](CONSULTA_NORMAS_CONCRETO.md): fontes editoriais da NBR 6118:2026, acesso oficial e TCC UFRJ integralmente lido.
- [Consulta de ações, combinações e vento](CONSULTA_NORMAS_ACOES.md): NBR 8681, 6120 e 6123; edições/correções identificadas e lacunas do gerador.
- [Revisão independente do motor](REVISAO_INDEPENDENTE_MOTOR.md): defeitos reproduzidos, cobertura dos ensaios e métodos ainda ausentes.

**Normas vigentes integrais lidas nesta etapa: nenhuma.** A leitura do exemplar histórico NBR 6118:2014 e do projeto de fundações está delimitada nos catálogos. Catálogos editoriais e comunicados confirmam publicação e rotas de acesso das edições atuais, sem validar cláusulas ou fatores. O confronto com essas edições continua pendente.

O lote ampliado reúne normas históricas de concreto, fundações, instalações, emergência e representação, além de memoriais/material acadêmico. Não inclui os textos atuais de 6118/8681/6120/7480 nem o texto específico de aço estrutural. As fontes faltantes e os limites de aplicação estão catalogados; nenhum parâmetro histórico foi habilitado automaticamente para produção.

Veja a [origem do acervo](ORIGEM_ACERVO.json) e o [inventário dos arquivos](INVENTARIO_ACERVO.json), com identificação e integridade documental.

O TCC UFRJ REL_UFRJ (2019) foi lido integralmente, 81 páginas; cita NBR 6118:2014. Na contraprova de sua viga, corrigir a altura útil de 575 para 555 mm reduz a capacidade idealizada de 207,963 para 199,768 kN·m, abaixo da demanda de 200 kN·m. É apoio acadêmico com achados críticos, sem substituir a edição vigente. O TCC UFRGS do relatório de ações teve leitura parcial explicitamente delimitada.

## Correções apontadas pela revisão

| Achado | Estado | Critério/evidência |
|---|---|---|
| A1 — demanda e narrativa do memorial com valores fixos | Corrigido, com quatro regressões Python | Entradas/saída Java atuais e hipóteses explícitas; triplicar B1 gera Md189 kN·m, Vd126 kN e conclusão parcial não atende; evidências regeneradas |
| A2 — máximo de deslocamento amostrado | Corrigido, com regressões | Extremos internos da resultante espacial; P1: 2,763916257720 mm. Auditoria informa suíte `cca-domain` 86/86; JSON/memorial regenerados |
| A3 — peso próprio do pilar concentrado no topo | Corrigido, com teste | `MontadorPesoProprioPilarTest`: equilíbrio, normal variável e encurtamento de 3,75 µm no prisma de referência |
| A4 — coeficiente arredondado no equilíbrio da flexão | Corrigido, com contraprova | A seção conhecida com x=90 mm recupera x/d=0,2 e As=6,032571428571 cm² usando `2/0,85` no discriminante |

Os achados do relatório independente descrevem o snapshot examinado; esta tabela registra a evolução da equipe. Corrigir esses defeitos não fecha as verificações normativas, de estabilidade, serviço ou detalhamento.

## Desenho e modelos

- [Planta](desenho-planta.png) e [3D](desenho-3d.png) capturados no motor real da área de treino.
- [Diagramas em PDF](ensaios-estruturais.pdf) e [PNG](ensaios-estruturais.png) para os cinco benchmarks isolados.
- [Entradas dos seis ensaios, incluindo D1](casos-estruturais.json), [saída Java](resultados-motor.json) e [referência independente](comparacao-independente.json).
- [Comandos da base](desenho-base.vi) e [cobertura](desenho-cobertura.vi). Execute a base no Térreo; selecione Cobertura antes do segundo arquivo.

## Verificação executada

Rodada do backend e Python atualizada em 11/11/2026, após A1–A4. As verificações de frontend abaixo registram a rodada anterior, sem novas alterações nesse código nesta etapa documental/numérica. O estado global segue não verificado.

- Backend: **103 testes unitários**, sem falhas; inclui casos isolados/D1, extremos de momento/deslocamento, peso próprio axial distribuído, domínio/precisão e contraprova de equilíbrio da flexão.
- API: **9 testes de integração** (`DimensionamentoIT`, `AnaliseIT`), com PostGIS/Valkey reais em Testcontainers, sem falhas. Não há aprovação executiva habilitada.
- Frontend: **23 testes unitários** (13 da Notação VI + 10 do app), sem falhas. Incluem identidade da geometria desenhada com D1, cotas e orientação da laje.
- Navegador: teste `motor-estruturas.spec.ts` passou criando os nove elementos reais; sessão de teste controlada. Validação de pixels impede aceitar uma cena 3D vazia como sucesso.
- Typecheck, build e estrutura dos documentos RF passaram. O checklist React foi aplicado às alterações de cena/Suspense, sem novos hooks condicionais.
- Python/NumPy independente: comparação dos cinco modelos isolados, 20 combinações no total, além de quatro combinações de D1 por decomposição simétrica. Reações, esforços, extremos e deslocamentos compatíveis com as hipóteses; não equivale a validação normativa global.
- Memorial: **4 regressões Python**, sem falhas, incluindo cargas triplicadas com mudança de conclusão, demanda ausente, geometria alterada e interação não suportada.
- Fontes: os 31 SHA-256 conferem com o acervo baixado; extração/índice de todos os PDF/DOCX e origem registrados. Texto extraído não é sinônimo de leitura técnica ou vigência normativa.

Falhas reproduzidas e corrigidas: extremo de momento entre as 11 seções originais; perda de precisão por arredondamento do solver; cota de vigas/lajes e espelhamento do contorno da laje na cena; geometria 3D suspensa pelo carregamento de fontes.

O memorial informa quais resistências parciais foram calculadas e quais verificações normativas faltam. As ações e combinações dos ensaios são entradas didáticas explícitas; não substituem o levantamento de cargas ou a auditoria de uma obra real. O desenho D1 foi analisado com a transferência simplificada de laje do montador atual, sem solução de placa ou verificação completa de pilares/segunda ordem.
