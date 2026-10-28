// @vitest-environment jsdom
import { cleanup, fireEvent, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  automationQueryKey,
  buildPlansQueryKey,
  planBuildingsQueryKey,
  type VillageAutomation,
} from '../../api/queries/automation';
import { recruitTemplatesQueryKey } from '../../api/queries/recruitTemplates';
import { villageQueryKey } from '../../api/queries/villages';
import { FIXTURE_NOW, village } from '../../testing/fixtures';
import { renderPage } from '../../testing/renderPage';
import { AutomationPage } from './AutomationPage';

// the request itself is the typed client's; here only what the page sends
const setAutomation = vi.hoisted(() => vi.fn());
vi.mock('../../api/queries/automation', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/queries/automation')>()),
  useSetVillageAutomation: () => ({ mutate: setAutomation, isPending: false, isError: false }),
}));

const plans = [
  {
    letter: 'A',
    name: 'Economy',
    lines: [
      { building: 'HEADQUARTERS', level: 5, recruit: null, mines: false },
      { building: 'BARRACKS', level: 3, recruit: null, mines: false },
      { building: null, level: 0, recruit: 'B', mines: false },
    ],
  },
];

const buildings = [
  { building: 'HEADQUARTERS', startLevel: 1, maxLevel: 20, requires: [], available: true },
  {
    building: 'BARRACKS',
    startLevel: 0,
    maxLevel: 25,
    requires: [{ building: 'HEADQUARTERS', level: 3 }],
    available: true,
  },
];

const templates = [{ letter: 'B', name: 'Spears', units: { SPEARMAN: 100 } }];

const running: VillageAutomation = {
  state: 'WAITING_RESOURCES',
  villagesNeeded: 3,
  villages: 3,
  plan: 'A',
  enabled: true,
  pausedByCancel: false,
  line: 1,
  lineCount: 3,
  building: 'BARRACKS',
  level: 3,
  readyAt: '2026-10-15T22:00:00.000Z',
  recruiting: null,
  missing: {},
  byHand: [],
  blocked: {},
};

function open(status: VillageAutomation, language: 'en' | 'de' = 'en') {
  return renderPage({
    path: '/v/:villageId/automation',
    url: `/v/${village.id}/automation`,
    element: <AutomationPage />,
    data: [
      [villageQueryKey(village.id), village],
      [automationQueryKey(village.id), status],
      [buildPlansQueryKey, plans],
      [planBuildingsQueryKey, buildings],
      [recruitTemplatesQueryKey, templates],
    ],
    language,
  });
}

describe('AutomationPage', () => {
  beforeEach(() => {
    setAutomation.mockReset();
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(FIXTURE_NOW));
  });

  afterEach(() => {
    cleanup();
    vi.useRealTimers();
  });

  it('shows the plan of the village, how far it is and what it waits for', () => {
    open(running);

    expect(screen.getByRole('heading', { level: 1, name: 'Automation' })).toBeTruthy();
    expect(screen.getByText(village.name)).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Plan A: Economy. Change plan' })).toBeTruthy();
    expect(screen.getByText('Running')).toBeTruthy();
    expect(screen.getByText('Line 2 of 3')).toBeTruthy();
    expect(screen.getByText(/^Waits for resources: /)).toBeTruthy();
    // the whole plan is listed, the line it stands at marked
    expect(screen.getByRole('heading', { name: 'Plan A · Economy' })).toBeTruthy();
    expect(screen.getAllByRole('listitem').find((item) => item.getAttribute('aria-current') === 'step')).toBeTruthy();
  });

  it('turns the switch off for this village only', () => {
    open(running);

    fireEvent.click(screen.getByRole('switch', { name: 'Automation on' }));

    expect(setAutomation).toHaveBeenCalledExactlyOnceWith({ plan: 'A', enabled: false });
  });

  it('asks for a plan when the village has none and offers no switch', () => {
    open({ ...running, state: 'NO_PLAN', plan: null, enabled: false, line: 0, building: null });

    expect(screen.getByRole('button', { name: 'Choose a plan' })).toBeTruthy();
    expect(screen.queryByRole('switch')).toBeNull();
    expect(screen.getByRole('heading', { name: 'Build plans' })).toBeTruthy();
  });

  it('says from which village the automation unlocks and hides the plan', () => {
    open({ ...running, state: 'LOCKED', villagesNeeded: 3, villages: 2, plan: null, enabled: false });

    expect(screen.getByText(/^Unlocks with your 3rd village/)).toBeTruthy();
    expect(screen.getByText('You have 2 of 3 villages.')).toBeTruthy();
    expect(screen.queryByRole('switch')).toBeNull();
  });

  it('is shown in German too', () => {
    open(running, 'de');

    expect(screen.getByRole('heading', { level: 1, name: 'Automatik' })).toBeTruthy();
  });
});
