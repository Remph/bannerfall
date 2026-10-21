// @vitest-environment jsdom
import { cleanup, fireEvent, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import { api } from '../../api/client';
import type { BuildingInfo, Demolition } from '../../api/queries/buildings';
import { renderPage } from '../../testing/renderPage';
import { DemolishCard } from './DemolishCard';

function building(overrides: Partial<BuildingInfo>, demolish: Partial<Demolition> | null = {}): BuildingInfo {
  return {
    building: 'SMITHY',
    level: 5,
    queuedLevel: 5,
    maxLevel: 20,
    next: null,
    requirementsMet: true,
    missing: [],
    ...overrides,
    demolish:
      demolish === null
        ? null
        : {
            level: 4,
            durationSeconds: 754,
            pop: 3,
            points: 33,
            locksBuildings: [],
            locksUnits: [],
            blocker: null,
            ...demolish,
          },
  };
}

function open(buildings: BuildingInfo[], queueFull = false) {
  return renderPage({
    path: '/',
    url: '/',
    element: <DemolishCard villageId={47} buildings={buildings} queueFull={queueFull} />,
    data: [],
  });
}

beforeAll(() => {
  // jsdom has no modal dialogs
  HTMLDialogElement.prototype.showModal = function (this: HTMLDialogElement) {
    this.open = true;
  };
});

describe('DemolishCard', () => {
  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  it('lists the built buildings with the time, freed population and points of the next demolition', () => {
    open([building({}), building({ building: 'WALL', level: 0, queuedLevel: 0 }, null)]);

    // the wall was never built: it isn't listed
    expect(screen.queryByText('Wall')).toBeNull();
    expect(screen.getByText('0:12:34')).toBeTruthy();
    expect(screen.getByText('+3 free')).toBeTruthy();
    expect(screen.getByText('−33 points')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Demolish Smithy to level 4' })).toBeTruthy();
  });

  it('says why a building goes no lower, and why a demolition must wait', () => {
    open([
      building({ building: 'HEADQUARTERS', level: 1, queuedLevel: 1 }, null),
      building({ building: 'RALLY_POINT', level: 1, queuedLevel: 1 }, null),
      building({}, { blocker: 'BUILD_ORDER_CONFLICT' }),
    ]);

    expect(screen.getByText('Level 1 is the lowest.')).toBeTruthy();
    expect(screen.getByText('The rally point can’t be demolished.')).toBeTruthy();
    expect(screen.getByText('An upgrade of this building is in the build queue.')).toBeTruthy();
    // no facts and no button: they would be those of the level the queued upgrade adds
    expect(screen.queryByRole('button', { name: 'Demolish Smithy to level 4' })).toBeNull();
    expect(screen.queryByText('0:12:34')).toBeNull();
  });

  it('keeps the farm’s facts but disables the button when the smaller farm is too small', () => {
    open([building({ building: 'FARM' }, { blocker: 'NOT_ENOUGH_POPULATION' })]);

    expect(screen.getByText('The smaller farm could not feed your population.')).toBeTruthy();
    expect((screen.getByRole('button', { name: 'Demolish Farm to level 4' }) as HTMLButtonElement).disabled).toBe(true);
  });

  it('asks first, names what gets locked, then queues the demolition', async () => {
    const post = vi.spyOn(api, 'POST').mockResolvedValue({ data: {}, response: new Response() } as never);
    open([building({}, { locksBuildings: ['STABLE'], locksUnits: ['AXEMAN'] })]);

    fireEvent.click(screen.getByRole('button', { name: 'Demolish Smithy to level 4' }));
    expect(await screen.findByText('Lv 5 → 4')).toBeTruthy();
    expect(screen.getByText('Stable: no further upgrades')).toBeTruthy();
    expect(screen.getByText('Axeman: no recruiting')).toBeTruthy();
    expect(post).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', { name: 'Demolish' }));
    await waitFor(() =>
      expect(post).toHaveBeenCalledWith('/api/villages/{villageId}/demolitions', {
        params: { path: { villageId: 47 } },
        body: { building: 'SMITHY' },
      }),
    );
  });
});
