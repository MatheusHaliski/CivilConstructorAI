package br.com.civilconstructorai.notacao;

import java.text.Normalizer;

/**
 * Higieniza identificadores e textos antes do transporte HTTP ao auditor Python (diretrizes §3):
 * remove acentos, converte ordinais (º ª) e descarta caracteres fora do ASCII imprimível (emojis), evitando
 * {@code UnicodeDecodeError} do outro lado. Textos livres (rótulos) mantêm letras e espaços.
 */
public final class Sanitizador {

    private Sanitizador() {
    }

    public static String identificador(String texto) {
        if (texto == null) {
            return "";
        }
        var t = texto.replace('º', 'o').replace('ª', 'a').replace('°', 'o');
        t = Normalizer.normalize(t, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        t = t.replaceAll("[^A-Za-z0-9_\\-]", "_").replaceAll("_+", "_").replaceAll("^_|_$", "");
        return t.isEmpty() ? "ID" : t;
    }

    public static String texto(String texto) {
        if (texto == null) {
            return "";
        }
        var t = texto.replace('º', 'o').replace('ª', 'a').replace('°', 'o');
        t = Normalizer.normalize(t, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return t.replaceAll("[^\\x20-\\x7E]", "").trim();
    }
}
