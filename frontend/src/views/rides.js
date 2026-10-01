import { api, ApiError } from '../api.js';
import { state } from '../state.js';
import { $, $$, dl, dpct, esc, fmt, mm, when } from '../util.js';

let open = () => {};
let removed = () => {};

export function initRides(onOpen, onRemoved) {
  open = onOpen;
  removed = onRemoved;
  const drop = $('#drop');
  const file = $('#file');
  drop.onclick = () => file.click();
  drop.ondragover = (e) => {
    e.preventDefault();
    drop.classList.add('over');
  };
  drop.ondragleave = () => drop.classList.remove('over');
  drop.ondrop = (e) => {
    e.preventDefault();
    drop.classList.remove('over');
    upload([...e.dataTransfer.files]);
  };
  file.onchange = () => upload([...file.files]).then(() => (file.value = ''));
}

async function upload(files) {
  if (!files.length) return;
  const out = $('#upres');
  out.innerHTML = '<div class="muted">Беру след…</div>';
  const lines = [];
  for (const f of files) {
    const body = new FormData();
    body.append('file', f);
    try {
      const w = await api('/api/workouts', { method: 'POST', body });
      const s = w.analysis.summary;
      const warn = w.analysis.warnings.length ? ` <span class="tag warn">⚠ ${w.analysis.warnings.length}</span>` : '';
      lines.push(`<div>✓ ${esc(f.name)} → ${dl(w.date)}: ${s.distance_km} км, ${mm(s.moving_min)}, ср. пульс ${s.avg_hr ?? '—'}${warn}</div>`);
    } catch (e) {
      lines.push(e instanceof ApiError && e.status === 409
        ? `<div class="muted">${esc(f.name)}: уже есть (#${esc(e.problem.existing_id)})</div>`
        : `<div class="bad">${esc(f.name)}: ${esc(e.message)}</div>`);
    }
    out.innerHTML = lines.join('');
  }
  loadRides();
}

export async function loadRides() {
  const page = await api('/api/workouts?size=500');
  const good = state.settings?.analysis.decoupling_good_pct ?? 5;
  $('#rides tbody').innerHTML = page.content.map((x) => {
    const m = x.metrics;
    const drift = m.decoupling_reliable === false && m.decoupling_pct != null
      ? `<span class="muted" title="интервальная — не показателен">(${fmt(m.decoupling_pct)}%)</span>`
      : dpct(m.decoupling_pct, good);
    return `<tr class="row" data-id="${x.id}">
      <td>${when(x.start_time)}${x.indoor ? '<span class="tag in">станок</span>' : ''}${m.warning_count ? '<span class="tag warn">⚠</span>' : ''}</td>
      <td class="small">${esc(x.title)}</td><td>${fmt(m.distance_km)}</td><td>${mm(m.moving_min)}</td>
      <td>${m.avg_hr ?? '—'}/${m.max_hr ?? '—'}</td><td>${fmt(m.ef, 3)}</td><td>${drift}</td>
      <td>${fmt(m.z2_band_pct, 0)}%</td><td>${fmt(m.le_z2_top_pct, 0)}%</td><td>${m.load}</td>
      <td><button class="btn danger small" data-del="${x.id}" aria-label="Удалить">✕</button></td></tr>`;
  }).join('') || '<tr><td colspan="11" class="muted">Следов пока нет — загрузи первый FIT.</td></tr>';
  $$('#rides tr.row').forEach((tr) => (tr.onclick = (e) => !e.target.dataset.del && open(+tr.dataset.id)));
  $$('[data-del]').forEach((b) => (b.onclick = async (e) => {
    e.stopPropagation();
    if (!confirm('Удалить заезд? Исходный FIT и разбор пропадут, восстановить нельзя.')) return;
    await api(`/api/workouts/${b.dataset.del}`, { method: 'DELETE' });
    removed(+b.dataset.del);
    loadRides();
  }));
}
