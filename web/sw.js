const CACHE="metonoia-shell-v38";
const ASSETS=["/","/index.html","/app.js?v=20261104-38","/manifest.webmanifest","/icons/metonoia.svg","/icons/ingrid-avatar.svg"];
self.addEventListener("install",event=>{
 event.waitUntil(caches.open(CACHE).then(cache=>cache.addAll(ASSETS)).then(()=>self.skipWaiting()));
});
self.addEventListener("activate",event=>{
 event.waitUntil(caches.keys().then(keys=>Promise.all(keys.filter(key=>key!==CACHE).map(key=>caches.delete(key)))).then(()=>self.clients.claim()));
});
self.addEventListener("fetch",event=>{
 if(event.request.method!=="GET")return;
 const url=new URL(event.request.url);
 const critical=event.request.mode==="navigate"||url.pathname==="/app.js"||url.pathname==="/index.html";
 if(critical){
  event.respondWith(fetch(event.request,{cache:"no-store"}).then(response=>{
   if(response.ok){const copy=response.clone();caches.open(CACHE).then(cache=>cache.put(event.request,copy));}
   return response;
  }).catch(()=>caches.match(event.request).then(cached=>cached||caches.match("/index.html"))));
  return;
 }
 event.respondWith(caches.match(event.request).then(cached=>cached||fetch(event.request).then(response=>{
  if(response.ok)caches.open(CACHE).then(cache=>cache.put(event.request,response.clone()));
  return response;
 }).catch(()=>caches.match("/index.html"))));
});
self.addEventListener("notificationclick",event=>{
 event.notification.close();
 event.waitUntil(clients.matchAll({type:"window",includeUncontrolled:true}).then(list=>{
  for(const client of list){if("focus"in client)return client.focus();}
  return clients.openWindow("/");
 }));
});