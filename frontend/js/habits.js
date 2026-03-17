/**
 * Persona — habits.js
 */

let allHabits = [];

async function loadHabits() {
  try {
    allHabits = await API.get('/habits/');
    updateHabitStats();
    renderHabits();
  } catch(e) {
    showToast('error', 'Error', e.message);
  }
}

function updateHabitStats() {
  const doneToday = allHabits.filter(h => h.done_today).length;
  if (document.getElementById('h-total'))    document.getElementById('h-total').textContent    = allHabits.length;
  if (document.getElementById('h-done'))     document.getElementById('h-done').textContent     = doneToday;
  if (document.getElementById('h-done-today')) document.getElementById('h-done-today').textContent = doneToday;

  // Progress bar
  const pct = allHabits.length ? Math.round((doneToday / allHabits.length) * 100) : 0;
  const label = document.getElementById('habit-progress-label') || document.getElementById('h-progress-label');
  if (label) label.textContent = `${doneToday} / ${allHabits.length}`;
  const bar = document.getElementById('habit-progress-fill') || document.getElementById('h-progress-bar');
  if (bar) bar.style.width = pct + '%';
}

function renderHabits() {
  const el = document.getElementById('habit-list');
  if (!el) return;
  if (!allHabits.length) {
    el.innerHTML = `<div class="empty-state" style="padding:20px 0"><div class="empty-icon sticker sticker-lg"><svg class="line-icon"><use href="#icon-ribbon"></use></svg></div><h3>No habits yet</h3><p>Start building good habits today</p></div>`;
    return;
  }
  el.innerHTML = allHabits.map(h => `
    <div class="habit-row" id="hrow-${h.id}">
      <div class="habit-icon">${h.icon && h.icon.includes('<svg') ? h.icon : '<svg class="line-icon"><use href="#icon-ribbon"></use></svg>'}</div>
      <div class="habit-info">
        <div class="habit-name">${esc(h.name)}</div>
        <div class="habit-streak"><svg class="line-icon" style="width:14px;height:14px;margin-bottom:-2px;stroke:var(--amber)"><use href="#icon-zap"></use></svg> ${h.streak || 0} day streak</div>
      </div>
      <div class="habit-toggle ${h.done_today ? 'on' : ''}"
        id="ht-${h.id}" onclick="toggleHabit('${h.id}', this)"></div>
      <button class="del-btn" onclick="deleteHabit('${h.id}')"><svg class="line-icon" style="width:16px"><use href="#icon-trash"></use></svg></button>
    </div>`).join('');
}

async function toggleHabit(id, toggleEl) {
  try {
    const res = await API.patch('/habits/' + id + '/check', {});
    toggleEl.classList.toggle('on', res.done_today);
    // Update streak display in same row
    const row = document.getElementById('hrow-' + id);
    if (row) {
      const streakEl = row.querySelector('.habit-streak');
      if (streakEl) streakEl.innerHTML = `<svg class="line-icon" style="width:14px;height:14px;margin-bottom:-2px;stroke:var(--amber)"><use href="#icon-zap"></use></svg> ${res.streak || 0} day streak`;
    }
    // Update local array
    const habit = allHabits.find(h => h.id === id);
    if (habit) { habit.done_today = res.done_today; habit.streak = res.streak; }
    updateHabitStats();
    if (res.done_today) showToast('success', 'Streak!', `${res.streak} day streak!`);
  } catch(e) {
    showToast('error', 'Error', e.message);
  }
}

async function addHabit() {
  const payload = {
    name:  document.getElementById('habit-name').value,
    icon:  document.getElementById('habit-icon').value  || '<svg class="line-icon"><use href="#icon-ribbon"></use></svg>',
    color: document.getElementById('habit-color').value || '#7C6FFF',
  };
  if (!payload.name) { showToast('error','Error','Habit name is required'); return; }
  try {
    await API.post('/habits/', payload);
    if (typeof closeFAB === 'function') closeFAB();
    showToast('success', 'Habit Added!', 'Track it every day');
    document.getElementById('habit-name').value = '';
    loadHabits();
  } catch(e) {
    showToast('error', 'Failed', e.message);
  }
}

async function deleteHabit(id) {
  if (!confirm('Delete this habit? This will also remove all its history.')) return;
  await API.del('/habits/' + id);
  showToast('info','Deleted','Habit removed');
  loadHabits();
}
