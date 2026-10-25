# Auditoria pelo uso real

Jornadas que usam o CivilConstructorAI como uma pessoa usaria — API e navegador — e gravam a evidência de cada passo em
`docs/auditoria/evidencias/`. Resultado e método: [AUDITORIA_2026-10.md](../../docs/auditoria/AUDITORIA_2026-10.md).

| Script | O que faz |
|---|---|
| `j1-identidade.mjs` | cadastro, verificação, login com 2FA, empresa, obra, convites, isolamento entre empresas, sudo |
| `j2-projeto-estrutura.mjs` | eixos, pavimentos, Notação VI, edição/desfazer/conflito, cargas, análise MEF, dimensionamento |
| `j3-entrega-ia-lgpd.mjs` | quantitativos, SINAPI, orçamento, sondagem, fundações, pranchas, IFC, rascunhos de IA, selo, cliente, LGPD |
| `j4-interface.mjs` | as mesmas jornadas pela tela (Playwright), com prints em `evidencias/telas/` |
| `catalogo-verificavel.mjs` | gera `docs/auditoria/CATALOGO_VERIFICAVEL.md` a partir do código (motores, front e testes) |
| `notas.py` | notas por RF e por família de comandos, com as rubricas da auditoria |
| `cliente.mjs` | cliente HTTP com cookie e XSRF, TOTP, leitura dos e-mails capturados |

## Como rodar

1. Suba o ambiente: `docker compose up -d` (PostGIS e Valkey), o Garage (`infra/garage/garage-init.sh`), um servidor SMTP
   de captura que grave cada e-mail como arquivo numa pasta, o backend com o perfil `dev` e o web (`next build && next start`).
2. Rode cada jornada:

```sh
CCA_EMAILS=/caminho/da/pasta/de/emails PW_CHROMIUM=/caminho/do/chromium node scripts/auditoria/j1-identidade.mjs
```

O backend limita tentativas por IP; entre execuções seguidas, limpe as chaves `cca:limite:*` no Valkey.

Para o catálogo: `node scripts/auditoria/catalogo-verificavel.mjs` (com `CATALOGO_IGNORAR=arquivo1,arquivo2` ele ignora
testes, para comparar antes × depois pelo mesmo critério). Para as notas: `python3 scripts/auditoria/notas.py`.
