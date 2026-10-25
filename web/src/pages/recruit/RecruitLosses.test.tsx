// @vitest-environment jsdom
import { cleanup, fireEvent, screen, within } from '@testing-library/react';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { recruitmentQueryKey, type RecruitBuilding } from '../../api/queries/recruitment';
import { troopsQueryKey } from '../../api/queries/troops';
import { villageQueryKey } from '../../api/queries/villages';
import { worldQueryKey } from '../../api/queries/world';
import { FIXTURE_NOW, village } from '../../testing/fixtures';
import { renderPage } from '../../testing/renderPage';
import { RecruitLossesButton } from './RecruitLosses';

const enqueued = vi.hoisted(() => vi.fn());

// the orders go to the server through this mutation; the test only needs to see it succeed
vi.mock('../../api/queries/recruitment', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/queries/recruitment')>()),
  useEnqueueRecruitAll: () => ({ mutate: enqueued, reset: vi.fn(), isPending: false, isError: false, error: null }),
}));

const barracks: RecruitBuilding = {
  building: 'BARRACKS',
  level: 5,
  units: [
    {
      unit: 'AXEMAN',
      cost: { wood: 50, clay: 30, iron: 20 },
      pop: 1,
      secondsPerUnit: 100,
      available: true,
      missing: [],
      maxAffordable: 100,
      atHome: 50,
      dismissSecondsPerUnit: 10,
    },
  ],
  queue: [],
  dismissQueue: [],
};

function open(stock = 4200, queued = 0) {
  const home = {
    ...village,
    unitsHome: { AXEMAN: 50 },
    resources: { ...village.resources, wood: stock, clay: stock, iron: stock },
  };
  renderPage({
    path: '/',
    url: '/',
    element: <RecruitLossesButton reportId={7} villageId={village.id} losses={{ AXEMAN: 40 }} />,
    data: [
      [villageQueryKey(village.id), home],
      [
        recruitmentQueryKey(village.id),
        [
          queued === 0
            ? barracks
            : {
                ...barracks,
                queue: [
                  {
                    id: 1,
                    unit: 'AXEMAN',
                    count: queued,
                    completedCount: 0,
                    building: 'BARRACKS',
                    secondsPerUnit: 100,
                    startAt: FIXTURE_NOW,
                    endAt: FIXTURE_NOW,
                  },
                ],
              },
        ],
      ],
      [troopsQueryKey(village.id), { ownUnitsElsewhere: [], outgoing: [], incoming: [] }],
      [worldQueryKey, { recruitQueueSize: 5 }],
    ],
  });
  fireEvent.click(screen.getByRole('button', { name: 'Re-recruit losses' }));
}

describe('RecruitLossesButton', () => {
  beforeAll(() => {
    // jsdom has no modal dialogs
    HTMLDialogElement.prototype.showModal = function (this: HTMLDialogElement) {
      this.open = true;
    };
    HTMLDialogElement.prototype.close = function (this: HTMLDialogElement) {
      this.open = false;
      this.dispatchEvent(new Event('close'));
    };
  });

  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(FIXTURE_NOW));
  });

  afterEach(() => {
    cleanup();
    localStorage.clear();
    vi.useRealTimers();
  });

  it('opens the plan with the lost units on top of what the village owns', () => {
    open();

    const sheet = screen.getByRole('dialog', { name: 'Re-recruit losses' });
    expect((within(sheet).getByRole('textbox', { name: 'Goal for Axeman' }) as HTMLInputElement).value).toBe('90');
    const row = within(sheet).getByRole('row', { name: /Axeman/ });
    expect(row.textContent).toContain('50'); // have
    expect(row.textContent).toContain('40'); // now
    expect(within(sheet).getByRole('button', { name: 'Recruit' }).hasAttribute('disabled')).toBe(false);
  });

  it('cuts the units by one share when the resources fall short', () => {
    open(1000); // 1000 wood pay 20 axemen of 50 wood each

    const row = within(screen.getByRole('dialog', { name: 'Re-recruit losses' })).getByRole('row', { name: /Axeman/ });
    expect(row.textContent).toContain('20');
  });

  it('recruits less when the goal is lowered', () => {
    open();
    const sheet = screen.getByRole('dialog', { name: 'Re-recruit losses' });

    fireEvent.change(within(sheet).getByRole('textbox', { name: 'Goal for Axeman' }), { target: { value: '60' } });

    expect(within(sheet).getByRole('row', { name: /Axeman/ }).textContent).toContain('10');
  });

  it('keeps the goal that was recruited from this report, so the queue is not asked for twice', () => {
    // the 40 axemen are queued by now: 50 at home + 40 in training would make the goal 130
    localStorage.setItem('bannerfall.recruitLossesGoals', JSON.stringify({ [`7:${village.id}`]: { AXEMAN: 90 } }));
    open(4200, 40);

    const sheet = screen.getByRole('dialog', { name: 'Re-recruit losses' });
    expect((within(sheet).getByRole('textbox', { name: 'Goal for Axeman' }) as HTMLInputElement).value).toBe('90');
  });

  it('counts the queue as owned when nothing was recruited from the report yet', () => {
    open(4200, 40);

    const sheet = screen.getByRole('dialog', { name: 'Re-recruit losses' });
    expect((within(sheet).getByRole('textbox', { name: 'Goal for Axeman' }) as HTMLInputElement).value).toBe('130');
  });

  it('remembers the goal once the orders went through', async () => {
    enqueued.mockImplementation((_orders: unknown, options: { onSuccess: () => void }) => options.onSuccess());
    open();
    const sheet = screen.getByRole('dialog', { name: 'Re-recruit losses' });
    fireEvent.change(within(sheet).getByRole('textbox', { name: 'Goal for Axeman' }), { target: { value: '70' } });

    fireEvent.click(within(sheet).getByRole('button', { name: 'Recruit' }));

    expect(JSON.parse(localStorage.getItem('bannerfall.recruitLossesGoals') ?? '{}')).toEqual({
      [`7:${village.id}`]: { AXEMAN: 70 },
    });
  });
});
