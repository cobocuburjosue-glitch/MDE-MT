// Service worker de la portada MDE MT: abre sin internet con la última copia.
// Cada módulo (Calibración, Catálogo, Vuelos) tiene su propio service worker en su carpeta.
const CACHE = 'mde-mt-v1';
const BASE = ['./', 'index.html', 'config.js', 'manifest.json', 'logo.png', 'icon-192.png', 'icon-512.png',
  'fondos/dron.webp', 'fondos/t30.webp', 'fondos/catalogo-equipos.jpg',
  'https://fonts.googleapis.com/css2?family=Nunito:wght@500;600;700;800;900&display=swap'];
self.addEventListener('install', e => {
  e.waitUntil(caches.open(CACHE).then(c => Promise.all(BASE.map(u => c.add(u).catch(() => {})))).then(() => self.skipWaiting()));
});
self.addEventListener('activate', e => {
  e.waitUntil(caches.keys().then(ks => Promise.all(ks.filter(k => k.startsWith('mde-mt-') && k !== CACHE).map(k => caches.delete(k)))).then(() => self.clients.claim()));
});
self.addEventListener('fetch', e => {
  const r = e.request;
  if (r.method !== 'GET') return;
  const u = new URL(r.url);
  if (u.hostname.includes('script.google')) return;                  // datos: siempre en línea
  if (/\.apk$|version\.json$/.test(u.pathname)) return;
  const fuente = /fonts\.(googleapis|gstatic)\.com$/.test(u.hostname);
  if (u.origin !== location.origin && !fuente) return;
  e.respondWith(fetch(r).then(res => { if (res.ok || res.type === 'opaque') { const cp = res.clone(); caches.open(CACHE).then(c => c.put(r, cp)); } return res; })
    .catch(() => caches.match(r).then(m => m || (r.mode === 'navigate' ? caches.match('index.html') : Response.error()))));
});
