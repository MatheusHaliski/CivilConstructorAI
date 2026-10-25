# Catálogo verificável de comandos

Gerado por `scripts/auditoria/catalogo-verificavel.mjs` em 2026-10-25 a partir do código (não da documentação).
Referências funcionais: **AutoCAD 2026** (Windows, produto base, sem toolsets; *Command Reference* da ajuda oficial) e **Revit 2026**
(Windows, disciplinas Arquitetura e Estrutura; atalhos padrão do diálogo *Keyboard Shortcuts*). O AutoCAD tem cerca de 1.590 comandos
(contagem de fonte secundária, CAD Forum) — este catálogo cobre **85**, escolhidos para projeto civil; os demais estão
classificados como fora do escopo na seção 3, por família.

| Estado | Comandos |
|---|---:|
| Implementado e verificado | 73 |
| Parcialmente implementado | 8 |
| Ausente | 4 |

Critério: comando que altera o modelo só conta como **implementado e verificado** se for executado pelo backend (`ModeloBim.java`, fonte da
verdade), pelo motor local (`modelo.ts`, prévia) e aparecer em pelo menos um teste automatizado. Comando de vista/modo conta se o motor do
front o trata e há teste. Comando **traduzido no front** (ex.: `CONCORDAR` → `ALTERAR` + `ARCO`) conta pelo teste da tradução
(`apps/web/lib/motor/estado.test.ts`) — o que vai ao servidor é o comando traduzido, verificado à parte. A fixture
`packages/notacao-vi/fixtures/comandos-paridade.json` roda nos dois motores (`paridade.test.ts` e `ModeloBimParidadeTest`) e
prova que prévia e gravação dão o mesmo resultado. "Parcial" = executa em um dos lados, ou não tem teste.

## 1. Comandos do catálogo

| Código | pt-BR | Referência (EN) | Categoria | Backend | Motor TS | Front | Testes | Estado | Observação | RF |
|---|---|---|---|---|---|---|---|---|---|---|
| `LINHA` | LINHA | LINE | desenho | sim | sim | sim | GeradorPranchaTest.java, ModeloBimTest.java, modelo.test.ts | Implementado e verificado |  | RF01, RF14 |
| `POLILINHA` | POLILINHA | PLINE | desenho | sim | sim | sim | estado.test.ts, comandos-paridade.json | Implementado e verificado |  | RF01, RF04 |
| `CIRCULO` | CÍRCULO | CIRCLE | desenho | sim | sim | sim | comandos-paridade.json | Implementado e verificado |  | RF01 |
| `RETANGULO` | RETÂNGULO | RECTANG | desenho | sim | sim | sim | comandos-paridade.json | Implementado e verificado |  | RF01 |
| `ARCO` | ARCO | ARC | desenho | sim | sim | sim | estado.test.ts, comandos-paridade.json | Implementado e verificado |  | RF01 |
| `POLIGONO` | POLÍGONO | POLYGON | desenho | sim | sim | — | comandos-paridade.json | Implementado e verificado |  | RF01 |
| `ELIPSE` | ELIPSE | ELLIPSE | desenho | sim | sim | — | CatalogoComandosTest.java, comandos-paridade.json | Implementado e verificado |  | RF01 |
| `HACHURA` | HACHURA | HATCH | anotacao | sim | sim | sim | estado.test.ts, comandos-paridade.json | Implementado e verificado |  | RF14 |
| `SPLINE` | SPLINE | SPLINE | desenho | sim | sim | sim | comandos-paridade.json | Implementado e verificado |  | RF22, RF09 |
| `PONTO` | PONTO | POINT | desenho | sim | sim | sim | comandos-paridade.json | Implementado e verificado |  | RF09 |
| `RAIO_CONSTRUCAO` | RAIO DE CONSTRUÇÃO | RAY | desenho | sim | sim | sim | comandos-paridade.json | Implementado e verificado |  | RF01 |
| `LINHA_CONSTRUCAO` | LINHA DE CONSTRUÇÃO | XLINE | desenho | sim | sim | sim | comandos-paridade.json | Implementado e verificado |  | RF01, RF03 |
| `ANEL` | ANEL | DONUT | desenho | sim | sim | — | comandos-paridade.json | Implementado e verificado |  | RF10 |
| `REGIAO` | REGIÃO | REGION | desenho | n/a | n/a | sim | estado.test.ts | Parcialmente implementado | traduzido no front → HACHURA; vira hachura do contorno; não há operações booleanas de região | RF04, RF10, RF11 |
| `PAREDE` | PAREDE | WALL | desenho | sim | sim | sim | EscritorIfcTest.java, GeradorPranchaTest.java, EstresseAuditoriaTest.java | Implementado e verificado |  | RF01 |
| `PORTA` | PORTA | DOOR | desenho | sim | sim | sim | EscritorIfcTest.java, EstresseAuditoriaTest.java, ModeloBimTest.java | Implementado e verificado |  | RF01, RF16 |
| `JANELA` | JANELA | WINDOW | desenho | sim | sim | sim | GeradorPranchaTest.java, EstresseAuditoriaTest.java | Implementado e verificado |  | RF01, RF16 |
| `AMBIENTE` | AMBIENTE | ROOM | desenho | sim | sim | sim | comandos-paridade.json | Implementado e verificado |  | RF01, RF11 |
| `EIXO` | EIXO | GRID | desenho | sim | sim | sim | comandos-paridade.json | Implementado e verificado |  | RF03 |
| `MALHA` | MALHA DE EIXOS | GRID ARRAY | desenho | sim | sim | — | GeradorPranchaTest.java, EstresseAuditoriaTest.java, ModeloBimTest.java | Implementado e verificado |  | RF03 |
| `PAVIMENTO` | PAVIMENTO | LEVEL | desenho | sim | sim | sim | EscritorIfcTest.java, GeradorPranchaTest.java, EstresseAuditoriaTest.java | Implementado e verificado |  | RF03, RF17 |
| `PLANO_REF` | PLANO DE REFERÊNCIA | REFERENCE PLANE | desenho | sim | sim | sim | comandos-paridade.json | Implementado e verificado |  | RF01 |
| `VIGA` | VIGA | STRUCTURAL BEAM | desenho | sim | sim | sim | EscritorIfcTest.java, GeradorPranchaTest.java, EstresseAuditoriaTest.java | Implementado e verificado |  | RF04 |
| `CONTRAVENTAMENTO` | CONTRAVENTAMENTO | STRUCTURAL BRACE | desenho | sim | sim | sim | CatalogoComandosTest.java, modelo.test.ts | Implementado e verificado |  | RF04 |
| `PILAR` | PILAR | STRUCTURAL COLUMN | desenho | sim | sim | sim | EscritorIfcTest.java, GeradorPranchaTest.java, EstresseAuditoriaTest.java | Implementado e verificado |  | RF04, RF17 |
| `LAJE` | LAJE | STRUCTURAL FLOOR | desenho | sim | sim | sim | EscritorIfcTest.java, GeradorPranchaTest.java, EstresseAuditoriaTest.java | Implementado e verificado |  | RF04 |
| `FUNDACAO` | FUNDAÇÃO | FOUNDATION | desenho | sim | sim | sim | comandos-paridade.json | Implementado e verificado |  | RF10 |
| `MOVER` | MOVER | MOVE | modificacao | sim | sim | sim | CatalogoComandosTest.java, comandos-paridade.json | Implementado e verificado |  | RF28 |
| `COPIAR` | COPIAR | COPY | modificacao | sim | sim | sim | ModeloBimTest.java, comandos-paridade.json | Implementado e verificado |  | RF28 |
| `ROTACIONAR` | ROTACIONAR | ROTATE | modificacao | sim | sim | sim | comandos-paridade.json | Implementado e verificado |  | RF28 |
| `APARAR` | APARAR | TRIM | modificacao | sim | sim | sim | comandos-paridade.json | Implementado e verificado |  | RF28 |
| `ESTENDER` | ESTENDER | EXTEND | modificacao | sim | sim | sim | comandos-paridade.json | Implementado e verificado |  | RF28 |
| `CONCORDAR` | CONCORDAR | FILLET | modificacao | n/a | n/a | sim | estado.test.ts | Implementado e verificado | traduzido no front → ALTERAR + ARCO | RF28 |
| `CHANFRAR` | CHANFRAR | CHAMFER | modificacao | n/a | n/a | sim | estado.test.ts | Implementado e verificado | traduzido no front → ALTERAR + LINHA | RF28 |
| `DESLOCAR` | DESLOCAR | OFFSET | modificacao | sim | sim | sim | comandos-paridade.json | Implementado e verificado |  | RF01, RF28 |
| `ESPELHAR` | ESPELHAR | MIRROR | modificacao | sim | sim | sim | ModeloBimTest.java, comandos-paridade.json | Implementado e verificado |  | RF28 |
| `ESTICAR` | ESTICAR | STRETCH | modificacao | sim | sim | sim | comandos-paridade.json | Parcialmente implementado | a janela de captura só existe via Notação VI; no canvas estica a seleção inteira | RF28 |
| `ESCALAR` | ESCALAR | SCALE | modificacao | sim | sim | sim | comandos-paridade.json | Implementado e verificado |  | RF28 |
| `MATRIZ` | MATRIZ | ARRAY | modificacao | sim | sim | sim | comandos-paridade.json | Implementado e verificado |  | RF28 |
| `APAGAR` | APAGAR | ERASE | modificacao | sim | sim | sim | ModeloBimTest.java, comandos-paridade.json | Implementado e verificado |  | RF28 |
| `EXPLODIR` | EXPLODIR | EXPLODE | modificacao | sim | sim | sim | comandos-paridade.json | Implementado e verificado |  | RF28 |
| `UNIR` | UNIR | JOIN | modificacao | sim | sim | sim | comandos-paridade.json | Implementado e verificado |  | RF28 |
| `QUEBRAR` | QUEBRAR | BREAK | modificacao | n/a | n/a | sim | CatalogoComandosTest.java, modelo.test.ts, estado.test.ts | Implementado e verificado | traduzido no front → APAGAR + LINHA | RF28 |
| `ALONGAR` | ALONGAR | LENGTHEN | modificacao | sim | sim | sim | comandos-paridade.json | Implementado e verificado |  | RF28 |
| `ALINHAR` | ALINHAR | ALIGN | modificacao | n/a | n/a | sim | estado.test.ts | Implementado e verificado | traduzido no front → MOVER | RF28 |
| `ZOOM` | ZOOM | ZOOM | visualizacao | n/a | n/a | sim | CatalogoComandosTest.java | Implementado e verificado |  | RF13 |
| `ZOOM_TUDO` | ENQUADRAR TUDO | ZOOM EXTENTS | visualizacao | n/a | n/a | sim | estado.test.ts | Parcialmente implementado | enquadra o pavimento ativo; sem ZOOM janela/anterior no catálogo | RF13 |
| `PAN` | MOVER VISTA | PAN | visualizacao | n/a | n/a | sim | estado.test.ts | Implementado e verificado |  | RF13 |
| `REGENERAR` | REGENERAR | REGEN | visualizacao | n/a | n/a | sim | estado.test.ts | Implementado e verificado |  | RF13 |
| `OCULTAR` | OCULTAR ELEMENTO | HIDE ELEMENT | visualizacao | n/a | n/a | sim | estado.test.ts | Implementado e verificado |  | RF13 |
| `ISOLAR` | ISOLAR ELEMENTO | ISOLATE ELEMENT | visualizacao | n/a | n/a | sim | estado.test.ts | Implementado e verificado |  | RF13 |
| `REEXIBIR` | REEXIBIR ELEMENTO | UNHIDE ELEMENT | visualizacao | n/a | n/a | sim | estado.test.ts | Implementado e verificado |  | RF13 |
| `REEXIBIR_CATEGORIA` | REEXIBIR CATEGORIA | UNHIDE CATEGORY | visualizacao | n/a | n/a | sim | estado.test.ts | Parcialmente implementado | reexibe tudo; o VU do Revit reexibe só a categoria escolhida | RF13 |
| `VISIBILIDADE` | VISIBILIDADE E ESTILOS | VISIBILITY/GRAPHICS | visualizacao | n/a | n/a | sim | estado.test.ts | Parcialmente implementado | abre o painel de camadas; não há estilos gráficos por categoria (V/G) | RF13 |
| `CAMADAS` | CAMADAS | LAYER | visualizacao | n/a | n/a | sim | estado.test.ts | Implementado e verificado |  | RF13 |
| `COPIAR_PROPRIEDADES` | COPIAR PROPRIEDADES | MATCHPROP | modificacao | n/a | n/a | sim | estado.test.ts | Implementado e verificado | traduzido no front → ALTERAR | RF13 |
| `PROPRIEDADES` | PROPRIEDADES | PROPERTIES | visualizacao | n/a | n/a | sim | estado.test.ts | Implementado e verificado |  | RF01 |
| `TEXTO` | TEXTO | MTEXT | anotacao | sim | sim | sim | GeradorPranchaTest.java, PranchaIT.java, comandos-paridade.json | Implementado e verificado |  | RF14 |
| `ESTILO_COTA` | ESTILO DE COTA | DIMSTYLE | anotacao | n/a | n/a | sim | estado.test.ts | Ausente | só aviso no motor ("age na prancha"); sem execução | RF14 |
| `COTA_LINEAR` | COTA LINEAR | DIMLINEAR | anotacao | sim | sim | sim | GeradorPranchaTest.java, CatalogoComandosTest.java, estado.test.ts | Implementado e verificado |  | RF14 |
| `COTA_ALINHADA` | COTA ALINHADA | DIMALIGNED | anotacao | sim | sim | sim | comandos-paridade.json | Implementado e verificado |  | RF14 |
| `COTA_CONTINUA` | COTA CONTÍNUA | DIMCONTINUE | anotacao | n/a | n/a | sim | estado.test.ts | Implementado e verificado | traduzido no front → COTA_LINEAR | RF14 |
| `COTA_NIVEL` | COTA DE NÍVEL | SPOT ELEVATION | anotacao | sim | sim | sim | CatalogoComandosTest.java | Implementado e verificado |  | RF14 |
| `LINHA_DETALHE` | LINHA DE DETALHE | DETAIL LINE | anotacao | sim | sim | sim | estado.test.ts, comandos-paridade.json | Implementado e verificado |  | RF14 |
| `ETIQUETA` | ETIQUETA | TAG BY CATEGORY | anotacao | sim | sim | sim | comandos-paridade.json | Implementado e verificado |  | RF14 |
| `BLOCO` | BLOCO | BLOCK | anotacao | sim | sim | sim | estado.test.ts | Ausente | só aviso no motor ("age na prancha"); sem execução. O mesmo código, enviado em VI, cria um bloco de fundação no backend — ambiguidade a resolver | RF14 |
| `INSERIR` | INSERIR BLOCO | INSERT | anotacao | — | — | sim | estado.test.ts | Ausente | só aviso no motor ("age na prancha"); sem execução | RF14 |
| `LINHA_QUEBRA` | LINHA DE QUEBRA | BREAKLINE | anotacao | n/a | n/a | sim | estado.test.ts | Parcialmente implementado | traduzido no front → LINHA_DETALHE; gravada como linha de detalhe; sem o símbolo em zigue-zague | RF14 |
| `LOCALIZAR` | LOCALIZAR E SUBSTITUIR | FIND | anotacao | n/a | n/a | sim | estado.test.ts | Ausente | só aviso no motor ("age na prancha"); sem execução | RF14 |
| `OSNAP` | ATRAÇÃO A OBJETOS | OSNAP (F3) | modo | n/a | n/a | sim | CatalogoComandosTest.java | Implementado e verificado |  | RF28 |
| `OSNAP3D` | ATRAÇÃO 3D | 3D OSNAP (F4) | modo | n/a | n/a | sim | estado.test.ts | Parcialmente implementado | liga o modo; a vista 3D só inspeciona e seleciona — não recebe pontos, então não há o que atrair | RF28 |
| `PLANO_TRABALHO` | PLANO DE TRABALHO | ISOPLANE (F5) | modo | n/a | n/a | sim | estado.test.ts | Implementado e verificado |  | RF28 |
| `UCS_DINAMICO` | UCS DINÂMICO | DYNAMIC UCS (F6) | modo | n/a | n/a | sim | estado.test.ts | Implementado e verificado |  | RF28 |
| `GRADE` | GRADE | GRID (F7) | modo | n/a | n/a | sim | estado.test.ts | Implementado e verificado |  | RF28 |
| `ORTO` | ORTO | ORTHO (F8) | modo | n/a | n/a | sim | estado.test.ts | Implementado e verificado |  | RF28 |
| `SNAP_GRADE` | SNAP DE GRADE | SNAP (F9) | modo | n/a | n/a | sim | estado.test.ts | Implementado e verificado |  | RF28 |
| `POLAR` | RASTREAMENTO POLAR | POLAR (F10) | modo | n/a | n/a | sim | estado.test.ts | Implementado e verificado |  | RF28 |
| `OTRACK` | RASTREAMENTO DE OBJETOS | OTRACK (F11) | modo | n/a | n/a | sim | estado.test.ts | Implementado e verificado |  | RF28 |
| `ENTRADA_DINAMICA` | ENTRADA DINÂMICA | DYNAMIC INPUT (F12) | modo | n/a | n/a | sim | estado.test.ts | Implementado e verificado |  | RF28 |
| `HISTORICO` | HISTÓRICO DE COMANDOS | TEXT SCREEN (F2) | config | n/a | n/a | sim | estado.test.ts | Implementado e verificado |  | RF28 |
| `AJUDA` | AJUDA | HELP (F1) | config | n/a | n/a | sim | estado.test.ts | Implementado e verificado |  | RF28 |
| `UNIDADES` | UNIDADES DO PROJETO | PROJECT UNITS | config | n/a | n/a | sim | estado.test.ts | Parcialmente implementado | só informa (m, kN, MPa); não é configurável | RF28 |
| `ATALHOS` | ATALHOS DE TECLADO | KEYBOARD SHORTCUTS | config | n/a | n/a | sim | estado.test.ts | Implementado e verificado |  | RF28 |
| `DESFAZER` | DESFAZER | UNDO | modificacao | n/a | n/a | sim | estado.test.ts | Implementado e verificado | traduzido no front → desfazer do servidor | RF01 |
| `REFAZER` | REFAZER | REDO | modificacao | n/a | n/a | sim | estado.test.ts | Implementado e verificado | traduzido no front → refazer local | RF01 |

## 2. Comandos executados pelo backend que não estão no catálogo

`SAPATA`, `RADIER`, `ALTERAR`

(Alguns são internos — `ALTERAR`, `MOVER_EIXO` — e outros são sinônimos de domínio, como `SAPATA`/`BLOCO`/`RADIER` para `FUNDACAO`.)

## 3. Famílias de referência (AutoCAD 2026 / Revit 2026) fora do catálogo

Classificação por família, para o que o catálogo **não** cobre. "Fora do escopo" = decisão de produto (o CivilConstructorAI não é um
substituto geral do AutoCAD); "Dependente de integração" = exige biblioteca/serviço externo ainda não contratado; "Ausente" = deveria
existir para o fluxo de projeto civil e não existe.

| Família | Exemplos na referência | Situação no CivilConstructorAI | Classificação |
|---|---|---|---|
| Desenho 2D | XLINE, RAY, DONUT, REVCLOUD, WIPEOUT, MLINE | XLINE/RAY/DONUT presentes; nuvem de revisão, wipeout e multilinha não existem | Ausente (REVCLOUD é útil em revisão) / Fora do escopo (MLINE — a parede já é paramétrica) |
| Edição | LENGTHEN (DY, TOTAL), PEDIT, SPLINEDIT, OVERKILL, GRIPS | ALONGAR só por incremento; sem edição de vértices de polilinha nem limpeza de duplicados | Parcial / Ausente |
| Precisão | OSNAP (todos os modos), TRACKING, FROM, CAL, UCS 3 pontos | Osnap de 9 modos, polar, orto, grade, entrada dinâmica (absoluta, relativa, polar); sem FROM e sem UCS por 3 pontos | Parcial |
| Organização | LAYER (estados, filtros), LAYISO, GROUP, XREF, PURGE | Camadas fixas por disciplina (desenho, anotação, estrutura, arquitetura, fundação, ia-rascunho); sem grupos, sem estados de camada | Parcial |
| Componentes | BLOCK, INSERT, ATTDEF, dynamic blocks; famílias do Revit | Sem biblioteca de blocos; elementos construtivos paramétricos (parede, pilar, viga, laje, porta, janela, fundação) cumprem o papel de família | Ausente (blocos) / Parcial (famílias) |
| Referências externas | XREF, ATTACH (PDF/DWG/imagem), Link Revit/IFC | Importação de IFC (RF17) cria elementos; não há vínculo vivo | Dependente de integração |
| Anotações | DIM*, MLEADER, TEXT/MTEXT, TABLE, tags e schedules do Revit | Cotas linear, alinhada, contínua, de nível; texto; etiqueta; quadros nas pranchas; sem tabela editável | Parcial |
| 3D | EXTRUDE, PRESSPULL, BOOLEAN, SECTIONPLANE; vistas 3D/cortes do Revit | Vista 3D de inspeção gerada do modelo; sem modelagem sólida livre nem corte interativo | Fora do escopo (sólidos livres) / Ausente (corte) |
| Inspeção | DIST, AREA, MEASUREGEOM, LIST, ID, interferências | Propriedades mostram medidas; auditor de regras (vãos, níveis, aberturas); quantitativos calculam áreas/volumes; sem detecção de interferência 3D | Parcial |
| Documentação | LAYOUT, VIEWPORTS, PLOT, PUBLISH; folhas do Revit | Pranchas geradas (RF14) com selo e emissão; SVG e DXF; sem PDF e sem viewport editável | Parcial |
| Automação | SCRIPT, LISP, ACTION RECORDER, Dynamo | Notação VI em lote (console), comandos por texto natural (simulação em dev), ações construtivas (RF29) | Parcial |
| Colaboração | Sheet Set, DWG compare, Worksharing/Central model, BIM Collaborate | Papéis por obra, histórico de comandos com autor, rascunhos de IA aprovados por profissional; sem comentários em elemento, sem bloqueio de elemento, sem comparar/restaurar versão | Ausente |
| Interoperabilidade | DWG, DXF, PDF, DWF, IFC, BCF, NWC | IFC4X3_ADD2 (escrita e leitura, sem materiais), DXF/SVG (escrita), CSV/XLSX (quantitativos/orçamento); sem DWG, PDF, BCF e STEP | Parcial / Dependente de integração (DWG — biblioteca ODA) |
