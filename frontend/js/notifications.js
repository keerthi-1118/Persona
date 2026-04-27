/**
 * Persona — notifications.js
 * Smart Reminders: local Notification API + Web Push via VAPID
 *
 * Features:
 *   - requestNotificationPermission() — ask for browser permission
 *   - scheduleLocalReminder() — schedule a one-shot local notification
 *   - setTaskReminder() — auto-schedule 10-min-before + at-start reminders
 *   - subscribeToPush() — subscribe to server-sent push notifications
 *
 * NOTE: VAPID_PUBLIC_KEY is loaded from /api/push/vapid-public-key at runtime.
 */

let VAPID_PUBLIC_KEY = ''; // Loaded from backend

// ── Permission ────────────────────────────────────────────────
async function requestNotificationPermission() {
  if (!('Notification' in window)) return false;
  if (Notification.permission === 'granted') {
    // Try to auto-subscribe to push on first grant
    await _tryPushSubscribe();
    return true;
  }
  if (Notification.permission === 'denied') return false;

  const perm = await Notification.requestPermission();
  if (perm === 'granted') {
    await _tryPushSubscribe();
    return true;
  }
  return false;
}

// ── Push Subscribe ────────────────────────────────────────────
async function subscribeToPush() {
  const granted = await requestNotificationPermission();
  if (!granted) {
    showToast('warning', 'Notifications Blocked', 'Please allow notifications in browser settings');
    return null;
  }
  return _tryPushSubscribe();
}

async function _tryPushSubscribe() {
  if (!('serviceWorker' in navigator) || !('PushManager' in window)) return null;

  try {
    // Load VAPID public key from backend (avoids hardcoding)
    if (!VAPID_PUBLIC_KEY) {
      const resp = await fetch('/api/push/vapid-public-key');
      if (resp.ok) {
        const data = await resp.json();
        VAPID_PUBLIC_KEY = data.public_key || '';
      }
    }
    if (!VAPID_PUBLIC_KEY) return null; // Not configured

    const reg = await navigator.serviceWorker.ready;
    const existing = await reg.pushManager.getSubscription();
    if (existing) return existing;

    const subscription = await reg.pushManager.subscribe({
      userVisibleOnly: true,
      applicationServerKey: _urlBase64ToUint8Array(VAPID_PUBLIC_KEY),
    });

    // Register subscription with backend
    await fetch('/api/push/subscribe', {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(subscription.toJSON()),
    });

    return subscription;
  } catch (e) {
    console.warn('[Notifications] Push subscribe failed:', e.message);
    return null;
  }
}

function _urlBase64ToUint8Array(base64String) {
  const padding = '='.repeat((4 - base64String.length % 4) % 4);
  const base64  = (base64String + padding).replace(/-/g, '+').replace(/_/g, '/');
  const rawData = window.atob(base64);
  return new Uint8Array([...rawData].map(c => c.charCodeAt(0)));
}

// ── Local Notifications ────────────────────────────────────────
/**
 * Schedule a one-shot local notification after `delayMs` milliseconds.
 * Falls back silently if permission not granted.
 */
function scheduleLocalReminder(title, message, delayMs) {
  if (!('Notification' in window) || Notification.permission !== 'granted') return;
  if (delayMs < 0) return; // Already past

  setTimeout(() => {
    try {
      new Notification('Persona — ' + title, {
        body:    message,
        icon:    '/icons/icon-192.png',
        badge:   '/icons/icon-72.png',
        vibrate: [200, 100, 200],
        tag:     'persona-reminder-' + title.replace(/\s+/g, '-'),
      });
    } catch (e) {
      console.warn('[Notifications] Local notification failed:', e);
    }
  }, delayMs);
}

/**
 * Called after tasks are loaded.
 * Registers two local reminders per task:
 *   1. 10 minutes before start
 *   2. Exactly at start time
 */
function setTaskReminder(task) {
  if (!task || !task.start_time) return;

  const startMs  = new Date(task.start_time).getTime();
  const nowMs    = Date.now();
  const remind10 = startMs - 10 * 60 * 1000; // 10 min before

  // 10-minute warning
  if (remind10 > nowMs) {
    scheduleLocalReminder(
      task.title,
      `⏰ Starting in 10 minutes — get ready!`,
      remind10 - nowMs
    );
  }

  // At start time
  if (startMs > nowMs) {
    scheduleLocalReminder(
      task.title,
      `🚀 Time to start: ${task.title}`,
      startMs - nowMs
    );
  }
}
