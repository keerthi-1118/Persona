/**
 * Persona PWA — app.js
 * Global utilities: API client, auth, modals, toasts, sidebar
 */

// Use relative path — Flask serves frontend + API on the same origin
const API_BASE = '/api';

// ── API Client ─────────────────────────────────────────────────
const API = {
  _fetch: async (method, path, body) => {
    const opts = {
      method,
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
    };
    if (body) opts.body = JSON.stringify(body);
    const res = await fetch(API_BASE + path, opts);
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      throw new Error(err.error || `HTTP ${res.status}`);
    }
    return res.json();
  },
  get:   (path)         => API._fetch('GET',    path),
  post:  (path, body)   => API._fetch('POST',   path, body),
  put:   (path, body)   => API._fetch('PUT',    path, body),
  patch: (path, body)   => API._fetch('PATCH',  path, body),
  del:   (path)         => API._fetch('DELETE', path),
};

// ── Init (called on every page) ────────────────────────────────
async function initApp() {
  setActiveNav();
  await loadUser();
}

async function loadUser() {
  try {
    const user = await API.get('/auth/me');
    const init = (user.username || 'S')[0].toUpperCase();
    const avatar = document.getElementById('user-avatar');
    const name   = document.getElementById('user-name');
    if (avatar) avatar.textContent = init;
    if (name)   name.textContent   = user.username;
  } catch (e) {
    // Not logged in — allow guest mode for dev, or redirect:
    // window.location.href = 'login.html';
  }
}

function setActiveNav() {
  const path = window.location.pathname.split('/').pop() || 'index.html';
  document.querySelectorAll('.nav-link').forEach(link => {
    const href = link.getAttribute('href');
    if (href === path) link.classList.add('active');
    else link.classList.remove('active');
  });
}

async function logout() {
  try { await API.post('/auth/logout', {}); } catch (_) {}
  window.location.href = 'index.html';
}

// ── Modal helpers ──────────────────────────────────────────────
function openModal(id) {
  const el = document.getElementById(id);
  if (el) el.classList.add('show');
}

function closeModal(id) {
  const el = document.getElementById(id);
  if (el) el.classList.remove('show');
}

// Close modal on backdrop click
document.addEventListener('click', e => {
  if (e.target.classList.contains('modal-backdrop')) {
    e.target.classList.remove('show');
  }
});

// ── Toast notifications ────────────────────────────────────────
function showToast(type, title, msg) {
  const icons = {
    success: '<svg class="line-icon" style="width:16px;height:16px;stroke:var(--green)"><use href="#icon-check-circle"></use></svg>',
    error: '<svg class="line-icon" style="width:16px;height:16px;stroke:var(--red)"><use href="#icon-alert"></use></svg>',
    info: '<svg class="line-icon" style="width:16px;height:16px;stroke:var(--sky)"><use href="#icon-bot"></use></svg>',
    warning: '<svg class="line-icon" style="width:16px;height:16px;stroke:var(--amber)"><use href="#icon-alert"></use></svg>'
  };
  const container = document.getElementById('toast-container');
  if (!container) return;
  const toast = document.createElement('div');
  toast.className = `toast toast-${type}`;
  toast.innerHTML = `
    <span class="toast-icon">${icons[type] || 'ℹ️'}</span>
    <div class="toast-body">
      <div class="toast-title">${title}</div>
      ${msg ? `<div class="toast-msg">${msg}</div>` : ''}
    </div>
    <button class="toast-close" onclick="this.closest('.toast').remove()"><svg class="line-icon" style="width:16px"><use href="#icon-x"></use></svg></button>`;
  container.appendChild(toast);
  setTimeout(() => toast.remove(), 4000);
}

// ── Sidebar toggle (mobile) ────────────────────────────────────
function toggleSidebar() {
  const sb  = document.getElementById('sidebar');
  const ov  = document.getElementById('sidebar-overlay');
  const isOpen = sb.classList.toggle('open');
  if (ov) ov.classList.toggle('show', isOpen);
}

// ── HTML escape helper ─────────────────────────────────────────
function esc(str) {
  if (!str) return '';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

// ── Register Service Worker ────────────────────────────────────
if ('serviceWorker' in navigator) {
  window.addEventListener('load', async () => {
    try {
      const reg = await navigator.serviceWorker.register('/service-worker.js', {
        // Always bypass the HTTP cache when fetching the SW script itself
        updateViaCache: 'none',
      });

      // Check for SW updates immediately on every page load
      reg.update().catch(() => {});

      // When a new SW installs (waiting), tell it to skip waiting
      reg.addEventListener('updatefound', () => {
        const newWorker = reg.installing;
        newWorker?.addEventListener('statechange', () => {
          if (newWorker.statechange === 'installed' && navigator.serviceWorker.controller) {
            newWorker.postMessage({ type: 'SKIP_WAITING' });
          }
        });
      });

      // When the SW controller changes (new SW took over), reload all tabs
      let refreshing = false;
      navigator.serviceWorker.addEventListener('controllerchange', () => {
        if (!refreshing) {
          refreshing = true;
          window.location.reload();
        }
      });
    } catch (e) {
      console.warn('[SW] Registration failed:', e);
    }
  });
}
