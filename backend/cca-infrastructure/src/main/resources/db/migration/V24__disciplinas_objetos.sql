
-- V24 · disciplinas de mobiliário, elétrica, hidrossanitária e pessoas (AUD-CC-3D-001): objetos da biblioteca e conexões
-- prediais gravam-se como elementos comuns (tipo OBJETO e CONEXAO, geometria em jsonb); só a lista de camadas muda
alter table elemento drop constraint if exists elemento_camada_check;
alter table elemento add constraint elemento_camada_check
  check (camada in ('arquitetura','estrutura','fundacao','desenho','anotacao','ia-rascunho','mobiliario','eletrica','hidrossanitaria','humanos'));
