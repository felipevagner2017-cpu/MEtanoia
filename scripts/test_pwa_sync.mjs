import assert from "node:assert/strict";
import fs from "node:fs";
import vm from "node:vm";
import { webcrypto } from "node:crypto";
import { TextEncoder, TextDecoder } from "node:util";

class MemoryStorage {
  #values = new Map();
  getItem(key) { return this.#values.has(key) ? this.#values.get(key) : null; }
  setItem(key, value) { this.#values.set(key, String(value)); }
  removeItem(key) { this.#values.delete(key); }
}
function createIndexedDB() {
  const stores = { meta: new Map(), counters: new Map(), outbox: new Map() };
  return {
    open() {
      const request = {};
      queueMicrotask(() => {
        const db = {
          objectStoreNames: { contains: name => !!stores[name] },
          createObjectStore(name) { if (!stores[name]) stores[name] = new Map(); },
          close() {},
          transaction(name) {
            const map = stores[name];
            let pending = 0, completeScheduled = false;
            const tx = { oncomplete: null, onerror: null, onabort: null };
            const scheduleComplete = () => {
              if (pending || completeScheduled) return;
              completeScheduled = true;
              setTimeout(() => { completeScheduled = false; if (!pending) tx.oncomplete?.(); }, 0);
            };
            tx.objectStore = () => ({
              get(key) {
                const req = { onsuccess: null, onerror: null };
                pending++;
                setTimeout(() => { req.result = map.get(key); req.onsuccess?.(); pending--; scheduleComplete(); }, 0);
                return req;
              },
              put(value) {
                const req = { onsuccess: null, onerror: null };
                pending++;
                setTimeout(() => { map.set(value.key, structuredClone(value)); req.result = value.key; req.onsuccess?.(); pending--; scheduleComplete(); }, 0);
                return req;
              },
              getAll() {
                const req = { onsuccess: null, onerror: null };
                pending++;
                setTimeout(() => { req.result = [...map.values()].map(x => structuredClone(x)); req.onsuccess?.(); pending--; scheduleComplete(); }, 0);
                return req;
              },
              delete(key) {
                const req = { onsuccess: null, onerror: null };
                pending++;
                setTimeout(() => { map.delete(key); req.onsuccess?.(); pending--; scheduleComplete(); }, 0);
                return req;
              }
            });
            return tx;
          }
        };
        request.result = db;
        request.onupgradeneeded?.();
        request.onsuccess?.();
      });
      return request;
    }
  };
}
const cryptoSource = fs.readFileSync("web/crypto.js", "utf8");
const syncSource = fs.readFileSync("web/sync.js", "utf8");
const accountId = "account-sync-integration";
const dataKey = webcrypto.getRandomValues(new Uint8Array(32));
const remote = new Map();
let serverRevisionTime = Date.now();
const operationIds = new Set();
let failNextPush = false;
const pushAttempts = [];

function makeDevice(deviceId, initialDb) {
  const localStorage = new MemoryStorage();
  const sessionStorage = new MemoryStorage();
  sessionStorage.setItem("metonoia-user-id", accountId);
  const db = structuredClone(initialDb);
  const context = {
    window: null, crypto: webcrypto, Uint8Array, TextEncoder, TextDecoder, btoa, atob,
    indexedDB: createIndexedDB(), localStorage, sessionStorage,
    setTimeout, clearTimeout, Date, JSON, Map, Error, Promise, console,
    addEventListener: () => {}, document: { hidden: false, addEventListener: () => {} },
    fetch: async (url, options = {}) => {
      assert.equal(options.headers?.Authorization, "Bearer test-session");
      const path = new URL(url, "https://metanoia.test").pathname;
      if (path === "/api/sync/pull") {
        const rows = [...remote.values()].sort((a,b)=>a.server_updated_at-b.server_updated_at||a.record_id.localeCompare(b.record_id));
        return new Response(JSON.stringify({records:rows,serverTime:Date.now(),hasMore:false,nextCursor:null}),{status:200});
      }
      if (path === "/api/sync/push") {
        const body = JSON.parse(options.body);
        pushAttempts.push(...body.operations.map(op => structuredClone(op)));
        if (failNextPush) { failNextPush = false; throw new Error("simulated offline"); }
        const results = body.operations.map(op => {
          if (operationIds.has(op.operationId)) return {operationId:op.operationId,status:"applied",duplicate:true,revision:remote.get(op.recordId)?.revision};
          if (op.encryptionVersion === 2) {
            assert.equal(op.sourceDeviceId, op.deviceId);
            assert.equal(op.purpose, "record");
            assert.ok(op.payloadCiphertext.startsWith("v2."));
          }
          const current = remote.get(op.recordId);
          if ((current?.revision || 0) !== op.baseRevision)
            return {operationId:op.operationId,status:"conflict",currentRevision:current?.revision||0};
          const revision = op.baseRevision + 1;
          const now = ++serverRevisionTime;
          remote.set(op.recordId,{
            record_id:op.recordId,dimension:op.dimension,
            record_type:op.operation==="delete"?"tombstone":op.recordType,
            payload_ciphertext:op.operation==="delete"?"":op.payloadCiphertext,
            encryption_version:op.operation==="delete"?1:op.encryptionVersion,
            source_device_id:op.operation==="delete"?null:op.sourceDeviceId,
            purpose:op.operation==="delete"?null:op.purpose,
            revision,client_updated_at:op.clientUpdatedAt||now,
            server_updated_at:now,deleted_at:op.operation==="delete"?now:null
          });
          operationIds.add(op.operationId);
          return {operationId:op.operationId,status:"applied",revision,serverUpdatedAt:now};
        });
        return new Response(JSON.stringify({results,serverTime:Date.now()}),{status:200});
      }
      throw new Error("Unexpected API route "+path);
    }
  };
  context.window = context;
  vm.createContext(context);
  vm.runInContext(cryptoSource, context, {filename:"crypto.js"});
  vm.runInContext(syncSource, context, {filename:"sync.js"});
  const engine = context.MEtanoiaSync;
  engine.configure({
    token:"test-session",accountId,deviceId,dataKey,
    getDb:()=>db,onApply:()=>localStorage.setItem("metonoia-pwa-v1",JSON.stringify(db))
  });
  return {db,engine,stores};
}

const first = makeDevice("11111111-1111-4111-8111-111111111111",{
  items:[{id:"agenda-1",title:"Culto",dimension:"Espiritual",date:"2026-11-10",time:"19:30"}],
  journal:[{id:"journal-1",title:"Registro inicial",body:"Conteúdo privado",dimension:"Matrimonial",at:Date.now(),checkin:{}}],
  reviews:[],words:[]
});
await first.engine.sync();
assert.equal(remote.size,2);
for (const row of remote.values()) {
  assert.equal(row.encryption_version,2);
  assert.ok(row.payload_ciphertext.startsWith("v2."));
  assert.ok(!row.payload_ciphertext.includes("Conteúdo privado"));
}
const retryRecord={id:"review-retry",body:"persistir e reenviar exatamente a mesma operação",at:Date.now()};
first.db.reviews.push(retryRecord);
failNextPush=true;
await assert.rejects(first.engine.sync(),/simulated offline/);
const queuedBefore=[...first.stores.outbox.values()][0];
assert.ok(queuedBefore?.operation?.operationId,"a operação deve estar persistida antes do envio");
const stableOperationId=queuedBefore.operation.operationId;
const stableCiphertext=queuedBefore.operation.payloadCiphertext;
await first.engine.sync();
const retryAttempts=pushAttempts.filter(op=>op.recordId==="pwa-review-review-retry");
assert.ok(retryAttempts.length>=2,"a operação precisa ter sido tentada novamente");
assert.equal(retryAttempts.at(-1).operationId,stableOperationId,"a repetição deve reutilizar o mesmo operationId");
assert.equal(retryAttempts.at(-1).payloadCiphertext,stableCiphertext,"a repetição deve reutilizar o mesmo ciphertext/nonce");
assert.equal(first.stores.outbox.size,0,"a fila só pode ser limpa após confirmação do servidor");
const second = makeDevice("22222222-2222-4222-8222-222222222222",{items:[],journal:[],reviews:[],words:[]});
await second.engine.sync();
assert.equal(second.db.items[0].title,"Culto");
assert.equal(second.db.journal[0].body,"Conteúdo privado");
second.db.journal[0].body="Atualizado no segundo dispositivo";
second.db.journal[0].checkin={remoteField:"preservado"};
await second.engine.sync();
first.db.journal[0].title="Título alterado localmente durante a sincronização";
await first.engine.sync();
assert.equal(first.db.journal[0].body,"Atualizado no segundo dispositivo");
assert.equal(first.db.journal[0].title,"Título alterado localmente durante a sincronização");
assert.equal(first.db.journal[0].checkin.remoteField,"preservado","o campo atualizado remotamente deve sobreviver ao rebase");
await second.engine.sync();
assert.equal(second.db.journal[0].title,"Título alterado localmente durante a sincronização");
first.engine.markDeleted("items","agenda-1","Espiritual");
first.db.items=[];
await first.engine.sync();
await second.engine.sync();
assert.equal(second.db.items.length,0);
assert.equal([...remote.values()].find(x=>x.record_id==="pwa-item-agenda-1").deleted_at!==null,true);
console.log("PWA sync integration passed: encrypted v2 create, persistence, second-device recovery, update propagation, and explicit tombstone deletion.");
