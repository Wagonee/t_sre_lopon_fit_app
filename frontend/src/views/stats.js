import { api } from '../api.js';
import { C, ZC, addChart, barChart, resetCharts } from '../charts.js';
import { state } from '../state.js';
import { $, TEMP, dl, kpi, mm } from '../util.js';

const DAYS = ['Пн', 'Вт', 'Ср', 'Чт', 'Пт', 'Сб', 'Вс'];

export async function loadWeeks() {
  state.weeks = (await api('/api/stats/weeks')).weeks;
  const select = $('#weeksel');
  select.innerHTML = state.weeks.map((w, i) => `<option value="${i}">${dl(w.week_start)}–${dl(w.week_end)} · ${w.km} км · ${w.rides} заезд(а)</option>`).join('') || '<option>нет данных</option>';
  select.onchange = renderWeek;
  renderWeek();
}

export function renderWeek() {
  const w = state.weeks[+$('#weeksel').value || 0];
  if (!w) {
    $('#week').innerHTML = '<div class="card muted">Следов пока нет — загрузи заезды, неделя соберётся сама.</div>';
    return;
  }
  resetCharts();
  $('#week').innerHTML = `<div class="card"><h2>Неделя ${dl(w.week_start)}–${dl(w.week_end)}</h2><div class="grid">
      ${kpi('Объём', `${w.km} км`, `${w.moving_h} ч в движении`)}${kpi('Заездов', w.rides, `набор ${w.ascent_m} м`)}${kpi('Нагрузка', w.load, 'мин × коэф. зоны')}
      ${kpi(`Полка Z2 ${w.z2_band.join('–')}`, `${w.z2_band_pct}%`, 'цель ~80% объёма')}${kpi('≤ верха Z2', `${w.le_z2_top_pct}%`)}
      ${kpi('Длиннейший', `${w.longest.km} км`, `${dl(w.longest.date)}, ${mm(w.longest.moving_min)}, пульс ${w.longest.avg_hr ?? '—'}`)}
    </div><p class="small muted">Температурный контекст заездов: ${w.temps.map((t) => TEMP[t] ?? '?').join(', ')}</p></div>
    <div class="card"><h3>Часы в движении по дням</h3><div class="chart tall"><canvas id="c_week"></canvas></div>
    <div class="scroll"><table><thead><tr><th>День</th><th>Заездов</th><th>км</th><th>В движении</th><th>Полка Z2</th></tr></thead><tbody>
      ${w.days.map((d, i) => `<tr><td>${DAYS[i]} ${dl(d.date)}${d.indoor ? '<span class="tag in">станок</span>' : ''}</td><td>${d.rides || '—'}</td><td>${d.rides ? d.km : '—'}</td><td>${d.rides ? mm(d.moving_min) : '—'}</td><td>${d.rides ? `${d.z2_band_pct}%` : '—'}</td></tr>`).join('')}
    </tbody></table></div></div>`;
  barChart($('#c_week'), w.days.map((d, i) => `${DAYS[i]} ${dl(d.date)}`), w.days.map((d) => +(d.moving_min / 60).toFixed(2)),
    w.days.map((d) => (d.indoor ? C.pw : C.alt)), 'часы', (c) => `${c.raw} ч · Z2 ${w.days[c.dataIndex].z2_band_pct}%`);
}

export async function loadTrends() {
  const [trends, weeks] = await Promise.all([api('/api/stats/trends'), api('/api/stats/weeks')]);
  state.trends = { trends, weeks: weeks.weeks.slice().reverse() };
  renderTrends();
}

export function renderTrends() {
  if (!state.trends) return;
  resetCharts();
  const { trends, weeks } = state.trends;
  const rides = trends.rides;
  const k = trends.wkg;
  const good = state.settings?.analysis.decoupling_good_pct ?? 5;
  $('#trends').innerHTML = `<div class="card"><h2>Дорога к ${k.target_wkg} Вт/кг</h2><div class="grid">${kpi('FTP', `${k.ftp} Вт`)}${kpi('Вес', `${k.weight_kg} кг`)}${kpi('Сейчас', `${k.wkg} Вт/кг`)}${kpi('Цель', `${k.target_ftp} Вт`, `до цели ${k.gap_w} Вт`)}</div>
      <p class="small muted">FTP берётся из настроек (или из поля threshold_power файла, если так выбрано). Реальный замер — рамп-тест на станке.</p></div>
    <div class="card"><h3>EF по заездам (м/мин на удар; фиолетовые точки — станок)</h3><div class="chart"><canvas id="t_ef"></canvas></div></div>
    <div class="card"><h3>Дрейф по заездам, % (только где считался; зелёный ≤${good}%)</h3><div class="chart"><canvas id="t_dec"></canvas></div></div>
    <div class="card"><h3>Нагрузка по неделям</h3><div class="chart"><canvas id="t_load"></canvas></div></div>`;
  const labels = rides.map((r) => dl(r.date));
  const line = (id, data, color, points, yLabel) => addChart($(`#${id}`), {
    type: 'line',
    data: { labels, datasets: [{ data, borderColor: color, pointBackgroundColor: points, pointRadius: 4, spanGaps: true, borderWidth: 1.5 }] },
    options: { responsive: true, maintainAspectRatio: false, animation: false, plugins: { legend: { display: false } }, scales: { y: { title: { display: true, text: yLabel } }, x: { grid: { display: false } } } },
  });
  line('t_ef', rides.map((r) => r.ef), C.spd, rides.map((r) => (r.indoor ? C.pw : C.spd)), 'EF');
  line('t_dec', rides.map((r) => r.decoupling_pct), C.hr, rides.map((r) => (r.decoupling_pct == null ? C.na : r.decoupling_pct <= good ? C.spd : ZC[4])), '%');
  barChart($('#t_load'), weeks.map((w) => dl(w.week_start)), weeks.map((w) => w.load), C.alt, '',
    (c) => `нагрузка ${c.raw} · ${weeks[c.dataIndex].km} км · Z2 ${weeks[c.dataIndex].z2_band_pct}%`);
}

