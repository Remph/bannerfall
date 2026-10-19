// @vitest-environment jsdom
import { cleanup, fireEvent, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { worldStatisticsQueryKey, type WorldStatistics } from '../../api/queries/statistics';
import { renderPage } from '../../testing/renderPage';
import { WorldStatisticsPage } from './WorldStatisticsPage';

const statistics: WorldStatistics = {
  since: '2026-10-20T08:00:00Z',
  days: [
    { day: '2026-10-20', players: 10, villages: 40, barbarianVillages: 30, points: 9000 },
    { day: '2026-10-21', players: 11, villages: 44, barbarianVillages: 28, points: 9800 },
  ],
  leaders: [
    {
      playerId: 7,
      name: 'ada',
      npc: false,
      days: [
        { day: '2026-10-20', points: 2000 },
        { day: '2026-10-21', points: 2300 },
      ],
    },
    { playerId: 9, name: 'Sir Godwin', npc: true, days: [{ day: '2026-10-21', points: 1900 }] },
  ],
};

afterEach(cleanup);

describe('WorldStatisticsPage (1.5)', () => {
  it('charts the leaders and the world, today first in the read-out', () => {
    renderPage({
      path: '/statistics',
      url: '/statistics',
      element: <WorldStatisticsPage />,
      data: [[worldStatisticsQueryKey, statistics]],
    });

    expect(screen.getByRole('heading', { level: 1, name: 'World statistics' })).toBeTruthy();
    expect(screen.getByRole('img', { name: 'Leading players' })).toBeTruthy();
    expect(screen.getAllByRole('listitem').map((item) => item.textContent)).toEqual(['ada', 'Sir Godwin']);
    expect(screen.getByText('11 players · 44 villages · 28 barbarian villages')).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: 'Points' }));
    expect(screen.getByRole('img', { name: 'The world: Points' })).toBeTruthy();
  });
});
