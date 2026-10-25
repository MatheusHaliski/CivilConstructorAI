package br.com.civilconstructorai.notacao;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/** Serializa {@code VI(COD, k=v, …)} a partir de código e argumentos (inverso do {@link ParserVi}). */
public final class FormatadorVi {

    private FormatadorVi() {
    }

    public static String valor(Object v) {
        if (v instanceof Number n) {
            double d = n.doubleValue();
            if (d == Math.rint(d) && Math.abs(d) < 1e15) {
                return String.valueOf((long) d);
            }
            var s = String.format(Locale.ROOT, "%.3f", d);
            return s.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        if (v instanceof Boolean b) {
            return b ? "true" : "false";
        }
        if (v instanceof String s) {
            return s.matches("^[A-Z0-9_]+$") ? s : "\"" + s + "\"";
        }
        if (v instanceof Ponto2D p) {
            return "[" + valor(p.x()) + "," + valor(p.y()) + "]";
        }
        if (v instanceof ParserVi.Ref r) {
            return "@" + r.ref();
        }
        if (v instanceof ParserVi.Relativo r) {
            return "@" + valor(r.dx()) + "," + valor(r.dy());
        }
        if (v instanceof ParserVi.Polar p) {
            return "@" + valor(p.d()) + "<" + valor(p.ang());
        }
        if (v instanceof List<?> l) {
            return "[" + l.stream().map(FormatadorVi::valor).collect(Collectors.joining(",")) + "]";
        }
        if (v instanceof Map<?, ?> m && m.containsKey("x") && m.containsKey("y")) {
            return "[" + valor(m.get("x")) + "," + valor(m.get("y")) + "]";
        }
        return String.valueOf(v);
    }

    public static String formatar(String codigo, Map<String, ?> args) {
        var partes = args.entrySet().stream()
                .filter(e -> e.getValue() != null)
                .map(e -> e.getKey().equals("_") ? ((List<?>) e.getValue()).stream().map(FormatadorVi::valor).collect(Collectors.joining(", ")) : e.getKey() + "=" + valor(e.getValue()))
                .toList();
        return "VI(" + codigo + (partes.isEmpty() ? "" : ", " + String.join(", ", partes)) + ")";
    }
}
