/**
 * The setup link (`/setup?code=…`, 1.6) may open before the host is logged in: the code waits in
 * sessionStorage through the login or registration (also through the account server's redirect),
 * and the pre-game page sends the host back to /setup. Storage can be off: then only the link itself works.
 */
const STORAGE_KEY = 'bannerfall.setupCode';

/** Called once at start-up with the page's address. */
export function rememberSetupCode(location: Pick<Location, 'pathname' | 'search'>): void {
  if (location.pathname !== '/setup') {
    return;
  }
  const code = new URLSearchParams(location.search).get('code');
  if (code !== null && code.trim() !== '') {
    try {
      sessionStorage.setItem(STORAGE_KEY, code.trim());
    } catch {
      // storage off: the code is still in the address
    }
  }
}

/** The code waiting to be claimed, or null. */
export function pendingSetupCode(): string | null {
  try {
    return sessionStorage.getItem(STORAGE_KEY);
  } catch {
    return null;
  }
}

export function forgetSetupCode(): void {
  try {
    sessionStorage.removeItem(STORAGE_KEY);
  } catch {
    // nothing stored
  }
}
