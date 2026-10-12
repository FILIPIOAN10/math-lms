/*
 * Math LMS service worker (E11 PWA). Deliberately small, and deliberately NOT an offline copy of the app:
 *   - /api, sign-in and /actuator are never touched: answers, grades and a minor's photos stay out of any cache,
 *     and every request still reaches the server with its cookies (the browser handles them as if there were no worker)
 *   - pages (navigations) always come from the network, so a deploy is visible on the next click; only when the network
 *     is down does the "Ești offline" page answer
 *   - /assets/* (Vite's content-hashed JS/CSS: a new build means new names) come from the cache once seen
 * The file name never changes and nginx serves it with Cache-Control: no-cache, so the browser checks it on every visit.
 * Changing anything below? Bump VERSION so the old caches are dropped.
 */
const VERSION = 'v1'
const SHELL_CACHE = `math-lms-shell-${VERSION}`
const ASSET_CACHE = `math-lms-assets-${VERSION}`
const OFFLINE_URL = '/offline.html'
const PRECACHE = [OFFLINE_URL, '/icon-192.png', '/favicon-32.png']
const NETWORK_ONLY = ['/api/', '/oauth2/', '/login/oauth2/', '/actuator/']
const MAX_ASSETS = 80

self.addEventListener('install', (event) => {
  event.waitUntil(caches.open(SHELL_CACHE).then((cache) => cache.addAll(PRECACHE)))
  self.skipWaiting() // nothing here can break a page already open: take over straight away
})

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((names) => Promise.all(names.filter((n) => n !== SHELL_CACHE && n !== ASSET_CACHE).map((n) => caches.delete(n))))
      .then(() => self.clients.claim()),
  )
})

self.addEventListener('fetch', (event) => {
  const request = event.request
  if (request.method !== 'GET') return
  const url = new URL(request.url)
  if (url.origin !== self.location.origin) return
  if (NETWORK_ONLY.some((prefix) => url.pathname.startsWith(prefix))) return

  if (request.mode === 'navigate') {
    event.respondWith(fetch(request).catch(() => caches.match(OFFLINE_URL)))
    return
  }
  if (url.pathname.startsWith('/assets/')) {
    event.respondWith(cacheFirst(request))
  }
  // anything else (icons, the manifest): the browser's normal handling
})

async function cacheFirst(request) {
  const cache = await caches.open(ASSET_CACHE)
  const hit = await cache.match(request)
  if (hit) return hit
  const response = await fetch(request)
  if (response.ok) {
    await cache.put(request, response.clone())
    await trim(cache)
  }
  return response
}

/** Old builds' files pile up: keep the newest MAX_ASSETS (the cache keeps insertion order). */
async function trim(cache) {
  const keys = await cache.keys()
  await Promise.all(keys.slice(0, Math.max(0, keys.length - MAX_ASSETS)).map((key) => cache.delete(key)))
}
