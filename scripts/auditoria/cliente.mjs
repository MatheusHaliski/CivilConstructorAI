// Harness da auditoria: cliente HTTP real (cookies + CSRF SPA), leitura de e-mails do receptor SMTP local,
// CNPJ válido, TOTP (RFC 6238) e registro de evidências. Uso: ver scripts/auditoria/README.md.
import { request } from "playwright";
import fs from "node:fs";
import crypto from "node:crypto";

export const BASE = process.env.CCA_WEB ?? "http://localhost:3000";
export const DIR_EMAILS = process.env.CCA_EMAILS ?? "/tmp/cca-emails";
export const SENHA = "concreto-armado-fck30-2026";
export const espera = (ms) => new Promise((r) => setTimeout(r, ms));

export function cnpj() {
  const n = Array.from({ length: 8 }, () => Math.floor(Math.random() * 10)).concat([0, 0, 0, 1]);
  const dv = (b, pesos) => { const s = b.reduce((a, x, i) => a + x * pesos[i], 0) % 11; return s < 2 ? 0 : 11 - s; };
  const d1 = dv(n, [5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2]); const d2 = dv([...n, d1], [6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2]);
  return [...n, d1, d2].join("");
}

export function totp(segredoBase32, deslocamento = 0, t = Date.now()) {
  const alfa = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"; let bits = "";
  for (const c of segredoBase32.replace(/=+$/, "").toUpperCase()) bits += alfa.indexOf(c).toString(2).padStart(5, "0");
  const chave = Buffer.from(bits.match(/.{8}/g).map((b) => parseInt(b, 2)));
  const passo = Math.floor(t / 1000 / 30) + deslocamento; const buf = Buffer.alloc(8); buf.writeBigUInt64BE(BigInt(passo));
  const h = crypto.createHmac("sha1", chave).update(buf).digest(); const o = h[h.length - 1] & 15;
  return String(((h.readUInt32BE(o) & 0x7fffffff) % 1_000_000)).padStart(6, "0");
}

export async function codigoEmail(email, { depoisDe = 0 } = {}) {
  for (let i = 0; i < 60; i++) {
    const arqs = fs.existsSync(DIR_EMAILS) ? fs.readdirSync(DIR_EMAILS).sort().reverse() : [];
    for (const a of arqs) {
      const p = `${DIR_EMAILS}/${a}`; if (fs.statSync(p).mtimeMs < depoisDe) continue;
      const t = fs.readFileSync(p, "utf8").replace(/=\r?\n/g, "");
      if (!t.includes(email)) continue;
      const c = /\b(\d{6})\b/.exec(t.split(/\r?\n\r?\n/).slice(1).join("\n")); if (c) return c[1];
    }
    await espera(500);
  }
  throw new Error(`nenhum código para ${email}`);
}
export function emailsPara(email) {
  if (!fs.existsSync(DIR_EMAILS)) return [];
  return fs.readdirSync(DIR_EMAILS).sort().map((a) => fs.readFileSync(`${DIR_EMAILS}/${a}`, "utf8").replace(/=\r?\n/g, "")).filter((t) => t.includes(email));
}

export async function novoCliente() {
  const ctx = await request.newContext({ baseURL: BASE });
  await ctx.get("/api/v1/auth/csrf");
  const xsrf = async () => (await ctx.storageState()).cookies.find((c) => c.name === "XSRF-TOKEN")?.value;
  const call = async (metodo, url, data, { semCsrf = false, query } = {}) => {
    const t0 = performance.now();
    const r = await ctx.fetch("/api/v1" + url, { method: metodo, data, params: query, headers: semCsrf ? {} : { "X-XSRF-TOKEN": (await xsrf()) ?? "" } });
    const txt = await r.text(); let j; try { j = JSON.parse(txt); } catch { j = txt; }
    return { s: r.status(), j, h: r.headers(), ms: Math.round(performance.now() - t0) };
  };
  return { ctx, call, cookies: async () => (await ctx.storageState()).cookies, fechar: () => ctx.dispose() };
}

/** Cadastra e verifica uma conta; PESSOA por padrão, EMPRESA cria também a empresa. */
export async function contaVerificada({ tipo = "PESSOA", nome = "Pessoa Auditoria", prefixo = "aud" } = {}) {
  const c = await novoCliente();
  const email = `${prefixo}-${Date.now()}-${Math.floor(Math.random() * 1e4)}@exemplo.cca`;
  const v = (await c.call("GET", "/auth/legal/versoes")).j;
  const corpo = { tipo, nome, email, senha: SENHA, dataNascimento: "1985-03-10", aceitaTermos: true, aceitaPolitica: true, versaoTermos: v.termos, versaoPolitica: v.politica,
    ...(tipo === "EMPRESA" ? { cnpj: cnpj(), razaoSocial: `${nome} Engenharia Ltda` } : {}) };
  const reg = await c.call("POST", "/auth/register", corpo);
  if (reg.s !== 201) throw new Error(`register ${reg.s} ${JSON.stringify(reg.j)}`);
  const ver = await c.call("POST", "/me/verify-email", { codigo: await codigoEmail(email) });
  if (ver.s !== 204 && ver.s !== 200) throw new Error(`verify ${ver.s}`);
  return { ...c, email };
}

/** Configura TOTP na sessão atual e devolve o segredo. */
export async function ativarMfa(c) {
  const s = await c.call("POST", "/me/mfa/totp/setup");
  // confirma com o passo anterior (aceito na janela ±1) para deixar o passo atual livre para o próximo login
  const conf = await c.call("POST", "/me/mfa/totp/confirm", { codigo: totp(s.j.segredo, -1) });
  return { segredo: s.j.segredo, recuperacao: conf.j.codigosRecuperacao, status: conf.s };
}

/** Login completo (senha → 2FA → empresa) num cliente novo. */
export async function entrar(email, { segredo, empresaId } = {}) {
  const c = await novoCliente();
  let r = await c.call("POST", "/auth/login", { email, senha: SENHA });
  const etapas = [r.j?.proximaEtapa];
  let codigoUsado;
  if (r.j?.proximaEtapa === "MFA") {
    // o servidor recusa replay: tenta o passo atual e, se já usado, o próximo (aceito na janela ±1)
    for (const d of [0, 1, -1]) { codigoUsado = totp(segredo, d); r = await c.call("POST", "/auth/login/2fa", { codigo: codigoUsado }); if (r.s === 200) break; }
    etapas.push(r.j?.proximaEtapa);
  }
  if (r.j?.proximaEtapa === "ESCOLHER_EMPRESA") { r = await c.call("POST", "/auth/login/empresa", { empresaId: empresaId ?? r.j.empresas[0] }); etapas.push(r.j?.proximaEtapa); }
  return { ...c, email, etapas, ultimo: r, codigoUsado };
}

const resultados = [];
/** Registra uma verificação: ok=true/false/null (null = não verificado/bloqueado). */
export function registrar(rf, id, descricao, ok, evidencia) {
  resultados.push({ rf, id, descricao, ok, evidencia });
  const marca = ok === true ? "PASSA" : ok === false ? "FALHA" : "N/VERIF";
  console.log(`[${marca}] ${rf} ${id} — ${descricao}${evidencia ? ` :: ${typeof evidencia === "string" ? evidencia : JSON.stringify(evidencia)}`.slice(0, 400) : ""}`);
}
export function salvar(arquivo) { fs.mkdirSync(new URL(".", `file://${arquivo}`).pathname, { recursive: true }); fs.writeFileSync(arquivo, JSON.stringify(resultados, null, 2)); }
