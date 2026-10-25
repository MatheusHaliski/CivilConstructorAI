"use client";
/**
 * Diagnóstico da obra para o Copilot e o Autopilot (RF29). As sugestões saem de regras sobre dados reais
 * (contagens do modelo, estados de jobs); cada uma traz a evidência e, quando automatizável, o passo que o
 * Autopilot pode executar. Nada aqui aprova nada: aprovações continuam humanas (RF27).
 */
import { post } from "@/lib/api";
import { useDados } from "@/lib/dados";
import type { Analise, Carga, Dimensionamento, Furo, VisaoModelo } from "@/lib/tipos";

export type Passo = { id: string; titulo: string; porque: string; href: string; acaoId?: string; executar?: () => Promise<unknown>; exigeAprovacao?: string };

export function useDiagnostico(obraId: string) {
  const b = `/obras/${obraId}`;
  const modelo = useDados<VisaoModelo>(["modelo", obraId], `${b}/modelo`);
  const cargas = useDados<Carga[]>(["estrutura", obraId, "cargas"], `${b}/estrutura/cargas`);
  const lanc = useDados<{ pavimentoId: string; estado: string }[]>(["estrutura", obraId, "lanc"], `${b}/estrutura/lancamentos`);
  const rasc = useDados<{ id: string; estado: string; pavimentoId: string }[]>(["estrutura", obraId, "rascunhos"], `${b}/estrutura/lancamento/rascunhos`);
  const analises = useDados<Analise[]>(["analises", obraId], `${b}/analises`);
  const dims = useDados<Dimensionamento[]>(["dims", obraId], `${b}/dimensionamentos`);
  const furos = useDados<Furo[]>(["furos", obraId], `${b}/sondagens`);
  const carregando = [modelo, cargas, lanc, rasc, analises, dims, furos].some((q) => q.isLoading);
  const m = modelo.data;
  const terreo = [...(m?.pavimentos ?? [])].sort((x, y) => Math.abs(x.elevacao) - Math.abs(y.elevacao))[0];
  const conta = (t: string) => (m?.elementos ?? []).filter((e) => e["tipo"] === t).length;
  const n = { eixos: m?.eixos.length ?? 0, pilares: conta("PILAR"), vigas: conta("VIGA"), lajes: conta("LAJE"), paredes: conta("PAREDE"), aberturas: conta("PORTA") + conta("JANELA") };
  const passos: Passo[] = [];
  if (m) {
    if (n.eixos === 0) passos.push({ id: "malha", titulo: "Criar a malha de eixos", porque: "O modelo não tem eixos; pilares e vigas se apoiam nas interseções.", href: `${b}/acoes?acao=MOD.CRIAR.MALHA`, acaoId: "MOD.CRIAR.MALHA" });
    if (n.eixos > 0 && n.pilares === 0) {
      const pendente = (rasc.data ?? []).find((r) => r.estado === "GERADO_POR_IA");
      passos.push(pendente
        ? { id: "aprovar-lanc", titulo: "Decidir o lançamento sugerido pela IA", porque: "Há um rascunho de lançamento aguardando aprovação do engenheiro.", href: `${b}/estrutura/lancamento-ia`, exigeAprovacao: "Engenheiro com ART/RRT" }
        : { id: "sugerir-lanc", titulo: "Pedir à IA o lançamento de pilares, vigas e lajes", porque: `${n.eixos} eixo(s) e nenhum pilar. A sugestão entra como rascunho.`, href: `${b}/estrutura/lancamento-ia`,
            executar: terreo ? () => post(`${b}/estrutura/lancamento/sugerir`, { pavimentoId: terreo.id }) : undefined });
    }
    if (n.pilares > 0 && n.vigas === 0) passos.push({ id: "vigas", titulo: "Lançar vigas entre os pilares", porque: `${n.pilares} pilar(es) sem nenhuma viga.`, href: `${b}/acoes?acao=MOD.CRIAR.VIGA`, acaoId: "MOD.CRIAR.VIGA" });
    if (n.vigas > 0 && n.lajes === 0) passos.push({ id: "lajes", titulo: "Fechar os panos de laje", porque: `${n.vigas} viga(s) e nenhuma laje.`, href: `${b}/acoes?acao=MOD.CRIAR.LAJE`, acaoId: "MOD.CRIAR.LAJE" });
    if (n.paredes > 0 && n.aberturas === 0) passos.push({ id: "aberturas", titulo: "Inserir portas e janelas", porque: `${n.paredes} parede(s) sem aberturas: quantitativos de alvenaria ficarão superestimados.`, href: `${b}/acoes?acao=MOD.CRIAR.PORTA`, acaoId: "MOD.CRIAR.PORTA" });
    if (n.pilares > 0 && !(cargas.data ?? []).length) passos.push({ id: "cargas", titulo: "Lançar cargas (NBR 6120/6123)", porque: "Nenhuma carga além do peso próprio; a análise sairia subestimada.", href: `${b}/estrutura/cargas` });
    const lancPronto = (lanc.data ?? []).some((l) => l.estado === "PRONTO_PARA_ANALISE" || l.estado === "PRONTO");
    if (n.pilares > 0 && (cargas.data ?? []).length && !lancPronto && terreo) passos.push({ id: "pronto", titulo: "Marcar o lançamento como pronto para análise", porque: "Cargas lançadas, mas o lançamento não foi liberado para o MEF.", href: `${b}/estrutura/cargas`, exigeAprovacao: "Engenheiro" });
    const concluidas = (analises.data ?? []).filter((a) => a.estado === "CONCLUIDO");
    const emCurso = (analises.data ?? []).some((a) => a.estado === "PENDENTE" || a.estado === "EM_EXECUCAO");
    if (lancPronto && !concluidas.length && !emCurso) passos.push({ id: "analise", titulo: "Solicitar análise MEF", porque: "Lançamento pronto e nenhuma análise concluída.", href: `${b}/analises/mef`, executar: () => post(`${b}/analises`) });
    const ultima = concluidas.at(-1);
    if (ultima && !(dims.data ?? []).some((d) => d["analiseId"] === ultima.id)) passos.push({ id: "dimensionar", titulo: "Dimensionar a última análise", porque: `Análise v${ultima.versaoModelo ?? "?"} concluída sem dimensionamento.`, href: `${b}/analises/dimensionamento`, executar: () => post(`${b}/dimensionamentos`, { analiseId: ultima.id }) });
    if ((dims.data ?? []).some((d) => d.estado === "CALCULADO")) passos.push({ id: "aprovar-dim", titulo: "Revisar e aprovar o dimensionamento", porque: "Há dimensionamento calculado aguardando ART.", href: `${b}/analises/dimensionamento`, exigeAprovacao: "Engenheiro com selo verificado e 2FA" });
    if (n.pilares > 0 && !(furos.data ?? []).length) passos.push({ id: "furos", titulo: "Cadastrar sondagens SPT", porque: "Sem furos de sondagem não há perfil de solo para as fundações.", href: `${b}/geotecnia/furos` });
  }
  return { carregando, contagens: n, versao: m?.versao, passos };
}
