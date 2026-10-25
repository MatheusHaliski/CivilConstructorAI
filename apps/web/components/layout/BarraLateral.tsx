"use client";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState, type ComponentType } from "react";
import { LogOut, Menu, X } from "lucide-react";
import { useSessao } from "@/lib/sessao";
import { cx } from "@/components/ui";
import { IconeConta, IconeEmpresa, IconeIa, IconeObras, IconePlataforma, IconePrivacidade, Logomarca, type IconeMarcaProps } from "@/components/marca/IconesMarca";

type Item = { href: string; rotulo: string; Icone: ComponentType<IconeMarcaProps>; exato?: boolean; so?: (s: { papelEmpresa: string; suportePlataforma?: boolean }) => boolean };

const ITENS: Item[] = [
  { href: "/obras", rotulo: "Obras", Icone: IconeObras },
  { href: "/empresa", rotulo: "Empresa", Icone: IconeEmpresa },
  { href: "/ia", rotulo: "Uso de IA", Icone: IconeIa },
  { href: "/conta/privacidade", rotulo: "Seus dados", Icone: IconePrivacidade },
  { href: "/conta", rotulo: "Conta", Icone: IconeConta, exato: true },
  { href: "/plataforma/selos", rotulo: "Plataforma", Icone: IconePlataforma, so: (s) => (s as { suportePlataforma?: boolean }).suportePlataforma === true },
];

export function BarraLateral() {
  const caminho = usePathname();
  const { sessao, sair } = useSessao();
  const [aberta, setAberta] = useState(false);
  const ativo = (i: Item) => (i.exato ? caminho === i.href : caminho === i.href || caminho.startsWith(i.href + "/"));

  const lista = (
    <nav aria-label="Principal" className="flex flex-col gap-1">
      {ITENS.filter((i) => !i.so || (sessao && i.so(sessao))).map((i) => {
        const on = ativo(i);
        return (
          <Link key={i.href} href={i.href} aria-current={on ? "page" : undefined} onClick={() => setAberta(false)}
            className={cx("tap flex items-center gap-3 rounded-[var(--radius-sm)] px-3 text-sm font-medium transition-colors", on ? "bg-surface text-text" : "text-text-2 hover:bg-surface hover:text-text")}>
            <i.Icone ativo={on} tamanho={24} className={on ? "text-marca-grafite" : "text-current"} />
            <span>{i.rotulo}</span>
          </Link>
        );
      })}
    </nav>
  );

  return (
    <>
      <header className="lg:hidden flex items-center justify-between gap-3 border-b border-line-soft bg-bg px-4 h-14">
        <Link href="/obras" className="flex items-center gap-2 font-semibold text-text"><Logomarca tamanho={30} /> CivilConstructorAI</Link>
        <button type="button" aria-expanded={aberta} aria-controls="menu-lateral" aria-label={aberta ? "Fechar menu" : "Abrir menu"} onClick={() => setAberta((v) => !v)} className="tap px-2 rounded-[var(--radius-sm)] hover:bg-surface">
          {aberta ? <X className="size-6" aria-hidden /> : <Menu className="size-6" aria-hidden />}
        </button>
      </header>
      <aside id="menu-lateral" className={cx("fixed inset-y-0 left-0 z-40 w-64 flex-col border-r border-line-soft bg-bg p-4 gap-6 lg:static lg:flex", aberta ? "flex top-14 shadow-xl" : "hidden")}>
        <Link href="/obras" className="hidden lg:flex items-center gap-3 px-2">
          <Logomarca tamanho={40} />
          <span className="font-semibold leading-tight text-text">CivilConstructor<span className="text-obra-andamento">AI</span><span className="block text-[11px] font-normal text-text-3">CAD/BIM generativo</span></span>
        </Link>
        {lista}
        <div className="mt-auto flex flex-col gap-2 border-t border-line-soft pt-4">
          {sessao && <p className="px-3 text-xs text-text-3">{sessao.papelEmpresa ? `Papel na empresa: ${sessao.papelEmpresa}` : "Sem empresa ativa"}{sessao.seloVerificado && " · selo verificado"}</p>}
          <button type="button" onClick={() => void sair().then(() => (window.location.href = "/entrar"))} className="tap flex items-center gap-3 rounded-[var(--radius-sm)] px-3 text-sm text-text-2 hover:bg-surface hover:text-text">
            <LogOut className="size-5" aria-hidden /> Sair
          </button>
        </div>
      </aside>
    </>
  );
}
