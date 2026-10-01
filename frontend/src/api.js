export class ApiError extends Error {
  constructor(message, status, problem = {}) {
    super(message);
    this.status = status;
    this.problem = problem;
  }
}

let accessToken = null;
let refreshing = null;
let signedOut = () => {};

export const onSignedOut = (handler) => (signedOut = handler);
export const setSession = (tokens) => (accessToken = tokens?.access_token ?? null);

const withLock = (fn) => (navigator.locks ? navigator.locks.request('lopon-refresh', fn) : fn());

export function refreshSession() {
  refreshing ??= withLock(async () => {
    const response = await fetch('/api/auth/refresh', { method: 'POST' });
    const tokens = response.ok ? await response.json() : null;
    setSession(tokens);
    return tokens;
  }).finally(() => (refreshing = null));
  return refreshing;
}

export async function api(path, options = {}, retry = true) {
  const headers = { ...(options.headers ?? {}) };
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`;
  if (options.json !== undefined) {
    headers['Content-Type'] = 'application/json';
    options = { ...options, body: JSON.stringify(options.json) };
  }
  const response = await fetch(path, { ...options, headers });
  if (response.status === 401 && retry && !path.startsWith('/api/auth/')) {
    if (await refreshSession()) return api(path, options, false);
    signedOut();
    throw new ApiError('Сессия закончилась — войди заново', 401);
  }
  if (!response.ok) {
    let problem = {};
    try {
      problem = await response.json();
    } catch (e) {}
    throw new ApiError(problem.detail || problem.title || `Ошибка ${response.status}`, response.status, problem);
  }
  if (response.status === 204) return null;
  return (response.headers.get('content-type') ?? '').includes('json') ? response.json() : response.blob();
}
