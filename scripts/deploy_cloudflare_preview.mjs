import fs from "node:fs";
import { spawnSync } from "node:child_process";
import vm from "node:vm";
import { webcrypto } from "node:crypto";
import { TextEncoder, TextDecoder } from "node:util";

const accountId=process.env.CLOUDFLARE_ACCOUNT_ID||"";
const token=process.env.CLOUDFLARE_API_TOKEN||"";
if(!accountId||!token)throw new Error("Preview requires the existing Cloudflare account token and account ID secrets.");
const headers={Authorization:"Bearer "+token,"Content-Type":"application/json"};
async function cf(path,method="GET",body){
 const response=await fetch("https://api.cloudflare.com/client/v4"+path,{method,headers,body:body?JSON.stringify(body):undefined});
 const data=await response.json().catch(()=>({}));
 if(!response.ok||data.success===false)throw new Error("Cloudflare API "+method+" "+path+" failed (HTTP "+response.status+"): "+(data.errors||[]).map(x=>x.message).join("; "));
 return data.result;
}
// Preview D1 was independently created and verified in the isolated account.
 // Its UUID and applied migrations are pinned here so the deployment token can remain
 // least-privilege (Workers Editor only). This script never creates, alters, or migrates D1.
const EXPECTED_PREVIEW_D1_ID="08740f17-371b-43b9-aa7f-518337a91b22";
const database={name:"metanoia-preview",uuid:EXPECTED_PREVIEW_D1_ID};

const config=JSON.parse(fs.readFileSync("wrangler.jsonc","utf8"));
const productionDatabase=(config.d1_databases||[]).find(item=>item.binding==="DB")?.database_id;
if(!productionDatabase||productionDatabase===database.uuid)throw new Error("Production/Preview D1 isolation check failed; deployment stopped.");
if(config.routes?.length)throw new Error("Preview config inherited production routes; refusing deployment.");
config.name="metanoia-preview";
config.workers_dev=true;
config.keep_vars=true;
config.vars={...(config.vars||{}),METANOIA_PREVIEW_OPEN_REGISTRATION:"true"};
config.d1_databases=(config.d1_databases||[]).map(item=>item.binding==="DB"?{...item,database_name:"metanoia-preview",database_id:database.uuid}:item);
if(config.d1_databases.filter(x=>x.binding==="DB"&&x.database_id===database.uuid).length!==1)throw new Error("Preview D1 binding could not be isolated from production.");
fs.writeFileSync("wrangler.preview.jsonc",JSON.stringify(config,null,2)+"\n");
function run(args){
 const result=spawnSync("wrangler",args,{encoding:"utf8",stdio:"inherit",env:process.env});
 if(result.status!==0)throw new Error("wrangler "+args.join(" ")+" failed with exit "+result.status);
}
// The isolated Preview D1 was verified earlier: migrations 0001-0003 are applied.
 // Do not call D1 APIs or run migrations during a Worker-only deployment.
console.log("PREVIEW_D1_UUID="+database.uuid);
console.log("PREVIEW_D1_MIGRATIONS=0001,0002,0003 (previously verified; unchanged)");
run(["deploy","--keep-vars","--config","wrangler.preview.jsonc"]);
const subdomainResult=await cf("/accounts/"+accountId+"/workers/subdomain");
const subdomain=subdomainResult?.subdomain;
if(!subdomain)throw new Error("Worker was deployed but Cloudflare did not return an active workers.dev subdomain.");
const url="https://metanoia-preview."+subdomain+".workers.dev";
const page=await fetch(url,{redirect:"follow",cache:"no-store"});
const html=await page.text();
if(!page.ok||!html.includes("MEtanoia")||!html.includes("/sync.js"))throw new Error("Preview smoke test failed: root page did not return the expected MEtanoia PWA.");
const apiHeaders={"Content-Type":"application/json"};
const email="preview-"+webcrypto.randomUUID()+"@example.invalid";
const password="MEtanoia-preview-"+webcrypto.randomUUID();
const deviceId=webcrypto.randomUUID();
const registration=await fetch(url+"/api/auth/register",{method:"POST",headers:apiHeaders,body:JSON.stringify({email,password,platform:"pwa",deviceLabel:"CI Preview smoke test",deviceId})});
const registered=await registration.json().catch(()=>({}));
if(registration.status!==201||!registered.userId||registered.deviceId!==deviceId)throw new Error("Preview account registration failed (HTTP "+registration.status+"): "+JSON.stringify(registered));
const login=await fetch(url+"/api/auth/login",{method:"POST",headers:apiHeaders,body:JSON.stringify({email,password,platform:"pwa",deviceLabel:"CI Preview smoke test",deviceId})});
const session=await login.json().catch(()=>({}));
if(login.status!==200||!session.token)throw new Error("Preview login failed (HTTP "+login.status+").");
const cryptoContext={window:null,crypto:webcrypto,Uint8Array,TextEncoder,TextDecoder,btoa,atob};
cryptoContext.window=cryptoContext;
vm.runInNewContext(fs.readFileSync("web/crypto.js","utf8"),cryptoContext,{filename:"web/crypto.js"});
const accountCrypto=cryptoContext.MEtanoiaAccountCrypto;
const passphrase="MEtanoia-preview-key-"+webcrypto.randomUUID();
const envelope=await accountCrypto.createEnvelope(session.userId,passphrase);
const auth={"Authorization":"Bearer "+session.token};
const aiSmoke=await fetch(url+"/api/ai/chat",{method:"POST",headers:{...apiHeaders,...auth},body:JSON.stringify({
 dimension:"Devoção",context:"Validação técnica do Preview. Nenhum registro pessoal foi enviado.",history:[],
 question:"Responda em português com uma frase curta confirmando que a análise contextual está disponível."
})});
const aiData=await aiSmoke.json().catch(()=>({}));
if(aiSmoke.status!==200||typeof aiData.answer!=="string"||!aiData.answer.trim())throw new Error("Preview contextual AI inference failed (HTTP "+aiSmoke.status+").");
const saveEnvelope=await fetch(url+"/api/crypto/key-envelope",{method:"POST",headers:{...apiHeaders,...auth},body:JSON.stringify({envelope})});
if(saveEnvelope.status!==201)throw new Error("Preview encrypted key-envelope persistence failed (HTTP "+saveEnvelope.status+").");
const dek=await accountCrypto.openEnvelope(session.userId,passphrase,envelope);
const recordUuid=webcrypto.randomUUID(),recordId="pwa-journal-"+recordUuid,recordType="pwa-journal-entry";
const firstEntity={id:recordUuid,title:"Preview smoke test",body:"encrypted-persisted-record",dimension:"Cotidiano",at:Date.now(),checkin:{}};
const firstCipher=await accountCrypto.encryptRecordV2(session.userId,deviceId,"record",recordId,"DAILY",recordType,dek,1,JSON.stringify(firstEntity));
const firstOperation={operationId:webcrypto.randomUUID(),recordId,operation:"upsert",dimension:"DAILY",recordType,
 payloadCiphertext:firstCipher,encryptionVersion:2,sourceDeviceId:deviceId,purpose:"record",
 baseRevision:0,deviceId,clientUpdatedAt:Date.now()};
const push=await fetch(url+"/api/sync/push",{method:"POST",headers:{...apiHeaders,...auth},body:JSON.stringify({operations:[firstOperation]})});
const pushed=await push.json().catch(()=>({}));
if(push.status!==200||pushed.results?.[0]?.status!=="applied")throw new Error("Preview encrypted sync push failed (HTTP "+push.status+").");
const duplicatePush=await fetch(url+"/api/sync/push",{method:"POST",headers:{...apiHeaders,...auth},body:JSON.stringify({operations:[firstOperation]})});
const duplicateData=await duplicatePush.json().catch(()=>({}));
if(duplicatePush.status!==200||duplicateData.results?.[0]?.duplicate!==true)throw new Error("Preview operationId retry was not idempotent.");
const secondDevice=webcrypto.randomUUID();
const secondLogin=await fetch(url+"/api/auth/login",{method:"POST",headers:apiHeaders,body:JSON.stringify({email,password,platform:"pwa",deviceLabel:"CI second device",deviceId:secondDevice})});
const secondSession=await secondLogin.json().catch(()=>({}));
if(secondLogin.status!==200||!secondSession.token)throw new Error("Preview second-device login failed.");
const secondAuth={"Authorization":"Bearer "+secondSession.token};
const fetchedEnvelope=await fetch(url+"/api/crypto/key-envelope",{headers:secondAuth});
const fetchedEnvelopeData=await fetchedEnvelope.json().catch(()=>({}));
if(fetchedEnvelope.status!==200||!fetchedEnvelopeData.envelope)throw new Error("Preview second-device key-envelope recovery failed.");
const secondDek=await accountCrypto.openEnvelope(session.userId,passphrase,fetchedEnvelopeData.envelope);
const pull=await fetch(url+"/api/sync/pull?since=0",{headers:secondAuth});
const pulled=await pull.json().catch(()=>({}));
const remoteRecord=pulled.records?.find(x=>x.record_id===recordId);
if(pull.status!==200||!remoteRecord||remoteRecord.source_device_id!==deviceId||remoteRecord.purpose!=="record")throw new Error("Preview sync pull or v2 metadata recovery failed.");
const plaintext=await accountCrypto.decryptRecordV2(session.userId,remoteRecord.source_device_id,remoteRecord.purpose,remoteRecord.record_id,remoteRecord.dimension,remoteRecord.record_type,secondDek,remoteRecord.payload_ciphertext);
if(JSON.parse(new TextDecoder().decode(plaintext)).body!=="encrypted-persisted-record")throw new Error("Preview second-device decryption returned different plaintext.");
const concurrentEntity={...firstEntity,body:"second-device-concurrent-edit",at:Date.now()};
const concurrentCipher=await accountCrypto.encryptRecordV2(session.userId,secondDevice,"record",recordId,"DAILY",recordType,secondDek,2,JSON.stringify(concurrentEntity));
const concurrentOperation={operationId:webcrypto.randomUUID(),recordId,operation:"upsert",dimension:"DAILY",recordType,
 payloadCiphertext:concurrentCipher,encryptionVersion:2,sourceDeviceId:secondDevice,purpose:"record",
 baseRevision:0,deviceId:secondDevice,clientUpdatedAt:Date.now()};
const conflictResponse=await fetch(url+"/api/sync/push",{method:"POST",headers:{...apiHeaders,...secondAuth},body:JSON.stringify({operations:[concurrentOperation]})});
const conflictData=await conflictResponse.json().catch(()=>({}));
if(conflictResponse.status!==200||conflictData.results?.[0]?.status!=="conflict"||conflictData.results[0].currentRevision!==1)throw new Error("Preview concurrent stale edit did not return a revision conflict.");
const rebasedOperation={...concurrentOperation,operationId:webcrypto.randomUUID(),baseRevision:1,clientUpdatedAt:Date.now()};
const rebaseResponse=await fetch(url+"/api/sync/push",{method:"POST",headers:{...apiHeaders,...secondAuth},body:JSON.stringify({operations:[rebasedOperation]})});
const rebaseData=await rebaseResponse.json().catch(()=>({}));
if(rebaseResponse.status!==200||rebaseData.results?.[0]?.status!=="applied"||rebaseData.results[0].revision!==2)throw new Error("Preview conflict rebase did not apply at revision 2.");
const rebasedPull=await fetch(url+"/api/sync/pull?since=0",{headers:auth});
const rebasedData=await rebasedPull.json().catch(()=>({}));
const rebasedRecord=rebasedData.records?.find(x=>x.record_id===recordId);
if(rebasedPull.status!==200||!rebasedRecord||rebasedRecord.revision!==2||rebasedRecord.source_device_id!==secondDevice)throw new Error("Preview rebased record metadata was not persisted.");
const rebasedPlain=await accountCrypto.decryptRecordV2(session.userId,rebasedRecord.source_device_id,rebasedRecord.purpose,rebasedRecord.record_id,rebasedRecord.dimension,rebasedRecord.record_type,secondDek,rebasedRecord.payload_ciphertext);
if(JSON.parse(new TextDecoder().decode(rebasedPlain)).body!=="second-device-concurrent-edit")throw new Error("Preview rebased record did not decrypt to the second-device update.");
const backupResponse=await fetch(url+"/api/backups/create",{method:"POST",headers:{...apiHeaders,...auth},body:JSON.stringify({deviceId,payloadCiphertext:firstCipher,recordCount:1,formatVersion:1})});
const backupData=await backupResponse.json().catch(()=>({}));
if(backupResponse.status!==201||!backupData.id)throw new Error("Preview encrypted backup creation failed.");
const backupRestore=await fetch(url+"/api/backups/"+backupData.id,{headers:auth});
const restored=await backupRestore.json().catch(()=>({}));
if(backupRestore.status!==200||restored.backup?.payload_ciphertext!==firstCipher)throw new Error("Preview encrypted backup restore did not preserve ciphertext.");
const deleteOperation={operationId:webcrypto.randomUUID(),recordId,operation:"delete",dimension:"DAILY",recordType:"tombstone",encryptionVersion:1,baseRevision:2,deviceId};
const deleteResponse=await fetch(url+"/api/sync/push",{method:"POST",headers:{...apiHeaders,...auth},body:JSON.stringify({operations:[deleteOperation]})});
const deleteData=await deleteResponse.json().catch(()=>({}));
if(deleteResponse.status!==200||deleteData.results?.[0]?.status!=="applied"||deleteData.results[0].revision!==3)throw new Error("Preview tombstone deletion failed.");
const tombstonePull=await fetch(url+"/api/sync/pull?since=0",{headers:secondAuth});
const tombstones=await tombstonePull.json().catch(()=>({}));
const tombstone=tombstones.records?.find(x=>x.record_id===recordId);
if(tombstonePull.status!==200||!tombstone||!tombstone.deleted_at||tombstone.record_type!=="tombstone")throw new Error("Preview second-device tombstone recovery failed.");
const isolatedDevice=webcrypto.randomUUID(),isolatedEmail="isolated-"+webcrypto.randomUUID()+"@example.invalid";
const isolatedRegistration=await fetch(url+"/api/auth/register",{method:"POST",headers:apiHeaders,body:JSON.stringify({email:isolatedEmail,password:"MEtanoia-isolated-"+webcrypto.randomUUID(),platform:"pwa",deviceLabel:"CI isolation test",deviceId:isolatedDevice})});
const isolatedSession=await isolatedRegistration.json().catch(()=>({}));
if(isolatedRegistration.status!==201||!isolatedSession.token)throw new Error("Preview isolated-account registration failed.");
const isolatedPull=await fetch(url+"/api/sync/pull?since=0",{headers:{"Authorization":"Bearer "+isolatedSession.token}});
const isolatedData=await isolatedPull.json().catch(()=>({}));
if(isolatedPull.status!==200||isolatedData.records?.some(x=>x.record_id===recordId))throw new Error("Preview record leaked across accounts.");
const revokeResponse=await fetch(url+"/api/devices/"+secondDevice,{method:"DELETE",headers:auth});
if(revokeResponse.status!==200)throw new Error("Preview second-device revocation failed.");
const revokedPull=await fetch(url+"/api/sync/pull?since=0",{headers:secondAuth});
if(revokedPull.status!==401)throw new Error("Preview revoked device retained API access.");
const summary=[
 "### MEtanoia isolated Cloudflare Preview",
 "URL: "+url,
 "Worker: metanoia-preview",
 "Preview D1: "+database.uuid,
 "PWA root: HTTP "+page.status,
 "New-account registration: HTTP "+registration.status,
 "Login: HTTP "+login.status,
  "Contextual AI inference: passed ("+aiData.model+")",
 "Encrypted key-envelope persistence and second-device recovery: passed",
 "Encrypted v2 push/pull, second-device decryption and metadata recovery: passed",
  "OperationId retry idempotency, stale-write conflict and rebase: passed",
  "Encrypted backup create/restore, tombstone pull, account isolation and device revocation: passed",
 "Preview registration is open only on this isolated Preview Worker and database.",
 "Production Worker and production D1 were not modified."
].join("\n");
fs.appendFileSync(process.env.GITHUB_STEP_SUMMARY,summary+"\n");
console.log("PREVIEW_URL="+url);
