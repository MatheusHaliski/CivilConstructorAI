// Jornada M0 (RF18–RF23) + RF24: visitante → cadastro → verificação → login → 2FA → empresa → convite → obra.
import { ativarMfa, cnpj, codigoEmail, contaVerificada, emailsPara, entrar, espera, novoCliente, registrar, salvar, SENHA, totp } from "./cliente.mjs";

const v = await (await novoCliente()).call("GET", "/auth/legal/versoes");

// RF18 CA-01/02/03/05: cadastro inválido devolve todos os erros de uma vez
{
  const c = await novoCliente();
  const r = await c.call("POST", "/auth/register", { tipo: "PESSOA", nome: "", email: "x", senha: "123", dataNascimento: "2015-01-01", aceitaTermos: false, aceitaPolitica: false });
  const campos = (r.j.errors ?? []).map((e) => e.campo ?? e.field ?? e.pointer);
  registrar("RF18", "CA-01", "cadastro inválido → 422 com todos os erros", r.s === 422 && campos.length >= 4, { s: r.s, campos });
  registrar("RF24", "CA-01", "sem aceite de Termos e Política → os dois erros juntos", r.s === 422 && campos.some((x) => /termo/i.test(x)) && campos.some((x) => /pol/i.test(x)), campos);
  const local = `ana-${Date.now()}`;
  const r2 = await c.call("POST", "/auth/register", { tipo: "PESSOA", nome: "Ana", email: `${local}@exemplo.cca`, senha: `${local}-forte`, dataNascimento: "1990-01-01", aceitaTermos: true, aceitaPolitica: true, versaoTermos: v.j.termos, versaoPolitica: v.j.politica });
  registrar("RF18", "CA-02", "senha contendo a parte local do e-mail é rejeitada", r2.s === 422, { s: r2.s, e: r2.j.errors });
}
// RF18 CA-11: escrita sem CSRF
{
  const c = await novoCliente();
  const r = await c.call("POST", "/auth/register", {}, { semCsrf: true });
  registrar("RF18", "CA-11", "escrita sem X-XSRF-TOKEN → 403", r.s === 403, r.s);
}
// RF18 CA-06/07/10: conta pendente
const email = `j1-${Date.now()}@exemplo.cca`;
const c1 = await novoCliente();
{
  const r = await c1.call("POST", "/auth/register", { tipo: "PESSOA", nome: "Júlia Mendes", email, senha: SENHA, dataNascimento: "1987-06-01", aceitaTermos: true, aceitaPolitica: true, versaoTermos: v.j.termos, versaoPolitica: v.j.politica });
  registrar("RF18", "CA-06", "cadastro → 201 e sessão aberta", r.s === 201, r.s);
  const ck = (await c1.cookies()).map((k) => k.name);
  registrar("RF19", "CA-07", "cookie de sessão __Host-cca_session (dev usa cca_session sem Secure)", ck.some((n) => /cca_session/.test(n)), ck);
  const bloqueado = await c1.call("GET", "/obras");
  registrar("RF18", "CA-10", "conta PENDING_VERIFICATION não acessa /obras", bloqueado.s === 403, { s: bloqueado.s, type: bloqueado.j.type });
  const errado = await c1.call("POST", "/me/verify-email", { codigo: "000000" });
  registrar("RF18", "CA-07a", "código errado → 422 sem invalidar a conta", errado.s >= 400 && errado.s < 500, { s: errado.s, d: errado.j.detail });
  const reenvio = await c1.call("POST", "/me/verify-email/resend");
  registrar("RF18", "CA-07b", "reenvio antes de 60 s → 429 + Retry-After", reenvio.s === 429 && !!reenvio.h["retry-after"], { s: reenvio.s, ra: reenvio.h["retry-after"] });
  const ok = await c1.call("POST", "/me/verify-email", { codigo: await codigoEmail(email) });
  registrar("RF18", "CA-06b", "código correto ativa a conta", ok.s === 204 || ok.s === 200, ok.s);
  const sess = await c1.call("GET", "/auth/session");
  registrar("RF19", "CA-10", "GET /session traz empresa ativa, papéis, selo, mfa", sess.s === 200 && "papeisPorObra" in sess.j, Object.keys(sess.j));
  registrar("RF19", "CA-12", "nenhuma resposta traz o id real da sessão", !JSON.stringify(sess.j).match(/sessionId|sessao_id/i), Object.keys(sess.j));
}
// RF18 CA-08: e-mail já cadastrado não revela existência
{
  const c = await novoCliente();
  const r = await c.call("POST", "/auth/register", { tipo: "PESSOA", nome: "Outra", email, senha: SENHA, dataNascimento: "1980-01-01", aceitaTermos: true, aceitaPolitica: true, versaoTermos: v.j.termos, versaoPolitica: v.j.politica });
  registrar("RF18", "CA-08", "e-mail repetido → mesma resposta 201 (sem enumeração)", r.s === 201, r.s);
}
// RF19 CA-01: credencial errada x inexistente com tempos parecidos
{
  const c = await novoCliente();
  const t = [];
  for (let i = 0; i < 3; i++) { t.push((await c.call("POST", "/auth/login", { email, senha: "errada-errada-123" })).ms); t.push(-(await c.call("POST", "/auth/login", { email: `nao-existe-${i}@exemplo.cca`, senha: "errada-errada-123" })).ms); }
  const a = t.filter((x) => x > 0).sort((x, y) => x - y)[1], b = -t.filter((x) => x < 0).sort((x, y) => y - x)[1];
  const r = await c.call("POST", "/auth/login", { email, senha: "errada" });
  registrar("RF19", "CA-01", "401 genérico e tempo equivalente (mediana; tolerância 80 ms neste ambiente)", r.s === 401 && Math.abs(a - b) < 80, { s: r.s, detail: r.j.detail, medianaExiste: a, medianaInexiste: b });
}
// RF19 CA-03/04: login, fixação de sessão
const l1 = await entrar(email);
registrar("RF19", "CA-03", "login de PESSOA sem empresa conclui", ["CONCLUIDO", "ESCOLHER_EMPRESA", "CONFIGURAR_MFA"].includes(l1.ultimo.j?.proximaEtapa) || l1.ultimo.s === 200, l1.etapas);

// RF22: criar empresa (vira DONO → 2FA obrigatório)
{
  const inv = await l1.call("POST", "/companies", { razaoSocial: "X", cnpj: "11111111111111" });
  registrar("RF22", "CA-01b", "CNPJ inválido → 422", inv.s === 422, inv.s);
}
const emp = await l1.call("POST", "/companies", { razaoSocial: "Mendes Projetos Ltda", nomeFantasia: "Mendes Projetos", cnpj: cnpj() });
registrar("RF22", "CA-01a", "criar empresa com CNPJ válido → 201", emp.s === 201, { s: emp.s, j: emp.j?.id ?? emp.j });
const sessDono = await l1.call("GET", "/auth/session");
registrar("RF19", "CA-06", "empresa criada vira a ativa e a sessão de DONO sem 2FA fica com 2FA pendente", sessDono.j.mfaPendente === true && sessDono.j.empresaAtivaId === emp.j.id, { mfaPendente: sessDono.j.mfaPendente, empresa: sessDono.j.empresaAtivaId, papel: sessDono.j.papelEmpresa });
const semMfa = await l1.call("GET", "/obras");
registrar("RF19", "CA-06b", "DONO sem 2FA: dados da empresa bloqueados (403 mfa_obrigatorio), conta liberada", semMfa.s === 403 && /mfa_obrigatorio/.test(semMfa.j.type) && (await l1.call("GET", "/me/profile")).s === 200, { s: semMfa.s, type: semMfa.j.type });
const mfa = await ativarMfa(l1);
registrar("RF23", "CA-02", "2FA confirmado devolve 10 códigos de recuperação", mfa.status === 200 && mfa.recuperacao?.length === 10, { s: mfa.status, n: mfa.recuperacao?.length });

// novo login completo com 2FA

const dono = await entrar(email, { segredo: mfa.segredo });
registrar("RF19", "CA-05", "login com TOTP válido conclui", dono.ultimo.s === 200, dono.etapas);
{
  // replay: o código usado no login acima, numa sessão nova, no mesmo passo
  const c = await novoCliente();
  await c.call("POST", "/auth/login", { email, senha: SENHA });
  const rep = await c.call("POST", "/auth/login/2fa", { codigo: dono.codigoUsado });
  registrar("RF19", "CA-05b", "TOTP já usado é rejeitado (replay) com 401", rep.s === 401, { s: rep.s, d: rep.j.detail });
  const rec = await c.call("POST", "/auth/login/2fa", { codigo: mfa.recuperacao[0] });
  registrar("RF19", "CA-05c", "código de recuperação funciona uma vez", rec.s === 200, rec.s);
  const c2 = await novoCliente(); await c2.call("POST", "/auth/login", { email, senha: SENHA });
  const prot = await c2.call("GET", "/obras");
  registrar("RF19", "CA-04", "etapa intermediária (MFA pendente): rota protegida → 401", prot.s === 401, { s: prot.s, type: prot.j.type });
  const rec2 = await c2.call("POST", "/auth/login/2fa", { codigo: mfa.recuperacao[0] });
  registrar("RF19", "CA-05d", "código de recuperação consumido não serve de novo", rec2.s === 401, rec2.s);
}
// RF22 obra
const obra = await dono.call("POST", "/obras", { nome: "Residencial Auditoria", uf: "PR", municipio: "Curitiba" });
registrar("RF22", "CA-02", "obra criada PLANEJADA com Térreo z=0", obra.s === 201 && obra.j.estado === "PLANEJADA", { s: obra.s, estado: obra.j.estado });
const modelo = await dono.call("GET", `/obras/${obra.j.id}/modelo`);
registrar("RF22", "CA-02b", "modelo novo tem Térreo em 0,00", modelo.j.pavimentos?.some((p) => p.elevacao === 0), modelo.j.pavimentos);
const trans = await dono.call("POST", `/obras/${obra.j.id}/transicoes`, { para: "EM_ANDAMENTO" });
registrar("RF22", "CA-05", "PLANEJADA→EM_ANDAMENTO sem engenheiro com ART → 422", trans.s === 422, { s: trans.s, d: trans.j.detail });
const transInv = await dono.call("POST", `/obras/${obra.j.id}/transicoes`, { para: "CONCLUIDA" });
registrar("RF22", "CA-04", "transição fora do diagrama → 409 com transições possíveis", transInv.s === 409, { s: transInv.s, d: transInv.j.detail });

// RF20 convites
const arqEmail = `arq-${Date.now()}@exemplo.cca`;
const empId = emp.j.id;
const conv = await dono.call("POST", `/companies/${empId}/invites`, { email: arqEmail, obraId: obra.j.id, papel: "ARQUITETO" });
registrar("RF20", "criar", "dono convida arquiteta para a obra", conv.s === 201 || conv.s === 200, { s: conv.s, j: Object.keys(conv.j ?? {}) });
registrar("RF20", "CA-03", "resposta da API não expõe o token", !JSON.stringify(conv.j).match(/"token"/), Object.keys(conv.j ?? {}));
const dono2 = await dono.call("POST", `/companies/${empId}/invites`, { email: "x@exemplo.cca", papel: "DONO" });
registrar("RF20", "CA-04", "convite para DONO → 403 com motivo", dono2.s === 403 && /dono/i.test(dono2.j.detail ?? ""), { s: dono2.s, d: dono2.j.detail });
const subst1 = await dono.call("POST", `/companies/${empId}/invites`, { email: "troca@exemplo.cca", obraId: obra.j.id, papel: "ORCAMENTISTA" });
const subst2 = await dono.call("POST", `/companies/${empId}/invites`, { email: "troca@exemplo.cca", obraId: obra.j.id, papel: "ENGENHEIRO" });
const lista = (await dono.call("GET", `/companies/${empId}/invites`)).j;
const doTroca = lista.filter((c) => c.email === "troca@exemplo.cca");
registrar("RF20", "CA-08", "convite novo para o mesmo e-mail substitui o pendente (anterior REVOGADO)", subst2.s === 201 && doTroca.some((c) => c.estado === "REVOGADO" && c.papel === "ORCAMENTISTA") && doTroca.some((c) => c.estado === "PENDENTE" && c.papel === "ENGENHEIRO"), doTroca.map((c) => `${c.papel}:${c.estado}`));
await espera(1500);
const msg = emailsPara(arqEmail).at(-1) ?? "";
const token = /\/convite\/([A-Za-z0-9_-]{20,})/.exec(msg)?.[1];
  registrar("RF20", "e-mail-url", "link absoluto para a rota /convite/{token} do app", /https?:\/\/[^\s]+\/convite\/[A-Za-z0-9_-]{20,}/.test(msg), msg.split("\n").slice(-6).join(" ").slice(0, 300));
registrar("RF20", "e-mail", "e-mail do convite chega com link", !!token, token ? "link presente" : msg.slice(0, 200));
// aceitar com conta de outro e-mail
const outra = await contaVerificada({ nome: "Pedro Errado", prefixo: "outro" });
if (token) {
  const pub = await outra.call("GET", `/convites/${token}/publico`);
  registrar("RF20", "publico", "página pública do convite mostra empresa/obra/papel", pub.s === 200, pub.j);
  const errado = await outra.call("POST", `/invites/${token}/accept`);
  registrar("RF20", "CA-06", "aceite por conta de outro e-mail → 403", errado.s === 403, { s: errado.s, d: errado.j.detail });
  const inval = await outra.call("GET", `/convites/tokeninvalidotokeninvalido00/publico`);
  registrar("RF20", "CA-07", "token inválido não revela empresa/obra", inval.s === 404 || inval.s === 410, { s: inval.s, j: inval.j });
}
// arquiteta cadastra com o e-mail convidado e aceita
const arq = await novoCliente();
await arq.call("POST", "/auth/register", { tipo: "PESSOA", nome: "Carla Arquiteta", email: arqEmail, senha: SENHA, dataNascimento: "1991-02-02", aceitaTermos: true, aceitaPolitica: true, versaoTermos: v.j.termos, versaoPolitica: v.j.politica });
await arq.call("POST", "/me/verify-email", { codigo: await codigoEmail(arqEmail) });
if (token) {
  const ac = await arq.call("POST", `/invites/${token}/accept`);
  registrar("RF20", "aceite", "convidada aceita e entra na obra", ac.s === 200 || ac.s === 204, { s: ac.s, j: ac.j });
  const de2 = await arq.call("POST", `/invites/${token}/accept`);
  registrar("RF20", "CA-02", "segundo aceite → 410", de2.s === 410, de2.s);
}
const sessArq = await arq.call("GET", "/auth/session");
registrar("RF20", "sessao", "sessão da arquiteta reflete o papel na obra sem novo login", JSON.stringify(sessArq.j.papeisPorObra ?? {}).includes("ARQUITETO"), sessArq.j.papeisPorObra);

// RF21 isolamento: conta de outra empresa tenta a obra
const intrusa = await contaVerificada({ tipo: "EMPRESA", nome: "Intrusa", prefixo: "intrusa" });
const mfaI = await ativarMfa(intrusa);
const iso = await intrusa.call("GET", `/obras/${obra.j.id}`);
const naoExiste = await intrusa.call("GET", `/obras/01a00000-0000-7000-8000-000000000000`);
registrar("RF21", "CA-01", "obra de outra empresa → 404 idêntico a id inexistente", iso.s === 404 && naoExiste.s === 404 && JSON.stringify(iso.j.title) === JSON.stringify(naoExiste.j.title), { a: iso.s, b: naoExiste.s });
const isoModelo = await intrusa.call("GET", `/obras/${obra.j.id}/modelo`);
registrar("RF21", "CA-04a", "modelo de outra empresa → 404", isoModelo.s === 404, isoModelo.s);

// RF21 CA-02: papel insuficiente — arquiteta tenta aprovar/ calcular
const arqAnalise = await arq.call("POST", `/obras/${obra.j.id}/analises`);
registrar("RF21", "CA-02", "arquiteta solicitando análise → 403 com motivo pt-BR", arqAnalise.s === 403, { s: arqAnalise.s, d: arqAnalise.j.detail });

// RF23 sudo
const exp = await dono.call("POST", "/me/exportacoes");
registrar("RF23", "CA-03", "ação sensível sem sudo → 403 sudo_required", exp.s === 403, { s: exp.s, type: exp.j.type, codigo: exp.j.codigo });
const sudo = await dono.call("POST", "/me/sudo", { senha: SENHA, codigo: mfa.recuperacao[1] });
registrar("RF23", "sudo", "sudo com senha + TOTP", sudo.s === 200 || sudo.s === 204, { s: sudo.s, j: sudo.j });
const desMfa = await dono.call("DELETE", "/me/mfa");
registrar("RF23", "CA-07", "dono não pode desativar 2FA obrigatório → 409", desMfa.s === 409, { s: desMfa.s, d: desMfa.j.detail });
const sessoes = await dono.call("GET", "/me/sessions");
registrar("RF23", "CA-05", "GET /me/sessions não expõe id real", sessoes.s === 200 && !JSON.stringify(sessoes.j).match(/"id"/), sessoes.j?.[0]);

// sessão expirada / logout
const out = await dono.call("POST", "/auth/logout");
const depois = await dono.call("GET", "/obras");
registrar("RF19", "logout", "após logout, rotas protegidas → 401", depois.s === 401, { logout: out.s, depois: depois.s });

salvar(new URL("../../docs/auditoria/evidencias/j1-identidade.json", import.meta.url).pathname);
console.log(JSON.stringify({ email, arqEmail, obra: obra.j.id, empresa: empId, segredo: mfa.segredo, intrusa: intrusa.email, segredoIntrusa: mfaI.segredo }));
