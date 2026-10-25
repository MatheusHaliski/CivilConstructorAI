
-- V17 · RF12 orçamento, itens vinculados a composições SINAPI e aditivos aprovados pelo cliente

create table orcamento (
  id                        uuid primary key,
  tenant_id                 uuid not null references empresa(id) on delete cascade,
  obra_id                   uuid not null references obra(id) on delete cascade,
  levantamento_id           uuid references levantamento_quantitativo(id) on delete set null,
  versao_modelo             bigint not null,
  tabela_sinapi_id          uuid not null references tabela_sinapi(id),
  data_base                 text not null,
  estado                    text not null,
  total                     numeric(16,2) not null default 0,
  bdi_pct                   double precision not null default 25,
  itens_gerados_por_ia      integer not null default 0,
  liberado_cliente          boolean not null default false,
  detalhe_liberado_cliente  boolean not null default false,
  aprovado_por              uuid references conta(id),
  decidido_em               timestamptz,
  criado_em                 timestamptz not null default now(),
  atualizado_em             timestamptz not null default now(),
  versao                    bigint not null default 0
);
create index orcamento_obra_idx on orcamento (obra_id, criado_em desc);

create table item_orcamento (
  id               uuid primary key,
  tenant_id        uuid not null references empresa(id) on delete cascade,
  orcamento_id     uuid not null references orcamento(id) on delete cascade,
  etapa            text not null,
  servico          text not null,
  elemento_tipo    text,
  codigo_sinapi    text not null,
  descricao        text not null,
  unidade          text not null,
  quantidade       numeric(14,3) not null,
  custo_unitario   numeric(14,2) not null,
  total            numeric(16,2) not null,
  gerado_por_ia    boolean not null default false,
  confirmado_por   uuid references conta(id),
  justificativa_ia text,
  criado_em        timestamptz not null default now(),
  atualizado_em    timestamptz not null default now(),
  versao           bigint not null default 0
);
create index item_orcamento_orc_idx on item_orcamento (orcamento_id);

create table aditivo (
  id            uuid primary key,
  tenant_id     uuid not null references empresa(id) on delete cascade,
  orcamento_id  uuid not null references orcamento(id) on delete cascade,
  descricao     text not null,
  valor         numeric(16,2) not null,
  estado        text not null check (estado in ('PROPOSTO','APROVADO_CLIENTE','RECUSADO_CLIENTE','CANCELADO')),
  proposto_por  uuid not null references conta(id),
  decidido_por  uuid references conta(id),
  decidido_em   timestamptz,
  criado_em     timestamptz not null default now(),
  atualizado_em timestamptz not null default now(),
  versao        bigint not null default 0
);

call app_aplicar_rls('orcamento');
call app_aplicar_rls('item_orcamento');
call app_aplicar_rls('aditivo');
