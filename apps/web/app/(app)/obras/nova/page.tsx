"use client";
import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { ErroApi, post } from "@/lib/api";
import { useAcao } from "@/lib/dados";
import type { ObraResumo } from "@/lib/tipos";
import { Botao, CabecalhoPagina, Cartao, Entrada } from "@/components/ui";

const UFS = ["AC","AL","AP","AM","BA","CE","DF","ES","GO","MA","MT","MS","MG","PA","PB","PR","PE","PI","RJ","RN","RS","RO","RR","SC","SP","SE","TO"];

/** RF22 · Criar obra (perímetro e localização em PostGIS; opcionais no início). */
export default function PaginaNovaObra() {
  const router = useRouter();
  const [f, setF] = useState({ nome: "", endereco: "", municipio: "", uf: "", lat: "", lon: "" });
  const criar = useAcao((v: typeof f) => post<ObraResumo>("/obras", { nome: v.nome, endereco: v.endereco || null, municipio: v.municipio || null, uf: v.uf || null, localizacao: v.lat && v.lon ? [Number(v.lon.replace(",", ".")), Number(v.lat.replace(",", "."))] : null }), { invalidar: [["obras"]], sucesso: "Obra criada." });
  const erro = criar.error instanceof ErroApi ? criar.error : null;
  const set = (k: keyof typeof f) => (e: { target: { value: string } }) => setF((v) => ({ ...v, [k]: e.target.value }));
  const enviar = (e: FormEvent) => { e.preventDefault(); criar.mutate(f, { onSuccess: (o) => router.push(`/obras/${o.id}`) }); };
  return (
    <>
      <CabecalhoPagina titulo="Nova obra" migalhas={<span>Obras › Nova</span>} />
      <Cartao className="max-w-2xl">
        <form onSubmit={enviar} className="flex flex-col gap-4" noValidate>
          <Entrada rotulo="Nome da obra" value={f.nome} onChange={set("nome")} erro={erro?.campo("nome")} obrigatorio />
          <Entrada rotulo="Endereço" value={f.endereco} onChange={set("endereco")} erro={erro?.campo("endereco")} />
          <div className="grid gap-4 sm:grid-cols-[1fr_8rem]"><Entrada rotulo="Município" value={f.municipio} onChange={set("municipio")} /><Entrada rotulo="UF" list="ufs" maxLength={2} value={f.uf} onChange={(e) => setF((v) => ({ ...v, uf: e.target.value.toUpperCase() }))} erro={erro?.campo("uf")} /><datalist id="ufs">{UFS.map((u) => <option key={u} value={u} />)}</datalist></div>
          <div className="grid gap-4 sm:grid-cols-2"><Entrada rotulo="Latitude" inputMode="decimal" value={f.lat} onChange={set("lat")} ajuda="Opcional; usada no perímetro e nas sondagens." className="mono" /><Entrada rotulo="Longitude" inputMode="decimal" value={f.lon} onChange={set("lon")} className="mono" /></div>
          <div className="flex gap-2"><Botao type="submit" carregando={criar.isPending}>Criar obra</Botao><Botao type="button" variante="secundario" onClick={() => router.back()}>Cancelar</Botao></div>
        </form>
      </Cartao>
    </>
  );
}
