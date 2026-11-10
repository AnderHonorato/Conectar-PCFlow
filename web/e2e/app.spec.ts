import { expect, test } from "@playwright/test";

test.beforeEach(async ({ page }) => {
  page.on("websocket", () => {
    throw new Error("websocket inesperado na prévia desconectada");
  });
});

test("carrega desconectado, navega e explica o controle", async ({ page }) => {
  await page.goto("./");
  await expect(page.getByRole("heading", { name: "PCFlow" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Computadores" })).toBeVisible();
  await expect(page.getByText("Desconectado")).toBeVisible();
  await expect(page.getByText("Nenhum computador nesta lista")).toBeVisible();

  await page.getByRole("link", { name: "Abrir o controle" }).click();
  const touchpad = page.getByRole("application", { name: "Touchpad" });
  await expect(touchpad).toBeVisible();
  await expect(page.getByRole("status")).toContainText("Desconectado. Conecte um PC para usar este controle.");

  const touchBox = await touchpad.boundingBox();
  const panelBox = await page.getByRole("region", { name: "Painel" }).boundingBox();
  const toolbarBox = await page.getByRole("toolbar", { name: "Módulos" }).boundingBox();
  expect(touchBox && panelBox && toolbarBox).toBeTruthy();
  expect(touchBox!.height).toBeGreaterThan(panelBox!.height + toolbarBox!.height);

  await page.getByRole("button", { name: "Mídia" }).click();
  await page.getByRole("button", { name: "Reproduzir ou pausar" }).click();
  await expect(page.getByRole("status")).toContainText("Desconectado. Conecte um PC para usar este controle.");

  await page.getByRole("button", { name: "Menu" }).click();
  await page.getByRole("link", { name: "Ajustes" }).click();
  await expect(page.getByRole("heading", { name: "Configurações" })).toBeVisible();
  await expect(page.getByRole("switch", { name: "Botões do mouse" })).toBeVisible();

  await page.getByRole("switch", { name: "Modo canhoto" }).click();
  await page.goto("./#/controle");
  await expect(page.locator(".device")).toHaveAttribute("data-left-handed", "true");

  await page.goto("./#/paridade");
  await expect(page.getByRole("heading", { name: "O que este navegador faz" })).toBeVisible();
  const bluetooth = page.getByRole("row", { name: /Bluetooth/ });
  await expect(bluetooth).toContainText("dependente do navegador");
  await expect(bluetooth).not.toContainText("universal");

  await page.goto("./");
  await page.getByLabel("Endereço do agente").fill("http://pc.local/pcflow");
  await page.getByRole("button", { name: "Conectar" }).click();
  await expect(page.getByRole("alert")).toContainText("Informe um endereço ws:// ou wss://.");
});

test("publica manifesto e service worker", async ({ page }) => {
  const manifest = await page.request.get("./manifest.webmanifest");
  expect(manifest.ok()).toBeTruthy();
  const body = await manifest.json();
  expect(body.name).toBe("PCFlow");
  expect(body.lang).toBe("pt-BR");
  expect(body.display).toBe("standalone");
  const worker = await page.request.get("./sw.js");
  expect(worker.ok()).toBeTruthy();
  const icon = await page.request.get("./icons/icon-192.png");
  expect(icon.ok()).toBeTruthy();
});

test("o touchpad segue dominante numa janela larga", async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 800 });
  await page.goto("./#/controle");
  const touchBox = await page.getByRole("application", { name: "Touchpad" }).boundingBox();
  const panelBox = await page.getByRole("region", { name: "Painel" }).boundingBox();
  const toolbarBox = await page.getByRole("toolbar", { name: "Módulos" }).boundingBox();
  expect(touchBox!.height).toBeGreaterThan(panelBox!.height + toolbarBox!.height);
  await expect(page.getByText("Nenhum PC")).toBeVisible();
  await expect(page.getByText("Nenhuma janela")).toBeVisible();
});
