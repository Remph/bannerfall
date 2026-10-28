// @vitest-environment jsdom
import { cleanup, fireEvent, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { meQueryKey } from '../../api/queries/auth';
import { recruitmentQueryKey } from '../../api/queries/recruitment';
import { troopsQueryKey, type Troops } from '../../api/queries/troops';
import { villageQueryKey, type Village } from '../../api/queries/villages';
import { FIXTURE_NOW, me, village } from '../../testing/fixtures';
import { renderPage } from '../../testing/renderPage';
import { VillagePage } from './VillagePage';

const noTroops: Troops = { home: {}, supportsHere: [], ownUnitsElsewhere: [], outgoing: [], incoming: [] };

function open(shown: Village = village, language: 'en' | 'de' = 'en') {
  return renderPage({
    path: '/v/:villageId',
    url: `/v/${shown.id}`,
    element: <VillagePage />,
    data: [
      [meQueryKey, me],
      [villageQueryKey(shown.id), shown],
      [troopsQueryKey(shown.id), noTroops],
      [recruitmentQueryKey(shown.id), []],
    ],
    language,
  });
}

describe('VillagePage', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(FIXTURE_NOW));
  });

  afterEach(() => {
    cleanup();
    vi.useRealTimers();
  });

  it('shows the village with its place, points, facts and the cards of the overview', () => {
    open();

    expect(screen.getByRole('heading', { level: 1, name: village.name })).toBeTruthy();
    expect(screen.getByText(/61\|58/)).toBeTruthy();
    for (const title of ['Buildings', 'Village', 'Troops at home']) {
      expect(screen.getByRole('heading', { name: title })).toBeTruthy();
    }
    expect(screen.getByText('No troops at home yet.')).toBeTruthy();
    // 150 of 1,151 people
    expect(screen.getByText('150 / 1,151')).toBeTruthy();
    expect(screen.queryByText(/under attack|attack is on its way/i)).toBeNull();
  });

  it('lists every building with its level when the list is chosen, and remembers the choice', () => {
    open();

    fireEvent.click(screen.getByRole('button', { name: 'List' }));

    expect(screen.getByRole('link', { name: 'Town HallLv 10' }).getAttribute('href')).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Stablenot built' })).toBeTruthy();
    expect(screen.getAllByRole('link', { name: /(Lv \d+|not built)$/ })).toHaveLength(16);
    expect(localStorage.getItem('bannerfall.villageView')).toBe('list');
  });

  it('warns about the attacks on their way and links to the rally point', () => {
    open({ ...village, incomingAttacks: 2 });

    const link = screen.getByRole('link', { name: /incoming/i });
    expect(link.getAttribute('href')).toBe(`/v/${village.id}/rally#incoming`);
  });

  it('shows the beginner protection until it ends', () => {
    open();
    expect(screen.queryByText(/protection/i)).toBeNull();
    cleanup();

    renderPage({
      path: '/v/:villageId',
      url: `/v/${village.id}`,
      element: <VillagePage />,
      data: [
        [meQueryKey, { ...me, protectedUntil: '2026-10-17T20:27:07Z' }],
        [villageQueryKey(village.id), village],
        [troopsQueryKey(village.id), noTroops],
        [recruitmentQueryKey(village.id), []],
      ],
    });
    expect(screen.getAllByText(/protect/i).length).toBeGreaterThan(0);
  });

  it('is shown in German too', () => {
    open(village, 'de');

    expect(screen.getByRole('heading', { name: 'Gebäude' })).toBeTruthy();
    expect(screen.getByText('Noch keine Truppen im Dorf.')).toBeTruthy();
  });
});
