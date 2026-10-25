"use client";
import { useEffect, useState } from "react";
import { Building2, Repeat, UserPlus } from "lucide-react";
import { del, patch, post, put } from "@/lib/api";
import { useAcao, useDados } from "@/lib/dados";
import { useSessao } from "@/lib/sessao";
import { PAPEIS, rotuloPapel, type ConviteResumo, type EmpresaResumo, type Membro, type ObraResumo, type PapelObra } from "@/lib/tipos";
import { data, dia, mascararCnpj, numero } from "@/lib/formatos";
import { Abas, Aviso, Botao, CabecalhoPagina, Cartao, Carregando, Entrada, Etiqueta, Selecao, Tabela, Vazio } from "@/components/ui";
import { EstadoEtiqueta } from "@/components/obra/comuns";

type LinhaInventario = { categoria: string; titulo: string; finalidade: string; baseLegal: string; minimoMeses: number; prazoMeses: number; descricaoPrazo: string; sensivel: boolean; registros: number };
type PedidoFila = { id: string; tipo: string; descricao: string; estado: string; prazoEm?: string; criadoEm?: string; resposta?: string; categoria?: string };
type Consumo = { mes: string; rota: string; chamadas: number; inputTokens: number; outputTokens: number; cacheReadInputTokens: number; recusas: number };

/** RF22 empresa (tenant) e membros · RF20 convites · RF25 inventário, retenção e fila de pedidos dos titulares · consumo de IA. */
export default function PaginaEmpresa() {
  const { sessao, recarregar } = useSessao();
  const id = sessao?.empresaAtivaId || "";
  const gerente = ["DONO", "ADMIN"].includes(sessao?.papelEmpresa ?? "");
  const [aba, setAba] = useState("dados");
  const emp = useDados<EmpresaResumo>(["empresa", id], `/companies/${id}`, { ativo: !!id });
  const membros = useDados<Membro[]>(["empresa", id, "membros"], `/companies/${id}/membros`, { ativo: !!id && aba === "membros" });
  const convites = useDados<ConviteResumo[]>(["convites", id], `/companies/${id}/invites`, { ativo: !!id && gerente && aba === "membros" });
  const obras = useDados<ObraResumo[]>(["obras"], "/obras", { ativo: !!id });
  const inv = useDados<LinhaInventario[]>(["inventario", id], `/empresas/${id}/privacidade/inventario`, { ativo: !!id && aba === "lgpd" });
  const fila = useDados<PedidoFila[]>(["fila-titulares", id], `/empresas/${id}/privacidade/pedidos`, { ativo: !!id && aba === "lgpd" });
  const consumo = useDados<Consumo[]>(["consumo-ia", id], `/empresas/${id}/ia/consumo`, { ativo: !!id && aba === "ia" });
  const [nova, setNova] = useState({ razaoSocial: "", nomeFantasia: "", cnpj: "" });
  const [f, setF] = useState({ razaoSocial: "", nomeFantasia: "", encarregadoContato: "" });
  const [cv, setCv] = useState({ email: "", papel: "ENGENHEIRO" as PapelObra, obraId: "" });
  const [resp, setResp] = useState<Record<string, string>>({});
  useEffect(() => { if (emp.data) setF({ razaoSocial: emp.data.razaoSocial, nomeFantasia: emp.data.nomeFantasia ?? "", encarregadoContato: emp.data.encarregadoContato ?? "" }); }, [emp.data]);
  const criar = useAcao(async (v: typeof nova) => { const e = await post<EmpresaResumo>("/companies", v); await post("/session/empresa", { empresaId: e.id }); await recarregar(); return e; }, { invalidar: [["obras"]], sucesso: "Empresa criada; você é o(a) DONO(A). Configure o 2FA em Conta › Segurança." });
  const salvar = useAcao((v: typeof f) => patch(`/companies/${id}`, v), { invalidar: [["empresa", id]], sucesso: "Empresa atualizada." });
  const mudarPapel = useAcao((v: { contaId: string; papel: PapelObra }) => patch(`/companies/${id}/membros/${v.contaId}`, { papel: v.papel }), { invalidar: [["empresa", id, "membros"]], sucesso: "Papel atualizado." });
  const convidar = useAcao((v: typeof cv) => post(`/companies/${id}/invites`, { email: v.email, papel: v.papel, obraId: v.obraId || null }), { invalidar: [["convites", id]], sucesso: "Convite enviado." });
  const revogar = useAcao((cid: string) => del(`/companies/${id}/invites/${cid}`), { invalidar: [["convites", id]] });
  const reenviar = useAcao((cid: string) => post(`/companies/${id}/invites/${cid}/resend`), { sucesso: "Convite reenviado." });
  const retencao = useAcao((v: { categoria: string; prazoMeses: number }) => put(`/empresas/${id}/privacidade/retencao/${v.categoria}`, { prazoMeses: v.prazoMeses }), { invalidar: [["inventario", id]], sucesso: "Prazo de retenção atualizado." });
  const responder = useAcao((v: { pedidoId: string; estado: string; resposta: string }) => patch(`/empresas/${id}/privacidade/pedidos/${v.pedidoId}`, { estado: v.estado, resposta: v.resposta }), { invalidar: [["fila-titulares", id]], sucesso: "Pedido respondido." });
  const trocar = useAcao(async (empresaId: string) => { await post("/session/empresa", { empresaId }); await recarregar(); }, { invalidar: [["obras"]], sucesso: "Empresa ativa trocada." });

  if (!id) return (
    <>
      <CabecalhoPagina titulo="Empresa" descricao="A empresa é o tenant: obras, membros e dados ficam isolados por empresa (RLS)." />
      <Cartao titulo="Criar empresa" className="max-w-xl" descricao="Você se torna DONO(A); o 2FA passa a ser obrigatório para você.">
        <Entrada rotulo="Razão social" value={nova.razaoSocial} onChange={(e) => setNova((v) => ({ ...v, razaoSocial: e.target.value }))} obrigatorio />
        <Entrada rotulo="Nome fantasia" value={nova.nomeFantasia} onChange={(e) => setNova((v) => ({ ...v, nomeFantasia: e.target.value }))} />
        <Entrada rotulo="CNPJ" inputMode="numeric" className="mono" value={nova.cnpj} onChange={(e) => setNova((v) => ({ ...v, cnpj: e.target.value }))} obrigatorio />
        <Botao icone={<Building2 className="size-4" aria-hidden />} disabled={!nova.razaoSocial || !nova.cnpj} carregando={criar.isPending} onClick={() => criar.mutate(nova)}>Criar empresa</Botao>
        <Aviso tom="info">Recebeu um convite? Abra o link do e-mail para entrar numa empresa existente.</Aviso>
      </Cartao>
    </>
  );
  const e = emp.data;
  return (
    <>
      <CabecalhoPagina titulo={e ? e.nomeFantasia || e.razaoSocial : "Empresa"} descricao={e ? `CNPJ ${mascararCnpj(e.cnpj)}` : undefined} acoes={<>{e && <Etiqueta tom={e.cnpjVerificado ? "sucesso" : "aviso"}>{e.cnpjVerificado ? "CNPJ conferido" : "CNPJ não conferido"}</Etiqueta>}<Etiqueta tom="marca">{rotuloPapel(sessao?.papelEmpresa ?? "")}</Etiqueta></>} />
      <Abas ativa={aba} onChange={setAba} abas={[{ id: "dados", rotulo: "Dados" }, { id: "membros", rotulo: "Membros e convites" }, { id: "lgpd", rotulo: "Privacidade (controladora)" }, { id: "ia", rotulo: "Consumo de IA" }]} />
      <div className="mt-4 grid gap-4 lg:grid-cols-3">
        {aba === "dados" && <>
          <Cartao titulo="Dados da empresa" className="lg:col-span-2">
            {emp.isLoading ? <Carregando /> : <>
              <Entrada rotulo="Razão social" value={f.razaoSocial} disabled={!gerente} onChange={(ev) => setF((v) => ({ ...v, razaoSocial: ev.target.value }))} />
              <Entrada rotulo="Nome fantasia" value={f.nomeFantasia} disabled={!gerente} onChange={(ev) => setF((v) => ({ ...v, nomeFantasia: ev.target.value }))} />
              <Entrada rotulo="Contato do encarregado (DPO)" value={f.encarregadoContato} disabled={!gerente} onChange={(ev) => setF((v) => ({ ...v, encarregadoContato: ev.target.value }))} ajuda="Obrigatório para a primeira obra sair de Planejada (DN-01)." />
              {gerente && <Botao pequeno className="self-start" carregando={salvar.isPending} onClick={() => salvar.mutate(f)}>Salvar</Botao>}
            </>}
          </Cartao>
          <Cartao titulo="Empresa ativa" descricao="Troque se você participa de mais de uma.">
            <p className="text-sm">{obras.data?.length ?? 0} obra(s) visíveis nesta empresa.</p>
            <TrocaEmpresa atual={id} trocar={(x) => trocar.mutate(x)} />
          </Cartao>
        </>}
        {aba === "membros" && <>
          <Cartao titulo="Membros" className="lg:col-span-2">
            {membros.isLoading ? <Carregando /> : <Tabela linhas={membros.data ?? []} chave={(m) => m.contaId} vazio={<Vazio titulo="Sem membros" />} colunas={[
              { titulo: "Nome", celula: (m) => <span className="font-medium">{m.nome}</span> },
              { titulo: "Papel na empresa", celula: (m) => gerente && m.contaId !== sessao?.contaId ? <select aria-label={`Papel de ${m.nome}`} value={m.papel} onChange={(ev) => mudarPapel.mutate({ contaId: m.contaId, papel: ev.target.value as PapelObra })} className="min-h-9 rounded border border-line bg-bg px-2 text-sm">{PAPEIS.map((p) => <option key={p.valor} value={p.valor}>{p.rotulo}</option>)}</select> : <Etiqueta tom="marca">{rotuloPapel(m.papel)}</Etiqueta> },
            ]} />}
            {gerente && <><h3 className="text-sm font-semibold mt-2">Convites</h3><Tabela linhas={convites.data ?? []} chave={(c) => c.id} vazio={<p className="text-sm text-text-3">Nenhum convite.</p>} colunas={[
              { titulo: "E-mail", celula: (c) => <span className="mono text-xs">{c.email}</span> }, { titulo: "Papel", celula: (c) => rotuloPapel(c.papel) }, { titulo: "Obra", celula: (c) => obras.data?.find((o) => o.id === c.obraId)?.nome ?? "Empresa" },
              { titulo: "Estado", celula: (c) => <EstadoEtiqueta estado={c.estado} /> }, { titulo: "Expira", celula: (c) => dia(c.expiraEm) },
              { titulo: "", celula: (c) => c.estado === "PENDENTE" ? <span className="flex gap-1"><Botao pequeno variante="fantasma" onClick={() => reenviar.mutate(c.id)}>Reenviar</Botao><Botao pequeno variante="fantasma" onClick={() => revogar.mutate(c.id)}>Revogar</Botao></span> : null },
            ]} /></>}
          </Cartao>
          {gerente && <Cartao titulo="Convidar" descricao="Válido por 7 dias, uso único; nunca acima do seu papel.">
            <Entrada rotulo="E-mail" type="email" value={cv.email} onChange={(ev) => setCv((v) => ({ ...v, email: ev.target.value }))} />
            <Selecao rotulo="Papel" value={cv.papel} onChange={(ev) => setCv((v) => ({ ...v, papel: ev.target.value as PapelObra }))} opcoes={PAPEIS.filter((p) => p.valor !== "DONO")} />
            <Selecao rotulo="Obra (opcional)" value={cv.obraId} onChange={(ev) => setCv((v) => ({ ...v, obraId: ev.target.value }))} vazio="Só a empresa" opcoes={(obras.data ?? []).map((o) => ({ valor: o.id, rotulo: o.nome }))} />
            <Botao pequeno icone={<UserPlus className="size-4" aria-hidden />} disabled={!cv.email} carregando={convidar.isPending} onClick={() => convidar.mutate(cv, { onSuccess: () => setCv((v) => ({ ...v, email: "" })) })}>Enviar convite</Botao>
          </Cartao>}
        </>}
        {aba === "lgpd" && <>
          <Cartao titulo="Inventário de dados (Art. 37)" className="lg:col-span-3" descricao="Dados de trabalhadores, clientes e fornecedores: a empresa é a controladora; a plataforma, operadora (DN-01).">
            {inv.isLoading ? <Carregando /> : <Tabela linhas={inv.data ?? []} chave={(l) => l.categoria} colunas={[
              { titulo: "Categoria", celula: (l) => <span>{l.titulo}{l.sensivel && <> <Etiqueta tom="aviso">sensível</Etiqueta></>}</span> }, { titulo: "Finalidade / base legal", celula: (l) => <span className="text-xs">{l.finalidade}<br /><span className="text-text-3">{l.baseLegal}</span></span> },
              { titulo: "Registros", celula: (l) => numero(l.registros, 0), numerica: true },
              { titulo: "Retenção (meses)", celula: (l) => gerente ? <input aria-label={`Retenção de ${l.titulo}`} type="number" min={l.minimoMeses} defaultValue={l.prazoMeses} className="mono w-20 min-h-9 rounded border border-line bg-bg px-2" onBlur={(ev) => { const n = Number(ev.target.value); if (n !== l.prazoMeses) retencao.mutate({ categoria: l.categoria, prazoMeses: n }); }} /> : l.prazoMeses },
              { titulo: "Regra", celula: (l) => <span className="text-xs text-text-3">{l.descricaoPrazo} (mín. {l.minimoMeses})</span> },
            ]} />}
          </Cartao>
          <Cartao titulo="Pedidos dos titulares" className="lg:col-span-3" descricao="Prazo de 15 dias (Art. 18 §5º).">
            {fila.isLoading ? <Carregando /> : <Tabela linhas={fila.data ?? []} chave={(p) => p.id} vazio={<Vazio titulo="Fila vazia" />} colunas={[
              { titulo: "Tipo", celula: (p) => p.tipo }, { titulo: "Descrição", celula: (p) => p.descricao }, { titulo: "Prazo", celula: (p) => dia(p.prazoEm) }, { titulo: "Estado", celula: (p) => <EstadoEtiqueta estado={p.estado} /> },
              { titulo: "Resposta", celula: (p) => p.resposta ?? (gerente ? <span className="flex gap-1"><input aria-label="Resposta" className="min-h-9 rounded border border-line bg-bg px-2 text-sm" value={resp[p.id] ?? ""} onChange={(ev) => setResp((r) => ({ ...r, [p.id]: ev.target.value }))} /><Botao pequeno disabled={!resp[p.id]} onClick={() => responder.mutate({ pedidoId: p.id, estado: "ATENDIDO", resposta: resp[p.id] ?? "" })}>Atender</Botao><Botao pequeno variante="fantasma" disabled={!resp[p.id]} onClick={() => responder.mutate({ pedidoId: p.id, estado: "NEGADO", resposta: resp[p.id] ?? "" })}>Negar</Botao></span> : "—") },
            ]} />}
          </Cartao>
        </>}
        {aba === "ia" && <Cartao titulo="Consumo de IA por mês e rota" className="lg:col-span-3" descricao="Só metadados: tokens, recusas e rota (sem conteúdo).">
          {consumo.isLoading ? <Carregando /> : <Tabela linhas={consumo.data ?? []} chave={(c) => `${c.mes}-${c.rota}`} vazio={<Vazio titulo="Sem chamadas ao modelo" />} colunas={[
            { titulo: "Mês", celula: (c) => c.mes }, { titulo: "Rota", celula: (c) => <span className="mono text-xs">{c.rota}</span> }, { titulo: "Chamadas", celula: (c) => numero(c.chamadas, 0), numerica: true },
            { titulo: "Tokens entrada", celula: (c) => numero(c.inputTokens, 0), numerica: true }, { titulo: "Tokens saída", celula: (c) => numero(c.outputTokens, 0), numerica: true }, { titulo: "Cache lido", celula: (c) => numero(c.cacheReadInputTokens, 0), numerica: true }, { titulo: "Recusas", celula: (c) => numero(c.recusas, 0), numerica: true },
          ]} />}
        </Cartao>}
      </div>
      <p className="mt-4 text-xs text-text-3">Atualizado {data(new Date())}.</p>
    </>
  );
}

function TrocaEmpresa({ atual, trocar }: { atual: string; trocar: (id: string) => void }) {
  const [alvo, setAlvo] = useState("");
  return <div className="flex gap-2 items-end"><Entrada rotulo="Id da outra empresa" className="mono text-xs" value={alvo} onChange={(e) => setAlvo(e.target.value)} placeholder={atual} /><Botao pequeno variante="secundario" icone={<Repeat className="size-4" aria-hidden />} disabled={!alvo || alvo === atual} onClick={() => trocar(alvo)}>Trocar</Botao></div>;
}
