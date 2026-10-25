
-- V16 · RF12 tabelas SINAPI versionadas (globais, sem tenant) com semente mínima

create table tabela_sinapi (
  id             uuid primary key,
  uf             varchar(2) not null,
  mes_referencia date not null,
  desonerado     boolean not null,
  composicoes    integer not null default 0,
  criado_em      timestamptz not null default now(),
  atualizado_em  timestamptz not null default now(),
  versao         bigint not null default 0,
  unique (uf, mes_referencia, desonerado)
);

create table composicao_sinapi (
  id             uuid primary key,
  tabela_id      uuid not null references tabela_sinapi(id) on delete cascade,
  codigo         text not null,
  descricao      text not null,
  unidade        text not null,
  custo_unitario numeric(14,2) not null,
  grupo          text not null default '',
  criado_em      timestamptz not null default now(),
  atualizado_em  timestamptz not null default now(),
  versao         bigint not null default 0,
  unique (tabela_id, codigo)
);
create index composicao_sinapi_descricao_idx on composicao_sinapi (tabela_id, lower(descricao));

insert into tabela_sinapi (id, uf, mes_referencia, desonerado, composicoes)
values ('019a0000-0000-7000-8000-000000000001', 'PR', date '2026-09-01', false, 10);

insert into composicao_sinapi (id, tabela_id, codigo, descricao, unidade, custo_unitario, grupo) values
 (gen_random_uuid(), '019a0000-0000-7000-8000-000000000001', '94965', 'Concreto fck = 30 MPa, usinado, bombeado, lançado e adensado', 'm3', 450.00, 'ESTRUTURA'),
 (gen_random_uuid(), '019a0000-0000-7000-8000-000000000001', '94964', 'Concreto fck = 25 MPa, usinado, bombeado, lançado e adensado', 'm3', 410.00, 'ESTRUTURA'),
 (gen_random_uuid(), '019a0000-0000-7000-8000-000000000001', '92779', 'Armação de aço CA-50, corte, dobra e montagem', 'kg', 9.80, 'ESTRUTURA'),
 (gen_random_uuid(), '019a0000-0000-7000-8000-000000000001', '92267', 'Fôrma de madeira para vigas e pilares, 4 reaproveitamentos', 'm2', 68.50, 'ESTRUTURA'),
 (gen_random_uuid(), '019a0000-0000-7000-8000-000000000001', '92480', 'Fôrma de madeira para lajes, 4 reaproveitamentos', 'm2', 54.20, 'ESTRUTURA'),
 (gen_random_uuid(), '019a0000-0000-7000-8000-000000000001', '103323', 'Alvenaria de vedação de blocos cerâmicos 14x19x39 cm', 'm2', 98.00, 'VEDACOES'),
 (gen_random_uuid(), '019a0000-0000-7000-8000-000000000001', '103355', 'Alvenaria estrutural de blocos de concreto 19x19x39 cm', 'm2', 125.00, 'VEDACOES'),
 (gen_random_uuid(), '019a0000-0000-7000-8000-000000000001', '100777', 'Estrutura metálica em perfis laminados W', 'kg', 14.20, 'ESTRUTURA'),
 (gen_random_uuid(), '019a0000-0000-7000-8000-000000000001', '102180', 'Vidro temperado incolor 8 mm, instalado', 'm2', 420.00, 'ESQUADRIAS'),
 (gen_random_uuid(), '019a0000-0000-7000-8000-000000000001', '90843', 'Porta de madeira semi-oca 80x210 cm, completa', 'un', 650.00, 'ESQUADRIAS');
