"use client";
import { useState } from "react";
import { BadgeCheck } from "lucide-react";
import { ErroApi, post } from "@/lib/api";
import { useAcao, useDados } from "@/lib/dados";
import { data } from "@/lib/formatos";
import { Aviso, Botao, CabecalhoPagina, Cartao, Carregando, Etiqueta, Selecao, Tabela, Vazio } from "@/components/ui";

type Pedido = { contaId: string; nome?: string; email?: string; tipo: string; registro?: string; uf?: string; estado: string; solicitadoEm?: string; urlDocumento?: string; [k: string]: unknown };

/** RF21 · Fila de verificação de selos (CREA/CAU/CNPJ) para a equipe da plataforma. */
export default function PaginaSelos() {
  const [estado, setEstado] = useState("PENDING");
  const fila = useDados<Pedido[]>(["selos", estado], "/plataforma/selos", { query: { estado } });
  const decidir = useAcao((v: { contaId: string; aprovado: boolean; motivo?: string }) => post(`/plataforma/selos/${v.contaId}/decisao`, { aprovado: v.aprovado, motivo: v.motivo ?? null }), { invalidar: [["selos"]], sucesso: "Decisão registrada." });
  const proibido = fila.error instanceof ErroApi && fila.error.status === 403;
  return (
    <>
      <CabecalhoPagina titulo="Verificação de selos" descricao="Confira o registro no conselho (CREA/CAU) ou o CNPJ antes de aprovar. Só a equipe da plataforma vê esta fila." acoes={<Selecao rotulo="Estado" value={estado} onChange={(e) => setEstado(e.target.value)} opcoes={[{ valor: "PENDING", rotulo: "Pendentes" }, { valor: "VERIFIED", rotulo: "Verificados" }, { valor: "REJECTED", rotulo: "Recusados" }]} />} />
      {proibido ? <Aviso tom="aviso">Esta área é exclusiva da equipe de suporte da plataforma.</Aviso> : (
        <Cartao>
          {fila.isLoading ? <Carregando /> : <Tabela linhas={fila.data ?? []} chave={(p) => p.contaId} vazio={<Vazio icone={<BadgeCheck className="size-8" aria-hidden />} titulo="Nenhum pedido" />} colunas={[
            { titulo: "Pessoa", celula: (p) => <span>{p.nome ?? "—"}<br /><span className="mono text-xs text-text-3">{p.email ?? p.contaId}</span></span> }, { titulo: "Selo", celula: (p) => <span><Etiqueta tom="marca">{p.tipo}</Etiqueta> <span className="mono">{p.registro}</span> {p.uf}</span> },
            { titulo: "Pedido em", celula: (p) => data(p.solicitadoEm) }, { titulo: "Documento", celula: (p) => p.urlDocumento ? <a className="text-brand-text underline" href={p.urlDocumento} target="_blank" rel="noreferrer">abrir</a> : "—" },
            { titulo: "", celula: (p) => p.estado === "PENDING" ? <span className="flex gap-1"><Botao pequeno onClick={() => decidir.mutate({ contaId: p.contaId, aprovado: true })}>Aprovar</Botao><Botao pequeno variante="fantasma" onClick={() => { const m = window.prompt("Motivo da recusa:"); if (m) decidir.mutate({ contaId: p.contaId, aprovado: false, motivo: m }); }}>Recusar</Botao></span> : <Etiqueta>{p.estado}</Etiqueta> },
          ]} />}
        </Cartao>
      )}
    </>
  );
}
