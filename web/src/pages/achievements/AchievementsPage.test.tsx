// @vitest-environment jsdom
import { cleanup, screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { achievementsQueryKey, type Achievement } from '../../api/queries/achievements';
import { renderPage } from '../../testing/renderPage';
import { AchievementsPage } from './AchievementsPage';

const achievement = (over: Partial<Achievement> & Pick<Achievement, 'key' | 'category'>): Achievement => ({
  leveled: false,
  level: 0,
  earnedAt: null,
  thresholds: [],
  progress: null,
  ...over,
});

const achievements: Achievement[] = [
  achievement({ key: 'FOREMAN', category: 'BUILDING', level: 1, earnedAt: '2026-10-14T18:00:00Z' }),
  achievement({ key: 'SCHOLAR', category: 'BUILDING' }),
  achievement({
    key: 'FIRST_BLOOD',
    category: 'COMBAT',
    leveled: true,
    level: 1,
    earnedAt: '2026-10-13T09:00:00Z',
    thresholds: [1, 50, 500],
    progress: 1234,
  }),
  achievement({ key: 'FIRST_WORDS', category: 'SOCIAL' }),
];

function open(list: Achievement[] = achievements, language: 'en' | 'de' = 'en') {
  return renderPage({
    path: '/achievements',
    url: '/achievements',
    element: <AchievementsPage />,
    data: [[achievementsQueryKey, { achievements: list }]],
    language,
  });
}

/** The render test of the achievements page (2.1 H1): categories, earned and locked lines, the progress. */
describe('AchievementsPage', () => {
  afterEach(cleanup);

  it('counts what is earned and lists each category with its achievements', () => {
    open();
    expect(screen.getByRole('heading', { level: 1, name: 'Achievements' })).toBeTruthy();
    expect(screen.getByText('2 of 4 earned')).toBeTruthy();
    for (const category of ['Building', 'Combat', 'Social']) {
      expect(screen.getByRole('heading', { name: category })).toBeTruthy();
    }
    // a category without achievements is left out
    expect(screen.queryByRole('heading', { name: 'Trade' })).toBeNull();
    expect(screen.getAllByText('Not earned yet')).toHaveLength(2);
    expect(screen.getAllByText(/^Earned /)).toHaveLength(2);
  });

  it('shows a levelled achievement with its level and the way to the next one', () => {
    open();
    const line = screen.getByRole('heading', { name: /First Blood/ }).closest('li');
    expect(line).not.toBeNull();
    const item = within(line as HTMLElement);
    expect(item.getByText('Bronze')).toBeTruthy();
    // 1,234 is past silver's 50, but the level is what the server says: the next is silver
    expect(item.getByText(/1,234 of 50 · Next: Silver/)).toBeTruthy();
    expect(item.getByRole('progressbar')).toBeTruthy();
  });

  it('says when the highest level is reached', () => {
    open([
      achievement({
        key: 'FIRST_BLOOD',
        category: 'COMBAT',
        leveled: true,
        level: 3,
        earnedAt: '2026-10-13T09:00:00Z',
        thresholds: [1, 50, 500],
        progress: 1234,
      }),
    ]);
    expect(screen.getByText('1,234 – highest level reached')).toBeTruthy();
    expect(screen.getByText('Gold')).toBeTruthy();
  });

  it('speaks German', () => {
    open(achievements, 'de');
    expect(screen.getByText('2 von 4 verdient')).toBeTruthy();
  });
});
