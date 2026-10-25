import { defineConfig } from "@playwright/test";

/** E2E/acessibilidade contra um app no ar (CCA_WEB_URL, padrão http://localhost:3000, com o backend em :8080). */
export default defineConfig({
  testDir: "e2e",
  timeout: 60_000,
  use: {
    baseURL: process.env.CCA_WEB_URL ?? "http://localhost:3000", locale: "pt-BR", viewport: { width: 1280, height: 860 },
    // ambientes com Chromium pré-instalado em outra versão (ex.: sessões em nuvem): PW_CHROMIUM=/caminho/do/chrome
    launchOptions: process.env.PW_CHROMIUM ? { executablePath: process.env.PW_CHROMIUM } : {},
  },
  reporter: [["list"]],
});
