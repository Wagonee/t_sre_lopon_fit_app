import { api } from '../api.js';
import { state } from '../state.js';
import { $, $$, esc, toast } from '../util.js';

const SCHEMA = [
  ['Спортсмен', 'athlete.name', 'Имя', 'text'], ['Спортсмен', 'athlete.weight_kg', 'Вес, кг', 'number', 0.1], ['Спортсмен', 'athlete.height_cm', 'Рост, см', 'number', 1],
  ['Спортсмен', 'athlete.hr_max', 'HRmax, уд/мин', 'number', 1], ['Спортсмен', 'athlete.hr_rest', 'Пульс покоя', 'number', 1], ['Спортсмен', 'athlete.ftp', 'FTP, Вт', 'number', 1],
  ['Спортсмен', 'athlete.ftp_source', 'Источник FTP', 'select', ['settings', 'fit']], ['Спортсмен', 'athlete.target_wkg', 'Цель, Вт/кг', 'number', 0.1],
  ['Зоны пульса', 'hr_zones.mode', 'Режим зон', 'select', ['auto', 'manual']], ['Зоны пульса', 'hr_zones.pct', 'Доли HRmax (верх Z1–Z4)', 'list'],
  ['Зоны пульса', 'hr_zones.manual_bounds', 'Ручные границы Z1–Z4, уд/мин', 'list'],
  ['Зоны пульса', 'z2_band.lo', 'Полка Z2 — низ', 'number', 1], ['Зоны пульса', 'z2_band.hi', 'Полка Z2 — верх', 'number', 1],
  ['Разбор', 'analysis.effort_hr_threshold', 'Порог эффорта, уд/мин', 'number', 1], ['Разбор', 'analysis.effort_min_sec', 'Мин. длительность эффорта, с', 'number', 1],
  ['Разбор', 'analysis.moving_speed_kmh', 'Порог движения, км/ч', 'number', 0.1], ['Разбор', 'analysis.altitude_smoothing', 'Сглаживание высоты, точек', 'number', 1],
  ['Разбор', 'analysis.speed_rolling', 'Сглаживание скорости, точек', 'number', 1], ['Разбор', 'analysis.flat_min_minutes', 'Ровный сегмент: мин. длина, мин', 'number', 1],
  ['Разбор', 'analysis.flat_net_climb_m_per_min', 'Ровный сегмент: допуск набора, м/мин', 'number', 0.5], ['Разбор', 'analysis.flat_min_speed_kmh', 'Ровный сегмент: мин. скорость', 'number', 1],
  ['Разбор', 'analysis.skip_first_minutes', 'Пропустить первые минуты', 'number', 1], ['Разбор', 'analysis.decoupling_good_pct', 'Дрейф «хорошо» до, %', 'number', 0.5],
  ['Разбор', 'analysis.heat_allowance_pp', 'Поправка на жару, п.п.', 'number', 0.5], ['Разбор', 'analysis.hr_artifact_speed_kmh', 'Артефакт HR: скорость выше, км/ч', 'number', 1],
  ['Мощность', 'power.zones_pct', 'Зоны от FTP (верх Z1–Z6)', 'list'], ['Мощность', 'power.erg_block_min_sec', 'ERG-блок: мин. длительность, с', 'number', 1],
  ['Мощность', 'power.erg_block_min_pct_ftp', 'ERG-блок: от FTP, доля', 'number', 0.05],
  ['Температура', 'temperature.cold_below', 'Холод ниже, °C', 'number', 1], ['Температура', 'temperature.hot_above', 'Жара выше, °C', 'number', 1],
  ['Время', 'time.fit_is_local', 'Время в FIT уже локальное', 'checkbox'], ['Время', 'time.offset_hours', 'Смещение, ч (если не локальное)', 'number', 0.5],
];

const get = (o, path) => path.split('.').reduce((a, k) => a?.[k], o);
const set = (o, path, v) => {
  const keys = path.split('.');
  let a = o;
  keys.slice(0, -1).forEach((k) => (a = a[k] ??= {}));
  a[keys.at(-1)] = v;
};

let defaults = null;

export async function loadSettings() {
  const r = await api('/api/settings');
  state.settings = r.settings;
  defaults = r.defaults;
  return r;
}

export function initSettings(onSessionReplaced, onAccountDeleted) {
  $('#save').onclick = async () => {
    try {
      const r = await api('/api/settings', { method: 'PUT', json: collect() });
      state.settings = r.settings;
      toast('Сохранено. Старые заезды пересчитаются по кнопке «Пересчитать».');
    } catch (e) {
      toast(e.message);
    }
  };
  $('#reanalyze').onclick = async () => {
    toast('Пересчитываю…');
    const r = await api('/api/workouts/reanalyze', { method: 'POST' });
    toast(`Пересчитано ${r.reanalyzed} заездов${r.errors.length ? `, ${r.errors.length} с ошибкой` : ''}`);
  };
  $('#reset').onclick = () => {
    state.settings = structuredClone(defaults);
    renderSettings(defaults);
    toast('Подставлены значения по умолчанию — ещё не сохранено, нажми «Сохранить»');
  };
  $('#change-password').onclick = async () => {
    const form = new FormData($('#password'));
    try {
      onSessionReplaced(await api('/api/auth/password', { method: 'POST', json: Object.fromEntries(form) }));
      $('#password').reset();
      toast('Пароль изменён, остальные сессии закрыты');
    } catch (e) {
      toast(e.message);
    }
  };
  $('#delete-account').onclick = async () => {
    const password = prompt('Удаление аккаунта сотрёт все заезды. Введи пароль для подтверждения:');
    if (!password) return;
    try {
      await api('/api/auth/me', { method: 'DELETE', json: { password } });
      onAccountDeleted();
    } catch (e) {
      toast(e.message);
    }
  };
}

export function renderSettings(s = state.settings) {
  const groups = {};
  SCHEMA.forEach((f) => (groups[f[0]] ??= []).push(f));
  $('#sform').innerHTML = Object.entries(groups).map(([group, fields]) => `<div><h3>${group}</h3>${fields.map(([, path, label, type, extra]) => {
    const v = get(s, path);
    if (type === 'select') return `<label class="f">${label}<select class="f" data-p="${path}">${extra.map((o) => `<option ${o === v ? 'selected' : ''}>${o}</option>`).join('')}</select></label>`;
    if (type === 'checkbox') return `<label class="f"><input type="checkbox" data-p="${path}" ${v ? 'checked' : ''}> ${label}</label>`;
    if (type === 'list') return `<label class="f">${label}<input class="f" data-p="${path}" data-list="1" value="${esc(v.join(', '))}"></label>`;
    return `<label class="f">${label}<input class="f" type="${type}" ${extra ? `step="${extra}"` : ''} data-p="${path}" value="${esc(v ?? '')}"></label>`;
  }).join('')}</div>`).join('');
  $$('#sform [data-p]').forEach((el) => (el.oninput = preview));
  preview();
}

function collect() {
  const s = structuredClone(state.settings);
  $$('#sform [data-p]').forEach((el) => {
    let v;
    if (el.type === 'checkbox') v = el.checked;
    else if (el.dataset.list) v = el.value.split(',').map((x) => +x.trim()).filter((x) => !Number.isNaN(x));
    else if (el.type === 'number') v = +el.value;
    else v = el.value;
    set(s, el.dataset.p, v);
  });
  return s;
}

function preview() {
  const s = collect();
  const b = s.hr_zones.mode === 'manual' ? s.hr_zones.manual_bounds : s.hr_zones.pct.map((p) => Math.round(s.athlete.hr_max * p));
  $('#zprev').textContent = `Зоны сейчас: Z1 <${b[0]} · Z2 ${b[0]}–${b[1]} · Z3 ${b[1]}–${b[2]} · Z4 ${b[2]}–${b[3]} · Z5 ${b[3]}+   |   Вт/кг: ${(s.athlete.ftp / s.athlete.weight_kg).toFixed(2)} → цель ${Math.round(s.athlete.target_wkg * s.athlete.weight_kg)} Вт`;
}
