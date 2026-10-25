package br.com.civilconstructorai.empresa;

import br.com.civilconstructorai.comum.ExcecaoDominio;

/** Validação de CNPJ (14 dígitos, dígitos verificadores módulo 11). */
public final class Cnpj {

    private Cnpj() {
    }

    public static String normalizar(String cnpj) {
        if (cnpj == null) {
            throw new ExcecaoDominio.Validacao("cnpj", "Informe o CNPJ.");
        }
        var d = cnpj.replaceAll("\\D", "");
        if (!valido(d)) {
            throw new ExcecaoDominio.Validacao("cnpj", "CNPJ inválido.");
        }
        return d;
    }

    public static boolean valido(String d) {
        if (d == null || d.length() != 14 || d.chars().distinct().count() == 1) {
            return false;
        }
        int[] p1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        int[] p2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        return digito(d, p1) == d.charAt(12) - '0' && digito(d, p2) == d.charAt(13) - '0';
    }

    private static int digito(String d, int[] pesos) {
        int soma = 0;
        for (int i = 0; i < pesos.length; i++) {
            soma += (d.charAt(i) - '0') * pesos[i];
        }
        int r = soma % 11;
        return r < 2 ? 0 : 11 - r;
    }
}
