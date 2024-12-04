/*
var CACHE = "offline-cache";

importScripts('https://storage.googleapis.com/workbox-cdn/releases/5.1.2/workbox-sw.js');

var offlineFallbackPage = "offline.html";

self.addEventListener("message", (event) => {
  if (event.data && event.data.type === "SKIP_WAITING") {
    self.skipWaiting();
  }
});

self.addEventListener('install', async (event) => {
  event.waitUntil(
    caches.open(CACHE)
      .then((cache) => cache.add(offlineFallbackPage))
  );
});

if (workbox.navigationPreload.isSupported()) {
  workbox.navigationPreload.enable();
}

workbox.routing.registerRoute(
  new RegExp('/*'),
  new workbox.strategies.StaleWhileRevalidate({
    cacheName: CACHE
  })
);

self.addEventListener('fetch', (event) => {
  if (event.request.mode === 'navigate') {
    event.respondWith((async () => {
      try {
        var preloadResp = await event.preloadResponse;

        if (preloadResp) {
          return preloadResp;
        }

        var networkResp = await fetch(event.request);
        return networkResp;
      } catch (error) {

        var cache = await caches.open(CACHE);
        var cachedResp = await cache.match(offlineFallbackPage);
        return cachedResp;
      }
    })());
  }
});
*/