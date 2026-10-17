// Folio PDF — service worker: guarda la app para usarla sin conexión.
const VERSION = "folio-v3";
const ARCHIVOS = [
  "./", "index.html", "manifest.webmanifest",
  "lib/pdf.min.js", "lib/pdf.worker.min.js", "lib/pdf-lib.min.js",
  "fonts/instrument-sans-latin-400-normal.woff2", "fonts/instrument-sans-latin-500-normal.woff2",
  "fonts/instrument-sans-latin-600-normal.woff2", "fonts/instrument-sans-latin-700-normal.woff2",
  "fonts/jetbrains-mono-latin-500-normal.woff2",
  "icons/icon-192.png", "icons/icon-512.png", "icons/maskable-512.png", "icons/apple-touch-icon.png"
];

self.addEventListener("install", e => {
  e.waitUntil(caches.open(VERSION).then(c => c.addAll(ARCHIVOS)).then(() => self.skipWaiting()));
});

self.addEventListener("activate", e => {
  e.waitUntil(
    caches.keys()
      .then(ks => Promise.all(ks.filter(k => k !== VERSION && k !== "folio-compartido").map(k => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener("fetch", e => {
  const url = new URL(e.request.url);

  // PDF compartido desde otra app (Android: menú Compartir → Folio PDF)
  if (e.request.method === "POST" && url.pathname.endsWith("/compartir")) {
    e.respondWith((async () => {
      try {
        const form = await e.request.formData();
        const f = form.get("archivo");
        if (f) {
          const c = await caches.open("folio-compartido");
          await c.put("archivo-compartido", new Response(f, {
            headers: { "content-type": "application/pdf", "x-nombre": encodeURIComponent(f.name || "compartido.pdf") }
          }));
        }
      } catch (_) {}
      return Response.redirect("./?compartido=1", 303);
    })());
    return;
  }

  if (e.request.method !== "GET" || url.origin !== location.origin) return;

  // Página: red primero (para recibir actualizaciones), caché si no hay conexión
  if (e.request.mode === "navigate") {
    e.respondWith(
      fetch(e.request)
        .then(r => { const copia = r.clone(); caches.open(VERSION).then(c => c.put("index.html", copia)); return r; })
        .catch(() => caches.match("index.html"))
    );
    return;
  }

  // Recursos: caché primero
  e.respondWith(
    caches.match(e.request, { ignoreSearch: true }).then(r => r || fetch(e.request).then(res => {
      if (res.ok) { const copia = res.clone(); caches.open(VERSION).then(c => c.put(e.request, copia)); }
      return res;
    }))
  );
});
