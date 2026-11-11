"use client";
import { useEffect, useState } from "react";
import { CIMENTOS, avaliarCimento, cimentosComDosagem, cimentoPorId, material, orientarCimento, type DosagemCimentoDocumentada, type Elemento, type EspecificacaoCimento } from "@cca/notacao-vi";
import { Botao } from "@/components/ui";
import { get, post, mensagemDeErro } from "@/lib/api";
import { usePapeis } from "@/lib/sessao";

type Registro = Omit<DosagemCimentoDocumentada, "id" | "estado"> & { produto: string; dataEnsaio: string; validoAte: string; metodoEstatistico: string };
type Visao = { id: string; estado: "PENDENTE" | "VALIDADO"; dados: Registro; confirmadoPor?: string; referenciaValidacao?: string; contextoCompativel: boolean; versaoModeloAplicavel: number };
type Orientacao = { cimentoIds: string[]; origem: string; mensagem: string };
type Props = {
  elemento: Elemento; podeEditar: boolean; onAlterar: (campo: string, valor: unknown) => void; obraId?: string;
  versaoModelo?: number; fckProjetoMPa?: number; especificacao?: EspecificacaoCimento;
  onAplicar?: (props: Record<string, unknown>) => void;
};
const HOJE = () => new Date().toISOString().slice(0, 10);

export function PainelCimentos({ elemento, podeEditar, onAplicar, obraId, versaoModelo, fckProjetoMPa, especificacao }: Props) {
  const papeis = usePapeis(obraId);
  const engenheiro = papeis.includes("ENGENHEIRO");
  const [registros, setRegistros] = useState<Visao[]>([]);
  const [ocupado, setOcupado] = useState(false);
  const [erro, setErro] = useState("");
  const [orientacao, setOrientacao] = useState<Orientacao>();
  const [prioridade, setPrioridade] = useState<"GERAL" | "BAIXO_CALOR" | "RESISTENCIA_INICIAL">("GERAL");
  const [ambiente, setAmbiente] = useState("INTERNO");
  const [referencia, setReferencia] = useState("");
  const mat = "materialId" in elemento ? material(elemento.materialId ?? "") : undefined;
  const fck = fckProjetoMPa ?? mat?.physicalProperties.fck_MPa;
  const caminho = `/obras/${obraId}/cimentos`;
  const carregar = async () => {
    if (!obraId || versaoModelo === undefined) { setRegistros([]); return; }
    setRegistros(await get<Visao[]>(`${caminho}/dosagens`, { versaoModelo }));
  };
  useEffect(() => {
    let ativo = true;
    setRegistros([]); setOrientacao(undefined); setErro("");
    if (obraId && versaoModelo !== undefined) get<Visao[]>(`${caminho}/dosagens`, { versaoModelo }).then((r) => { if (ativo) setRegistros(r); }).catch((e) => { if (ativo) setErro(mensagemDeErro(e)); });
    return () => { ativo = false; };
  }, [obraId, versaoModelo, caminho, elemento.id]);
  const comprovadas: DosagemCimentoDocumentada[] = registros.filter((r) => r.contextoCompativel && r.confirmadoPor && r.referenciaValidacao && r.dados.validoAte >= HOJE()).map((r) => ({ ...r.dados, versaoModelo:r.versaoModeloAplicavel, id: r.id, estado: r.estado }));
  const aplicaveis = cimentosComDosagem(fck, versaoModelo, comprovadas);
  const atual = "cimentoId" in elemento ? cimentoPorId(elemento.cimentoId) : undefined;
  const acao = async (fn: () => Promise<void>) => {
    setOcupado(true); setErro("");
    try { await fn(); } catch (e) { setErro(mensagemDeErro(e)); } finally { setOcupado(false); }
  };
  const consultar = () => acao(async () => {
    if (!obraId) {
      setOrientacao({ cimentoIds: [], origem: "ORIENTACAO_TECNICA_SEM_IA", mensagem: "Modo treino: sem chamada de IA. Não há dosagem documentada e confirmada para este fck; não existe cimento elegível para associação." });
      return;
    }
    setOrientacao(await post<Orientacao>(`${caminho}/recomendacao`, { elementoId: elemento.id, ambiente, prioridade }));
  });
  const aplicar = (id: string) => acao(async () => {
    const d = comprovadas.find((r) => r.cimentoId === id && cimentosComDosagem(fck, versaoModelo, [r]).length > 0);
    if (!obraId || !d || versaoModelo === undefined) return;
    await post(`${caminho}/associacao`, { dosagemId: d.id, elementoId: elemento.id, cimentoId: id, versaoModelo });
    onAplicar?.({ cimentoId: id, dosagemCimentoId: d.id });
  });
  const registrar = (form: HTMLFormElement) => acao(async () => {
    const v = new FormData(form);
    const texto = (k: string) => String(v.get(k) ?? "");
    await post(`${caminho}/dosagens`, {
      cimentoId: texto("cimentoId"), produto: texto("produto"), fckProjetoMPa: fck, fckComprovadoMPa: Number(texto("fckComprovadoMPa")),
      referenciaEnsaio: texto("referenciaEnsaio"), origemMemorial: texto("origemMemorial"), dataEnsaio: texto("dataEnsaio"), validoAte: texto("validoAte"),
      metodoEstatistico: texto("metodoEstatistico"), versaoModelo,
      composicao: { aguaLigante: Number(texto("aguaLigante")), agregado: texto("agregado"), consumoCimentoKgM3: Number(texto("consumoCimentoKgM3")), procedimentoCura: texto("procedimentoCura") },
    });
    await carregar(); form.reset();
  });
  const campos = [
    ["produto", "Produto comercial / fabricante / lote", "text"], ["fckComprovadoMPa", "fck comprovado no relatório (MPa)", "number"],
    ["referenciaEnsaio", "Referência do relatório de ensaio", "text"], ["origemMemorial", "Memorial de origem", "text"],
    ["dataEnsaio", "Data do ensaio", "date"], ["validoAte", "Validade da evidência", "date"], ["metodoEstatistico", "Método / análise estatística documentada", "text"],
    ["aguaLigante", "Relação água/ligante", "number"], ["agregado", "Agregados: origem e identificação", "text"],
    ["consumoCimentoKgM3", "Consumo de cimento (kg/m³)", "number"], ["procedimentoCura", "Procedimento de cura e condições de uso", "text"],
  ] as const;
  if (!fck) return null;
  return <section className="flex flex-col gap-2 text-xs border-t border-line-soft pt-3" aria-label="Seleção de cimento">
    <h3 className="font-semibold text-sm">Cimento para concreto fck {fck} MPa</h3>
    <p>Somente dosagens documentadas e confirmadas por engenheiro, compatíveis com o fck e a versão {versaoModelo ?? "não informada"}.</p>
    <p className="text-text-3">Classe do cimento não equivale ao fck do concreto. A confirmação de evidência não libera execução estrutural.</p>
    {atual && <p>Cimento registrado: {atual.nome}. {aplicaveis.some((c) => c.id === atual.id) ? "Dosagem compatível disponível." : "NÃO VERIFICADO: sem evidência vigente para esta versão."}</p>}
    {atual && <><p className="text-text-3">Alterar geometria ou material do contexto estrutural exige retirar a associação e registrar nova evidência. Datas de vigência são comparadas em UTC.</p><Botao pequeno variante="secundario" disabled={!podeEditar || ocupado || !onAplicar} onClick={() => onAplicar?.({ cimentoId: null, dosagemCimentoId: null })}>Remover associação de cimento</Botao></>}
    {!aplicaveis.length ? <p role="status" className="rounded border border-line p-2">Nenhum cimento com dosagem comprovada para este fck e versão. Aplicação bloqueada até registrar e confirmar evidência técnica.</p> : <div className="overflow-x-auto"><table className="w-full text-left"><caption className="text-left">Cimentos com correspondência documentada ao concreto</caption><thead><tr><th>Cimento</th><th>Classe do cimento</th><th>Comprovação</th><th>Seleção</th></tr></thead><tbody>{aplicaveis.map((c) => {
      const compat = especificacao ? avaliarCimento(c.id, especificacao) : undefined;
      const d = comprovadas.find((r) => r.cimentoId === c.id && cimentosComDosagem(fck, versaoModelo, [r]).length);
      return <tr key={c.id} className="border-t border-line-soft"><td className="py-2">{c.nome}</td><td>{c.classe28d ? `${c.classe28d} MPa / 28d` : "ARI"}</td><td>{d?.fckComprovadoMPa} MPa<br />{d?.referenciaEnsaio}{compat && <span>{compat.estado}: {compat.pendencias.join(" ")}</span>}</td><td><Botao pequeno variante="secundario" disabled={!podeEditar || !onAplicar || ocupado || compat?.estado === "INCOMPATIVEL"} onClick={() => void aplicar(c.id)}>Aplicar</Botao></td></tr>;
    })}</tbody></table></div>}
    <details><summary className="cursor-pointer font-medium">Orientação técnica e IA</summary><div className="flex flex-col gap-2 pt-2">
      <label>Ambiente<select className="block min-h-9 w-full rounded border border-line bg-bg" value={ambiente} onChange={(e) => setAmbiente(e.target.value)}><option value="INTERNO">Interno</option><option value="EXTERNO">Externo</option><option value="UMIDO">Úmido</option><option value="MARINHO">Marinho</option></select></label>
      <label>Prioridade<select className="block min-h-9 w-full rounded border border-line bg-bg" value={prioridade} onChange={(e) => setPrioridade(e.target.value as typeof prioridade)}><option value="GERAL">Geral</option><option value="BAIXO_CALOR">Controle térmico</option><option value="RESISTENCIA_INICIAL">Resistência inicial</option></select></label>
      <Botao pequeno variante="secundario" disabled={ocupado || (!!obraId && !podeEditar)} onClick={() => void consultar()}>{ocupado ? "Consultando…" : obraId ? "Consultar IA sobre cimento" : "Orientação local (sem IA)"}</Botao>
      {orientacao && <div role="status"><strong>{orientacao.origem === "IA" ? "Orientação gerada por IA" : "Orientação técnica sem IA"}</strong><p>{orientacao.mensagem}</p><p>Famílias para estudo: {orientacao.cimentoIds.map((id) => cimentoPorId(id)?.nome).filter(Boolean).join(", ")}. Essas indicações não acrescentam opções à tabela sem dosagem comprovada.</p></div>}
    </div></details>
    {obraId && engenheiro && <details><summary className="cursor-pointer font-medium">Registrar evidência de dosagem</summary>
      <p className="mt-2">Transcreva o relatório existente. O sistema não calcula fck a partir da classe de cimento. Salvar cria rascunho; confirmar exige profissional verificado e 2FA.</p>
      <form className="flex flex-col gap-2 mt-2" onSubmit={(ev) => { ev.preventDefault(); void registrar(ev.currentTarget); }}>
        <label>Família/classificação do produto<select name="cimentoId" required className="block min-h-9 w-full border rounded bg-bg border-line">{CIMENTOS.map((c) => <option value={c.id} key={c.id}>{c.nome}</option>)}</select></label>
        {campos.map(([id, rotulo, tipo]) => <label key={id}>{rotulo}<input name={id} type={tipo} step={tipo === "number" ? "any" : undefined} min={tipo === "number" ? "0.0001" : undefined} maxLength={2000} required className="block min-h-9 w-full rounded border border-line bg-bg px-2" /></label>)}
        <Botao pequeno variante="secundario" disabled={ocupado || !podeEditar || versaoModelo === undefined}>Salvar dosagem como rascunho</Botao>
      </form>
      {!!registros.filter((r) => r.estado === "PENDENTE").length && <div className="flex flex-col gap-2 mt-3"><label>Referência de validação profissional / ART / relatório<input value={referencia} onChange={(e) => setReferencia(e.target.value)} className="block min-h-9 w-full rounded border border-line bg-bg px-2" /></label>{registros.filter((r) => r.estado === "PENDENTE").map((r) => <div key={r.id}><p>{cimentoPorId(r.dados.cimentoId)?.nome} · {r.dados.produto} · ensaio {r.dados.referenciaEnsaio} · fck {r.dados.fckComprovadoMPa} MPa</p><Botao pequeno variante="secundario" disabled={!podeEditar || ocupado || !referencia.trim()} onClick={() => void acao(async () => { await post(`${caminho}/dosagens/${r.id}/confirmar`, { referenciaValidacao: referencia }); await carregar(); })}>Confirmar evidência como engenheiro</Botao></div>)}</div>}
    </details>}
    <details><summary className="cursor-pointer">Referência editorial de famílias (sem aplicação)</summary><p>Composição qualitativa, sem percentuais normativos ou certificação de produto.</p>{CIMENTOS.filter((c) => c.classe28d === 32 || !c.classe28d).map((c) => <p className="my-2" key={c.id}><strong>{c.tipo}</strong>: {c.composicao} {c.cuidados} <a href={c.fonte} target="_blank" rel="noreferrer" className="underline">Fonte do fabricante</a></p>)}</details>
    {erro && <p role="alert" className="text-danger">{erro}</p>}
  </section>;
}
