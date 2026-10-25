"use client";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { cx } from "@/components/ui";

const ITENS = [{ href: "/conta", rotulo: "Perfil e selo" }, { href: "/conta/seguranca", rotulo: "Segurança e sessões" }, { href: "/conta/privacidade", rotulo: "Privacidade e seus dados" }];

export function NavConta() {
  const c = usePathname();
  return (
    <nav aria-label="Seções da conta" className="flex gap-1 overflow-x-auto border-b border-line-soft mb-4">
      {ITENS.map((i) => <Link key={i.href} href={i.href} aria-current={c === i.href ? "page" : undefined} className={cx("tap inline-flex items-center px-3 text-sm whitespace-nowrap border-b-2 -mb-px", c === i.href ? "border-brand text-brand-text font-medium" : "border-transparent text-text-2 hover:text-text")}>{i.rotulo}</Link>)}
    </nav>
  );
}

/** Modo sudo (RF23): senha + código; vale 10 min na sessão. */
export function useSudoPrompt() {
  return async (acao: () => Promise<unknown>, pedir: (senha: string, codigo: string) => Promise<unknown>) => {
    try { return await acao(); }
    catch (e) {
      const { ErroApi } = await import("@/lib/api");
      if (!(e instanceof ErroApi) || e.codigo !== "sudo_required") throw e;
      const senha = window.prompt("Confirme sua senha para continuar (modo seguro por 10 min):");
      if (!senha) throw e;
      const codigo = window.prompt("Código do autenticador ou enviado por e-mail (deixe vazio se não tiver 2FA):") ?? "";
      await pedir(senha, codigo);
      return acao();
    }
  };
}
