import fs from "node:fs";
import { spawnSync } from "node:child_process";

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
config.d1_databases=(config.d1_databases||[]).map(item=>item.binding==="DB"?{...item,database_name:"metanoia-preview",database_id:database.uuid}:item);
if(config.d1_databases.filter(x=>x.binding==="DB"&&x.database_id===database.uuid).length!==1)throw new Error("Preview D1 binding could not be isolated from production.");
fs.writeFileSync("wrangler.preview.jsonc",JSON.stringify(config,null,2)+"\n");
function run(args){
 const result=spawnSync("wrangler",args,{encoding:"utf8",stdio:"inherit",env:process.env});
 if(result.status!==0)throw new Error("wrangler "+args.join(" ")+" failed with exit "+result.status);
}
run(["d1","migrations","apply","metanoia-preview","--remote","--config","wrangler.preview.jsonc"]);
if(process.env.METANOIA_REGISTRATION_KEY){
 const result=spawnSync("wrangler",["secret","put","METANOIA_REGISTRATION_KEY","--config","wrangler.preview.jsonc"],{encoding:"utf8",input:process.env.METANOIA_REGISTRATION_KEY+"\n",stdio:["pipe","inherit","inherit"],env:process.env});
 if(result.status!==0)throw new Error("Unable to configure the optional preview invitation secret.");
}
run(["deploy","--config","wrangler.preview.jsonc"]);
const subdomainResult=await cf("/accounts/"+accountId+"/workers/subdomain");
const subdomain=subdomainResult?.subdomain;
if(!subdomain)throw new Error("Worker was deployed but Cloudflare did not return an active workers.dev subdomain.");
const url="https://metanoia-preview."+subdomain+".workers.dev";
const page=await fetch(url,{redirect:"follow",cache:"no-store"});
const html=await page.text();
if(!page.ok||!html.includes("MEtanoia")||!html.includes("/sync.js"))throw new Error("Preview smoke test failed: root page did not return the expected MEtanoia PWA.");
const login=await fetch(url+"/api/auth/login",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({})});
if(login.status!==401)throw new Error("Preview login route smoke test expected HTTP 401 for empty credentials; received "+login.status+".");
const registrationEnabled=!!process.env.METANOIA_REGISTRATION_KEY;
const summary=[
 "### MEtanoia isolated Cloudflare Preview",
 "URL: "+url,
 "Worker: metanoia-preview",
 "Preview D1: "+database.uuid,
 "PWA root: HTTP "+page.status,
 "Login endpoint: HTTP "+login.status+" for intentionally empty credentials",
 "New-account registration: "+(registrationEnabled?"invitation secret configured":"closed (METANOIA_REGISTRATION_KEY absent)"),
 "Production Worker and production D1 were not modified."
].join("\n");
fs.appendFileSync(process.env.GITHUB_STEP_SUMMARY,summary+"\n");
console.log("PREVIEW_URL="+url);
