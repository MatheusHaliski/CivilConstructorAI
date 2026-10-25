"use client";
import { Suspense, useState, type FormEvent } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { MailCheck } from "lucide-react";
import { mensagemDeErro, post } from "@/lib/api";
import { useDados } from "@/lib/dados";
import { destinoSeguro } from "@/lib/formatos";
import type { Perfil } from "@/lib/tipos";
import { useSessao } from "@/lib/sessao";
import { Carregando, Aviso, Botao, Cartao, Entrada } from "@/components/ui";

/** RF18 · Código de verificação (30 min, 5 tentativas, reenvio 1/min). */
function PaginaVerificarEmailConteudo() {
  const router = useRouter();
  const { recarregar, sair } = useSessao();
  const voltar = destinoSeguro(useSearchParams().get("voltar"));
  const perfil = useDados<Perfil>(["perfil"], "/me/profile");
  const [codigo, setCodigo] = useState("");
  const [erro, setErro] = useState<string | null>(null);
  const [info, setInfo] = useState<string | null>("Enviamos um código de 6 dígitos para o seu e-mail.");
  const [ocupado, setOcupado] = useState(false);

  async function enviar(e: FormEvent) {
    e.preventDefault(); setErro(null); setOcupado(true);
    try { await post("/me/verify-email", { codigo: codigo.trim() }); await recarregar(); router.replace(voltar); }
    catch (x) { setErro(mensagemDeErro(x)); } finally { setOcupado(false); }
  }
  async function reenviar() {
    setErro(null);
    try { await post("/me/verify-email/resend"); setInfo("Novo código enviado. Você pode pedir outro em 1 minuto."); } catch (x) { setErro(mensagemDeErro(x)); }
  }
  return (
    <Cartao titulo="Confirme seu e-mail" descricao="Isso ativa a sua conta.">
      {info && <Aviso tom="info">{info}{perfil.data?.email && <> Destino: <strong className="break-all">{perfil.data.email}</strong>.</>}</Aviso>}
      {erro && <Aviso tom="perigo">{erro}</Aviso>}
      <form onSubmit={enviar} className="flex flex-col gap-4" noValidate>
        <Entrada rotulo="Código" inputMode="numeric" autoComplete="one-time-code" className="mono text-lg tracking-widest" value={codigo} onChange={(e) => setCodigo(e.target.value)} obrigatorio />
        <Botao type="submit" carregando={ocupado} icone={<MailCheck className="size-4" aria-hidden />}>Ativar conta</Botao>
        <Botao type="button" variante="link" onClick={() => void reenviar()}>Reenviar código</Botao>
      </form>
      <p className="text-sm text-text-3">E-mail errado? <button type="button" className="tap text-brand-text underline underline-offset-4" onClick={() => void sair().then(() => router.replace("/cadastrar"))}>Cadastrar com outro e-mail</button>. O código atual vale 30 minutos.</p>
    </Cartao>
  );
}

export default function PaginaVerificarEmail() {
  return <Suspense fallback={<Carregando />}><PaginaVerificarEmailConteudo /></Suspense>;
}
