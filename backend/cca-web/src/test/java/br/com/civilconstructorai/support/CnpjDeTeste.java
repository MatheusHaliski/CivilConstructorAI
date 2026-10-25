package br.com.civilconstructorai.support;

import java.util.concurrent.ThreadLocalRandom;

/** CNPJ válido e aleatório para cada teste (a coluna {@code empresa.cnpj} é única e o banco é compartilhado entre os ITs). */
public final class CnpjDeTeste {

    private CnpjDeTeste() {
    }

    public static String aleatorio() {
        var r = ThreadLocalRandom.current();
        var sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            sb.append(r.nextInt(10));
        }
        sb.append("0001");
        sb.append(digito(sb.toString(), new int[]{5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2}));
        sb.append(digito(sb.toString(), new int[]{6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2}));
        var d = sb.toString();
        return d.substring(0, 2) + "." + d.substring(2, 5) + "." + d.substring(5, 8) + "/" + d.substring(8, 12) + "-" + d.substring(12);
    }

    private static int digito(String d, int[] pesos) {
        int soma = 0;
        for (int i = 0; i < pesos.length; i++) {
            soma += (d.charAt(i) - '0') * pesos[i];
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }
}
