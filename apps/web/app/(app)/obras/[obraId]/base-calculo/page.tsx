"use client";
import { use, useEffect, useState } from "react";
import { put } from "@/lib/api";
import { useAcao, useDados } from "@/lib/dados";
import { aprova, usePapeis } from "@/lib/sessao";
import { CATALOGO_MATERIAIS } from "@cca/notacao-vi";
import { Aviso, Botao, Cartao, Carregando, Etiqueta, Selecao, Tabela } from "@/components/ui";
import { CabecalhoObra } from "@/components/obra/comuns";

/** espelha BaseCalculoService.Visao */
type Visao = {
  obraId: string; finalidade?: string; sistemaConstrutivo?: string; classeAgressividade?: string; agregado?: string; versaoCatalogo?: string; versaoCatalogoAtual: string;
  referencias: { id: string; identificador: string; edicao: string; titulo: string; situacao: string; tipo: string }[]; naoInformado: string[]; gravada: boolean;
};

const FINALIDADES = [["RESIDENCIAL", "Residencial"], ["COMERCIAL", "Comercial"], ["INSTITUCIONAL", "Institucional"], ["INDUSTRIAL", "Industrial"], ["MISTA", "Mista"]];
const SISTEMAS = [["CONCRETO_ARMADO_MOLDADO_NO_LOCAL", "Concreto armado moldado no local"], ["CONCRETO_PRE_MOLDADO", "Concreto pré-moldado (não verificado pelo software)"], ["ESTRUTURA_METALICA", "Estrutura metálica (não verificada pelo software)"], ["ALVENARIA_ESTRUTURAL", "Alvenaria estrutural (não verificada pelo software)"], ["MISTO", "Misto"]];
const CAAS = [["I", "I — fraca (rural, submersa)"], ["II", "II — moderada (urbana)"], ["III", "III — forte (marinha, industrial)"], ["IV", "IV — muito forte (industrial química, respingos de maré)"]];
const AGREGADOS = [["basalto", "Basalto (αE 1,2)"], ["diabasio", "Diabásio (αE 1,2)"], ["granito", "Granito (αE 1,0)"], ["gnaisse", "Gnaisse (αE 1,0)"], ["calcario", "Calcário (αE 0,9)"], ["arenito", "Arenito (αE 0,7)"]];
const op = (l: string[][]) => l.map(([valor, rotulo]) => ({ valor: valor!, rotulo: rotulo! }));

/**
 * Base de cálculo da obra: finalidade, sistema construtivo, CAA, agregado, versão do catálogo e referências com edição.
 * É copiada em cada análise e dimensionamento; alterá-la marca esses resultados como desatualizados (não recalcula).
 */
export default function PaginaBaseCalculo({ params }: { params: Promise<{ obraId: string }> }) {
  const { obraId } = use(params);
  const pode = aprova(usePapeis(obraId));
  const base = useDados<Visao>(["base-calculo", obraId], `/obras/${obraId}/base-calculo`);
  const [f, setF] = useState({ finalidade: "", sistemaConstrutivo: "", classeAgressividade: "", agregado: "" });
  useEffect(() => { if (base.data) setF({ finalidade: base.data.finalidade ?? "", sistemaConstrutivo: base.data.sistemaConstrutivo ?? "", classeAgressividade: base.data.classeAgressividade ?? "", agregado: base.data.agregado ?? "" }); }, [base.data]);
  const salvar = useAcao((v: typeof f) => put<Visao>(`/obras/${obraId}/base-calculo`, v), { invalidar: [["base-calculo", obraId], ["analises", obraId], ["dims", obraId]], sucesso: "Base de cálculo gravada. Análises e dimensionamentos anteriores ficaram desatualizados." });
  if (base.isLoading) return <Carregando />;
  const b = base.data;
  const refs = CATALOGO_MATERIAIS.referencias;
  return (
    <>
      <CabecalhoObra obraId={obraId} secao="Base de cálculo e referências" grupo="Obra" descricao="Dados de projeto que os motores de cálculo usam. Cada resultado grava uma cópia desta base; mudar a base não recalcula nada em silêncio: os resultados anteriores passam a “Resultado desatualizado”." />
      <div className="grid gap-4 lg:grid-cols-2">
        <Cartao titulo="Dados de projeto">
          {b && b.naoInformado.length > 0 && <Aviso tom="aviso" titulo="Dados insuficientes">Não informado: {b.naoInformado.join(", ")}. Sem CAA o cobrimento e a classe mínima de concreto não são verificados (estado “Dados insuficientes”).</Aviso>}
          {b?.versaoCatalogo && b.versaoCatalogo !== b.versaoCatalogoAtual && <Aviso tom="aviso" titulo="Catálogo atualizado">A base foi gravada com o catálogo {b.versaoCatalogo}; o atual é {b.versaoCatalogoAtual}. Ao salvar, a base passa a usar o catálogo atual e os resultados emitidos com o anterior ficam desatualizados.</Aviso>}
          <Selecao rotulo="Finalidade" value={f.finalidade} vazio="Não informada" onChange={(e) => setF((v) => ({ ...v, finalidade: e.target.value }))} opcoes={op(FINALIDADES)} disabled={!pode} />
          <Selecao rotulo="Sistema construtivo" value={f.sistemaConstrutivo} vazio="Não informado" onChange={(e) => setF((v) => ({ ...v, sistemaConstrutivo: e.target.value }))} opcoes={op(SISTEMAS)} disabled={!pode} />
          <Selecao rotulo="Classe de agressividade ambiental (ABNT NBR 6118:2023, tabela 6.1)" value={f.classeAgressividade} vazio="Não informada" onChange={(e) => setF((v) => ({ ...v, classeAgressividade: e.target.value }))} opcoes={op(CAAS)} disabled={!pode} ajuda="A escolha é do projetista, conforme o ambiente da obra; o software não infere a CAA pelo endereço." />
          <Selecao rotulo="Agregado graúdo (módulo de elasticidade, 8.2.8)" value={f.agregado} vazio="Não informado (adota-se granito, αE 1,0)" onChange={(e) => setF((v) => ({ ...v, agregado: e.target.value }))} opcoes={op(AGREGADOS)} disabled={!pode} />
          <p className="text-xs text-text-3">Localização: município e UF vêm dos dados da obra (Painel). Catálogo de materiais: {b?.versaoCatalogo ?? b?.versaoCatalogoAtual}.</p>
          {pode ? <Botao carregando={salvar.isPending} onClick={() => salvar.mutate(f)}>Salvar base de cálculo</Botao> : <p className="text-xs text-text-3">Só Engenheiro(a), Dono(a) ou Admin alteram a base.</p>}
        </Cartao>
        <Cartao titulo="Referências usadas pelos motores" descricao="Edição que o software segue. Fontes de terceiros quando o catálogo oficial não foi consultado — confira no catálogo ABNT antes de usar em projeto.">
          <Tabela linhas={b?.referencias ?? []} chave={(r) => r.id} colunas={[{ titulo: "Documento", celula: (r) => <span><strong>{r.identificador}:{r.edicao}</strong><span className="block text-xs text-text-3">{r.titulo}</span></span> }, { titulo: "Tipo", celula: (r) => <Etiqueta>{r.tipo.toLowerCase()}</Etiqueta> }, { titulo: "Situação", celula: (r) => <span className="text-xs">{r.situacao}</span> }]} />
        </Cartao>
        <Cartao titulo="Todas as referências do catálogo" className="lg:col-span-2" descricao="Organismo, identificador, edição, tipo (produto, ensaio, projeto, execução), situação, escopo e fonte consultada. Nenhum item deste catálogo é um selo de conformidade.">
          <Tabela linhas={refs} chave={(r) => r.id} colunas={[
            { titulo: "Documento", celula: (r) => <span><strong>{r.organismo} {r.identificador}:{r.edicao}</strong>{r.cancelada && <Etiqueta tom="perigo" className="ml-1">cancelada</Etiqueta>}<span className="block text-xs text-text-3">{r.titulo}</span></span> },
            { titulo: "Tipo", celula: (r) => <span className="text-xs">{CATALOGO_MATERIAIS.tiposReferencia[r.tipo] ?? r.tipo}</span> }, { titulo: "Escopo no software", celula: (r) => <span className="text-xs">{r.escopo}</span> },
            { titulo: "Situação", celula: (r) => <span className="text-xs">{r.situacao}</span> },
            { titulo: "Fonte", celula: (r) => <span className="text-xs">{r.fonte.url ? <a className="underline" href={r.fonte.url} target="_blank" rel="noreferrer">{r.fonte.natureza}</a> : r.fonte.natureza}{r.fonte.consultadoEm && ` · ${r.fonte.consultadoEm}`}</span> },
          ]} />
        </Cartao>
      </div>
    </>
  );
}
