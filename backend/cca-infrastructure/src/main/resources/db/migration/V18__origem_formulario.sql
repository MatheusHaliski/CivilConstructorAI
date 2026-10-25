-- RF29: comandos montados pela aba Ações construtivas (formulário guiado) ficam rastreáveis com origem própria.
alter table elemento drop constraint elemento_origem_check;
alter table elemento add constraint elemento_origem_check check (origem in ('CANVAS','CONSOLE_VI','FORMULARIO','IA','SISTEMA'));
