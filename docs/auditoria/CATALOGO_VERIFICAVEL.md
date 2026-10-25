# Catálogo verificável de comandos

Gerado por `scripts/auditoria/catalogo-verificavel.mjs` em 2026-10-25 a partir do código (não da documentação).
Referências funcionais: **AutoCAD 2026** (Windows, produto base, sem toolsets; *Command Reference* da ajuda oficial) e **Revit 2026**
(Windows, disciplinas Arquitetura e Estrutura; atalhos padrão do diálogo *Keyboard Shortcuts*). O AutoCAD tem cerca de 1.590 comandos
(contagem de fonte secundária, CAD Forum) — este catálogo cobre **85**, escolhidos para projeto civil; os demais estão
classificados como fora do escopo na seção 3, por família.

| Estado | Comandos |
|---|---:|
| Implementado e verificado | 20 |
| Parcialmente implementado | 56 |
| Ausente | 9 |

Critério: comando que altera o modelo só conta como **implementado e verificado** se for executado pelo backend (`ModeloBim.java`, fonte da
verdade), pelo motor local (`modelo.ts`, prévia) e aparecer em pelo menos um teste automatizado. Comando de vista/modo conta se o motor do
front o trata e há teste. "Parcial" = executa em um dos lados, ou não tem teste.

## 1. Comandos do catálogo

| Código | pt-BR | Referência (EN) | Categoria | Backend | Motor TS | Front | Testes | Estado | RF |
|---|---|---|---|---|---|---|---|---|---|
| `LINHA` | LINHA | LINE | desenho | sim | sim | sim | GeradorPranchaTest.java, ModeloBimTest.java, modelo.test.ts | Implementado e verificado | RF01, RF14 |
| `POLILINHA` | POLILINHA | PLINE | desenho | sim | sim | sim | — | Parcialmente implementado | RF01, RF04 |
| `CIRCULO` | CÍRCULO | CIRCLE | desenho | sim | sim | sim | — | Parcialmente implementado | RF01 |
| `RETANGULO` | RETÂNGULO | RECTANG | desenho | sim | sim | sim | — | Parcialmente implementado | RF01 |
| `ARCO` | ARCO | ARC | desenho | sim | sim | sim | — | Parcialmente implementado | RF01 |
| `POLIGONO` | POLÍGONO | POLYGON | desenho | sim | sim | — | — | Parcialmente implementado | RF01 |
| `ELIPSE` | ELIPSE | ELLIPSE | desenho | sim | sim | — | CatalogoComandosTest.java | Implementado e verificado | RF01 |
| `HACHURA` | HACHURA | HATCH | anotacao | sim | sim | sim | — | Parcialmente implementado | RF14 |
| `SPLINE` | SPLINE | SPLINE | desenho | sim | sim | sim | — | Parcialmente implementado | RF22, RF09 |
| `PONTO` | PONTO | POINT | desenho | sim | sim | sim | — | Parcialmente implementado | RF09 |
| `RAIO_CONSTRUCAO` | RAIO DE CONSTRUÇÃO | RAY | desenho | sim | sim | sim | — | Parcialmente implementado | RF01 |
| `LINHA_CONSTRUCAO` | LINHA DE CONSTRUÇÃO | XLINE | desenho | sim | sim | sim | — | Parcialmente implementado | RF01, RF03 |
| `ANEL` | ANEL | DONUT | desenho | sim | sim | — | — | Parcialmente implementado | RF10 |
| `REGIAO` | REGIÃO | REGION | desenho | sim | sim | sim | — | Parcialmente implementado | RF04, RF10, RF11 |
| `PAREDE` | PAREDE | WALL | desenho | sim | sim | sim | EscritorIfcTest.java, GeradorPranchaTest.java, EstresseAuditoriaTest.java | Implementado e verificado | RF01 |
| `PORTA` | PORTA | DOOR | desenho | sim | sim | sim | EscritorIfcTest.java, EstresseAuditoriaTest.java, ModeloBimTest.java | Implementado e verificado | RF01, RF16 |
| `JANELA` | JANELA | WINDOW | desenho | sim | sim | sim | GeradorPranchaTest.java, EstresseAuditoriaTest.java | Implementado e verificado | RF01, RF16 |
| `AMBIENTE` | AMBIENTE | ROOM | desenho | sim | sim | sim | — | Parcialmente implementado | RF01, RF11 |
| `EIXO` | EIXO | GRID | desenho | sim | sim | sim | — | Parcialmente implementado | RF03 |
| `MALHA` | MALHA DE EIXOS | GRID ARRAY | desenho | sim | sim | — | GeradorPranchaTest.java, EstresseAuditoriaTest.java, ModeloBimTest.java | Implementado e verificado | RF03 |
| `PAVIMENTO` | PAVIMENTO | LEVEL | desenho | sim | sim | sim | EscritorIfcTest.java, GeradorPranchaTest.java, EstresseAuditoriaTest.java | Implementado e verificado | RF03, RF17 |
| `PLANO_REF` | PLANO DE REFERÊNCIA | REFERENCE PLANE | desenho | sim | sim | sim | — | Parcialmente implementado | RF01 |
| `VIGA` | VIGA | STRUCTURAL BEAM | desenho | sim | sim | sim | EscritorIfcTest.java, GeradorPranchaTest.java, EstresseAuditoriaTest.java | Implementado e verificado | RF04 |
| `CONTRAVENTAMENTO` | CONTRAVENTAMENTO | STRUCTURAL BRACE | desenho | sim | sim | sim | CatalogoComandosTest.java, modelo.test.ts | Implementado e verificado | RF04 |
| `PILAR` | PILAR | STRUCTURAL COLUMN | desenho | sim | sim | sim | EscritorIfcTest.java, GeradorPranchaTest.java, EstresseAuditoriaTest.java | Implementado e verificado | RF04, RF17 |
| `LAJE` | LAJE | STRUCTURAL FLOOR | desenho | sim | sim | sim | EscritorIfcTest.java, GeradorPranchaTest.java, EstresseAuditoriaTest.java | Implementado e verificado | RF04 |
| `FUNDACAO` | FUNDAÇÃO | FOUNDATION | desenho | sim | sim | sim | — | Parcialmente implementado | RF10 |
| `MOVER` | MOVER | MOVE | modificacao | sim | sim | sim | CatalogoComandosTest.java | Implementado e verificado | RF28 |
| `COPIAR` | COPIAR | COPY | modificacao | sim | sim | sim | ModeloBimTest.java | Implementado e verificado | RF28 |
| `ROTACIONAR` | ROTACIONAR | ROTATE | modificacao | sim | sim | sim | — | Parcialmente implementado | RF28 |
| `APARAR` | APARAR | TRIM | modificacao | sim | sim | sim | — | Parcialmente implementado | RF28 |
| `ESTENDER` | ESTENDER | EXTEND | modificacao | sim | sim | sim | — | Parcialmente implementado | RF28 |
| `CONCORDAR` | CONCORDAR | FILLET | modificacao | — | — | sim | — | Ausente | RF28 |
| `CHANFRAR` | CHANFRAR | CHAMFER | modificacao | — | — | sim | — | Ausente | RF28 |
| `DESLOCAR` | DESLOCAR | OFFSET | modificacao | sim | sim | sim | — | Parcialmente implementado | RF01, RF28 |
| `ESPELHAR` | ESPELHAR | MIRROR | modificacao | sim | sim | sim | ModeloBimTest.java | Implementado e verificado | RF28 |
| `ESTICAR` | ESTICAR | STRETCH | modificacao | sim | sim | sim | — | Parcialmente implementado | RF28 |
| `ESCALAR` | ESCALAR | SCALE | modificacao | sim | sim | sim | — | Parcialmente implementado | RF28 |
| `MATRIZ` | MATRIZ | ARRAY | modificacao | sim | sim | sim | — | Parcialmente implementado | RF28 |
| `APAGAR` | APAGAR | ERASE | modificacao | sim | sim | sim | ModeloBimTest.java | Implementado e verificado | RF28 |
| `EXPLODIR` | EXPLODIR | EXPLODE | modificacao | sim | sim | sim | — | Parcialmente implementado | RF28 |
| `UNIR` | UNIR | JOIN | modificacao | sim | sim | sim | — | Parcialmente implementado | RF28 |
| `QUEBRAR` | QUEBRAR | BREAK | modificacao | — | — | sim | CatalogoComandosTest.java, modelo.test.ts | Ausente | RF28 |
| `ALONGAR` | ALONGAR | LENGTHEN | modificacao | sim | sim | sim | — | Parcialmente implementado | RF28 |
| `ALINHAR` | ALINHAR | ALIGN | modificacao | sim | — | sim | — | Parcialmente implementado | RF28 |
| `ZOOM` | ZOOM | ZOOM | visualizacao | n/a | n/a | sim | CatalogoComandosTest.java | Implementado e verificado | RF13 |
| `ZOOM_TUDO` | ENQUADRAR TUDO | ZOOM EXTENTS | visualizacao | n/a | n/a | sim | — | Parcialmente implementado | RF13 |
| `PAN` | MOVER VISTA | PAN | visualizacao | n/a | n/a | sim | — | Parcialmente implementado | RF13 |
| `REGENERAR` | REGENERAR | REGEN | visualizacao | n/a | n/a | sim | — | Parcialmente implementado | RF13 |
| `OCULTAR` | OCULTAR ELEMENTO | HIDE ELEMENT | visualizacao | n/a | n/a | sim | — | Parcialmente implementado | RF13 |
| `ISOLAR` | ISOLAR ELEMENTO | ISOLATE ELEMENT | visualizacao | n/a | n/a | sim | — | Parcialmente implementado | RF13 |
| `REEXIBIR` | REEXIBIR ELEMENTO | UNHIDE ELEMENT | visualizacao | n/a | n/a | sim | — | Parcialmente implementado | RF13 |
| `REEXIBIR_CATEGORIA` | REEXIBIR CATEGORIA | UNHIDE CATEGORY | visualizacao | n/a | n/a | sim | — | Parcialmente implementado | RF13 |
| `VISIBILIDADE` | VISIBILIDADE E ESTILOS | VISIBILITY/GRAPHICS | visualizacao | n/a | n/a | sim | — | Parcialmente implementado | RF13 |
| `CAMADAS` | CAMADAS | LAYER | visualizacao | n/a | n/a | sim | — | Parcialmente implementado | RF13 |
| `COPIAR_PROPRIEDADES` | COPIAR PROPRIEDADES | MATCHPROP | modificacao | — | — | sim | — | Ausente | RF13 |
| `PROPRIEDADES` | PROPRIEDADES | PROPERTIES | visualizacao | n/a | n/a | sim | — | Parcialmente implementado | RF01 |
| `TEXTO` | TEXTO | MTEXT | anotacao | sim | sim | sim | GeradorPranchaTest.java, PranchaIT.java | Implementado e verificado | RF14 |
| `ESTILO_COTA` | ESTILO DE COTA | DIMSTYLE | anotacao | n/a | n/a | sim | — | Parcialmente implementado | RF14 |
| `COTA_LINEAR` | COTA LINEAR | DIMLINEAR | anotacao | sim | sim | sim | GeradorPranchaTest.java, CatalogoComandosTest.java | Implementado e verificado | RF14 |
| `COTA_ALINHADA` | COTA ALINHADA | DIMALIGNED | anotacao | sim | sim | sim | — | Parcialmente implementado | RF14 |
| `COTA_CONTINUA` | COTA CONTÍNUA | DIMCONTINUE | anotacao | — | — | sim | — | Ausente | RF14 |
| `COTA_NIVEL` | COTA DE NÍVEL | SPOT ELEVATION | anotacao | sim | sim | sim | CatalogoComandosTest.java | Implementado e verificado | RF14 |
| `LINHA_DETALHE` | LINHA DE DETALHE | DETAIL LINE | anotacao | sim | sim | sim | — | Parcialmente implementado | RF14 |
| `ETIQUETA` | ETIQUETA | TAG BY CATEGORY | anotacao | sim | sim | sim | — | Parcialmente implementado | RF14 |
| `BLOCO` | BLOCO | BLOCK | anotacao | sim | sim | sim | — | Parcialmente implementado | RF14 |
| `INSERIR` | INSERIR BLOCO | INSERT | anotacao | — | — | sim | — | Ausente | RF14 |
| `LINHA_QUEBRA` | LINHA DE QUEBRA | BREAKLINE | anotacao | — | — | sim | — | Ausente | RF14 |
| `LOCALIZAR` | LOCALIZAR E SUBSTITUIR | FIND | anotacao | n/a | n/a | sim | — | Parcialmente implementado | RF14 |
| `OSNAP` | ATRAÇÃO A OBJETOS | OSNAP (F3) | modo | n/a | n/a | sim | CatalogoComandosTest.java | Implementado e verificado | RF28 |
| `OSNAP3D` | ATRAÇÃO 3D | 3D OSNAP (F4) | modo | n/a | n/a | sim | — | Parcialmente implementado | RF28 |
| `PLANO_TRABALHO` | PLANO DE TRABALHO | ISOPLANE (F5) | modo | n/a | n/a | sim | — | Parcialmente implementado | RF28 |
| `UCS_DINAMICO` | UCS DINÂMICO | DYNAMIC UCS (F6) | modo | n/a | n/a | sim | — | Parcialmente implementado | RF28 |
| `GRADE` | GRADE | GRID (F7) | modo | n/a | n/a | sim | — | Parcialmente implementado | RF28 |
| `ORTO` | ORTO | ORTHO (F8) | modo | n/a | n/a | sim | — | Parcialmente implementado | RF28 |
| `SNAP_GRADE` | SNAP DE GRADE | SNAP (F9) | modo | n/a | n/a | sim | — | Parcialmente implementado | RF28 |
| `POLAR` | RASTREAMENTO POLAR | POLAR (F10) | modo | n/a | n/a | sim | — | Parcialmente implementado | RF28 |
| `OTRACK` | RASTREAMENTO DE OBJETOS | OTRACK (F11) | modo | n/a | n/a | sim | — | Parcialmente implementado | RF28 |
| `ENTRADA_DINAMICA` | ENTRADA DINÂMICA | DYNAMIC INPUT (F12) | modo | n/a | n/a | sim | — | Parcialmente implementado | RF28 |
| `HISTORICO` | HISTÓRICO DE COMANDOS | TEXT SCREEN (F2) | config | n/a | n/a | sim | — | Parcialmente implementado | RF28 |
| `AJUDA` | AJUDA | HELP (F1) | config | n/a | n/a | sim | — | Parcialmente implementado | RF28 |
| `UNIDADES` | UNIDADES DO PROJETO | PROJECT UNITS | config | n/a | n/a | sim | — | Parcialmente implementado | RF28 |
| `ATALHOS` | ATALHOS DE TECLADO | KEYBOARD SHORTCUTS | config | n/a | n/a | sim | — | Parcialmente implementado | RF28 |
| `DESFAZER` | DESFAZER | UNDO | modificacao | — | — | sim | — | Ausente | RF01 |
| `REFAZER` | REFAZER | REDO | modificacao | — | — | sim | — | Ausente | RF01 |

## 2. Comandos executados pelo backend que não estão no catálogo

`SAPATA`, `RADIER`, `ALTERAR`

(Alguns são internos — `ALTERAR`, `MOVER_EIXO` — e outros são sinônimos de domínio, como `SAPATA`/`BLOCO`/`RADIER` para `FUNDACAO`.)
