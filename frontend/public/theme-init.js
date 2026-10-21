// Apply the theme before the first paint; Angular handles subsequent changes.
(() => {
  let preference;
  try { preference = localStorage.getItem('tr8ders:theme'); } catch { /* Use device preference. */ }
  const theme = preference === 'light' || preference === 'dark' ? preference
    : window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  document.documentElement.dataset.theme = theme;
  document.documentElement.style.colorScheme = theme;
})();
