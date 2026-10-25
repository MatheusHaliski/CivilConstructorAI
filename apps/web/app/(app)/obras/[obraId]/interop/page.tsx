"use client";
import { use, useState } from "react";
import { Box, Download, Link2, Upload } from "lucide-react";
import { api, get, post } from "@/lib/api";
import { useAcao, useDados } from "@/lib/dados";
import { aprova, editaModelo, usePapeis } from "@/lib/sessao";
import type { Exportacao, Pareamento } from "@/lib/tipos";
import { data } from "@/lib/formatos";
import { Aviso, Botao, Cartao, Carregando, Etiqueta, Tabela, Vazio } from "@/components/ui";
import { CabecalhoObra, EstadoEtiqueta } from "@/components/obra/comuns";

type Resumo = { schema: string; totalEntidades: number; porTipo: Record<string, number>; problemas: string[] };

/** RF15 · Exportação IFC 4.3 / STEP AP242, importação IFC para referência e pareamento com o Revit (pyRevit). */
export default function PaginaInterop({ params }: { params: Promise<{ obraId: string }> }) {
  const { obraId } = use(params);
  const papeis = usePapeis(obraId); const edita = editaModelo(papeis); const eng = aprova(papeis);
  const base = `/obras/${obraId}`;
  const exps = useDados<Exportacao[]>(["exportacoes", obraId], `${base}/exportacoes`, { intervalo: 5000 });
  const pares = useDados<Pareamento[]>(["pareamentos", obraId], `${base}/revit/pareamentos`);
  const [resumo, setResumo] = useState<Resumo | null>(null);
  const [codigo, setCodigo] = useState<{ codigo: string; expiraEm: string } | null>(null);
  const exportar = useAcao((formato: "IFC4X3" | "STEP_AP242") => post(`${base}/exportacoes`, { formato }), { invalidar: [["exportacoes", obraId]], sucesso: "Exportação solicitada." });
  const processar = useAcao(() => post(`${base}/exportacoes/processar`), { invalidar: [["exportacoes", obraId]], sucesso: "Fila processada." });
  const baixar = useAcao(async (id: string) => { const r = await get<{ url: string }>(`${base}/exportacoes/${id}/download`); window.open(r.url, "_blank", "noopener"); });
  const importar = useAcao(async (arquivo: File) => {
    const texto = await arquivo.text();
    return api<Resumo>("POST", `${base}/importacoes/ifc`, { corpo: { nomeArquivo: arquivo.name, conteudo: texto } });
  }, { sucesso: "IFC lido para referência." });
  const gerarCodigo = useAcao(() => post<{ pareamentoId: string; codigo: string; expiraEm: string }>(`${base}/revit/pareamentos`), { invalidar: [["pareamentos", obraId]] });
  const revogar = useAcao((id: string) => post(`${base}/revit/pareamentos/${id}/revogar`), { invalidar: [["pareamentos", obraId]], sucesso: "Pareamento revogado." });
  return (
    <>
      <CabecalhoObra obraId={obraId} secao="IFC / Revit" descricao="OpenBIM: IfcProject → IfcSite → IfcBuilding → IfcBuildingStorey → elementos com Psets da Tabela A; GlobalId estável." />
      <div className="grid gap-4 lg:grid-cols-3">
        <Cartao titulo="Exportações" className="lg:col-span-2" acoes={<>{edita && <><Botao pequeno icone={<Box className="size-4" aria-hidden />} carregando={exportar.isPending} onClick={() => exportar.mutate("IFC4X3")}>IFC 4.3</Botao><Botao pequeno variante="secundario" onClick={() => exportar.mutate("STEP_AP242")}>STEP AP242</Botao></>}{eng && <Botao pequeno variante="fantasma" onClick={() => processar.mutate()}>Processar fila</Botao>}</>}>
          {exps.isLoading ? <Carregando /> : <Tabela linhas={exps.data ?? []} chave={(e) => e.id} vazio={<Vazio titulo="Nenhuma exportação" />} colunas={[
            { titulo: "Formato", celula: (e) => <Etiqueta tom="marca">{e.formato}</Etiqueta> }, { titulo: "Modelo", celula: (e) => `v${e.versaoModelo}` }, { titulo: "Estado", celula: (e) => <EstadoEtiqueta estado={e.estado} /> }, { titulo: "Quando", celula: (e) => data(e.criadaEm) },
            { titulo: "", celula: (e) => e.estado === "CONCLUIDO" ? <Botao pequeno variante="secundario" icone={<Download className="size-4" aria-hidden />} onClick={() => baixar.mutate(e.id)}>Baixar</Botao> : e.erro ? <span className="text-xs text-danger">{e.erro}</span> : null },
          ]} />}
        </Cartao>
        <Cartao titulo="Importar IFC (referência)">
          <label className="flex flex-col gap-1 text-sm"><span className="font-medium">Arquivo .ifc</span><input type="file" accept=".ifc" disabled={!edita} onChange={(e) => { const a = e.target.files?.[0]; if (a) importar.mutate(a, { onSuccess: (r) => setResumo(r) }); }} /></label>
          {importar.isPending && <Carregando texto="Lendo entidades…" />}
          {resumo && <div className="text-xs"><p className="font-medium">{resumo.schema} · {resumo.totalEntidades} entidades</p><ul className="mono">{Object.entries(resumo.porTipo).map(([k, v]) => <li key={k}>{k}: {v}</li>)}</ul>{resumo.problemas.length > 0 && <ul className="text-danger mt-1">{resumo.problemas.map((p, i) => <li key={i}>{p}</li>)}</ul>}</div>}
          <p className="text-xs text-text-3">Nenhum dado pessoal entra no IFC além do responsável técnico autorizado.</p>
        </Cartao>
        <Cartao titulo="Pareamento com o Revit (pyRevit)" className="lg:col-span-3" acoes={edita && <Botao pequeno icone={<Link2 className="size-4" aria-hidden />} carregando={gerarCodigo.isPending} onClick={() => gerarCodigo.mutate(undefined, { onSuccess: (r) => setCodigo(r) })}>Gerar código</Botao>}>
          {codigo && <Aviso tom="info" titulo={`Código: ${codigo.codigo}`}>Digite no painel CivilConstructorAI do Revit até {data(codigo.expiraEm)}. O token do dispositivo fica só no Revit.</Aviso>}
          <Tabela linhas={pares.data ?? []} chave={(p) => p.id} vazio={<Vazio icone={<Upload className="size-8" aria-hidden />} titulo="Nenhum dispositivo pareado" />} colunas={[
            { titulo: "Dispositivo", celula: (p) => p.dispositivo ?? "—" }, { titulo: "Estado", celula: (p) => <EstadoEtiqueta estado={p.estado} /> }, { titulo: "Pareado em", celula: (p) => data(p.pareadoEm) }, { titulo: "Expira", celula: (p) => data(p.expiraEm) },
            { titulo: "", celula: (p) => edita && p.estado !== "REVOGADO" ? <Botao pequeno variante="fantasma" onClick={() => revogar.mutate(p.id)}>Revogar</Botao> : null },
          ]} />
        </Cartao>
      </div>
    </>
  );
}
