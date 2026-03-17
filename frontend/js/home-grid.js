/**
 * home-grid.js  —  Persona PWA  v2
 *
 * Customisable home widget grid.
 *
 * EDIT MODE triggers:
 *  • Double-tap on the clock (handled in clock-nav.js → calls window.homeGridEnterEditMode)
 *
 * In edit mode:
 *  • Widgets wiggle (like iOS) and show drag handles.
 *  • Each widget shows an ✕ delete button.
 *  • A "+ Add Widget" card appears at the bottom.
 *  • Drag handle → reorder widgets.
 *
 * Deleted widgets are hidden (not removed from DOM) and their IDs
 * stored in localStorage.  The "+ Add Widget" sheet lets you re-add them.
 */

(function () {
  'use strict';

  /* ── Widget catalogue ───────────────────────────────────────── */
  // Must match the data-widget-id attrs in index.html
  const WIDGET_CATALOG = {
    'stats':        { label: 'Stats Overview',    icon: '<svg class="line-icon" style="width:18px"><use href="#icon-chart"></use></svg>' },
    'focus-timer':  { label: 'Focus Timer',       icon: '<svg class="line-icon" style="width:18px"><use href="#icon-clock"></use></svg>' },
    'tasks':        { label: "Today's Tasks",     icon: '<svg class="line-icon" style="width:18px"><use href="#icon-list"></use></svg>' },
    'habits':       { label: 'Habits Tracker',    icon: '<svg class="line-icon" style="width:18px"><use href="#icon-ribbon"></use></svg>' },
    'assignments':  { label: 'Due Soon',          icon: '<svg class="line-icon" style="width:18px"><use href="#icon-bell"></use></svg>' },
  };

  /* ── Keys ───────────────────────────────────────────────────── */
  const ORDER_KEY  = 'persona_widget_order';
  const HIDDEN_KEY = 'persona_hidden_widgets';

  /* ── State ──────────────────────────────────────────────────── */
  let editMode     = false;
  let dragging     = null;
  let longTimer    = null;
  let widgets      = [];
  let hiddenIds    = new Set();
  let container    = null;
  let addCardEl    = null;
  let pickerEl     = null;
  let pickerBdEl   = null;

  /* ═══════════════════════════════════════════════════════════════
     PERSISTENCE
  ═══════════════════════════════════════════════════════════════ */
  function loadHidden() {
    try { return new Set(JSON.parse(localStorage.getItem(HIDDEN_KEY)) || []); }
    catch { return new Set(); }
  }
  function saveHidden() {
    localStorage.setItem(HIDDEN_KEY, JSON.stringify([...hiddenIds]));
  }
  function loadOrder() {
    try { return JSON.parse(localStorage.getItem(ORDER_KEY)) || []; }
    catch { return []; }
  }
  function saveOrder() {
    const ids = [...container.querySelectorAll('.home-widget')]
      .map(w => w.dataset.widgetId);
    localStorage.setItem(ORDER_KEY, JSON.stringify(ids));
  }

  /* ═══════════════════════════════════════════════════════════════
     APPLY SAVED STATE
  ═══════════════════════════════════════════════════════════════ */
  function applyHidden() {
    hiddenIds.forEach(id => {
      const el = container.querySelector(`[data-widget-id="${id}"]`);
      if (el) el.classList.add('home-widget-hidden');
    });
  }

  function applyOrder() {
    const order = loadOrder();
    if (!order.length) return;
    order.forEach(id => {
      const el = container.querySelector(`[data-widget-id="${id}"]`);
      if (el) container.appendChild(el);
    });
  }

  /* ═══════════════════════════════════════════════════════════════
     HIDE / SHOW INDIVIDUAL WIDGETS
  ═══════════════════════════════════════════════════════════════ */
  function hideWidget(id) {
    hiddenIds.add(id);
    saveHidden();
    const el = container.querySelector(`[data-widget-id="${id}"]`);
    if (el) el.classList.add('home-widget-hidden');
    refreshPickerList();
  }

  function showWidget(id) {
    hiddenIds.delete(id);
    saveHidden();
    const el = container.querySelector(`[data-widget-id="${id}"]`);
    if (el) el.classList.remove('home-widget-hidden');
    refreshPickerList();
  }

  /* ═══════════════════════════════════════════════════════════════
     EDIT MODE
  ═══════════════════════════════════════════════════════════════ */
  function enterEditMode() {
    if (editMode) return;
    editMode = true;
    document.body.classList.add('home-edit-mode');

    const doneBtn = document.getElementById('edit-done-btn');
    if (doneBtn) doneBtn.classList.add('visible');

    if (addCardEl) addCardEl.classList.add('visible');
    if (navigator.vibrate) navigator.vibrate([18, 50, 18]);
  }

  function exitEditMode() {
    if (!editMode) return;
    editMode = false;
    document.body.classList.remove('home-edit-mode');

    const doneBtn = document.getElementById('edit-done-btn');
    if (doneBtn) doneBtn.classList.remove('visible');

    if (addCardEl) addCardEl.classList.remove('visible');
    closePicker();
    saveOrder();
  }

  // Expose globally for clock-nav.js double-tap
  window.homeGridEnterEditMode = enterEditMode;

  /* ═══════════════════════════════════════════════════════════════
     WIDGET PICKER SHEET (Add Widget)
  ═══════════════════════════════════════════════════════════════ */
  function buildPicker() {
    pickerBdEl = document.createElement('div');
    pickerBdEl.id = 'widget-picker-backdrop';
    pickerBdEl.addEventListener('click', closePicker);

    pickerEl = document.createElement('div');
    pickerEl.id = 'widget-picker';
    pickerEl.innerHTML = `
      <div class="picker-handle"></div>
      <div class="picker-header">
        <span class="picker-title"><svg class="line-icon" style="width:18px"><use href="#icon-plus"></use></svg> Add Widget</span>
        <button class="picker-close" id="picker-close-btn"><svg class="line-icon" style="width:20px"><use href="#icon-x"></use></svg></button>
      </div>
      <div class="picker-list" id="picker-list"></div>
    `;

    document.body.appendChild(pickerBdEl);
    document.body.appendChild(pickerEl);
    document.getElementById('picker-close-btn').addEventListener('click', closePicker);
  }

  function refreshPickerList() {
    const list = document.getElementById('picker-list');
    if (!list) return;

    const available = Object.entries(WIDGET_CATALOG).filter(([id]) => hiddenIds.has(id));
    if (!available.length) {
      list.innerHTML = `<div class="picker-empty">
        <div style="margin-bottom:10px" class="sticker sticker-md"><svg class="line-icon" style="width:32px;height:32px;stroke:var(--green)"><use href="#icon-check-circle"></use></svg></div>
        <p>All widgets are already on your home screen.</p>
      </div>`;
      return;
    }

    list.innerHTML = available.map(([id, info]) => `
      <div class="picker-item" data-widget-id="${id}">
        <span class="picker-item-icon">${info.icon}</span>
        <span class="picker-item-label">${info.label}</span>
        <button class="picker-add-btn" data-widget-id="${id}">Add</button>
      </div>
    `).join('');

    list.querySelectorAll('.picker-add-btn').forEach(btn => {
      btn.addEventListener('click', e => {
        e.stopPropagation();
        showWidget(btn.dataset.widgetId);
        if ([...hiddenIds].filter(id => WIDGET_CATALOG[id]).length === 0) {
          closePicker();
        }
      });
    });
  }

  function openPicker() {
    if (!pickerEl) buildPicker();
    refreshPickerList();
    pickerBdEl.classList.add('show');
    pickerEl.classList.add('show');
  }

  function closePicker() {
    if (pickerBdEl) pickerBdEl.classList.remove('show');
    if (pickerEl)   pickerEl.classList.remove('show');
  }

  /* ═══════════════════════════════════════════════════════════════
     "ADD WIDGET" CARD (shown at bottom in edit mode)
  ═══════════════════════════════════════════════════════════════ */
  function buildAddCard() {
    addCardEl = document.createElement('div');
    addCardEl.className = 'widget-add-card';
    addCardEl.innerHTML = `<span class="wac-icon">＋</span><span class="wac-label">Add Widget</span>`;
    addCardEl.addEventListener('click', openPicker);
    container.appendChild(addCardEl);
  }

  /* ═══════════════════════════════════════════════════════════════
     DELETE BUTTONS (injected on each widget)
  ═══════════════════════════════════════════════════════════════ */
  function addDeleteBtn(el) {
    if (el.querySelector('.widget-delete-btn')) return;
    const id  = el.dataset.widgetId;
    const btn = document.createElement('button');
    btn.className = 'widget-delete-btn';
    btn.innerHTML = '<svg class="line-icon" style="width:14px;margin-bottom:-2px"><use href="#icon-x"></use></svg>';
    btn.title     = 'Remove widget';
    btn.addEventListener('click', e => {
      e.stopPropagation();
      hideWidget(id);
    });
    el.appendChild(btn);
  }

  /* ═══════════════════════════════════════════════════════════════
     DRAG HANDLES
  ═══════════════════════════════════════════════════════════════ */
  function addHandle(el) {
    if (el.querySelector('.widget-handle')) return;
    const h = document.createElement('div');
    h.className = 'widget-handle';
    h.setAttribute('aria-hidden', 'true');
    h.innerHTML = `<span class="widget-handle-icon">⠿</span>`;
    el.prepend(h);
  }

  /* ═══════════════════════════════════════════════════════════════
     DRAG & DROP REORDER
  ═══════════════════════════════════════════════════════════════ */
  function getVisibleWidgets() {
    return [...container.querySelectorAll('.home-widget:not(.home-widget-hidden):not(.widget-add-card)')];
  }

  function closestWidget(y, excluded) {
    let closest = null, minDist = Infinity;
    getVisibleWidgets().forEach(w => {
      if (w === excluded) return;
      const rect = w.getBoundingClientRect();
      const mid  = rect.top + rect.height / 2;
      const dist = Math.abs(y - mid);
      if (dist < minDist) { minDist = dist; closest = w; }
    });
    return closest;
  }

  function bindWidget(el) {
    let lastY = 0;

    el.addEventListener('pointerdown', e => {
      if (!editMode) {
        // Long press to enter edit mode
        longTimer = setTimeout(() => {
          longTimer = null;
          enterEditMode();
        }, 500);
        return;
      }

      // In edit mode — only drag via handle
      const handle = e.target.closest('.widget-handle');
      if (!handle) return;

      e.preventDefault();
      lastY = e.clientY;
      el.classList.add('widget-dragging');
      el.setPointerCapture(e.pointerId);
      dragging = el;
    });

    el.addEventListener('pointermove', e => {
      if (longTimer) {
        if (Math.hypot(e.movementX, e.movementY) > 6) {
          clearTimeout(longTimer); longTimer = null;
        }
      }
      if (dragging !== el) return;
      e.preventDefault();

      const dy = e.clientY - lastY;
      lastY = e.clientY;

      const cur = parseFloat(el.style.getPropertyValue('--drag-dy') || '0');
      el.style.setProperty('--drag-dy', `${cur + dy}px`);
      el.style.transform = `translateY(var(--drag-dy)) scale(1.04)`;

      const swap = closestWidget(e.clientY, el);
      if (swap) {
        const sRect = swap.getBoundingClientRect();
        const eRect = el.getBoundingClientRect();
        if (eRect.top < sRect.top) container.insertBefore(el, swap.nextSibling);
        else container.insertBefore(el, swap);
      }
    }, { passive: false });

    el.addEventListener('pointerup', e => {
      clearTimeout(longTimer); longTimer = null;
      if (dragging !== el) return;
      el.classList.remove('widget-dragging');
      el.style.transform = '';
      el.style.removeProperty('--drag-dy');
      dragging = null;
      saveOrder();
    });

    el.addEventListener('pointercancel', () => {
      clearTimeout(longTimer); longTimer = null;
      if (dragging !== el) return;
      el.classList.remove('widget-dragging');
      el.style.transform = '';
      el.style.removeProperty('--drag-dy');
      dragging = null;
    });
  }

  /* ═══════════════════════════════════════════════════════════════
     DONE BUTTON
  ═══════════════════════════════════════════════════════════════ */
  function injectDoneButton() {
    if (document.getElementById('edit-done-btn')) return;
    const btn = document.createElement('button');
    btn.id = 'edit-done-btn';
    btn.className = 'edit-done-btn';
    btn.textContent = 'Done';
    btn.addEventListener('click', exitEditMode);
    const right = document.querySelector('.app-header .header-right');
    if (right) right.prepend(btn);
    else document.body.appendChild(btn);
  }

  /* ═══════════════════════════════════════════════════════════════
     INIT
  ═══════════════════════════════════════════════════════════════ */
  function init() {
    container = document.querySelector('.page-content');
    if (!container) return;

    widgets = [...container.querySelectorAll('.home-widget')];
    if (!widgets.length) return;

    hiddenIds = loadHidden();
    applyHidden();
    applyOrder();

    // Add UI elements to every widget
    widgets.forEach(w => {
      addHandle(w);
      addDeleteBtn(w);
      bindWidget(w);
    });

    injectDoneButton();
    buildAddCard();

    // Exit edit mode on outside tap
    document.addEventListener('pointerdown', e => {
      if (!editMode) return;
      const inside = e.target.closest('.home-widget, #edit-done-btn, .widget-add-card, #widget-picker, #widget-picker-backdrop');
      if (!inside) exitEditMode();
    });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
