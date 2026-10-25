package br.com.civilconstructorai.comum;

import java.time.Clock;
import java.time.Instant;

/** Relógio injetável: testes controlam o tempo (carência de 30 dias, expiração de convite, sudo de 10 min). */
public interface Relogio {

    Instant agora();

    static Relogio sistema() {
        return Clock.systemUTC()::instant;
    }
}
