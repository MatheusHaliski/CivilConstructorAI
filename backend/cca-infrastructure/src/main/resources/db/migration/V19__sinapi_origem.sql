
-- V19 · RF12 tabela SINAPI com origem: a tabela semeada pela V16 tem valores ilustrativos e não pode passar por preço oficial

alter table tabela_sinapi add column origem text not null default 'IMPORTADA' check (origem in ('IMPORTADA','DEMONSTRACAO'));
update tabela_sinapi set origem = 'DEMONSTRACAO' where id = '019a0000-0000-7000-8000-000000000001';
