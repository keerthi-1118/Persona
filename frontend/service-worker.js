/**
 * Persona PWA — Service Worker
 * Strategy:
 *   - Static assets: Cache First (CSS, JS, HTML, fonts)
 *   - API calls:     Network First with cache fallback
 *   - Images:        Cache First with stale-while-revalidate
 */

const CACHE_NAME    = 'persona-v1';
const API_CACHE     = 'persona-api-v1';

const STATIC_ASSETS = [
  '/index.html',
  '/planner.html',
  '/assignments.html',
  '/expenses.html',
  '/habits.html',
  '/notes.html',
  '/analytics.html',
  '/css/style.css',
  '/js/app.js',
  '/js/planner.js',
  '/js/expenses.js',
  '/js/focus_timer.js',
  '/js/habits.js',
  '/js/analytics.js',
  '/js/notifications.js',
  '/manifest.json',
  'https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700;800&display=swap',
  'https://cdn.jsdelivr.net/npm/chart.js@4.4.2/dist/chart.umd.min.js',
];

// ── Install ────────────────────────────────────────────────────
self.addEventListener('install', event => {
  event.waitUntil(
    caches.open(CACHE_NAME).then(cache => cache.addAll(STATIC_ASSETS))
  );
  self.skipWaiting();
});

// ── Activate ──────────────────────────────────────────────────
self.addEventListener('activate', event => {
  event.waitUntil(
    caches.keys().then(keys =>
      Promise.all(
        keys
          .filter(k => k !== CACHE_NAME && k !== API_CACHE)
          .map(k => caches.delete(k))
      )
    )
  );
  self.clients.claim();
});

// ── Fetch ─────────────────────────────────────────────────────
self.addEventListener('fetch', event => {
  const { request } = event;
  const url = new URL(request.url);

  // API requests: Network First
  if (url.pathname.startsWith('/api/')) {
    event.respondWith(networkFirst(request, API_CACHE));
    return;
  }

  // Static assets: Cache First
  event.respondWith(cacheFirst(request));
});

async function cacheFirst(request) {
  const cached = await caches.match(request);
  if (cached) return cached;
  try {
    const response = await fetch(request);
    if (response.ok) {
      const cache = await caches.open(CACHE_NAME);
      cache.put(request, response.clone());
    }
    return response;
  } catch (_) {
    return new Response('Offline — no cached version available', {
      status: 503,
      headers: { 'Content-Type': 'text/plain' },
    });
  }
}

async function networkFirst(request, cacheName) {
  try {
    const response = await fetch(request);
    if (response.ok) {
      const cache = await caches.open(cacheName);
      if (request.method === 'GET') {
        cache.put(request, response.clone());
      }
    }
    return response;
  } catch (_) {
    const cached = await caches.match(request);
    return cached || new Response(JSON.stringify({ error: 'Offline' }), {
      status: 503,
      headers: { 'Content-Type': 'application/json' },
    });
  }
}

// ── Push Notifications ─────────────────────────────────────────
self.addEventListener('push', event => {
  let data = { title: 'Persona', body: 'You have a new notification' };
  try { data = event.data.json(); } catch (_) {}

  event.waitUntil(
    self.registration.showNotification(data.title || 'Persona', {
      body:    data.body    || '',
      icon:    '/frontend/icons/icon-192.png',
      badge:   '/frontend/icons/icon-72.png',
      vibrate: [200, 100, 200],
      data:    { url: data.url || '/frontend/index.html' },
      actions: [
        { action: 'open',    title: 'Open App' },
        { action: 'dismiss', title: 'Dismiss' },
      ],
    })
  );
});

// ── Notification Click ─────────────────────────────────────────
self.addEventListener('notificationclick', event => {
  event.notification.close();
  if (event.action === 'dismiss') return;
  const url = event.notification.data?.url || '/frontend/index.html';
  event.waitUntil(clients.openWindow(url));
});

// ── Background Sync (offline form submissions) ─────────────────
self.addEventListener('sync', event => {
  if (event.tag === 'sync-tasks') {
    event.waitUntil(syncOfflineTasks());
  }
});

async function syncOfflineTasks() {
  // Placeholder: retrieve pending offline requests from IndexedDB and send them
  console.log('[SW] Background sync triggered for tasks');
}
