# Diagrama de classes (modelo de domínio consolidado)

Modelo de domínio do CivilConstructorAI dividido em três contextos. Cobre todos os RFs do
[catálogo](rf/README.md): identidade, acesso e LGPD ([RF18](rf/RF18-cadastro-e-verificacao-email.md)–[RF27](rf/RF27-transparencia-ia-revisao-humana.md));
modelo BIM e auditoria ([RF01](rf/RF01-modelagem-vetorial-canvas-webgl.md)–[RF03](rf/RF03-grids-e-pavimentos.md),
[RF06](rf/RF06-auditoria-vaos-fencepost.md), [RF08](rf/RF08-filtro-tolerancias-secoes-minimas.md),
[RF13](rf/RF13-renderizacao-cena-3d.md)–[RF17](rf/RF17-controle-elevacao-pilares.md)); cálculo, geotecnia e
5D ([RF04](rf/RF04-lancamento-elementos-e-cargas.md), [RF05](rf/RF05-diagramas-esforcos-mef.md),
[RF07](rf/RF07-dimensionamento-armaduras.md), [RF09](rf/RF09-perfil-solo-spt.md)–[RF12](rf/RF12-associacao-sinapi.md)).
As classes específicas de cada RF estão na seção 7.3 do seu doc.

Convenções: IDs públicos são **UUID v7**; toda entidade de empresa carrega `tenantId` (RLS no PostgreSQL);
estados são `enum` com transições validadas no domínio ([MAQUINA_ESTADOS.md](MAQUINA_ESTADOS.md)).

## 1. Identidade, acesso e LGPD

```mermaid
classDiagram
  direction LR
  class Conta {
    +UUID id
    +String email
    +String nome
    +LocalDate nascimento
    +TipoConta tipo
    +EstadoConta estado
    +String senhaHashArgon2id
    +boolean mfaAtivo
    +Instant exclusaoAgendadaEm
    +ativar()
    +suspender()
    +pedirExclusao()
    +reativar()
    +anonimizar()
  }
  class EstadoConta {
    <<enumeration>>
    PENDING_VERIFICATION
    ACTIVE
    SUSPENDED
    PENDING_DELETION
    ANONYMOUS_LGPD_DELETED
  }
  class SeloProfissional {
    +TipoRegistro tipo
    +String numero
    +String uf
    +EstadoSelo estado
    +Instant verificadoEm
    +voltarParaPendente()
  }
  class Empresa {
    +UUID id
    +UUID tenantId
    +String razaoSocial
    +String cnpj
  }
  class Obra {
    +UUID id
    +UUID tenantId
    +String nome
    +Geometry perimetro
    +String artRrt
    +EstadoObra estado
    +transitar(EstadoObra)
  }
  class MembroObra {
    +UUID contaId
    +UUID obraId
    +PapelObra papel
  }
  class PapelObra {
    <<enumeration>>
    DONO
    ADMIN
    ENGENHEIRO
    ARQUITETO
    MESTRE_OBRAS
    TECNICO_SEGURANCA
    CLIENTE
    FORNECEDOR
    ORCAMENTISTA
    TRABALHADOR
  }
  class Convite {
    +UUID id
    +String email
    +PapelObra papel
    +String tokenHash
    +Instant expiraEm
    +EstadoConvite estado
    +aceitar(Conta)
    +revogar()
  }
  class Sessao {
    +String idOpaco
    +UUID empresaAtivaId
    +Instant criadaEm
    +Instant sudoAte
    +EtapaLogin etapa
  }
  class SegredoMfa {
    +byte[] totpCifradoAesGcm
    +List~String~ codigosRecuperacaoHash
  }
  class AceiteTermos {
    +String documento
    +String versao
    +Instant aceitoEm
    +String ip
  }
  class ConsentEvent {
    +Finalidade finalidade
    +AcaoConsentimento acao
    +String versaoTexto
    +Instant em
    +String ip
  }
  class UserKey {
    +UUID contaId
    +byte[] dekCifrada
    +destruir()
  }
  class PedidoTitular {
    +TipoPedido tipo
    +EstadoPedido estado
    +LocalDate prazo
  }
  class RegistroOperacao {
    +UUID id
    +UUID atorId
    +String operacao
    +String recurso
    +Instant em
  }
  Conta "1" *-- "0..1" SeloProfissional
  Conta "1" *-- "0..1" SegredoMfa
  Conta "1" *-- "1" UserKey
  Conta "1" *-- "1..*" AceiteTermos
  Conta "1" *-- "0..*" ConsentEvent
  Conta "1" -- "0..*" Sessao
  Conta "1" -- "0..*" PedidoTitular
  Conta ..> EstadoConta
  Empresa "1" *-- "0..*" Obra
  Obra "1" *-- "1..*" MembroObra
  Conta "1" -- "0..*" MembroObra
  MembroObra ..> PapelObra
  Empresa "1" -- "0..*" Convite
  Convite ..> Obra : para
  RegistroOperacao ..> Conta : ator
```

## 2. Modelo BIM, Notação VI, auditoria e IA

```mermaid
classDiagram
  direction LR
  class ModeloBim {
    +UUID id
    +UUID obraId
    +long versao
    +aplicar(ComandoVI) DiffModelo
  }
  class Pavimento {
    +UUID id
    +String nome
    +double elevacaoZ
    +double peDireito
  }
  class Grid {
    +UUID id
    +double offsetBolha
  }
  class Eixo {
    +String nome
    +Direcao direcao
    +double posicao
  }
  class ElementoConstrutivo {
    <<abstract>>
    +UUID id
    +String ifcGuid
    +String materialId
    +Geometria geometria
    +validar() List~Violacao~
  }
  class Pilar {
    +double b
    +double h
    +UUID nivelBase
    +UUID nivelTopo
    +boolean continuo
  }
  class Viga {
    +double b
    +double h
  }
  class Laje {
    +Poligono contorno
    +double espessura
  }
  class Parede {
    +double altura
    +double espessura
  }
  class Abertura {
    +UUID paredeHospedeiraId
    +double largura
    +double altura
    +double peitoril
  }
  class Fundacao {
    <<abstract>>
    +UUID pilarId
  }
  class Sapata
  class BlocoEstacas
  class Radier
  class Material {
    +String materialId
    +String name
    +Ontology ontology
    +PhysicalProperties physicalProperties
    +GeometricConstraints geometricConstraints
    +BaseQuantities baseQuantities
    +UiProperties uiProperties
  }
  class Ontology {
    +String symbolDomain
    +String borderType
    +String category
  }
  class PhysicalProperties {
    +double fck_MPa
    +double thermalTransmittance
    +String fireRating
    +boolean loadBearing
  }
  class GeometricConstraints {
    +double shortCurveTolerance_m
    +double baseOffset
    +double elevation_Z
  }
  class BaseQuantities {
    +String unit
    +double unitCost
    +double grossVolume
    +double netVolume
  }
  class UiProperties {
    +Map attrs
    +Map state
  }
  class ComandoVI {
    +UUID id
    +String codigo
    +Map args
    +long versaoBase
    +OrigemComando origem
    +EstadoComando estado
    +UUID autorId
  }
  class RelatorioAuditoria {
    +UUID comandoId
    +List~Correcao~ correcoes
    +List~Violacao~ violacoes
    +int iteracoes
  }
  class DiffModelo {
    +long versaoNova
    +List criados
    +List alterados
    +List removidos
  }
  class RascunhoIA {
    +UUID id
    +TipoRascunho tipo
    +EstadoRascunho estado
    +UUID aprovadoPor
    +aprovar(Conta)
    +rejeitar(String)
  }
  class ChamadaModelo {
    +String rota
    +String modelo
    +String effort
    +String finalidade
    +String baseLegal
    +int inputTokens
    +int outputTokens
    +int cacheReadInputTokens
    +String stopReason
  }
  ModeloBim "1" *-- "1..*" Pavimento
  ModeloBim "1" *-- "1" Grid
  Grid "1" *-- "2..*" Eixo
  Pavimento "1" *-- "0..*" ElementoConstrutivo
  ElementoConstrutivo <|-- Pilar
  ElementoConstrutivo <|-- Viga
  ElementoConstrutivo <|-- Laje
  ElementoConstrutivo <|-- Parede
  ElementoConstrutivo <|-- Abertura
  ElementoConstrutivo <|-- Fundacao
  Fundacao <|-- Sapata
  Fundacao <|-- BlocoEstacas
  Fundacao <|-- Radier
  Parede "1" o-- "0..*" Abertura
  ElementoConstrutivo "*" --> "1" Material
  Material *-- Ontology
  Material *-- PhysicalProperties
  Material *-- GeometricConstraints
  Material *-- BaseQuantities
  Material *-- UiProperties
  ModeloBim ..> ComandoVI : aplica
  ModeloBim ..> DiffModelo : produz
  ComandoVI "1" -- "1" RelatorioAuditoria
  RascunhoIA "1" -- "1..*" ChamadaModelo
  RascunhoIA ..> ComandoVI : pode conter
```

## 3. Cálculo estrutural, geotecnia, 5D e pranchas

```mermaid
classDiagram
  direction LR
  class Carga {
    +TipoCarga tipo
    +String casoCarga
    +double valor
    +String unidade
  }
  class CombinacaoCarga {
    +String nome
    +EstadoLimite estadoLimite
    +Map~String,Double~ coeficientes
  }
  class AnaliseEstrutural {
    +UUID id
    +long versaoModelo
    +EstadoJob estado
    +List~ResultadoBarra~ resultados
  }
  class ResultadoBarra {
    +UUID elementoId
    +String combinacao
    +double[] N
    +double[] V
    +double[] M
    +double[] T
  }
  class Dimensionamento {
    +UUID elementoId
    +String norma
    +double asLongitudinal_cm2
    +double asTransversal_cm2m
    +boolean verifica
    +String memoriaCalculo
  }
  class FuroSondagem {
    +String codigo
    +Point localizacao
    +double cotaBoca
    +double nivelAgua
  }
  class CamadaSolo {
    +String tipoSolo
    +double cotaTopo
    +double cotaBase
  }
  class LeituraSpt {
    +double profundidade
    +int nspt
    +boolean impenetravel
  }
  class Quantitativo {
    +long versaoModelo
    +String pavimento
    +String materialId
    +double concreto_m3
    +double forma_m2
    +double aco_kg
    +boolean desatualizado
  }
  class Orcamento {
    +UUID id
    +String uf
    +YearMonth dataBase
    +boolean desonerado
    +String versaoTabelaSinapi
    +EstadoRascunho estado
  }
  class ItemOrcamento {
    +String codigoSinapi
    +String descricao
    +String unidade
    +BigDecimal quantidade
    +BigDecimal custoUnitario
    +BigDecimal total
    +boolean geradoPorIA
  }
  class ComposicaoSinapi {
    +String codigo
    +String descricao
    +String unidade
    +BigDecimal custo
    +YearMonth referencia
  }
  class Prancha {
    +TipoPrancha tipo
    +String escala
    +String formato
    +long versaoModelo
    +String chaveS3
  }
  Carga "*" --> "1" CombinacaoCarga : participa
  AnaliseEstrutural "1" *-- "*" ResultadoBarra
  AnaliseEstrutural ..> CombinacaoCarga : usa
  Dimensionamento ..> ResultadoBarra : usa
  FuroSondagem "1" *-- "1..*" CamadaSolo
  FuroSondagem "1" *-- "1..*" LeituraSpt
  Orcamento "1" *-- "1..*" ItemOrcamento
  ItemOrcamento "*" --> "1" ComposicaoSinapi
  ItemOrcamento "*" --> "1" Quantitativo
```
