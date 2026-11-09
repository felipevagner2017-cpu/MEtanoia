# Cloudflare publication — MEtanoia

## Source and scope

This deployment branch is based on `main` and transports only the web/PWA, Worker, D1 schema/migration files, their local integration tests, and Cloudflare workflows from the reviewed development branch `work/phase1-integrated-main` at `a549d50327a3010915b205f91c08a9b5e423c0cb`.

The Android application source and Android workflows are intentionally not copied. The separate Android PR #3 is not merged or modified. The reserved matrimonial image is not integrated.

The Worker name is `metanoia`, matching the requested production hostname `metanoia.restaura-lar.workers.dev`. Static assets are served from `web/`; the Worker entry point is `worker/index.ts`.

## Current production blocker — D1 is not provisioned

The source documentation and configuration audit indicate that the remote MEtanoia D1 database has not yet been provisioned or bound. The Worker requires binding `DB` for registration, login, device/session management, encrypted key envelopes, sync, backups, consents, and the authenticated AI endpoint. Without it, those routes return HTTP 503. The local test database is not a production database and its ID must never be copied to production.

Do not deploy this Worker until the dedicated MEtanoia D1 database exists, its real database ID and name are added to `wrangler.jsonc` as binding `DB`, and the remote migration status has been reviewed. No remote database was created, changed, or migrated by this PR.

The required schema files are `migrations/0001_initial.sql` and `migrations/0002_account_key_envelopes.sql`. The deployment workflow only lists remote migration status; it deliberately does not apply migrations. Applying them requires a separate explicit approval after reviewing the target database identity and migration plan.

## Workers AI and secrets

The current Worker uses the native Workers AI binding `AI` configured in `wrangler.jsonc`; no separate AI API token is referenced by the current Worker implementation.

Required GitHub Actions secrets for a later approved production run:
- `CLOUDFLARE_API_TOKEN` — token with Workers Scripts edit/deploy and D1 read permissions (D1 write only if separately approved migrations are ever added).
- `CLOUDFLARE_ACCOUNT_ID` — account containing the Worker and the dedicated MEtanoia D1 database.
- `METANOIA_REGISTRATION_KEY` — high-entropy invitation secret; the workflow installs it as a Worker secret only after explicit production confirmation.

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
