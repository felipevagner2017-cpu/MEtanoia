import fs from "node:fs";
import path from "node:path";
import { chromium } from "playwright";

const baseUrl = (process.env.METANOIA_PREVIEW_URL || "https://metanoia-preview.restaura-lar.workers.dev").replace(/\/$/, "");
const outDir = "artifacts/web-preview";
fs.mkdirSync(outDir, { recursive: true });
const browser = await chromium.launch({ headless: true });
const errors = [];
try {
  const context = await browser.newContext({
    viewport: { width: 390, height: 844 },
    deviceScaleFactor: 2,
    isMobile: true,
    hasTouch: true,
  });
  const page = await context.newPage();
  page.on("pageerror", error => errors.push(error.message));
  const response = await page.goto(baseUrl, { waitUntil: "networkidle", timeout: 60000 });
  if (!response || !response.ok()) throw new Error("Preview root returned HTTP " + (response?.status() ?? "no response"));
  await page.waitForSelector("#word:not(.hidden)", { timeout: 20000 });
  if (!(await page.locator("body").innerText()).includes("MEtonoia")) {
    throw new Error("MEtonoia brand text is missing from the live Preview.");
  }

  async function capture(name, section) {
    await page.waitForSelector("#" + section + ":not(.hidden)", { timeout: 15000 });
    await page.waitForTimeout(250);
    await page.screenshot({ path: path.join(outDir, name + ".png"), fullPage: true });
    console.log("CAPTURED " + name + " -> #" + section);
  }
  async function tab(name) {
    await page.locator('[data-tab="' + name + '"]').first().click();
    await page.waitForTimeout(200);
  }
  async function openArea(area, target) {
    await tab("areas");
    const card = page.locator('[data-word-slide="' + area + '"]').first();
    await card.scrollIntoViewIfNeeded();
    await card.locator('[data-open-section="' + target + '"]').click();
    await capture(area.toLowerCase(), target);
  }
  await capture("01-jornada-diaria", "word");
  await tab("areas");
  await capture("02-areas", "areas");
  await openArea("Espiritual", "pastoralStudy");
  if (!(await page.locator("#pastoralStudy").innerText()).includes("John Wesley")) {
    throw new Error("Pastoral study references are missing from the deployed PWA.");
  }
  await openArea("Matrimonial", "ingrid");
  await openArea("Profissional", "professional");
  await openArea("Cotidiano", "cotidiano");

  for (const [name, section] of [
    ["habitos", "habits"],
    ["diario", "diary"],
    ["revisoes", "evolution"],
    ["alertas", "alerts"],
  ]) {
    await page.locator('#cotidiano [data-open-section="' + section + '"]').click().catch(async () => {
      await tab("areas");
      const card = page.locator('[data-word-slide="Cotidiano"]').first();
      await card.scrollIntoViewIfNeeded();
      await card.locator('[data-open-section="cotidiano"]').click();
      await page.locator('#cotidiano [data-open-section="' + section + '"]').click();
    });
    await capture(name, section);
  }

  await tab("agenda");
  await capture("agenda", "agenda");
  await page.waitForFunction(async () => "serviceWorker" in navigator && Boolean(await navigator.serviceWorker.getRegistration()), null, { timeout: 15000 }).catch(() => {});
  const pwa = await page.evaluate(async () => ({
    serviceWorker: "serviceWorker" in navigator ? Boolean(await navigator.serviceWorker.getRegistration()) : false,
    manifest: document.querySelector('link[rel="manifest"]')?.getAttribute("href") || null,
  }));
  if (!pwa.serviceWorker) throw new Error("The live PWA did not register its Service Worker.");
  if (!pwa.manifest) throw new Error("The live PWA manifest link is missing.");
  fs.writeFileSync(path.join(outDir, "inspection.json"), JSON.stringify({
    url: baseUrl,
    viewport: "390x844",
    screenshots: fs.readdirSync(outDir).filter(x => x.endsWith(".png")),
    pwa,
    pageErrors: errors,
  }, null, 2));
  if (errors.length) throw new Error("Browser runtime errors: " + errors.join(" | "));

  const desktop = await browser.newPage({ viewport: { width: 1365, height: 900 } });
  desktop.on("pageerror", error => errors.push(error.message));
  await desktop.goto(baseUrl, { waitUntil: "networkidle", timeout: 60000 });
  await desktop.screenshot({ path: path.join(outDir, "desktop-jornada.png"), fullPage: true });
  console.log("LIVE PREVIEW BROWSER INSPECTION PASSED: " + baseUrl);
} finally {
  await browser.close();
}
