// Applies the stored light/dark choice and colour theme before the first paint (src/theme/theme.ts, same keys and
// the same list of palettes). Loaded by index.html as a classic, blocking script; kept out of index.html because
// the Content-Security-Policy (Caddyfile) allows no inline scripts.
try {
  var theme = localStorage.getItem('bannerfall.theme');
  if (theme === 'light' || theme === 'dark') document.documentElement.setAttribute('data-theme', theme);
  var palette = localStorage.getItem('bannerfall.palette');
  if (
    palette === 'blue' ||
    palette === 'black' ||
    palette === 'oled' ||
    palette === 'purple' ||
    palette === 'leather'
  ) {
    document.documentElement.setAttribute('data-palette', palette);
  }
} catch (e) {}
