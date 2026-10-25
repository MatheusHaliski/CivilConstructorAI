
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
