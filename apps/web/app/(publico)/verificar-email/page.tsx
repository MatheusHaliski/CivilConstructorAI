"use client";
import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { MailCheck } from "lucide-react";
import { mensagemDeErro, post } from "@/lib/api";
import { useSessao } from "@/lib/sessao";
import { Aviso, Botao, Cartao, Entrada } from "@/components/ui";

/** RF18 · Código de verificação (30 min, 5 tentativas, reenvio 1/min). */
export default function PaginaVerificarEmail() {
  const router = useRouter();
  const { recarregar } = useSessao();
  const [codigo, setCodigo] = useState("");
  const [erro, setErro] = useState<string | null>(null);
  const [info, setInfo] = useState<string | null>("Enviamos um código de 6 dígitos para o seu e-mail.");
  const [ocupado, setOcupado] = useState(false);

  async function enviar(e: FormEvent) {
    e.preventDefault(); setErro(null); setOcupado(true);
    try { await post("/me/verify-email", { codigo: codigo.trim() }); await recarregar(); router.replace("/obras"); }
    catch (x) { setErro(mensagemDeErro(x)); } finally { setOcupado(false); }
  }
  async function reenviar() {
    setErro(null);
    try { await post("/me/verify-email/resend"); setInfo("Novo código enviado. Você pode pedir outro em 1 minuto."); } catch (x) { setErro(mensagemDeErro(x)); }
  }
  return (
    <Cartao titulo="Confirme seu e-mail" descricao="Isso ativa a sua conta.">
      {info && <Aviso tom="info">{info}</Aviso>}
      {erro && <Aviso tom="perigo">{erro}</Aviso>}
      <form onSubmit={enviar} className="flex flex-col gap-4" noValidate>
        <Entrada rotulo="Código" inputMode="numeric" autoComplete="one-time-code" className="mono text-lg tracking-widest" value={codigo} onChange={(e) => setCodigo(e.target.value)} obrigatorio />
        <Botao type="submit" carregando={ocupado} icone={<MailCheck className="size-4" aria-hidden />}>Ativar conta</Botao>
        <Botao type="button" variante="link" onClick={() => void reenviar()}>Reenviar código</Botao>
      </form>
    </Cartao>
  );
}
