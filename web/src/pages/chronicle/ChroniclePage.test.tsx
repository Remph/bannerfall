// @vitest-environment jsdom
import { act, cleanup, fireEvent, screen, within } from '@testing-library/react';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { chronicleQueryKey } from '../../api/queries/chronicle';
import { chronicleFixture } from '../../testing/chronicleFixture';
import { renderPage } from '../../testing/renderPage';
import { ChroniclePage } from './ChroniclePage';
import { PLAY_STEP_MS } from './ReplayMap';

let reducedMotion = false;

beforeAll(() => {
  // no canvas in jsdom: the replay then draws nothing; the page and its controls are tested here
  vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(null);
  vi.stubGlobal(
    'ResizeObserver',
    class {
      observe() {}
      disconnect() {}
    },
  );
  vi.stubGlobal('matchMedia', (query: string) => ({
    matches: query === '(prefers-reduced-motion: reduce)' && reducedMotion,
    media: query,
    addEventListener: () => {},
    removeEventListener: () => {},
  }));
});

beforeEach(() => {
  reducedMotion = false;
});

afterEach(() => {
  cleanup();
  vi.useRealTimers();
});

function open(language: 'en' | 'de' = 'en') {
  return renderPage({
    path: '/chronicle',
    url: '/chronicle',
    element: <ChroniclePage />,
    data: [[chronicleQueryKey, chronicleFixture]],
    language,
  });
}

describe('ChroniclePage (2.1 I1)', () => {
  it('tells the turning points day by day, the reader’s own marked', () => {
    open();

    expect(screen.getByRole('heading', { level: 1, name: 'Chronicle' })).toBeTruthy();
    expect(screen.getByText(/The story of this world: 3 days, ended on/)).toBeTruthy();
    const timeline = screen.getByRole('region', { name: 'Turning points' });
    expect(
      within(timeline)
        .getAllByRole('heading', { level: 3 })
        .map((h) => h.textContent),
    ).toEqual(['Day 1', 'Day 2', 'Day 3']);
    expect(within(timeline).getByText('Anna took Wulfsburg (5|5) from Boris.')).toBeTruthy();
    expect(within(timeline).getByText('It was the last village of Boris.')).toBeTruthy();
    expect(within(timeline).getByText('The world ended. Anna won.')).toBeTruthy();
  });

  it('opens the replay on the end and shows the day of a moment the player taps', () => {
    open();

    expect(screen.getByText('Day 3 of 3')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: /Anna took Wulfsburg/ }));
    expect(screen.getByText('Day 2 of 3')).toBeTruthy();
    expect(screen.getByRole('button', { name: /Anna took Wulfsburg/ }).getAttribute('aria-pressed')).toBe('true');
    // the legend counts the villages of that day: Anna holds all three after the conquest
    expect(screen.getByText('3 villages')).toBeTruthy();
  });

  it('scrolls the map back into view when a moment is picked below it (a phone), not when it is in view', () => {
    open();
    const scrollTo = vi.spyOn(window, 'scrollTo').mockImplementation(() => {});
    const column = screen
      .getByRole('region', { name: /Replay of who owned which village/ })
      .closest('[class*="mapColumn"]')!;
    expect(column).toBeTruthy();
    const place = (top: number) => vi.spyOn(column, 'getBoundingClientRect').mockReturnValue({ top } as DOMRect);

    place(-1200); // scrolled down the timeline: the map is above the screen
    fireEvent.click(screen.getByRole('button', { name: /Anna took Wulfsburg/ }));
    expect(scrollTo).toHaveBeenCalledTimes(1);
    expect(scrollTo.mock.calls[0]![0]).toMatchObject({ top: window.scrollY - 1200 });

    place(20); // the PC's sticky column: in view, nothing moves
    fireEvent.click(screen.getByRole('button', { name: /The world ended/ }));
    expect(scrollTo).toHaveBeenCalledTimes(1);
    scrollTo.mockRestore();
  });

  it('steps, slides and plays through the days', () => {
    vi.useFakeTimers();
    open();

    fireEvent.click(screen.getByRole('button', { name: 'Day before' }));
    expect(screen.getByText('Day 2 of 3')).toBeTruthy();
    fireEvent.change(screen.getByRole('slider', { name: 'Day' }), { target: { value: '0' } });
    expect(screen.getByText('The opening')).toBeTruthy();
    // at the opening Anna and Boris hold one village each
    expect(screen.getAllByText('1 village')).toHaveLength(2);

    fireEvent.click(screen.getByRole('button', { name: 'Play' }));
    act(() => {
      vi.advanceTimersByTime(PLAY_STEP_MS);
    });
    expect(screen.getByText('Day 1 of 3')).toBeTruthy();
    for (let step = 0; step < 3; step++) {
      act(() => {
        vi.advanceTimersByTime(PLAY_STEP_MS);
      });
    }
    expect(screen.getByText('Day 3 of 3')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Play' })).toBeTruthy(); // stopped on the last day
    expect(screen.getByRole('button', { name: 'Day after' })).toHaveProperty('disabled', true);
  });

  it('has no play button with reduced motion: the days are stepped by hand', () => {
    reducedMotion = true;
    open();

    expect(screen.queryByRole('button', { name: 'Play' })).toBeNull();
    expect(screen.getByRole('button', { name: 'Day before' })).toBeTruthy();
  });

  it('reads in German', () => {
    open('de');

    expect(screen.getByRole('heading', { level: 1, name: 'Chronik' })).toBeTruthy();
    expect(screen.getByText('Tag 3 von 3')).toBeTruthy();
    expect(screen.getByText('Die Welt endete. Anna siegte.')).toBeTruthy();
  });
});
