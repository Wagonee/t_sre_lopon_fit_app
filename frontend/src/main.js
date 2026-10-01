import '@fontsource/montserrat/400.css';
import '@fontsource/montserrat/600.css';
import '@fontsource/montserrat/700.css';
import './styles.css';
import { api, onSignedOut, refreshSession, setSession } from './api.js';
import { resetCharts } from './charts.js';
import { state } from './state.js';
import { $, $$, esc } from './util.js';
import { clearRide, initRide, openRide, renderRide } from './views/ride.js';
import { initRides, loadRides } from './views/rides.js';
import { initSettings, loadSettings, renderSettings } from './views/settings.js';
import { loadTrends, loadWeeks, renderTrends, renderWeek } from './views/stats.js';

const LOADERS = {
  rides: loadRides,
  ride: async () => renderRide(),
  week: loadWeeks,
  trends: loadTrends,
  settings: async () => renderSettings(),
};

function show(view) {
  state.view = view;
  $$('#nav button').forEach((b) => b.classList.toggle('on', b.dataset.v === view));
  $$('.view').forEach((s) => s.classList.toggle('on', s.id === `v-${view}`));
  if (view !== 'auth') LOADERS[view]().catch(report);
}

function rerender() {
  resetCharts();
  if (state.view === 'ride') renderRide();
  if (state.view === 'week' && state.weeks.length) renderWeek();
  if (state.view === 'trends') renderTrends();
}

function report(error) {
  const main = $(`#v-${state.view}`);
  main?.insertAdjacentHTML('afterbegin', `<div class="card bad small">${esc(error.message)}</div>`);
}

async function signedIn(tokens) {
  setSession(tokens);
  rememberSession(true);
  state.user = tokens.user;
  $('#user').textContent = tokens.user.display_name;
  ['#nav', '#user', '#logout'].forEach((s) => ($(s).hidden = false));
  await loadSettings();
  show('rides');
}

function signedOut() {
  setSession(null);
  rememberSession(false);
  state.user = null;
  state.trends = null;
  state.weeks = [];
  clearRide();
  ['#nav', '#user', '#logout'].forEach((s) => ($(s).hidden = true));
  show('auth');
}

const SESSION_HINT = 'lopon.session';

function rememberSession(active) {
  try {
    if (active) localStorage.setItem(SESSION_HINT, '1');
    else localStorage.removeItem(SESSION_HINT);
  } catch (e) {}
}

function hasSessionHint() {
  try {
    return localStorage.getItem(SESSION_HINT) === '1';
  } catch (e) {
    return true;
  }
}

let registering = false;

function toggleAuthMode() {
  registering = !registering;
  $('#auth-title').textContent = registering ? 'Регистрация' : 'Вход';
  $('#auth-submit').textContent = registering ? 'Создать аккаунт' : 'Войти';
  $('#auth-switch').textContent = registering ? 'Уже есть аккаунт — войти' : 'Нет аккаунта — зарегистрироваться';
  $('#name-field').hidden = !registering;
  $('#auth [name=password]').autocomplete = registering ? 'new-password' : 'current-password';
  $('#auth-error').textContent = '';
}

$('#auth').onsubmit = async (e) => {
  e.preventDefault();
  const body = Object.fromEntries(new FormData(e.target));
  if (!registering) delete body.display_name;
  try {
    await signedIn(await api(registering ? '/api/auth/register' : '/api/auth/login', { method: 'POST', json: body }));
    e.target.reset();
  } catch (error) {
    $('#auth-error').textContent = error.message;
  }
};
$('#auth-switch').onclick = toggleAuthMode;
$('#logout').onclick = async () => {
  await api('/api/auth/logout', { method: 'POST' }).catch(() => {});
  signedOut();
};
$('#theme').onclick = () => {
  const theme = document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark';
  try {
    localStorage.theme = theme;
  } catch (e) {}
  document.documentElement.dataset.theme = theme;
  rerender();
};
matchMedia('(prefers-color-scheme: dark)').onchange = (e) => {
  let saved = null;
  try {
    saved = localStorage.theme;
  } catch (x) {}
  if (!saved) {
    document.documentElement.dataset.theme = e.matches ? 'dark' : 'light';
    rerender();
  }
};
$$('#nav button').forEach((b) => (b.onclick = () => show(b.dataset.v)));

onSignedOut(signedOut);
initRides(async (id) => {
  await openRide(id);
  show('ride');
}, (id) => state.last?.workout.id === id && clearRide());
initRide(() => show('rides'));
initSettings((tokens) => setSession(tokens), signedOut);
clearRide();

(hasSessionHint() ? refreshSession() : Promise.resolve(null))
  .then((tokens) => (tokens ? signedIn(tokens) : signedOut()))
  .catch(signedOut);
