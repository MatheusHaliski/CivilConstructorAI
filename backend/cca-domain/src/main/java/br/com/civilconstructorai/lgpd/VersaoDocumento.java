package br.com.civilconstructorai.lgpd;

import java.util.Comparator;

/** Compara versões "maior.menor" de documentos legais e textos de finalidade. */
public final class VersaoDocumento {

    private VersaoDocumento() {
    }

    public static final Comparator<String> ORDEM = VersaoDocumento::comparar;

    public static int comparar(String a, String b) {
        String[] pa = a.trim().split("\\.");
        String[] pb = b.trim().split("\\.");
        for (int i = 0; i < Math.max(pa.length, pb.length); i++) {
            int x = i < pa.length ? parse(pa[i]) : 0, y = i < pb.length ? parse(pb[i]) : 0;
            if (x != y) {
                return Integer.compare(x, y);
            }
        }
        return 0;
    }

    public static boolean desatualizada(String aceita, String vigente) {
        return aceita == null || comparar(aceita, vigente) < 0;
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s.replaceAll("\\D", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
