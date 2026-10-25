"use client";
import { use, useMemo, useState } from "react";
import { del, post } from "@/lib/api";
import { useAcao, useDados } from "@/lib/dados";
import { aprova, usePapeis, useSessao } from "@/lib/sessao";
import { referencia, type Propriedade } from "@cca/notacao-vi";
import { Aviso, Botao, Cartao, Carregando, Entrada, Etiqueta, Selecao, Tabela, Vazio } from "@/components/ui";
import { CabecalhoObra } from "@/components/obra/comuns";

type Familia = { id: string; nome: string; descricao: string; suporte: string; referencias: string[] };
type Item = { codigo: string; nome: string; familia: string; resumo?: string; legado: string[]; aplicacoes: string[]; propriedades: (Propriedade & { origem: string })[]; personalizado?: boolean };
type Personalizado = { id: string; codigo: string; nome: string; familia: string; baseadoEm?: string; situacao: string; item: Item & { produto?: { fabricante: string; referencia: string; documento: string } }; historico: { quando: string; acao: string; motivo?: string }[] };
/** espelha MateriaisService.catalogo */
type Catalogo = { versao: string; familias: Familia[]; itens: Item[]; bitolas: Record<string, { mm: number; areaCm2: number; kgPorMetro: number; situacao: string }[]>; composicoes: { codigo: string; nome: string; elemento: string; camadas: { material: string; espessura: number }[]; nota?: string }[]; servicos: { codigo: string; nome: string; material: string; unidade: string; sinapi?: string; situacaoSinapi?: string }[]; personalizados: Personalizado[] };

const SUPORTE: Record<string, { rotulo: string; tom: "sucesso" | "info" | "aviso" | "neutro" | "perigo" }> = {
  CALCULO: { rotulo: "entra no cálculo", tom: "sucesso" }, ANALISE: { rotulo: "entra na análise", tom: "info" }, QUANTITATIVO: { rotulo: "só quantitativo e orçamento", tom: "aviso" },
  REPRESENTACAO: { rotulo: "só representação", tom: "neutro" }, NAO_IMPLEMENTADO: { rotulo: "não implementado", tom: "perigo" },
};
const ORIGEM: Record<string, string> = { ESPECIFICADO: "especificado", DECLARADO_FABRICANTE: "declarado pelo fabricante", ENSAIO: "ensaio", ESTIMADO: "estimado", ADOTADO: "adotado" };
const SITUACAO: Record<string, { rotulo: string; tom: "aviso" | "sucesso" | "perigo" }> = { VALIDACAO_PENDENTE: { rotulo: "validação pendente", tom: "aviso" }, VALIDADO: { rotulo: "validado por profissional", tom: "sucesso" }, REJEITADO: { rotulo: "rejeitado", tom: "perigo" } };

function Propriedades({ props }: { props: Item["propriedades"] }) {
  return (
    <table className="w-full text-xs"><thead><tr className="text-left text-text-3"><th scope="col">Propriedade</th><th scope="col">Valor</th><th scope="col">Origem</th><th scope="col">Referência / método</th></tr></thead>
      <tbody>{props.map((p) => { const r = referencia(p.referencia); return (
        <tr key={p.chave} className="border-t border-line-soft align-top"><th scope="row" className="text-left font-normal py-1 pr-2">{p.rotulo}</th><td className="mono pr-2">{typeof p.valor === "number" ? p.valor.toLocaleString("pt-BR") : p.valor ?? "—"} {p.unidade ?? ""}</td>
          <td className="pr-2">{ORIGEM[p.origem] ?? p.origem}</td><td className="text-text-3">{r ? `${r.identificador}:${r.edicao}${r.cancelada ? " (cancelada)" : ""}` : "—"}{p.metodo ? ` · ${p.metodo}` : ""}{p.nota ? ` · ${p.nota}` : ""}</td></tr>); })}</tbody>
    </table>
  );
}

/** Catálogo técnico: material ≠ produto comercial ≠ composição ≠ elemento ≠ serviço; personalizados da empresa com validação. */
export default function PaginaMateriais({ params }: { params: Promise<{ obraId: string }> }) {
  const { obraId } = use(params);
  const { sessao } = useSessao();
  const pode = aprova(usePapeis(obraId));
  const cat = useDados<Catalogo>(["materiais", obraId], `/materiais/catalogo`, { query: { obraId } });
  const [familiaSel, setFamiliaSel] = useState("CONCRETO");
  const [busca, setBusca] = useState("");
  const [aberto, setAberto] = useState<string | null>(null);
  const [novo, setNovo] = useState({ familia: "CONCRETO", baseadoEm: "", nome: "", fck: "", fabricante: "", referencia: "", documento: "", origem: "ESPECIFICADO" });
  const criar = useAcao(() => post(`/materiais/personalizados`, {
    familia: novo.familia, baseadoEm: novo.baseadoEm || null, nome: novo.nome,
    propriedades: novo.fck ? [{ chave: "fck", valor: Number(novo.fck.replace(",", ".")), unidade: "MPa", origem: novo.origem }] : [],
    produto: novo.fabricante || novo.referencia || novo.documento ? { fabricante: novo.fabricante, referencia: novo.referencia, documento: novo.documento } : null,
  }), { invalidar: [["materiais", obraId]], sucesso: "Material personalizado criado (validação pendente: não entra no cálculo até ser validado)." });
  const decidir = useAcao((v: { id: string; validar: boolean; motivo?: string }) => post(`/materiais/personalizados/${v.id}/decisao`, { validar: v.validar, motivo: v.motivo }), { invalidar: [["materiais", obraId]], sucesso: "Decisão registrada." });
  const excluir = useAcao((id: string) => del(`/materiais/personalizados/${id}`), { invalidar: [["materiais", obraId]], sucesso: "Material excluído." });
  const itens = useMemo(() => {
    const t = busca.trim().toLowerCase();
    return (cat.data?.itens ?? []).filter((i) => i.familia === familiaSel).filter((i) => !t || [i.codigo, i.nome, i.resumo ?? "", ...i.legado].some((x) => x.toLowerCase().includes(t)));
  }, [cat.data, familiaSel, busca]);
  if (cat.isLoading) return <Carregando />;
  if (!cat.data) return <Aviso tom="perigo">Catálogo indisponível.</Aviso>;
  const c = cat.data;
  const fam = c.familias.find((f) => f.id === familiaSel);
  return (
    <>
      <CabecalhoObra obraId={obraId} secao="Materiais e componentes" grupo="Projeto" descricao={`Catálogo técnico ${c.versao}. Material (o que é), produto comercial (de quem e com qual documento), composição (camadas), elemento (onde está aplicado) e serviço (como é orçado) são coisas diferentes. Propriedades derivadas usam o agregado da base de cálculo da obra.`} />
      <div className="grid gap-4">
        <Cartao titulo="Famílias e o que o software faz com cada uma">
          <Tabela linhas={c.familias} chave={(f) => f.id} colunas={[
            { titulo: "Família", celula: (f) => <button type="button" className={`underline text-left ${f.id === familiaSel ? "font-semibold" : ""}`} aria-pressed={f.id === familiaSel} onClick={() => setFamiliaSel(f.id)}>{f.nome}</button> },
            { titulo: "Suporte", celula: (f) => <Etiqueta tom={SUPORTE[f.suporte]?.tom ?? "neutro"}>{SUPORTE[f.suporte]?.rotulo ?? f.suporte}</Etiqueta> },
            { titulo: "Itens", celula: (f) => c.itens.filter((i) => i.familia === f.id).length, numerica: true },
            { titulo: "Descrição", celula: (f) => <span className="text-xs">{f.descricao}</span> },
          ]} />
        </Cartao>
        <Cartao titulo={fam ? `${fam.nome}` : "Itens"} descricao={fam ? `Referências: ${fam.referencias.map((r) => { const x = referencia(r); return x ? `${x.identificador}:${x.edicao}${x.cancelada ? " (cancelada)" : ""}` : r; }).join(" · ") || "—"}` : undefined}>
          <Entrada rotulo="Pesquisar nesta família (nome, código, código legado)" value={busca} onChange={(e) => setBusca(e.target.value)} />
          {!itens.length ? <Vazio titulo="Nenhum item" descricao="Nenhum material desta família corresponde à pesquisa." /> : (
            <ul className="flex flex-col gap-2">{itens.map((i) => (
              <li key={i.codigo} className="rounded border border-line-soft p-3">
                <div className="flex items-center gap-2 flex-wrap"><strong>{i.nome}</strong><span className="mono text-xs text-text-3">{i.codigo}</span>{i.legado.length > 0 && <span className="text-xs text-text-3">também aceito como {i.legado.join(", ")}</span>}<span className="ml-auto text-xs text-text-3">aplica-se a: {i.aplicacoes.map((a) => a.toLowerCase()).join(", ") || "— (insumo de composição)"}</span></div>
                {i.resumo && <p className="text-xs text-text-2">{i.resumo}</p>}
                <Botao pequeno variante="fantasma" onClick={() => setAberto(aberto === i.codigo ? null : i.codigo)} aria-expanded={aberto === i.codigo}>{aberto === i.codigo ? "Ocultar propriedades" : `Propriedades (${i.propriedades.length})`}</Botao>
                {aberto === i.codigo && <Propriedades props={i.propriedades} />}
              </li>))}</ul>
          )}
          {familiaSel === "ACO_ARMADURA" && <div className="grid gap-3 sm:grid-cols-2">{Object.entries(c.bitolas).map(([cod, bs]) => (
            <div key={cod}><p className="text-sm font-medium">Bitolas {cod.split(".").pop()}</p>
              <Tabela linhas={bs} chave={(b) => String(b.mm)} colunas={[{ titulo: "φ (mm)", celula: (b) => b.mm, numerica: true }, { titulo: "Área (cm²)", celula: (b) => b.areaCm2, numerica: true }, { titulo: "Massa (kg/m)", celula: (b) => b.kgPorMetro, numerica: true }, { titulo: "Situação", celula: (b) => <span className="text-xs">{b.situacao}</span> }]} /></div>))}
            <p className="text-xs text-text-3 sm:col-span-2">Área e massa nominal pela geometria (π·φ²/4 × 7850 kg/m³). Material (CA-50, CA-60) e configuração (bitola, quantidade, espaçamento) são registrados separadamente no detalhamento.</p></div>}
        </Cartao>
        <div className="grid gap-4 lg:grid-cols-2">
          <Cartao titulo="Composições (camadas)" descricao="Uma composição combina materiais em camadas; não é um material.">
            <Tabela linhas={c.composicoes} chave={(x) => x.codigo} colunas={[{ titulo: "Composição", celula: (x) => <span>{x.nome}<span className="block mono text-xs text-text-3">{x.codigo}</span></span> }, { titulo: "Camadas", celula: (x) => <span className="text-xs">{x.camadas.map((k) => `${k.material.split(".").pop()} ${k.espessura * 100} cm`).join(" + ")}</span> }]} />
          </Cartao>
          <Cartao titulo="Serviços (orçamento)" descricao="Vínculo material → serviço. Códigos SINAPI herdados do catálogo anterior não foram conferidos na tabela vigente.">
            <Tabela linhas={c.servicos} chave={(x) => x.codigo} colunas={[{ titulo: "Serviço", celula: (x) => <span className="text-xs">{x.nome}</span> }, { titulo: "Un.", celula: (x) => x.unidade }, { titulo: "SINAPI", celula: (x) => <span className="text-xs">{x.sinapi ?? "—"}{x.situacaoSinapi && <span className="block text-warning">{x.situacaoSinapi}</span>}</span> }]} />
          </Cartao>
        </div>
        <Cartao titulo="Materiais personalizados da empresa" descricao="Valores declarados pelo fabricante ou de ensaio exigem fabricante, referência do produto e documento. Só entram no cálculo depois de validados por profissional com selo verificado; o histórico registra cada decisão.">
          {!c.personalizados.length ? <Vazio titulo="Nenhum material personalizado" descricao="Crie um quando o projeto especificar um material fora do catálogo." /> : (
            <Tabela linhas={c.personalizados} chave={(p) => p.id} colunas={[
              { titulo: "Material", celula: (p) => <span>{p.nome}<span className="block mono text-xs text-text-3">{p.codigo}{p.baseadoEm ? ` · baseado em ${p.baseadoEm}` : ""}</span></span> },
              { titulo: "Situação", celula: (p) => <Etiqueta tom={SITUACAO[p.situacao]?.tom ?? "aviso"}>{SITUACAO[p.situacao]?.rotulo ?? p.situacao}</Etiqueta> },
              { titulo: "Produto", celula: (p) => <span className="text-xs">{p.item.produto ? `${p.item.produto.fabricante} · ${p.item.produto.referencia} · ${p.item.produto.documento}` : "sem documentação de produto"}</span> },
              { titulo: "Propriedades", celula: (p) => <Propriedades props={p.item.propriedades} /> },
              { titulo: "Histórico", celula: (p) => <ul className="text-xs">{p.historico.map((h, k) => <li key={k}>{h.quando.slice(0, 10)} {h.acao.toLowerCase()}{h.motivo ? `: ${h.motivo}` : ""}</li>)}</ul> },
              { titulo: "", celula: (p) => <div className="flex flex-col gap-1">{p.situacao === "VALIDACAO_PENDENTE" && sessao?.seloVerificado && <><Botao pequeno onClick={() => decidir.mutate({ id: p.id, validar: true, motivo: "Conferido com o documento do produto" })}>Validar</Botao><Botao pequeno variante="secundario" onClick={() => { const m = window.prompt("Motivo da rejeição"); if (m) decidir.mutate({ id: p.id, validar: false, motivo: m }); }}>Rejeitar</Botao></>}<Botao pequeno variante="perigo" onClick={() => excluir.mutate(p.id)}>Excluir</Botao></div> },
            ]} />
          )}
          {pode && <form className="grid gap-3 sm:grid-cols-2 border-t border-line-soft pt-3" onSubmit={(e) => { e.preventDefault(); criar.mutate(); }}>
            <p className="sm:col-span-2 text-sm font-medium">Novo concreto personalizado</p>
            <Selecao rotulo="Baseado em" value={novo.baseadoEm} vazio="Nenhum" onChange={(e) => setNovo((v) => ({ ...v, baseadoEm: e.target.value }))} opcoes={c.itens.filter((i) => i.familia === "CONCRETO").map((i) => ({ valor: i.codigo, rotulo: i.nome }))} />
            <Entrada rotulo="Nome" obrigatorio value={novo.nome} onChange={(e) => setNovo((v) => ({ ...v, nome: e.target.value }))} placeholder="Concreto C32 — fornecedor X" />
            <Entrada rotulo="fck (MPa)" obrigatorio inputMode="decimal" value={novo.fck} onChange={(e) => setNovo((v) => ({ ...v, fck: e.target.value }))} />
            <Selecao rotulo="Origem do valor" value={novo.origem} onChange={(e) => setNovo((v) => ({ ...v, origem: e.target.value }))} opcoes={[{ valor: "ESPECIFICADO", rotulo: "Especificado em projeto" }, { valor: "DECLARADO_FABRICANTE", rotulo: "Declarado pelo fabricante" }, { valor: "ENSAIO", rotulo: "Ensaio" }]} />
            <Entrada rotulo="Fabricante" value={novo.fabricante} onChange={(e) => setNovo((v) => ({ ...v, fabricante: e.target.value }))} />
            <Entrada rotulo="Referência do produto" value={novo.referencia} onChange={(e) => setNovo((v) => ({ ...v, referencia: e.target.value }))} />
            <Entrada rotulo="Documento (ficha técnica, laudo)" value={novo.documento} onChange={(e) => setNovo((v) => ({ ...v, documento: e.target.value }))} className="sm:col-span-2" />
            <div className="sm:col-span-2"><Botao type="submit" carregando={criar.isPending} disabled={!novo.nome || !novo.fck}>Criar (validação pendente)</Botao></div>
          </form>}
        </Cartao>
      </div>
    </>
  );
}
