// @vitest-environment jsdom
import { cleanup, fireEvent, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { playerQueryKey, type PlayerHistory, type PlayerStats } from '../../api/queries/ranking';
import { renderPage } from '../../testing/renderPage';
import { HistoryCard, StatisticsCard } from './PlayerStatistics';

const stats: PlayerStats = {
  attacksWon: 12,
  attacksLost: 3,
  defencesWon: 4,
  defencesLost: 1,
  killedAttacking: 1520,
  killedDefending: 300,
  unitsLost: 410,
  lootTaken: 25_400,
  lootLost: 900,
  villagesConquered: 2,
  villagesLost: 0,
  unitsRecruited: 800,
  buildingLevels: 95,
  since: '2026-10-20T08:00:00Z',
};

function history(days: number): PlayerHistory {
  return {
    since: '2026-10-20T08:00:00Z',
    days: Array.from({ length: days }, (_, i) => ({
      day: `2026-10-${20 + i}`,
      points: 1000 + 100 * i,
      villages: 1 + i,
      rank: 2,
    })),
  };
}

afterEach(cleanup);

describe('the profile statistics (1.5)', () => {
  it('shows every total, won and lost side by side', () => {
    renderPage({ path: '/players/:id', url: '/players/7', element: <StatisticsCard stats={stats} />, data: [] });

    expect(screen.getByText('12 won · 3 lost')).toBeTruthy();
    expect(screen.getByText('25,400 taken · 900 lost')).toBeTruthy();
    expect(screen.getByText('2 conquered · 0 lost')).toBeTruthy();
    expect(screen.getByText('1,520')).toBeTruthy();
    expect(screen.getByText(/Counted since/)).toBeTruthy();
  });

  it('speaks German', () => {
    renderPage({
      path: '/players/:id',
      url: '/players/7',
      element: <StatisticsCard stats={stats} />,
      data: [],
      language: 'de',
    });

    expect(screen.getByRole('heading', { name: 'Statistik' })).toBeTruthy();
    expect(screen.getByText('25.400 erbeutet · 900 verloren')).toBeTruthy();
  });

  it('charts the points and switches to the villages', () => {
    renderPage({
      path: '/players/:id',
      url: '/players/7',
      element: <HistoryCard playerId={7} name="ada" />,
      data: [[[...playerQueryKey, 7, 'history'], history(3)]],
    });

    expect(screen.getByRole('img', { name: 'ada: Points' })).toBeTruthy();
    expect(document.querySelector('[aria-live]')?.textContent).toContain('1,200');
    fireEvent.click(screen.getByRole('button', { name: 'Villages' }));
    expect(document.querySelector('[aria-live]')?.textContent).toContain('3');
    expect(screen.getByRole('img', { name: 'ada: Villages' })).toBeTruthy();
  });

  it('says that a single day is only the start', () => {
    renderPage({
      path: '/players/:id',
      url: '/players/7',
      element: <HistoryCard playerId={7} name="ada" />,
      data: [[[...playerQueryKey, 7, 'history'], history(1)]],
    });

    expect(screen.getByText(/The first day/)).toBeTruthy();
  });
});
