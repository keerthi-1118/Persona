/**
 * Persona — analytics.js
 * Charts using Chart.js for focus, tasks, expenses, habits
 */

let focusChart = null, taskChart = null, expChart = null;

async function loadAnalytics(period, btn) {
  if (btn) {
    btn.closest('.tabs')?.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
    btn.classList.add('active');
  }
  try {
    const data = await API.get('/analytics/?period=' + (period || 'week'));
    renderScoreRing(data.productivity_score || 0);
    updateStatCards(data);
    renderFocusChart(data.focus?.daily || []);
    renderTaskChart(data.tasks?.by_status || {});
    renderExpChart(data.expenses?.by_category || []);
    renderHabitStreaks(data.habits || []);
  } catch(e) {
    showToast('error','Error', e.message);
  }
}

function renderScoreRing(score) {
  document.getElementById('productivity-score').textContent = score + '%';
  const circle = document.getElementById('score-circle');
  if (!circle) return;
  const CIRC   = 351.86;
  const offset = CIRC * (1 - score / 100);
  circle.style.strokeDashoffset = offset;
  circle.style.stroke = score >= 70 ? '#48E5A0' : score >= 40 ? '#FFB347' : '#FF5C6C';
}

function updateStatCards(data) {
  document.getElementById('an-focus').textContent      = (data.focus?.total_hours || 0) + 'h';
  document.getElementById('an-completion').textContent = (data.tasks?.completion_rate || 0) + '%';
  document.getElementById('an-habits').textContent     = (data.habits?.length || 0);
  document.getElementById('an-due').textContent        = (data.assignments_due_soon || 0);
}

function renderFocusChart(daily) {
  const ctx = document.getElementById('focus-chart');
  if (!ctx) return;
  if (focusChart) { focusChart.destroy(); }

  const labels = daily.map(d => d.day ? new Date(d.day).toLocaleDateString('en-IN',{weekday:'short',day:'numeric'}) : '');
  const values = daily.map(d => parseFloat((d.mins / 60).toFixed(1)));

  focusChart = new Chart(ctx, {
    type: 'bar',
    data: {
      labels,
      datasets: [{
        label: 'Focus Hours',
        data: values,
        backgroundColor: 'rgba(124,111,255,0.5)',
        borderColor: '#7C6FFF',
        borderWidth: 2,
        borderRadius: 6,
        hoverBackgroundColor: 'rgba(124,111,255,0.8)',
      }],
    },
    options: chartOptions('Focus Hours'),
  });
}

function renderTaskChart(byStatus) {
  const ctx = document.getElementById('task-chart');
  if (!ctx) return;
  if (taskChart) { taskChart.destroy(); }

  const status = Object.keys(byStatus);
  const counts = Object.values(byStatus);
  const colors = { pending:'#FFB347', in_progress:'#7C6FFF', completed:'#48E5A0', cancelled:'#FF5C6C' };

  taskChart = new Chart(ctx, {
    type: 'doughnut',
    data: {
      labels: status.map(s => s.replace('_',' ')),
      datasets: [{
        data: counts,
        backgroundColor: status.map(s => colors[s] || '#9898BB'),
        borderColor: '#13132a',
        borderWidth: 2,
        hoverOffset: 8,
      }],
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: { labels: { color: '#9898BB', font: { family: 'Inter' } } },
      },
    },
  });
}

function renderExpChart(summary) {
  const ctx = document.getElementById('exp-chart');
  if (!ctx) return;
  if (expChart) { expChart.destroy(); }

  const CAT_COLORS = {
    food:'#FF6F91', transport:'#7C6FFF', books:'#48E5A0',
    entertainment:'#FFB347', health:'#00D1B2', shopping:'#FF5C6C', other:'#9898BB',
  };

  expChart = new Chart(ctx, {
    type: 'bar',
    data: {
      labels: summary.map(s => s.category),
      datasets: [{
        label: 'Amount (₹)',
        data: summary.map(s => s.total),
        backgroundColor: summary.map(s => CAT_COLORS[s.category] + '88' || '#9898BB88'),
        borderColor:     summary.map(s => CAT_COLORS[s.category] || '#9898BB'),
        borderWidth: 2,
        borderRadius: 6,
      }],
    },
    options: chartOptions('₹'),
  });
}

function renderHabitStreaks(habits) {
  const el = document.getElementById('habit-streaks');
  if (!el) return;
  if (!habits.length) {
    el.innerHTML = `<div class="empty-state" style="padding:16px"><p>No habits tracked yet</p></div>`;
    return;
  }
  el.innerHTML = habits.sort((a,b) => b.streak - a.streak).map(h => `
    <div style="display:flex;align-items:center;gap:12px">
      <span style="flex:1;font-size:14px;font-weight:500">${esc(h.name)}</span>
      <div class="progress-bar" style="width:100px">
        <div class="progress-fill" style="width:${Math.min(h.streak * 3, 100)}%"></div>
      </div>
      <span style="font-size:13px;font-weight:700;color:var(--amber);min-width:48px;text-align:right">🔥 ${h.streak}d</span>
    </div>`).join('');
}

function chartOptions(label) {
  return {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { display: false },
      tooltip: { callbacks: { label: ctx => ` ${ctx.raw} ${label}` } },
    },
    scales: {
      x: { ticks: { color: '#9898BB', font: { family: 'Inter', size: 11 } }, grid: { color: 'rgba(255,255,255,0.04)' } },
      y: { ticks: { color: '#9898BB', font: { family: 'Inter', size: 11 } }, grid: { color: 'rgba(255,255,255,0.06)' } },
    },
  };
}
