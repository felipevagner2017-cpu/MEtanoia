import { spawn, spawnSync } from "node:child_process";
import { setTimeout as sleep } from "node:timers/promises";

const config = "wrangler.test.jsonc";
const persist = "/tmp/metanoia-d1-api-test-state";
const port = 8787;
const base = `http://127.0.0.1:${port}`;
const invite = "local-test-invite-only";
const run = (args) => {
  const result = spawnSync("wrangler", args, { encoding: "utf8", stdio: "inherit" });
  if (result.status !== 0) throw new Error(`wrangler ${args.join(" ")} failed`);
};
run(["d1", "migrations", "apply", "metanoia-local-test", "--local", "--config", config, "--persist-to", persist]);

const server = spawn("wrangler", [
  "dev", "--local", "--port", String(port), "--config", config,
  "--persist-to", persist, "--var", `METANOIA_REGISTRATION_KEY:${invite}`
], { stdio: ["ignore", "pipe", "pipe"] });
let serverOutput = "";
server.stdout.on("data", chunk => { serverOutput += chunk.toString(); });
server.stderr.on("data", chunk => { serverOutput += chunk.toString(); });

async function request(path, method = "GET", body, token, headers = {}) {
  const response = await fetch(base + path, {
    method,
    headers: {
      ...(body === undefined ? {} : { "Content-Type": "application/json" }),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...headers
    },
    ...(body === undefined ? {} : { body: JSON.stringify(body) })
  });
  const data = await response.json().catch(() => ({}));
  return { status: response.status, data };
}

try {
  let ready = false;
  for (let i = 0; i < 60; i++) {
    try { const response = await fetch(base + "/api/auth/login", { method: "POST" }); if (response.status) { ready = true; break; } }
    catch {}
    if (server.exitCode !== null) break;
    await sleep(1000);
  }
  if (!ready) throw new Error("Local Worker did not become ready. " + serverOutput.slice(-4000));

  const email = "sync-test@example.invalid";
  const password = "Metanoia-local-test-password-2026";
  const stableDeviceId = "4d5f7a10-2b3c-4d5e-8f90-1234567890ab";
  const registration = await request("/api/auth/register", "POST", {
    email, password, platform: "pwa", deviceLabel: "CI local test", deviceId: stableDeviceId
  }, null, { "X-Registration-Key": invite });
  if (registration.status !== 201) throw new Error("Register failed: " + JSON.stringify(registration));
  const session = registration.data;
  if (!session.token || !session.deviceId || !session.userId) throw new Error("Registration did not return a complete session.");

  const stableLogin = await request("/api/auth/login", "POST", {
    email, password, platform: "android", deviceLabel: "CI same installation", deviceId: stableDeviceId
  });
  if (stableLogin.status !== 200 || stableLogin.data.deviceId !== stableDeviceId)
    throw new Error("Stable installation identity was not reused: " + JSON.stringify(stableLogin));
  const login = await request("/api/auth/login", "POST", {
    email, password, platform: "android", deviceLabel: "CI legacy second device"
  });
  if (login.status !== 200 || login.data.deviceId === stableDeviceId)
    throw new Error("Legacy login without deviceId failed: " + JSON.stringify(login));

  const otherRegistration = await request("/api/auth/register", "POST", {
    email: "isolated-account@example.invalid", password: "Another-local-test-password-2026",
    platform: "pwa", deviceLabel: "Isolation test"
  }, null, { "X-Registration-Key": invite });
  if (otherRegistration.status !== 201) throw new Error("Second account registration failed.");
  const crossAccountClaim = await request("/api/auth/login", "POST", {
    email: "isolated-account@example.invalid", password: "Another-local-test-password-2026",
    platform: "android", deviceId: stableDeviceId
  });
  if (crossAccountClaim.status !== 409)
    throw new Error("A different account claimed an existing installation identity: " + JSON.stringify(crossAccountClaim));

  const keyEnvelope = {
    format: "metanoia-key-envelope", version: 1, kdf: "PBKDF2-SHA256", iterations: 600000,
    salt: "AAECAwQFBgcICQoLDA0ODw==", nonce: "EBESExQVFhcYGRob",
    ciphertext: "TEkPBZsPEhWxEfDk4jt+tZq4dFKFTmjZ3tR4rQC4+L/+p0cDJNMM9ealgfi2H14P"
  };
  const envelopeSaved = await request("/api/crypto/key-envelope", "POST", { envelope: keyEnvelope }, session.token);
  if (envelopeSaved.status !== 201) throw new Error("Encrypted account key envelope save failed: " + JSON.stringify(envelopeSaved));
  const envelopeFromSecondDevice = await request("/api/crypto/key-envelope", "GET", undefined, login.data.token);
  if (envelopeFromSecondDevice.status !== 200 || envelopeFromSecondDevice.data.envelope?.ciphertext !== keyEnvelope.ciphertext)
    throw new Error("Account key envelope was not available to the second authorized device.");
  const duplicateEnvelope = await request("/api/crypto/key-envelope", "POST", { envelope: keyEnvelope }, login.data.token);
  if (duplicateEnvelope.status !== 409) throw new Error("Existing account key envelope was overwritten.");
  const isolatedEnvelope = await request("/api/crypto/key-envelope", "GET", undefined, otherRegistration.data.token);
  if (isolatedEnvelope.status !== 200 || isolatedEnvelope.data.envelope !== null)
    throw new Error("Account key envelope leaked across accounts.");

  const operation = {
    operationId: "operation-local-0001",
    recordId: "record-local-0001",
    operation: "upsert",
    dimension: "DAILY",
    recordType: "journal",
    payloadCiphertext: "ciphertext-local-test",
    baseRevision: 0,
    deviceId: session.deviceId,
    clientUpdatedAt: Date.now()
  };
  const pushed = await request("/api/sync/push", "POST", { operations: [operation] }, session.token);
  if (pushed.status !== 200 || pushed.data.results?.[0]?.status !== "applied" || pushed.data.results[0].revision !== 1)
    throw new Error("Initial sync push failed: " + JSON.stringify(pushed));

  const duplicate = await request("/api/sync/push", "POST", { operations: [operation] }, session.token);
  if (duplicate.data.results?.[0]?.duplicate !== true) throw new Error("Idempotency check failed: " + JSON.stringify(duplicate));

  const stale = await request("/api/sync/push", "POST", { operations: [{ ...operation, operationId: "operation-local-stale", baseRevision: 0 }] }, session.token);
  if (stale.data.results?.[0]?.status !== "conflict" || stale.data.results[0].currentRevision !== 1)
    throw new Error("Optimistic conflict check failed: " + JSON.stringify(stale));

  const pulled = await request("/api/sync/pull?since=0", "GET", undefined, session.token);
  if (pulled.status !== 200 || pulled.data.records?.length !== 1 || pulled.data.records[0].record_id !== operation.recordId)
    throw new Error("Sync pull failed: " + JSON.stringify(pulled));

  const backup = await request("/api/backups/create", "POST", {
    deviceId: session.deviceId, payloadCiphertext: "encrypted-backup-test", recordCount: 1, formatVersion: 1
  }, session.token);
  if (backup.status !== 201) throw new Error("Backup creation failed: " + JSON.stringify(backup));
  const restore = await request("/api/backups/" + backup.data.id, "GET", undefined, session.token);
  if (restore.status !== 200 || restore.data.backup?.payload_ciphertext !== "encrypted-backup-test")
    throw new Error("Backup integrity/restore check failed: " + JSON.stringify(restore));

  const devices = await request("/api/devices/list", "GET", undefined, session.token);
  if (devices.status !== 200 || devices.data.devices?.length !== 2)
    throw new Error("Device inventory failed: " + JSON.stringify(devices));

  const revoked = await request("/api/devices/" + stableDeviceId, "DELETE", undefined, login.data.token);
  if (revoked.status !== 200) throw new Error("Device revocation failed: " + JSON.stringify(revoked));
  const afterRevoke = await request("/api/sync/pull?since=0", "GET", undefined, stableLogin.data.token);
  if (afterRevoke.status !== 401) throw new Error("Revoked device still authenticated.");
  const reloginRevoked = await request("/api/auth/login", "POST", {
    email, password, platform: "android", deviceId: stableDeviceId
  });
  if (reloginRevoked.status !== 403)
    throw new Error("A revoked installation identity was allowed to log in again: " + JSON.stringify(reloginRevoked));

  console.log("D1 Worker integration passed: registration, login, device binding, account-key envelope isolation, push, idempotency, conflict, pull, encrypted backup restore, device revocation.");
} finally {
  server.kill("SIGTERM");
  await Promise.race([new Promise(resolve => server.once("exit", resolve)), sleep(5000)]);
}
