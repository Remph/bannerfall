// @vitest-environment jsdom
import { cleanup, fireEvent, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { meQueryKey } from '../../api/queries/auth';
import { bannersQueryKey } from '../../api/queries/banners';
import { reportsQueryKey } from '../../api/queries/reports';
import { smithyQueryKey } from '../../api/queries/smithy';
import { troopsQueryKey, type Troops } from '../../api/queries/troops';
import { tutorialQueryKey } from '../../api/queries/tutorial';
import { villageQueryKey } from '../../api/queries/villages';
import { worldQueryKey } from '../../api/queries/world';
import { SIMULATOR_REPORT_LOOKBACK } from '../../game/simulator';
import { banners, FIXTURE_NOW, me, smithy, tutorial, village, world } from '../../testing/fixtures';
import { renderPage } from '../../testing/renderPage';
import { SimulatorPage } from './SimulatorPage';

const troops: Troops = {
  home: { SPEARMAN: 50, AXEMAN: 30 },
  supportsHere: [],
  ownUnitsElsewhere: [],
  outgoing: [],
  incoming: [],
};

const noReports = {
  items: [],
  page: 0,
  size: SIMULATOR_REPORT_LOOKBACK,
  total: 0,
  unread: 0,
  newestId: 0,
  barbarian: 0,
  counts: {},
};

function open(language: 'en' | 'de' = 'en') {
  return renderPage({
    path: '/v/:villageId/rally/simulator',
    url: `/v/${village.id}/rally/simulator`,
    element: <SimulatorPage />,
    data: [
      [meQueryKey, me],
      [villageQueryKey(village.id), village],
      [troopsQueryKey(village.id), troops],
      [worldQueryKey, world],
      [bannersQueryKey, banners],
      [smithyQueryKey(village.id), smithy],
      [tutorialQueryKey, tutorial],
      [[...reportsQueryKey, 'recent', SIMULATOR_REPORT_LOOKBACK], noReports],
    ],
    language,
  });
}

const count = (label: string) => (screen.getByRole('textbox', { name: label }) as HTMLInputElement).value;

/** The render test of the attack simulator (2.1 H1): both sides, the report picker, filling in and the check. */
describe('SimulatorPage', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(FIXTURE_NOW));
  });
  afterEach(() => {
    cleanup();
    vi.useRealTimers();
  });

  it('shows the attacker, the defender, the report picker and Simulate', () => {
    open();
    expect(screen.getByRole('heading', { level: 1, name: 'Attack simulator' })).toBeTruthy();
    for (const title of ['Report', 'Attacker', 'Defender']) {
      expect(screen.getByRole('heading', { name: title })).toBeTruthy();
    }
    expect(screen.getByText('You have no battle reports yet.')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Simulate' })).toBeTruthy();
    expect(screen.getByRole('link', { name: /Rally Point/ }).getAttribute('href')).toBe(`/v/${village.id}/rally`);
  });

  it('asks for an attacking unit before it simulates', () => {
    open();
    fireEvent.click(screen.getByRole('button', { name: 'Simulate' }));
    expect(screen.getByText('Type in at least one attacking unit.')).toBeTruthy();
  });

  it('fills the attacker with the units at home', () => {
    open();
    fireEvent.click(screen.getByRole('button', { name: 'My units here' }));
    expect(count('Attacking: Spearman')).toBe('50');
    expect(count('Attacking: Axeman')).toBe('30');
    // a unit the village has none of stays empty
    expect(count('Attacking: Light Cavalry')).toBe('');
  });

  it('speaks German', () => {
    open('de');
    expect(screen.getByRole('heading', { level: 1, name: 'Angriffssimulator' })).toBeTruthy();
  });
});
