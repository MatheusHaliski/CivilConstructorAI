package br.com.civilconstructorai.identidade;

import br.com.civilconstructorai.comum.MaquinaDeEstados;

import java.util.Map;
import java.util.Set;

/** Selo profissional (CREA/CAU/CNPJ) — RF21. Trocar nome, registro ou CNPJ de conta verificada volta a PENDING. */
public enum EstadoSelo {
    NONE, PENDING, VERIFIED, REJECTED;

    public static final MaquinaDeEstados<EstadoSelo> MAQUINA = new MaquinaDeEstados<>("Selo profissional", Map.of(
            NONE, Set.of(PENDING),
            PENDING, Set.of(VERIFIED, REJECTED),
            VERIFIED, Set.of(PENDING),
            REJECTED, Set.of(PENDING)));
}
