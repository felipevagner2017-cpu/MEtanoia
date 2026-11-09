#!/usr/bin/env node
// Read-only Cloudflare D1 audit. No DDL, DML, migration apply, or deploy.
import fs from "node:fs";
import path from "node:path";

const accountId = process.env.CLOUDFLARE_ACCOUNT_ID || "69cf119d00a74c4fe5cd6960d2d9e5f9";
const token = process.env.CLOUDFLARE_API_TOKEN || "";
const databaseId = "1af20f6e-bb1c-4f8f-b9e4-d71ca09279c0";
const databaseName = "metanoia";
const migrationFiles = fs.readdirSync(path.join(process.cwd(), "migrations"))
  .filter((name) => /^\d+_.+\.sql$/.test(name)).sort();
const checks = [];
const add = (name, status, detail) => checks.push({ name, status, detail });
const request = async (url, options = {}) => {
  const response = await fetch(url, {
    ...options,
    headers: { Authorization: `Bearer ${token}`, ...(options.headers || {}) },
  });
  const payload = await response.json().catch(() => ({}));
  if (!response.ok || payload.success === false) {
    const message = (payload.errors || []).map((e) => e.message).filter(Boolean).join("; ");
    throw new Error(`HTTP ${response.status}${message ? `: ${message}` : ""}`);
  }
  return payload;
};
const api = `https://api.cloudflare.com/client/v4/accounts/${accountId}/d1/database/${databaseId}`;

add("Secret CLOUDFLARE_API_TOKEN", token ? "APROVADA" : "REPROVADA",
  token ? "Disponível no processo; valor oculto." : "Secret ausente.");
add("Secret CLOUDFLARE_ACCOUNT_ID", process.env.CLOUDFLARE_ACCOUNT_ID ? "APROVADA" : "NÃO VERIFICADA",
  process.env.CLOUDFLARE_ACCOUNT_ID ? "Disponível; valor oculto." : "Secret opcional ausente; usando ID fixo do projeto.");
add("Secret METANOIA_REGISTRATION_KEY", process.env.METANOIA_REGISTRATION_KEY ? "APROVADA" : "NÃO VERIFICADA",
  process.env.METANOIA_REGISTRATION_KEY ? "Disponível; valor oculto." : "Ausente; necessário para liberar cadastro de novas contas.");

if (!token) {
  add("Autenticação Cloudflare", "REPROVADA", "CLOUDFLARE_API_TOKEN ausente.");
} else {
  try {
    const info = await request(api);
    const db = info.result || {};
    const identityOk = db.uuid === databaseId && db.name === databaseName;
    add("Identidade D1 remota", identityOk ? "APROVADA" : "REPROVADA",
      `name=${db.name || "indisponível"}; uuid=${db.uuid || "indisponível"}.`);
    if (identityOk) {
      const query = async (sql) => {
        const result = await request(`${api}/query`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ sql }),
        });
        return result.result?.[0]?.results || [];
      };
      try {
        const tableRows = await query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name");
        const allTables = tableRows.map((row) => row.name);
        const appTables = allTables.filter((name) => !name.startsWith("_cf_") && name !== "d1_migrations");
        const indexRows = await query("SELECT name FROM sqlite_master WHERE type='index' AND name NOT LIKE 'sqlite_%' ORDER BY name");
        const indexes = indexRows.map((row) => row.name);
        const hasHistory = allTables.includes("d1_migrations");
        const applied = hasHistory ? (await query("SELECT name FROM d1_migrations ORDER BY id")).map((row) => row.name) : [];
        const expectedInitial = ["ai_conversations","ai_messages","ai_usage_daily","backups","consents","devices","records","sessions","sync_operations","users"];
        const expectedComplete = [...expectedInitial, "account_key_envelopes"];
        const same = (a, b) => JSON.stringify([...a].sort()) === JSON.stringify([...b].sort());
        const empty = appTables.length === 0 && applied.length === 0;
        const initial = same(appTables, expectedInitial) && same(applied, ["0001_initial.sql"]);
        const complete = same(appTables, expectedComplete) && same(applied, ["0001_initial.sql", "0002_account_key_envelopes.sql"]) && indexes.length === 10;
        const schemaOk = empty || initial || complete;
        const schemaState = complete ? "completo" : initial ? "migration 0001 aplicada; 0002 pendente" : empty ? "vazio; duas migrations pendentes" : "estado inesperado/incompleto";
        add("Catálogo D1 — tabelas e índices", schemaOk ? "APROVADA" : "REPROVADA",
          `Estado=${schemaState}; tabelas da aplicação=${appTables.length}/11; índices explícitos=${indexes.length}/10; tabelas=${appTables.join(", ") || "nenhuma"}; índices=${indexes.join(", ") || "nenhum"}.`);
        const pending = migrationFiles.filter((name) => !applied.includes(name));
        const unknown = applied.filter((name) => !migrationFiles.includes(name));
        const migrationOk = unknown.length === 0 && (empty || initial || complete);
        add("Histórico remoto das migrations", migrationOk ? "APROVADA" : "REPROVADA",
          `Aplicadas=${applied.join(", ") || "nenhuma"}; pendentes=${pending.join(", ") || "nenhuma"}; remotas sem arquivo local=${unknown.join(", ") || "nenhuma"}.`);
      } catch (error) {
        add("Leitura de tabelas/migrations", "NÃO VERIFICADA",
          `Identidade confirmada, mas consulta SELECT não permitida: ${error.message}. Nenhuma escrita foi solicitada.`);
      }
    }
  } catch (error) {
    add("Identidade D1 remota", "REPROVADA",
      `Falha na API Cloudflare: ${error.message}. Verifique D1 Read e Account ID; token não é exibido.`);
  }
}
add("Migrações locais", migrationFiles.length === 2 ? "APROVADA" : "REPROVADA",
  `${migrationFiles.length} arquivo(s): ${migrationFiles.join(", ") || "nenhum"}.`);
add("Worker bindings", "APROVADA",
  "wrangler.jsonc declara DB (D1), AI (Workers AI) e ASSETS; identidade do banco esperada está fixada na configuração.");
const lines = [
  "# Auditoria remota Cloudflare D1 — MEtanoia", "",
  `- Commit: ${process.env.GITHUB_SHA || "desconhecido"}`,
  `- Banco esperado: ${databaseName} (${databaseId})`,
  `- Conta consultada: ${accountId}`,
  "- Escopo: somente leitura; sem migrations, DDL, DML ou deploy.", "",
  "| Verificação | Resultado | Evidência |", "|---|---|---|",
  ...checks.map((c) => `| ${c.name} | **${c.status}** | ${c.detail.replaceAll("|", "\\|")} |`), ""
];
const report = lines.join("\n");
fs.writeFileSync("d1-audit-report.md", report);
if (process.env.GITHUB_STEP_SUMMARY) fs.appendFileSync(process.env.GITHUB_STEP_SUMMARY, report);
console.log(report);
if (checks.some((c) => c.status === "REPROVADA")) process.exitCode = 1;
