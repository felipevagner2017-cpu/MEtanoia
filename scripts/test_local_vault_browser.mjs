import fs from "node:fs";
import http from "node:http";
import path from "node:path";
import { chromium } from "playwright";

const root = process.cwd();
const webRoot = path.join(root, "web");
const mime = { ".html": "text/html; charset=utf-8", ".js": "text/javascript; charset=utf-8", ".css": "text/css; charset=utf-8", ".svg": "image/svg+xml", ".json": "application/json", ".webmanifest": "application/manifest+json", ".png": "image/png", ".jpg": "image/jpeg", ".webp": "image/webp" };
const server = http.createServer((request, response) => {
  const pathname = new URL(request.url, "http://localhost").pathname;
  if (pathname === "/vault-harness") {
    response.writeHead(200, { "Content-Type": "text/html; charset=utf-8", "Cache-Control": "no-store" });
    response.end("<!doctype html><html lang='pt-BR'><meta charset='utf-8'><body><script src='/crypto.js'></script><script src='/local-vault.js'></script></body></html>");
    return;
  }
  const relative = pathname === "/" ? "index.html" : pathname.replace(/^\//, "");
  const file = path.resolve(webRoot, relative);
  if (!file.startsWith(webRoot + path.sep) && file !== path.join(webRoot, "index.html")) {
    response.writeHead(403); response.end(); return;
  }
  if (!fs.existsSync(file) || !fs.statSync(file).isFile()) {
    response.writeHead(404); response.end("Not found"); return;
  }
  response.writeHead(200, { "Content-Type": mime[path.extname(file)] || "application/octet-stream", "Cache-Control": "no-store" });
  response.end(fs.readFileSync(file));
});
await new Promise(resolve => server.listen(0, "127.0.0.1", resolve));
const base = "http://127.0.0.1:" + server.address().port;
const browser = await chromium.launch({ headless: true });
const phrase = "LocalVault-Test-Phrase-2026!";
const exportPhrase = "Protected-Backup-Phrase-2026!";
const legacyKey = "metonoia-pwa-v1";
const original = {
  items: [{ id: "agenda-1", title: "PRIVATE_AGENDA_SENTINEL", date: "2026-11-11" }],
  journal: [{ id: "journal-1", title: "PRIVATE_DIARY_SENTINEL", body: "Confissão e diário não podem ficar legíveis." }],
  reviews: [{ id: "review-1", body: "Revisão privada." }],
  words: [],
  confessions: [{ id: "confession-1", content: "PRIVATE_CONFESSION_SENTINEL" }],
  dailyAnalysis: [{ id: "analysis-1", summary: "PRIVATE_ANALYSIS_SENTINEL" }],
  fastingPlans: [{ id: "fasting-1", purpose: "PRIVATE_FASTING_SENTINEL" }],
  fastingEntries: [{ id: "fasting-entry-1", reflection: "PRIVATE_FASTING_REFLECTION_SENTINEL" }],
  studyProgress: [{ id: "study-1", chapter: 4, status: "in_progress" }],
  studyReflections: [{ id: "study-reflection-1", body: "PRIVATE_STUDY_SENTINEL" }],
  goals: [{ id: "goal-1", title: "PRIVATE_GOAL_SENTINEL" }],
  habits: [{ id: "habit-1", title: "PRIVATE_HABIT_SENTINEL" }]
};

async function unlock(page, value, create = false) {
  await page.waitForSelector("#vaultPassphrase", { timeout: 10000 });
  await page.locator("#vaultPassphrase").fill(value);
  if (create) await page.locator("#vaultPassphraseConfirm").fill(value);
  await page.locator("#vaultPassphraseForm button[type=submit]").click();
}

try {
  const context = await browser.newContext();
  const page = await context.newPage();
  await page.goto(base + "/vault-harness");
  await page.evaluate(({ key, data }) => localStorage.setItem(key, JSON.stringify(data)), { key: legacyKey, data: original });

  const opening = page.evaluate(async key => {
    window.localVault = await window.MEtanoiaLocalVault.open(key, { items: [], journal: [], reviews: [], words: [] });
    return window.localVault.data;
  }, legacyKey);
  await unlock(page, phrase, true);
  const opened = await opening;
  if (opened.journal[0]?.body !== original.journal[0].body) throw new Error("Legacy migration changed the private diary record.");

  const localState = await page.evaluate(async key => {
    const db = await new Promise((resolve, reject) => {
      const request = indexedDB.open("metonoia-local-vault-v1");
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error);
    });
    const values = await new Promise((resolve, reject) => {
      const tx = db.transaction("vault", "readonly"), store = tx.objectStore("vault");
      const requests = ["key-envelope", "app-state"].map(name => store.get(name));
      Promise.all(requests.map(request => new Promise((res, rej) => {
        request.onsuccess = () => res(request.result);
        request.onerror = () => rej(request.error);
      }))).then(([envelope, payload]) => resolve({ envelope, payload }), reject);
    });
    db.close();
    return { legacy: localStorage.getItem(key), values };
  }, legacyKey);
  if (localState.legacy !== null) throw new Error("Legacy plaintext localStorage was not removed after verified migration.");
  if (!localState.values.envelope?.ciphertext || !localState.values.payload?.startsWith("v1.")) throw new Error("Encrypted IndexedDB envelope or payload is missing.");
  const storedValues = JSON.stringify(localState.values);
  for (const sentinel of ["PRIVATE_DIARY_SENTINEL", "PRIVATE_CONFESSION_SENTINEL", "PRIVATE_ANALYSIS_SENTINEL", "PRIVATE_FASTING_SENTINEL", "PRIVATE_FASTING_REFLECTION_SENTINEL", "PRIVATE_STUDY_SENTINEL", "PRIVATE_GOAL_SENTINEL", "PRIVATE_HABIT_SENTINEL"]) {
    if (storedValues.includes(sentinel)) throw new Error("A sensitive app-state sentinel is visible in IndexedDB: " + sentinel);
  }

  await page.evaluate(async () => {
    window.localVault.data.items.push({ id: "agenda-2", title: "Persisted offline" });
    await window.localVault.save(window.localVault.data);
  });
  await context.setOffline(true);
  const offlineSave = await page.evaluate(async () => {
    window.localVault.data.reviews.push({ id: "review-offline", body: "Offline encrypted write." });
    await window.localVault.save(window.localVault.data);
    return window.localVault.data.reviews.length;
  });
  if (offlineSave !== 2) throw new Error("Offline encrypted write failed.");

  const syncState = await page.evaluate(async () => {
    const reserved = await Promise.all(Array.from({ length: 3 }, () =>
      window.localVault.reserveCounter("sync-user", "device-a", "fasting-plan")
    ));
    await window.localVault.setSyncMetadata("sync-user", "record-1", { revision: 4, state: "synced" });
    return { reserved, metadata: window.localVault.getSyncMetadata("sync-user", "record-1") };
  });
  if (syncState.reserved.join(",") !== "0,1,2" || syncState.metadata?.revision !== 4) {
    throw new Error("Encrypted sync nonce reservations or revision metadata were not serialized.");
  }

  const backup = await page.evaluate(async pass => window.localVault.exportBackup(pass, window.localVault.data), exportPhrase);
  if (backup.includes("PRIVATE_DIARY_SENTINEL") || backup.includes("Offline encrypted write.")) throw new Error("Export contains plaintext personal records.");
  const backupObject = JSON.parse(backup);
  if (backupObject.format !== "metonoia-encrypted-backup" || backupObject.cipher !== "AES-256-GCM") throw new Error("Export metadata is not a protected backup format.");

  const wrongPass = await page.evaluate(async ({ backup, pass }) => {
    try { await window.localVault.restoreBackup(backup, pass); return false; }
    catch { return true; }
  }, { backup, pass: "Incorrect-Backup-Phrase-2026!" });
  if (!wrongPass) throw new Error("An export with the wrong password was accepted.");

  const tampered = { ...backupObject, payload: backupObject.payload.slice(0, -4) + "AAAA" };
  const tamperRejected = await page.evaluate(async ({ backup, pass }) => {
    try { await window.localVault.restoreBackup(JSON.stringify(backup), pass); return false; }
    catch { return true; }
  }, { backup: tampered, pass: exportPhrase });
  if (!tamperRejected) throw new Error("Tampered export was accepted.");

  await page.evaluate(async () => {
    window.localVault.data.items[0].title = "MUTATED_AFTER_EXPORT";
    await window.localVault.save(window.localVault.data);
  });
  const restored = await page.evaluate(async ({ backup, pass }) => {
    return await window.localVault.restoreBackup(backup, pass);
  }, { backup, pass: exportPhrase });
  if (restored.journal[0]?.body !== original.journal[0].body ||
      !restored.items.some(item => item.title === "Persisted offline") ||
      restored.items.some(item => item.title === "MUTATED_AFTER_EXPORT")) {
    throw new Error("Protected backup restore did not recover the exact exported snapshot.");
  }
  const previousSnapshot = await page.evaluate(async () => {
    const request = indexedDB.open("metonoia-local-vault-v1");
    const db = await new Promise((resolve, reject) => { request.onsuccess = () => resolve(request.result); request.onerror = () => reject(request.error); });
    const value = await new Promise((resolve, reject) => {
      const tx = db.transaction("vault", "readonly"), req = tx.objectStore("vault").get("previous-app-state");
      req.onsuccess = () => resolve(req.result); req.onerror = () => reject(req.error);
    });
    db.close(); return value;
  });
  if (!previousSnapshot?.startsWith("v1.")) throw new Error("Pre-restore encrypted recovery snapshot was not retained.");
  console.log("PASS: legacy localStorage migration, AES-GCM IndexedDB storage, offline writes, password-protected export, wrong-password/tamper rejection, and restore recovery.");

  // Simulate interruption after ciphertext was committed but before legacy cleanup.
  const recoveryContext = await browser.newContext();
  const recoveryPage = await recoveryContext.newPage();
  await recoveryPage.goto(base + "/vault-harness");
  const interruptedLegacy = { items: [{ id: "recover", title: "INTERRUPTED_MIGRATION" }], journal: [], reviews: [], words: [] };
  await recoveryPage.evaluate(({ key, data }) => {
    localStorage.setItem(key, JSON.stringify(data));
    const originalRemove = Storage.prototype.removeItem;
    Storage.prototype.removeItem = function (name) {
      if (name === key) throw new Error("simulated interruption after encrypted commit");
      return originalRemove.call(this, name);
    };
  }, { key: legacyKey, data: interruptedLegacy });
  const interrupted = recoveryPage.evaluate(async key => {
    try { await window.MEtanoiaLocalVault.open(key, { items: [], journal: [], reviews: [], words: [] }); return false; }
    catch { return true; }
  }, legacyKey);
  await unlock(recoveryPage, phrase, true);
  if (!await interrupted) throw new Error("The simulated legacy-cleanup interruption did not occur.");
  await recoveryPage.evaluate(key => { Storage.prototype.removeItem = Storage.prototype.removeItem; }, legacyKey).catch(() => {});
  // Restore the native method from a clean same-origin realm, then retry the migration.
  await recoveryPage.reload();
  const retry = recoveryPage.evaluate(async key => {
    window.recoveredVault = await window.MEtanoiaLocalVault.open(key, { items: [], journal: [], reviews: [], words: [] });
    return window.recoveredVault.data.items[0]?.title;
  }, legacyKey);
  await unlock(recoveryPage, phrase, false);
  if (await retry !== "INTERRUPTED_MIGRATION") throw new Error("Interrupted migration did not recover its committed encrypted copy.");
  console.log("PASS: interrupted legacy cleanup recovered from the authenticated IndexedDB copy without deleting the original first.");
  await recoveryContext.close();
  await context.close();

  // End-to-end startup against the real PWA HTML and app.js, not just the vault module.
  const appContext = await browser.newContext({ serviceWorkers: "block", acceptDownloads: true });
  const appPage = await appContext.newPage();
  await appPage.addInitScript(({ key, data }) => localStorage.setItem(key, JSON.stringify(data)), { key: legacyKey, data: original });
  await appPage.goto(base);
  await unlock(appPage, phrase, true);
  await appPage.waitForSelector("#todayDate", { timeout: 15000 });
  await appPage.waitForSelector("#homeReminderCard");
  const appMigration = await appPage.evaluate(key => ({
    legacy: localStorage.getItem(key),
    heading: document.querySelector("h1")?.textContent,
    vaultLoaded: !!window.MEtanoiaLocalVault
  }), legacyKey);
  if (appMigration.legacy !== null || appMigration.heading !== "MEtanoia" || !appMigration.vaultLoaded) {
    throw new Error("The actual PWA did not start with its encrypted local store.");
  }
  await appPage.locator('[data-tab="evolution"]').click();
  await appPage.waitForSelector("#exportLocalBackup", { state: "visible" });
  const downloadPromise = appPage.waitForEvent("download");
  await appPage.locator("#exportLocalBackup").click();
  await unlock(appPage, exportPhrase, true);
  const download = await downloadPromise;
  if (!download.suggestedFilename().includes("MEtanoia-backup-")) throw new Error("The PWA export control did not create the protected backup file.");
  console.log("PASS: real PWA startup, legacy data migration, and user-interface export flow.");
  await appContext.close();

  // Two independent tabs must reserve distinct counters from the same IndexedDB database.
  const parallelPage = await context.newPage();
  await parallelPage.goto(base + "/vault-harness");
  const parallelOpen = parallelPage.evaluate(async key => {
    window.parallelVault = await window.MEtanoiaLocalVault.open(key, { items: [], journal: [], reviews: [], words: [] });
    return true;
  }, legacyKey);
  await unlock(parallelPage, phrase, false);
  await parallelOpen;
  const concurrentNonces = await Promise.all([
    page.evaluate(() => window.localVault.reserveCounter("sync-user", "device-b", "fasting-plan")),
    parallelPage.evaluate(() => window.parallelVault.reserveCounter("sync-user", "device-b", "fasting-plan"))
  ]);
  if (concurrentNonces.slice().sort((a,b)=>a-b).join(",") !== "0,1") {
    throw new Error("Two tabs reserved a duplicate shared-device nonce.");
  }
  await parallelPage.close();

  // A fresh tab in the same browser profile must reopen the persisted vault.
  await page.close();
  const reopenedPage = await context.newPage();
  await reopenedPage.goto(base + "/vault-harness");
  const reopenedPromise = reopenedPage.evaluate(async key => {
    window.reopenedVault = await window.MEtanoiaLocalVault.open(key, { items: [], journal: [], reviews: [], words: [] });
    return window.reopenedVault.data;
  }, legacyKey);
  await unlock(reopenedPage, phrase, false);
  const reopened = await reopenedPromise;
  if (reopened.journal[0]?.body !== original.journal[0].body ||
      reopened.confessions[0]?.content !== "PRIVATE_CONFESSION_SENTINEL" ||
      reopened.fastingEntries[0]?.reflection !== "PRIVATE_FASTING_REFLECTION_SENTINEL" ||
      reopened.studyProgress[0]?.chapter !== 4) {
    throw new Error("Encrypted records were not preserved after closing and reopening the browser tab.");
  }
  const nextNonce = await reopenedPage.evaluate(() =>
    window.reopenedVault.reserveCounter("sync-user", "device-a", "fasting-plan")
  );
  const restoredMetadata = await reopenedPage.evaluate(() =>
    window.reopenedVault.getSyncMetadata("sync-user", "record-1")
  );
  if (nextNonce !== 3 || restoredMetadata?.revision !== 4) {
    throw new Error("Encrypted sync nonce/revision state did not survive browser close and reopen.");
  }
  console.log("PASS: browser tab close/reopen, including confession, fasting, study, goals and habits collections.");
  await reopenedPage.close();
  await context.close();

  // Restoring an older export must not roll back the independent IndexedDB nonce counter.
  const rollbackContext = await browser.newContext();
  const rollbackPage = await rollbackContext.newPage();
  await rollbackPage.goto(base + "/vault-harness");
  const rollbackOpen = rollbackPage.evaluate(async key => {
    window.rollbackVault = await window.MEtanoiaLocalVault.open(key, { items: [], journal: [], reviews: [], words: [] });
    return true;
  }, legacyKey);
  await unlock(rollbackPage, phrase, true);
  await rollbackOpen;
  const oldBackup = await rollbackPage.evaluate(async pass => window.rollbackVault.exportBackup(pass, window.rollbackVault.data), exportPhrase);
  const used = await rollbackPage.evaluate(async () => Promise.all(Array.from({length:3}, () =>
    window.rollbackVault.reserveCounter("sync-user", "device-c", "fasting-entry")
  )));
  if (used.join(",") !== "0,1,2") throw new Error("Initial nonce reservations were not monotonic.");
  await rollbackPage.evaluate(async ({backup,pass}) => window.rollbackVault.restoreBackup(backup,pass), {backup:oldBackup,pass:exportPhrase});
  const afterRollback = await rollbackPage.evaluate(() =>
    window.rollbackVault.reserveCounter("sync-user", "device-c", "fasting-entry")
  );
  if (afterRollback !== 3) throw new Error("Restoring an older backup rolled back the AES-GCM nonce counter.");
  console.log("PASS: cross-tab atomic nonce reservation and backup-rollback resistance.");
  await rollbackContext.close();
} finally {
  await browser.close();
  await new Promise(resolve => server.close(resolve));
}
