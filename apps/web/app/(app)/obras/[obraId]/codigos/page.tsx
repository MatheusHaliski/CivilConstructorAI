"use client";
import { use, useMemo, useState } from "react";
import Link from "next/link";
import { Copy, Search } from "lucide-react";
import { CATALOGO, TABELA_A, buscarComandos, formatarVI, type CategoriaComando, type PerfilAtalhos } from "@cca/notacao-vi";
import { ACOES, codigoMaterial } from "@/lib/acoes";
import { moeda, numero } from "@/lib/formatos";
import { Cartao, Etiqueta, Selecao, Tabela, avisar, cx } from "@/components/ui";
import { CabecalhoObra } from "@/components/obra/comuns";

const CATEGORIAS: CategoriaComando[] = ["DESENHO", "MODIFICACAO", "ANOTACAO", "VISUALIZACAO", "MODO", "CONFIG"];

/** RF29 · catálogo navegável: comandos do motor (aliases AutoCAD/Revit), famílias de código e materiais, sem precisar decorar nada. */
export default function PaginaCodigos({ params }: { params: Promise<{ obraId: string }> }) {
  const { obraId } = use(params);
  const [termo, setTermo] = useState("");
  const [perfil, setPerfil] = useState<PerfilAtalhos>("CIVILCONSTRUCTOR");
  const [cat, setCat] = useState<CategoriaComando | "">("");
  const comandos = useMemo(() => (termo.trim() ? buscarComandos(termo, perfil, 200) : CATALOGO).filter((c) => !cat || c.categoria === cat), [termo, perfil, cat]);
  const acaoDe = (codigo: string) => ACOES.find((a) => a.codigoVi === codigo);
  const copiar = (t: string) => void navigator.clipboard?.writeText(t).then(() => avisar("Copiado.", "sucesso"));
  return (
    <>
      <CabecalhoObra obraId={obraId} grupo="Ações e códigos" secao="Códigos construtivos" descricao={`${CATALOGO.length} comandos do motor, ${ACOES.length} ações guiadas e ${TABELA_A.length} materiais. Clique para usar; não é preciso digitar códigos.`} />
      <div className="flex flex-col gap-4">
        <Cartao titulo="Famílias de código" descricao="Identificadores estáveis, separados do nome exibido. Proposta interna do produto (não são códigos de norma).">
          <Tabela linhas={[
            { f: "MOD.", ex: "MOD.CRIAR.PAREDE", s: "Ação de modelagem executável; vira um comando da Notação VI auditado.", n: ACOES.length },
            { f: "MAT.", ex: "MAT.ESTRUTURAL.CONC_FCK30", s: "Material da Tabela A com propriedades físicas, estilo, restrições e custo-base.", n: TABELA_A.length },
            { f: "VI", ex: "PAREDE(p1=(0,0), p2=(5,0), esp=0.15)", s: "Comando do motor (Tabela B); aliases por perfil AutoCAD/Revit.", n: CATALOGO.length },
          ]} chave={(l) => l.f} colunas={[{ titulo: "Família", celula: (l) => <span className="mono font-semibold">{l.f}</span> }, { titulo: "Exemplo", celula: (l) => <code className="mono text-xs">{l.ex}</code> }, { titulo: "Significado", celula: (l) => l.s }, { titulo: "Itens", celula: (l) => l.n, numerica: true }]} />
        </Cartao>

        <Cartao titulo="Comandos do motor" descricao="Busca por nome, alias ou descrição; os aliases mudam conforme o perfil de atalhos."
          acoes={<div className="flex flex-wrap items-end gap-2"><Selecao rotulo="Perfil" value={perfil} onChange={(e) => setPerfil(e.target.value as PerfilAtalhos)} opcoes={[{ valor: "CIVILCONSTRUCTOR", rotulo: "CivilConstructor" }, { valor: "AUTOCAD", rotulo: "AutoCAD" }, { valor: "REVIT", rotulo: "Revit" }]} />
            <Selecao rotulo="Categoria" value={cat} onChange={(e) => setCat(e.target.value as CategoriaComando | "")} vazio="Todas" opcoes={CATEGORIAS.map((c) => ({ valor: c, rotulo: c.toLowerCase() }))} /></div>}>
          <label className="relative block max-w-md"><span className="sr-only">Buscar comandos</span><Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-text-3" aria-hidden />
            <input type="search" value={termo} onChange={(e) => setTermo(e.target.value)} placeholder="parede, offset, cota, L, CO…" className="tap w-full rounded-full border border-line bg-bg pl-9 pr-3 text-sm" /></label>
          <p className="text-xs text-text-3" aria-live="polite">{comandos.length} comando(s)</p>
          <Tabela linhas={comandos} chave={(c) => c.codigo} colunas={[
            { titulo: "Comando", celula: (c) => <span><span className="font-medium">{c.nomePtBr}</span> <span className="text-xs text-text-3">{c.nomeEn}</span>{c.extensao && <Etiqueta tom="info" className="ml-1">extensão</Etiqueta>}</span> },
            { titulo: "Alias", celula: (c) => <span className="mono text-xs">{(c.aliases[perfil] ?? []).join(", ") || "—"}</span> },
            { titulo: "Categoria", celula: (c) => <Etiqueta tom="neutro">{c.categoria.toLowerCase()}</Etiqueta> },
            { titulo: "O que faz", celula: (c) => <details className="text-xs"><summary className="cursor-pointer">{c.descricao}</summary><ol className="mono mt-1 list-decimal pl-4 text-text-2">{c.roteiro.map((p, i) => <li key={i}>{p.mensagem}</li>)}</ol><p className="mt-1 text-text-3">{c.rf.join(" · ")}</p></details> },
            { titulo: "", celula: (c) => { const a = acaoDe(c.codigo); return (
              <span className="flex flex-col items-end gap-2">
                {a ? <Link href={`/obras/${obraId}/acoes?acao=${a.id}`} className="tap inline-flex items-center rounded-full bg-text px-3 text-xs font-medium text-bg">Usar com formulário</Link>
                  : <Link href={`/obras/${obraId}/modelo?cmd=${encodeURIComponent((c.aliases[perfil] ?? [c.codigo])[0]!)}`} className="inline-flex min-h-6 items-center text-xs text-brand-text underline">Abrir no modelo</Link>}
                <button type="button" onClick={() => copiar(formatarVI(c.codigo, {}))} className="inline-flex min-h-6 items-center gap-1 text-xs text-text-3 hover:text-text"><Copy className="size-3" aria-hidden /> copiar VI</button>
              </span>); } },
          ]} />
        </Cartao>

        <Cartao titulo="Materiais (MAT.)" descricao="Tabela A: o código MAT. e o materialId legado são aceitos; propriedades físicas, visuais e comerciais ficam separadas.">
          <Tabela linhas={TABELA_A} chave={(m) => m.materialId} colunas={[
            { titulo: "Código", celula: (m) => <span className="flex flex-col"><span className="mono text-xs font-semibold">{codigoMaterial(m)}</span><span className="mono text-[11px] text-text-3">legado {m.materialId}</span></span> },
            { titulo: "Nome", celula: (m) => <span className="flex items-center gap-2"><span aria-hidden className={cx("inline-block size-3 rounded-sm border border-line")} style={{ background: m.style.colorHex }} />{m.name}</span> },
            { titulo: "Físicas", celula: (m) => <span className="mono text-xs">{[m.physicalProperties.fck_MPa && `fck ${m.physicalProperties.fck_MPa} MPa`, m.physicalProperties.fyk_MPa && `fyk ${m.physicalProperties.fyk_MPa} MPa`, `γ ${numero(m.physicalProperties.densidade_kN_m3)} kN/m³`, m.physicalProperties.fireRating].filter(Boolean).join(" · ")}</span> },
            { titulo: "Restrições", celula: (m) => <span className="mono text-xs">{m.geometricConstraints.secaoMinima_m ? `seção ≥ ${numero(m.geometricConstraints.secaoMinima_m)} m` : m.geometricConstraints.espessuraMinima_m ? `esp ≥ ${numero(m.geometricConstraints.espessuraMinima_m)} m` : "—"}</span> },
            { titulo: "Comercial (referência)", celula: (m) => <span className="text-xs">{moeda(m.baseQuantities.unitCost)}/{m.baseQuantities.unit}{m.baseQuantities.sinapi ? ` · SINAPI ${m.baseQuantities.sinapi}` : ""}</span> },
          ]} />
          <p className="text-xs text-text-3">O custo da Tabela A é só referência de catálogo; o orçamento usa a tabela SINAPI importada (fonte, UF e data-base em Custos › Orçamento).</p>
        </Cartao>
      </div>
    </>
  );
}
