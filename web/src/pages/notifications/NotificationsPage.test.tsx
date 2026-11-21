// @vitest-environment jsdom
import { cleanup, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { pushQueryKey, type PushSettings } from '../../api/queries/push';
import { renderPage } from '../../testing/renderPage';
import { NotificationsPage } from './NotificationsPage';

// jsdom has no service worker: the test says which browser it is
const device = vi.hoisted(() => ({ support: 'unsupported' as 'supported' | 'unsupported' | 'ios-install', on: false }));
vi.mock('../../push/pushClient', async (original) => ({
  ...(await original<typeof import('../../push/pushClient')>()),
  pushSupport: () => device.support,
  devicePushOn: () => Promise.resolve(device.on),
}));

const settings: PushSettings = {
  available: true,
  publicKey: 'BKey',
  devices: 2,
  kinds: [
    { kind: 'ATTACK', enabled: true },
    { kind: 'MESSAGE', enabled: false },
    { kind: 'STORAGE_FULL', enabled: true },
  ],
};

function open(shown: PushSettings = settings, language: 'en' | 'de' = 'en') {
  return renderPage({
    path: '/notifications',
    url: '/notifications',
    element: <NotificationsPage />,
    data: [[pushQueryKey, shown]],
    language,
  });
}

/** The render test of the notifications page (2.1 H1): the device part per browser and the kinds' switches. */
describe('NotificationsPage', () => {
  afterEach(() => {
    cleanup();
    device.support = 'unsupported';
    device.on = false;
  });

  it('lists every kind with its hint and switch, as the server has them', () => {
    open();
    expect(screen.getByRole('heading', { name: 'What to notify' })).toBeTruthy();
    const checked = (name: RegExp) => (screen.getByRole('switch', { name }) as HTMLInputElement).checked;
    expect(checked(/Incoming attacks/)).toBe(true);
    expect(checked(/Private messages/)).toBe(false);
    expect(screen.getByText(/reaches the warehouse limit/)).toBeTruthy();
    expect(screen.getAllByRole('switch')).toHaveLength(3);
  });

  it('says when this browser cannot show notifications', () => {
    open();
    expect(screen.getByText('This browser can’t show notifications from the game.')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Turn on' })).toBeNull();
  });

  it('tells an iPhone to add the game to the home screen', () => {
    device.support = 'ios-install';
    open();
    expect(screen.getByText(/Add to Home Screen/)).toBeTruthy();
  });

  it('offers to turn notifications on for a device that has them off', async () => {
    device.support = 'supported';
    vi.stubGlobal('Notification', { permission: 'default' });
    open();
    expect(await screen.findByRole('button', { name: 'Turn on' })).toBeTruthy();
    expect(screen.getByText('Notifications are off for this device.')).toBeTruthy();
    expect(screen.getByText('Notifications reach 2 of your devices.')).toBeTruthy();
    vi.unstubAllGlobals();
  });

  it('offers to turn off and to send a test on a device that has them on', async () => {
    device.support = 'supported';
    device.on = true;
    vi.stubGlobal('Notification', { permission: 'granted' });
    open();
    expect(await screen.findByRole('button', { name: 'Turn off' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Send a test' })).toBeTruthy();
    vi.unstubAllGlobals();
  });

  it('shows no kinds in a world without notifications', () => {
    open({ available: false, publicKey: null, devices: 0, kinds: [] });
    expect(screen.getByText('This world doesn’t send notifications.')).toBeTruthy();
    expect(screen.queryByRole('heading', { name: 'What to notify' })).toBeNull();
  });

  it('speaks German', () => {
    open(settings, 'de');
    expect(screen.getByRole('heading', { name: 'Benachrichtigen bei' })).toBeTruthy();
  });
});
