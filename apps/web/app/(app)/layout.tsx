"use client";
import { useEffect, useState } from "react";
import { usePathname, useRouter } from "next/navigation";
import { useSessao } from "@/lib/sessao";
import { BarraLateral } from "@/components/layout/BarraLateral";
import { BarraSuperior } from "@/components/layout/BarraSuperior";
import { Carregando, cx } from "@/components/ui";

/** Área autenticada: barra superior + lateral agrupada + conteúdo. Sem sessão → /entrar (a autorização real é do backend). */
export default function LayoutApp({ children }: { children: React.ReactNode }) {
  const { carregando, sessao } = useSessao();
  const router = useRouter();
  const caminho = usePathname();
  const [menuAberto, setMenuAberto] = useState(false);
  const [recolhida, setRecolhida] = useState(false);
  useEffect(() => { if (!carregando && !sessao) router.replace(`/entrar?voltar=${encodeURIComponent(caminho)}`); }, [carregando, sessao, router, caminho]);
  useEffect(() => { try { setRecolhida(localStorage.getItem("cca.lateral.recolhida") === "1"); } catch { /* sem storage */ } }, []);
  const alternarRecolhida = () => setRecolhida((v) => { try { localStorage.setItem("cca.lateral.recolhida", v ? "0" : "1"); } catch { /* sem storage */ } return !v; });
  if (carregando || !sessao) return <main className="min-h-dvh grid place-items-center"><Carregando texto="Verificando sua sessão…" /></main>;
  return (
    <div className="min-h-dvh bg-surface/60">
      <BarraSuperior menuAberto={menuAberto} recolhida={recolhida} alternarMenu={() => setMenuAberto((v) => !v)} alternarRecolhida={alternarRecolhida} />
      <div className={cx("lg:grid", recolhida ? "lg:grid-cols-[1fr]" : "lg:grid-cols-[17.5rem_1fr]")}>
        <BarraLateral aberta={menuAberto} recolhida={recolhida} onNavegar={() => setMenuAberto(false)} />
        <main id="conteudo" className="min-w-0 px-4 py-6 sm:px-6 lg:px-8 lg:py-8">{children}</main>
      </div>
    </div>
  );
}
