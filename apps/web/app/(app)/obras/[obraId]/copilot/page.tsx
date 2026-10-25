"use client";
import { use, useState } from "react";
import Link from "next/link";
import { ArrowRight, Lightbulb, ShieldCheck, Sparkles } from "lucide-react";
import { post } from "@/lib/api";
import { useAcao } from "@/lib/dados";
import { editaModelo, usePapeis } from "@/lib/sessao";
import { useDiagnostico } from "@/lib/copiloto";
import { usePavimentos } from "@/components/obra/comuns";
import { AreaTexto, Aviso, Botao, Cartao, Carregando, Etiqueta, Selecao, Vazio } from "@/components/ui";
import { CabecalhoObra } from "@/components/obra/comuns";

const EXEMPLOS = ["Crie uma parede de 15 cm entre A/1 e C/1", "Lance pilares 20x40 em todas as interseções", "Coloque uma porta de 80 cm no meio da parede P1"];

/** RF29 · Copilot: próxima ação sugerida a partir do estado real da obra + pedido em linguagem natural que vira rascunho de IA. */
export default function PaginaCopilot({ params }: { params: Promise<{ obraId: string }> }) {
  const { obraId } = use(params);
  const pode = editaModelo(usePapeis(obraId));
  const d = useDiagnostico(obraId);
  const pav = usePavimentos(obraId);
  const [pavId, setPavId] = useState("");
  const [texto, setTexto] = useState("");
  const pedir = useAcao((t: string) => post<{ geradoPorIa: boolean; aviso: string; comandos: { vi: string }[] }>(`/obras/${obraId}/modelo/texto-natural`, { pavimentoId: pavId || pav[0]?.id, texto: t }),
    { invalidar: [["modelo", obraId]], sucesso: "Pedido interpretado: os comandos entraram como rascunho de IA." });
  const [proximo, ...depois] = d.passos;
  return (
    <>
      <CabecalhoObra obraId={obraId} grupo="Assistente IA" secao="Copilot" descricao="Sugere a próxima ação com base no que já existe na obra. Você decide; nada é gravado sem a sua confirmação." />
      <div className="grid gap-4 lg:grid-cols-3">
        <div className="flex flex-col gap-4 lg:col-span-2">
          <Cartao titulo={<span className="flex items-center gap-2"><Lightbulb className="size-4 text-marca-laranja" aria-hidden />Próxima ação sugerida</span>} descricao="Regras sobre dados verificados do modelo e dos cálculos (não é palpite do modelo de linguagem).">
            {d.carregando ? <Carregando /> : !proximo ? <Vazio titulo="Nada pendente pelas regras atuais" descricao="Modelo, cargas, análise e dimensionamento estão em dia. Continue pelas abas da obra." /> : (
              <div className="flex flex-col gap-3">
                <div className="rounded-[var(--radius-md)] border border-line-soft bg-surface p-4">
                  <p className="text-lg font-semibold">{proximo.titulo}</p>
                  <p className="mt-1 text-sm text-text-2"><Etiqueta tom="info">evidência</Etiqueta> {proximo.porque}</p>
                  {proximo.exigeAprovacao && <p className="mt-1 text-xs text-text-3">Exige decisão humana: {proximo.exigeAprovacao}.</p>}
                  <Link href={proximo.href} className="tap mt-3 inline-flex items-center gap-2 rounded-full bg-text px-4 text-sm font-medium text-bg">Fazer agora <ArrowRight className="size-4" aria-hidden /></Link>
                </div>
                {depois.length > 0 && <div><p className="mb-2 text-xs font-semibold uppercase tracking-wider text-text-3">Depois</p>
                  <ol className="flex flex-col gap-2">{depois.map((p, i) => <li key={p.id} className="flex items-start justify-between gap-3 text-sm"><span><span className="mono text-text-3">{i + 2}.</span> <span className="font-medium">{p.titulo}</span><span className="block text-xs text-text-3">{p.porque}</span></span><Link href={p.href} className="inline-flex min-h-6 shrink-0 items-center text-xs text-brand-text underline">abrir</Link></li>)}</ol></div>}
              </div>
            )}
          </Cartao>
          <Cartao titulo={<span className="flex items-center gap-2"><Sparkles className="size-4 text-ai-line" aria-hidden />Peça em linguagem natural</span>} descricao="A IA traduz o pedido em comandos VI; o auditor valida e tudo entra como rascunho para aprovação em Rascunhos de IA.">
            <Selecao rotulo="Pavimento" value={pavId || pav[0]?.id || ""} onChange={(e) => setPavId(e.target.value)} opcoes={pav.map((p) => ({ valor: p.id, rotulo: p.nome }))} />
            <AreaTexto rotulo="O que você quer fazer?" value={texto} onChange={(e) => setTexto(e.target.value)} placeholder={EXEMPLOS[0]} />
            <div className="flex flex-wrap gap-1">{EXEMPLOS.map((x) => <button key={x} type="button" onClick={() => setTexto(x)} className="tap rounded-full border border-line px-3 text-xs hover:bg-surface">{x}</button>)}</div>
            <Botao icone={<Sparkles className="size-4" aria-hidden />} disabled={!pode || !texto.trim()} carregando={pedir.isPending} onClick={() => pedir.mutate(texto)}>Interpretar e criar rascunho</Botao>
            {pedir.data && <Aviso tom="info" titulo={pedir.data.aviso}><ul className="mono text-xs">{pedir.data.comandos.map((c, i) => <li key={i}>{c.vi}</li>)}</ul><Link href={`/obras/${obraId}/ia`} className="inline-flex min-h-6 items-center text-xs underline">Decidir em Rascunhos de IA</Link></Aviso>}
          </Cartao>
        </div>
        <Cartao titulo="Estado considerado" descricao={d.versao != null ? `Modelo v${d.versao}` : undefined}>
          <dl className="grid grid-cols-2 gap-2 text-sm">{Object.entries(d.contagens).map(([k, v]) => <div key={k}><dt className="text-xs capitalize text-text-3">{k}</dt><dd className="mono text-lg font-semibold">{v}</dd></div>)}</dl>
          <p className="flex items-start gap-2 text-xs text-text-3"><ShieldCheck className="size-4 shrink-0" aria-hidden />Sugestões não aprovam projeto nem garantem conformidade normativa; a responsabilidade técnica continua do profissional com ART/RRT.</p>
        </Cartao>
      </div>
    </>
  );
}
