-- Papel da aplicação em dev (o mesmo dos testes de integração): sem superusuário, sujeito ao RLS.
-- As migrações rodam como cca_migracao (dono do banco); a V2 concede DML a cca_app.
create role cca_app login password 'cca_app';
