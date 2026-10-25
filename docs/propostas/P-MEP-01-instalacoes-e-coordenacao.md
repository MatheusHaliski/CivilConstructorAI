# Propostas P-MEP-01 (instalações) e P-COORD-01 (coordenação)

> **Situação: proposta.** Nenhum RF do catálogo (`docs/rf/README.md`) cobre instalações elétricas, água fria, água
> quente, esgoto, ventilação ou compatibilização entre disciplinas. O que está descrito aqui é capacidade nova,
> separada das obrigações já documentadas. Nada nesta proposta é aprovação técnica de projeto: validação do
> software não substitui a responsabilidade do profissional habilitado (ART/RRT).

Auditoria 2026-10, 2ª rodada. Relatório: [AUDITORIA_2026-10_ESTRUTURA_INSTALACOES_MATERIAIS.md](../auditoria/AUDITORIA_2026-10_ESTRUTURA_INSTALACOES_MATERIAIS.md).

## 1. Ponto de partida (antes)

- O modelo BIM (`ModeloBim` em Java, `modelo.ts` no TypeScript) **não tem** eletroduto, bandeja, caixa, quadro,
  tomada, luminária, tubo, conexão, registro, válvula, ramal, caixa de inspeção nem coluna de ventilação.
- Não existe porta (conector) em nenhum elemento: elementos não se ligam uns aos outros por rede.
- O catálogo antigo trazia tubos e condutores apenas como linha de orçamento.
- **Classificação:** representação gráfica = inexistente; modelo com conectividade = inexistente; compatibilidade,
  análise física e dimensionamento = inexistentes.

## 2. O que foi implementado nesta rodada (experimental, fora do modelo BIM)

### 2.1 P-MEP-01 · Verificador de redes — `cca-domain/.../instalacoes/VerificadorRedes.java`

Rede descrita como **componentes** (QUADRO, TOMADA, LUMINARIA, ENTRADA, RESERVATORIO, AQUECEDOR, PONTO_CONSUMO,
APARELHO, CAIXA_INSPECAO, COLETOR…) e **trechos** com origem → destino explícitos, sistema (ELETRICA, AGUA_FRIA,
AGUA_QUENTE, ESGOTO, VENTILACAO), regime (GRAVIDADE, PRESSURIZADO, CONDUTOR), material do catálogo, comprimento, DN,
cotas e, para elétrica, circuito, seção e condutores (F, N, PE).

| Código | Verificação | Natureza da regra | Estado possível |
|---|---|---|---|
| MEP-CON-01 | extremidades ligadas a componentes existentes | lógica do modelo | atendida / não atendida |
| MEP-SIS-01 | sistemas não se misturam (AF↔AQ só no aquecedor; esgoto↔ventilação só no ponto de ventilação) | lógica | não atendida |
| MEP-MAT-01 | material do catálogo indicado para o sistema (`aplicacoes`) | catálogo técnico | não atendida / dados insuficientes |
| MEP-ESG-00 | esgoto pressurizado (recalque) não recebe regra de gravidade | — | não verificado |
| MEP-ESG-01 | declividade mínima 2 % (DN ≤ 75) e 1 % (DN ≥ 100) | ABNT NBR 8160:1999, segundo fontes que citam a norma | atendida / não atendida / dados insuficientes (DN entre 75 e 100) |
| MEP-ESG-02 | contrapendência (destino acima da origem) | física (gravidade) | não atendida |
| MEP-ESG-03 | DN não diminui a jusante | boa prática, **não conferida no texto da norma** | não atendida |
| MEP-ESG-04 | aparelho chega a coletor ou caixa de inspeção | lógica | não atendida |
| MEP-ESG-05 | distância entre inspeções | limite não registrado | **não verificado** |
| MEP-VEN-01/02 | rede de esgoto com ventilação; dimensionamento da ventilação | NBR 8160:1999 | não atendida / **não verificado** |
| MEP-AGU-01..03 | fonte (entrada/reservatório), ponto alimentado, aquecedor alimentado por água fria | lógica | não atendida |
| MEP-AGU-04 | vazões, perdas de carga e pressões | ABNT NBR 5626:2020 — método **não consultado** | **não verificado** |
| MEP-ELE-01..03 | circuito identificado, origem no quadro, continuidade até as cargas, fase e neutro | lógica / física | dados insuficientes / não atendida |
| MEP-ELE-04 | condutor de proteção em circuito com tomada | ABNT NBR 5410:2004 (cláusula não conferida) | não atendida |
| MEP-ELE-05 | Ib = P/(V·FP) ≤ In do disjuntor declarado | física + NBR 5410 | atendida / não atendida / dados insuficientes |
| MEP-ELE-06 | In ≤ Iz | tabelas de capacidade **não transcritas**: Iz precisa vir de fonte identificada | **dados insuficientes** |
| MEP-ELE-07 | queda de tensão ΔV = 2·ρ·L·Ib/S, ρ do cobre a 20 °C, carga no fim do circuito | física; **limite declarado pelo projetista com a fonte** | atendida / não atendida / dados insuficientes |

Regras deliberadas:

- Uma linha desenhada **não** é dimensionamento: sem trecho conectado, nada é verificado.
- Nenhum limite normativo foi inventado. Onde o texto da norma não foi consultado, o estado é *Não verificado* ou
  *Dados insuficientes* e a mensagem diz o que falta.
- `assinatura(rede, parâmetros)` muda com qualquer alteração: um resultado com assinatura diferente da atual está
  **desatualizado**.

Teste: `VerificadorRedesTest` (5 casos com defeitos introduzidos — contrapendência, declividade de 0,5 % em DN 100,
redução de DN a jusante, PVC de água fria na água quente, aquecedor sem alimentação, mistura de sistemas, circuito
sem PE, Ib = 19,69 A > In = 10 A, ΔV = 5,33 % > 4 %, Iz nunca inventado).

### 2.2 P-COORD-01 · Conflitos de coordenação — `cca-domain/.../coordenacao/DetectorConflitos.java`

Sobre o modelo BIM **atual** (estrutura e arquitetura), com a mesma convenção de nível do modelo analítico:

| Regra | Tipo | O que detecta |
|---|---|---|
| GEO-01 | geométrico | pilares que ocupam o mesmo volume (teste de eixos separantes em planta × intervalo em z) |
| GEO-02 | geométrico | vigas colineares sobrepostas (duplicadas) |
| GEO-03 | geométrico | lajes sobrepostas no mesmo pavimento (caixa envolvente — simplificação declarada) |
| CON-01 | construtivo | porta ou janela que atravessa pilar |
| CON-02 | construtivo | viga do pavimento de cima com fundo abaixo do topo da porta ou janela |
| LIG-01 | conectividade | extremidade de viga sem pilar nem viga a menos de 5 cm |

Estados persistidos (`conflito_coordenacao`, V23): **ABERTO → EM_REVISAO → RESOLVIDO | ACEITO**.

- *Resolvido* só é atribuído pela **reverificação**, quando o conflito deixa de ser detectado numa versão posterior
  do modelo (a versão fica registrada). Marcar à mão responde 422.
- *Aceito* exige justificativa e papel aprovador; o conflito continua listado.
- Um conflito resolvido que volta a aparecer é **reaberto**.
- Nenhuma regra propõe furo em viga ou pilar como solução.

Não verificado (sempre devolvido junto da verificação): folgas (exigem instalações modeladas), conflitos funcionais
(acesso a registros, quadros e inspeções) e lajes não retangulares exatas.

API: `GET /obras/{id}/coordenacao/conflitos`, `POST /obras/{id}/coordenacao/verificar`,
`POST /obras/{id}/coordenacao/conflitos/{cid}/estado`. Tela: Obra › Painel de falhas › Conflitos de coordenação.
Testes: `DetectorConflitosTest` (4), `CoordenacaoMateriaisIT` (resolução só por reverificação e reabertura), jornada j5.

## 3. O que falta para virar requisito (proposta de RF)

1. Elementos de instalações nos **dois** motores (Java e TypeScript, com paridade em `comandos-paridade.json`):
   `COMPONENTE_MEP` (tipo, sistema, ponto 3D, portas com direção e DN/seção) e `TRECHO_MEP` (porta de origem →
   porta de destino, material, DN, cotas, regime), com id estável e persistência no modelo versionado.
2. Comandos VI de criação, ligação e edição de trechos; auditor que recusa porta inexistente e trecho sem destino.
3. Ligação do `VerificadorRedes` ao modelo e invalidação por versão (como análise e dimensionamento).
4. Visualização 3D por sistema (filtro, isolar, raio X já existem para a estrutura) e seleção de tubo oculto.
5. Folgas e conflitos funcionais no detector, usando as portas e os DN.
6. Métodos de cálculo de água fria e quente **conferidos no texto da ABNT NBR 5626:2020** e tabelas de capacidade de
   condução **conferidas na ABNT NBR 5410:2004** (ou edição vigente) antes de qualquer verificação automática.
7. Cabos estruturais (protensão, estais): **lacuna registrada**. O software não modela cabo; contraventamento é
   barra de treliça (tração e compressão) e a mensagem do modelo analítico diz "Não é cabo".
