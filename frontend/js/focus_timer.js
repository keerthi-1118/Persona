/**
 * Persona — focus_timer.js
 * Pomodoro timer with ring animation and desktop notifications
 */

const MODES = {
  focus: { label: 'Focus',      duration: 25 * 60, color: '#7C6FFF' },
  short: { label: 'Short Break', duration:  5 * 60, color: '#48E5A0' },
  long:  { label: 'Long Break',  duration: 15 * 60, color: '#00D1B2' },
};

let currentMode    = 'focus';
let remaining      = MODES.focus.duration;
let isRunning      = false;
let timerInterval  = null;
let sessionId      = null;

const CIRCUMFERENCE = 2 * Math.PI * 70; // r=70

function setTimerMode(mode, btn) {
  if (isRunning) return;
  currentMode = mode;
  remaining   = MODES[mode].duration;
  document.querySelectorAll('#timer-btn').forEach(() => {});
  if (btn) {
    btn.closest('.timer-pills')?.querySelectorAll('.timer-pill').forEach(b => b.classList.remove('active'));
    btn.classList.add('active');
  }
  updateDisplay();
}

function updateDisplay() {
  const mins = Math.floor(remaining / 60);
  const secs = remaining % 60;
  const el   = document.getElementById('timer-display');
  const label= document.getElementById('timer-mode-label');
  const circle = document.getElementById('timer-circle');
  const btn  = document.getElementById('timer-btn');

  if (el) el.textContent = `${String(mins).padStart(2,'0')}:${String(secs).padStart(2,'0')}`;
  if (label) label.textContent = MODES[currentMode].label;
  if (btn)   btn.textContent   = isRunning ? '⏸ Pause' : '▶ Start';

  if (circle) {
    const total    = MODES[currentMode].duration;
    const progress = remaining / total;
    const offset   = CIRCUMFERENCE * (1 - progress);
    circle.style.strokeDashoffset = offset;
    circle.style.stroke           = MODES[currentMode].color;
  }
}

function toggleTimer() {
  if (isRunning) {
    pauseTimer();
  } else {
    startTimer();
  }
}

async function startTimer() {
  isRunning = true;
  // Start session in backend
  try {
    const res = await API.post('/focus/start', {
      duration: Math.floor(MODES[currentMode].duration / 60),
      type: currentMode === 'focus' ? 'focus' : currentMode === 'short' ? 'short_break' : 'long_break',
    });
    sessionId = res.session_id;
  } catch (_) {}

  timerInterval = setInterval(() => {
    remaining--;
    updateDisplay();
    if (remaining <= 0) {
      timerComplete();
    }
  }, 1000);
  updateDisplay();
}

function pauseTimer() {
  isRunning = false;
  clearInterval(timerInterval);
  updateDisplay();
}

function resetTimer() {
  isRunning = false;
  clearInterval(timerInterval);
  remaining = MODES[currentMode].duration;
  sessionId = null;
  updateDisplay();
}

async function timerComplete() {
  isRunning = false;
  clearInterval(timerInterval);

  // End session in backend
  if (sessionId) {
    try { await API.patch('/focus/' + sessionId + '/end', { completed: true }); } catch (_) {}
    sessionId = null;
  }

  // Desktop notification
  const msg = currentMode === 'focus'
    ? '🎉 Focus session complete! Take a break.'
    : '⚡ Break over! Ready to focus?';

  if (Notification.permission === 'granted') {
    new Notification('Persona Timer', { body: msg, icon: '/icons/icon-192.png' });
  } else {
    if (typeof showToast === 'function') showToast('success', 'Timer Done!', msg);
  }

  // Auto switch mode
  if (currentMode === 'focus') {
    setTimerMode('short', null);
  } else {
    setTimerMode('focus', null);
  }
}

// Initialise on page load
document.addEventListener('DOMContentLoaded', () => {
  updateDisplay();
  // Request notification permission
  if ('Notification' in window && Notification.permission === 'default') {
    Notification.requestPermission();
  }
});
