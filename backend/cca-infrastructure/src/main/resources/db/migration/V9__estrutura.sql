
-- V9 · RF04 lançamento estrutural: cargas, combinações por pavimento e sugestões de lançamento por IA

create table carga (
  id               uuid primary key,
  tenant_id        uuid not null references empresa(id) on delete cascade,
  obra_id          uuid not null references obra(id) on delete cascade,
  pavimento_id     text not null,
  natureza         text not null check (natureza in ('PERMANENTE','ACIDENTAL','VENTO')),
  tipo             text not null check (tipo in ('AREA','LINEAR','PONTUAL')),
  valor            double precision not null check (valor > 0),
  elemento_alvo_id text,
  descricao        text not null,
  caso_carga       text not null,
  criado_em        timestamptz not null default now(),
  atualizado_em    timestamptz not null default now(),
  versao           bigint not null default 0
);
create index carga_obra_idx on carga (obra_id, pavimento_id);

create table lancamento_estrutural (
  id               uuid primary key,
  tenant_id        uuid not null references empresa(id) on delete cascade,
  obra_id          uuid not null references obra(id) on delete cascade,
  pavimento_id     text not null,
  estado           text not null check (estado in ('VAZIO','SUGESTAO_IA','EM_LANCAMENTO','CARREGADO','PRONTO_PARA_ANALISE')),
  combinacoes_json text not null default '[]',
  vento_json       text,
  criado_em        timestamptz not null default now(),
  atualizado_em    timestamptz not null default now(),
  versao           bigint not null default 0,
  unique (obra_id, pavimento_id)
);

create table rascunho_lancamento (
  id              uuid primary key,
  tenant_id       uuid not null references empresa(id) on delete cascade,
  obra_id         uuid not null references obra(id) on delete cascade,
  pavimento_id    text not null,
  estado          text not null,
  conteudo_json   text not null,
  modelo_ia       text not null,
  input_tokens    bigint not null default 0,
  output_tokens   bigint not null default 0,
  stop_reason     text not null,
  aprovado_por    uuid references conta(id),
  decidido_em     timestamptz,
  motivo_rejeicao text,
  criado_em       timestamptz not null default now(),
  atualizado_em   timestamptz not null default now(),
  versao          bigint not null default 0
);
create index rascunho_lancamento_obra_idx on rascunho_lancamento (obra_id, criado_em desc);

call app_aplicar_rls('carga');
call app_aplicar_rls('lancamento_estrutural');
call app_aplicar_rls('rascunho_lancamento');
