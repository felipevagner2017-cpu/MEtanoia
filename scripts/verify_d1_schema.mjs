#!/usr/bin/env node
// Read-only post-migration schema verification for Cloudflare D1.
const accountId = process.env.CLOUDFLARE_ACCOUNT_ID || "69cf119d00a74c4fe5cd6960d2d9e5f9";
const token = process.env.CLOUDFLARE_API_TOKEN || "";
const databaseId = "1af20f6e-bb1c-4f8f-b9e4-d71ca09279c0";
const databaseName = "metanoia";
const api = "https://api.cloudflare.com/client/v4/accounts/" + accountId + "/d1/database/" + databaseId;
const checks = [];
const add = (name, ok, detail) => checks.push({ name, ok, detail });
async function request(url, options = {}) {
  const response = await fetch(url, {
    ...options,
    headers: { Authorization: "Bearer " + token, ...(options.headers || {}) }
  });
  const payload = await response.json().catch(() => ({}));
  if (!response.ok || payload.success === false) {
    const message = (payload.errors || []).map(e => e.message).filter(Boolean).join("; ");
    throw new Error("Cloudflare API HTTP " + response.status + (message ? ": " + message : ""));
  }
  return payload;
}
async function query(sql) {
  const result = await request(api + "/query", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ sql })
  });
  return result.result?.[0]?.results || [];
}
if (!token) throw new Error("CLOUDFLARE_API_TOKEN ausente.");
try {
  const info = await request(api);
  const db = info.result || {};
  const identityOk = db.uuid === databaseId && db.name === databaseName;
  add("Identidade do D1", identityOk, "name=" + (db.name || "ausente") + "; uuid=" + (db.uuid || "ausente"));
  if (!identityOk) throw new Error("Identidade do D1 não corresponde ao alvo autorizado.");
  const tableRows = await query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name");
  const tables = tableRows.map(x => x.name).filter(x => !x.startsWith("_cf_"));
  const expectedTables = ["users","sessions","devices","records","sync_operations","consents","ai_conversations","ai_messages","ai_usage_daily","backups","account_key_envelopes"];
  const missingTables = expectedTables.filter(x => !tables.includes(x));
  add("Tabelas da aplicação (11)", missingTables.length === 0, "Encontradas: " + tables.join(", ") + (missingTables.length ? "; ausentes: " + missingTables.join(", ") : ""));
  const indexRows = await query("SELECT name FROM sqlite_master WHERE type='index' AND name NOT LIKE 'sqlite_%' ORDER BY name");
  const indexes = indexRows.map(x => x.name).filter(x => !x.startsWith("_cf_"));
  const expectedIndexes = ["idx_sessions_user_expiry","idx_sessions_device_active","idx_devices_user_active","idx_records_user_server_time","idx_records_user_dimension","idx_sync_ops_user_time","idx_consents_pair","idx_ai_conversations_user_updated","idx_ai_messages_conversation_time","idx_backups_user_time"];
  const missingIndexes = expectedIndexes.filter(x => !indexes.includes(x));
  add("Índices da aplicação (10)", missingIndexes.length === 0, "Encontrados: " + indexes.join(", ") + (missingIndexes.length ? "; ausentes: " + missingIndexes.join(", ") : ""));
  const historyExists = tableRows.some(x => x.name === "d1_migrations");
  if (!historyExists) throw new Error("Histórico d1_migrations não encontrado.");
  const appliedRows = await query("SELECT name FROM d1_migrations ORDER BY id");
  const applied = appliedRows.map(x => x.name);
  const expectedMigrations = ["0001_initial.sql","0002_account_key_envelopes.sql"];
  const missingMigrations = expectedMigrations.filter(x => !applied.includes(x));
  const unexpectedMigrations = applied.filter(x => !expectedMigrations.includes(x));
  add("Histórico de migrations", missingMigrations.length === 0 && unexpectedMigrations.length === 0,
    "Aplicadas: " + applied.join(", ") + (missingMigrations.length ? "; pendentes: " + missingMigrations.join(", ") : "") + (unexpectedMigrations.length ? "; não esperadas: " + unexpectedMigrations.join(", ") : ""));
} catch (error) {
  add("Auditoria remota", false, error.message);
}
const lines = [
  "# Auditoria pós-migração — MEtanoia", "",
  "- Banco: " + databaseName + " (" + databaseId + ")",
  "- Escopo: consultas SELECT; nenhuma escrita nesta auditoria.", "",
  "| Verificação | Resultado | Evidência |",
  "|---|---|---|",
  ...checks.map(x => "| " + x.name + " | **" + (x.ok ? "APROVADA" : "REPROVADA") + "** | " + x.detail.replaceAll("|", "\\|") + " |"),
  ""
];
const report = lines.join("\n");
console.log(report);
if (process.env.GITHUB_STEP_SUMMARY) {
  const fs = await import("node:fs");
  fs.appendFileSync(process.env.GITHUB_STEP_SUMMARY, report);
}
const fs = await import("node:fs");
fs.writeFileSync("d1-post-migration-report.md", report);
if (checks.some(x => !x.ok)) process.exitCode = 1;
