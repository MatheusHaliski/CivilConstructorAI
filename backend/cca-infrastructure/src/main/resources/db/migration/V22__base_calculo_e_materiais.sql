
-- V22 · base de cálculo por obra (finalidade, sistema construtivo, classe de agressividade, agregado, versão do
-- catálogo e referências adotadas), gravada em cada análise e dimensionamento; materiais personalizados por empresa
-- com situação de validação e histórico (auditoria 2026-10: materiais, conformidade)

create table base_calculo_obra (
  id                   uuid primary key,
  tenant_id            uuid not null references empresa(id) on delete cascade,
  obra_id              uuid not null unique references obra(id) on delete cascade,
  finalidade           text check (finalidade in ('RESIDENCIAL','COMERCIAL','INSTITUCIONAL','INDUSTRIAL','MISTA')),
  sistema_construtivo  text check (sistema_construtivo in ('CONCRETO_ARMADO_MOLDADO_NO_LOCAL','CONCRETO_PRE_MOLDADO','ESTRUTURA_METALICA','ALVENARIA_ESTRUTURAL','MISTO')),
  classe_agressividade text check (classe_agressividade in ('I','II','III','IV')),
  agregado             text check (agregado in ('basalto','diabasio','granito','gnaisse','calcario','arenito')),
  versao_catalogo      text not null,
  referencias_json     text not null default '[]',
  atualizado_por       uuid references conta(id),
  criado_em            timestamptz not null default now(),
  atualizado_em        timestamptz not null default now(),
  versao               bigint not null default 0
);
call app_aplicar_rls('base_calculo_obra');

create table material_personalizado (
  id             uuid primary key,
  tenant_id      uuid not null references empresa(id) on delete cascade,
  codigo         text not null,
  nome           text not null,
  familia        text not null,
  baseado_em     text,
  item_json      text not null,
  situacao       text not null check (situacao in ('VALIDACAO_PENDENTE','VALIDADO','REJEITADO')),
  criado_por     uuid not null references conta(id),
  historico_json text not null default '[]',
  criado_em      timestamptz not null default now(),
  atualizado_em  timestamptz not null default now(),
  versao         bigint not null default 0,
  unique (tenant_id, codigo)
);
call app_aplicar_rls('material_personalizado');

alter table analise_estrutural add column base_calculo_json text;
alter table dimensionamento add column base_calculo_json text;
