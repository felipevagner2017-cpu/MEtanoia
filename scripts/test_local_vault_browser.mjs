import fs from "node:fs";
import http from "node:http";
import path from "node:path";
import { chromium } from "playwright";

const root = process.cwd();
const assets = {
  "/crypto.js": fs.readFileSync(path.join(root, "web/crypto.js")),
  "/local-vault.js": fs.readFileSync(path.join(root, "web/local-vault.js"))
};
const server = http.createServer((request, response) => {
  const asset = assets[new URL(request.url, "http://localhost").pathname];
  if (asset) {
    response.writeHead(200, { "Content-Type": "text/javascript; charset=utf-8", "Cache-Control": "no-store" });
    response.end(asset);
    return;
  }
  response.writeHead(200, { "Content-Type": "text/html; charset=utf-8", "Cache-Control": "no-store" });
  response.end("<!doctype html><html lang='pt-BR'><meta charset='utf-8'><body><script src='/crypto.js'></script><script src='/local-vault.js'></script></body></html>");
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
  words: []
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
  await page.goto(base);
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
  if (JSON.stringify(localState.values).includes("PRIVATE_DIARY_SENTINEL")) throw new Error("A private diary sentinel is visible in IndexedDB values.");

  await page.evaluate(async () => {
    window.localVault.data.items.push({ id: "agenda-2", title: "Persisted offline" });
    await window.localVault.save(window.localVault.data);
  });
  await page.setOffline(true);
  const offlineSave = await page.evaluate(async () => {
    window.localVault.data.reviews.push({ id: "review-offline", body: "Offline encrypted write." });
    await window.localVault.save(window.localVault.data);
    return window.localVault.data.reviews.length;
  });
  if (offlineSave !== 2) throw new Error("Offline encrypted write failed.");

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

  const restored = await page.evaluate(async ({ backup, pass }) => {
    return await window.localVault.restoreBackup(backup, pass);
  }, { backup, pass: exportPhrase });
  if (restored.journal[0]?.body !== original.journal[0].body || restored.items.some(item => item.title === "Persisted offline")) {
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
  const recoveryPage = await context.newPage();
  await recoveryPage.goto(base);
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
  await context.close();
} finally {
  await browser.close();
  await new Promise(resolve => server.close(resolve));
}
