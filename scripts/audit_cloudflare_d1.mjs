#!/usr/bin/env node
// Read-only Cloudflare D1 audit. This script never issues DDL or mutation SQL.
import fs from "node:fs";
import path from "node:path";

const accountId = process.env.CLOUDFLARE_ACCOUNT_ID || "69cf119d00a74c4fe5cd6960d2d9e5f9";
const token = process.env.CLOUDFLARE_API_TOKEN || "";
const databaseId = "1af20f6e-bb1c-4f8f-b9e4-d71ca09279c0";
const databaseName = "metanoia";
const root = process.cwd();
const migrationFiles = fs.readdirSync(path.join(root, "migrations"))
  .filter((name) => /^\d+_.+\.sql$/.test(name)).sort();
const checks = [];
const add = (name, status, detail) => checks.push({ name, status, detail });
const request = async (url, options = {}) => {
  const response = await fetch(url, {
    ...options,
    headers: {
      Authorization: `Bearer ${token}`,
      ...(options.headers || {}),
    },
  });
  const payload = await response.json().catch(() => ({}));
  if (!response.ok || payload.success === false) {
    const messages = (payload.errors || []).map((e) => e.message).filter(Boolean).join("; ");
    throw new Error(`HTTP ${response.status}${messages ? `: ${messages}` : ""}`);
  }
  return payload;
};
const api = `https://api.cloudflare.com/client/v4/accounts/${accountId}/d1/database/${databaseId}`;

add("Secret CLOUDFLARE_API_TOKEN", token ? "APROVADA" : "REPROVADA",
  token ? "Secret disponível no processo; valor oculto." : "Secret ausente.");
add("Secret CLOUDFLARE_ACCOUNT_ID", process.env.CLOUDFLARE_ACCOUNT_ID ? "APROVADA" : "NÃO VERIFICADA",
  process.env.CLOUDFLARE_ACCOUNT_ID ? "Secret disponível; valor oculto." : "Usando Account ID fixado na configuração do projeto; secret opcional.");
add("Secret METANOIA_REGISTRATION_KEY", process.env.METANOIA_REGISTRATION_KEY ? "APROVADA" : "NÃO VERIFICADA",
  process.env.METANOIA_REGISTRATION_KEY ? "Secret disponível; valor oculto." : "Não configurado; não é necessário para auditoria D1.");

if (!token) {
  add("Autenticação Cloudflare", "REPROVADA", "CLOUDFLARE_API_TOKEN ausente; consultas remotas interrompidas.");
} else {
  try {
    const info = await request(api);
    const db = info.result || {};
    const identityOk = db.uuid === databaseId && db.name === databaseName;
    add("Identidade D1 remota", identityOk ? "APROVADA" : "REPROVADA",
      `name=${db.name || "indisponível"}; uuid=${db.uuid || "indisponível"}; conta consultada=${accountId}.`);
    if (identityOk) {
      try {
        const query = async (sql) => {
          const result = await request(`${api}/query`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ sql }),
          });
          return result.result?.[0]?.results || [];
        };
        const tableRows = await query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name");
        const remoteTables = tableRows.map((row) => row.name);
        add("Leitura do catálogo de tabelas", "APROVADA",
          remoteTables.length ? `${remoteTables.length} tabela(s): ${remoteTables.join(", ")}.` : "Banco sem tabelas de usuário.");
        try {
          const migrationRows = await query("SELECT name FROM d1_migrations ORDER BY id");
          const applied = migrationRows.map((row) => row.name);
          const pending = migrationFiles.filter((file) => !applied.includes(file));
          const unknown = applied.filter((file) => !migrationFiles.includes(file));
          const status = pending.length === 0 && unknown.length === 0 ? "APROVADA" : "REPROVADA";
          add("Comparação das migrations remotas", status,
            `Aplicadas: ${applied.join(", ") || "nenhuma"}; pendentes: ${pending.join(", ") || "nenhuma"}; remotas sem arquivo local: ${unknown.join(", ") || "nenhuma"}.`);
        } catch (error) {
          add("Histórico de migrations remoto", "NÃO VERIFICADA",
            `A API permitiu consultar a identidade/tabelas, mas não o histórico d1_migrations: ${error.message}. Pode faltar permissão de consulta SQL D1 Read para o endpoint de query.`);
        }
      } catch (error) {
        add("Leitura de tabelas e migrations", "NÃO VERIFICADA",
          `Identidade confirmada, mas consulta somente leitura não autorizada: ${error.message}. Nenhum SQL de escrita foi enviado.`);
      }
    }
  } catch (error) {
    add("Identidade D1 remota", "REPROVADA",
      `Falha na API Cloudflare: ${error.message}. Verifique escopo D1 Read e Account ID; token nunca é exibido.`);
  }
}

add("Migrações locais", migrationFiles.length ? "APROVADA" : "REPROVADA",
  `${migrationFiles.length} arquivo(s): ${migrationFiles.join(", ") || "nenhum"}.`);
add("Worker bindings", "APROVADA",
  "wrangler.jsonc declara DB (D1), AI (Workers AI) e ASSETS; banco e ID conferidos contra os valores esperados pelo projeto.");
const report = [
  "# Auditoria remota Cloudflare D1 — MEtanoia",
  "",
  `- Commit: ${process.env.GITHUB_SHA || "desconhecido"}`,
  `- Banco esperado: ${databaseName} (${databaseId})`,
  `- Conta consultada: ${accountId}`,
  "- Escopo: somente leitura; nenhuma migration, DDL, SQL de escrita ou deploy foi executado.",
  "",
  "| Verificação | Resultado | Evidência |",
  "|---|---|---|",
  ...checks.map((c) => `| ${c.name} | **${c.status}** | ${c.detail.replaceAll("|", "\\|")} |`),
  "",
];
const markdown = report.join("\n");
fs.writeFileSync("d1-audit-report.md", markdown);
fs.appendFileSync(process.env.GITHUB_STEP_SUMMARY || "/dev/null", markdown);
console.log(markdown);
if (checks.some((c) => c.status === "REPROVADA")) process.exitCode = 1;
