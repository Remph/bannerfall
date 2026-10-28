// @vitest-environment jsdom
import { cleanup, fireEvent, screen, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { RecruitBuilding } from '../../api/queries/recruitment';
import { recruitmentQueryKey } from '../../api/queries/recruitment';
import { recruitTemplatesQueryKey } from '../../api/queries/recruitTemplates';
import { troopsQueryKey, type Troops } from '../../api/queries/troops';
import { villageQueryKey } from '../../api/queries/villages';
import { FIXTURE_NOW, village } from '../../testing/fixtures';
import { renderPage } from '../../testing/renderPage';
import { RecruitPage } from './RecruitPage';

// the request itself is the typed client's; here only what the page sends
const recruit = vi.hoisted(() => vi.fn());
vi.mock('../../api/queries/recruitment', async (importOriginal) => {
  const original = await importOriginal<typeof import('../../api/queries/recruitment')>();
  return {
    ...original,
    useEnqueueRecruit: () => ({ mutate: recruit, isPending: false, isError: false, variables: undefined }),
  };
});

const cost = { wood: 50, clay: 30, iron: 20 };

const buildings: RecruitBuilding[] = [
  {
    building: 'BARRACKS',
    level: 5,
    units: [
      {
        unit: 'SPEARMAN',
        cost,
        pop: 1,
        secondsPerUnit: 60,
        available: true,
        missing: [],
        maxAffordable: 80,
        atHome: 12,
        dismissSecondsPerUnit: 20,
      },
      {
        unit: 'AXEMAN',
        cost,
        pop: 1,
        secondsPerUnit: 70,
        available: false,
        missing: [{ building: 'SMITHY', level: 20 }],
        maxAffordable: 0,
        atHome: 0,
        dismissSecondsPerUnit: 25,
      },
    ],
    queue: [],
    dismissQueue: [],
  },
  { building: 'STABLE', level: 0, units: [], queue: [], dismissQueue: [] },
];

const troops: Troops = { home: {}, supportsHere: [], ownUnitsElsewhere: [], outgoing: [], incoming: [] };

function open(building: string, data: RecruitBuilding[] = buildings, language: 'en' | 'de' = 'en') {
  return renderPage({
    path: '/v/:villageId/recruit/:building',
    url: `/v/${village.id}/recruit/${building}`,
    element: <RecruitPage />,
    data: [
      [villageQueryKey(village.id), village],
      [recruitmentQueryKey(village.id), data],
      [troopsQueryKey(village.id), troops],
      [recruitTemplatesQueryKey, []],
    ],
    language,
  });
}

describe('RecruitPage', () => {
  beforeEach(() => {
    recruit.mockReset();
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(FIXTURE_NOW));
  });

  afterEach(() => {
    cleanup();
    vi.useRealTimers();
  });

  it('shows the tabs of the recruiting buildings and the units of the open one', () => {
    open('barracks');

    expect(screen.getByRole('heading', { level: 1, name: 'Recruit' })).toBeTruthy();
    const tabs = within(screen.getByRole('navigation', { name: 'Recruiting buildings' }));
    expect(tabs.getByRole('link', { name: /Barracks/ }).textContent).toContain('Lv 5');
    expect(tabs.getByRole('link', { name: /Stable/ }).textContent).toContain('not built');
    expect(screen.getByRole('heading', { name: 'Units (Barracks)' })).toBeTruthy();
    expect(screen.getByLabelText('Number of units to recruit: Spearman')).toBeTruthy();
    expect(screen.getAllByText('0 at home')).toHaveLength(2);
  });

  it('says what a locked unit still needs and offers no field for it', () => {
    open('barracks');

    expect(screen.queryByLabelText('Number of units to recruit: Axeman')).toBeNull();
    expect(screen.getByText('Axeman')).toBeTruthy();
    expect(screen.getByText(/Smithy/)).toBeTruthy();
  });

  it('sends the typed count of one unit', () => {
    open('barracks');

    expect(screen.getByText('Enter a number for a unit first.')).toBeTruthy();
    fireEvent.change(screen.getByLabelText('Number of units to recruit: Spearman'), { target: { value: '10' } });
    fireEvent.click(screen.getAllByRole('button', { name: 'Recruit' })[0]!);

    expect(recruit.mock.calls[0]?.[0]).toEqual({ unit: 'SPEARMAN', count: 10 });
  });

  it('sends a building that is not built to the Town Hall', () => {
    open('stable');

    expect(screen.getByText('Stable: not built yet.')).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Build it in the Town Hall' }).getAttribute('href')).toBe(
      `/v/${village.id}/hq#STABLE`,
    );
  });

  it('opens the barracks for an unknown building name', () => {
    open('moat');

    expect(screen.getByRole('heading', { name: 'Units (Barracks)' })).toBeTruthy();
  });

  it('is shown in German too', () => {
    open('barracks', buildings, 'de');

    expect(screen.getByRole('heading', { level: 1, name: 'Rekrutieren' })).toBeTruthy();
  });
});
