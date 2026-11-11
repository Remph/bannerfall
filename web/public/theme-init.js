// Applies the stored day/night/black choice and colour theme before the first paint (src/theme/theme.ts, same
// keys, the same list of palettes and the same table of 1.x themes). Loaded by index.html as a classic, blocking
// script; kept out of index.html because the Content-Security-Policy (Caddyfile) allows no inline scripts.
try {
  var root = document.documentElement;
  var theme = localStorage.getItem('bannerfall.theme');
  var palette = localStorage.getItem('bannerfall.palette');
  // a theme stored by 1.x becomes its 2.0 successor, once; the old OLED theme becomes the black mode
  var legacy = { green: 'wiese', blue: 'frost', black: 'wiese', oled: 'wiese', purple: 'heide', leather: 'kornfeld' };
  if (palette !== null && Object.prototype.hasOwnProperty.call(legacy, palette)) {
    if (palette === 'oled' && theme !== 'light') {
      theme = 'black';
      localStorage.setItem('bannerfall.theme', theme);
    }
    palette = legacy[palette];
    if (palette === 'wiese') localStorage.removeItem('bannerfall.palette');
    else localStorage.setItem('bannerfall.palette', palette);
  }
  if (theme === 'light' || theme === 'dark') root.setAttribute('data-theme', theme);
  if (theme === 'black') {
    root.setAttribute('data-theme', 'dark');
    root.setAttribute('data-black', '');
  }
  if (palette === 'kornfeld' || palette === 'frost' || palette === 'heide' || palette === 'gestickt') {
    root.setAttribute('data-palette', palette);
  }
  // the browser's own bar (theme-color) in the colour of the chosen theme's sky from the first paint on, not
  // Wiese's: day and night top band of every theme (the same values as --color-sky-1 in palettes.css, checked by
  // palettes.test.ts); the black mode is black. src/theme/theme.ts keeps it up to date after that.
  var skies = {
    wiese: ['#a8d2e2', '#0e1830'],
    kornfeld: ['#9ccbe0', '#101a33'],
    frost: ['#b4cbe2', '#0a1222'],
    heide: ['#f2c3a0', '#150f26'],
    gestickt: ['#aac6d5', '#131c2e'],
  };
  var night =
    theme === 'dark' ||
    theme === 'black' ||
    (theme !== 'light' && window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches);
  var sky = theme === 'black' ? '#000000' : (skies[palette] || skies.wiese)[night ? 1 : 0];
  var metas = document.querySelectorAll('meta[name="theme-color"]');
  for (var i = 0; i < metas.length; i++) metas[i].setAttribute('content', sky);
} catch (e) {}
