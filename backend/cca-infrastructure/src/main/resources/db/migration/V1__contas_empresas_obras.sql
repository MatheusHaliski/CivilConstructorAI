
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
