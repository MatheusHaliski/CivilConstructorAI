"use client";
import { Globe2, ShieldOff, Sparkles } from "lucide-react";
import { useDados } from "@/lib/dados";
import { Aviso, CabecalhoPagina, Cartao, Carregando, Etiqueta } from "@/components/ui";

type Rota = { id: string; rf: string; finalidade: string; baseLegal: string; effort: string; dadosEnviados: string; nuncaEnviado: string; localProcessamento: string; retencao: string; modelo: string };

/** RF27 · Transparência: o que vai ao modelo, finalidade, base legal, transferência internacional e retenção, por rota. */
export default function PaginaUsoIa() {
  const q = useDados<Rota[]>(["ia-uso"], "/ia/uso");
  return (
    <>
      <CabecalhoPagina titulo="Uso de IA" descricao="Onde a IA ajuda, o que ela recebe e o que nunca recebe. Tudo que a IA gera nasce como rascunho e só vale após aprovação de profissional habilitado." />
      <Aviso tom="info" className="mb-4" titulo="Transferência internacional (LGPD Art. 33)">O processamento acontece na Anthropic (suboperadora). Nenhum dado pessoal é enviado ao modelo; você pode pedir revisão humana de qualquer decisão automatizada (Art. 20).</Aviso>
      {q.isLoading ? <Carregando /> : (
        <div className="grid gap-4 md:grid-cols-2">
          {(q.data ?? []).map((r) => (
            <Cartao key={r.id} titulo={<span className="flex items-center gap-2"><Sparkles className="size-4 text-ai-line" aria-hidden />{r.finalidade}</span>} descricao={`${r.rf} · ${r.baseLegal}`} acoes={<Etiqueta tom="ia">{r.modelo}</Etiqueta>}>
              <dl className="grid gap-2 text-sm">
                <div><dt className="text-text-3 text-xs">Enviado ao modelo</dt><dd>{r.dadosEnviados}</dd></div>
                <div><dt className="text-text-3 text-xs flex items-center gap-1"><ShieldOff className="size-3.5" aria-hidden />Nunca enviado</dt><dd>{r.nuncaEnviado}</dd></div>
              </dl>
              <p className="flex gap-4 flex-wrap text-xs text-text-2"><span className="flex items-center gap-1"><Globe2 className="size-3.5" aria-hidden />{r.localProcessamento}</span><span>Retenção: {r.retencao}</span><span>Esforço: {r.effort}</span><span className="mono">rota {r.id}</span></p>
            </Cartao>
          ))}
        </div>
      )}
    </>
  );
}
