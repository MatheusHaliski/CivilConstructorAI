package br.com.civilconstructorai.notacao;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Catálogo do motor de desenho (RF28) e Tabela B (funções VI). Fonte: docs/CATALOGO_COMANDOS.md.
 * Mesmo conteúdo de packages/notacao-vi/src/catalogo.ts.
 */
public final class CatalogoComandos {

    private CatalogoComandos() {
    }

    public enum Categoria { DESENHO, MODIFICACAO, VISUALIZACAO, ANOTACAO, MODO, CONFIG }

    public enum Perfil { CIVILCONSTRUCTOR, AUTOCAD, REVIT }

    public enum TipoEntrada { PONTO, VALOR, TEXTO, SELECAO, OPCAO, ANGULO }

    /** modos de Osnap em ordem de prioridade (RF28 CA-04) */
    public enum ModoOsnap { EIX, EXT, INT, MED, CEN, PER, TAN, NOD, QUA, PRX, NIV }

    public static final List<String> TECLAS_RESERVADAS_NAVEGADOR = List.of("F1", "F5", "F11", "F12");
    public static final List<String> CAMADAS_PADRAO = List.of("arquitetura", "estrutura", "fundacao", "desenho", "anotacao", "ia-rascunho");

    public record Passo(String nome, String mensagem, TipoEntrada tipo, Object padrao, boolean opcional, List<String> opcoes, boolean repetir) {
    }

    public record Comando(String codigo, String nomePtBr, String nomeEn, Categoria categoria, Map<Perfil, List<String>> aliases,
                          List<Passo> roteiro, List<String> papeis, List<String> rf, boolean extensao, String descricao) {
    }

    public record Conflito(String alias, List<String> comandos) {
    }

    private static Map<Perfil, List<String>> todos(String... a) {
        var l = List.of(a);
        return Map.of(Perfil.CIVILCONSTRUCTOR, l, Perfil.AUTOCAD, l, Perfil.REVIT, l);
    }

    private static Map<Perfil, List<String>> por(List<String> cca, List<String> acad, List<String> revit) {
        return Map.of(Perfil.CIVILCONSTRUCTOR, cca, Perfil.AUTOCAD, acad, Perfil.REVIT, revit);
    }

    private static Passo p(String nome, String msg) {
        return new Passo(nome, msg, TipoEntrada.PONTO, null, false, List.of("Desfazer"), false);
    }

    private static Passo pr(String nome, String msg, String... opcoes) {
        return new Passo(nome, msg, TipoEntrada.PONTO, null, false, List.of(opcoes), true);
    }

    private static Passo v(String nome, String msg, Object padrao) {
        return new Passo(nome, msg, TipoEntrada.VALOR, padrao, false, List.of(), false);
    }

    private static Passo t(String nome, String msg, String padrao) {
        return new Passo(nome, msg, TipoEntrada.TEXTO, padrao, padrao == null || padrao.isEmpty(), List.of(), false);
    }

    private static Passo o(String nome, String msg, String padrao, String... opcoes) {
        return new Passo(nome, msg, TipoEntrada.OPCAO, padrao, false, List.of(opcoes), false);
    }

    private static Passo s(String msg) {
        return new Passo("sel", msg, TipoEntrada.SELECAO, null, false, List.of(), false);
    }

    private static final List<String> ARQ_ENG = List.of("ARQUITETO", "ENGENHEIRO");
    private static final List<String> ENG = List.of("ENGENHEIRO");

    private static Comando c(String codigo, String pt, String en, Categoria cat, Map<Perfil, List<String>> aliases, List<String> rf, String desc, List<String> papeis, boolean ext, Passo... roteiro) {
        return new Comando(codigo, pt, en, cat, aliases, List.of(roteiro), papeis, rf, ext, desc);
    }

    public static final List<Comando> CATALOGO = List.of(
            // desenho · AutoCAD
            c("LINHA", "LINHA", "LINE", Categoria.DESENHO, todos("L"), List.of("RF01", "RF14"), "Linha auxiliar ou de detalhe 2D (camada desenho); não é elemento construtivo.", List.of(), false, p("p1", "Ponto inicial:"), pr("p2", "Próximo ponto ou [Desfazer/Fechar]:", "Desfazer", "Fechar")),
            c("POLILINHA", "POLILINHA", "PLINE", Categoria.DESENHO, todos("PL"), List.of("RF01", "RF04"), "Polilinha; fechada vira contorno de laje ou perímetro.", List.of(), false, p("p1", "Ponto inicial:"), pr("pn", "Próximo ponto ou [Desfazer/Fechar]:", "Desfazer", "Fechar")),
            c("CIRCULO", "CÍRCULO", "CIRCLE", Categoria.DESENHO, todos("C"), List.of("RF01"), "Círculo auxiliar; como seção de pilar → PILAR circular.", List.of(), false, p("centro", "Centro:"), v("raio", "Raio [1.0]:", 1.0)),
            c("RETANGULO", "RETÂNGULO", "RECTANG", Categoria.DESENHO, todos("REC"), List.of("RF01"), "Retângulo por dois pontos diagonais.", List.of(), false, p("p1", "Primeiro canto:"), p("p2", "Canto oposto:")),
            c("ARCO", "ARCO", "ARC", Categoria.DESENHO, todos("A"), List.of("RF01"), "Arco por 3 pontos.", List.of(), false, p("p1", "Ponto inicial:"), p("p2", "Segundo ponto:"), p("p3", "Ponto final:")),
            c("POLIGONO", "POLÍGONO", "POLYGON", Categoria.DESENHO, todos("POL"), List.of("RF01"), "Polígono regular inscrito.", List.of(), false, v("n", "Número de lados [6]:", 6), p("centro", "Centro:"), v("raio", "Raio [1.0]:", 1.0)),
            c("ELIPSE", "ELIPSE", "ELLIPSE", Categoria.DESENHO, por(List.of("ELI"), List.of("EL"), List.of("ELI")), List.of("RF01"), "Elipse auxiliar.", List.of(), false, p("centro", "Centro:"), v("a", "Semieixo maior [2.0]:", 2.0), v("b", "Semieixo menor [1.0]:", 1.0)),
            c("HACHURA", "HACHURA", "HATCH", Categoria.ANOTACAO, todos("H"), List.of("RF14"), "Hachura de corte/material (padrão ABNT por material).", List.of(), false, s("Selecione o contorno:"), t("padrao", "Padrão [CONCRETO]:", "CONCRETO")),
            c("SPLINE", "SPLINE", "SPLINE", Categoria.DESENHO, todos("SPL"), List.of("RF22", "RF09"), "Curva suave: curvas de nível e topografia.", List.of(), false, p("p1", "Ponto inicial:"), pr("pn", "Próximo ponto ou [Fechar]:", "Desfazer", "Fechar")),
            c("PONTO", "PONTO", "POINT", Categoria.DESENHO, todos("PO"), List.of("RF09"), "Ponto de referência; com rótulo SP-nn vira furo de sondagem.", List.of(), false, p("p", "Ponto:"), t("rotulo", "Rótulo (opcional):", "")),
            c("RAIO_CONSTRUCAO", "RAIO DE CONSTRUÇÃO", "RAY", Categoria.DESENHO, todos("RAY"), List.of("RF01"), "Linha de construção semi-infinita (não imprime).", List.of(), false, p("origem", "Origem:"), p("direcao", "Ponto na direção:")),
            c("LINHA_CONSTRUCAO", "LINHA DE CONSTRUÇÃO", "XLINE", Categoria.DESENHO, todos("XL"), List.of("RF01", "RF03"), "Linha de construção infinita; sobre eixo vira EIXO.", List.of(), false, p("p", "Ponto:"), p("direcao", "Ponto na direção:")),
            c("ANEL", "ANEL", "DONUT", Categoria.DESENHO, todos("DO"), List.of("RF10"), "Anel (seção vazada).", List.of(), false, p("centro", "Centro:"), v("ri", "Raio interno [0.2]:", 0.2), v("re", "Raio externo [0.3]:", 0.3)),
            c("REGIAO", "REGIÃO", "REGION", Categoria.DESENHO, todos("REG"), List.of("RF04", "RF10", "RF11"), "Região 2D fechada: base para laje, radier e área.", List.of(), false, s("Selecione o contorno fechado:")),
            // desenho · Revit → Tabela B
            c("PAREDE", "PAREDE", "WALL", Categoria.DESENHO, por(List.of("WA", "ML"), List.of("ML"), List.of("WA")), List.of("RF01"), "Parede básica, composta ou cortina (tipo pelo material da Tabela A).", ARQ_ENG, false, p("p1", "Ponto inicial da parede:"), p("p2", "Ponto final ou [Desfazer]:"), v("esp", "Espessura [0.15]:", 0.15), v("h", "Altura [2.80]:", 2.8), t("mat", "Material [MAT.ALVENARIA.CERAMICO_VEDACAO_14]:", "MAT.ALVENARIA.CERAMICO_VEDACAO_14")),
            c("PORTA", "PORTA", "DOOR", Categoria.DESENHO, todos("DR"), List.of("RF01", "RF16"), "Porta hospedada na parede (NewOpening por projeção vetorial).", ARQ_ENG, false, p("pos", "Clique na parede hospedeira na posição da porta:"), v("larg", "Largura [0.80]:", 0.8), v("alt", "Altura [2.10]:", 2.1)),
            c("JANELA", "JANELA", "WINDOW", Categoria.DESENHO, todos("WN"), List.of("RF01", "RF16"), "Janela hospedada na parede.", ARQ_ENG, false, p("pos", "Clique na parede hospedeira na posição da janela:"), v("larg", "Largura [1.20]:", 1.2), v("alt", "Altura [1.20]:", 1.2), v("peitoril", "Peitoril [1.00]:", 1.0)),
            c("AMBIENTE", "AMBIENTE", "ROOM", Categoria.DESENHO, todos("RM"), List.of("RF01", "RF11"), "Ambiente delimitado por paredes; calcula área e perímetro.", List.of(), false, p("ponto", "Clique dentro do ambiente:"), t("nome", "Nome [Sala]:", "Sala")),
            c("EIXO", "EIXO", "GRID", Categoria.DESENHO, todos("GR"), List.of("RF03"), "Eixo estrutural por dois pontos.", List.of(), false, p("p1", "Ponto inicial do eixo:"), p("p2", "Ponto final:"), t("nome", "Nome (vazio = automático):", "")),
            c("MALHA", "MALHA DE EIXOS", "GRID ARRAY", Categoria.DESENHO, todos("MALHA"), List.of("RF03"), "Gera a grade inteira: nx × ny eixos com espaçamentos.", List.of(), true, v("nx", "Eixos na direção X (letras) [5]:", 5), v("dx", "Espaçamento X [5.0]:", 5.0), v("ny", "Eixos na direção Y (números) [3]:", 3), v("dy", "Espaçamento Y [6.0]:", 6.0)),
            c("PAVIMENTO", "PAVIMENTO", "LEVEL", Categoria.DESENHO, todos("LL"), List.of("RF03", "RF17"), "Nível/pavimento em Z; base da Disciplina Z.", List.of(), false, t("nome", "Nome [Pavimento]:", "Pavimento"), v("z", "Elevação em metros:", null)),
            c("PLANO_REF", "PLANO DE REFERÊNCIA", "REFERENCE PLANE", Categoria.DESENHO, todos("RP"), List.of("RF01"), "Plano de referência não imprimível para alinhamento.", List.of(), false, p("p1", "Ponto inicial:"), p("p2", "Ponto final:")),
            c("VIGA", "VIGA", "STRUCTURAL BEAM", Categoria.DESENHO, todos("BM"), List.of("RF04"), "Viga entre apoios.", ENG, false, p("p1", "Ponto inicial da viga:"), p("p2", "Ponto final ou [Desfazer]:"), v("b", "Largura b [0.12]:", 0.12), v("h", "Altura h [0.40]:", 0.4), t("mat", "Material [MAT.CONCRETO.C30]:", "MAT.CONCRETO.C30")),
            c("CONTRAVENTAMENTO", "CONTRAVENTAMENTO", "STRUCTURAL BRACE", Categoria.DESENHO, por(List.of("BR"), List.of("BRACE"), List.of("BR")), List.of("RF04"), "Contraventamento (aço).", ENG, false, p("p1", "Ponto inicial:"), p("p2", "Ponto final:"), t("perfil", "Perfil [L 50x5]:", "L 50x5")),
            c("PILAR", "PILAR", "STRUCTURAL COLUMN", Categoria.DESENHO, todos("CL"), List.of("RF04", "RF17"), "Pilar; base e topo presos aos pavimentos (Disciplina Z).", ENG, true, p("centro", "Centro do pilar (snap EIX):"), v("b", "Largura b [0.19]:", 0.19), v("h", "Altura h [0.40]:", 0.4), t("mat", "Material [MAT.CONCRETO.C30]:", "MAT.CONCRETO.C30")),
            c("LAJE", "LAJE", "STRUCTURAL FLOOR", Categoria.DESENHO, todos("SB"), List.of("RF04"), "Laje por contorno fechado.", ARQ_ENG, true, p("p1", "Primeiro vértice do contorno:"), pr("pn", "Próximo vértice ou [Fechar]:", "Desfazer", "Fechar"), v("h", "Espessura [0.12]:", 0.12)),
            c("FUNDACAO", "FUNDAÇÃO", "FOUNDATION", Categoria.DESENHO, todos("FD"), List.of("RF10"), "Sapata, bloco ou radier sob um pilar.", ENG, true, s("Selecione o pilar:"), o("tipo", "Tipo [SAPATA/BLOCO/RADIER]:", "SAPATA", "SAPATA", "BLOCO", "RADIER")),
            // modificação
            c("MOVER", "MOVER", "MOVE", Categoria.MODIFICACAO, por(List.of("M", "MV"), List.of("M"), List.of("MV")), List.of("RF28"), "Move a seleção com precisão por Osnap.", List.of(), false, s("Selecione objetos:"), p("base", "Ponto base:"), p("destino", "Segundo ponto (destino):")),
            c("COPIAR", "COPIAR", "COPY", Categoria.MODIFICACAO, por(List.of("CO", "CP"), List.of("CO", "CP"), List.of("CO")), List.of("RF28"), "Cópia múltipla até [Sair].", List.of(), false, s("Selecione objetos:"), p("base", "Ponto base:"), pr("destino", "Destino ou [Sair]:", "Desfazer", "Sair")),
            c("ROTACIONAR", "ROTACIONAR", "ROTATE", Categoria.MODIFICACAO, todos("RO"), List.of("RF28"), "Gira em torno do ponto base.", List.of(), false, s("Selecione objetos:"), p("base", "Ponto base:"), new Passo("ang", "Ângulo em graus [90]:", TipoEntrada.ANGULO, 90, false, List.of(), false)),
            c("APARAR", "APARAR", "TRIM", Categoria.MODIFICACAO, todos("TR"), List.of("RF28"), "Corta em interseções (linhas, polilinhas e paredes).", List.of(), false, s("Selecione as arestas de corte:"), s("Selecione os objetos a aparar:")),
            c("ESTENDER", "ESTENDER", "EXTEND", Categoria.MODIFICACAO, todos("EX"), List.of("RF28"), "Estende até a aresta.", List.of(), false, s("Selecione as arestas de destino:"), s("Selecione os objetos a estender:")),
            c("CONCORDAR", "CONCORDAR", "FILLET", Categoria.MODIFICACAO, todos("F"), List.of("RF28"), "Concordância; raio 0 fecha canto vivo entre paredes.", List.of(), false, v("raio", "Raio [0]:", 0), s("Primeiro objeto:"), s("Segundo objeto:")),
            c("CHANFRAR", "CHANFRAR", "CHAMFER", Categoria.MODIFICACAO, todos("CHA"), List.of("RF28"), "Chanfro.", List.of(), false, v("d1", "Distância 1 [0.1]:", 0.1), v("d2", "Distância 2 [0.1]:", 0.1), s("Primeiro objeto:"), s("Segundo objeto:")),
            c("DESLOCAR", "DESLOCAR", "OFFSET", Categoria.MODIFICACAO, todos("O"), List.of("RF01", "RF28"), "Cópia paralela (paredes, eixos, contornos); MULTIPLE repete.", List.of(), false, v("dist", "Distância [1.0]:", 1.0), s("Selecione o objeto:"), pr("lado", "Clique no lado do deslocamento ou [Sair]:", "Sair")),
            c("ESPELHAR", "ESPELHAR", "MIRROR", Categoria.MODIFICACAO, todos("MI"), List.of("RF28"), "Espelha em torno de uma linha.", List.of(), false, s("Selecione objetos:"), p("p1", "Primeiro ponto da linha de espelho:"), p("p2", "Segundo ponto:"), o("apagar", "Apagar originais? [N]:", "N", "S", "N")),
            c("ESTICAR", "ESTICAR", "STRETCH", Categoria.MODIFICACAO, todos("S"), List.of("RF28"), "Estica o que a janela cruza; pilar não estica, move.", List.of(), false, s("Selecione por janela de cruzamento:"), p("base", "Ponto base:"), p("destino", "Segundo ponto:")),
            c("ESCALAR", "ESCALAR", "SCALE", Categoria.MODIFICACAO, todos("SC"), List.of("RF28"), "Escala proporcional (só auxiliares).", List.of(), false, s("Selecione objetos:"), p("base", "Ponto base:"), v("fator", "Fator [2]:", 2)),
            c("MATRIZ", "MATRIZ", "ARRAY", Categoria.MODIFICACAO, todos("AR"), List.of("RF28"), "Matriz retangular, polar ou em caminho.", List.of(), false, s("Selecione objetos:"), o("tipo", "Tipo [RETANGULAR/POLAR]:", "RETANGULAR", "RETANGULAR", "POLAR"), v("nx", "Colunas [3]:", 3), v("dx", "Passo X [5.0]:", 5.0), v("ny", "Linhas [1]:", 1), v("dy", "Passo Y [6.0]:", 6.0)),
            c("APAGAR", "APAGAR", "ERASE", Categoria.MODIFICACAO, por(List.of("E", "DE"), List.of("E"), List.of("DE")), List.of("RF28"), "Remove; gera comando inverso para desfazer.", List.of(), false, s("Selecione objetos:")),
            c("EXPLODIR", "EXPLODIR", "EXPLODE", Categoria.MODIFICACAO, todos("X"), List.of("RF28"), "Polilinha → linhas; elemento construtivo não explode.", List.of(), false, s("Selecione objetos:")),
            c("UNIR", "UNIR", "JOIN", Categoria.MODIFICACAO, todos("J"), List.of("RF28"), "Linhas/arcos → uma polilinha.", List.of(), false, s("Selecione objetos:")),
            c("QUEBRAR", "QUEBRAR", "BREAK", Categoria.MODIFICACAO, por(List.of("QB"), List.of("BR"), List.of("QB")), List.of("RF28"), "Quebra entre dois pontos.", List.of(), false, s("Selecione o objeto:"), p("p1", "Primeiro ponto de quebra:"), p("p2", "Segundo ponto:")),
            c("ALONGAR", "ALONGAR", "LENGTHEN", Categoria.MODIFICACAO, todos("LEN"), List.of("RF28"), "Muda comprimento (delta ou total).", List.of(), false, s("Selecione o objeto:"), v("delta", "Incremento em metros [1.0]:", 1.0)),
            c("ALINHAR", "ALINHAR", "ALIGN", Categoria.MODIFICACAO, todos("AL"), List.of("RF28"), "Alinha faces/bordas a uma referência (eixo, plano).", List.of(), false, s("Selecione a referência:"), s("Selecione os objetos a alinhar:")),
            c("COPIAR_PROPRIEDADES", "COPIAR PROPRIEDADES", "MATCHPROP", Categoria.MODIFICACAO, todos("MA"), List.of("RF13"), "Copia propriedades visuais/camada.", List.of(), false, s("Selecione o objeto de origem:"), s("Selecione os objetos de destino:")),
            c("DESFAZER", "DESFAZER", "UNDO", Categoria.MODIFICACAO, todos("U", "UNDO"), List.of("RF01"), "Desfaz o último comando (gera o inverso).", List.of(), false),
            c("REFAZER", "REFAZER", "REDO", Categoria.MODIFICACAO, todos("REDO"), List.of("RF01"), "Refaz.", List.of(), false),
            // visualização
            c("ZOOM", "ZOOM", "ZOOM", Categoria.VISUALIZACAO, todos("Z"), List.of("RF13"), "Zoom por janela.", List.of(), false, p("p1", "Primeiro canto da janela:"), p("p2", "Canto oposto:")),
            c("ZOOM_TUDO", "ENQUADRAR TUDO", "ZOOM EXTENTS", Categoria.VISUALIZACAO, todos("ZE", "ZA"), List.of("RF13"), "Enquadra todo o desenho.", List.of(), false),
            c("PAN", "MOVER VISTA", "PAN", Categoria.VISUALIZACAO, todos("P"), List.of("RF13"), "Move a vista sem alterar a escala.", List.of(), false, p("p1", "Ponto base:"), p("p2", "Segundo ponto:")),
            c("REGENERAR", "REGENERAR", "REGEN", Categoria.VISUALIZACAO, todos("RE"), List.of("RF13"), "Reconstrói a cena a partir do modelo.", List.of(), false),
            c("OCULTAR", "OCULTAR ELEMENTO", "HIDE ELEMENT", Categoria.VISUALIZACAO, todos("HH"), List.of("RF13"), "Oculta temporariamente.", List.of(), false, s("Selecione objetos:")),
            c("ISOLAR", "ISOLAR ELEMENTO", "ISOLATE ELEMENT", Categoria.VISUALIZACAO, todos("HI"), List.of("RF13"), "Isola temporariamente.", List.of(), false, s("Selecione objetos:")),
            c("REEXIBIR", "REEXIBIR ELEMENTO", "UNHIDE ELEMENT", Categoria.VISUALIZACAO, todos("EU"), List.of("RF13"), "Reexibe elementos ocultos.", List.of(), false),
            c("REEXIBIR_CATEGORIA", "REEXIBIR CATEGORIA", "UNHIDE CATEGORY", Categoria.VISUALIZACAO, todos("VU"), List.of("RF13"), "Reexibe uma categoria inteira.", List.of(), false),
            c("VISIBILIDADE", "VISIBILIDADE E ESTILOS", "VISIBILITY/GRAPHICS", Categoria.VISUALIZACAO, todos("VG"), List.of("RF13"), "Painel de camadas e estilos (Tabela H).", List.of(), false),
            c("CAMADAS", "CAMADAS", "LAYER", Categoria.VISUALIZACAO, todos("LA"), List.of("RF13"), "Gerenciador de camadas.", List.of(), false),
            c("PROPRIEDADES", "PROPRIEDADES", "PROPERTIES", Categoria.VISUALIZACAO, todos("PR", "CH"), List.of("RF01"), "Painel de propriedades (formulário H5).", List.of(), false),
            // anotação
            c("TEXTO", "TEXTO", "MTEXT", Categoria.ANOTACAO, por(List.of("TX", "MT", "T"), List.of("MT", "T"), List.of("TX")), List.of("RF14"), "Caixa de texto.", List.of(), false, p("p", "Ponto de inserção:"), t("texto", "Texto:", "")),
            c("ESTILO_COTA", "ESTILO DE COTA", "DIMSTYLE", Categoria.ANOTACAO, todos("D"), List.of("RF14"), "Estilo de cota (NBR 10126).", List.of(), false),
            c("COTA_LINEAR", "COTA LINEAR", "DIMLINEAR", Categoria.ANOTACAO, todos("DLI"), List.of("RF14"), "Cota horizontal/vertical.", List.of(), false, p("p1", "Origem da primeira linha de chamada:"), p("p2", "Origem da segunda:"), p("pos", "Posição da linha de cota:")),
            c("COTA_ALINHADA", "COTA ALINHADA", "DIMALIGNED", Categoria.ANOTACAO, todos("DAL", "DI"), List.of("RF14"), "Cota alinhada ao objeto.", List.of(), false, p("p1", "Origem da primeira linha de chamada:"), p("p2", "Origem da segunda:"), p("pos", "Posição da linha de cota:")),
            c("COTA_CONTINUA", "COTA CONTÍNUA", "DIMCONTINUE", Categoria.ANOTACAO, todos("DCO"), List.of("RF14"), "Continua a partir da última cota.", List.of(), false, pr("p", "Origem da próxima linha de chamada ou [Sair]:", "Sair")),
            c("COTA_NIVEL", "COTA DE NÍVEL", "SPOT ELEVATION", Categoria.ANOTACAO, por(List.of("EL"), List.of("NIVEL"), List.of("EL")), List.of("RF14"), "Cota de nível pontual (+3,16).", List.of(), false, p("p", "Ponto:")),
            c("LINHA_DETALHE", "LINHA DE DETALHE", "DETAIL LINE", Categoria.ANOTACAO, todos("DL"), List.of("RF14"), "Linha 2D sobre a vista.", List.of(), false, p("p1", "Ponto inicial:"), p("p2", "Ponto final:")),
            c("ETIQUETA", "ETIQUETA", "TAG BY CATEGORY", Categoria.ANOTACAO, todos("TG"), List.of("RF14"), "Etiqueta por categoria (P1, V12, L3).", List.of(), false, s("Selecione o elemento a etiquetar:")),
            c("BLOCO", "BLOCO", "BLOCK", Categoria.ANOTACAO, todos("B"), List.of("RF14"), "Define um bloco (símbolos, carimbo).", List.of(), false, s("Selecione os objetos do bloco:"), t("nome", "Nome do bloco:", "")),
            c("INSERIR", "INSERIR BLOCO", "INSERT", Categoria.ANOTACAO, todos("I"), List.of("RF14"), "Insere um bloco existente.", List.of(), false, t("nome", "Nome do bloco:", ""), p("p", "Ponto de inserção:")),
            c("LINHA_QUEBRA", "LINHA DE QUEBRA", "BREAKLINE", Categoria.ANOTACAO, todos("BREAKLINE", "BRALINE"), List.of("RF14"), "Símbolo de quebra na prancha.", List.of(), false, p("p1", "Ponto inicial:"), p("p2", "Ponto final:")),
            c("LOCALIZAR", "LOCALIZAR E SUBSTITUIR", "FIND", Categoria.ANOTACAO, todos("FIND"), List.of("RF14"), "Busca e substitui em textos da prancha.", List.of(), false, t("buscar", "Buscar:", ""), t("substituir", "Substituir por:", "")),
            // modos e configuração
            c("OSNAP", "ATRAÇÃO A OBJETOS", "OSNAP (F3)", Categoria.MODO, todos("F3", "OSNAP"), List.of("RF28"), "Liga/desliga atração a pontos notáveis.", List.of(), false),
            c("OSNAP3D", "ATRAÇÃO 3D", "3D OSNAP (F4)", Categoria.MODO, todos("F4"), List.of("RF28"), "Atração a vértices/faces no 3D.", List.of(), false),
            c("PLANO_TRABALHO", "PLANO DE TRABALHO", "ISOPLANE (F5)", Categoria.MODO, todos("Ctrl+Alt+5", "ISO"), List.of("RF28"), "Alterna Topo/Frente/Lateral (F5 é do navegador).", List.of(), false),
            c("UCS_DINAMICO", "UCS DINÂMICO", "DYNAMIC UCS (F6)", Categoria.MODO, todos("F6"), List.of("RF28"), "Plano de trabalho segue a face apontada.", List.of(), false),
            c("GRADE", "GRADE", "GRID (F7)", Categoria.MODO, todos("F7"), List.of("RF28"), "Mostra/oculta a grade de fundo.", List.of(), false),
            c("ORTO", "ORTO", "ORTHO (F8)", Categoria.MODO, todos("F8"), List.of("RF28"), "Restringe o movimento a 90°.", List.of(), false),
            c("SNAP_GRADE", "SNAP DE GRADE", "SNAP (F9)", Categoria.MODO, todos("F9"), List.of("RF28"), "Prende o cursor ao passo da grade.", List.of(), false),
            c("POLAR", "RASTREAMENTO POLAR", "POLAR (F10)", Categoria.MODO, todos("F10"), List.of("RF28"), "Rastreamento angular.", List.of(), false),
            c("OTRACK", "RASTREAMENTO DE OBJETOS", "OTRACK (F11)", Categoria.MODO, todos("Ctrl+Alt+1", "OTRACK"), List.of("RF28"), "Rastreia a partir de pontos de snap (F11 é do navegador).", List.of(), false),
            c("ENTRADA_DINAMICA", "ENTRADA DINÂMICA", "DYNAMIC INPUT (F12)", Categoria.MODO, todos("Ctrl+Alt+2", "DYN"), List.of("RF28"), "Prompt junto ao cursor (F12 é do navegador).", List.of(), false),
            c("HISTORICO", "HISTÓRICO DE COMANDOS", "TEXT SCREEN (F2)", Categoria.CONFIG, todos("F2"), List.of("RF28"), "Histórico da linha de comando.", List.of(), false),
            c("AJUDA", "AJUDA", "HELP (F1)", Categoria.CONFIG, todos("?", "Shift+F1", "AJUDA"), List.of("RF28"), "Ajuda do comando ativo (F1 é do navegador).", List.of(), false),
            c("UNIDADES", "UNIDADES DO PROJETO", "PROJECT UNITS", Categoria.CONFIG, todos("UN"), List.of("RF28"), "SI: m, kN, MPa.", List.of(), false),
            c("ATALHOS", "ATALHOS DE TECLADO", "KEYBOARD SHORTCUTS", Categoria.CONFIG, todos("KS"), List.of("RF28"), "Perfil e aliases próprios.", List.of(), false));

    private static final Map<String, Comando> POR_CODIGO = new LinkedHashMap<>();

    static {
        CATALOGO.forEach(x -> POR_CODIGO.put(x.codigo(), x));
    }

    public static Optional<Comando> porCodigo(String codigo) {
        return Optional.ofNullable(POR_CODIGO.get(codigo.toUpperCase(Locale.ROOT)));
    }

    /** funções da Tabela B = comandos que produzem ComandoVI (desenho e modificação) */
    public static List<Comando> tabelaB() {
        return CATALOGO.stream().filter(x -> x.categoria() == Categoria.DESENHO || x.categoria() == Categoria.MODIFICACAO || x.categoria() == Categoria.ANOTACAO).toList();
    }

    public static Optional<Comando> resolver(String texto, Perfil perfil) {
        var t = texto == null ? "" : texto.trim().toUpperCase(Locale.ROOT);
        if (t.isEmpty()) {
            return Optional.empty();
        }
        return CATALOGO.stream().filter(x -> x.aliases().getOrDefault(perfil, List.of()).stream().anyMatch(a -> a.equalsIgnoreCase(t))).findFirst()
                .or(() -> CATALOGO.stream().filter(x -> x.codigo().equals(t) || x.nomePtBr().equalsIgnoreCase(t) || x.nomeEn().equalsIgnoreCase(t)).findFirst());
    }

    public static List<Comando> buscar(String texto, Perfil perfil, int limite) {
        var t = texto == null ? "" : texto.trim().toUpperCase(Locale.ROOT);
        if (t.isEmpty()) {
            return CATALOGO.subList(0, Math.min(limite, CATALOGO.size()));
        }
        record Pontuado(Comando c, int p) {
        }
        return CATALOGO.stream().map(x -> {
            var aliases = x.aliases().getOrDefault(perfil, List.of()).stream().map(a -> a.toUpperCase(Locale.ROOT)).toList();
            int p = aliases.contains(t) ? 0 : aliases.stream().anyMatch(a -> a.startsWith(t)) ? 1
                    : (x.nomePtBr().startsWith(t) || x.nomeEn().startsWith(t) || x.codigo().startsWith(t)) ? 2
                    : (x.nomePtBr().contains(t) || x.nomeEn().contains(t) || x.descricao().toUpperCase(Locale.ROOT).contains(t)) ? 3 : 9;
            return new Pontuado(x, p);
        }).filter(x -> x.p < 9).sorted(Comparator.comparingInt(Pontuado::p)).limit(limite).map(Pontuado::c).toList();
    }

    /** aliases com significados diferentes entre perfis (BR, EL, CO…) */
    public static List<Conflito> conflitos() {
        var porAlias = new LinkedHashMap<String, Set<String>>();
        for (var c : CATALOGO) {
            for (var lista : c.aliases().values()) {
                for (var a : lista) {
                    porAlias.computeIfAbsent(a.toUpperCase(Locale.ROOT), k -> new LinkedHashSet<>()).add(c.codigo());
                }
            }
        }
        var out = new ArrayList<Conflito>();
        porAlias.forEach((a, s) -> {
            if (s.size() > 1) {
                out.add(new Conflito(a, List.copyOf(s)));
            }
        });
        return out;
    }

    public static List<ModoOsnap> prioridadeOsnap() {
        return Arrays.asList(ModoOsnap.values());
    }
}
