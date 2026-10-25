"use client";
import { Suspense, useState, type FormEvent } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { KeyRound, ShieldCheck } from "lucide-react";
import { ErroApi, get, mensagemDeErro, post } from "@/lib/api";
import { useSessao } from "@/lib/sessao";
import type { EmpresaResumo, EtapaLogin, RespostaLogin } from "@/lib/tipos";
import { Aviso, Botao, Cartao, Entrada } from "@/components/ui";

/** RF19 · Login em etapas: senha → 2FA (TOTP ou código de recuperação) → reativação (se PENDING_DELETION) → empresa ativa. */
export default function PaginaEntrar() {
  return <Suspense><Entrar /></Suspense>;
}

function Entrar() {
  const router = useRouter();
  const params = useSearchParams();
  const { recarregar } = useSessao();
  const [etapa, setEtapa] = useState<"SENHA" | EtapaLogin>("SENHA");
  const [empresas, setEmpresas] = useState<string[]>([]);
  const [erro, setErro] = useState<string | null>(null);
  const [ocupado, setOcupado] = useState(false);
  const [email, setEmail] = useState("");
  const [senha, setSenha] = useState("");
  const [codigo, setCodigo] = useState("");
  const voltar = params.get("voltar") ?? "/obras";

  async function seguir(r: RespostaLogin) {
    setEmpresas(r.empresas ?? []);
    if (r.proximaEtapa === "AUTENTICADO") { await recarregar(); router.replace(voltar); return; }
    if (r.proximaEtapa === "CONFIGURAR_MFA") { await recarregar(); router.replace("/conta/seguranca?configurar-mfa=1"); return; }
    setEtapa(r.proximaEtapa);
  }
  async function executar(acao: () => Promise<RespostaLogin>) {
    setErro(null); setOcupado(true);
    try { await seguir(await acao()); } catch (e) { setErro(mensagemDeErro(e)); } finally { setOcupado(false); }
  }
  const onSenha = (e: FormEvent) => { e.preventDefault(); void executar(() => post<RespostaLogin>("/auth/login", { email, senha })); };
  const onCodigo = (e: FormEvent) => { e.preventDefault(); void executar(() => post<RespostaLogin>("/auth/login/2fa", { codigo: codigo.replace(/\s/g, "") })); };

  return (
    <Cartao titulo={etapa === "SENHA" ? "Entrar" : etapa === "MFA" ? "Segundo fator" : etapa === "OFERTA_REATIVACAO" ? "Sua conta está marcada para exclusão" : "Escolha a empresa"}
      descricao={etapa === "SENHA" ? "Use o e-mail e a senha da sua conta." : etapa === "MFA" ? "Digite o código do seu aplicativo autenticador ou um código de recuperação." : etapa === "OFERTA_REATIVACAO" ? "Você pode reativá-la agora ou manter a exclusão." : "Você participa de mais de uma empresa."}>
      {erro && <Aviso tom="perigo">{erro}</Aviso>}
      {etapa === "SENHA" && (
        <form onSubmit={onSenha} className="flex flex-col gap-4" noValidate>
          <Entrada rotulo="E-mail" type="email" autoComplete="username" value={email} onChange={(e) => setEmail(e.target.value)} obrigatorio />
          <Entrada rotulo="Senha" type="password" autoComplete="current-password" value={senha} onChange={(e) => setSenha(e.target.value)} obrigatorio />
          <Botao type="submit" carregando={ocupado} icone={<KeyRound className="size-4" aria-hidden />}>Entrar</Botao>
          <p className="text-sm text-text-3">Não tem conta? <Link className="text-brand-text underline underline-offset-4" href="/cadastrar">Cadastre-se</Link></p>
        </form>
      )}
      {etapa === "MFA" && (
        <form onSubmit={onCodigo} className="flex flex-col gap-4" noValidate>
          <Entrada rotulo="Código" inputMode="numeric" autoComplete="one-time-code" className="mono text-lg tracking-widest" value={codigo} onChange={(e) => setCodigo(e.target.value)} ajuda="6 dígitos do autenticador ou um código de recuperação de uso único." obrigatorio />
          <Botao type="submit" carregando={ocupado} icone={<ShieldCheck className="size-4" aria-hidden />}>Confirmar</Botao>
        </form>
      )}
      {etapa === "OFERTA_REATIVACAO" && (
        <div className="flex flex-col gap-3">
          <Aviso tom="aviso">Durante a carência de 30 dias você pode reativar a conta. Depois disso, a eliminação é definitiva.</Aviso>
          <div className="flex gap-2">
            <Botao carregando={ocupado} onClick={() => void executar(() => post<RespostaLogin>("/auth/reactivate", { reativar: true }))}>Reativar minha conta</Botao>
            <Botao variante="secundario" carregando={ocupado} onClick={() => void executar(() => post<RespostaLogin>("/auth/reactivate", { reativar: false }))}>Manter a exclusão</Botao>
          </div>
        </div>
      )}
      {etapa === "ESCOLHER_EMPRESA" && <EscolhaEmpresa ids={empresas} ocupado={ocupado} escolher={(id) => void executar(() => post<RespostaLogin>("/auth/login/empresa", { empresaId: id }))} />}
    </Cartao>
  );
}

function EscolhaEmpresa({ ids, escolher, ocupado }: { ids: string[]; escolher: (id: string) => void; ocupado: boolean }) {
  const q = useQuery({ queryKey: ["empresas-login", ids], queryFn: async () => Promise.all(ids.map((id) => get<EmpresaResumo>(`/companies/${id}`).catch((e: unknown) => ({ id, razaoSocial: e instanceof ErroApi ? "Empresa" : "Empresa", cnpj: "" } as EmpresaResumo)))) });
  return (
    <ul className="flex flex-col gap-2">
      {(q.data ?? ids.map((id) => ({ id, razaoSocial: "Carregando…", cnpj: "" } as EmpresaResumo))).map((e) => (
        <li key={e.id}>
          <button type="button" disabled={ocupado} onClick={() => escolher(e.id)} className="tap w-full text-left rounded-[var(--radius-sm)] border border-line px-4 hover:bg-surface">
            <span className="font-medium text-text block">{e.nomeFantasia || e.razaoSocial}</span>
            <span className="text-xs text-text-3">{e.cnpj}</span>
          </button>
        </li>
      ))}
    </ul>
  );
}
