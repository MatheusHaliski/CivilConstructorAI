"use client";
import { use, useRef, useState } from "react";
import Link from "next/link";
import { useQueryClient } from "@tanstack/react-query";
import { Bot, Hand, Play, Square } from "lucide-react";
import { mensagemDeErro } from "@/lib/api";
import { aprova, usePapeis } from "@/lib/sessao";
import { useDiagnostico } from "@/lib/copiloto";
import { Aviso, Botao, Caixa, Cartao, Carregando, Etiqueta, Vazio } from "@/components/ui";
import { CabecalhoObra } from "@/components/obra/comuns";

type Registro = { quando: string; passo: string; resultado: "ok" | "erro" | "parou"; detalhe: string };

/**
 * RF29 · Autopilot: executa, em ordem, só os passos que você autorizou e que criam rascunhos ou jobs
 * (sugestão de lançamento, análise, dimensionamento). Para em qualquer passo que exija aprovação humana
 * e pode ser interrompido a qualquer momento.
 */
export default function PaginaAutopilot({ params }: { params: Promise<{ obraId: string }> }) {
  const { obraId } = use(params);
  const eng = aprova(usePapeis(obraId));
  const d = useDiagnostico(obraId);
  const qc = useQueryClient();
  const [autorizados, setAutorizados] = useState<Record<string, boolean>>({});
  const [rodando, setRodando] = useState(false);
  const [log, setLog] = useState<Registro[]>([]);
  const parar = useRef(false);
  const registrar = (r: Omit<Registro, "quando">) => setLog((l) => [{ ...r, quando: new Date().toLocaleTimeString("pt-BR") }, ...l]);

  const executar = async () => {
    parar.current = false; setRodando(true);
    try {
      for (const p of d.passos) {
        if (parar.current) { registrar({ passo: p.titulo, resultado: "parou", detalhe: "Interrompido por você." }); break; }
        if (p.exigeAprovacao || !p.executar) { registrar({ passo: p.titulo, resultado: "parou", detalhe: p.exigeAprovacao ? `Aguardando decisão humana (${p.exigeAprovacao}).` : "Passo manual: abra a aba indicada." }); break; }
        if (!autorizados[p.id]) { registrar({ passo: p.titulo, resultado: "parou", detalhe: "Passo não autorizado neste roteiro." }); break; }
        try { await p.executar(); registrar({ passo: p.titulo, resultado: "ok", detalhe: "Rascunho/job criado; resultado precisa de revisão." }); }
        catch (e) { registrar({ passo: p.titulo, resultado: "erro", detalhe: mensagemDeErro(e) }); break; }
        await qc.invalidateQueries({ queryKey: ["estrutura", obraId] }); await qc.invalidateQueries({ queryKey: ["analises", obraId] }); await qc.invalidateQueries({ queryKey: ["dims", obraId] });
        break; // um passo por rodada: o diagnóstico é recalculado com os dados novos antes do próximo
      }
    } finally { setRodando(false); }
  };

  return (
    <>
      <CabecalhoObra obraId={obraId} grupo="Assistente IA" secao="Autopilot" descricao="Roteiro de passos com limites explícitos: só executa o que você marcar, um passo por vez, e para em toda aprovação técnica." />
      {!eng && <Aviso tom="info" className="mb-4">Executar o roteiro exige papel de engenheiro(a) nesta obra; você pode ver o plano.</Aviso>}
      <div className="grid gap-4 lg:grid-cols-3">
        <Cartao titulo={<span className="flex items-center gap-2"><Bot className="size-4" aria-hidden />Roteiro proposto</span>} className="lg:col-span-2" descricao="Calculado a partir do estado atual. Passos com aprovação ou manuais interrompem a execução."
          acoes={<>{rodando ? <Botao variante="perigo" icone={<Square className="size-4" aria-hidden />} onClick={() => { parar.current = true; }}>Interromper</Botao>
            : <Botao icone={<Play className="size-4" aria-hidden />} disabled={!eng || !d.passos.length} onClick={() => void executar()}>Executar próximo passo</Botao>}</>}>
          {d.carregando ? <Carregando /> : !d.passos.length ? <Vazio titulo="Nada a executar" descricao="Pelas regras atuais, a obra está em dia." /> : (
            <ol className="flex flex-col gap-2">{d.passos.map((p, i) => (
              <li key={p.id} className="flex items-start justify-between gap-3 rounded-[var(--radius-sm)] border border-line-soft p-3">
                <div className="min-w-0">
                  <p className="text-sm font-medium"><span className="mono text-text-3">{i + 1}.</span> {p.titulo}</p>
                  <p className="text-xs text-text-3">{p.porque}</p>
                  <div className="mt-1 flex flex-wrap gap-1">{p.exigeAprovacao ? <Etiqueta tom="aviso" icone={<Hand className="size-3" aria-hidden />}>para: {p.exigeAprovacao}</Etiqueta> : p.executar ? <Etiqueta tom="ia">automatizável</Etiqueta> : <Etiqueta tom="neutro">manual</Etiqueta>}</div>
                </div>
                <div className="flex shrink-0 flex-col items-end gap-1">
                  {p.executar && !p.exigeAprovacao && <Caixa rotulo="Autorizar" checked={!!autorizados[p.id]} disabled={!eng} onChange={(e) => setAutorizados((a) => ({ ...a, [p.id]: e.target.checked }))} />}
                  <Link href={p.href} className="inline-flex min-h-6 items-center text-xs text-brand-text underline">abrir aba</Link>
                </div>
              </li>))}</ol>
          )}
        </Cartao>
        <Cartao titulo="Registro de execução" descricao="Quem executou, o quê e o resultado (também consta no histórico de IA e de comandos do servidor).">
          {!log.length ? <p className="text-sm text-text-3">Nada executado nesta sessão.</p> : <ul className="flex flex-col gap-2 text-xs">{log.map((r, i) => <li key={i}><Etiqueta tom={r.resultado === "ok" ? "sucesso" : r.resultado === "erro" ? "perigo" : "aviso"}>{r.resultado}</Etiqueta> <span className="mono text-text-3">{r.quando}</span> <span className="font-medium">{r.passo}</span><span className="block text-text-2">{r.detalhe}</span></li>)}</ul>}
        </Cartao>
      </div>
    </>
  );
}
