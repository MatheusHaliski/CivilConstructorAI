"use client";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { ArrowLeft, LogOut } from "lucide-react";
import { useSessao } from "@/lib/sessao";
import { useDados } from "@/lib/dados";
import type { ObraResumo } from "@/lib/tipos";
import { gruposGeraisCompactos, gruposGlobais, gruposObra, itemAtivo, obraDoCaminho, type GrupoNav } from "@/lib/navegacao";
import { cx } from "@/components/ui";
import { IconeObras } from "@/components/marca/IconesMarca";

/**
 * Barra lateral agrupada: título do grupo em maiúsculas, todos os itens visíveis, ícone num disco creme
 * e item ativo em pílula escura. Dentro de uma obra mostra as abas da obra e, abaixo, atalhos gerais.
 */
export function BarraLateral({ aberta, recolhida, onNavegar }: { aberta: boolean; recolhida: boolean; onNavegar: () => void }) {
  const caminho = usePathname();
  const { sessao, sair } = useSessao();
  const obraId = obraDoCaminho(caminho);
  const obra = useDados<ObraResumo>(["obra", obraId], `/obras/${obraId}`, { ativo: !!obraId });
  const suporte = (sessao as { suportePlataforma?: boolean } | null)?.suportePlataforma === true;
  const grupos: GrupoNav[] = obraId ? [...gruposObra(obraId), gruposGeraisCompactos()] : gruposGlobais({ suportePlataforma: suporte });

  return (
    <aside id="menu-lateral" aria-label="Navegação"
      className={cx("z-30 bg-bg lg:bg-transparent lg:block lg:sticky lg:top-16 lg:h-[calc(100dvh-4rem)] lg:p-3",
        aberta ? "fixed inset-x-0 top-16 bottom-0 block overflow-y-auto p-3 shadow-xl" : "hidden",
        recolhida && "lg:hidden")}>
      <div className="flex h-full flex-col gap-1 overflow-y-auto rounded-[var(--radius-lg)] border border-line-soft bg-bg p-3 lg:shadow-sm">
        {obraId && (
          <div className="mb-2 flex flex-col gap-2 border-b border-line-soft pb-3">
            <Link href="/obras" onClick={onNavegar} className="tap inline-flex items-center gap-2 rounded-full px-2 text-xs font-medium text-text-2 hover:bg-surface"><ArrowLeft className="size-4" aria-hidden /> Todas as obras</Link>
            <div className="flex items-center gap-3 px-2">
              <span className="grid size-10 shrink-0 place-items-center rounded-full bg-marca-creme text-marca-grafite"><IconeObras tamanho={24} /></span>
              <span className="min-w-0"><span className="block text-[11px] font-semibold uppercase tracking-wider text-text-3">Obra atual</span><span className="block truncate font-semibold text-text">{obra.data?.nome ?? "…"}</span></span>
            </div>
          </div>
        )}
        <nav aria-label={obraId ? "Abas da obra" : "Principal"} className="flex flex-col gap-4">
          {grupos.map((g) => (
            <section key={g.id} aria-labelledby={`grupo-${g.id}`} className="flex flex-col gap-0.5">
              <h2 id={`grupo-${g.id}`} className="px-3 pb-1 text-[11px] font-semibold uppercase tracking-[0.12em] text-text-3">{g.titulo}</h2>
              <ul className="flex flex-col gap-0.5">
                {g.itens.map((i) => {
                  const on = itemAtivo(i, caminho);
                  return (
                    <li key={i.href}>
                      <Link href={i.href} aria-current={on ? "page" : undefined} onClick={onNavegar}
                        className={cx("tap flex items-center gap-3 rounded-full py-1 pl-1 pr-3 text-sm transition-colors duration-[var(--dur-fast)]",
                          on ? "bg-text font-semibold text-bg" : "text-text hover:bg-surface")}>
                        <span className="grid size-8 shrink-0 place-items-center rounded-full bg-marca-creme text-marca-grafite"><i.Icone className="size-4" strokeWidth={1.9} aria-hidden /></span>
                        <span className="truncate">{i.rotulo}</span>
                      </Link>
                    </li>
                  );
                })}
              </ul>
            </section>
          ))}
        </nav>
        <div className="mt-auto flex flex-col gap-2 border-t border-line-soft pt-3">
          {sessao && <p className="px-3 text-xs text-text-3">{sessao.papelEmpresa ? `Papel na empresa: ${sessao.papelEmpresa}` : "Sem empresa ativa"}{sessao.seloVerificado && " · selo verificado"}</p>}
          <button type="button" onClick={() => void sair().then(() => (window.location.href = "/entrar"))} className="tap flex items-center gap-3 rounded-full py-1 pl-1 pr-3 text-sm text-text-2 hover:bg-surface hover:text-text">
            <span className="grid size-8 place-items-center rounded-full bg-surface"><LogOut className="size-4" aria-hidden /></span> Sair
          </button>
        </div>
      </div>
    </aside>
  );
}
