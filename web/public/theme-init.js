// Applies the stored light/dark choice before the first paint (src/theme/theme.ts, same key). Loaded by
// index.html as a classic, blocking script; kept out of index.html because the Content-Security-Policy
// (Caddyfile) allows no inline scripts.
try {
  var theme = localStorage.getItem('bannerfall.theme');
  if (theme === 'light' || theme === 'dark') document.documentElement.setAttribute('data-theme', theme);
} catch (e) {}
