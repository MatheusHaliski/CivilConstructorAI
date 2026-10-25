/**
 * Cliente HTTP do backend (`/api/v1`, proxied pelo Next). Regras: CSRF no padrão SPA (cookie XSRF-TOKEN →
 * cabeçalho X-XSRF-TOKEN em toda escrita), erros em Problem Details (RFC 9457) em pt-BR, sem token em
 * localStorage — a sessão é um cookie HttpOnly.
 */

export type ProblemDetail = {
  type?: string;
  title: string;
  status: number;
  detail?: string;
  instance?: string;
  codigo?: string;
  errors?: { campo?: string; field?: string; mensagem?: string; message?: string }[];
  retryAfter?: number;
  [k: string]: unknown;
};

export class ErroApi extends Error {
  constructor(public readonly status: number, public readonly problema: ProblemDetail) {
    super(problema.detail ?? problema.title ?? `Erro ${status}`);
  }
  get codigo() { return this.problema.codigo ?? (typeof this.problema.type === "string" ? this.problema.type.split("/").pop() : undefined); }
  get erros() {
    return (this.problema.errors ?? []).map((e) => ({ campo: e.campo ?? e.field ?? "", mensagem: e.mensagem ?? e.message ?? "" }));
  }
  /** mensagem de um campo específico (422) */
  campo(nome: string) { return this.erros.find((e) => e.campo === nome)?.mensagem; }
}

function lerCookie(nome: string): string | undefined {
  if (typeof document === "undefined") return undefined;
  const m = document.cookie.split("; ").find((c) => c.startsWith(nome + "="));
  return m ? decodeURIComponent(m.slice(nome.length + 1)) : undefined;
}

let csrfPronto: Promise<void> | null = null;
async function garantirCsrf() {
  if (lerCookie("XSRF-TOKEN")) return;
  csrfPronto ??= fetch("/api/v1/auth/csrf", { credentials: "same-origin" }).then(() => undefined).finally(() => { csrfPronto = null; });
  await csrfPronto;
}

export type Opcoes = { corpo?: unknown; query?: Record<string, string | number | boolean | undefined | null>; sinal?: AbortSignal; semJson?: boolean };

export async function api<T = unknown>(metodo: "GET" | "POST" | "PUT" | "PATCH" | "DELETE", caminho: string, opcoes: Opcoes = {}): Promise<T> {
  const url = new URL(caminho.startsWith("/api") ? caminho : `/api/v1${caminho}`, typeof window === "undefined" ? "http://localhost:3000" : window.location.origin);
  for (const [k, v] of Object.entries(opcoes.query ?? {})) if (v !== undefined && v !== null && v !== "") url.searchParams.set(k, String(v));
  const headers: Record<string, string> = { Accept: "application/json, application/problem+json" };
  if (metodo !== "GET") {
    await garantirCsrf();
    const token = lerCookie("XSRF-TOKEN");
    if (token) headers["X-XSRF-TOKEN"] = token;
    if (opcoes.corpo !== undefined) headers["Content-Type"] = "application/json";
  }
  const res = await fetch(url.toString().replace(url.origin, ""), {
    method: metodo,
    credentials: "same-origin",
    headers,
    body: opcoes.corpo !== undefined ? JSON.stringify(opcoes.corpo) : undefined,
    signal: opcoes.sinal,
  });
  if (res.status === 204 || opcoes.semJson) return undefined as T;
  const texto = await res.text();
  let dados: unknown = undefined;
  if (texto) { try { dados = JSON.parse(texto); } catch { dados = { title: texto }; } }
  if (!res.ok) {
    const problema = (dados && typeof dados === "object" ? dados : { title: res.statusText }) as ProblemDetail;
    problema.status ??= res.status;
    const ra = res.headers.get("Retry-After");
    if (ra) problema.retryAfter = Number(ra);
    const erro = new ErroApi(res.status, problema);
    // 2FA obrigatório ainda não configurado (RF19 CA-06): leva direto à configuração em vez de quebrar a tela
    if (erro.codigo === "mfa_obrigatorio" && typeof window !== "undefined" && !window.location.pathname.startsWith("/conta/seguranca")) {
      const aqui = window.location.pathname + window.location.search;
      window.location.assign(`/conta/seguranca?configurar-mfa=1&voltar=${encodeURIComponent(aqui)}`);
    }
    throw erro;
  }
  return dados as T;
}

export const get = <T,>(caminho: string, query?: Opcoes["query"]) => api<T>("GET", caminho, { query });
export const post = <T,>(caminho: string, corpo?: unknown, query?: Opcoes["query"]) => api<T>("POST", caminho, { corpo, query });
export const put = <T,>(caminho: string, corpo?: unknown) => api<T>("PUT", caminho, { corpo });
export const patch = <T,>(caminho: string, corpo?: unknown) => api<T>("PATCH", caminho, { corpo });
export const del = <T,>(caminho: string) => api<T>("DELETE", caminho);

/** Texto curto e humano para qualquer erro (rede, 4xx, 5xx). */
export function mensagemDeErro(e: unknown): string {
  if (e instanceof ErroApi) {
    if (e.status === 401) return "Sua sessão expirou. Entre novamente.";
    if (e.status === 403 && e.codigo === "sudo_required") return "Esta ação exige confirmar sua senha (modo seguro).";
    if (e.status === 429) return `Muitas tentativas. Tente de novo em ${e.problema.retryAfter ?? 60} s.`;
    if (e.erros.length) return e.erros.map((x) => (x.campo ? `${x.campo}: ${x.mensagem}` : x.mensagem)).join(" · ");
    return e.message;
  }
  if (e instanceof Error) return e.message === "Failed to fetch" ? "Sem conexão com o servidor." : e.message;
  return "Algo deu errado.";
}
