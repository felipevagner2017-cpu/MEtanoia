import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
globalThis.window = {};
const source = await readFile(new URL("../web/crypto.js", import.meta.url), "utf8");
(0, eval)(source);
const api = window.MEtanoiaAccountCrypto;
assert.equal(api.version, 1);

// Android-produced deterministic vector: PWA opens its wrapped account key and record.
const androidEnvelope = {
  format:"metanoia-key-envelope",version:1,kdf:"PBKDF2-SHA256",iterations:600000,
  salt:"AAECAwQFBgcICQoLDA0ODw==",nonce:"EBESExQVFhcYGRob",
  ciphertext:"TEkPBZsPEhWxEfDk4jt+tZq4dFKFTmjZ3tR4rQC4+L/+p0cDJNMM9ealgfi2H14P"
};
const androidKey = await api.openEnvelope("interop-user-01","interop passphrase 123",androidEnvelope);
assert.equal(Buffer.from(androidKey).toString("base64"),"ICEiIyQlJicoKSorLC0uLzAxMjM0NTY3ODk6Ozw9Pj8=");
const androidRecord = "v1.UFFSU1RVVldYWVpb8s/GZlTszWlbL2loBGjnwPyCIB85mB1yzZW8etUqQVuEtDK3Rk/YUnXFLTjkJae5iv7LIpXRIuM/kzc=";
assert.equal(new TextDecoder().decode(await api.decryptRecord("interop-user-01","record-interop-1","DEVOTION","journal",androidKey,androidRecord)),"Conteúdo criado no Android e aberto na PWA");

// PWA-produced deterministic vector: proves the reciprocal byte format expected by Android.
const pwaEnvelope = {
  format:"metanoia-key-envelope",version:1,kdf:"PBKDF2-SHA256",iterations:600000,
  salt:"EBESExQVFhcYGRobHB0eHw==",nonce:"KCkqKywtLi8wMTIz",
  ciphertext:"6imIo6IAg9BsXvVld9AnOtPQB/in8/PQmOF1kRrXK5ULX+5c9zMWqQkm+X+YSLdV"
};
const pwaKey = await api.openEnvelope("interop-user-pwa","pwa-generated phrase 456",pwaEnvelope);
assert.equal(Buffer.from(pwaKey).toString("base64"),"QEFCQ0RFRkdISUpLTE1OT1BRUlNUVVZXWFlaW1xdXl8=");
const pwaRecord = "v1.YGFiY2RlZmdoaWpr70dncWJXKsJmoRpEbOc4ZC9CjzFHadAua1qq2s6wc5QAgYI+OqW7+8eQxB+X3YwgYxmg6EHoBNKru5I=";
assert.equal(new TextDecoder().decode(await api.decryptRecord("interop-user-pwa","record-pwa-1","MATRIMONIAL","journal",pwaKey,pwaRecord)),"Conteúdo criado na PWA e aberto no Android");

await assert.rejects(() => api.openEnvelope("another-account","interop passphrase 123",androidEnvelope));
await assert.rejects(() => api.openEnvelope("interop-user-01","wrong encryption passphrase",androidEnvelope));
await assert.rejects(() => api.decryptRecord("another-account","record-interop-1","DEVOTION","journal",androidKey,androidRecord));
await assert.rejects(() => api.decryptRecord("interop-user-01","record-other","DEVOTION","journal",androidKey,androidRecord));
console.log("PASS: fixed Android→PWA and PWA→Android AES-256-GCM/PBKDF2 vectors; account and record AAD isolation.");
