
-- V10 · RF08 tolerâncias geométricas, seções mínimas e exceções normativas por obra

create table restricoes_projeto (
  id                    uuid primary key,
  tenant_id             uuid not null references empresa(id) on delete cascade,
  obra_id               uuid not null unique references obra(id) on delete cascade,
  short_curve_tolerance double precision not null default 0.05,
  pilar_lado_min        double precision not null default 0.19,
  pilar_area_min        double precision not null default 0.036,
  viga_b_min            double precision not null default 0.12,
  laje_h_min            double precision not null default 0.08,
  parede_esp_min        double precision not null default 0.05,
  criado_em             timestamptz not null default now(),
  atualizado_em         timestamptz not null default now(),
  versao                bigint not null default 0
);

create table excecao_normativa (
  id               uuid primary key,
  tenant_id        uuid not null references empresa(id) on delete cascade,
  obra_id          uuid not null references obra(id) on delete cascade,
  regra            text not null,
  elemento_id      text,
  valor_solicitado double precision not null,
  justificativa    text not null,
  estado           text not null check (estado in ('SOLICITADA','ATIVA','RECUSADA','REVOGADA','OBSOLETA')),
  solicitada_por   uuid not null references conta(id),
  decidida_por     uuid references conta(id),
  decidida_em      timestamptz,
  criado_em        timestamptz not null default now(),
  atualizado_em    timestamptz not null default now(),
  versao           bigint not null default 0
);
create index excecao_normativa_obra_idx on excecao_normativa (obra_id, estado);

call app_aplicar_rls('restricoes_projeto');
call app_aplicar_rls('excecao_normativa');
