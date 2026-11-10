#!/usr/bin/env node
// Read-only deployment gate: refuse to publish code whose D1 migrations are not already applied.
import fs from "node:fs";
import path from "node:path";

const token = process.env.CLOUDFLARE_API_TOKEN || "";
const accountId = process.env.CLOUDFLARE_ACCOUNT_ID || "";
if (!token || !accountId) throw new Error("CLOUDFLARE_API_TOKEN and CLOUDFLARE_ACCOUNT_ID are required for the read-only migration gate.");

const configPath = path.join(process.cwd(), "wrangler.jsonc");
const config = JSON.parse(fs.readFileSync(configPath, "utf8"));
const binding = (config.d1_databases || []).filter((entry) => entry.binding === "DB");
if (binding.length !== 1 || !binding[0].database_id) throw new Error("A single configured D1 binding DB is required.");

const local = fs.readdirSync(path.join(process.cwd(), "migrations"))
  .filter((name) => /^\d+_.+\.sql$/.test(name)).sort();
const url = `https://api.cloudflare.com/client/v4/accounts/${accountId}/d1/database/${binding[0].database_id}/query`;
const response = await fetch(url, {
  method: "POST",
  headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
  body: JSON.stringify({ sql: "SELECT name FROM d1_migrations ORDER BY id" })
});
const body = await response.json().catch(() => ({}));
if (!response.ok || body.success === false) {
  const detail = (body.errors || []).map((item) => item.message).filter(Boolean).join("; ");
  throw new Error(`Unable to verify remote D1 migration history (HTTP ${response.status}): ${detail || "Cloudflare API error"}`);
}
const applied = (body.result?.[0]?.results || []).map((row) => row.name);
const pending = local.filter((name) => !applied.includes(name));
const unknown = applied.filter((name) => !local.includes(name));
if (unknown.length) throw new Error("Remote D1 has migrations absent from this source branch: " + unknown.join(", "));
if (pending.length) throw new Error("Production deployment blocked. Apply and verify these reviewed D1 migrations first: " + pending.join(", "));
console.log(`Remote D1 migration gate passed: ${applied.length} migration(s) applied; no pending migrations.`);
