package br.com.civilconstructorai.email;

/** Porta de e-mail transacional (código de verificação, convite, alerta de login, exportação pronta). */
public interface EnviadorEmail {

    void enviar(String para, String assunto, String corpoTexto);
}
