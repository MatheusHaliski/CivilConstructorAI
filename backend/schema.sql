-- V1__contas_empresas_obras.sql

-- V1 · contas, empresas, obras, membros, termos, consentimentos, preferências e chave de dados por usuário
-- RFs: RF18, RF19, RF21, RF22, RF23, RF24, RF26. Isolamento por tenant: coluna tenant_id + RLS em V2.

create extension if not exists postgis;
create extension if not exists pgcrypto;

-- papel de aplicação (RLS vale para ele; o papel de migração é dono das tabelas)
do $$
begin
  if not exists (select 1 from pg_roles where rolname = 'cca_app') then
    create role cca_app login password 'cca_app';
  end if;
end $$;

create table conta (
  id              uuid primary key,
  email           citext not null unique,
  email_normalizado text generated always as (lower(email::text)) stored,
  senha_hash      text not null,
  nome            text not null,
  tipo            text not null check (tipo in ('PESSOA','EMPRESA')),
  estado          text not null check (estado in ('PENDING_VERIFICATION','ACTIVE','SUSPENDED','PENDING_DELETION','ANONYMOUS_LGPD_DELETED')),
  data_nascimento date,
  cpf_hash        text,
  cnpj            text,
  registro_profissional text,           -- CREA/CAU
  registro_uf     varchar(2),
  selo_tipo       text check (selo_tipo in ('CREA','CAU','CNPJ')),
  selo_estado     text not null default 'NONE' check (selo_estado in ('NONE','PENDING','VERIFIED','REJECTED')),
  selo_verificado_em timestamptz,
  mfa_ativo       boolean not null default false,
  mfa_segredo_cifrado bytea,
  mfa_obrigatorio boolean not null default false,
  eliminacao_prevista_em timestamptz,
  ultimo_login_em timestamptz,
  criado_em       timestamptz not null default now(),
  atualizado_em   timestamptz not null default now(),
  versao          bigint not null default 0
);
create index conta_estado_idx on conta (estado);
create index conta_eliminacao_idx on conta (eliminacao_prevista_em) where estado = 'PENDING_DELETION';

create table codigo_verificacao (
  id          uuid primary key,
  conta_id    uuid not null references conta(id) on delete cascade,
  finalidade  text not null check (finalidade in ('EMAIL','RECUPERACAO_SENHA','SUDO')),
  codigo_hash text not null,
  expira_em   timestamptz not null,
  tentativas  int not null default 0,
  usado_em    timestamptz,
  criado_em   timestamptz not null default now()
);
create index codigo_verificacao_conta_idx on codigo_verificacao (conta_id, finalidade);

create table codigo_recuperacao_mfa (
  id         uuid primary key,
  conta_id   uuid not null references conta(id) on delete cascade,
  codigo_hash text not null,
  usado_em   timestamptz,
  criado_em  timestamptz not null default now()
);
create index codigo_recuperacao_mfa_conta_idx on codigo_recuperacao_mfa (conta_id);

-- DEK por usuário, cifrada por uma KEK que mora no cofre de chaves (fora deste banco) — RF26 crypto-shredding
create table user_key (
  usuario_id   uuid primary key references conta(id) on delete cascade,
  kek_id       uuid not null,
  versao_chave int not null default 1,
  dek_cifrada_pela_kek bytea not null,
  criado_em    timestamptz not null default now()
);

create table empresa (
  id            uuid primary key,
  tenant_id     uuid not null,                    -- = id (tenant raiz); mantido para a política de RLS uniforme
  razao_social  text not null,
  nome_fantasia text,
  cnpj          text not null unique,
  cnpj_verificado boolean not null default false,
  encarregado_contato text,
  criado_em     timestamptz not null default now(),
  atualizado_em timestamptz not null default now(),
  versao        bigint not null default 0,
  check (tenant_id = id)
);

create table membro_empresa (
  id         uuid primary key,
  tenant_id  uuid not null references empresa(id) on delete cascade,
  conta_id   uuid not null references conta(id) on delete cascade,
  papel      text not null check (papel in ('DONO','ADMIN')),
  criado_em  timestamptz not null default now(),
  atualizado_em timestamptz not null default now(),
  versao     bigint not null default 0,
  unique (tenant_id, conta_id)
);

create table obra (
  id          uuid primary key,
  tenant_id   uuid not null references empresa(id) on delete cascade,
  nome        text not null,
  endereco    text,
  municipio   text,
  uf          varchar(2),
  perimetro   geometry(Polygon, 4674),            -- SIRGAS 2000
  localizacao geometry(Point, 4674),
  estado      text not null check (estado in ('PLANEJADA','EM_ANDAMENTO','PARALISADA','CONCLUIDA','ARQUIVADA')),
  art_rrt     text,
  responsavel_tecnico_id uuid references conta(id),
  criado_em   timestamptz not null default now(),
  atualizado_em timestamptz not null default now(),
  versao      bigint not null default 0
);
create index obra_tenant_idx on obra (tenant_id);
create index obra_perimetro_gix on obra using gist (perimetro);

create table membro_obra (
  id         uuid primary key,
  tenant_id  uuid not null references empresa(id) on delete cascade,
  obra_id    uuid not null references obra(id) on delete cascade,
  conta_id   uuid not null references conta(id) on delete cascade,
  papel      text not null check (papel in ('ENGENHEIRO','ARQUITETO','ORCAMENTISTA','MESTRE_OBRAS','TECNICO_SEGURANCA','CLIENTE','FORNECEDOR','TRABALHADOR')),
  criado_em  timestamptz not null default now(),
  atualizado_em timestamptz not null default now(),
  versao     bigint not null default 0,
  unique (obra_id, conta_id, papel)
);
create index membro_obra_conta_idx on membro_obra (conta_id);

create table convite (
  id             uuid primary key,
  tenant_id      uuid not null references empresa(id) on delete cascade,
  obra_id        uuid references obra(id) on delete cascade,
  email          citext not null,
  papel          text not null,
  token_hash     text not null unique,
  convidado_por  uuid not null references conta(id),
  estado         text not null check (estado in ('PENDENTE','ACEITO','RECUSADO','EXPIRADO','REVOGADO')),
  expira_em      timestamptz not null,
  aceito_por     uuid references conta(id),
  aceito_em      timestamptz,
  criado_em      timestamptz not null default now(),
  atualizado_em  timestamptz not null default now(),
  versao         bigint not null default 0
);
create index convite_tenant_idx on convite (tenant_id, estado);

create table documento_legal (
  id        uuid primary key,
  tipo      text not null check (tipo in ('TERMOS','POLITICA')),
  versao    text not null,
  vigente_desde timestamptz not null,
  url       text not null,
  unique (tipo, versao)
);

create table aceite_termos (
  id         uuid primary key,
  conta_id   uuid not null references conta(id) on delete cascade,
  tipo       text not null check (tipo in ('TERMOS','POLITICA')),
  versao     text not null,
  aceito_em  timestamptz not null default now(),
  ip_hash    text not null,
  user_agent text
);
create index aceite_termos_conta_idx on aceite_termos (conta_id, tipo);

create table consentimento_atual (
  conta_id    uuid not null references conta(id) on delete cascade,
  finalidade  text not null check (finalidade in ('LOCALIZACAO_CANTEIRO','FOTOS_TREINAMENTO','MARKETING')),
  estado      text not null check (estado in ('NAO_CONCEDIDO','CONCEDIDO','REVOGADO','A_CONFIRMAR','ENCERRADO')),
  versao_texto text not null,
  atualizado_em timestamptz not null default now(),
  primary key (conta_id, finalidade)
);

create table consent_event (
  id          uuid primary key,
  conta_id    uuid not null references conta(id) on delete cascade,
  finalidade  text not null,
  acao        text not null check (acao in ('CONCEDER','REVOGAR','CONFIRMAR','ENCERRAR')),
  versao_texto text not null,
  ocorrido_em timestamptz not null default now(),
  ip_hash     text not null
);
create index consent_event_conta_idx on consent_event (conta_id, ocorrido_em);

create table preferencia (
  conta_id   uuid primary key references conta(id) on delete cascade,
  idioma     text not null default 'pt-BR',
  tema       text not null default 'claro',
  fonte_pct  int not null default 100 check (fonte_pct between 85 and 150),
  motor      jsonb not null default '{}'::jsonb,   -- ConfiguracaoMotor (RF28)
  atualizado_em timestamptz not null default now()
);

create table registro_operacao (
  id          uuid primary key,
  tipo        text not null,
  autor_id    uuid,
  tenant_id   uuid,
  obra_id     uuid,
  entidade    text,
  entidade_id uuid,
  atributos   jsonb not null default '{}'::jsonb,
  ocorrido_em timestamptz not null default now()
);
create index registro_operacao_tenant_idx on registro_operacao (tenant_id, ocorrido_em desc);
-- V2__rls.sql

-- V2 · Row-Level Security por tenant (RF21). A aplicação define app.tenant_id a cada transação
-- (TenantJpaTransactionManager). app.sistema = on libera jobs internos (saga de eliminação, importação SINAPI).

create or replace function app_tenant_atual() returns uuid
language sql stable as $$
  select nullif(current_setting('app.tenant_id', true), '')::uuid
$$;

create or replace function app_eh_sistema() returns boolean
language sql stable as $$
  select coalesce(current_setting('app.sistema', true), 'off') = 'on'
$$;

-- aplica a política padrão numa tabela que tenha coluna tenant_id
create or replace procedure app_aplicar_rls(tabela regclass)
language plpgsql as $$
begin
  execute format('alter table %s enable row level security', tabela);
  execute format('alter table %s force row level security', tabela);
  execute format('drop policy if exists tenant_isolamento on %s', tabela);
  execute format($p$create policy tenant_isolamento on %s
      using (app_eh_sistema() or tenant_id = app_tenant_atual())
      with check (app_eh_sistema() or tenant_id = app_tenant_atual())$p$, tabela);
end $$;

call app_aplicar_rls('empresa');
call app_aplicar_rls('membro_empresa');
call app_aplicar_rls('obra');
call app_aplicar_rls('membro_obra');
call app_aplicar_rls('convite');

-- membro_empresa/membro_obra também precisam ser lidos ANTES de haver empresa ativa (login: listar empresas da
-- pessoa). Política extra: a própria pessoa enxerga os vínculos dela.
create or replace function app_conta_atual() returns uuid
language sql stable as $$
  select nullif(current_setting('app.conta_id', true), '')::uuid
$$;

create policy proprio_vinculo on membro_empresa for select using (conta_id = app_conta_atual());
create policy proprio_vinculo on membro_obra for select using (conta_id = app_conta_atual());
create policy propria_empresa on empresa for select
  using (exists (select 1 from membro_empresa m where m.tenant_id = empresa.id and m.conta_id = app_conta_atual())
      or exists (select 1 from membro_obra m where m.tenant_id = empresa.id and m.conta_id = app_conta_atual()));

grant usage on schema public to cca_app;
grant select, insert, update, delete on all tables in schema public to cca_app;
alter default privileges in schema public grant select, insert, update, delete on tables to cca_app;
-- V3__identidade_extras.sql

-- V3 · Identidade (RF18/RF19): as entidades CodigoVerificacao e CodigoRecuperacaoMfa estendem EntidadeBase
-- (criado_em, atualizado_em, versao); a V1 criou essas tabelas só com criado_em.
alter table codigo_verificacao
  add column atualizado_em timestamptz not null default now(),
  add column versao        bigint      not null default 0;

alter table codigo_recuperacao_mfa
  add column atualizado_em timestamptz not null default now(),
  add column versao        bigint      not null default 0;
-- V4__lgpd_consentimentos.sql

-- V4 · RF24: textos de finalidade versionados (toggle de consentimento), documentos legais vigentes.
-- consentimento_atual, consent_event, aceite_termos e documento_legal já existem na V1.

create table finalidade_texto (
  finalidade   text primary key check (finalidade in ('LOCALIZACAO_CANTEIRO','FOTOS_TREINAMENTO','MARKETING')),
  versao_texto text not null,
  titulo       text not null,
  descricao    text not null,
  base_legal   text not null,
  atualizado_em timestamptz not null default now()
);

insert into finalidade_texto (finalidade, versao_texto, titulo, descricao, base_legal) values
  ('LOCALIZACAO_CANTEIRO', '1.0', 'Localização do aparelho no canteiro',
   'Usar a localização do seu aparelho enquanto o app está aberto, para registrar presença e fotos no perímetro da obra.',
   'Art. 7º, I'),
  ('FOTOS_TREINAMENTO', '1.0', 'Uso de fotos para treinar classificadores internos',
   'Usar fotos de canteiro que você enviar para treinar classificadores internos de segurança. Fotos com pessoas identificáveis são excluídas.',
   'Art. 7º, I · Art. 11'),
  ('MARKETING', '1.0', 'Comunicação de marketing',
   'Receber novidades do produto por e-mail. Nunca compartilhamos seu contato com terceiros.',
   'Art. 7º, I');

insert into documento_legal (id, tipo, versao, vigente_desde, url) values
  ('00000000-0000-7000-8000-000000000001', 'TERMOS',   '1.0', now(), 'https://civilconstructorai.com.br/legal/termos/1.0'),
  ('00000000-0000-7000-8000-000000000002', 'POLITICA', '1.0', now(), 'https://civilconstructorai.com.br/legal/politica/1.0')
on conflict (tipo, versao) do nothing;

create index consent_event_finalidade_idx on consent_event (finalidade, ocorrido_em desc);
-- V5__lgpd_titular.sql

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
-- V6__lgpd_eliminacao.sql

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
-- V7__modelo_bim.sql

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
-- V8__rascunho_ia.sql

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
-- V9__estrutura.sql

-- V9 · RF04 lançamento estrutural: cargas, combinações por pavimento e sugestões de lançamento por IA

create table carga (
  id               uuid primary key,
  tenant_id        uuid not null references empresa(id) on delete cascade,
  obra_id          uuid not null references obra(id) on delete cascade,
  pavimento_id     text not null,
  natureza         text not null check (natureza in ('PERMANENTE','ACIDENTAL','VENTO')),
  tipo             text not null check (tipo in ('AREA','LINEAR','PONTUAL')),
  valor            double precision not null check (valor > 0),
  elemento_alvo_id text,
  descricao        text not null,
  caso_carga       text not null,
  criado_em        timestamptz not null default now(),
  atualizado_em    timestamptz not null default now(),
  versao           bigint not null default 0
);
create index carga_obra_idx on carga (obra_id, pavimento_id);

create table lancamento_estrutural (
  id               uuid primary key,
  tenant_id        uuid not null references empresa(id) on delete cascade,
  obra_id          uuid not null references obra(id) on delete cascade,
  pavimento_id     text not null,
  estado           text not null check (estado in ('VAZIO','SUGESTAO_IA','EM_LANCAMENTO','CARREGADO','PRONTO_PARA_ANALISE')),
  combinacoes_json text not null default '[]',
  vento_json       text,
  criado_em        timestamptz not null default now(),
  atualizado_em    timestamptz not null default now(),
  versao           bigint not null default 0,
  unique (obra_id, pavimento_id)
);

create table rascunho_lancamento (
  id              uuid primary key,
  tenant_id       uuid not null references empresa(id) on delete cascade,
  obra_id         uuid not null references obra(id) on delete cascade,
  pavimento_id    text not null,
  estado          text not null,
  conteudo_json   text not null,
  modelo_ia       text not null,
  input_tokens    bigint not null default 0,
  output_tokens   bigint not null default 0,
  stop_reason     text not null,
  aprovado_por    uuid references conta(id),
  decidido_em     timestamptz,
  motivo_rejeicao text,
  criado_em       timestamptz not null default now(),
  atualizado_em   timestamptz not null default now(),
  versao          bigint not null default 0
);
create index rascunho_lancamento_obra_idx on rascunho_lancamento (obra_id, criado_em desc);

call app_aplicar_rls('carga');
call app_aplicar_rls('lancamento_estrutural');
call app_aplicar_rls('rascunho_lancamento');
-- V10__restricoes_projeto.sql

-- V10 · RF08 tolerâncias geométricas, seções mínimas e exceções normativas por obra

create table restricoes_projeto (
  id                    uuid primary key,
  tenant_id             uuid not null references empresa(id) on delete cascade,
  obra_id               uuid not null unique references obra(id) on delete cascade,
  short_curve_tolerance double precision not null default 0.05,
  pilar_lado_min        double precision not null default 0.19,
  pilar_area_min        double precision not null default 0.036,
  viga_b_min            double precision not null default 0.12,
  laje_h_min            double precision not null default 0.08,
  parede_esp_min        double precision not null default 0.05,
  criado_em             timestamptz not null default now(),
  atualizado_em         timestamptz not null default now(),
  versao                bigint not null default 0
);

create table excecao_normativa (
  id               uuid primary key,
  tenant_id        uuid not null references empresa(id) on delete cascade,
  obra_id          uuid not null references obra(id) on delete cascade,
  regra            text not null,
  elemento_id      text,
  valor_solicitado double precision not null,
  justificativa    text not null,
  estado           text not null check (estado in ('SOLICITADA','ATIVA','RECUSADA','REVOGADA','OBSOLETA')),
  solicitada_por   uuid not null references conta(id),
  decidida_por     uuid references conta(id),
  decidida_em      timestamptz,
  criado_em        timestamptz not null default now(),
  atualizado_em    timestamptz not null default now(),
  versao           bigint not null default 0
);
create index excecao_normativa_obra_idx on excecao_normativa (obra_id, estado);

call app_aplicar_rls('restricoes_projeto');
call app_aplicar_rls('excecao_normativa');
-- V11__analise_estrutural.sql

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
-- V12__dimensionamento.sql

-- V12 · RF07 dimensionamento de armaduras (NBR 6118): rascunho até aprovação com ART/RRT

create table dimensionamento (
  id                       uuid primary key,
  tenant_id                uuid not null references empresa(id) on delete cascade,
  obra_id                  uuid not null references obra(id) on delete cascade,
  analise_id               uuid not null references analise_estrutural(id) on delete cascade,
  versao_modelo            bigint not null,
  estado                   text not null,
  resultados_json          text,
  aco_total_kg             double precision not null default 0,
  elementos_nao_verificam  integer not null default 0,
  mensagem_erro            text,
  aprovado_por             uuid references conta(id),
  decidido_em              timestamptz,
  art_rrt                  text,
  criado_em                timestamptz not null default now(),
  atualizado_em            timestamptz not null default now(),
  versao                   bigint not null default 0
);
create index dimensionamento_obra_idx on dimensionamento (obra_id, criado_em desc);

call app_aplicar_rls('dimensionamento');
-- V13__geotecnia.sql

-- V13 · RF09 sondagens SPT: furos (camadas e leituras em JSON) e extrações de boletim por IA

create table furo_sondagem (
  id               uuid primary key,
  tenant_id        uuid not null references empresa(id) on delete cascade,
  obra_id          uuid not null references obra(id) on delete cascade,
  codigo           text not null,
  x                double precision not null,
  y                double precision not null,
  longitude        double precision,
  latitude         double precision,
  cota_boca        double precision not null,
  nivel_agua       double precision,
  camadas_json     text not null default '[]',
  leituras_json    text not null default '[]',
  estado           text not null check (estado in ('RASCUNHO','CONFIRMADO','CANCELADO')),
  boletim_chave_s3 text,
  origem_ia        boolean not null default false,
  criado_em        timestamptz not null default now(),
  atualizado_em    timestamptz not null default now(),
  versao           bigint not null default 0,
  unique (obra_id, codigo)
);

create table extracao_boletim (
  id            uuid primary key,
  tenant_id     uuid not null references empresa(id) on delete cascade,
  obra_id       uuid not null references obra(id) on delete cascade,
  chave_s3      text not null,
  estado        text not null,
  dados_json    text,
  citacoes_json text,
  modelo_ia     text,
  input_tokens  bigint not null default 0,
  output_tokens bigint not null default 0,
  mensagem_erro text,
  decidido_por  uuid references conta(id),
  decidido_em   timestamptz,
  criado_em     timestamptz not null default now(),
  atualizado_em timestamptz not null default now(),
  versao        bigint not null default 0
);
create index extracao_boletim_obra_idx on extracao_boletim (obra_id, criado_em desc);

call app_aplicar_rls('furo_sondagem');
call app_aplicar_rls('extracao_boletim');
-- V14__fundacoes.sql

-- V14 · RF10 dimensionamento de fundações e propostas por pilar

create table dimensionamento_fundacao (
  id            uuid primary key,
  tenant_id     uuid not null references empresa(id) on delete cascade,
  obra_id       uuid not null references obra(id) on delete cascade,
  analise_id    uuid not null references analise_estrutural(id) on delete cascade,
  versao_modelo bigint not null,
  estado        text not null,
  solucao_geral text not null default 'INDEFINIDA',
  mensagem_erro text,
  aprovado_por  uuid references conta(id),
  decidido_em   timestamptz,
  criado_em     timestamptz not null default now(),
  atualizado_em timestamptz not null default now(),
  versao        bigint not null default 0
);
create index dimensionamento_fundacao_obra_idx on dimensionamento_fundacao (obra_id, criado_em desc);

create table fundacao_proposta (
  id                  uuid primary key,
  tenant_id           uuid not null references empresa(id) on delete cascade,
  dimensionamento_id  uuid not null references dimensionamento_fundacao(id) on delete cascade,
  obra_id             uuid not null references obra(id) on delete cascade,
  pilar_id            text not null,
  pilar_rotulo        text,
  tipo                text not null check (tipo in ('SAPATA','BLOCO','RADIER')),
  centro_x            double precision not null,
  centro_y            double precision not null,
  bx                  double precision not null,
  by                  double precision not null,
  h                   double precision not null,
  carga_kn            double precision not null,
  estacas             integer,
  diametro_estaca     double precision,
  comprimento_estaca  double precision,
  memoria_json        text not null default '[]',
  verifica            boolean not null default true,
  criado_em           timestamptz not null default now(),
  atualizado_em       timestamptz not null default now(),
  versao              bigint not null default 0
);
create index fundacao_proposta_dim_idx on fundacao_proposta (dimensionamento_id);

call app_aplicar_rls('dimensionamento_fundacao');
call app_aplicar_rls('fundacao_proposta');
-- V15__quantitativos.sql

-- V15 · RF11 levantamento quantitativo determinístico (hash) com liberação ao cliente

create table levantamento_quantitativo (
  id               uuid primary key,
  tenant_id        uuid not null references empresa(id) on delete cascade,
  obra_id          uuid not null references obra(id) on delete cascade,
  versao_modelo    bigint not null,
  hash             text not null,
  estado           text not null check (estado in ('PENDENTE','EM_EXECUCAO','CONCLUIDO','FALHOU','OBSOLETO')),
  linhas_json      text not null,
  totais_json      text not null,
  liberado_cliente boolean not null default false,
  criado_em        timestamptz not null default now(),
  atualizado_em    timestamptz not null default now(),
  versao           bigint not null default 0
);
create index levantamento_quantitativo_obra_idx on levantamento_quantitativo (obra_id, criado_em desc);

call app_aplicar_rls('levantamento_quantitativo');
-- V16__sinapi.sql

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
-- V17__orcamento.sql

-- V17 · RF12 orçamento, itens vinculados a composições SINAPI e aditivos aprovados pelo cliente

create table orcamento (
  id                        uuid primary key,
  tenant_id                 uuid not null references empresa(id) on delete cascade,
  obra_id                   uuid not null references obra(id) on delete cascade,
  levantamento_id           uuid references levantamento_quantitativo(id) on delete set null,
  versao_modelo             bigint not null,
  tabela_sinapi_id          uuid not null references tabela_sinapi(id),
  data_base                 text not null,
  estado                    text not null,
  total                     numeric(16,2) not null default 0,
  bdi_pct                   double precision not null default 25,
  itens_gerados_por_ia      integer not null default 0,
  liberado_cliente          boolean not null default false,
  detalhe_liberado_cliente  boolean not null default false,
  aprovado_por              uuid references conta(id),
  decidido_em               timestamptz,
  criado_em                 timestamptz not null default now(),
  atualizado_em             timestamptz not null default now(),
  versao                    bigint not null default 0
);
create index orcamento_obra_idx on orcamento (obra_id, criado_em desc);

create table item_orcamento (
  id               uuid primary key,
  tenant_id        uuid not null references empresa(id) on delete cascade,
  orcamento_id     uuid not null references orcamento(id) on delete cascade,
  etapa            text not null,
  servico          text not null,
  elemento_tipo    text,
  codigo_sinapi    text not null,
  descricao        text not null,
  unidade          text not null,
  quantidade       numeric(14,3) not null,
  custo_unitario   numeric(14,2) not null,
  total            numeric(16,2) not null,
  gerado_por_ia    boolean not null default false,
  confirmado_por   uuid references conta(id),
  justificativa_ia text,
  criado_em        timestamptz not null default now(),
  atualizado_em    timestamptz not null default now(),
  versao           bigint not null default 0
);
create index item_orcamento_orc_idx on item_orcamento (orcamento_id);

create table aditivo (
  id            uuid primary key,
  tenant_id     uuid not null references empresa(id) on delete cascade,
  orcamento_id  uuid not null references orcamento(id) on delete cascade,
  descricao     text not null,
  valor         numeric(16,2) not null,
  estado        text not null check (estado in ('PROPOSTO','APROVADO_CLIENTE','RECUSADO_CLIENTE','CANCELADO')),
  proposto_por  uuid not null references conta(id),
  decidido_por  uuid references conta(id),
  decidido_em   timestamptz,
  criado_em     timestamptz not null default now(),
  atualizado_em timestamptz not null default now(),
  versao        bigint not null default 0
);

call app_aplicar_rls('orcamento');
call app_aplicar_rls('item_orcamento');
call app_aplicar_rls('aditivo');
-- V18__origem_formulario.sql

-- V18 · RF29 comandos montados pela aba Ações construtivas (formulário guiado) ficam rastreáveis com origem própria

alter table elemento drop constraint elemento_origem_check;
alter table elemento add constraint elemento_origem_check check (origem in ('CANVAS','CONSOLE_VI','FORMULARIO','IA','SISTEMA'));
-- V19__sinapi_origem.sql

-- V19 · RF12 tabela SINAPI com origem: a tabela semeada pela V16 tem valores ilustrativos e não pode passar por preço oficial

alter table tabela_sinapi add column origem text not null default 'IMPORTADA' check (origem in ('IMPORTADA','DEMONSTRACAO'));
update tabela_sinapi set origem = 'DEMONSTRACAO' where id = '019a0000-0000-7000-8000-000000000001';
-- V20__selo_motivo.sql

-- V20 · RF21 motivo da rejeição do selo profissional, mostrado ao profissional (antes era descartado)

alter table conta add column selo_motivo text;
