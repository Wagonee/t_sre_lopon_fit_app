import { api } from '../api.js';
import { C, PC, ZC, lineChart, resetCharts } from '../charts.js';
import { state } from '../state.js';
import { $, TEMP, esc, fmt, kpi, mm, toast, when } from '../util.js';

const METHOD = { flat_segment: 'ровный сегмент', whole_ride_power: 'весь заезд, мощность/пульс', whole_ride_speed: 'весь заезд, скорость/пульс', none: 'не считался' };
const EMPTY = '<div class="card muted">Выбери заезд в списке — разберём по косточкам.</div>';

let afterDelete = () => {};

export const initRide = (onDeleted) => (afterDelete = onDeleted);
export const clearRide = () => {
  state.last = null;
  $('#ride').innerHTML = EMPTY;
};

export async function openRide(id) {
  const [workout, points] = await Promise.all([api(`/api/workouts/${id}`), api(`/api/workouts/${id}/track-points`)]);
  state.last = { workout, points };
}

export function renderRide() {
  if (!state.last) return clearRide();
  resetCharts();
  const { workout: w, points } = state.last;
  const a = w.analysis;
  const s = a.summary;
  const z = a.hr_zones;
  const d = a.decoupling;
  const p = a.power;
  let h = `<div class="card"><h2>${esc(w.title)} ${w.indoor ? '<span class="tag in">станок</span>' : ''}<span class="tag">${esc(w.filename)}</span></h2>
    <p class="small muted">${when(w.start_time)}</p>
    ${a.warnings.map((x) => `<div class="small warn-line">⚠ ${esc(x)}</div>`).join('')}
    <div class="grid">
      ${kpi('Дистанция', `${fmt(s.distance_km)} км`)}${kpi('В движении', mm(s.moving_min), `стоп ${Math.round(s.stopped_min)} мин`)}
      ${kpi('Скорость', `${fmt(s.avg_speed_kmh)} км/ч`, `макс ${fmt(s.max_speed_kmh)}`)}${kpi('Пульс', `${s.avg_hr ?? '—'} / ${s.max_hr ?? '—'}`, 'ср / макс')}
      ${kpi('Каденс', s.avg_cad ?? '—', `≥80: ${s.cad_ge80_pct ?? '—'}% · ≥90: ${s.cad_ge90_pct ?? '—'}%`)}${kpi('Набор', `${s.ascent_m} м`, `сглаж.; спуск ${s.descent_m}`)}
      ${kpi('Температура', s.temp ? `${fmt(s.temp.avg, 0)}°C` : '—', s.temp ? `${s.temp.min}…${s.temp.max}, ${TEMP[s.temp_context]}` : '')}${kpi('Нагрузка', s.load, `ккал ${s.kcal ?? '—'}`)}
    </div></div>
    <div class="card"><h3>Название и заметки</h3>
      <label class="f">Название<input class="f" id="w-title" maxlength="200" value="${esc(w.title)}"></label>
      <label class="f">Описание — контекст для пульса: сон, кофеин, самочувствие<textarea class="f" id="w-desc" maxlength="10000">${esc(w.description ?? '')}</textarea></label>
      <div class="actions"><button class="btn" id="w-save">Сохранить</button><button class="btn sec" id="w-file">Скачать FIT</button></div></div>
    <div class="card"><h3>Пульсовые зоны (границы ${z.bounds.join(' / ')})</h3>
      <div class="zbar">${z.zones.map((q, i) => `<div style="width:${q.pct}%;background:${ZC[i]}" title="${esc(q.name)}">${q.pct >= 7 ? `${q.pct}%` : ''}</div>`).join('')}</div>
      <div class="small muted">${z.zones.map((q, i) => `<span style="color:${ZC[i]}">■</span> ${esc(q.name)}: ${q.minutes} мин (${q.pct}%)`).join(' · ')}</div>
      <div class="grid">${kpi(`Полка Z2 ${z.z2_band.lo}–${z.z2_band.hi}`, `${z.z2_band.pct}%`, `${z.z2_band.minutes} мин`)}${kpi('Время ≤ верха Z2', `${z.le_z2_top_pct}%`, 'дисциплина лёгких дней')}
      ${kpi('EF', fmt(a.ef, 3), `м/мин на удар${a.ef_power ? ` · по мощн. ${fmt(a.ef_power, 2)}` : ''}`)}</div></div>
    <div class="card"><h3>Аэробный дрейф — ${METHOD[d.method]}</h3><div class="grid">
      ${kpi('Дрейф', d.pct == null ? '—' : `${d.pct > 0 ? '+' : ''}${d.pct}%`, d.segment ? `${d.segment.minutes} мин, с ${d.segment.start_min}-й по ${d.segment.end_min}-ю` : '')}
      ${d.first ? kpi('1-я половина', `${d.first.value} ${d.unit}`, `пульс ${d.first.hr}`) : ''}${d.second ? kpi('2-я половина', `${d.second.value} ${d.unit}`, `пульс ${d.second.hr}`) : ''}</div>
      <p class="small">${esc(d.verdict)}</p>
      <p class="small muted">Рельеф по половинам: 1-я ${a.halves_altitude.first.ascent}↑/${a.halves_altitude.first.descent}↓ (нетто ${a.halves_altitude.first.net} м), 2-я ${a.halves_altitude.second.ascent}↑/${a.halves_altitude.second.descent}↓ (нетто ${a.halves_altitude.second.net} м)</p></div>`;
  const byTime = w.indoor || !points.some((pt) => pt.distance_km > 0);
  h += `<div class="card"><h3>Профиль (ось: ${byTime ? 'мин' : 'км'})</h3><div class="chart"><canvas id="c_alt"></canvas></div><div class="chart"><canvas id="c_hr"></canvas></div><div class="chart"><canvas id="c_spd"></canvas></div>${p ? '<div class="chart"><canvas id="c_pw"></canvas></div>' : ''}</div>`;
  if (a.efforts.length) {
    h += `<div class="card"><h3>Эффорты (пульс ≥ ${state.settings?.analysis.effort_hr_threshold ?? 'порога'})</h3><div class="scroll"><table><thead><tr><th>#</th><th>Старт</th><th>Длит.</th><th>Ср. пульс</th><th>Пик</th><th>Ср. мощн.</th></tr></thead><tbody>
      ${a.efforts.map((e, i) => `<tr><td>${i + 1}</td><td>+${Math.round(e.start_min)} мин</td><td>${fmt(e.minutes)} мин</td><td>${e.avg_hr}</td><td>${e.peak_hr}</td><td>${e.avg_power ?? '—'}</td></tr>`).join('')}</tbody></table></div></div>`;
  }
  if (p) {
    h += `<div class="card"><h3>Мощность (FTP ${p.ftp_used} Вт${p.ftp_in_fit ? `, в файле ${p.ftp_in_fit}` : ''})</h3><div class="grid">${kpi('Средняя', `${p.avg} Вт`, `без нулей ${p.avg_nonzero}`)}${kpi('NP', `${p.np} Вт`, `макс ${p.max}`)}${kpi('IF', p.if, `TSS ${p.tss}`)}${kpi('Вт/кг по FTP', p.wkg_ftp)}</div>
      <div class="zbar">${p.zones.map((q, i) => `<div style="width:${q.pct}%;background:${PC[i % PC.length]}" title="${esc(q.name)}">${q.pct >= 7 ? `${q.pct}%` : ''}</div>`).join('')}</div>
      <div class="small muted">${p.zones.filter((q) => q.pct > 0).map((q) => `${esc(q.name)}: ${q.minutes} мин`).join(' · ')}</div>`;
    if (p.erg_blocks.length) {
      h += `<h3>Рабочие блоки (≥${Math.round((state.settings?.power.erg_block_min_pct_ftp ?? 0.8) * 100)}% FTP)</h3><div class="scroll"><table><thead><tr><th>#</th><th>Старт</th><th>Длит.</th><th>Вт</th><th>% FTP</th><th>Стабильность (CV)</th><th>Каденс</th><th>Пульс нач→кон</th></tr></thead><tbody>
        ${p.erg_blocks.map((b, i) => `<tr><td>${i + 1}</td><td>+${Math.round(b.start_min)}</td><td>${fmt(b.minutes)} мин</td><td>${b.avg_power}</td><td>${b.pct_ftp}%</td><td class="${b.power_cv_pct < 5 ? 'ok' : ''}">${b.power_cv_pct}%</td><td>${b.avg_cad ?? '—'}</td><td>${b.hr_start ?? '—'}→${b.hr_end ?? '—'}</td></tr>`).join('')}</tbody></table></div>`;
    }
    h += '</div>';
  }
  h += '<div class="card"><button class="btn danger" id="w-delete">Удалить заезд</button></div>';
  $('#ride').innerHTML = h;
  bindActions(w);
  drawProfile(points, byTime, a.settings_used.z2_band, !!p);
}

function bindActions(w) {
  $('#w-save').onclick = async () => {
    const updated = await api(`/api/workouts/${w.id}`, { method: 'PATCH', json: { title: $('#w-title').value, description: $('#w-desc').value } })
      .catch((e) => toast(e.message));
    if (!updated) return;
    state.last.workout = updated;
    toast('Сохранено');
    renderRide();
  };
  $('#w-file').onclick = async () => {
    const blob = await api(`/api/workouts/${w.id}/file`);
    const link = Object.assign(document.createElement('a'), { href: URL.createObjectURL(blob), download: w.filename });
    link.click();
    URL.revokeObjectURL(link.href);
  };
  $('#w-delete').onclick = async () => {
    if (!confirm('Удалить заезд? Исходный FIT и разбор пропадут, восстановить нельзя.')) return;
    await api(`/api/workouts/${w.id}`, { method: 'DELETE' });
    clearRide();
    afterDelete();
  };
}

function drawProfile(points, byTime, band, hasPower) {
  const x = points.map((pt) => (byTime ? pt.t_min : pt.distance_km));
  const series = (key) => points.map((pt, i) => ({ x: x[i], y: pt[key] }));
  const flat = (y) => x.map((v) => ({ x: v, y }));
  lineChart($('#c_alt'), x, [{ data: series('altitude_m'), borderColor: C.alt, backgroundColor: C.altFill, fill: 'origin' }], 'высота, м');
  lineChart($('#c_hr'), x, [
    { data: flat(band[0]), borderWidth: 0, fill: '+1', backgroundColor: C.band },
    { data: flat(band[1]), borderWidth: 0, fill: false },
    { data: series('heart_rate'), borderColor: C.hr },
  ], 'пульс (полка Z2 серым)');
  lineChart($('#c_spd'), x, [{ data: series('speed_kmh'), borderColor: C.spd }], 'скорость, км/ч');
  if (hasPower) lineChart($('#c_pw'), x, [{ data: series('power'), borderColor: C.pw }], 'мощность, Вт');
}
