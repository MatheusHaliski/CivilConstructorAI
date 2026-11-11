import { test, expect } from "@playwright/test";
import { mkdirSync, readFileSync } from "node:fs";
import path from "node:path";

/** Desenho real no motor local; só a sessão é controlada. Não grava obra nem simula aprovação. */
test("desenha quatro pilares, quatro vigas e uma laje em dois níveis", async ({ page }) => {
  await page.setViewportSize({ width: 1600, height: 1100 });
  await page.route("**/api/v1/auth/session", route => route.fulfill({ json: {
    contaId: "ensaio", empresaAtivaId: "ensaio", papelEmpresa: "ENGENHEIRO", papeisPorObra: {},
    mfaVerificado: true, seloVerificado: true, sudoAtivo: false,
  } }));
  await page.goto("/motor");
  await page.getByRole("tab", { name: "VI / IA", exact: true }).click();
  const base = readFileSync(path.resolve("../../docs/validacao/desenho-base.vi"), "utf8");
  const cobertura = readFileSync(path.resolve("../../docs/validacao/desenho-cobertura.vi"), "utf8");
  await page.locator("#console-vi").fill(base);
  await page.getByRole("button", { name: "Executar VI", exact: true }).click();
  await expect(page.getByRole("img", { name: "Planta do pavimento Térreo: 4 elementos, 4 eixos", exact: true })).toBeVisible();
  await page.getByLabel("Pavimento ativo", { exact: true }).selectOption({ label: "Cobertura (+3.00)" });
  await page.locator("#console-vi").fill(cobertura);
  await page.getByRole("button", { name: "Executar VI", exact: true }).click();
  const planta = page.getByRole("img", { name: "Planta do pavimento Cobertura: 5 elementos, 4 eixos", exact: true });
  await expect(planta).toBeVisible();
  await page.getByRole("tab", { name: "VI / IA", exact: true }).click();
  // Aguarda dois quadros de pintura antes de capturar o canvas.
  await page.evaluate(() => new Promise<void>(r => requestAnimationFrame(() => requestAnimationFrame(() => r()))));
  const out = path.resolve("../../docs/validacao"); mkdirSync(out, { recursive: true });
  await page.screenshot({ path: path.join(out,"desenho-planta.png"), fullPage: true });
  await page.getByRole("button", { name: "3D", exact: true }).click();
  const cena = page.getByRole("img", { name: "Cena 3D com 9 elementos", exact: true });
  await expect(cena).toBeVisible();
  await expect(cena.locator("canvas")).toBeVisible();
  // Inspeciona pixels da captura, não apenas a presença de um canvas vazio.
  await expect.poll(async () => {
    const png = await cena.locator("canvas").screenshot();
    return page.evaluate(async (url) => {
      const img = new Image(); img.src = url; await img.decode();
      const c = document.createElement("canvas"); c.width = img.width; c.height = img.height;
      const ctx = c.getContext("2d")!; ctx.drawImage(img,0,0);
      const pixels = ctx.getImageData(0,0,c.width,c.height).data;
      let azuis = 0;
      for (let i=0;i<pixels.length;i+=4) if (pixels[i+2]! > pixels[i]!*1.3 && pixels[i+2]! > pixels[i+1]!*1.1 && pixels[i+2]! > 90) azuis++;
      return azuis;
    }, `data:image/png;base64,${png.toString("base64")}`);
  }, { timeout: 20_000 }).toBeGreaterThan(50);
  await page.screenshot({ path: path.join(out,"desenho-3d.png"), fullPage: true });
});
