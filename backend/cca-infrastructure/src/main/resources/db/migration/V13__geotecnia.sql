
-- V13 · RF09 sondagens SPT: furos (camadas e leituras em JSON) e extrações de boletim por IA

create table furo_sondagem (
  id               uuid primary key,
  tenant_id        uuid not null references empresa(id) on delete cascade,
  obra_id          uuid not null references obra(id) on delete cascade,
  codigo           text not null,
  x                double precision not null,
  y                double precision not null,
  longitude        double precision,
  latitude         double precision,
  cota_boca        double precision not null,
  nivel_agua       double precision,
  camadas_json     text not null default '[]',
  leituras_json    text not null default '[]',
  estado           text not null check (estado in ('RASCUNHO','CONFIRMADO','CANCELADO')),
  boletim_chave_s3 text,
  origem_ia        boolean not null default false,
  criado_em        timestamptz not null default now(),
  atualizado_em    timestamptz not null default now(),
  versao           bigint not null default 0,
  unique (obra_id, codigo)
);

create table extracao_boletim (
  id            uuid primary key,
  tenant_id     uuid not null references empresa(id) on delete cascade,
  obra_id       uuid not null references obra(id) on delete cascade,
  chave_s3      text not null,
  estado        text not null,
  dados_json    text,
  citacoes_json text,
  modelo_ia     text,
  input_tokens  bigint not null default 0,
  output_tokens bigint not null default 0,
  mensagem_erro text,
  decidido_por  uuid references conta(id),
  decidido_em   timestamptz,
  criado_em     timestamptz not null default now(),
  atualizado_em timestamptz not null default now(),
  versao        bigint not null default 0
);
create index extracao_boletim_obra_idx on extracao_boletim (obra_id, criado_em desc);

call app_aplicar_rls('furo_sondagem');
call app_aplicar_rls('extracao_boletim');
