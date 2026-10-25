"use client";
import { Suspense, useState } from "react";
import { useSearchParams } from "next/navigation";
import { KeyRound, LogOut, MonitorSmartphone, ShieldCheck } from "lucide-react";
import { del, post } from "@/lib/api";
import { useAcao, useDados } from "@/lib/dados";
import { useSessao } from "@/lib/sessao";
import type { SessaoResumo } from "@/lib/tipos";
import { data } from "@/lib/formatos";
import { Aviso, Botao, CabecalhoPagina, Cartao, Carregando, Entrada, Etiqueta, Tabela } from "@/components/ui";
import { useSudoPrompt } from "@/components/conta/NavConta";

type Perfil = { mfaAtivo: boolean; mfaObrigatorio: boolean };

export default function PaginaSeguranca() { return <Suspense><Seguranca /></Suspense>; }

/** RF23 · Senha (sudo, derruba outras sessões), sessões ativas, 2FA TOTP com 10 códigos de recuperação, modo sudo. */
function Seguranca() {
  const params = useSearchParams();
  const { sessao, recarregar } = useSessao();
  const perfil = useDados<Perfil>(["perfil"], "/me/profile");
  const sessoes = useDados<SessaoResumo[]>(["sessoes"], "/me/sessions");
  const comSudo = useSudoPrompt();
  const sudo = (senha: string, codigo: string) => post("/me/sudo", { senha, codigo: codigo || null });
  const [senha, setSenha] = useState({ senhaAtual: "", novaSenha: "" });
  const [setup, setSetup] = useState<{ segredo: string; uriOtpauth: string } | null>(null);
  const [codigo, setCodigo] = useState("");
  const [codigos, setCodigos] = useState<string[] | null>(null);
  const trocar = useAcao((v: typeof senha) => comSudo(() => post("/me/password", v), sudo), { invalidar: [["sessoes"]], sucesso: "Senha trocada; as outras sessões foram encerradas." });
  const encerrar = useAcao((apelido: string) => del(`/me/sessions/${apelido}`), { invalidar: [["sessoes"]], sucesso: "Sessão encerrada." });
  const encerrarOutras = useAcao(() => del("/me/sessions"), { invalidar: [["sessoes"]], sucesso: "Outras sessões encerradas." });
  const iniciarTotp = useAcao(() => post<{ segredo: string; uriOtpauth: string }>("/me/mfa/totp/setup"), {});
  const confirmarTotp = useAcao((c: string) => post<{ codigosRecuperacao: string[] }>("/me/mfa/totp/confirm", { codigo: c }), { invalidar: [["perfil"]], sucesso: "2FA ativado." });
  const regenerar = useAcao(() => comSudo(() => post<{ codigosRecuperacao: string[] }>("/me/mfa/recovery-codes"), sudo) as Promise<{ codigosRecuperacao: string[] }>, {});
  const desativar = useAcao(() => comSudo(() => del("/me/mfa"), sudo), { invalidar: [["perfil"]], sucesso: "2FA desativado." });
  const pedirCodigoSudo = useAcao(() => post("/me/sudo/codigo"), { sucesso: "Código enviado ao seu e-mail." });
  const p = perfil.data;
  return (
    <>
      <CabecalhoPagina titulo="Segurança" descricao="Sessão opaca no servidor (cookie HttpOnly), 2FA por aplicativo autenticador e modo seguro para ações sensíveis." />
      {params.get("configurar-mfa") && !p?.mfaAtivo && <Aviso tom="aviso" titulo="Configure o segundo fator" className="mb-4">Seu papel exige 2FA (dono/admin ou quem aprova orçamento e medição). Ative abaixo para liberar as aprovações.</Aviso>}
      <div className="grid gap-4 lg:grid-cols-2">
        <Cartao titulo="Verificação em duas etapas (TOTP)" acoes={p && <Etiqueta tom={p.mfaAtivo ? "sucesso" : p.mfaObrigatorio ? "aviso" : "neutro"} icone={<ShieldCheck className="size-3.5" aria-hidden />}>{p.mfaAtivo ? "ativa" : p.mfaObrigatorio ? "obrigatória · inativa" : "inativa"}</Etiqueta>}>
          {!p ? <Carregando /> : p.mfaAtivo ? <>
            <p className="text-sm text-text-2">Sessão atual {sessao?.mfaVerificado ? "verificada com 2FA" : "sem 2FA nesta entrada"}.</p>
            <div className="flex gap-2 flex-wrap"><Botao pequeno variante="secundario" carregando={regenerar.isPending} onClick={() => regenerar.mutate(undefined, { onSuccess: (r) => setCodigos(r.codigosRecuperacao) })}>Gerar novos códigos de recuperação</Botao>{!p.mfaObrigatorio && <Botao pequeno variante="perigo" onClick={() => desativar.mutate()}>Desativar 2FA</Botao>}</div>
          </> : !setup ? <Botao pequeno icone={<ShieldCheck className="size-4" aria-hidden />} carregando={iniciarTotp.isPending} onClick={() => iniciarTotp.mutate(undefined, { onSuccess: (r) => setSetup(r) })}>Ativar 2FA</Botao> : <>
            <ol className="text-sm list-decimal pl-5 flex flex-col gap-1"><li>Abra o aplicativo autenticador e adicione uma conta.</li><li>Use a chave abaixo (ou o link otpauth em um leitor de QR).</li><li>Digite o código de 6 dígitos.</li></ol>
            <p className="mono break-all rounded bg-surface p-2 text-sm select-all">{setup.segredo}</p>
            <a href={setup.uriOtpauth} className="text-xs text-brand-text underline break-all">{setup.uriOtpauth}</a>
            <Entrada rotulo="Código" inputMode="numeric" autoComplete="one-time-code" className="mono tracking-widest" value={codigo} onChange={(e) => setCodigo(e.target.value)} />
            <Botao pequeno disabled={codigo.length < 6} carregando={confirmarTotp.isPending} onClick={() => confirmarTotp.mutate(codigo.trim(), { onSuccess: (r) => { setCodigos(r.codigosRecuperacao); setSetup(null); void recarregar(); } })}>Confirmar</Botao>
          </>}
          {codigos && <Aviso tom="aviso" titulo="Guarde estes códigos de recuperação"><p>Cada um vale uma vez e não serão mostrados de novo.</p><ul className="mono grid grid-cols-2 gap-x-4 mt-2">{codigos.map((c) => <li key={c}>{c}</li>)}</ul></Aviso>}
        </Cartao>
        <Cartao titulo="Trocar senha" descricao="Exige o modo seguro e encerra as outras sessões.">
          <Entrada rotulo="Senha atual" type="password" autoComplete="current-password" value={senha.senhaAtual} onChange={(e) => setSenha((v) => ({ ...v, senhaAtual: e.target.value }))} />
          <Entrada rotulo="Nova senha" type="password" autoComplete="new-password" value={senha.novaSenha} onChange={(e) => setSenha((v) => ({ ...v, novaSenha: e.target.value }))} ajuda="Mínimo 12 caracteres." />
          <div className="flex gap-2 flex-wrap"><Botao pequeno icone={<KeyRound className="size-4" aria-hidden />} disabled={!senha.senhaAtual || senha.novaSenha.length < 12} carregando={trocar.isPending} onClick={() => trocar.mutate(senha, { onSuccess: () => setSenha({ senhaAtual: "", novaSenha: "" }) })}>Trocar senha</Botao>{!p?.mfaAtivo && <Botao pequeno variante="fantasma" onClick={() => pedirCodigoSudo.mutate()}>Receber código por e-mail</Botao>}</div>
          <p className="text-xs text-text-3">Modo seguro {sessao?.sudoAtivo ? <Etiqueta tom="sucesso">ativo</Etiqueta> : "inativo"}: vale 10 minutos nesta sessão.</p>
        </Cartao>
        <Cartao titulo="Sessões ativas" className="lg:col-span-2" acoes={<Botao pequeno variante="secundario" icone={<LogOut className="size-4" aria-hidden />} onClick={() => encerrarOutras.mutate()}>Encerrar as outras</Botao>}>
          {sessoes.isLoading ? <Carregando /> : <Tabela linhas={sessoes.data ?? []} chave={(s) => s.apelido} colunas={[
            { titulo: "Dispositivo", celula: (s) => <span className="flex items-center gap-2"><MonitorSmartphone className="size-4 text-text-3" aria-hidden /><span className="truncate max-w-xs" title={s.userAgent}>{s.userAgent || "Desconhecido"}</span>{s.atual && <Etiqueta tom="marca">esta</Etiqueta>}</span> },
            { titulo: "Criada", celula: (s) => data(s.criadaEm) }, { titulo: "Último acesso", celula: (s) => data(s.ultimoAcessoEm) },
            { titulo: "", celula: (s) => s.atual ? null : <Botao pequeno variante="fantasma" onClick={() => encerrar.mutate(s.apelido)}>Encerrar</Botao> },
          ]} />}
        </Cartao>
      </div>
    </>
  );
}
