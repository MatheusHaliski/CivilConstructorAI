import { expect, test } from "@playwright/test";

/** Regressão de apresentação com respostas controladas; a autorização real é exercitada em DimensionamentoIT. */
test("pré-dimensionamento aparece como não verificado e não permite aprovar", async ({ page }) => {
  const obraId = "00000000-0000-0000-0000-000000000001";
  await page.route("**/api/v1/**", async (route) => {
    const path = new URL(route.request().url()).pathname;
    let body: unknown = [];
    if (path.endsWith("/auth/session")) body = { contaId: "engenheiro", empresaAtivaId: "empresa", papelEmpresa: "ENGENHEIRO", papeisPorObra: {}, mfaVerificado: true, seloVerificado: true };
    else if (path.endsWith(`/obras/${obraId}`)) body = { id: obraId, nome: "Obra de teste", estado: "PLANEJADA", meusPapeis: ["ENGENHEIRO"] };
    else if (path.endsWith("/dimensionamentos/dim/resultados")) body = [{ elementoId: "V1", tipo: "VIGA", estadoVerificacao: "NAO_VERIFICADO", verifica: false, motivo: "Pré-dimensionamento: detalhamento incompleto.", mdKnm: 10, vdKn: 5, ndKn: 0, asCm2: 2, acoKg: 10, memoria: [] }];
    else if (path.endsWith("/dimensionamentos")) body = [{ id: "dim", estado: "CALCULADO", versaoModelo: 1, elementosNaoVerificam: 1, acoTotalKg: 10 }];
    await route.fulfill({ json: body });
  });
  await page.goto(`/obras/${obraId}/analises`);
  await expect(page.getByText("Pré-dimensionamento sem aprovação técnica", { exact: true })).toBeVisible();
  await page.getByLabel("ART/RRT para aprovação").fill("ART-123");
  await expect(page.getByRole("button", { name: "Aprovar", exact: true })).toBeDisabled();
  await page.getByRole("button", { name: "Memória", exact: true }).click();
  await expect(page.getByText("NÃO VERIFICADO", { exact: true })).toBeVisible();
  await expect(page.getByText("Pré-dimensionamento: detalhamento incompleto.", { exact: true })).toBeVisible();
});
