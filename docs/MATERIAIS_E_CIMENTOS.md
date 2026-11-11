# Materiais, acabamentos e seleção de cimento

O desenho permite compor paredes e lajes com camadas físicas, sem substituir o material resistente do núcleo. O painel **Propriedades → Materiais e acabamentos** inclui reboco, semi-reboco configurável, chapisco, argamassa, azulejo, porcelanato, granito, mármore, gesso, chapas drywall ST/RU/RF e pintura. Alvenaria e núcleo de divisória seca são materiais da parede.

## Uso no desenho

1. Criar uma parede ou laje e selecioná-la na planta ou perspectiva.
2. Adicionar camadas na face desejada, em ordem do núcleo para fora. A face A é a normal esquerda de p1→p2; B é a direita. Lajes têm faces superior e inferior.
3. Definir espessura, dimensões das peças, junta, perdas de compra e especificação qualitativa. Medidas de edição usam mm; armazenamento usa m.
4. Conferir a composição em 3D. Portas/janelas recortam o núcleo e todas as camadas; as medições descontam a união dos vãos, sem descontar sobreposições duas vezes.
5. Consultar área bruta/líquida, volume, área de compra, peças estimadas e peso. A página de quantitativos e as exportações CSV/XLSX também incluem as camadas.

Formatos do catálogo são configurações geométricas ou exemplos de produtos identificados, não confirmação de disponibilidade comercial. “Semi-reboco” não é substância com densidade ou espessura padronizada: é uma configuração editável. O número de peças é estimativa por área modular, sem otimização de cortes ou arredondamento por caixas.

Em drywall, a espessura da parede representa o núcleo/cavidade; cadastrar chapas em cada face. A massa do núcleo contém perfis, isolantes e componentes que não estejam nas camadas. Não usar a densidade do gesso sobre todo o volume da divisória nem atribuir desempenho ao fogo/acústico somente pela cor/tipo da chapa.

## Integração com cargas permanentes

A integração é explícita pelo controle **Integrar peso dos acabamentos na carga G**. Até ser ativada, as camadas servem ao desenho/levantamento e seu peso não é lançado automaticamente no MEF.

- Peso por camada: área instalada × massa superficial × 9,80665/1000, ou área × espessura × peso específico. Massa superficial explicitamente informada prevalece quando ambos os campos estão preenchidos.
- Perdas comerciais aumentam compra; não aumentam peso instalado. Estoques de obra são outras ações, não esta parcela permanente.
- Massa ausente permanece desconhecida. Não é zero e impede a integração automática.
- Laje: acrescentar uma única vez o peso das camadas ao peso próprio do núcleo existente.
- Parede: acrescentar núcleo e camadas à viga de apoio explicitamente selecionada. Nesta implementação, parede/viga devem coincidir integralmente em extensão, alinhamento e pavimento. Apoio parcial ou por placa exige outro modelo e é recusado.
- A existência de cargas permanentes manuais de área na laje ou lineares na viga pode duplicar o peso: o cálculo recusa esses conflitos para revisão. Não identifica origem de cargas pela descrição textual.

O ensaio de referência de parede 5×2,8 m com porta 0,8×2,1 m tem área líquida 12,32 m² por face. Uma camada de 20 mm e 20 kN/m³ corresponde a 0,2464 m³ e 4,928 kN. Para laje de 20 m² e núcleo de 12 cm, o peso próprio é 60 kN; camada de 20 mm com 20 kN/m³ acrescenta 8 kN. Esses são benchmarks mecânicos, não especificações liberadas para obra.

As [fontes dos materiais](validacao/FONTES_MATERIAIS_ACABAMENTOS.md) distinguem massa instalada, densidade de produto, intervalos e consumo seco. O catálogo não converte consumo de pó em densidade curada. A densidade previamente preenchida do granito pertence à referência Barre Gray/Polycor; deve ser substituída quando o produto escolhido for outro.

## Cimento e correspondência com fck

O painel de cimento se aplica a elementos de concreto com fck cadastrado. A tabela de seleção exibe somente cimentos vinculados a dosagens documentadas e confirmadas na obra para o fck exigido, com contexto compatível e evidência vigente. Sem evidência, a tabela não oferece produto para aplicação.

A classe 25/32/40 do cimento não equivale a C25/C30/C40 do concreto. O sistema não calcula fck a partir do nome do cimento nem de uma resposta da IA. A correspondência depende do estudo de dosagem e dos ensaios: água/ligante, agregados, consumo de cimento, cura, produto/lote e avaliação estatística documentada. O campo fck comprovado é transcrição do relatório técnico, não resultado de inferência automática.

Fluxo da obra: engenheiro registra evidência como pendente; profissional autenticado, verificado e com MFA confirma a referência técnica; somente depois a dosagem elegível aparece na tabela. A seleção associa cimento e dosagem em uma única alteração do modelo. O servidor confere os dados persistidos, obra, fck, assinatura, vigência e contexto; enviar um booleano de aprovação ou contornar o painel por comando VI não substitui essa conferência.

A IA auxilia na comparação e explicação das opções elegíveis. A escolha continua sujeita às regras determinísticas e aos documentos da obra. A referência editorial de famílias descreve CP I, CP II-E/Z/F, CP III, CP IV e CP V-ARI, sem habilitar uma aplicação estrutural pelo catálogo. O modo treino não simula uma chamada de IA real.

As dosagens conservam contexto/hash e versão de origem. Uma mudança incompatível no contexto exige nova evidência; a associação não se torna válida apenas por avançar o contador de versão. Consultar as [fontes de cimento](FONTES_CIMENTOS.md) para escopo e lacunas normativas.

As [evidências dos testes](validacao/ENSAIOS_MATERIAIS_CIMENTOS.md) registram os casos e seus limites. Essas funcionalidades mantêm os bloqueios de aprovação estrutural existentes. Cadastro de composição e confirmação profissional de uma dosagem não comprovam, por si, estabilidade, detalhamento, todas as verificações ELU/ELS ou conformidade normativa integral. A alvenaria é desenhada e pode contribuir com peso próprio; seu dimensionamento como sistema resistente de alvenaria estrutural não foi implementado.
