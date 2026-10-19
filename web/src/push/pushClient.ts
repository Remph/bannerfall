import { api, expectData } from '../api/client';
import type { Language } from '../i18n/language';

/**
 * The browser's side of push notifications (1.5, 05-frontend §7b): the service worker, the
 * browser's subscription and the server's copy of it. "On for this device" is remembered in
 * localStorage, so a later start can sign the device up again (a new password ends all devices on
 * the server) and a logout can tell the next player on this browser apart.
 */

const STORAGE_KEY = 'bannerfall.push';
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
  await save(subscription, language);
  setWanted(true);
}

/** Tells the server to forget this device and ends the browser's subscription. */
export async function turnOffPush(): Promise<void> {
  setWanted(false);
  const subscription = await currentSubscription();
  if (subscription !== null) {
    try {
      await api.POST('/api/push/subscription/remove', { body: { endpoint: subscription.endpoint } });
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
    await save(await subscribe(settings.publicKey), language);
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
    if (sameKey(existing.options.applicationServerKey, key)) {
      return existing;
    }
    await existing.unsubscribe();
  }
  return registration.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: key });
}

async function save(subscription: PushSubscription, language: Language): Promise<void> {
  const json = subscription.toJSON();
  const p256dh = json.keys?.p256dh;
  const auth = json.keys?.auth;
  if (json.endpoint === undefined || p256dh === undefined || auth === undefined) {
    throw new Error('The browser gave no push keys');
  }
  await api.PUT('/api/push/subscription', { body: { endpoint: json.endpoint, keys: { p256dh, auth }, language } });
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

function sameKey(current: ArrayBuffer | null, key: Uint8Array): boolean {
  if (current === null) {
    return false;
  }
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
