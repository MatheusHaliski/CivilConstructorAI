package br.com.civilconstructorai.comum;

import com.github.f4b6a3.uuid.UuidCreator;

import java.util.UUID;

/** Identificadores públicos: UUID v7 (ordenável no tempo). */
public final class Ids {

    private Ids() {
    }

    public static UUID novo() {
        return UuidCreator.getTimeOrderedEpoch();
    }
}
