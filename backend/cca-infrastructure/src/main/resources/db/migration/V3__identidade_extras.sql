
-- V3 · Identidade (RF18/RF19): as entidades CodigoVerificacao e CodigoRecuperacaoMfa estendem EntidadeBase
-- (criado_em, atualizado_em, versao); a V1 criou essas tabelas só com criado_em.
alter table codigo_verificacao
  add column atualizado_em timestamptz not null default now(),
  add column versao        bigint      not null default 0;

alter table codigo_recuperacao_mfa
  add column atualizado_em timestamptz not null default now(),
  add column versao        bigint      not null default 0;
