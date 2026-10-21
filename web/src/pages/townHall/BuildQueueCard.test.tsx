// @vitest-environment jsdom
import { cleanup, fireEvent, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { api } from '../../api/client';
import type { BuildOrder } from '../../api/queries/buildings';
import { useVillage, villageQueryKey, type Village } from '../../api/queries/villages';
import { renderPage } from '../../testing/renderPage';
import { BuildQueueCard } from './BuildQueueCard';

const NOW = '2026-10-16T12:00:00Z';

const running: BuildOrder = {
  id: 1,
  building: 'WALL',
  targetLevel: 3,
  startAt: '2026-10-16T11:50:00Z',
  endAt: '2026-10-16T12:10:00Z',
  demolish: false,
  automated: false,
};
const farm: BuildOrder = {
  id: 2,
  building: 'FARM',
  targetLevel: 5,
  startAt: '2026-10-16T12:10:00Z',
  endAt: '2026-10-16T12:30:00Z',
  demolish: false,
  automated: false,
};
const market: BuildOrder = {
  id: 3,
  building: 'MARKET',
  targetLevel: 2,
  startAt: '2026-10-16T12:30:00Z',
  endAt: '2026-10-16T12:45:00Z',
  demolish: false,
  automated: false,
};

function open(buildQueue: BuildOrder[]) {
  const village = { id: 47, buildQueue } as Village;
  return renderPage({
    path: '/',
    url: '/',
    element: <BuildQueueCard village={village} queueSize={5} queueMax={5} />,
    data: [],
  });
}

/** The card fed from the cached village, as on the Town Hall page, so a move's answer shows. */
function CachedCard() {
  const village = useVillage(47).data;
  return village === undefined ? null : <BuildQueueCard village={village} queueSize={5} queueMax={5} />;
}

beforeAll(() => {
  // jsdom has no modal dialogs
  HTMLDialogElement.prototype.showModal = function (this: HTMLDialogElement) {
    this.open = true;
  };
});

describe('BuildQueueCard', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(NOW));
  });

  afterEach(() => {
    cleanup();
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('shows waiting orders as slim lines with a cancel button and no move buttons', () => {
    open([running, farm, market]);

    expect(screen.getAllByRole('listitem')).toHaveLength(3);
    expect(screen.getByText('Farm')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Cancel Farm Lv 5' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: /^Move / })).toBeNull();
  });

  it('shows the move buttons after "Change order" until "Done"', () => {
    open([running, farm, market]);

    fireEvent.click(screen.getByRole('button', { name: 'Change order' }));
    expect(screen.getByRole('button', { name: 'Move Farm Lv 5 down' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Move Market Lv 2 up' })).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: 'Done' }));
    expect(screen.queryByRole('button', { name: /^Move / })).toBeNull();
  });

  it('marks a demolition and says nothing comes back when it is cancelled (1.6)', async () => {
    open([running, { ...market, building: 'WALL', targetLevel: 2, demolish: true }]);

    expect(screen.getByText('demolition')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Cancel Wall Lv 2' }));
    expect(await screen.findByText('Wall Lv 2 will not be demolished.')).toBeTruthy();
    expect(screen.getByText(/nothing comes back/)).toBeTruthy();
  });

  it('offers no "Change order" while nothing can move', () => {
    open([running, farm]);

    expect(screen.queryByRole('button', { name: 'Change order' })).toBeNull();
  });

  it('gives the focus back to the moved order, on its other button once it reached the end', async () => {
    const moved = [running, { ...market, startAt: farm.startAt }, farm];
    vi.spyOn(api, 'POST').mockResolvedValue({ data: moved, response: new Response() } as never);
    renderPage({
      path: '/',
      url: '/',
      element: <CachedCard />,
      data: [[villageQueryKey(47), { id: 47, buildQueue: [running, farm, market] }]],
    });
    fireEvent.click(screen.getByRole('button', { name: 'Change order' }));

    const down = screen.getByRole('button', { name: 'Move Farm Lv 5 down' });
    down.focus();
    fireEvent.click(down);

    // the farm is last now: its down button is off, so the up button has the focus
    await waitFor(() => expect(document.activeElement).toBe(screen.getByRole('button', { name: 'Move Farm Lv 5 up' })));
    expect(screen.getAllByRole('listitem')[2]?.textContent).toContain('Farm');
  });

  it('gives the focus back to the pressed button when the move fails', async () => {
    vi.spyOn(api, 'POST').mockRejectedValue(new Error('refused'));
    // the failed move reloads the village: the same queue, so only the failure ends the wait
    const unchanged = { id: 47, buildQueue: [running, farm, market] };
    vi.spyOn(api, 'GET').mockResolvedValue({ data: unchanged, response: new Response() } as never);
    renderPage({
      path: '/',
      url: '/',
      element: <CachedCard />,
      data: [[villageQueryKey(47), unchanged]],
    });
    fireEvent.click(screen.getByRole('button', { name: 'Change order' }));

    const up = screen.getByRole('button', { name: 'Move Market Lv 2 up' });
    up.focus();
    fireEvent.click(up);

    await waitFor(() => expect(screen.getByRole('alert')).toBeTruthy());
    expect(document.activeElement).toBe(screen.getByRole('button', { name: 'Move Market Lv 2 up' }));
  });
});
