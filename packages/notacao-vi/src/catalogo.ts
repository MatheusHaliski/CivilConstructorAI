/**
 * Catálogo do motor de desenho (RF28) e Tabela B (funções da Notação VI).
 * Fonte: docs/CATALOGO_COMANDOS.md. Cada comando tem aliases por perfil, categoria e roteiro de prompts.
 */

export type CategoriaComando = "DESENHO" | "MODIFICACAO" | "VISUALIZACAO" | "ANOTACAO" | "MODO" | "CONFIG";
export type PerfilAtalhos = "CIVILCONSTRUCTOR" | "AUTOCAD" | "REVIT";
export type TipoEntrada = "PONTO" | "VALOR" | "TEXTO" | "SELECAO" | "OPCAO" | "ANGULO";

export type PassoPrompt = {
  nome: string;
  mensagem: string;
  tipo: TipoEntrada;
  /** valor padrão (Enter aceita) */
  padrao?: unknown;
  opcional?: boolean;
  /** opções entre colchetes: [Desfazer/Fechar] */
  opcoes?: string[];
  /** repete este passo até [Fechar]/[Sair] (polilinha, cópia múltipla) */
  repetir?: boolean;
};

export type Comando = {
  /** código da função VI que o comando produz (DESENHO/MODIFICACAO) ou ação do motor */
  codigo: string;
  nomePtBr: string;
  nomeEn: string;
  categoria: CategoriaComando;
  aliases: Partial<Record<PerfilAtalhos, string[]>>;
  roteiro: PassoPrompt[];
  papeis?: ("ARQUITETO" | "ENGENHEIRO")[];
  rf: string[];
  /** comandos que não existem no guia e foram acrescentados no padrão do Revit */
  extensao?: boolean;
  descricao: string;
};

const todos = (a: string[]): Comando["aliases"] => ({ CIVILCONSTRUCTOR: a, AUTOCAD: a, REVIT: a });
const P = (nome: string, mensagem: string, extra: Partial<PassoPrompt> = {}): PassoPrompt => ({ nome, mensagem, tipo: "PONTO", opcoes: ["Desfazer"], ...extra });
const V = (nome: string, mensagem: string, padrao?: unknown, extra: Partial<PassoPrompt> = {}): PassoPrompt => ({ nome, mensagem, tipo: "VALOR", padrao, ...extra });
const S = (mensagem = "Selecione objetos:"): PassoPrompt => ({ nome: "sel", mensagem, tipo: "SELECAO" });

export const CATALOGO: Comando[] = [
  // ---------- Desenho · AutoCAD
  { codigo: "LINHA", nomePtBr: "LINHA", nomeEn: "LINE", categoria: "DESENHO", aliases: todos(["L"]), rf: ["RF01", "RF14"],
    descricao: "Linha auxiliar ou de detalhe 2D (camada desenho); não é elemento construtivo.",
    roteiro: [P("p1", "Ponto inicial:"), P("p2", "Próximo ponto ou [Desfazer/Fechar]:", { repetir: true, opcoes: ["Desfazer", "Fechar"] })] },
  { codigo: "POLILINHA", nomePtBr: "POLILINHA", nomeEn: "PLINE", categoria: "DESENHO", aliases: todos(["PL"]), rf: ["RF01", "RF04"],
    descricao: "Polilinha; fechada vira contorno de laje ou perímetro.",
    roteiro: [P("p1", "Ponto inicial:"), P("pn", "Próximo ponto ou [Desfazer/Fechar]:", { repetir: true, opcoes: ["Desfazer", "Fechar"] })] },
  { codigo: "CIRCULO", nomePtBr: "CÍRCULO", nomeEn: "CIRCLE", categoria: "DESENHO", aliases: todos(["C"]), rf: ["RF01"],
    descricao: "Círculo auxiliar; como seção de pilar → PILAR circular.",
    roteiro: [P("centro", "Centro:"), V("raio", "Raio [1.0]:", 1.0)] },
  { codigo: "RETANGULO", nomePtBr: "RETÂNGULO", nomeEn: "RECTANG", categoria: "DESENHO", aliases: todos(["REC"]), rf: ["RF01"],
    descricao: "Retângulo por dois pontos diagonais.", roteiro: [P("p1", "Primeiro canto:"), P("p2", "Canto oposto:")] },
  { codigo: "ARCO", nomePtBr: "ARCO", nomeEn: "ARC", categoria: "DESENHO", aliases: todos(["A"]), rf: ["RF01"],
    descricao: "Arco por 3 pontos.", roteiro: [P("p1", "Ponto inicial:"), P("p2", "Segundo ponto:"), P("p3", "Ponto final:")] },
  { codigo: "POLIGONO", nomePtBr: "POLÍGONO", nomeEn: "POLYGON", categoria: "DESENHO", aliases: todos(["POL"]), rf: ["RF01"],
    descricao: "Polígono regular inscrito.", roteiro: [V("n", "Número de lados [6]:", 6), P("centro", "Centro:"), V("raio", "Raio [1.0]:", 1.0)] },
  { codigo: "ELIPSE", nomePtBr: "ELIPSE", nomeEn: "ELLIPSE", categoria: "DESENHO", aliases: { CIVILCONSTRUCTOR: ["ELI"], AUTOCAD: ["EL"], REVIT: ["ELI"] }, rf: ["RF01"],
    descricao: "Elipse auxiliar.", roteiro: [P("centro", "Centro:"), V("a", "Semieixo maior [2.0]:", 2.0), V("b", "Semieixo menor [1.0]:", 1.0)] },
  { codigo: "HACHURA", nomePtBr: "HACHURA", nomeEn: "HATCH", categoria: "ANOTACAO", aliases: todos(["H"]), rf: ["RF14"],
    descricao: "Hachura de corte/material (padrão ABNT por material).", roteiro: [S("Selecione o contorno:"), V("padrao", "Padrão [CONCRETO]:", "CONCRETO")] },
  { codigo: "SPLINE", nomePtBr: "SPLINE", nomeEn: "SPLINE", categoria: "DESENHO", aliases: todos(["SPL"]), rf: ["RF22", "RF09"],
    descricao: "Curva suave: curvas de nível e topografia.", roteiro: [P("p1", "Ponto inicial:"), P("pn", "Próximo ponto ou [Fechar]:", { repetir: true, opcoes: ["Desfazer", "Fechar"] })] },
  { codigo: "PONTO", nomePtBr: "PONTO", nomeEn: "POINT", categoria: "DESENHO", aliases: todos(["PO"]), rf: ["RF09"],
    descricao: "Ponto de referência; com rótulo SP-nn vira furo de sondagem.", roteiro: [P("p", "Ponto:"), V("rotulo", "Rótulo (opcional):", "", { tipo: "TEXTO", opcional: true })] },
  { codigo: "RAIO_CONSTRUCAO", nomePtBr: "RAIO DE CONSTRUÇÃO", nomeEn: "RAY", categoria: "DESENHO", aliases: todos(["RAY"]), rf: ["RF01"],
    descricao: "Linha de construção semi-infinita (não imprime).", roteiro: [P("origem", "Origem:"), P("direcao", "Ponto na direção:")] },
  { codigo: "LINHA_CONSTRUCAO", nomePtBr: "LINHA DE CONSTRUÇÃO", nomeEn: "XLINE", categoria: "DESENHO", aliases: todos(["XL"]), rf: ["RF01", "RF03"],
    descricao: "Linha de construção infinita; sobre eixo vira EIXO.", roteiro: [P("p", "Ponto:"), P("direcao", "Ponto na direção:")] },
  { codigo: "ANEL", nomePtBr: "ANEL", nomeEn: "DONUT", categoria: "DESENHO", aliases: todos(["DO"]), rf: ["RF10"],
    descricao: "Anel (seção vazada).", roteiro: [P("centro", "Centro:"), V("ri", "Raio interno [0.2]:", 0.2), V("re", "Raio externo [0.3]:", 0.3)] },
  { codigo: "REGIAO", nomePtBr: "REGIÃO", nomeEn: "REGION", categoria: "DESENHO", aliases: todos(["REG"]), rf: ["RF04", "RF10", "RF11"],
    descricao: "Região 2D fechada: base para laje, radier e área.", roteiro: [S("Selecione o contorno fechado:")] },
  // ---------- Desenho · Revit → Tabela B
  { codigo: "PAREDE", nomePtBr: "PAREDE", nomeEn: "WALL", categoria: "DESENHO", aliases: { CIVILCONSTRUCTOR: ["WA", "ML"], AUTOCAD: ["ML"], REVIT: ["WA"] }, rf: ["RF01"],
    papeis: ["ARQUITETO", "ENGENHEIRO"], descricao: "Parede básica, composta ou cortina (tipo pelo material da Tabela A).",
    roteiro: [P("p1", "Ponto inicial da parede:"), P("p2", "Ponto final ou [Desfazer]:"), V("esp", "Espessura [0.15]:", 0.15), V("h", "Altura [2.80]:", 2.8), V("mat", "Material [MAT_ALV_BLOCO14]:", "MAT_ALV_BLOCO14", { tipo: "TEXTO" })] },
  { codigo: "PORTA", nomePtBr: "PORTA", nomeEn: "DOOR", categoria: "DESENHO", aliases: todos(["DR"]), rf: ["RF01", "RF16"],
    papeis: ["ARQUITETO", "ENGENHEIRO"], descricao: "Porta hospedada na parede (NewOpening por projeção vetorial).",
    roteiro: [P("pos", "Clique na parede hospedeira na posição da porta:"), V("larg", "Largura [0.80]:", 0.8), V("alt", "Altura [2.10]:", 2.1)] },
  { codigo: "JANELA", nomePtBr: "JANELA", nomeEn: "WINDOW", categoria: "DESENHO", aliases: todos(["WN"]), rf: ["RF01", "RF16"],
    papeis: ["ARQUITETO", "ENGENHEIRO"], descricao: "Janela hospedada na parede.",
    roteiro: [P("pos", "Clique na parede hospedeira na posição da janela:"), V("larg", "Largura [1.20]:", 1.2), V("alt", "Altura [1.20]:", 1.2), V("peitoril", "Peitoril [1.00]:", 1.0)] },
  { codigo: "AMBIENTE", nomePtBr: "AMBIENTE", nomeEn: "ROOM", categoria: "DESENHO", aliases: todos(["RM"]), rf: ["RF01", "RF11"],
    descricao: "Ambiente delimitado por paredes; calcula área e perímetro.", roteiro: [P("ponto", "Clique dentro do ambiente:"), V("nome", "Nome [Sala]:", "Sala", { tipo: "TEXTO" })] },
  { codigo: "EIXO", nomePtBr: "EIXO", nomeEn: "GRID", categoria: "DESENHO", aliases: todos(["GR"]), rf: ["RF03"],
    descricao: "Eixo estrutural por dois pontos.", roteiro: [P("p1", "Ponto inicial do eixo:"), P("p2", "Ponto final:"), V("nome", "Nome (vazio = automático):", "", { tipo: "TEXTO", opcional: true })] },
  { codigo: "MALHA", nomePtBr: "MALHA DE EIXOS", nomeEn: "GRID ARRAY", categoria: "DESENHO", aliases: todos(["MALHA"]), rf: ["RF03"], extensao: true,
    descricao: "Gera a grade inteira: nx × ny eixos com espaçamentos.",
    roteiro: [V("nx", "Eixos na direção X (letras) [5]:", 5), V("dx", "Espaçamento X [5.0]:", 5.0), V("ny", "Eixos na direção Y (números) [3]:", 3), V("dy", "Espaçamento Y [6.0]:", 6.0)] },
  { codigo: "PAVIMENTO", nomePtBr: "PAVIMENTO", nomeEn: "LEVEL", categoria: "DESENHO", aliases: todos(["LL"]), rf: ["RF03", "RF17"],
    descricao: "Nível/pavimento em Z; base da Disciplina Z.", roteiro: [V("nome", "Nome [Pavimento]:", "Pavimento", { tipo: "TEXTO" }), V("z", "Elevação em metros:", undefined)] },
  { codigo: "PLANO_REF", nomePtBr: "PLANO DE REFERÊNCIA", nomeEn: "REFERENCE PLANE", categoria: "DESENHO", aliases: todos(["RP"]), rf: ["RF01"],
    descricao: "Plano de referência não imprimível para alinhamento.", roteiro: [P("p1", "Ponto inicial:"), P("p2", "Ponto final:")] },
  { codigo: "VIGA", nomePtBr: "VIGA", nomeEn: "STRUCTURAL BEAM", categoria: "DESENHO", aliases: todos(["BM"]), rf: ["RF04"], papeis: ["ENGENHEIRO"],
    descricao: "Viga entre apoios.", roteiro: [P("p1", "Ponto inicial da viga:"), P("p2", "Ponto final ou [Desfazer]:"), V("b", "Largura b [0.12]:", 0.12), V("h", "Altura h [0.40]:", 0.4), V("mat", "Material [MAT_CONC_FCK30]:", "MAT_CONC_FCK30", { tipo: "TEXTO" })] },
  { codigo: "CONTRAVENTAMENTO", nomePtBr: "CONTRAVENTAMENTO", nomeEn: "STRUCTURAL BRACE", categoria: "DESENHO", aliases: { CIVILCONSTRUCTOR: ["BR"], REVIT: ["BR"], AUTOCAD: ["BRACE"] }, rf: ["RF04"], papeis: ["ENGENHEIRO"],
    descricao: "Contraventamento (aço).", roteiro: [P("p1", "Ponto inicial:"), P("p2", "Ponto final:"), V("perfil", "Perfil [L 50x5]:", "L 50x5", { tipo: "TEXTO" })] },
  { codigo: "PILAR", nomePtBr: "PILAR", nomeEn: "STRUCTURAL COLUMN", categoria: "DESENHO", aliases: todos(["CL"]), rf: ["RF04", "RF17"], papeis: ["ENGENHEIRO"], extensao: true,
    descricao: "Pilar; base e topo presos aos pavimentos (Disciplina Z).",
    roteiro: [P("centro", "Centro do pilar (snap EIX):"), V("b", "Largura b [0.19]:", 0.19), V("h", "Altura h [0.40]:", 0.4), V("mat", "Material [MAT_CONC_FCK30]:", "MAT_CONC_FCK30", { tipo: "TEXTO" })] },
  { codigo: "LAJE", nomePtBr: "LAJE", nomeEn: "STRUCTURAL FLOOR", categoria: "DESENHO", aliases: todos(["SB"]), rf: ["RF04"], papeis: ["ENGENHEIRO", "ARQUITETO"], extensao: true,
    descricao: "Laje por contorno fechado.", roteiro: [P("p1", "Primeiro vértice do contorno:"), P("pn", "Próximo vértice ou [Fechar]:", { repetir: true, opcoes: ["Desfazer", "Fechar"] }), V("h", "Espessura [0.12]:", 0.12)] },
  { codigo: "FUNDACAO", nomePtBr: "FUNDAÇÃO", nomeEn: "FOUNDATION", categoria: "DESENHO", aliases: todos(["FD"]), rf: ["RF10"], papeis: ["ENGENHEIRO"], extensao: true,
    descricao: "Sapata, bloco ou radier sob um pilar.", roteiro: [S("Selecione o pilar:"), V("tipo", "Tipo [SAPATA/BLOCO/RADIER]:", "SAPATA", { tipo: "OPCAO", opcoes: ["SAPATA", "BLOCO", "RADIER"] })] },
  // ---------- Modificação
  { codigo: "MOVER", nomePtBr: "MOVER", nomeEn: "MOVE", categoria: "MODIFICACAO", aliases: { CIVILCONSTRUCTOR: ["M", "MV"], AUTOCAD: ["M"], REVIT: ["MV"] }, rf: ["RF28"],
    descricao: "Move a seleção com precisão por Osnap.", roteiro: [S(), P("base", "Ponto base:"), P("destino", "Segundo ponto (destino):")] },
  { codigo: "COPIAR", nomePtBr: "COPIAR", nomeEn: "COPY", categoria: "MODIFICACAO", aliases: { CIVILCONSTRUCTOR: ["CO", "CP"], AUTOCAD: ["CO", "CP"], REVIT: ["CO"] }, rf: ["RF28"],
    descricao: "Cópia múltipla até [Sair].", roteiro: [S(), P("base", "Ponto base:"), P("destino", "Destino ou [Sair]:", { repetir: true, opcoes: ["Desfazer", "Sair"] })] },
  { codigo: "ROTACIONAR", nomePtBr: "ROTACIONAR", nomeEn: "ROTATE", categoria: "MODIFICACAO", aliases: todos(["RO"]), rf: ["RF28"],
    descricao: "Gira em torno do ponto base.", roteiro: [S(), P("base", "Ponto base:"), V("ang", "Ângulo em graus [90]:", 90, { tipo: "ANGULO" })] },
  { codigo: "APARAR", nomePtBr: "APARAR", nomeEn: "TRIM", categoria: "MODIFICACAO", aliases: todos(["TR"]), rf: ["RF28"],
    descricao: "Corta em interseções (linhas, polilinhas e paredes).", roteiro: [S("Selecione as arestas de corte:"), S("Selecione os objetos a aparar:")] },
  { codigo: "ESTENDER", nomePtBr: "ESTENDER", nomeEn: "EXTEND", categoria: "MODIFICACAO", aliases: todos(["EX"]), rf: ["RF28"],
    descricao: "Estende até a aresta.", roteiro: [S("Selecione as arestas de destino:"), S("Selecione os objetos a estender:")] },
  { codigo: "CONCORDAR", nomePtBr: "CONCORDAR", nomeEn: "FILLET", categoria: "MODIFICACAO", aliases: todos(["F"]), rf: ["RF28"],
    descricao: "Concordância; raio 0 fecha canto vivo entre paredes.", roteiro: [V("raio", "Raio [0]:", 0), S("Primeiro objeto:"), S("Segundo objeto:")] },
  { codigo: "CHANFRAR", nomePtBr: "CHANFRAR", nomeEn: "CHAMFER", categoria: "MODIFICACAO", aliases: todos(["CHA"]), rf: ["RF28"],
    descricao: "Chanfro.", roteiro: [V("d1", "Distância 1 [0.1]:", 0.1), V("d2", "Distância 2 [0.1]:", 0.1), S("Primeiro objeto:"), S("Segundo objeto:")] },
  { codigo: "DESLOCAR", nomePtBr: "DESLOCAR", nomeEn: "OFFSET", categoria: "MODIFICACAO", aliases: todos(["O"]), rf: ["RF01", "RF28"],
    descricao: "Cópia paralela (paredes, eixos, contornos); MULTIPLE repete.", roteiro: [V("dist", "Distância [1.0]:", 1.0), S("Selecione o objeto:"), P("lado", "Clique no lado do deslocamento ou [Sair]:", { repetir: true, opcoes: ["Sair"] })] },
  { codigo: "ESPELHAR", nomePtBr: "ESPELHAR", nomeEn: "MIRROR", categoria: "MODIFICACAO", aliases: todos(["MI"]), rf: ["RF28"],
    descricao: "Espelha em torno de uma linha.", roteiro: [S(), P("p1", "Primeiro ponto da linha de espelho:"), P("p2", "Segundo ponto:"), V("apagar", "Apagar originais? [N]:", false, { tipo: "OPCAO", opcoes: ["S", "N"] })] },
  { codigo: "ESTICAR", nomePtBr: "ESTICAR", nomeEn: "STRETCH", categoria: "MODIFICACAO", aliases: todos(["S"]), rf: ["RF28"],
    descricao: "Estica o que a janela cruza; pilar não estica, move.", roteiro: [S("Selecione por janela de cruzamento:"), P("base", "Ponto base:"), P("destino", "Segundo ponto:")] },
  { codigo: "ESCALAR", nomePtBr: "ESCALAR", nomeEn: "SCALE", categoria: "MODIFICACAO", aliases: todos(["SC"]), rf: ["RF28"],
    descricao: "Escala proporcional (só auxiliares).", roteiro: [S(), P("base", "Ponto base:"), V("fator", "Fator [2]:", 2)] },
  { codigo: "MATRIZ", nomePtBr: "MATRIZ", nomeEn: "ARRAY", categoria: "MODIFICACAO", aliases: todos(["AR"]), rf: ["RF28"],
    descricao: "Matriz retangular, polar ou em caminho.", roteiro: [S(), V("tipo", "Tipo [RETANGULAR/POLAR]:", "RETANGULAR", { tipo: "OPCAO", opcoes: ["RETANGULAR", "POLAR"] }), V("nx", "Colunas [3]:", 3), V("dx", "Passo X [5.0]:", 5.0), V("ny", "Linhas [1]:", 1), V("dy", "Passo Y [6.0]:", 6.0)] },
  { codigo: "APAGAR", nomePtBr: "APAGAR", nomeEn: "ERASE", categoria: "MODIFICACAO", aliases: { CIVILCONSTRUCTOR: ["E", "DE"], AUTOCAD: ["E"], REVIT: ["DE"] }, rf: ["RF28"],
    descricao: "Remove; gera comando inverso para desfazer.", roteiro: [S()] },
  { codigo: "EXPLODIR", nomePtBr: "EXPLODIR", nomeEn: "EXPLODE", categoria: "MODIFICACAO", aliases: todos(["X"]), rf: ["RF28"],
    descricao: "Polilinha → linhas; elemento construtivo não explode.", roteiro: [S()] },
  { codigo: "UNIR", nomePtBr: "UNIR", nomeEn: "JOIN", categoria: "MODIFICACAO", aliases: todos(["J"]), rf: ["RF28"],
    descricao: "Linhas/arcos → uma polilinha.", roteiro: [S()] },
  { codigo: "QUEBRAR", nomePtBr: "QUEBRAR", nomeEn: "BREAK", categoria: "MODIFICACAO", aliases: { CIVILCONSTRUCTOR: ["QB"], AUTOCAD: ["BR"], REVIT: ["QB"] }, rf: ["RF28"],
    descricao: "Quebra entre dois pontos.", roteiro: [S("Selecione o objeto:"), P("p1", "Primeiro ponto de quebra:"), P("p2", "Segundo ponto:")] },
  { codigo: "ALONGAR", nomePtBr: "ALONGAR", nomeEn: "LENGTHEN", categoria: "MODIFICACAO", aliases: todos(["LEN"]), rf: ["RF28"],
    descricao: "Muda comprimento (delta ou total).", roteiro: [S("Selecione o objeto:"), V("delta", "Incremento em metros [1.0]:", 1.0)] },
  { codigo: "ALINHAR", nomePtBr: "ALINHAR", nomeEn: "ALIGN", categoria: "MODIFICACAO", aliases: todos(["AL"]), rf: ["RF28"],
    descricao: "Alinha faces/bordas a uma referência (eixo, plano).", roteiro: [S("Selecione a referência:"), S("Selecione os objetos a alinhar:")] },
  // ---------- Visualização
  { codigo: "ZOOM", nomePtBr: "ZOOM", nomeEn: "ZOOM", categoria: "VISUALIZACAO", aliases: todos(["Z"]), rf: ["RF13"], descricao: "Zoom por janela.", roteiro: [P("p1", "Primeiro canto da janela:"), P("p2", "Canto oposto:")] },
  { codigo: "ZOOM_TUDO", nomePtBr: "ENQUADRAR TUDO", nomeEn: "ZOOM EXTENTS", categoria: "VISUALIZACAO", aliases: todos(["ZE", "ZA"]), rf: ["RF13"], descricao: "Enquadra todo o desenho.", roteiro: [] },
  { codigo: "PAN", nomePtBr: "MOVER VISTA", nomeEn: "PAN", categoria: "VISUALIZACAO", aliases: todos(["P"]), rf: ["RF13"], descricao: "Move a vista sem alterar a escala.", roteiro: [P("p1", "Ponto base:"), P("p2", "Segundo ponto:")] },
  { codigo: "REGENERAR", nomePtBr: "REGENERAR", nomeEn: "REGEN", categoria: "VISUALIZACAO", aliases: todos(["RE"]), rf: ["RF13"], descricao: "Reconstrói a cena a partir do modelo.", roteiro: [] },
  { codigo: "OCULTAR", nomePtBr: "OCULTAR ELEMENTO", nomeEn: "HIDE ELEMENT", categoria: "VISUALIZACAO", aliases: todos(["HH"]), rf: ["RF13"], descricao: "Oculta temporariamente.", roteiro: [S()] },
  { codigo: "ISOLAR", nomePtBr: "ISOLAR ELEMENTO", nomeEn: "ISOLATE ELEMENT", categoria: "VISUALIZACAO", aliases: todos(["HI"]), rf: ["RF13"], descricao: "Isola temporariamente.", roteiro: [S()] },
  { codigo: "REEXIBIR", nomePtBr: "REEXIBIR ELEMENTO", nomeEn: "UNHIDE ELEMENT", categoria: "VISUALIZACAO", aliases: todos(["EU"]), rf: ["RF13"], descricao: "Reexibe elementos ocultos.", roteiro: [] },
  { codigo: "REEXIBIR_CATEGORIA", nomePtBr: "REEXIBIR CATEGORIA", nomeEn: "UNHIDE CATEGORY", categoria: "VISUALIZACAO", aliases: todos(["VU"]), rf: ["RF13"], descricao: "Reexibe uma categoria inteira.", roteiro: [] },
  { codigo: "VISIBILIDADE", nomePtBr: "VISIBILIDADE E ESTILOS", nomeEn: "VISIBILITY/GRAPHICS", categoria: "VISUALIZACAO", aliases: todos(["VG"]), rf: ["RF13"], descricao: "Painel de camadas e estilos (Tabela H).", roteiro: [] },
  { codigo: "CAMADAS", nomePtBr: "CAMADAS", nomeEn: "LAYER", categoria: "VISUALIZACAO", aliases: todos(["LA"]), rf: ["RF13"], descricao: "Gerenciador de camadas.", roteiro: [] },
  { codigo: "COPIAR_PROPRIEDADES", nomePtBr: "COPIAR PROPRIEDADES", nomeEn: "MATCHPROP", categoria: "MODIFICACAO", aliases: todos(["MA"]), rf: ["RF13"], descricao: "Copia propriedades visuais/camada.", roteiro: [S("Selecione o objeto de origem:"), S("Selecione os objetos de destino:")] },
  { codigo: "PROPRIEDADES", nomePtBr: "PROPRIEDADES", nomeEn: "PROPERTIES", categoria: "VISUALIZACAO", aliases: todos(["PR", "CH"]), rf: ["RF01"], descricao: "Painel de propriedades (formulário H5).", roteiro: [] },
  // ---------- Anotação (prancha)
  { codigo: "TEXTO", nomePtBr: "TEXTO", nomeEn: "MTEXT", categoria: "ANOTACAO", aliases: { CIVILCONSTRUCTOR: ["TX", "MT", "T"], AUTOCAD: ["MT", "T"], REVIT: ["TX"] }, rf: ["RF14"], descricao: "Caixa de texto.", roteiro: [P("p", "Ponto de inserção:"), V("texto", "Texto:", "", { tipo: "TEXTO" })] },
  { codigo: "ESTILO_COTA", nomePtBr: "ESTILO DE COTA", nomeEn: "DIMSTYLE", categoria: "ANOTACAO", aliases: todos(["D"]), rf: ["RF14"], descricao: "Estilo de cota (NBR 10126).", roteiro: [] },
  { codigo: "COTA_LINEAR", nomePtBr: "COTA LINEAR", nomeEn: "DIMLINEAR", categoria: "ANOTACAO", aliases: todos(["DLI"]), rf: ["RF14"], descricao: "Cota horizontal/vertical.", roteiro: [P("p1", "Origem da primeira linha de chamada:"), P("p2", "Origem da segunda:"), P("pos", "Posição da linha de cota:")] },
  { codigo: "COTA_ALINHADA", nomePtBr: "COTA ALINHADA", nomeEn: "DIMALIGNED", categoria: "ANOTACAO", aliases: todos(["DAL", "DI"]), rf: ["RF14"], descricao: "Cota alinhada ao objeto.", roteiro: [P("p1", "Origem da primeira linha de chamada:"), P("p2", "Origem da segunda:"), P("pos", "Posição da linha de cota:")] },
  { codigo: "COTA_CONTINUA", nomePtBr: "COTA CONTÍNUA", nomeEn: "DIMCONTINUE", categoria: "ANOTACAO", aliases: todos(["DCO"]), rf: ["RF14"], descricao: "Continua a partir da última cota.", roteiro: [P("p", "Origem da próxima linha de chamada ou [Sair]:", { repetir: true, opcoes: ["Sair"] })] },
  { codigo: "COTA_NIVEL", nomePtBr: "COTA DE NÍVEL", nomeEn: "SPOT ELEVATION", categoria: "ANOTACAO", aliases: { CIVILCONSTRUCTOR: ["EL"], REVIT: ["EL"], AUTOCAD: ["NIVEL"] }, rf: ["RF14"], descricao: "Cota de nível pontual (+3,16).", roteiro: [P("p", "Ponto:")] },
  { codigo: "LINHA_DETALHE", nomePtBr: "LINHA DE DETALHE", nomeEn: "DETAIL LINE", categoria: "ANOTACAO", aliases: todos(["DL"]), rf: ["RF14"], descricao: "Linha 2D sobre a vista.", roteiro: [P("p1", "Ponto inicial:"), P("p2", "Ponto final:")] },
  { codigo: "ETIQUETA", nomePtBr: "ETIQUETA", nomeEn: "TAG BY CATEGORY", categoria: "ANOTACAO", aliases: todos(["TG"]), rf: ["RF14"], descricao: "Etiqueta por categoria (P1, V12, L3).", roteiro: [S("Selecione o elemento a etiquetar:")] },
  { codigo: "BLOCO", nomePtBr: "BLOCO", nomeEn: "BLOCK", categoria: "ANOTACAO", aliases: todos(["B"]), rf: ["RF14"], descricao: "Define um bloco (símbolos, carimbo).", roteiro: [S("Selecione os objetos do bloco:"), V("nome", "Nome do bloco:", "", { tipo: "TEXTO" })] },
  { codigo: "INSERIR", nomePtBr: "INSERIR BLOCO", nomeEn: "INSERT", categoria: "ANOTACAO", aliases: todos(["I"]), rf: ["RF14"], descricao: "Insere um bloco existente.", roteiro: [V("nome", "Nome do bloco:", "", { tipo: "TEXTO" }), P("p", "Ponto de inserção:")] },
  { codigo: "LINHA_QUEBRA", nomePtBr: "LINHA DE QUEBRA", nomeEn: "BREAKLINE", categoria: "ANOTACAO", aliases: todos(["BREAKLINE", "BRALINE"]), rf: ["RF14"], descricao: "Símbolo de quebra na prancha.", roteiro: [P("p1", "Ponto inicial:"), P("p2", "Ponto final:")] },
  { codigo: "LOCALIZAR", nomePtBr: "LOCALIZAR E SUBSTITUIR", nomeEn: "FIND", categoria: "ANOTACAO", aliases: todos(["FIND"]), rf: ["RF14"], descricao: "Busca e substitui em textos da prancha.", roteiro: [V("buscar", "Buscar:", "", { tipo: "TEXTO" }), V("substituir", "Substituir por:", "", { tipo: "TEXTO" })] },
  // ---------- Modos e configuração
  { codigo: "OSNAP", nomePtBr: "ATRAÇÃO A OBJETOS", nomeEn: "OSNAP (F3)", categoria: "MODO", aliases: todos(["F3", "OSNAP"]), rf: ["RF28"], descricao: "Liga/desliga atração a pontos notáveis.", roteiro: [] },
  { codigo: "OSNAP3D", nomePtBr: "ATRAÇÃO 3D", nomeEn: "3D OSNAP (F4)", categoria: "MODO", aliases: todos(["F4"]), rf: ["RF28"], descricao: "Atração a vértices/faces no 3D.", roteiro: [] },
  { codigo: "PLANO_TRABALHO", nomePtBr: "PLANO DE TRABALHO", nomeEn: "ISOPLANE (F5)", categoria: "MODO", aliases: todos(["Ctrl+Alt+5", "ISO"]), rf: ["RF28"], descricao: "Alterna Topo/Frente/Lateral (F5 é do navegador).", roteiro: [] },
  { codigo: "UCS_DINAMICO", nomePtBr: "UCS DINÂMICO", nomeEn: "DYNAMIC UCS (F6)", categoria: "MODO", aliases: todos(["F6"]), rf: ["RF28"], descricao: "Plano de trabalho segue a face apontada.", roteiro: [] },
  { codigo: "GRADE", nomePtBr: "GRADE", nomeEn: "GRID (F7)", categoria: "MODO", aliases: todos(["F7"]), rf: ["RF28"], descricao: "Mostra/oculta a grade de fundo.", roteiro: [] },
  { codigo: "ORTO", nomePtBr: "ORTO", nomeEn: "ORTHO (F8)", categoria: "MODO", aliases: todos(["F8"]), rf: ["RF28"], descricao: "Restringe o movimento a 90°.", roteiro: [] },
  { codigo: "SNAP_GRADE", nomePtBr: "SNAP DE GRADE", nomeEn: "SNAP (F9)", categoria: "MODO", aliases: todos(["F9"]), rf: ["RF28"], descricao: "Prende o cursor ao passo da grade.", roteiro: [] },
  { codigo: "POLAR", nomePtBr: "RASTREAMENTO POLAR", nomeEn: "POLAR (F10)", categoria: "MODO", aliases: todos(["F10"]), rf: ["RF28"], descricao: "Rastreamento angular.", roteiro: [] },
  { codigo: "OTRACK", nomePtBr: "RASTREAMENTO DE OBJETOS", nomeEn: "OTRACK (F11)", categoria: "MODO", aliases: todos(["Ctrl+Alt+1", "OTRACK"]), rf: ["RF28"], descricao: "Rastreia a partir de pontos de snap (F11 é do navegador).", roteiro: [] },
  { codigo: "ENTRADA_DINAMICA", nomePtBr: "ENTRADA DINÂMICA", nomeEn: "DYNAMIC INPUT (F12)", categoria: "MODO", aliases: todos(["Ctrl+Alt+2", "DYN"]), rf: ["RF28"], descricao: "Prompt junto ao cursor (F12 é do navegador).", roteiro: [] },
  { codigo: "HISTORICO", nomePtBr: "HISTÓRICO DE COMANDOS", nomeEn: "TEXT SCREEN (F2)", categoria: "CONFIG", aliases: todos(["F2"]), rf: ["RF28"], descricao: "Histórico da linha de comando.", roteiro: [] },
  { codigo: "AJUDA", nomePtBr: "AJUDA", nomeEn: "HELP (F1)", categoria: "CONFIG", aliases: todos(["?", "Shift+F1", "AJUDA"]), rf: ["RF28"], descricao: "Ajuda do comando ativo (F1 é do navegador).", roteiro: [] },
  { codigo: "UNIDADES", nomePtBr: "UNIDADES DO PROJETO", nomeEn: "PROJECT UNITS", categoria: "CONFIG", aliases: todos(["UN"]), rf: ["RF28"], descricao: "SI: m, kN, MPa.", roteiro: [] },
  { codigo: "ATALHOS", nomePtBr: "ATALHOS DE TECLADO", nomeEn: "KEYBOARD SHORTCUTS", categoria: "CONFIG", aliases: todos(["KS"]), rf: ["RF28"], descricao: "Perfil e aliases próprios.", roteiro: [] },
  { codigo: "DESFAZER", nomePtBr: "DESFAZER", nomeEn: "UNDO", categoria: "MODIFICACAO", aliases: todos(["U", "UNDO"]), rf: ["RF01"], descricao: "Desfaz o último comando (gera o inverso).", roteiro: [] },
  { codigo: "REFAZER", nomePtBr: "REFAZER", nomeEn: "REDO", categoria: "MODIFICACAO", aliases: todos(["REDO"]), rf: ["RF01"], descricao: "Refaz.", roteiro: [] },
];

export type ConflitoAlias = { alias: string; comandos: string[] };

export function resolverComando(texto: string, perfil: PerfilAtalhos): Comando | undefined {
  const t = texto.trim().toUpperCase();
  if (!t) return undefined;
  return (
    CATALOGO.find((c) => (c.aliases[perfil] ?? []).some((a) => a.toUpperCase() === t)) ??
    CATALOGO.find((c) => c.codigo === t || c.nomePtBr === t || c.nomeEn === t)
  );
}

export function buscarComandos(texto: string, perfil: PerfilAtalhos, limite = 12): Comando[] {
  const t = texto.trim().toUpperCase();
  if (!t) return CATALOGO.slice(0, limite);
  const pontua = (c: Comando) => {
    const aliases = (c.aliases[perfil] ?? []).map((a) => a.toUpperCase());
    if (aliases.includes(t)) return 0;
    if (aliases.some((a) => a.startsWith(t))) return 1;
    if (c.nomePtBr.startsWith(t) || c.nomeEn.startsWith(t) || c.codigo.startsWith(t)) return 2;
    if (c.nomePtBr.includes(t) || c.nomeEn.includes(t) || c.descricao.toUpperCase().includes(t)) return 3;
    return 9;
  };
  return CATALOGO.map((c) => ({ c, p: pontua(c) })).filter((x) => x.p < 9).sort((a, b) => a.p - b.p).slice(0, limite).map((x) => x.c);
}

/** aliases que significam coisas diferentes entre perfis (BR, EL, CO…) */
export function conflitosDeAlias(): ConflitoAlias[] {
  const porAlias = new Map<string, Set<string>>();
  for (const c of CATALOGO) for (const lista of Object.values(c.aliases)) for (const a of lista ?? []) {
    const s = porAlias.get(a.toUpperCase()) ?? new Set();
    s.add(c.codigo); porAlias.set(a.toUpperCase(), s);
  }
  return [...porAlias.entries()].filter(([, s]) => s.size > 1).map(([alias, s]) => ({ alias, comandos: [...s] }));
}

/** Teclas que o navegador reserva: o motor nunca as captura e oferece o alias alternativo. */
export const TECLAS_RESERVADAS_NAVEGADOR = ["F1", "F5", "F11", "F12"] as const;

export const MODOS_OSNAP = ["EIX", "EXT", "INT", "MED", "CEN", "PER", "TAN", "NOD", "QUA", "PRX", "NIV"] as const;
export type ModoOsnap = (typeof MODOS_OSNAP)[number];
/** prioridade do Osnap (RF28 CA-04) */
export const PRIORIDADE_OSNAP: ModoOsnap[] = ["EIX", "EXT", "INT", "MED", "CEN", "PER", "TAN", "NOD", "QUA", "PRX", "NIV"];

export const CAMADAS_PADRAO = ["arquitetura", "estrutura", "fundacao", "desenho", "anotacao", "ia-rascunho"] as const;
