
-- V23 · conflitos de coordenação por obra (proposta P-COORD-01, auditoria 2026-10): chave estável entre versões,
-- estado (aberto, em revisão, resolvido só por reverificação, aceito com justificativa) e versões do modelo
create table conflito_coordenacao (
  id                uuid primary key,
  tenant_id         uuid not null references empresa(id) on delete cascade,
  obra_id           uuid not null references obra(id) on delete cascade,
  chave             text not null,
  tipo              text not null check (tipo in ('GEOMETRICO','FOLGA','CONECTIVIDADE','FUNCIONAL','CONSTRUTIVO')),
  regra             text not null,
  elementos_json    text not null,
  mensagem          text not null,
  evidencia         text not null,
  estado            text not null check (estado in ('ABERTO','EM_REVISAO','RESOLVIDO','ACEITO')),
  justificativa     text,
  versao_detectada  bigint not null,
  versao_verificada bigint not null,
  decidido_por      uuid references conta(id),
  decidido_em       timestamptz,
  criado_em         timestamptz not null default now(),
  atualizado_em     timestamptz not null default now(),
  versao            bigint not null default 0,
  unique (obra_id, chave)
);
call app_aplicar_rls('conflito_coordenacao');
