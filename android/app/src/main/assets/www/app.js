/* Polo 6R launcher UI.
 * Runs inside the Android WebView (window.PoloBridge present) or in a desktop browser (demo mode). */
(() => {
'use strict';
// Show any script error on screen so problems on the head unit are visible
window.onerror = (msg, src, line) => { try { const t = document.getElementById('toast'); t.textContent = 'Launcher error: ' + msg + ' (line ' + line + ')'; t.classList.add('show'); } catch (e) {} };
const B = window.PoloBridge || null;
const $ = s => document.querySelector(s);
const $$ = s => Array.from(document.querySelectorAll(s));

/* ---------------- icons (24x24 line icons) ---------------- */
const S = (body, extra = '') => `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" ${extra}>${body}</svg>`;
const ICON = {
  car: S('<path d="M5 11l1.6-4.2A2 2 0 0 1 8.5 5.5h7a2 2 0 0 1 1.9 1.3L19 11"/><rect x="3" y="11" width="18" height="6" rx="1.5"/><path d="M5.5 17v2M18.5 17v2"/><circle cx="7" cy="14" r="1"/><circle cx="17" cy="14" r="1"/>'),
  video: S('<rect x="3" y="5" width="18" height="14" rx="2"/><path d="M10 9.3v5.4l4.6-2.7z"/>'),
  phone: S('<path d="M5.2 3.8h3l1.6 4-2.1 1.4a11 11 0 0 0 7.1 7.1l1.4-2.1 4 1.6v3a2 2 0 0 1-2.1 2A16.5 16.5 0 0 1 3.2 5.9a2 2 0 0 1 2-2.1z"/>'),
  carplay: S('<rect x="3.5" y="3.5" width="17" height="17" rx="4.5"/><circle cx="12" cy="12" r="5"/><path d="M11 9.8v4.4l3.4-2.2z"/>'),
  bt: S('<path d="M7 7.5l10 9-5 4.5V3l5 4.5-10 9"/>'),
  radio: S('<rect x="3" y="8" width="18" height="12" rx="2"/><path d="M7 8l10-4"/><circle cx="15.5" cy="14" r="2.6"/><path d="M6 11.5h4M6 14h4M6 16.5h4"/>'),
  usb: S('<path d="M12 3v15"/><circle cx="12" cy="19.5" r="1.6"/><path d="M10 5.5L12 3l2 2.5"/><path d="M12 14l-4-2.5V9"/><path d="M12 12l4-2V7.5"/><rect x="15" y="5.5" width="2" height="2"/><circle cx="8" cy="8" r="1.1"/>'),
  aauto: S('<path d="M12 3.5l7.5 17-7.5-4.2-7.5 4.2z"/><path d="M12 8.5v7.8"/>'),
  nav: S('<path d="M12 21s-6-6.2-6-11a6 6 0 0 1 12 0c0 4.8-6 11-6 11z"/><circle cx="12" cy="10" r="2.2"/>'),
  gear: S('<circle cx="12" cy="12" r="3.2"/><path d="M12 2.8l1.4 2.4 2.7-.6.6 2.7 2.4 1.4-1.2 2.5 1.2 2.5-2.4 1.4-.6 2.7-2.7-.6L12 21.2l-1.4-2.4-2.7.6-.6-2.7-2.4-1.4L6.1 12 4.9 9.5l2.4-1.4.6-2.7 2.7.6z"/>'),
  image: S('<rect x="3" y="5" width="18" height="14" rx="2"/><circle cx="9" cy="10" r="1.8"/><path d="M21 16l-5-5-9 8"/>'),
  folder: S('<path d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>'),
  eq: S('<path d="M6 4v16M12 4v16M18 4v16"/><rect x="4" y="13" width="4" height="3" rx="1" fill="currentColor"/><rect x="10" y="7" width="4" height="3" rx="1" fill="currentColor"/><rect x="16" y="11" width="4" height="3" rx="1" fill="currentColor"/>'),
  globe: S('<circle cx="12" cy="12" r="9"/><path d="M3 12h18M12 3c3 3.5 3 14.5 0 18M12 3c-3 3.5-3 14.5 0 18"/>'),
  apps: S('<circle cx="6" cy="6" r="1.3"/><circle cx="12" cy="6" r="1.3"/><circle cx="18" cy="6" r="1.3"/><circle cx="6" cy="12" r="1.3"/><circle cx="12" cy="12" r="1.3"/><circle cx="18" cy="12" r="1.3"/><circle cx="6" cy="18" r="1.3"/><circle cx="12" cy="18" r="1.3"/><circle cx="18" cy="18" r="1.3"/>', ''),
  sliders: S('<path d="M4 6h10M18 6h2M4 12h4M12 12h8M4 18h12M20 18h0"/><circle cx="16" cy="6" r="2"/><circle cx="10" cy="12" r="2"/><circle cx="18" cy="18" r="2"/>'),
  plus: S('<circle cx="12" cy="12" r="9"/><path d="M12 8v8M8 12h8"/>'),
  minus: S('<circle cx="12" cy="12" r="9"/><path d="M8 12h8"/>'),
  speaker: S('<path d="M4 9.5h3.5L12 6v12l-4.5-3.5H4z"/><path d="M15 9.5a3.5 3.5 0 0 1 0 5M17.5 7a7 7 0 0 1 0 10"/>'),
  muted: S('<path d="M4 9.5h3.5L12 6v12l-4.5-3.5H4z"/><path d="M16 9.5l5 5M21 9.5l-5 5"/>'),
  grid8: `<svg viewBox="0 0 52 30" fill="currentColor"><rect x="0" y="0" width="10" height="10" rx="2"/><rect x="14" y="0" width="10" height="10" rx="2"/><rect x="28" y="0" width="10" height="10" rx="2"/><rect x="42" y="0" width="10" height="10" rx="2"/><rect x="0" y="16" width="10" height="10" rx="2"/><rect x="14" y="16" width="10" height="10" rx="2"/><rect x="28" y="16" width="10" height="10" rx="2"/><rect x="42" y="16" width="10" height="10" rx="2"/></svg>`,
  back: S('<path d="M10 5.5L3.5 12l6.5 6.5"/><path d="M4 12h16.5"/>'),
  recents: S('<rect x="5" y="5" width="14" height="14" rx="2.5"/>'),
  pump: S('<rect x="4" y="3.5" width="9" height="16.5" rx="1.3"/><path d="M4 9.5h9"/><path d="M13 8l3 2.6v6.4a1.5 1.5 0 0 0 3 0V9.2l-2.6-2.7"/><path d="M2.8 20.5h11.4"/>'),
  dist: S('<circle cx="4.5" cy="12" r="2.2"/><circle cx="19.5" cy="12" r="2.2"/><path d="M6.7 12h10.6"/>'),
  clock: S('<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>'),
  speedo: S('<path d="M3.5 17.5a8.5 8.5 0 1 1 17 0"/><path d="M12 17.5l4.2-5.2"/><circle cx="12" cy="17.5" r="1.2"/>'),
};
$$('[data-icon]').forEach(el => el.innerHTML = ICON[el.dataset.icon] || '');

/* ---------------- storage ---------------- */
const store = {
  get(k, d) { try { const v = localStorage.getItem('polo.' + k); return v == null ? d : JSON.parse(v); } catch (e) { return d; } },
  set(k, v) { try { localStorage.setItem('polo.' + k, JSON.stringify(v)); } catch (e) {} },
};
const settings = Object.assign({ units: 'kml', tank: 45, demo: true }, store.get('settings', {}));
const saveSettings = () => store.set('settings', settings);

/* ---------------- stage scaling ---------------- */
const stage = $('#stage');
function fit() {
  const s = Math.min(innerWidth / 1280, innerHeight / 720);
  stage.style.transform = `translate(-50%,-50%) scale(${s})`;
}
addEventListener('resize', fit); fit();

/* ---------------- toast ---------------- */
let toastT;
function toast(msg) {
  const t = $('#toast'); t.textContent = msg; t.classList.add('show');
  clearTimeout(toastT); toastT = setTimeout(() => t.classList.remove('show'), 2600);
}

/* ---------------- clock ---------------- */
function tick() {
  const d = new Date();
  $('#clock').textContent = d.getHours() + ':' + String(d.getMinutes()).padStart(2, '0');
}
tick(); setInterval(tick, 5000);

/* ---------------- tiles ---------------- */
// action: {internal:'vehicle'} | {pkg:'...'} | {intent:'android.settings.SETTINGS'}
// Tiles without a package ask you to pick the app the first time you tap them (long-press to change later).
// CarPlay and Android Auto both run through ZLink on this unit. Known ZLink package names are tried first,
// then any installed app whose name or package contains "zlink".
const ZLINK = { pkgs: ['com.zjinnova.zlink', 'com.zjinnova.zlink5', 'com.zjinnova.zlinkcarplay'], match: /z-?link/i };
const TILES = [
  [
    { id: 'usbvideo', label: 'USB Video', icon: 'video', pkgs: ['com.nwd.android.video.ui'], match: /video/i },
    { id: 'phone', label: 'Phone', icon: 'phone', pkgs: ['com.nwd.android.phone'], match: /phone|dialer|bluetooth phone/i },
    { id: 'carplay', label: 'Apple CarPlay', icon: 'carplay', hi: true, pkgs: ZLINK.pkgs, match: ZLINK.match },
    { id: 'btmusic', label: 'Bluetooth Music', icon: 'bt', pkgs: ['com.nwd.bt.music'], match: /bt.?music|bluetooth music|a2dp/i },
    { id: 'radio', label: 'Radio', icon: 'radio', internal: 'radio' },
    { id: 'usbmusic', label: 'USB Music', icon: 'usb', pkgs: ['com.nwd.android.music.ui'], match: /music/i },
    { id: 'aauto', label: 'Android Auto', icon: 'aauto', hi: true, pkgs: ZLINK.pkgs, match: ZLINK.match },
    { id: 'nav', label: 'Navigation', icon: 'nav', pkgs: ['com.google.android.apps.maps', 'com.waze'], match: /maps|navi|waze/i },
  ],
  [
    { id: 'settings', label: 'Settings', icon: 'gear', pkgs: ['com.android.settings'], intent: 'android.settings.SETTINGS' },
    { id: 'gallery', label: 'Pictures', icon: 'image', pkgs: ['com.alensw.PicFolder', 'com.google.android.apps.photos'], match: /gallery|photo|pic/i },
    { id: 'files', label: 'Files', icon: 'folder', pkgs: ['com.nwd.filemanager'], match: /file/i },
    { id: 'eq', label: 'Sound', icon: 'eq', pkgs: ['com.nwd.audioset'], match: /audio|dsp|equali[sz]er|sound/i },
    { id: 'browser', label: 'Browser', icon: 'globe', pkgs: ['com.android.chrome', 'org.mozilla.firefox'], match: /browser|chrome/i },
    { id: 'allapps', label: 'All apps', icon: 'apps', internal: 'allapps' },
    { id: 'launcher', label: 'Launcher', icon: 'sliders', internal: 'launcher' },
  ],
];
let mapping = store.get('mapping', {}); // tileId -> {pkg,label}
if (mapping.settings && mapping.settings.pkg !== 'com.android.settings') { delete mapping.settings; store.set('mapping', mapping); }
if (!store.get('mapFix15', false)) { mapping = {}; store.set('mapping', mapping); store.set('mapFix15', true); }

function tileEl(t) {
  const el = document.createElement('button');
  el.className = 'tile' + (t.hi ? ' hi' : '');
  el.dataset.id = t.id;
  el.innerHTML = `<div class="sq">${ICON[t.icon]}</div><div class="lb">${t.label}</div>`;
  if (!t.internal && !t.intent && !t.pkgs && !mapping[t.id]) el.classList.add('unset');
  return el;
}
const pagesEl = $('#pages'), dotsEl = $('#dots');
const carPage = document.getElementById('vehicle');
carPage.className = 'page carpage';
function buildHome() {
  pagesEl.innerHTML = ''; dotsEl.innerHTML = '';
  pagesEl.appendChild(carPage);                 // page 1: car info
  dotsEl.appendChild(document.createElement('i'));
  order.forEach((ids, i) => {
    const p = document.createElement('div'); p.className = 'page';
    ids.map(findTile).filter(Boolean).forEach(t => p.appendChild(tileEl(t)));
    pagesEl.appendChild(p);
    dotsEl.appendChild(document.createElement('i'));
  });
  setPage(homePage, false);
}
const HOME_PAGE = 1; // first apps page is the default; car info is page 0 (swipe right)
let homePage = HOME_PAGE;
function setPage(i, anim = true) {
  homePage = Math.max(0, Math.min(order.length, i));
  $('#home').classList.toggle('oncar', homePage === 0);
  pagesEl.style.transition = anim ? '' : 'none';
  pagesEl.style.transform = `translateX(${-homePage * 100}%)`;
  $$('#dots i').forEach((d, j) => d.classList.toggle('on', j === homePage));
}
const ALL_TILES = [].concat.apply([], TILES);
const findTile = id => ALL_TILES.find(t => t.id === id);

// Tile arrangement (hold a tile to move it). Saved per page; new tiles in future updates are added at the end.
function loadOrder() {
  const saved = store.get('tileOrder', null);
  let o = Array.isArray(saved) ? saved.map(p => (Array.isArray(p) ? p : []).filter(id => findTile(id))) : TILES.map(p => p.map(t => t.id));
  const seen = {}; o.forEach(p => p.forEach(id => { seen[id] = 1; }));
  ALL_TILES.forEach(t => {
    if (seen[t.id]) return;
    let last = o[o.length - 1];
    if (!last || last.length >= 8) { last = []; o.push(last); }
    last.push(t.id);
  });
  o = o.filter(p => p.length);
  while (o.length < 2) o.push([]);
  return o;
}
var order = loadOrder();

function openTile(id) {
  const t = findTile(id); if (!t) return;

  if (t.internal === 'radio') return openRadio();
  if (t.internal === 'allapps') return openAppSheet(null);
  if (t.internal === 'launcher') return openLauncherSettings();
  const m = mapping[id];
  if (m && m.pkg) return launchPkg(m.pkg, t.label);
  if (t.pkgs || t.match) {
    const apps = getApps();
    const hit = (t.pkgs || []).map(p => apps.find(a => a.pkg === p)).find(Boolean)
      || (t.match && apps.find(a => t.match.test(a.pkg) || t.match.test(a.label)));
    if (hit) return launchPkg(hit.pkg, t.label);
    toast(t.label + ': app not found – choose the one to use');
  }
  if (t.intent) {
    if (B) { if (!B.openAction(t.intent)) toast('Could not open ' + t.label); }
    else toast('Would open: ' + t.intent);
    return;
  }
  openAppSheet(id); // first use: choose app
}
function launchPkg(pkg, label) {
  if (B) { if (!B.launch(pkg)) toast(label + ' app not found – long-press the tile to choose another'); }
  else toast('Would launch ' + pkg);
}

/* ---------------- pointer handling (swipe, tap, long-press) ---------------- */
function gestures(el, { onSwipe, onTap, onLong, skip }) {
  let x0, y0, t0, moved, longT, target, dragging = false;
  el.addEventListener('pointerdown', e => {
    if (skip && skip()) return;
    x0 = e.clientX; y0 = e.clientY; t0 = Date.now(); moved = false; dragging = true;
    target = e.target.closest('[data-id],[data-tab]');
    if (target) target.classList.add('press');
    clearTimeout(longT);
    if (onLong && target) longT = setTimeout(() => { if (!moved) { dragging = false; target.classList.remove('press'); onLong(target, x0, y0); } }, 650);
  });
  el.addEventListener('pointermove', e => {
    if (!dragging) return;
    const s = stageScale();
    if (Math.abs(e.clientX - x0) / s > 14 || Math.abs(e.clientY - y0) / s > 14) { moved = true; clearTimeout(longT); target && target.classList.remove('press'); }
  });
  const end = e => {
    if (!dragging) return; dragging = false; clearTimeout(longT);
    target && target.classList.remove('press');
    const dx = (e.clientX - x0) / stageScale();
    if (moved && Math.abs(dx) > 60 && onSwipe) onSwipe(dx < 0 ? 1 : -1);
    else if (!moved && target && onTap) onTap(target);
  };
  el.addEventListener('pointerup', end);
  el.addEventListener('pointercancel', () => { dragging = false; clearTimeout(longT); target && target.classList.remove('press'); });
}
const stageScale = () => stage.getBoundingClientRect().width / 1280;

gestures($('#pager'), {
  onSwipe: d => setPage(homePage + d),
  onTap: el => openTile(el.dataset.id),
  onLong: (el, x, y) => { if (el.dataset.id) enterEdit(el, x, y); },
  skip: () => editing,
});

/* ---------------- arrange tiles: hold, then drag ---------------- */
let editing = false, drag = null, edgeT = null;
function enterEdit(tileEl, x, y) {
  editing = true;
  $('#home').classList.add('editing');
  if (tileEl) drag = { id: tileEl.dataset.id, x0: x, y0: y, moved: false, clone: null, fromLong: true };
  if (tileEl) markDragging();
}
function exitEdit() {
  editing = false; drag = null; clearTimeout(edgeT);
  $('#home').classList.remove('editing');
  store.set('tileOrder', order);
}
$('#editDone').addEventListener('click', exitEdit);
function markDragging() {
  $$('#pages .tile').forEach(t => t.classList.toggle('dragging', !!drag && drag.moved && t.dataset.id === drag.id));
}
function toStage(e) {
  const r = stage.getBoundingClientRect(), s = r.width / 1280;
  return { x: (e.clientX - r.left) / s, y: (e.clientY - r.top) / s };
}
function pageOf(id) { return order.findIndex(p => p.indexOf(id) >= 0); }
function moveTo(id, pageIdx, idx) {
  const from = pageOf(id); if (from < 0) return;
  order[from].splice(order[from].indexOf(id), 1);
  const dest = order[pageIdx];
  idx = Math.max(0, Math.min(dest.length, idx));
  dest.splice(idx, 0, id);
  if (dest.length > 8) {            // page full: push its last tile on to the next page
    const spill = dest.pop();
    if (!order[pageIdx + 1]) order.push([]);
    order[pageIdx + 1].unshift(spill);
  }
  order = order.filter((p, i) => p.length || i < 2);
  buildHome(); markDragging();
}
document.getElementById('pager').addEventListener('pointerdown', e => {
  if (!editing) return;
  const t = e.target.closest('.tile'); if (!t) return;
  drag = { id: t.dataset.id, x0: e.clientX, y0: e.clientY, moved: false, clone: null };
});
addEventListener('pointermove', e => {
  if (!drag) return;
  const s = stageScale();
  if (!drag.moved) {
    if (Math.abs(e.clientX - drag.x0) / s < 12 && Math.abs(e.clientY - drag.y0) / s < 12) return;
    drag.moved = true;
    const src = $$('#pages .tile').find(t => t.dataset.id === drag.id);
    drag.clone = src.cloneNode(true); drag.clone.className = 'tile dragclone';
    stage.appendChild(drag.clone); markDragging();
  }
  const p = toStage(e);
  drag.clone.style.left = (p.x - 120) + 'px'; drag.clone.style.top = (p.y - 70) + 'px';
  // drop position on the current page: nearest tile slot
  if (homePage === 0) return;                    // car info page: no tiles
  const page = pagesEl.children[homePage];
  const tiles = Array.prototype.slice.call(page.children);
  let best = tiles.length, bestD = 1e9;
  tiles.forEach((t, i) => {
    const r = t.getBoundingClientRect(), cx = r.left + r.width / 2, cy = r.top + r.height / 2;
    const d = Math.hypot(e.clientX - cx, e.clientY - cy);
    if (d < bestD) { bestD = d; best = i; }
  });
  const cur = order[homePage - 1].indexOf(drag.id);
  if (cur !== best && bestD / s < 140) moveTo(drag.id, homePage - 1, best);
  // hold at the left / right edge to move to the other page
  const pr = $('#pager').getBoundingClientRect(), edge = 60 * s;
  const dir = e.clientX < pr.left + edge ? -1 : e.clientX > pr.right - edge ? 1 : 0;
  if (dir && !edgeT && homePage + dir >= 1 && order[homePage - 1 + dir]) {
    edgeT = setTimeout(() => {
      edgeT = null;
      if (!drag) return;
      setPage(homePage + dir);
      moveTo(drag.id, homePage - 1, dir > 0 ? 0 : order[homePage - 1].length);
    }, 700);
  } else if (!dir && edgeT) { clearTimeout(edgeT); edgeT = null; }
});
addEventListener('pointerup', () => {
  if (!drag) return;
  const d = drag; drag = null; clearTimeout(edgeT); edgeT = null;
  if (d.clone) d.clone.remove();
  markDragging();
  store.set('tileOrder', order);
  if (!d.moved && editing && !d.fromLong) {       // tap while arranging = change the tile's app
    const t = findTile(d.id);
    if (t && !t.internal) openAppSheet(t.id); else toast('This tile is built in');
  }
});

/* ---------------- screens ---------------- */
let screen = 'home';
function show(name) {
  screen = name;
  $$('.screen').forEach(s => s.classList.toggle('active', s.id === name));
  if (name === 'vehicle') render();
}
window.goHome = () => { if (editing) exitEdit(); closeSheet(); show('home'); setPage(HOME_PAGE); };
window.showCarInfo = () => { if (editing) exitEdit(); closeSheet(); show('home'); setPage(0); };
window.onBack = () => { if (editing) return exitEdit(); if ($('#sheet').classList.contains('open')) closeSheet(); else if (screen !== 'home') show('home'); };
window.onResumeLauncher = () => { tick(); refreshDefault(); try { refreshNet(); refreshFix(); } catch (e) {} };

/* ---------------- default home screen ---------------- */
// Banner on the home screen until Polo Launcher is the default Home app (hide it with ✕).
function refreshDefault() {
  let isDef = true;
  try { isDef = !B || B.isDefaultLauncher(); } catch (e) {}
  $('#defBanner').classList.toggle('show', !isDef && !store.get('hideDefBanner', false));
}
window.refreshDefault = refreshDefault;
// Home button fix switched off by the unit? Offer to switch it back on (it also brings the logo when the car starts).
function refreshFix() {
  let off = false;
  try { off = !!B && !homeFix().on; } catch (e) {}
  $('#fixBanner').classList.toggle('show', off && !sessionStorage.getItem('hideFix'));
}
$('#fixOn').addEventListener('click', () => { if (B) B.openAccessibilitySettings(); });
$('#fixHide').addEventListener('click', () => { try { sessionStorage.setItem('hideFix', '1'); } catch (e) {} refreshFix(); });
$('#defMake').addEventListener('click', () => { if (B) B.requestDefaultLauncher(); });
$('#defHide').addEventListener('click', () => { store.set('hideDefBanner', true); refreshDefault(); toast('You can still do this from Launcher settings'); });

/* ---------------- side rail (you choose the buttons) ---------------- */
// Four buttons you pick + the app-grid / Home button at the bottom.
// Long-press a side-bar button (or use Launcher settings) to change it.
const RAIL_SPECIAL = {
  vehicle: { label: 'Car info', icon: 'car' },
  back: { label: 'Back', icon: 'back' }, recents: { label: 'Recent apps', icon: 'recents' },
  volup: { label: 'Volume +', icon: 'plus' }, voldown: { label: 'Volume –', icon: 'minus' }, mute: { label: 'Mute', icon: 'speaker' },
  none: { label: 'Empty', icon: 'none' },
};
const RAIL_OPTIONS = ['vehicle', 'phone', 'carplay', 'aauto', 'nav', 'btmusic', 'radio', 'usbmusic', 'usbvideo', 'eq',
  'settings', 'allapps', 'back', 'recents', 'volup', 'voldown', 'mute', 'none'];
const RAIL_DEFAULT = ['phone', 'nav', 'carplay', 'vehicle'];
let rail = store.get('rail2', RAIL_DEFAULT);
function railInfo(id) { const t = findTile(id); return t ? { label: t.label, icon: t.icon } : (RAIL_SPECIAL[id] || RAIL_SPECIAL.none); }
function buildRail() {
  const el = $('#rail'); el.innerHTML = '';
  rail.forEach((id, i) => {
    const b = document.createElement('button');
    b.dataset.id = id; b.dataset.slot = i;
    b.setAttribute('aria-label', railInfo(id).label);
    b.innerHTML = ICON[railInfo(id).icon] || '';
    if (id === 'none') b.className = 'empty';
    el.appendChild(b);
  });
  const h = document.createElement('button');
  h.className = 'gridbtn'; h.dataset.id = '__home'; h.setAttribute('aria-label', 'Home'); h.innerHTML = ICON.grid8;
  el.appendChild(h);
}
function railAction(id, b) {
  if (id === '__home') return window.goHome();
  if (id === 'vehicle') return window.showCarInfo();
  if (findTile(id)) return openTile(id);
  if (id === 'none') return;
  if (!B) return toast(railInfo(id).label);
  if (id === 'volup') B.volume(1);
  else if (id === 'voldown') B.volume(-1);
  else if (id === 'mute') { const m = B.toggleMute(); b.innerHTML = ICON[m ? 'muted' : 'speaker']; }
  else if (id === 'back' || id === 'recents') {
    if (!B.globalAction(id)) { toast('Switch on “Home button fix” in Launcher settings to use this button'); }
  }
}
function openRailSheet(slot) {
  openSheet('Side bar button ' + (slot + 1), `
    <div class="row"><div>Choose what this button does<div class="sub">Back and Recent apps need the Home button fix switched on</div></div></div>
    <div class="applist" style="margin-top:12px">${RAIL_OPTIONS.map(id => {
      const r = railInfo(id);
      return `<button class="app${rail[slot] === id ? ' cur' : ''}" data-opt="${id}"><div class="ph ic-ph">${ICON[r.icon] || ''}</div><span>${r.label}</span></button>`;
    }).join('')}</div>`);
  sheetBody.onclick = e => {
    const a = e.target.closest('[data-opt]'); if (!a) return;
    rail[slot] = a.dataset.opt; store.set('rail2', rail); buildRail(); closeSheet();
    toast('Side bar button ' + (slot + 1) + ': ' + railInfo(a.dataset.opt).label);
  };
}
buildRail();
gestures($('#rail'), {
  onTap: el => railAction(el.dataset.id, el),
  onLong: el => { if (el.dataset.slot != null) openRailSheet(+el.dataset.slot); },
});
$('#fuelChip').addEventListener('click', () => window.showCarInfo());

/* ---------------- sheet: app picker & launcher settings ---------------- */
const sheet = $('#sheet'), sheetBody = $('#sheetBody');
let sheetOpenedAt = 0;
function openSheet(title, html) { $('#sheetTitle').textContent = title; sheetBody.innerHTML = html; sheet.classList.add('open'); sheetBody.scrollTop = 0; sheetOpenedAt = Date.now(); }
// ignore the "click" that follows the tap which opened the sheet (it would pick whatever is under the finger)
sheet.addEventListener('click', e => { if (Date.now() - sheetOpenedAt < 500) { e.stopPropagation(); e.preventDefault(); } }, true);
function closeSheet() { sheet.classList.remove('open'); }
$('#sheetClose').addEventListener('click', closeSheet);
sheet.addEventListener('click', e => { if (e.target === sheet) closeSheet(); });

const DEMO_APPS = [
  ['Radio', 'com.desay.radio'], ['Bluetooth', 'com.desay.bt'], ['Music', 'com.desay.music'], ['Video', 'com.desay.video'],
  ['ZLink', 'com.zjinnova.zlink'], ['Google Maps', 'com.google.android.apps.maps'], ['Waze', 'com.waze'],
  ['Spotify', 'com.spotify.music'], ['YouTube Music', 'com.google.android.apps.youtube.music'], ['Settings', 'com.android.settings'],
].map(([label, pkg]) => ({ label, pkg }));

function getApps() {
  if (!B) return DEMO_APPS;
  try { return JSON.parse(B.getApps()); } catch (e) { return []; }
}
const iconCache = {};
function appIcon(pkg) {
  if (!B) return '';
  if (!(pkg in iconCache)) { try { iconCache[pkg] = B.getIcon(pkg) || ''; } catch (e) { iconCache[pkg] = ''; } }
  return iconCache[pkg];
}
function openAppSheet(tileId) {
  const t = tileId ? findTile(tileId) : null;
  const apps = getApps().sort((a, b) => a.label.localeCompare(b.label));
  const cur = t && mapping[t.id];
  const html = (t ? `<div class="row"><div>Choose the app this tile opens${cur ? `<div class="sub">Now: ${cur.label} (${cur.pkg})</div>` : ''}</div>${cur ? '<button class="btn" data-clear="1">Clear</button>' : ''}</div>` : '')
    + `<div class="applist" style="margin-top:12px">${apps.map(a => {
        const ic = appIcon(a.pkg);
        return `<button class="app" data-pkg="${a.pkg}" data-label="${a.label.replace(/"/g, '&quot;')}">${ic ? `<img src="${ic}" alt="">` : '<div class="ph"></div>'}<span>${a.label}</span></button>`;
      }).join('')}</div>`;
  openSheet(t ? t.label : 'All apps', html);
  sheetBody.onclick = e => {
    if (e.target.closest('[data-clear]')) { delete mapping[t.id]; store.set('mapping', mapping); buildHome(); closeSheet(); return; }
    const a = e.target.closest('.app'); if (!a) return;
    if (t) {
      mapping[t.id] = { pkg: a.dataset.pkg, label: a.dataset.label };
      store.set('mapping', mapping); buildHome(); closeSheet();
      toast(`${t.label} → ${a.dataset.label}`);
      launchPkg(a.dataset.pkg, a.dataset.label);
    } else { closeSheet(); launchPkg(a.dataset.pkg, a.dataset.label); }
  };
}

function openLauncherSettings() {
  let diag = 'Running in a desktop browser (no head unit bridge).';
  if (B) {
    try {
      const info = JSON.parse(B.getDeviceInfo());
      const pk = JSON.parse(B.getCarPackages());
      diag = `Unit: ${info.manufacturer} ${info.model} (${info.device})\nAndroid ${info.android} / SDK ${info.sdk}\nScreen: ${info.screen}\n`
        + `Vehicle data: ${info.vehicleStatus}\n\nCar / system packages found (${pk.length}):\n${pk.join('\n')}`;
    } catch (e) { diag = 'Could not read device info: ' + e; }
  }
  diag += `\nWeb view: ${innerWidth}x${innerHeight} @${devicePixelRatio}x`;
  openSheet('Launcher', `
    <div class="row"><div>Home button fix ${homeFix().on ? '<span class="pill on">On</span>' : '<span class="pill off">Off</span>'}<div class="sub">Makes the unit's Home button open Polo Launcher</div></div><button class="btn" data-homefix="1">Set up</button></div>
    <div class="row"><div>VW logo when the car starts<div class="sub">Shows for a moment each time the unit wakes up, then returns to where you were</div></div>
      <div style="white-space:nowrap;display:flex"><div class="seg" data-set="splash">
        <button data-v="true" class="${splashOn() ? 'on' : ''}">On</button><button data-v="false" class="${!splashOn() ? 'on' : ''}">Off</button></div>
        <button class="btn" data-splashprev="1" style="margin-left:10px">Preview</button></div></div>
    <div class="row"><div>Find car data<div class="sub">Works out how this unit sends fuel / trip data</div></div><button class="btn" data-cardata="1">Open</button></div>
    <div class="row"><div>Updates<div class="sub">${updInfoText()}</div></div><div style="white-space:nowrap"><button class="btn" data-updcheck="1">Check now</button><button class="btn" data-updsrc="1">Source</button></div></div>
    <div class="row"><div>Side bar buttons<div class="sub">Tap one to change it (or long-press it on the side bar)</div></div>
      <div class="railpick">${rail.map((id, i) => `<button data-railslot="${i}">${ICON[railInfo(id).icon] || ''}</button>`).join('')}</div></div>
    <div class="row"><div>Consumption units</div><div class="seg" data-set="units">
      <button data-v="kml" class="${settings.units === 'kml' ? 'on' : ''}">km/l</button>
      <button data-v="l100" class="${settings.units === 'l100' ? 'on' : ''}">l/100 km</button></div></div>
    <div class="row"><div>Fuel tank size<div class="sub">Polo 6R: 45 litres</div></div><div class="seg" data-set="tank">
      ${[40, 45, 55].map(v => `<button data-v="${v}" class="${settings.tank === v ? 'on' : ''}">${v} l</button>`).join('')}</div></div>
    <div class="row"><div>Show demo data<div class="sub">Used only until real vehicle data is connected</div></div><div class="seg" data-set="demo">
      <button data-v="true" class="${settings.demo ? 'on' : ''}">On</button><button data-v="false" class="${!settings.demo ? 'on' : ''}">Off</button></div></div>
    <div class="row"><div>Default home screen<div class="sub" id="defSub">${!B ? 'Only available on the head unit' : (B.isDefaultLauncher() ? 'Polo Launcher opens when you press Home' : 'Another app opens when you press Home')}</div></div>
      ${!B ? '' : (B.isDefaultLauncher() ? '<button class="btn ok" data-default="1">✓ Default</button>' : '<button class="btn" data-default="1">Make default</button>')}</div>
    <div class="row"><div>Tiles<div class="sub">Hold a tile to move it or change its app</div></div><button class="btn" data-reset="1">Reset layout</button></div>
    <div class="row"><div>Long-term trip data<div class="sub">Start the long-term memory again from today</div></div><button class="btn" data-ltreset="1">Reset</button></div>
    <div class="row" style="display:block;border:0"><div>Diagnostics <span class="sub">(send this to whoever is setting up the vehicle data)</span></div><pre class="diag">${diag.replace(/</g, '&lt;')}</pre></div>`);
  sheetBody.onclick = e => {
    if (e.target.closest('[data-default]')) { if (B) B.requestDefaultLauncher(); closeSheet(); return; }
    const rs = e.target.closest('[data-railslot]'); if (rs) return openRailSheet(+rs.dataset.railslot);
    if (e.target.closest('[data-homefix]')) return openHomeFixSheet();
    if (e.target.closest('[data-cardata]')) return openCarDataSheet();
    if (e.target.closest('[data-ltreset]')) { if (B) B.resetLongTerm(); toast('Long-term trip data reset'); return; }
    if (e.target.closest('[data-splashprev]')) { if (B) B.showSplash(); else toast('Only on the head unit'); return; }
    if (e.target.closest('[data-updcheck]')) { if (B) B.checkUpdates(); else toast('Only on the head unit'); return; }
    if (e.target.closest('[data-updsrc]')) return openUpdateSourceSheet();
    const r = e.target.closest('[data-reset]');
    if (r) { mapping = {}; store.set('mapping', mapping); order = TILES.map(p => p.map(t => t.id)); store.set('tileOrder', order); buildHome(); toast('Tiles and app assignments reset'); return; }
    const b = e.target.closest('.seg button'); if (!b) return;
    const key = b.parentElement.dataset.set; let v = b.dataset.v;
    if (key === 'splash') { const on = v === 'true'; if (B) B.setSplashOnStart(on); b.parentElement.querySelectorAll('button').forEach(x => x.classList.toggle('on', x === b)); return; }
    if (key === 'tank') v = +v; if (key === 'demo') v = v === 'true';
    settings[key] = v; saveSettings();
    b.parentElement.querySelectorAll('button').forEach(x => x.classList.toggle('on', x === b));
    if (key === 'demo') { v ? startDemo() : stopDemo(); }
    render();
  };
}

/* ---------------- Home button fix ---------------- */
function homeFix() { try { return B ? JSON.parse(B.homeFixStatus()) : { on: false, targets: [], extra: [], recent: [] }; } catch (e) { return { on: false, targets: [], extra: [], recent: [] }; } }
const esc = t => String(t).replace(/&/g, '&amp;').replace(/</g, '&lt;');
function openHomeFixSheet() {
  const h = homeFix();
  const extra = h.extra || [];
  const targets = (h.targets || []).map(t => esc(t.label) + ' <span class="sub">(' + esc(t.pkg) + ')</span>').join('<br>') || '<span class="sub">none detected</span>';
  const recent = (h.recent || []).filter(r => !(h.targets || []).some(t => t.pkg === r.pkg));
  openSheet('Home button fix', `
    <div class="row" style="display:block"><div>Status: ${h.on ? '<span class="pill on" style="margin-left:0">On</span>' : '<span class="pill off" style="margin-left:0">Off</span>'}</div>
      <div class="sub" style="margin-top:8px">${h.on
        ? 'The Home button now opens Polo Launcher straight away.'
        : 'Tap <b>Turn on</b>, find <b>Polo Launcher Home button</b> in the list (sometimes under “Installed apps” or “Downloaded services”) and switch it on.'}</div>
      <div style="margin-top:14px"><button class="btn" data-a11y="1">${h.on ? 'Open accessibility settings' : 'Turn on'}</button></div></div>
    <div class="row" style="display:block"><div>Old home screens replaced automatically</div><div class="sub" style="margin-top:6px">${targets}</div></div>
    <div class="row" style="display:block;border:0"><div>Still seeing the old screen?</div>
      <div class="sub" style="margin-top:6px">Press Home, come back here, and tap the app that appeared:</div>
      <div style="margin-top:10px">${recent.length ? recent.map(r => `<div class="row"><div>${esc(r.label)}<div class="sub">${esc(r.pkg)}</div></div>
        <button class="btn${extra.indexOf(r.pkg) >= 0 ? ' ok' : ''}" data-redir="${esc(r.pkg)}">${extra.indexOf(r.pkg) >= 0 ? '✓ Replaced' : 'Replace'}</button></div>`).join('')
        : '<div class="sub">Nothing recorded yet – switch the fix on first.</div>'}</div></div>`);
  sheetBody.onclick = e => {
    if (e.target.closest('[data-a11y]')) { if (B) B.openAccessibilitySettings(); else toast('Only on the head unit'); return; }
    const r = e.target.closest('[data-redir]');
    if (r && B) { const on = extra.indexOf(r.dataset.redir) < 0; B.setHomeRedirect(r.dataset.redir, on); openHomeFixSheet(); }
  };
}

/* ---------------- Find car data ---------------- */
let scanReport = '', scanState = 'Not scanned yet';
const sniffLog = [];
function openCarDataSheet() {
  openSheet('Find car data', `
    <div class="row" style="display:block"><div>How to use</div>
      <div class="sub" style="margin-top:6px">1. Switch the ignition on (engine running is best).<br>
      2. Tap <b>Start scan</b> and keep this screen open for about 2 minutes – drive if you can.<br>
      3. Tap <b>Save report</b> and send the file from the unit's <b>Download</b> folder (or photos of this screen).</div></div>
    <div class="row"><div id="scanState">${esc(scanState)}</div><div style="white-space:nowrap"><button class="btn" data-scan="1">Start scan</button><button class="btn" data-save="1">Save report</button></div></div>
    <div class="sub" style="margin-top:12px">Live from Car Info</div>
    <pre class="diag" id="carRaw">${esc(B ? B.getRawCarData() : 'Only on the head unit')}</pre>
    <div class="sub" style="margin-top:12px">Live car messages</div>
    <pre class="diag" id="sniff">${esc(sniffLog.slice(-60).join('\n')) || 'Nothing yet…'}</pre>
    <div class="sub" style="margin-top:12px">What was found</div>
    <pre class="diag" id="scanOut">${esc(scanReport) || '–'}</pre>`);
  sheetBody.onclick = e => {
    if (e.target.closest('[data-scan]')) {
      if (!B) return toast('Only on the head unit');
      scanState = 'Scanning… (about 30 seconds)'; $('#scanState').textContent = scanState;
      B.startCarScan(navigator.userAgent);
    }
    if (e.target.closest('[data-save]')) { if (!B) return toast('Only on the head unit'); toast(B.saveCarReport()); }
  };
}
window.onScanDone = (report, n) => {
  scanReport = report; scanState = n ? 'Scan done – listening to ' + n + ' kinds of car message' : 'Scan done – no car messages found';
  const a = $('#scanState'), b = $('#scanOut');
  if (a) a.textContent = scanState; if (b) b.textContent = report;
};
window.onSniff = line => {
  sniffLog.push(line); if (sniffLog.length > 500) sniffLog.shift();
  const el = $('#sniff'); if (el) el.textContent = sniffLog.slice(-60).join('\n');
};

function splashOn() { try { return B ? B.getSplashOnStart() : true; } catch (e) { return true; } }

/* ---------------- network status icon ---------------- */
function netSvg(n) {
  const lv = n.level || 0, on = i => (i <= lv ? '' : ' class="dim"');
  const warn = n.internet === false && n.type !== 'none'
    ? '<circle cx="20.5" cy="18.5" r="3.3" fill="#ffb020" stroke="none"/><path d="M20.5 16.6v2.2M20.5 20.2v.2" stroke="#1a1000" stroke-width="1.4"/>' : '';
  if (n.type === 'wifi') return S(`<circle cx="12" cy="18.5" r="1.4" fill="currentColor"/><path${on(1)} d="M8.4 15.3a5.2 5.2 0 0 1 7.2 0"/><path${on(2)} d="M5.4 12.2a9.4 9.4 0 0 1 13.2 0"/><path${on(3)} d="M2.4 9.1a13.6 13.6 0 0 1 19.2 0"/>${warn}`);
  if (n.type === 'cell') return S(`<rect x="3" y="16" width="3" height="4" rx=".6"${on(1)} fill="currentColor"/><rect x="8" y="12.5" width="3" height="7.5" rx=".6"${on(2)} fill="currentColor"/><rect x="13" y="9" width="3" height="11" rx=".6"${on(3)} fill="currentColor"/><rect x="18" y="5.5" width="3" height="14.5" rx=".6"${on(4)} fill="currentColor"/>${warn}`);
  if (n.type === 'eth' || n.type === 'other') return S(`<rect x="7" y="4" width="10" height="9" rx="1.5"/><path d="M12 13v4M5 21v-4h14v4"/>${warn}`);
  return S('<path class="dim" d="M5.4 12.2a9.4 9.4 0 0 1 13.2 0M2.4 9.1a13.6 13.6 0 0 1 19.2 0M8.4 15.3a5.2 5.2 0 0 1 7.2 0"/><circle cx="12" cy="18.5" r="1.4" fill="currentColor" class="dim"/><path d="M4 3.5l16 17"/>');
}
function refreshNet() {
  let n = { type: 'wifi', level: 3, internet: true }; // desktop preview
  if (B) { try { n = JSON.parse(B.getNetworkStatus()); } catch (e) { n = { type: 'none' }; } }
  const el = $('#netIcon');
  el.innerHTML = netSvg(n);
  el.classList.toggle('off', n.type === 'none');
  el.setAttribute('aria-label', n.type === 'none' ? 'No connection' : n.type === 'wifi' ? 'Wi-Fi' : n.type === 'cell' ? 'Mobile data' : 'Wired');
}
refreshNet(); setInterval(refreshNet, 5000);

/* ---------------- updates ---------------- */
function updInfo() { try { return B ? JSON.parse(B.getUpdateInfo()) : { source: '', appVersion: 'browser', webVersion: '-' }; } catch (e) { return {}; } }
function updInfoText() {
  const u = updInfo();
  return 'App ' + esc(u.appVersion) + ' · Screens v' + esc(u.webVersion) + ' · ' + (u.source ? 'Source: ' + esc(u.source) : 'No update source set');
}
function openUpdateSourceSheet() {
  const u = updInfo();
  openSheet('Update source', `
    <div class="row" style="display:block;border:0"><div>Where the launcher looks for updates</div>
      <div class="sub" style="margin-top:6px">A web address that holds <b>update.json</b>. Screen updates are applied automatically;
      app updates show a banner and you confirm the install once.</div>
      <input class="field" id="updUrl" type="url" inputmode="url" autocapitalize="off" autocorrect="off" spellcheck="false"
        placeholder="e.g. http://192.168.1.20:8765/" value="${esc(u.source || '')}">
      <div style="margin-top:16px"><button class="btn" data-updsave="1">Save</button><button class="btn" data-updcheck2="1">Save and check now</button></div></div>`);
  sheetBody.onclick = e => {
    const save = e.target.closest('[data-updsave]'), chk = e.target.closest('[data-updcheck2]');
    if (!save && !chk) return;
    const v = $('#updUrl').value.trim();
    if (B) { B.setUpdateSource(v); if (chk) B.checkUpdates(); }
    closeSheet(); toast(v ? 'Update source saved' : 'Update source cleared');
  };
}
window.onUpdate = st => {
  if (!st) return;
  if (st.state === 'app-available') {
    $('#updInstall').textContent = 'Update ' + (st.versionName || '') + ' available – tap to install';
    if (!store.get('hideUpd_' + st.versionName, false)) $('#updBanner').classList.add('show');
  }
  if (st.state !== 'app-available' || $('#sheet').classList.contains('open')) toast(st.message);
};
$('#updInstall').addEventListener('click', () => { if (B) B.installUpdate(); $('#updBanner').classList.remove('show'); });
$('#updHide').addEventListener('click', () => { $('#updBanner').classList.remove('show'); toast('You can install it later from Launcher › Updates'); });

/* ---------------- radio (the unit's own FM/AM tuner, via its radio service) ---------------- */
const RICON = {
  seekdown: S('<path d="M5 6v12"/><path d="M19 6l-7 6 7 6z" fill="currentColor"/><path d="M12 6l-7 6 7 6z" fill="currentColor"/>'),
  seekup: S('<path d="M19 6v12"/><path d="M5 6l7 6-7 6z" fill="currentColor"/><path d="M12 6l7 6-7 6z" fill="currentColor"/>'),
  down: S('<path d="M15 5l-7 7 7 7"/>'),
  up: S('<path d="M9 5l7 7-7 7"/>'),
  power: S('<path d="M12 3v8"/><path d="M6.3 6.8a8 8 0 1 0 11.4 0"/>'),
};
$$('.rd-controls button').forEach(b => { b.innerHTML = RICON[b.dataset.rc]; });
let R = B ? {} : { available: true, on: true, band: 0, freq: 9450, ps: 'KFM', rt: 'Best variety of music', stereo: true,
  presets: [9450, 9750, 9420, 10370, 8870, 10200, 8950, 0, 0, 0, 0, 0].map(f => (f ? { band: 0, freq: f, ps: '' } : {})) };
let rBank = 0;
const isFM = f => f >= 5000;
const FM_MIN = 8750, FM_MAX = 10800, AM_MIN = 522, AM_MAX = 1620;
function fmtFreq(f) {
  if (f == null) return '--';
  return isFM(f) ? (f % 10 ? (f / 100).toFixed(2) : (f / 100).toFixed(1)) : String(f);
}
window.onRadio = st => {
  if (typeof st === 'string') { try { st = JSON.parse(st); } catch (e) { return; } }
  if (!st) return;
  R = Object.assign(R, st);
  if (screen === 'radio') renderRadio();
};
window.openRadio = () => openRadio();
function openRadio() {
  show('radio');
  if (B) { try { R = Object.assign(R, JSON.parse(B.radioState())); } catch (e) {} B.radioCmd('open', 0, 0); }
  R.on = true;
  renderRadio();
}
function buildDial(fm) {
  const sc = $('#rdScale'); sc.innerHTML = '';
  const lo = fm ? FM_MIN : AM_MIN, hi = fm ? FM_MAX : AM_MAX;
  const step = fm ? 50 : 18, major = fm ? 200 : 108;
  for (let f = Math.ceil(lo / step) * step; f <= hi; f += step) {
    const x = (f - lo) / (hi - lo) * 100;
    const isMajor = f % major === 0;
    sc.insertAdjacentHTML('beforeend', `<i class="${isMajor ? 'major' : ''}" style="left:${x}%;height:${isMajor ? 22 : 12}px"></i>`
      + (isMajor && x > 2 && x < 97 ? `<span style="left:${x}%">${fm ? f / 100 : f}</span>` : ''));
  }
  sc.dataset.fm = fm ? '1' : '0';
}
function renderRadio() {
  const fm = R.freq == null ? true : isFM(R.freq);
  if ($('#rdScale').dataset.fm !== (fm ? '1' : '0')) buildDial(fm);
  $('#rdPs').textContent = R.available === false ? 'Radio not available' : (R.ps || (R.on === false ? 'Radio off' : 'Radio'));
  $('#rdFreq').textContent = fmtFreq(R.freq);
  $('#rdUnit').textContent = fm ? 'MHz' : 'kHz';
  $('#rdSt').classList.toggle('on', !!R.stereo && fm);
  $('#rdRt').textContent = R.rt || '';
  $$('#rdTabs button').forEach(b => b.classList.toggle('on', (b.dataset.band === 'fm') === fm));
  $('#rdInk').style.transform = `translateX(${fm ? 0 : 100}%)`;
  const lo = fm ? FM_MIN : AM_MIN, hi = fm ? FM_MAX : AM_MAX;
  if (R.freq != null) $('#rdNeedle').style.left = Math.max(0, Math.min(100, (R.freq - lo) / (hi - lo) * 100)) + '%';
  $('.rd-power').classList.toggle('off', R.on === false);
  // presets 1-6 / 7-12
  const pr = (R.presets || []).slice(rBank * 6, rBank * 6 + 6);
  while (pr.length < 6) pr.push({});
  $('#rdPresets').innerHTML = pr.map((p, i) => {
    const n = rBank * 6 + i + 1, has = p && p.freq;
    const cur = has && p.freq === R.freq;
    return `<button data-slot="${n - 1}" class="${cur ? 'cur' : ''}"><span class="n">${n}</span><span class="f">${has ? fmtFreq(p.freq) : '–'}</span><span class="p">${has ? (p.ps || (isFM(p.freq) ? 'MHz' : 'kHz')) : 'hold to store'}</span></button>`;
  }).join('') + `<button class="bank" data-bank="1">${rBank ? '1–6' : '7–12'}</button>`;
}
function radioCmd(cmd, a, b) { if (B) B.radioCmd(cmd, a || 0, b || 0); else toast('radio: ' + cmd); }
function tuneTo(f) {
  const fm = isFM(R.freq || 9000);
  f = fm ? Math.round(f / 10) * 10 : Math.round(f / 9) * 9;
  f = Math.max(fm ? FM_MIN : AM_MIN, Math.min(fm ? FM_MAX : AM_MAX, f));
  R.freq = f; R.ps = ''; R.rt = '';
  renderRadio();
  radioCmd('tune', f, R.band || 0);
}
$('.rd-controls').addEventListener('click', e => {
  const b = e.target.closest('button'); if (!b) return;
  const c = b.dataset.rc, fm = isFM(R.freq || 9000);
  if (c === 'seekup') radioCmd('seek', 1);
  if (c === 'seekdown') radioCmd('seek', 0);
  if (c === 'up') tuneTo((R.freq || 0) + (fm ? 10 : 9));
  if (c === 'down') tuneTo((R.freq || 0) - (fm ? 10 : 9));
  if (c === 'power') { R.on = R.on === false; radioCmd(R.on ? 'play' : 'off'); renderRadio(); }
});
$('.rd-tools').addEventListener('click', e => {
  const b = e.target.closest('[data-rc]'); if (!b) return;
  if (b.dataset.rc === 'scan') { radioCmd('scan'); toast('Scanning for stations…'); }
  if (b.dataset.rc === 'ams') { radioCmd('ams'); toast('Finding the strongest stations and storing them…'); }
});
$('#rdTabs').addEventListener('click', e => {
  const b = e.target.closest('[data-band]'); if (!b) return;
  const wantFM = b.dataset.band === 'fm';
  if (wantFM !== isFM(R.freq || 9000)) radioCmd('band');
});
// presets: tap = tune, hold = store the current station
(function () {
  let t, held = false, target = null;
  const el = $('#rdPresets');
  el.addEventListener('pointerdown', e => {
    target = e.target.closest('[data-slot]'); held = false;
    if (!target) return;
    target.classList.add('press');
    t = setTimeout(() => { held = true; target.classList.remove('press'); radioCmd('save', +target.dataset.slot); toast('Stored in preset ' + (+target.dataset.slot + 1)); }, 700);
  });
  const end = () => { clearTimeout(t); if (target) target.classList.remove('press'); };
  el.addEventListener('pointerup', end); el.addEventListener('pointercancel', () => { end(); held = true; });
  el.addEventListener('click', e => {
    const bank = e.target.closest('[data-bank]');
    if (bank) { rBank = rBank ? 0 : 1; renderRadio(); return; }
    const p = e.target.closest('[data-slot]'); if (!p || held) return;
    const pr = (R.presets || [])[+p.dataset.slot];
    if (pr && pr.freq) { R.ps = pr.ps || ''; tuneTo(pr.freq); }
  });
})();
// dial: tap or drag to tune
(function () {
  const d = $('#rdDial'); let drag = false;
  const at = e => {
    const r = d.getBoundingClientRect(), x = Math.max(0, Math.min(1, (e.clientX - r.left) / r.width));
    const fm = isFM(R.freq || 9000), lo = fm ? FM_MIN : AM_MIN, hi = fm ? FM_MAX : AM_MAX;
    return lo + x * (hi - lo);
  };
  d.addEventListener('pointerdown', e => { drag = true; d.setPointerCapture && d.setPointerCapture(e.pointerId); const f = at(e); $('#rdNeedle').style.left = ((e.clientX - d.getBoundingClientRect().left) / d.getBoundingClientRect().width * 100) + '%'; $('#rdFreq').textContent = fmtFreq(isFM(f) ? Math.round(f / 10) * 10 : Math.round(f / 9) * 9); });
  d.addEventListener('pointermove', e => { if (!drag) return; const f = at(e); $('#rdNeedle').style.left = ((e.clientX - d.getBoundingClientRect().left) / d.getBoundingClientRect().width * 100) + '%'; $('#rdFreq').textContent = fmtFreq(isFM(f) ? Math.round(f / 10) * 10 : Math.round(f / 9) * 9); });
  d.addEventListener('pointerup', e => { if (!drag) return; drag = false; tuneTo(at(e)); });
})();

/* ---------------- vehicle data ---------------- */
// Flat schema (also what the Android side sends):
//  fuel_litres, fuel_percent, range_km, temp_c,
//  ss_distance_km, ss_time_min, ss_consumption_kml | ss_consumption_l100, ss_avg_speed_kmh   (since start)
//  rf_... same fields                                                                       (since refuelling)
//  lt_distance_km, lt_time_min, lt_consumption_kml | lt_consumption_l100, lt_avg_speed_kmh
let V = {};
let live = false;
window.onVehicle = d => {
  if (typeof d === 'string') { try { d = JSON.parse(d); } catch (e) { return; } }
  if (!d) return;
  if (!d._demo && !live) { live = true; stopDemo(); }
  if (d._demo && live) return;
  Object.assign(V, d);
  render();
};

let tab = 0;
function setTab(i) {
  if (i < 0 || i > 1 || i === tab) return; // Since Start, Since Refuelling
  tab = i;
  $$('#tabs button').forEach((b, j) => b.classList.toggle('on', j === tab));
  $('#ink').style.transform = `translateX(${tab * 100}%)`;
  const st = $('.stats'); st.classList.add('fade');
  setTimeout(() => { render(); st.classList.remove('fade'); }, 140);
}
$('.stats').classList.add('vals');
gestures($('#vehicle'), { onTap: el => el.dataset.tab != null && setTab(+el.dataset.tab) });
$('#consStat').addEventListener('click', () => { settings.units = settings.units === 'kml' ? 'l100' : 'kml'; saveSettings(); render(); });

const num = (v, dp = 0) => (v == null || isNaN(v)) ? '--' : Number(v).toLocaleString('en-US', { minimumFractionDigits: dp, maximumFractionDigits: dp, useGrouping: false });
const hm = min => (min == null || isNaN(min)) ? '--' : Math.floor(min / 60) + ':' + String(Math.floor(min % 60)).padStart(2, '0');

function consumption(prefix) {
  let kml = V[prefix + '_consumption_kml'], l100 = V[prefix + '_consumption_l100'];
  if (kml == null && l100) kml = 100 / l100;
  if (l100 == null && kml) l100 = 100 / kml;
  return settings.units === 'kml' ? { v: num(kml, 1), u: 'km/l' } : { v: num(l100, 1), u: 'l/100 km' };
}

function render() {
  const tank = settings.tank || 45;
  let litres = V.fuel_litres, pct = V.fuel_percent;
  if (litres == null && pct != null) litres = pct / 100 * tank;
  if (pct == null && litres != null) pct = litres / tank * 100;
  const low = litres != null && litres <= 7; // Polo 6R fuel reserve light comes on at ~7 l

  // status chip
  $('#fuelMini').style.width = (pct == null ? 0 : Math.max(2, Math.min(100, pct))) + '%';
  $('#fuelPct').textContent = litres == null ? '--L' : Math.round(litres) + 'L';
  $('#fuelChip').classList.toggle('low', low);
  $('#tempTxt').textContent = V.temp_c == null ? '' : num(V.temp_c, 0) + '°C';

  // vehicle screen
  $('#range').textContent = num(V.range_km);
  $('#rangeRow').classList.toggle('low', low);
  $('#fuelFill').style.width = (pct == null ? 0 : Math.max(1.5, Math.min(100, pct))) + '%';
  $('#fuelText').textContent = litres == null ? '--L' : `${num(litres, 0)}L · ${Math.round(pct)}%`;
  $('#fuelBox').classList.toggle('low', low);
  const cl = [];
  if (V.odometer_km != null) cl.push(num(V.odometer_km) + ' km');
  if (V.rpm != null) cl.push(num(V.rpm) + ' rpm');
  if (V.battery_v != null) cl.push(num(V.battery_v, 1) + ' V');
  $('#carLine').textContent = cl.join('  ·  ');

  // Since Start: distance this trip; time, consumption, avg. speed for today.
  // Since Refuelling: distance and time since the tank was filled; consumption and avg. speed as Since Start.
  const p = ['ss', 'rf'][tab];
  const dk = V[p + '_distance_km'];
  $('#sDist').textContent = dk != null && dk < 100 ? num(dk, 1) : num(dk, 0);
  $('#sTime').textContent = hm(V[p + '_time_min']);
  const c = consumption('ss'); $('#sCons').textContent = c.v; $('#sConsU').textContent = c.u;
  $('#sAvg').textContent = num(V.ss_avg_speed_kmh, 0);

  $('#vehicle').classList.toggle('demo', !live && !!demoT);
  renderDoors();
}

/* ---------------- doors ---------------- */
// Top-down car pointing up: left side on the left. Open doors swing out in amber.
function doorsSvg(d) {
  const amber = '#ffb020', dim = 'rgba(255,255,255,.55)';
  const door = (open, x, y1, y2, side) => {
    const swing = side < 0 ? -1 : 1, len = y2 - y1;
    return open
      ? `<line x1="${x}" y1="${y1}" x2="${x + swing * len * 0.7}" y2="${y1 + len * 0.7}" stroke="${amber}" stroke-width="3.2" stroke-linecap="round"/>`
      : `<line x1="${x}" y1="${y1}" x2="${x}" y2="${y2}" stroke="${dim}" stroke-width="2.4" stroke-linecap="round"/>`;
  };
  return `<svg viewBox="0 0 64 118">
    <rect x="12" y="6" width="40" height="106" rx="15" fill="rgba(255,255,255,.08)" stroke="${dim}" stroke-width="1.6"/>
    <path d="M17 34 Q32 28 47 34 L45 44 Q32 40 19 44 Z" fill="rgba(255,255,255,.25)"/>
    <path d="M19 88 Q32 92 45 88 L47 96 Q32 101 17 96 Z" fill="rgba(255,255,255,.18)"/>
    <path d="M14 8 Q32 2 50 8" fill="none" stroke="${d.door_bonnet ? amber : 'none'}" stroke-width="3.2" stroke-linecap="round"/>
    ${d.door_bonnet ? `<rect x="15" y="9" width="34" height="20" rx="8" fill="${amber}" opacity=".45"/>` : ''}
    ${d.door_boot ? `<rect x="15" y="98" width="34" height="12" rx="6" fill="${amber}" opacity=".7"/>` : ''}
    ${door(d.door_fl, 12, 46, 66, -1)}${door(d.door_rl, 12, 68, 86, -1)}
    ${door(d.door_fr, 52, 46, 66, 1)}${door(d.door_rr, 52, 68, 86, 1)}
  </svg>`;
}
function renderDoors() {
  // car icon at the top right, only while a door, the boot or the bonnet is open
  const keys = ['door_fl', 'door_fr', 'door_rl', 'door_rr', 'door_boot', 'door_bonnet'];
  const d = {}; keys.forEach(k => { d[k] = V[k] === 1; });
  const open = keys.filter(k => d[k]);
  const names = { door_fl: 'Front left', door_fr: 'Front right', door_rl: 'Rear left', door_rr: 'Rear right', door_boot: 'Boot', door_bonnet: 'Bonnet' };
  const el = $('#doorIcon');
  el.classList.toggle('show', open.length > 0);
  el.innerHTML = open.length ? doorsSvg(d) : '';
  el.setAttribute('aria-label', open.map(k => names[k] + ' open').join(', ') || 'All doors closed');
}

/* ---------------- demo simulator (until real CAN data is wired up) ---------------- */
let demoT = null;
const sim = { litres: 12.4, speed: 0, target: 40,
  ss: { km: 11.2, min: 22, fuel: 11.2 / 10.4 }, rf: { km: 412.8, min: 545, fuel: 412.8 / 13.1 },
  lt: { km: 1834.6, min: 2472, fuel: 1834.6 / 14.2 } };
function simStep() {
  if (Math.random() < 0.06) sim.target = [0, 25, 45, 60, 80][Math.floor(Math.random() * 5)];
  sim.speed += Math.sign(sim.target - sim.speed) * Math.min(4, Math.abs(sim.target - sim.speed));
  const km = sim.speed / 3600, inst = sim.speed < 5 ? 0 : 8 + sim.speed / 12, idle = 0.8 / 3600;
  const used = sim.speed < 5 ? idle : km / inst;
  for (const m of [sim.ss, sim.rf, sim.lt]) { m.km += km; m.min += 1 / 60; m.fuel += used; }
  sim.litres = Math.max(0, sim.litres - used);
  const kml = m => m.km / m.fuel;
  window.onVehicle({
    _demo: true, fuel_litres: sim.litres, temp_c: 24,
    range_km: sim.litres * (0.7 * kml(sim.ss) + 0.3 * kml(sim.lt)),
    ss_distance_km: sim.ss.km, ss_time_min: sim.ss.min, ss_consumption_kml: kml(sim.ss), ss_avg_speed_kmh: sim.ss.km / (sim.ss.min / 60),
    rf_distance_km: sim.rf.km, rf_time_min: sim.rf.min, rf_consumption_kml: kml(sim.rf), rf_avg_speed_kmh: sim.rf.km / (sim.rf.min / 60),
    lt_distance_km: sim.lt.km, lt_time_min: sim.lt.min, lt_consumption_kml: kml(sim.lt), lt_avg_speed_kmh: sim.lt.km / (sim.lt.min / 60),
  });
}
function startDemo() { if (live || demoT) return; demoT = setInterval(simStep, 1000); simStep(); }
function stopDemo() { clearInterval(demoT); demoT = null; if (!live) { V = {}; } render(); }

/* ---------------- boot ---------------- */
buildHome();
refreshDefault();
setTimeout(() => { try { refreshFix(); } catch (e) {} }, 3000);
requestAnimationFrame(() => requestAnimationFrame(() => { try { B && B.pageReady && B.pageReady(); } catch (e) {} }));
// app update found before the screens reloaded
try { const pu = B && B.pendingUpdate(); if (pu) window.onUpdate({ state: 'app-available', versionName: pu, message: 'Version ' + pu + ' is available' }); } catch (e) {}
if (B) { try { const d = B.getVehicle(); if (d && d !== 'null') window.onVehicle(JSON.parse(d)); } catch (e) {} }
if (!live && settings.demo) startDemo(); else render();

// Splash: VW logo the first time the launcher starts (i.e. right after the unit boots)
const splash = $('#splash');
const showSplash = B ? B.isFirstStart() : !sessionStorage.getItem('splashed');
if (showSplash) {
  try { sessionStorage.setItem('splashed', '1'); } catch (e) {}
  setTimeout(() => {
    splash.classList.add('out');
    setTimeout(() => {
      splash.remove();
      // force one full repaint so no stale splash tiles remain on slow GPUs
      stage.style.opacity = '0.999';
      requestAnimationFrame(() => requestAnimationFrame(() => { stage.style.opacity = ''; }));
    }, 800);
  }, 2200);
} else splash.remove();

// allow ?screen=vehicle for previews
const qs = new URLSearchParams(location.search);
if (qs.get('screen') === 'apps') setPage(1);
if (qs.get('nosplash') && document.body.contains(splash)) splash.remove();
})();
