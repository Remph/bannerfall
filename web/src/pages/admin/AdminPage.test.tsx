// @vitest-environment jsdom
import { cleanup, fireEvent, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { api } from '../../api/client';
import type { AdminStatus, FailedEvent } from '../../api/queries/admin';
import { ToastProvider } from '../../components/ToastProvider';
import { renderPage } from '../../testing/renderPage';
import { AdminPage } from './AdminPage';

const status: AdminStatus = {
  version: '2.0.0',
  serverTime: '2026-11-10T12:00:00Z',
  uptimeSeconds: 3 * 3600 + 5 * 60,
  databaseOk: true,
  humanPlayers: 6,
  villages: 120,
  barbarianVillages: 80,
  npcLords: 4,
  events: { pending: 12, nextDueAt: '2026-11-10T12:05:00Z', lagSeconds: 0, failed: 1 },
  cheatsEnabled: false,
  abuse: { bannedPlayers: 0, suspiciousAccounts: 0, autoLocks: 0, invariants: null },
  push: { enabled: true, devices: 2, players: 2 },
  debugLogging: false,
};

const failed: FailedEvent = {
  id: 31,
  kind: 'BUILD_COMPLETE',
  refId: 7,
  dueAt: '2026-11-10T10:00:00Z',
  attempts: 3,
  lastError: 'Deadlock detected',
  processedAt: '2026-11-10T10:05:00Z',
};

/** The admin endpoints the page asks for; anything else answers like an empty list. */
function answer(shown: AdminStatus, events: FailedEvent[]) {
  return vi.spyOn(api, 'GET').mockImplementation(((path: string) => {
    const data = path === '/api/admin/status' ? shown : path === '/api/admin/events/failed' ? events : [];
    return Promise.resolve({ data, response: new Response(null, { status: 200 }) });
  }) as never);
}

function open(url = '/admin', language: 'en' | 'de' = 'en') {
  return renderPage({
    path: '/admin',
    url,
    element: (
      <ToastProvider>
        <AdminPage />
      </ToastProvider>
    ),
    data: [],
    language,
  });
}

/** The host's page (08-host-console §7): one tab per area, kept in the URL. */
describe('AdminPage', () => {
  beforeEach(() => {
    answer(status, [failed]);
  });

  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  it('lists the tabs, Cheats only while the server has them on', async () => {
    open();

    const tabs = screen.getByRole('tablist', { name: 'Admin sections' });
    expect(
      within(tabs)
        .getAllByRole('tab')
        .map((tab) => tab.textContent),
    ).toEqual(['Status', 'Players', 'World', 'Events', 'NPC lords', 'Suspicious', 'Audit log']);
    expect(within(tabs).getByRole('tab', { name: 'Status' }).getAttribute('aria-selected')).toBe('true');
    // the tab bar is there at once, the Cheats tab follows the status
    cleanup();
    vi.restoreAllMocks();
    answer({ ...status, cheatsEnabled: true }, []);
    open();
    expect(await screen.findByRole('tab', { name: 'Cheats' })).toBeTruthy();
  });

  it('opens on the status of the server', async () => {
    open();

    expect(screen.getByRole('heading', { level: 1, name: 'Admin' })).toBeTruthy();
    expect(await screen.findByText(/Version 2\.0\.0 · running for 3 h 5 min/)).toBeTruthy();
    expect(screen.getByText('Cheats off')).toBeTruthy();
    expect(screen.getByText('12 pending', { exact: false })).toBeTruthy();
    expect(screen.getByText('1 failed')).toBeTruthy();
    // the broadcast cards sit under it
    expect(screen.getByRole('heading', { name: 'Message to everybody' })).toBeTruthy();
  });

  it('switches the tab, keeps it in the URL and links the panel to its tab', async () => {
    open();

    fireEvent.click(screen.getByRole('tab', { name: 'Events' }));

    expect(screen.getByRole('tab', { name: 'Events' }).getAttribute('aria-selected')).toBe('true');
    expect(screen.getByRole('tab', { name: 'Status' }).getAttribute('aria-selected')).toBe('false');
    const panel = screen.getByRole('tabpanel');
    expect(panel.getAttribute('aria-labelledby')).toBe('admin-tab-events');
    expect(await within(panel).findByText('BUILD_COMPLETE')).toBeTruthy();
    expect(within(panel).getByText('Deadlock detected')).toBeTruthy();
    expect(within(panel).getByText(/3 attempts/)).toBeTruthy();
  });

  it('opens the tab named in the URL, and Status for an unknown or hidden one', () => {
    open('/admin?tab=events');
    expect(screen.getByRole('tab', { name: 'Events' }).getAttribute('aria-selected')).toBe('true');
    cleanup();

    open('/admin?tab=cheats');
    expect(screen.getByRole('tab', { name: 'Status' }).getAttribute('aria-selected')).toBe('true');
    cleanup();

    open('/admin?tab=nonsense');
    expect(screen.getByRole('tab', { name: 'Status' }).getAttribute('aria-selected')).toBe('true');
  });

  it('retries a failed event', async () => {
    const post = vi
      .spyOn(api, 'POST')
      .mockResolvedValue({ data: {}, response: new Response(null, { status: 200 }) } as never);
    open('/admin?tab=events');

    fireEvent.click(await screen.findByRole('button', { name: 'Retry' }));

    await waitFor(() => expect(post).toHaveBeenCalled());
    expect(post).toHaveBeenCalledWith('/api/admin/events/{id}/retry', { params: { path: { id: failed.id } } });
  });

  it('switches the debug logs on', async () => {
    const put = vi
      .spyOn(api, 'PUT')
      .mockResolvedValue({ data: {}, response: new Response(null, { status: 200 }) } as never);
    open();

    fireEvent.click(await screen.findByRole('switch', { name: 'Debug logs' }));

    await waitFor(() => expect(put).toHaveBeenCalledWith('/api/admin/debug-logging', { body: { enabled: true } }));
  });

  it('is shown in German too', () => {
    open('/admin', 'de');

    expect(screen.getByRole('tablist', { name: 'Admin-Bereiche' })).toBeTruthy();
  });
});
