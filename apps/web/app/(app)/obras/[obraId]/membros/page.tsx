"use client";
import { use, useState } from "react";
import { UserPlus } from "lucide-react";
import { del, patch, post } from "@/lib/api";
import { useAcao, useDados } from "@/lib/dados";
import { aprova, usePapeis, useSessao } from "@/lib/sessao";
import { PAPEIS, rotuloPapel, type ConviteResumo, type Membro, type PapelObra } from "@/lib/tipos";
import { data } from "@/lib/formatos";
import { Botao, Cartao, Carregando, Entrada, Etiqueta, Selecao, Tabela, Vazio } from "@/components/ui";
import { CabecalhoObra } from "@/components/obra/comuns";

/** RF22 membros e papéis por obra · RF20 convites para a obra (nunca acima do papel de quem convida). */
export default function PaginaMembros({ params }: { params: Promise<{ obraId: string }> }) {
  const { obraId } = use(params);
  const { sessao } = useSessao();
  const papeis = usePapeis(obraId);
  const gerente = aprova(papeis);
  const empresaId = sessao?.empresaAtivaId ?? "";
  const membros = useDados<Membro[]>(["obra", obraId, "membros"], `/obras/${obraId}/membros`);
  const convites = useDados<ConviteResumo[]>(["convites", empresaId], `/companies/${empresaId}/invites`, { ativo: !!empresaId && gerente });
  const [f, setF] = useState({ email: "", papel: "MESTRE_OBRAS" as PapelObra });
  const mudar = useAcao((v: { contaId: string; papel: PapelObra }) => patch(`/obras/${obraId}/membros/${v.contaId}`, { papel: v.papel }), { invalidar: [["obra", obraId, "membros"]], sucesso: "Papel atualizado." });
  const remover = useAcao((contaId: string) => del(`/obras/${obraId}/membros/${contaId}`), { invalidar: [["obra", obraId, "membros"]], sucesso: "Membro removido da obra." });
  const convidar = useAcao((v: typeof f) => post(`/companies/${empresaId}/invites`, { email: v.email, obraId, papel: v.papel }), { invalidar: [["convites", empresaId]], sucesso: "Convite enviado por e-mail (válido por 7 dias)." });
  const revogar = useAcao((id: string) => del(`/companies/${empresaId}/invites/${id}`), { invalidar: [["convites", empresaId]], sucesso: "Convite revogado." });
  const reenviar = useAcao((id: string) => post(`/companies/${empresaId}/invites/${id}/resend`), { sucesso: "Convite reenviado." });
  const trabalhador = useAcao(() => post(`/obras/${obraId}/trabalhadores`, {}), { invalidar: [["obra", obraId, "membros"]], sucesso: "Trabalhador cadastrado (sem login)." });
  const daObra = (convites.data ?? []).filter((c) => c.obraId === obraId && c.estado === "PENDENTE");
  return (
    <>
      <CabecalhoObra obraId={obraId} secao="Membros" descricao="Papéis por obra: a camada 2 do controle de acesso (RF21)." />
      <div className="grid gap-4 lg:grid-cols-3">
        <Cartao titulo="Membros da obra" className="lg:col-span-2" acoes={gerente && <Botao pequeno variante="secundario" carregando={trabalhador.isPending} onClick={() => trabalhador.mutate()}>Cadastrar trabalhador sem login</Botao>}>
          {membros.isLoading ? <Carregando /> : (
            <Tabela linhas={membros.data ?? []} chave={(m) => m.contaId} vazio={<Vazio titulo="Sem membros" />} colunas={[
              { titulo: "Nome", celula: (m) => <span className="font-medium">{m.nome}</span> },
              { titulo: "Papel", celula: (m) => gerente ? <select aria-label={`Papel de ${m.nome}`} value={m.papel} onChange={(e) => mudar.mutate({ contaId: m.contaId, papel: e.target.value as PapelObra })} className="min-h-9 rounded border border-line bg-bg px-2 text-sm">{PAPEIS.map((p) => <option key={p.valor} value={p.valor}>{p.rotulo}</option>)}</select> : <Etiqueta tom="marca">{rotuloPapel(m.papel)}</Etiqueta> },
              { titulo: "", celula: (m) => gerente && m.contaId !== sessao?.contaId ? <Botao pequeno variante="fantasma" onClick={() => remover.mutate(m.contaId)}>Remover</Botao> : null },
            ]} />
          )}
        </Cartao>
        {gerente && (
          <Cartao titulo="Convidar para esta obra" descricao="O convite nunca promove acima do seu papel.">
            <Entrada rotulo="E-mail" type="email" value={f.email} onChange={(e) => setF((v) => ({ ...v, email: e.target.value }))} />
            <Selecao rotulo="Papel na obra" value={f.papel} onChange={(e) => setF((v) => ({ ...v, papel: e.target.value as PapelObra }))} opcoes={PAPEIS.filter((p) => p.valor !== "DONO").map((p) => ({ valor: p.valor, rotulo: p.rotulo }))} />
            <Botao pequeno icone={<UserPlus className="size-4" aria-hidden />} disabled={!f.email} carregando={convidar.isPending} onClick={() => convidar.mutate(f, { onSuccess: () => setF((v) => ({ ...v, email: "" })) })}>Enviar convite</Botao>
            {daObra.length > 0 && <ul className="flex flex-col gap-2 text-sm border-t border-line-soft pt-3">{daObra.map((c) => <li key={c.id} className="flex items-center justify-between gap-2"><span><span className="mono">{c.email}</span> · {rotuloPapel(c.papel)} <span className="text-xs text-text-3">até {data(c.expiraEm)}</span></span><span className="flex gap-1"><Botao pequeno variante="fantasma" onClick={() => reenviar.mutate(c.id)}>Reenviar</Botao><Botao pequeno variante="fantasma" onClick={() => revogar.mutate(c.id)}>Revogar</Botao></span></li>)}</ul>}
          </Cartao>
        )}
      </div>
    </>
  );
}
