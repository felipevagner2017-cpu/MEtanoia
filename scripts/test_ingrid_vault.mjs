import { readFileSync } from "node:fs";
import vm from "node:vm";
import { webcrypto } from "node:crypto";
import assert from "node:assert/strict";

const app = readFileSync(new URL("../web/app.js", import.meta.url), "utf8");
const start = app.indexOf("const INGRID_VAULT_KEY=");
const end = app.indexOf("function dayOffset", start);
assert.ok(start >= 0 && end > start, "production vault implementation must exist");

const storage = new Map();
const prompts = [];
const alerts = [];
const context = {
  crypto: webcrypto,
  prompts,
  alerts,
  TextEncoder,
  TextDecoder,
  Uint8Array,
  Date,
  JSON,
  Math,
  btoa,
  atob,
  localStorage: {
    getItem: (key) => storage.has(key) ? storage.get(key) : null,
    setItem: (key, value) => storage.set(key, String(value)),
    removeItem: (key) => storage.delete(key),
  },
  prompt: () => prompts.shift() ?? null,
  alert: (message) => alerts.push(String(message)),
  confirm: () => true,
  renderIngrid: () => {},
  $: (id) => id === "ingridConsent" ? { checked: true } : null,
};
const test = `
${app.slice(start, end)}
(async () => {
  const fixture = { cycle: { lastPeriodStart: "2026-10-01", cycleLength: 28 }, entries: [{ date: "2026-11-05", shared: "private-sentinel-value" }] };
  ingridData = fixture;
  ingridSessionPin = "correct-horse-battery";
  ingridVaultSalt = crypto.getRandomValues(new Uint8Array(16));
  await saveIngridVault();
  const encrypted = JSON.parse(localStorage.getItem(INGRID_VAULT_KEY));
  if (encrypted.version !== 3 || encrypted.encrypted !== true || encrypted.algorithm !== "AES-256-GCM") throw new Error("vault was not stored using AES-GCM v3");
  if (localStorage.getItem(INGRID_VAULT_KEY).includes("private-sentinel-value")) throw new Error("plaintext leaked into localStorage");

  ingridData = null; ingridUnlocked = false; ingridSessionPin = ""; ingridVaultSalt = null;
  prompts.push("wrong-password");
  await unlockIngridVault();
  if (ingridUnlocked || ingridData !== null) throw new Error("wrong password unlocked the vault");
  if (JSON.stringify(JSON.parse(localStorage.getItem(INGRID_VAULT_KEY))) !== JSON.stringify(encrypted)) throw new Error("failed unlock modified stored ciphertext");

  prompts.push("correct-horse-battery");
  await unlockIngridVault();
  if (!ingridUnlocked || ingridData.entries[0].shared !== "private-sentinel-value") throw new Error("valid password did not restore original data");

  const legacy = { version: 2, encrypted: false, data: fixture };
  localStorage.setItem(INGRID_VAULT_KEY, JSON.stringify(legacy));
  ingridData = null; ingridUnlocked = false; ingridSessionPin = ""; ingridVaultSalt = null;
  prompts.push("new-strong-vault-password", "new-strong-vault-password");
  await unlockIngridVault();
  const migrated = JSON.parse(localStorage.getItem(INGRID_VAULT_KEY));
  if (!ingridUnlocked || migrated.encrypted !== true || migrated.version !== 3) throw new Error("legacy plaintext data was not migrated");
  if (ingridData.entries[0].shared !== "private-sentinel-value") throw new Error("legacy migration lost records");
  return "AES-GCM encryption, wrong-password rejection, unlock, and non-destructive legacy migration passed";
})()
`;
const result = await vm.runInNewContext(test, context);
console.log(result);
