package br.com.civilconstructorai.modelo;

import br.com.civilconstructorai.modelo.Enums.OrigemComando;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Um comando da Notação VI aplicado (ou tentado) sobre o modelo. */
public record ComandoVi(UUID id, String codigo, Map<String, Object> args, OrigemComando origem, long versaoBase, UUID autorId, Instant criadoEm) {
}
