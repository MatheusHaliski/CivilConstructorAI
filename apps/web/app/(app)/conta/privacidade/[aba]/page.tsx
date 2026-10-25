"use client";
import { notFound, useRouter } from "next/navigation";
import { PRIVACIDADE_ABAS, TITULOS_ABAS } from "@/lib/navegacao";
import { use, useState } from "react";
import { Download, FileLock2, Trash2 } from "lucide-react";
import { post, put } from "@/lib/api";
import { useAcao, useDados } from "@/lib/dados";
import { useSessao } from "@/lib/sessao";
import type { Compartilhamento, Consequencias, ExportacaoTitular } from "@/lib/tipos";
import { bytes, data, dia } from "@/lib/formatos";
import { Alternar, Aviso, Botao, CabecalhoPagina, Caixa, Cartao, Carregando, Dialogo, Entrada, Etiqueta, Selecao, Tabela, Vazio } from "@/components/ui";
import { useSudoPrompt } from "@/components/conta/NavConta";
import { EstadoEtiqueta } from "@/components/obra/comuns";

type Item = { finalidade: string; estado: string; ativo: boolean; versaoTexto?: string; versaoVigente?: string; titulo: string; descricao: string; baseLegal: string };
type Evento = { finalidade: string; acao: string; versaoTexto: string; em?: string; criadoEm?: string };
type Categoria = { categoria: string; titulo: string; finalidade: string; baseLegal: string; retencao: string; sensivel: boolean; dados: Record<string, unknown> };
type Pedido = { id: string; tipo: string; categoria?: string; descricao: string; estado: string; prazoEm?: string; criadoEm?: string; resposta?: string; respondidoEm?: string };
const TIPOS_PEDIDO = [["ACESSO", "Acesso aos dados"], ["CORRECAO", "Correção"], ["EXCLUSAO", "Eliminação"], ["PORTABILIDADE", "Portabilidade"], ["INFORMACAO_COMPARTILHAMENTO", "Com quem foi compartilhado"], ["REVOGACAO", "Revogar consentimento"], ["OPOSICAO", "Oposição a tratamento"]].map(([valor, rotulo]) => ({ valor: valor!, rotulo: rotulo! }));
const CORRIGIVEIS = new Set(["nome", "registroProfissional", "registroUf", "cnpj"]);

/** RF24 consentimentos e reaceite · RF25 ver/corrigir/exportar/compartilhamentos/pedidos · RF26 exclusão com carência. */
export default function PaginaPrivacidade({ params }: { params: Promise<{ aba: string }> }) {
  const { aba } = use(params);
  const router = useRouter();
  if (!(PRIVACIDADE_ABAS as readonly string[]).includes(aba)) notFound();
  const { sessao, sair } = useSessao();
  const comSudo = useSudoPrompt();
  const sudo = (senha: string, codigo: string) => post("/me/sudo", { senha, codigo: codigo || null });
  const cons = useDados<Item[]>(["consentimentos"], "/me/consentimentos");
  const hist = useDados<Evento[]>(["consentimentos-hist"], "/me/consentimentos/historico", { ativo: aba === "consentimentos" });
  const pend = useDados<Record<string, string>>(["legal-pendencias"], "/me/legal/pendencias");
  const dados = useDados<Categoria[]>(["meus-dados"], "/me/dados", { ativo: aba === "meus-dados" });
  const comp = useDados<Compartilhamento[]>(["compartilhamentos"], "/me/compartilhamentos", { ativo: aba === "meus-dados" });
  const exps = useDados<ExportacaoTitular[]>(["exportacoes-titular"], "/me/exportacoes", { ativo: aba === "exportar", intervalo: 5000 });
  const pedidos = useDados<Pedido[]>(["pedidos-titular"], "/me/pedidos", { ativo: aba === "pedidos" });
  const consq = useDados<Consequencias>(["exclusao"], "/me/deletion", { ativo: aba === "excluir-conta" });
  const [corrigir, setCorrigir] = useState<{ campo: string; valor: string } | null>(null);
  const [pedido, setPedido] = useState({ tipo: "ACESSO", descricao: "", categoria: "" });
  const [excluir, setExcluir] = useState(false); const [entendi, setEntendi] = useState(false);
  const alterar = useAcao((v: { finalidade: string; concedido: boolean }) => put(`/me/consentimentos/${v.finalidade}`, { concedido: v.concedido }), { invalidar: [["consentimentos"], ["consentimentos-hist"]], sucesso: "Consentimento registrado." });
  const reaceitar = useAcao((v: { documento: string; versao: string }) => post("/me/legal/reaceite", v), { invalidar: [["legal-pendencias"]], sucesso: "Aceite registrado." });
  const salvarCorrecao = useAcao((v: { campo: string; valor: string }) => put(`/me/dados/${v.campo}`, { valor: v.valor }), { invalidar: [["meus-dados"], ["perfil"]], sucesso: "Dado corrigido (Art. 18, III)." });
  const exportar = useAcao(() => comSudo(() => post("/me/exportacoes"), sudo), { invalidar: [["exportacoes-titular"]], sucesso: "Exportação solicitada: o ZIP cifrado fica pronto em instantes." });
  const abrirPedido = useAcao((v: typeof pedido) => post("/me/pedidos", { tipo: v.tipo, descricao: v.descricao, categoria: v.categoria || null, empresaId: sessao?.empresaAtivaId || null }), { invalidar: [["pedidos-titular"]], sucesso: "Pedido aberto: prazo de resposta de 15 dias." });
  const solicitarExclusao = useAcao(() => comSudo(() => post("/me/deletion", { entendi: true }), sudo), { sucesso: "Exclusão agendada. Você pode reativar a conta entrando durante a carência." });
  const pendencias = Object.entries(pend.data ?? {});

  return (
    <>
      <CabecalhoPagina migalhas={<>Seus dados › {TITULOS_ABAS[aba] ?? aba}</>} titulo={TITULOS_ABAS[aba] ?? aba} descricao="Seus direitos como titular (LGPD Art. 18): consentir e revogar, ver, corrigir, exportar, saber com quem compartilhamos e pedir a eliminação." />
      {pendencias.length > 0 && <Aviso tom="aviso" titulo="Há uma nova versão dos documentos legais" className="mb-4"><ul className="flex flex-col gap-1">{pendencias.map(([doc, versao]) => <li key={doc} className="flex items-center gap-2">{doc === "TERMOS" ? "Termos de uso" : "Política de privacidade"} v{versao} <a className="underline" href={`/legal/${doc === "TERMOS" ? "termos" : "politica"}`} target="_blank" rel="noreferrer">ler</a><Botao pequeno onClick={() => reaceitar.mutate({ documento: doc, versao })}>Aceitar</Botao></li>)}</ul></Aviso>}
      <div className="mt-4 grid gap-4 lg:grid-cols-3">
        {aba === "consentimentos" && <>
          <Cartao titulo="Finalidades opcionais" className="lg:col-span-2" descricao="Todas começam desligadas; revogar é o mesmo controle e vale na hora (Art. 8º §5º).">
            {cons.isLoading ? <Carregando /> : (cons.data ?? []).map((c) => <div key={c.finalidade}><Alternar rotulo={c.titulo} descricao={`${c.descricao} · ${c.baseLegal}${c.versaoTexto && c.versaoVigente && c.versaoTexto !== c.versaoVigente ? " · texto mudou, confirme de novo" : ""}`} ligado={c.ativo} onChange={(v) => alterar.mutate({ finalidade: c.finalidade, concedido: v })} /></div>)}
          </Cartao>
          <Cartao titulo="Histórico (imutável)">
            <ul className="text-xs flex flex-col gap-1 max-h-80 overflow-auto">{(hist.data ?? []).map((h, i) => <li key={i} className="border-b border-line-soft pb-1"><span className="font-medium">{h.acao}</span> · {h.finalidade} · v{h.versaoTexto} · {data(h.em ?? h.criadoEm)}</li>)}{!hist.data?.length && <li className="text-text-3">Sem eventos.</li>}</ul>
          </Cartao>
        </>}
        {aba === "meus-dados" && <>
          <div className="lg:col-span-2 flex flex-col gap-4">
            {dados.isLoading ? <Carregando /> : (dados.data ?? []).map((c) => (
              <Cartao key={c.categoria} titulo={<span className="flex items-center gap-2">{c.titulo}{c.sensivel && <Etiqueta tom="aviso">sensível</Etiqueta>}</span>} descricao={`${c.finalidade} · ${c.baseLegal} · guardado ${c.retencao}`}>
                <dl className="grid gap-x-4 gap-y-1 sm:grid-cols-[12rem_1fr] text-sm">{Object.entries(c.dados).map(([k, v]) => <div key={k} className="contents"><dt className="text-text-3">{k}</dt><dd className="flex items-center gap-2 break-all"><span className="mono text-xs">{typeof v === "object" ? JSON.stringify(v) : String(v ?? "—")}</span>{CORRIGIVEIS.has(k) && <Botao pequeno variante="link" onClick={() => setCorrigir({ campo: k, valor: String(v ?? "") })}>corrigir</Botao>}</dd></div>)}</dl>
              </Cartao>
            ))}
          </div>
          <Cartao titulo="Com quem compartilhamos" descricao="Art. 18, VII">
            <ul className="flex flex-col gap-2 text-sm">{(comp.data ?? []).map((x, i) => <li key={i} className="border-b border-line-soft pb-2"><p className="font-medium">{x.nome} <span className="text-text-3 font-normal">({x.papel})</span></p><p className="text-xs text-text-2">{x.finalidade} · {x.baseLegal}{x.internacional && <> · <Etiqueta tom="info">transferência internacional</Etiqueta></>}</p></li>)}{!comp.data?.length && <li className="text-text-3">Nenhum compartilhamento.</li>}</ul>
          </Cartao>
        </>}
        {aba === "exportar" && <Cartao titulo="Exportar meus dados (portabilidade)" className="lg:col-span-3" descricao="JSON versionado num ZIP cifrado; link válido por 15 min; a senha do ZIP aparece uma única vez." acoes={<Botao icone={<FileLock2 className="size-4" aria-hidden />} carregando={exportar.isPending} onClick={() => exportar.mutate()}>Nova exportação</Botao>}>
          {exps.isLoading ? <Carregando /> : <Tabela linhas={exps.data ?? []} chave={(e) => e.id} vazio={<Vazio titulo="Nenhuma exportação" />} colunas={[
            { titulo: "Estado", celula: (e) => <EstadoEtiqueta estado={e.estado} /> }, { titulo: "Tamanho", celula: (e) => bytes(e.tamanhoBytes) }, { titulo: "Expira", celula: (e) => data(e.expiraEm) },
            { titulo: "Senha do ZIP", celula: (e) => e.senhaZip ? <span className="mono select-all rounded bg-warning-bg px-2 py-0.5">{e.senhaZip}</span> : <span className="text-text-3 text-xs">já exibida</span> },
            { titulo: "", celula: (e) => e.urlDownload ? <a href={e.urlDownload} download><Botao pequeno variante="secundario" icone={<Download className="size-4" aria-hidden />}>Baixar</Botao></a> : e.erro ? <span className="text-xs text-danger">{e.erro}</span> : null },
          ]} />}
        </Cartao>}
        {aba === "pedidos" && <>
          <Cartao titulo="Meus pedidos" className="lg:col-span-2">
            {pedidos.isLoading ? <Carregando /> : <Tabela linhas={pedidos.data ?? []} chave={(p) => p.id} vazio={<Vazio titulo="Nenhum pedido" />} colunas={[
              { titulo: "Tipo", celula: (p) => TIPOS_PEDIDO.find((t) => t.valor === p.tipo)?.rotulo ?? p.tipo }, { titulo: "Descrição", celula: (p) => <span className="text-sm">{p.descricao}{p.resposta && <span className="block text-xs text-text-2">↳ {p.resposta}</span>}</span> },
              { titulo: "Estado", celula: (p) => <EstadoEtiqueta estado={p.estado} /> }, { titulo: "Prazo", celula: (p) => dia(p.prazoEm) },
            ]} />}
          </Cartao>
          <Cartao titulo="Abrir pedido" descricao="Vai para o encarregado da plataforma ou da empresa ativa.">
            <Selecao rotulo="Tipo" value={pedido.tipo} onChange={(e) => setPedido((v) => ({ ...v, tipo: e.target.value }))} opcoes={TIPOS_PEDIDO} />
            <Entrada rotulo="Categoria de dado (opcional)" value={pedido.categoria} onChange={(e) => setPedido((v) => ({ ...v, categoria: e.target.value }))} />
            <Entrada rotulo="Descrição" value={pedido.descricao} onChange={(e) => setPedido((v) => ({ ...v, descricao: e.target.value }))} />
            <Botao pequeno disabled={!pedido.descricao} carregando={abrirPedido.isPending} onClick={() => abrirPedido.mutate(pedido, { onSuccess: () => setPedido((v) => ({ ...v, descricao: "" })) })}>Enviar</Botao>
          </Cartao>
        </>}
        {aba === "excluir-conta" && <Cartao titulo="Excluir minha conta" className="lg:col-span-3" descricao="A conta fica 30 dias em carência (você pode reativar entrando); depois os dados são anonimizados e a chave é destruída.">
          {consq.isLoading ? <Carregando /> : consq.data && <div className="grid gap-4 md:grid-cols-2 text-sm">
            <div><p className="font-medium">O que sai</p><ul className="list-disc pl-5 text-text-2">{consq.data.oQueSai.map((x) => <li key={x}>{x}</li>)}</ul></div>
            <div><p className="font-medium">O que fica (obrigação legal)</p><ul className="list-disc pl-5 text-text-2">{consq.data.oQueFica.map((x) => <li key={x}>{x}</li>)}</ul></div>
            <p className="md:col-span-2">Data final da eliminação: <strong>{dia(consq.data.dataFinal)}</strong> ({consq.data.carenciaDias} dias).</p>
          </div>}
          <div className="flex gap-2 flex-wrap"><Botao variante="secundario" icone={<Download className="size-4" aria-hidden />} onClick={() => router.push("/conta/privacidade/exportar")}>Baixar uma cópia antes</Botao><Botao variante="perigo" icone={<Trash2 className="size-4" aria-hidden />} onClick={() => setExcluir(true)}>Excluir conta</Botao></div>
        </Cartao>}
      </div>
      <Dialogo aberto={!!corrigir} onFechar={() => setCorrigir(null)} titulo={`Corrigir ${corrigir?.campo ?? ""}`} acoes={<><Botao type="button" variante="secundario" onClick={() => setCorrigir(null)}>Cancelar</Botao><Botao type="button" carregando={salvarCorrecao.isPending} onClick={() => corrigir && salvarCorrecao.mutate(corrigir, { onSuccess: () => setCorrigir(null) })}>Salvar</Botao></>}>
        <Entrada rotulo="Novo valor" value={corrigir?.valor ?? ""} onChange={(e) => setCorrigir((v) => (v ? { ...v, valor: e.target.value } : v))} />
      </Dialogo>
      <Dialogo aberto={excluir} perigo onFechar={() => setExcluir(false)} titulo="Excluir a conta?" acoes={<><Botao type="button" variante="secundario" onClick={() => setExcluir(false)}>Cancelar</Botao><Botao type="button" variante="perigo" disabled={!entendi} carregando={solicitarExclusao.isPending} onClick={() => solicitarExclusao.mutate(undefined, { onSuccess: () => { setExcluir(false); void sair().then(() => (window.location.href = "/entrar")); } })}>Excluir</Botao></>}>
        <Aviso tom="perigo">Sua conta entra em carência até {dia(consq.data?.dataFinal)}. Depois disso a eliminação é definitiva e não há como recuperar.</Aviso>
        <Caixa rotulo="Entendo as consequências" checked={entendi} onChange={(e) => setEntendi(e.target.checked)} />
        <p className="text-xs text-text-3">Vamos pedir sua senha e o código do 2FA (modo seguro).</p>
      </Dialogo>
    </>
  );
}
