# Cloudflare publication — MEtanoia

## Source and scope

This deployment branch is based on `main` and transports only the web/PWA, Worker, D1 schema/migration files, their local integration tests, and Cloudflare workflows from the reviewed development branch `work/phase1-integrated-main` at `a549d50327a3010915b205f91c08a9b5e423c0cb`.

The Android application source and Android workflows are intentionally not copied. The separate Android PR #3 is not merged or modified. The reserved matrimonial image is not integrated.

The Worker name is `metanoia`, matching the requested production hostname `metanoia.restaura-lar.workers.dev`. Static assets are served from `web/`; the Worker entry point is `worker/index.ts`.

## Remote D1 status — provisioned; additive v2 migration pending

The read-only Cloudflare audit on 2026-11-09 confirmed the dedicated remote database `metanoia` (`1af20f6e-bb1c-4f8f-b9e4-d71ca09279c0`), binding `DB`, all 11 application tables, and the original 10 indexes. Migrations `0001_initial.sql` and `0002_account_key_envelopes.sql` are applied. The additive `0003_sync_crypto_metadata.sql` is prepared locally and remains pending remotely; it adds only `source_device_id` and `purpose` to `records`, required to preserve the v2 cryptographic derivation context.

The D1 audit exported the remote database and verified SQLite `integrity_check=ok` and zero foreign-key violations. Direct remote PRAGMA queries were denied by D1 (`SQLITE_AUTH`), so the successful export-based checks are the integrity evidence.

The Worker requires binding `DB` for registration, login, device/session management, encrypted key envelopes, sync, backups, consents, and the authenticated AI endpoint. The configured binding points to the verified dedicated MEtanoia database.

Do not apply migration 0003 remotely until it is separately approved. The deployment workflow must stop when any local migration remains pending; it never applies migrations automatically.
## Workers AI and secrets

The current Worker uses the native Workers AI binding `AI` configured in `wrangler.jsonc`; no separate AI API token is referenced by the current Worker implementation.

Required GitHub Actions secrets for a later approved production run:
- `CLOUDFLARE_API_TOKEN` — token with Workers Scripts edit/deploy and D1 read permissions (D1 write only if separately approved migrations are ever added).
- `CLOUDFLARE_ACCOUNT_ID` — account containing the Worker and the dedicated MEtanoia D1 database.
- `METANOIA_REGISTRATION_KEY` — optional high-entropy invitation secret. It is required only to create new accounts through `POST /api/auth/register`; existing-account login and authenticated sync do not require it. If absent, registration stays closed while login remains available.

Secret presence cannot be inferred from workflow YAML. GitHub does not expose secret values to repository read APIs. The workflow checks them at runtime without printing values.

## Deployment controls

- The production workflow is manual only; it has no push or Android workflow trigger.
- It requires the exact reviewed 40-character commit SHA and the literal confirmation `PUBLICAR`.
- It checks the real D1 binding and required secrets before deployment.
- It runs `wrangler d1 migrations list --remote` (read-only) and stops if configuration or credentials are missing.
- It performs a Worker dry run before deploying the exact requested commit.
- GitHub environment `production` can additionally require a reviewer before the job receives environment secrets.
- The pull-request validation workflow never deploys to production.

## PWA behavior and cache

The source service worker uses a versioned cache key and fetches navigation, `/index.html`, and `/app.js` with `cache: "no-store"`; it refreshes cached critical assets after successful network responses and uses cache only as an offline fallback. The source pins app/crypto asset query versions. A later release should bump the cache key and asset query versions when those files change.

## Validation scope

The PR validation workflow checks JavaScript syntax, manifest metadata and referenced assets, independent Agenda navigation, D1 migration SQL, local D1/Worker integration, and a Wrangler dry-run bundle. These tests do not prove remote D1 provisioning, production credentials, domain routing, or live production behavior.
