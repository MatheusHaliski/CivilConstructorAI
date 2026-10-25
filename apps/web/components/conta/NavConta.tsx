"use client";
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
