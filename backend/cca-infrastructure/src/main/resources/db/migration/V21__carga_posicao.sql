
-- V21 · RF04 posição da carga pontual ao longo da viga (antes a carga caía no nó do apoio e a viga não fletia)

alter table carga add column posicao_m double precision check (posicao_m is null or posicao_m >= 0);
