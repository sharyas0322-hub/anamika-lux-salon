const CACHE_NAME="anamika-lux-v1";
const CORE=["./","./index.html","./portal.html","./manifest.webmanifest","./the-anamika-lux-logo.png"];
self.addEventListener("install",event=>{event.waitUntil(caches.open(CACHE_NAME).then(c=>c.addAll(CORE).catch(()=>{})).then(()=>self.skipWaiting()))});
self.addEventListener("activate",event=>{event.waitUntil(caches.keys().then(keys=>Promise.all(keys.filter(k=>k!==CACHE_NAME).map(k=>caches.delete(k)))).then(()=>self.clients.claim()))});
self.addEventListener("fetch",event=>{
  const req=event.request;
  if(req.method!=="GET" || new URL(req.url).origin!==location.origin) return;
  event.respondWith(caches.match(req).then(cached=>cached||fetch(req).then(res=>{
    const copy=res.clone(); caches.open(CACHE_NAME).then(c=>c.put(req,copy)).catch(()=>{}); return res;
  }).catch(()=>caches.match("./index.html"))));
});