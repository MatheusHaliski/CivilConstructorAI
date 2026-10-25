# Auditoria de IHC/UX — AUD-CC-3D-001

**Cenários construtivos, modelagem arquitetônica, mobiliário, instalações e renderização 3D** · rodada de 2026-10-25

Escopo pedido: verificar se o CivilConstructor oferece um ambiente arquitetônico/BIM integrado — editar a planta,
inserir móveis, equipamentos e pessoas em 3D, ligá-los às instalações e ver o edifício em vários níveis de
detalhe e realismo — tendo AutoCAD, Revit e SketchUp como referência de uso, e se o resultado é um **modelo BIM
interoperável** (objetos, propriedades e relações) e não só uma cena 3D.

Nada foi aprovado pela existência de código, endpoint ou botão: cada item abaixo foi executado contra a instância
real e conferido no servidor (banco) e, quando havia saída de arquivo, em validador externo.

## 1. Método

| Camada | Como foi testada |
|---|---|
| Frontend | `next build` de produção servido por `next start`; Chromium (Playwright) com WebGL por software (SwiftShader). Jornada `scripts/auditoria/j6-interface.mjs`: cada clique é conferido no servidor pela mesma sessão do navegador. |
| Backend | Spring Boot (perfil dev) com migrações Flyway até a V24. Jornada `scripts/auditoria/j6-diagnostico.mjs` pela API (mesmos comandos que a interface envia). |
| Banco de dados | PostgreSQL/PostGIS 17 (container); reabertura em **outra sessão** compara elemento a elemento (tipo, camada, propriedades). IT `CoordenacaoMateriaisIT` com Testcontainers. |
| Motor de renderização | Three.js r186 via React Three Fiber; 10 modos capturados; quadros por segundo medidos (só como referência relativa — sem GPU). |
| Motores de regra | TS (`packages/notacao-vi`, prévia no navegador) e Java (`ModeloBim`, gravação), com a fixture de paridade `comandos-paridade.json` (45 casos idênticos nos dois). |
| IFC | Exportação real (fila → Garage S3 → download) validada com **ifcopenshell 0.9.0** (`python -m ifcopenshell.validate`, esquema IFC4X3_ADD2) e relida para conferir relações. |

Evidências: `docs/auditoria/evidencias/j6-antes.json` (diagnóstico antes das correções), `j6-depois.json`,
`j6-interface.json`, `arquivos/j6-antes.ifc` (vazio: exportação bloqueada), `arquivos/j6-depois.ifc` e as telas em
`evidencias/telas/aud3d/`.

## 2. Resultado

| Jornada | Antes | Depois |
|---|---|---|
| API (CT-01…CT-10, AUD-01…08, RF15) | **6 de 16** | **34 de 34** |
| Interface (navegador real) | — (não havia biblioteca, conexão nem modos) | **12 de 13** — o 13º é a medição de quadros por segundo, registrada como "não verificado" (sem GPU) |
| IFC exportado | 0 paredes, 0 portas (exportação recusada) | 11 paredes, 4 portas, 2 janelas, 5 espaços, 29 objetos, 36 portas de conexão, 24 `IfcRelConnectsPorts`; **0 erros de esquema** |
| Testes automatizados | — | TS 66 + web 40; Java domínio 109, aplicação 55, web 2, integração 52 — todos passando |
| Regressão das jornadas anteriores | — | j1 50, j2 40, j3 70, j4 22, j5 25 — 0 falhas (j5 ajustada a dois textos que mudaram de propósito) |

**Fluxo da diretriz** (planta → 3D → móveis/pessoas → conectar → validar → renderizar → salvar/exportar → reabrir):
**executado de ponta a ponta sem perda de dados**, pela API e pela interface. Isso não equivale a "sem falhas": a
seção 8 lista o que continua parcial ou não implementado.

## 3. Defeitos encontrados (causa-raiz, classe, correção)

Classes: **I** interface · **F** funcionalidade ausente · **G** erro geométrico · **B** inconsistência de banco ·
**N** integração entre partes.

| # | Defeito | Classe | Sev. | Causa-raiz | Correção | Reteste |
|---|---|---|---|---|---|---|
| F1 | Divisória de 12 cm impossível | F | média | catálogo só tinha bloco de 14 cm; a regra espessura × bloco recusava | bloco cerâmico de vedação de 9 cm (`MAT.ALVENARIA.CERAMICO_VEDACAO_9`, ABNT NBR 15270-1 como referência de produto; sem código SINAPI cadastrado — dito no catálogo) | j6 `divisoria-12cm` |
| F2 | Mover parede 60 cm deixava a parede ligada para trás (folga de 0,60 m) | G | **alta** | `MOVER` só deslocava a parede selecionada | propagação nos dois motores: parede não paralela com ponta sobre a parede movida vai à nova interseção; porta mantém a distância absoluta; objeto hospedado acompanha | j6 `parede-ligada`, paridade, `objetos.test.ts` |
| F3 | Cota não acompanhava | G | média | cota sem associação | pontos da cota sobre a linha da parede movem com ela | j6 `cota` |
| F4 | Não havia objetos, conexões nem pessoas (VI-001 422) | F | **crítica** | modelo só tinha elementos construtivos | elementos `OBJETO` e `CONEXAO`, biblioteca compartilhada (`catalogo/objetos.json`, 44 itens, 9 categorias), comandos `OBJETO`/`CONECTAR` nos dois motores, camadas mobiliário/elétrica/hidrossanitária/humanos (migração V24) | CT-03/04/05/09 |
| F5 | Rota inexistente da API respondia 500 | I | baixa | `NoResourceFoundException` caía no tratador genérico | 404 `rota_inexistente`; 405 para método não aceito | j6 `rota-inexistente`, IT |
| F6 | Exportação IFC recusada; arquivo vazio | N | **alta** | consequência de F2 (envoltória aberta) | F2 | RF15 `exportar` |
| F7 | IFC sem mobiliário, sem portas de conexão | F | alta | escritor só conhecia elementos construtivos | objetos com a entidade do catálogo, `IfcDistributionPort` por conector, `IfcRelNests`, `IfcRelConnectsPorts` por conexão, circuitos e sistemas como `IfcDistributionCircuit`/`IfcDistributionSystem`, `IfcSpace` por ambiente | RF15 `ifc-objetos` |
| F8 | Todo `IfcShapeRepresentation` sem contexto | N | alta | atributo obrigatório `ContextOfItems` gravado como `$` (pré-existente) | referência ao contexto 'Model' | ifcopenshell: 0 erros |
| F9 | Acentos corrompidos no IFC ("Térreo" → "Trreo") | N | média | texto UTF-8 cru, sem o escape `\X2\…\X0\` da ISO 10303-21 (pré-existente) | codificação STEP de literais | `EscritorIfcTest`, leitura com ifcopenshell |
| F10 | Porta IFC com várias ligações (inválido) | N | média | IFC permite uma ligação por porta; quadro, entrada de água e caixa de inspeção têm várias | uma porta por ligação (`S1/1`, `S1/2`…) | ifcopenshell: 0 erros |
| F11 | Verificação de conflitos quebrava com 409 | B | **alta** | viga sem apoio nas duas pontas gerava dois LIG-01 com a mesma chave; a gravação violava a unicidade (pré-existente) | chave inclui a ponta; deduplicação por chave antes de gravar | `DetectorConflitosTest`, CT-07 |
| F12 | Chuveiro de 220 V calculado como 127 V (falso "não atendida") | N | alta | verificador de redes usava uma tensão única para todos os circuitos | tensão do circuito = a declarada pelas cargas; tensões diferentes no mesmo circuito → `MEP-ELE-08` | `RedeDoModeloTest` |
| F13 | Aparelho ligado em tomada "saía" do circuito | N | média | cabo tomada → aparelho recebia o circuito padrão C1 | herda o circuito que alimenta a tomada (dois motores; prompt aceita Enter) | paridade, j6 `ui-conectar` (C5) |
| F14 | Cabo do aparelho desenhado subindo ao forro (5 m para 0,4 m) | G | média | todas as ligações elétricas usavam o percurso de eletroduto | tomada → aparelho é linha direta (cabo do aparelho); quadro → tomada segue o percurso de forro | `objetos.test.ts` |
| F15 | Em 1ª pessoa, W/A/S/D iam para a linha de comando | I | média | a linha de comando capturava qualquer tecla | áreas com `data-teclado-proprio` ficam com o teclado | j6 `ui-colisao` |
| F16 | "Ver no modelo" de um conflito selecionava objetos invisíveis (dentro da casa fechada) | I | média | seleção sem enquadramento nem transparência | link de conflito abre o 3D em Raio X enquadrando os elementos; painel lista os selecionados | j6 `ui-destacar`, tela 18 |
| F17 | Painel de falhas dizia "não há modelo de instalações" | I | baixa | texto desatualizado | texto revisto e cartão "Instalações prediais" com a verificação de redes | tela 17 |
| F18 | Link "Minha conta" do cabeçalho com nome acessível diferente do texto visível (WCAG 2.5.3, em todas as telas) | I | média | `aria-label` "Minha conta: …" sobre o texto "DI Davi Interiores" (pré-existente) | nome vem do texto; iniciais decorativas | axe na j6-interface: 0 violações graves |

Também apareceram, no cenário de teste, conflitos **verdadeiros** que o detector acusou corretamente e que eram
erro do cenário (não do produto): cama de solteiro dentro do giro da porta do quarto 2, geladeira de 0,90 m
encostando na tomada, plafon sob a viga e todos os percursos de forro cruzando a viga de 10 m. Ficaram registrados em
`j6-depois.json` → CT-07.

## 4. Matriz de verificação

Estados: aprovado · aprovado parcialmente · reprovado · não implementado · bloqueado · não verificado.

| Área / requisito | Estado | Evidência | Impacto do que falta | Prioridade | Recomendação | Reteste |
|---|---|---|---|---|---|---|
| **RF01** planta 2D (AUD-01) | aprovado parcialmente | CT-01; mover parede 60 cm atualiza parede ligada, porta, cota e objeto hospedado (API e paridade); divisória de 12 cm; telas 01, 03 | cota só se associa a pontos sobre a linha da parede; corte e fachada são vistas 3D, não desenhos 2D | média | cota associada a referência (face/eixo) e geração de fachada/corte 2D a partir do 3D (RF14) | j6 + paridade |
| **RF13** 3D e renderização (AUD-06/07) | aprovado parcialmente | 10 modos com descrição (tela 06-01…10); sol por hora/data/latitude (07-08h/12h/17h); luminárias com fluxo e temperatura do catálogo (08); exposição; sombras do sol; cortes (10, 11); fachadas e topo (09) | luminárias não projetam sombra (a luz atravessa paredes); renderização em tempo real, não traçado de raios; intensidade e temperatura editáveis só no catálogo | média | sombra para até N luminárias próximas; edição de luz por instância | j6-interface |
| **RF15** IFC (BIM interoperável) | aprovado parcialmente | exportação real validada sem erros; relações porta↔objeto, porta↔porta, circuito, sistema, espaço; Pset com item, medidas, potência, fabricante "genérico" | corpo dos objetos é a caixa envolvente; importação IFC só conta entidades (não traz objetos para o modelo) | média | geometria detalhada (BRep/malha) e importação de objetos | j6 RF15, `EscritorIfcTest` |
| **RF16** dupla validação (auditor + coordenação) | aprovado | laço 1 recusa: deformar equipamento/pessoa, tensão, corrente, sistema, sentido, conector ocupado, objeto de parede sem parede; laço 2 avisa percurso recalculado; coordenação: OBJ-01…04, MEP-01…03, HUM-01 com explicação e correção | — | — | — | j6, `DetectorConflitosTest` |
| **AUD-02** biblioteca e objetos | aprovado parcialmente | busca por nome/categoria/palavra/medida ("geladeira 70" → 1 item); prévia planta+elevação; inserção pela interface gravada; parede (TV a 1,00 m na face), teto (pendente a 2,28 m), piso; equipamento não deforma; 2D e 3D com a mesma posição, medidas e identificação | fabricante só "genérico" (dimensões típicas adotadas); objeto "embutido" (spot) não recorta o forro/laje | baixa | biblioteca por fabricante com fonte; hospedeiro com recorte | j6, telas 02–04 |
| **AUD-03** elétrica | aprovado parcialmente | quadro → tomada → aparelho; lógica (circuito, disjuntor) × física (conectores, condutores, seção) × gráfica (percurso derivado) separadas na tela; recusa chuveiro 5500 W/220 V em tomada 10 A/127 V com motivo; mover geladeira mantém a ligação; verificador: Ib ≤ In atendida por circuito com a tensão das cargas | capacidade de condução (Iz) e queda de tensão dependem de dados do projetista e ficam "Dados insuficientes"; percurso é esquemático | média | informar Iz e limite de queda com fonte | j6, tela 05, `RedeDoModeloTest` |
| **AUD-04** hidrossanitária | aprovado | água fria, quente (via aquecedor), esgoto com declividade (2 %, 1 %) e ventilação; lavatório, chuveiro, máquina, geladeira com dispenser; sofá recusado ("não há par de conectores compatível"); água fria na entrada de água quente recusada | perdas de carga e pressões: "Não verificado" (NBR 5626 não consultada) | média | — | j6 CT-05 |
| **AUD-05** compatibilização | aprovado | os cinco casos do enunciado detectados com objetos, regra, explicação, evidência numérica e correção; nenhuma mensagem propõe furo; "Ver no modelo" destaca no 3D | percursos esquemáticos (não traçado de obra); sem correção automática | baixa | — | j6 `cenario`, tela 18 |
| **AUD-06** modos de exibição | aprovado | dez modos; malha/nós/pontos dizem que não são MEF nem nuvem de pontos | FPS só medido sem GPU | baixa | medir em GPU real | j6-interface |
| **AUD-07** iluminação | aprovado parcialmente | sol pela hora; luminária desligada só apaga a luz (conexão e carga continuam no circuito — conferido no servidor) | ver RF13 | média | — | j6 `luminaria-desligada` |
| **AUD-08** pessoas | aprovado parcialmente | altura 1,40–2,10 m com escala uniforme; deformar recusado; camada humanos; ocultas nos modos técnicos (opcional); 1ª pessoa com colisão em parede e objeto, passando por vãos de porta; fora de quantitativos, orçamento, cargas e IFC | **escadas e rampas não existem no modelo** (pessoa anda só no piso do próprio pavimento); a posição da caminhada não é gravada | média | elementos escada/rampa; botão "gravar posição" | j6 CT-09, telas 12–13 |
| Persistência e reabertura (CT-10) | aprovado | outra sessão: 80+ elementos idênticos (tipo, camada, propriedades), 31 objetos, 25 conexões; recarregar a página após edição pela interface não perde nada | — | — | — | j6, j6-interface |

## 5. Casos de teste CT-01 … CT-10

| CT | O que foi feito | Resultado |
|---|---|---|
| CT-01 | sala, cozinha, banheiro, dois quartos: 11 paredes, 4 portas, 2 janelas, 5 ambientes, cota | aprovado |
| CT-02 | ampliar a cozinha deslocando a parede 60 cm | aprovado (parede ligada, porta, cota, objeto de parede; parede paralela não arrastada) |
| CT-03 | 15 móveis/equipamentos + 3 luminárias + decoração; TV de parede; geladeira indeformável; mesa redimensionável | aprovado |
| CT-04 | 12 ligações elétricas + 1 pela interface; incompatíveis recusadas | aprovado |
| CT-05 | 13 ligações de água fria/quente, esgoto e ventilação | aprovado |
| CT-06 | tubulação × viga, eletroduto × tubulação, porta × geladeira, luminária × laje, equipamento em outro ambiente | aprovado (cinco detectados) |
| CT-07 | verificação de conflitos com estados e reverificação | aprovado (após F11) |
| CT-08 | dez modos, sol, noite, fachadas, cortes, isolamento por disciplina | aprovado (telas 06–15) |
| CT-09 | adulto 1,60 m e criança; deformar e altura fora da faixa recusados; quantitativos sem pessoas | aprovado |
| CT-10 | reabrir em outra sessão; recarregar a página; exportar IFC | aprovado |

## 6. Critérios de IHC

| # | Critério | Avaliação | Evidência |
|---|---|---|---|
| 1 | Visibilidade do estado | atende | barra mostra modo, versão e seleção; noite mostra "n de m luminárias ligadas"; sol mostra elevação e azimute; conexão mostra "livre/ligado a…" por conector |
| 2 | Reconhecimento em vez de memorização | atende | biblioteca com prévia; "Conectar E1 a…" a partir do conector; modos com descrição sempre visível |
| 3 | Consistência | atende | mesma regra no navegador e no servidor (paridade 45/45); cores de sistema iguais na planta e no 3D; vocabulário do Revit/AutoCAD (`CM`, `CN`) |
| 4 | Prevenção de erros | atende | equipamento não deforma; tomada recusa chuveiro; conector ocupado; objeto de parede sem parede |
| 5 | Reversibilidade | atende | todo comando desfaz no servidor (comando inverso); apagar objeto apaga suas conexões e avisa |
| 6 | Feedback | atende | aviso de percurso recalculado; mensagens de recusa com o motivo e a correção; colisão em 1ª pessoa |
| 7 | Controle do usuário | parcial | câmera, cortes, exposição, hora; falta editar luz por instância e gravar posição da caminhada |
| 8 | Aprendizado | parcial | descrições e prompts; não há tutorial guiado do fluxo de instalações |
| 9 | Acessibilidade | atende (no que foi medido) | axe (WCAG 2.1 A/AA) na tela do modelo: 0 violações graves ou críticas após F18; conectores distinguidos por cor **e** forma (quadrado = saída, círculo = entrada); 1ª pessoa por teclado; cena 3D com descrição textual. Leitor de tela e contraste dentro do canvas não foram avaliados |
| 10 | Desempenho | não verificado | sem GPU no ambiente; com WebGL por software: ~9 qps realista, ~26 qps sólido, ~16 qps malha (só comparação entre modos) |

## 7. Visibilidade por disciplina

| Disciplina | Planta | 3D | Isolar | IFC |
|---|---|---|---|---|
| Arquitetura | sim | sim (paredes recortadas pelos vãos, folha, vidro) | sim | `IfcWall`, `IfcDoor`, `IfcWindow`, `IfcSpace` |
| Estrutura | sim | sim | sim | `IfcColumn`, `IfcBeam`, `IfcSlab`, `IfcFooting` |
| Elétrica | símbolo + conectores + circuito no percurso | cabos/eletrodutos amarelos | sim (tela 14) | aparelhos, tomadas, quadro, portas, circuitos |
| Hidrossanitária | percursos tracejados por sistema | tubos azul/vermelho/marrom/cinza | sim (tela 15) | louças, medidor, aquecedor, caixa, sistemas |
| Mobiliário | contorno real com frente marcada | formas procedurais por tipo | sim | `IfcFurniture` |
| Humanos | círculo + nome | figura proporcional à altura; oculta nos modos técnicos | sim | não exportado (não é elemento da edificação) |

## 8. Pendências abertas (não aprovadas)

1. **Acessibilidade dentro do canvas** (2D e 3D): avaliada só pela descrição textual e pelo teclado; leitor de tela
   e contraste das cenas não foram medidos.
2. **Escadas e rampas** não modeladas: AUD-08 "pisos/escadas/rampas" não implementado.
3. **Luminárias sem sombra** no 3D: a luz artificial atravessa paredes (efeito visual).
4. **Cota associativa** só a pontos sobre a linha da parede movida.
5. **Fachada e corte** como desenhos 2D a partir do 3D (hoje só como vista da câmera).
6. **Objetos embutidos** não recortam forro/laje; **fabricante** só "genérico".
7. **IFC**: corpo dos objetos pela caixa envolvente; importação não traz objetos.
8. **Instalações**: percurso esquemático (não traçado de obra); Iz, queda de tensão, perdas de carga e pressões
   ficam "Dados insuficientes"/"Não verificado" até o projetista informar dados com fonte.
9. **Desempenho** a medir em máquina com GPU.

## 9. Como reproduzir

```bash
# stack: postgis, valkey, garage, receptor SMTP, backend (dev) e web (next start) — ver scripts/auditoria/README.md
CCA_FASE=depois CCA_SESSAO=/tmp/sessao-j6.json node scripts/auditoria/j6-diagnostico.mjs
CCA_SESSAO=/tmp/sessao-j6.json node scripts/auditoria/j6-interface.mjs   # 31 s depois (anti-replay do TOTP)
python -m ifcopenshell.validate docs/auditoria/evidencias/arquivos/j6-depois.ifc
```
