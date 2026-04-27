/**
 * Persona PWA — Service Worker v6
 *
 * Strategy (FIXED for development & production):
 *   - HTML pages:    Network First → always gets latest code
 *   - CSS / JS:      Stale-While-Revalidate → fast load + background update
 *   - API calls:     Network First with offline cache fallback
 *   - Fonts / CDN:   Cache First (rarely changes)
 *   - Images:        Cache First
 *
 * Bumping CACHE_NAME forces all old caches to be deleted on next activate.
 */

const CACHE_NAME     = 'persona-v6';
const API_CACHE      = 'persona-api-v6';
const FONT_CACHE     = 'persona-fonts-v6';

// Only pre-cache truly static assets (NOT HTML — those are network-first)
const PRECACHE_ASSETS = [
  '/css/style.css',
  '/js/app.js',
  '/js/planner.js',
  '/js/expenses.js',
  '/js/focus_timer.js',
  '/js/habits.js',
  '/js/analytics.js',
  '/js/notifications.js',
  '/js/icons.js',
  '/js/clock-nav.js',
  '/js/home-grid.js',
  '/manifest.json',
];

// HTML routes — always network first
const HTML_ROUTES = [
  '/index.html', '/',
  '/planner.html', '/planner',
  '/assignments.html', '/assignments',
  '/expenses.html', '/expenses',
  '/habits.html', '/habits',
  '/notes.html', '/notes',
  '/analytics.html', '/analytics',
];

// ── Install ────────────────────────────────────────────────────
self.addEventListener('install', event => {
  // skipWaiting so new SW takes over immediately without waiting for tab close
  self.skipWaiting();
  event.waitUntil(
    caches.open(CACHE_NAME).then(cache => {
      // Pre-cache JS/CSS assets — ignore failures so install always succeeds
      return Promise.allSettled(
        PRECACHE_ASSETS.map(url =>
          cache.add(url).catch(e => console.warn('[SW] Pre-cache skip:', url, e.message))
        )
      );
    })
  );
});

// ── Activate ──────────────────────────────────────────────────
self.addEventListener('activate', event => {
  event.waitUntil(
    caches.keys().then(keys =>
      Promise.all(
        keys
          .filter(k => k !== CACHE_NAME && k !== API_CACHE && k !== FONT_CACHE)
          .map(k => {
            console.log('[SW] Deleting old cache:', k);
            return caches.delete(k);
          })
      )
    ).then(() => self.clients.claim())  // claim all open tabs immediately
  );
});

// ── Message handler (from clients) ────────────────────────────
// When a new SW is waiting, the page sends SKIP_WAITING to activate immediately
self.addEventListener('message', event => {
  if (event.data?.type === 'SKIP_WAITING') {
    self.skipWaiting();
  }
});

// ── Fetch ─────────────────────────────────────────────────────
self.addEventListener('fetch', event => {
  const { request } = event;
  const url = new URL(request.url);

  // Only handle GET requests
  if (request.method !== 'GET') return;

  // Skip chrome-extension / non-http requests
  if (!url.protocol.startsWith('http')) return;

  // ── API calls → Network First (with offline cache fallback) ──
  if (url.pathname.startsWith('/api/')) {
    event.respondWith(networkFirst(request, API_CACHE));
    return;
  }

  // ── Fonts / Google Fonts / CDN → Cache First (rarely changes) ─
  if (
    url.hostname === 'fonts.googleapis.com' ||
    url.hostname === 'fonts.gstatic.com' ||
    url.hostname === 'cdn.jsdelivr.net'
  ) {
    event.respondWith(cacheFirst(request, FONT_CACHE));
    return;
  }

  // ── HTML pages → Network First (always latest code) ──────────
  if (
    request.headers.get('Accept')?.includes('text/html') ||
    url.pathname.endsWith('.html') ||
    url.pathname === '/' ||
    !url.pathname.includes('.')  // SPA-style paths
  ) {
    event.respondWith(networkFirstHtml(request));
    return;
  }

  // ── CSS / JS → Stale-While-Revalidate (fast + updates in bg) ─
  if (url.pathname.endsWith('.css') || url.pathname.endsWith('.js')) {
    event.respondWith(staleWhileRevalidate(request));
    return;
  }

  // ── Everything else → Cache First ────────────────────────────
  event.respondWith(cacheFirst(request, CACHE_NAME));
});

// ── Strategies ────────────────────────────────────────────────

/**
 * Network First for HTML — always fetch latest, cache as fallback
 */
async function networkFirstHtml(request) {
  try {
    const response = await fetch(request, { cache: 'no-cache' });
    if (response.ok) {
      const cache = await caches.open(CACHE_NAME);
      cache.put(request, response.clone());
    }
    return response;
  } catch (_) {
    const cached = await caches.match(request);
    return cached || caches.match('/index.html') || new Response(
      '<h1>Offline</h1><p>Connect to the internet to load Persona.</p>',
      { status: 503, headers: { 'Content-Type': 'text/html' } }
    );
  }
}

/**
 * Stale-While-Revalidate — return cached immediately, update in background
 */
async function staleWhileRevalidate(request) {
  const cache  = await caches.open(CACHE_NAME);
  const cached = await cache.match(request);

  // Fire network request regardless (background update)
  const fetchPromise = fetch(request).then(response => {
    if (response.ok) cache.put(request, response.clone());
    return response;
  }).catch(() => null);

  return cached || fetchPromise;
}

/**
 * Network First for API — fetch, fallback to cache offline
 */
async function networkFirst(request, cacheName) {
  try {
    const response = await fetch(request);
    if (response.ok) {
      const cache = await caches.open(cacheName);
      cache.put(request, response.clone());
    }
    return response;
  } catch (_) {
    const cached = await caches.match(request);
    return cached || new Response(JSON.stringify({ error: 'Offline — no connection' }), {
      status: 503,
      headers: { 'Content-Type': 'application/json' },
    });
  }
}

/**
 * Cache First — for fonts & images
 */
async function cacheFirst(request, cacheName = CACHE_NAME) {
  const cache  = await caches.open(cacheName);
  const cached = await cache.match(request);
  if (cached) return cached;
  try {
    const response = await fetch(request);
    if (response.ok) cache.put(request, response.clone());
    return response;
  } catch (_) {
    return new Response('Offline', { status: 503, headers: { 'Content-Type': 'text/plain' } });
  }
}

// ── Push Notifications ─────────────────────────────────────────
self.addEventListener('push', event => {
  let data = { title: 'Persona', body: 'You have a new notification' };
  try { data = event.data.json(); } catch (_) {}

  event.waitUntil(
    self.registration.showNotification(data.title || 'Persona', {
      body:    data.body || '',
      icon:    '/icons/icon-192.png',
      badge:   '/icons/icon-72.png',
      vibrate: [200, 100, 200],
      data:    { url: data.url || '/' },
      actions: [
        { action: 'open',    title: 'Open App' },
        { action: 'dismiss', title: 'Dismiss'  },
      ],
    })
  );
});

// ── Notification Click ─────────────────────────────────────────
self.addEventListener('notificationclick', event => {
  event.notification.close();
  if (event.action === 'dismiss') return;
  const url = event.notification.data?.url || '/';
  event.waitUntil(clients.openWindow(url));
});

// ── Background Sync ────────────────────────────────────────────
self.addEventListener('sync', event => {
  if (event.tag === 'sync-tasks') {
    event.waitUntil(syncOfflineTasks());
  }
});

async function syncOfflineTasks() {
  console.log('[SW] Background sync triggered for tasks');
}
