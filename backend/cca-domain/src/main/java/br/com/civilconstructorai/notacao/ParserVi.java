package br.com.civilconstructorai.notacao;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Parser da Notação VI: {@code VI(COD, nome=valor, …)}. Valores: número (vírgula decimal aceita), texto,
 * booleano, ponto {@code [x,y]}, lista, {@code @ref}, relativo {@code @dx,dy}, polar {@code @d<ang}.
 * Devolve <strong>todos</strong> os erros de uma vez, com linha e coluna (VI-000..VI-003).
 */
public final class ParserVi {

    private static final Pattern RE_COMANDO = Pattern.compile("^\\s*VI\\s*\\(\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*(?:,\\s*(.*))?\\)\\s*;?\\s*$");
    private static final Pattern RE_NUM = Pattern.compile("^-?\\d+([.,]\\d+)?$");
    private static final Pattern RE_POLAR = Pattern.compile("^@-?[\\d.,]+<-?[\\d.,]+$");
    private static final Pattern RE_REL = Pattern.compile("^@-?[\\d.,]+\\s*,\\s*-?[\\d.,]+$");
    private static final Pattern RE_REF = Pattern.compile("^@[A-Za-z_][\\w-]*$");
    private static final Pattern RE_NOME = Pattern.compile("^[a-zA-Z_]\\w*$");

    private ParserVi() {
    }

    public record Ref(String ref) {
    }

    public record Relativo(double dx, double dy) {
    }

    public record Polar(double d, double ang) {
    }

    public record ErroVi(String codigo, String mensagem, int linha, int coluna) {
    }

    public record ComandoLido(String codigo, Map<String, Object> args, int linha) {
    }

    public record ProgramaVi(List<ComandoLido> comandos, List<ErroVi> erros) {
        public boolean valido() {
            return erros.isEmpty();
        }
    }

    public static ProgramaVi parse(String fonte) {
        var comandos = new ArrayList<ComandoLido>();
        var erros = new ArrayList<ErroVi>();
        var linhas = fonte.split("\\r?\\n");
        for (int i = 0; i < linhas.length; i++) {
            int linha = i + 1;
            var texto = linhas[i];
            if (texto.isBlank() || texto.trim().startsWith("#")) {
                continue;
            }
            var m = RE_COMANDO.matcher(texto);
            if (!m.matches()) {
                erros.add(new ErroVi("VI-000", "Esperado VI(CODIGO, arg=valor, …)", linha, 1));
                continue;
            }
            var codigo = m.group(1).toUpperCase(Locale.ROOT);
            var args = new LinkedHashMap<String, Object>();
            var posicionais = new ArrayList<Object>();
            for (var parte : m.group(2) == null ? List.<String>of() : dividir(m.group(2))) {
                int eq = parte.indexOf('=');
                if (eq < 0) {
                    posicionais.add(parseValor(parte));
                    continue;
                }
                var nome = parte.substring(0, eq).trim();
                if (!RE_NOME.matcher(nome).matches()) {
                    erros.add(new ErroVi("VI-003", "Nome de argumento inválido: \"" + nome + "\"", linha, texto.indexOf(parte) + 1));
                    continue;
                }
                args.put(nome, parseValor(parte.substring(eq + 1)));
            }
            if (!posicionais.isEmpty()) {
                args.put("_", posicionais);
            }
            comandos.add(new ComandoLido(codigo, args, linha));
        }
        return new ProgramaVi(List.copyOf(comandos), List.copyOf(erros));
    }

    public static Object parseValor(String texto) {
        var t = texto.trim();
        if ((t.startsWith("\"") && t.endsWith("\"") && t.length() >= 2) || (t.startsWith("'") && t.endsWith("'") && t.length() >= 2)) {
            return t.substring(1, t.length() - 1);
        }
        if (t.equals("true") || t.equals("false")) {
            return Boolean.parseBoolean(t);
        }
        if (RE_POLAR.matcher(t).matches()) {
            var p = t.substring(1).split("<");
            return new Polar(num(p[0]), num(p[1]));
        }
        if (RE_REL.matcher(t).matches()) {
            var p = t.substring(1).split(",");
            return new Relativo(num(p[0]), num(p[1]));
        }
        if (RE_REF.matcher(t).matches()) {
            return new Ref(t.substring(1));
        }
        if (t.startsWith("[") && t.endsWith("]")) {
            var itens = dividir(t.substring(1, t.length() - 1)).stream().map(ParserVi::parseValor).toList();
            if (itens.size() == 2 && itens.stream().allMatch(v -> v instanceof Double)) {
                return Ponto2D.de((Double) itens.get(0), (Double) itens.get(1));
            }
            return itens;
        }
        if (RE_NUM.matcher(t).matches()) {
            return num(t);
        }
        return t;
    }

    private static double num(String t) {
        return Double.parseDouble(t.trim().replace(',', '.'));
    }

    static List<String> dividir(String texto) {
        var partes = new ArrayList<String>();
        int nivel = 0;
        var atual = new StringBuilder();
        Character aspas = null;
        for (char ch : texto.toCharArray()) {
            if (aspas != null) {
                atual.append(ch);
                if (ch == aspas) {
                    aspas = null;
                }
                continue;
            }
            if (ch == '"' || ch == '\'') {
                aspas = ch;
                atual.append(ch);
                continue;
            }
            if (ch == '[') {
                nivel++;
            }
            if (ch == ']') {
                nivel--;
            }
            if (ch == ',' && nivel == 0) {
                partes.add(atual.toString());
                atual.setLength(0);
                continue;
            }
            atual.append(ch);
        }
        if (!atual.toString().isBlank()) {
            partes.add(atual.toString());
        }
        return partes;
    }
}
