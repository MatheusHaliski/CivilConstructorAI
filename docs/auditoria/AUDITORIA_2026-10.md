# Auditoria geral do CivilConstructorAI · outubro de 2026

Auditoria feita **usando o aplicativo como uma pessoa usaria** (navegador real e API), seguindo o
[diagrama de casos de uso](../DIAGRAMA_CASOS_DE_USO.md) e os docs [RF01–RF28](../rf/README.md), com AutoCAD 2026 e Revit 2026
como referência funcional dos comandos. Cada problema encontrado foi corrigido no código, retestado pela mesma jornada e
registrado aqui com nota antes × depois.

> **Regra desta auditoria.** Nada aqui atribui ao produto o que só existe em protótipo, descrição ou plano. Onde não houve
> evidência, está escrito **não verificado**. Não houve teste com pessoas: as notas são do auditor, a partir das evidências
> listadas, e não medem satisfação. A IA, no ambiente de desenvolvimento, é **simulada** (`app.ia.habilitada=false`): todo
> resultado de IA abaixo é de simulação.

## 0. Resumo

| Medida | Antes | Depois |
|---|---:|---:|
| Nota média por RF (rubrica da seção 3) | 6,7 | 8,3 |
| Passos de jornada aprovados (j1–j4, 182 passos) | — ¹ | 182 / 182 |
| Comandos do catálogo implementados **e** verificados por teste ² | 21 / 85 | 73 / 85 |
| Casos de paridade prévia (TS) × gravação (Java) | 0 | 36 nos dois motores |
| Telas auditadas por axe (WCAG 2.2 AA) sem violação séria/crítica | 4 públicas ³ | 4 públicas + 15 internas + 22 da obra |
| Testes automatizados do backend (unitário + IT) | — | 156 |

¹ As jornadas foram escritas durante a auditoria; a primeira execução de cada uma achou os problemas da seção 5.
² Mesmo critério nas duas colunas (`catalogo-verificavel.mjs`); "antes" ignora só os testes criados nesta auditoria
(`CATALOGO_IGNORAR=estado.test.ts,paridade.test.ts,comandos-paridade.json,ModeloBimParidadeTest.java`).
³ As telas internas só rodam com uma conta logada; com o 2FA obrigatório imposto nesta auditoria, a suíte passou a
responder ao segundo fator (`E2E_TOTP`) e cobre todas elas.

Os achados mais graves, todos corrigidos e cobertos por teste:

1. **Escrita entre empresas (P0).** Um DONO da empresa B gravava comandos no modelo de uma obra da empresa A conhecendo o id;
   a obra de A passava a dar erro 500 para sempre (unicidade de `modelo_bim.obra_id`). Corrigido em `AcessoObraImpl`
   (papel de empresa só vale para obras da empresa ativa) — `EscopoEmpresaIT`.
2. **Resultados velhos tratados como atuais (P1, resultado errado).** Mudar o modelo ou as cargas não invalidava análise,
   dimensionamento, fundações, quantitativo nem pranchas: era possível dimensionar sobre esforços de outra geometria. Agora
   `DependentesDoModelo` marca tudo como obsoleto e o dimensionamento recusa análise obsoleta (409 com orientação).
3. **Orçamento com preço de outro estado ou de demonstração (P1).** Sem tabela da UF, o orçamento usava a de outra UF ou o
   seed de demonstração como se fosse real; composição ausente derrubava a geração ou sumia em silêncio. Agora: sem tabela
   da UF/regime → 422 explicando; seed marcado como demonstração; item sem composição aparece com custo zero e bloqueia a
   aprovação.
4. **Segurança de acesso (P1).** TOTP reaproveitável (replay), 2FA obrigatório não exigido de verdade, convite podendo criar
   DONO ou papel acima de quem convida, redirecionamento aberto em `/entrar?voltar=`, URL do e-mail de convite montada a
   partir do cabeçalho `Origin`, e o papel ARQUITETO aprovando rascunhos de IA sem 2FA.
5. **Bloqueios de jornada (P1).** Toda prancha com porta ou janela falhava (formatação `%.1f` recebendo inteiro); exportação
   para o S3 local falhava (região e checksum); sondagem com leitura inválida dava 500; segunda malha de eixos dava 500.

## 1. Ambiente, versões, contas e integrações

| Item | Valor |
|---|---|
| Data | 25/10/2026 |
| Código | branch `claude/hopeful-carson-3lrvl3` (base: `main` após o merge do MatheusHaliski/CivilConstructorAI#5) |
| Backend | Java 21 · Spring Boot 4.1.2 · Spring Security 7 · Flyway V1–V20 · perfil `dev` |
| Web | Next.js 16.4.3 · React 19.3 · Node 22.22 · build de produção (`next start`) |
| Banco e cache | PostGIS 17-3.5 (RLS por empresa) · Valkey 9 (sessões, limites, uso único de TOTP) |
| Arquivos | Garage v2.4.0 (S3 local), região `garage` |
| E-mail | servidor SMTP de captura local (fora do repositório): cada e-mail vira um arquivo, lido pelas jornadas |
| Navegador | Chromium (Playwright 1.64) em 1440×900, 1280×860 e 390×844 (celular) |
| Contas de teste | criadas pelas próprias jornadas a cada execução (`j1-…@exemplo.cca` etc.), com e-mail verificado pelo código real recebido; suporte da plataforma: `suporte-auditoria@exemplo.cca` (variável `APP_PLATAFORMA_SUPORTE`) |

| Integração | Situação nesta auditoria |
|---|---|
| E-mail (SMTP) | **Real** (servidor local de captura): código de verificação, código de sudo e convite conferidos pelas jornadas; aviso de novo acesso e decisão de selo são enviados, mas o conteúdo não foi conferido |
| Armazenamento S3 | **Real** (Garage): upload de boletim SPT por URL pré-assinada, pranchas, IFC, ZIP de exportação |
| Banco, RLS, sessões | **Real** |
| IA (Claude) | **Simulada** (`FakeModeloLinguagem`): texto natural, lançamento por IA, extração de boletim, associação SINAPI |
| Tabela SINAPI | Seed de **demonstração** + importação de arquivo no formato da Caixa (a jornada importa PR 06/2020; o orçamento usou a tabela importada mais recente da UF, PR 08/2026, de execução anterior — nunca a de demonstração) |
| Verificação de CREA/CAU | **Manual** pelo suporte (fila de selos); não há consulta automática ao conselho |
| Saga de eliminação (RF26) | **Não verificada** em jornada: depende de 30 dias de carência (há `EliminacaoIT`) |
| Expiração de convite (7 dias) e de sessão (60 min) | **Não verificadas** em jornada (tempo) |

## 2. Método

1. **Coerência documental primeiro** (seção 6): o diagrama de casos de uso foi comparado com os docs de RF e com o código.
2. **Jornadas reais** em `scripts/auditoria/` (rodam contra o app no ar; cada passo grava evidência em
   `docs/auditoria/evidencias/*.json`):
   - **j1 · identidade** (50 passos): cadastro, verificação, login com 2FA, empresa, obra, convites, isolamento, sudo.
   - **j2 · projeto e estrutura** (40): eixos, pavimentos, Notação VI, edição/desfazer/conflito, cargas, análise, dimensionamento, motor.
   - **j3 · entrega, IA e LGPD** (70): quantitativos, SINAPI, orçamento, sondagem, fundações, pranchas, IFC, rascunhos de IA,
     selo, cliente, consentimentos, direitos do titular, exclusão e reativação.
   - **j4 · interface** (22): as mesmas jornadas pela tela, com prints em `docs/auditoria/evidencias/telas/`.
3. **Conferência independente dos números**: equilíbrio do MEF (ΣRz = Σ cargas × fator da combinação), volumes de concreto
   e alvenaria recalculados à mão, total do orçamento = soma dos itens, hash do quantitativo determinístico.
4. **Catálogo verificável de comandos** ([CATALOGO_VERIFICAVEL.md](CATALOGO_VERIFICAVEL.md)): gerado do código, não da doc.
5. **Correção → reteste → nota**. Prioridade: perda de dados, permissões, resultado errado, bloqueio de jornada, depois interface.

## 3. Matriz RF01–RF28 (cobertura e notas)

### 3.1 Cobertura

| RF | Atores | Jornada | Tela | API principal | Dados | Testes automatizados |
|---|---|---|---|---|---|---|
| RF01 | arquiteto, engenheiro | j2, j4 | Modelo BIM | `POST /obras/{id}/modelo/comandos` | `modelo_bim`, `comando_vi` | ModeloComandoIT, ModeloBimTest, paridade |
| RF02 | arquiteto, engenheiro | j2, j3 | Modelo BIM (console), Códigos | `POST /vi/validar`, `/modelo/texto` | `comando_vi`, `rascunho_ia` | ParserViTest, CatalogoComandosTest |
| RF03 | arquiteto, engenheiro | j2 | Modelo BIM, Ações | comandos `MALHA`, `EIXO`, `PAVIMENTO` | `modelo_bim` | ModeloBimTest, EstresseAuditoriaTest |
| RF04 | engenheiro | j2 | Estrutura › Cargas | `/estrutura/cargas`, `/lancamentos` | `carga`, `lancamento` | AnaliseIT, GeradorCombinacoesTest |
| RF05 | engenheiro | j2, j3 | Análises › MEF | `POST /analises` | `analise_estrutural` | SolverMefTest, MontadorModeloAnaliticoTest, AnaliseIT |
| RF06 | (auditor) | j2 | Estrutura › Vãos | `/estrutura/vaos` | — | EstresseAuditoriaTest e04 |
| RF07 | engenheiro | j2 | Análises › Dimensionamento | `POST /dimensionamentos` | `dimensionamento` | Nbr6118Test, DimensionamentoIT |
| RF08 | (auditor) | j2 | Estrutura › Restrições | comandos reprovados (422) | — | ModeloBimTest, EstresseAuditoriaTest |
| RF09 | engenheiro | j3 | Geotecnia › Furos/Boletim/Perfil | `/sondagens` | `sondagem` + S3 | SondagemIT |
| RF10 | engenheiro | j3 | Geotecnia › Fundações | `/fundacoes` | `dimensionamento_fundacao` | CalculoFundacoesTest |
| RF11 | engenheiro, orçamentista | j3 | Quantitativos | `/quantitativos` | `levantamento_quantitativo` | QuantificadorTest |
| RF12 | orçamentista, engenheiro | j3 | Orçamento | `/orcamentos`, `/sinapi` | `orcamento`, `tabela_sinapi` | OrcamentoIT |
| RF13 | todos (cliente: liberado) | j3, j4 | Modelo BIM (3D) | `GET /modelo` | `modelo_bim` | SnapshotClienteIT |
| RF14 | arquiteto, engenheiro | j3 | Pranchas | `/pranchas` | `prancha` + S3 | GeradorPranchaTest, PranchaIT |
| RF15 | arquiteto, engenheiro | j3 | Interoperabilidade | `/interop/ifc` | job + S3 | EscritorIfcTest, ExportacaoIfcIT |
| RF16 | (auditor + IA) | j3 (simulada) | Assistente IA | `/modelo/texto` | `rascunho_ia` | EstresseAuditoriaTest e13/e15 |
| RF17 | (auditor) | — | Modelo BIM | comandos de pilar | `modelo_bim` | EstresseAuditoriaTest e05/e06, ModeloBimTest |
| RF18 | visitante | j1, j4 | Cadastrar, Verificar e-mail | `/auth/register`, `/me/verify-email` | `conta` | CadastroIT |
| RF19 | usuário | j1, j4 | Entrar | `/auth/login`, `/auth/session` | sessão (Valkey) | LoginIT |
| RF20 | dono, eng., arq., convidado | j1, j3, j4 | Empresa › Membros, Convite | `/companies/{id}/invites`, `/invites/{t}/accept` | `convite` | ConviteEAcessoIT |
| RF21 | todos, suporte | j1, j3 | Perfil e selo, Plataforma › Selos | `@PreAuthorize`, `/plataforma/selos` | `membro_obra`, `conta` | EscopoEmpresaIT, ConviteEAcessoIT |
| RF22 | dono/admin | j1, j4 | Empresa, Obras | `/companies`, `/obras` | `empresa`, `obra` | ConviteEAcessoIT |
| RF23 | usuário | j1, j3, j4 | Segurança e sessões | `/me/mfa/*`, `/me/sudo`, `/me/sessions` | `conta`, Valkey | LoginIT |
| RF24 | visitante, usuário | j1, j3 | Consentimentos | `/me/consentimentos` | `consentimento` | ConsentimentoIT |
| RF25 | usuário, DPO | j3 | Meus dados, Exportar, Pedidos | `/me/dados`, `/me/exportacoes` | `pedido_titular` + S3 | ExportacaoIT, CifraExportacaoTest |
| RF26 | usuário, job | j3 | Excluir conta | `/me/exclusao` | `conta` | EliminacaoIT, MaquinasDeEstadoLgpdTest |
| RF27 | arquiteto, engenheiro, DPO | j3 | Assistente IA, Transparência | `/ia/rascunhos`, `/ia/uso` | `rascunho_ia` | RascunhoIaIT, ServicoIaTest |
| RF28 | arquiteto, engenheiro | j2, j4 | Modelo BIM, Códigos | `/vi/catalogo`, `/vi/resolver` | — | estado.test.ts, paridade, CatalogoComandosTest |

O RF29 (Ações construtivas, Copilot e Autopilot) existe na interface e foi exercitado na j4, mas **não tem doc de RF** nem
backend próprio: as ações montam comandos VI no navegador e gravam pelo mesmo endpoint do RF01. Fica como pendência (seção 10).

### 3.2 Notas por RF — antes × depois

Rubrica: **C** conclusão da tarefa 30% · **P** precisão 25% · **Cl** clareza 20% · **F** feedback 15% · **A** consistência e
acessibilidade 10%. Subnotas do auditor a partir das evidências citadas; tabela gerada por `scripts/auditoria/notas.py`.

| RF | Antes (C/P/Cl/F/A) | Nota antes | Depois (C/P/Cl/F/A) | Nota depois | Evidência | Principal problema restante |
|---|---|---:|---|---:|---|---|
| RF01 | 7/5/7/7/8 | 6.6 | 9/9/8/8/8 | 8.6 | j2 CA-03/04/05, ModeloComandoIT, paridade TS↔Java | sem comparar/restaurar versões |
| RF02 | 7/7/6/6/8 | 6.8 | 9/9/8/8/8 | 8.6 | j2 CA-01/02, equivalência; j3 CA-04 | texto natural só simulado em dev |
| RF03 | 6/6/5/3/8 | 5.5 | 9/9/8/8/8 | 8.6 | j2 malha/CA-01..04 | — |
| RF04 | 7/6/6/6/8 | 6.5 | 9/9/8/8/8 | 8.6 | j2 CA-02..05, carga-linear | — |
| RF05 | 8/7/7/6/8 | 7.2 | 9/9/8/8/8 | 8.6 | j2 equilíbrio ΣRz = 1,4·ΣG; SolverMefTest | doc descreve serviço Python; código é Java |
| RF06 | 8/8/7/7/8 | 7.7 | 8/8/7/7/8 | 7.7 | j2 CA-02/caixa; EstresseAuditoriaTest e04 | — |
| RF07 | 7/4/6/5/8 | 5.9 | 8/9/8/8/8 | 8.2 | j2 CA-01 (409 obsoleta); DimensionamentoIT, Nbr6118Test | armaduras não conferidas contra memória de cálculo independente (não verificado) |
| RF08 | 9/9/8/8/8 | 8.6 | 9/9/8/8/8 | 8.6 | j2 CA-01/03; ModeloBimTest | — |
| RF09 | 6/8/4/3/8 | 5.8 | 9/9/8/8/8 | 8.6 | j3 CA-02/03, perfil, upload S3; SondagemIT | extração do boletim por IA só simulada |
| RF10 | 7/5/7/5/8 | 6.3 | 8/8/8/8/8 | 8.0 | j3 dimensionar, CA-02/04/05; CalculoFundacoesTest | capacidade de carga não conferida contra caso de referência (não verificado) |
| RF11 | 8/8/7/6/8 | 7.5 | 9/9/8/8/8 | 8.6 | j3 CA-01/03/04/05/06, totais; QuantificadorTest | critério de interseção não aparece na tela |
| RF12 | 5/3/5/4/8 | 4.7 | 8/8/8/8/8 | 8.0 | j3 sem-tabela, importar, soma, sem-vinculo, CA-05/06; OrcamentoIT | preço real depende de importar a tabela da Caixa; o seed é demonstração |
| RF13 | 8/6/7/7/7 | 7.0 | 8/8/7/7/7 | 7.5 | j3 CA-08, liberado; SnapshotClienteIT | 3D só inspeciona; sem corte interativo |
| RF14 | 3/6/5/3/8 | 4.7 | 8/8/8/8/8 | 8.0 | j3 CA-03/04/05/07; GeradorPranchaTest, PranchaIT | sem PDF (só SVG e DXF) |
| RF15 | 4/6/6/5/8 | 5.5 | 8/7/7/7/8 | 7.4 | j3 CA-02/08, conteúdo IFC; EscritorIfcTest, ExportacaoIfcIT | IFC sem materiais |
| RF16 | 6/6/6/6/8 | 6.2 | 7/7/7/7/8 | 7.1 | EstresseAuditoriaTest e13/e15; j3 CA-04 (simulação) | laço com LLM real não verificado |
| RF17 | 8/8/7/7/8 | 7.7 | 8/8/7/7/8 | 7.7 | EstresseAuditoriaTest e05/e06, ModeloBimTest disciplinaZ | não exercitado em jornada |
| RF18 | 8/9/6/7/9 | 7.8 | 9/9/9/9/9 | 9.0 | j1 CA-01..10; j4 UX-erros/corrigir-email; CadastroIT | — |
| RF19 | 7/5/7/6/9 | 6.5 | 9/9/8/9/9 | 8.8 | j1 CA-01..06c, replay; j4 UX-mfa/voltar; LoginIT | sem QR do TOTP na tela |
| RF20 | 5/4/5/5/9 | 5.2 | 9/9/9/9/9 | 9.0 | j1 CA-02..08; j4 UX-email/convite/aceite; ConviteEAcessoIT | — |
| RF21 | 6/3/6/6/9 | 5.5 | 9/9/8/8/9 | 8.7 | j1 CA-01/02/04a; j3 selo; EscopoEmpresaIT | veDadoDeSaude sem caso de uso |
| RF22 | 7/8/5/6/9 | 6.9 | 9/9/8/8/9 | 8.7 | j1 CA-01/02/04/05; j4 UX-contexto | — |
| RF23 | 8/7/7/7/9 | 7.5 | 9/9/8/8/9 | 8.7 | j1 CA-02/03/05/07; j3 sudo-email | — |
| RF24 | 9/9/8/8/9 | 8.7 | 9/9/8/8/9 | 8.7 | j1 CA-01; j3 CA-03/04/07; ConsentimentoIT | — |
| RF25 | 9/9/8/8/9 | 8.7 | 9/9/8/8/9 | 8.7 | j3 CA-01..08; ExportacaoIT, CifraExportacaoTest | — |
| RF26 | 7/8/8/8/9 | 7.8 | 7/8/8/8/9 | 7.8 | j3 consequências, CA-02/03; EliminacaoIT | saga após 30 dias não exercitada em jornada |
| RF27 | 6/6/7/6/8 | 6.4 | 8/8/8/8/8 | 8.0 | j3 CA-01/03/04/06/08/10, promoção; RascunhoIaIT | IA real não verificada em dev |
| RF28 | 7/6/7/6/8 | 6.7 | 9/9/8/8/8 | 8.6 | catálogo verificável 73/85; estado.test.ts; paridade 34 casos | BLOCO/INSERIR/ESTILO_COTA/LOCALIZAR ausentes |

Média: antes 6.7 · depois 8.3

## 4. Jornadas e pontos de interrupção

| Jornada | Antes da correção — onde a pessoa parava | Depois |
|---|---|---|
| Cadastro → obra (RF18, RF19, RF22) | E-mail digitado errado não tinha saída na verificação; ao criar a empresa ela não virava a ativa (precisava sair e entrar); o DONO usava tudo sem configurar o 2FA obrigatório | Destino do código visível e "Cadastrar com outro e-mail"; empresa criada vira a ativa; DONO é levado à configuração do 2FA e o resto fica bloqueado (403 `mfa_obrigatorio`) até confirmar |
| Convite (RF20, RF21) | Link do e-mail apontava para rota inexistente; quem não tinha conta perdia o convite após o cadastro; após aceitar, a obra não aparecia sem trocar a empresa à mão | Link `/convite/{token}`; cadastro volta ao convite; aceite troca a empresa e leva à obra; papel que aprova passa pela configuração do 2FA e volta à obra |
| Concepção (RF01–RF03) | Segunda malha → 500; pé-direito absurdo aceito sem aviso; eixos depois de Z ficavam sem nome válido | 422 com o que fazer; confirmação explícita fora de 2,20–12,00 m; nomes A…Z, AA… |
| Estrutura (RF04–RF07) | Carga sem descrição → 422 sem campo marcado; análise rodava sem lançamento pronto; dimensionamento aceitava análise de outra geometria | Descrição padrão; 422 "marque como pronto"; 409 "rode a análise da versão atual" |
| Geotecnia (RF09, RF10) | Leitura SPT inválida → 500; upload do boletim falhava no S3 local | 422 com todos os erros juntos; upload por URL pré-assinada funcionando |
| Orçamento (RF11, RF12) | Orçamento saía com preço de outra UF ou de demonstração; serviço sem composição sumia | 422 explicando a tabela que falta; itens sem composição visíveis e bloqueando a aprovação; aviso de quantitativo desatualizado |
| Entrega (RF14, RF15) | Prancha com porta/janela falhava sempre; IFC não exportava (S3) e levava rascunhos de IA | Prancha gerada (SVG/DXF); IFC4X3 sem a camada de rascunho |
| IA (RF16, RF27) | Texto natural gravava direto no modelo; aprovar o rascunho não mudava nada no modelo; cliente via rascunhos | Texto vira rascunho na camada `ia-rascunho`; aprovar promove à camada da disciplina com versão no histórico; descartar remove; cliente e fornecedor não veem rascunhos |
| LGPD (RF24–RF26) | — (sem bloqueios) | — |

## 5. Problemas encontrados (reprodução, esperado × observado)

Severidade: **P0** perda de dados/isolamento · **P1** permissão, resultado errado ou bloqueio · **P2** clareza/feedback.

| # | Sev. | RF | Reprodução | Esperado | Observado (antes) | Correção | Teste |
|---|---|---|---|---|---|---|---|
| 1 | P0 | RF21 | DONO da empresa B: `POST /obras/{obraDeA}/modelo/comandos` | 404 (obra fora do escopo) | 201; a obra de A passa a responder 500 | `AcessoObraImpl.noEscopo` | EscopoEmpresaIT |
| 2 | P1 | RF01/05/07/10/11/14 | Rodar análise; mover um pilar; dimensionar | Análise obsoleta; dimensionar recusa | Dimensionamento sobre esforços antigos | `DependentesDoModelo` + 409 | AnaliseIT, DimensionamentoIT, j2/j3 |
| 3 | P1 | RF12 | Obra no AC sem tabela do AC; gerar orçamento | 422 dizendo qual tabela importar | Usava tabela de outra UF/demonstração | `SinapiService.vigente`, V19 | OrcamentoIT, j3 |
| 4 | P1 | RF12 | Quantitativo com fôrma; composição inexistente | Item visível, sem custo, bloqueando aprovação | Item sumia ou geração falhava | `ItemOrcamento.semComposicao` | OrcamentoIT |
| 5 | P1 | RF19 | Repetir o mesmo TOTP em dois logins dentro de 30 s | 401 no segundo | Aceito | `primeiroUso` no Valkey | LoginIT, j1 CA-05b |
| 6 | P1 | RF19 | DONO sem 2FA chama `GET /companies/{id}` | 403 `mfa_obrigatorio` | 200 | `MfaPendenteFilter` | LoginIT, j1 CA-06b |
| 7 | P1 | RF20 | Engenheiro convida com papel ADMIN; DONO convida DONO | 403 com motivo | 201 | `Convite.novo` | DominioIdentidadeTest, j1 CA-04 |
| 8 | P1 | RF19 | Abrir `/entrar?voltar=https://exemplo.com` e entrar | Fica no app | Saía para o site externo | `destinoSeguro` | navegacao.test.ts |
| 9 | P1 | RF20 | Criar convite com cabeçalho `Origin: https://atacante` | Link com a URL do app | Link com o Origin | `app.web.url` | j1 e-mail-url |
| 10 | P1 | RF19/RF21 | Arquiteta aceita convite e aprova rascunho de IA sem 2FA | 2FA obrigatório (aprovadora) | Sem 2FA | `PapelObra.ARQUITETO` exige 2FA | DominioIdentidadeTest (invariante), j1 CA-06c, j4 |
| 11 | P1 | RF14 | Gerar prancha de modelo com uma porta | Prancha gerada | Falha sempre (`%.1f` com inteiro) | `GeradorPrancha` | GeradorPranchaTest |
| 12 | P1 | RF15/RF09 | Exportar IFC / enviar boletim no ambiente local | Arquivo no S3 | Falha de região/checksum | `ArmazenamentoS3`, `garage-init.sh` | ExportacaoIfcIT, j3 |
| 13 | P1 | RF09 | Leitura com Nspt negativo | 422 com todos os erros | 500 | `LeituraSpt.valida()` | SondagemIT, j3 |
| 14 | P1 | RF03 | Segunda `MALHA` com nomes já usados | 422 explicando | 500 | `ModeloBim` | j2 malha-dupla |
| 15 | P1 | RF27 | Aprovar rascunho de IA | Elementos promovidos à disciplina | Nada mudava no modelo | `EfeitoRascunhoComandoVi` | RascunhoIaIT, j3 promoção |
| 16 | P1 | RF13/DN-02 | Cliente abre o modelo antes da liberação | 404 | Via a versão atual com rascunhos | `ModeloService.obter` | SnapshotClienteIT, j3 |
| 17 | P1 | RF28 | `CONCORDAR` raio 1 entre duas linhas | Arco tangente de raio 1 | Canto vivo (raio ignorado) | `cantos()` no motor | estado.test.ts |
| 18 | P1 | RF28 | `CHANFRAR` d1 0,5 / d2 1 | Recuo e segmento | Canto vivo | `cantos()` | estado.test.ts |
| 19 | P1 | RF28 | `LINHA_DETALHE` ou `LINHA_QUEBRA` com dois pontos | Uma linha | Nada era criado | `montar()` lê `p2` | estado.test.ts |
| 20 | P1 | RF28/RF29 | `VI(VIGA, …, mat=MAT.ESTRUTURAL.CONC_FCK25)` | Viga com o material | Reprovada (material inexistente) — a tela Códigos dizia que o código era aceito | `normalizarId` nos dois motores | paridade (2 casos) |
| 21 | P2 | RF28 | F2 (`HISTORICO`) | Expande o histórico | Nada visível | `LinhaComando` | — (visual) |
| 22 | P2 | RF22 | Pessoa convidada só para uma obra abre o menu | Só o que pode usar | Via Membros, Privacidade e Consumo de IA da empresa (403 ao abrir) e "Sem empresa ativa" | `gestorEmpresa` no menu | navegacao.test.ts, print 15a |
| 23 | P2 | RF02 | `VI(VIGAA, …)` | Erro com sugestão | Erro genérico | `CatalogoController` | j2 CA-02 |
| 24 | P2 | RF04 | Carga sem descrição pela tela | Aceita | 422 sem campo marcado | descrição padrão | j2 UX-descricao |
| 25 | P2 | RF20 | Ler o e-mail do convite | Quem, obra, papel legível, validade em pt-BR | Papel em código e data ISO | `ConviteService` | j4 UX-email |
| 26 | P2 | geral | Parâmetro ausente, JSON ilegível, violação de integridade | 400/409 em pt-BR | 500 | `ProblemDetailsHandler` | j2 erro-400 |

## 6. Coerência do diagrama de casos de uso

| Ponto apontado | Achado no código | O que mudou no diagrama |
|---|---|---|
| Mestre de obras, técnico(a) e fornecedor fora dos fluxos | Mestre vincula trabalhador sem login (`ObraService.cadastrarTrabalhador`); técnico só vê; fornecedor é tratado como cliente (só o liberado) | Entraram nos fluxos com exatamente isso |
| Permissões sem caso de uso | `PapelObra.veDadoDeSaude()` não é usado por nenhum serviço; "pedidos do fornecedor" não tem módulo | Notas ³ e ⁴ da matriz; **não** viraram caso de uso (lacuna documental ≠ funcionalidade) |
| Visibilidade do cliente "pendente" | Implementada: liberação explícita por versão do modelo, do quantitativo e do orçamento; rascunho de IA nunca aparece | Caso "Liberar versão ao cliente"; nota ² |
| RF06/RF08 como middleware | São regras do auditor do `ModeloBim` (Java), rodadas a cada comando junto com RF16/RF17; não há serviço Python | Subgrafo "Auditor (a cada comando)" e ator "Auditor do ModeloBim - Java" |
| Exclusão × reativação × saga | A saga é disparada pelo job quando a carência acaba; a reativação é oferecida no login | Saga ligada ao fim da carência; "Reativar conta" estende o login |
| Referências divergentes do RF21 | O doc do RF21 é "controle de acesso por papel na obra" (o selo é parte dele) | Dois casos: controlar acesso por papel e pedir/decidir selo |
| Sem colaboração/revisão de versões | Existe: histórico de comandos com autor, versão e inverso; aprovação de rascunho de IA. Não existe: comentários, bloqueio de elemento, comparar/restaurar versão | Subgrafo "Colaboração e revisão" com o que existe e o que falta |

Também divergem do código, sem correção nesta auditoria: o doc do RF05 descreve o MEF num serviço Python (`geometria`,
`scipy`) — o cálculo roda em Java (`cca-domain/estrutura/mef`); e o RF29 não tem doc.

## 7. CAD/BIM, códigos, materiais e IA

### 7.1 Comandos e famílias de referência

O [catálogo verificável](CATALOGO_VERIFICAVEL.md) cruza os 85 comandos do catálogo com o código: **73 implementados e
verificados**, **8 parciais** (executam, mas fazem menos que a referência — p.ex. `REEXIBIR_CATEGORIA` reexibe tudo) e **4
ausentes** (`ESTILO_COTA`, `BLOCO`, `INSERIR`, `LOCALIZAR`: o motor só mostra o aviso "age na prancha"). O AutoCAD 2026 tem
cerca de 1.590 comandos (contagem de fonte secundária); a seção 3 do catálogo classifica, por família (desenho 2D, edição,
precisão, organização, componentes, referências, anotações, 3D, inspeção, documentação, automação, colaboração,
interoperabilidade), o que fica fora do escopo, o que depende de integração e o que falta.

Nota por família (rubrica da auditoria de arquitetura: **C** correção 30% · **P** precisão 25% · **Cl** clareza 15% ·
**F** feedback 10% · **V** consistência visual 10% · **A** acessibilidade 10%; gerada por `scripts/auditoria/notas.py`):

| Família | Antes (C/P/Cl/F/V/A) | Nota antes | Depois | Nota depois | Base |
|---|---|---:|---|---:|---|
| Desenho 2D | 7/7/7/7/8/7 | 7.1 | 9/9/8/8/8/7 | 8.4 | paridade TS↔Java em todos os comandos de desenho; LINHA_DETALHE passou a criar a linha |
| Edição | 6/5/7/6/8/7 | 6.2 | 9/9/8/8/8/7 | 8.4 | CONCORDAR/CHANFRAR respeitam raio e distâncias; QUEBRAR, UNIR, ESTICAR testados |
| Precisão | 8/8/7/8/8/7 | 7.8 | 8/9/8/8/8/7 | 8.2 | osnap, orto, polar, grade e entrada dinâmica testados; sem FROM e UCS por 3 pontos |
| Organização | 6/7/7/6/7/7 | 6.6 | 6/7/7/7/7/7 | 6.7 | camadas fixas por disciplina; sem grupos e estados de camada |
| Anotações | 5/6/6/5/7/7 | 5.8 | 7/7/7/7/7/7 | 7.0 | cotas e linha de detalhe gravando; estilo de cota e blocos ausentes |
| Vista e inspeção | 7/7/7/5/7/7 | 6.8 | 8/8/7/8/7/7 | 7.7 | ocultar/isolar/reexibir testados; F2 funcionando; VG e VU parciais |
| Materiais e códigos | 6/4/6/6/8/7 | 5.8 | 9/9/8/8/8/7 | 8.4 | MAT. aceito nos dois motores (antes só na tela Códigos) |

### 7.2 Códigos, materiais e ações

| Conceito | No produto | Código | Onde |
|---|---|---|---|
| Material | Tabela A: propriedades físicas, estilo, restrições, custo-base, código SINAPI | `MAT.<CATEGORIA>.<ID>` ou o legado `MAT_<ID>` — **os dois aceitos nos dois motores** desde esta auditoria | `tabela-a.ts`, `TabelaA.java` |
| Tipo de elemento | Parede, pilar, viga, laje, porta, janela, sapata, bloco, radier, estaca, contraventamento | código do comando VI | `catalogo.ts`, `ModeloBim.java` |
| Instância | Elemento no modelo, com id estável, pavimento, camada, origem (canvas, console, IA, formulário) | uuid | `modelo_bim` |
| Composição de custo | Composição SINAPI da tabela importada da UF/mês/regime | código SINAPI | `tabela_sinapi` |
| Serviço do orçamento | Item = quantitativo × composição; sem composição → custo zero, visível, bloqueia a aprovação | código SINAPI | `orcamento` |
| Ação construtiva | `MOD.CRIAR.<TIPO>` (10 ações): formulário por listas que vira um comando VI auditado | `MOD.` | `apps/web/lib/acoes.ts` |
| `ELM.`, `CMP.`, `SRV.`, `CHK.` | Proposta de famílias de código; **não implementadas** (nenhum código no repositório as usa) | — | — |

### 7.3 Modos de IA

| Modo | Equivalente no produto | Situação |
|---|---|---|
| Manual | Motor de desenho, Notação VI, ações por formulário | Implementado e verificado |
| Consultivo | Copilot: sugere a próxima ação com a evidência do modelo; não grava | Implementado (regras locais, sem LLM) — j4 |
| Sugestões | Correções do auditor junto ao erro (`correcoesAuditor` no histórico) | Implementado e verificado |
| Assistido | Texto natural → rascunho na camada `ia-rascunho` → aprovação por profissional com selo, 2FA e ART | Implementado; IA **simulada** em dev — j3 |
| Agente | Autopilot: passos de regras locais (sem LLM); a pessoa autoriza cada passo, e ele para em toda decisão que exige aprovação humana | Implementado (regras); **não verificado** com IA real |

### 7.4 Interoperabilidade

| Formato | Direção | Versão | Perdas conhecidas | Evidência |
|---|---|---|---|---|
| IFC | escrita e leitura | IFC4X3_ADD2 | sem materiais (`IfcMaterial` ausente); GlobalId estável entre exportações | j3, `evidencias/arquivos/modelo.ifc` |
| DXF | escrita | ASCII mínimo (HEADER só com `$INSUNITS`, sem `$ACADVER`); camadas EIXOS, COTAS, CORTADO, OCULTO, VISTA, TEXTO | sem blocos nem estilos de cota | `planta-baixa.dxf` |
| SVG | escrita | — | — | `planta-baixa.svg` |
| CSV / XLSX | escrita | — | — | j3 CA-05 |
| STEP | — | — | **ausente** (o título do RF15 cita STEP; não há escritor no código) | — |
| PDF | — | — | **ausente** | — |
| DWG | — | — | **ausente** (dependente de integração: biblioteca ODA) | — |
| BCF | — | — | **ausente** | — |

### 7.5 Desempenho

Não houve medição de carga nem de tempo de cálculo. O único tempo medido foi o do login (j1 CA-01): mediana de 489 ms com
e-mail existente e 478 ms com inexistente — o Argon2 roda nos dois casos, de propósito, para não revelar quais e-mails têm
conta. Modelos grandes (milhares de elementos) **não foram verificados**.

## 8. Correções, arquivos alterados e testes

As correções estão nos commits `54abe82` (isolamento, autenticação, propagação, IA, orçamento, pranchas, jornadas) e no
commit desta entrega (motor de comandos, paridade, códigos MAT., 2FA do arquiteto, menu por papel, docs). Principais arquivos:

- **Acesso e identidade:** `acesso/AcessoObraImpl.java`, `acesso/PapelObra.java`, `identidade/LoginService.java`,
  `identidade/MfaService.java`, `identidade/SessaoUsuarioFabrica.java`, `identidade/PrincipalSessao.java`,
  `config/MfaPendenteFilter.java`, `empresa/ConviteService.java`, `empresa/Convite.java`, `api/empresa/*Controller.java`.
- **Modelo e dependentes:** `modelo/ModeloBim.java`, `modelo/ModeloService.java`, `modelo/DependentesDoModelo.java`,
  `modelo/DependentesDoModeloJdbc.java`, `modelo/EfeitoRascunhoComandoVi.java`, `notacao/TabelaA.java`,
  `packages/notacao-vi/src/modelo.ts`, `packages/notacao-vi/src/tabela-a.ts`.
- **Cálculo, orçamento, entrega:** `estrutura/AnaliseService.java`, `estrutura/DimensionamentoService.java`,
  `estrutura/CargasService.java`, `geotecnia/*`, `orcamento/*`, `prancha/GeradorPrancha.java`, `interop/EscritorIfc.java`,
  `arquivo/ArmazenamentoS3.java`, migrações V19 e V20.
- **Web:** `lib/motor/estado.ts`, `components/motor/LinhaComando.tsx`, `lib/navegacao.ts`, `components/layout/*`,
  `lib/api.ts`, `lib/formatos.ts`, telas de cadastro, verificação, convite, segurança, empresa, conta e orçamento.
- **Docs:** este relatório, [CATALOGO_VERIFICAVEL.md](CATALOGO_VERIFICAVEL.md), [DIAGRAMA_CASOS_DE_USO.md](../DIAGRAMA_CASOS_DE_USO.md)
  e a seção "Auditoria 2026-10" de cada RF alterado.

Testes executados nesta entrega (todos passando):

| Suíte | Resultado |
|---|---|
| Backend `mvn verify` (domínio, aplicação, web e ITs com PostGIS/Valkey/Garage) | 67 + 44 + 2 + 43 |
| `packages/notacao-vi` (vitest, inclui paridade) | 49 |
| `apps/web` (vitest: navegação, entrada, motor) | 36 |
| Playwright + axe (WCAG 2.2 AA) | 20 testes, 41 telas |
| Jornadas j1–j4 | 182 / 182 passos |
| `scripts/check_rf_docs.py --diff origin/main` | OK |

Novos testes: `ModeloBimParidadeTest.java` + `paridade.test.ts` (mesma fixture `packages/notacao-vi/fixtures/comandos-paridade.json`,
36 casos), `apps/web/lib/motor/estado.test.ts` (16 casos do motor), `EscopoEmpresaIT`, `GeradorPranchaTest`, invariante
"aprovador exige 2FA" em `DominioIdentidadeTest`, filtro de menu em `navegacao.test.ts`.

## 9. Evidências

- Jornadas: `docs/auditoria/evidencias/j1-identidade.json`, `j2-projeto-estrutura.json`, `j3-entrega-ia-lgpd.json`,
  `j4-interface.json` (cada passo com RF, critério, resultado e evidência — status HTTP, corpo resumido, URL).
- Arquivos gerados pelo app: `evidencias/arquivos/modelo.ifc`, `planta-baixa.dxf`, `planta-baixa.svg`.
- Telas: `evidencias/telas/01…16` (visitante, cadastro com erros, verificação, código errado, obras sem empresa, empresa
  criada, configurar 2FA, códigos de recuperação, painel da obra, ações com prévia e aplicada, Copilot, convite enviado,
  convite sem conta, convidado configurando 2FA, convidado na obra, celular).
- Para reproduzir: subir o ambiente (`compose.yaml`, Garage, backend `dev`, web) e rodar
  `CCA_EMAILS=<pasta do SMTP> PW_CHROMIUM=<chromium> node scripts/auditoria/j1-identidade.mjs` (idem j2–j4).

## 10. Pendências de produto, integrações indisponíveis e limitações

| Pendência | Tipo | Prioridade |
|---|---|---|
| Comentários em elemento, bloqueio de elemento, comparar e restaurar versão | Ausente (colaboração) | Alta |
| Doc do RF29 (ações, Copilot, Autopilot) e catálogo de ações no backend | Documentação/arquitetura | Alta |
| IFC com materiais (`IfcMaterial`, `IfcRelAssociatesMaterial`) | Parcial | Média |
| PDF das pranchas; STEP (citado no título do RF15) | Ausente | Média |
| QR do TOTP na tela (hoje: chave e link `otpauth`) | Clareza | Média |
| `ESTILO_COTA`, `BLOCO`, `INSERIR`, `LOCALIZAR` (o código `BLOCO` também nomeia o bloco de fundação — ambíguo) | Ausente | Média |
| `PapelObra.veDadoDeSaude()` sem caso de uso; "pedidos do fornecedor" sem módulo | Lacuna documental | Baixa |
| `/actuator/health` fica 503 quando o SMTP cai (saúde atrelada a dependência não crítica) | Operação | Baixa |
| Doc do RF05 descreve serviço Python; código é Java | Documentação | Baixa |
| IA real (Claude) | Dependente de integração (chave e contrato) | — |
| DWG | Dependente de integração (biblioteca ODA) | — |
| Consulta automática de CREA/CAU | Dependente de integração | — |
| Saga de eliminação após 30 dias, expiração de convite e de sessão | Não verificado em jornada (tempo) | — |
| Desempenho com modelos grandes | Não verificado | — |
