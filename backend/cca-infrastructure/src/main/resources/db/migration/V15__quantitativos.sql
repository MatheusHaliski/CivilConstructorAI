
-- V15 · RF11 levantamento quantitativo determinístico (hash) com liberação ao cliente

create table levantamento_quantitativo (
  id               uuid primary key,
  tenant_id        uuid not null references empresa(id) on delete cascade,
  obra_id          uuid not null references obra(id) on delete cascade,
  versao_modelo    bigint not null,
  hash             text not null,
  estado           text not null check (estado in ('PENDENTE','EM_EXECUCAO','CONCLUIDO','FALHOU','OBSOLETO')),
  linhas_json      text not null,
  totais_json      text not null,
  liberado_cliente boolean not null default false,
  criado_em        timestamptz not null default now(),
  atualizado_em    timestamptz not null default now(),
  versao           bigint not null default 0
);
create index levantamento_quantitativo_obra_idx on levantamento_quantitativo (obra_id, criado_em desc);

call app_aplicar_rls('levantamento_quantitativo');
