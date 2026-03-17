/**
 * Persona — planner.js
 * Daily timeline, week view, drag-and-drop, task CRUD
 */

let currentDate  = new Date();
let currentView  = 'day';
let allTasks     = [];
let allTimetable = []; // Tracks fixed weekly classes

function initPlanner() {
  updateDateLabel();
  renderDateScroller();
  loadData();
}

function updateDateLabel() {
  const el   = document.getElementById('planner-date');
  const isToday = currentDate.toDateString() === new Date().toDateString();
  if (el) el.textContent = isToday
    ? 'Today'
    : currentDate.toLocaleDateString('en-IN', { weekday: 'short', month: 'short', day: 'numeric' });
}

function renderDateScroller() {
  const scroller = document.getElementById('date-scroller');
  if (!scroller) return;
  scroller.innerHTML = '';

  const realToday = new Date();
  realToday.setHours(0,0,0,0);
  let activeBtn = null;

  for (let i = -7; i <= 14; i++) {
    const d = new Date(realToday);
    d.setDate(realToday.getDate() + i);
    
    const dayStr = d.toLocaleDateString('en-US', { weekday: 'short' });
    const dateNum = d.getDate();
    const isSelected = d.toDateString() === currentDate.toDateString();
    
    const btn = document.createElement('div');
    btn.className = 'date-btn ' + (isSelected ? 'active' : '');
    btn.innerHTML = `<div class="date-day">${dayStr}</div><div class="date-num">${dateNum}</div>`;
    btn.onclick = () => {
      btn.style.transform = 'scale(0.9)';
      setTimeout(() => btn.style.transform = '', 150);
      currentDate = new Date(d);
      updateDateLabel();
      renderDateScroller();
      loadData();
    };
    scroller.appendChild(btn);
    if (isSelected) activeBtn = btn;
  }
  
  if (activeBtn) {
    const scrollPos = activeBtn.offsetLeft - scroller.offsetWidth / 2 + activeBtn.offsetWidth / 2;
    scroller.scrollTo({ left: scrollPos, behavior: 'smooth' });
  }
}

async function loadData() {
  const status  = document.getElementById('filter-status')?.value || '';
  const dateStr = currentDate.toISOString().slice(0, 10);
  let taskPath  = `/tasks/?date=${dateStr}`;
  if (status) taskPath += `&status=${status}`;

  try {
    const [tasksRes, timetableRes] = await Promise.all([
      API.get(taskPath).catch(() => []),
      API.get('/timetable/').catch(() => [])
    ]);
    allTasks = tasksRes || [];
    allTimetable = timetableRes || [];
  } catch (_) {
    allTasks = [];
    allTimetable = [];
  }

  renderTimeline();
  renderTaskList();
}

// ── Timeline ──────────────────────────────────────────────────
function renderTimeline() {
  const el = document.getElementById('timeline-slots');
  if (!el) return;

  const now = new Date();
  const isToday = currentDate.toDateString() === now.toDateString();
  
  // Decide start hour: If today, start at current hour. Otherwise, start early (6 AM).
  let startHour = 6;
  if (isToday) {
    startHour = Math.max(6, now.getHours());
  }
  const endHour = 22; // 10 PM

  const hours = [];
  for (let h = startHour; h <= endHour; h++) hours.push(h);

  const daysOfWeek = ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"];
  const currentDayName = daysOfWeek[currentDate.getDay()];

  el.innerHTML = hours.map(h => {
    const timeStr  = `${String(h).padStart(2, '0')}:00`;
    
    // Find task starting in this hour
    const task = allTasks.find(t => t.start_time && new Date(t.start_time).getHours() === h);
    
    // Find fixed timetable class starting in this hour (parsing HH:MM string)
    const tclass = allTimetable.find(c => c.day_of_week === currentDayName && c.start_time && parseInt(c.start_time.split(':')[0], 10) === h);

    let contentHtml = '<div style="min-width:1px;flex:1"></div>';
    let hasItem = false;

    if (tclass) {
      hasItem = true;
      contentHtml = `
        <div class="slot-task" style="background:var(--bg-glass2); border-left:4px solid var(--accent)" onclick="editClass(${JSON.stringify(tclass).replace(/"/g,'&quot;')})">
          <div class="slot-task-title"><svg class="line-icon" style="width:12px;height:12px;margin-bottom:-1px"><use href="#icon-book"></use></svg> ${esc(tclass.label)}</div>
          <div class="slot-task-time">${tclass.start_time} – ${tclass.end_time} <span class="badge badge-medium" style="margin-left:8px">${tclass.type || 'Class'}</span></div>
        </div>
      `;
    } else if (task) {
      hasItem = true;
      contentHtml = `
        <div class="slot-task" onclick="editTask(${JSON.stringify(task).replace(/"/g,'&quot;')})">
          <div class="slot-task-title">${esc(task.title)}</div>
          <div class="slot-task-time">
            ${fmtTime(task.start_time)} – ${task.end_time ? fmtTime(task.end_time) : '?'}
            <span class="badge badge-${task.priority}" style="margin-left:8px">${task.priority}</span>
          </div>
        </div>`;
    }

    return `
      <div class="timeline-slot" style="opacity: ${isToday && h < now.getHours() ? '0.4' : '1'}">
        <div class="slot-time">${timeStr}</div>
        <div class="slot-dot ${hasItem ? 'has-task' : ''}"></div>
        ${contentHtml}
      </div>`;
  }).join('');
}

// ── Task List ─────────────────────────────────────────────────
function renderTaskList() {
  const el = document.getElementById('task-list');
  if (!el) return;

  if (!allTasks.length) {
    el.innerHTML = `<div class="empty-state"><div class="empty-icon sticker sticker-lg"><svg class="line-icon"><use href="#icon-list"></use></svg></div><h3>No tasks</h3><p>Add a task or change the filter</p></div>`;
    return;
  }

  el.innerHTML = allTasks.map(t => `
    <div class="task-item ${t.status === 'completed' ? 'completed' : ''}" draggable="true">
      <div class="task-check" onclick="completeTask('${t.id}', this)">
        ${t.status === 'completed' ? '<svg class="line-icon" style="width:14px;height:14px;stroke-width:3px"><use href="#icon-check"></use></svg>' : ''}
      </div>
      <div class="task-body" onclick="editTask(${JSON.stringify(t).replace(/"/g,'&quot;')})">
        <div class="task-title">${esc(t.title)}</div>
        <div class="task-meta">
          <span class="task-time">${t.start_time ? fmtTime(t.start_time) : '<svg class="line-icon" style="width:12px;height:12px;margin-bottom:-2px"><use href="#icon-zap"></use></svg> Auto'}</span>
          <span class="badge badge-${t.priority}">${t.priority}</span>
          <span class="badge badge-${t.status}">${t.status.replace('_',' ')}</span>
        </div>
      </div>
      <div style="display:flex;gap:6px">
        <button class="btn btn-secondary btn-sm btn-icon" title="Edit" onclick="editTask(${JSON.stringify(t).replace(/"/g,'&quot;')})"><svg class="line-icon" style="width:14px"><use href="#icon-edit"></use></svg></button>
        <button class="btn btn-danger btn-sm btn-icon" title="Delete" onclick="deleteTask('${t.id}')"><svg class="line-icon" style="width:14px"><use href="#icon-trash"></use></svg></button>
      </div>
    </div>`).join('');
}

// ── Week View ─────────────────────────────────────────────────
async function renderWeekView() {
  const el = document.getElementById('week-grid');
  if (!el) return;

  const weekDays = [];
  const start    = new Date(currentDate);
  start.setDate(start.getDate() - start.getDay() + 1); // Monday

  for (let i = 0; i < 7; i++) {
    const d = new Date(start);
    d.setDate(d.getDate() + i);
    weekDays.push(d);
  }

  el.innerHTML = weekDays.map(d => {
    const isToday = d.toDateString() === new Date().toDateString();
    return `
      <div style="border-right:1px solid var(--border);padding:8px;min-height:120px;cursor:pointer;border-radius:8px;
        ${isToday ? 'background:rgba(124,111,255,0.08);border:1px solid rgba(124,111,255,0.2)' : ''}"
        onclick="goToDay('${d.toISOString().slice(0,10)}')">
        <div style="font-size:11px;color:var(--text-muted);text-transform:uppercase;letter-spacing:0.5px">
          ${d.toLocaleDateString('en-IN',{weekday:'short'})}
        </div>
        <div style="font-size:20px;font-weight:800;margin:4px 0;
          ${isToday ? 'color:var(--accent)' : ''}">
          ${d.getDate()}
        </div>
      </div>`;
  }).join('');
}

function goToDay(dateStr) {
  currentDate = new Date(dateStr);
  switchView('day', null);
}

function switchView(view, btn) {
  currentView = view;
  if (btn) {
    btn.closest('.tabs')?.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
    btn.classList.add('active');
  }
  document.getElementById('timeline-view').style.display = view === 'day'  ? '' : 'none';
  document.getElementById('week-view').style.display     = view === 'week' ? '' : 'none';
  if (view === 'week') renderWeekView();
  else loadData();
}

// ── CRUD ──────────────────────────────────────────────────────
function editTask(t) {
  document.getElementById('edit-task-id').value = t.id;
  document.getElementById('task-title').value   = t.title;
  document.getElementById('task-desc').value    = t.description || '';
  document.getElementById('task-start').value   = t.start_time ? t.start_time.replace(' ','T').slice(0,16) : '';
  document.getElementById('task-end').value     = t.end_time   ? t.end_time.replace(' ','T').slice(0,16)   : '';
  document.getElementById('task-priority').value= t.priority;
  document.getElementById('task-status').value  = t.status;
  document.getElementById('task-modal-title').innerHTML = '<svg class="line-icon" style="width:18px"><use href="#icon-edit"></use></svg> Edit Task';
  openModal('add-task-modal');
}

async function saveTask() {
  const id      = document.getElementById('edit-task-id').value;
  const payload = {
    title:       document.getElementById('task-title').value,
    description: document.getElementById('task-desc').value,
    start_time:  document.getElementById('task-start').value,
    end_time:    document.getElementById('task-end').value,
    priority:    document.getElementById('task-priority').value,
    status:      document.getElementById('task-status').value,
  };
  if (!payload.title) { showToast('error','Error','Title is required'); return; }

  try {
    if (id) await API.put('/tasks/' + id, payload);
    else    await API.post('/tasks/', payload);
    closeModal('add-task-modal');
    document.getElementById('edit-task-id').value = '';
    document.getElementById('task-modal-title').innerHTML = '<svg class="line-icon" style="width:18px"><use href="#icon-plus"></use></svg> New Task';
    showToast('success', id ? 'Updated!' : 'Created!', 'Task saved');
    loadTasks();
  } catch (e) {
    showToast('error', 'Failed', e.message);
  }
}

async function deleteTask(id) {
  if (!confirm('Delete this task?')) return;
  await API.del('/tasks/' + id);
  showToast('info', 'Deleted', 'Task removed');
  loadTasks();
}

async function completeTask(id, el) {
  await API.patch('/tasks/' + id + '/complete', {});
  el.closest('.task-item').classList.add('completed');
  el.innerHTML = '<svg class="line-icon" style="width:14px;height:14px;stroke-width:3px"><use href="#icon-check"></use></svg>';
  showToast('success', 'Done!', 'Task marked complete');
}

async function autoScheduleAll() {
  showToast('info', 'Scheduling…', 'Finding free time slots');
  try {
    const res = await API.post('/scheduler/auto-schedule', {});
    showToast('success', 'Scheduled!', res.message);
    loadData();
  } catch (e) {
    showToast('error', 'Failed', e.message);
  }
}

// ── Fixed Class CRUD (Timetable) ──────────────────────────────
function editClass(c) {
  document.getElementById('edit-class-id').value = c.id;
  document.getElementById('class-label').value   = c.label;
  document.getElementById('class-day').value     = c.day_of_week;
  document.getElementById('class-start').value   = c.start_time;
  document.getElementById('class-end').value     = c.end_time;
  document.getElementById('class-type').value    = c.type;
  document.getElementById('class-modal-title').innerHTML = '<svg class="line-icon" style="width:18px"><use href="#icon-edit"></use></svg> Edit Class';
  openClassFAB();
}

async function saveClass() {
  const id = document.getElementById('edit-class-id').value;
  const payload = {
    label:       document.getElementById('class-label').value,
    day_of_week: document.getElementById('class-day').value,
    start_time:  document.getElementById('class-start').value,
    end_time:    document.getElementById('class-end').value,
    type:        document.getElementById('class-type').value
  };
  
  if (!payload.label || !payload.start_time || !payload.end_time) {
    showToast('error', 'Validation Error', 'Label and times are required');
    return;
  }

  try {
    if (id) await API.put('/timetable/' + id, payload);
    else    await API.post('/timetable/', payload);
    closeClassFAB();
    showToast('success', 'Saved', 'Timetable updated');
    loadData();
  } catch (e) {
    showToast('error', 'Error', e.message);
  }
}

async function deleteClassId() {
  const id = document.getElementById('edit-class-id').value;
  if (!id) return;
  if (!confirm('Delete this class?')) return;
  await API.del('/timetable/' + id);
  closeClassFAB();
  showToast('info', 'Deleted', 'Class removed');
  loadData();
}

// ── Helpers ───────────────────────────────────────────────────
function fmtTime(iso) {
  if (!iso) return '';
  return new Date(iso).toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit' });
}
