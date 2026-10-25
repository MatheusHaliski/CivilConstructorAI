"use client";
import { use, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { get, mensagemDeErro, post } from "@/lib/api";
import { useSessao } from "@/lib/sessao";
import { rotuloPapel, type ConvitePublico } from "@/lib/tipos";
import { dia } from "@/lib/formatos";
import { Aviso, Botao, Cartao, Carregando } from "@/components/ui";

/** RF20 · Página pública do convite: mostra empresa/obra/papel (sem dados pessoais) e aceita com conta logada. */
export default function PaginaConvite({ params }: { params: Promise<{ token: string }> }) {
  const { token } = use(params);
  const router = useRouter();
  const { sessao, carregando, recarregar } = useSessao();
  const q = useQuery({ queryKey: ["convite", token], queryFn: () => get<ConvitePublico>(`/convites/${token}/publico`), retry: false });
  const [erro, setErro] = useState<string | null>(null);
  const [ocupado, setOcupado] = useState(false);
  const agir = async (acao: "accept" | "decline") => {
    setErro(null); setOcupado(true);
    try {
      const r = await post<{ empresaId: string; obraId?: string | null }>(`/invites/${token}/${acao}`);
      await recarregar();
      router.replace(acao === "accept" ? (r?.obraId ? `/obras/${r.obraId}` : "/obras") : "/entrar");
    }
    catch (x) { setErro(mensagemDeErro(x)); } finally { setOcupado(false); }
  };
  if (q.isLoading || carregando) return <Carregando />;
  if (q.error) return <Cartao titulo="Convite indisponível"><Aviso tom="aviso">{mensagemDeErro(q.error)}</Aviso><Link href="/entrar" className="text-brand-text underline">Ir para o início</Link></Cartao>;
  const c = q.data!;
  return (
    <Cartao titulo={`Convite para ${c.empresa}`} descricao={c.obra ? `Obra: ${c.obra}` : "Acesso à empresa"}>
      <dl className="grid grid-cols-2 gap-2 text-sm">
        <dt className="text-text-3">Papel</dt><dd className="font-medium">{rotuloPapel(c.papel)}</dd>
        <dt className="text-text-3">E-mail convidado</dt><dd className="mono">{c.emailMascarado}</dd>
        <dt className="text-text-3">Válido até</dt><dd>{dia(c.expiraEm)}</dd>
      </dl>
      {erro && <Aviso tom="perigo">{erro}</Aviso>}
      {sessao ? (
        <div className="flex gap-2">
          <Botao carregando={ocupado} onClick={() => void agir("accept")}>Aceitar convite</Botao>
          <Botao variante="secundario" carregando={ocupado} onClick={() => void agir("decline")}>Recusar</Botao>
        </div>
      ) : (
        <div className="flex flex-col gap-2">
          <Aviso tom="info">Entre com a conta do e-mail convidado para aceitar.</Aviso>
          <div className="flex gap-2"><Link href={`/entrar?voltar=/convite/${token}`}><Botao>Entrar</Botao></Link><Link href={`/cadastrar?voltar=/convite/${token}`}><Botao variante="secundario">Criar conta</Botao></Link></div>
        </div>
      )}
    </Cartao>
  );
}
