import { mkdirSync } from "node:fs";
import path from "node:path";
import { expect, test } from "@playwright/test";

/** Fluxo público local: cria uma parede e aplica camadas sem injetar estado no motor. */
test("aplica acabamentos em faces distintas com espessura, compra e peso", async ({ page }) => {
  await page.setViewportSize({ width: 1600, height: 1100 });
  await page.route("**/api/v1/auth/session", route => route.fulfill({ json: {
    contaId: "ensaio", empresaAtivaId: "ensaio", papelEmpresa: "ENGENHEIRO", papeisPorObra: {},
    mfaVerificado: true, seloVerificado: true, sudoAtivo: false,
  } }));
  await page.goto("/motor");
  await page.getByRole("tab", { name: "VI / IA", exact: true }).click();
  await page.locator("#console-vi").fill("VI(PAREDE, p1=[7,8], p2=[13,8], esp=0.14, h=2.8)\nVI(PORTA, pos=[8.5,8], larg=0.9, alt=2.1)");
  await page.getByRole("button", { name: "Executar VI", exact: true }).click();
  await page.getByRole("tab", { name: "Propriedades", exact: true }).click();
  const planta = page.getByRole("img", { name: /Planta do pavimento Térreo: 2 elementos/ });
  const caixa = await planta.boundingBox();
  expect(caixa).not.toBeNull();
  await planta.click({ position: { x: caixa!.width / 2, y: caixa!.height / 2 } });
  const painel = page.getByRole("region", { name: "Materiais e acabamentos" });
  await expect(painel).toBeVisible();
  const nucleo = page.getByLabel("Material do núcleo", { exact: true });
  await expect(nucleo.locator('option[value="MAT_ALV_BLOCO19"]')).toHaveCount(1);
  await expect(nucleo.locator('option[value="MAT_VIDRO_TEMP8"]')).toHaveCount(0);
  await nucleo.selectOption("MAT_ALV_BLOCO19");
  await expect(nucleo).toHaveValue("MAT_ALV_BLOCO19");
  const material = painel.getByLabel("Material da nova camada", { exact: true });
  const reboco = await material.locator("option").filter({ hasText: /reboco/i }).first().getAttribute("value");
  await material.selectOption(reboco!);
  await painel.getByRole("button", { name: "Adicionar camada", exact: true }).click();
  await expect(painel.getByText(/Área líquida: 14,91 m²/)).toBeVisible();
  const espessura = painel.getByLabel("Espessura (mm)", { exact: true }).first();
  await espessura.fill("25"); await espessura.press("Tab");
  await expect(espessura).toHaveValue("25");
  const azulejo = await material.locator("option").filter({ hasText: /^Azulejo$/ }).getAttribute("value");
  await material.selectOption(azulejo!);
  await painel.getByLabel("Face da nova camada", { exact: true }).selectOption("LADO_B");
  await painel.getByRole("button", { name: "Adicionar camada", exact: true }).click();
  await expect(painel.locator("fieldset")).toHaveCount(2);
  await painel.getByLabel("Formato comercial 2", { exact: true }).selectOption("0.2x0.3");
  await painel.locator("fieldset").nth(1).getByLabel("Junta (mm)", { exact: true }).fill("5");
  await painel.locator("fieldset").nth(1).getByLabel("Junta (mm)", { exact: true }).press("Tab");
  await expect(painel.locator("fieldset").nth(1).getByLabel("Largura da peça (mm)", { exact: true })).toHaveValue("200");
  await expect(painel.locator("fieldset").nth(1).getByLabel("Altura da peça (mm)", { exact: true })).toHaveValue("300");
  await expect(painel.locator("fieldset").nth(0).locator("legend")).toContainText("Lado A");
  await expect(painel.locator("fieldset").nth(1).locator("legend")).toContainText("Lado B");
  await expect(painel.getByText(/Compra:/).first()).toBeVisible();
  await expect(painel.getByText(/Peso instalado:/).first()).toBeVisible();
  await page.getByRole("button", { name: "3D", exact: true }).click();
  const cena = page.getByRole("img", { name: "Cena 3D com 2 elementos", exact: true });
  await expect(cena.locator("canvas")).toBeVisible();
  await cena.locator("canvas").hover();
  await page.mouse.wheel(0, -1400);
  await page.evaluate(() => new Promise<void>(r => requestAnimationFrame(() => requestAnimationFrame(() => r()))));
  const pasta = path.resolve("../../docs/validacao"); mkdirSync(pasta, { recursive: true });
  await page.screenshot({ path: path.join(pasta, "acabamentos3d.png"), fullPage: true });
});


test("não oferece cimento sem dosagem comprovada para o concreto", async ({ page }) => {
  await page.route("**/api/v1/auth/session", route => route.fulfill({ json: {
    contaId: "ensaio", empresaAtivaId: "ensaio", papelEmpresa: "ENGENHEIRO", papeisPorObra: {},
    mfaVerificado: true, seloVerificado: true, sudoAtivo: false,
  } }));
  await page.goto("/motor");
  await page.getByRole("tab", { name: "VI / IA", exact: true }).click();
  await page.locator("#console-vi").fill("VI(PILAR, centro=[10,8], b=0.25, h=0.25, mat=MAT_CONC_FCK30)");
  await page.getByRole("button", { name: "Executar VI", exact: true }).click();
  await page.getByRole("tab", { name: "Propriedades", exact: true }).click();
  const planta = page.getByRole("img", { name: /Planta do pavimento Térreo: 1 elementos/ });
  const caixa = await planta.boundingBox();
  await planta.click({ position: { x: caixa!.width / 2, y: caixa!.height / 2 } });
  const painel = page.getByRole("region", { name: "Seleção de cimento" });
  await expect(painel.getByText("Cimento para concreto fck 30 MPa", { exact: true })).toBeVisible();
  await expect(painel.getByRole("status")).toContainText("Nenhum cimento com dosagem comprovada");
  await expect(painel.getByRole("table", { name: "Cimentos com correspondência documentada ao concreto" })).toHaveCount(0);
  await expect(painel.getByRole("button", { name: "Aplicar", exact: true })).toHaveCount(0);
});
