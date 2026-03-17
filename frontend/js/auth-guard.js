/**
 * auth-guard.js  —  Persona PWA
 *
 * Runs on every page (except login / signup themselves).
 * • Checks session with /api/auth/me
 * • If 401 → redirect to /login
 * • If authenticated → set window.currentUser and update avatar
 */
(function () {
  'use strict';

  const PUBLIC_PATHS = ['/login', '/signup', '/login.html', '/signup.html'];

  const path = window.location.pathname;
  // Don't guard login/signup pages
  if (PUBLIC_PATHS.some(p => path.startsWith(p))) return;

  async function checkAuth() {
    try {
      const res = await fetch('/api/auth/me', { credentials: 'include' });
      if (res.status === 401) {
        window.location.href = '/login';
        return;
      }
      if (!res.ok) return;   // server error — don't block user

      const user = await res.json();
      window.currentUser = user;
      updateAvatar(user);
    } catch {
      // Network error — don't redirect, let other pages handle it
    }
  }

  function updateAvatar(user) {
    // Update the 'S' avatar button with the user's real initial
    const btn = document.getElementById('user-avatar-btn');
    if (!btn) return;

    if (user.avatar_url) {
      btn.innerHTML = `<img src="${user.avatar_url}" alt="${user.username}" style="width:32px;height:32px;border-radius:50%;object-fit:cover;"/>`;
      btn.style.padding = '0';
    } else {
      btn.textContent = (user.username || user.email || 'U')[0].toUpperCase();
    }

    btn.title = `${user.username || user.email} — tap to sign out`;

    btn.addEventListener('click', async () => {
      if (!confirm('Sign out of Persona?')) return;
      await fetch('/api/auth/logout', { method: 'POST', credentials: 'include' });
      window.location.href = '/login';
    });
  }

  // Run as soon as DOM is ready
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', checkAuth);
  } else {
    checkAuth();
  }
})();
