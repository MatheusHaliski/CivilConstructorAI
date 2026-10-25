"use client";
import { useEffect } from "react";
import { usePathname, useRouter } from "next/navigation";
import { useSessao } from "@/lib/sessao";
import { BarraLateral } from "@/components/layout/BarraLateral";
import { Carregando } from "@/components/ui";

/** Área autenticada: barra lateral + conteúdo. Sem sessão → /entrar (a autorização real é do backend). */
export default function LayoutApp({ children }: { children: React.ReactNode }) {
  const { carregando, sessao } = useSessao();
  const router = useRouter();
  const caminho = usePathname();
  useEffect(() => { if (!carregando && !sessao) router.replace(`/entrar?voltar=${encodeURIComponent(caminho)}`); }, [carregando, sessao, router, caminho]);
  if (carregando || !sessao) return <main className="min-h-dvh grid place-items-center"><Carregando texto="Verificando sua sessão…" /></main>;
  return (
    <div className="min-h-dvh lg:grid lg:grid-cols-[16rem_1fr]">
      <BarraLateral />
      <main id="conteudo" className="min-w-0 px-4 py-6 sm:px-6 lg:px-8 lg:py-8">{children}</main>
    </div>
  );
}
