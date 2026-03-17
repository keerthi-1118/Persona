/**
 * Persona — notifications.js
 * Web Push Notification setup using VAPID
 */

const VAPID_PUBLIC_KEY = 'YOUR_VAPID_PUBLIC_KEY'; // Replace from .env

async function requestNotificationPermission() {
  if (!('Notification' in window)) {
    console.warn('Notifications not supported');
    return false;
  }
  if (Notification.permission === 'granted') return true;
  const perm = await Notification.requestPermission();
  return perm === 'granted';
}

async function subscribeToPush() {
  const granted = await requestNotificationPermission();
  if (!granted) {
    showToast('warning', 'Notifications Blocked', 'Please allow notifications in browser settings');
    return;
  }

  const reg = await navigator.serviceWorker.ready;
  const existing = await reg.pushManager.getSubscription();
  if (existing) return existing;

  const subscription = await reg.pushManager.subscribe({
    userVisibleOnly: true,
    applicationServerKey: urlBase64ToUint8Array(VAPID_PUBLIC_KEY),
  });

  // Send subscription to backend
  await API.post('/push/subscribe', subscription.toJSON());
  showToast('success', 'Notifications On!', 'You will receive smart reminders');
  return subscription;
}

function urlBase64ToUint8Array(base64String) {
  const padding = '='.repeat((4 - base64String.length % 4) % 4);
  const base64  = (base64String + padding).replace(/-/g, '+').replace(/_/g, '/');
  const rawData = window.atob(base64);
  return new Uint8Array([...rawData].map(c => c.charCodeAt(0)));
}

// Schedule a local notification reminder (for tasks without push)
function scheduleLocalReminder(title, message, delayMs) {
  if (Notification.permission !== 'granted') return;
  setTimeout(() => {
    new Notification('Persona — ' + title, {
      body: message,
      icon: '/icons/icon-192.png',
      badge: '/icons/icon-72.png',
      vibrate: [200, 100, 200],
    });
  }, delayMs);
}

// Called from planner to set a reminder for a task
function setTaskReminder(task) {
  if (!task.start_time) return;
  const startMs = new Date(task.start_time).getTime();
  const nowMs   = Date.now();
  const remindMs = startMs - 10 * 60 * 1000; // 10 min before

  if (remindMs > nowMs) {
    scheduleLocalReminder(
      task.title,
      `Starting in 10 minutes — get ready!`,
      remindMs - nowMs,
    );
    scheduleLocalReminder(
      task.title,
      `⏰ Time to start: ${task.title}`,
      startMs - nowMs,
    );
  }
}
