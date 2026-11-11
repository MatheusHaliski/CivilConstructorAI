
-- Evidência técnica de dosagem; aprovação nunca vem de boolean do cliente ou LLM.
create table dosagem_cimento (
  id uuid primary key,
  tenant_id uuid not null references empresa(id) on delete cascade,
  obra_id uuid not null references obra(id) on delete cascade,
  versao_modelo bigint not null check (versao_modelo >= 0),
  dados_json text not null,
  contexto_hash text not null,
  estado text not null check (estado in ('PENDENTE','VALIDADO')),
  criado_por uuid not null references conta(id),
  confirmado_por uuid references conta(id),
  confirmado_em timestamptz,
  referencia_validacao text,
  criado_em timestamptz not null default now(),
  atualizado_em timestamptz not null default now(),
  versao bigint not null default 0,
  check (estado <> 'VALIDADO' or (confirmado_por is not null and confirmado_em is not null and length(trim(referencia_validacao)) > 0))
);
create index dosagem_cimento_obra_versao_idx on dosagem_cimento(obra_id,versao_modelo);
call app_aplicar_rls('dosagem_cimento');
