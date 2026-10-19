/**
 * The service worker (1.5, 05-frontend §7b): push notifications only. It caches nothing, so the
 * game still has no offline mode. Built on its own into `/sw.js` (`vite.config.ts`), so it never
 * shares a chunk with the page.
 *
 * The worker's own types (ServiceWorkerGlobalScope …) clash with the page's DOM types in one
 * TypeScript program, so the few parts used here are declared below.
 */
import { de } from '../i18n/de';
import { en } from '../i18n/en';
import { parsePushMessage } from './pushMessage';
import { pushNotificationContent } from './pushText';

interface ExtendableEvent extends Event {
  waitUntil(promise: Promise<unknown>): void;
}

interface PushEvent extends ExtendableEvent {
  readonly data: { json(): unknown } | null;
}

interface NotificationEvent extends ExtendableEvent {
  readonly notification: Notification;
}

interface WindowClient {
  readonly url: string;
  readonly focused: boolean;
  readonly visibilityState: DocumentVisibilityState;
  focus(): Promise<WindowClient>;
  navigate(url: string): Promise<WindowClient | null>;
}

interface WorkerScope {
  readonly registration: ServiceWorkerRegistration;
  readonly clients: {
    matchAll(options: { type: 'window'; includeUncontrolled: boolean }): Promise<readonly WindowClient[]>;
    openWindow(url: string): Promise<WindowClient | null>;
    claim(): Promise<void>;
  };
  readonly location: Location;
  skipWaiting(): Promise<void>;
  addEventListener(type: 'install' | 'activate', listener: (event: ExtendableEvent) => void): void;
  addEventListener(type: 'push', listener: (event: PushEvent) => void): void;
  addEventListener(type: 'notificationclick', listener: (event: NotificationEvent) => void): void;
}

const worker = self as unknown as WorkerScope;

worker.addEventListener('install', (event) => event.waitUntil(worker.skipWaiting()));
worker.addEventListener('activate', (event) => event.waitUntil(worker.clients.claim()));

worker.addEventListener('push', (event) => {
  event.waitUntil(show(event.data));
});

worker.addEventListener('notificationclick', (event) => {
  event.notification.close();
  const data: unknown = event.notification.data;
  const url =
    typeof data === 'object' && data !== null && 'url' in data && typeof data.url === 'string' ? data.url : '/';
  event.waitUntil(open(url));
});

async function show(data: PushEvent['data']): Promise<void> {
  let payload: unknown;
  try {
    payload = data?.json();
  } catch {
    return;
  }
  const message = parsePushMessage(payload);
  if (message === undefined) {
    return;
  }
  const language = message.lang === 'de' ? 'de' : 'en';
  const content = pushNotificationContent(message, language === 'de' ? de : en, language, Date.now());
  if (content === undefined) {
    return;
  }
  const options: NotificationOptions & { renotify?: boolean } = {
    body: content.body,
    tag: content.tag,
    // a replaced notification (the next attack on the same village) buzzes again
    renotify: true,
    icon: '/icon-192.png',
    badge: '/icon-192.png',
    lang: language,
    data: { url: content.url },
  };
  await worker.registration.showNotification(content.title, options);
  // A game window on screen shows the toast already. Safari takes a push that shows nothing as
  // abuse, so the notification is shown and closed again instead of being skipped (a test always
  // stays).
  if (message.test !== true && (await gameOnScreen())) {
    for (const shown of await worker.registration.getNotifications({ tag: content.tag })) {
      shown.close();
    }
  }
}

async function gameOnScreen(): Promise<boolean> {
  const windows = await worker.clients.matchAll({ type: 'window', includeUncontrolled: true });
  return windows.some((window) => window.visibilityState === 'visible' && window.focused);
}

/** Focuses a game window and shows the page there, or opens a new one. */
async function open(path: string): Promise<void> {
  const url = new URL(path, worker.location.origin).href;
  const windows = await worker.clients.matchAll({ type: 'window', includeUncontrolled: true });
  const game = windows.find((window) => new URL(window.url).origin === worker.location.origin);
  if (game === undefined) {
    await worker.clients.openWindow(url);
    return;
  }
  const focused = await game.focus();
  if (focused.url !== url) {
    // navigate() only works on a window this worker controls (not after a hard reload)
    const navigated = await focused.navigate(url).catch(() => null);
    if (navigated === null) {
      await worker.clients.openWindow(url);
    }
  }
}
