
-- V7 · modelo BIM (RF01, RF02, RF03, RF13, RF16, RF17), pranchas (RF14) e interoperabilidade (RF15).
-- Geometria e propriedades dos elementos em jsonb; cada comando guarda o estado anterior para desfazer e diff.

create table modelo_bim (
  id               uuid primary key,
  tenant_id        uuid not null references empresa(id) on delete cascade,
  obra_id          uuid not null unique references obra(id) on delete cascade,
  versao           bigint not null default 0,
  liberada_cliente boolean not null default false,
  versao_liberada  bigint,
  criado_em        timestamptz not null default now(),
  atualizado_em    timestamptz not null default now()
);

create table pavimento (
  id        uuid primary key,
  tenant_id uuid not null,
  modelo_id uuid not null references modelo_bim(id) on delete cascade,
  nome      text not null,
  elevacao  numeric(8,3) not null,
  tipo      text not null check (tipo in ('FUNDACAO','TERREO','TIPO','COBERTURA','CAIXA_DAGUA')),
  unique (modelo_id, elevacao)
);

create table eixo (
  id          uuid primary key,
  tenant_id   uuid not null,
  modelo_id   uuid not null references modelo_bim(id) on delete cascade,
  nome        text not null,
  direcao     char(1) not null check (direcao in ('X','Y')),
  posicao     numeric(10,3) not null,
  inicio      numeric(10,3) not null,
  fim         numeric(10,3) not null,
  recuo_bolha numeric(6,3) not null default 1.0,
  unique (modelo_id, nome)
);

create table elemento (
  id             uuid primary key,
  tenant_id      uuid not null,
  modelo_id      uuid not null references modelo_bim(id) on delete cascade,
  pavimento_id   uuid not null,
  tipo           text not null,
  camada         text not null check (camada in ('arquitetura','estrutura','fundacao','desenho','anotacao','ia-rascunho')),
  material_id    text,
  origem         text not null check (origem in ('CANVAS','CONSOLE_VI','IA','SISTEMA')),
  rascunho_ia_id uuid,
  rotulo         text,
  geometria      jsonb not null,
  criado_em      timestamptz not null default now(),
  atualizado_em  timestamptz not null default now()
);
create index elemento_modelo_pav_idx on elemento (modelo_id, pavimento_id);
create index elemento_tipo_idx on elemento (modelo_id, tipo);

create table comando_vi (
  id              uuid primary key,
  tenant_id       uuid not null,
  modelo_id       uuid not null references modelo_bim(id) on delete cascade,
  obra_id         uuid not null,
  codigo          text not null,
  vi              text not null,
  args            jsonb not null,
  origem          text not null,
  versao_base     bigint not null,
  versao_nova     bigint not null,
  autor_id        uuid,
  estado_anterior jsonb not null,
  relatorio       jsonb not null,
  criado_em       timestamptz not null default now(),
  unique (modelo_id, versao_nova)
);
create index comando_vi_obra_versao_idx on comando_vi (obra_id, versao_base);

create table ciclo_auditoria (
  id          uuid primary key,
  tenant_id   uuid not null,
  obra_id     uuid not null,
  comando_id  uuid not null,
  estado      text not null check (estado in ('INICIADO','LACO_CODIGO','LACO_GEOMETRIA','CORRIGINDO_COM_LLM','APROVADO','REPROVADO')),
  iteracoes   int not null default 0,
  historico   jsonb not null default '[]'::jsonb,
  iniciado_em timestamptz not null,
  encerrado_em timestamptz
);
create index ciclo_auditoria_comando_idx on ciclo_auditoria (comando_id);

create table alerta_transicao (
  id            uuid primary key,
  tenant_id     uuid not null,
  obra_id       uuid not null,
  elemento_id   uuid not null,
  tipo          text not null,
  estado        text not null check (estado in ('ABERTO','CONFIRMADO','REJEITADO','RESOLVIDO')),
  mensagem      text not null,
  justificativa text,
  criado_em     timestamptz not null default now(),
  unique (obra_id, elemento_id, tipo)
);

create table prancha (
  id             uuid primary key,
  tenant_id      uuid not null,
  obra_id        uuid not null references obra(id) on delete cascade,
  pavimento_id   uuid not null,
  tipo           text not null check (tipo in ('PLANTA_BAIXA','PLANTA_FORMAS','CORTE','ELEVACAO')),
  estado         text not null check (estado in ('EM_GERACAO','GERADA','EMITIDA','OBSOLETA','FALHOU')),
  formato        text not null default 'A1',
  escala         text not null default '1:50',
  versao_modelo  bigint not null,
  chave_svg      text,
  chave_dxf      text,
  erro           text,
  solicitada_por uuid,
  emitida_por    uuid,
  art_rrt        text,
  criada_em      timestamptz not null default now(),
  emitida_em     timestamptz
);
create index prancha_obra_idx on prancha (obra_id, estado);

create table exportacao_modelo (
  id             uuid primary key,
  tenant_id      uuid not null,
  obra_id        uuid not null references obra(id) on delete cascade,
  formato        text not null check (formato in ('IFC4X3','STEP_AP242')),
  estado         text not null check (estado in ('PENDENTE','EM_EXECUCAO','CONCLUIDO','FALHOU','OBSOLETO')),
  versao_modelo  bigint not null,
  chave          text,
  erro           text,
  solicitada_por uuid,
  criada_em      timestamptz not null default now(),
  concluida_em   timestamptz
);
create index exportacao_modelo_obra_idx on exportacao_modelo (obra_id, estado);

create table pareamento_revit (
  id          uuid primary key,
  tenant_id   uuid not null,
  obra_id     uuid not null references obra(id) on delete cascade,
  conta_id    uuid not null,
  estado      text not null check (estado in ('AGUARDANDO_CODIGO','PAREADO','EXPIRADO','REVOGADO')),
  codigo_hash text not null unique,
  token_hash  text unique,
  dispositivo text,
  expira_em   timestamptz not null,
  criado_em   timestamptz not null default now(),
  pareado_em  timestamptz
);

call app_aplicar_rls('modelo_bim');
call app_aplicar_rls('pavimento');
call app_aplicar_rls('eixo');
call app_aplicar_rls('elemento');
call app_aplicar_rls('comando_vi');
call app_aplicar_rls('ciclo_auditoria');
call app_aplicar_rls('alerta_transicao');
call app_aplicar_rls('prancha');
call app_aplicar_rls('exportacao_modelo');
call app_aplicar_rls('pareamento_revit');

-- o pyRevit troca código por token sem sessão de usuário (sem tenant na conexão): leitura pelo hash
create policy pareamento_por_codigo on pareamento_revit for select using (true);
create policy pareamento_update_por_codigo on pareamento_revit for update using (true) with check (true);
