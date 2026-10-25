package br.com.civilconstructorai.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * E-mail transacional por SMTP ({@code spring.mail.*}; Mailpit em dev). Texto puro, sem dado pessoal além do
 * destinatário. Falha de envio é registrada sem o corpo (pode conter código) e propagada para quem chamou decidir.
 */
@Component
@Profile("!it")
public class EnviadorEmailSmtp implements EnviadorEmail {

    private static final Logger log = LoggerFactory.getLogger(EnviadorEmailSmtp.class);
    private final JavaMailSender mail;
    private final String remetente;

    public EnviadorEmailSmtp(JavaMailSender mail, @Value("${app.email.remetente:CivilConstructorAI <nao-responda@civilconstructorai.com.br>}") String remetente) {
        this.mail = mail;
        this.remetente = remetente;
    }

    @Override
    public void enviar(String para, String assunto, String corpoTexto) {
        var m = new SimpleMailMessage();
        m.setFrom(remetente);
        m.setTo(para);
        m.setSubject(assunto);
        m.setText(corpoTexto);
        try {
            mail.send(m);
        } catch (MailException e) {
            log.warn("falha ao enviar e-mail '{}': {}", assunto, e.getClass().getSimpleName());
            throw e;
        }
    }
}
