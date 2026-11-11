# Verificação de materiais, acabamentos e cimentos

Rodada de 11/11/2026. Uso: [materiais e cimentos](../MATERIAIS_E_CIMENTOS.md). Fontes: [materiais](FONTES_MATERIAIS_ACABAMENTOS.md) e [cimentos](../FONTES_CIMENTOS.md).

## Evidências executadas

- **125 testes unitários Java**, sem falhas/erros/ignorados nos relatórios dos módulos. Cobrem camada, união de vãos, massa desconhecida, núcleo seco, paridade de limites, pesos e exportações, além das regressões existentes.
- **21 testes de integração** com PostGIS/Valkey reais: CimentoIT (3), ModeloComandoIT (6), AnaliseIT (4), DimensionamentoIT (5), OrcamentoIT (3). CimentoIT foi repetido após a versão final do hash de contexto; seus três casos passaram novamente.
- **37 testes TypeScript**: 22 do pacote Notação VI e 15 do app. Incluem seleção por evidência, massa/área/compra, desfazer/refazer, geometria/modulação e remoção de campos opcionais no JSON enviado ao servidor.
- **2 testes Playwright** no treino: parede/porta, núcleo de alvenaria, camadas nas duas faces, espessuras/formatos/juntas, 3D e ausência de cimento selecionável sem dosagem. Apenas sessão foi controlada; desenho e comandos são reais. [Perspectiva capturada](acabamentos3d.png).
- Build de produção Next.js, typecheck web/Notação VI/tokens, documentos RF e `git diff --check` aprovados.

## Casos significativos

| Caso | Resultado conferido |
|---|---|
| Parede 5×2,8 m; porta 0,8×2,1 m, inclusive duplicata sobreposta | 12,32 m² líquidos por face; desconta união dos vãos |
| Camada de 20 mm e 20 kN/m³ nessa parede | 0,2464 m³ e 4,928 kN por face; 10% de perda aumenta compra para 13,552 m² sem aumentar peso |
| Laje de 20 m², núcleo de concreto de 12 cm e camada de 20 mm a 20 kN/m³ | 60 kN + 8 kN = 68 kN, sem duplicar núcleo |
| Núcleo seco sem massa de perfis/isolantes | Integração recusada mesmo com massa das chapas informada |
| Apoio parcial/desalinhado ou carga manual conflitante | Integração recusada para revisão do caminho de cargas ou duplicidade |
| Cimento sem registro persistido confirmado | Sem recomendação elegível; VI forjado recebe 422 e versão permanece intacta |
| Dosagem confirmada com fck comprovado suficiente | Associação atômica aceita; metadata não se autoinvalida com avanço de versão |
| Troca posterior para fck incompatível | Escrita recusada; remover associação ou registrar nova evidência |
| Arquiteto tentando registrar/confirmar como engenheiro | Acesso recusado; confirmação exige papel, selo e MFA verificados no servidor |
| Evidência de outra obra | Acesso recusado; segregação por obra/tenant e RLS |
| IA sugere ID sem dosagem correspondente | Saída descartada; fallback limitado ao conjunto elegível e identificado como sem IA |

## Reprodução principal

```sh
mvn -f backend/pom.xml -pl cca-web -am -Dit.test=CimentoIT,ModeloComandoIT,AnaliseIT,DimensionamentoIT,OrcamentoIT -Dfailsafe.failIfNoSpecifiedTests=false verify
npm --workspace @cca/notacao-vi test
npm --workspace @cca/web test
npm --workspace @cca/web run typecheck
npm --workspace @cca/web run build
# Com frontend em execução e Chromium disponível:
PW_CHROMIUM=/usr/bin/chromium npm --workspace @cca/web run e2e -- materiais-acabamentos.spec.ts
```

Os testes de API usam dados sintéticos para conferir regras de software. Não inserem dosagens fictícias na biblioteca de produção nem comprovam desempenho real do concreto. Nenhuma chamada ao provider real de IA foi necessária: seu contrato foi exercitado por respostas controladas, incluindo IDs recusados. Os bloqueios globais de aprovação estrutural permanecem.
