"use client";
import { use, useMemo, useState } from "react";
import Link from "next/link";
import { useAcao, useDados } from "@/lib/dados";
import { post } from "@/lib/api";
import { aprova, usePapeis } from "@/lib/sessao";
import type { AlertaTransicao, Analise, Dimensionamento, RascunhoIa } from "@/lib/tipos";
import { data } from "@/lib/formatos";
import { Aviso, Botao, Carregando, Cartao, Etiqueta, Selecao, Tabela, Vazio } from "@/components/ui";
import { CabecalhoObra } from "@/components/obra/comuns";
import { EtiquetaVerificacao, type ResultadoAnalise, type ResultadoElemento } from "@/components/obra/resultados-estrutura";

type Origem = "DETERMINISTICA" | "IA" | "HUMANA";
type Falha = { id: string; elemento: string; localizacao: string; regra: string; evidencia: string; impacto: string; sugestao: string; estado: string; origem: Origem; fonte: string; link?: string };
type Base = { naoInformado: string[]; classeAgressividade?: string };

const ORIGEM: Record<Origem, { rotulo: string; tom: "info" | "ia" | "marca" }> = { DETERMINISTICA: { rotulo: "regra determinística", tom: "info" }, IA: { rotulo: "IA (rascunho)", tom: "ia" }, HUMANA: { rotulo: "decisão humana", tom: "marca" } };

/** o que o software não verifica: listado sempre, para que a ausência de falhas não seja lida como aprovação */
const NAO_VERIFICADO = [
  "Instalações elétricas, água fria, água quente, esgoto e ventilação: não há modelo de redes nem verificação (proposta em docs/propostas).",
  "Compatibilização com instalações (folgas e conflitos funcionais): não verificada — o modelo não tem elementos de instalações.",
  "Perfis metálicos e contraventamentos (ABNT NBR 8800): sem verificação de resistência ou estabilidade.",
  "Efeitos de 2ª ordem globais (γz, P-Δ), punção em lajes, torção, fissuração e flecha diferida: não verificados.",
  "Lajes como placas, lajes não retangulares ou com borda livre: só aproximação por comprimento de apoio.",
  "Resistência ao fogo, desempenho térmico e acústico: não verificados.",
  "Fundações: verificações do módulo de geotecnia não cobrem recalque diferencial nem interação solo-estrutura.",
];

/** espelha CoordenacaoService.Visao */
type Conflito = { id: string; tipo: string; regra: string; elementos: string[]; mensagem: string; evidencia: string; estado: string; justificativa?: string; versaoDetectada: number; versaoVerificada: number };
const ESTADO_CONFLITO: Record<string, { rotulo: string; tom: "perigo" | "info" | "sucesso" | "neutro" }> = { ABERTO: { rotulo: "Aberto", tom: "perigo" }, EM_REVISAO: { rotulo: "Em revisão", tom: "info" }, RESOLVIDO: { rotulo: "Resolvido (reverificado)", tom: "sucesso" }, ACEITO: { rotulo: "Aceito com justificativa", tom: "neutro" } };

function Coordenacao({ obraId }: { obraId: string }) {
  const b = `/obras/${obraId}/coordenacao`;
  const pode = aprova(usePapeis(obraId));
  const lista = useDados<Conflito[]>(["conflitos", obraId], `${b}/conflitos`);
  const verificar = useAcao(() => post<{ versaoModelo: number; detectados: number; novos: number; resolvidos: number; reabertos: number; naoVerificado: string[] }>(`${b}/verificar`), { invalidar: [["conflitos", obraId]], sucesso: (r) => `Modelo v${r.versaoModelo}: ${r.detectados} conflito(s) · ${r.novos} novo(s) · ${r.resolvidos} resolvido(s) · ${r.reabertos} reaberto(s).` });
  const decidir = useAcao((v: { id: string; estado: string; justificativa?: string }) => post(`${b}/conflitos/${v.id}/estado`, { estado: v.estado, justificativa: v.justificativa }), { invalidar: [["conflitos", obraId]], sucesso: "Estado do conflito atualizado." });
  return (
    <Cartao titulo="Conflitos de coordenação (proposta P-COORD-01)" descricao="Regras determinísticas sobre o modelo: pilares sobrepostos, vigas duplicadas, lajes sobrepostas, abertura atravessando pilar, viga abaixo do topo de porta/janela, viga sem apoio. Resolvido só quando a reverificação deixa de encontrar o conflito; nenhuma regra sugere furar viga ou pilar."
      acoes={<Botao pequeno carregando={verificar.isPending} onClick={() => verificar.mutate()}>Verificar agora</Botao>}>
      {verificar.data && <ul className="text-xs text-text-3 list-disc pl-5">{verificar.data.naoVerificado.map((x) => <li key={x}>Não verificado — {x}</li>)}</ul>}
      {lista.isLoading ? <Carregando /> : !(lista.data ?? []).length ? <Vazio titulo="Nenhuma verificação registrada" descricao="Clique em Verificar agora para rodar as regras na versão atual do modelo." /> : (
        <Tabela linhas={lista.data ?? []} chave={(c) => c.id} colunas={[
          { titulo: "Estado", celula: (c) => <Etiqueta tom={ESTADO_CONFLITO[c.estado]?.tom ?? "neutro"}>{ESTADO_CONFLITO[c.estado]?.rotulo ?? c.estado}</Etiqueta> },
          { titulo: "Tipo / regra", celula: (c) => <span className="text-xs">{c.tipo.toLowerCase()} · <span className="mono">{c.regra}</span></span> },
          { titulo: "Conflito", celula: (c) => <span className="text-xs">{c.mensagem}<span className="block mono text-text-3">{c.evidencia}</span></span> },
          { titulo: "Versões", celula: (c) => <span className="text-xs">detectado v{c.versaoDetectada} · verificado v{c.versaoVerificada}</span> },
          { titulo: "Justificativa", celula: (c) => <span className="text-xs">{c.justificativa ?? "—"}</span> },
          { titulo: "", celula: (c) => <div className="flex gap-1 flex-wrap">{c.estado === "ABERTO" && <Botao pequeno variante="secundario" onClick={() => decidir.mutate({ id: c.id, estado: "EM_REVISAO" })}>Em revisão</Botao>}{(c.estado === "ABERTO" || c.estado === "EM_REVISAO") && pode && <Botao pequeno variante="fantasma" onClick={() => { const j = window.prompt("Justificativa técnica para aceitar o conflito"); if (j) decidir.mutate({ id: c.id, estado: "ACEITO", justificativa: j }); }}>Aceitar…</Botao>}{c.estado === "ACEITO" && <Botao pequeno variante="fantasma" onClick={() => decidir.mutate({ id: c.id, estado: "ABERTO" })}>Reabrir</Botao>}</div> },
        ]} />
      )}
    </Cartao>
  );
}

/** Painel de falhas: agrega o que cada motor registrou, com origem (regra, IA, decisão humana) e estado. */
export default function PaginaFalhas({ params }: { params: Promise<{ obraId: string }> }) {
  const { obraId } = use(params);
  const b = `/obras/${obraId}`;
  const analises = useDados<Analise[]>(["analises", obraId], `${b}/analises`);
  const dims = useDados<Dimensionamento[]>(["dims", obraId], `${b}/dimensionamentos`);
  const alertas = useDados<AlertaTransicao[]>(["obra", obraId, "alertas"], `${b}/modelo/alertas-transicao`);
  const rascunhos = useDados<RascunhoIa[]>(["rascunhos-ia", obraId], `${b}/rascunhos-ia`, { query: { estado: "" } });
  const base = useDados<Base>(["base-calculo", obraId], `${b}/base-calculo`);
  const ultimaAnalise = (analises.data ?? []).find((a) => a.estado === "CONCLUIDO" || a.estado === "OBSOLETO");
  const ultimoDim = (dims.data ?? []).find((d) => d.estado !== "FALHOU");
  const diag = useDados<ResultadoAnalise>(["analise", obraId, ultimaAnalise?.id], `${b}/analises/${ultimaAnalise?.id}/diagramas`, { ativo: !!ultimaAnalise });
  const res = useDados<ResultadoElemento[]>(["dim", obraId, ultimoDim?.id], `${b}/dimensionamentos/${ultimoDim?.id}/resultados`, { ativo: !!ultimoDim });
  const [filtro, setFiltro] = useState("");

  const falhas = useMemo(() => {
    const out: Falha[] = [];
    for (const f of base.data?.naoInformado ?? []) out.push({ id: `base-${f}`, elemento: "Obra", localizacao: "Base de cálculo", regra: "Dados de projeto obrigatórios para os motores", evidencia: `${f}: não informado`, impacto: "Verificações que dependem do dado ficam em “Dados insuficientes”", sugestao: "Preencher em Base de cálculo", estado: "DADOS_INSUFICIENTES", origem: "DETERMINISTICA", fonte: "Base de cálculo", link: `${b}/base-calculo` });
    const desatualAnalise = ultimaAnalise?.estado === "OBSOLETO";
    for (const a of diag.data?.avisos ?? []) out.push({ id: `av-${a.elementoId}-${a.mensagem}`, elemento: a.elementoId ? a.elementoId.slice(0, 8) : "Modelo", localizacao: `Análise v${ultimaAnalise?.versaoModelo ?? "—"}`, regra: `Montagem do modelo analítico (${a.tipo.toLowerCase()})`, evidencia: a.mensagem, impacto: a.tipo === "EXCLUIDO" ? "Elemento ou carga fora da análise" : a.tipo === "APROXIMACAO" ? "Resultado aproximado" : "Premissa adotada", sugestao: a.tipo === "EXCLUIDO" ? "Corrigir a conexão ou a carga no modelo e reanalisar" : "Conferir se a premissa vale para o projeto", estado: desatualAnalise ? "DESATUALIZADA" : a.tipo === "EXCLUIDO" ? "NAO_ATENDIDA" : "NAO_VERIFICADA", origem: "DETERMINISTICA", fonte: "MEF", link: `${b}/analises/mef` });
    const desatualDim = ultimoDim?.estado === "OBSOLETO";
    for (const r of res.data ?? []) for (const v of r.verificacoes ?? []) {
      if (v.estado === "ATENDIDA" || v.estado === "NAO_APLICAVEL") continue;
      out.push({ id: `v-${r.elementoId}-${v.codigo}`, elemento: r.rotulo ?? r.elementoId.slice(0, 8), localizacao: `${r.tipo.toLowerCase()} · ${r.material ?? "material não informado"}`, regra: `${v.nome}${v.referencia ? ` (${v.referencia})` : ""}`, evidencia: v.evidencia ?? "—", impacto: v.etapa === "DETALHAMENTO" ? "Detalhamento incompleto" : "Elemento não pode ser considerado verificado", sugestao: v.motivo ?? "—", estado: desatualDim ? "DESATUALIZADA" : v.estado, origem: "DETERMINISTICA", fonte: "Dimensionamento NBR 6118", link: `${b}/analises/dimensionamento` });
    }
    for (const a of alertas.data ?? []) out.push({ id: `al-${a.id}`, elemento: a.elementoId.slice(0, 8), localizacao: "Modelo BIM", regra: `Transição de pilar (RF17, ${a.tipo})`, evidencia: a.mensagem, impacto: "Carga sem caminho direto até a fundação", sugestao: a.justificativa ? `Aceito: ${a.justificativa}` : "Apoiar o pilar ou aceitar com justificativa", estado: a.estado === "ABERTO" || a.estado === "PENDENTE" ? "NAO_ATENDIDA" : "NAO_APLICAVEL", origem: a.justificativa ? "HUMANA" : "DETERMINISTICA", fonte: "Auditor do modelo", link: b });
    for (const r of rascunhos.data ?? []) if (["GERADO_POR_IA", "EM_REVISAO", "CALCULADO"].includes(String(r.estado))) out.push({ id: `ia-${r.id}`, elemento: String(r.tipo ?? "rascunho"), localizacao: "Rascunhos de IA", regra: "Resultado de IA exige decisão de profissional habilitado", evidencia: String(r.resumo ?? "—"), impacto: "Não vale como projeto até ser aprovado", sugestao: "Revisar e aprovar ou rejeitar", estado: "NAO_VERIFICADA", origem: "IA", fonte: "IA", link: `${b}/ia` });
    return out;
  }, [base.data, diag.data, res.data, alertas.data, rascunhos.data, ultimaAnalise, ultimoDim, b]);

  const carregando = analises.isLoading || dims.isLoading || alertas.isLoading;
  const visiveis = falhas.filter((f) => !filtro || f.estado === filtro || f.origem === filtro);
  const contagem = falhas.reduce<Record<string, number>>((a, f) => { a[f.estado] = (a[f.estado] ?? 0) + 1; return a; }, {});
  return (
    <>
      <CabecalhoObra obraId={obraId} secao="Painel de falhas" grupo="Obra" descricao="Reúne o que os motores registraram: base de cálculo, montagem do modelo analítico, verificações do dimensionamento, alertas do modelo e rascunhos de IA. A origem diz se veio de regra determinística, de IA ou de decisão humana." />
      {carregando ? <Carregando /> : (
        <div className="grid gap-4">
          <Cartao titulo="Situação">
            <div className="flex gap-3 flex-wrap text-xs">{Object.entries(contagem).map(([e, n]) => <span key={e} className="flex items-center gap-1"><EtiquetaVerificacao estado={e} /> {n}</span>)}</div>
            {!ultimaAnalise && <Aviso tom="aviso" titulo="Análise não executada">Sem análise concluída, nada da estrutura foi verificado.</Aviso>}
            {ultimaAnalise && !ultimoDim && <Aviso tom="aviso" titulo="Dimensionamento não executado">Há análise, mas nenhum dimensionamento: resistência dos elementos não verificada.</Aviso>}
            {!falhas.length && <Aviso tom="info" titulo="Nenhuma falha registrada pelos motores">Isso não significa projeto sem falhas: veja abaixo o que o software não verifica.</Aviso>}
            <p className="text-xs text-text-3">Fontes: análise {ultimaAnalise ? `v${ultimaAnalise.versaoModelo} (${data((ultimaAnalise["concluidaEm"] ?? ultimaAnalise["criadoEm"]) as string)})` : "—"} · dimensionamento {ultimoDim ? data(ultimoDim.criadoEm) : "—"}.</p>
          </Cartao>
          <Cartao titulo={`Falhas e pendências (${visiveis.length})`} acoes={<Selecao rotulo="Filtrar" value={filtro} vazio="Todas" onChange={(e) => setFiltro(e.target.value)} opcoes={[{ valor: "NAO_ATENDIDA", rotulo: "Não atendidas" }, { valor: "DADOS_INSUFICIENTES", rotulo: "Dados insuficientes" }, { valor: "NAO_VERIFICADA", rotulo: "Não verificadas" }, { valor: "DESATUALIZADA", rotulo: "Desatualizadas" }, { valor: "IA", rotulo: "Origem IA" }, { valor: "HUMANA", rotulo: "Decisão humana" }]} />}>
            <Tabela linhas={visiveis} chave={(f) => f.id} colunas={[
              { titulo: "Estado", celula: (f) => <EtiquetaVerificacao estado={f.estado} /> }, { titulo: "Origem", celula: (f) => <Etiqueta tom={ORIGEM[f.origem].tom}>{ORIGEM[f.origem].rotulo}</Etiqueta> },
              { titulo: "Elemento / local", celula: (f) => <span className="text-xs"><strong>{f.elemento}</strong><span className="block text-text-3">{f.localizacao}</span></span> },
              { titulo: "Regra", celula: (f) => <span className="text-xs">{f.regra}</span> }, { titulo: "Evidência", celula: (f) => <span className="mono text-xs">{f.evidencia}</span> },
              { titulo: "Impacto", celula: (f) => <span className="text-xs">{f.impacto}</span> }, { titulo: "Ação sugerida", celula: (f) => <span className="text-xs">{f.sugestao}{f.link && <> · <Link className="underline" href={f.link}>abrir</Link></>}</span> },
            ]} />
          </Cartao>
          <Coordenacao obraId={obraId} />
          <Cartao titulo="Não verificado pelo software" descricao="Sempre listado: a ausência de falhas acima não cobre estes itens.">
            <ul className="list-disc pl-5 text-sm">{NAO_VERIFICADO.map((x) => <li key={x}>{x}</li>)}</ul>
          </Cartao>
        </div>
      )}
    </>
  );
}
