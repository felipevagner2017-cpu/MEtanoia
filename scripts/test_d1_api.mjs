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
  const registration = await request("/api/auth/register", "POST", {
    email, password, platform: "pwa", deviceLabel: "CI local test"
  }, null, { "X-Registration-Key": invite });
  if (registration.status !== 201) throw new Error("Register failed: " + JSON.stringify(registration));
  const session = registration.data;
  if (!session.token || !session.deviceId || !session.userId) throw new Error("Registration did not return a complete session.");

  const login = await request("/api/auth/login", "POST", { email, password, platform: "android", deviceLabel: "CI second device" });
  if (login.status !== 200) throw new Error("Login failed: " + JSON.stringify(login));

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

  const revoked = await request("/api/devices/" + session.deviceId, "DELETE", undefined, login.data.token);
  if (revoked.status !== 200) throw new Error("Device revocation failed: " + JSON.stringify(revoked));
  const afterRevoke = await request("/api/sync/pull?since=0", "GET", undefined, session.token);
  if (afterRevoke.status !== 401) throw new Error("Revoked device still authenticated.");

  console.log("D1 Worker integration passed: registration, login, device binding, push, idempotency, conflict, pull, encrypted backup restore, device revocation.");
} finally {
  server.kill("SIGTERM");
  await Promise.race([new Promise(resolve => server.once("exit", resolve)), sleep(5000)]);
}
