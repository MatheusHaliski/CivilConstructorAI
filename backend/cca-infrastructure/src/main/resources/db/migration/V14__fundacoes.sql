
-- V14 · RF10 dimensionamento de fundações e propostas por pilar

create table dimensionamento_fundacao (
  id            uuid primary key,
  tenant_id     uuid not null references empresa(id) on delete cascade,
  obra_id       uuid not null references obra(id) on delete cascade,
  analise_id    uuid not null references analise_estrutural(id) on delete cascade,
  versao_modelo bigint not null,
  estado        text not null,
  solucao_geral text not null default 'INDEFINIDA',
  mensagem_erro text,
  aprovado_por  uuid references conta(id),
  decidido_em   timestamptz,
  criado_em     timestamptz not null default now(),
  atualizado_em timestamptz not null default now(),
  versao        bigint not null default 0
);
create index dimensionamento_fundacao_obra_idx on dimensionamento_fundacao (obra_id, criado_em desc);

create table fundacao_proposta (
  id                  uuid primary key,
  tenant_id           uuid not null references empresa(id) on delete cascade,
  dimensionamento_id  uuid not null references dimensionamento_fundacao(id) on delete cascade,
  obra_id             uuid not null references obra(id) on delete cascade,
  pilar_id            text not null,
  pilar_rotulo        text,
  tipo                text not null check (tipo in ('SAPATA','BLOCO','RADIER')),
  centro_x            double precision not null,
  centro_y            double precision not null,
  bx                  double precision not null,
  by                  double precision not null,
  h                   double precision not null,
  carga_kn            double precision not null,
  estacas             integer,
  diametro_estaca     double precision,
  comprimento_estaca  double precision,
  memoria_json        text not null default '[]',
  verifica            boolean not null default true,
  criado_em           timestamptz not null default now(),
  atualizado_em       timestamptz not null default now(),
  versao              bigint not null default 0
);
create index fundacao_proposta_dim_idx on fundacao_proposta (dimensionamento_id);

call app_aplicar_rls('dimensionamento_fundacao');
call app_aplicar_rls('fundacao_proposta');
