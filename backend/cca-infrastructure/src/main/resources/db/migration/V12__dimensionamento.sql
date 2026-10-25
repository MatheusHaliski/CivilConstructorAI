
-- V12 · RF07 dimensionamento de armaduras (NBR 6118): rascunho até aprovação com ART/RRT

create table dimensionamento (
  id                       uuid primary key,
  tenant_id                uuid not null references empresa(id) on delete cascade,
  obra_id                  uuid not null references obra(id) on delete cascade,
  analise_id               uuid not null references analise_estrutural(id) on delete cascade,
  versao_modelo            bigint not null,
  estado                   text not null,
  resultados_json          text,
  aco_total_kg             double precision not null default 0,
  elementos_nao_verificam  integer not null default 0,
  mensagem_erro            text,
  aprovado_por             uuid references conta(id),
  decidido_em              timestamptz,
  art_rrt                  text,
  criado_em                timestamptz not null default now(),
  atualizado_em            timestamptz not null default now(),
  versao                   bigint not null default 0
);
create index dimensionamento_obra_idx on dimensionamento (obra_id, criado_em desc);

call app_aplicar_rls('dimensionamento');
