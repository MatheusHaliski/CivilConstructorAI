"use client";
import { use, useState } from "react";
import Link from "next/link";
import { AlertTriangle, ArrowRight, ShieldCheck } from "lucide-react";
import { patch, post, put } from "@/lib/api";
import { useAcao, useDados } from "@/lib/dados";
import { aprova, usePapeis } from "@/lib/sessao";
import { ESTADOS_OBRA, TRANSICOES_OBRA, rotuloPapel, type AlertaTransicao, type EstadoObra, type Membro, type VisaoModelo } from "@/lib/tipos";
import { data } from "@/lib/formatos";
import { Alternar, Aviso, Botao, Cartao, Carregando, Dialogo, Entrada, Etiqueta, Selecao, Tabela, Vazio } from "@/components/ui";
import { CabecalhoObra, useObra } from "@/components/obra/comuns";
import { EtiquetaObra } from "../page";

/** RF22 painel da obra · RF17 alertas de transição · RF16 verificação de conclusão · DN-02 liberação ao cliente. */
export default function PaginaObra({ params }: { params: Promise<{ obraId: string }> }) {
  const { obraId } = use(params);
  const obra = useObra(obraId);
  const papeis = usePapeis(obraId);
  const podeAprovar = aprova(papeis);
  const membros = useDados<Membro[]>(["obra", obraId, "membros"], `/obras/${obraId}/membros`);
  const modelo = useDados<VisaoModelo>(["modelo", obraId], `/obras/${obraId}/modelo`);
  const alertas = useDados<AlertaTransicao[]>(["obra", obraId, "alertas"], `/obras/${obraId}/modelo/alertas-transicao`);
  const [transicao, setTransicao] = useState<EstadoObra | null>(null);
  const [motivo, setMotivo] = useState("");
  const [edit, setEdit] = useState(false);
  const [f, setF] = useState({ nome: "", endereco: "", municipio: "", uf: "", artRrt: "" });
  const [resp, setResp] = useState({ contaId: "", artRrt: "" });

  const transicionar = useAcao((v: { para: EstadoObra; motivo?: string }) => post(`/obras/${obraId}/transicoes`, v), { invalidar: [["obra", obraId], ["obras"]], sucesso: "Estado da obra atualizado." });
  const atualizar = useAcao((v: typeof f) => patch(`/obras/${obraId}`, { ...v, uf: v.uf || null }), { invalidar: [["obra", obraId]], sucesso: "Obra atualizada." });
  const responsavel = useAcao((v: typeof resp) => put(`/obras/${obraId}/responsavel-tecnico`, { contaId: v.contaId, artRrt: v.artRrt || null }), { invalidar: [["obra", obraId]], sucesso: "Responsável técnico definido." });
  const liberar = useAcao((liberar: boolean) => patch(`/obras/${obraId}/modelo/liberar-cliente`, { liberar }), { invalidar: [["modelo", obraId]], sucesso: "Visibilidade do modelo para o cliente atualizada." });
  const verificar = useAcao(() => post<{ aprovado?: boolean; violacoes?: { codigo: string; mensagem: string }[] }>(`/obras/${obraId}/modelo/verificar-conclusao`), { sucesso: (r) => (r?.aprovado ? "Modelo fechado: envoltória íntegra." : undefined) });
  const decidir = useAcao((v: { id: string; decisao: string; justificativa?: string }) => post(`/obras/${obraId}/modelo/alertas-transicao/${v.id}/decidir`, { decisao: v.decisao, justificativa: v.justificativa }), { invalidar: [["obra", obraId, "alertas"]], sucesso: "Alerta decidido." });

  if (obra.isLoading) return <Carregando />;
  if (!obra.data) return <Aviso tom="perigo">Obra não encontrada ou sem acesso.</Aviso>;
  const o = obra.data;
  const proximos = TRANSICOES_OBRA[o.estado];
  const abertos = (alertas.data ?? []).filter((a) => a.estado === "ABERTO" || a.estado === "PENDENTE");

  return (
    <>
      <CabecalhoObra obraId={obraId} secao="Painel" acoes={<><EtiquetaObra estado={o.estado} />{podeAprovar && o.estado !== "ARQUIVADA" && <Botao variante="secundario" pequeno onClick={() => { setF({ nome: o.nome, endereco: o.endereco ?? "", municipio: o.municipio ?? "", uf: o.uf ?? "", artRrt: o.artRrt ?? "" }); setEdit(true); }}>Editar dados</Botao>}</>} />
      <div className="grid gap-4 lg:grid-cols-3">
        <Cartao titulo="Dados da obra" className="lg:col-span-2">
          <dl className="grid gap-x-6 gap-y-2 sm:grid-cols-2 text-sm">
            <div><dt className="text-text-3">Endereço</dt><dd>{[o.endereco, o.municipio, o.uf].filter(Boolean).join(" · ") || "—"}</dd></div>
            <div><dt className="text-text-3">ART/RRT</dt><dd>{o.artRrt ?? <span className="text-warning">não informada</span>}</dd></div>
            <div><dt className="text-text-3">Responsável técnico</dt><dd>{membros.data?.find((m) => m.contaId === o.responsavelTecnicoId)?.nome ?? <span className="text-warning">não definido</span>}</dd></div>
            <div><dt className="text-text-3">Seus papéis</dt><dd className="flex gap-1 flex-wrap">{papeis.map((p) => <Etiqueta key={p} tom="marca">{rotuloPapel(p)}</Etiqueta>)}</dd></div>
            <div><dt className="text-text-3">Modelo BIM</dt><dd>{modelo.data ? `v${modelo.data.versao} · ${modelo.data.elementos.length} elementos · ${modelo.data.pavimentos.length} pavimento(s) · ${modelo.data.eixos.length} eixos` : "—"} <Link className="underline text-brand-text" href={`/obras/${obraId}/modelo`}>abrir</Link></dd></div>
          </dl>
          {podeAprovar && o.estado !== "ARQUIVADA" && (
            <div className="border-t border-line-soft pt-3 flex flex-col gap-3">
              <p className="text-sm font-medium">Ciclo de vida</p>
              <div className="flex gap-2 flex-wrap">{proximos.map((p) => <Botao key={p} pequeno variante={p === "ARQUIVADA" ? "perigo" : "secundario"} icone={<ArrowRight className="size-4" aria-hidden />} onClick={() => { setTransicao(p); setMotivo(""); }}>{ESTADOS_OBRA[p].rotulo}</Botao>)}{!proximos.length && <p className="text-sm text-text-3">Obra arquivada: somente leitura.</p>}</div>
              {o.estado === "PLANEJADA" && !o.responsavelTecnicoId && <Aviso tom="aviso">Para sair de Planejada a obra precisa de um(a) Engenheiro(a) responsável.</Aviso>}
            </div>
          )}
        </Cartao>
        <Cartao titulo="Cliente vê (DN-02)" descricao="O cliente só vê o que a empresa libera, por artefato.">
          <Alternar rotulo="Modelo 3D liberado" descricao={modelo.data?.liberadaCliente ? "Snapshot da versão liberada" : "Oculto para o papel CLIENTE"} ligado={!!modelo.data?.liberadaCliente} onChange={(v) => liberar.mutate(v)} disabled={!podeAprovar} />
          <Botao pequeno variante="secundario" icone={<ShieldCheck className="size-4" aria-hidden />} carregando={verificar.isPending} onClick={() => verificar.mutate()}>Verificar conclusão do modelo</Botao>
          {verificar.data && !verificar.data.aprovado && <ul className="text-xs text-danger">{(verificar.data.violacoes ?? []).map((v, i) => <li key={i}>{v.codigo}: {v.mensagem}</li>)}</ul>}
        </Cartao>
        <Cartao titulo="Responsável técnico" className="lg:col-span-1">
          <Selecao rotulo="Membro" value={resp.contaId} onChange={(e) => setResp((v) => ({ ...v, contaId: e.target.value }))} vazio="Escolher…" opcoes={(membros.data ?? []).filter((m) => m.papel === "ENGENHEIRO" || m.papel === "DONO" || m.papel === "ADMIN").map((m) => ({ valor: m.contaId, rotulo: `${m.nome} (${rotuloPapel(m.papel)})` }))} disabled={!podeAprovar} />
          <Entrada rotulo="ART/RRT" value={resp.artRrt} onChange={(e) => setResp((v) => ({ ...v, artRrt: e.target.value }))} className="mono" disabled={!podeAprovar} />
          <Botao pequeno disabled={!resp.contaId || !podeAprovar} carregando={responsavel.isPending} onClick={() => responsavel.mutate(resp)}>Definir</Botao>
        </Cartao>
        <Cartao titulo="Alertas de transição (RF17)" descricao="Pilares sem apoio inferior ou que atravessam pavimentos." className="lg:col-span-2">
          {alertas.isLoading ? <Carregando /> : !abertos.length ? <Vazio titulo="Nenhum alerta aberto" descricao="A Disciplina Z está satisfeita." /> : (
            <Tabela linhas={abertos} chave={(a) => a.id} colunas={[
              { titulo: "Alerta", celula: (a) => <span className="flex items-center gap-1"><AlertTriangle className="size-4 text-warning" aria-hidden />{a.mensagem}</span> },
              { titulo: "Quando", celula: (a) => data(a.criadoEm) },
              { titulo: "Decisão", celula: (a) => podeAprovar ? <div className="flex gap-1"><Botao pequeno onClick={() => decidir.mutate({ id: a.id, decisao: "ACEITAR", justificativa: "Transição de carga prevista em viga" })}>Aceitar</Botao><Botao pequeno variante="secundario" onClick={() => decidir.mutate({ id: a.id, decisao: "CORRIGIR" })}>Corrigir</Botao></div> : "—" },
            ]} />
          )}
        </Cartao>
      </div>
      <Dialogo aberto={!!transicao} onFechar={() => setTransicao(null)} titulo={`Mudar para ${transicao ? ESTADOS_OBRA[transicao].rotulo : ""}`} perigo={transicao === "ARQUIVADA"}
        acoes={<><Botao variante="secundario" type="button" onClick={() => setTransicao(null)}>Cancelar</Botao><Botao type="button" carregando={transicionar.isPending} onClick={() => transicao && transicionar.mutate({ para: transicao, motivo: motivo || undefined }, { onSuccess: () => setTransicao(null) })}>Confirmar</Botao></>}>
        {transicao === "ARQUIVADA" && <Aviso tom="aviso">Obra arquivada fica somente leitura para todos os papéis.</Aviso>}
        <Entrada rotulo="Motivo (opcional)" value={motivo} onChange={(e) => setMotivo(e.target.value)} />
      </Dialogo>
      <Dialogo aberto={edit} onFechar={() => setEdit(false)} titulo="Editar dados da obra" acoes={<><Botao variante="secundario" type="button" onClick={() => setEdit(false)}>Cancelar</Botao><Botao type="button" carregando={atualizar.isPending} onClick={() => atualizar.mutate(f, { onSuccess: () => setEdit(false) })}>Salvar</Botao></>}>
        <Entrada rotulo="Nome" value={f.nome} onChange={(e) => setF((v) => ({ ...v, nome: e.target.value }))} /><Entrada rotulo="Endereço" value={f.endereco} onChange={(e) => setF((v) => ({ ...v, endereco: e.target.value }))} />
        <div className="grid grid-cols-[1fr_6rem] gap-3"><Entrada rotulo="Município" value={f.municipio} onChange={(e) => setF((v) => ({ ...v, municipio: e.target.value }))} /><Entrada rotulo="UF" maxLength={2} value={f.uf} onChange={(e) => setF((v) => ({ ...v, uf: e.target.value.toUpperCase() }))} /></div>
        <Entrada rotulo="ART/RRT" value={f.artRrt} onChange={(e) => setF((v) => ({ ...v, artRrt: e.target.value }))} className="mono" />
      </Dialogo>
    </>
  );
}
