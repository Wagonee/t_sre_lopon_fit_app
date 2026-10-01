(() => {
  let saved = null;
  try { saved = localStorage.theme; } catch (e) {}
  document.documentElement.dataset.theme = saved || (matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light');
})();
