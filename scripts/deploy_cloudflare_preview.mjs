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
const dbPath="/accounts/"+accountId+"/d1/database";
let list=await cf(dbPath+"?name=metanoia-preview");
let database=Array.isArray(list)?list.find(x=>x.name==="metanoia-preview"):null;
if(!database){
 try{database=await cf(dbPath,"POST",{name:"metanoia-preview"});}
 catch(error){
  list=await cf(dbPath+"?name=metanoia-preview");
  database=Array.isArray(list)?list.find(x=>x.name==="metanoia-preview"):null;
  if(!database)throw error;
 }
}
if(!database?.uuid)throw new Error("Cloudflare did not return the isolated preview D1 database ID.");
const config=JSON.parse(fs.readFileSync("wrangler.jsonc","utf8"));
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
run(["d1","migrations","apply","metanoia-preview","--remote","--config","wrangler.preview.jsonc"]);
run(["d1","migrations","list","metanoia-preview","--remote","--config","wrangler.preview.jsonc"]);
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
if(registration.status!==201||!registered.userId||registered.deviceId!==deviceId)throw new Error("Preview account registration failed (HTTP "+registration.status+").");
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
const saveEnvelope=await fetch(url+"/api/crypto/key-envelope",{method:"POST",headers:{...apiHeaders,...auth},body:JSON.stringify({envelope})});
if(saveEnvelope.status!==201)throw new Error("Preview encrypted key-envelope persistence failed (HTTP "+saveEnvelope.status+").");
const dek=await accountCrypto.openEnvelope(session.userId,passphrase,envelope);
const recordUuid=webcrypto.randomUUID(),recordId="pwa-journal-"+recordUuid,recordType="pwa-journal-entry";
const firstEntity={id:recordUuid,title:"Preview smoke test",body:"encrypted-persisted-record",dimension:"Cotidiano",at:Date.now(),checkin:{}};
const firstCipher=await accountCrypto.encryptRecordV2(session.userId,deviceId,"record",recordId,"DAILY",recordType,dek,1,JSON.stringify(firstEntity));
const push=await fetch(url+"/api/sync/push",{method:"POST",headers:{...apiHeaders,...auth},body:JSON.stringify({operations:[{
 operationId:webcrypto.randomUUID(),recordId,operation:"upsert",dimension:"DAILY",recordType,
 payloadCiphertext:firstCipher,encryptionVersion:2,sourceDeviceId:deviceId,purpose:"record",
 baseRevision:0,deviceId,clientUpdatedAt:Date.now()
}]})});
const pushed=await push.json().catch(()=>({}));
if(push.status!==200||pushed.results?.[0]?.status!=="applied")throw new Error("Preview encrypted sync push failed (HTTP "+push.status+").");
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
const summary=[
 "### MEtanoia isolated Cloudflare Preview",
 "URL: "+url,
 "Worker: metanoia-preview",
 "Preview D1: "+database.uuid,
 "PWA root: HTTP "+page.status,
 "New-account registration: HTTP "+registration.status,
 "Login: HTTP "+login.status,
 "Encrypted key-envelope persistence and second-device recovery: passed",
 "Encrypted v2 push, D1 persistence, pull and cross-device decryption: passed",
 "Preview registration is open only on this isolated Preview Worker and database.",
 "Production Worker and production D1 were not modified."
].join("\n");
fs.appendFileSync(process.env.GITHUB_STEP_SUMMARY,summary+"\n");
console.log("PREVIEW_URL="+url);
