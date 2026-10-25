"use client";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useId, useMemo, useRef, useState } from "react";
import { Building2, Menu, PanelLeftClose, PanelLeftOpen, Search, X } from "lucide-react";
import { useSessao } from "@/lib/sessao";
import { useDados } from "@/lib/dados";
import type { Perfil } from "@/lib/tipos";
import { buscarNavegacao, gruposGlobais, gruposObra, obraDoCaminho } from "@/lib/navegacao";
import { cx } from "@/components/ui";
import { Logomarca } from "@/components/marca/IconesMarca";

/** Barra superior: menu, marca, busca de seções/sub-abas (sem decorar caminhos) e usuário. */
export function BarraSuperior({ menuAberto, recolhida, alternarMenu, alternarRecolhida }: { menuAberto: boolean; recolhida: boolean; alternarMenu: () => void; alternarRecolhida: () => void }) {
  const { sessao } = useSessao();
  const perfil = useDados<Perfil>(["perfil"], "/me/profile", { ativo: !!sessao });
  const nome = perfil.data?.nome ?? "";
  const empresas = useDados<{ id: string; nome: string }[]>(["minhas-empresas"], "/me/empresas", { ativo: !!sessao && !sessao.mfaPendente });
  const empresaAtiva = empresas.data?.find((e) => e.id === sessao?.empresaAtivaId)?.nome;
  const iniciais = nome.split(/\s+/).filter(Boolean).slice(0, 2).map((p) => p[0]!.toUpperCase()).join("") || "?";
  return (
    <header className="sticky top-0 z-40 flex h-16 items-center gap-2 border-b border-line-soft bg-bg/95 px-3 backdrop-blur sm:gap-3 sm:px-4">
      <button type="button" aria-expanded={menuAberto} aria-controls="menu-lateral" aria-label={menuAberto ? "Fechar menu" : "Abrir menu"} onClick={alternarMenu} className="tap grid place-items-center rounded-full px-2 hover:bg-surface lg:hidden">
        {menuAberto ? <X className="size-6" aria-hidden /> : <Menu className="size-6" aria-hidden />}
      </button>
      <button type="button" aria-expanded={!recolhida} aria-controls="menu-lateral" aria-label={recolhida ? "Mostrar barra lateral" : "Recolher barra lateral"} onClick={alternarRecolhida} className="tap hidden place-items-center rounded-full px-2 hover:bg-surface lg:grid">
        {recolhida ? <PanelLeftOpen className="size-5" aria-hidden /> : <PanelLeftClose className="size-5" aria-hidden />}
      </button>
      <Link href="/obras" className="flex shrink-0 items-center gap-2 font-semibold text-text">
        <Logomarca tamanho={34} />
        <span className="hidden leading-tight sm:block">CivilConstructor<span className="text-obra-andamento">AI</span></span>
      </Link>
      <BuscaGlobal />
      {empresaAtiva && <Link href="/empresa/dados" title="Empresa ativa (troque em Empresa › Dados)" className="tap ml-auto hidden shrink-0 items-center gap-2 rounded-full border border-line-soft px-3 text-sm text-text-2 hover:bg-surface lg:inline-flex"><Building2 className="size-4" aria-hidden /><span className="max-w-48 truncate">{empresaAtiva}</span></Link>}
      <Link href="/conta" className={cx("tap flex shrink-0 items-center gap-2 rounded-full py-1 pl-1 pr-1 hover:bg-surface sm:pr-3", empresaAtiva ? "ml-auto lg:ml-0" : "ml-auto")} aria-label={nome ? `Minha conta: ${nome}` : "Minha conta"}>
        <span className="grid size-9 place-items-center overflow-hidden rounded-full bg-marca-grafite text-sm font-semibold text-white">
          {perfil.data?.fotoUrl ? <img src={perfil.data.fotoUrl} alt="" className="size-full object-cover" /> : iniciais}
        </span>
        <span className="hidden max-w-40 truncate text-sm font-medium text-text md:block">{nome || "Minha conta"}</span>
      </Link>
    </header>
  );
}

function BuscaGlobal() {
  const caminho = usePathname();
  const router = useRouter();
  const { sessao } = useSessao();
  const obraId = obraDoCaminho(caminho);
  const [termo, setTermo] = useState("");
  const [aberta, setAberta] = useState(false);
  const [sel, setSel] = useState(0);
  const id = useId();
  const caixa = useRef<HTMLInputElement>(null);
  const grupos = useMemo(() => [...(obraId ? gruposObra(obraId) : []), ...gruposGlobais({ suportePlataforma: (sessao as { suportePlataforma?: boolean } | null)?.suportePlataforma === true })], [obraId, sessao]);
  const resultados = useMemo(() => buscarNavegacao(grupos, termo), [grupos, termo]);
  const ir = (href: string) => { setTermo(""); setAberta(false); caixa.current?.blur(); router.push(href); };
  const mostrar = aberta && termo.trim().length > 0;
  return (
    <div className="relative mx-auto w-full max-w-xl">
      <label htmlFor={`${id}-q`} className="sr-only">Buscar seções e sub-abas</label>
      <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-text-3" aria-hidden />
      <input ref={caixa} id={`${id}-q`} type="search" role="combobox" autoComplete="off" placeholder={obraId ? "Buscar nesta obra e no sistema…" : "Buscar…"}
        aria-expanded={mostrar} aria-controls={`${id}-lista`} aria-autocomplete="list" aria-activedescendant={mostrar && resultados[sel] ? `${id}-r${sel}` : undefined}
        value={termo} onChange={(e) => { setTermo(e.target.value); setSel(0); setAberta(true); }} onFocus={() => setAberta(true)} onBlur={() => setTimeout(() => setAberta(false), 150)}
        onKeyDown={(e) => {
          if (e.key === "ArrowDown") { e.preventDefault(); setSel((s) => Math.min(s + 1, resultados.length - 1)); }
          else if (e.key === "ArrowUp") { e.preventDefault(); setSel((s) => Math.max(s - 1, 0)); }
          else if (e.key === "Enter" && resultados[sel]) { e.preventDefault(); ir(resultados[sel].href); }
          else if (e.key === "Escape") { setAberta(false); }
        }}
        className="tap w-full rounded-full border border-line-soft bg-surface pl-9 pr-4 text-sm text-text placeholder:text-text-3 focus:bg-bg" />
      {mostrar && (
        <ul id={`${id}-lista`} role="listbox" aria-label="Resultados" className="absolute inset-x-0 top-full mt-2 max-h-96 overflow-y-auto rounded-[var(--radius-md)] border border-line-soft bg-bg p-1 shadow-xl">
          {resultados.length === 0 && <li className="px-3 py-2 text-sm text-text-3" role="presentation">Nada encontrado para “{termo}”.</li>}
          {resultados.map((r, i) => (
            <li key={r.href} id={`${id}-r${i}`} role="option" aria-selected={i === sel} onMouseDown={(e) => { e.preventDefault(); ir(r.href); }} onMouseEnter={() => setSel(i)}
              className={cx("flex cursor-pointer items-center gap-3 rounded-[var(--radius-sm)] px-2 py-1.5", i === sel && "bg-surface")}>
              <span className="grid size-8 shrink-0 place-items-center rounded-full bg-marca-creme text-marca-grafite"><r.Icone className="size-4" aria-hidden /></span>
              <span className="min-w-0"><span className="block truncate text-sm font-medium text-text">{r.rotulo}</span><span className="block truncate text-xs text-text-3">{r.grupo}{r.descricao ? ` · ${r.descricao}` : ""}</span></span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
