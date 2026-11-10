/* Encrypted, revision-aware PWA synchronization. Plaintext never leaves the browser. */
(()=> {
"use strict";
const DB_NAME="metonoia-sync-v1",DB_VERSION=2,PURPOSE="record";
const DIMENSION={Espiritual:"DEVOTION",Matrimonial:"MATRIMONIAL",Profissional:"PROFESSIONAL",Cotidiano:"DAILY"};
const TYPES={items:"pwa-agenda-item",journal:"pwa-journal-entry",reviews:"pwa-review-entry",words:"pwa-word-entry"};
const PREFIX={items:"pwa-item-",journal:"pwa-journal-",reviews:"pwa-review-",words:"pwa-word-"};
const encoder=new TextEncoder(),decoder=new TextDecoder();
let configuration=null,running=null,timer=null;
const uuid=()=>crypto.randomUUID();
const json=value=>JSON.stringify(value);
const parse=value=>JSON.parse(value);
function openDb(){
 return new Promise((resolve,reject)=>{
  const request=indexedDB.open(DB_NAME,DB_VERSION);
  request.onupgradeneeded=()=>{const db=request.result;if(!db.objectStoreNames.contains("meta"))db.createObjectStore("meta",{keyPath:"key"});if(!db.objectStoreNames.contains("counters"))db.createObjectStore("counters",{keyPath:"key"});if(!db.objectStoreNames.contains("outbox"))db.createObjectStore("outbox",{keyPath:"key"});};
  request.onsuccess=()=>resolve(request.result);request.onerror=()=>reject(request.error||Error("Não foi possível abrir o armazenamento seguro de sincronização."));
 });
}
async function getMeta(key){const db=await openDb();try{return await new Promise((resolve,reject)=>{const tx=db.transaction("meta","readonly"),r=tx.objectStore("meta").get(key);r.onsuccess=()=>resolve(r.result||null);r.onerror=()=>reject(r.error);});}finally{db.close();}}
async function putMeta(value){const db=await openDb();try{await new Promise((resolve,reject)=>{const tx=db.transaction("meta","readwrite");tx.objectStore("meta").put(value);tx.oncomplete=resolve;tx.onerror=()=>reject(tx.error);tx.onabort=()=>reject(tx.error||Error("Falha ao persistir metadados de sincronização."));});}finally{db.close();}}
async function allMeta(prefix){const db=await openDb();try{return await new Promise((resolve,reject)=>{const tx=db.transaction("meta","readonly"),r=tx.objectStore("meta").getAll();r.onsuccess=()=>resolve((r.result||[]).filter(x=>x.key.startsWith(prefix)));r.onerror=()=>reject(r.error);});}finally{db.close();}}
async function getOutbox(key){const db=await openDb();try{return await new Promise((resolve,reject)=>{const tx=db.transaction("outbox","readonly"),r=tx.objectStore("outbox").get(key);r.onsuccess=()=>resolve(r.result||null);r.onerror=()=>reject(r.error);});}finally{db.close();}}
async function putOutbox(value){const db=await openDb();try{await new Promise((resolve,reject)=>{const tx=db.transaction("outbox","readwrite");tx.objectStore("outbox").put(value);tx.oncomplete=resolve;tx.onerror=()=>reject(tx.error);tx.onabort=()=>reject(tx.error||Error("Falha ao persistir operação pendente."));});}finally{db.close();}}
async function deleteOutbox(key){const db=await openDb();try{await new Promise((resolve,reject)=>{const tx=db.transaction("outbox","readwrite");tx.objectStore("outbox").delete(key);tx.oncomplete=resolve;tx.onerror=()=>reject(tx.error);tx.onabort=()=>reject(tx.error||Error("Falha ao confirmar operação pendente."));});}finally{db.close();}}
async function allOutbox(prefix){const db=await openDb();try{return await new Promise((resolve,reject)=>{const tx=db.transaction("outbox","readonly"),r=tx.objectStore("outbox").getAll();r.onsuccess=()=>resolve((r.result||[]).filter(x=>x.key.startsWith(prefix)));r.onerror=()=>reject(r.error);});}finally{db.close();}}
async function nextCounter(accountId,deviceId){
 const db=await openDb(),key=accountId+":"+deviceId+":"+PURPOSE;
 try{return await new Promise((resolve,reject)=>{
  const tx=db.transaction("counters","readwrite"),store=tx.objectStore("counters"),request=store.get(key);let value=0;
  request.onsuccess=()=>{value=Number(request.result?.value||0)+1;store.put({key,value});};
  request.onerror=()=>reject(request.error);tx.oncomplete=()=>resolve(value);tx.onerror=()=>reject(tx.error);tx.onabort=()=>reject(tx.error||Error("Falha ao reservar contador criptográfico."));
 });}finally{db.close();}
}
const authHeaders=()=>({Authorization:"Bearer "+configuration.token});
async function api(path,options={}){
 const response=await fetch(path,{cache:"no-store",...options,headers:{...authHeaders(),...(options.headers||{})}});
 const data=await response.json().catch(()=>({}));
 if(!response.ok)throw Error(data.error||("Falha HTTP "+response.status+" em "+path));
 return data;
}
function dimensionOf(kind,value){
 if(kind==="items"||kind==="journal")return DIMENSION[value.dimension]||"DAILY";
 return "DAILY";
}
function collectLocal(){
 const db=configuration.getDb(),records=new Map();
 for(const kind of Object.keys(TYPES)){
  const list=Array.isArray(db[kind])?db[kind]:[];
  for(const entity of list){
   if(!entity.id)entity.id=uuid();
   const recordId=PREFIX[kind]+entity.id;
   records.set(recordId,{recordId,kind,id:String(entity.id),dimension:dimensionOf(kind,entity),recordType:TYPES[kind],entity,plain:json(entity)});
  }
 }
 return records;
}
function kindFromRecord(record){
 for(const kind of Object.keys(PREFIX))if(record.record_id?.startsWith(PREFIX[kind]))return kind;
 return null;
}
function removeLocal(kind,id){
 const list=configuration.getDb()[kind];configuration.getDb()[kind]=list.filter(x=>String(x.id)!==id);
}
function applyLocal(kind,id,entity){
 const db=configuration.getDb(),list=db[kind],index=list.findIndex(x=>String(x.id)===id);
 if(index<0)list.push(entity);else list[index]=entity;
}
async function pullAll(){
 let since=0,afterId="",records=[];
 for(let page=0;page<100;page++){
  const suffix="?since="+encodeURIComponent(since)+(afterId?"&afterId="+encodeURIComponent(afterId):"");
  const data=await api("/api/sync/pull"+suffix);
  records.push(...(data.records||[]));
  if(!data.hasMore)break;
  if(!data.nextCursor||page===99)throw Error("A recuperação excedeu o limite de páginas; nenhum dado local foi substituído.");
  since=Number(data.nextCursor.since);afterId=String(data.nextCursor.afterId||"");
 }
 return records;
}
async function decryptRemote(record){
 if(record.deleted_at)return null;
 const cryptoApi=window.MEtanoiaAccountCrypto;
 if(!cryptoApi)throw Error("Módulo criptográfico indisponível.");
 const kind=kindFromRecord(record);
 if(!kind)return undefined;
 const source=record.source_device_id||"";
 const purpose=record.purpose||"";
 let bytes;
 if(Number(record.encryption_version)===2){
  if(!source||purpose!==PURPOSE)throw Error("Metadados v2 ausentes para "+record.record_id+".");
  bytes=await cryptoApi.decryptRecordV2(configuration.accountId,source,purpose,record.record_id,record.dimension,record.record_type,configuration.dataKey,record.payload_ciphertext);
 }else if(Number(record.encryption_version)===1){
  bytes=await cryptoApi.decryptRecord(configuration.accountId,record.record_id,record.dimension,record.record_type,configuration.dataKey,record.payload_ciphertext);
 }else throw Error("Versão criptográfica desconhecida em "+record.record_id+".");
 const entity=parse(decoder.decode(bytes));
 if(!entity||typeof entity!=="object"||String(entity.id)!==record.record_id.slice(PREFIX[kind].length))throw Error("Identidade do registro divergente em "+record.record_id+".");
 return {kind,id:String(entity.id),entity,plain:json(entity)};
}
async function encryptLocal(record){
 const counter=await nextCounter(configuration.accountId,configuration.deviceId);
 const ciphertext=await window.MEtanoiaAccountCrypto.encryptRecordV2(
  configuration.accountId,configuration.deviceId,PURPOSE,record.recordId,record.dimension,
  record.recordType,configuration.dataKey,counter,encoder.encode(record.plain)
 );
 return {
  operationId:uuid(),recordId:record.recordId,operation:"upsert",dimension:record.dimension,
  recordType:record.recordType,payloadCiphertext:ciphertext,encryptionVersion:2,
  sourceDeviceId:configuration.deviceId,purpose:PURPOSE,baseRevision:record.baseRevision||0,
  deviceId:configuration.deviceId,clientUpdatedAt:Date.now()
 };
}
async function pushOperations(operations){
 const applied=[];
 for(const candidate of operations){
  if(candidate.operation==="upsert"&&candidate.payloadCiphertext.length>49000)throw Error("Um registro excede o limite seguro de sincronização (49 KB). Ele permanece salvo neste aparelho.");
  const key=configuration.accountId+":"+candidate.recordId;
  let queued=await getOutbox(key);
  if(!queued){const localRecord=collectLocal().get(candidate.recordId);queued={key,operation:candidate,plain:localRecord?.plain||"",createdAt:Date.now()};await putOutbox(queued);}
  const op=queued.operation;
  const data=await api("/api/sync/push",{method:"POST",headers:{"Content-Type":"application/json"},body:json({operations:[op]})});
  const result=data.results?.[0];
  if(result?.status==="applied"||result?.duplicate){applied.push({operation:op,result,plain:queued.plain});await deleteOutbox(key);continue;}
  if(result?.status==="conflict")throw Object.assign(Error("Conflito de edição em "+op.recordId+"; a operação criptografada permanece na fila local e nenhum dado local será substituído."),{conflict:true});
  throw Error("O servidor rejeitou "+op.recordId+": "+(result?.reason||result?.status||"resposta inválida")+"; a operação permanece na fila local.");
 }
 return applied;
}
function deletionQueueKey(){return "metonoia-pwa-deletions:"+configuration.accountId;}
function readDeletionQueue(){try{return JSON.parse(localStorage.getItem(deletionQueueKey())||"[]")}catch{return []}}
function writeDeletionQueue(queue){localStorage.setItem(deletionQueueKey(),json(queue))}
function markDeleted(kind,id,dimension){
 const accountId=configuration?.accountId||sessionStorage.getItem("metonoia-user-id");
 if(!accountId||!PREFIX[kind])return;
 const key="metonoia-pwa-deletions:"+accountId;
 let queue=[];try{queue=JSON.parse(localStorage.getItem(key)||"[]")}catch{}
 const recordId=PREFIX[kind]+id;
 if(!queue.some(x=>x.recordId===recordId))queue.push({recordId,kind,id:String(id),dimension:dimensionOf(kind,{dimension}),at:Date.now()});
 localStorage.setItem(key,json(queue));
}
async function syncOnce(){
 const remoteRows=await pullAll(),remoteById=new Map(remoteRows.map(r=>[r.record_id,r]));
 const local=collectLocal(),shadowRows=await allMeta("shadow:"+configuration.accountId+":");
 const shadows=new Map(shadowRows.map(x=>[x.key.slice(("shadow:"+configuration.accountId+":").length),x]));
 const outboxRows=await allOutbox(configuration.accountId+":");
 const outboxByRecord=new Map(outboxRows.map(x=>[x.operation.recordId,x]));
 const deletionQueue=readDeletionQueue(),deletionIds=new Set(deletionQueue.map(x=>x.recordId));
 const pending=[];
 let changed=false;
 for(const row of remoteRows){
  const kind=kindFromRecord(row);if(!kind)continue;
  const decoded=await decryptRemote(row);
  const localRecord=local.get(row.record_id),shadow=shadows.get(row.record_id),queued=outboxByRecord.get(row.record_id);
  if(deletionIds.has(row.record_id)){
   const queued=deletionQueue.find(x=>x.recordId===row.record_id);
   if(!row.deleted_at)pending.push({kind:queued.kind,id:queued.id,deleted:true,baseRevision:Number(row.revision||0),dimension:queued.dimension});
   continue;
  }
  const localDirty=!!localRecord&&(!!queued||!!shadow&&localRecord.plain!==shadow.plain);
  if(localDirty){
   let candidateRecord=localRecord;
   if((queued||shadow)&&Number(row.revision||0)>Number(queued?.operation.baseRevision??shadow?.revision??0)){
    const merged={...(decoded?.entity||{})},base=shadow?.plain?parse(shadow.plain):{};
    for(const field of new Set([...Object.keys(base),...Object.keys(localRecord.entity)])){
     const hadBase=Object.prototype.hasOwnProperty.call(base,field),hasLocal=Object.prototype.hasOwnProperty.call(localRecord.entity,field);
     const localChanged=hadBase!==hasLocal||JSON.stringify(base[field])!==JSON.stringify(localRecord.entity[field]);
     if(localChanged){if(hasLocal)merged[field]=localRecord.entity[field];else delete merged[field];}
    }
    applyLocal(kind,localRecord.id,merged);
    candidateRecord=collectLocal().get(row.record_id);
    const rebased=await encryptLocal({...candidateRecord,baseRevision:Number(row.revision||0)});
    await putOutbox({key:queued.key,operation:rebased,plain:candidateRecord.plain,createdAt:Date.now(),rebasedFrom:queued.operation.operationId});
   }
   pending.push({kind,id:candidateRecord.id,record:candidateRecord,baseRevision:Number(row.revision||0)});
   continue;
  }
  if(row.deleted_at){
   if(localRecord){removeLocal(kind,localRecord.id);local.delete(row.record_id);changed=true;}
   await putMeta({key:"shadow:"+configuration.accountId+":"+row.record_id,plain:"",revision:Number(row.revision||0),deleted:true});
  }else if(decoded){
   applyLocal(kind,decoded.id,decoded.entity);local.set(row.record_id,{...decoded,recordId:row.record_id,dimension:row.dimension,recordType:row.record_type});changed=true;
   await putMeta({key:"shadow:"+configuration.accountId+":"+row.record_id,plain:decoded.plain,revision:Number(row.revision||0),deleted:false});
  }
 }
 for(const [recordId,record] of local){
  if(remoteById.has(recordId))continue;
  pending.push({kind:record.kind,id:record.id,record,baseRevision:0});
 }
 if(changed)configuration.onApply?.();
 const operations=[];
 for(const item of pending){
  if(item.deleted){
   operations.push({operationId:uuid(),recordId:PREFIX[item.kind]+item.id,operation:"delete",dimension:item.dimension||"DAILY",recordType:"tombstone",payloadCiphertext:"",encryptionVersion:1,baseRevision:item.baseRevision,deviceId:configuration.deviceId});
  }else{
   const record={...item.record,baseRevision:item.baseRevision};
   operations.push(await encryptLocal(record));
  }
 }
 const results=await pushOperations(operations);
 const completedDeletes=new Set();
 for(const {operation,result,plain} of results){
  const kind=kindFromRecord({record_id:operation.recordId});
  const localRecord=collectLocal().get(operation.recordId);
  await putMeta({key:"shadow:"+configuration.accountId+":"+operation.recordId,plain:operation.operation==="delete"?"":(plain??localRecord?.plain??""),revision:Number(result.revision||operation.baseRevision+1),deleted:operation.operation==="delete"});
  if(operation.operation==="delete")completedDeletes.add(operation.recordId);
 }
 if(completedDeletes.size)writeDeletionQueue(deletionQueue.filter(x=>!completedDeletes.has(x.recordId)));
 if(operations.length){
  const verify=await pullAll(),map=new Map(verify.map(r=>[r.record_id,r]));
  for(const {operation:op} of results){
   const row=map.get(op.recordId);
   if(!row||Number(row.revision)<Number(op.baseRevision)+1)throw Error("Persistência remota não confirmada para "+op.recordId+".");
  }
 }
 return {pulled:remoteRows.length,pushed:operations.length};
}
async function sync(){
 if(!configuration)return {skipped:true};
 if(running)return running;
 running=(async()=>{
  configuration.status?.("Sincronizando registros criptografados…");
  try{
   const result=await syncOnce();
   configuration.status?.("Sincronização concluída · "+result.pulled+" registros verificados · "+result.pushed+" enviados.");
   return result;
  }catch(error){
   configuration.status?.("Sincronização pendente: "+(error.message||"falha de conexão")+". Os dados locais foram preservados.");
   throw error;
  }finally{running=null;}
 })();
 return running;
}
function schedule(){if(!configuration)return;clearTimeout(timer);timer=setTimeout(()=>sync().catch(()=>{}),1200);}
function configure(options){
 if(!options?.token||!options?.accountId||!options?.deviceId||!(options.dataKey instanceof Uint8Array))throw Error("Sessão ou chave criptográfica incompleta para sincronizar.");
 configuration=options;
}
function clear(){clearTimeout(timer);timer=null;configuration=null;}
window.addEventListener("online",schedule);
document.addEventListener("visibilitychange",()=>{if(!document.hidden)schedule()});
window.MEtanoiaSync=Object.freeze({configure,sync,schedule,markDeleted,clear});
})();