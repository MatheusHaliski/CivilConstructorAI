import { expect, test, type Page } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";

/**
 * Acessibilidade (WCAG 2.2 AA via axe) nas telas públicas e, com E2E_EMAIL/E2E_SENHA de uma conta ativa, nas telas
 * autenticadas. Falha com qualquer violação "serious" ou "critical".
 */
async function auditar(page: Page, rota: string) {
  await page.goto(rota);
  await page.waitForLoadState("networkidle");
  const r = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21aa", "wcag22aa"]).analyze();
  const graves = r.violations.filter((v) => v.impact === "serious" || v.impact === "critical");
  expect(graves.map((v) => `${v.id}: ${v.help} (${v.nodes.length}× — ${v.nodes[0]?.target.join(" ")})`), rota).toEqual([]);
}

for (const rota of ["/entrar", "/cadastrar", "/verificar-email", "/legal/termos"]) {
  test(`público ${rota}`, async ({ page }) => auditar(page, rota));
}

test.describe("autenticado", () => {
  test.skip(!process.env.E2E_EMAIL, "defina E2E_EMAIL e E2E_SENHA para auditar as telas internas");
  test.describe.configure({ mode: "serial" });
  // um único login para a suíte inteira (o backend limita tentativas por IP: 429 + Retry-After)
  let sessao: Awaited<ReturnType<import("@playwright/test").BrowserContext["storageState"]>>;
  test.beforeAll(async ({ browser }) => {
    const ctx = await browser.newContext();
    const page = await ctx.newPage();
    await page.goto("/entrar");
    await page.getByLabel("E-mail").fill(process.env.E2E_EMAIL!);
    await page.getByLabel("Senha").fill(process.env.E2E_SENHA!);
    await page.getByRole("button", { name: "Entrar" }).click();
    await page.waitForURL(/\/(obras|conta)/);
    sessao = await ctx.storageState();
    await ctx.close();
  });
  const comSessao = async (browser: import("@playwright/test").Browser) => (await browser.newContext({ storageState: sessao })).newPage();
  for (const rota of ["/obras", "/obras/nova", "/empresa", "/conta", "/conta/seguranca", "/conta/privacidade", "/ia", "/motor"]) {
    test(`interno ${rota}`, async ({ browser }) => auditar(await comSessao(browser), rota));
  }
  test("telas da obra", async ({ browser }) => {
    const page = await comSessao(browser);
    await page.goto("/obras");
    await page.waitForLoadState("networkidle");
    const primeira = page.locator('a[href^="/obras/0"]').first();
    test.skip(!(await primeira.count()), "conta sem obra");
    const href = await primeira.getAttribute("href");
    for (const seg of ["", "/modelo", "/estrutura", "/analises", "/geotecnia", "/quantitativos", "/orcamento", "/pranchas", "/interop", "/ia", "/membros"]) await auditar(page, `${href}${seg}`);
  });
});
