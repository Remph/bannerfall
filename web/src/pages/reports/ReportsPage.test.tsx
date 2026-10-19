// @vitest-environment jsdom
import { cleanup, fireEvent, screen, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { meQueryKey, type Me } from '../../api/queries/auth';
import { reportPageQueryKey, type ReportItem, type ReportPage } from '../../api/queries/reports';
import { renderPage } from '../../testing/renderPage';
import { ReportsPage } from './ReportsPage';

// select mode's delete: the request itself is the typed client's; here only what the page asks for
const deleteReports = vi.hoisted(() => vi.fn());
vi.mock('../../api/queries/reports', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/queries/reports')>()),
  useDeleteReports: () => ({ mutate: deleteReports, isPending: false, isError: false, error: null, reset: () => {} }),
}));

const NOW = '2026-10-16T12:00:00Z';

const me: Me = {
  accountId: 1,
  username: 'ben',
  role: 'PLAYER',
  playerId: 7,
  points: 100,
  protectedUntil: '2026-10-10T00:00:00Z',
  needsRespawn: false,
  villages: [],
};

const ben = { id: 1, name: 'Benhausen', x: 48, y: 52, playerId: 7, playerName: 'Ben' };
const tom = { id: 2, name: 'Tomburg', x: 50, y: 50, playerId: 8, playerName: 'Tom' };

function battle(id: number, at: string, summary: Partial<NonNullable<ReportItem['summary']>> = {}): ReportItem {
  return {
    id,
    kind: 'ATTACK',
    happenedAt: at,
    listedAt: at,
    read: true,
    sharedBy: null,
    summary: {
      from: ben,
      to: tom,
      outcome: 'ATTACKER_WON',
      bannersGained: [],
      loot: { wood: 150, clay: 425, iron: 425 },
      losses: {},
      fullHaul: false,
      nobles: false,
      ...summary,
    },
  };
}

const page: ReportPage = {
  items: [
    battle(3, '2026-10-16T09:12:00Z', { fullHaul: true }),
    battle(2, '2026-10-15T21:40:00Z', { losses: { AXEMAN: 3 } }),
    battle(1, '2026-10-13T08:05:00Z', { outcome: 'DEFENDER_WON', loot: { wood: 0, clay: 0, iron: 0 } }),
  ],
  page: 0,
  size: 25,
  total: 3,
  unread: 0,
  newestId: 3,
  barbarian: 0,
  counts: { ATTACKS: 3, DEFENSE: 0, SCOUTING: 2, SUPPORT: 1, TRADE: 0 },
};

function open(url = '/reports', data: [readonly unknown[], unknown][] = [[reportPageQueryKey(0), page]]) {
  return renderPage({
    path: '/reports',
    url,
    element: <ReportsPage />,
    // the report itself is ReportPage's: here only where it goes
    children: [{ path: ':reportId', element: <p>The open report</p> }],
    data: [[meQueryKey, me], ...data],
  });
}

/** A desktop window (≥ 1024 px): the list and the open report side by side. Without it jsdom is a phone. */
function onDesktop() {
  vi.stubGlobal('matchMedia', (query: string) => ({
    matches: true,
    media: query,
    addEventListener: () => {},
    removeEventListener: () => {},
  }));
}

describe('ReportsPage', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(NOW));
  });

  afterEach(() => {
    cleanup();
    vi.useRealTimers();
    vi.unstubAllGlobals();
    deleteReports.mockClear();
  });

  it('offers a filter chip per kind with its count, all reports first', () => {
    open();

    const chips = within(screen.getByRole('navigation', { name: 'Show reports' })).getAllByRole('link');
    expect(chips.map((chip) => chip.textContent)).toEqual([
      'All 6',
      'Attacks 3',
      'Defence 0',
      'Scouting 2',
      'Support 1',
      'Trade 0',
    ]);
    expect(chips[0]?.getAttribute('aria-current')).toBe('page');
    expect(chips[1]?.getAttribute('href')).toBe('/reports?filter=attacks');
    // the head counts all reports, not the shown ones
    expect(screen.getByText('6 reports')).toBeTruthy();
  });

  it('puts the reports under one heading per day', () => {
    open();

    expect(screen.getAllByRole('heading', { level: 2 }).map((heading) => heading.textContent)).toEqual([
      'Today',
      'Yesterday',
      'Tuesday 13 October',
    ]);
  });

  it('shows a battle’s loot and losses in the list', () => {
    open();

    expect(screen.getByText('full haul')).toBeTruthy();
    expect(screen.getAllByText('no losses')).toHaveLength(2);
    expect(screen.getByText('Losses:')).toBeTruthy();
  });

  it('rings the icon of an attack with nobles in gold and says so to screen readers', () => {
    const withNobles = {
      ...page,
      items: [battle(3, '2026-10-16T09:12:00Z', { nobles: true }), ...page.items.slice(1)],
    };
    open('/reports', [[reportPageQueryKey(0), withNobles]]);

    const lines = screen.getAllByRole('listitem');
    expect(lines[0]?.textContent).toContain('Attack, with nobles');
    expect(lines.filter((line) => line.textContent?.includes('with nobles'))).toHaveLength(1);
  });

  it('asks the server for the filter in the address', () => {
    const attacks = { ...page, items: page.items.slice(0, 1), total: 1 };
    open('/reports?filter=attacks', [[reportPageQueryKey(0, 'ATTACKS'), attacks]]);

    expect(screen.getByRole('link', { name: 'Attacks 3' }).getAttribute('aria-current')).toBe('page');
    expect(screen.getAllByRole('heading', { level: 2 })).toHaveLength(1);
  });

  it('says when a filter has no reports', () => {
    open('/reports?filter=trade', [[reportPageQueryKey(0, 'TRADE'), { ...page, items: [], total: 0 }]]);

    expect(screen.getByText('No reports of this kind.')).toBeTruthy();
  });

  it('keeps "Delete barbarian reports" and "Delete all" and has no delete button on the lines', () => {
    open('/reports', [[reportPageQueryKey(0), { ...page, barbarian: 2 }]]);

    expect(screen.getByRole('button', { name: 'Delete barbarian reports' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Delete all' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: /^Delete report/ })).toBeNull();
    expect(screen.queryAllByRole('checkbox')).toHaveLength(0);
  });

  it('deletes the lines chosen in select mode with two taps', () => {
    open();

    fireEvent.click(screen.getByRole('button', { name: 'Select' }));
    const boxes = screen.getAllByRole('checkbox');
    expect(boxes).toHaveLength(3);
    fireEvent.click(boxes[0]!);
    fireEvent.click(boxes[2]!);
    expect(screen.getByRole('status').textContent).toBe('2 selected');

    fireEvent.click(screen.getByRole('button', { name: 'Delete' }));
    expect(deleteReports).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: 'Really delete?' }));

    expect(deleteReports).toHaveBeenCalledOnce();
    expect(deleteReports.mock.calls[0]?.[0]).toEqual([3, 1]);
  });

  it('chooses all lines of the page, then none', () => {
    open();

    fireEvent.click(screen.getByRole('button', { name: 'Select' }));
    expect(screen.getByRole('button', { name: 'Delete' }).hasAttribute('disabled')).toBe(true);
    fireEvent.click(screen.getByRole('button', { name: 'Select all' }));
    expect(screen.getByRole('status').textContent).toBe('3 selected');
    fireEvent.click(screen.getByRole('button', { name: 'Select none' }));
    expect(screen.getByRole('status').textContent).toBe('0 selected');

    fireEvent.click(screen.getByRole('button', { name: 'Done' }));
    expect(screen.queryAllByRole('checkbox')).toHaveLength(0);
  });

  it('links each line to its report with the list it is on, for the way back', () => {
    open('/reports?filter=attacks', [[reportPageQueryKey(0, 'ATTACKS'), page]]);

    const lines = within(screen.getAllByRole('list')[0]!).getAllByRole('link');
    expect(lines[0]?.getAttribute('href')).toBe('/reports/3?filter=attacks');
  });

  it('shows an open report as a page of its own on phones', () => {
    open('/reports/2');

    expect(screen.getByText('The open report')).toBeTruthy();
    expect(screen.queryByRole('heading', { level: 1, name: 'Reports' })).toBeNull();
  });
});

describe('ReportsPage on desktop', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(NOW));
    onDesktop();
  });

  afterEach(() => {
    cleanup();
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('asks to pick a report next to the list until one is open', () => {
    open();

    expect(screen.getByRole('heading', { level: 1, name: 'Reports' })).toBeTruthy();
    expect(screen.getByText('Pick a report on the left to read it here.')).toBeTruthy();
  });

  it('runs the title, the buttons and the filter chips across the list and the open report', () => {
    open('/reports/2');

    // the list and the report share a row below the head, not the head itself (1.4.1)
    const split = screen.getByRole('region', { name: 'Open report' }).parentElement!;
    expect(split.contains(screen.getByRole('heading', { level: 1, name: 'Reports' }))).toBe(false);
    expect(split.contains(screen.getByRole('button', { name: 'Delete all' }))).toBe(false);
    expect(split.contains(screen.getByRole('navigation', { name: 'Show reports' }))).toBe(false);
    expect(split.contains(screen.getAllByRole('list')[0]!)).toBe(true);
  });

  it('keeps the head, the list and the open report in place while another filter loads', () => {
    // the attacks' page never answers here: the page shows what it shows while it loads
    vi.stubGlobal('fetch', () => new Promise(() => {}));
    open('/reports/2');

    fireEvent.click(screen.getByRole('link', { name: 'Attacks 3' }));

    expect(screen.queryByText('Loading…')).toBeNull();
    expect(screen.getByRole('heading', { level: 1, name: 'Reports' })).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Attacks 3' }).getAttribute('aria-current')).toBe('page');
    expect(screen.getByRole('region', { name: 'Open report' }).textContent).toBe('The open report');
  });

  it('shows the open report next to the list and marks its line', () => {
    open('/reports/2?filter=attacks', [[reportPageQueryKey(0, 'ATTACKS'), page]]);

    expect(screen.getByRole('heading', { level: 1, name: 'Reports' })).toBeTruthy();
    expect(screen.getByRole('region', { name: 'Open report' }).textContent).toBe('The open report');
    const current = screen.getAllByRole('link').filter((link) => link.getAttribute('aria-current') === 'page');
    // the open line and the filter chip
    expect(current.map((link) => link.getAttribute('href'))).toEqual([
      '/reports/2?filter=attacks',
      '/reports/2?filter=attacks',
    ]);
    // the chips keep the report open
    expect(screen.getByRole('link', { name: 'All 6' }).getAttribute('href')).toBe('/reports/2');
  });

  it('moves the focus to a report opened from the list, not to the one the page opens with', () => {
    open('/reports/2?filter=attacks', [[reportPageQueryKey(0, 'ATTACKS'), page]]);
    const detail = screen.getByRole('region', { name: 'Open report' });
    expect(document.activeElement).not.toBe(detail);

    fireEvent.click(within(screen.getAllByRole('list')[0]!).getAllByRole('link')[0]!);

    expect(document.activeElement).toBe(detail);
  });
});
