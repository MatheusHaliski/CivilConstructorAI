
-- V5 · RF25: direitos do titular — pedidos, exportação (ZIP cifrado) e retenção configurada pela empresa.

create table pedido_titular (
  id            uuid primary key,
  conta_id      uuid not null references conta(id) on delete cascade,
  tenant_id     uuid references empresa(id) on delete cascade,   -- null = pedido sobre dados da conta (plataforma)
  tipo          text not null check (tipo in ('ACESSO','CORRECAO','EXCLUSAO','PORTABILIDADE','INFORMACAO_COMPARTILHAMENTO','REVOGACAO','OPOSICAO')),
  categoria     text not null,
  descricao     text not null,
  estado        text not null check (estado in ('ABERTO','EM_ANALISE','AGUARDANDO_TITULAR','ATENDIDO','NEGADO','ATRASADO')),
  prazo_em      timestamptz not null,
  resposta      text,
  respondido_por uuid,
  respondido_em timestamptz,
  criado_em     timestamptz not null default now(),
  atualizado_em timestamptz not null default now(),
  versao        bigint not null default 0
);
create index pedido_titular_conta_idx on pedido_titular (conta_id, criado_em desc);
create index pedido_titular_tenant_idx on pedido_titular (tenant_id, estado, prazo_em);
-- sem RLS: o titular lê os próprios pedidos em qualquer empresa; a aplicação filtra por conta_id / tenant_id.

create table exportacao (
  id                 uuid primary key,
  titular_id         uuid not null references conta(id) on delete cascade,
  estado             text not null check (estado in ('PENDENTE','EM_EXECUCAO','CONCLUIDO','FALHOU','OBSOLETO')),
  chave_s3           text,
  senha_cifrada_dek  bytea,                 -- senha do pacote, cifrada com a DEK do titular até a 1ª exibição
  senha_exibida_em   timestamptz,
  hash_senha         text,                  -- SHA-256 da senha (prova de que o servidor não a guarda)
  tamanho_bytes      bigint,
  expira_em          timestamptz,
  erro               text,
  criado_em          timestamptz not null default now(),
  atualizado_em      timestamptz not null default now(),
  versao             bigint not null default 0
);
create index exportacao_titular_idx on exportacao (titular_id, criado_em desc);
create index exportacao_pendente_idx on exportacao (estado) where estado = 'PENDENTE';

create table retencao_empresa (
  id            uuid primary key,
  tenant_id     uuid not null references empresa(id) on delete cascade,
  categoria     text not null,
  prazo_meses   int not null check (prazo_meses >= 0),
  base_legal    text not null,
  criado_em     timestamptz not null default now(),
  atualizado_em timestamptz not null default now(),
  versao        bigint not null default 0,
  unique (tenant_id, categoria)
);
call app_aplicar_rls('retencao_empresa');
