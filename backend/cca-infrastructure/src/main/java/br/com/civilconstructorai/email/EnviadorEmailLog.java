package br.com.civilconstructorai.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Perfil {@code it}: guarda os e-mails em memória para os testes lerem o código enviado. */
@Component
@Profile("it")
public class EnviadorEmailLog implements EnviadorEmail {

    private static final Logger log = LoggerFactory.getLogger(EnviadorEmailLog.class);
    private final List<Mensagem> enviados = new CopyOnWriteArrayList<>();

    @Override
    public void enviar(String para, String assunto, String corpoTexto) {
        enviados.add(new Mensagem(para, assunto, corpoTexto));
        log.debug("e-mail para {} · {}", para, assunto);
    }

    public List<Mensagem> enviados() {
        return List.copyOf(enviados);
    }

    public void limpar() {
        enviados.clear();
    }

    public record Mensagem(String para, String assunto, String corpo) {
    }
}
