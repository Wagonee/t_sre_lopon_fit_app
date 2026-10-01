export const $ = (s) => document.querySelector(s);
export const $$ = (s) => [...document.querySelectorAll(s)];

const ENTITIES = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };
export const esc = (v) => String(v ?? '').replace(/[&<>"']/g, (c) => ENTITIES[c]);

export const fmt = (x, d = 1) => (x == null ? '—' : (+x).toFixed(d));
export const mm = (m) => {
  if (m == null) return '—';
  const total = Math.round(m);
  return `${Math.floor(total / 60)}:${String(total % 60).padStart(2, '0')}`;
};
export const dl = (d) => {
  const [, m, day] = d.split('-');
  return `${day}.${m}`;
};
export const when = (s) => s.slice(0, 16).replace('T', ' ');
export const kpi = (label, value, sub = '') =>
  `<div class="kpi"><div class="l">${label}</div><div class="v">${value}</div><div class="s">${sub}</div></div>`;
export const dpct = (p, good = 5) =>
  p == null ? '—' : `<span class="${p <= good ? 'ok' : 'bad'}">${p > 0 ? '+' : ''}${fmt(p)}%</span>`;
export const TEMP = { cold: 'холод', normal: 'норма', hot: 'жара', unknown: '—' };

let toastTimer;
export function toast(message) {
  const t = $('#toast');
  t.textContent = message;
  t.style.display = 'block';
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => (t.style.display = 'none'), 2600);
}
