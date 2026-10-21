import { api, expectData } from '../api/client';
import type { Language } from '../i18n/language';

/**
 * The browser's side of push notifications (1.5, 05-frontend §7b): the service worker, the
 * browser's subscription and the server's copy of it. "On for this device" is remembered in
 * localStorage, so a later start can sign the device up again (a new password ends all devices on
 * the server) and a logout can tell the next player on this browser apart.
 *
 * The browser's push address (the endpoint) can change: turned off and on again, a new world key,
 * or the push service handing out a new one. The server keeps one device per address, so the last
 * address it was told (and the world key it was made with) is remembered too, and the server
 * forgets that address when a new one comes (1.5.2; Firefox on Android had made ten).
 */

const STORAGE_KEY = 'bannerfall.push';
const DEVICE_KEY = 'bannerfall.push.device';
const WORKER_URL = '/sw.js';

/** Whether this browser can get push: `ios-install` is an iPhone or iPad outside the home screen. */
export type PushSupport = 'supported' | 'unsupported' | 'ios-install';

export function pushSupport(): PushSupport {
  if ('serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window) {
    return 'supported';
  }
  const ios =
    /iPad|iPhone|iPod/.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
  const standalone = window.matchMedia('(display-mode: standalone)').matches;
  return ios && !standalone ? 'ios-install' : 'unsupported';
}

/** The player said no to notifications for this site (the browser won't ask again). */
export class PushBlockedError extends Error {
  constructor() {
    super('Notifications are blocked');
    this.name = 'PushBlockedError';
  }
}

/** Whether notifications are on for this device. */
export async function devicePushOn(): Promise<boolean> {
  if (pushSupport() !== 'supported' || !wanted() || Notification.permission !== 'granted') {
    return false;
  }
  const registration = await navigator.serviceWorker.getRegistration('/');
  return (await registration?.pushManager.getSubscription()) != null;
}

/** Asks for permission, signs the browser up with the world's key and tells the server. */
export async function turnOnPush(publicKey: string, language: Language): Promise<void> {
  const permission = await Notification.requestPermission();
  if (permission !== 'granted') {
    throw new PushBlockedError();
  }
  const subscription = await subscribe(publicKey);
  await save(subscription, language, publicKey);
  setWanted(true);
}

/** Tells the server to forget this device and ends the browser's subscription. */
export async function turnOffPush(): Promise<void> {
  setWanted(false);
  const subscription = await currentSubscription();
  if (subscription !== null) {
    try {
      await api.POST('/api/push/subscription/remove', { body: { endpoint: subscription.endpoint } });
      // only once the server forgot it: else the next sign-up asks again
      if (savedDevice()?.endpoint === subscription.endpoint) {
        setSavedDevice(null);
      }
    } finally {
      await subscription.unsubscribe();
    }
  }
}

/**
 * At the start and after a new language or password: a device that is on signs up again (the same
 * subscription, or a new one if the world's key changed). Quietly does nothing when it can't.
 */
export async function refreshPush(language: Language): Promise<void> {
  if (!(await devicePushOn())) {
    return;
  }
  try {
    const settings = await expectData(api.GET('/api/push'));
    if (!settings.available || settings.publicKey === null) {
      return;
    }
    await save(await subscribe(settings.publicKey), language, settings.publicKey);
  } catch {
    // offline, or the push service is down: the next start tries again
  }
}

/** On logout: the next player on this browser doesn't get this player's notifications. */
export async function leavePush(): Promise<void> {
  if (pushSupport() !== 'supported' || !wanted()) {
    return;
  }
  try {
    await turnOffPush();
  } catch {
    // the logout goes on; the server drops the device when its push service says it is gone
  }
}

async function currentSubscription(): Promise<PushSubscription | null> {
  if (pushSupport() !== 'supported') {
    return null;
  }
  const registration = await navigator.serviceWorker.getRegistration('/');
  return (await registration?.pushManager.getSubscription()) ?? null;
}

/** The browser's subscription for the world's key: the existing one, or a new one. */
async function subscribe(publicKey: string): Promise<PushSubscription> {
  await navigator.serviceWorker.register(WORKER_URL, { scope: '/' });
  const registration = await navigator.serviceWorker.ready;
  const key = base64UrlToBytes(publicKey);
  const existing = await registration.pushManager.getSubscription();
  if (existing !== null) {
    // Firefox on Android doesn't say which key a subscription was made with (null, or nothing at
    // all): then the key it was saved with decides, and one saved before 1.5.2 is kept (a new one
    // at every start was a new device on the server each time)
    const current = existing.options.applicationServerKey;
    const same =
      current != null && current.byteLength > 0
        ? sameKey(current, key)
        : (savedDevice()?.key ?? publicKey) === publicKey;
    if (same) {
      return existing;
    }
    await existing.unsubscribe();
  }
  return registration.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: key });
}

async function save(subscription: PushSubscription, language: Language, publicKey: string): Promise<void> {
  const json = subscription.toJSON();
  const p256dh = json.keys?.p256dh;
  const auth = json.keys?.auth;
  if (json.endpoint === undefined || p256dh === undefined || auth === undefined) {
    throw new Error('The browser gave no push keys');
  }
  const previous = savedDevice()?.endpoint;
  if (previous !== undefined && previous !== json.endpoint) {
    try {
      await api.POST('/api/push/subscription/remove', { body: { endpoint: previous } });
    } catch {
      // offline or refused: the server drops it when its push service says it is gone
    }
  }
  await api.PUT('/api/push/subscription', { body: { endpoint: json.endpoint, keys: { p256dh, auth }, language } });
  setSavedDevice({ endpoint: json.endpoint, key: publicKey });
}

export function base64UrlToBytes(text: string): Uint8Array<ArrayBuffer> {
  const base64 = text.replace(/-/g, '+').replace(/_/g, '/');
  const binary = atob(base64 + '='.repeat((4 - (base64.length % 4)) % 4));
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes;
}

function sameKey(current: ArrayBuffer, key: Uint8Array): boolean {
  const bytes = new Uint8Array(current);
  return bytes.length === key.length && bytes.every((byte, i) => byte === key[i]);
}

function wanted(): boolean {
  try {
    return localStorage.getItem(STORAGE_KEY) === 'on';
  } catch {
    return false;
  }
}

function setWanted(on: boolean): void {
  try {
    if (on) {
      localStorage.setItem(STORAGE_KEY, 'on');
    } else {
      localStorage.removeItem(STORAGE_KEY);
    }
  } catch {
    // private mode: the device stays signed up on the server until it logs out
  }
}

/** The address the server was last told for this browser, and the world key it was made with. */
interface SavedDevice {
  endpoint: string;
  key: string;
}

function savedDevice(): SavedDevice | null {
  try {
    const parsed: unknown = JSON.parse(localStorage.getItem(DEVICE_KEY) ?? 'null');
    if (
      typeof parsed === 'object' &&
      parsed !== null &&
      'endpoint' in parsed &&
      typeof parsed.endpoint === 'string' &&
      'key' in parsed &&
      typeof parsed.key === 'string'
    ) {
      return { endpoint: parsed.endpoint, key: parsed.key };
    }
  } catch {
    // no storage, or not ours
  }
  return null;
}

function setSavedDevice(device: SavedDevice | null): void {
  try {
    if (device === null) {
      localStorage.removeItem(DEVICE_KEY);
    } else {
      localStorage.setItem(DEVICE_KEY, JSON.stringify(device));
    }
  } catch {
    // private mode: the old device stays on the server until its push service says it is gone
  }
}
