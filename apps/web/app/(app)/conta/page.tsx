"use client";
import { useEffect, useState } from "react";
import { BadgeCheck, Camera } from "lucide-react";
import { patch, post } from "@/lib/api";
import { useAcao, useDados } from "@/lib/dados";
import { useSessao } from "@/lib/sessao";
import type { Preferencias, TipoSelo } from "@/lib/tipos";
import { Aviso, Botao, CabecalhoPagina, Cartao, Carregando, Entrada, Etiqueta, Selecao } from "@/components/ui";

type Perfil = { id: string; nome: string; email: string; tipo: string; estado: string; cnpj?: string; registroProfissional?: string; registroUf?: string; seloTipo?: TipoSelo; seloEstado: string; mfaAtivo: boolean; mfaObrigatorio: boolean; fotoUrl?: string };
type Selo = { tipo: string; estado: "NONE" | "PENDING" | "VERIFIED" | "REJECTED"; registro: string; uf: string; motivo?: string };
const UFS = ["AC","AL","AP","AM","BA","CE","DF","ES","GO","MA","MT","MS","MG","PA","PB","PR","PE","PI","RJ","RN","RS","RO","RR","SC","SP","SE","TO"].map((u) => ({ valor: u, rotulo: u }));
const SELO: Record<Selo["estado"], { rotulo: string; tom: "neutro" | "aviso" | "sucesso" | "perigo" }> = { NONE: { rotulo: "Sem selo", tom: "neutro" }, PENDING: { rotulo: "Em verificação", tom: "aviso" }, VERIFIED: { rotulo: "Verificado", tom: "sucesso" }, REJECTED: { rotulo: "Recusado", tom: "perigo" } };

/** RF23 perfil e foto (upload pré-assinado, sem EXIF) · preferências (tema, fonte) · RF21 selo profissional CREA/CAU/CNPJ. */
export default function PaginaConta() {
  const { recarregar } = useSessao();
  const perfil = useDados<Perfil>(["perfil"], "/me/profile");
  const selo = useDados<Selo>(["selo"], "/me/selo");
  const pref = useDados<Preferencias>(["preferencias"], "/me/preferences");
  const [f, setF] = useState({ nome: "", registroProfissional: "", registroUf: "", cnpj: "" });
  const [p, setP] = useState<Preferencias>({});
  const [s, setS] = useState({ tipo: "CREA" as TipoSelo, registro: "", uf: "" });
  const [doc, setDoc] = useState<File | null>(null);
  useEffect(() => { if (perfil.data) setF({ nome: perfil.data.nome ?? "", registroProfissional: perfil.data.registroProfissional ?? "", registroUf: perfil.data.registroUf ?? "", cnpj: perfil.data.cnpj ?? "" }); }, [perfil.data]);
  useEffect(() => { if (pref.data) setP(pref.data); }, [pref.data]);
  useEffect(() => { document.documentElement.dataset.theme = p.tema === "ESCURO" ? "dark" : p.tema === "CLARO" ? "light" : ""; document.documentElement.style.setProperty("--fonte-pct", String((p.fontePct ?? 100) / 100)); }, [p.tema, p.fontePct]);

  const salvar = useAcao((v: typeof f) => patch("/me/profile", { nome: v.nome, registroProfissional: v.registroProfissional || null, registroUf: v.registroUf || null, cnpj: v.cnpj || null }), { invalidar: [["perfil"], ["selo"]], sucesso: "Perfil atualizado. Mudar nome, registro ou CNPJ de conta verificada volta o selo para verificação." });
  const salvarPref = useAcao((v: Preferencias) => patch("/me/preferences", v), { invalidar: [["preferencias"]], sucesso: "Preferências salvas." });
  const foto = useAcao(async (arquivo: File) => {
    const { url } = await post<{ url: string }>("/me/photo/upload-url", { contentType: arquivo.type });
    const r = await fetch(url, { method: "PUT", body: arquivo, headers: { "Content-Type": arquivo.type } });
    if (!r.ok) throw new Error("Falha ao enviar a foto.");
    return post("/me/photo/confirm");
  }, { invalidar: [["perfil"]], sucesso: "Foto atualizada (recortada em 512 px, sem metadados EXIF)." });
  const pedirSelo = useAcao(async (v: typeof s) => {
    const r = await post<{ urlDocumento: string }>("/me/selo", { tipo: v.tipo, registro: v.registro || null, uf: v.uf || null, contentTypeDocumento: doc?.type ?? "application/pdf" });
    if (doc) { const up = await fetch(r.urlDocumento, { method: "PUT", body: doc, headers: { "Content-Type": doc.type || "application/pdf" } }); if (!up.ok) throw new Error("Falha ao enviar o documento."); }
    await recarregar();
  }, { invalidar: [["selo"], ["perfil"]], sucesso: "Pedido de verificação enviado." });

  if (perfil.isLoading) return <Carregando />;
  const d = perfil.data;
  return (
    <>
      <CabecalhoPagina titulo="Conta" descricao="Seus dados de perfil, preferências e o selo profissional que habilita aprovações." />
      <div className="grid gap-4 lg:grid-cols-3">
        <Cartao titulo="Perfil" className="lg:col-span-2">
          <div className="flex items-center gap-4">
            {/* eslint-disable-next-line @next/next/no-img-element */}
            {d?.fotoUrl ? <img src={d.fotoUrl} alt="Sua foto" width={64} height={64} className="size-16 rounded-full object-cover" /> : <div aria-hidden className="size-16 rounded-full bg-surface-2 grid place-items-center text-xl font-semibold">{d?.nome?.[0] ?? "?"}</div>}
            <label className="inline-flex items-center gap-2 text-sm cursor-pointer text-brand-text"><Camera className="size-4" aria-hidden />Trocar foto<input type="file" accept="image/jpeg,image/png,image/webp" className="sr-only" onChange={(e) => { const a = e.target.files?.[0]; if (a) foto.mutate(a); }} /></label>
            <div className="ml-auto text-right text-sm"><p className="mono">{d?.email}</p><p className="text-text-3">{d?.tipo === "EMPRESA" ? "Conta de empresa" : "Pessoa"} · <Etiqueta tom={d?.estado === "ACTIVE" ? "sucesso" : "aviso"}>{d?.estado}</Etiqueta></p></div>
          </div>
          <div className="grid gap-3 sm:grid-cols-2">
            <Entrada rotulo="Nome" value={f.nome} onChange={(e) => setF((v) => ({ ...v, nome: e.target.value }))} autoComplete="name" />
            {d?.tipo === "EMPRESA" && <Entrada rotulo="CNPJ" className="mono" value={f.cnpj} onChange={(e) => setF((v) => ({ ...v, cnpj: e.target.value }))} />}
            <Entrada rotulo="Registro profissional" className="mono" value={f.registroProfissional} onChange={(e) => setF((v) => ({ ...v, registroProfissional: e.target.value }))} />
            <Selecao rotulo="UF do registro" value={f.registroUf} onChange={(e) => setF((v) => ({ ...v, registroUf: e.target.value }))} vazio="—" opcoes={UFS} />
          </div>
          <Botao pequeno carregando={salvar.isPending} onClick={() => salvar.mutate(f)} className="self-start">Salvar perfil</Botao>
        </Cartao>
        <Cartao titulo="Preferências">
          <Selecao rotulo="Tema" value={p.tema ?? "SISTEMA"} onChange={(e) => setP((v) => ({ ...v, tema: e.target.value }))} opcoes={[{ valor: "SISTEMA", rotulo: "Seguir o sistema" }, { valor: "CLARO", rotulo: "Claro (alto contraste)" }, { valor: "ESCURO", rotulo: "Escuro" }]} />
          <label className="flex flex-col gap-1 text-sm"><span className="font-medium">Tamanho da fonte: {p.fontePct ?? 100}%</span><input type="range" min={85} max={150} step={5} value={p.fontePct ?? 100} onChange={(e) => setP((v) => ({ ...v, fontePct: Number(e.target.value) }))} className="accent-[var(--brand)]" /></label>
          <Selecao rotulo="Idioma" value={p.idioma ?? "pt-BR"} onChange={(e) => setP((v) => ({ ...v, idioma: e.target.value }))} opcoes={[{ valor: "pt-BR", rotulo: "Português (Brasil)" }]} />
          <Botao pequeno variante="secundario" carregando={salvarPref.isPending} onClick={() => salvarPref.mutate(p)}>Salvar preferências</Botao>
        </Cartao>
        <Cartao titulo="Selo profissional" className="lg:col-span-3" descricao="Sem selo verificado ninguém aprova orçamento, medição, checklist nem cálculo (RF21)." acoes={selo.data && <Etiqueta tom={SELO[selo.data.estado].tom} icone={selo.data.estado === "VERIFIED" ? <BadgeCheck className="size-3.5" aria-hidden /> : undefined}>{SELO[selo.data.estado].rotulo}</Etiqueta>}>
          {selo.data?.estado === "VERIFIED" ? <p className="text-sm">{selo.data.tipo} <span className="mono">{selo.data.registro}</span> {selo.data.uf} · verificado.</p> : <>
            {selo.data?.estado === "PENDING" && <Aviso tom="info">Seu pedido está na fila da equipe da plataforma.</Aviso>}
            {selo.data?.estado === "REJECTED" && <Aviso tom="perigo" titulo="Pedido recusado">{selo.data.motivo ? <>Motivo informado pelo suporte: <strong>{selo.data.motivo}</strong>. </> : null}Corrija e envie de novo.</Aviso>}
            <div className="grid gap-3 sm:grid-cols-4 items-end">
              <Selecao rotulo="Tipo" value={s.tipo} onChange={(e) => setS((v) => ({ ...v, tipo: e.target.value as TipoSelo }))} opcoes={[{ valor: "CREA", rotulo: "CREA" }, { valor: "CAU", rotulo: "CAU" }, { valor: "CNPJ", rotulo: "CNPJ (empresa)" }]} />
              <Entrada rotulo="Número" className="mono" value={s.registro} onChange={(e) => setS((v) => ({ ...v, registro: e.target.value }))} />
              <Selecao rotulo="UF" value={s.uf} onChange={(e) => setS((v) => ({ ...v, uf: e.target.value }))} vazio="—" opcoes={UFS} />
              <label className="flex flex-col gap-1 text-sm"><span className="font-medium">Documento (PDF/imagem)</span><input type="file" accept="application/pdf,image/*" onChange={(e) => setDoc(e.target.files?.[0] ?? null)} className="text-xs" /></label>
            </div>
            <Botao pequeno disabled={selo.data?.estado === "PENDING"} carregando={pedirSelo.isPending} onClick={() => pedirSelo.mutate(s)} className="self-start">Pedir verificação</Botao>
          </>}
        </Cartao>
      </div>
    </>
  );
}
