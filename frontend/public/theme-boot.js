// Runs before the app (a plain file: the CSP allows no inline script). Same rules as src/lib/theme.ts: the choice saved
// under "mathlms-theme" in this browser, light by default, "system" follows the phone or computer.
(function () {
  try {
    var choice = localStorage.getItem('mathlms-theme')
    var dark = choice === 'dark' ||
      (choice === 'system' && window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches)
    if (dark) document.documentElement.classList.add('dark')
  } catch (e) {
    // Storage blocked: stay light.
  }
})()
