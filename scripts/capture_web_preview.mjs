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

  async function openCotidianoTool(name, section) {
    if (!(await page.locator("#cotidiano").isVisible())) {
      await tab("areas");
      const card = page.locator('[data-word-slide="Cotidiano"]').first();
      await card.scrollIntoViewIfNeeded();
      await card.locator('[data-open-section="cotidiano"]').click();
    }
    await page.locator('#cotidiano [data-open-section="' + section + '"]').click();
    await capture(name, section);
  }
  for (const [name, section] of [
    ["habitos", "habits"],
    ["diario", "diary"],
    ["revisoes", "evolution"],
    ["alertas", "alerts"],
  ]) await openCotidianoTool(name, section);

  // Functional UI smoke tests in a disposable browser profile.
  await openCotidianoTool("habitos", "habits");
  const habitTitle = "CI smoke habit " + Date.now();
  await page.locator("#newHabit").click();
  await page.locator("#eventTitle").fill(habitTitle);
  await page.locator("#eventDescription").fill("Registro automatizado sem dados pessoais.");
  if (await page.locator("#scheduleMode").inputValue() !== "daily") {
    throw new Error("New habits are not defaulting to daily recurrence.");
  }
  await page.locator("#edit button.btn.gold").click();
  await page.locator("#habitsList").getByText(habitTitle).waitFor();
  await page.locator('#habitsList [data-answer]').first().click();
  await page.locator("#done").click();
  if (!(await page.locator("#habitsList").innerText()).includes("100%")) {
    throw new Error("Habit completion did not update its seven-day consistency.");
  }
  await page.reload({ waitUntil: "networkidle" });
  await openCotidianoTool("habitos", "habits");
  const persistedHabit = page.locator("#habitsList").getByText(habitTitle);
  await persistedHabit.waitFor();
  if (!(await page.locator("#habitsList").innerText()).includes("100%")) {
    throw new Error("Habit completion history did not persist after reload.");
  }
  page.once("dialog", dialog => dialog.accept());
  await page.locator('#habitsList [data-del]').first().click();
  await persistedHabit.waitFor({ state: "detached" });

  await tab("agenda");
  await page.locator('[data-agenda-view="week"]').click();
  if (await page.locator("#calendarGrid .calendar-day").count() !== 7) throw new Error("Week view did not render exactly seven days.");
  await page.locator('[data-agenda-view="day"]').click();
  if (await page.locator("#calendarGrid").isVisible()) throw new Error("Day view did not hide the month/week grid.");
  await page.locator('[data-agenda-view="month"]').click();
  if (await page.locator("#calendarGrid .calendar-day").count() < 28) throw new Error("Month view did not render a full calendar grid.");
  const eventTitle = "CI smoke event " + Date.now();
  await page.locator("#addCommitment").click();
  await page.locator("#eventTitle").fill(eventTitle);
  await page.locator("#eventDescription").fill("Descrição automatizada do compromisso.");
  await page.locator("#eventLocation").fill("Local de homologação");
  await page.locator('input[name="time"]').fill("10:00");
  await page.locator('input[name="endTime"]').fill("11:00");
  await page.locator('#edit input[name="reminders"][value="120"]').uncheck();
  await page.locator("#edit button.btn.gold").click();
  let eventCard = page.locator("#agendaList .agenda-event").filter({ hasText: eventTitle });
  await eventCard.waitFor();
  await page.reload({ waitUntil: "networkidle" });
  await tab("agenda");
  eventCard = page.locator("#agendaList .agenda-event").filter({ hasText: eventTitle });
  await eventCard.waitFor();
  const eventText = await eventCard.innerText();
  for (const expected of ["10:00 – 11:00", "Descrição automatizada do compromisso.", "Local: Local de homologação"]) {
    if (!eventText.includes(expected)) throw new Error("Agenda field did not persist: " + expected);
  }
  await eventCard.locator("[data-edit]").click();
  if (await page.locator('#edit input[name="reminders"]:checked').count() !== 2) {
    throw new Error("Per-event reminder preferences did not persist after reload.");
  }
  await page.locator("#eventTitle").fill(eventTitle + " editado");
  await page.locator("#edit button.btn.gold").click();
  await page.locator("#agendaList").getByText(eventTitle + " editado").waitFor();
  page.once("dialog", dialog => dialog.accept());
  await page.locator("#agendaList .agenda-event").filter({ hasText: eventTitle + " editado" }).locator("[data-del]").click();
  await page.reload({ waitUntil: "networkidle" });
  await tab("agenda");
  if ((await page.locator("#agendaList").innerText()).includes(eventTitle)) {
    throw new Error("Agenda deletion did not persist after reload.");
  }
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
