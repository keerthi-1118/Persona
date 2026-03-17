/**
 * clock-nav.js  —  Persona PWA  v2
 *
 * Clock sits fixed in bottom-right corner.
 * • Real-time hour + minute hands.
 * • SHORT TAP  → navigate to Home  (or on Home page: start double-tap timer)
 * • DOUBLE TAP (on Home page only) → trigger widget edit mode.
 * • LONG PRESS → 5 nav items fan out from 270° (9 o'clock) to 360° (12 o'clock)
 *   – minute hand extends past the clock ring and acts as selector pointer.
 *   – Drag finger outward → hand follows → nearest item highlighted.
 *   – When item highlighted: its ring glows + label appears outside the circle.
 *   – Release on item → navigate there.
 *   – Drag back to center (abort zone) → collapse, no navigation.
 */

(function () {
  'use strict';

  /* ── Config ─────────────────────────────────────────────────── */
  const LONG_PRESS_MS   = 450;
  const DOUBLE_TAP_MS   = 320;
  const ABORT_RADIUS    = 36;
  const MENU_RADIUS     = 122;   // px from clock center to item center
  const LABEL_DIST      = 38;    // extra px outward from item edge for label

  // 6 items in an arc: 270° (left) → 360° (up)
  const NAV_ITEMS = [
    { label: 'Time',    icon: '<svg class="line-icon"><use href="#icon-clock"></use></svg>', href: '/timetable',   angle: 270 },
    { label: 'Habits',  icon: '<svg class="line-icon"><use href="#icon-ribbon"></use></svg>', href: '/habits',      angle: 288 },
    { label: 'Stats',   icon: '<svg class="line-icon"><use href="#icon-chart"></use></svg>', href: '/analytics',   angle: 306 },
    { label: 'Money',   icon: '<svg class="line-icon"><use href="#icon-wallet"></use></svg>', href: '/expenses',    angle: 324 },
    { label: 'Tasks',   icon: '<svg class="line-icon"><use href="#icon-list"></use></svg>', href: '/assignments', angle: 342 },
    { label: 'Planner', icon: '<svg class="line-icon"><use href="#icon-calendar"></use></svg>', href: '/planner',     angle: 360 },
  ];

  /* ── State ──────────────────────────────────────────────────── */
  let menuOpen        = false;
  let longPressTimer  = null;
  let activeItem      = null;
  let lastTapTime     = 0;
  let tapPending      = false;   // waiting for possible second tap
  let tapTimer        = null;
  let pointerMoved    = false;
  let downX = 0, downY = 0;

  /* ── DOM refs ───────────────────────────────────────────────── */
  let clockEl, minuteHandGroup, hourHandGroup, minuteHandLine;
  let menuItemEls = [];
  let centerX, centerY;

  /* ═══════════════════════════════════════════════════════════════
     REAL-TIME CLOCK
  ═══════════════════════════════════════════════════════════════ */
  function updateClock() {
    if (menuOpen) return;   // don't overwrite pointer rotation
    const now  = new Date();
    const hrs  = now.getHours() % 12;
    const mins = now.getMinutes();
    const secs = now.getSeconds();
    hourHandGroup.style.transform   = `rotate(${hrs * 30 + mins * 0.5}deg)`;
    minuteHandGroup.style.transform = `rotate(${mins * 6 + secs * 0.1}deg)`;
  }

  /* ═══════════════════════════════════════════════════════════════
     MENU OPEN / CLOSE
  ═══════════════════════════════════════════════════════════════ */
  function openMenu() {
    if (menuOpen) return;
    menuOpen = true;
    clockEl.classList.add('clock-menu-open', 'clock-pulse');

    // Compute clock center in viewport (do this fresh each open)
    const rect = clockEl.getBoundingClientRect();
    centerX = rect.left + rect.width  / 2;
    centerY = rect.top  + rect.height / 2;

    // Extend the minute hand to poke past the outer ring
    minuteHandLine.setAttribute('y2', '-6');  // extends ~10px past ring

    // Fan items out from clock center to their arc positions
    menuItemEls.forEach((el, i) => {
      const rad  = (NAV_ITEMS[i].angle - 90) * (Math.PI / 180);
      const tx   = Math.cos(rad) * MENU_RADIUS;
      const ty   = Math.sin(rad) * MENU_RADIUS;

      // Label direction: outward unit vector × distance from item's surface
      const unitX = tx / MENU_RADIUS;
      const unitY = ty / MENU_RADIUS;
      const ITEM_RADIUS = 22;   // half of 44px item size
      const ldx = unitX * (ITEM_RADIUS + LABEL_DIST);
      const ldy = unitY * (ITEM_RADIUS + LABEL_DIST);

      el.style.setProperty('--tx', `${tx}px`);
      el.style.setProperty('--ty', `${ty}px`);
      el.style.setProperty('--ld-x', `${ldx}px`);
      el.style.setProperty('--ld-y', `${ldy}px`);
      el.style.transitionDelay = `${i * 50}ms`;
      el.classList.add('visible');
    });

    // Point minute hand at the middle-ish item initially (315° = Money)
    minuteHandGroup.classList.add('clock-hand-pointer');
    rotatePointer(NAV_ITEMS[Math.floor(NAV_ITEMS.length / 2)].angle);
  }

  function closeMenu(navigate) {
    if (!menuOpen) return;
    menuOpen  = false;
    activeItem = null;

    clockEl.classList.remove('clock-menu-open', 'clock-pulse');
    minuteHandGroup.classList.remove('clock-hand-pointer');

    // Restore minute hand length
    minuteHandLine.setAttribute('y2', '13');

    // Collapse items (reverse stagger)
    menuItemEls.forEach((el, i) => {
      el.style.transitionDelay = `${(menuItemEls.length - 1 - i) * 30}ms`;
      el.classList.remove('visible', 'active');
    });

    if (navigate) {
      setTimeout(() => { window.location.href = navigate; }, 260);
    } else {
      // Restore real-time hand after animation
      setTimeout(updateClock, 350);
    }
  }

  /* ═══════════════════════════════════════════════════════════════
     POINTER HAND TRACKING
  ═══════════════════════════════════════════════════════════════ */
  function rotatePointer(angleDeg) {
    minuteHandGroup.style.transform = `rotate(${angleDeg}deg)`;
  }

  function getAngle(cx, cy, px, py) {
    // 0° = 12 o'clock, CW positive
    let deg = Math.atan2(py - cy, px - cx) * (180 / Math.PI) + 90;
    if (deg < 0) deg += 360;
    return deg;
  }

  function getDist(cx, cy, px, py) {
    return Math.hypot(px - cx, py - cy);
  }

  function closestItem(angleDeg) {
    let best = 0, bestDiff = Infinity;
    NAV_ITEMS.forEach((item, i) => {
      let diff = Math.abs(item.angle - angleDeg);
      if (diff > 180) diff = 360 - diff;
      if (diff < bestDiff) { bestDiff = diff; best = i; }
    });
    return best;
  }

  function highlightItem(idx) {
    if (idx === activeItem) return;
    activeItem = idx;
    menuItemEls.forEach((el, i) => el.classList.toggle('active', i === idx));
    if (navigator.vibrate) navigator.vibrate(9);
  }

  /* ═══════════════════════════════════════════════════════════════
     SHORT TAP — Home navigation or double-tap for edit
  ═══════════════════════════════════════════════════════════════ */
  function handleShortTap() {
    const isHome = window.location.pathname === '/' ||
                   window.location.pathname === '' ||
                   window.location.pathname === '/index.html';

    if (!isHome) {
      // On any other page: go home
      window.location.href = '/';
      return;
    }

    // On home page: detect double-tap for edit mode
    const now = Date.now();
    if (tapPending && (now - lastTapTime) < DOUBLE_TAP_MS) {
      // Double tap!
      clearTimeout(tapTimer);
      tapPending = false;
      if (typeof window.homeGridEnterEditMode === 'function') {
        window.homeGridEnterEditMode();
      }
      if (navigator.vibrate) navigator.vibrate([15, 40, 15]);
    } else {
      // First tap on home — wait for possible second
      lastTapTime = now;
      tapPending  = true;
      tapTimer    = setTimeout(() => { tapPending = false; }, DOUBLE_TAP_MS + 50);
    }
  }

  /* ═══════════════════════════════════════════════════════════════
     POINTER EVENT HANDLERS
  ═══════════════════════════════════════════════════════════════ */
  function onPointerDown(e) {
    e.preventDefault();
    downX = e.clientX; downY = e.clientY; pointerMoved = false;
    clockEl.setPointerCapture(e.pointerId);

    if (menuOpen) return;  // extra touches while menu open = ignore

    clockEl.classList.add('clock-pressing');

    longPressTimer = setTimeout(() => {
      longPressTimer = null;
      if (!pointerMoved) openMenu();
    }, LONG_PRESS_MS);
  }

  function onPointerMove(e) {
    // Detect any meaningful movement so we don't trigger long-press while scrolling
    if (Math.hypot(e.clientX - downX, e.clientY - downY) > 6) {
      pointerMoved = true;
      if (longPressTimer) { clearTimeout(longPressTimer); longPressTimer = null; }
    }

    if (!menuOpen) return;
    e.preventDefault();

    const px = e.clientX, py = e.clientY;
    const dist = getDist(centerX, centerY, px, py);

    if (dist < ABORT_RADIUS) {
      menuItemEls.forEach(el => el.classList.remove('active'));
      activeItem = null;
      // Hand follows finger in abort zone too (subtle)
      rotatePointer(getAngle(centerX, centerY, px, py));
      return;
    }

    const angle = getAngle(centerX, centerY, px, py);
    rotatePointer(angle);
    highlightItem(closestItem(angle));
  }

  function onPointerUp(e) {
    const wasMenu = menuOpen;
    clockEl.classList.remove('clock-pressing');

    // Cancel pending long press
    if (longPressTimer) { clearTimeout(longPressTimer); longPressTimer = null; }

    if (wasMenu) {
      // Determine what to do with the menu
      const dist = getDist(centerX, centerY, e.clientX, e.clientY);
      if (dist < ABORT_RADIUS || activeItem === null) {
        closeMenu(null);
      } else {
        closeMenu(NAV_ITEMS[activeItem].href);
      }
      return;
    }

    // Short tap (no menu opened, no significant movement)
    if (!pointerMoved) {
      handleShortTap();
    }
  }

  function onPointerCancel() {
    if (longPressTimer) { clearTimeout(longPressTimer); longPressTimer = null; }
    clockEl.classList.remove('clock-pressing');
    closeMenu(null);
  }

  function onDocumentDown(e) {
    if (menuOpen && !clockEl.contains(e.target)) closeMenu(null);
  }

  /* ═══════════════════════════════════════════════════════════════
     BUILD DOM
  ═══════════════════════════════════════════════════════════════ */
  function buildClock() {
    clockEl = document.createElement('div');
    clockEl.id = 'clock-nav';
    clockEl.setAttribute('role', 'button');
    clockEl.setAttribute('aria-label', 'Clock — tap for home, long press for navigation');

    // Build ticks
    const ticks = Array.from({ length: 12 }, (_, i) => {
      const a  = (i * 30 - 90) * (Math.PI / 180);
      const r1 = 26, r2 = i % 3 === 0 ? 20 : 23;
      const x1 = (36 + r1 * Math.cos(a)).toFixed(2);
      const y1 = (36 + r1 * Math.sin(a)).toFixed(2);
      const x2 = (36 + r2 * Math.cos(a)).toFixed(2);
      const y2 = (36 + r2 * Math.sin(a)).toFixed(2);
      const cls = i % 3 === 0 ? 'tick-major' : 'tick-minor';
      return `<line class="${cls}" x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}"/>`;
    }).join('');

    clockEl.innerHTML = `
      <svg class="clock-face" viewBox="0 0 72 72" xmlns="http://www.w3.org/2000/svg">
        <circle class="clock-ring-outer" cx="36" cy="36" r="33"/>
        <circle class="clock-ring-inner" cx="36" cy="36" r="28"/>
        ${ticks}
        <g class="hand-group" id="hour-hand-group">
          <line class="clock-hand hour-hand" id="hour-hand" x1="36" y1="36" x2="36" y2="20"/>
        </g>
        <g class="hand-group" id="minute-hand-group">
          <line class="clock-hand minute-hand" id="minute-hand" x1="36" y1="36" x2="36" y2="13"/>
        </g>
        <circle class="clock-center-dot" cx="36" cy="36" r="3.5"/>
      </svg>
      <div class="clock-abort-ring"></div>
    `;

    // Build nav items — compact circles, label appears outside when active
    menuItemEls = NAV_ITEMS.map((item, i) => {
      const el = document.createElement('div');
      el.className = 'clock-menu-item';
      el.dataset.index = i;
      // cmi-label uses --ld-x / --ld-y (set in openMenu per item)
      el.innerHTML = `
        <span class="cmi-icon sticker">${item.icon}</span>
        <span class="cmi-label">${item.label}</span>
      `;

      el.addEventListener('pointerup', e => {
        e.stopPropagation();
        if (menuOpen) closeMenu(item.href);
      });

      clockEl.appendChild(el);
      return el;
    });

    // Cache refs
    minuteHandGroup = clockEl.querySelector('#minute-hand-group');
    hourHandGroup   = clockEl.querySelector('#hour-hand-group');
    minuteHandLine  = clockEl.querySelector('#minute-hand');

    // Events
    clockEl.addEventListener('pointerdown',   onPointerDown,   { passive: false });
    clockEl.addEventListener('pointermove',   onPointerMove,   { passive: false });
    clockEl.addEventListener('pointerup',     onPointerUp);
    clockEl.addEventListener('pointercancel', onPointerCancel);
    clockEl.addEventListener('contextmenu',   e => e.preventDefault());
    document.addEventListener('pointerdown',  onDocumentDown);

    document.body.appendChild(clockEl);
  }

  /* ═══════════════════════════════════════════════════════════════
     ACTIVE PAGE HIGHLIGHT
  ═══════════════════════════════════════════════════════════════ */
  function markActivePage() {
    const path = window.location.pathname;
    menuItemEls.forEach((el, i) => {
      const href = NAV_ITEMS[i].href;
      const active = href !== '/' && path.startsWith(href);
      el.classList.toggle('page-active', active);
    });
    // Home = clock itself is always "active" on home page — add glow class
    const isHome = path === '/' || path === '' || path === '/index.html';
    clockEl.classList.toggle('clock-is-home', isHome);
  }

  /* ═══════════════════════════════════════════════════════════════
     INIT
  ═══════════════════════════════════════════════════════════════ */
  function init() {
    buildClock();
    updateClock();
    markActivePage();
    setInterval(updateClock, 1000);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
