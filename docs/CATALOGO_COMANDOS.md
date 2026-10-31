# Catálogo de comandos do motor de desenho 2D/3D

Referência do **motor de desenho** do CivilConstructorAI (RF28), construída a partir do *Guia completo de
comandos e atalhos — AutoCAD, Revit e AutoCAD Civil 3D*. Cada comando do guia foi classificado em uma de
quatro categorias e mapeado para o que ele vira dentro do app:

| Categoria | O que é | Vira |
|---|---|---|
| **Desenho** | cria geometria | função da **Tabela B** da Notação VI → `ComandoVI` → loop duplo (RF16) → modelo |
| **Modificação** | altera geometria existente | função da Tabela B sobre uma seleção → `ComandoVI` |
| **Visualização / anotação / organização** | muda a vista, a visibilidade, cotas e textos | estado da cena (RF13) ou objeto de prancha (RF14); **não** passa pelo auditor quando não muda o modelo |
| **Modo / variável de sistema** | liga ou desliga um comportamento do cursor ou da seleção | `ConfiguracaoMotor` (por usuário, salva nas preferências) |

Convenções:

- **Alias** é o que se digita na linha de comando ou aperta no teclado. Os aliases do AutoCAD e do Revit
  convivem em **perfis de atalho** (`PERFIL_CIVILCONSTRUCTOR` padrão, `PERFIL_AUTOCAD`, `PERFIL_REVIT`); quando
  o mesmo alias tem significado diferente nos dois mundos (ex.: `BR`), o perfil decide e a paleta mostra os
  dois com o nome por extenso.
- Nome por extenso em **pt-BR** é sempre aceito (`PAREDE`, `MOVER`); o nome em inglês do guia também
  (`WALL`, `MOVE`), para quem vem do AutoCAD/Revit.
- Todo comando que muda o modelo responde às opções `[Desfazer]` e `[Fechar]`/`[Sair]` na entrada dinâmica.
- Função VI: `VI(COD, args...)`. Argumentos entre `<>` são pontos ou valores pedidos ao usuário; `?` marca
  opcional.

## 1. Desenho (Draw)

### 1.1 AutoCAD → Tabela B

| Alias | Comando (guia) | Função VI | O que faz no app | RF |
|---|---|---|---|---|
| `L` | LINE | `VI(LINHA, <p1>, <p2>...)` | linha auxiliar ou de detalhe 2D (camada `desenho`), não é elemento construtivo | RF01, RF14 |
| `PL` | PLINE | `VI(POLILINHA, <p1>...<pn>, fechar?)` | polilinha; fechada vira contorno de **laje** (`VI(LAJE, contorno)`) ou perímetro de obra | RF01, RF04 |
| `C` | CIRCLE | `VI(CIRCULO, <centro>, <raio>)` | círculo auxiliar; como seção de pilar → `VI(PILAR, <centro>, d=<raio*2>, secao=CIRCULAR)` | RF01, RF04 |
| `REC` | RECTANG | `VI(RETANGULO, <p1>, <p2>)` | retângulo auxiliar; como seção de pilar → `VI(PILAR, <centro>, b, h)`; como laje → contorno | RF01 |
| `A` | ARC | `VI(ARCO, <p1>, <p2>, <p3>)` | arco de 3 pontos; parede curva → `VI(PAREDE_CURVA, ...)` | RF01 |
| `POL` | POLYGON | `VI(POLIGONO, <centro>, n, <raio>, inscrito?)` | polígono regular (pilar poligonal, base de reservatório) | RF01 |
| `EL` | ELLIPSE | `VI(ELIPSE, <centro>, <a>, <b>)` | elipse auxiliar | RF01 |
| `H` | HATCH | `VI(HACHURA, <contorno>, padrao)` | hachura de corte/material nas pranchas (padrão ABNT por material da Tabela A) | RF14 |
| `SPL` | SPLINE | `VI(SPLINE, <p1>...<pn>)` | curva suave: **curvas de nível** e topografia do terreno | RF22, RF09 |
| `PO` | POINT | `VI(PONTO, <p>, rotulo?)` | ponto de referência; com rótulo `SP-nn` vira **furo de sondagem** | RF09 |
| `ML` | MLINE | `VI(PAREDE, <p1>, <p2>, esp, h)` | linhas paralelas = **parede** (equivalente ao `WA` do Revit) | RF01 |
| `RAY` | RAY | `VI(RAIO_CONSTRUCAO, <origem>, <direcao>)` | linha de construção semi-infinita (não imprime) | RF01 |
| `XL` | XLINE | `VI(LINHA_CONSTRUCAO, <p>, <direcao>)` | linha de construção infinita; sobre eixo vira `VI(EIXO, ...)` | RF01, RF03 |
| `DO` | DONUT | `VI(ANEL, <centro>, <ri>, <re>)` | anel; seção de tubo/estaca circular vazada | RF10 |
| `REG` | REGION | `VI(REGIAO, <contorno>)` | região 2D fechada; base para laje, radier e cálculo de área | RF04, RF10, RF11 |

### 1.2 Revit (arquitetura, estrutura, MEP) → Tabela B

| Alias | Comando (guia) | Função VI | O que faz no app | RF |
|---|---|---|---|---|
| `WA` | Wall | `VI(PAREDE, <p1>, <p2>, esp, h, mat)` | parede básica, composta ou cortina (tipo pelo material da Tabela A) | RF01 |
| `DR` | Door | `VI(PORTA, <parede>, <pos>, larg, alt)` | porta hospedada na parede → `NewOpening` por projeção vetorial | RF01, RF16 |
| `WN` | Window | `VI(JANELA, <parede>, <pos>, larg, alt, peitoril)` | janela hospedada | RF01, RF16 |
| `RM` | Room | `VI(AMBIENTE, <ponto_interno>, nome)` | ambiente delimitado por paredes; calcula área e perímetro | RF01, RF11 |
| `GR` | Grid | `VI(EIXO, <p1>, <p2>, nome)` / `VI(MALHA, nx, ny, dx, dy)` | eixo estrutural; `MALHA` gera a grade inteira | RF03 |
| `LL` | Level | `VI(PAVIMENTO, nome, elevacao_z)` | nível/pavimento em Z; base da Disciplina Z | RF03, RF17 |
| `RP` | Reference Plane | `VI(PLANO_REF, <p1>, <p2>, nome?)` | plano de referência não imprimível para alinhamento | RF01 |
| `BM` | Structural Beam | `VI(VIGA, <p1>, <p2>, b, h, mat)` | viga entre apoios | RF04 |
| `BR`¹ | Structural Brace | `VI(CONTRAVENTAMENTO, <p1>, <p2>, perfil)` | contraventamento (aço) | RF04 |
| `CL`² | Structural Column | `VI(PILAR, <centro>, b, h, mat, nivel_base?, nivel_topo?)` | pilar; base/topo presos aos pavimentos | RF04, RF17 |
| `SB`² | Structural Floor | `VI(LAJE, <contorno>, h, mat)` | laje estrutural | RF04 |
| `FD`² | Fundação | `VI(SAPATA...)`, `VI(BLOCO...)`, `VI(RADIER...)` | fundação gerada pelo RF10 ou lançada à mão | RF10 |
| `CM` / `OB`² | Component | `VI(OBJETO, item, <pos>, rot, altura?)` | móvel, equipamento, louça, luminária, ponto de instalação ou pessoa da biblioteca (`catalogo/objetos.json`); objeto de parede encosta na face, de teto vai ao teto livre; equipamento não deforma, pessoa escala de modo uniforme | RF01, RF13 |
| `ST`² | Stair | `VI(ESCADA, <p1>, <p2>, larg, desnivel?, degraus?)` | escada reta do pé ao topo; desnível até o pavimento de cima quando omitido; degraus pelo espelho adotado de 17,5 cm (ajustável); pessoa sobre ela fica na altura do degrau | RF01, RF13 |
| `RAM`² | Ramp | `VI(RAMPA, <p1>, <p2>, larg, desnivel?)` | rampa reta; inclinação derivada do desnível (não verificada contra norma) | RF01, RF13 |
| `CN`² | Connect | `VI(CONECTAR, <origem[:conector]>, <destino[:conector]>, circuito?)` | conexão física saída → entrada entre conectores do mesmo sistema (elétrica, água fria/quente, esgoto, ventilação); recusa tensão, corrente, sistema ou sentido incompatíveis; cabo de aparelho herda o circuito da tomada | RF01, RF16 |
| `DT` | Duct | `VI(DUTO, ...)` | **fora do MVP** (o percurso de conexões é esquemático, derivado de `CONECTAR`) | — |
| `PI` | Pipe | `VI(TUBULACAO, ...)` | **fora do MVP** (idem) | — |

¹ `BR` colide com BREAK do AutoCAD: no `PERFIL_CIVILCONSTRUCTOR`, `BR` = contraventamento e BREAK usa `QB`
(quebrar). ² Não constam do guia; são extensões próprias no padrão do Revit, marcadas como tais na paleta.

## 2. Modificação (Modify)

| Alias | Comando (guia) | Função VI | Comportamento | Observações |
|---|---|---|---|---|
| `M` / `MV` | MOVE / Move | `VI(MOVER, <sel>, <base>, <destino>)` | move seleção com precisão por Osnap | pilares reancorados ao eixo mais próximo (RF03) |
| `CO` / `CP` | COPY / Copy | `VI(COPIAR, <sel>, <base>, <destino>...)` | cópia múltipla até `[Sair]` | |
| `RO` | ROTATE / Rotate | `VI(ROTACIONAR, <sel>, <base>, ang)` | gira em torno do ponto base, no plano ativo | |
| `TR` | TRIM | `VI(APARAR, <arestas>, <objetos>)` | corta em interseções | só linhas, polilinhas e paredes |
| `EX` | EXTEND | `VI(ESTENDER, <arestas>, <objetos>)` | estende até a aresta | |
| `F` | FILLET | `VI(CONCORDAR, <o1>, <o2>, raio)` | concordância; `raio=0` fecha canto vivo entre paredes | |
| `CHA` | CHAMFER | `VI(CHANFRAR, <o1>, <o2>, d1, d2)` | chanfro | |
| `O` | OFFSET | `VI(DESLOCAR, <sel>, dist, <lado>)` | cópia paralela (paredes, eixos, contornos) | `MULTIPLE` = repete até `[Sair]` |
| `MI` | MIRROR | `VI(ESPELHAR, <sel>, <p1>, <p2>, apagar?)` | espelha em torno de uma linha | |
| `S` | STRETCH | `VI(ESTICAR, <janela>, <base>, <destino>)` | estica o que a janela cruza | pilar não estica: move |
| `SC` | SCALE | `VI(ESCALAR, <sel>, <base>, fator)` | escala proporcional | bloqueado para elementos construtivos (fica só para auxiliares) |
| `AR` | ARRAY / Array | `VI(MATRIZ, <sel>, tipo, n, passo)` | retangular, polar ou em caminho | pilares em matriz seguem a malha |
| `E` / `DE` | ERASE / Delete | `VI(APAGAR, <sel>)` | remove; gera comando inverso para desfazer | abertura sem parede hospedeira é apagada junto |
| `X` | EXPLODE | `VI(EXPLODIR, <sel>)` | polilinha → linhas; bloco → elementos | elemento construtivo não explode |
| `J` | JOIN | `VI(UNIR, <sel>)` | linhas/arcos → uma polilinha | |
| `QB` (`BR` no perfil AutoCAD) | BREAK | `VI(QUEBRAR, <obj>, <p1>, <p2>)` | quebra entre dois pontos | |
| `LEN` | LENGTHEN | `VI(ALONGAR, <obj>, delta ou total)` | muda comprimento/ângulo | |
| `AL` | Align | `VI(ALINHAR, <referencia>, <sel>)` | alinha faces/bordas a uma referência (eixo, plano) | |

## 3. Visualização, anotação e organização

| Alias | Comando (guia) | Ação no app | Onde | RF |
|---|---|---|---|---|
| `Z` / `ZE` / `ZA` | ZOOM / EXTENTS / Zoom All | zoom por janela, enquadrar tudo | cena | RF13 |
| `P` | PAN | mover a vista | cena | RF13 |
| `RE` | REGEN | reconstruir a cena a partir do modelo (descarta cache) | cena | RF13 |
| `HH` / `HI` | Hide / Isolate Element | ocultar / isolar temporariamente | cena (por sessão) | RF13 |
| `EU` / `VU` | Unhide Element / Category | reexibir elemento / categoria | cena | RF13 |
| `VG` | Visibility/Graphics | painel de camadas e estilos (Tabela H) | cena | RF13 |
| `LA` | LAYER | gerenciador de camadas (`arquitetura`, `estrutura`, `fundacao`, `desenho`, `anotacao`, `ia-rascunho`) | cena | RF13 |
| `MT` / `T` / `TX` | MTEXT / Text | caixa de texto na prancha | prancha | RF14 |
| `D` | DIMSTYLE | estilo de cota (ABNT NBR 10126) | prancha | RF14 |
| `DLI` | DIMLINEAR | cota linear horizontal/vertical | prancha | RF14 |
| `DAL` / `DI` | DIMALIGNED / Aligned Dimension | cota alinhada | prancha | RF14 |
| `DCO` | DIMCONTINUE | cota contínua | prancha | RF14 |
| `EL`³ | Spot Elevation | cota de nível pontual (`+3,16`) | prancha | RF14 |
| `DL` | Detail Line | linha de detalhamento 2D sobre a vista | prancha | RF14 |
| `TG` | Tag by Category | etiqueta por categoria (P1, V12, L3) | prancha | RF14 |
| `MA` | MATCHPROP | copia propriedades visuais/camada | cena | RF13 |
| `B` / `I` | BLOCK / INSERT | define / insere bloco (símbolos, carimbo) | prancha | RF14 |
| `PR` / `CH` | PROPERTIES | painel de propriedades (formulário H5) | UI | RF01 |
| `UN` | Project Units | unidades do projeto (SI: m, kN, MPa) | configuração | RF28 |
| `KS` | Keyboard Shortcuts | editor de atalhos (perfil e aliases) | configuração | RF28 |
| `F2` | Text Screen | histórico de comandos | UI | RF28 |

³ No `PERFIL_CIVILCONSTRUCTOR`, `EL` = cota de nível (Revit); ELLIPSE usa `ELI`.

## 4. Modos do cursor (teclas de função) e variáveis de sistema

O navegador reserva **F1, F5, F11 e F12**; o motor usa o alias alternativo para eles e documenta na paleta.

| Tecla (guia) | Modo | Alias web | `ConfiguracaoMotor` | Efeito |
|---|---|---|---|---|
| F1 | Help | `?` ou `Shift+F1` | — | ajuda do comando ativo |
| F2 | Text Screen | F2 | — | histórico de comandos |
| F3 | Osnap | F3 | `osnap.ativo` | atração a pontos notáveis 2D |
| F4 | 3D Osnap | F4 | `osnap3d.ativo` | atração a vértices/faces no 3D |
| F5 | Isoplane | `Ctrl+Alt+5` | `vista.plano` | alterna plano de trabalho (Topo/Frente/Lateral) |
| F6 | Dynamic UCS | F6 | `ucs.dinamico` | plano de trabalho segue a face apontada |
| F7 | Grid | F7 | `grade.visivel` | mostra grade de fundo |
| F8 | Ortho | F8 | `orto.ativo` | movimento restrito a 90° |
| F9 | Snap | F9 | `snap.grade` | cursor preso ao passo da grade |
| F10 | Polar | F10 | `polar.ativo`, `polar.incremento` | rastreamento angular |
| F11 | Otrack | `Ctrl+Alt+1` | `otrack.ativo` | rastreamento a partir de pontos de snap |
| F12 | Dynamic Input | `Ctrl+Alt+2` | `entrada.dinamica` | prompt e campos junto ao cursor |

Modos de Osnap (F3): `EXT` extremidade, `MED` ponto médio, `CEN` centro, `INT` interseção, `PER` perpendicular,
`TAN` tangente, `NOD` ponto/nó, `QUA` quadrante, `PRX` mais próximo, e os próprios do BIM: `EIX` interseção de
eixos (prioridade máxima, RF03) e `NIV` nível de pavimento no 3D (RF17).

| Variável (Civil 3D) | `ConfiguracaoMotor` | Significado no app |
|---|---|---|
| `PICKFIRST = 1` | `selecao.previa` | seleção substantivo-verbo: selecionar antes de chamar o comando |
| `PLINEGEN = 1` | `polilinha.tracoContinuo` | padrão de linha tracejada contínuo ao longo dos vértices |
| `FILEDIA = 1` | `dialogos.arquivo` | abre diálogo do sistema para importar/exportar (IFC, DXF) |
| `IMAGEFRAME (0,1,2)` | `imagem.quadro` | moldura de imagens de referência (levantamento, foto do terreno) |

## 5. Civil 3D → terreno e perímetro da obra

| Comando (guia) | Função VI | Uso | RF |
|---|---|---|---|
| polilinha 3D + `CONVERT3DTO2DPOLY` | `VI(PROJETAR_2D, <polilinha3d>)` | projeta curva de nível/perímetro com cotas para o plano | RF22 |
| `DELETEFEATUREPI` | `VI(REMOVER_VERTICE, <linha>, <vertice>)` | remove vértice sem quebrar a linha de recurso | RF22 |
| `BREAKLINE` / `BRALINE` | `VI(LINHA_QUEBRA, <p1>, <p2>)` | símbolo de quebra em pranchas | RF14 |
| `OFFSET MULTIPLE` | `VI(DESLOCAR, ..., repetir=true)` | deslocamentos sucessivos | RF01 |
| `EXTRACTOBJECTS` | `VI(EXTRAIR_CURVAS, <superficie>)` | curvas de nível e bordas da superfície do terreno | RF22, RF09 |
| `FIND` | busca e substitui em anotações | textos e etiquetas da prancha | RF14 |

A superfície do terreno (`Superficie`) nasce de pontos (`PO`), curvas de nível (`SPL`) e polilinhas 3D; o
perímetro da obra (PostGIS, RF22) é a projeção 2D fechada; os furos de sondagem (RF09) são pontos rotulados
sobre a superfície, com a cota da boca lida da superfície.

## 6. O que **não** entra pelo motor

- Comandos que não mudam modelo nem vista (ex.: `FILEDIA`) viram configuração, não `ComandoVI`.
- Nada do catálogo chama a IA: o motor é determinístico. A IA só entra pelo console de texto natural (RF02),
  que produz as **mesmas** funções VI deste catálogo.
