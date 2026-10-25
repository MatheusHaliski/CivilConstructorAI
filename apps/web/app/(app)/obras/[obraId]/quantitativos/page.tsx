"use client";
import { use } from "react";
import { Download } from "lucide-react";
import { post } from "@/lib/api";
import { useAcao, useDados } from "@/lib/dados";
import { aprova, usePapeis } from "@/lib/sessao";
import { numero } from "@/lib/formatos";
import { Alternar, Aviso, Botao, Cartao, Carregando, Etiqueta, Tabela, Vazio } from "@/components/ui";
import { CabecalhoObra, usePavimentos } from "@/components/obra/comuns";

type Totais = { concretoM3: number; formaM2: number; acoKg: number; alvenariaM2: number };
type Linha = { pavimentoId: string; elementoId: string; tipo: string; rotulo?: string; materialId?: string; concretoM3: number; formaM2: number; acoKg: number; alvenariaM2: number; origemAco?: string };
/** espelha QuantitativoService.Visao */
type Visao = { id: string; versaoModelo: number; hash: string; estado: string; liberadoCliente: boolean; linhas: Linha[]; totaisPorPavimento: Record<string, Totais>; totalGeral: Totais };

/** RF11 · Quantitativos determinísticos (sem IA) por pavimento/elemento/material, exportáveis em CSV/XLSX. */
export default function PaginaQuantitativos({ params }: { params: Promise<{ obraId: string }> }) {
  const { obraId } = use(params);
  const eng = aprova(usePapeis(obraId));
  const q = useDados<Visao>(["quantitativos", obraId], `/obras/${obraId}/quantitativos`);
  const pav = usePavimentos(obraId);
  const nomePav = (id: string) => pav.find((p) => p.id === id)?.nome ?? "—";
  const liberar = useAcao((v: { id: string; liberar: boolean }) => post(`/obras/${obraId}/quantitativos/${v.id}/liberar-cliente`, undefined, { liberar: v.liberar }), { invalidar: [["quantitativos", obraId]], sucesso: "Visibilidade dos totais para o cliente atualizada." });
  const d = q.data; const t: Partial<Totais> = d?.totalGeral ?? {}; const id = d?.id;
  const linhas = d?.linhas ?? [];
  return (
    <>
      <CabecalhoObra obraId={obraId} secao="Quantitativos" descricao="Concreto (m³), fôrma (m²) e aço (kg) pelo modelo auditado; interseções não contam duas vezes." acoes={<><a href={`/api/v1/obras/${obraId}/quantitativos/export?formato=csv`} download><Botao variante="secundario" icone={<Download className="size-4" aria-hidden />}>CSV</Botao></a><a href={`/api/v1/obras/${obraId}/quantitativos/export?formato=xlsx`} download><Botao variante="secundario" icone={<Download className="size-4" aria-hidden />}>XLSX</Botao></a></>} />
      {q.isLoading ? <Carregando /> : !d ? <Vazio titulo="Sem quantitativo" descricao="Lance elementos no modelo." /> : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {d.estado === "DESATUALIZADO" && <Aviso tom="aviso" className="sm:col-span-2 lg:col-span-4">O modelo mudou desde este levantamento (v{d.versaoModelo}); ele será recalculado na próxima consulta.</Aviso>}
          {[["Concreto", t.concretoM3, "m³"], ["Fôrma", t.formaM2, "m²"], ["Aço", t.acoKg, "kg"], ["Alvenaria", t.alvenariaM2, "m²"]].map(([r, v, u]) => <Cartao key={String(r)}><p className="text-sm text-text-3">{r}</p><p className="text-3xl font-semibold tabular">{numero(v as number, 2)} <span className="text-base font-normal text-text-3">{u}</span></p></Cartao>)}
          <Cartao titulo={`Por elemento (modelo v${d.versaoModelo ?? "—"})`} className="sm:col-span-2 lg:col-span-4" acoes={<Etiqueta tom="neutro">determinístico · sem IA</Etiqueta>}>
            <Tabela linhas={linhas} chave={(l) => l.elementoId} vazio={<Vazio titulo="Nenhuma linha" />} colunas={[
              { titulo: "Pavimento", celula: (l) => nomePav(l.pavimentoId) }, { titulo: "Elemento", celula: (l) => <span>{l.rotulo ?? "—"} <span className="text-xs text-text-3">{l.tipo.toLowerCase()}</span></span> }, { titulo: "Material", celula: (l) => <span className="mono text-xs">{l.materialId ?? "—"}</span> },
              { titulo: "Concreto (m³)", celula: (l) => numero(l.concretoM3, 3), numerica: true }, { titulo: "Fôrma (m²)", celula: (l) => numero(l.formaM2, 2), numerica: true }, { titulo: "Aço (kg)", celula: (l) => numero(l.acoKg, 1), numerica: true }, { titulo: "Alvenaria (m²)", celula: (l) => numero(l.alvenariaM2, 2), numerica: true },
            ]} />
          </Cartao>
          {eng && id && <Cartao titulo="Cliente vê (DN-02)"><Alternar rotulo="Totais liberados ao cliente" ligado={!!d.liberadoCliente} onChange={(v) => liberar.mutate({ id, liberar: v })} /></Cartao>}
        </div>
      )}
    </>
  );
}
