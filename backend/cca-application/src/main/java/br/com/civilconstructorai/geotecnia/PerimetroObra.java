package br.com.civilconstructorai.geotecnia;

import java.util.UUID;

/** Porta: o ponto geográfico (SIRGAS 2000, lon/lat) está dentro do perímetro da obra? (PostGIS ST_Contains) */
public interface PerimetroObra {

    /** true quando a obra tem perímetro e o ponto está dentro; {@code null} quando a obra não tem perímetro cadastrado */
    Boolean contem(UUID obraId, double longitude, double latitude);
}
