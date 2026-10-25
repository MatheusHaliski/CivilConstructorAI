"use client";
import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useSessao } from "@/lib/sessao";
import { Carregando } from "@/components/ui";

export default function Inicio() {
  const { carregando, sessao } = useSessao();
  const router = useRouter();
  useEffect(() => { if (!carregando) router.replace(sessao ? "/obras" : "/entrar"); }, [carregando, sessao, router]);
  return <main className="min-h-dvh grid place-items-center"><Carregando /></main>;
}
