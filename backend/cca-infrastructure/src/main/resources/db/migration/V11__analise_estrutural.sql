
-- V11 · RF05 job de análise estrutural (MEF), versionado pela versão do modelo

create table analise_estrutural (
  id             uuid primary key,
  tenant_id      uuid not null references empresa(id) on delete cascade,
  obra_id        uuid not null references obra(id) on delete cascade,
  versao_modelo  bigint not null,
  estado         text not null check (estado in ('PENDENTE','EM_EXECUCAO','CONCLUIDO','FALHOU','OBSOLETO')),
  solicitada_por uuid not null references conta(id),
  resultado_json text,
  resumo_json    text,
  mensagem_erro  text,
  iniciada_em    timestamptz,
  concluida_em   timestamptz,
  criado_em      timestamptz not null default now(),
  atualizado_em  timestamptz not null default now(),
  versao         bigint not null default 0
);
create index analise_estrutural_obra_idx on analise_estrutural (obra_id, criado_em desc);

call app_aplicar_rls('analise_estrutural');
