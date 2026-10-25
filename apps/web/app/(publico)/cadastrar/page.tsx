"use client";
import { useState, type FormEvent } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { ErroApi, get, mensagemDeErro, post } from "@/lib/api";
import type { TipoConta, TipoSelo } from "@/lib/tipos";
import { Aviso, Botao, Caixa, Cartao, Entrada, Selecao } from "@/components/ui";

const UFS = ["AC","AL","AP","AM","BA","CE","DF","ES","GO","MA","MT","MS","MG","PA","PB","PR","PE","PI","RJ","RN","RS","RO","RR","SC","SP","SE","TO"].map((u) => ({ valor: u, rotulo: u }));

/** RF18 · Cadastro de pessoa ou empresa com aceite versionado (caixas desmarcadas por padrão). */
export default function PaginaCadastrar() {
  const router = useRouter();
  const versoes = useQuery({ queryKey: ["legal-versoes"], queryFn: () => get<{ termos: string; politica: string }>("/auth/legal/versoes") });
  const [tipo, setTipo] = useState<TipoConta>("PESSOA");
  const [f, setF] = useState({ nome: "", email: "", senha: "", dataNascimento: "", cnpj: "", razaoSocial: "", seloTipo: "" as "" | TipoSelo, registroProfissional: "", registroUf: "", aceitaTermos: false, aceitaPolitica: false });
  const [erro, setErro] = useState<ErroApi | Error | null>(null);
  const [ocupado, setOcupado] = useState(false);
  const campo = (n: string) => (erro instanceof ErroApi ? erro.campo(n) : undefined);
  const set = (k: keyof typeof f) => (e: { target: { value: string } }) => setF((v) => ({ ...v, [k]: e.target.value }));

  async function enviar(e: FormEvent) {
    e.preventDefault(); setErro(null); setOcupado(true);
    try {
      await post("/auth/register", {
        tipo, nome: f.nome, email: f.email, senha: f.senha, dataNascimento: f.dataNascimento || null,
        cnpj: tipo === "EMPRESA" ? f.cnpj : null, razaoSocial: tipo === "EMPRESA" ? f.razaoSocial : null,
        seloTipo: f.seloTipo || null, registroProfissional: f.registroProfissional || null, registroUf: f.registroUf || null,
        aceitaTermos: f.aceitaTermos, aceitaPolitica: f.aceitaPolitica, versaoTermos: versoes.data?.termos, versaoPolitica: versoes.data?.politica,
      });
      router.push("/verificar-email");
    } catch (x) { setErro(x as Error); } finally { setOcupado(false); }
  }

  return (
    <Cartao titulo="Criar conta" descricao="Pessoa física ou empresa. Só para maiores de 18 anos.">
      {erro && !(erro instanceof ErroApi && erro.erros.length) && <Aviso tom="perigo">{mensagemDeErro(erro)}</Aviso>}
      <form onSubmit={enviar} className="flex flex-col gap-4" noValidate>
        <fieldset className="flex gap-2" aria-label="Tipo de conta">
          {(["PESSOA", "EMPRESA"] as TipoConta[]).map((t) => (
            <label key={t} className={`tap flex-1 flex items-center justify-center rounded-[var(--radius-sm)] border cursor-pointer text-sm font-medium ${tipo === t ? "border-brand bg-brand-soft text-brand-text" : "border-line text-text-2"}`}>
              <input type="radio" name="tipo" value={t} className="sr-only" checked={tipo === t} onChange={() => setTipo(t)} />{t === "PESSOA" ? "Pessoa" : "Empresa"}
            </label>
          ))}
        </fieldset>
        <Entrada rotulo={tipo === "EMPRESA" ? "Nome do responsável" : "Nome completo"} value={f.nome} onChange={set("nome")} erro={campo("nome")} obrigatorio autoComplete="name" />
        {tipo === "EMPRESA" && <>
          <Entrada rotulo="Razão social" value={f.razaoSocial} onChange={set("razaoSocial")} erro={campo("razaoSocial")} obrigatorio />
          <Entrada rotulo="CNPJ" inputMode="numeric" value={f.cnpj} onChange={set("cnpj")} erro={campo("cnpj")} obrigatorio className="mono" />
        </>}
        <Entrada rotulo="E-mail" type="email" value={f.email} onChange={set("email")} erro={campo("email")} obrigatorio autoComplete="email" />
        <Entrada rotulo="Senha" type="password" value={f.senha} onChange={set("senha")} erro={campo("senha")} obrigatorio autoComplete="new-password" ajuda="Mínimo 12 caracteres; não use seu e-mail nem senhas comuns." />
        <Entrada rotulo="Data de nascimento" type="date" value={f.dataNascimento} onChange={set("dataNascimento")} erro={campo("dataNascimento")} obrigatorio />
        <details className="rounded-[var(--radius-sm)] border border-line-soft p-3">
          <summary className="cursor-pointer text-sm font-medium">Sou profissional habilitado (CREA/CAU)</summary>
          <div className="grid gap-3 sm:grid-cols-3 mt-3">
            <Selecao rotulo="Registro" value={f.seloTipo} onChange={set("seloTipo")} vazio="Nenhum" opcoes={[{ valor: "CREA", rotulo: "CREA" }, { valor: "CAU", rotulo: "CAU" }]} />
            <Entrada rotulo="Número" value={f.registroProfissional} onChange={set("registroProfissional")} erro={campo("registroProfissional")} className="mono" />
            <Selecao rotulo="UF" value={f.registroUf} onChange={set("registroUf")} vazio="—" opcoes={UFS} erro={campo("registroUf")} />
          </div>
        </details>
        <div className="flex flex-col">
          <Caixa rotulo={<>Li e aceito os <Link href="/legal/termos" target="_blank" className="text-brand-text underline">Termos de uso</Link>{versoes.data && <span className="text-text-3"> (v{versoes.data.termos})</span>}</>} checked={f.aceitaTermos} onChange={(e) => setF((v) => ({ ...v, aceitaTermos: e.target.checked }))} />
          <Caixa rotulo={<>Li e aceito a <Link href="/legal/politica" target="_blank" className="text-brand-text underline">Política de privacidade</Link>{versoes.data && <span className="text-text-3"> (v{versoes.data.politica})</span>}</>} checked={f.aceitaPolitica} onChange={(e) => setF((v) => ({ ...v, aceitaPolitica: e.target.checked }))} />
          {(campo("aceitaTermos") || campo("aceitaPolitica")) && <p role="alert" className="text-xs text-danger">{campo("aceitaTermos") ?? campo("aceitaPolitica")}</p>}
        </div>
        <Botao type="submit" carregando={ocupado}>Criar conta</Botao>
        <p className="text-sm text-text-3">Já tem conta? <Link className="text-brand-text underline underline-offset-4" href="/entrar">Entrar</Link></p>
      </form>
    </Cartao>
  );
}
