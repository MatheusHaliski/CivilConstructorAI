
-- V8 · RF27: rascunhos gerados por IA, pedidos de revisão humana (Art. 20) e registro de chamadas ao modelo.

create table rascunho_ia (
  id            uuid primary key,
  tenant_id     uuid not null references empresa(id) on delete cascade,
  obra_id       uuid not null references obra(id) on delete cascade,
  tipo          text not null,                 -- COMANDO_VI, LANCAMENTO_ESTRUTURAL, ORCAMENTO, EXTRACAO_SPT, CORRECAO_AUDITORIA…
  rota          text not null,
  entidade_ref  text,
  conteudo      text not null,                 -- JSON da saída estruturada
  estado        text not null check (estado in ('GERADO_POR_IA','EM_REVISAO','APROVADO','REJEITADO','OBSOLETO','RECUSADO_PELO_MODELO')),
  criado_por    uuid,
  aprovado_por  uuid,
  aprovado_em   timestamptz,
  art_rrt       text,
  motivo        text,
  chamada_ia_id uuid,
  criado_em     timestamptz not null default now(),
  atualizado_em timestamptz not null default now(),
  versao        bigint not null default 0
);
create index rascunho_ia_obra_idx on rascunho_ia (obra_id, estado);
call app_aplicar_rls('rascunho_ia');

create table pedido_revisao_humana (
  id            uuid primary key,
  tenant_id     uuid not null references empresa(id) on delete cascade,
  rascunho_id   uuid not null references rascunho_ia(id) on delete cascade,
  solicitante_id uuid not null,
  motivo        text not null,
  estado        text not null check (estado in ('ABERTO','EM_ANALISE','RESPONDIDO')),
  prazo_em      timestamptz not null,
  resposta      text,
  respondido_por uuid,
  respondido_em timestamptz,
  criado_em     timestamptz not null default now(),
  atualizado_em timestamptz not null default now(),
  versao        bigint not null default 0
);
create index pedido_revisao_humana_rascunho_idx on pedido_revisao_humana (rascunho_id);
call app_aplicar_rls('pedido_revisao_humana');

-- registro por chamada ao modelo: nunca o conteúdo, só metadados (retenção de 2 anos por job)
create table chamada_ia (
  id              uuid primary key,
  tenant_id       uuid not null,
  obra_id         uuid,
  usuario_id      uuid,
  rota            text not null,
  finalidade      text not null,
  base_legal      text not null,
  modelo          text not null,
  effort          text not null,
  input_tokens    bigint not null default 0,
  output_tokens   bigint not null default 0,
  cache_read_input_tokens bigint not null default 0,
  cache_creation_input_tokens bigint not null default 0,
  stop_reason     text,
  recusa          boolean not null default false,
  latencia_ms     bigint not null default 0,
  ocorrido_em     timestamptz not null default now(),
  criado_em       timestamptz not null default now(),
  atualizado_em   timestamptz not null default now(),
  versao          bigint not null default 0
);
create index chamada_ia_tenant_idx on chamada_ia (tenant_id, ocorrido_em desc);
call app_aplicar_rls('chamada_ia');
