// @vitest-environment jsdom
import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterAll, afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import { ListDetail } from './ListDetail';

describe('ListDetail', () => {
  // where the detail column starts in the window; jsdom lays nothing out
  let top = 263;

  beforeAll(() => {
    vi.stubGlobal(
      'ResizeObserver',
      class {
        observe() {}
        disconnect() {}
      },
    );
    vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockImplementation(() => ({ top }) as DOMRect);
    vi.useFakeTimers({ toFake: ['requestAnimationFrame', 'cancelAnimationFrame'] });
  });

  afterEach(cleanup);

  afterAll(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  const scrollTo = (newTop: number) => {
    top = newTop;
    fireEvent.scroll(window);
    act(() => vi.advanceTimersToNextFrame());
  };

  it('ends the open item at the window bottom wherever it starts, so its own scroll reaches the end', () => {
    top = 263;
    render(<ListDetail list={<p>list</p>} detail={<p>report</p>} empty="Pick one" label="Open report" openKey={1} />);
    const detail = screen.getByRole('region', { name: 'Open report' });
    // where it sticks below the bars
    detail.style.top = '72px';
    expect(detail.style.getPropertyValue('--detail-top')).toBe('263px');

    // the page scrolls: the head above goes out of view and the column moves up
    scrollTo(150);
    expect(detail.style.getPropertyValue('--detail-top')).toBe('150px');

    // stuck below the bars, or pushed up at the end of a long list: the stuck height
    scrollTo(72);
    expect(detail.style.getPropertyValue('--detail-top')).toBe('72px');
    scrollTo(-120);
    expect(detail.style.getPropertyValue('--detail-top')).toBe('72px');
  });
});
