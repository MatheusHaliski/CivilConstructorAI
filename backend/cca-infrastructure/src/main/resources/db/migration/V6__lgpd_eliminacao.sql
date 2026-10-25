
-- V6 · RF26: saga idempotente de eliminação, retenção legal, recibo e cofre de chaves local (KEK por usuário).
-- Em produção o cofre é o KMS sa-east-1 + armazenamento separado com snapshots de 7 dias e tombstones;
-- aqui as duas tabelas vivem no mesmo banco só para dev/it e ficam fora do backup lógico (ver ADR/RF26).

create table saga_eliminacao (
  id            uuid primary key,
  conta_id      uuid not null,
  conta_hash    text not null,
  estado        text not null check (estado in ('INICIADA','EM_EXECUCAO','FALHOU_PASSO','CONCLUIDA')),
  passo_atual   int not null default 1 check (passo_atual between 1 and 7),
  tentativas    int not null default 0,
  erro          text,
  chaves_s3     text not null default '[]',
  iniciada_em   timestamptz not null default now(),
  concluida_em  timestamptz,
  criado_em     timestamptz not null default now(),
  atualizado_em timestamptz not null default now(),
  versao        bigint not null default 0,
  unique (conta_id)
);

create table retencao_legal (
  id            uuid primary key,
  saga_id       uuid not null references saga_eliminacao(id) on delete cascade,
  entidade      text not null,
  registro_id   uuid not null,
  base_legal    text not null,
  prazo_ate     timestamptz,
  dados         text,              -- só o mínimo que a lei obriga (ex.: nome + CREA do responsável técnico)
  criado_em     timestamptz not null default now(),
  atualizado_em timestamptz not null default now(),
  versao        bigint not null default 0
);
create index retencao_legal_saga_idx on retencao_legal (saga_id);

create table recibo_eliminacao (
  id            uuid primary key,
  saga_id       uuid not null references saga_eliminacao(id) on delete cascade,
  conta_hash    text not null,
  passos        text not null,
  bases_legais  text not null,
  criado_em     timestamptz not null default now(),
  atualizado_em timestamptz not null default now(),
  versao        bigint not null default 0
);

-- cofre de chaves (local): KEK por usuário cifrada pela chave-mestra (HKDF do segredo do app em dev/it; KMS em prod)
create table cofre_kek (
  id            uuid primary key,
  usuario_id    uuid not null,
  kek_cifrada   bytea not null,
  criado_em     timestamptz not null default now()
);
create table kek_revogada (
  kek_id        uuid primary key,
  revogada_em   timestamptz not null default now()
);
