/* MEtanoia account-level E2EE. Byte contract mirrors AccountCrypto.kt. */
(()=> {
"use strict";
const VERSION=1,ITERATIONS=600000,KEY_BYTES=32,SALT_BYTES=16,NONCE_BYTES=12,enc=new TextEncoder();
const b64=b=>btoa(String.fromCharCode(...b)),unb64=s=>Uint8Array.from(atob(s),c=>c.charCodeAt(0));
const keyAad=id=>enc.encode("metanoia-account-key|1|"+id);
const recordAad=(u,id,d,t)=>enc.encode("metanoia-record|1|"+u+"|"+id+"|"+d+"|"+t);
const random=n=>crypto.getRandomValues(new Uint8Array(n));
async function derive(pass,salt,iterations=ITERATIONS){
 if(typeof pass!=="string"||pass.length<12)throw Error("Frase de criptografia inválida.");
 if(iterations<ITERATIONS||iterations>2000000)throw Error("Parâmetro KDF inválido.");
 const material=await crypto.subtle.importKey("raw",enc.encode(pass),"PBKDF2",false,["deriveKey"]);
 return crypto.subtle.deriveKey({name:"PBKDF2",hash:"SHA-256",salt,iterations},material,{name:"AES-GCM",length:256},false,["encrypt","decrypt"]);
}
async function createEnvelope(userId,passphrase){
 if(!userId)throw Error("Conta inválida.");
 const salt=random(SALT_BYTES),nonce=random(NONCE_BYTES),dataKey=random(KEY_BYTES),kek=await derive(passphrase,salt);
 const cipher=await crypto.subtle.encrypt({name:"AES-GCM",iv:nonce,additionalData:keyAad(userId),tagLength:128},kek,dataKey);
 return {format:"metanoia-key-envelope",version:VERSION,kdf:"PBKDF2-SHA256",iterations:ITERATIONS,salt:b64(salt),nonce:b64(nonce),ciphertext:b64(new Uint8Array(cipher))};
}
async function openEnvelope(userId,passphrase,e){
 if(!e||e.format!=="metanoia-key-envelope"||e.version!==VERSION||e.kdf!=="PBKDF2-SHA256")throw Error("Envelope incompatível.");
 const salt=unb64(e.salt),nonce=unb64(e.nonce);
 if(salt.length!==SALT_BYTES||nonce.length!==NONCE_BYTES)throw Error("Envelope inválido.");
 const kek=await derive(passphrase,salt,e.iterations);
 const raw=await crypto.subtle.decrypt({name:"AES-GCM",iv:nonce,additionalData:keyAad(userId),tagLength:128},kek,unb64(e.ciphertext));
 if(raw.byteLength!==KEY_BYTES)throw Error("Chave inválida.");
 return new Uint8Array(raw);
}
async function encryptRecord(userId,id,dimension,type,dataKey,plaintext){
 if(dataKey.byteLength!==KEY_BYTES)throw Error("Chave inválida.");
 const iv=random(NONCE_BYTES),key=await crypto.subtle.importKey("raw",dataKey,{name:"AES-GCM"},false,["encrypt"]);
 const bytes=plaintext instanceof Uint8Array?plaintext:enc.encode(String(plaintext));
 const cipher=await crypto.subtle.encrypt({name:"AES-GCM",iv,additionalData:recordAad(userId,id,dimension,type),tagLength:128},key,bytes);
 const out=new Uint8Array(iv.length+cipher.byteLength);out.set(iv);out.set(new Uint8Array(cipher),iv.length);return "v1."+b64(out);
}
async function decryptRecord(userId,id,dimension,type,dataKey,value){
 if(dataKey.byteLength!==KEY_BYTES||!value.startsWith("v1."))throw Error("Registro inválido.");
 const all=unb64(value.slice(3)),iv=all.slice(0,NONCE_BYTES),cipher=all.slice(NONCE_BYTES);
 if(iv.length!==NONCE_BYTES||cipher.length<=16)throw Error("Registro inválido.");
 const key=await crypto.subtle.importKey("raw",dataKey,{name:"AES-GCM"},false,["decrypt"]);
 return new Uint8Array(await crypto.subtle.decrypt({name:"AES-GCM",iv,additionalData:recordAad(userId,id,dimension,type),tagLength:128},key,cipher));
}
window.MEtanoiaAccountCrypto=Object.freeze({createEnvelope,openEnvelope,encryptRecord,decryptRecord,version:VERSION,kdfIterations:ITERATIONS});
})();