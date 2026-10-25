
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
