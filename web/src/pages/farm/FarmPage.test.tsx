// @vitest-environment jsdom
import { cleanup, fireEvent, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { meQueryKey } from '../../api/queries/auth';
import { farmQueryKey, type FarmTarget } from '../../api/queries/farm';
import { farmTemplatesQueryKey } from '../../api/queries/farmTemplates';
import { tutorialQueryKey } from '../../api/queries/tutorial';
import { villageQueryKey } from '../../api/queries/villages';
import { worldQueryKey } from '../../api/queries/world';
import { FIXTURE_NOW, me, tutorial, village, world } from '../../testing/fixtures';
import { renderPage } from '../../testing/renderPage';
import { FarmPage } from './FarmPage';

const target = (id: number, name: string, over: Partial<FarmTarget> = {}): FarmTarget => ({
  id,
  target: { id: 100 + id, name, x: 60 + id, y: 57, playerId: null, playerName: null },
  units: { SPEARMAN: 10 },
  template: null,
  distance: 2.2,
  travelSeconds: 600,
  protectedUntil: null,
  lastSentAt: null,
  nextBannerRollAt: null,
  bannerChance: false,
  note: null,
  morale: null,
  haulCapacity: 250,
  lastRaid: null,
  ...over,
});

const targets: FarmTarget[] = [target(1, 'Barbarian village'), target(2, 'Eckhart', { note: 'walls 5' })];

function open(list: FarmTarget[] = targets, language: 'en' | 'de' = 'en') {
  return renderPage({
    path: '/v/:villageId/farm',
    url: `/v/${village.id}/farm`,
    element: <FarmPage />,
    data: [
      [meQueryKey, me],
      [villageQueryKey(village.id), village],
      [farmQueryKey(village.id), list],
      [farmTemplatesQueryKey, []],
      [worldQueryKey, world],
      [tutorialQueryKey, tutorial],
    ],
    language,
  });
}

/** The render test of the farm list page (2.1 H1): the list with its bar and rows, the empty list, templates. */
describe('FarmPage', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(FIXTURE_NOW));
  });
  afterEach(() => {
    cleanup();
    vi.useRealTimers();
  });

  it('shows the village’s targets with "Send all", a row per target and the count', () => {
    open();
    expect(screen.getByRole('heading', { level: 1, name: 'Farm list' })).toBeTruthy();
    expect(screen.getByRole('heading', { name: `Targets of ${village.name}` })).toBeTruthy();
    expect(screen.getByRole('button', { name: /Send all/ })).toBeTruthy();
    // one row per target, in the list's order
    const rows = screen.getAllByRole('button', { expanded: false }).map((row) => row.textContent ?? '');
    expect(rows.findIndex((text) => text.includes('Barbarian village'))).toBeLessThan(
      rows.findIndex((text) => text.includes('Eckhart')),
    );
    expect(screen.getByText(/^2 of 50 targets/)).toBeTruthy();
  });

  it('explains an empty list and offers the form to add a target', () => {
    open([]);
    expect(screen.getByText(/No targets yet/)).toBeTruthy();
    expect(screen.queryByRole('button', { name: /Send all/ })).toBeNull();
    expect(screen.getAllByRole('textbox').length).toBeGreaterThan(0);
  });

  it('switches between the targets and the templates and keeps both cards on the page', () => {
    open();
    const templates = screen.getByRole('button', { name: 'Templates', pressed: false });
    fireEvent.click(templates);
    expect(screen.getByRole('button', { name: 'Templates', pressed: true })).toBeTruthy();
    expect(screen.getByRole('heading', { name: 'Templates' })).toBeTruthy();
    // the phone shows one part at a time (by CSS); the targets stay in the page
    expect(screen.getAllByText(/Eckhart/).length).toBeGreaterThan(0);
  });

  it('speaks German', () => {
    open(targets, 'de');
    expect(screen.getByRole('heading', { level: 1, name: 'Farmliste' })).toBeTruthy();
  });
});
