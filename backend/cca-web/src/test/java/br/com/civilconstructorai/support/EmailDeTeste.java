package br.com.civilconstructorai.support;

import br.com.civilconstructorai.email.EnviadorEmailLog;

import java.util.regex.Pattern;

/** Lê o último código de 6 dígitos enviado para um e-mail (perfil it guarda os e-mails em memória). */
public final class EmailDeTeste {

    private static final Pattern CODIGO = Pattern.compile("\\b(\\d{6})\\b");
    private static final Pattern LINK_CONVITE = Pattern.compile("/convites/([A-Za-z0-9_-]+)");

    private EmailDeTeste() {
    }

    public static String ultimoCodigo(EnviadorEmailLog log, String email) {
        var msgs = log.enviados().stream().filter(m -> m.para().equalsIgnoreCase(email)).toList();
        for (int i = msgs.size() - 1; i >= 0; i--) {
            var m = CODIGO.matcher(msgs.get(i).corpo());
            if (m.find()) {
                return m.group(1);
            }
        }
        throw new IllegalStateException("nenhum código enviado para " + email);
    }

    public static String ultimoTokenDeConvite(EnviadorEmailLog log, String email) {
        var msgs = log.enviados().stream().filter(m -> m.para().equalsIgnoreCase(email)).toList();
        for (int i = msgs.size() - 1; i >= 0; i--) {
            var m = LINK_CONVITE.matcher(msgs.get(i).corpo());
            if (m.find()) {
                return m.group(1);
            }
        }
        throw new IllegalStateException("nenhum convite enviado para " + email);
    }
}
