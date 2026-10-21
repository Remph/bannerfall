import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const calls = vi.hoisted(() => ({
  put: [] as string[],
  remove: [] as string[],
  removeFails: false,
  publicKey: 'AQID',
}));

vi.mock('../api/client', () => ({
  api: {
    GET: () => Promise.resolve({ data: { available: true, publicKey: calls.publicKey } }),
    PUT: (_path: string, init: { body: { endpoint: string } }) => {
      calls.put.push(init.body.endpoint);
      return Promise.resolve({});
    },
    POST: (_path: string, init: { body: { endpoint: string } }) => {
      if (calls.removeFails) {
        return Promise.reject(new Error('offline'));
      }
      calls.remove.push(init.body.endpoint);
      return Promise.resolve({});
    },
  },
  expectData: async (call: Promise<{ data?: unknown }>) => (await call).data,
}));

import { base64UrlToBytes, refreshPush, turnOffPush, turnOnPush } from './pushClient';

/** A browser's push manager: one subscription at a time, a new address for every new one. */
class FakePushManager {
  current: FakeSubscription | null = null;
  created = 0;
  reportsKey = true;
  emptyKey = false;

  getSubscription(): Promise<FakeSubscription | null> {
    return Promise.resolve(this.current);
  }

  subscribe(options: { applicationServerKey: Uint8Array<ArrayBuffer> }): Promise<FakeSubscription> {
    this.created++;
    this.current = new FakeSubscription(this, `https://push.example/${this.created}`, options.applicationServerKey);
    return Promise.resolve(this.current);
  }
}

class FakeSubscription {
  private readonly manager: FakePushManager;
  readonly endpoint: string;
  private readonly key: Uint8Array<ArrayBuffer>;

  constructor(manager: FakePushManager, endpoint: string, key: Uint8Array<ArrayBuffer>) {
    this.manager = manager;
    this.endpoint = endpoint;
    this.key = key;
  }

  get options(): { applicationServerKey: ArrayBuffer | null } {
    if (this.manager.emptyKey) {
      return { applicationServerKey: new ArrayBuffer(0) };
    }
    return { applicationServerKey: this.manager.reportsKey ? this.key.slice().buffer : null };
  }

  toJSON() {
    return { endpoint: this.endpoint, keys: { p256dh: 'p', auth: 'a' } };
  }

  unsubscribe(): Promise<boolean> {
    if (this.manager.current === this) {
      this.manager.current = null;
    }
    return Promise.resolve(true);
  }
}

let manager: FakePushManager;

beforeEach(() => {
  calls.put = [];
  calls.remove = [];
  calls.removeFails = false;
  calls.publicKey = 'AQID';
  manager = new FakePushManager();
  const registration = { pushManager: manager };
  const storage = new Map<string, string>();
  vi.stubGlobal('localStorage', {
    getItem: (key: string) => storage.get(key) ?? null,
    setItem: (key: string, value: string) => storage.set(key, value),
    removeItem: (key: string) => storage.delete(key),
  });
  vi.stubGlobal('Notification', { permission: 'granted', requestPermission: () => Promise.resolve('granted') });
  vi.stubGlobal('window', { PushManager: {}, Notification: {}, matchMedia: () => ({ matches: false }) });
  vi.stubGlobal('navigator', {
    userAgent: 'test',
    serviceWorker: {
      register: () => Promise.resolve(registration),
      ready: Promise.resolve(registration),
      getRegistration: () => Promise.resolve(registration),
    },
  });
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('one device per browser on the server (1.5.2)', () => {
  it('signs the same subscription up again at every start', async () => {
    await turnOnPush(calls.publicKey, 'en');
    await refreshPush('en');
    await refreshPush('de');

    expect(manager.created).toBe(1);
    expect(calls.put).toEqual(['https://push.example/1', 'https://push.example/1', 'https://push.example/1']);
    expect(calls.remove).toEqual([]);
  });

  it('keeps a subscription whose browser does not say its key (Firefox on Android)', async () => {
    manager.reportsKey = false;
    await turnOnPush(calls.publicKey, 'en');
    await refreshPush('en');
    await refreshPush('en');

    expect(manager.created).toBe(1);
    expect(calls.remove).toEqual([]);
  });

  it('keeps a subscription whose browser gives an empty key', async () => {
    manager.emptyKey = true;
    await turnOnPush(calls.publicKey, 'en');
    await refreshPush('en');

    expect(manager.created).toBe(1);
    expect(calls.remove).toEqual([]);
  });

  it('keeps such a subscription from before 1.5.2, when nothing was saved with it', async () => {
    manager.reportsKey = false;
    await turnOnPush(calls.publicKey, 'en');
    localStorage.removeItem('bannerfall.push.device');
    await refreshPush('en');

    expect(manager.created).toBe(1);
  });

  it('renews such a subscription when the world key changed', async () => {
    manager.reportsKey = false;
    await turnOnPush(calls.publicKey, 'en');
    calls.publicKey = 'BAUG';
    await refreshPush('en');

    expect(manager.current?.endpoint).toBe('https://push.example/2');
    expect(calls.remove).toEqual(['https://push.example/1']);
  });

  it('drops the old address on the server when the world key changed', async () => {
    await turnOnPush(calls.publicKey, 'en');
    calls.publicKey = 'BAUG';
    await refreshPush('en');

    expect(manager.current?.endpoint).toBe('https://push.example/2');
    expect(calls.remove).toEqual(['https://push.example/1']);
    expect(calls.put).toEqual(['https://push.example/1', 'https://push.example/2']);
  });

  it('drops the old address when the push service handed out a new one', async () => {
    await turnOnPush(calls.publicKey, 'en');
    await manager.subscribe({ applicationServerKey: base64UrlToBytes(calls.publicKey) });
    await refreshPush('en');

    expect(calls.remove).toEqual(['https://push.example/1']);
    expect(calls.put.at(-1)).toBe('https://push.example/2');
  });

  it('asks again at the next sign-up when the server could not be told at turning off', async () => {
    await turnOnPush(calls.publicKey, 'en');
    calls.removeFails = true;
    await expect(turnOffPush()).rejects.toThrow('offline');
    calls.removeFails = false;
    await turnOnPush(calls.publicKey, 'en');

    expect(calls.remove).toEqual(['https://push.example/1']);
    expect(calls.put.at(-1)).toBe('https://push.example/2');
  });

  it('forgets nothing twice after a clean turning off', async () => {
    await turnOnPush(calls.publicKey, 'en');
    await turnOffPush();
    await turnOnPush(calls.publicKey, 'en');

    expect(calls.remove).toEqual(['https://push.example/1']);
    expect(calls.put).toEqual(['https://push.example/1', 'https://push.example/2']);
  });
});
