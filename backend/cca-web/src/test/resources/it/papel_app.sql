-- papel da aplicação nos testes de integração: sem superusuário, sujeito ao RLS
create role cca_app login password 'cca_app';
