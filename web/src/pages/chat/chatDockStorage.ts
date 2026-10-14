/**
 * Desktop: whether the chat panel is open or minimised to its button, stored per device. Minimised
 * is the default, so the panel doesn't cover the screens until the player opens it.
 */
const STORAGE_KEY = 'bannerfall.chatDock';

export function parseChatDockOpen(value: string | null): boolean {
  return value === 'open';
}

export function loadChatDockOpen(): boolean {
  try {
    return parseChatDockOpen(localStorage.getItem(STORAGE_KEY));
  } catch {
    return false; // storage blocked (private mode)
  }
}

export function saveChatDockOpen(open: boolean): void {
  try {
    if (open) {
      localStorage.setItem(STORAGE_KEY, 'open');
    } else {
      localStorage.removeItem(STORAGE_KEY);
    }
  } catch {
    // ignore: the choice still holds until the page is reloaded
  }
}
