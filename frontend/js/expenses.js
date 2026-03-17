/**
 * Persona — expenses.js
 * Expense tracker with Chart.js pie chart
 */

let allExpenses = [];
let catChart    = null;

const CAT_COLORS = {
  Income:        '#48E5A0',
  food:          '#FF6F91',
  transport:     '#7CC6FE',
  books:         '#5CB8FF',
  entertainment: '#FFB347',
  health:        '#00D1B2',
  shopping:      '#FF5C6C',
  other:         '#9898BB',
};

const CAT_ICONS = {
  Income: '<svg class="line-icon"><use href="#icon-dollar"></use></svg>', food: '<svg class="line-icon"><use href="#icon-coffee"></use></svg>', transport: '<svg class="line-icon"><use href="#icon-truck"></use></svg>', books: '<svg class="line-icon"><use href="#icon-book"></use></svg>',
  entertainment: '<svg class="line-icon"><use href="#icon-tv"></use></svg>', health: '<svg class="line-icon"><use href="#icon-heart"></use></svg>', shopping: '<svg class="line-icon"><use href="#icon-shopping-bag"></use></svg>', other: '<svg class="line-icon"><use href="#icon-box"></use></svg>',
};

async function loadExpenses() {
  const month = document.getElementById('month-picker')?.value || new Date().toISOString().slice(0,7);
  try {
    const data  = await API.get(`/expenses/?month=${month}`);
    allExpenses = data.expenses || [];
    updateStats(data);
    renderBarChart(data.summary || []);
    renderExpenses();
  } catch (e) {
    showToast('error', 'Error', e.message);
  }
}

function updateStats(data) {
  const summary = data.summary || [];
  
  const incomeRow = summary.find(s => s.category === 'Income');
  const totalIncome = incomeRow ? incomeRow.total : 0;
  
  const totalSpent = summary
    .filter(s => s.category !== 'Income')
    .reduce((s, e) => s + e.total, 0);

  const remaining = totalIncome - totalSpent;

  document.getElementById('total-spent').textContent  = '₹' + totalSpent.toFixed(0);
  
  const incEl = document.getElementById('total-income');
  if (incEl) incEl.textContent = '₹' + totalIncome.toFixed(0);
  
  const remEl = document.getElementById('total-remaining');
  if (remEl) {
    remEl.textContent = '₹' + remaining.toFixed(0);
    remEl.style.color = remaining < 0 ? 'var(--red)' : 'var(--green)';
  }

  // Update progress bar if it exists
  const bar = document.getElementById('income-progress-bar');
  if (bar) {
    if (totalIncome > 0) {
      let pct = Math.min(100, Math.max(0, (totalSpent / totalIncome) * 100));
      bar.style.width = pct + '%';
      bar.style.background = pct > 90 ? 'var(--red)' : 'var(--accent-light)';
    } else {
      bar.style.width = '0%';
    }
  }
}

function renderExpenses() {
  const catFilter = document.getElementById('filter-cat')?.value || '';
  const filtered  = catFilter ? allExpenses.filter(e => e.category === catFilter) : allExpenses;
  const el        = document.getElementById('expense-list');
  if (!el) return;

  if (!filtered.length) {
    el.innerHTML = `<div class="empty-state" style="padding:24px"><div class="empty-icon sticker sticker-md" style="margin-bottom:12px"><svg class="line-icon"><use href="#icon-wallet"></use></svg></div><p>No expenses found</p></div>`;
    return;
  }

  el.innerHTML = filtered.map(e => `
    <div class="expense-row">
      <div class="expense-icon" style="background:${CAT_COLORS[e.category]}20">
        ${CAT_ICONS[e.category] || '<svg class="line-icon"><use href="#icon-box"></use></svg>'}
      </div>
      <div class="expense-info">
        <div class="expense-desc">${esc(e.description || e.category)}</div>
        <div class="expense-date">${new Date(e.date).toLocaleDateString('en-IN')} · ${e.category}</div>
      </div>
      <div class="expense-amount">₹${parseFloat(e.amount).toFixed(2)}</div>
      <button class="btn btn-danger btn-sm btn-icon" onclick="deleteExpense('${e.id}')"><svg class="line-icon" style="width:16px"><use href="#icon-trash"></use></svg></button>
    </div>`).join('');
}

function renderBarChart(summary) {
  const ctx = document.getElementById('cat-chart');
  if (!ctx) return;

  if (catChart) { catChart.destroy(); catChart = null; }

  // Exclude Income from Expense Pie Chart
  const expenseSummary = summary.filter(s => s.category !== 'Income');

  if (!expenseSummary.length) {
    ctx.parentElement.innerHTML = `<div class="empty-state"><div class="empty-icon sticker sticker-md" style="margin-bottom:12px"><svg class="line-icon"><use href="#icon-chart"></use></svg></div><p>No expenses this month</p></div>`;
    return;
  }

  catChart = new Chart(ctx, {
    type: 'doughnut',
    data: {
      labels: expenseSummary.map(s => (CAT_ICONS[s.category] || '') + ' ' + s.category),
      datasets: [{
        data: expenseSummary.map(s => s.total),
        backgroundColor: expenseSummary.map(s => CAT_COLORS[s.category] || '#9898BB'),
        borderWidth: 2,
        borderColor: '#13132a',
        hoverOffset: 8,
      }],
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: { labels: { color: '#9898BB', font: { family: 'Inter' } } },
        tooltip: {
          callbacks: {
            label: ctx => ` ₹${ctx.raw.toFixed(2)}`,
          },
        },
      },
    },
  });
}

async function addExpense() {
  const payload = {
    amount:      document.getElementById('exp-amount').value,
    category:    document.getElementById('exp-cat').value,
    description: document.getElementById('exp-desc').value,
    date:        document.getElementById('exp-date').value,
  };
  if (!payload.amount) { showToast('error','Error','Amount is required'); return; }
  try {
    await API.post('/expenses/', payload);
    closeModal('add-expense-modal');
    showToast('success', 'Expense Added!', `₹${parseFloat(payload.amount).toFixed(2)} logged`);
    loadExpenses();
  } catch (e) {
    showToast('error', 'Failed', e.message);
  }
}

async function deleteExpense(id) {
  if (!confirm('Delete this expense?')) return;
  await API.del('/expenses/' + id);
  showToast('info', 'Deleted', 'Expense removed');
  loadExpenses();
}
