export interface D1Env {
  DB?: D1Database;
  METANOIA_REGISTRATION_KEY?: string;
}

const json = (data: unknown, status = 200, origin?: string) => {
  const headers = new Headers({
    "Content-Type": "application/json; charset=utf-8",
    "Cache-Control": "no-store",
    "X-Content-Type-Options": "nosniff",
    "Vary": "Origin",
  });
  if (origin) {
    headers.set("Access-Control-Allow-Origin", origin);
    headers.set("Access-Control-Allow-Headers", "Authorization, Content-Type, X-Registration-Key");
    headers.set("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
    headers.set("Access-Control-Max-Age", "86400");
  }
  return new Response(JSON.stringify(data), { status, headers });
};

const b64 = (bytes: Uint8Array) => btoa(String.fromCharCode(...bytes));
const unb64 = (value: string) => Uint8Array.from(atob(value), c => c.charCodeAt(0));
const randomToken = (size = 32) => {
  const bytes = crypto.getRandomValues(new Uint8Array(size));
  return b64(bytes).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
};
const sha256 = async (value: string) =>
  b64(new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value))));
const passwordHash = async (password: string, salt: Uint8Array) => {
  const material = await crypto.subtle.importKey("raw", new TextEncoder().encode(password), "PBKDF2", false, ["deriveBits"]);
  const bits = await crypto.subtle.deriveBits({ name: "PBKDF2", hash: "SHA-256", salt, iterations: 310000 }, material, 256);
  return b64(new Uint8Array(bits));
};
const parseBody = async (request: Request, max = 30000) => {
  const raw = await request.text();
  if (raw.length > max) throw new Error("PAYLOAD_TOO_LARGE");
  return JSON.parse(raw);
};

export async function authenticate(request: Request, db: D1Database) {
  const token = (request.headers.get("Authorization") || "").replace(/^Bearer\s+/i, "");
  if (!token || token.length > 256) return null;
  const tokenHash = await sha256(token);
  const row = await db.prepare(
    "SELECT s.id AS session_id, s.user_id, s.device_id, s.expires_at, u.status FROM sessions s JOIN users u ON u.id=s.user_id JOIN devices d ON d.id=s.device_id AND d.user_id=s.user_id AND d.revoked_at IS NULL WHERE s.token_hash=? AND s.revoked_at IS NULL LIMIT 1"
  ).bind(tokenHash).first<{ session_id: string; user_id: string; device_id: string | null; expires_at: number; status: string }>();
  if (!row || row.status !== "active" || row.expires_at <= Date.now()) return null;
  return row;
}

export async function handleD1Api(request: Request, env: D1Env, url: URL, origin?: string): Promise<Response | null> {
  if (!url.pathname.startsWith("/api/auth/") && !url.pathname.startsWith("/api/sync/") && !url.pathname.startsWith("/api/devices/") && !url.pathname.startsWith("/api/backups/") && !url.pathname.startsWith("/api/consents/") && !url.pathname.startsWith("/api/crypto/")) return null;
  if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: {
    ...(origin ? { "Access-Control-Allow-Origin": origin } : {}),
    "Access-Control-Allow-Headers": "Authorization, Content-Type, X-Registration-Key",
    "Access-Control-Allow-Methods": "GET, POST, DELETE, OPTIONS",
    "Access-Control-Max-Age": "86400", Vary: "Origin",
  }});
  if (!env.DB) return json({ error: "Banco central ainda não provisionado. Nenhum dado foi alterado." }, 503, origin);
  const db = env.DB;

  try {
    if (url.pathname === "/api/auth/register" && request.method === "POST") {
      if (!env.METANOIA_REGISTRATION_KEY || request.headers.get("X-Registration-Key") !== env.METANOIA_REGISTRATION_KEY)
        return json({ error: "Cadastro protegido: convite inválido ou não configurado." }, 403, origin);
      const body = await parseBody(request, 8000);
      const email = String(body.email || "").trim().toLowerCase();
      const password = String(body.password || "");
      const platform = ["android", "pwa", "web"].includes(body.platform) ? body.platform : "web";
      const label = String(body.deviceLabel || platform).slice(0, 80);
      if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) || email.length > 254 || password.length < 12 || password.length > 256)
        return json({ error: "Informe e-mail válido e senha com pelo menos 12 caracteres." }, 400, origin);
      if (body.deviceId !== undefined && (typeof body.deviceId !== "string" ||
          !/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(body.deviceId)))
        return json({ error: "Identidade de dispositivo inválida." }, 400, origin);
      const id = crypto.randomUUID(), deviceId = body.deviceId || crypto.randomUUID(), sessionId = crypto.randomUUID();
      const salt = crypto.getRandomValues(new Uint8Array(16));
      const hash = await passwordHash(password, salt);
      const token = randomToken(), now = Date.now(), expires = now + 30 * 86400000;
      try {
        await db.batch([
          db.prepare("INSERT INTO users(id,email_normalized,password_salt_b64,password_hash_b64,password_kdf,status,created_at,updated_at) VALUES(?,?,?,?,?,'active',?,?)").bind(id,email,b64(salt),hash,"PBKDF2-SHA256-310000",now,now),
          db.prepare("INSERT INTO devices(id,user_id,platform,label,last_seen_at) VALUES(?,?,?,?,?)").bind(deviceId,id,platform,label,now),
          db.prepare("INSERT INTO sessions(id,user_id,device_id,token_hash,created_at,expires_at) VALUES(?,?,?,?,?,?)").bind(sessionId,id,deviceId,await sha256(token),now,expires),
        ]);
      } catch {
        return json({ error: "Não foi possível criar a conta. Verifique se o e-mail já está cadastrado." }, 409, origin);
      }
      return json({ token, expiresAt: expires, userId: id, deviceId }, 201, origin);
    }

    if (url.pathname === "/api/auth/login" && request.method === "POST") {
      const body = await parseBody(request, 8000);
      const email = String(body.email || "").trim().toLowerCase();
      const password = String(body.password || "");
      const row = await db.prepare("SELECT id,password_salt_b64,password_hash_b64,status FROM users WHERE email_normalized=? LIMIT 1")
        .bind(email).first<{id:string;password_salt_b64:string;password_hash_b64:string;status:string}>();
      if (!row || row.status !== "active" || password.length > 256) return json({ error: "Credenciais inválidas." }, 401, origin);
      const candidate = await passwordHash(password, unb64(row.password_salt_b64));
      if (candidate !== row.password_hash_b64) return json({ error: "Credenciais inválidas." }, 401, origin);
      if (body.deviceId !== undefined && (typeof body.deviceId !== "string" ||
          !/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(body.deviceId)))
        return json({ error: "Identidade de dispositivo inválida." }, 400, origin);
      const token = randomToken(), sessionId = crypto.randomUUID(), deviceId = body.deviceId || crypto.randomUUID();
      const now = Date.now(), expires = now + 30 * 86400000;
      const platform = ["android","pwa","web"].includes(body.platform) ? body.platform : "web";
      const label = String(body.deviceLabel || platform).slice(0,80);
      const existingDevice = await db.prepare("SELECT user_id,revoked_at FROM devices WHERE id=? LIMIT 1")
        .bind(deviceId).first<{user_id:string;revoked_at:number|null}>();
      if (existingDevice && existingDevice.user_id !== row.id)
        return json({ error: "Esta identidade já pertence a outra conta." }, 409, origin);
      if (existingDevice?.revoked_at)
        return json({ error: "Este dispositivo foi revogado e não pode iniciar uma nova sessão." }, 403, origin);
      const statements = existingDevice
        ? [
            db.prepare("UPDATE devices SET platform=?,label=?,last_seen_at=? WHERE id=? AND user_id=? AND revoked_at IS NULL")
              .bind(platform,label,now,deviceId,row.id),
            db.prepare("INSERT INTO sessions(id,user_id,device_id,token_hash,created_at,expires_at) VALUES(?,?,?,?,?,?)")
              .bind(sessionId,row.id,deviceId,await sha256(token),now,expires)
          ]
        : [
            db.prepare("INSERT INTO devices(id,user_id,platform,label,last_seen_at) VALUES(?,?,?,?,?)")
              .bind(deviceId,row.id,platform,label,now),
            db.prepare("INSERT INTO sessions(id,user_id,device_id,token_hash,created_at,expires_at) VALUES(?,?,?,?,?,?)")
              .bind(sessionId,row.id,deviceId,await sha256(token),now,expires)
          ];
      try {
        await db.batch(statements);
      } catch {
        return json({ error: "Não foi possível registrar o dispositivo. Verifique a identidade e tente novamente." }, 409, origin);
      }
      return json({ token, expiresAt: expires, userId: row.id, deviceId }, 200, origin);
    }

    const session = await authenticate(request, db);
    if (!session) return json({ error: "Sessão inválida ou expirada." }, 401, origin);
    if (session.device_id) await db.prepare("UPDATE devices SET last_seen_at=? WHERE id=? AND user_id=? AND revoked_at IS NULL").bind(Date.now(),session.device_id,session.user_id).run();

    if (url.pathname === "/api/auth/logout" && request.method === "POST") {
      await db.prepare("UPDATE sessions SET revoked_at=? WHERE id=?").bind(Date.now(),session.session_id).run();
      return json({ ok: true }, 200, origin);
    }

    if (url.pathname === "/api/devices/list" && request.method === "GET") {
      const result = await db.prepare("SELECT id,platform,label,last_seen_at,revoked_at FROM devices WHERE user_id=? ORDER BY last_seen_at DESC")
        .bind(session.user_id).all();
      return json({ devices: result.results }, 200, origin);
    }

    if (url.pathname.startsWith("/api/devices/") && request.method === "DELETE") {
      const deviceId = url.pathname.split("/").pop() || "";
      if (!deviceId) return json({ error: "Dispositivo inválido." }, 400, origin);
      await db.batch([
        db.prepare("UPDATE devices SET revoked_at=? WHERE id=? AND user_id=?").bind(Date.now(),deviceId,session.user_id),
        db.prepare("UPDATE sessions SET revoked_at=? WHERE user_id=? AND device_id=? AND revoked_at IS NULL").bind(Date.now(),session.user_id,deviceId),
      ]);
      return json({ ok: true, note: "Dispositivo revogado; sessões ativas devem ser encerradas no próximo ciclo." }, 200, origin);
    }

    if (url.pathname === "/api/crypto/key-envelope" && request.method === "GET") {
      const row = await db.prepare(
        "SELECT envelope_json,envelope_version,created_at,updated_at FROM account_key_envelopes WHERE user_id=? LIMIT 1"
      ).bind(session.user_id).first<{envelope_json:string;envelope_version:number;created_at:number;updated_at:number}>();
      if (!row) return json({ envelope: null }, 200, origin);
      return json({ envelope: JSON.parse(row.envelope_json), version: row.envelope_version, createdAt: row.created_at, updatedAt: row.updated_at }, 200, origin);
    }

    if (url.pathname === "/api/crypto/key-envelope" && request.method === "POST") {
      const body = await parseBody(request, 4000);
      const envelope = body.envelope;
      if (!session.device_id || !envelope || envelope.format !== "metanoia-key-envelope" ||
          envelope.version !== 1 || envelope.kdf !== "PBKDF2-SHA256" ||
          !Number.isInteger(envelope.iterations) || envelope.iterations < 600000 || envelope.iterations > 2000000 ||
          typeof envelope.salt !== "string" || typeof envelope.nonce !== "string" || typeof envelope.ciphertext !== "string") {
        return json({ error: "Envelope de chave inválido." }, 400, origin);
      }
      try {
        if (unb64(envelope.salt).length !== 16 || unb64(envelope.nonce).length !== 12 || unb64(envelope.ciphertext).length !== 48)
          return json({ error: "Tamanhos criptográficos inválidos." }, 400, origin);
      } catch {
        return json({ error: "Codificação Base64 inválida." }, 400, origin);
      }
      const device = await db.prepare("SELECT id FROM devices WHERE id=? AND user_id=? AND revoked_at IS NULL")
        .bind(session.device_id, session.user_id).first();
      if (!device) return json({ error: "Dispositivo não autorizado." }, 403, origin);
      const now = Date.now();
      try {
        await db.prepare(
          "INSERT INTO account_key_envelopes(user_id,envelope_json,envelope_version,created_by_device_id,updated_by_device_id,created_at,updated_at) VALUES(?,?,1,?,?,?,?)"
        ).bind(session.user_id, JSON.stringify(envelope), session.device_id, session.device_id, now, now).run();
      } catch {
        return json({ error: "A chave da conta já foi inicializada. A substituição exige o fluxo explícito de rotação." }, 409, origin);
      }
      return json({ ok: true, version: 1, createdAt: now }, 201, origin);
    }

    if (url.pathname === "/api/backups/list" && request.method === "GET") {
      const result = await db.prepare("SELECT id,format_version,sha256,record_count,created_at FROM backups WHERE user_id=? ORDER BY created_at DESC LIMIT 30")
        .bind(session.user_id).all();
      return json({ backups: result.results }, 200, origin);
    }

    if (url.pathname === "/api/backups/create" && request.method === "POST") {
      const body = await parseBody(request, 7000000);
      const deviceId = String(body.deviceId || "");
      const payload = String(body.payloadCiphertext || "");
      const recordCount = Math.max(0, Number(body.recordCount || 0));
      const formatVersion = Math.max(1, Number(body.formatVersion || 1));
      if (!deviceId || !payload || payload.length > 6500000 || recordCount > 100000 || !Number.isSafeInteger(recordCount))
        return json({ error: "Backup inválido ou acima do limite de tamanho." }, 400, origin);
      const device = await db.prepare("SELECT id FROM devices WHERE id=? AND user_id=? AND revoked_at IS NULL").bind(deviceId,session.user_id).first();
      if (!device || deviceId !== session.device_id) return json({ error: "Backup deve ser criado pelo dispositivo autenticado." }, 403, origin);
      const id = crypto.randomUUID(), digest = await sha256(payload), now = Date.now();
      await db.prepare("INSERT INTO backups(id,user_id,device_id,format_version,payload_ciphertext,sha256,record_count,created_at) VALUES(?,?,?,?,?,?,?,?)")
        .bind(id,session.user_id,deviceId,formatVersion,payload,digest,recordCount,now).run();
      return json({ id, sha256: digest, recordCount, createdAt: now }, 201, origin);
    }

    if (url.pathname.startsWith("/api/backups/") && request.method === "GET" && url.pathname !== "/api/backups/list") {
      const id = url.pathname.split("/").pop() || "";
      const backup = await db.prepare("SELECT id,format_version,payload_ciphertext,sha256,record_count,created_at FROM backups WHERE id=? AND user_id=?")
        .bind(id,session.user_id).first<{id:string;format_version:number;payload_ciphertext:string;sha256:string;record_count:number;created_at:number}>();
      if (!backup) return json({ error: "Backup não encontrado." }, 404, origin);
      if (await sha256(backup.payload_ciphertext) !== backup.sha256) return json({ error: "Integridade do backup inválida." }, 409, origin);
      return json({ backup }, 200, origin);
    }

    if (url.pathname.startsWith("/api/backups/") && request.method === "DELETE") {
      const id = url.pathname.split("/").pop() || "";
      await db.prepare("DELETE FROM backups WHERE id=? AND user_id=?").bind(id,session.user_id).run();
      return json({ ok: true }, 200, origin);
    }

    if (url.pathname === "/api/consents/list" && request.method === "GET") {
      const result = await db.prepare("SELECT id,owner_user_id,participant_user_id,purpose,scope,granted_at,revoked_at FROM consents WHERE owner_user_id=? OR participant_user_id=? ORDER BY granted_at DESC")
        .bind(session.user_id,session.user_id).all();
      return json({ consents: result.results }, 200, origin);
    }

    if (url.pathname === "/api/consents/grant" && request.method === "POST") {
      const body = await parseBody(request, 8000);
      const ownerId = String(body.ownerUserId || "");
      const purpose = String(body.purpose || "").slice(0,80);
      const scope = String(body.scope || "").slice(0,1000);
      const evidence = String(body.evidenceCiphertext || "");
      if (!ownerId || ownerId === session.user_id || !purpose || !scope || !evidence || evidence.length > 5000)
        return json({ error: "Consentimento incompleto ou inválido." }, 400, origin);
      const owner = await db.prepare("SELECT id FROM users WHERE id=? AND status='active'").bind(ownerId).first();
      if (!owner) return json({ error: "Conta proprietária não encontrada." }, 404, origin);
      const id = crypto.randomUUID(), now = Date.now();
      await db.prepare("INSERT INTO consents(id,owner_user_id,participant_user_id,purpose,scope,granted_at,evidence_ciphertext) VALUES(?,?,?,?,?,?,?)")
        .bind(id,ownerId,session.user_id,purpose,scope,now,evidence).run();
      return json({ id, grantedAt: now }, 201, origin);
    }

    if (url.pathname === "/api/consents/revoke" && request.method === "POST") {
      const body = await parseBody(request, 2000);
      const id = String(body.id || "");
      if (!id) return json({ error: "Consentimento inválido." }, 400, origin);
      await db.prepare("UPDATE consents SET revoked_at=? WHERE id=? AND participant_user_id=? AND revoked_at IS NULL")
        .bind(Date.now(),id,session.user_id).run();
      return json({ ok: true }, 200, origin);
    }

    if (url.pathname === "/api/sync/pull" && request.method === "GET") {
      const since = Math.max(0, Number(url.searchParams.get("since") || 0));
      const afterId = String(url.searchParams.get("afterId") || "");
      const result = await db.prepare("SELECT r.record_id,r.dimension,r.record_type,r.payload_ciphertext,r.encryption_version,r.revision,r.client_updated_at,r.server_updated_at,r.deleted_at,c.source_device_id,c.purpose FROM records r LEFT JOIN sync_record_context c ON c.user_id=r.user_id AND c.record_id=r.record_id WHERE r.user_id=? AND (r.server_updated_at>? OR (r.server_updated_at=? AND r.record_id>?)) ORDER BY r.server_updated_at,r.record_id LIMIT 501")
        .bind(session.user_id,since,since,afterId).all();
      const records = result.results.slice(0,500);
      const last = records[records.length - 1] as {server_updated_at:number;record_id:string} | undefined;
      const hasMore = result.results.length > 500;
      await db.prepare("UPDATE sessions SET expires_at=? WHERE id=?").bind(Date.now()+30*86400000,session.session_id).run();
      return json({ records, serverTime: Date.now(), hasMore, nextCursor: hasMore && last ? { since: last.server_updated_at, afterId: last.record_id } : null }, 200, origin);
    }

    if (url.pathname === "/api/sync/push" && request.method === "POST") {
      const body = await parseBody(request, 100000);
      if (!Array.isArray(body.operations) || body.operations.length < 1 || body.operations.length > 50)
        return json({ error: "Envie de 1 a 50 operações por lote." }, 400, origin);
      const results = [];
      for (const op of body.operations) {
        const operationId = String(op?.operationId || "");
        const recordId = String(op?.recordId || "");
        const operation = op?.operation === "delete" ? "delete" : op?.operation === "upsert" ? "upsert" : "";
        const dimension = String(op?.dimension || "");
        const payload = String(op?.payloadCiphertext || "");
        const baseRevision = Math.max(0, Number(op?.baseRevision || 0));
        const deviceId = String(op?.deviceId || "");
        const sourceDeviceId = String(op?.sourceDeviceId || deviceId);
        const purpose = String(op?.purpose || "record").slice(0, 80);
        const encryptionVersion = Number(op?.encryptionVersion || 1);
        const recordType = operation === "delete" ? "tombstone" : String(op?.recordType || "entry").slice(0, 80);
        if (!/^[\w-]{8,100}$/.test(operationId) || !/^[\w-]{8,100}$/.test(recordId) || !operation ||
            !["DEVOTION","MATRIMONIAL","PROFESSIONAL","DAILY"].includes(dimension) ||
            !deviceId || sourceDeviceId !== deviceId || !purpose ||
            !Number.isSafeInteger(baseRevision) || baseRevision < 0 ||
            !Number.isInteger(encryptionVersion) || ![1,2].includes(encryptionVersion) ||
            (operation === "upsert" && (!payload || payload.length > 50000)) ||
            (encryptionVersion === 2 && (operation !== "upsert" || !payload.startsWith("v2."))) ||
            (encryptionVersion === 1 && payload.startsWith("v2.")) ||
            (encryptionVersion === 2 && purpose === "fasting-plan" && recordType !== "fasting_plan") ||
            (encryptionVersion === 2 && purpose === "fasting-entry" && recordType !== "fasting_daily_entry")) {
          results.push({ operationId, status: "rejected", reason: "invalid_operation_or_crypto_context" }); continue;
        }
        const device = await db.prepare("SELECT id FROM devices WHERE id=? AND user_id=? AND revoked_at IS NULL").bind(deviceId,session.user_id).first();
        if (!device || deviceId !== session.device_id || sourceDeviceId !== session.device_id) { results.push({ operationId, status: "rejected", reason: "device_revoked_or_mismatched" }); continue; }
        const previous = await db.prepare("SELECT status,result_revision FROM sync_operations WHERE user_id=? AND operation_id=?")
          .bind(session.user_id,operationId).first<{status:string;result_revision:number|null}>();
        if (previous) { results.push({ operationId, status: previous.status, revision: previous.result_revision, duplicate: true }); continue; }
        const nextRevision = baseRevision + 1;
        // Allocate a strictly increasing per-account server cursor. This prevents
        // same-millisecond writes from being skipped by (server_updated_at, record_id).
        const clock = await db.prepare(
          "INSERT INTO sync_account_clocks(user_id,last_server_updated_at) VALUES(?,?) " +
          "ON CONFLICT(user_id) DO UPDATE SET last_server_updated_at=MAX(sync_account_clocks.last_server_updated_at+1,excluded.last_server_updated_at) " +
          "RETURNING last_server_updated_at"
        ).bind(session.user_id, Date.now()).first<{last_server_updated_at:number}>();
        const now = Number(clock?.last_server_updated_at || Date.now());
        const storedPayload = operation === "delete" ? "" : payload;
        const storedClientTime = operation === "delete" ? now : Number(op.clientUpdatedAt || now);
        const write = db.prepare(
          "INSERT INTO records(user_id,record_id,dimension,record_type,payload_ciphertext,encryption_version,revision,client_updated_at,server_updated_at,deleted_at) " +
          "VALUES(?,?,?,?,?,?,?,?,?,?) " +
          "ON CONFLICT(user_id,record_id) DO UPDATE SET dimension=excluded.dimension,record_type=excluded.record_type,payload_ciphertext=excluded.payload_ciphertext,encryption_version=excluded.encryption_version,revision=excluded.revision,client_updated_at=excluded.client_updated_at,server_updated_at=excluded.server_updated_at,deleted_at=excluded.deleted_at " +
          "WHERE records.revision=?"
        ).bind(session.user_id,recordId,dimension,recordType,storedPayload,encryptionVersion,nextRevision,storedClientTime,now,operation === "delete" ? now : null,baseRevision);
        const audit = db.prepare(
          "INSERT INTO sync_operations(user_id,operation_id,device_id,record_id,operation,base_revision,result_revision,status,server_updated_at) " +
          "SELECT ?,?,?,?,?,?,?, 'applied', ? WHERE changes()=1"
        ).bind(session.user_id,operationId,deviceId,recordId,operation,baseRevision,nextRevision,now);
        const context = db.prepare(
          "INSERT INTO sync_record_context(user_id,record_id,source_device_id,purpose) " +
          "SELECT user_id,record_id,device_id,? FROM sync_operations WHERE user_id=? AND operation_id=? AND status='applied' " +
          "ON CONFLICT(user_id,record_id) DO UPDATE SET source_device_id=excluded.source_device_id,purpose=excluded.purpose"
        ).bind(purpose,session.user_id,operationId);
        const batchResult = await db.batch([write,audit,context]);
        const changed = Number((batchResult[0] as any)?.meta?.changes || 0);
        if (changed !== 1) {
          const current = await db.prepare("SELECT revision FROM records WHERE user_id=? AND record_id=?")
            .bind(session.user_id,recordId).first<{revision:number}>();
          results.push({ operationId, status: "conflict", currentRevision: current?.revision || 0 });
          continue;
        }
        results.push({ operationId, status: "applied", revision: nextRevision, serverUpdatedAt: now });
      }
      return json({ results, serverTime: Date.now() }, 200, origin);
    }
    return json({ error: "Rota não encontrada." }, 404, origin);
  } catch (error) {
    const message = error instanceof Error && error.message === "PAYLOAD_TOO_LARGE" ? "Solicitação muito grande." : "Falha ao processar a solicitação.";
    return json({ error: message }, error instanceof Error && error.message === "PAYLOAD_TOO_LARGE" ? 413 : 400, origin);
  }
}
